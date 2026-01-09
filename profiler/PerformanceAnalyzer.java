package engine.profiler;

import engine.logging.LogManager;
import engine.logging.MetricsCollector;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Performance analyzer for regression detection and optimization analysis.
 * Provides automated performance analysis, bottleneck identification, and optimization suggestions.
 */
public class PerformanceAnalyzer {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = logManager.getMetricsCollector();
    
    private final ProfilerConfiguration config;
    private final ProfilerManager profilerManager;
    private final AtomicBoolean active = new AtomicBoolean(false);
    
    // Performance baselines
    private final Map<String, PerformanceBaseline> baselines = new ConcurrentHashMap<>();
    
    // Analysis results
    private final Queue<AnalysisResult> analysisHistory = new ConcurrentLinkedQueue<>();
    private PerformanceAnalysis currentAnalysis;
    
    // Regression detection
    private final Map<String, RegressionTracker> regressionTrackers = new ConcurrentHashMap<>();
    
    // Bottleneck detection
    private final Map<String, BottleneckTracker> bottleneckTrackers = new ConcurrentHashMap<>();
    
    // Analysis intervals
    private long lastAnalysisTime = 0;
    private final long analysisIntervalMs = 5000; // Analyze every 5 seconds
    
    public PerformanceAnalyzer(ProfilerConfiguration config, ProfilerManager profilerManager) {
        this.config = config;
        this.profilerManager = profilerManager;
    }
    
    /**
     * Initialize the performance analyzer.
     */
    public void initialize() {
        logManager.info("PerformanceAnalyzer", "Initializing performance analyzer",
                       "regressionDetection", config.isRegressionDetection(),
                       "bottleneckIdentification", config.isBottleneckIdentification(),
                       "optimizationSuggestions", config.isOptimizationSuggestions());
        
        // Initialize baseline trackers
        initializeBaselines();
        
        logManager.info("PerformanceAnalyzer", "Performance analyzer initialized");
    }
    
    /**
     * Start performance analysis.
     */
    public void start() {
        if (active.get()) {
            return;
        }
        
        logManager.info("PerformanceAnalyzer", "Starting performance analysis");
        
        // Clear previous analysis data
        analysisHistory.clear();
        regressionTrackers.clear();
        bottleneckTrackers.clear();
        
        lastAnalysisTime = System.currentTimeMillis();
        
        active.set(true);
        
        logManager.info("PerformanceAnalyzer", "Performance analysis started");
    }
    
    /**
     * Stop performance analysis.
     */
    public void stop() {
        if (!active.get()) {
            return;
        }
        
        logManager.info("PerformanceAnalyzer", "Stopping performance analysis");
        
        // Generate final analysis
        if (config.isRegressionDetection() || config.isBottleneckIdentification()) {
            performAnalysis();
        }
        
        active.set(false);
        
        logManager.info("PerformanceAnalyzer", "Performance analysis stopped");
    }
    
