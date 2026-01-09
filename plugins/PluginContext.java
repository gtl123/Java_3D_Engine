package engine.plugins;

import engine.assets.AssetManager;
import engine.config.ConfigurationManager;
import engine.gfx.RenderSystem;
import engine.logging.LogManager;

/**
 * Provides plugins with controlled access to engine systems and services.
 * Acts as a service locator and security boundary for plugin operations.
 */
public interface PluginContext {
    
    /**
     * Get the plugin manager instance.
     * @return Plugin manager
     */
    PluginManager getPluginManager();
    
    /**
     * Get the logging manager for plugin logging.
     * @return Log manager
     */
    LogManager getLogManager();
    
    /**
     * Get the configuration manager for plugin configuration.
     * @return Configuration manager
     */
    ConfigurationManager getConfigurationManager();
    
    /**
     * Get the asset manager for loading plugin assets.
     * @return Asset manager
     */
    AssetManager getAssetManager();
    
    /**
     * Get the render system for rendering operations.
     * @return Render system
     */
    RenderSystem getRenderSystem();
    
    /**
     * Get the event bus for inter-plugin communication.
     * @return Event bus
     */
    EventBus getEventBus();
    
    /**
     * Get the plugin registry for discovering other plugins.
     * @return Plugin registry
     */
    PluginRegistry getPluginRegistry();
    
    /**
     * Get the plugin's data directory for storing plugin-specific files.
     * @return Plugin data directory path
     */
    String getPluginDataDirectory();
    
    /**
     * Get the plugin's configuration directory.
     * @return Plugin config directory path
     */
    String getPluginConfigDirectory();
    
    /**
     * Check if the plugin has a specific permission.
     * @param permission Permission to check
     * @return true if plugin has permission
     */
    boolean hasPermission(String permission);
    
    /**
     * Request a permission from the security manager.
     * @param permission Permission to request
     * @throws SecurityException if permission is denied
     */
    void requestPermission(String permission) throws SecurityException;
    
    /**
     * Get a service by type if available and permitted.
     * @param serviceType Service class type
     * @return Service instance or null if not available
     */
    <T> T getService(Class<T> serviceType);
    
    /**
     * Register a service that other plugins can access.
     * @param serviceType Service interface type
     * @param implementation Service implementation
     * @throws SecurityException if plugin doesn't have permission to register services
     */
    <T> void registerService(Class<T> serviceType, T implementation) throws SecurityException;
    
    /**
     * Get the engine version.
     * @return Engine version string
     */
    String getEngineVersion();
    
    /**
     * Get the current plugin's metadata.
     * @return Plugin metadata
     */
    PluginMetadata getPluginMetadata();
    
    /**
     * Schedule a task to run on the main engine thread.
     * @param task Task to execute
     */
    void runOnMainThread(Runnable task);
    
    /**
     * Schedule a task to run asynchronously.
     * @param task Task to execute
     */
    void runAsync(Runnable task);
    
    /**
     * Get a configuration value specific to this plugin.
     * @param key Configuration key
     * @param defaultValue Default value if not found
     * @return Configuration value
     */
    <T> T getPluginConfig(String key, T defaultValue);
    
    /**
     * Set a configuration value specific to this plugin.
     * @param key Configuration key
     * @param value Configuration value
     */
    void setPluginConfig(String key, Object value);
}