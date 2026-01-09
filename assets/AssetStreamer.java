package engine.assets;

import engine.logging.LogManager;
import engine.logging.MetricsCollector;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.*;

/**
 * Streaming system for large assets with progressive loading.
 * Supports memory-mapped files, chunked loading, and bandwidth management
 * for efficient handling of large assets without blocking the main thread.
 */
public class AssetStreamer {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = LogManager.getInstance().getMetricsCollector();
    
    // Configuration
    private static final int DEFAULT_CHUNK_SIZE = 64 * 1024; // 64KB chunks
    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024 * 1024; // 8MB buffer
    private static final long DEFAULT_BANDWIDTH_LIMIT = 50 * 1024 * 1024; // 50MB/s
    
    // Thread pool for streaming operations
    private final ExecutorService streamingExecutor;
    private final ScheduledExecutorService scheduledExecutor;
    
    // Active streams tracking
    private final ConcurrentHashMap<String, StreamingSession> activeSessions;
    
    // Bandwidth management
    private final AtomicLong totalBytesStreamed = new AtomicLong(0);
    private final AtomicLong bandwidthUsed = new AtomicLong(0);
    private volatile long bandwidthLimit = DEFAULT_BANDWIDTH_LIMIT;
    
    // Configuration
    private volatile int chunkSize = DEFAULT_CHUNK_SIZE;
    private volatile int bufferSize = DEFAULT_BUFFER_SIZE;
    private volatile boolean enableMemoryMapping = true;
    private volatile boolean enableBandwidthThrottling = true;
    
    /**
     * Streaming session for an asset.
     */
    public static class StreamingSession {
        private final String assetId;
        private final String path;
        private final long totalSize;
        private final int chunkSize;
        private final StreamingCallback callback;
        
        private final AtomicLong bytesStreamed = new AtomicLong(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private final AtomicReference<Exception> error = new AtomicReference<>();
        
        private volatile FileChannel fileChannel;
        private volatile long startTime;
        private volatile long lastChunkTime;
        
        StreamingSession(String assetId, String path, long totalSize, int chunkSize, StreamingCallback callback) {
            this.assetId = assetId;
            this.path = path;
            this.totalSize = totalSize;
            this.chunkSize = chunkSize;
            this.callback = callback;
            this.startTime = System.currentTimeMillis();
            this.lastChunkTime = startTime;
        }
        
        public String getAssetId() { return assetId; }
        public String getPath() { return path; }
        public long getTotalSize() { return totalSize; }
        public long getBytesStreamed() { return bytesStreamed.get(); }
        public boolean isCancelled() { return cancelled.get(); }
        public boolean isCompleted() { return completed.get(); }
        public Exception getError() { return error.get(); }
        
        public double getProgress() {
            return totalSize > 0 ? (double) bytesStreamed.get() / totalSize : 0.0;
        }
        
        public long getElapsedTime() {
            return System.currentTimeMillis() - startTime;
        }
        
        public double getAverageSpeed() {
            long elapsed = getElapsedTime();
            return elapsed > 0 ? (double) bytesStreamed.get() / elapsed * 1000.0 : 0.0; // bytes per second
        }
        
        void cancel() {
            cancelled.set(true);
            closeChannel();
        }
        
        void complete() {
            completed.set(true);
            closeChannel();
        }
        
        void setError(Exception e) {
            error.set(e);
            closeChannel();
        }
        
        void addBytesStreamed(long bytes) {
            bytesStreamed.addAndGet(bytes);
            lastChunkTime = System.currentTimeMillis();
        }
        
        void setFileChannel(FileChannel channel) {
            this.fileChannel = channel;
        }
        
        private void closeChannel() {
            if (fileChannel != null) {
                try {
                    fileChannel.close();
                } catch (IOException e) {
                    // Log but don't throw
                }
                fileChannel = null;
            }
        }
    }
    
    /**
     * Callback interface for streaming progress and completion.
     */
    public interface StreamingCallback {
        /**
         * Called when a chunk of data is available.
         * @param session Streaming session
         * @param chunkData Chunk data
         * @param chunkIndex Index of this chunk (0-based)
         * @param isLastChunk true if this is the final chunk
         */
        void onChunkReceived(StreamingSession session, ByteBuffer chunkData, int chunkIndex, boolean isLastChunk);
        
        /**
         * Called when streaming completes successfully.
         * @param session Streaming session
         */
        void onStreamingComplete(StreamingSession session);
        
        /**
         * Called when streaming fails.
         * @param session Streaming session
         * @param error Error that occurred
         */
        void onStreamingError(StreamingSession session, Exception error);
        
        /**
         * Called when streaming is cancelled.
         * @param session Streaming session
         */
        void onStreamingCancelled(StreamingSession session);
    }
    
