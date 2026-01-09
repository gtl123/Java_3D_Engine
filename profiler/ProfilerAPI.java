package engine.profiler;

import engine.logging.LogManager;
import engine.config.ConfigurationManager;
import engine.camera.Camera;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Comprehensive Profiler API providing unified access to all profiling capabilities.
 * This class serves as the main entry point for integrating profiling into any engine system.
 * Provides high-level methods for common profiling tasks and system integration.
 */
public class ProfilerAPI {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static ProfilerAPI instance;
    
    private final ProfilerManager profilerManager;
    private final Map<String, Long> activeTimers = new ConcurrentHashMap<>();
    private final Map<String, Integer> scopeCounters = new ConcurrentHashMap<>();
    
    private ProfilerAPI() {
        this.profilerManager = ProfilerManager.getInstance();
    }
    
    /**
     * Get the singleton instance of the Profiler API.
     * @return ProfilerAPI instance
     */
    public static synchronized ProfilerAPI getInstance() {
        if (instance == null) {
            instance = new ProfilerAPI();
        }
        return instance;
    }
    
    // ========================================
    // INITIALIZATION AND LIFECYCLE
    // ========================================
    
    /**
     * Initialize the profiler system with default configuration.
     * @return true if initialization was successful
     */
    public boolean initialize() {
        try {
            if (!profilerManager.isInitialized()) {
                profilerManager.initialize();
            }
            logManager.info("ProfilerAPI", "Profiler API initialized");
            return true;
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to initialize profiler API", e);
            return false;
        }
    }
    
    /**
     * Initialize the profiler system with custom configuration.
     * @param config Custom profiler configuration
     * @return true if initialization was successful
     */
    public boolean initialize(ProfilerConfiguration config) {
        try {
            if (!profilerManager.isInitialized()) {
                profilerManager.initialize();
            }
            profilerManager.setConfiguration(config);
            logManager.info("ProfilerAPI", "Profiler API initialized with custom configuration");
            return true;
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to initialize profiler API with custom config", e);
            return false;
        }
    }
    
    /**
     * Enable profiling across all systems.
     */
    public void enableProfiling() {
        try {
            profilerManager.enable();
            profilerManager.startProfiling();
            logManager.info("ProfilerAPI", "Profiling enabled");
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to enable profiling", e);
        }
    }
    
    /**
     * Disable profiling across all systems.
     */
    public void disableProfiling() {
        try {
            profilerManager.stopProfiling();
            profilerManager.disable();
            logManager.info("ProfilerAPI", "Profiling disabled");
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to disable profiling", e);
        }
    }
    
    /**
     * Check if profiling is currently active.
     * @return true if profiling is active
     */
    public boolean isProfilingActive() {
        return profilerManager.isInitialized() && profilerManager.isProfiling();
    }
    
    // ========================================
    // PERFORMANCE PROFILING
    // ========================================
    
