package engine.profiler;

import engine.logging.LogManager;
import engine.logging.MetricsCollector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Integration layer between the profiler system and the logging/metrics infrastructure.
 * Handles automatic metrics collection, logging integration, and data synchronization.
 */
public class ProfilerIntegration {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = logManager.getMetricsCollector();
    
    private final ProfilerManager profilerManager;
    private final AtomicBoolean integrationActive = new AtomicBoolean(false);
    
    // Scheduled metrics collection
    private ScheduledExecutorService metricsScheduler;
    private volatile boolean autoMetricsEnabled = false;
    
    // Metrics collection intervals
    private static final long DEFAULT_METRICS_INTERVAL_MS = 1000; // 1 second
    private static final long DEFAULT_LOG_INTERVAL_MS = 30000; // 30 seconds
    
    // Performance tracking
    private final Map<String, Long> lastMetricsCollection = new ConcurrentHashMap<>();
    private final Map<String, ProfilerData> lastProfilerData = new ConcurrentHashMap<>();
    
    public ProfilerIntegration(ProfilerManager profilerManager) {
        this.profilerManager = profilerManager;
    }
    
    /**
     * Initialize the profiler integration.
     */
    public void initialize() {
        if (integrationActive.get()) {
            logManager.warn("ProfilerIntegration", "Profiler integration already initialized");
            return;
        }
        
        logManager.info("ProfilerIntegration", "Initializing profiler integration");
        
        try {
            // Initialize metrics scheduler
            metricsScheduler = Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "ProfilerIntegration-MetricsCollector");
                t.setDaemon(true);
                return t;
            });
            
            // Setup automatic metrics collection
            setupAutomaticMetricsCollection();
            
            // Setup periodic logging
            setupPeriodicLogging();
            
            integrationActive.set(true);
            
            logManager.info("ProfilerIntegration", "Profiler integration initialized successfully");
            
        } catch (Exception e) {
            logManager.error("ProfilerIntegration", "Failed to initialize profiler integration", e);
            throw new RuntimeException("Profiler integration initialization failed", e);
        }
    }
    
    /**
     * Setup automatic metrics collection from profilers.
     */
    private void setupAutomaticMetricsCollection() {
        if (metricsScheduler != null && !autoMetricsEnabled) {
            metricsScheduler.scheduleAtFixedRate(() -> {
                try {
                    if (profilerManager.isEnabled()) {
                        collectAllProfilerMetrics();
                    }
                } catch (Exception e) {
                    logManager.error("ProfilerIntegration", "Error in automatic metrics collection", e);
                }
            }, DEFAULT_METRICS_INTERVAL_MS, DEFAULT_METRICS_INTERVAL_MS, TimeUnit.MILLISECONDS);
            
            autoMetricsEnabled = true;
            logManager.debug("ProfilerIntegration", "Automatic metrics collection enabled",
                           "intervalMs", DEFAULT_METRICS_INTERVAL_MS);
        }
    }
    
    /**
     * Setup periodic profiler status logging.
     */
    private void setupPeriodicLogging() {
        if (metricsScheduler != null) {
            metricsScheduler.scheduleAtFixedRate(() -> {
                try {
                    if (profilerManager.isEnabled()) {
                        logProfilerStatus();
                    }
                } catch (Exception e) {
                    logManager.error("ProfilerIntegration", "Error in periodic logging", e);
                }
            }, DEFAULT_LOG_INTERVAL_MS, DEFAULT_LOG_INTERVAL_MS, TimeUnit.MILLISECONDS);
            
            logManager.debug("ProfilerIntegration", "Periodic logging enabled",
                           "intervalMs", DEFAULT_LOG_INTERVAL_MS);
        }
    }
    
    /**
     * Collect metrics from all active profilers.
     */
    public void collectAllProfilerMetrics() {
        if (!profilerManager.isEnabled()) {
            return;
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, IProfiler> profilers = profilerManager.getAllProfilers();
            
            for (Map.Entry<String, IProfiler> entry : profilers.entrySet()) {
                String profilerType = entry.getKey();
                IProfiler profiler = entry.getValue();
                
                if (profiler.isActive()) {
                    collectProfilerMetrics(profilerType, profiler);
                }
            }
            
            // Record integration performance
            long duration = System.currentTimeMillis() - startTime;
            metricsCollector.recordTime("profiler.integration.collection.time", duration);
            metricsCollector.incrementCounter("profiler.integration.collection.count");
            
        } catch (Exception e) {
            logManager.error("ProfilerIntegration", "Error collecting profiler metrics", e);
            metricsCollector.incrementCounter("profiler.integration.collection.errors");
        }
    }
    
    /**
     * Collect metrics from a specific profiler.
     */
    private void collectProfilerMetrics(String profilerType, IProfiler profiler) {
        try {
            long collectionStart = System.currentTimeMillis();
            
            // Collect profiler data
            ProfilerData data = profiler.collectData();
            if (data == null) {
                return;
            }
            
            // Record collection timing
            long collectionTime = System.currentTimeMillis() - collectionStart;
            logManager.logProfilerTiming(profilerType, "dataCollection", collectionTime);
            
            // Store current data for comparison
            ProfilerData previousData = lastProfilerData.put(profilerType, data);
            
            // Record metrics in MetricsCollector
            Map<String, Object> metrics = data.getMetrics();
            metricsCollector.recordProfilerMetrics(profilerType, metrics);
            
            // Log significant changes
            if (previousData != null) {
                logSignificantChanges(profilerType, previousData, data);
            }
            
            // Update last collection time
            lastMetricsCollection.put(profilerType, System.currentTimeMillis());
            
            logManager.debug("ProfilerIntegration", "Collected metrics from profiler",
                           "profilerType", profilerType, "metricCount", metrics.size(),
                           "collectionTimeMs", collectionTime);
            
        } catch (Exception e) {
            logManager.error("ProfilerIntegration", "Error collecting metrics from profiler", e,
                           "profilerType", profilerType);
            metricsCollector.incrementCounter("profiler.integration.profiler.errors." + profilerType);
        }
    }
    
    /**
     * Log significant changes between profiler data collections.
     */
    private void logSignificantChanges(String profilerType, ProfilerData previous, ProfilerData current) {
        Map<String, Object> previousMetrics = previous.getMetrics();
        Map<String, Object> currentMetrics = current.getMetrics();
        
        for (Map.Entry<String, Object> entry : currentMetrics.entrySet()) {
            String metricName = entry.getKey();
            Object currentValue = entry.getValue();
            Object previousValue = previousMetrics.get(metricName);
            
            if (previousValue != null && currentValue instanceof Number && previousValue instanceof Number) {
                double current = ((Number) currentValue).doubleValue();
                double prev = ((Number) previousValue).doubleValue();
                
                // Log significant changes (>20% change for most metrics)
                if (prev > 0) {
                    double changePercent = Math.abs((current - prev) / prev) * 100;
                    
                    if (changePercent > 20.0) {
                        logManager.info("ProfilerIntegration", "Significant metric change detected",
                                      "profilerType", profilerType, "metric", metricName,
                                      "previousValue", prev, "currentValue", current,
                                      "changePercent", String.format("%.1f%%", changePercent));
                        
                        // Record significant changes as metrics
                        metricsCollector.incrementCounter("profiler.integration.significantChanges." + profilerType);
                    }
                }
            }
        }
    }
    
    /**
     * Log current profiler status and summary metrics.
     */
    private void logProfilerStatus() {
        try {
            Map<String, IProfiler> profilers = profilerManager.getAllProfilers();
            int activeProfilers = 0;
            int totalProfilers = profilers.size();
            
            for (IProfiler profiler : profilers.values()) {
                if (profiler.isActive()) {
                    activeProfilers++;
                }
            }
            
            logManager.info("ProfilerIntegration", "Profiler status summary",
                          "totalProfilers", totalProfilers, "activeProfilers", activeProfilers,
                          "enabled", profilerManager.isEnabled());
            
            // Log metrics summary
            logMetricsSummary();
            
        } catch (Exception e) {
            logManager.error("ProfilerIntegration", "Error logging profiler status", e);
        }
    }
    
    /**
     * Log summary of collected metrics.
     */
    private void logMetricsSummary() {
        Map<String, MetricsCollector.ProfilerMetrics> allProfilerMetrics = metricsCollector.getAllProfilerMetrics();
        
        for (Map.Entry<String, MetricsCollector.ProfilerMetrics> entry : allProfilerMetrics.entrySet()) {
            String profilerType = entry.getKey();
            MetricsCollector.ProfilerMetrics metrics = entry.getValue();
            
            logManager.debug("ProfilerIntegration", "Profiler metrics summary",
                           "profilerType", profilerType, "updateCount", metrics.getUpdateCount(),
                           "lastUpdate", System.currentTimeMillis() - metrics.getLastUpdateTime() + "ms ago",
                           "metricCount", metrics.getMetrics().size());
        }
    }
    
    /**
     * Force immediate collection of all profiler metrics.
     */
    public void forceMetricsCollection() {
        logManager.info("ProfilerIntegration", "Forcing immediate metrics collection");
        collectAllProfilerMetrics();
    }
    
    /**
     * Get metrics for a specific profiler type.
     */
    public Map<String, Object> getProfilerMetrics(String profilerType) {
        return metricsCollector.getProfilerMetricsByType(profilerType);
    }
    
    /**
     * Get all profiler metrics.
     */
    public Map<String, MetricsCollector.ProfilerMetrics> getAllProfilerMetrics() {
        return metricsCollector.getAllProfilerMetrics();
    }
    
    /**
     * Reset metrics for a specific profiler.
     */
    public void resetProfilerMetrics(String profilerType) {
        metricsCollector.resetProfilerMetrics(profilerType);
        lastMetricsCollection.remove(profilerType);
        lastProfilerData.remove(profilerType);
        
        logManager.info("ProfilerIntegration", "Reset metrics for profiler", "profilerType", profilerType);
    }
    
    /**
     * Reset all profiler metrics.
     */
    public void resetAllMetrics() {
        for (String profilerType : lastMetricsCollection.keySet()) {
            resetProfilerMetrics(profilerType);
        }
        
        logManager.info("ProfilerIntegration", "Reset all profiler metrics");
    }
    
    /**
     * Generate a comprehensive metrics report.
     */
    public ProfilerMetricsReport generateMetricsReport() {
        ProfilerMetricsReport report = new ProfilerMetricsReport();
        
        // Add profiler metrics
        Map<String, MetricsCollector.ProfilerMetrics> allMetrics = getAllProfilerMetrics();
        for (Map.Entry<String, MetricsCollector.ProfilerMetrics> entry : allMetrics.entrySet()) {
            report.addProfilerMetrics(entry.getKey(), entry.getValue());
        }
        
        // Add integration metrics
        report.addIntegrationMetrics(getIntegrationMetrics());
        
        // Add system metrics
        report.addSystemMetrics(getSystemMetrics());
        
        return report;
    }
    
    /**
     * Get integration-specific metrics.
     */
    private Map<String, Object> getIntegrationMetrics() {
        Map<String, Object> integrationMetrics = new ConcurrentHashMap<>();
        
        integrationMetrics.put("active", integrationActive.get());
        integrationMetrics.put("autoMetricsEnabled", autoMetricsEnabled);
        integrationMetrics.put("profilerCount", lastMetricsCollection.size());
        integrationMetrics.put("totalCollections", metricsCollector.getCounter("profiler.integration.collection.count"));
        integrationMetrics.put("collectionErrors", metricsCollector.getCounter("profiler.integration.collection.errors"));
        integrationMetrics.put("averageCollectionTime", metricsCollector.getTimer("profiler.integration.collection.time"));
        
        return integrationMetrics;
    }
    
    /**
     * Get system-level metrics.
     */
    private Map<String, Object> getSystemMetrics() {
        Map<String, Object> systemMetrics = new ConcurrentHashMap<>();
        
        Runtime runtime = Runtime.getRuntime();
        systemMetrics.put("totalMemoryMB", runtime.totalMemory() / (1024 * 1024));
        systemMetrics.put("freeMemoryMB", runtime.freeMemory() / (1024 * 1024));
        systemMetrics.put("usedMemoryMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        systemMetrics.put("maxMemoryMB", runtime.maxMemory() / (1024 * 1024));
        systemMetrics.put("availableProcessors", runtime.availableProcessors());
        systemMetrics.put("currentTimeMs", System.currentTimeMillis());
        
        return systemMetrics;
    }
    
    /**
     * Shutdown the profiler integration.
     */
    public void shutdown() {
        logManager.info("ProfilerIntegration", "Shutting down profiler integration");
        
        integrationActive.set(false);
        autoMetricsEnabled = false;
        
        if (metricsScheduler != null) {
            metricsScheduler.shutdown();
            try {
                if (!metricsScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    metricsScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                metricsScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        lastMetricsCollection.clear();
        lastProfilerData.clear();
        
        logManager.info("ProfilerIntegration", "Profiler integration shutdown complete");
    }
    
    // Getters
    public boolean isActive() { return integrationActive.get(); }
    public boolean isAutoMetricsEnabled() { return autoMetricsEnabled; }
    public ProfilerManager getProfilerManager() { return profilerManager; }
    
    /**
     * Comprehensive metrics report container.
     */
    public static class ProfilerMetricsReport {
        private final long timestamp = System.currentTimeMillis();
        private final Map<String, MetricsCollector.ProfilerMetrics> profilerMetrics = new ConcurrentHashMap<>();
        private final Map<String, Object> integrationMetrics = new ConcurrentHashMap<>();
        private final Map<String, Object> systemMetrics = new ConcurrentHashMap<>();
        
        public void addProfilerMetrics(String profilerType, MetricsCollector.ProfilerMetrics metrics) {
            profilerMetrics.put(profilerType, metrics);
        }
        
        public void addIntegrationMetrics(Map<String, Object> metrics) {
            integrationMetrics.putAll(metrics);
        }
        
        public void addSystemMetrics(Map<String, Object> metrics) {
            systemMetrics.putAll(metrics);
        }
        
        // Getters
        public long getTimestamp() { return timestamp; }
        public Map<String, MetricsCollector.ProfilerMetrics> getProfilerMetrics() { return new ConcurrentHashMap<>(profilerMetrics); }
        public Map<String, Object> getIntegrationMetrics() { return new ConcurrentHashMap<>(integrationMetrics); }
        public Map<String, Object> getSystemMetrics() { return new ConcurrentHashMap<>(systemMetrics); }
    }
}