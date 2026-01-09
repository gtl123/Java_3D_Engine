package engine.profiler.examples;

import engine.profiler.*;
import engine.logging.LogManager;
import engine.config.ConfigurationManager;
import engine.io.Window;
import engine.camera.Camera;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive examples demonstrating the profiler system capabilities.
 * Shows various profiling scenarios, configuration options, and integration patterns.
 */
public class ProfilerExamples {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    /**
     * Example 1: Basic Profiling Setup and Usage
     * Demonstrates how to initialize and use the profiler system.
     */
    public static void basicProfilingExample() {
        logManager.info("ProfilerExamples", "Running basic profiling example");
        
        try {
            // Get profiler manager instance
            ProfilerManager profilerManager = ProfilerManager.getInstance();
            
            // Initialize profiler system
            if (!profilerManager.isInitialized()) {
                profilerManager.initialize();
                logManager.info("ProfilerExamples", "Profiler system initialized");
            }
            
            // Enable profiling
            profilerManager.enable();
            profilerManager.startProfiling();
            
            // Simulate some work to profile
            simulateGameLoop(profilerManager, 100); // 100 frames
            
            // Collect profiling data
            ProfilerData data = profilerManager.collectData();
            
            // Display results
            displayBasicResults(data);
            
            // Stop profiling
            profilerManager.stopProfiling();
            
        } catch (Exception e) {
            logManager.error("ProfilerExamples", "Basic profiling example failed", e);
        }
    }
    
    /**
     * Example 2: Performance Profiling with CPU and GPU Metrics
     * Shows detailed performance analysis capabilities.
     */
    public static void performanceProfilingExample() {
        logManager.info("ProfilerExamples", "Running performance profiling example");
        
        try {
            ProfilerManager profilerManager = ProfilerManager.getInstance();
            PerformanceProfiler performanceProfiler = profilerManager.getPerformanceProfiler();
            
            if (performanceProfiler == null) {
                logManager.warn("ProfilerExamples", "Performance profiler not available");
                return;
            }
            
            // Start method profiling
            performanceProfiler.startMethodProfiling("GameLoop.update");
            
            // Simulate CPU-intensive work
            simulateCPUWork();
            
            // End method profiling
            performanceProfiler.endMethodProfiling("GameLoop.update");
            
            // Start GPU profiling (if available)
            performanceProfiler.startGPUProfiling("RenderPass.geometry");
            
            // Simulate GPU work
            simulateGPUWork();
            
            // End GPU profiling
            performanceProfiler.endGPUProfiling("RenderPass.geometry");
            
            // Collect and display performance data
            ProfilerData data = profilerManager.collectData();
            displayPerformanceResults(data);
            
        } catch (Exception e) {
            logManager.error("ProfilerExamples", "Performance profiling example failed", e);
        }
    }
    
    /**
     * Example 3: Memory Profiling and Leak Detection
     * Demonstrates memory usage tracking and leak detection.
     */
    public static void memoryProfilingExample() {
        logManager.info("ProfilerExamples", "Running memory profiling example");
        
        try {
            ProfilerManager profilerManager = ProfilerManager.getInstance();
            MemoryProfiler memoryProfiler = profilerManager.getMemoryProfiler();
            
            if (memoryProfiler == null) {
                logManager.warn("ProfilerExamples", "Memory profiler not available");
                return;
            }
            
            // Take initial memory snapshot
            memoryProfiler.takeSnapshot("initial");
            
            // Simulate memory allocation
            simulateMemoryAllocation();
            
            // Take snapshot after allocation
            memoryProfiler.takeSnapshot("after_allocation");
            
            // Simulate memory leak scenario
            simulateMemoryLeak();
            
            // Take final snapshot
            memoryProfiler.takeSnapshot("potential_leak");
            
            // Analyze memory usage
            ProfilerData data = profilerManager.collectData();
            displayMemoryResults(data);
            
            // Check for memory leaks
            if (memoryProfiler.hasMemoryLeaks()) {
                logManager.warn("ProfilerExamples", "Potential memory leaks detected!");
                // In a real scenario, you would investigate further
            }
            
        } catch (Exception e) {
            logManager.error("ProfilerExamples", "Memory profiling example failed", e);
        }
    }
    
