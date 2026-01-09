package engine.profiler;

import engine.logging.LogManager;
import engine.logging.MetricsCollector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

/**
 * Central manager for all profiler components.
 * Coordinates profiler lifecycle, data collection, and reporting.
 */
public class ProfilerManager {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static ProfilerManager instance;
    
    private final ProfilerConfiguration config;
    private final Map<String, IProfiler> profilers = new ConcurrentHashMap<>();
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    // Profiler components
    private PerformanceProfiler performanceProfiler;
    private MemoryProfiler memoryProfiler;
    private NetworkProfiler networkProfiler;
    private RenderProfiler renderProfiler;
    private DebugRenderer debugRenderer;
    private PerformanceAnalyzer performanceAnalyzer;
    private ProfilerUI profilerUI;
    
    // Integration with logging and metrics
    private ProfilerIntegration profilerIntegration;
    
    // Scheduled tasks
    private ScheduledExecutorService scheduler;
    private volatile boolean autoUpdateEnabled = false;
    
    // Data collection
    private volatile ProfilerReport lastReport;
    private final List<ProfilerReport> reportHistory = new ArrayList<>();
    private static final int MAX_REPORT_HISTORY = 100;
    
    private ProfilerManager() {
        this.config = new ProfilerConfiguration();
    }
    
    public static synchronized ProfilerManager getInstance() {
        if (instance == null) {
            instance = new ProfilerManager();
        }
        return instance;
    }
    
