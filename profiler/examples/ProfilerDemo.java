package engine.profiler.examples;

import engine.profiler.*;
import engine.logging.LogManager;
import engine.config.ConfigurationManager;

/**
 * Simple demonstration application showing profiler system usage.
 * This class provides a practical example of integrating profiling into an application.
 */
public class ProfilerDemo {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    public static void main(String[] args) {
        logManager.info("ProfilerDemo", "Starting Profiler System Demonstration");
        
        try {
            // Initialize configuration
            ConfigurationManager.getInstance().initialize();
            
            // Run the demonstration
            runDemo();
            
        } catch (Exception e) {
            logManager.error("ProfilerDemo", "Demo failed", e);
            e.printStackTrace();
        }
        
        logManager.info("ProfilerDemo", "Profiler demonstration completed");
    }
    
    private static void runDemo() throws Exception {
        // 1. Initialize the profiler system
        logManager.info("ProfilerDemo", "Step 1: Initializing profiler system");
        ProfilerManager profilerManager = ProfilerManager.getInstance();
        profilerManager.initialize();
        
        // 2. Configure profiling options
        logManager.info("ProfilerDemo", "Step 2: Configuring profiler");
        ProfilerConfiguration config = profilerManager.getConfiguration();
        config.setEnabled(true);
        config.setPerformanceProfilingEnabled(true);
        config.setMemoryProfilingEnabled(true);
        config.setNetworkProfilingEnabled(true);
        config.setRenderProfilingEnabled(true);
        
        // 3. Start profiling
        logManager.info("ProfilerDemo", "Step 3: Starting profiling session");
        profilerManager.enable();
        profilerManager.startProfiling();
        
        // 4. Simulate application workload
        logManager.info("ProfilerDemo", "Step 4: Running simulated workload");
        simulateApplicationWorkload(profilerManager);
        
        // 5. Collect profiling data
        logManager.info("ProfilerDemo", "Step 5: Collecting profiling data");
        ProfilerData data = profilerManager.collectData();
        
        // 6. Display results
        logManager.info("ProfilerDemo", "Step 6: Displaying results");
        displayResults(data);
        
        // 7. Generate reports
        logManager.info("ProfilerDemo", "Step 7: Generating reports");
        generateReports(profilerManager);
        
        // 8. Stop profiling
        logManager.info("ProfilerDemo", "Step 8: Stopping profiling");
        profilerManager.stopProfiling();
        profilerManager.shutdown();
        
        logManager.info("ProfilerDemo", "Demo completed successfully!");
    }
    
    private static void simulateApplicationWorkload(ProfilerManager profilerManager) throws InterruptedException {
        PerformanceProfiler performanceProfiler = profilerManager.getPerformanceProfiler();
        MemoryProfiler memoryProfiler = profilerManager.getMemoryProfiler();
        NetworkProfiler networkProfiler = profilerManager.getNetworkProfiler();
        RenderProfiler renderProfiler = profilerManager.getRenderProfiler();
        
        // Simulate 5 seconds of application runtime at 60 FPS
        for (int frame = 0; frame < 300; frame++) {
            // Start frame profiling
            if (performanceProfiler != null) {
                performanceProfiler.startMethodProfiling("GameLoop.update");
            }
            if (renderProfiler != null) {
                renderProfiler.beginFrame();
            }
            
            // Simulate game logic update
            simulateGameLogicUpdate();
            
            // Simulate rendering
            if (renderProfiler != null) {
                simulateRendering(renderProfiler);
            }
            
            // Simulate network activity (every 10 frames)
            if (frame % 10 == 0 && networkProfiler != null) {
                simulateNetworkActivity(networkProfiler);
            }
            
            // Simulate memory allocation (every 30 frames)
            if (frame % 30 == 0 && memoryProfiler != null) {
                simulateMemoryOperations();
            }
            
            // Update profiler
            profilerManager.update(1.0f / 60.0f);
            
            // End frame profiling
            if (performanceProfiler != null) {
                performanceProfiler.endMethodProfiling("GameLoop.update");
            }
            if (renderProfiler != null) {
                renderProfiler.recordFrameTime(16.67); // 60 FPS
                renderProfiler.endFrame();
            }
            
            // Simulate frame timing
            Thread.sleep(16); // ~60 FPS
            
            // Log progress every second
            if (frame % 60 == 0) {
                logManager.info("ProfilerDemo", "Simulation progress: " + (frame / 60 + 1) + "/5 seconds");
            }
        }
    }
    
    private static void simulateGameLogicUpdate() {
        // Simulate CPU-intensive game logic
        double result = 0;
        for (int i = 0; i < 10000; i++) {
            result += Math.sin(i * 0.01) * Math.cos(i * 0.01);
        }
        // Use result to prevent optimization
        if (result > 1000000) {
            logManager.debug("ProfilerDemo", "Heavy computation result: " + result);
        }
    }
    