    /**
     * Example 4: Network Profiling
     * Shows network performance monitoring capabilities.
     */
    public static void networkProfilingExample() {
        logManager.info("ProfilerExamples", "Running network profiling example");
        
        try {
            ProfilerManager profilerManager = ProfilerManager.getInstance();
            NetworkProfiler networkProfiler = profilerManager.getNetworkProfiler();
            
            if (networkProfiler == null) {
                logManager.warn("ProfilerExamples", "Network profiler not available");
                return;
            }
            
            // Simulate network activity
            simulateNetworkActivity(networkProfiler);
            
            // Collect network data
            ProfilerData data = profilerManager.collectData();
            displayNetworkResults(data);
            
        } catch (Exception e) {
            logManager.error("ProfilerExamples", "Network profiling example failed", e);
        }
    }
    
    /**
     * Example 5: Render Profiling
     * Demonstrates rendering performance analysis.
     */
    public static void renderProfilingExample() {
        logManager.info("ProfilerExamples", "Running render profiling example");
        
        try {
            ProfilerManager profilerManager = ProfilerManager.getInstance();
            RenderProfiler renderProfiler = profilerManager.getRenderProfiler();
            
            if (renderProfiler == null) {
                logManager.warn("ProfilerExamples", "Render profiler not available");
                return;
            }
            
            // Simulate rendering operations
            simulateRenderingOperations(renderProfiler);
            
            // Collect render data
            ProfilerData data = profilerManager.collectData();
            displayRenderResults(data);
            
        } catch (Exception e) {
            logManager.error("ProfilerExamples", "Render profiling example failed", e);
        }
    }
    
    /**
     * Example 6: Debug Visualization
     * Shows how to use debug rendering capabilities.
     */
    public static void debugVisualizationExample(Camera camera) {
        logManager.info("ProfilerExamples", "Running debug visualization example");
        
        try {
            ProfilerManager profilerManager = ProfilerManager.getInstance();
            DebugRenderer debugRenderer = profilerManager.getDebugRenderer();
            
            if (debugRenderer == null) {
                logManager.warn("ProfilerExamples", "Debug renderer not available");
                return;
            }
            
            // Enable debug rendering
            debugRenderer.setEnabled(true);
            
            // Add debug visualizations
            addDebugVisualizations(debugRenderer);
            
            // Render debug information (would be called in render loop)
            debugRenderer.render(camera);
            
            logManager.info("ProfilerExamples", "Debug visualization setup complete");
            
        } catch (Exception e) {
            logManager.error("ProfilerExamples", "Debug visualization example failed", e);
        }
    }
    
    /**
     * Example 7: Performance Analysis and Regression Detection
     * Demonstrates automated performance analysis.
     */
    public static void performanceAnalysisExample() {
        logManager.info("ProfilerExamples", "Running performance analysis example");
        
        try {
            ProfilerManager profilerManager = ProfilerManager.getInstance();
            PerformanceAnalyzer analyzer = profilerManager.getPerformanceAnalyzer();
            
            if (analyzer == null) {
                logManager.warn("ProfilerExamples", "Performance analyzer not available");
                return;
            }
            
            // Simulate multiple profiling sessions to establish baseline
            for (int i = 0; i < 5; i++) {
                simulateGameLoop(profilerManager, 50);
                ProfilerData data = profilerManager.collectData();
                analyzer.addDataPoint(data);
                
                // Small delay between sessions
                Thread.sleep(100);
            }
            
            // Simulate a performance regression
            simulatePerformanceRegression(profilerManager);
            ProfilerData regressionData = profilerManager.collectData();
            analyzer.addDataPoint(regressionData);
            
            // Analyze for regressions
            if (analyzer.hasPerformanceRegression()) {
                logManager.warn("ProfilerExamples", "Performance regression detected!");
                
                // Get optimization suggestions
                String[] suggestions = analyzer.getOptimizationSuggestions();
                for (String suggestion : suggestions) {
                    logManager.info("ProfilerExamples", "Optimization suggestion: " + suggestion);
                }
            }
            
        } catch (Exception e) {
            logManager.error("ProfilerExamples", "Performance analysis example failed", e);
        }
    }
    
