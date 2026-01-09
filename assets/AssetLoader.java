package engine.assets;

import engine.logging.LogManager;
import engine.logging.MetricsCollector;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

/**
 * Multi-threaded asset loading system with priority queues and work-stealing.
 * Provides high-performance asset loading with configurable thread pools,
 * priority-based scheduling, and comprehensive load balancing.
 */
public class AssetLoader {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = LogManager.getInstance().getMetricsCollector();
    
    // Thread pool configuration
    private final int coreThreads;
    private final int maxThreads;
    private final long keepAliveTime;
    
    // Executor services
    private final ThreadPoolExecutor highPriorityExecutor;
    private final ThreadPoolExecutor normalPriorityExecutor;
    private final ThreadPoolExecutor lowPriorityExecutor;
    private final ScheduledExecutorService scheduledExecutor;
    
    // Loading queues with priority
    private final PriorityBlockingQueue<LoadTask> pendingTasks;
    private final ConcurrentHashMap<String, LoadTask> activeTasks;
    private final ConcurrentHashMap<String, CompletableFuture<Asset>> loadFutures;
    
    // Statistics
    private final AtomicLong totalLoadsStarted = new AtomicLong(0);
    private final AtomicLong totalLoadsCompleted = new AtomicLong(0);
    private final AtomicLong totalLoadsFailed = new AtomicLong(0);
    private final AtomicLong totalLoadTime = new AtomicLong(0);
    private final AtomicInteger activeLoadCount = new AtomicInteger(0);
    
    // Configuration
    private volatile boolean enableWorkStealing = true;
    private volatile int maxConcurrentLoads = 50;
    private volatile long loadTimeoutMs = 30000; // 30 seconds
    
    /**
     * Asset loading task with priority and metadata.
     */
    private static class LoadTask implements Comparable<LoadTask> {
        final String assetId;
        final String path;
        final AssetType type;
        final int priority;
        final long submitTime;
        final CompletableFuture<Asset> future;
        final AssetFactory factory;
        
        LoadTask(String assetId, String path, AssetType type, int priority, 
                AssetFactory factory, CompletableFuture<Asset> future) {
            this.assetId = assetId;
            this.path = path;
            this.type = type;
            this.priority = priority;
            this.submitTime = System.currentTimeMillis();
            this.future = future;
            this.factory = factory;
        }
        
        @Override
        public int compareTo(LoadTask other) {
            // Higher priority first, then by submit time (FIFO for same priority)
            int priorityCompare = Integer.compare(other.priority, this.priority);
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Long.compare(this.submitTime, other.submitTime);
        }
        
        long getWaitTime() {
            return System.currentTimeMillis() - submitTime;
        }
    }
    
    /**
     * Asset factory interface for creating assets.
     */
    public interface AssetFactory {
        Asset createAsset(String assetId, String path, AssetType type) throws Exception;
    }
    
    /**
     * Loader configuration.
     */
    public static class Config {
        private int coreThreads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        private int maxThreads = Runtime.getRuntime().availableProcessors();
        private long keepAliveTimeSeconds = 60;
        private boolean enableWorkStealing = true;
        private int maxConcurrentLoads = 50;
        private long loadTimeoutMs = 30000;
        
        public Config coreThreads(int threads) { this.coreThreads = Math.max(1, threads); return this; }
        public Config maxThreads(int threads) { this.maxThreads = Math.max(coreThreads, threads); return this; }
        public Config keepAliveTime(long seconds) { this.keepAliveTimeSeconds = Math.max(1, seconds); return this; }
        public Config enableWorkStealing(boolean enable) { this.enableWorkStealing = enable; return this; }
        public Config maxConcurrentLoads(int max) { this.maxConcurrentLoads = Math.max(1, max); return this; }
        public Config loadTimeout(long timeoutMs) { this.loadTimeoutMs = Math.max(1000, timeoutMs); return this; }
        
        public AssetLoader build() {
            return new AssetLoader(this);
        }
    }
    
