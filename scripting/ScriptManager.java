package engine.scripting;

import engine.config.ConfigurationManager;
import engine.logging.LogManager;
import engine.logging.MetricsCollector;

import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Script lifecycle and caching management system.
 * Coordinates script loading, caching, hot-reloading, and resource management.
 */
public class ScriptManager {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = LogManager.getInstance().getMetricsCollector();
    private static final ConfigurationManager configManager = ConfigurationManager.getInstance();
    
    private static volatile ScriptManager instance;
    
    // Core components
    private final ScriptEngine scriptEngine;
    private final ScriptCache scriptCache;
    private final ScriptHotReload hotReloadManager;
    
    // Script registry and tracking
    private final Map<String, ScriptMetadata> scriptRegistry = new ConcurrentHashMap<>();
    private final Map<String, Path> scriptPaths = new ConcurrentHashMap<>();
    private final Set<String> autoLoadScripts = ConcurrentHashMap.newKeySet();
    
    // Execution management
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    
    // Configuration
    private final Duration cacheExpiration;
    private final int maxCachedScripts;
    private final boolean enableAutoReload;
    private final Duration autoReloadInterval;
    
    // Statistics
    private final AtomicLong totalScriptsLoaded = new AtomicLong(0);
    private final AtomicLong totalScriptsUnloaded = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    
    // State
    private volatile boolean initialized = false;
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private final String sessionId = UUID.randomUUID().toString().substring(0, 8);
    
    /**
     * Script manager configuration.
     */
    public static class Config {
        private Duration cacheExpiration = Duration.ofMinutes(30);
        private int maxCachedScripts = 100;
        private boolean enableAutoReload = true;
        private Duration autoReloadInterval = Duration.ofSeconds(5);
        private ScriptEngine scriptEngine;
        
        public Config cacheExpiration(Duration duration) { this.cacheExpiration = duration; return this; }
        public Config maxCachedScripts(int max) { this.maxCachedScripts = max; return this; }
        public Config enableAutoReload(boolean enable) { this.enableAutoReload = enable; return this; }
        public Config autoReloadInterval(Duration interval) { this.autoReloadInterval = interval; return this; }
        public Config scriptEngine(ScriptEngine engine) { this.scriptEngine = engine; return this; }
        
        public ScriptManager build() {
            return new ScriptManager(this);
        }
    }
    
