package engine.plugins;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Registry for plugin discovery, dependency management, and metadata access.
 * Provides a centralized way to query and manage plugin information.
 */
public interface PluginRegistry {
    
    /**
     * Register a plugin in the registry.
     * @param plugin Plugin to register
     * @throws PluginException if registration fails
     */
    void registerPlugin(Plugin plugin) throws PluginException;
    
    /**
     * Unregister a plugin from the registry.
     * @param pluginId Plugin ID to unregister
     * @return true if plugin was found and unregistered
     */
    boolean unregisterPlugin(String pluginId);
    
    /**
     * Get a plugin by its ID.
     * @param pluginId Plugin ID
     * @return Plugin instance or empty if not found
     */
    Optional<Plugin> getPlugin(String pluginId);
    
    /**
     * Get plugin metadata by ID.
     * @param pluginId Plugin ID
     * @return Plugin metadata or empty if not found
     */
    Optional<PluginMetadata> getPluginMetadata(String pluginId);
    
    /**
     * Get all registered plugins.
     * @return Collection of all plugins
     */
    Collection<Plugin> getAllPlugins();
    
    /**
     * Get all plugins in a specific state.
     * @param state Plugin state to filter by
     * @return Collection of plugins in the specified state
     */
    Collection<Plugin> getPluginsByState(PluginState state);
    
    /**
     * Get plugins that implement a specific interface or extend a class.
     * @param type Plugin type to search for
     * @return Collection of matching plugins
     */
    <T> Collection<T> getPluginsByType(Class<T> type);
    
    /**
     * Find plugins that depend on a specific plugin.
     * @param pluginId Plugin ID to find dependents for
     * @return Collection of dependent plugins
     */
    Collection<Plugin> getDependentPlugins(String pluginId);
    
    /**
     * Get the dependency chain for a plugin.
     * @param pluginId Plugin ID
     * @return Ordered list of dependencies (direct and transitive)
     */
    List<String> getDependencyChain(String pluginId);
    
    /**
     * Check if a plugin exists in the registry.
     * @param pluginId Plugin ID to check
     * @return true if plugin exists
     */
    boolean hasPlugin(String pluginId);
    
    /**
     * Check if a plugin is loaded and active.
     * @param pluginId Plugin ID to check
     * @return true if plugin is loaded and active
     */
    boolean isPluginActive(String pluginId);
    
    /**
     * Validate plugin dependencies.
     * @param pluginId Plugin ID to validate
     * @return Dependency validation result
     */
    DependencyValidationResult validateDependencies(String pluginId);
    
    /**
     * Resolve the load order for plugins based on dependencies.
     * @param pluginIds Plugin IDs to resolve order for
     * @return Ordered list of plugin IDs for loading
     * @throws PluginException if circular dependencies are detected
     */
    List<String> resolveLoadOrder(Collection<String> pluginIds) throws PluginException;
    
    /**
     * Search for plugins by name or description.
     * @param query Search query
     * @return Collection of matching plugins
     */
    Collection<Plugin> searchPlugins(String query);
    
    /**
     * Get plugins by author.
     * @param author Author name
     * @return Collection of plugins by the author
     */
    Collection<Plugin> getPluginsByAuthor(String author);
    
    /**
     * Get registry statistics.
     * @return Registry statistics
     */
    RegistryStatistics getStatistics();
    
    /**
     * Add a registry listener for plugin events.
     * @param listener Registry listener
     */
    void addRegistryListener(RegistryListener listener);
    
    /**
     * Remove a registry listener.
     * @param listener Registry listener to remove
     */
    void removeRegistryListener(RegistryListener listener);
    
    /**
     * Dependency validation result.
     */
    class DependencyValidationResult {
        private final boolean valid;
        private final List<String> missingDependencies;
        private final List<String> conflictingDependencies;
        private final List<String> circularDependencies;
        
        public DependencyValidationResult(boolean valid, List<String> missingDependencies,
                                        List<String> conflictingDependencies, List<String> circularDependencies) {
            this.valid = valid;
            this.missingDependencies = missingDependencies;
            this.conflictingDependencies = conflictingDependencies;
            this.circularDependencies = circularDependencies;
        }
        
        public boolean isValid() { return valid; }
        public List<String> getMissingDependencies() { return missingDependencies; }
        public List<String> getConflictingDependencies() { return conflictingDependencies; }
        public List<String> getCircularDependencies() { return circularDependencies; }
        
        public boolean hasMissingDependencies() { return !missingDependencies.isEmpty(); }
        public boolean hasConflictingDependencies() { return !conflictingDependencies.isEmpty(); }
        public boolean hasCircularDependencies() { return !circularDependencies.isEmpty(); }
    }
    
    /**
     * Registry statistics for monitoring.
     */
    class RegistryStatistics {
        private final int totalPlugins;
        private final int activePlugins;
        private final int failedPlugins;
        private final int dependencyConflicts;
        
        public RegistryStatistics(int totalPlugins, int activePlugins, int failedPlugins, int dependencyConflicts) {
            this.totalPlugins = totalPlugins;
            this.activePlugins = activePlugins;
            this.failedPlugins = failedPlugins;
            this.dependencyConflicts = dependencyConflicts;
        }
        
        public int getTotalPlugins() { return totalPlugins; }
        public int getActivePlugins() { return activePlugins; }
        public int getFailedPlugins() { return failedPlugins; }
        public int getDependencyConflicts() { return dependencyConflicts; }
        
        @Override
        public String toString() {
            return String.format("RegistryStats{total=%d, active=%d, failed=%d, conflicts=%d}",
                               totalPlugins, activePlugins, failedPlugins, dependencyConflicts);
        }
    }
    
    /**
     * Listener for registry events.
     */
    interface RegistryListener {
        /**
         * Called when a plugin is registered.
         * @param plugin Registered plugin
         */
        default void onPluginRegistered(Plugin plugin) {}
        
        /**
         * Called when a plugin is unregistered.
         * @param pluginId Unregistered plugin ID
         */
        default void onPluginUnregistered(String pluginId) {}
        
        /**
         * Called when a plugin state changes.
         * @param pluginId Plugin ID
         * @param oldState Previous state
         * @param newState New state
         */
        default void onPluginStateChanged(String pluginId, PluginState oldState, PluginState newState) {}
        
        /**
         * Called when a dependency conflict is detected.
         * @param pluginId Plugin with conflict
         * @param conflictDetails Conflict details
         */
        default void onDependencyConflict(String pluginId, String conflictDetails) {}
    }
}