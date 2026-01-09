package engine.config;

import java.util.Map;
import java.util.Optional;

/**
 * Interface for configuration sources that provide key-value configuration data.
 * Supports multiple configuration sources like files, environment variables, and system properties.
 */
public interface ConfigSource {
    
    /**
     * Get the name of this configuration source.
     * @return The source name
     */
    String getName();
    
    /**
     * Get the priority of this configuration source.
     * Higher priority sources override lower priority ones.
     * @return The priority (higher number = higher priority)
     */
    int getPriority();
    
    /**
     * Get a configuration value by key.
     * @param key The configuration key
     * @return The value if present, empty otherwise
     */
    Optional<String> getValue(String key);
    
    /**
     * Get all configuration values as a map.
     * @return Map of all key-value pairs
     */
    Map<String, String> getAllValues();
    
    /**
     * Check if this configuration source is available and can be read.
     * @return true if available, false otherwise
     */
    boolean isAvailable();
    
    /**
     * Reload the configuration from the source.
     * @return true if reload was successful, false otherwise
     */
    boolean reload();
    
    /**
     * Check if this configuration source supports hot reloading.
     * @return true if hot reload is supported
     */
    boolean supportsHotReload();
    
    /**
     * Get the last modified timestamp of the configuration source.
     * @return Last modified time in milliseconds, or -1 if not supported
     */
    long getLastModified();
}