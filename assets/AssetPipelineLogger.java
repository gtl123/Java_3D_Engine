package engine.assets;

import engine.logging.LogManager;
import engine.logging.MetricsCollector;
import engine.logging.PerformanceMonitor;

/**
 * Specialized logging utility for the Asset Pipeline.
 * Provides structured logging methods specifically designed for asset operations
 * with consistent formatting and metrics collection.
 */
public class AssetPipelineLogger {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = logManager.getMetricsCollector();
    private static final PerformanceMonitor performanceMonitor = logManager.getPerformanceMonitor();
    
    private static final String ASSET_COMPONENT_PREFIX = "Asset";
    
    // Asset Loading Events
    
    /**
     * Log asset loading started.
     */
    public static void logAssetLoadStarted(String assetId, String path, AssetType type) {
        logManager.info(ASSET_COMPONENT_PREFIX + "Loader", "Asset loading started",
                       "assetId", assetId,
                       "path", path,
                       "type", type.getTypeName(),
                       "operation", "load_start");
        
        metricsCollector.incrementCounter("asset.load.started");
        metricsCollector.incrementCounter("asset.load.started." + type.getTypeName().toLowerCase());
    }
    
    /**
     * Log asset loading completed successfully.
     */
    public static void logAssetLoadCompleted(String assetId, String path, AssetType type, long loadTimeMs, long sizeBytes) {
        logManager.info(ASSET_COMPONENT_PREFIX + "Loader", "Asset loading completed",
                       "assetId", assetId,
                       "path", path,
                       "type", type.getTypeName(),
                       "loadTime", loadTimeMs,
                       "size", sizeBytes,
                       "operation", "load_complete");
        
        metricsCollector.incrementCounter("asset.load.completed");
        metricsCollector.incrementCounter("asset.load.completed." + type.getTypeName().toLowerCase());
        metricsCollector.recordValue("asset.load.time", loadTimeMs);
        metricsCollector.recordValue("asset.load.size", sizeBytes);
        
        performanceMonitor.recordOperation("asset.load." + type.getTypeName().toLowerCase(), loadTimeMs);
    }
    
    /**
     * Log asset loading failed.
     */
    public static void logAssetLoadFailed(String assetId, String path, AssetType type, String error, Throwable throwable) {
        logManager.error(ASSET_COMPONENT_PREFIX + "Loader", "Asset loading failed",
                        throwable,
                        "assetId", assetId,
                        "path", path,
                        "type", type.getTypeName(),
                        "error", error,
                        "operation", "load_failed");
        
        metricsCollector.incrementCounter("asset.load.failed");
        metricsCollector.incrementCounter("asset.load.failed." + type.getTypeName().toLowerCase());
    }
    
    // Asset Caching Events
    
    /**
     * Log cache hit.
     */
    public static void logCacheHit(String assetId, AssetType type) {
        logManager.debug(ASSET_COMPONENT_PREFIX + "Cache", "Cache hit",
                        "assetId", assetId,
                        "type", type.getTypeName(),
                        "operation", "cache_hit");
        
        metricsCollector.incrementCounter("asset.cache.hit");
        metricsCollector.incrementCounter("asset.cache.hit." + type.getTypeName().toLowerCase());
    }
    
    /**
     * Log cache miss.
     */
    public static void logCacheMiss(String assetId, AssetType type) {
        logManager.debug(ASSET_COMPONENT_PREFIX + "Cache", "Cache miss",
                        "assetId", assetId,
                        "type", type.getTypeName(),
                        "operation", "cache_miss");
        
        metricsCollector.incrementCounter("asset.cache.miss");
        metricsCollector.incrementCounter("asset.cache.miss." + type.getTypeName().toLowerCase());
    }
    
    /**
     * Log cache eviction.
     */
    public static void logCacheEviction(String assetId, AssetType type, String reason, long sizeBytes) {
        logManager.debug(ASSET_COMPONENT_PREFIX + "Cache", "Asset evicted from cache",
                        "assetId", assetId,
                        "type", type.getTypeName(),
                        "reason", reason,
                        "size", sizeBytes,
                        "operation", "cache_evict");
        
        metricsCollector.incrementCounter("asset.cache.evicted");
        metricsCollector.incrementCounter("asset.cache.evicted." + type.getTypeName().toLowerCase());
    }
    
    // Asset Streaming Events
    
    /**
     * Log streaming started.
     */
    public static void logStreamingStarted(String assetId, String path, long totalSize) {
        logManager.info(ASSET_COMPONENT_PREFIX + "Streamer", "Asset streaming started",
                       "assetId", assetId,
                       "path", path,
                       "totalSize", totalSize,
                       "operation", "stream_start");
        
        metricsCollector.incrementCounter("asset.stream.started");
    }
    
