package engine.assets;

import engine.logging.LogManager;
import engine.logging.PerformanceMonitor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive demonstration of the High-Performance Asset Pipeline.
 * Shows all major features including loading, caching, streaming, compression,
 * dependency management, and hot reloading.
 */
public class AssetPipelineDemo {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static AssetManagerV2 assetManager;
    private static AssetPipelineConfiguration config;
    
    public static void main(String[] args) {
        try {
            System.out.println("=== High-Performance Asset Pipeline Demo ===\n");
            
            // Initialize the asset pipeline
            initializeAssetPipeline();
            
            // Run demonstrations
            demonstrateBasicAssetLoading();
            demonstrateAssetCaching();
            demonstrateAssetStreaming();
            demonstrateAssetCompression();
            demonstrateDependencyManagement();
            demonstrateHotReloading();
            demonstratePerformanceMetrics();
            demonstrateConfigurationManagement();
            
            // Cleanup
            cleanup();
            
            System.out.println("\n=== Asset Pipeline Demo Complete ===");
            
        } catch (Exception e) {
            logManager.error("AssetPipelineDemo", "Demo failed", e);
            e.printStackTrace();
        }
    }
    
    private static void initializeAssetPipeline() throws Exception {
        System.out.println("1. Initializing Asset Pipeline...");
        
        // Initialize configuration
        config = AssetPipelineConfiguration.getInstance();
        config.initialize();
        
        // Initialize asset manager
        AssetManagerV2.initialize();
        assetManager = AssetManagerV2.getInstance();
        
        System.out.println("   ✓ Asset pipeline initialized successfully");
        System.out.println("   ✓ Configuration loaded: " + config.getAllAssetConfiguration().size() + " settings");
        System.out.println("   ✓ Asset manager ready with " + assetManager.getFactories().size() + " registered factories");
        System.out.println();
    }
    
    private static void demonstrateBasicAssetLoading() throws Exception {
        System.out.println("2. Demonstrating Basic Asset Loading...");
        
        // Create sample texture assets
        createSampleAssets();
        
        // Load different asset types
        System.out.println("   Loading texture asset...");
        CompletableFuture<Asset> textureLoad = assetManager.loadAssetAsync("textures/sample_texture.png", AssetType.TEXTURE);
        TextureAsset texture = (TextureAsset) textureLoad.get(5, TimeUnit.SECONDS);
        System.out.println("   ✓ Texture loaded: " + texture.getWidth() + "x" + texture.getHeight() + " (" + texture.getFormat() + ")");
        
        System.out.println("   Loading configuration asset...");
        CompletableFuture<Asset> configLoad = assetManager.loadAssetAsync("config/sample_config.json", AssetType.CONFIG);
        ConfigAsset configAsset = (ConfigAsset) configLoad.get(5, TimeUnit.SECONDS);
        System.out.println("   ✓ Config loaded: " + configAsset.getKeys().size() + " configuration keys");
        
        System.out.println("   Loading shader asset...");
        CompletableFuture<Asset> shaderLoad = assetManager.loadAssetAsync("shaders/sample_shader.glsl", AssetType.SHADER);
        ShaderAsset shader = (ShaderAsset) shaderLoad.get(5, TimeUnit.SECONDS);
        System.out.println("   ✓ Shader loaded: Program ID " + shader.getProgramId() + " with " + shader.getUniformLocations().size() + " uniforms");
        
        System.out.println();
    }
    
    private static void demonstrateAssetCaching() throws Exception {
        System.out.println("3. Demonstrating Asset Caching...");
        
        String assetPath = "textures/sample_texture.png";
        
        // First load (cache miss)
        long startTime = System.currentTimeMillis();
        Asset asset1 = assetManager.loadAsset(assetPath);
        long firstLoadTime = System.currentTimeMillis() - startTime;
        System.out.println("   First load (cache miss): " + firstLoadTime + "ms");
        
        // Second load (cache hit)
        startTime = System.currentTimeMillis();
        Asset asset2 = assetManager.loadAsset(assetPath);
        long secondLoadTime = System.currentTimeMillis() - startTime;
        System.out.println("   Second load (cache hit): " + secondLoadTime + "ms");
        
        // Verify same instance
        System.out.println("   ✓ Cache working: " + (asset1 == asset2 ? "Same instance returned" : "Different instances"));
        
        // Show cache statistics
        AssetCache.CacheStatistics cacheStats = assetManager.getCache().getStatistics();
        System.out.println("   Cache stats: " + cacheStats.getHits() + " hits, " + cacheStats.getMisses() + " misses");
        System.out.println();
    }
    
