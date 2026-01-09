package fps.performance;

import engine.logging.LogManager;
import engine.logging.PerformanceMonitor;
import engine.logging.MetricsCollector;
import engine.profiler.ProfilerManager;
import fps.server.ServerMetrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;

/**
 * Comprehensive performance management system for FPS client.
 * Monitors and optimizes client-side performance to achieve 144+ FPS target.
 */
public class FPSPerformanceManager {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Performance targets
    private static final double TARGET_FPS = 144.0;
    private static final double MIN_FPS = 60.0;
    private static final double MAX_FRAME_TIME_MS = 1000.0 / TARGET_FPS; // ~6.94ms
    private static final long MAX_GC_PAUSE_MS = 5; // Maximum acceptable GC pause
    
    // Core components
    private final PerformanceMonitor performanceMonitor;
    private final MetricsCollector metricsCollector;
    private final ProfilerManager profilerManager;
    
    // Performance tracking
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean monitoring = new AtomicBoolean(false);
    private final AtomicReference<PerformanceState> currentState = new AtomicReference<>(PerformanceState.UNKNOWN);
    
    // Frame timing metrics
    private final AtomicLong frameCount = new AtomicLong(0);
    private final AtomicReference<Double> currentFPS = new AtomicReference<>(0.0);
    private final AtomicReference<Double> averageFrameTime = new AtomicReference<>(0.0);
    private final AtomicReference<Double> frameTimeVariance = new AtomicReference<>(0.0);
    
    // System metrics
    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    private final AtomicLong lastGCTime = new AtomicLong(0);
    private final AtomicLong gcPauseCount = new AtomicLong(0);
    private final AtomicLong totalGCTime = new AtomicLong(0);
    
    // Performance optimization
    private final AtomicBoolean adaptiveOptimization = new AtomicBoolean(true);
    private final AtomicLong lastOptimizationTime = new AtomicLong(0);
    private final Map<String, Object> optimizationSettings = new ConcurrentHashMap<>();
    
    // Monitoring scheduler
    private ScheduledExecutorService monitoringScheduler;
    private static final int MONITORING_INTERVAL_MS = 100; // 10 times per second
    
    // Performance history for trend analysis
    private final PerformanceHistory performanceHistory = new PerformanceHistory();
    
    public FPSPerformanceManager() {
        this.performanceMonitor = new PerformanceMonitor();
        this.metricsCollector = new MetricsCollector();
        this.profilerManager = ProfilerManager.getInstance();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        
        // Initialize default optimization settings
        initializeOptimizationSettings();
    }
    