    /**
     * Log streaming progress.
     */
    public static void logStreamingProgress(String assetId, int chunkIndex, long bytesReceived, long totalSize, float progress) {
        logManager.debug(ASSET_COMPONENT_PREFIX + "Streamer", "Streaming progress",
                        "assetId", assetId,
                        "chunkIndex", chunkIndex,
                        "bytesReceived", bytesReceived,
                        "totalSize", totalSize,
                        "progress", progress,
                        "operation", "stream_progress");
        
        metricsCollector.recordValue("asset.stream.progress", progress);
    }
    
    /**
     * Log streaming completed.
     */
    public static void logStreamingCompleted(String assetId, long totalSize, long streamTimeMs) {
        logManager.info(ASSET_COMPONENT_PREFIX + "Streamer", "Asset streaming completed",
                       "assetId", assetId,
                       "totalSize", totalSize,
                       "streamTime", streamTimeMs,
                       "operation", "stream_complete");
        
        metricsCollector.incrementCounter("asset.stream.completed");
        metricsCollector.recordValue("asset.stream.time", streamTimeMs);
        performanceMonitor.recordOperation("asset.stream", streamTimeMs);
    }
    
    /**
     * Log streaming failed.
     */
    public static void logStreamingFailed(String assetId, String error, Throwable throwable) {
        logManager.error(ASSET_COMPONENT_PREFIX + "Streamer", "Asset streaming failed",
                        throwable,
                        "assetId", assetId,
                        "error", error,
                        "operation", "stream_failed");
        
        metricsCollector.incrementCounter("asset.stream.failed");
    }
    
    // Asset Compression Events
    
    /**
     * Log compression started.
     */
    public static void logCompressionStarted(String assetId, String algorithm, long originalSize) {
        logManager.debug(ASSET_COMPONENT_PREFIX + "Compressor", "Asset compression started",
                        "assetId", assetId,
                        "algorithm", algorithm,
                        "originalSize", originalSize,
                        "operation", "compress_start");
        
        metricsCollector.incrementCounter("asset.compression.started");
        metricsCollector.incrementCounter("asset.compression.started." + algorithm.toLowerCase());
    }
    
    /**
     * Log compression completed.
     */
    public static void logCompressionCompleted(String assetId, String algorithm, long originalSize, 
                                             long compressedSize, long compressionTimeMs) {
        float compressionRatio = (float) compressedSize / originalSize;
        
        logManager.debug(ASSET_COMPONENT_PREFIX + "Compressor", "Asset compression completed",
                        "assetId", assetId,
                        "algorithm", algorithm,
                        "originalSize", originalSize,
                        "compressedSize", compressedSize,
                        "compressionRatio", compressionRatio,
                        "compressionTime", compressionTimeMs,
                        "operation", "compress_complete");
        
        metricsCollector.incrementCounter("asset.compression.completed");
        metricsCollector.incrementCounter("asset.compression.completed." + algorithm.toLowerCase());
        metricsCollector.recordValue("asset.compression.ratio", compressionRatio);
        metricsCollector.recordValue("asset.compression.time", compressionTimeMs);
        performanceMonitor.recordOperation("asset.compression." + algorithm.toLowerCase(), compressionTimeMs);
    }
    
    /**
     * Log decompression completed.
     */
    public static void logDecompressionCompleted(String assetId, String algorithm, long compressedSize, 
                                               long decompressedSize, long decompressionTimeMs) {
        logManager.debug(ASSET_COMPONENT_PREFIX + "Compressor", "Asset decompression completed",
                        "assetId", assetId,
                        "algorithm", algorithm,
                        "compressedSize", compressedSize,
                        "decompressedSize", decompressedSize,
                        "decompressionTime", decompressionTimeMs,
                        "operation", "decompress_complete");
        
        metricsCollector.incrementCounter("asset.decompression.completed");
        metricsCollector.incrementCounter("asset.decompression.completed." + algorithm.toLowerCase());
        metricsCollector.recordValue("asset.decompression.time", decompressionTimeMs);
        performanceMonitor.recordOperation("asset.decompression." + algorithm.toLowerCase(), decompressionTimeMs);
    }
    
    // Hot Reload Events
    
    /**
     * Log hot reload triggered.
     */
    public static void logHotReloadTriggered(String assetId, String path, String changeType) {
        logManager.info(ASSET_COMPONENT_PREFIX + "Manager", "Hot reload triggered",
                       "assetId", assetId,
                       "path", path,
                       "changeType", changeType,
                       "operation", "hotreload_trigger");
        
        metricsCollector.incrementCounter("asset.hotreload.triggered");
    }
    
    /**
     * Log hot reload completed.
     */
    public static void logHotReloadCompleted(String assetId, long reloadTimeMs) {
        logManager.info(ASSET_COMPONENT_PREFIX + "Manager", "Hot reload completed",
                       "assetId", assetId,
                       "reloadTime", reloadTimeMs,
                       "operation", "hotreload_complete");
        
        metricsCollector.incrementCounter("asset.hotreload.completed");
        metricsCollector.recordValue("asset.hotreload.time", reloadTimeMs);
        performanceMonitor.recordOperation("asset.hotreload", reloadTimeMs);
    }
    