    /**
     * Example 8: Profiler UI Integration
     * Shows how to use the profiler UI for real-time monitoring.
     */
    public static void profilerUIExample() {
        logManager.info("ProfilerExamples", "Running profiler UI example");
        
        try {
            ProfilerManager profilerManager = ProfilerManager.getInstance();
            ProfilerUI profilerUI = profilerManager.getProfilerUI();
            
            if (profilerUI == null) {
                logManager.warn("ProfilerExamples", "Profiler UI not available");
                return;
            }
            
            // Show profiler UI
            profilerUI.setVisible(true);
            
            // Simulate continuous profiling for UI demonstration
            CompletableFuture.runAsync(() -> {
                try {
                    for (int i = 0; i < 300; i++) { // 5 seconds at 60 FPS
                        simulateGameLoop(profilerManager, 1);
                        profilerUI.update(1.0f / 60.0f); // 60 FPS
                        Thread.sleep(16); // ~60 FPS
                    }
                } catch (Exception e) {
                    logManager.error("ProfilerExamples", "UI simulation failed", e);
                }
            });
            
            logManager.info("ProfilerExamples", "Profiler UI demonstration started");
            
        } catch (Exception e) {
            logManager.error("ProfilerExamples", "Profiler UI example failed", e);
        }
    }
    
    /**
     * Example 9: Configuration and Customization
     * Demonstrates profiler configuration options.
     */
    public static void configurationExample() {
        logManager.info("ProfilerExamples", "Running configuration example");
        
        try {
            // Get configuration manager
            ConfigurationManager configManager = ConfigurationManager.getInstance();
            
            // Configure profiler settings
            configManager.setProperty("profiler.enabled", true);
            configManager.setProperty("profiler.performance.enabled", true);
            configManager.setProperty("profiler.memory.enabled", true);
            configManager.setProperty("profiler.network.enabled", true);
            configManager.setProperty("profiler.render.enabled", true);
            configManager.setProperty("profiler.ui.enabled", true);
            configManager.setProperty("profiler.debug.enabled", true);
            
            // Performance profiler settings
            configManager.setProperty("profiler.performance.sampleRate", 1000); // 1000 Hz
            configManager.setProperty("profiler.performance.maxSamples", 10000);
            
            // Memory profiler settings
            configManager.setProperty("profiler.memory.trackAllocations", true);
            configManager.setProperty("profiler.memory.detectLeaks", true);
            configManager.setProperty("profiler.memory.snapshotInterval", 5000); // 5 seconds
            
            // Network profiler settings
            configManager.setProperty("profiler.network.trackBandwidth", true);
            configManager.setProperty("profiler.network.trackLatency", true);
            configManager.setProperty("profiler.network.maxConnections", 100);
            
            // Render profiler settings
            configManager.setProperty("profiler.render.trackDrawCalls", true);
            configManager.setProperty("profiler.render.trackGPUMemory", true);
            configManager.setProperty("profiler.render.trackFrameTime", true);
            
            // UI settings
            configManager.setProperty("profiler.ui.updateRate", 30); // 30 Hz
            configManager.setProperty("profiler.ui.showGraphs", true);
            configManager.setProperty("profiler.ui.showMetrics", true);
            
            // Debug renderer settings
            configManager.setProperty("profiler.debug.wireframe", false);
            configManager.setProperty("profiler.debug.boundingBoxes", true);
            configManager.setProperty("profiler.debug.collisionShapes", true);
            
            logManager.info("ProfilerExamples", "Profiler configuration updated");
            
            // Reinitialize profiler with new settings
            ProfilerManager profilerManager = ProfilerManager.getInstance();
            if (profilerManager.isInitialized()) {
                profilerManager.shutdown();
                profilerManager.initialize();
                logManager.info("ProfilerExamples", "Profiler reinitialized with new configuration");
            }
            
        } catch (Exception e) {
            logManager.error("ProfilerExamples", "Configuration example failed", e);
        }
    }
    
