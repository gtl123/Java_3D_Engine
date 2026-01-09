package fps.benchmarking;

import engine.logging.LogManager;
import engine.logging.PerformanceMonitor;
import engine.logging.MetricsCollector;
import fps.performance.FPSPerformanceManager;
import fps.server.ServerMetrics;
import fps.testing.StressTestFramework;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.ArrayList;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;

/**
 * Comprehensive performance benchmarking suite for FPS game systems.
 * Validates performance targets and measures system capabilities.
 */
public class PerformanceBenchmarkSuite {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Performance targets
    private static final double TARGET_SERVER_TICK_RATE = 64.0; // Hz
    private static final double MAX_SERVER_TICK_TIME = 15.0; // ms
    private static final double TARGET_CLIENT_FPS = 144.0;
    private static final double MIN_CLIENT_FPS = 60.0;
    private static final int TARGET_CONCURRENT_PLAYERS = 32;
    private static final double MAX_NETWORK_LATENCY = 50.0; // ms
    private static final double MAX_MEMORY_USAGE_PERCENT = 80.0;
    
    private final BenchmarkConfiguration config;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // Benchmark components
    private ExecutorService benchmarkExecutor;
    private final Map<String, BenchmarkTest> benchmarkTests = new ConcurrentHashMap<>();
    private final List<BenchmarkResult> benchmarkResults = new ArrayList<>();
    
    // System monitoring
    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    
    public PerformanceBenchmarkSuite(BenchmarkConfiguration config) {
        this.config = config;
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        
        initializeBenchmarkTests();
    }
    