    /**
     * Start timing a method or code block.
     * @param name Unique name for the timer
     */
    public void startTimer(String name) {
        if (!isProfilingActive()) return;
        
        try {
            PerformanceProfiler performanceProfiler = profilerManager.getPerformanceProfiler();
            if (performanceProfiler != null) {
                performanceProfiler.startMethodProfiling(name);
                activeTimers.put(name, System.nanoTime());
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to start timer", e, "name", name);
        }
    }
    
    /**
     * Stop timing a method or code block.
     * @param name Name of the timer to stop
     */
    public void stopTimer(String name) {
        if (!isProfilingActive()) return;
        
        try {
            PerformanceProfiler performanceProfiler = profilerManager.getPerformanceProfiler();
            if (performanceProfiler != null) {
                performanceProfiler.endMethodProfiling(name);
                activeTimers.remove(name);
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to stop timer", e, "name", name);
        }
    }
    
    /**
     * Profile a code block using a lambda expression.
     * @param name Name for the profiling scope
     * @param code Code to profile
     * @param <T> Return type of the code
     * @return Result of the code execution
     */
    public <T> T profile(String name, Supplier<T> code) {
        if (!isProfilingActive()) {
            return code.get();
        }
        
        startTimer(name);
        try {
            return code.get();
        } finally {
            stopTimer(name);
        }
    }
    
    /**
     * Profile a code block without return value.
     * @param name Name for the profiling scope
     * @param code Code to profile
     */
    public void profile(String name, Runnable code) {
        if (!isProfilingActive()) {
            code.run();
            return;
        }
        
        startTimer(name);
        try {
            code.run();
        } finally {
            stopTimer(name);
        }
    }
    
    /**
     * Profile an asynchronous operation.
     * @param name Name for the profiling scope
     * @param asyncCode Asynchronous code to profile
     * @param <T> Return type
     * @return CompletableFuture with profiling
     */
    public <T> CompletableFuture<T> profileAsync(String name, Supplier<CompletableFuture<T>> asyncCode) {
        if (!isProfilingActive()) {
            return asyncCode.get();
        }
        
        startTimer(name);
        return asyncCode.get().whenComplete((result, throwable) -> stopTimer(name));
    }
    
    /**
     * Record CPU usage for a specific component.
     * @param component Component name
     * @param cpuUsage CPU usage percentage (0-100)
     */
    public void recordCPUUsage(String component, double cpuUsage) {
        if (!isProfilingActive()) return;
        
        try {
            PerformanceProfiler performanceProfiler = profilerManager.getPerformanceProfiler();
            if (performanceProfiler != null) {
                performanceProfiler.recordCPUUsage(cpuUsage);
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to record CPU usage", e, "component", component);
        }
    }
    
    // ========================================
    // MEMORY PROFILING
    // ========================================
    
    /**
     * Take a memory snapshot with a descriptive name.
     * @param snapshotName Name for the snapshot
     */
    public void takeMemorySnapshot(String snapshotName) {
        if (!isProfilingActive()) return;
        
        try {
            MemoryProfiler memoryProfiler = profilerManager.getMemoryProfiler();
            if (memoryProfiler != null) {
                memoryProfiler.takeSnapshot(snapshotName);
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to take memory snapshot", e, "name", snapshotName);
        }
    }
    
    /**
     * Track memory allocation for a specific object type.
     * @param objectType Type of object being allocated
     * @param size Size in bytes
     */
    public void trackAllocation(String objectType, long size) {
        if (!isProfilingActive()) return;
        
        try {
            MemoryProfiler memoryProfiler = profilerManager.getMemoryProfiler();
            if (memoryProfiler != null) {
                memoryProfiler.trackAllocation(objectType, size);
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to track allocation", e, "type", objectType);
        }
    }
    
    /**
     * Check for memory leaks in the system.
     * @return true if potential memory leaks are detected
     */
    public boolean checkMemoryLeaks() {
        if (!isProfilingActive()) return false;
        
        try {
            MemoryProfiler memoryProfiler = profilerManager.getMemoryProfiler();
            return memoryProfiler != null && memoryProfiler.hasMemoryLeaks();
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to check memory leaks", e);
            return false;
        }
    }
    
    /**
     * Get current memory usage statistics.
     * @return Memory usage information
     */
    public MemoryUsageInfo getMemoryUsage() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();
            
            return new MemoryUsageInfo(usedMemory, totalMemory, maxMemory, freeMemory);
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to get memory usage", e);
            return new MemoryUsageInfo(0, 0, 0, 0);
        }
    }
    
    // ========================================
    // NETWORK PROFILING
    // ========================================
    
    /**
     * Record a network packet being sent.
     * @param size Packet size in bytes
     */
    public void recordPacketSent(int size) {
        if (!isProfilingActive()) return;
        
        try {
            NetworkProfiler networkProfiler = profilerManager.getNetworkProfiler();
            if (networkProfiler != null) {
                networkProfiler.recordPacketSent(size);
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to record packet sent", e);
        }
    }
    
    /**
     * Record a network packet being received.
     * @param size Packet size in bytes
     */
    public void recordPacketReceived(int size) {
        if (!isProfilingActive()) return;
        
        try {
            NetworkProfiler networkProfiler = profilerManager.getNetworkProfiler();
            if (networkProfiler != null) {
                networkProfiler.recordPacketReceived(size);
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to record packet received", e);
        }
    }
    
    /**
     * Record network latency measurement.
     * @param latencyMs Latency in milliseconds
     */
    public void recordNetworkLatency(double latencyMs) {
        if (!isProfilingActive()) return;
        
        try {
            NetworkProfiler networkProfiler = profilerManager.getNetworkProfiler();
            if (networkProfiler != null) {
                networkProfiler.recordLatency(latencyMs);
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to record network latency", e);
        }
    }
    
    /**
     * Record bandwidth usage.
     * @param bytesIn Bytes received
     * @param bytesOut Bytes sent
     */
    public void recordBandwidthUsage(long bytesIn, long bytesOut) {
        if (!isProfilingActive()) return;
        
        try {
            NetworkProfiler networkProfiler = profilerManager.getNetworkProfiler();
            if (networkProfiler != null) {
                networkProfiler.recordBandwidthUsage(bytesIn, bytesOut);
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to record bandwidth usage", e);
        }
    }
    
    // ========================================
    // RENDER PROFILING
    // ========================================
    
    /**
     * Begin profiling a rendering frame.
     */
    public void beginRenderFrame() {
        if (!isProfilingActive()) return;
        
        try {
            RenderProfiler renderProfiler = profilerManager.getRenderProfiler();
            if (renderProfiler != null) {
                renderProfiler.beginFrame();
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to begin render frame", e);
        }
    }
    
    /**
     * End profiling a rendering frame.
     */
    public void endRenderFrame() {
        if (!isProfilingActive()) return;
        
        try {
            RenderProfiler renderProfiler = profilerManager.getRenderProfiler();
            if (renderProfiler != null) {
                renderProfiler.endFrame();
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to end render frame", e);
        }
    }
    
    /**
     * Begin profiling a render pass.
     * @param passName Name of the render pass
     */
    public void beginRenderPass(String passName) {
        if (!isProfilingActive()) return;
        
        try {
            RenderProfiler renderProfiler = profilerManager.getRenderProfiler();
            if (renderProfiler != null) {
                renderProfiler.beginRenderPass(passName);
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to begin render pass", e, "pass", passName);
        }
    }
    
    /**
     * End profiling a render pass.
     * @param passName Name of the render pass
     */
    public void endRenderPass(String passName) {
        if (!isProfilingActive()) return;
        
        try {
            RenderProfiler renderProfiler = profilerManager.getRenderProfiler();
            if (renderProfiler != null) {
                renderProfiler.endRenderPass(passName);
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to end render pass", e, "pass", passName);
        }
    }
    
    /**
     * Record a draw call with triangle count.
     * @param triangleCount Number of triangles drawn
     */
    public void recordDrawCall(int triangleCount) {
        if (!isProfilingActive()) return;
        
        try {
            RenderProfiler renderProfiler = profilerManager.getRenderProfiler();
            if (renderProfiler != null) {
                renderProfiler.recordDrawCall(triangleCount);
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to record draw call", e);
        }
    }
    
    /**
     * Record frame time measurement.
     * @param frameTimeMs Frame time in milliseconds
     */
    public void recordFrameTime(double frameTimeMs) {
        if (!isProfilingActive()) return;
        
        try {
            RenderProfiler renderProfiler = profilerManager.getRenderProfiler();
            if (renderProfiler != null) {
                renderProfiler.recordFrameTime(frameTimeMs);
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to record frame time", e);
        }
    }
    
    // ========================================
    // DEBUG VISUALIZATION
    // ========================================
    
    /**
     * Enable debug rendering.
     */
    public void enableDebugRendering() {
        try {
            DebugRenderer debugRenderer = profilerManager.getDebugRenderer();
            if (debugRenderer != null) {
                debugRenderer.setEnabled(true);
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to enable debug rendering", e);
        }
    }
    
    /**
     * Disable debug rendering.
     */
    public void disableDebugRendering() {
        try {
            DebugRenderer debugRenderer = profilerManager.getDebugRenderer();
            if (debugRenderer != null) {
                debugRenderer.setEnabled(false);
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to disable debug rendering", e);
        }
    }
    
    /**
     * Render debug information for the current frame.
     * @param camera Camera for rendering
     */
    public void renderDebugInfo(Camera camera) {
        try {
            DebugRenderer debugRenderer = profilerManager.getDebugRenderer();
            if (debugRenderer != null && debugRenderer.isEnabled()) {
                debugRenderer.render(camera);
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to render debug info", e);
        }
    }
    
    // ========================================
    // DATA COLLECTION AND ANALYSIS
    // ========================================
    
    /**
     * Collect all current profiling data.
     * @return Comprehensive profiling data
     */
    public ProfilerData collectData() {
        try {
            return profilerManager.collectData();
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to collect profiler data", e);
            return new ProfilerData();
        }
    }
    
    /**
     * Get performance analysis results.
     * @return Performance analysis or null if not available
     */
    public PerformanceAnalysis getPerformanceAnalysis() {
        try {
            PerformanceAnalyzer analyzer = profilerManager.getPerformanceAnalyzer();
            if (analyzer != null) {
                return analyzer.analyzePerformance();
            }
            return null;
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to get performance analysis", e);
            return null;
        }
    }
    
    /**
     * Check if there are performance regressions.
     * @return true if regressions are detected
     */
    public boolean hasPerformanceRegressions() {
        try {
            PerformanceAnalyzer analyzer = profilerManager.getPerformanceAnalyzer();
            return analyzer != null && analyzer.hasPerformanceRegression();
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to check performance regressions", e);
            return false;
        }
    }
    
    /**
     * Get optimization suggestions based on current profiling data.
     * @return Array of optimization suggestions
     */
    public String[] getOptimizationSuggestions() {
        try {
            PerformanceAnalyzer analyzer = profilerManager.getPerformanceAnalyzer();
            if (analyzer != null) {
                return analyzer.getOptimizationSuggestions();
            }
            return new String[0];
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to get optimization suggestions", e);
            return new String[0];
        }
    }
    
    // ========================================
    // REPORTING
    // ========================================
    
    /**
     * Generate a comprehensive profiling report in JSON format.
     * @param filename Output filename
     * @return Path to generated report or null if failed
     */
    public String generateJSONReport(String filename) {
        try {
            ProfilerReport report = new ProfilerReport(profilerManager);
            return report.exportToJSON(filename);
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to generate JSON report", e);
            return null;
        }
    }
    
    /**
     * Generate a comprehensive profiling report in CSV format.
     * @param filename Output filename
     * @return Path to generated report or null if failed
     */
    public String generateCSVReport(String filename) {
        try {
            ProfilerReport report = new ProfilerReport(profilerManager);
            return report.exportToCSV(filename);
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to generate CSV report", e);
            return null;
        }
    }
    
    // ========================================
    // UI INTEGRATION
    // ========================================
    
    /**
     * Show the profiler UI.
     */
    public void showProfilerUI() {
        try {
            ProfilerUI profilerUI = profilerManager.getProfilerUI();
            if (profilerUI != null) {
                profilerUI.setVisible(true);
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to show profiler UI", e);
        }
    }
    
    /**
     * Hide the profiler UI.
     */
    public void hideProfilerUI() {
        try {
            ProfilerUI profilerUI = profilerManager.getProfilerUI();
            if (profilerUI != null) {
                profilerUI.setVisible(false);
            }
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to hide profiler UI", e);
        }
    }
    
    /**
     * Check if the profiler UI is currently visible.
     * @return true if UI is visible
     */
    public boolean isProfilerUIVisible() {
        try {
            ProfilerUI profilerUI = profilerManager.getProfilerUI();
            return profilerUI != null && profilerUI.isVisible();
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to check profiler UI visibility", e);
            return false;
        }
    }
    
    // ========================================
    // UTILITY METHODS
    // ========================================
    
    /**
     * Update the profiler system (should be called each frame).
     * @param deltaTime Time since last update in seconds
     */
    public void update(float deltaTime) {
        try {
            profilerManager.update(deltaTime);
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to update profiler", e);
        }
    }
    
    /**
     * Get profiler system statistics.
     * @return System statistics
     */
    public ProfilerStatistics getStatistics() {
        try {
            return profilerManager.getStatistics();
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to get profiler statistics", e);
            return new ProfilerStatistics();
        }
    }
    
    /**
     * Reset all profiling data and counters.
     */
    public void reset() {
        try {
            profilerManager.reset();
            activeTimers.clear();
            scopeCounters.clear();
            logManager.info("ProfilerAPI", "Profiler data reset");
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to reset profiler", e);
        }
    }
    
    /**
     * Shutdown the profiler system.
     */
    public void shutdown() {
        try {
            profilerManager.shutdown();
            activeTimers.clear();
            scopeCounters.clear();
            logManager.info("ProfilerAPI", "Profiler API shutdown");
        } catch (Exception e) {
            logManager.error("ProfilerAPI", "Failed to shutdown profiler API", e);
        }
    }
    
    // ========================================
    // HELPER CLASSES
    // ========================================
    
    /**
     * Memory usage information container.
     */
    public static class MemoryUsageInfo {
        private final long usedMemory;
        private final long totalMemory;
        private final long maxMemory;
        private final long freeMemory;
        
        public MemoryUsageInfo(long usedMemory, long totalMemory, long maxMemory, long freeMemory) {
            this.usedMemory = usedMemory;
            this.totalMemory = totalMemory;
            this.maxMemory = maxMemory;
            this.freeMemory = freeMemory;
        }
        
        public long getUsedMemory() { return usedMemory; }
        public long getTotalMemory() { return totalMemory; }
        public long getMaxMemory() { return maxMemory; }
        public long getFreeMemory() { return freeMemory; }
        
        public double getUsagePercentage() {
            return maxMemory > 0 ? (usedMemory * 100.0) / maxMemory : 0.0;
        }
        
        public String getUsedMemoryMB() {
            return String.format("%.1f MB", usedMemory / 1024.0 / 1024.0);
        }
        
        public String getMaxMemoryMB() {
            return String.format("%.1f MB", maxMemory / 1024.0 / 1024.0);
        }
        
        @Override
        public String toString() {
            return String.format("Memory: %s / %s (%.1f%%)", 
                               getUsedMemoryMB(), getMaxMemoryMB(), getUsagePercentage());
        }
    }
    
    /**
     * Performance analysis results container.
     */
    public static class PerformanceAnalysis {
        private final double averageFrameTime;
        private final double averageCPUUsage;
        private final boolean hasRegressions;
        private final String[] bottlenecks;
        private final String[] suggestions;
        
        public PerformanceAnalysis(double averageFrameTime, double averageCPUUsage, 
                                 boolean hasRegressions, String[] bottlenecks, String[] suggestions) {
            this.averageFrameTime = averageFrameTime;
            this.averageCPUUsage = averageCPUUsage;
            this.hasRegressions = hasRegressions;
            this.bottlenecks = bottlenecks;
            this.suggestions = suggestions;
        }
        
        public double getAverageFrameTime() { return averageFrameTime; }
        public double getAverageCPUUsage() { return averageCPUUsage; }
        public boolean hasRegressions() { return hasRegressions; }
        public String[] getBottlenecks() { return bottlenecks; }
        public String[] getSuggestions() { return suggestions; }
        
        public double getAverageFPS() {
            return averageFrameTime > 0 ? 1000.0 / averageFrameTime : 0.0;
        }
    }
    
    /**
     * Profiler system statistics container.
     */
    public static class ProfilerStatistics {
        private final long totalSamples;
        private final long totalMemoryAllocations;
        private final long totalNetworkPackets;
        private final long totalDrawCalls;
        private final double averageFrameTime;
        
        public ProfilerStatistics() {
            this(0, 0, 0, 0, 0.0);
        }
        
        public ProfilerStatistics(long totalSamples, long totalMemoryAllocations, 
                                long totalNetworkPackets, long totalDrawCalls, double averageFrameTime) {
            this.totalSamples = totalSamples;
            this.totalMemoryAllocations = totalMemoryAllocations;
            this.totalNetworkPackets = totalNetworkPackets;
            this.totalDrawCalls = totalDrawCalls;
            this.averageFrameTime = averageFrameTime;
        }
        
        public long getTotalSamples() { return totalSamples; }
        public long getTotalMemoryAllocations() { return totalMemoryAllocations; }
        public long getTotalNetworkPackets() { return totalNetworkPackets; }
        public long getTotalDrawCalls() { return totalDrawCalls; }
        public double getAverageFrameTime() { return averageFrameTime; }
    }
}