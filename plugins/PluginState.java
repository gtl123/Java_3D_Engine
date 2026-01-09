package engine.plugins;

/**
 * Represents the current state of a plugin in its lifecycle.
 */
public enum PluginState {
    /**
     * Plugin is loaded but not yet initialized.
     */
    LOADED,
    
    /**
     * Plugin is initialized but not yet started.
     */
    INITIALIZED,
    
    /**
     * Plugin is running and active.
     */
    STARTED,
    
    /**
     * Plugin is stopped but not yet cleaned up.
     */
    STOPPED,
    
    /**
     * Plugin has been cleaned up and unloaded.
     */
    UNLOADED,
    
    /**
     * Plugin encountered an error and is in a failed state.
     */
    FAILED;
    
    /**
     * Check if the plugin is in an active state (initialized or started).
     * @return true if plugin is active
     */
    public boolean isActive() {
        return this == INITIALIZED || this == STARTED;
    }
    
    /**
     * Check if the plugin can be started from its current state.
     * @return true if plugin can be started
     */
    public boolean canStart() {
        return this == LOADED || this == INITIALIZED || this == STOPPED;
    }
    
    /**
     * Check if the plugin can be stopped from its current state.
     * @return true if plugin can be stopped
     */
    public boolean canStop() {
        return this == STARTED;
    }
}