    /**
     * Example 10: Complete Profiling Workflow
     * Demonstrates a complete profiling session from start to finish.
     */
    public static void completeWorkflowExample() {
        logManager.info("ProfilerExamples", "Running complete workflow example");
        
        try {
            // 1. Initialize profiler system
            ProfilerManager profilerManager = ProfilerManager.getInstance();
            profilerManager.initialize();
            profilerManager.enable();
            
            // 2. Configure profiling
            ProfilerConfiguration config = profilerManager.getConfiguration();
            config.setEnabled(true);
            config.setPerformanceProfilingEnabled(true);
            config.setMemoryProfilingEnabled(true);
            config.setNetworkProfilingEnabled(true);
            config.setRenderProfilingEnabled(true);
            
            // 3. Start profiling session
            profilerManager.startProfiling();
            logManager.info("ProfilerExamples", "Profiling session started");
            
            // 4. Run application simulation
            simulateCompleteApplication(profilerManager);
            
            // 5. Collect comprehensive data
            ProfilerData finalData = profilerManager.collectData();
            
            // 6. Generate report
            ProfilerReport report = new ProfilerReport(profilerManager);
            String jsonReport = report.exportToJSON("complete_workflow_report.json");
            String csvReport = report.exportToCSV("complete_workflow_report.csv");
            
            // 7. Display summary
            displayCompleteSummary(finalData);
            
            // 8. Stop profiling
            profilerManager.stopProfiling();
            
            logManager.info("ProfilerExamples", "Complete workflow finished",
                           "jsonReport", jsonReport,
                           "csvReport", csvReport);
            
        } catch (Exception e) {
            logManager.error("ProfilerExamples", "Complete workflow example failed", e);
        }
    }
    
    // Helper methods for simulation
    
