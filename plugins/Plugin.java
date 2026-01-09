package engine.plugins;

/**
 * Base interface for all engine plugins.
 * Defines the core lifecycle and metadata methods that all plugins must implement.
 */
public interface Plugin {
    
    /**
     * Get the plugin metadata containing information about the plugin.
     * @return Plugin metadata
     */
    PluginMetadata getMetadata();
    
    /**
     * Initialize the plugin. Called when the plugin is loaded.
     * @param context Plugin context providing access to engine systems
     * @throws PluginException if initialization fails
     */
    void initialize(PluginContext context) throws PluginException;
    
    /**
     * Start the plugin. Called after all plugins are initialized.
     * @throws PluginException if startup fails
     */
    void start() throws PluginException;
    
    /**
     * Stop the plugin. Called when the plugin is being unloaded.
     * @throws PluginException if shutdown fails
     */
    void stop() throws PluginException;
    
    /**
     * Cleanup plugin resources. Called after stop().
     * @throws PluginException if cleanup fails
     */
    void cleanup() throws PluginException;
    
    /**
     * Get the current state of the plugin.
     * @return Plugin state
     */
    PluginState getState();
    
    /**
     * Check if the plugin is compatible with the given engine version.
     * @param engineVersion Engine version to check compatibility with
     * @return true if compatible, false otherwise
     */
    boolean isCompatible(String engineVersion);
}