    /**
     * Initialize the performance manager.
     */
    public void initialize() {
        if (initialized.get()) {
            logManager.warn("FPSPerformanceManager", "Performance manager already initialized");
            return;
        }
        
        logManager.info("FPSPerformanceManager", "Initializing FPS performance manager");
        
        try {
            // Initialize profiler manager if not already done
            if (!profilerManager.isInitialized()) {
                profilerManager.initialize();
            }
            
            // Setup monitoring scheduler
            monitoringScheduler = Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "FPSPerformanceManager-Monitor");
                t.setDaemon(true);
                t.setPriority(Thread.MAX_PRIORITY - 1); // High priority for monitoring
                return t;
            });
            
            // Start monitoring
            startMonitoring();
            
            initialized.set(true);
            currentState.set(PerformanceState.OPTIMAL);
            
            logManager.info("FPSPerformanceManager", "FPS performance manager initialized successfully");
            
        } catch (Exception e) {
            logManager.error("FPSPerformanceManager", "Failed to initialize performance manager", e);
            throw new RuntimeException("Performance manager initialization failed", e);
        }
    }
    
    /**
     * Initialize default optimization settings.
     */
    private void initializeOptimizationSettings() {
        // Rendering optimizations
        optimizationSettings.put("render.culling.enabled", true);
        optimizationSettings.put("render.lod.enabled", true);
        optimizationSettings.put("render.batching.enabled", true);
        optimizationSettings.put("render.occlusion.enabled", true);
        
        // Memory optimizations
        optimizationSettings.put("memory.pooling.enabled", true);
        optimizationSettings.put("memory.gc.tuning", true);
        optimizationSettings.put("memory.cache.size", 256 * 1024 * 1024); // 256MB cache
        
        // Threading optimizations
        optimizationSettings.put("threading.parallel.enabled", true);
        optimizationSettings.put("threading.worker.count", Runtime.getRuntime().availableProcessors());
        
        // Network optimizations
        optimizationSettings.put("network.compression.enabled", true);
        optimizationSettings.put("network.batching.enabled", true);
        optimizationSettings.put("network.prediction.enabled", true);
    }
    
    /**
     * Start performance monitoring.
     */
    private void startMonitoring() {
        if (monitoring.get()) {
            return;
        }
        
        // Schedule regular performance monitoring
        monitoringScheduler.scheduleAtFixedRate(this::updatePerformanceMetrics, 
            0, MONITORING_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        // Schedule adaptive optimization
        monitoringScheduler.scheduleAtFixedRate(this::performAdaptiveOptimization,
            1000, 1000, TimeUnit.MILLISECONDS); // Every second
        
        monitoring.set(true);
        logManager.info("FPSPerformanceManager", "Performance monitoring started");
    }
    
    /**
     * Update performance metrics.
     */
    private void updatePerformanceMetrics() {
        try {
            // Update frame timing
            performanceMonitor.startFrame();
            
            // Collect current metrics
            double fps = performanceMonitor.getCurrentFPS();
            double frameTime = performanceMonitor.getAverageFrameTime();
            
            currentFPS.set(fps);
            averageFrameTime.set(frameTime);
            frameCount.incrementAndGet();
            
            // Update performance state
            updatePerformanceState(fps, frameTime);
            
            // Collect system metrics
            collectSystemMetrics();
            
            // Record metrics
            recordPerformanceMetrics(fps, frameTime);
            
            // Update performance history
            performanceHistory.addSample(fps, frameTime, System.currentTimeMillis());
            
        } catch (Exception e) {
            logManager.error("FPSPerformanceManager", "Error updating performance metrics", e);
        }
    }
    
    /**
     * Update performance state based on current metrics.
     */
    private void updatePerformanceState(double fps, double frameTime) {
        PerformanceState newState;
        
        if (fps >= TARGET_FPS && frameTime <= MAX_FRAME_TIME_MS) {
            newState = PerformanceState.OPTIMAL;
        } else if (fps >= MIN_FPS) {
            newState = PerformanceState.ACCEPTABLE;
        } else if (fps >= MIN_FPS * 0.8) {
            newState = PerformanceState.DEGRADED;
        } else {
            newState = PerformanceState.CRITICAL;
        }
        
        PerformanceState oldState = currentState.getAndSet(newState);
        
        if (oldState != newState) {
            logManager.info("FPSPerformanceManager", "Performance state changed",
                           "oldState", oldState,
                           "newState", newState,
                           "fps", fps,
                           "frameTime", frameTime);
            
            // Trigger immediate optimization if performance degraded
            if (newState.ordinal() > oldState.ordinal()) {
                triggerEmergencyOptimization();
            }
        }
    }
    
    /**
     * Collect system-level metrics.
     */
    private void collectSystemMetrics() {
        // Memory metrics
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        double heapUsagePercent = (double) heapUsed / heapMax * 100.0;
        
        metricsCollector.recordGauge("system.memory.heap.used", heapUsed);
        metricsCollector.recordGauge("system.memory.heap.usage.percent", heapUsagePercent);
        
        // Thread metrics
        int threadCount = threadBean.getThreadCount();
        metricsCollector.recordGauge("system.threads.count", threadCount);
        
        // GC metrics
        collectGCMetrics();
        
        // CPU metrics (approximation)
        long cpuTime = threadBean.getCurrentThreadCpuTime();
        if (cpuTime > 0) {
            metricsCollector.recordGauge("system.cpu.thread.time", cpuTime);
        }
    }
    
    /**
     * Collect garbage collection metrics.
     */
    private void collectGCMetrics() {
        long totalGCTime = 0;
        long totalGCCount = 0;
        
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long gcTime = gcBean.getCollectionTime();
            long gcCount = gcBean.getCollectionCount();
            
            if (gcTime > 0) {
                totalGCTime += gcTime;
                totalGCCount += gcCount;
                
                metricsCollector.recordGauge("system.gc." + gcBean.getName() + ".time", gcTime);
                metricsCollector.recordGauge("system.gc." + gcBean.getName() + ".count", gcCount);
            }
        }
        
        // Check for GC pauses
        long currentGCTime = totalGCTime;
        long lastGC = lastGCTime.getAndSet(currentGCTime);
        
        if (currentGCTime > lastGC) {
            long gcPause = currentGCTime - lastGC;
            if (gcPause > MAX_GC_PAUSE_MS) {
                gcPauseCount.incrementAndGet();
                logManager.warn("FPSPerformanceManager", "Long GC pause detected", 
                               "pauseMs", gcPause, "maxAllowed", MAX_GC_PAUSE_MS);
            }
        }
        
        this.totalGCTime.set(totalGCTime);
        metricsCollector.recordGauge("system.gc.total.time", totalGCTime);
        metricsCollector.recordGauge("system.gc.total.count", totalGCCount);
    }
    
    /**
     * Record performance metrics.
     */
    private void recordPerformanceMetrics(double fps, double frameTime) {
        metricsCollector.recordGauge("performance.fps.current", fps);
        metricsCollector.recordGauge("performance.frametime.average", frameTime);
        metricsCollector.recordGauge("performance.frametime.target", MAX_FRAME_TIME_MS);
        metricsCollector.recordGauge("performance.fps.target", TARGET_FPS);
        
        // Performance efficiency metrics
        double fpsEfficiency = Math.min(fps / TARGET_FPS, 1.0);
        double frameTimeEfficiency = Math.min(MAX_FRAME_TIME_MS / frameTime, 1.0);
        double overallEfficiency = (fpsEfficiency + frameTimeEfficiency) / 2.0;
        
        metricsCollector.recordGauge("performance.efficiency.fps", fpsEfficiency);
        metricsCollector.recordGauge("performance.efficiency.frametime", frameTimeEfficiency);
        metricsCollector.recordGauge("performance.efficiency.overall", overallEfficiency);
        
        // State metrics
        metricsCollector.recordGauge("performance.state", currentState.get().ordinal());
    }
    
    /**
     * Perform adaptive optimization based on current performance.
     */
    private void performAdaptiveOptimization() {
        if (!adaptiveOptimization.get()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long lastOptimization = lastOptimizationTime.get();
        
        // Don't optimize too frequently
        if (currentTime - lastOptimization < 5000) { // 5 second cooldown
            return;
        }
        
        PerformanceState state = currentState.get();
        double fps = currentFPS.get();
        
        try {
            switch (state) {
                case CRITICAL:
                    performEmergencyOptimizations();
                    break;
                case DEGRADED:
                    performAggressiveOptimizations();
                    break;
                case ACCEPTABLE:
                    performModerateOptimizations();
                    break;
                case OPTIMAL:
                    // Try to improve quality if we have headroom
                    if (fps > TARGET_FPS * 1.2) {
                        improveQualitySettings();
                    }
                    break;
            }
            
            lastOptimizationTime.set(currentTime);
            
        } catch (Exception e) {
            logManager.error("FPSPerformanceManager", "Error during adaptive optimization", e);
        }
    }
    
    /**
     * Trigger emergency optimization for critical performance issues.
     */
    private void triggerEmergencyOptimization() {
        logManager.warn("FPSPerformanceManager", "Triggering emergency optimization");
        performEmergencyOptimizations();
    }
    
    /**
     * Perform emergency optimizations for critical performance.
     */
    private void performEmergencyOptimizations() {
        logManager.info("FPSPerformanceManager", "Performing emergency optimizations");
        
        // Disable expensive features
        optimizationSettings.put("render.shadows.enabled", false);
        optimizationSettings.put("render.reflections.enabled", false);
        optimizationSettings.put("render.particles.quality", "low");
        optimizationSettings.put("render.lod.distance", 0.5);
        
        // Aggressive memory management
        System.gc(); // Force garbage collection
        
        // Reduce update frequencies
        optimizationSettings.put("physics.update.frequency", 30);
        optimizationSettings.put("ai.update.frequency", 10);
        
        metricsCollector.incrementCounter("optimization.emergency.triggered");
    }
    
    /**
     * Perform aggressive optimizations for degraded performance.
     */
    private void performAggressiveOptimizations() {
        logManager.info("FPSPerformanceManager", "Performing aggressive optimizations");
        
        // Reduce quality settings
        optimizationSettings.put("render.texture.quality", "medium");
        optimizationSettings.put("render.effects.quality", "medium");
        optimizationSettings.put("render.lod.distance", 0.7);
        
        // Optimize rendering
        optimizationSettings.put("render.culling.distance", 800);
        optimizationSettings.put("render.batching.threshold", 50);
        
        metricsCollector.incrementCounter("optimization.aggressive.triggered");
    }
    
    /**
     * Perform moderate optimizations for acceptable performance.
     */
    private void performModerateOptimizations() {
        logManager.debug("FPSPerformanceManager", "Performing moderate optimizations");
        
        // Fine-tune settings
        optimizationSettings.put("render.lod.distance", 0.8);
        optimizationSettings.put("render.culling.distance", 900);
        
        metricsCollector.incrementCounter("optimization.moderate.triggered");
    }
    
    /**
     * Improve quality settings when performance allows.
     */
    private void improveQualitySettings() {
        logManager.debug("FPSPerformanceManager", "Improving quality settings");
        
        // Increase quality if performance allows
        optimizationSettings.put("render.texture.quality", "high");
        optimizationSettings.put("render.effects.quality", "high");
        optimizationSettings.put("render.lod.distance", 1.0);
        optimizationSettings.put("render.shadows.enabled", true);
        
        metricsCollector.incrementCounter("optimization.quality.improved");
    }
    
    /**
     * Get current performance report.
     */
    public PerformanceReport getPerformanceReport() {
        PerformanceReport report = new PerformanceReport();
        
        report.fps = currentFPS.get();
        report.frameTime = averageFrameTime.get();
        report.state = currentState.get();
        report.frameCount = frameCount.get();
        report.gcPauseCount = gcPauseCount.get();
        report.totalGCTime = totalGCTime.get();
        report.heapUsage = memoryBean.getHeapMemoryUsage().getUsed();
        report.heapMax = memoryBean.getHeapMemoryUsage().getMax();
        report.threadCount = threadBean.getThreadCount();
        
        // Performance trends
        report.performanceTrend = performanceHistory.getPerformanceTrend();
        report.averageFPS = performanceHistory.getAverageFPS();
        report.frameTimeVariance = performanceHistory.getFrameTimeVariance();
        
        return report;
    }
    
    /**
     * Shutdown the performance manager.
     */
    public void shutdown() {
        logManager.info("FPSPerformanceManager", "Shutting down FPS performance manager");
        
        monitoring.set(false);
        
        if (monitoringScheduler != null) {
            monitoringScheduler.shutdown();
            try {
                if (!monitoringScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    monitoringScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                monitoringScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        initialized.set(false);
        logManager.info("FPSPerformanceManager", "FPS performance manager shutdown complete");
    }
    
    // Getters
    public boolean isInitialized() { return initialized.get(); }
    public boolean isMonitoring() { return monitoring.get(); }
    public PerformanceState getCurrentState() { return currentState.get(); }
    public double getCurrentFPS() { return currentFPS.get(); }
    public double getAverageFrameTime() { return averageFrameTime.get(); }
    public long getFrameCount() { return frameCount.get(); }
    public Map<String, Object> getOptimizationSettings() { return new ConcurrentHashMap<>(optimizationSettings); }
    public PerformanceMonitor getPerformanceMonitor() { return performanceMonitor; }
    public MetricsCollector getMetricsCollector() { return metricsCollector; }
    
    // Setters
    public void setAdaptiveOptimization(boolean enabled) { adaptiveOptimization.set(enabled); }
    public void setTargetFPS(double targetFPS) { 
        optimizationSettings.put("performance.target.fps", targetFPS);
    }
    
    /**
     * Performance state enumeration.
     */
    public enum PerformanceState {
        UNKNOWN,
        OPTIMAL,     // >= 144 FPS
        ACCEPTABLE,  // >= 60 FPS
        DEGRADED,    // >= 48 FPS
        CRITICAL     // < 48 FPS
    }
    
    /**
     * Performance report data structure.
     */
    public static class PerformanceReport {
        public double fps;
        public double frameTime;
        public PerformanceState state;
        public long frameCount;
        public long gcPauseCount;
        public long totalGCTime;
        public long heapUsage;
        public long heapMax;
        public int threadCount;
        public String performanceTrend;
        public double averageFPS;
        public double frameTimeVariance;
        
        @Override
        public String toString() {
            return String.format("PerformanceReport{fps=%.1f, frameTime=%.2fms, state=%s, trend=%s}",
                fps, frameTime, state, performanceTrend);
        }
    }
    
    /**
     * Performance history tracking for trend analysis.
     */
    private static class PerformanceHistory {
        private static final int MAX_SAMPLES = 300; // 30 seconds at 10Hz
        private final double[] fpsSamples = new double[MAX_SAMPLES];
        private final double[] frameTimeSamples = new double[MAX_SAMPLES];
        private final long[] timestamps = new long[MAX_SAMPLES];
        private int currentIndex = 0;
        private boolean bufferFull = false;
        
        public synchronized void addSample(double fps, double frameTime, long timestamp) {
            fpsSamples[currentIndex] = fps;
            frameTimeSamples[currentIndex] = frameTime;
            timestamps[currentIndex] = timestamp;
            
            currentIndex = (currentIndex + 1) % MAX_SAMPLES;
            if (currentIndex == 0) {
                bufferFull = true;
            }
        }
        
        public synchronized String getPerformanceTrend() {
            if (!bufferFull && currentIndex < 10) {
                return "INSUFFICIENT_DATA";
            }
            
            int sampleCount = bufferFull ? MAX_SAMPLES : currentIndex;
            double firstHalfAvg = 0;
            double secondHalfAvg = 0;
            
            int halfPoint = sampleCount / 2;
            
            for (int i = 0; i < halfPoint; i++) {
                firstHalfAvg += fpsSamples[i];
            }
            firstHalfAvg /= halfPoint;
            
            for (int i = halfPoint; i < sampleCount; i++) {
                secondHalfAvg += fpsSamples[i];
            }
            secondHalfAvg /= (sampleCount - halfPoint);
            
            double change = (secondHalfAvg - firstHalfAvg) / firstHalfAvg;
            
            if (change > 0.05) return "IMPROVING";
            if (change < -0.05) return "DEGRADING";
            return "STABLE";
        }
        
        public synchronized double getAverageFPS() {
            if (!bufferFull && currentIndex == 0) return 0.0;
            
            int sampleCount = bufferFull ? MAX_SAMPLES : currentIndex;
            double sum = 0;
            for (int i = 0; i < sampleCount; i++) {
                sum += fpsSamples[i];
            }
            return sum / sampleCount;
        }
        
        public synchronized double getFrameTimeVariance() {
            if (!bufferFull && currentIndex < 2) return 0.0;
            
            int sampleCount = bufferFull ? MAX_SAMPLES : currentIndex;
            double mean = 0;
            for (int i = 0; i < sampleCount; i++) {
                mean += frameTimeSamples[i];
            }
            mean /= sampleCount;
            
            double variance = 0;
            for (int i = 0; i < sampleCount; i++) {
                double diff = frameTimeSamples[i] - mean;
                variance += diff * diff;
            }
            return variance / sampleCount;
        }
    }
}