package engine.scripting;

import engine.config.ConfigurationManager;
import engine.logging.LogManager;
import engine.logging.MetricsCollector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of the ScriptEngine interface.
 * Provides multi-language script execution with Lua and JavaScript support.
 */
public class DefaultScriptEngine implements ScriptEngine {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = LogManager.getInstance().getMetricsCollector();
    private static final ConfigurationManager configManager = ConfigurationManager.getInstance();
    
    // Core components
    private final Map<String, Script> loadedScripts = new ConcurrentHashMap<>();
    private final Map<String, Object> globalVariables = new ConcurrentHashMap<>();
    private final List<ScriptEngineListener> listeners = new CopyOnWriteArrayList<>();
    
    // Language-specific engines
    private final Map<ScriptLanguage, LanguageEngine> languageEngines = new EnumMap<>(ScriptLanguage.class);
    
    // Execution management
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    
    // Hot reload support
    private final AtomicBoolean hotReloadEnabled = new AtomicBoolean(false);
    private final Map<String, Path> scriptPaths = new ConcurrentHashMap<>();
    
    // Statistics
    private final AtomicLong totalExecutions = new AtomicLong(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);
    private final AtomicLong hotReloads = new AtomicLong(0);
    
    // State
    private volatile boolean initialized = false;
    private final String sessionId = UUID.randomUUID().toString().substring(0, 8);
    