    /**
     * Streaming configuration.
     */
    public static class Config {
        private int chunkSize = DEFAULT_CHUNK_SIZE;
        private int bufferSize = DEFAULT_BUFFER_SIZE;
        private long bandwidthLimit = DEFAULT_BANDWIDTH_LIMIT;
        private boolean enableMemoryMapping = true;
        private boolean enableBandwidthThrottling = true;
        private int streamingThreads = Math.max(2, Runtime.getRuntime().availableProcessors() / 4);
        
        public Config chunkSize(int size) { this.chunkSize = Math.max(1024, size); return this; }
        public Config bufferSize(int size) { this.bufferSize = Math.max(chunkSize, size); return this; }
        public Config bandwidthLimit(long bytesPerSecond) { this.bandwidthLimit = Math.max(1024, bytesPerSecond); return this; }
        public Config enableMemoryMapping(boolean enable) { this.enableMemoryMapping = enable; return this; }
        public Config enableBandwidthThrottling(boolean enable) { this.enableBandwidthThrottling = enable; return this; }
        public Config streamingThreads(int threads) { this.streamingThreads = Math.max(1, threads); return this; }
        
        public AssetStreamer build() {
            return new AssetStreamer(this);
        }
    }
    
    /**
     * Create asset streamer with default configuration.
     */
    public AssetStreamer() {
        this(new Config());
    }
    