    /**
     * Create asset loader with default configuration.
     */
    public AssetLoader() {
        this(new Config());
    }
    
    /**
     * Create asset loader with specified configuration.
     */
    private AssetLoader(Config config) {
        this.coreThreads = config.coreThreads;
        this.maxThreads = config.maxThreads;
        this.keepAliveTime = config.keepAliveTimeSeconds;
        this.enableWorkStealing = config.enableWorkStealing;
        this.maxConcurrentLoads = config.maxConcurrentLoads;
        this.loadTimeoutMs = config.loadTimeoutMs;
        
        // Initialize thread pools with different priorities
        this.highPriorityExecutor = createThreadPool("AssetLoader-High", 2, 4);
        this.normalPriorityExecutor = createThreadPool("AssetLoader-Normal", coreThreads, maxThreads);
        this.lowPriorityExecutor = createThreadPool("AssetLoader-Low", 1, 2);
        
        // Scheduled executor for timeouts and monitoring
        this.scheduledExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "AssetLoader-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // Initialize data structures
        this.pendingTasks = new PriorityBlockingQueue<>();
        this.activeTasks = new ConcurrentHashMap<>();
        this.loadFutures = new ConcurrentHashMap<>();
        
        // Start background processing
        startBackgroundProcessing();
        
        logManager.info("AssetLoader", "Asset loader initialized",
                       "coreThreads", coreThreads,
                       "maxThreads", maxThreads,
                       "workStealing", enableWorkStealing,
                       "maxConcurrentLoads", maxConcurrentLoads);
    }
    
    /**
     * Load an asset asynchronously.
     * @param assetId Asset identifier
     * @param path Asset path
     * @param type Asset type
     * @param priority Loading priority (higher = more urgent)
     * @param factory Asset factory for creating the asset
     * @return CompletableFuture that completes when asset is loaded
     */
    public CompletableFuture<Asset> loadAsync(String assetId, String path, AssetType type, 
                                            int priority, AssetFactory factory) {
        if (assetId == null || path == null || type == null || factory == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid parameters"));
        }
        
        // Check if already loading
        CompletableFuture<Asset> existingFuture = loadFutures.get(assetId);
        if (existingFuture != null) {
            return existingFuture;
        }
        
        // Create new load future
        CompletableFuture<Asset> future = new CompletableFuture<Asset>()
            .orTimeout(loadTimeoutMs, TimeUnit.MILLISECONDS);
        
        // Check if we can add to existing future
        CompletableFuture<Asset> raceFuture = loadFutures.putIfAbsent(assetId, future);
        if (raceFuture != null) {
            return raceFuture; // Another thread started loading
        }
        
        // Create and queue load task
        LoadTask task = new LoadTask(assetId, path, type, priority, factory, future);
        pendingTasks.offer(task);
        
        totalLoadsStarted.incrementAndGet();
        metricsCollector.incrementCounter("asset.loads.started");
        
        logManager.debug("AssetLoader", "Asset load queued",
                        "assetId", assetId,
                        "path", path,
                        "type", type.getTypeName(),
                        "priority", priority,
                        "queueSize", pendingTasks.size());
        
        return future;
    }
    
    /**
     * Load an asset with default priority.
     */
    public CompletableFuture<Asset> loadAsync(String assetId, String path, AssetType type, AssetFactory factory) {
        return loadAsync(assetId, path, type, type.getLoadingPriority(), factory);
    }
    
    /**
     * Cancel a pending asset load.
     * @param assetId Asset identifier
     * @return true if cancelled, false if not found or already completed
     */
    public boolean cancelLoad(String assetId) {
        CompletableFuture<Asset> future = loadFutures.remove(assetId);
        if (future != null) {
            boolean cancelled = future.cancel(true);
            
            // Remove from active tasks if present
            LoadTask activeTask = activeTasks.remove(assetId);
            if (activeTask != null) {
                activeLoadCount.decrementAndGet();
            }
            
            logManager.debug("AssetLoader", "Asset load cancelled",
                           "assetId", assetId, "cancelled", cancelled);
            
            return cancelled;
        }
        return false;
    }
    
