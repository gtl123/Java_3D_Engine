package engine.assets;

import engine.logging.LogManager;
import engine.logging.MetricsCollector;
import engine.logging.PerformanceMonitor;

import java.io.File;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

/**
 * Enhanced Asset Manager with integrated configuration management.
 * This version integrates with AssetPipelineConfiguration for centralized configuration.
 */
public class AssetManagerV2 {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = LogManager.getInstance().getMetricsCollector();
    private static final PerformanceMonitor performanceMonitor = LogManager.getInstance().getPerformanceMonitor();
    
    private static volatile AssetManagerV2 instance;
    
    // Configuration
    private final AssetPipelineConfiguration config;
    
    // Core components
    private final AssetLoader loader;
    private final AssetCache cache;
    private final AssetStreamer streamer;
    private final AssetCompressor compressor;
    private final AssetDependencyGraph dependencyGraph;
    
    // Asset factories
    private final ConcurrentHashMap<AssetType, AssetLoader.AssetFactory> factories;
    
    // Asset registry and tracking
    private final ConcurrentHashMap<String, Asset> loadedAssets;
    private final ConcurrentHashMap<String, AssetMetadata> assetMetadata;
    private final ConcurrentHashMap<String, String> pathToIdMapping;
    
    // Hot reloading
    private final WatchService fileWatcher;
    private final ConcurrentHashMap<WatchKey, Path> watchedPaths;
    private final ExecutorService hotReloadExecutor;
    private final AtomicBoolean hotReloadEnabled = new AtomicBoolean(false);
    
    // Statistics
    private final AtomicLong totalAssetsLoaded = new AtomicLong(0);
    private final AtomicLong totalAssetsFailed = new AtomicLong(0);
    private final AtomicLong totalHotReloads = new AtomicLong(0);
    