    /**
     * Create asset streamer with specified configuration.
     */
    private AssetStreamer(Config config) {
        this.chunkSize = config.chunkSize;
        this.bufferSize = config.bufferSize;
        this.bandwidthLimit = config.bandwidthLimit;
        this.enableMemoryMapping = config.enableMemoryMapping;
        this.enableBandwidthThrottling = config.enableBandwidthThrottling;
        
        // Initialize thread pools
        this.streamingExecutor = Executors.newFixedThreadPool(config.streamingThreads, r -> {
            Thread t = new Thread(r, "AssetStreamer-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        
        this.scheduledExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "AssetStreamer-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        this.activeSessions = new ConcurrentHashMap<>();
        
        // Start bandwidth monitoring
        startBandwidthMonitoring();
        
        logManager.info("AssetStreamer", "Asset streamer initialized",
                       "chunkSize", chunkSize,
                       "bufferSize", bufferSize,
                       "bandwidthLimit", bandwidthLimit,
                       "memoryMapping", enableMemoryMapping,
                       "bandwidthThrottling", enableBandwidthThrottling);
    }
    
    /**
     * Start streaming an asset.
     * @param assetId Asset identifier
     * @param path Asset file path
     * @param callback Streaming callback
     * @return Streaming session
     */
    public StreamingSession startStreaming(String assetId, String path, StreamingCallback callback) {
        return startStreaming(assetId, path, callback, chunkSize);
    }
    
    /**
     * Start streaming an asset with custom chunk size.
     * @param assetId Asset identifier
     * @param path Asset file path
     * @param callback Streaming callback
     * @param customChunkSize Custom chunk size for this stream
     * @return Streaming session
     */
    public StreamingSession startStreaming(String assetId, String path, StreamingCallback callback, int customChunkSize) {
        if (assetId == null || path == null || callback == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        
        // Check if already streaming
        StreamingSession existingSession = activeSessions.get(assetId);
        if (existingSession != null && !existingSession.isCompleted() && !existingSession.isCancelled()) {
            logManager.warn("AssetStreamer", "Asset already being streamed", "assetId", assetId);
            return existingSession;
        }
        
        try {
            // Get file information
            Path filePath = Paths.get(path);
            long fileSize = java.nio.file.Files.size(filePath);
            
            // Create streaming session
            StreamingSession session = new StreamingSession(assetId, path, fileSize, customChunkSize, callback);
            activeSessions.put(assetId, session);
            
            // Start streaming in background
            streamingExecutor.submit(() -> performStreaming(session));
            
            logManager.info("AssetStreamer", "Started streaming asset",
                           "assetId", assetId,
                           "path", path,
                           "fileSize", fileSize,
                           "chunkSize", customChunkSize);
            
            return session;
            
        } catch (Exception e) {
            logManager.error("AssetStreamer", "Failed to start streaming",
                           "assetId", assetId, "path", path, "error", e.getMessage(), e);
            throw new RuntimeException("Failed to start streaming: " + assetId, e);
        }
    }
    
    /**
     * Cancel streaming for an asset.
     * @param assetId Asset identifier
     * @return true if cancelled, false if not found or already completed
     */
    public boolean cancelStreaming(String assetId) {
        StreamingSession session = activeSessions.get(assetId);
        if (session != null && !session.isCompleted()) {
            session.cancel();
            activeSessions.remove(assetId);
            
            try {
                session.callback.onStreamingCancelled(session);
            } catch (Exception e) {
                logManager.warn("AssetStreamer", "Error in streaming cancelled callback",
                               "assetId", assetId, "error", e.getMessage());
            }
            
            logManager.info("AssetStreamer", "Streaming cancelled", "assetId", assetId);
            return true;
        }
        return false;
    }
    
    /**
     * Get active streaming session.
     * @param assetId Asset identifier
     * @return Streaming session, or null if not found
     */
    public StreamingSession getStreamingSession(String assetId) {
        return activeSessions.get(assetId);
    }
    
    /**
     * Get all active streaming sessions.
     * @return Collection of active sessions
     */
    public Collection<StreamingSession> getActiveSessions() {
        return new ArrayList<>(activeSessions.values());
    }
    
    /**
     * Get streaming statistics.
     */
    public StreamingStatistics getStatistics() {
        long currentBandwidth = bandwidthUsed.get();
        int activeSessions = this.activeSessions.size();
        long totalStreamed = totalBytesStreamed.get();
        
        return new StreamingStatistics(activeSessions, totalStreamed, currentBandwidth, bandwidthLimit);
    }
    
    private void performStreaming(StreamingSession session) {
        try {
            Path filePath = Paths.get(session.getPath());
            
            if (enableMemoryMapping && session.getTotalSize() > bufferSize) {
                performMemoryMappedStreaming(session, filePath);
            } else {
                performBufferedStreaming(session, filePath);
            }
            
        } catch (Exception e) {
            session.setError(e);
            
            try {
                session.callback.onStreamingError(session, e);
            } catch (Exception callbackError) {
                logManager.warn("AssetStreamer", "Error in streaming error callback",
                               "assetId", session.getAssetId(), "error", callbackError.getMessage());
            }
            
            logManager.error("AssetStreamer", "Streaming failed",
                           "assetId", session.getAssetId(),
                           "path", session.getPath(),
                           "error", e.getMessage(), e);
        } finally {
            activeSessions.remove(session.getAssetId());
        }
    }
    
    private void performMemoryMappedStreaming(StreamingSession session, Path filePath) throws IOException {
        try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)) {
            session.setFileChannel(channel);
            
            long fileSize = channel.size();
            long position = 0;
            int chunkIndex = 0;
            
            while (position < fileSize && !session.isCancelled()) {
                // Calculate chunk size for this iteration
                long remainingBytes = fileSize - position;
                int currentChunkSize = (int) Math.min(session.chunkSize, remainingBytes);
                
                // Apply bandwidth throttling
                if (enableBandwidthThrottling) {
                    waitForBandwidth(currentChunkSize);
                }
                
                // Map memory region
                ByteBuffer mappedBuffer = channel.map(FileChannel.MapMode.READ_ONLY, position, currentChunkSize);
                
                // Create a copy for the callback (mapped buffer may become invalid)
                ByteBuffer chunkData = ByteBuffer.allocate(currentChunkSize);
                chunkData.put(mappedBuffer);
                chunkData.flip();
                
                boolean isLastChunk = (position + currentChunkSize >= fileSize);
                
                // Deliver chunk to callback
                try {
                    session.callback.onChunkReceived(session, chunkData, chunkIndex, isLastChunk);
                } catch (Exception e) {
                    logManager.warn("AssetStreamer", "Error in chunk received callback",
                                   "assetId", session.getAssetId(), "chunkIndex", chunkIndex, "error", e.getMessage());
                }
                
                // Update progress
                session.addBytesStreamed(currentChunkSize);
                totalBytesStreamed.addAndGet(currentChunkSize);
                bandwidthUsed.addAndGet(currentChunkSize);
                
                position += currentChunkSize;
                chunkIndex++;
                
                metricsCollector.incrementCounter("asset.streamer.chunks.delivered");
                metricsCollector.recordGauge("asset.streamer.bytes.total", totalBytesStreamed.get());
            }
            
            if (!session.isCancelled()) {
                session.complete();
                
                try {
                    session.callback.onStreamingComplete(session);
                } catch (Exception e) {
                    logManager.warn("AssetStreamer", "Error in streaming complete callback",
                                   "assetId", session.getAssetId(), "error", e.getMessage());
                }
                
                logManager.info("AssetStreamer", "Streaming completed",
                               "assetId", session.getAssetId(),
                               "bytesStreamed", session.getBytesStreamed(),
                               "elapsedTime", session.getElapsedTime(),
                               "averageSpeed", session.getAverageSpeed());
            }
        }
    }
    
    private void performBufferedStreaming(StreamingSession session, Path filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             BufferedInputStream bis = new BufferedInputStream(fis, bufferSize)) {
            
            byte[] buffer = new byte[session.chunkSize];
            int chunkIndex = 0;
            long totalBytesRead = 0;
            
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1 && !session.isCancelled()) {
                // Apply bandwidth throttling
                if (enableBandwidthThrottling) {
                    waitForBandwidth(bytesRead);
                }
                
                // Create ByteBuffer for the chunk
                ByteBuffer chunkData = ByteBuffer.allocate(bytesRead);
                chunkData.put(buffer, 0, bytesRead);
                chunkData.flip();
                
                totalBytesRead += bytesRead;
                boolean isLastChunk = (totalBytesRead >= session.getTotalSize());
                
                // Deliver chunk to callback
                try {
                    session.callback.onChunkReceived(session, chunkData, chunkIndex, isLastChunk);
                } catch (Exception e) {
                    logManager.warn("AssetStreamer", "Error in chunk received callback",
                                   "assetId", session.getAssetId(), "chunkIndex", chunkIndex, "error", e.getMessage());
                }
                
                // Update progress
                session.addBytesStreamed(bytesRead);
                totalBytesStreamed.addAndGet(bytesRead);
                bandwidthUsed.addAndGet(bytesRead);
                
                chunkIndex++;
                
                metricsCollector.incrementCounter("asset.streamer.chunks.delivered");
            }
            
            if (!session.isCancelled()) {
                session.complete();
                
                try {
                    session.callback.onStreamingComplete(session);
                } catch (Exception e) {
                    logManager.warn("AssetStreamer", "Error in streaming complete callback",
                                   "assetId", session.getAssetId(), "error", e.getMessage());
                }
                
                logManager.info("AssetStreamer", "Streaming completed",
                               "assetId", session.getAssetId(),
                               "bytesStreamed", session.getBytesStreamed(),
                               "elapsedTime", session.getElapsedTime(),
                               "averageSpeed", session.getAverageSpeed());
            }
        }
    }
    
