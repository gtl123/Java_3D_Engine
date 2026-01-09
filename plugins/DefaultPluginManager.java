package engine.plugins;

import engine.assets.AssetManager;
import engine.config.ConfigurationManager;
import engine.gfx.RenderSystem;
import engine.logging.LogManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of the PluginManager interface.
 * Provides complete plugin lifecycle management with integration to existing engine systems.
 */
public class DefaultPluginManager implements PluginManager {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final String ENGINE_VERSION = "1.0.0";
    
    // Core components
    private final DefaultPluginRegistry pluginRegistry;
    private final DefaultEventBus eventBus;
    private final DefaultPluginLoader pluginLoader;
    private final DefaultPluginSandbox pluginSandbox;
    private final DefaultModSupport modSupport;
    
    // Plugin storage
    private final ConcurrentHashMap<String, Plugin> loadedPlugins = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PluginContext> pluginContexts = new ConcurrentHashMap<>();
    
    // Lifecycle management
    private final ExecutorService pluginExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "PluginManager-" + System.currentTimeMillis());
        t.setDaemon(true);
        return t;
    });
    
    // Configuration and state
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean hotReloadEnabled = new AtomicBoolean(true);
    private final List<PluginManagerListener> listeners = new CopyOnWriteArrayList<>();
    
    // Statistics
    private final AtomicLong totalLoadTime = new AtomicLong(0);
    private final AtomicLong totalStartTime = new AtomicLong(0);
    private final AtomicLong hotReloadCount = new AtomicLong(0);
    
    // Engine system references
    private ConfigurationManager configManager;
    private AssetManager assetManager;
    private RenderSystem renderSystem;
    
    public DefaultPluginManager() {
        this.pluginRegistry = new DefaultPluginRegistry();
        this.eventBus = new DefaultEventBus();
        this.pluginLoader = new DefaultPluginLoader();
        this.pluginSandbox = new DefaultPluginSandbox();
        this.modSupport = new DefaultModSupport();
    }
    
    @Override
    public void initialize() throws PluginException {
        if (initialized.get()) {
            logManager.warn("PluginManager", "Plugin manager already initialized");
            return;
        }
        
        logManager.info("PluginManager", "Initializing plugin manager", "engineVersion", ENGINE_VERSION);
        
        try {
            // Get engine system references
            this.configManager = ConfigurationManager.getInstance();
            this.assetManager = AssetManager.getInstance();
            
            // Initialize core components
            pluginRegistry.initialize();
            eventBus.initialize();
            pluginLoader.initialize();
            pluginSandbox.initialize();
            modSupport.initialize();
            
            // Create plugins directory if it doesn't exist
            Path pluginsDir = Paths.get("plugins");
            if (!Files.exists(pluginsDir)) {
                Files.createDirectories(pluginsDir);
                logManager.info("PluginManager", "Created plugins directory", "path", pluginsDir.toString());
            }
            
            initialized.set(true);
            logManager.info("PluginManager", "Plugin manager initialized successfully");
            
        } catch (Exception e) {
            logManager.error("PluginManager", "Failed to initialize plugin manager", e);
            throw new PluginException("Failed to initialize plugin manager", e);
        }
    }
    
    @Override
    public void shutdown() throws PluginException {
        if (!initialized.get()) {
            return;
        }
        
        logManager.info("PluginManager", "Shutting down plugin manager");
        
        try {
            // Stop all plugins
            stopAllPlugins().get(30, TimeUnit.SECONDS);
            
            // Unload all plugins
            for (String pluginId : new ArrayList<>(loadedPlugins.keySet())) {
                unloadPluginSync(pluginId);
            }
            
            // Shutdown components
            modSupport.shutdown();
            pluginSandbox.shutdown();
            pluginLoader.shutdown();
            eventBus.shutdown();
            pluginRegistry.shutdown();
            
            // Shutdown executor
            pluginExecutor.shutdown();
            if (!pluginExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                pluginExecutor.shutdownNow();
            }
            
            initialized.set(false);
            logManager.info("PluginManager", "Plugin manager shutdown complete");
            
        } catch (Exception e) {
            logManager.error("PluginManager", "Error during plugin manager shutdown", e);
            throw new PluginException("Error during shutdown", e);
        }
    }
    
    @Override
    public CompletableFuture<Plugin> loadPlugin(Path pluginPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadPluginSync(pluginPath);
            } catch (PluginException e) {
                throw new RuntimeException(e);
            }
        }, pluginExecutor);
    }
    
    @Override
    public Plugin loadPluginSync(Path pluginPath) throws PluginException {
        if (!initialized.get()) {
            throw new PluginException("Plugin manager not initialized");
        }
        
        logManager.info("PluginManager", "Loading plugin", "path", pluginPath.toString());
        listeners.forEach(l -> l.onPluginLoading(pluginPath));
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Load plugin using plugin loader
            Plugin plugin = pluginLoader.loadPlugin(pluginPath);
            String pluginId = plugin.getMetadata().getId();
            
            // Check if already loaded
            if (loadedPlugins.containsKey(pluginId)) {
                throw new PluginException("Plugin already loaded: " + pluginId);
            }
            
            // Validate compatibility
            if (!plugin.isCompatible(ENGINE_VERSION)) {
                throw new PluginException("Plugin not compatible with engine version " + ENGINE_VERSION);
            }
            
            // Register plugin
            pluginRegistry.registerPlugin(plugin);
            
            // Create plugin context
            PluginContext context = createPluginContext(plugin);
            pluginContexts.put(pluginId, context);
            
            // Initialize plugin
            plugin.initialize(context);
            
            // Store loaded plugin
            loadedPlugins.put(pluginId, plugin);
            
            long loadTime = System.currentTimeMillis() - startTime;
            totalLoadTime.addAndGet(loadTime);
            
            logManager.info("PluginManager", "Plugin loaded successfully", 
                           "pluginId", pluginId, "loadTime", loadTime + "ms");
            listeners.forEach(l -> l.onPluginLoaded(plugin));
            
            return plugin;
            
        } catch (Exception e) {
            logManager.error("PluginManager", "Failed to load plugin", e, "path", pluginPath.toString());
            listeners.forEach(l -> l.onPluginLoadFailed(pluginPath, e));
            throw new PluginException("Failed to load plugin: " + pluginPath, e);
        }
    }
    
    @Override
    public CompletableFuture<Boolean> startPlugin(String pluginId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return startPluginSync(pluginId);
            } catch (PluginException e) {
                throw new RuntimeException(e);
            }
        }, pluginExecutor);
    }
    
    @Override
    public boolean startPluginSync(String pluginId) throws PluginException {
        Plugin plugin = loadedPlugins.get(pluginId);
        if (plugin == null) {
            throw new PluginException("Plugin not loaded: " + pluginId);
        }
        
        if (plugin.getState() == PluginState.STARTED) {
            return true; // Already started
        }
        
        logManager.info("PluginManager", "Starting plugin", "pluginId", pluginId);
        listeners.forEach(l -> l.onPluginStarting(plugin));
        
        long startTime = System.currentTimeMillis();
        
        try {
            plugin.start();
            
            long duration = System.currentTimeMillis() - startTime;
            totalStartTime.addAndGet(duration);
            
            logManager.info("PluginManager", "Plugin started successfully", 
                           "pluginId", pluginId, "startTime", duration + "ms");
            listeners.forEach(l -> l.onPluginStarted(plugin));
            
            return true;
            
        } catch (Exception e) {
            logManager.error("PluginManager", "Failed to start plugin", e, "pluginId", pluginId);
            listeners.forEach(l -> l.onPluginStartFailed(plugin, e));
            throw new PluginException("Failed to start plugin: " + pluginId, e);
        }
    }
    
    @Override
    public CompletableFuture<Boolean> stopPlugin(String pluginId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return stopPluginSync(pluginId);
            } catch (PluginException e) {
                throw new RuntimeException(e);
            }
        }, pluginExecutor);
    }
    
    @Override
    public boolean stopPluginSync(String pluginId) throws PluginException {
        Plugin plugin = loadedPlugins.get(pluginId);
        if (plugin == null) {
            return false;
        }
        
        if (plugin.getState() != PluginState.STARTED) {
            return true; // Already stopped
        }
        
        logManager.info("PluginManager", "Stopping plugin", "pluginId", pluginId);
        listeners.forEach(l -> l.onPluginStopping(plugin));
        
        try {
            plugin.stop();
            
            logManager.info("PluginManager", "Plugin stopped successfully", "pluginId", pluginId);
            listeners.forEach(l -> l.onPluginStopped(plugin));
            
            return true;
            
        } catch (Exception e) {
            logManager.error("PluginManager", "Failed to stop plugin", e, "pluginId", pluginId);
            throw new PluginException("Failed to stop plugin: " + pluginId, e);
        }
    }
    
    @Override
    public CompletableFuture<Boolean> unloadPlugin(String pluginId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return unloadPluginSync(pluginId);
            } catch (PluginException e) {
                throw new RuntimeException(e);
            }
        }, pluginExecutor);
    }
    
    @Override
    public boolean unloadPluginSync(String pluginId) throws PluginException {
        Plugin plugin = loadedPlugins.get(pluginId);
        if (plugin == null) {
            return false;
        }
        
        logManager.info("PluginManager", "Unloading plugin", "pluginId", pluginId);
        listeners.forEach(l -> l.onPluginUnloading(plugin));
        
        try {
            // Stop plugin if running
            if (plugin.getState() == PluginState.STARTED) {
                stopPluginSync(pluginId);
            }
            
            // Cleanup plugin
            plugin.cleanup();
            
            // Remove from registry and storage
            pluginRegistry.unregisterPlugin(pluginId);
            loadedPlugins.remove(pluginId);
            pluginContexts.remove(pluginId);
            
            // Unload from classloader
            pluginLoader.unloadPlugin(plugin);
            
            logManager.info("PluginManager", "Plugin unloaded successfully", "pluginId", pluginId);
            listeners.forEach(l -> l.onPluginUnloaded(pluginId));
            
            return true;
            
        } catch (Exception e) {
            logManager.error("PluginManager", "Failed to unload plugin", e, "pluginId", pluginId);
            throw new PluginException("Failed to unload plugin: " + pluginId, e);
        }
    }
    
    @Override
    public CompletableFuture<Void> startAllPlugins() {
        return CompletableFuture.runAsync(() -> {
            try {
                List<String> loadOrder = pluginRegistry.resolveLoadOrder(loadedPlugins.keySet());
                for (String pluginId : loadOrder) {
                    startPluginSync(pluginId);
                }
            } catch (PluginException e) {
                throw new RuntimeException(e);
            }
        }, pluginExecutor);
    }
    
    @Override
    public CompletableFuture<Void> stopAllPlugins() {
        return CompletableFuture.runAsync(() -> {
            try {
                List<String> loadOrder = pluginRegistry.resolveLoadOrder(loadedPlugins.keySet());
                Collections.reverse(loadOrder); // Stop in reverse order
                for (String pluginId : loadOrder) {
                    stopPluginSync(pluginId);
                }
            } catch (PluginException e) {
                throw new RuntimeException(e);
            }
        }, pluginExecutor);
    }
    
    @Override
    public CompletableFuture<List<Plugin>> discoverAndLoadPlugins(Path pluginDirectory) {
        return CompletableFuture.supplyAsync(() -> {
            List<Plugin> loadedPluginsList = new ArrayList<>();
            List<Path> pluginPaths = pluginLoader.discoverPlugins(pluginDirectory);
            
            for (Path pluginPath : pluginPaths) {
                try {
                    Plugin plugin = loadPluginSync(pluginPath);
                    loadedPluginsList.add(plugin);
                } catch (PluginException e) {
                    logManager.error("PluginManager", "Failed to load discovered plugin", e, 
                                   "path", pluginPath.toString());
                }
            }
            
            return loadedPluginsList;
        }, pluginExecutor);
    }
    
    // Helper methods
    private PluginContext createPluginContext(Plugin plugin) {
        return new DefaultPluginContext(plugin, this, configManager, assetManager, renderSystem);
    }
    
    // Getters and utility methods
    @Override
    public Optional<Plugin> getPlugin(String pluginId) {
        return Optional.ofNullable(loadedPlugins.get(pluginId));
    }
    
    @Override
    public Collection<Plugin> getAllPlugins() {
        return new ArrayList<>(loadedPlugins.values());
    }
    
    @Override
    public Collection<Plugin> getPluginsByState(PluginState state) {
        return loadedPlugins.values().stream()
                .filter(plugin -> plugin.getState() == state)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    @Override
    public boolean isPluginLoaded(String pluginId) {
        return loadedPlugins.containsKey(pluginId);
    }
    
    @Override
    public boolean isPluginRunning(String pluginId) {
        Plugin plugin = loadedPlugins.get(pluginId);
        return plugin != null && plugin.getState() == PluginState.STARTED;
    }
    
    @Override
    public PluginRegistry getPluginRegistry() {
        return pluginRegistry;
    }
    
    @Override
    public EventBus getEventBus() {
        return eventBus;
    }
    
    @Override
    public PluginLoader getPluginLoader() {
        return pluginLoader;
    }
    
    @Override
    public PluginSandbox getPluginSandbox() {
        return pluginSandbox;
    }
    
    @Override
    public void setHotReloadEnabled(boolean enabled) {
        hotReloadEnabled.set(enabled);
        logManager.info("PluginManager", "Hot reload " + (enabled ? "enabled" : "disabled"));
    }
    
    @Override
    public boolean isHotReloadEnabled() {
        return hotReloadEnabled.get();
    }
    
    @Override
    public void addPluginManagerListener(PluginManagerListener listener) {
        listeners.add(listener);
    }
    
    @Override
    public void removePluginManagerListener(PluginManagerListener listener) {
        listeners.remove(listener);
    }
    
    @Override
    public PluginManagerStatistics getStatistics() {
        int totalPlugins = loadedPlugins.size();
        int runningPlugins = (int) loadedPlugins.values().stream()
                .filter(p -> p.getState() == PluginState.STARTED)
                .count();
        int failedPlugins = (int) loadedPlugins.values().stream()
                .filter(p -> p.getState() == PluginState.FAILED)
                .count();
        
        return new PluginManagerStatistics(
                totalPlugins, totalPlugins, runningPlugins, failedPlugins,
                totalLoadTime.get(), totalStartTime.get(), (int) hotReloadCount.get()
        );
    }
    
    @Override
    public String getEngineVersion() {
        return ENGINE_VERSION;
    }
    
    // Placeholder implementations for remaining methods
    @Override
    public CompletableFuture<Plugin> reloadPlugin(String pluginId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Plugin plugin = loadedPlugins.get(pluginId);
                if (plugin == null) {
                    throw new RuntimeException("Plugin not found: " + pluginId);
                }
                
                // Store plugin path for reloading
                Path pluginPath = Paths.get(plugin.getMetadata().getProperty("pluginPath", ""));
                
                // Unload current plugin
                unloadPluginSync(pluginId);
                
                // Load new version
                Plugin reloadedPlugin = loadPluginSync(pluginPath);
                hotReloadCount.incrementAndGet();
                
                return reloadedPlugin;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, pluginExecutor);
    }
    
    @Override
    public DependencyValidationResult validatePluginDependencies(Collection<String> pluginIds) {
        try {
            List<String> loadOrder = pluginRegistry.resolveLoadOrder(pluginIds);
            return new DependencyValidationResult(true, loadOrder, Collections.emptyList(), Collections.emptyList());
        } catch (PluginException e) {
            return new DependencyValidationResult(false, Collections.emptyList(), 
                    Collections.singletonList(e.getMessage()), Collections.emptyList());
        }
    }
    
    /**
     * Set the render system reference.
     * @param renderSystem Render system instance
     */
    public void setRenderSystem(RenderSystem renderSystem) {
        this.renderSystem = renderSystem;
    }
}