    /**
     * Get the singleton instance of ScriptManager.
     */
    public static ScriptManager getInstance() {
        if (instance == null) {
            synchronized (ScriptManager.class) {
                if (instance == null) {
                    instance = new Config()
                        .scriptEngine(new DefaultScriptEngine())
                        .build();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize ScriptManager with custom configuration.
     */
    public static synchronized void initialize(Config config) {
        if (instance != null) {
            logManager.warn("ScriptManager", "ScriptManager already initialized, shutting down existing instance");
            instance.shutdown();
        }
        instance = config.build();
    }
    
    private ScriptManager(Config config) {
        this.scriptEngine = config.scriptEngine != null ? config.scriptEngine : new DefaultScriptEngine();
        this.cacheExpiration = config.cacheExpiration;
        this.maxCachedScripts = config.maxCachedScripts;
        this.enableAutoReload = config.enableAutoReload;
        this.autoReloadInterval = config.autoReloadInterval;
        
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ScriptManager-Executor");
            t.setDaemon(true);
            return t;
        });
        
        this.scheduledExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ScriptManager-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        this.scriptCache = new ScriptCache(maxCachedScripts, cacheExpiration);
        this.hotReloadManager = new ScriptHotReload(this);
    }
    
    /**
     * Initialize the script manager.
     */
    public synchronized void initialize() throws ScriptException {
        if (initialized) {
            logManager.warn("ScriptManager", "Script manager already initialized");
            return;
        }
        
        logManager.info("ScriptManager", "Initializing script manager", "sessionId", sessionId);
        
        try {
            // Initialize script engine
            scriptEngine.initialize();
            
            // Start cache cleanup task
            startCacheCleanupTask();
            
            // Start auto-reload monitoring if enabled
            if (enableAutoReload) {
                hotReloadManager.start();
            }
            
            initialized = true;
            
            logManager.info("ScriptManager", "Script manager initialized successfully",
                           "cacheExpiration", cacheExpiration,
                           "maxCachedScripts", maxCachedScripts,
                           "autoReload", enableAutoReload);
            
        } catch (Exception e) {
            throw new ScriptException("Failed to initialize script manager", e);
        }
    }
    
    /**
     * Shutdown the script manager.
     */
    public synchronized void shutdown() {
        if (!initialized || shutdownRequested.get()) {
            return;
        }
        
        shutdownRequested.set(true);
        
        logManager.info("ScriptManager", "Shutting down script manager");
        
        try {
            // Stop hot reload monitoring
            if (hotReloadManager != null) {
                hotReloadManager.stop();
            }
            
            // Unload all scripts
            unloadAllScripts();
            
            // Shutdown script engine
            if (scriptEngine != null) {
                scriptEngine.shutdown();
            }
            
            // Shutdown executors
            executorService.shutdown();
            scheduledExecutor.shutdown();
            
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
                if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                scheduledExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            // Clear data structures
            scriptRegistry.clear();
            scriptPaths.clear();
            autoLoadScripts.clear();
            scriptCache.clear();
            
            initialized = false;
            
            logManager.info("ScriptManager", "Script manager shutdown complete");
            
        } catch (Exception e) {
            logManager.error("ScriptManager", "Error during shutdown", e);
        }
    }
    
    /**
     * Load a script from file.
     */
    public CompletableFuture<Script> loadScript(Path scriptPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadScriptSync(scriptPath);
            } catch (ScriptException e) {
                throw new RuntimeException(e);
            }
        }, executorService);
    }
    
    /**
     * Load a script from file synchronously.
     */
    public Script loadScriptSync(Path scriptPath) throws ScriptException {
        if (!initialized) {
            throw new ScriptException("Script manager not initialized");
        }
        
        String scriptId = generateScriptId(scriptPath);
        
        // Check cache first
        Script cachedScript = scriptCache.get(scriptId);
        if (cachedScript != null && !isScriptModified(scriptPath, cachedScript)) {
            cacheHits.incrementAndGet();
            metricsCollector.incrementCounter("script.manager.cache.hit");
            return cachedScript;
        }
        
        cacheMisses.incrementAndGet();
        metricsCollector.incrementCounter("script.manager.cache.miss");
        
        // Load script through engine
        Script script = scriptEngine.loadScriptSync(scriptPath);
        
        // Register script
        registerScript(script, scriptPath);
        
        // Cache the script
        scriptCache.put(script);
        
        totalScriptsLoaded.incrementAndGet();
        metricsCollector.incrementCounter("script.manager.loaded");
        
        logManager.info("ScriptManager", "Script loaded and cached", 
                       "scriptId", scriptId, "path", scriptPath);
        
        return script;
    }
    
    /**
     * Load a script from source code.
     */
    public CompletableFuture<Script> loadScript(String scriptId, String source, ScriptLanguage language) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadScriptSync(scriptId, source, language);
            } catch (ScriptException e) {
                throw new RuntimeException(e);
            }
        }, executorService);
    }
    
    /**
     * Load a script from source code synchronously.
     */
    public Script loadScriptSync(String scriptId, String source, ScriptLanguage language) throws ScriptException {
        if (!initialized) {
            throw new ScriptException("Script manager not initialized");
        }
        
        // Check cache first
        Script cachedScript = scriptCache.get(scriptId);
        if (cachedScript != null) {
            cacheHits.incrementAndGet();
            metricsCollector.incrementCounter("script.manager.cache.hit");
            return cachedScript;
        }
        
        cacheMisses.incrementAndGet();
        metricsCollector.incrementCounter("script.manager.cache.miss");
        
        // Load script through engine
        Script script = scriptEngine.loadScriptSync(scriptId, source, language);
        
        // Register script
        registerScript(script, null);
        
        // Cache the script
        scriptCache.put(script);
        
        totalScriptsLoaded.incrementAndGet();
        metricsCollector.incrementCounter("script.manager.loaded");
        
        logManager.info("ScriptManager", "Script loaded and cached", "scriptId", scriptId);
        
        return script;
    }
    
    /**
     * Unload a script by ID.
     */
    public boolean unloadScript(String scriptId) {
        if (!initialized) {
            return false;
        }
        
        // Remove from cache
        scriptCache.remove(scriptId);
        
        // Remove from registry
        scriptRegistry.remove(scriptId);
        scriptPaths.remove(scriptId);
        autoLoadScripts.remove(scriptId);
        
        // Unload from engine
        boolean unloaded = scriptEngine.unloadScript(scriptId);
        
        if (unloaded) {
            totalScriptsUnloaded.incrementAndGet();
            metricsCollector.incrementCounter("script.manager.unloaded");
            
            logManager.info("ScriptManager", "Script unloaded", "scriptId", scriptId);
        }
        
        return unloaded;
    }
    
    /**
     * Get a script by ID.
     */
    public Optional<Script> getScript(String scriptId) {
        if (!initialized) {
            return Optional.empty();
        }
        
        // Check cache first
        Script cachedScript = scriptCache.get(scriptId);
        if (cachedScript != null) {
            cacheHits.incrementAndGet();
            return Optional.of(cachedScript);
        }
        
        // Check engine
        return scriptEngine.getScript(scriptId);
    }
    
    /**
     * Reload a script from its source.
     */
    public CompletableFuture<Script> reloadScript(String scriptId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Remove from cache
                scriptCache.remove(scriptId);
                
                // Get script path
                Path scriptPath = scriptPaths.get(scriptId);
                if (scriptPath != null) {
                    // Reload from file
                    return loadScriptSync(scriptPath);
                } else {
                    // Reload through engine
                    Script reloadedScript = scriptEngine.reloadScript(scriptId).get();
                    scriptCache.put(reloadedScript);
                    return reloadedScript;
                }
                
            } catch (Exception e) {
                throw new RuntimeException(new ScriptException("Failed to reload script: " + scriptId, e));
            }
        }, executorService);
    }
    
    /**
     * Load scripts from a directory.
     */
    public CompletableFuture<List<Script>> loadScriptsFromDirectory(Path directory) {
        return CompletableFuture.supplyAsync(() -> {
            List<Script> loadedScripts = new ArrayList<>();
            
            try {
                if (!Files.exists(directory) || !Files.isDirectory(directory)) {
                    logManager.warn("ScriptManager", "Script directory not found", "directory", directory);
                    return loadedScripts;
                }
                
                Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .filter(path -> ScriptLanguage.fromFilename(path.getFileName().toString()) != null)
                    .forEach(scriptPath -> {
                        try {
                            Script script = loadScriptSync(scriptPath);
                            loadedScripts.add(script);
                        } catch (ScriptException e) {
                            logManager.error("ScriptManager", "Failed to load script from directory", 
                                           "path", scriptPath, "error", e.getMessage());
                        }
                    });
                
                logManager.info("ScriptManager", "Loaded scripts from directory", 
                               "directory", directory, "count", loadedScripts.size());
                
            } catch (Exception e) {
                logManager.error("ScriptManager", "Error loading scripts from directory", 
                               "directory", directory, "error", e.getMessage());
            }
            
            return loadedScripts;
        }, executorService);
    }
    
    /**
     * Add a script to auto-reload monitoring.
     */
    public void addAutoReloadScript(String scriptId) {
        autoLoadScripts.add(scriptId);
        logManager.debug("ScriptManager", "Script added to auto-reload", "scriptId", scriptId);
    }
    
    /**
     * Remove a script from auto-reload monitoring.
     */
    public void removeAutoReloadScript(String scriptId) {
        autoLoadScripts.remove(scriptId);
        logManager.debug("ScriptManager", "Script removed from auto-reload", "scriptId", scriptId);
    }
    
    /**
     * Get script manager statistics.
     */
    public ScriptManagerStatistics getStatistics() {
        return new ScriptManagerStatistics(
            scriptRegistry.size(),
            (int) totalScriptsLoaded.get(),
            (int) totalScriptsUnloaded.get(),
            (int) cacheHits.get(),
            (int) cacheMisses.get(),
            scriptCache.getSize(),
            scriptCache.getMaxSize(),
            autoLoadScripts.size()
        );
    }
    
    /**
     * Get the underlying script engine.
     */
    public ScriptEngine getScriptEngine() {
        return scriptEngine;
    }
    
    private void registerScript(Script script, Path scriptPath) {
        ScriptMetadata metadata = new ScriptMetadata(
            script.getId(),
            script.getName(),
            script.getLanguage(),
            script.getLoadTime(),
            scriptPath
        );
        
        scriptRegistry.put(script.getId(), metadata);
        
        if (scriptPath != null) {
            scriptPaths.put(script.getId(), scriptPath);
        }
    }
    
    private void unloadAllScripts() {
        logManager.info("ScriptManager", "Unloading all scripts", "count", scriptRegistry.size());
        
        for (String scriptId : new ArrayList<>(scriptRegistry.keySet())) {
            unloadScript(scriptId);
        }
    }
    
    private String generateScriptId(Path scriptPath) {
        return scriptPath.toString().replace('\\', '/');
    }
    
    private boolean isScriptModified(Path scriptPath, Script script) {
        try {
            Instant fileModified = Files.getLastModifiedTime(scriptPath).toInstant();
            return fileModified.isAfter(script.getLastModified());
        } catch (Exception e) {
            logManager.warn("ScriptManager", "Error checking script modification time", 
                           "path", scriptPath, "error", e.getMessage());
            return true; // Assume modified if we can't check
        }
    }
    
    private void startCacheCleanupTask() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                scriptCache.cleanup();
            } catch (Exception e) {
                logManager.warn("ScriptManager", "Error during cache cleanup", "error", e.getMessage());
            }
        }, 1, 1, TimeUnit.MINUTES);
    }
    
    /**
     * Script metadata for tracking.
     */
    private static class ScriptMetadata {
        private final String id;
        private final String name;
        private final ScriptLanguage language;
        private final Instant loadTime;
        private final Path path;
        
        public ScriptMetadata(String id, String name, ScriptLanguage language, Instant loadTime, Path path) {
            this.id = id;
            this.name = name;
            this.language = language;
            this.loadTime = loadTime;
            this.path = path;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public ScriptLanguage getLanguage() { return language; }
        public Instant getLoadTime() { return loadTime; }
        public Path getPath() { return path; }
    }
    
    /**
     * Script manager statistics.
     */
    public static class ScriptManagerStatistics {
        private final int totalScripts;
        private final int scriptsLoaded;
        private final int scriptsUnloaded;
        private final int cacheHits;
        private final int cacheMisses;
        private final int cacheSize;
        private final int maxCacheSize;
        private final int autoReloadScripts;
        
        public ScriptManagerStatistics(int totalScripts, int scriptsLoaded, int scriptsUnloaded,
                                     int cacheHits, int cacheMisses, int cacheSize, int maxCacheSize,
                                     int autoReloadScripts) {
            this.totalScripts = totalScripts;
            this.scriptsLoaded = scriptsLoaded;
            this.scriptsUnloaded = scriptsUnloaded;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.cacheSize = cacheSize;
            this.maxCacheSize = maxCacheSize;
            this.autoReloadScripts = autoReloadScripts;
        }
        
        public int getTotalScripts() { return totalScripts; }
        public int getScriptsLoaded() { return scriptsLoaded; }
        public int getScriptsUnloaded() { return scriptsUnloaded; }
        public int getCacheHits() { return cacheHits; }
        public int getCacheMisses() { return cacheMisses; }
        public int getCacheSize() { return cacheSize; }
        public int getMaxCacheSize() { return maxCacheSize; }
        public int getAutoReloadScripts() { return autoReloadScripts; }
        
        public double getCacheHitRatio() {
            int total = cacheHits + cacheMisses;
            return total > 0 ? (double) cacheHits / total : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("ScriptManagerStats{scripts=%d, loaded=%d, cache=%d/%d, hitRatio=%.2f, autoReload=%d}",
                               totalScripts, scriptsLoaded, cacheSize, maxCacheSize, getCacheHitRatio(), autoReloadScripts);
        }
    }
    
    /**
     * Create a new script manager configuration.
     */
    public static Config config() {
        return new Config();
    }
}