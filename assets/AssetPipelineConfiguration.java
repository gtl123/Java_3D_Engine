package engine.assets;

import engine.config.ConfigurationManager;
import engine.logging.LogManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration integration for the Asset Pipeline.
 * Provides centralized configuration management for all asset pipeline components
 * using the existing ConfigurationManager infrastructure.
 */
public class AssetPipelineConfiguration {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static volatile AssetPipelineConfiguration instance;
    
    private final ConfigurationManager configManager;
    private final Map<String, Object> defaultValues = new HashMap<>();
    
    // Configuration key prefixes
    private static final String ASSET_PREFIX = "engine.assets";
    private static final String CACHE_PREFIX = ASSET_PREFIX + ".cache";
    private static final String LOADER_PREFIX = ASSET_PREFIX + ".loader";
    private static final String STREAMER_PREFIX = ASSET_PREFIX + ".streamer";
    private static final String COMPRESSOR_PREFIX = ASSET_PREFIX + ".compressor";
    
    private AssetPipelineConfiguration() {
        this.configManager = ConfigurationManager.getInstance();
        setupDefaultConfiguration();
    }
    
    /**
     * Get the singleton instance.
     */
    public static AssetPipelineConfiguration getInstance() {
        if (instance == null) {
            synchronized (AssetPipelineConfiguration.class) {
                if (instance == null) {
                    instance = new AssetPipelineConfiguration();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize asset pipeline configuration.
     */
    public void initialize() {
        logManager.info("AssetPipelineConfiguration", "Initializing asset pipeline configuration");
        
        // Ensure ConfigurationManager is initialized
        if (!configManager.isInitialized()) {
            configManager.initialize();
        }
        
        // Set default values if not already configured
        for (Map.Entry<String, Object> entry : defaultValues.entrySet()) {
            if (!hasValue(entry.getKey())) {
                configManager.setValue(entry.getKey(), entry.getValue());
            }
        }
        
        logManager.info("AssetPipelineConfiguration", "Asset pipeline configuration initialized");
    }
    
    // Asset Manager Configuration
    
    public boolean isHotReloadEnabled() {
        return getValue(ASSET_PREFIX + ".hotReload.enabled", true);
    }
    
    public long getHotReloadCheckInterval() {
        return getValue(ASSET_PREFIX + ".hotReload.checkInterval", 1000L);
    }
    
    public String getAssetRootPath() {
        return getValue(ASSET_PREFIX + ".rootPath", "assets/");
    }
    
    public boolean isAssetValidationEnabled() {
        return getValue(ASSET_PREFIX + ".validation.enabled", true);
    }
    
    public boolean isAssetMetricsEnabled() {
        return getValue(ASSET_PREFIX + ".metrics.enabled", true);
    }
    
    // Cache Configuration
    
    public long getCacheMaxSize() {
        return getValue(CACHE_PREFIX + ".maxSize", 512L * 1024 * 1024); // 512MB default
    }
    
    public int getCacheMaxEntries() {
        return getValue(CACHE_PREFIX + ".maxEntries", 1000);
    }
    
    public long getCacheEvictionInterval() {
        return getValue(CACHE_PREFIX + ".evictionInterval", 30000L); // 30 seconds
    }
    
    public float getCacheEvictionThreshold() {
        return getValue(CACHE_PREFIX + ".evictionThreshold", 0.8f);
    }
    
    public boolean isCacheCompressionEnabled() {
        return getValue(CACHE_PREFIX + ".compression.enabled", false);
    }
    
    public String getCacheCompressionAlgorithm() {
        return getValue(CACHE_PREFIX + ".compression.algorithm", "GZIP");
    }
    
    // Loader Configuration
    
    public int getLoaderThreadPoolSize() {
        return getValue(LOADER_PREFIX + ".threadPool.size", Runtime.getRuntime().availableProcessors());
    }
    
    public int getLoaderThreadPoolMaxSize() {
        return getValue(LOADER_PREFIX + ".threadPool.maxSize", Runtime.getRuntime().availableProcessors() * 2);
    }
    
    public long getLoaderThreadKeepAlive() {
        return getValue(LOADER_PREFIX + ".threadPool.keepAlive", 60000L); // 60 seconds
    }
    
    public int getLoaderQueueCapacity() {
        return getValue(LOADER_PREFIX + ".queue.capacity", 1000);
    }
    
    public long getLoaderTimeout() {
        return getValue(LOADER_PREFIX + ".timeout", 30000L); // 30 seconds
    }
    
    public boolean isLoaderRetryEnabled() {
        return getValue(LOADER_PREFIX + ".retry.enabled", true);
    }
    
    public int getLoaderMaxRetries() {
        return getValue(LOADER_PREFIX + ".retry.maxAttempts", 3);
    }
    
    public long getLoaderRetryDelay() {
        return getValue(LOADER_PREFIX + ".retry.delay", 1000L);
    }
    
    // Streamer Configuration
    
    public int getStreamerChunkSize() {
        return getValue(STREAMER_PREFIX + ".chunkSize", 64 * 1024); // 64KB
    }
    
    public int getStreamerBufferSize() {
        return getValue(STREAMER_PREFIX + ".bufferSize", 1024 * 1024); // 1MB
    }
    
    public long getStreamerBandwidthLimit() {
        return getValue(STREAMER_PREFIX + ".bandwidthLimit", 0L); // 0 = unlimited
    }
    
    public int getStreamerMaxConcurrentStreams() {
        return getValue(STREAMER_PREFIX + ".maxConcurrentStreams", 10);
    }
    
    public boolean isStreamerProgressCallbackEnabled() {
        return getValue(STREAMER_PREFIX + ".progressCallback.enabled", true);
    }
    
    public long getStreamerProgressCallbackInterval() {
        return getValue(STREAMER_PREFIX + ".progressCallback.interval", 100L);
    }
    
    // Compressor Configuration
    
    public boolean isCompressionEnabled() {
        return getValue(COMPRESSOR_PREFIX + ".enabled", true);
    }
    
    public String getDefaultCompressionAlgorithm() {
        return getValue(COMPRESSOR_PREFIX + ".defaultAlgorithm", "GZIP");
    }
    
    public int getCompressionLevel() {
        return getValue(COMPRESSOR_PREFIX + ".level", 6);
    }
    
    public int getCompressionThreadPoolSize() {
        return getValue(COMPRESSOR_PREFIX + ".threadPool.size", 2);
    }
    
    public long getCompressionTimeout() {
        return getValue(COMPRESSOR_PREFIX + ".timeout", 10000L); // 10 seconds
    }
    
    public boolean isAsyncCompressionEnabled() {
        return getValue(COMPRESSOR_PREFIX + ".async.enabled", true);
    }
    
    // Asset Type Specific Configuration
    
    // Texture Configuration
    public boolean isTextureCompressionEnabled() {
        return getValue(ASSET_PREFIX + ".texture.compression.enabled", true);
    }
    
    public String getTextureCompressionFormat() {
        return getValue(ASSET_PREFIX + ".texture.compression.format", "DXT5");
    }
    
    public boolean isTextureMipmapGenerationEnabled() {
        return getValue(ASSET_PREFIX + ".texture.mipmap.enabled", true);
    }
    
    public String getTextureFilterMode() {
        return getValue(ASSET_PREFIX + ".texture.filter.mode", "LINEAR");
    }
    
    public String getTextureWrapMode() {
        return getValue(ASSET_PREFIX + ".texture.wrap.mode", "REPEAT");
    }
    
    // Model Configuration
    public boolean isModelOptimizationEnabled() {
        return getValue(ASSET_PREFIX + ".model.optimization.enabled", true);
    }
    
    public boolean isModelAnimationLoadingEnabled() {
        return getValue(ASSET_PREFIX + ".model.animation.enabled", true);
    }
    
    public boolean isModelMaterialLoadingEnabled() {
        return getValue(ASSET_PREFIX + ".model.material.enabled", true);
    }
    
    public int getModelAssimpFlags() {
        return getValue(ASSET_PREFIX + ".model.assimp.flags", 0x8 | 0x40 | 0x100); // Triangulate | FlipUVs | CalcTangentSpace
    }
    
    // Shader Configuration
    public boolean isShaderHotReloadEnabled() {
        return getValue(ASSET_PREFIX + ".shader.hotReload.enabled", true);
    }
    
    public boolean isShaderPreprocessingEnabled() {
        return getValue(ASSET_PREFIX + ".shader.preprocessing.enabled", true);
    }
    
    public int getShaderGLSLVersion() {
        return getValue(ASSET_PREFIX + ".shader.glsl.version", 330);
    }
    
    // Audio Configuration
    public boolean isAudioStreamingEnabled() {
        return getValue(ASSET_PREFIX + ".audio.streaming.enabled", true);
    }
    
    public int getAudioStreamingThreshold() {
        return getValue(ASSET_PREFIX + ".audio.streaming.threshold", 1024 * 1024); // 1MB
    }
    
    public int getAudioBufferCount() {
        return getValue(ASSET_PREFIX + ".audio.buffer.count", 4);
    }
    
    public int getAudioBufferSize() {
        return getValue(ASSET_PREFIX + ".audio.buffer.size", 4096);
    }
    
    // Configuration Management
    
    /**
     * Get a configuration value with type safety.
     */
    @SuppressWarnings("unchecked")
    private <T> T getValue(String key, T defaultValue) {
        Optional<T> value = configManager.getValue(key);
        return value.orElse(defaultValue);
    }
    
    /**
     * Check if a configuration value exists.
     */
    private boolean hasValue(String key) {
        return configManager.getValue(key).isPresent();
    }
    
    /**
     * Set a configuration value.
     */
    public void setValue(String key, Object value) {
        configManager.setValue(key, value);
        logManager.debug("AssetPipelineConfiguration", "Configuration value updated", "key", key, "value", value);
    }
    
    /**
     * Get all asset pipeline configuration values.
     */
    public Map<String, Object> getAllAssetConfiguration() {
        Map<String, Object> allConfig = configManager.getAllValues();
        Map<String, Object> assetConfig = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : allConfig.entrySet()) {
            if (entry.getKey().startsWith(ASSET_PREFIX)) {
                assetConfig.put(entry.getKey(), entry.getValue());
            }
        }
        
        return assetConfig;
    }
    
    /**
     * Setup default configuration values.
     */
    private void setupDefaultConfiguration() {
        // Asset Manager defaults
        defaultValues.put(ASSET_PREFIX + ".hotReload.enabled", true);
        defaultValues.put(ASSET_PREFIX + ".hotReload.checkInterval", 1000L);
        defaultValues.put(ASSET_PREFIX + ".rootPath", "assets/");
        defaultValues.put(ASSET_PREFIX + ".validation.enabled", true);
        defaultValues.put(ASSET_PREFIX + ".metrics.enabled", true);
        
        // Cache defaults
        defaultValues.put(CACHE_PREFIX + ".maxSize", 512L * 1024 * 1024); // 512MB
        defaultValues.put(CACHE_PREFIX + ".maxEntries", 1000);
        defaultValues.put(CACHE_PREFIX + ".evictionInterval", 30000L);
        defaultValues.put(CACHE_PREFIX + ".evictionThreshold", 0.8f);
        defaultValues.put(CACHE_PREFIX + ".compression.enabled", false);
        defaultValues.put(CACHE_PREFIX + ".compression.algorithm", "GZIP");
        
        // Loader defaults
        defaultValues.put(LOADER_PREFIX + ".threadPool.size", Runtime.getRuntime().availableProcessors());
        defaultValues.put(LOADER_PREFIX + ".threadPool.maxSize", Runtime.getRuntime().availableProcessors() * 2);
        defaultValues.put(LOADER_PREFIX + ".threadPool.keepAlive", 60000L);
        defaultValues.put(LOADER_PREFIX + ".queue.capacity", 1000);
        defaultValues.put(LOADER_PREFIX + ".timeout", 30000L);
        defaultValues.put(LOADER_PREFIX + ".retry.enabled", true);
        defaultValues.put(LOADER_PREFIX + ".retry.maxAttempts", 3);
        defaultValues.put(LOADER_PREFIX + ".retry.delay", 1000L);
        
        // Streamer defaults
        defaultValues.put(STREAMER_PREFIX + ".chunkSize", 64 * 1024);
        defaultValues.put(STREAMER_PREFIX + ".bufferSize", 1024 * 1024);
        defaultValues.put(STREAMER_PREFIX + ".bandwidthLimit", 0L);
        defaultValues.put(STREAMER_PREFIX + ".maxConcurrentStreams", 10);
        defaultValues.put(STREAMER_PREFIX + ".progressCallback.enabled", true);
        defaultValues.put(STREAMER_PREFIX + ".progressCallback.interval", 100L);
        
        // Compressor defaults
        defaultValues.put(COMPRESSOR_PREFIX + ".enabled", true);
        defaultValues.put(COMPRESSOR_PREFIX + ".defaultAlgorithm", "GZIP");
        defaultValues.put(COMPRESSOR_PREFIX + ".level", 6);
        defaultValues.put(COMPRESSOR_PREFIX + ".threadPool.size", 2);
        defaultValues.put(COMPRESSOR_PREFIX + ".timeout", 10000L);
        defaultValues.put(COMPRESSOR_PREFIX + ".async.enabled", true);
        
        // Asset type defaults
        defaultValues.put(ASSET_PREFIX + ".texture.compression.enabled", true);
        defaultValues.put(ASSET_PREFIX + ".texture.compression.format", "DXT5");
        defaultValues.put(ASSET_PREFIX + ".texture.mipmap.enabled", true);
        defaultValues.put(ASSET_PREFIX + ".texture.filter.mode", "LINEAR");
        defaultValues.put(ASSET_PREFIX + ".texture.wrap.mode", "REPEAT");
        
        defaultValues.put(ASSET_PREFIX + ".model.optimization.enabled", true);
        defaultValues.put(ASSET_PREFIX + ".model.animation.enabled", true);
        defaultValues.put(ASSET_PREFIX + ".model.material.enabled", true);
        defaultValues.put(ASSET_PREFIX + ".model.assimp.flags", 0x8 | 0x40 | 0x100);
        
        defaultValues.put(ASSET_PREFIX + ".shader.hotReload.enabled", true);
        defaultValues.put(ASSET_PREFIX + ".shader.preprocessing.enabled", true);
        defaultValues.put(ASSET_PREFIX + ".shader.glsl.version", 330);
        
        defaultValues.put(ASSET_PREFIX + ".audio.streaming.enabled", true);
        defaultValues.put(ASSET_PREFIX + ".audio.streaming.threshold", 1024 * 1024);
        defaultValues.put(ASSET_PREFIX + ".audio.buffer.count", 4);
        defaultValues.put(ASSET_PREFIX + ".audio.buffer.size", 4096);
        
        logManager.debug("AssetPipelineConfiguration", "Default configuration values setup", "count", defaultValues.size());
    }
    
    /**
     * Reload configuration from the ConfigurationManager.
     */
    public void reloadConfiguration() {
        configManager.reloadConfiguration();
        logManager.info("AssetPipelineConfiguration", "Asset pipeline configuration reloaded");
    }
    
    /**
     * Get the underlying ConfigurationManager instance.
     */
    public ConfigurationManager getConfigurationManager() {
        return configManager;
    }
    
    @Override
    public String toString() {
        return String.format("AssetPipelineConfiguration{configCount=%d, hotReload=%s, cacheSize=%d}",
                           getAllAssetConfiguration().size(),
                           isHotReloadEnabled(),
                           getCacheMaxSize());
    }
}