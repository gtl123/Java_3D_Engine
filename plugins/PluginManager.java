package engine.plugins;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Central plugin lifecycle management system.
 * Coordinates plugin loading, initialization, starting, stopping, and unloading.
 * Integrates with other plugin system components for complete plugin management.
 */
public interface PluginManager {
    
    /**
     * Initialize the plugin manager.
     * @throws PluginException if initialization fails
     */
    void initialize() throws PluginException;
    
    /**
     * Shutdown the plugin manager and all loaded plugins.
     * @throws PluginException if shutdown fails
     */
    void shutdown() throws PluginException;
    
    /**
     * Load a plugin from a JAR file.
     * @param pluginPath Path to the plugin JAR file
     * @return CompletableFuture that completes when plugin is loaded
     */
    CompletableFuture<Plugin> loadPlugin(Path pluginPath);
    
    /**
     * Load a plugin from a JAR file synchronously.
     * @param pluginPath Path to the plugin JAR file
     * @return Loaded plugin
     * @throws PluginException if loading fails
     */
    Plugin loadPluginSync(Path pluginPath) throws PluginException;
    
    /**
     * Unload a plugin by ID.
     * @param pluginId Plugin ID to unload
     * @return CompletableFuture that completes when plugin is unloaded
     */
    CompletableFuture<Boolean> unloadPlugin(String pluginId);
    
    /**
     * Unload a plugin synchronously.
     * @param pluginId Plugin ID to unload
     * @return true if plugin was found and unloaded
     * @throws PluginException if unloading fails
     */
    boolean unloadPluginSync(String pluginId) throws PluginException;
    
    /**
     * Start a plugin by ID.
     * @param pluginId Plugin ID to start
     * @return CompletableFuture that completes when plugin is started
     */
    CompletableFuture<Boolean> startPlugin(String pluginId);
    
    /**
     * Start a plugin synchronously.
     * @param pluginId Plugin ID to start
     * @return true if plugin was started successfully
     * @throws PluginException if starting fails
     */
    boolean startPluginSync(String pluginId) throws PluginException;
    
    /**
     * Stop a plugin by ID.
     * @param pluginId Plugin ID to stop
     * @return CompletableFuture that completes when plugin is stopped
     */
    CompletableFuture<Boolean> stopPlugin(String pluginId);
    
    /**
     * Stop a plugin synchronously.
     * @param pluginId Plugin ID to stop
     * @return true if plugin was stopped successfully
     * @throws PluginException if stopping fails
     */
    boolean stopPluginSync(String pluginId) throws PluginException;
    
    /**
     * Reload a plugin (stop, unload, load, start).
     * @param pluginId Plugin ID to reload
     * @return CompletableFuture that completes when plugin is reloaded
     */
    CompletableFuture<Plugin> reloadPlugin(String pluginId);
    
    /**
     * Start all loaded plugins in dependency order.
     * @return CompletableFuture that completes when all plugins are started
     */
    CompletableFuture<Void> startAllPlugins();
    
    /**
     * Stop all running plugins in reverse dependency order.
     * @return CompletableFuture that completes when all plugins are stopped
     */
    CompletableFuture<Void> stopAllPlugins();
    
    /**
     * Discover and load plugins from a directory.
     * @param pluginDirectory Directory to scan for plugins
     * @return CompletableFuture that completes when all plugins are loaded
     */
    CompletableFuture<List<Plugin>> discoverAndLoadPlugins(Path pluginDirectory);
    
    /**
     * Get a plugin by ID.
     * @param pluginId Plugin ID
     * @return Plugin instance or empty if not found
     */
    Optional<Plugin> getPlugin(String pluginId);
    
    /**
     * Get all loaded plugins.
     * @return Collection of all loaded plugins
     */
    Collection<Plugin> getAllPlugins();
    
    /**
     * Get plugins in a specific state.
     * @param state Plugin state to filter by
     * @return Collection of plugins in the specified state
     */
    Collection<Plugin> getPluginsByState(PluginState state);
    
    /**
     * Check if a plugin is loaded.
     * @param pluginId Plugin ID to check
     * @return true if plugin is loaded
     */
    boolean isPluginLoaded(String pluginId);
    
    /**
     * Check if a plugin is running.
     * @param pluginId Plugin ID to check
     * @return true if plugin is running
     */
    boolean isPluginRunning(String pluginId);
    
    /**
     * Get the plugin registry.
     * @return Plugin registry instance
     */
    PluginRegistry getPluginRegistry();
    
    /**
     * Get the event bus.
     * @return Event bus instance
     */
    EventBus getEventBus();
    
    /**
     * Get the plugin loader.
     * @return Plugin loader instance
     */
    PluginLoader getPluginLoader();
    
    /**
     * Get the plugin sandbox.
     * @return Plugin sandbox instance
     */
    PluginSandbox getPluginSandbox();
    
