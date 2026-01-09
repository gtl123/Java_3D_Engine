package engine.plugins;

/**
 * Exception thrown when plugin operations fail.
 * Provides detailed error information for plugin-related failures.
 */
public class PluginException extends Exception {
    
    private final String pluginId;
    private final PluginState pluginState;
    private final ErrorType errorType;
    
    /**
     * Types of plugin errors.
     */
    public enum ErrorType {
        INITIALIZATION_FAILED,
        START_FAILED,
        STOP_FAILED,
        CLEANUP_FAILED,
        DEPENDENCY_MISSING,
        DEPENDENCY_CONFLICT,
        VERSION_INCOMPATIBLE,
        SECURITY_VIOLATION,
        RESOURCE_ACCESS_DENIED,
        INVALID_CONFIGURATION,
        LOADING_FAILED,
        UNKNOWN
    }
    
    public PluginException(String message) {
        super(message);
        this.pluginId = null;
        this.pluginState = null;
        this.errorType = ErrorType.UNKNOWN;
    }
    
    public PluginException(String message, Throwable cause) {
        super(message, cause);
        this.pluginId = null;
        this.pluginState = null;
        this.errorType = ErrorType.UNKNOWN;
    }
    
    public PluginException(String pluginId, PluginState pluginState, ErrorType errorType, String message) {
        super(message);
        this.pluginId = pluginId;
        this.pluginState = pluginState;
        this.errorType = errorType;
    }
    
    public PluginException(String pluginId, PluginState pluginState, ErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.pluginId = pluginId;
        this.pluginState = pluginState;
        this.errorType = errorType;
    }
    
    /**
     * Get the ID of the plugin that caused the exception.
     * @return Plugin ID, or null if not available
     */
    public String getPluginId() {
        return pluginId;
    }
    
    /**
     * Get the state of the plugin when the exception occurred.
     * @return Plugin state, or null if not available
     */
    public PluginState getPluginState() {
        return pluginState;
    }
    
    /**
     * Get the type of error that occurred.
     * @return Error type
     */
    public ErrorType getErrorType() {
        return errorType;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PluginException: ").append(getMessage());
        
        if (pluginId != null) {
            sb.append(" [Plugin: ").append(pluginId).append("]");
        }
        
        if (pluginState != null) {
            sb.append(" [State: ").append(pluginState).append("]");
        }
        
        if (errorType != null) {
            sb.append(" [Type: ").append(errorType).append("]");
        }
        
        return sb.toString();
    }
}