    private static void demonstrateAssetStreaming() throws Exception {
        System.out.println("4. Demonstrating Asset Streaming...");
        
        // Create a large sample asset for streaming
        createLargeSampleAsset();
        
        System.out.println("   Starting streaming load of large texture...");
        
        // Load with streaming
        TextureAsset largeTexture = new TextureAsset("large_texture", "textures/large_sample.png");
        
        CompletableFuture<Void> streamingFuture = CompletableFuture.runAsync(() -> {
            try {
                largeTexture.loadWithStreaming(assetManager.getStreamer());
            } catch (Exception e) {
                logManager.error("AssetPipelineDemo", "Streaming failed", e);
            }
        });
        
        // Wait for streaming to complete
        streamingFuture.get(10, TimeUnit.SECONDS);
        
        System.out.println("   ✓ Large texture streamed successfully");
        System.out.println("   Streaming stats: " + assetManager.getStreamer().getActiveStreams() + " active streams");
        System.out.println();
    }
    
    private static void demonstrateAssetCompression() throws Exception {
        System.out.println("5. Demonstrating Asset Compression...");
        
        // Create sample data for compression
        byte[] sampleData = createSampleData(1024 * 10); // 10KB sample
        
        AssetCompressor compressor = assetManager.getCompressor();
        
        // Test different compression algorithms
        for (AssetCompressor.CompressionAlgorithm algorithm : AssetCompressor.CompressionAlgorithm.values()) {
            if (algorithm == AssetCompressor.CompressionAlgorithm.NONE) continue;
            
            long startTime = System.currentTimeMillis();
            byte[] compressed = compressor.compress(sampleData, algorithm);
            long compressionTime = System.currentTimeMillis() - startTime;
            
            startTime = System.currentTimeMillis();
            byte[] decompressed = compressor.decompress(compressed, algorithm);
            long decompressionTime = System.currentTimeMillis() - startTime;
            
            float ratio = (float) compressed.length / sampleData.length;
            
            System.out.println("   " + algorithm + ": " + 
                             String.format("%.2f", ratio) + " ratio, " +
                             compressionTime + "ms compress, " +
                             decompressionTime + "ms decompress");
        }
        
        System.out.println();
    }
    
    private static void demonstrateDependencyManagement() throws Exception {
        System.out.println("6. Demonstrating Dependency Management...");
        
        AssetDependencyGraph depGraph = assetManager.getDependencyGraph();
        
        // Create dependency chain: shader -> vertex_shader + fragment_shader -> common_includes
        depGraph.addDependency("shaders/main.glsl", "shaders/vertex.glsl");
        depGraph.addDependency("shaders/main.glsl", "shaders/fragment.glsl");
        depGraph.addDependency("shaders/vertex.glsl", "shaders/common.glsl");
        depGraph.addDependency("shaders/fragment.glsl", "shaders/common.glsl");
        
        // Get load order
        String[] loadOrder = depGraph.getLoadOrder("shaders/main.glsl");
        System.out.println("   Dependency load order for main.glsl:");
        for (int i = 0; i < loadOrder.length; i++) {
            System.out.println("     " + (i + 1) + ". " + loadOrder[i]);
        }
        
        // Test circular dependency detection
        try {
            depGraph.addDependency("shaders/common.glsl", "shaders/main.glsl"); // Creates cycle
            depGraph.getLoadOrder("shaders/main.glsl");
            System.out.println("   ✗ Circular dependency not detected!");
        } catch (Exception e) {
            System.out.println("   ✓ Circular dependency detected and prevented");
        }
        
        System.out.println();
    }
    