    /**
     * Enable or disable hot reloading for development.
     * @param enabled Whether hot reloading is enabled
     */
    void setHotReloadEnabled(boolean enabled);
    
    /**
     * Check if hot reloading is enabled.
     * @return true if hot reloading is enabled
     */
    boolean isHotReloadEnabled();
    
    /**
     * Add a plugin manager listener.
     * @param listener Listener to add
     */
    void addPluginManagerListener(PluginManagerListener listener);
    
    /**
     * Remove a plugin manager listener.
     * @param listener Listener to remove
     */
    void removePluginManagerListener(PluginManagerListener listener);
    
    /**
     * Get plugin manager statistics.
     * @return Plugin manager statistics
     */
    PluginManagerStatistics getStatistics();
    
    /**
     * Validate plugin dependencies and load order.
     * @param pluginIds Plugin IDs to validate
     * @return Validation result
     */
    DependencyValidationResult validatePluginDependencies(Collection<String> pluginIds);
    
    /**
     * Get the engine version for plugin compatibility checks.
     * @return Engine version string
     */
    String getEngineVersion();
    
    /**
     * Plugin manager listener for lifecycle events.
     */
    interface PluginManagerListener {
        /**
         * Called before a plugin is loaded.
         * @param pluginPath Path to plugin being loaded
         */
        default void onPluginLoading(Path pluginPath) {}
        
        /**
         * Called after a plugin is successfully loaded.
         * @param plugin Loaded plugin
         */
        default void onPluginLoaded(Plugin plugin) {}
        
        /**
         * Called when plugin loading fails.
         * @param pluginPath Path to plugin that failed to load
         * @param error Error that occurred
         */
        default void onPluginLoadFailed(Path pluginPath, Throwable error) {}
        
        /**
         * Called before a plugin is started.
         * @param plugin Plugin being started
         */
        default void onPluginStarting(Plugin plugin) {}
        
        /**
         * Called after a plugin is successfully started.
         * @param plugin Started plugin
         */
        default void onPluginStarted(Plugin plugin) {}
        
        /**
         * Called when plugin starting fails.
         * @param plugin Plugin that failed to start
         * @param error Error that occurred
         */
        default void onPluginStartFailed(Plugin plugin, Throwable error) {}
        
        /**
         * Called before a plugin is stopped.
         * @param plugin Plugin being stopped
         */
        default void onPluginStopping(Plugin plugin) {}
        
        /**
         * Called after a plugin is successfully stopped.
         * @param plugin Stopped plugin
         */
        default void onPluginStopped(Plugin plugin) {}
        
        /**
         * Called before a plugin is unloaded.
         * @param plugin Plugin being unloaded
         */
        default void onPluginUnloading(Plugin plugin) {}
        
        /**
         * Called after a plugin is successfully unloaded.
         * @param pluginId ID of unloaded plugin
         */
        default void onPluginUnloaded(String pluginId) {}
    }
    
    /**
     * Plugin manager statistics for monitoring.
     */
    class PluginManagerStatistics {
        private final int totalPlugins;
        private final int loadedPlugins;
        private final int runningPlugins;
        private final int failedPlugins;
        private final long totalLoadTime;
        private final long totalStartTime;
        private final int hotReloads;
        
        public PluginManagerStatistics(int totalPlugins, int loadedPlugins, int runningPlugins, 
                                     int failedPlugins, long totalLoadTime, long totalStartTime, int hotReloads) {
            this.totalPlugins = totalPlugins;
            this.loadedPlugins = loadedPlugins;
            this.runningPlugins = runningPlugins;
            this.failedPlugins = failedPlugins;
            this.totalLoadTime = totalLoadTime;
            this.totalStartTime = totalStartTime;
            this.hotReloads = hotReloads;
        }
        
        public int getTotalPlugins() { return totalPlugins; }
        public int getLoadedPlugins() { return loadedPlugins; }
        public int getRunningPlugins() { return runningPlugins; }
        public int getFailedPlugins() { return failedPlugins; }
        public long getTotalLoadTime() { return totalLoadTime; }
        public long getTotalStartTime() { return totalStartTime; }
        public int getHotReloads() { return hotReloads; }
        
        @Override
        public String toString() {
            return String.format("PluginManagerStats{total=%d, loaded=%d, running=%d, failed=%d, hotReloads=%d}",
                               totalPlugins, loadedPlugins, runningPlugins, failedPlugins, hotReloads);
        }
    }
    
    /**
     * Dependency validation result.
     */
    class DependencyValidationResult {
        private final boolean valid;
        private final List<String> loadOrder;
        private final List<String> errors;
        private final List<String> warnings;
        
        public DependencyValidationResult(boolean valid, List<String> loadOrder, 
                                        List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.loadOrder = loadOrder;
            this.errors = errors;
            this.warnings = warnings;
        }
        
        public boolean isValid() { return valid; }
        public List<String> getLoadOrder() { return loadOrder; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }
}