    private static void simulateGameLoop(ProfilerManager profilerManager, int frames) {
        for (int i = 0; i < frames; i++) {
            // Simulate frame processing
            profilerManager.update(1.0f / 60.0f); // 60 FPS
            
            // Small delay to simulate real frame time
            try {
                Thread.sleep(16); // ~60 FPS
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private static void simulateCPUWork() {
        // Simulate CPU-intensive calculation
        double result = 0;
        for (int i = 0; i < 1000000; i++) {
            result += Math.sin(i) * Math.cos(i);
        }
        // Use result to prevent optimization
        logManager.debug("ProfilerExamples", "CPU work result: " + result);
    }
    
    private static void simulateGPUWork() {
        // In a real scenario, this would involve OpenGL calls
        // For simulation, we just add a delay
        try {
            Thread.sleep(5); // Simulate GPU processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private static void simulateMemoryAllocation() {
        // Simulate memory allocation patterns
        java.util.List<byte[]> allocations = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            allocations.add(new byte[1024 * 1024]); // 1MB allocations
        }
        // Clear some allocations
        for (int i = 0; i < 50; i++) {
            allocations.remove(0);
        }
    }
    
    private static void simulateMemoryLeak() {
        // Simulate a potential memory leak (static collection that grows)
        // In a real scenario, this would be actual leaked objects
        for (int i = 0; i < 10; i++) {
            // Simulate objects that aren't properly cleaned up
            @SuppressWarnings("unused")
            byte[] leakedObject = new byte[512 * 1024]; // 512KB objects
        }
    }
    
    private static void simulateNetworkActivity(NetworkProfiler networkProfiler) {
        // Simulate network packets
        for (int i = 0; i < 50; i++) {
            networkProfiler.recordPacketSent(1024 + (i * 10)); // Varying packet sizes
            networkProfiler.recordPacketReceived(512 + (i * 5));
            networkProfiler.recordLatency(10.0 + Math.random() * 20.0); // 10-30ms latency
        }
        
        // Simulate bandwidth usage
        networkProfiler.recordBandwidthUsage(1024 * 50, 512 * 50); // 50KB in, 25KB out
    }
    
    private static void simulateRenderingOperations(RenderProfiler renderProfiler) {
        // Simulate rendering frame
        renderProfiler.beginFrame();
        
        // Simulate render passes
        renderProfiler.beginRenderPass("Geometry");
        renderProfiler.recordDrawCall(1000); // 1000 triangles
        renderProfiler.recordDrawCall(500);
        renderProfiler.endRenderPass("Geometry");
        
        renderProfiler.beginRenderPass("Lighting");
        renderProfiler.recordDrawCall(2000);
        renderProfiler.endRenderPass("Lighting");
        
        renderProfiler.beginRenderPass("PostProcess");
        renderProfiler.recordDrawCall(4); // Full-screen quad
        renderProfiler.endRenderPass("PostProcess");
        
        renderProfiler.recordFrameTime(16.67); // 60 FPS
        renderProfiler.endFrame();
    }
    
    private static void addDebugVisualizations(DebugRenderer debugRenderer) {
        // Add various debug visualizations
        // In a real scenario, these would be actual 3D coordinates and objects
        
        // Simulate bounding boxes
        for (int i = 0; i < 10; i++) {
            // debugRenderer.addBoundingBox(...); // Would add actual bounding boxes
        }
        
        // Simulate collision shapes
        for (int i = 0; i < 5; i++) {
            // debugRenderer.addCollisionShape(...); // Would add actual collision shapes
        }
        
        logManager.debug("ProfilerExamples", "Debug visualizations added");
    }
    
    private static void simulatePerformanceRegression(ProfilerManager profilerManager) {
        // Simulate a performance regression by adding extra work
        for (int i = 0; i < 50; i++) {
            simulateCPUWork(); // Extra CPU work to cause regression
            profilerManager.update(1.0f / 60.0f);
            
            try {
                Thread.sleep(32); // Slower frame rate (30 FPS instead of 60)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private static void simulateCompleteApplication(ProfilerManager profilerManager) {
        logManager.info("ProfilerExamples", "Simulating complete application run");
        
        // Simulate initialization phase
        simulateCPUWork();
        simulateMemoryAllocation();
        
        // Simulate main game loop
        simulateGameLoop(profilerManager, 180); // 3 seconds at 60 FPS
        
        // Simulate network activity
        NetworkProfiler networkProfiler = profilerManager.getNetworkProfiler();
        if (networkProfiler != null) {
            simulateNetworkActivity(networkProfiler);
        }
        
        // Simulate rendering
        RenderProfiler renderProfiler = profilerManager.getRenderProfiler();
        if (renderProfiler != null) {
            simulateRenderingOperations(renderProfiler);
        }
        
        logManager.info("ProfilerExamples", "Application simulation complete");
    }
    
    // Display methods for results
    
    private static void displayBasicResults(ProfilerData data) {
        logManager.info("ProfilerExamples", "=== Basic Profiling Results ===");
        logManager.info("ProfilerExamples", "Frame Time: " + data.getMetric("performance.frameTime", 0.0) + "ms");
        logManager.info("ProfilerExamples", "Memory Usage: " + data.getMetric("memory.heapUsed", 0L) + " bytes");
        logManager.info("ProfilerExamples", "Network Latency: " + data.getMetric("network.latency", 0.0) + "ms");
    }
    
    private static void displayPerformanceResults(ProfilerData data) {
        logManager.info("ProfilerExamples", "=== Performance Profiling Results ===");
        logManager.info("ProfilerExamples", "CPU Usage: " + data.getMetric("performance.cpuUsage", 0.0) + "%");
        logManager.info("ProfilerExamples", "GPU Usage: " + data.getMetric("performance.gpuUsage", 0.0) + "%");
        logManager.info("ProfilerExamples", "Method Calls: " + data.getMetric("performance.methodCalls", 0));
        logManager.info("ProfilerExamples", "Average Frame Time: " + data.getMetric("performance.avgFrameTime", 0.0) + "ms");
    }
    
    private static void displayMemoryResults(ProfilerData data) {
        logManager.info("ProfilerExamples", "=== Memory Profiling Results ===");
        logManager.info("ProfilerExamples", "Heap Used: " + data.getMetric("memory.heapUsed", 0L) + " bytes");
        logManager.info("ProfilerExamples", "Heap Max: " + data.getMetric("memory.heapMax", 0L) + " bytes");
        logManager.info("ProfilerExamples", "GC Collections: " + data.getMetric("memory.gcCollections", 0));
        logManager.info("ProfilerExamples", "Allocations: " + data.getMetric("memory.allocations", 0));
    }
    
    private static void displayNetworkResults(ProfilerData data) {
        logManager.info("ProfilerExamples", "=== Network Profiling Results ===");
        logManager.info("ProfilerExamples", "Packets Sent: " + data.getMetric("network.packetsSent", 0));
        logManager.info("ProfilerExamples", "Packets Received: " + data.getMetric("network.packetsReceived", 0));
        logManager.info("ProfilerExamples", "Bandwidth In: " + data.getMetric("network.bandwidthIn", 0L) + " bytes");
        logManager.info("ProfilerExamples", "Bandwidth Out: " + data.getMetric("network.bandwidthOut", 0L) + " bytes");
        logManager.info("ProfilerExamples", "Average Latency: " + data.getMetric("network.avgLatency", 0.0) + "ms");
    }
    
    private static void displayRenderResults(ProfilerData data) {
        logManager.info("ProfilerExamples", "=== Render Profiling Results ===");
        logManager.info("ProfilerExamples", "Draw Calls: " + data.getMetric("render.drawCalls", 0));
        logManager.info("ProfilerExamples", "Triangles: " + data.getMetric("render.triangles", 0));
        logManager.info("ProfilerExamples", "Texture Memory: " + data.getMetric("render.textureMemory", 0L) + " bytes");
        logManager.info("ProfilerExamples", "Render Passes: " + data.getMetric("render.passes", 0));
    }
    
    private static void displayCompleteSummary(ProfilerData data) {
        logManager.info("ProfilerExamples", "=== Complete Profiling Summary ===");
        displayBasicResults(data);
        displayPerformanceResults(data);
        displayMemoryResults(data);
        displayNetworkResults(data);
        displayRenderResults(data);
        logManager.info("ProfilerExamples", "=== End Summary ===");
    }
    
    /**
     * Run all profiling examples in sequence.
     * This method demonstrates the complete profiler system capabilities.
     */
    public static void runAllExamples() {
        logManager.info("ProfilerExamples", "Starting comprehensive profiler examples");
        
        try {
            // Initialize profiler system
            ProfilerManager profilerManager = ProfilerManager.getInstance();
            if (!profilerManager.isInitialized()) {
                profilerManager.initialize();
            }
            
            // Run examples in sequence
            basicProfilingExample();
            Thread.sleep(1000);
            
            performanceProfilingExample();
            Thread.sleep(1000);
            
            memoryProfilingExample();
            Thread.sleep(1000);
            
            networkProfilingExample();
            Thread.sleep(1000);
            
            renderProfilingExample();
            Thread.sleep(1000);
            
            performanceAnalysisExample();
            Thread.sleep(1000);
            
            configurationExample();
            Thread.sleep(1000);
            
            completeWorkflowExample();
            
            logManager.info("ProfilerExamples", "All profiler examples completed successfully");
            
        } catch (Exception e) {
            logManager.error("ProfilerExamples", "Error running profiler examples", e);
        }
    }
}