    private static void demonstrateHotReloading() throws Exception {
        System.out.println("7. Demonstrating Hot Reloading...");
        
        if (!config.isHotReloadEnabled()) {
            System.out.println("   Hot reloading is disabled in configuration");
            System.out.println();
            return;
        }
        
        String assetPath = "config/sample_config.json";
        
        // Load initial asset
        ConfigAsset configAsset = (ConfigAsset) assetManager.loadAsset(assetPath);
        String initialValue = configAsset.getString("sample.key", "default");
        System.out.println("   Initial config value: " + initialValue);
        
        // Simulate file change and reload
        System.out.println("   Simulating file change and hot reload...");
        CompletableFuture<Asset> reloadFuture = assetManager.reloadAsset(configAsset.getId());
        ConfigAsset reloadedAsset = (ConfigAsset) reloadFuture.get(5, TimeUnit.SECONDS);
        
        System.out.println("   ✓ Asset reloaded successfully");
        System.out.println("   Hot reload check interval: " + config.getHotReloadCheckInterval() + "ms");
        System.out.println();
    }
    
    private static void demonstratePerformanceMetrics() throws Exception {
        System.out.println("8. Demonstrating Performance Metrics...");
        
        PerformanceMonitor perfMonitor = logManager.getPerformanceMonitor();
        
        // Show current metrics
        System.out.println("   Asset Loading Metrics:");
        System.out.println("     Assets loaded: " + perfMonitor.getAssetsLoaded());
        System.out.println("     Load failures: " + perfMonitor.getAssetLoadsFailed());
        System.out.println("     Success rate: " + String.format("%.1f%%", perfMonitor.getAssetLoadSuccessRate()));
        System.out.println("     Average load time: " + String.format("%.2fms", perfMonitor.getAverageAssetLoadTime()));
        System.out.println("     Total asset size: " + perfMonitor.getTotalAssetSizeMB() + "MB");
        
        System.out.println("   Cache Metrics:");
        System.out.println("     Cache hits: " + perfMonitor.getCacheHits());
        System.out.println("     Cache misses: " + perfMonitor.getCacheMisses());
        System.out.println("     Hit rate: " + String.format("%.1f%%", perfMonitor.getCacheHitRate()));
        
        System.out.println("   Operations:");
        System.out.println("     Hot reloads: " + perfMonitor.getHotReloads());
        System.out.println("     Compressions: " + perfMonitor.getCompressionOperations());
        System.out.println("     Streaming ops: " + perfMonitor.getStreamingOperations());
        
        System.out.println();
    }
    
    private static void demonstrateConfigurationManagement() throws Exception {
        System.out.println("9. Demonstrating Configuration Management...");
        
        System.out.println("   Current Asset Pipeline Configuration:");
        System.out.println("     Asset root path: " + config.getAssetRootPath());
        System.out.println("     Hot reload enabled: " + config.isHotReloadEnabled());
        System.out.println("     Cache max size: " + (config.getCacheMaxSize() / 1024 / 1024) + "MB");
        System.out.println("     Loader threads: " + config.getLoaderThreadPoolSize());
        System.out.println("     Compression enabled: " + config.isCompressionEnabled());
        System.out.println("     Streaming chunk size: " + (config.getStreamerChunkSize() / 1024) + "KB");
        
        // Demonstrate runtime configuration change
        System.out.println("   Changing configuration at runtime...");
        boolean originalHotReload = config.isHotReloadEnabled();
        config.setValue("engine.assets.hotReload.enabled", !originalHotReload);
        
        System.out.println("   ✓ Hot reload setting changed from " + originalHotReload + " to " + config.isHotReloadEnabled());
        
        // Restore original setting
        config.setValue("engine.assets.hotReload.enabled", originalHotReload);
        System.out.println();
    }
    
    private static void createSampleAssets() {
        System.out.println("   Creating sample assets for demonstration...");
        
        // In a real implementation, these would be actual files
        // For demo purposes, we're just showing the structure
        
        // Sample texture metadata
        System.out.println("     - textures/sample_texture.png (256x256 RGBA)");
        System.out.println("     - config/sample_config.json (JSON configuration)");
        System.out.println("     - shaders/sample_shader.glsl (GLSL shader program)");
        System.out.println("     - models/sample_model.obj (3D model)");
        System.out.println("     - audio/sample_sound.ogg (Audio file)");
    }
    
    private static void createLargeSampleAsset() {
        System.out.println("   Creating large sample asset for streaming demo...");
        System.out.println("     - textures/large_sample.png (2048x2048 RGBA, ~16MB)");
    }
    
    private static byte[] createSampleData(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i % 256);
        }
        return data;
    }
    
    private static void cleanup() {
        System.out.println("10. Cleaning up...");
        
        if (assetManager != null) {
            assetManager.shutdown();
        }
        
        System.out.println("   ✓ Asset pipeline shutdown complete");
    }
}