    /**
     * Get loading statistics.
     */
    public LoaderStatistics getStatistics() {
        return new LoaderStatistics(
            totalLoadsStarted.get(),
            totalLoadsCompleted.get(),
            totalLoadsFailed.get(),
            activeLoadCount.get(),
            pendingTasks.size(),
            totalLoadTime.get(),
            getAverageLoadTime()
        );
    }
    
    /**
     * Check if an asset is currently being loaded.
     */
    public boolean isLoading(String assetId) {
        return loadFutures.containsKey(assetId);
    }
    
    /**
     * Get the number of pending load tasks.
     */
    public int getPendingTaskCount() {
        return pendingTasks.size();
    }
    
    /**
     * Get the number of active load tasks.
     */
    public int getActiveTaskCount() {
        return activeLoadCount.get();
    }
    
    private ThreadPoolExecutor createThreadPool(String namePrefix, int coreSize, int maxSize) {
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, namePrefix + "-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        };
        
        return new ThreadPoolExecutor(
            coreSize, maxSize,
            keepAliveTime, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            factory,
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
    
    private void startBackgroundProcessing() {
        // Task dispatcher - moves tasks from queue to appropriate executor
        scheduledExecutor.scheduleWithFixedDelay(this::processPendingTasks, 0, 10, TimeUnit.MILLISECONDS);
        
        // Statistics and monitoring
        scheduledExecutor.scheduleWithFixedDelay(this::updateStatistics, 1, 1, TimeUnit.SECONDS);
        
        // Timeout cleanup
        scheduledExecutor.scheduleWithFixedDelay(this::cleanupTimeouts, 5, 5, TimeUnit.SECONDS);
    }
    
    private void processPendingTasks() {
        try {
            while (activeLoadCount.get() < maxConcurrentLoads && !pendingTasks.isEmpty()) {
                LoadTask task = pendingTasks.poll();
                if (task != null && !task.future.isDone()) {
                    executeLoadTask(task);
                }
            }
        } catch (Exception e) {
            logManager.error("AssetLoader", "Error processing pending tasks", e);
        }
    }
    
    private void executeLoadTask(LoadTask task) {
        activeTasks.put(task.assetId, task);
        activeLoadCount.incrementAndGet();
        
        // Choose executor based on priority
        ThreadPoolExecutor executor = selectExecutor(task.priority);
        
        executor.submit(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                logManager.debug("AssetLoader", "Starting asset load",
                               "assetId", task.assetId,
                               "waitTime", task.getWaitTime());
                
                // Create the asset using the factory
                Asset asset = task.factory.createAsset(task.assetId, task.path, task.type);
                
                long loadTime = System.currentTimeMillis() - startTime;
                totalLoadTime.addAndGet(loadTime);
                totalLoadsCompleted.incrementAndGet();
                
                // Complete the future
                task.future.complete(asset);
                
                metricsCollector.incrementCounter("asset.loads.completed");
                metricsCollector.recordTimer("asset.load.time", loadTime);
                
                logManager.info("AssetLoader", "Asset loaded successfully",
                              "assetId", task.assetId,
                              "loadTime", loadTime,
                              "waitTime", task.getWaitTime());
                
            } catch (Exception e) {
                totalLoadsFailed.incrementAndGet();
                task.future.completeExceptionally(e);
                
                metricsCollector.incrementCounter("asset.loads.failed");
                
                logManager.error("AssetLoader", "Asset load failed",
                               "assetId", task.assetId,
                               "path", task.path,
                               "error", e.getMessage(), e);
            } finally {
                activeTasks.remove(task.assetId);
                loadFutures.remove(task.assetId);
                activeLoadCount.decrementAndGet();
            }
        });
    }
    
    private ThreadPoolExecutor selectExecutor(int priority) {
        if (priority >= 80) {
            return highPriorityExecutor;
        } else if (priority >= 40) {
            return normalPriorityExecutor;
        } else {
            return lowPriorityExecutor;
        }
    }
    
