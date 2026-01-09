package engine.plugins;

import engine.assets.AssetManager;
import engine.config.ConfigurationManager;
import engine.gfx.RenderSystem;
import engine.logging.LogManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Default implementation of PluginContext.
 * Provides plugins with controlled access to engine systems and services.
 */
public class DefaultPluginContext implements PluginContext {
    
    private final Plugin plugin;
    private final PluginManager pluginManager;
    private final LogManager logManager;
    private final ConfigurationManager configurationManager;
    private final AssetManager assetManager;
    private final RenderSystem renderSystem;
    
    private final String pluginDataDirectory;
    private final String pluginConfigDirectory;
    private final ConcurrentHashMap<Class<?>, Object> services = new ConcurrentHashMap<>();
    private final ExecutorService asyncExecutor;
    
    public DefaultPluginContext(Plugin plugin, PluginManager pluginManager, 
                               ConfigurationManager configurationManager, 
                               AssetManager assetManager, RenderSystem renderSystem) {
        this.plugin = plugin;
        this.pluginManager = pluginManager;
        this.logManager = LogManager.getInstance();
        this.configurationManager = configurationManager;
        this.assetManager = assetManager;
        this.renderSystem = renderSystem;
        
        String pluginId = plugin.getMetadata().getId();
        this.pluginDataDirectory = "plugins/" + pluginId + "/data";
        this.pluginConfigDirectory = "plugins/" + pluginId + "/config";
        
        this.asyncExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "Plugin-" + pluginId + "-Async");
            t.setDaemon(true);
            return t;
        });
    }
    
    @Override
    public PluginManager getPluginManager() {
        return pluginManager;
    }
    
    @Override
    public LogManager getLogManager() {
        return logManager;
    }
    
    @Override
    public ConfigurationManager getConfigurationManager() {
        return configurationManager;
    }
    
    @Override
    public AssetManager getAssetManager() {
        return assetManager;
    }
    
    @Override
    public RenderSystem getRenderSystem() {
        return renderSystem;
    }
    
    @Override
    public EventBus getEventBus() {
        return pluginManager.getEventBus();
    }
    
    @Override
    public PluginRegistry getPluginRegistry() {
        return pluginManager.getPluginRegistry();
    }
    
    @Override
    public String getPluginDataDirectory() {
        return pluginDataDirectory;
    }
    
    @Override
    public String getPluginConfigDirectory() {
        return pluginConfigDirectory;
    }
    
    @Override
    public boolean hasPermission(String permission) {
        return plugin.getMetadata().hasPermission(permission);
    }
    
    @Override
    public void requestPermission(String permission) throws SecurityException {
        if (!hasPermission(permission)) {
            throw new SecurityException("Plugin does not have permission: " + permission);
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> serviceType) {
        return (T) services.get(serviceType);
    }
    
    @Override
    public <T> void registerService(Class<T> serviceType, T implementation) throws SecurityException {
        requestPermission("engine.services.register");
        services.put(serviceType, implementation);
        logManager.info("PluginContext", "Service registered", 
                       "pluginId", plugin.getMetadata().getId(),
                       "serviceType", serviceType.getSimpleName());
    }
    
    @Override
    public String getEngineVersion() {
        return pluginManager.getEngineVersion();
    }
    
    @Override
    public PluginMetadata getPluginMetadata() {
        return plugin.getMetadata();
    }
    
    @Override
    public void runOnMainThread(Runnable task) {
        // For now, run immediately - in a real implementation, this would queue to main thread
        task.run();
    }
    
    @Override
    public void runAsync(Runnable task) {
        asyncExecutor.submit(task);
    }
    
    @Override
    public <T> T getPluginConfig(String key, T defaultValue) {
        String configKey = "plugin." + plugin.getMetadata().getId() + "." + key;
        return configurationManager.getValue(configKey, defaultValue);
    }
    
    @Override
    public void setPluginConfig(String key, Object value) {
        String configKey = "plugin." + plugin.getMetadata().getId() + "." + key;
        configurationManager.setValue(configKey, value);
    }
    
    /**
     * Cleanup context resources.
     */
    public void cleanup() {
        asyncExecutor.shutdown();
        services.clear();
    }
}