    /**
     * Get the singleton instance of AssetManagerV2.
     */
    public static AssetManagerV2 getInstance() {
        if (instance == null) {
            synchronized (AssetManagerV2.class) {
                if (instance == null) {
                    try {
                        instance = new AssetManagerV2();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to initialize AssetManagerV2", e);
                    }
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize AssetManagerV2 with configuration integration.
     */
    public static synchronized void initialize() throws Exception {
        if (instance != null) {
            logManager.warn("AssetManagerV2", "AssetManagerV2 already initialized, shutting down existing instance");
            instance.shutdown();
        }
        instance = new AssetManagerV2();
        instance.initializeComponents();
    }
    
    /**
     * Create AssetManagerV2 with configuration integration.
     */
    private AssetManagerV2() throws Exception {
        // Initialize configuration first
        this.config = AssetPipelineConfiguration.getInstance();
        config.initialize();
        
        // Initialize core components with configuration
        this.cache = new AssetCache(config);
        this.loader = new AssetLoader(config);
        this.streamer = new AssetStreamer(config);
        this.compressor = new AssetCompressor(config);
        this.dependencyGraph = new AssetDependencyGraph();
        
        // Initialize data structures
        this.factories = new ConcurrentHashMap<>();
        this.loadedAssets = new ConcurrentHashMap<>();
        this.assetMetadata = new ConcurrentHashMap<>();
        this.pathToIdMapping = new ConcurrentHashMap<>();
        this.watchedPaths = new ConcurrentHashMap<>();
        
        // Initialize file watching for hot reload
        this.fileWatcher = config.isHotReloadEnabled() ? FileSystems.getDefault().newWatchService() : null;
        this.hotReloadExecutor = config.isHotReloadEnabled() ? 
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "AssetManagerV2-HotReload");
                t.setDaemon(true);
                return t;
            }) : null;
        
        logManager.info("AssetManagerV2", "Asset manager created with configuration integration");
    }
    
    /**
     * Initialize all components.
     */
    public synchronized void initializeComponents() {
        logManager.info("AssetManagerV2", "Initializing asset manager components");
        
        try {
            // Initialize components
            cache.initialize();
            loader.initialize();
            streamer.initialize();
            compressor.initialize();
            
            // Register default asset factories
            registerDefaultFactories();
            
            // Start hot reload monitoring if enabled
            if (config.isHotReloadEnabled()) {
                startHotReloadMonitoring();
            }
            
            logManager.info("AssetManagerV2", "Asset manager initialized successfully",
                           "assetRootPath", config.getAssetRootPath(),
                           "hotReload", config.isHotReloadEnabled(),
                           "cacheMaxSize", config.getCacheMaxSize(),
                           "loaderThreads", config.getLoaderThreadPoolSize());
            
        } catch (Exception e) {
            logManager.error("AssetManagerV2", "Failed to initialize asset manager", "error", e.getMessage(), e);
            throw new RuntimeException("Asset manager initialization failed", e);
        }
    }
    
    /**
     * Load an asset asynchronously.
     * @param path Asset path relative to asset root
     * @return CompletableFuture that completes when asset is loaded
     */
    public CompletableFuture<Asset> loadAssetAsync(String path) {
        return loadAssetAsync(path, AssetType.fromFilePath(path));
    }
    
    /**
     * Load an asset asynchronously with explicit type.
     * @param path Asset path relative to asset root
     * @param type Asset type
     * @return CompletableFuture that completes when asset is loaded
     */
    public CompletableFuture<Asset> loadAssetAsync(String path, AssetType type) {
        return loadAssetAsync(generateAssetId(path), path, type);
    }
    
    /**
     * Load an asset asynchronously with explicit ID and type.
     * @param assetId Asset identifier
     * @param path Asset path relative to asset root
     * @param type Asset type
     * @return CompletableFuture that completes when asset is loaded
     */
    public CompletableFuture<Asset> loadAssetAsync(String assetId, String path, AssetType type) {
        if (assetId == null || path == null || type == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid parameters"));
        }
        
        // Check cache first
        Asset cachedAsset = cache.get(assetId);
        if (cachedAsset != null) {
            logManager.debug("AssetManagerV2", "Asset found in cache", "assetId", assetId);
            return CompletableFuture.completedFuture(cachedAsset);
        }
        
        // Check if already loaded
        Asset loadedAsset = loadedAssets.get(assetId);
        if (loadedAsset != null) {
            cache.put(loadedAsset); // Add to cache
            return CompletableFuture.completedFuture(loadedAsset);
        }
        
        // Get asset factory
        AssetLoader.AssetFactory factory = factories.get(type);
        if (factory == null) {
            return CompletableFuture.failedFuture(
                new UnsupportedOperationException("No factory registered for asset type: " + type));
        }
        
        // Start loading
        logManager.info("AssetManagerV2", "Starting asset load",
                       "assetId", assetId, "path", path, "type", type.getTypeName());
        
        return loader.loadAsync(assetId, path, type, factory)
            .thenApply(asset -> {
                // Register loaded asset
                loadedAssets.put(assetId, asset);
                pathToIdMapping.put(path, assetId);
                
                // Add to cache
                cache.put(asset);
                
                // Register for hot reload if enabled
                if (config.isHotReloadEnabled() && asset.getMetadata().isHotReloadEnabled()) {
                    registerForHotReload(path);
                }
                
                // Update statistics
                totalAssetsLoaded.incrementAndGet();
                metricsCollector.incrementCounter("asset.manager.loaded");
                
                logManager.info("AssetManagerV2", "Asset loaded successfully",
                               "assetId", assetId, "path", path, "size", asset.getSize());
                
                return asset;
            })
            .exceptionally(throwable -> {
                totalAssetsFailed.incrementAndGet();
                metricsCollector.incrementCounter("asset.manager.failed");
                
                logManager.error("AssetManagerV2", "Asset load failed",
                               "assetId", assetId, "path", path, "error", throwable.getMessage(), throwable);
                
                throw new RuntimeException("Failed to load asset: " + assetId, throwable);
            });
    }
    
    /**
     * Load an asset synchronously (blocks until loaded).
     * @param path Asset path relative to asset root
     * @return Loaded asset
     * @throws Exception if loading fails
     */
    public Asset loadAsset(String path) throws Exception {
        return loadAssetAsync(path).get();
    }
    
    /**
     * Load an asset synchronously with timeout.
     * @param path Asset path relative to asset root
     * @param timeoutMs Timeout in milliseconds
     * @return Loaded asset
     * @throws Exception if loading fails or times out
     */
    public Asset loadAsset(String path, long timeoutMs) throws Exception {
        return loadAssetAsync(path).get(timeoutMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Get an asset if it's already loaded.
     * @param assetId Asset identifier
     * @return Asset if loaded, null otherwise
     */
    public Asset getAsset(String assetId) {
        // Check cache first
        Asset cachedAsset = cache.get(assetId);
        if (cachedAsset != null) {
            return cachedAsset;
        }
        
        // Check loaded assets
        return loadedAssets.get(assetId);
    }
    
    /**
     * Get an asset by path if it's already loaded.
     * @param path Asset path
     * @return Asset if loaded, null otherwise
     */
    public Asset getAssetByPath(String path) {
        String assetId = pathToIdMapping.get(path);
        return assetId != null ? getAsset(assetId) : null;
    }
    
    /**
     * Unload an asset and free its resources.
     * @param assetId Asset identifier
     * @return true if unloaded, false if not found
     */
    public boolean unloadAsset(String assetId) {
        Asset asset = loadedAssets.remove(assetId);
        if (asset != null) {
            // Remove from cache
            cache.remove(assetId);
            
            // Remove path mapping
            pathToIdMapping.entrySet().removeIf(entry -> assetId.equals(entry.getValue()));
            
            // Dispose asset
            try {
                asset.dispose();
            } catch (Exception e) {
                logManager.warn("AssetManagerV2", "Error disposing asset",
                               "assetId", assetId, "error", e.getMessage());
            }
            
            logManager.info("AssetManagerV2", "Asset unloaded", "assetId", assetId);
            return true;
        }
        return false;
    }
    
    /**
     * Reload an asset from its source.
     * @param assetId Asset identifier
     * @return CompletableFuture that completes when reloaded
     */
    public CompletableFuture<Asset> reloadAsset(String assetId) {
        Asset currentAsset = getAsset(assetId);
        if (currentAsset == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Asset not loaded: " + assetId));
        }
        
        String path = currentAsset.getPath();
        AssetType type = currentAsset.getType();
        
        // Unload current asset
        unloadAsset(assetId);
        
        // Load new version
        return loadAssetAsync(assetId, path, type);
    }
    
    /**
     * Register an asset factory for a specific type.
     * @param type Asset type
     * @param factory Asset factory
     */
    public void registerAssetFactory(AssetType type, AssetLoader.AssetFactory factory) {
        factories.put(type, factory);
        logManager.info("AssetManagerV2", "Asset factory registered", "type", type.getTypeName());
    }
    
    /**
     * Get asset metadata.
     * @param assetId Asset identifier
     * @return Asset metadata, or null if not found
     */
    public AssetMetadata getAssetMetadata(String assetId) {
        return assetMetadata.get(assetId);
    }
    
    /**
     * Get asset manager statistics.
     */
    public AssetManagerStatistics getStatistics() {
        return new AssetManagerStatistics(
            loadedAssets.size(),
            totalAssetsLoaded.get(),
            totalAssetsFailed.get(),
            totalHotReloads.get(),
            cache.getStatistics(),
            loader.getStatistics()
        );
    }
    
    /**
     * Enable or disable hot reloading.
     * @param enabled Hot reload enabled
     */
    public void setHotReloadEnabled(boolean enabled) {
        hotReloadEnabled.set(enabled);
        config.setValue("engine.assets.hotReload.enabled", enabled);
        logManager.info("AssetManagerV2", "Hot reload " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Reload configuration and update components.
     */
    public void reloadConfiguration() {
        config.reloadConfiguration();
        
        // Update hot reload setting
        boolean newHotReloadSetting = config.isHotReloadEnabled();
        if (newHotReloadSetting != hotReloadEnabled.get()) {
            setHotReloadEnabled(newHotReloadSetting);
        }
        
        logManager.info("AssetManagerV2", "Configuration reloaded");
    }
    
    private void registerDefaultFactories() {
        // Register factories for built-in asset types
        registerAssetFactory(AssetType.TEXTURE, new TextureAsset.Factory());
        registerAssetFactory(AssetType.MODEL, new ModelAsset.Factory());
        registerAssetFactory(AssetType.SHADER, new ShaderAsset.Factory());
        registerAssetFactory(AssetType.AUDIO, new AudioAsset.Factory());
        registerAssetFactory(AssetType.CONFIG, new ConfigAsset.Factory());
        
        logManager.info("AssetManagerV2", "Default asset factories registered", "count", factories.size());
    }
    
    private String generateAssetId(String path) {
        // Generate a unique asset ID from the path
        return path.replace('\\', '/').toLowerCase();
    }
    
    private void registerForHotReload(String path) {
        if (!config.isHotReloadEnabled() || fileWatcher == null) return;
        
        try {
            Path filePath = Paths.get(config.getAssetRootPath(), path);
            Path parentDir = filePath.getParent();
            
            if (parentDir != null && Files.exists(parentDir)) {
                WatchKey key = parentDir.register(fileWatcher, 
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
                watchedPaths.put(key, parentDir);
                
                logManager.debug("AssetManagerV2", "Registered for hot reload",
                               "path", path, "watchDir", parentDir);
            }
        } catch (Exception e) {
            logManager.warn("AssetManagerV2", "Failed to register for hot reload",
                           "path", path, "error", e.getMessage());
        }
    }
    
    private void startHotReloadMonitoring() {
        hotReloadEnabled.set(true);
        
        hotReloadExecutor.submit(() -> {
            logManager.info("AssetManagerV2", "Hot reload monitoring started");
            
            while (hotReloadEnabled.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    WatchKey key = fileWatcher.poll(config.getHotReloadCheckInterval(), TimeUnit.MILLISECONDS);
                    if (key != null) {
                        processFileChanges(key);
                        key.reset();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logManager.error("AssetManagerV2", "Error in hot reload monitoring", e);
                }
            }
            
            logManager.info("AssetManagerV2", "Hot reload monitoring stopped");
        });
    }
    
    private void processFileChanges(WatchKey key) {
        Path watchedDir = watchedPaths.get(key);
        if (watchedDir == null) return;
        
        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.context() instanceof Path) {
                Path changedFile = watchedDir.resolve((Path) event.context());
                String relativePath = Paths.get(config.getAssetRootPath()).relativize(changedFile).toString();
                
                String assetId = pathToIdMapping.get(relativePath);
                if (assetId != null) {
                    logManager.info("AssetManagerV2", "File change detected, reloading asset",
                                   "path", relativePath, "assetId", assetId, "eventType", event.kind());
                    
                    reloadAsset(assetId).thenRun(() -> {
                        totalHotReloads.incrementAndGet();
                        metricsCollector.incrementCounter("asset.manager.hotreload");
                    });
                }
            }
        }
    }
    
    /**
     * Shutdown the asset manager and free all resources.
     */
    public synchronized void shutdown() {
        logManager.info("AssetManagerV2", "Shutting down asset manager");
        
        // Stop hot reload monitoring
        hotReloadEnabled.set(false);
        if (hotReloadExecutor != null) {
            hotReloadExecutor.shutdown();
            try {
                if (!hotReloadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    hotReloadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                hotReloadExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Close file watcher
        if (fileWatcher != null) {
            try {
                fileWatcher.close();
            } catch (Exception e) {
                logManager.warn("AssetManagerV2", "Error closing file watcher", "error", e.getMessage());
            }
        }
        
        // Unload all assets
        for (String assetId : new ArrayList<>(loadedAssets.keySet())) {
            unloadAsset(assetId);
        }
        
        // Shutdown components
        loader.shutdown();
        cache.clear();
        
        // Clear data structures
        factories.clear();
        loadedAssets.clear();
        assetMetadata.clear();
        pathToIdMapping.clear();
        watchedPaths.clear();
        
        logManager.info("AssetManagerV2", "Asset manager shutdown complete");
    }
    
    /**
     * Asset manager statistics.
     */
    public static class AssetManagerStatistics {
        public final int loadedAssets;
        public final long totalLoaded;
        public final long totalFailed;
        public final long totalHotReloads;
        public final AssetCache.CacheStatistics cacheStats;
        public final AssetLoader.LoaderStatistics loaderStats;
        
        AssetManagerStatistics(int loadedAssets, long totalLoaded, long totalFailed, long totalHotReloads,
                              AssetCache.CacheStatistics cacheStats, AssetLoader.LoaderStatistics loaderStats) {
            this.loadedAssets = loadedAssets;
            this.totalLoaded = totalLoaded;
            this.totalFailed = totalFailed;
            this.totalHotReloads = totalHotReloads;
            this.cacheStats = cacheStats;
            this.loaderStats = loaderStats;
        }
        
        @Override
        public String toString() {
            return String.format("AssetManagerStats{loaded=%d, total=%d, failed=%d, hotReloads=%d}",
                               loadedAssets, totalLoaded, totalFailed, totalHotReloads);
        }
    }
    
    // Getters
    
    public AssetPipelineConfiguration getConfiguration() { return config; }
    public AssetCache getCache() { return cache; }
    public AssetLoader getLoader() { return loader; }
    public AssetStreamer getStreamer() { return streamer; }
    public AssetCompressor getCompressor() { return compressor; }
    public AssetDependencyGraph getDependencyGraph() { return dependencyGraph; }
    public boolean isHotReloadEnabled() { return hotReloadEnabled.get(); }
    public Map<AssetType, AssetLoader.AssetFactory> getFactories() { return Collections.unmodifiableMap(factories); }
    public Map<String, Asset> getLoadedAssets() { return Collections.unmodifiableMap(loadedAssets); }
    public Set<WatchKey> getWatchedPaths() { return Collections.unmodifiableSet(watchedPaths.keySet()); }
}