    // Asset Manager Events
    
    /**
     * Log asset manager initialization.
     */
    public static void logAssetManagerInitialized(String assetRootPath, boolean hotReload, 
                                                 long cacheMaxSize, int loaderThreads) {
        logManager.info(ASSET_COMPONENT_PREFIX + "Manager", "Asset manager initialized",
                       "assetRootPath", assetRootPath,
                       "hotReload", hotReload,
                       "cacheMaxSize", cacheMaxSize,
                       "loaderThreads", loaderThreads,
                       "operation", "manager_init");
        
        metricsCollector.incrementCounter("asset.manager.initialized");
    }
    
    /**
     * Log asset factory registration.
     */
    public static void logAssetFactoryRegistered(AssetType type, String factoryClass) {
        logManager.info(ASSET_COMPONENT_PREFIX + "Manager", "Asset factory registered",
                       "type", type.getTypeName(),
                       "factoryClass", factoryClass,
                       "operation", "factory_register");
        
        metricsCollector.incrementCounter("asset.factory.registered");
    }
    
    /**
     * Log asset disposal.
     */
    public static void logAssetDisposed(String assetId, AssetType type) {
        logManager.debug(ASSET_COMPONENT_PREFIX + "Manager", "Asset disposed",
                        "assetId", assetId,
                        "type", type.getTypeName(),
                        "operation", "asset_dispose");
        
        metricsCollector.incrementCounter("asset.disposed");
        metricsCollector.incrementCounter("asset.disposed." + type.getTypeName().toLowerCase());
    }
    
    // Performance and Statistics
    
    /**
     * Log asset pipeline statistics.
     */
    public static void logAssetPipelineStatistics(int loadedAssets, long totalLoaded, long totalFailed, 
                                                 long totalHotReloads, long cacheHits, long cacheMisses) {
        logManager.info(ASSET_COMPONENT_PREFIX + "Manager", "Asset pipeline statistics",
                       "loadedAssets", loadedAssets,
                       "totalLoaded", totalLoaded,
                       "totalFailed", totalFailed,
                       "totalHotReloads", totalHotReloads,
                       "cacheHits", cacheHits,
                       "cacheMisses", cacheMisses,
                       "operation", "statistics");
    }
    
    /**
     * Log memory usage statistics.
     */
    public static void logMemoryStatistics(long totalMemoryUsed, long cacheMemoryUsed, int activeAssets) {
        logManager.debug(ASSET_COMPONENT_PREFIX + "Manager", "Memory statistics",
                        "totalMemoryUsed", totalMemoryUsed,
                        "cacheMemoryUsed", cacheMemoryUsed,
                        "activeAssets", activeAssets,
                        "operation", "memory_stats");
        
        metricsCollector.recordValue("asset.memory.total", totalMemoryUsed);
        metricsCollector.recordValue("asset.memory.cache", cacheMemoryUsed);
        metricsCollector.recordValue("asset.active.count", activeAssets);
    }
    
    // Error and Warning Events
    
    /**
     * Log asset validation warning.
     */
    public static void logAssetValidationWarning(String assetId, String path, String warning) {
        logManager.warn(ASSET_COMPONENT_PREFIX + "Validator", "Asset validation warning",
                       "assetId", assetId,
                       "path", path,
                       "warning", warning,
                       "operation", "validation_warning");
        
        metricsCollector.incrementCounter("asset.validation.warnings");
    }
    
    /**
     * Log asset validation error.
     */
    public static void logAssetValidationError(String assetId, String path, String error) {
        logManager.error(ASSET_COMPONENT_PREFIX + "Validator", "Asset validation error",
                        "assetId", assetId,
                        "path", path,
                        "error", error,
                        "operation", "validation_error");
        
        metricsCollector.incrementCounter("asset.validation.errors");
    }
    
    /**
     * Log dependency resolution warning.
     */
    public static void logDependencyWarning(String assetId, String dependency, String warning) {
        logManager.warn(ASSET_COMPONENT_PREFIX + "DependencyGraph", "Dependency resolution warning",
                       "assetId", assetId,
                       "dependency", dependency,
                       "warning", warning,
                       "operation", "dependency_warning");
        
        metricsCollector.incrementCounter("asset.dependency.warnings");
    }
    
    /**
     * Log circular dependency detected.
     */
    public static void logCircularDependency(String assetId, String[] dependencyChain) {
        logManager.error(ASSET_COMPONENT_PREFIX + "DependencyGraph", "Circular dependency detected",
                        "assetId", assetId,
                        "dependencyChain", String.join(" -> ", dependencyChain),
                        "operation", "circular_dependency");
        
        metricsCollector.incrementCounter("asset.dependency.circular");
    }
}