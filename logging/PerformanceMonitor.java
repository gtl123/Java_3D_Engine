package engine.logging;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Real-time performance metrics collection system.
 * Tracks FPS, frame time, memory usage, and other engine performance metrics.
 */
public class PerformanceMonitor {
    
    private static final int FRAME_HISTORY_SIZE = 60; // Track last 60 frames for averaging
    
    // Frame timing
    private final AtomicLong frameCount = new AtomicLong(0);
    private final AtomicReference<Double> currentFPS = new AtomicReference<>(0.0);
    private final AtomicReference<Double> averageFrameTime = new AtomicReference<>(0.0);
    private final AtomicReference<Double> minFrameTime = new AtomicReference<>(Double.MAX_VALUE);
    private final AtomicReference<Double> maxFrameTime = new AtomicReference<>(0.0);
    
    // Memory metrics
    private final MemoryMXBean memoryBean;
    private final AtomicLong heapUsed = new AtomicLong(0);
    private final AtomicLong heapMax = new AtomicLong(0);
    private final AtomicLong nonHeapUsed = new AtomicLong(0);
    
    // Render metrics
    private final AtomicLong renderCalls = new AtomicLong(0);
    private final AtomicLong trianglesRendered = new AtomicLong(0);
    private final AtomicLong textureBinds = new AtomicLong(0);
    
    // Asset loading metrics
    private final AtomicLong assetsLoaded = new AtomicLong(0);
    private final AtomicLong assetLoadsFailed = new AtomicLong(0);
    private final AtomicLong totalAssetLoadTime = new AtomicLong(0);
    private final AtomicLong totalAssetSize = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong hotReloads = new AtomicLong(0);
    private final AtomicLong compressionOperations = new AtomicLong(0);
    private final AtomicLong streamingOperations = new AtomicLong(0);
    
    // Frame time history for averaging
    private final double[] frameTimeHistory = new double[FRAME_HISTORY_SIZE];
    private int frameTimeIndex = 0;
    private boolean frameHistoryFull = false;
    
    // Timing
    private long lastFrameTime = System.nanoTime();
    private long lastFPSUpdate = System.currentTimeMillis();
    private long framesThisSecond = 0;
    