    private void updateStatistics() {
        try {
            metricsCollector.recordGauge("asset.loader.pending", pendingTasks.size());
            metricsCollector.recordGauge("asset.loader.active", activeLoadCount.get());
            metricsCollector.recordGauge("asset.loader.threads.high", highPriorityExecutor.getActiveCount());
            metricsCollector.recordGauge("asset.loader.threads.normal", normalPriorityExecutor.getActiveCount());
            metricsCollector.recordGauge("asset.loader.threads.low", lowPriorityExecutor.getActiveCount());
        } catch (Exception e) {
            logManager.warn("AssetLoader", "Error updating statistics", "error", e.getMessage());
        }
    }
    
    private void cleanupTimeouts() {
        try {
            long currentTime = System.currentTimeMillis();
            
            // Clean up timed out futures
            loadFutures.entrySet().removeIf(entry -> {
                CompletableFuture<Asset> future = entry.getValue();
                return future.isDone() && future.isCompletedExceptionally();
            });
            
            // Log long-running tasks
            for (LoadTask task : activeTasks.values()) {
                long runTime = currentTime - task.submitTime;
                if (runTime > loadTimeoutMs / 2) {
                    logManager.warn("AssetLoader", "Long-running asset load detected",
                                   "assetId", task.assetId,
                                   "runTime", runTime,
                                   "path", task.path);
                }
            }
        } catch (Exception e) {
            logManager.warn("AssetLoader", "Error during timeout cleanup", "error", e.getMessage());
        }
    }
    
    private double getAverageLoadTime() {
        long completed = totalLoadsCompleted.get();
        return completed > 0 ? (double) totalLoadTime.get() / completed : 0.0;
    }
    
    /**
     * Shutdown the asset loader.
     */
    public void shutdown() {
        logManager.info("AssetLoader", "Shutting down asset loader");
        
        // Cancel all pending futures
        for (CompletableFuture<Asset> future : loadFutures.values()) {
            future.cancel(true);
        }
        
        // Shutdown executors
        shutdownExecutor(highPriorityExecutor, "high priority");
        shutdownExecutor(normalPriorityExecutor, "normal priority");
        shutdownExecutor(lowPriorityExecutor, "low priority");
        shutdownExecutor(scheduledExecutor, "scheduled");
        
        // Clear data structures
        pendingTasks.clear();
        activeTasks.clear();
        loadFutures.clear();
        
        logManager.info("AssetLoader", "Asset loader shutdown complete");
    }
    
    private void shutdownExecutor(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                logManager.warn("AssetLoader", "Forcing shutdown of " + name + " executor");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Loader statistics data class.
     */
    public static class LoaderStatistics {
        public final long totalStarted;
        public final long totalCompleted;
        public final long totalFailed;
        public final int activeLoads;
        public final int pendingLoads;
        public final long totalLoadTime;
        public final double averageLoadTime;
        public final double successRate;
        
        LoaderStatistics(long totalStarted, long totalCompleted, long totalFailed,
                        int activeLoads, int pendingLoads, long totalLoadTime, double averageLoadTime) {
            this.totalStarted = totalStarted;
            this.totalCompleted = totalCompleted;
            this.totalFailed = totalFailed;
            this.activeLoads = activeLoads;
            this.pendingLoads = pendingLoads;
            this.totalLoadTime = totalLoadTime;
            this.averageLoadTime = averageLoadTime;
            
            long totalFinished = totalCompleted + totalFailed;
            this.successRate = totalFinished > 0 ? (double) totalCompleted / totalFinished : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("LoaderStats{started=%d, completed=%d, failed=%d, active=%d, pending=%d, avgTime=%.1fms, success=%.1f%%}",
                               totalStarted, totalCompleted, totalFailed, activeLoads, pendingLoads, 
                               averageLoadTime, successRate * 100.0);
        }
    }
    
    /**
     * Create a new loader configuration.
     */
    public static Config config() {
        return new Config();
    }
}