    /**
     * Initialize the benchmark suite.
     */
    public void initialize() {
        if (initialized.get()) {
            logManager.warn("PerformanceBenchmarkSuite", "Benchmark suite already initialized");
            return;
        }
        
        logManager.info("PerformanceBenchmarkSuite", "Initializing performance benchmark suite");
        
        try {
            benchmarkExecutor = Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "BenchmarkSuite-Executor");
                t.setDaemon(true);
                return t;
            });
            
            initialized.set(true);
            logManager.info("PerformanceBenchmarkSuite", "Performance benchmark suite initialized");
            
        } catch (Exception e) {
            logManager.error("PerformanceBenchmarkSuite", "Failed to initialize benchmark suite", e);
            throw new RuntimeException("Benchmark suite initialization failed", e);
        }
    }
    
    /**
     * Initialize all benchmark tests.
     */
    private void initializeBenchmarkTests() {
        // Server performance benchmarks
        benchmarkTests.put("server_tick_rate", new ServerTickRateBenchmark());
        benchmarkTests.put("server_concurrent_players", new ServerConcurrentPlayersBenchmark());
        benchmarkTests.put("server_memory_usage", new ServerMemoryUsageBenchmark());
        
        // Client performance benchmarks
        benchmarkTests.put("client_fps_performance", new ClientFPSBenchmark());
        benchmarkTests.put("client_memory_usage", new ClientMemoryUsageBenchmark());
        benchmarkTests.put("client_rendering_performance", new ClientRenderingBenchmark());
        
        // Network performance benchmarks
        benchmarkTests.put("network_latency", new NetworkLatencyBenchmark());
        benchmarkTests.put("network_throughput", new NetworkThroughputBenchmark());
        benchmarkTests.put("network_packet_loss", new NetworkPacketLossBenchmark());
        
        // System integration benchmarks
        benchmarkTests.put("full_system_load", new FullSystemLoadBenchmark());
        benchmarkTests.put("scalability_test", new ScalabilityBenchmark());
        benchmarkTests.put("stress_recovery", new StressRecoveryBenchmark());
    }
    
    /**
     * Run all benchmark tests.
     */
    public CompletableFuture<BenchmarkSuiteResult> runAllBenchmarks() {
        if (!initialized.get()) {
            throw new IllegalStateException("Benchmark suite not initialized");
        }
        
        if (running.get()) {
            throw new IllegalStateException("Benchmark suite already running");
        }
        
        return CompletableFuture.supplyAsync(() -> {
            running.set(true);
            benchmarkResults.clear();
            
            logManager.info("PerformanceBenchmarkSuite", "Starting comprehensive performance benchmarks");
            
            BenchmarkSuiteResult suiteResult = new BenchmarkSuiteResult();
            long suiteStartTime = System.currentTimeMillis();
            
            try {
                // Run benchmarks in order of importance
                String[] benchmarkOrder = {
                    "server_tick_rate",
                    "client_fps_performance", 
                    "server_concurrent_players",
                    "network_latency",
                    "server_memory_usage",
                    "client_memory_usage",
                    "network_throughput",
                    "client_rendering_performance",
                    "network_packet_loss",
                    "full_system_load",
                    "scalability_test",
                    "stress_recovery"
                };
                
                for (String benchmarkName : benchmarkOrder) {
                    BenchmarkTest test = benchmarkTests.get(benchmarkName);
                    if (test != null) {
                        logManager.info("PerformanceBenchmarkSuite", "Running benchmark", 
                                       "benchmark", benchmarkName);
                        
                        BenchmarkResult result = test.execute();
                        benchmarkResults.add(result);
                        suiteResult.addBenchmarkResult(benchmarkName, result);
                        
                        logManager.info("PerformanceBenchmarkSuite", "Benchmark completed",
                                       "benchmark", benchmarkName,
                                       "passed", result.isPassed(),
                                       "score", result.getScore());
                        
                        // Brief pause between benchmarks
                        Thread.sleep(2000);
                    }
                }
                
                // Calculate overall results
                suiteResult.calculateOverallResults();
                suiteResult.setDurationMs(System.currentTimeMillis() - suiteStartTime);
                
                logManager.info("PerformanceBenchmarkSuite", "All benchmarks completed",
                               "overallScore", suiteResult.getOverallScore(),
                               "passedTests", suiteResult.getPassedTests(),
                               "totalTests", suiteResult.getTotalTests());
                
                return suiteResult;
                
            } catch (Exception e) {
                logManager.error("PerformanceBenchmarkSuite", "Benchmark suite failed", e);
                suiteResult.setSuccess(false);
                suiteResult.setErrorMessage(e.getMessage());
                return suiteResult;
                
            } finally {
                running.set(false);
            }
        }, benchmarkExecutor);
    }
    
    /**
     * Run a specific benchmark test.
     */
    public CompletableFuture<BenchmarkResult> runBenchmark(String benchmarkName) {
        BenchmarkTest test = benchmarkTests.get(benchmarkName);
        if (test == null) {
            throw new IllegalArgumentException("Unknown benchmark: " + benchmarkName);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            logManager.info("PerformanceBenchmarkSuite", "Running single benchmark", 
                           "benchmark", benchmarkName);
            
            BenchmarkResult result = test.execute();
            
            logManager.info("PerformanceBenchmarkSuite", "Single benchmark completed",
                           "benchmark", benchmarkName,
                           "passed", result.isPassed(),
                           "score", result.getScore());
            
            return result;
        }, benchmarkExecutor);
    }
    
    /**
     * Validate that all performance targets are met.
     */
    public boolean validatePerformanceTargets() {
        logManager.info("PerformanceBenchmarkSuite", "Validating performance targets");
        
        // Run critical performance benchmarks
        try {
            BenchmarkResult serverTickRate = benchmarkTests.get("server_tick_rate").execute();
            BenchmarkResult clientFPS = benchmarkTests.get("client_fps_performance").execute();
            BenchmarkResult concurrentPlayers = benchmarkTests.get("server_concurrent_players").execute();
            BenchmarkResult networkLatency = benchmarkTests.get("network_latency").execute();
            
            boolean allTargetsMet = serverTickRate.isPassed() && 
                                   clientFPS.isPassed() && 
                                   concurrentPlayers.isPassed() && 
                                   networkLatency.isPassed();
            
            logManager.info("PerformanceBenchmarkSuite", "Performance target validation completed",
                           "allTargetsMet", allTargetsMet,
                           "serverTickRate", serverTickRate.isPassed(),
                           "clientFPS", clientFPS.isPassed(),
                           "concurrentPlayers", concurrentPlayers.isPassed(),
                           "networkLatency", networkLatency.isPassed());
            
            return allTargetsMet;
            
        } catch (Exception e) {
            logManager.error("PerformanceBenchmarkSuite", "Performance target validation failed", e);
            return false;
        }
    }
    
    /**
     * Generate performance report.
     */
    public PerformanceReport generatePerformanceReport() {
        PerformanceReport report = new PerformanceReport();
        
        // System information
        report.setSystemInfo(collectSystemInfo());
        
        // Benchmark results
        report.setBenchmarkResults(new ArrayList<>(benchmarkResults));
        
        // Performance summary
        report.setPerformanceSummary(generatePerformanceSummary());
        
        // Recommendations
        report.setRecommendations(generateRecommendations());
        
        return report;
    }
    
    /**
     * Collect system information.
     */
    private Map<String, Object> collectSystemInfo() {
        Map<String, Object> systemInfo = new ConcurrentHashMap<>();
        
        // JVM information
        systemInfo.put("jvm.version", System.getProperty("java.version"));
        systemInfo.put("jvm.vendor", System.getProperty("java.vendor"));
        systemInfo.put("jvm.maxMemory", Runtime.getRuntime().maxMemory());
        systemInfo.put("jvm.processors", Runtime.getRuntime().availableProcessors());
        
        // OS information
        systemInfo.put("os.name", System.getProperty("os.name"));
        systemInfo.put("os.version", System.getProperty("os.version"));
        systemInfo.put("os.arch", System.getProperty("os.arch"));
        
        // Memory information
        systemInfo.put("memory.heap.used", memoryBean.getHeapMemoryUsage().getUsed());
        systemInfo.put("memory.heap.max", memoryBean.getHeapMemoryUsage().getMax());
        systemInfo.put("memory.nonheap.used", memoryBean.getNonHeapMemoryUsage().getUsed());
        
        // Thread information
        systemInfo.put("threads.count", threadBean.getThreadCount());
        systemInfo.put("threads.peak", threadBean.getPeakThreadCount());
        
        return systemInfo;
    }
    
    /**
     * Generate performance summary.
     */
    private Map<String, Object> generatePerformanceSummary() {
        Map<String, Object> summary = new ConcurrentHashMap<>();
        
        if (!benchmarkResults.isEmpty()) {
            double averageScore = benchmarkResults.stream()
                .mapToDouble(BenchmarkResult::getScore)
                .average()
                .orElse(0.0);
            
            long passedTests = benchmarkResults.stream()
                .mapToLong(result -> result.isPassed() ? 1 : 0)
                .sum();
            
            summary.put("averageScore", averageScore);
            summary.put("passedTests", passedTests);
            summary.put("totalTests", benchmarkResults.size());
            summary.put("passRate", (double) passedTests / benchmarkResults.size());
        }
        
        return summary;
    }
    
    /**
     * Generate performance recommendations.
     */
    private List<String> generateRecommendations() {
        List<String> recommendations = new ArrayList<>();
        
        for (BenchmarkResult result : benchmarkResults) {
            if (!result.isPassed()) {
                switch (result.getBenchmarkName()) {
                    case "server_tick_rate":
                        recommendations.add("Server tick rate below target - consider optimizing server loop or reducing tick rate");
                        break;
                    case "client_fps_performance":
                        recommendations.add("Client FPS below target - optimize rendering pipeline or reduce graphics quality");
                        break;
                    case "server_concurrent_players":
                        recommendations.add("Server cannot handle target concurrent players - optimize networking or increase server resources");
                        break;
                    case "network_latency":
                        recommendations.add("Network latency too high - optimize network code or check network infrastructure");
                        break;
                    case "server_memory_usage":
                    case "client_memory_usage":
                        recommendations.add("Memory usage too high - implement memory pooling or optimize garbage collection");
                        break;
                }
            }
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("All performance targets met - system is production ready");
        }
        
        return recommendations;
    }
    
    /**
     * Shutdown the benchmark suite.
     */
    public void shutdown() {
        logManager.info("PerformanceBenchmarkSuite", "Shutting down benchmark suite");
        
        running.set(false);
        
        if (benchmarkExecutor != null) {
            benchmarkExecutor.shutdown();
            try {
                if (!benchmarkExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    benchmarkExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                benchmarkExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        initialized.set(false);
        logManager.info("PerformanceBenchmarkSuite", "Benchmark suite shutdown complete");
    }
    
    // Getters
    public boolean isInitialized() { return initialized.get(); }
    public boolean isRunning() { return running.get(); }
    public BenchmarkConfiguration getConfiguration() { return config; }
    public List<BenchmarkResult> getBenchmarkResults() { return new ArrayList<>(benchmarkResults); }
    
    /**
     * Benchmark configuration.
     */
    public static class BenchmarkConfiguration {
        private int warmupIterations = 3;
        private int benchmarkIterations = 5;
        private int timeoutSeconds = 300;
        private boolean enableDetailedLogging = true;
        
        // Getters and setters
        public int getWarmupIterations() { return warmupIterations; }
        public void setWarmupIterations(int warmupIterations) { this.warmupIterations = warmupIterations; }
        
        public int getBenchmarkIterations() { return benchmarkIterations; }
        public void setBenchmarkIterations(int benchmarkIterations) { this.benchmarkIterations = benchmarkIterations; }
        
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        
        public boolean isEnableDetailedLogging() { return enableDetailedLogging; }
        public void setEnableDetailedLogging(boolean enableDetailedLogging) { this.enableDetailedLogging = enableDetailedLogging; }
    }
    
    /**
     * Benchmark suite result.
     */
    public static class BenchmarkSuiteResult {
        private boolean success = true;
        private String errorMessage;
        private long durationMs;
        private double overallScore;
        private int passedTests;
        private int totalTests;
        private final Map<String, BenchmarkResult> benchmarkResults = new ConcurrentHashMap<>();
        
        public void addBenchmarkResult(String name, BenchmarkResult result) {
            benchmarkResults.put(name, result);
        }
        
        public void calculateOverallResults() {
            totalTests = benchmarkResults.size();
            passedTests = (int) benchmarkResults.values().stream()
                .mapToLong(result -> result.isPassed() ? 1 : 0)
                .sum();
            
            overallScore = benchmarkResults.values().stream()
                .mapToDouble(BenchmarkResult::getScore)
                .average()
                .orElse(0.0);
        }
        
        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        
        public double getOverallScore() { return overallScore; }
        public int getPassedTests() { return passedTests; }
        public int getTotalTests() { return totalTests; }
        
        public Map<String, BenchmarkResult> getBenchmarkResults() { return benchmarkResults; }
    }
    
    /**
     * Performance report.
     */
    public static class PerformanceReport {
        private Map<String, Object> systemInfo;
        private List<BenchmarkResult> benchmarkResults;
        private Map<String, Object> performanceSummary;
        private List<String> recommendations;
        
        // Getters and setters
        public Map<String, Object> getSystemInfo() { return systemInfo; }
        public void setSystemInfo(Map<String, Object> systemInfo) { this.systemInfo = systemInfo; }
        
        public List<BenchmarkResult> getBenchmarkResults() { return benchmarkResults; }
        public void setBenchmarkResults(List<BenchmarkResult> benchmarkResults) { this.benchmarkResults = benchmarkResults; }
        
        public Map<String, Object> getPerformanceSummary() { return performanceSummary; }
        public void setPerformanceSummary(Map<String, Object> performanceSummary) { this.performanceSummary = performanceSummary; }
        
        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
    }
}