    public PerformanceMonitor() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }
    
    /**
     * Called at the start of each frame to update timing metrics.
     */
    public void startFrame() {
        long currentTime = System.nanoTime();
        double frameTime = (currentTime - lastFrameTime) / 1_000_000.0; // Convert to milliseconds
        lastFrameTime = currentTime;
        
        // Update frame timing
        frameCount.incrementAndGet();
        framesThisSecond++;
        
        // Update frame time statistics
        updateFrameTimeStats(frameTime);
        
        // Update FPS every second
        long currentMillis = System.currentTimeMillis();
        if (currentMillis - lastFPSUpdate >= 1000) {
            currentFPS.set((double) framesThisSecond);
            framesThisSecond = 0;
            lastFPSUpdate = currentMillis;
        }
        
        // Update memory metrics
        updateMemoryMetrics();
    }
    
    private void updateFrameTimeStats(double frameTime) {
        // Update min/max
        minFrameTime.updateAndGet(current -> Math.min(current, frameTime));
        maxFrameTime.updateAndGet(current -> Math.max(current, frameTime));
        
        // Update history for averaging
        synchronized (frameTimeHistory) {
            frameTimeHistory[frameTimeIndex] = frameTime;
            frameTimeIndex = (frameTimeIndex + 1) % FRAME_HISTORY_SIZE;
            if (frameTimeIndex == 0) {
                frameHistoryFull = true;
            }
            
            // Calculate average
            double sum = 0;
            int count = frameHistoryFull ? FRAME_HISTORY_SIZE : frameTimeIndex;
            for (int i = 0; i < count; i++) {
                sum += frameTimeHistory[i];
            }
            averageFrameTime.set(sum / count);
        }
    }
    
    private void updateMemoryMetrics() {
        MemoryUsage heapMemory = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemory = memoryBean.getNonHeapMemoryUsage();
        
        heapUsed.set(heapMemory.getUsed());
        heapMax.set(heapMemory.getMax());
        nonHeapUsed.set(nonHeapMemory.getUsed());
    }
    
    /**
     * Record a render call for statistics.
     */
    public void recordRenderCall() {
        renderCalls.incrementAndGet();
    }
    
    /**
     * Record triangles rendered for statistics.
     * @param triangleCount Number of triangles rendered
     */
    public void recordTrianglesRendered(long triangleCount) {
        trianglesRendered.addAndGet(triangleCount);
    }
    
    /**
     * Record a texture bind operation for statistics.
     */
    public void recordTextureBind() {
        textureBinds.incrementAndGet();
    }
    
    /**
     * Record an asset loading operation.
     * @param loadTimeMs Time taken to load the asset in milliseconds
     * @param assetSizeBytes Size of the loaded asset in bytes
     */
    public void recordAssetLoad(long loadTimeMs, long assetSizeBytes) {
        assetsLoaded.incrementAndGet();
        totalAssetLoadTime.addAndGet(loadTimeMs);
        totalAssetSize.addAndGet(assetSizeBytes);
    }
    
    /**
     * Record a failed asset loading operation.
     */
    public void recordAssetLoadFailed() {
        assetLoadsFailed.incrementAndGet();
    }
    
    /**
     * Record a cache hit.
     */
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }
    
    /**
     * Record a cache miss.
     */
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }
    
    /**
     * Record a hot reload operation.
     */
    public void recordHotReload() {
        hotReloads.incrementAndGet();
    }
    
    /**
     * Record a compression operation.
     */
    public void recordCompressionOperation() {
        compressionOperations.incrementAndGet();
    }
    
    /**
     * Record a streaming operation.
     */
    public void recordStreamingOperation() {
        streamingOperations.incrementAndGet();
    }
    
    /**
     * Record a generic operation with timing.
     * @param operationName Name of the operation
     * @param durationMs Duration in milliseconds
     */
    public void recordOperation(String operationName, long durationMs) {
        // This method is used by AssetPipelineLogger for various operations
        // We can extend this to track specific operation types if needed
    }
    
    /**
     * Reset per-frame counters. Should be called at the end of each frame.
     */
    public void endFrame() {
        // Reset per-frame counters
        renderCalls.set(0);
        trianglesRendered.set(0);
        textureBinds.set(0);
    }
    
    // Getters for metrics
    public long getFrameCount() {
        return frameCount.get();
    }
    
    public double getCurrentFPS() {
        return currentFPS.get();
    }
    
    public double getAverageFrameTime() {
        return averageFrameTime.get();
    }
    
    public double getMinFrameTime() {
        return minFrameTime.get();
    }
    
    public double getMaxFrameTime() {
        return maxFrameTime.get();
    }
    
    public long getHeapUsedMB() {
        return heapUsed.get() / (1024 * 1024);
    }
    
    public long getHeapMaxMB() {
        return heapMax.get() / (1024 * 1024);
    }
    
    public double getHeapUsagePercent() {
        long used = heapUsed.get();
        long max = heapMax.get();
        return max > 0 ? (double) used / max * 100.0 : 0.0;
    }
    
    public long getNonHeapUsedMB() {
        return nonHeapUsed.get() / (1024 * 1024);
    }
    
    public long getRenderCalls() {
        return renderCalls.get();
    }
    
    public long getTrianglesRendered() {
        return trianglesRendered.get();
    }
    
    public long getTextureBinds() {
        return textureBinds.get();
    }
    
    /**
     * Reset all statistics to initial values.
     */
    public void reset() {
        frameCount.set(0);
        currentFPS.set(0.0);
        averageFrameTime.set(0.0);
        minFrameTime.set(Double.MAX_VALUE);
        maxFrameTime.set(0.0);
        renderCalls.set(0);
        trianglesRendered.set(0);
        textureBinds.set(0);
        
        synchronized (frameTimeHistory) {
            for (int i = 0; i < FRAME_HISTORY_SIZE; i++) {
                frameTimeHistory[i] = 0.0;
            }
            frameTimeIndex = 0;
            frameHistoryFull = false;
        }
        
        lastFrameTime = System.nanoTime();
        lastFPSUpdate = System.currentTimeMillis();
        framesThisSecond = 0;
    }
}