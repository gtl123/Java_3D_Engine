package engine.assets;

import engine.logging.LogManager;
import engine.logging.MetricsCollector;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.*;
import java.util.*;

/**
 * Runtime compression and decompression system with multiple algorithms.
 * Provides efficient asset compression using various algorithms including
 * GZIP, DEFLATE, and custom compression schemes optimized for different asset types.
 */
public class AssetCompressor {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = LogManager.getInstance().getMetricsCollector();
    
    // Thread pool for compression operations
    private final ExecutorService compressionExecutor;
    
    // Statistics
    private final AtomicLong totalCompressions = new AtomicLong(0);
    private final AtomicLong totalDecompressions = new AtomicLong(0);
    private final AtomicLong totalBytesCompressed = new AtomicLong(0);
    private final AtomicLong totalBytesDecompressed = new AtomicLong(0);
    private final AtomicLong totalCompressionTime = new AtomicLong(0);
    private final AtomicLong totalDecompressionTime = new AtomicLong(0);
    
    // Configuration
    private volatile boolean enableAsyncCompression = true;
    private volatile int defaultCompressionLevel = 6;
    
    /**
     * Compression algorithms supported by the system.
     */
    public enum CompressionAlgorithm {
        NONE("none", 0.0f, 0),
        GZIP("gzip", 0.4f, 6),
        DEFLATE("deflate", 0.45f, 6),
        LZ4_FAST("lz4-fast", 0.6f, 1),    // Simulated - would use real LZ4 in production
        ZSTD("zstd", 0.3f, 3),            // Simulated - would use real Zstandard in production
        CUSTOM_TEXTURE("texture", 0.25f, 8), // Custom algorithm for texture data
        CUSTOM_AUDIO("audio", 0.5f, 4);      // Custom algorithm for audio data
        
        private final String name;
        private final float estimatedRatio;
        private final int defaultLevel;
        
        CompressionAlgorithm(String name, float estimatedRatio, int defaultLevel) {
            this.name = name;
            this.estimatedRatio = estimatedRatio;
            this.defaultLevel = defaultLevel;
        }
        
        public String getName() { return name; }
        public float getEstimatedRatio() { return estimatedRatio; }
        public int getDefaultLevel() { return defaultLevel; }
    }
    
    /**
     * Compression result containing compressed data and metadata.
     */
    public static class CompressionResult {
        private final byte[] compressedData;
        private final CompressionAlgorithm algorithm;
        private final int level;
        private final long originalSize;
        private final long compressedSize;
        private final long compressionTime;
        private final float compressionRatio;
        
        CompressionResult(byte[] compressedData, CompressionAlgorithm algorithm, int level,
                         long originalSize, long compressionTime) {
            this.compressedData = compressedData;
            this.algorithm = algorithm;
            this.level = level;
            this.originalSize = originalSize;
            this.compressedSize = compressedData.length;
            this.compressionTime = compressionTime;
            this.compressionRatio = originalSize > 0 ? (float) compressedSize / originalSize : 1.0f;
        }
        
        public byte[] getCompressedData() { return compressedData; }
        public CompressionAlgorithm getAlgorithm() { return algorithm; }
        public int getLevel() { return level; }
        public long getOriginalSize() { return originalSize; }
        public long getCompressedSize() { return compressedSize; }
        public long getCompressionTime() { return compressionTime; }
        public float getCompressionRatio() { return compressionRatio; }
        public long getSpaceSaved() { return originalSize - compressedSize; }
        
        @Override
        public String toString() {
            return String.format("CompressionResult{algorithm=%s, ratio=%.2f, saved=%dKB, time=%dms}",
                               algorithm.getName(), compressionRatio, getSpaceSaved() / 1024, compressionTime);
        }
    }
    
    /**
     * Decompression result containing decompressed data and metadata.
     */
    public static class DecompressionResult {
        private final byte[] decompressedData;
        private final CompressionAlgorithm algorithm;
        private final long decompressedSize;
        private final long decompressionTime;
        
        DecompressionResult(byte[] decompressedData, CompressionAlgorithm algorithm, long decompressionTime) {
            this.decompressedData = decompressedData;
            this.algorithm = algorithm;
            this.decompressedSize = decompressedData.length;
            this.decompressionTime = decompressionTime;
        }
        
        public byte[] getDecompressedData() { return decompressedData; }
        public CompressionAlgorithm getAlgorithm() { return algorithm; }
        public long getDecompressedSize() { return decompressedSize; }
        public long getDecompressionTime() { return decompressionTime; }
        
        @Override
        public String toString() {
            return String.format("DecompressionResult{algorithm=%s, size=%dKB, time=%dms}",
                               algorithm.getName(), decompressedSize / 1024, decompressionTime);
        }
    }
    