    private void waitForBandwidth(int bytesToStream) {
        if (!enableBandwidthThrottling || bandwidthLimit <= 0) {
            return;
        }
        
        long currentBandwidth = bandwidthUsed.get();
        if (currentBandwidth + bytesToStream > bandwidthLimit) {
            // Calculate delay needed to stay within bandwidth limit
            long excessBytes = (currentBandwidth + bytesToStream) - bandwidthLimit;
            long delayMs = (excessBytes * 1000) / bandwidthLimit;
            
            if (delayMs > 0) {
                try {
                    Thread.sleep(Math.min(delayMs, 100)); // Max 100ms delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    
    private void startBandwidthMonitoring() {
        // Reset bandwidth usage every second
        scheduledExecutor.scheduleAtFixedRate(() -> {
            bandwidthUsed.set(0);
            
            // Update metrics
            metricsCollector.recordGauge("asset.streamer.sessions.active", activeSessions.size());
            metricsCollector.recordGauge("asset.streamer.bandwidth.limit", bandwidthLimit);
            
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    /**
     * Shutdown the asset streamer.
     */
    public void shutdown() {
        logManager.info("AssetStreamer", "Shutting down asset streamer");
        
        // Cancel all active sessions
        for (StreamingSession session : activeSessions.values()) {
            session.cancel();
        }
        activeSessions.clear();
        
        // Shutdown executors
        streamingExecutor.shutdown();
        scheduledExecutor.shutdown();
        
        try {
            if (!streamingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                streamingExecutor.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            streamingExecutor.shutdownNow();
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logManager.info("AssetStreamer", "Asset streamer shutdown complete");
    }
    
    /**
     * Streaming statistics.
     */
    public static class StreamingStatistics {
        public final int activeSessions;
        public final long totalBytesStreamed;
        public final long currentBandwidthUsage;
        public final long bandwidthLimit;
        public final double bandwidthUtilization;
        
        StreamingStatistics(int activeSessions, long totalBytesStreamed, 
                           long currentBandwidthUsage, long bandwidthLimit) {
            this.activeSessions = activeSessions;
            this.totalBytesStreamed = totalBytesStreamed;
            this.currentBandwidthUsage = currentBandwidthUsage;
            this.bandwidthLimit = bandwidthLimit;
            this.bandwidthUtilization = bandwidthLimit > 0 ? 
                (double) currentBandwidthUsage / bandwidthLimit : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("StreamingStats{sessions=%d, totalStreamed=%dMB, bandwidth=%.1f%%}",
                               activeSessions, totalBytesStreamed / (1024 * 1024), 
                               bandwidthUtilization * 100.0);
        }
    }
    
    /**
     * Create a new streamer configuration.
     */
    public static Config config() {
        return new Config();
    }
}