    /**
     * Initialize the profiler manager and all profiler components.
     */
    public void initialize() {
        if (initialized.get()) {
            logManager.warn("ProfilerManager", "Profiler manager already initialized");
            return;
        }
        
        logManager.info("ProfilerManager", "Initializing profiler manager");
        
        try {
            // Initialize scheduler
            scheduler = Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "ProfilerManager-Scheduler");
                t.setDaemon(true);
                return t;
            });
            
            // Initialize profiler components
            initializeProfilers();
            
            // Initialize profiler integration
            profilerIntegration = new ProfilerIntegration(this);
            profilerIntegration.initialize();
            
            // Setup auto-update if enabled
            if (config.isAutoStart()) {
                setupAutoUpdate();
            }
            
            initialized.set(true);
            
            logManager.info("ProfilerManager", "Profiler manager initialized successfully",
                           "profilerCount", profilers.size());
            
        } catch (Exception e) {
            logManager.error("ProfilerManager", "Failed to initialize profiler manager", e);
            throw new RuntimeException("Profiler manager initialization failed", e);
        }
    }
    
    /**
     * Initialize all profiler components.
     */
    private void initializeProfilers() {
        // Performance profiler
        if (config.isPerformanceEnabled()) {
            performanceProfiler = new PerformanceProfiler(config);
            performanceProfiler.initialize();
            profilers.put("performance", performanceProfiler);
            logManager.debug("ProfilerManager", "Performance profiler initialized");
        }
        
        // Memory profiler
        if (config.isMemoryEnabled()) {
            memoryProfiler = new MemoryProfiler(config);
            memoryProfiler.initialize();
            profilers.put("memory", memoryProfiler);
            logManager.debug("ProfilerManager", "Memory profiler initialized");
        }
        
        // Network profiler
        if (config.isNetworkEnabled()) {
            networkProfiler = new NetworkProfiler(config);
            networkProfiler.initialize();
            profilers.put("network", networkProfiler);
            logManager.debug("ProfilerManager", "Network profiler initialized");
        }
        
        // Render profiler
        if (config.isRenderEnabled()) {
            renderProfiler = new RenderProfiler(config);
            renderProfiler.initialize();
            profilers.put("render", renderProfiler);
            logManager.debug("ProfilerManager", "Render profiler initialized");
        }
        
        // Debug renderer
        if (config.isDebugEnabled()) {
            debugRenderer = new DebugRenderer(config);
            debugRenderer.initialize();
            profilers.put("debug", debugRenderer);
            logManager.debug("ProfilerManager", "Debug renderer initialized");
        }
        
        // Performance analyzer
        if (config.isAnalyzerEnabled()) {
            performanceAnalyzer = new PerformanceAnalyzer(config, this);
            performanceAnalyzer.initialize();
            profilers.put("analyzer", performanceAnalyzer);
            logManager.debug("ProfilerManager", "Performance analyzer initialized");
        }
        
        // Profiler UI
        if (config.isUiEnabled()) {
            profilerUI = new ProfilerUI(config, this);
            profilerUI.initialize();
            profilers.put("ui", profilerUI);
            logManager.debug("ProfilerManager", "Profiler UI initialized");
        }
    }
    
    /**
     * Setup automatic profiler updates.
     */
    private void setupAutoUpdate() {
        if (scheduler != null && !autoUpdateEnabled) {
            int updateInterval = config.getUpdateIntervalMs();
            
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    if (enabled.get()) {
                        update(updateInterval / 1000.0f);
                    }
                } catch (Exception e) {
                    logManager.error("ProfilerManager", "Error in auto-update", e);
                }
            }, updateInterval, updateInterval, TimeUnit.MILLISECONDS);
            
            autoUpdateEnabled = true;
            logManager.info("ProfilerManager", "Auto-update enabled", "intervalMs", updateInterval);
        }
    }
    
    /**
     * Enable profiling.
     */
    public void enable() {
        if (!initialized.get()) {
            initialize();
        }
        
        if (enabled.get()) {
            logManager.debug("ProfilerManager", "Profiler manager already enabled");
            return;
        }
        
        logManager.info("ProfilerManager", "Enabling profiler manager");
        
        // Start all profilers
        for (IProfiler profiler : profilers.values()) {
            try {
                profiler.start();
                logManager.debug("ProfilerManager", "Started profiler", "type", profiler.getProfilerType());
            } catch (Exception e) {
                logManager.error("ProfilerManager", "Failed to start profiler", e, 
                               "type", profiler.getProfilerType());
            }
        }
        
        enabled.set(true);
        
        // Setup auto-update if not already enabled
        if (!autoUpdateEnabled) {
            setupAutoUpdate();
        }
        
        logManager.info("ProfilerManager", "Profiler manager enabled");
    }
    
    /**
     * Disable profiling.
     */
    public void disable() {
        if (!enabled.get()) {
            logManager.debug("ProfilerManager", "Profiler manager already disabled");
            return;
        }
        
        logManager.info("ProfilerManager", "Disabling profiler manager");
        
        // Stop all profilers
        for (IProfiler profiler : profilers.values()) {
            try {
                profiler.stop();
                logManager.debug("ProfilerManager", "Stopped profiler", "type", profiler.getProfilerType());
            } catch (Exception e) {
                logManager.error("ProfilerManager", "Failed to stop profiler", e,
                               "type", profiler.getProfilerType());
            }
        }
        
        enabled.set(false);
        
        logManager.info("ProfilerManager", "Profiler manager disabled");
    }
    
    /**
     * Update all active profilers.
     */
    public void update(float deltaTime) {
        if (!enabled.get()) {
            return;
        }
        
        // Update all profilers
        for (IProfiler profiler : profilers.values()) {
            try {
                if (profiler.isActive()) {
                    profiler.update(deltaTime);
                }
            } catch (Exception e) {
                logManager.error("ProfilerManager", "Error updating profiler", e,
                               "type", profiler.getProfilerType());
            }
        }
    }
    
    /**
     * Generate a comprehensive profiling report.
     */
    public ProfilerReport generateReport() {
        ProfilerReport report = new ProfilerReport();
        
        // Collect data from all active profilers
        for (IProfiler profiler : profilers.values()) {
            try {
                if (profiler.isActive()) {
                    ProfilerData data = profiler.collectData();
                    if (data != null) {
                        report.addProfilerData(profiler.getProfilerType(), data);
                    }
                }
            } catch (Exception e) {
                logManager.error("ProfilerManager", "Error collecting profiler data", e,
                               "type", profiler.getProfilerType());
            }
        }
        
        // Add performance analysis if analyzer is available
        if (performanceAnalyzer != null && performanceAnalyzer.isActive()) {
            try {
                ProfilerData analyzerData = performanceAnalyzer.collectData();
                if (analyzerData != null) {
                    // Extract performance analysis from analyzer data
                    ProfilerReport.PerformanceAnalysis analysis = new ProfilerReport.PerformanceAnalysis();
                    
                    Double overallScore = analyzerData.getMetric("overallScore", Double.class);
                    if (overallScore != null) {
                        analysis.setOverallScore(overallScore);
                    }
                    
                    // Add bottlenecks and regressions from metadata
                    Object bottlenecks = analyzerData.getMetadata("bottlenecks");
                    if (bottlenecks instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> bottleneckList = (List<String>) bottlenecks;
                        for (String bottleneck : bottleneckList) {
                            analysis.addBottleneck(bottleneck);
                        }
                    }
                    
                    Object regressions = analyzerData.getMetadata("regressions");
                    if (regressions instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> regressionList = (List<String>) regressions;
                        for (String regression : regressionList) {
                            analysis.addRegression(regression);
                        }
                    }
                    
                    Object suggestions = analyzerData.getMetadata("optimizationSuggestions");
                    if (suggestions instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> suggestionList = (List<String>) suggestions;
                        for (String suggestion : suggestionList) {
                            analysis.addOptimizationSuggestion(suggestion);
                        }
                    }
                    
                    report.setPerformanceAnalysis(analysis);
                }
            } catch (Exception e) {
                logManager.error("ProfilerManager", "Error generating performance analysis", e);
            }
        }
        
        // Generate summary
        report.generateSummary();
        
        // Store report
        lastReport = report;
        synchronized (reportHistory) {
            reportHistory.add(report);
            
            // Maintain history size limit
            while (reportHistory.size() > MAX_REPORT_HISTORY) {
                reportHistory.remove(0);
            }
        }
        
        return report;
    }
    
    /**
     * Reset all profilers.
     */
    public void reset() {
        logManager.info("ProfilerManager", "Resetting all profilers");
        
        for (IProfiler profiler : profilers.values()) {
            try {
                profiler.reset();
            } catch (Exception e) {
                logManager.error("ProfilerManager", "Error resetting profiler", e,
                               "type", profiler.getProfilerType());
            }
        }
        
        // Clear report history
        synchronized (reportHistory) {
            reportHistory.clear();
        }
        lastReport = null;
        
        logManager.info("ProfilerManager", "All profilers reset");
    }
    
    /**
     * Cleanup and shutdown the profiler manager.
     */
    public void shutdown() {
        logManager.info("ProfilerManager", "Shutting down profiler manager");
        
        // Disable profiling
        disable();
        
        // Cleanup all profilers
        for (IProfiler profiler : profilers.values()) {
            try {
                profiler.cleanup();
            } catch (Exception e) {
                logManager.error("ProfilerManager", "Error cleaning up profiler", e,
                               "type", profiler.getProfilerType());
            }
        }
        
        profilers.clear();
        
        // Shutdown profiler integration
        if (profilerIntegration != null) {
            profilerIntegration.shutdown();
        }
        
        // Shutdown scheduler
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        initialized.set(false);
        autoUpdateEnabled = false;
        
        logManager.info("ProfilerManager", "Profiler manager shutdown complete");
    }
    
    /**
     * Get a specific profiler by type.
     */
    @SuppressWarnings("unchecked")
    public <T extends IProfiler> T getProfiler(String type, Class<T> profilerClass) {
        IProfiler profiler = profilers.get(type);
        if (profiler != null && profilerClass.isInstance(profiler)) {
            return (T) profiler;
        }
        return null;
    }
    
    /**
     * Add a custom profiler.
     */
    public void addProfiler(String type, IProfiler profiler) {
        if (profiler != null) {
            profilers.put(type, profiler);
            
            if (initialized.get()) {
                profiler.initialize();
                
                if (enabled.get()) {
                    profiler.start();
                }
            }
            
            logManager.info("ProfilerManager", "Custom profiler added", "type", type);
        }
    }
    
    /**
     * Remove a profiler.
     */
    public void removeProfiler(String type) {
        IProfiler profiler = profilers.remove(type);
        if (profiler != null) {
            try {
                profiler.stop();
                profiler.cleanup();
            } catch (Exception e) {
                logManager.error("ProfilerManager", "Error removing profiler", e, "type", type);
            }
            
            logManager.info("ProfilerManager", "Profiler removed", "type", type);
        }
    }
    
    /**
     * Force immediate metrics collection through integration.
     */
    public void forceMetricsCollection() {
        if (profilerIntegration != null) {
            profilerIntegration.forceMetricsCollection();
        }
    }
    
    /**
     * Get metrics for a specific profiler type.
     */
    public Map<String, Object> getProfilerMetrics(String profilerType) {
        if (profilerIntegration != null) {
            return profilerIntegration.getProfilerMetrics(profilerType);
        }
        return new ConcurrentHashMap<>();
    }
    
    /**
     * Generate comprehensive metrics report.
     */
    public ProfilerIntegration.ProfilerMetricsReport generateMetricsReport() {
        if (profilerIntegration != null) {
            return profilerIntegration.generateMetricsReport();
        }
        return new ProfilerIntegration.ProfilerMetricsReport();
    }
    
    // Getters
    public boolean isEnabled() { return enabled.get(); }
    public boolean isInitialized() { return initialized.get(); }
    public ProfilerConfiguration getConfiguration() { return config; }
    public ProfilerReport getLastReport() { return lastReport; }
    
    public List<ProfilerReport> getReportHistory() {
        synchronized (reportHistory) {
            return new ArrayList<>(reportHistory);
        }
    }
    
    public Map<String, IProfiler> getAllProfilers() {
        return new ConcurrentHashMap<>(profilers);
    }
    
    // Specific profiler getters
    public PerformanceProfiler getPerformanceProfiler() { return performanceProfiler; }
    public MemoryProfiler getMemoryProfiler() { return memoryProfiler; }
    public NetworkProfiler getNetworkProfiler() { return networkProfiler; }
    public RenderProfiler getRenderProfiler() { return renderProfiler; }
    public DebugRenderer getDebugRenderer() { return debugRenderer; }
    public PerformanceAnalyzer getPerformanceAnalyzer() { return performanceAnalyzer; }
    public ProfilerUI getProfilerUI() { return profilerUI; }
    public ProfilerIntegration getProfilerIntegration() { return profilerIntegration; }
}