    private static void simulateRendering(RenderProfiler renderProfiler) {
        // Simulate geometry pass
        renderProfiler.beginRenderPass("Geometry");
        renderProfiler.recordDrawCall(500);  // 500 triangles
        renderProfiler.recordDrawCall(300);  // 300 triangles
        renderProfiler.recordDrawCall(1200); // 1200 triangles
        renderProfiler.endRenderPass("Geometry");
        
        // Simulate lighting pass
        renderProfiler.beginRenderPass("Lighting");
        renderProfiler.recordDrawCall(2000); // Full-screen lighting
        renderProfiler.endRenderPass("Lighting");
        
        // Simulate post-processing
        renderProfiler.beginRenderPass("PostProcess");
        renderProfiler.recordDrawCall(4); // Full-screen quad
        renderProfiler.endRenderPass("PostProcess");
        
        // Simulate GPU work delay
        try {
            Thread.sleep(2); // 2ms GPU work
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private static void simulateNetworkActivity(NetworkProfiler networkProfiler) {
        // Simulate sending game state updates
        networkProfiler.recordPacketSent(256);  // Player position update
        networkProfiler.recordPacketSent(128);  // Input update
        networkProfiler.recordPacketSent(512);  // Game state update
        
        // Simulate receiving updates from server
        networkProfiler.recordPacketReceived(1024); // World update
        networkProfiler.recordPacketReceived(64);   // Chat message
        
        // Simulate network latency
        double latency = 15.0 + Math.random() * 10.0; // 15-25ms
        networkProfiler.recordLatency(latency);
        
        // Record bandwidth usage
        networkProfiler.recordBandwidthUsage(1024 + 64, 256 + 128 + 512);
    }
    
    private static void simulateMemoryOperations() {
        // Simulate temporary object allocation
        java.util.List<byte[]> tempObjects = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tempObjects.add(new byte[1024]); // 1KB objects
        }
        
        // Simulate object cleanup
        tempObjects.clear();
        
        // Suggest garbage collection
        System.gc();
    }
    
    private static void displayResults(ProfilerData data) {
        logManager.info("ProfilerDemo", "=== PROFILING RESULTS ===");
        
        // Performance metrics
        logManager.info("ProfilerDemo", "Performance Metrics:");
        logManager.info("ProfilerDemo", "  Average Frame Time: " + 
                       String.format("%.2f ms", data.getMetric("performance.avgFrameTime", 0.0)));
        logManager.info("ProfilerDemo", "  CPU Usage: " + 
                       String.format("%.1f%%", data.getMetric("performance.cpuUsage", 0.0)));
        logManager.info("ProfilerDemo", "  Method Calls: " + 
                       data.getMetric("performance.methodCalls", 0));
        
        // Memory metrics
        logManager.info("ProfilerDemo", "Memory Metrics:");
        long heapUsed = data.getMetric("memory.heapUsed", 0L);
        long heapMax = data.getMetric("memory.heapMax", 0L);
        logManager.info("ProfilerDemo", "  Heap Usage: " + 
                       String.format("%.1f MB / %.1f MB (%.1f%%)", 
                                   heapUsed / 1024.0 / 1024.0,
                                   heapMax / 1024.0 / 1024.0,
                                   (heapUsed * 100.0) / heapMax));
        logManager.info("ProfilerDemo", "  GC Collections: " + 
                       data.getMetric("memory.gcCollections", 0));
        
        // Network metrics
        logManager.info("ProfilerDemo", "Network Metrics:");
        logManager.info("ProfilerDemo", "  Packets Sent: " + 
                       data.getMetric("network.packetsSent", 0));
        logManager.info("ProfilerDemo", "  Packets Received: " + 
                       data.getMetric("network.packetsReceived", 0));
        logManager.info("ProfilerDemo", "  Average Latency: " + 
                       String.format("%.1f ms", data.getMetric("network.avgLatency", 0.0)));
        
        // Render metrics
        logManager.info("ProfilerDemo", "Render Metrics:");
        logManager.info("ProfilerDemo", "  Draw Calls: " + 
                       data.getMetric("render.drawCalls", 0));
        logManager.info("ProfilerDemo", "  Triangles Rendered: " + 
                       data.getMetric("render.triangles", 0));
        logManager.info("ProfilerDemo", "  Render Passes: " + 
                       data.getMetric("render.passes", 0));
        
        logManager.info("ProfilerDemo", "=== END RESULTS ===");
    }
    
    private static void generateReports(ProfilerManager profilerManager) {
        try {
            ProfilerReport report = new ProfilerReport(profilerManager);
            
            // Generate JSON report
            String jsonFile = report.exportToJSON("profiler_demo_report.json");
            logManager.info("ProfilerDemo", "JSON report generated: " + jsonFile);
            
            // Generate CSV report
            String csvFile = report.exportToCSV("profiler_demo_report.csv");
            logManager.info("ProfilerDemo", "CSV report generated: " + csvFile);
            
        } catch (Exception e) {
            logManager.error("ProfilerDemo", "Failed to generate reports", e);
        }
    }
}