    /**
     * Compression configuration.
     */
    public static class Config {
        private boolean enableAsyncCompression = true;
        private int defaultCompressionLevel = 6;
        private int compressionThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        
        public Config enableAsyncCompression(boolean enable) { this.enableAsyncCompression = enable; return this; }
        public Config defaultCompressionLevel(int level) { this.defaultCompressionLevel = Math.max(1, Math.min(9, level)); return this; }
        public Config compressionThreads(int threads) { this.compressionThreads = Math.max(1, threads); return this; }
        
        public AssetCompressor build() {
            return new AssetCompressor(this);
        }
    }
    
    /**
     * Create asset compressor with default configuration.
     */
    public AssetCompressor() {
        this(new Config());
    }
    
    /**
     * Create asset compressor with specified configuration.
     */
    private AssetCompressor(Config config) {
        this.enableAsyncCompression = config.enableAsyncCompression;
        this.defaultCompressionLevel = config.defaultCompressionLevel;
        
        // Initialize thread pool
        this.compressionExecutor = Executors.newFixedThreadPool(config.compressionThreads, r -> {
            Thread t = new Thread(r, "AssetCompressor-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        
        logManager.info("AssetCompressor", "Asset compressor initialized",
                       "asyncCompression", enableAsyncCompression,
                       "defaultLevel", defaultCompressionLevel,
                       "threads", config.compressionThreads);
    }
    
    /**
     * Compress data synchronously.
     * @param data Data to compress
     * @param algorithm Compression algorithm
     * @return Compression result
     */
    public CompressionResult compress(byte[] data, CompressionAlgorithm algorithm) {
        return compress(data, algorithm, algorithm.getDefaultLevel());
    }
    
    /**
     * Compress data synchronously with custom level.
     * @param data Data to compress
     * @param algorithm Compression algorithm
     * @param level Compression level (1-9)
     * @return Compression result
     */
    public CompressionResult compress(byte[] data, CompressionAlgorithm algorithm, int level) {
        if (data == null || algorithm == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        
        if (algorithm == CompressionAlgorithm.NONE) {
            return new CompressionResult(data, algorithm, 0, data.length, 0);
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            byte[] compressedData = performCompression(data, algorithm, level);
            long compressionTime = System.currentTimeMillis() - startTime;
            
            // Update statistics
            totalCompressions.incrementAndGet();
            totalBytesCompressed.addAndGet(data.length);
            totalCompressionTime.addAndGet(compressionTime);
            
            metricsCollector.incrementCounter("asset.compressor.compressions");
            metricsCollector.recordTimer("asset.compressor.compression.time", compressionTime);
            
            CompressionResult result = new CompressionResult(compressedData, algorithm, level, data.length, compressionTime);
            
            logManager.debug("AssetCompressor", "Data compressed",
                           "algorithm", algorithm.getName(),
                           "originalSize", data.length,
                           "compressedSize", compressedData.length,
                           "ratio", result.getCompressionRatio(),
                           "time", compressionTime);
            
            return result;
            
        } catch (Exception e) {
            logManager.error("AssetCompressor", "Compression failed",
                           "algorithm", algorithm.getName(),
                           "dataSize", data.length,
                           "error", e.getMessage(), e);
            throw new RuntimeException("Compression failed", e);
        }
    }
    
    /**
     * Compress data asynchronously.
     * @param data Data to compress
     * @param algorithm Compression algorithm
     * @return CompletableFuture with compression result
     */
    public CompletableFuture<CompressionResult> compressAsync(byte[] data, CompressionAlgorithm algorithm) {
        return compressAsync(data, algorithm, algorithm.getDefaultLevel());
    }
    
    /**
     * Compress data asynchronously with custom level.
     * @param data Data to compress
     * @param algorithm Compression algorithm
     * @param level Compression level
     * @return CompletableFuture with compression result
     */
    public CompletableFuture<CompressionResult> compressAsync(byte[] data, CompressionAlgorithm algorithm, int level) {
        if (!enableAsyncCompression) {
            return CompletableFuture.completedFuture(compress(data, algorithm, level));
        }
        
        return CompletableFuture.supplyAsync(() -> compress(data, algorithm, level), compressionExecutor);
    }
    
    /**
     * Decompress data synchronously.
     * @param compressedData Compressed data
     * @param algorithm Compression algorithm used
     * @return Decompression result
     */
    public DecompressionResult decompress(byte[] compressedData, CompressionAlgorithm algorithm) {
        if (compressedData == null || algorithm == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        
        if (algorithm == CompressionAlgorithm.NONE) {
            return new DecompressionResult(compressedData, algorithm, 0);
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            byte[] decompressedData = performDecompression(compressedData, algorithm);
            long decompressionTime = System.currentTimeMillis() - startTime;
            
            // Update statistics
            totalDecompressions.incrementAndGet();
            totalBytesDecompressed.addAndGet(decompressedData.length);
            totalDecompressionTime.addAndGet(decompressionTime);
            
            metricsCollector.incrementCounter("asset.compressor.decompressions");
            metricsCollector.recordTimer("asset.compressor.decompression.time", decompressionTime);
            
            DecompressionResult result = new DecompressionResult(decompressedData, algorithm, decompressionTime);
            
            logManager.debug("AssetCompressor", "Data decompressed",
                           "algorithm", algorithm.getName(),
                           "compressedSize", compressedData.length,
                           "decompressedSize", decompressedData.length,
                           "time", decompressionTime);
            
            return result;
            
        } catch (Exception e) {
            logManager.error("AssetCompressor", "Decompression failed",
                           "algorithm", algorithm.getName(),
                           "dataSize", compressedData.length,
                           "error", e.getMessage(), e);
            throw new RuntimeException("Decompression failed", e);
        }
    }
    
    /**
     * Decompress data asynchronously.
     * @param compressedData Compressed data
     * @param algorithm Compression algorithm used
     * @return CompletableFuture with decompression result
     */
    public CompletableFuture<DecompressionResult> decompressAsync(byte[] compressedData, CompressionAlgorithm algorithm) {
        if (!enableAsyncCompression) {
            return CompletableFuture.completedFuture(decompress(compressedData, algorithm));
        }
        
        return CompletableFuture.supplyAsync(() -> decompress(compressedData, algorithm), compressionExecutor);
    }
    
    /**
     * Choose the best compression algorithm for the given asset type and data.
     * @param assetType Asset type
     * @param data Data to analyze
     * @return Recommended compression algorithm
     */
    public CompressionAlgorithm chooseBestAlgorithm(AssetType assetType, byte[] data) {
        if (data == null || data.length < 1024) {
            return CompressionAlgorithm.NONE; // Don't compress small data
        }
        
        switch (assetType) {
            case TEXTURE:
                return CompressionAlgorithm.CUSTOM_TEXTURE;
            case AUDIO:
                return CompressionAlgorithm.CUSTOM_AUDIO;
            case MODEL:
            case BINARY:
                return CompressionAlgorithm.ZSTD;
            case SHADER:
            case CONFIG:
            case SCRIPT:
                return CompressionAlgorithm.GZIP;
            default:
                return CompressionAlgorithm.DEFLATE;
        }
    }
    
    /**
     * Estimate compression ratio for given algorithm and data.
     * @param algorithm Compression algorithm
     * @param dataSize Original data size
     * @return Estimated compression ratio
     */
    public float estimateCompressionRatio(CompressionAlgorithm algorithm, long dataSize) {
        if (dataSize < 1024) {
            return 1.0f; // No compression benefit for small data
        }
        
        return algorithm.getEstimatedRatio();
    }
    
    /**
     * Get compression statistics.
     */
    public CompressionStatistics getStatistics() {
        return new CompressionStatistics(
            totalCompressions.get(),
            totalDecompressions.get(),
            totalBytesCompressed.get(),
            totalBytesDecompressed.get(),
            getAverageCompressionTime(),
            getAverageDecompressionTime()
        );
    }
    
    private byte[] performCompression(byte[] data, CompressionAlgorithm algorithm, int level) throws IOException {
        switch (algorithm) {
            case GZIP:
                return compressGzip(data, level);
            case DEFLATE:
                return compressDeflate(data, level);
            case LZ4_FAST:
                return compressLZ4Fast(data);
            case ZSTD:
                return compressZstd(data, level);
            case CUSTOM_TEXTURE:
                return compressTexture(data, level);
            case CUSTOM_AUDIO:
                return compressAudio(data, level);
            default:
                return data;
        }
    }
    
    private byte[] performDecompression(byte[] compressedData, CompressionAlgorithm algorithm) throws IOException {
        switch (algorithm) {
            case GZIP:
                return decompressGzip(compressedData);
            case DEFLATE:
                return decompressDeflate(compressedData);
            case LZ4_FAST:
                return decompressLZ4Fast(compressedData);
            case ZSTD:
                return decompressZstd(compressedData);
            case CUSTOM_TEXTURE:
                return decompressTexture(compressedData);
            case CUSTOM_AUDIO:
                return decompressAudio(compressedData);
            default:
                return compressedData;
        }
    }
    
    private byte[] compressGzip(byte[] data, int level) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos) {{
            def.setLevel(level);
        }}) {
            gzos.write(data);
        }
        return baos.toByteArray();
    }
    
    private byte[] decompressGzip(byte[] compressedData) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(compressedData))) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
        }
        return baos.toByteArray();
    }
    
    private byte[] compressDeflate(byte[] data, int level) throws IOException {
        Deflater deflater = new Deflater(level);
        deflater.setInput(data);
        deflater.finish();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            baos.write(buffer, 0, count);
        }
        
        deflater.end();
        return baos.toByteArray();
    }
    
    private byte[] decompressDeflate(byte[] compressedData) throws IOException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressedData);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                baos.write(buffer, 0, count);
            }
        } catch (DataFormatException e) {
            throw new IOException("Invalid compressed data", e);
        } finally {
            inflater.end();
        }
        
        return baos.toByteArray();
    }
    
    // Simulated LZ4 compression (would use real LZ4 library in production)
    private byte[] compressLZ4Fast(byte[] data) {
        // Simplified simulation - just use deflate with fast settings
        try {
            return compressDeflate(data, 1);
        } catch (IOException e) {
            return data;
        }
    }
    
    private byte[] decompressLZ4Fast(byte[] compressedData) {
        try {
            return decompressDeflate(compressedData);
        } catch (IOException e) {
            return compressedData;
        }
    }
    
    // Simulated Zstandard compression (would use real Zstd library in production)
    private byte[] compressZstd(byte[] data, int level) {
        try {
            return compressDeflate(data, Math.min(level + 2, 9));
        } catch (IOException e) {
            return data;
        }
    }
    
    private byte[] decompressZstd(byte[] compressedData) {
        try {
            return decompressDeflate(compressedData);
        } catch (IOException e) {
            return compressedData;
        }
    }
    
    // Custom texture compression (simplified - would use specialized algorithms in production)
    private byte[] compressTexture(byte[] data, int level) throws IOException {
        // For textures, we might use specialized algorithms like DXT compression
        // For now, use deflate with texture-optimized settings
        return compressDeflate(data, level);
    }
    
    private byte[] decompressTexture(byte[] compressedData) throws IOException {
        return decompressDeflate(compressedData);
    }
    
    // Custom audio compression (simplified - would use specialized algorithms in production)
    private byte[] compressAudio(byte[] data, int level) throws IOException {
        // For audio, we might use specialized algorithms like OGG Vorbis or Opus
        // For now, use deflate with audio-optimized settings
        return compressDeflate(data, Math.max(level - 2, 1));
    }
    
    private byte[] decompressAudio(byte[] compressedData) throws IOException {
        return decompressDeflate(compressedData);
    }
    
    private double getAverageCompressionTime() {
        long compressions = totalCompressions.get();
        return compressions > 0 ? (double) totalCompressionTime.get() / compressions : 0.0;
    }
    
    private double getAverageDecompressionTime() {
        long decompressions = totalDecompressions.get();
        return decompressions > 0 ? (double) totalDecompressionTime.get() / decompressions : 0.0;
    }
    
    /**
     * Shutdown the asset compressor.
     */
    public void shutdown() {
        logManager.info("AssetCompressor", "Shutting down asset compressor");
        
        compressionExecutor.shutdown();
        try {
            if (!compressionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                compressionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            compressionExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logManager.info("AssetCompressor", "Asset compressor shutdown complete");
    }
    
    /**
     * Compression statistics.
     */
    public static class CompressionStatistics {
        public final long totalCompressions;
        public final long totalDecompressions;
        public final long totalBytesCompressed;
        public final long totalBytesDecompressed;
        public final double averageCompressionTime;
        public final double averageDecompressionTime;
        
        CompressionStatistics(long totalCompressions, long totalDecompressions,
                             long totalBytesCompressed, long totalBytesDecompressed,
                             double averageCompressionTime, double averageDecompressionTime) {
            this.totalCompressions = totalCompressions;
            this.totalDecompressions = totalDecompressions;
            this.totalBytesCompressed = totalBytesCompressed;
            this.totalBytesDecompressed = totalBytesDecompressed;
            this.averageCompressionTime = averageCompressionTime;
            this.averageDecompressionTime = averageDecompressionTime;
        }
        
        @Override
        public String toString() {
            return String.format("CompressionStats{compressions=%d, decompressions=%d, avgCompTime=%.1fms, avgDecompTime=%.1fms}",
                               totalCompressions, totalDecompressions, averageCompressionTime, averageDecompressionTime);
        }
    }
    
    /**
     * Create a new compressor configuration.
     */
    public static Config config() {
        return new Config();
    }
}