    /**
     * Update performance analysis.
     */
    public void update(float deltaTime) {
        if (!active.get()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // Perform periodic analysis
        if (currentTime - lastAnalysisTime >= analysisIntervalMs) {
            performAnalysis();
            lastAnalysisTime = currentTime;
        }
        
        // Update trackers
        updateRegressionTrackers();
        updateBottleneckTrackers();
    }
    
    /**
     * Cleanup performance analyzer.
     */
    public void cleanup() {
        logManager.info("PerformanceAnalyzer", "Cleaning up performance analyzer");
        
        stop();
        
        baselines.clear();
        analysisHistory.clear();
        regressionTrackers.clear();
        bottleneckTrackers.clear();
        
        logManager.info("PerformanceAnalyzer", "Performance analyzer cleanup complete");
    }
    
    /**
     * Generate comprehensive performance analysis.
     */
    public ProfilerReport.PerformanceAnalysis generateAnalysis() {
        ProfilerReport.PerformanceAnalysis analysis = new ProfilerReport.PerformanceAnalysis();
        
        // Calculate overall performance score
        double overallScore = calculateOverallPerformanceScore();
        analysis.setOverallScore(overallScore);
        
        // Identify bottlenecks
        if (config.isBottleneckIdentification()) {
            List<String> bottlenecks = identifyBottlenecks();
            for (String bottleneck : bottlenecks) {
                analysis.addBottleneck(bottleneck);
            }
        }
        
        // Detect regressions
        if (config.isRegressionDetection()) {
            List<String> regressions = detectRegressions();
            for (String regression : regressions) {
                analysis.addRegression(regression);
            }
        }
        
        // Generate optimization suggestions
        if (config.isOptimizationSuggestions()) {
            List<String> suggestions = generateOptimizationSuggestions();
            for (String suggestion : suggestions) {
                analysis.addOptimizationSuggestion(suggestion);
            }
        }
        
        return analysis;
    }
    
    /**
     * Set performance baseline for a specific metric.
     */
    public void setBaseline(String metricName, double value, double tolerance) {
        PerformanceBaseline baseline = new PerformanceBaseline();
        baseline.metricName = metricName;
        baseline.baselineValue = value;
        baseline.tolerance = tolerance;
        baseline.timestamp = System.currentTimeMillis();
        
        baselines.put(metricName, baseline);
        
        logManager.info("PerformanceAnalyzer", "Performance baseline set",
                       "metric", metricName, "value", value, "tolerance", tolerance);
    }
    
    /**
     * Check if a metric value represents a regression.
     */
    public boolean isRegression(String metricName, double currentValue) {
        PerformanceBaseline baseline = baselines.get(metricName);
        if (baseline == null) {
            return false;
        }
        
        double deviation = Math.abs(currentValue - baseline.baselineValue) / baseline.baselineValue * 100.0;
        return deviation > baseline.tolerance;
    }
    
    /**
     * Add a performance sample for analysis.
     */
    public void addPerformanceSample(String category, String metric, double value) {
        if (!active.get()) {
            return;
        }
        
        // Update regression tracker
        RegressionTracker tracker = regressionTrackers.computeIfAbsent(
            category + "." + metric, k -> new RegressionTracker(k));
        tracker.addSample(value);
        
        // Update bottleneck tracker
        BottleneckTracker bottleneckTracker = bottleneckTrackers.computeIfAbsent(
            category, k -> new BottleneckTracker(k));
        bottleneckTracker.addMetric(metric, value);
    }
    
    private void initializeBaselines() {
        // Set default baselines for common performance metrics
        setBaseline("performance.frameTime", 16.67, 10.0); // 60 FPS with 10% tolerance
        setBaseline("memory.heapUsagePercent", 70.0, 15.0); // 70% heap usage with 15% tolerance
        setBaseline("network.averageLatency", 50.0, 20.0); // 50ms latency with 20% tolerance
        setBaseline("render.drawCalls", 1000.0, 25.0); // 1000 draw calls with 25% tolerance
        
        logManager.debug("PerformanceAnalyzer", "Default baselines initialized");
    }
    
    private void performAnalysis() {
        if (!profilerManager.isEnabled()) {
            return;
        }
        
        logManager.debug("PerformanceAnalyzer", "Performing performance analysis");
        
        // Collect data from all profilers
        ProfilerReport report = profilerManager.generateReport();
        
        // Analyze the data
        AnalysisResult result = new AnalysisResult();
        result.timestamp = System.currentTimeMillis();
        result.overallScore = calculateOverallPerformanceScore();
        
        // Detect regressions
        if (config.isRegressionDetection()) {
            result.regressions = detectRegressions();
        }
        
        // Identify bottlenecks
        if (config.isBottleneckIdentification()) {
            result.bottlenecks = identifyBottlenecks();
        }
        
        // Generate suggestions
        if (config.isOptimizationSuggestions()) {
            result.suggestions = generateOptimizationSuggestions();
        }
        
        // Store result
        analysisHistory.offer(result);
        
        // Keep only recent analysis results
        if (analysisHistory.size() > 100) {
            analysisHistory.poll();
        }
        
        // Log significant findings
        if (!result.regressions.isEmpty() || !result.bottlenecks.isEmpty()) {
            logManager.warn("PerformanceAnalyzer", "Performance issues detected",
                           "regressions", result.regressions.size(),
                           "bottlenecks", result.bottlenecks.size(),
                           "overallScore", result.overallScore);
        }
    }
    
    private double calculateOverallPerformanceScore() {
        double score = 100.0; // Start with perfect score
        
        // Analyze frame time performance
        RegressionTracker frameTimeTracker = regressionTrackers.get("performance.frameTime");
        if (frameTimeTracker != null) {
            double avgFrameTime = frameTimeTracker.getAverage();
            if (avgFrameTime > 16.67) { // Worse than 60 FPS
                score -= Math.min(30.0, (avgFrameTime - 16.67) / 16.67 * 30.0);
            }
        }
        
        // Analyze memory usage
        RegressionTracker memoryTracker = regressionTrackers.get("memory.heapUsagePercent");
        if (memoryTracker != null) {
            double avgMemoryUsage = memoryTracker.getAverage();
            if (avgMemoryUsage > 80.0) { // High memory usage
                score -= Math.min(20.0, (avgMemoryUsage - 80.0) / 20.0 * 20.0);
            }
        }
        
        // Analyze network latency
        RegressionTracker latencyTracker = regressionTrackers.get("network.averageLatency");
        if (latencyTracker != null) {
            double avgLatency = latencyTracker.getAverage();
            if (avgLatency > 100.0) { // High latency
                score -= Math.min(15.0, (avgLatency - 100.0) / 100.0 * 15.0);
            }
        }
        
        // Analyze render performance
        RegressionTracker drawCallTracker = regressionTrackers.get("render.drawCalls");
        if (drawCallTracker != null) {
            double avgDrawCalls = drawCallTracker.getAverage();
            if (avgDrawCalls > 2000.0) { // High draw call count
                score -= Math.min(15.0, (avgDrawCalls - 2000.0) / 2000.0 * 15.0);
            }
        }
        
        return Math.max(0.0, score);
    }
    
    private List<String> detectRegressions() {
        List<String> regressions = new ArrayList<>();
        
        for (Map.Entry<String, RegressionTracker> entry : regressionTrackers.entrySet()) {
            String metricName = entry.getKey();
            RegressionTracker tracker = entry.getValue();
            
            if (tracker.hasRegression(config.getPerformanceThresholdPercent())) {
                double regressionPercent = tracker.getRegressionPercent();
                regressions.add(String.format("%s: %.1f%% regression detected", 
                                            metricName, regressionPercent));
            }
        }
        
        return regressions;
    }
    
    private List<String> identifyBottlenecks() {
        List<String> bottlenecks = new ArrayList<>();
        
        for (Map.Entry<String, BottleneckTracker> entry : bottleneckTrackers.entrySet()) {
            String category = entry.getKey();
            BottleneckTracker tracker = entry.getValue();
            
            List<String> categoryBottlenecks = tracker.identifyBottlenecks();
            for (String bottleneck : categoryBottlenecks) {
                bottlenecks.add(category + ": " + bottleneck);
            }
        }
        
        return bottlenecks;
    }
    
    private List<String> generateOptimizationSuggestions() {
        List<String> suggestions = new ArrayList<>();
        
        // Frame time optimization suggestions
        RegressionTracker frameTimeTracker = regressionTrackers.get("performance.frameTime");
        if (frameTimeTracker != null && frameTimeTracker.getAverage() > 20.0) {
            suggestions.add("Consider reducing draw calls or optimizing shaders to improve frame time");
        }
        
        // Memory optimization suggestions
        RegressionTracker memoryTracker = regressionTrackers.get("memory.heapUsagePercent");
        if (memoryTracker != null && memoryTracker.getAverage() > 85.0) {
            suggestions.add("High memory usage detected - consider implementing object pooling or reducing texture resolution");
        }
        
        // Network optimization suggestions
        RegressionTracker latencyTracker = regressionTrackers.get("network.averageLatency");
        if (latencyTracker != null && latencyTracker.getAverage() > 150.0) {
            suggestions.add("High network latency - consider implementing client-side prediction or reducing update frequency");
        }
        
        // Render optimization suggestions
        RegressionTracker drawCallTracker = regressionTrackers.get("render.drawCalls");
        if (drawCallTracker != null && drawCallTracker.getAverage() > 3000.0) {
            suggestions.add("High draw call count - consider batching similar objects or using instanced rendering");
        }
        
        // GPU memory optimization
        BottleneckTracker renderTracker = bottleneckTrackers.get("render");
        if (renderTracker != null) {
            Map<String, Double> metrics = renderTracker.getMetrics();
            Double textureMemory = metrics.get("totalTextureMemoryMB");
            if (textureMemory != null && textureMemory > 1024.0) {
                suggestions.add("High texture memory usage - consider texture compression or streaming");
            }
        }
        
        return suggestions;
    }
    
    private void updateRegressionTrackers() {
        // Clean up old samples from regression trackers
        for (RegressionTracker tracker : regressionTrackers.values()) {
            tracker.cleanup();
        }
    }
    
    private void updateBottleneckTrackers() {
        // Update bottleneck analysis
        for (BottleneckTracker tracker : bottleneckTrackers.values()) {
            tracker.update();
        }
    }
    
    // Helper classes
    private static class PerformanceBaseline {
        String metricName;
        double baselineValue;
        double tolerance; // Percentage
        long timestamp;
    }
    
    private static class AnalysisResult {
        long timestamp;
        double overallScore;
        List<String> regressions = new ArrayList<>();
        List<String> bottlenecks = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
    }
    
    private static class RegressionTracker {
        final String metricName;
        final Queue<Sample> samples = new ConcurrentLinkedQueue<>();
        final int maxSamples = 100;
        
        RegressionTracker(String metricName) {
            this.metricName = metricName;
        }
        
        void addSample(double value) {
            samples.offer(new Sample(value, System.currentTimeMillis()));
            
            // Keep only recent samples
            while (samples.size() > maxSamples) {
                samples.poll();
            }
        }
        
        boolean hasRegression(double thresholdPercent) {
            if (samples.size() < 10) {
                return false; // Need enough samples
            }
            
            List<Sample> sampleList = new ArrayList<>(samples);
            
            // Compare recent samples with older samples
            int recentCount = Math.min(10, sampleList.size() / 4);
            int olderCount = Math.min(10, sampleList.size() / 4);
            
            double recentAvg = sampleList.subList(sampleList.size() - recentCount, sampleList.size())
                .stream().mapToDouble(s -> s.value).average().orElse(0.0);
            
            double olderAvg = sampleList.subList(0, olderCount)
                .stream().mapToDouble(s -> s.value).average().orElse(0.0);
            
            if (olderAvg == 0.0) return false;
            
            double changePercent = Math.abs(recentAvg - olderAvg) / olderAvg * 100.0;
            return changePercent > thresholdPercent && recentAvg > olderAvg;
        }
        
        double getRegressionPercent() {
            if (samples.size() < 10) {
                return 0.0;
            }
            
            List<Sample> sampleList = new ArrayList<>(samples);
            
            int recentCount = Math.min(10, sampleList.size() / 4);
            int olderCount = Math.min(10, sampleList.size() / 4);
            
            double recentAvg = sampleList.subList(sampleList.size() - recentCount, sampleList.size())
                .stream().mapToDouble(s -> s.value).average().orElse(0.0);
            
            double olderAvg = sampleList.subList(0, olderCount)
                .stream().mapToDouble(s -> s.value).average().orElse(0.0);
            
            if (olderAvg == 0.0) return 0.0;
            
            return (recentAvg - olderAvg) / olderAvg * 100.0;
        }
        
        double getAverage() {
            return samples.stream().mapToDouble(s -> s.value).average().orElse(0.0);
        }
        
        void cleanup() {
            long cutoffTime = System.currentTimeMillis() - 300000; // Keep 5 minutes
            samples.removeIf(sample -> sample.timestamp < cutoffTime);
        }
        
        private static class Sample {
            final double value;
            final long timestamp;
            
            Sample(double value, long timestamp) {
                this.value = value;
                this.timestamp = timestamp;
            }
        }
    }
    
    private static class BottleneckTracker {
        final String category;
        final Map<String, Double> metrics = new ConcurrentHashMap<>();
        final Map<String, Double> previousMetrics = new ConcurrentHashMap<>();
        
        BottleneckTracker(String category) {
            this.category = category;
        }
        
        void addMetric(String name, double value) {
            metrics.put(name, value);
        }
        
        List<String> identifyBottlenecks() {
            List<String> bottlenecks = new ArrayList<>();
            
            // Identify metrics that are significantly higher than others
            if (metrics.size() > 1) {
                double maxValue = metrics.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                double avgValue = metrics.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                
                for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                    if (entry.getValue() > avgValue * 2.0 && entry.getValue() > maxValue * 0.7) {
                        bottlenecks.add(String.format("%s is consuming %.1f%% of resources", 
                                                    entry.getKey(), entry.getValue() / maxValue * 100.0));
                    }
                }
            }
            
            return bottlenecks;
        }
        
        Map<String, Double> getMetrics() {
            return new ConcurrentHashMap<>(metrics);
        }
        
        void update() {
            // Store previous metrics for trend analysis
            previousMetrics.clear();
            previousMetrics.putAll(metrics);
        }
    }
}