    public DefaultScriptEngine() {
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ScriptEngine-Executor");
            t.setDaemon(true);
            return t;
        });
        
        this.scheduledExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ScriptEngine-Scheduler");
            t.setDaemon(true);
            return t;
        });
    }
    
    @Override
    public void initialize() throws ScriptException {
        if (initialized) {
            logManager.warn("ScriptEngine", "Script engine already initialized");
            return;
        }
        
        logManager.info("ScriptEngine", "Initializing script engine", "sessionId", sessionId);
        
        try {
            // Initialize language engines
            initializeLanguageEngines();
            
            // Setup global variables
            setupGlobalVariables();
            
            // Configure hot reload if enabled
            boolean hotReload = configManager.getBoolean("engine.scripting.hotReload", true);
            setHotReloadEnabled(hotReload);
            
            initialized = true;
            
            logManager.info("ScriptEngine", "Script engine initialized successfully",
                           "supportedLanguages", Arrays.toString(getSupportedLanguages()),
                           "hotReload", hotReloadEnabled.get());
            
        } catch (Exception e) {
            throw new ScriptException("Failed to initialize script engine", e);
        }
    }
    
    @Override
    public void shutdown() throws ScriptException {
        if (!initialized) {
            return;
        }
        
        logManager.info("ScriptEngine", "Shutting down script engine");
        
        try {
            // Unload all scripts
            for (String scriptId : new ArrayList<>(loadedScripts.keySet())) {
                unloadScript(scriptId);
            }
            
            // Shutdown language engines
            for (LanguageEngine engine : languageEngines.values()) {
                try {
                    engine.cleanup();
                } catch (Exception e) {
                    logManager.warn("ScriptEngine", "Error shutting down language engine", 
                                   "engine", engine.getClass().getSimpleName(), "error", e.getMessage());
                }
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
            loadedScripts.clear();
            globalVariables.clear();
            scriptPaths.clear();
            listeners.clear();
            languageEngines.clear();
            
            initialized = false;
            
            logManager.info("ScriptEngine", "Script engine shutdown complete");
            
        } catch (Exception e) {
            throw new ScriptException("Failed to shutdown script engine", e);
        }
    }
    
    @Override
    public CompletableFuture<Script> loadScript(Path scriptPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadScriptSync(scriptPath);
            } catch (ScriptException e) {
                throw new RuntimeException(e);
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Script> loadScript(String scriptId, String source, ScriptLanguage language) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadScriptSync(scriptId, source, language);
            } catch (ScriptException e) {
                throw new RuntimeException(e);
            }
        }, executorService);
    }
    
    @Override
    public Script loadScriptSync(Path scriptPath) throws ScriptException {
        if (!initialized) {
            throw new ScriptException("Script engine not initialized");
        }
        
        if (!Files.exists(scriptPath)) {
            throw new ScriptException("Script file not found: " + scriptPath);
        }
        
        // Determine language from file extension
        ScriptLanguage language = ScriptLanguage.fromFilename(scriptPath.getFileName().toString());
        if (language == null) {
            throw new ScriptException("Unsupported script file type: " + scriptPath);
        }
        
        // Read source code
        String source;
        try {
            source = Files.readString(scriptPath);
        } catch (IOException e) {
            throw new ScriptException("Failed to read script file: " + scriptPath, e);
        }
        
        // Generate script ID from path
        String scriptId = scriptPath.toString().replace('\\', '/');
        
        // Store path for hot reload
        scriptPaths.put(scriptId, scriptPath);
        
        return loadScriptSync(scriptId, source, language);
    }
    
    @Override
    public Script loadScriptSync(String scriptId, String source, ScriptLanguage language) throws ScriptException {
        if (!initialized) {
            throw new ScriptException("Script engine not initialized");
        }
        
        if (scriptId == null || scriptId.trim().isEmpty()) {
            throw new ScriptException("Script ID cannot be null or empty");
        }
        
        if (source == null || source.trim().isEmpty()) {
            throw new ScriptException("Script source cannot be null or empty");
        }
        
        if (language == null) {
            throw new ScriptException("Script language cannot be null");
        }
        
        if (!isLanguageSupported(language)) {
            throw new ScriptException("Unsupported script language: " + language);
        }
        
        logManager.info("ScriptEngine", "Loading script", 
                       "scriptId", scriptId, "language", language.getDisplayName());
        
        try {
            // Get language engine
            LanguageEngine languageEngine = languageEngines.get(language);
            
            // Create script instance
            Script script = languageEngine.createScript(scriptId, source);
            
            // Store loaded script
            loadedScripts.put(scriptId, script);
            
            // Notify listeners
            for (ScriptEngineListener listener : listeners) {
                try {
                    listener.onScriptLoaded(script);
                } catch (Exception e) {
                    logManager.warn("ScriptEngine", "Error in script loaded listener", 
                                   "listener", listener.getClass().getSimpleName(), "error", e.getMessage());
                }
            }
            
            metricsCollector.incrementCounter("script.engine.loaded");
            
            logManager.info("ScriptEngine", "Script loaded successfully", 
                           "scriptId", scriptId, "language", language.getDisplayName());
            
            return script;
            
        } catch (Exception e) {
            metricsCollector.incrementCounter("script.engine.load.failed");
            throw new ScriptException("Failed to load script: " + scriptId, e, scriptId, language, -1, -1);
        }
    }
    
    @Override
    public boolean unloadScript(String scriptId) {
        Script script = loadedScripts.remove(scriptId);
        if (script != null) {
            try {
                script.cleanup();
            } catch (Exception e) {
                logManager.warn("ScriptEngine", "Error disposing script", 
                               "scriptId", scriptId, "error", e.getMessage());
            }
            
            scriptPaths.remove(scriptId);
            
            // Notify listeners
            for (ScriptEngineListener listener : listeners) {
                try {
                    listener.onScriptUnloaded(scriptId);
                } catch (Exception e) {
                    logManager.warn("ScriptEngine", "Error in script unloaded listener", 
                                   "listener", listener.getClass().getSimpleName(), "error", e.getMessage());
                }
            }
            
            logManager.info("ScriptEngine", "Script unloaded", "scriptId", scriptId);
            return true;
        }
        return false;
    }
    
    @Override
    public Optional<Script> getScript(String scriptId) {
        return Optional.ofNullable(loadedScripts.get(scriptId));
    }
    
    @Override
    public Collection<Script> getAllScripts() {
        return new ArrayList<>(loadedScripts.values());
    }
    
    @Override
    public Collection<Script> getScriptsByLanguage(ScriptLanguage language) {
        return loadedScripts.values().stream()
            .filter(script -> script.getLanguage() == language)
            .toList();
    }
    
    @Override
    public CompletableFuture<ScriptExecutionResult> executeScript(String scriptId, Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeScriptSync(scriptId, parameters);
            } catch (ScriptException e) {
                throw new RuntimeException(e);
            }
        }, executorService);
    }
    
    @Override
    public ScriptExecutionResult executeScriptSync(String scriptId, Map<String, Object> parameters) throws ScriptException {
        Script script = loadedScripts.get(scriptId);
        if (script == null) {
            throw new ScriptException("Script not found: " + scriptId);
        }
        
        // Notify listeners
        for (ScriptEngineListener listener : listeners) {
            try {
                listener.onScriptExecutionStarted(scriptId);
            } catch (Exception e) {
                logManager.warn("ScriptEngine", "Error in execution started listener", 
                               "listener", listener.getClass().getSimpleName(), "error", e.getMessage());
            }
        }
        
        Instant startTime = Instant.now();
        
        try {
            ScriptExecutionResult result = script.execute();
            
            // Update statistics
            totalExecutions.incrementAndGet();
            long executionTime = result.getExecutionTime().toMillis();
            totalExecutionTime.addAndGet(executionTime);
            
            metricsCollector.incrementCounter("script.engine.executed");
            metricsCollector.recordTimer("script.engine.execution.time", executionTime);
            
            // Notify listeners
            for (ScriptEngineListener listener : listeners) {
                try {
                    listener.onScriptExecutionCompleted(result);
                } catch (Exception e) {
                    logManager.warn("ScriptEngine", "Error in execution completed listener", 
                                   "listener", listener.getClass().getSimpleName(), "error", e.getMessage());
                }
            }
            
            return result;
            
        } catch (ScriptException e) {
            metricsCollector.incrementCounter("script.engine.execution.failed");
            
            // Notify listeners
            for (ScriptEngineListener listener : listeners) {
                try {
                    listener.onScriptExecutionFailed(scriptId, e);
                } catch (Exception ex) {
                    logManager.warn("ScriptEngine", "Error in execution failed listener", 
                                   "listener", listener.getClass().getSimpleName(), "error", ex.getMessage());
                }
            }
            
            throw e;
        }
    }
    
    @Override
    public CompletableFuture<Object> callScriptFunction(String scriptId, String functionName, Object... arguments) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return callScriptFunctionSync(scriptId, functionName, arguments);
            } catch (ScriptException e) {
                throw new RuntimeException(e);
            }
        }, executorService);
    }
    
    @Override
    public Object callScriptFunctionSync(String scriptId, String functionName, Object... arguments) throws ScriptException {
        Script script = loadedScripts.get(scriptId);
        if (script == null) {
            throw new ScriptException("Script not found: " + scriptId);
        }
        
        return script.callFunction(functionName, arguments).getReturnValue();
    }
    
    @Override
    public CompletableFuture<Script> reloadScript(String scriptId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Script currentScript = loadedScripts.get(scriptId);
                if (currentScript == null) {
                    throw new ScriptException("Script not found: " + scriptId);
                }
                
                // Unload current script
                unloadScript(scriptId);
                
                // Reload from path if available
                Path scriptPath = scriptPaths.get(scriptId);
                if (scriptPath != null) {
                    Script reloadedScript = loadScriptSync(scriptPath);
                    
                    // Notify listeners
                    for (ScriptEngineListener listener : listeners) {
                        try {
                            listener.onScriptReloaded(reloadedScript);
                        } catch (Exception e) {
                            logManager.warn("ScriptEngine", "Error in script reloaded listener", 
                                           "listener", listener.getClass().getSimpleName(), "error", e.getMessage());
                        }
                    }
                    
                    hotReloads.incrementAndGet();
                    metricsCollector.incrementCounter("script.engine.hotreload");
                    
                    return reloadedScript;
                } else {
                    throw new ScriptException("Cannot reload script without source path: " + scriptId);
                }
                
            } catch (ScriptException e) {
                throw new RuntimeException(e);
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Void> compileScript(String scriptId) {
        return CompletableFuture.runAsync(() -> {
            Script script = loadedScripts.get(scriptId);
            if (script == null) {
                throw new RuntimeException(new ScriptException("Script not found: " + scriptId));
            }
            
            // Compilation is handled by the language-specific engines
            logManager.debug("ScriptEngine", "Script compilation requested", "scriptId", scriptId);
        }, executorService);
    }
    
    @Override
    public void setGlobalVariable(String name, Object value) {
        globalVariables.put(name, value);
        
        // Update all loaded scripts
        for (Script script : loadedScripts.values()) {
            try {
                script.setGlobalVariable(name, value);
            } catch (Exception e) {
                logManager.warn("ScriptEngine", "Error setting global variable in script", 
                               "scriptId", script.getId(), "variable", name, "error", e.getMessage());
            }
        }
        
        logManager.debug("ScriptEngine", "Global variable set", "name", name, "value", value);
    }
    
    @Override
    public Object getGlobalVariable(String name) {
        return globalVariables.get(name);
    }
    
    @Override
    public boolean removeGlobalVariable(String name) {
        Object removed = globalVariables.remove(name);
        
        if (removed != null) {
            // Remove from all loaded scripts
            for (Script script : loadedScripts.values()) {
                try {
                    script.setGlobalVariable(name, null);
                } catch (Exception e) {
                    logManager.warn("ScriptEngine", "Error removing global variable from script", 
                                   "scriptId", script.getId(), "variable", name, "error", e.getMessage());
                }
            }
            
            logManager.debug("ScriptEngine", "Global variable removed", "name", name);
            return true;
        }
        
        return false;
    }
    
    @Override
    public Map<String, Object> getAllGlobalVariables() {
        return new HashMap<>(globalVariables);
    }
    
    @Override
    public boolean isLanguageSupported(ScriptLanguage language) {
        return languageEngines.containsKey(language);
    }
    
    @Override
    public ScriptLanguage[] getSupportedLanguages() {
        return languageEngines.keySet().toArray(new ScriptLanguage[0]);
    }
    
    @Override
    public void setHotReloadEnabled(boolean enabled) {
        hotReloadEnabled.set(enabled);
        logManager.info("ScriptEngine", "Hot reload " + (enabled ? "enabled" : "disabled"));
    }
    
    @Override
    public boolean isHotReloadEnabled() {
        return hotReloadEnabled.get();
    }
    
    @Override
    public ScriptEngineStatistics getStatistics() {
        Map<ScriptLanguage, Integer> scriptsByLanguage = new EnumMap<>(ScriptLanguage.class);
        for (Script script : loadedScripts.values()) {
            scriptsByLanguage.merge(script.getLanguage(), 1, Integer::sum);
        }
        
        int compiledScripts = (int) loadedScripts.values().stream()
            .mapToLong(script -> script.isLoaded() ? 1 : 0)
            .sum();
        
        long avgExecutionTime = totalExecutions.get() > 0 ? 
            totalExecutionTime.get() / totalExecutions.get() : 0;
        
        return new ScriptEngineStatistics(
            loadedScripts.size(),
            compiledScripts,
            totalExecutions.get(),
            totalExecutionTime.get(),
            avgExecutionTime,
            (int) hotReloads.get(),
            scriptsByLanguage
        );
    }
    
    @Override
    public void addScriptEngineListener(ScriptEngineListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }
    
    @Override
    public void removeScriptEngineListener(ScriptEngineListener listener) {
        listeners.remove(listener);
    }
    
    private void initializeLanguageEngines() throws ScriptException {
        logManager.debug("ScriptEngine", "Initializing language engines");
        
        // Initialize simplified Lua engine
        try {
            SimplifiedLuaLanguageEngine luaEngine = new SimplifiedLuaLanguageEngine();
            luaEngine.initialize();
            languageEngines.put(ScriptLanguage.LUA, luaEngine);
            logManager.info("ScriptEngine", "Simplified Lua engine initialized");
        } catch (Exception e) {
            logManager.warn("ScriptEngine", "Failed to initialize Lua engine", "error", e.getMessage());
        }
        
        // Initialize simplified JavaScript engine
        try {
            SimplifiedJavaScriptLanguageEngine jsEngine = new SimplifiedJavaScriptLanguageEngine();
            jsEngine.initialize();
            languageEngines.put(ScriptLanguage.JAVASCRIPT, jsEngine);
            logManager.info("ScriptEngine", "Simplified JavaScript engine initialized");
        } catch (Exception e) {
            logManager.warn("ScriptEngine", "Failed to initialize JavaScript engine", "error", e.getMessage());
        }
        
        if (languageEngines.isEmpty()) {
            throw new ScriptException("No language engines could be initialized");
        }
    }
    
    private void setupGlobalVariables() {
        // Setup default global variables
        setGlobalVariable("engine", new EngineAPI());
        setGlobalVariable("log", logManager);
        setGlobalVariable("config", configManager);
        
        logManager.debug("ScriptEngine", "Global variables setup complete");
    }
    
    /**
     * Interface for language-specific script engines.
     */
    interface LanguageEngine {
        ScriptLanguage getLanguage();
        Script createScript(String scriptId, String source) throws ScriptException;
        void initialize() throws ScriptException;
        void cleanup();
        boolean isInitialized();
        Map<String, Object> getEngineInfo();
    }
    
    /**
     * Placeholder for engine API bindings.
     */
    private static class EngineAPI {
        // This will be expanded with actual engine API bindings
    }
}