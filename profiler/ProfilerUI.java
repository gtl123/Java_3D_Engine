package engine.profiler;

import engine.logging.LogManager;
import engine.logging.MetricsCollector;
import engine.io.Window;
import engine.camera.Camera;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;

/**
 * Real-time profiling dashboard UI.
 * Provides interactive performance graphs, metrics display, and profiler controls.
 */
public class ProfilerUI implements IProfiler {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = logManager.getMetricsCollector();
    
    private final ProfilerConfiguration config;
    private final ProfilerManager profilerManager;
    private final AtomicBoolean active = new AtomicBoolean(false);
    
    // UI state
    private boolean visible = false;
    private UIPanel mainPanel;
    private final Map<String, UIGraph> performanceGraphs = new ConcurrentHashMap<>();
    private final Map<String, UIMetricDisplay> metricDisplays = new ConcurrentHashMap<>();
    
    // UI layout
    private int windowWidth = 1280;
    private int windowHeight = 800;
    private final Vector2f panelPosition = new Vector2f(10, 10);
    private final Vector2f panelSize = new Vector2f(400, 600);
    
    // Rendering resources
    private int uiVAO, uiVBO;
    private int textVAO, textVBO;
    
    // Update intervals
    private long lastUIUpdate = 0;
    private final long uiUpdateIntervalMs = 100; // Update UI every 100ms
    
    // Graph data
    private final Queue<GraphDataPoint> fpsHistory = new ConcurrentLinkedQueue<>();
    private final Queue<GraphDataPoint> memoryHistory = new ConcurrentLinkedQueue<>();
    private final Queue<GraphDataPoint> networkHistory = new ConcurrentLinkedQueue<>();
    private final Queue<GraphDataPoint> renderHistory = new ConcurrentLinkedQueue<>();
    
    // UI colors
    private static final Vector4f COLOR_BACKGROUND = new Vector4f(0.1f, 0.1f, 0.1f, 0.8f);
    private static final Vector4f COLOR_PANEL = new Vector4f(0.2f, 0.2f, 0.2f, 0.9f);
    private static final Vector4f COLOR_TEXT = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    private static final Vector4f COLOR_GRAPH_FPS = new Vector4f(0.0f, 1.0f, 0.0f, 1.0f);
    private static final Vector4f COLOR_GRAPH_MEMORY = new Vector4f(1.0f, 0.0f, 0.0f, 1.0f);
    private static final Vector4f COLOR_GRAPH_NETWORK = new Vector4f(0.0f, 0.0f, 1.0f, 1.0f);
    private static final Vector4f COLOR_GRAPH_RENDER = new Vector4f(1.0f, 1.0f, 0.0f, 1.0f);
    
    public ProfilerUI(ProfilerConfiguration config, ProfilerManager profilerManager) {
        this.config = config;
        this.profilerManager = profilerManager;
    }
    
    @Override
    public void initialize() {
        logManager.info("ProfilerUI", "Initializing profiler UI");
        
        // Initialize UI components
        initializeUIComponents();
        
        // Initialize rendering resources
        initializeRenderingResources();
        
        // Create performance graphs
        createPerformanceGraphs();
        
        // Create metric displays
        createMetricDisplays();
        
        logManager.info("ProfilerUI", "Profiler UI initialized");
    }
    
    @Override
    public void start() {
        if (active.get()) {
            return;
        }
        
        logManager.info("ProfilerUI", "Starting profiler UI");
        
        // Clear graph data
        fpsHistory.clear();
        memoryHistory.clear();
        networkHistory.clear();
        renderHistory.clear();
        
        lastUIUpdate = System.currentTimeMillis();
        
        active.set(true);
        
        logManager.info("ProfilerUI", "Profiler UI started");
    }
    
    @Override
    public void stop() {
        if (!active.get()) {
            return;
        }
        
        logManager.info("ProfilerUI", "Stopping profiler UI");
        
        visible = false;
        active.set(false);
        
        logManager.info("ProfilerUI", "Profiler UI stopped");
    }
    
    @Override
    public void update(float deltaTime) {
        if (!active.get()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // Update UI data periodically
        if (currentTime - lastUIUpdate >= uiUpdateIntervalMs) {
            updateUIData();
            lastUIUpdate = currentTime;
        }
        
        // Update graphs
        updateGraphs();
        
        // Update metric displays
        updateMetricDisplays();
    }
    
    @Override
    public ProfilerData collectData() {
        ProfilerData data = new ProfilerData("ui");
        
        // UI state
        data.addMetric("visible", visible);
        data.addMetric("graphCount", performanceGraphs.size());
        data.addMetric("metricDisplayCount", metricDisplays.size());
        
        // Graph data points
        data.addMetric("fpsHistorySize", fpsHistory.size());
        data.addMetric("memoryHistorySize", memoryHistory.size());
        data.addMetric("networkHistorySize", networkHistory.size());
        data.addMetric("renderHistorySize", renderHistory.size());
        
        // Add metadata
        data.addMetadata("updateInterval", uiUpdateIntervalMs);
        data.addMetadata("panelPosition", panelPosition.toString());
        data.addMetadata("panelSize", panelSize.toString());
        
        return data;
    }
    
    @Override
    public void reset() {
        logManager.info("ProfilerUI", "Resetting profiler UI");
        
        fpsHistory.clear();
        memoryHistory.clear();
        networkHistory.clear();
        renderHistory.clear();
        
        // Reset graphs
        for (UIGraph graph : performanceGraphs.values()) {
            graph.reset();
        }
        
        // Reset metric displays
        for (UIMetricDisplay display : metricDisplays.values()) {
            display.reset();
        }
    }
    
    @Override
    public void cleanup() {
        logManager.info("ProfilerUI", "Cleaning up profiler UI");
        
        stop();
        
        // Cleanup rendering resources
        cleanupRenderingResources();
        
        performanceGraphs.clear();
        metricDisplays.clear();
        fpsHistory.clear();
        memoryHistory.clear();
        networkHistory.clear();
        renderHistory.clear();
        
        logManager.info("ProfilerUI", "Profiler UI cleanup complete");
    }
    
    @Override
    public boolean isActive() {
        return active.get();
    }
    
    @Override
    public String getProfilerType() {
        return "ui";
    }
    
    /**
     * Render the profiler UI.
     */
    public void render(Window window, Matrix4f projectionMatrix) {
        if (!active.get() || !visible) {
            return;
        }
        
        // Update window dimensions
        windowWidth = window.getWidth();
        windowHeight = window.getHeight();
        
        // Enable UI rendering state
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        
        // Render main panel
        renderMainPanel();
        
        // Render performance graphs
        renderPerformanceGraphs();
        
        // Render metric displays
        renderMetricDisplays();
        
        // Render controls
        renderControls();
        
        // Restore rendering state
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
    
    /**
     * Toggle UI visibility.
     */
    public void toggleVisibility() {
        visible = !visible;
        logManager.debug("ProfilerUI", "UI visibility toggled", "visible", visible);
    }
    
    /**
     * Set UI visibility.
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
        logManager.debug("ProfilerUI", "UI visibility set", "visible", visible);
    }
    
    /**
     * Handle input events.
     */
    public void handleInput(int key, int action) {
        if (!active.get()) {
            return;
        }
        
        // Toggle visibility with F3 key (example)
        if (key == 292 && action == 1) { // F3 key pressed
            toggleVisibility();
        }
        
        // Handle other UI interactions
        if (visible) {
            handleUIInput(key, action);
        }
    }
    
    private void initializeUIComponents() {
        // Create main panel
        mainPanel = new UIPanel();
        mainPanel.position = new Vector2f(panelPosition);
        mainPanel.size = new Vector2f(panelSize);
        mainPanel.backgroundColor = COLOR_PANEL;
        mainPanel.title = "Performance Profiler";
    }
    
    private void initializeRenderingResources() {
        // Create UI rendering VAO/VBO
        uiVAO = GL30.glGenVertexArrays();
        uiVBO = GL15.glGenBuffers();
        
        GL30.glBindVertexArray(uiVAO);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, uiVBO);
        
        // Allocate buffer for UI vertices
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 1024 * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
        
        // Position attribute
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);
        
        // UV attribute
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);
        
        GL30.glBindVertexArray(0);
        
        // Create text rendering VAO/VBO
        textVAO = GL30.glGenVertexArrays();
        textVBO = GL15.glGenBuffers();
        
        GL30.glBindVertexArray(textVAO);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, textVBO);
        
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 2048 * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
        
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);
        
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);
        
        GL30.glBindVertexArray(0);
    }
    
    private void createPerformanceGraphs() {
        // FPS graph
        UIGraph fpsGraph = new UIGraph();
        fpsGraph.title = "FPS";
        fpsGraph.position = new Vector2f(panelPosition.x + 10, panelPosition.y + 40);
        fpsGraph.size = new Vector2f(180, 80);
        fpsGraph.color = COLOR_GRAPH_FPS;
        fpsGraph.minValue = 0.0f;
        fpsGraph.maxValue = 120.0f;
        performanceGraphs.put("fps", fpsGraph);
        
        // Memory graph
        UIGraph memoryGraph = new UIGraph();
        memoryGraph.title = "Memory (MB)";
        memoryGraph.position = new Vector2f(panelPosition.x + 200, panelPosition.y + 40);
        memoryGraph.size = new Vector2f(180, 80);
        memoryGraph.color = COLOR_GRAPH_MEMORY;
        memoryGraph.minValue = 0.0f;
        memoryGraph.maxValue = 1024.0f;
        performanceGraphs.put("memory", memoryGraph);
        
        // Network graph
        UIGraph networkGraph = new UIGraph();
        networkGraph.title = "Network (ms)";
        networkGraph.position = new Vector2f(panelPosition.x + 10, panelPosition.y + 140);
        networkGraph.size = new Vector2f(180, 80);
        networkGraph.color = COLOR_GRAPH_NETWORK;
        networkGraph.minValue = 0.0f;
        networkGraph.maxValue = 200.0f;
        performanceGraphs.put("network", networkGraph);
        
        // Render graph
        UIGraph renderGraph = new UIGraph();
        renderGraph.title = "Draw Calls";
        renderGraph.position = new Vector2f(panelPosition.x + 200, panelPosition.y + 140);
        renderGraph.size = new Vector2f(180, 80);
        renderGraph.color = COLOR_GRAPH_RENDER;
        renderGraph.minValue = 0.0f;
        renderGraph.maxValue = 5000.0f;
        performanceGraphs.put("render", renderGraph);
    }
    
    private void createMetricDisplays() {
        float yOffset = 240;
        float lineHeight = 20;
        
        // Performance metrics
        createMetricDisplay("frameTime", "Frame Time", panelPosition.x + 10, panelPosition.y + yOffset, "ms");
        yOffset += lineHeight;
        
        createMetricDisplay("heapUsage", "Heap Usage", panelPosition.x + 10, panelPosition.y + yOffset, "%");
        yOffset += lineHeight;
        
        createMetricDisplay("drawCalls", "Draw Calls", panelPosition.x + 10, panelPosition.y + yOffset, "");
        yOffset += lineHeight;
        
        createMetricDisplay("triangles", "Triangles", panelPosition.x + 10, panelPosition.y + yOffset, "");
        yOffset += lineHeight;
        
        createMetricDisplay("textureMemory", "Texture Memory", panelPosition.x + 10, panelPosition.y + yOffset, "MB");
        yOffset += lineHeight;
        
        createMetricDisplay("networkLatency", "Network Latency", panelPosition.x + 10, panelPosition.y + yOffset, "ms");
        yOffset += lineHeight;
        
        createMetricDisplay("activeConnections", "Active Connections", panelPosition.x + 10, panelPosition.y + yOffset, "");
    }
    
    private void createMetricDisplay(String id, String label, float x, float y, String unit) {
        UIMetricDisplay display = new UIMetricDisplay();
        display.id = id;
        display.label = label;
        display.unit = unit;
        display.position = new Vector2f(x, y);
        display.value = 0.0f;
        metricDisplays.put(id, display);
    }
    
    private void updateUIData() {
        if (!profilerManager.isEnabled()) {
            return;
        }
        
        // Collect data from profilers
        ProfilerReport report = profilerManager.generateReport();
        long currentTime = System.currentTimeMillis();
        
        // Update FPS data
        ProfilerData performanceData = report.getProfilerData().get("performance");
        if (performanceData != null) {
            Double fps = performanceData.getMetric("currentFPS", Double.class);
            if (fps != null) {
                fpsHistory.offer(new GraphDataPoint(currentTime, fps.floatValue()));
                performanceGraphs.get("fps").addDataPoint(fps.floatValue());
            }
            
            Double frameTime = performanceData.getMetric("averageFrameTime", Double.class);
            if (frameTime != null) {
                UIMetricDisplay display = metricDisplays.get("frameTime");
                if (display != null) {
                    display.value = frameTime.floatValue();
                }
            }
        }
        
        // Update memory data
        ProfilerData memoryData = report.getProfilerData().get("memory");
        if (memoryData != null) {
            Long heapUsed = memoryData.getMetric("heapUsedMB", Long.class);
            if (heapUsed != null) {
                memoryHistory.offer(new GraphDataPoint(currentTime, heapUsed.floatValue()));
                performanceGraphs.get("memory").addDataPoint(heapUsed.floatValue());
            }
            
            Double heapUsagePercent = memoryData.getMetric("heapUsagePercent", Double.class);
            if (heapUsagePercent != null) {
                UIMetricDisplay display = metricDisplays.get("heapUsage");
                if (display != null) {
                    display.value = heapUsagePercent.floatValue();
                }
            }
        }
        
        // Update network data
        ProfilerData networkData = report.getProfilerData().get("network");
        if (networkData != null) {
            Double latency = networkData.getMetric("averageLatency", Double.class);
            if (latency != null) {
                networkHistory.offer(new GraphDataPoint(currentTime, latency.floatValue()));
                performanceGraphs.get("network").addDataPoint(latency.floatValue());
                
                UIMetricDisplay display = metricDisplays.get("networkLatency");
                if (display != null) {
                    display.value = latency.floatValue();
                }
            }
            
            Long activeConns = networkData.getMetric("activeConnections", Long.class);
            if (activeConns != null) {
                UIMetricDisplay display = metricDisplays.get("activeConnections");
                if (display != null) {
                    display.value = activeConns.floatValue();
                }
            }
        }
        
        // Update render data
        ProfilerData renderData = report.getProfilerData().get("render");
        if (renderData != null) {
            Long drawCalls = renderData.getMetric("frameDrawCalls", Long.class);
            if (drawCalls != null) {
                renderHistory.offer(new GraphDataPoint(currentTime, drawCalls.floatValue()));
                performanceGraphs.get("render").addDataPoint(drawCalls.floatValue());
                
                UIMetricDisplay display = metricDisplays.get("drawCalls");
                if (display != null) {
                    display.value = drawCalls.floatValue();
                }
            }
            
            Long triangles = renderData.getMetric("frameTriangles", Long.class);
            if (triangles != null) {
                UIMetricDisplay display = metricDisplays.get("triangles");
                if (display != null) {
                    display.value = triangles.floatValue();
                }
            }
            
            Long textureMemoryMB = renderData.getMetric("totalTextureMemoryMB", Long.class);
            if (textureMemoryMB != null) {
                UIMetricDisplay display = metricDisplays.get("textureMemory");
                if (display != null) {
                    display.value = textureMemoryMB.floatValue();
                }
            }
        }
        
        // Clean up old graph data
        cleanupGraphData();
    }
    
    private void cleanupGraphData() {
        long cutoffTime = System.currentTimeMillis() - 60000; // Keep 1 minute of data
        
        fpsHistory.removeIf(point -> point.timestamp < cutoffTime);
        memoryHistory.removeIf(point -> point.timestamp < cutoffTime);
        networkHistory.removeIf(point -> point.timestamp < cutoffTime);
        renderHistory.removeIf(point -> point.timestamp < cutoffTime);
    }
    
    private void updateGraphs() {
        for (UIGraph graph : performanceGraphs.values()) {
            graph.update();
        }
    }
    
    private void updateMetricDisplays() {
        for (UIMetricDisplay display : metricDisplays.values()) {
            display.update();
        }
    }
    
    private void renderMainPanel() {
        // Render panel background
        renderQuad(mainPanel.position, mainPanel.size, mainPanel.backgroundColor);
        
        // Render panel title
        renderText(mainPanel.title, mainPanel.position.x + 10, mainPanel.position.y + 15, COLOR_TEXT);
    }
    
    private void renderPerformanceGraphs() {
        for (UIGraph graph : performanceGraphs.values()) {
            renderGraph(graph);
        }
    }
    
    private void renderMetricDisplays() {
        for (UIMetricDisplay display : metricDisplays.values()) {
            renderMetricDisplay(display);
        }
    }
    
    private void renderControls() {
        float yOffset = panelPosition.y + panelSize.y - 100;
        
        // Render profiler controls
        renderText("Controls:", panelPosition.x + 10, yOffset, COLOR_TEXT);
        yOffset += 20;
        
        renderText("F3: Toggle UI", panelPosition.x + 20, yOffset, COLOR_TEXT);
        yOffset += 15;
        
        renderText("F4: Reset Data", panelPosition.x + 20, yOffset, COLOR_TEXT);
        yOffset += 15;
        
        renderText("F5: Export Report", panelPosition.x + 20, yOffset, COLOR_TEXT);
    }
    
    private void renderGraph(UIGraph graph) {
        // Render graph background
        renderQuad(graph.position, graph.size, new Vector4f(0.0f, 0.0f, 0.0f, 0.5f));
        
        // Render graph title
        renderText(graph.title, graph.position.x + 5, graph.position.y - 5, COLOR_TEXT);
        
        // Render graph data
        if (!graph.dataPoints.isEmpty()) {
            renderGraphData(graph);
        }
        
        // Render graph border
        renderQuadOutline(graph.position, graph.size, COLOR_TEXT);
    }
    
    private void renderGraphData(UIGraph graph) {
        List<Float> points = new ArrayList<>(graph.dataPoints);
        if (points.size() < 2) return;
        
        float graphWidth = graph.size.x - 10;
        float graphHeight = graph.size.y - 10;
        float startX = graph.position.x + 5;
        float startY = graph.position.y + 5;
        
        // Create line vertices
        List<Float> vertices = new ArrayList<>();
        
        for (int i = 0; i < points.size() - 1; i++) {
            float x1 = startX + (i / (float)(points.size() - 1)) * graphWidth;
            float y1 = startY + ((points.get(i) - graph.minValue) / (graph.maxValue - graph.minValue)) * graphHeight;
            
            float x2 = startX + ((i + 1) / (float)(points.size() - 1)) * graphWidth;
            float y2 = startY + ((points.get(i + 1) - graph.minValue) / (graph.maxValue - graph.minValue)) * graphHeight;
            
            vertices.add(x1); vertices.add(y1);
            vertices.add(x2); vertices.add(y2);
        }
        
        // Render lines
        renderLines(vertices, graph.color);
    }
    
    private void renderMetricDisplay(UIMetricDisplay display) {
        String text = String.format("%s: %.1f %s", display.label, display.value, display.unit);
        renderText(text, display.position.x, display.position.y, COLOR_TEXT);
    }
    
    private void renderQuad(Vector2f position, Vector2f size, Vector4f color) {
        // Convert screen coordinates to normalized device coordinates
        float x1 = (position.x / windowWidth) * 2.0f - 1.0f;
        float y1 = 1.0f - (position.y / windowHeight) * 2.0f;
        float x2 = ((position.x + size.x) / windowWidth) * 2.0f - 1.0f;
        float y2 = 1.0f - ((position.y + size.y) / windowHeight) * 2.0f;
        
        float[] vertices = {
            x1, y1, 0.0f, 0.0f,
            x2, y1, 1.0f, 0.0f,
            x2, y2, 1.0f, 1.0f,
            x1, y1, 0.0f, 0.0f,
            x2, y2, 1.0f, 1.0f,
            x1, y2, 0.0f, 1.0f
        };
        
        GL30.glBindVertexArray(uiVAO);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, uiVBO);
        
        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, buffer);
        
        // Set color uniform (would need shader)
        GL11.glColor4f(color.x, color.y, color.z, color.w);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        
        GL30.glBindVertexArray(0);
    }
    
    private void renderQuadOutline(Vector2f position, Vector2f size, Vector4f color) {
        // Render quad outline using lines
        float x1 = (position.x / windowWidth) * 2.0f - 1.0f;
        float y1 = 1.0f - (position.y / windowHeight) * 2.0f;
        float x2 = ((position.x + size.x) / windowWidth) * 2.0f - 1.0f;
        float y2 = 1.0f - ((position.y + size.y) / windowHeight) * 2.0f;
        
        List<Float> vertices = new ArrayList<>();
        vertices.add(x1); vertices.add(y1);
        vertices.add(x2); vertices.add(y1);
        vertices.add(x2); vertices.add(y1);
        vertices.add(x2); vertices.add(y2);
        vertices.add(x2); vertices.add(y2);
        vertices.add(x1); vertices.add(y2);
        vertices.add(x1); vertices.add(y2);
        vertices.add(x1); vertices.add(y1);
        
        renderLines(vertices, color);
    }
    
    private void renderLines(List<Float> vertices, Vector4f color) {
        if (vertices.isEmpty()) return;
        
        GL30.glBindVertexArray(uiVAO);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, uiVBO);
        
        float[] vertexArray = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            vertexArray[i] = vertices.get(i);
        }
        
        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertexArray.length);
        buffer.put(vertexArray).flip();
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, buffer);
        
        GL11.glColor4f(color.x, color.y, color.z, color.w);
        GL11.glDrawArrays(GL11.GL_LINES, 0, vertices.size() / 2);
        
        GL30.glBindVertexArray(0);
    }
    
    private void renderText(String text, float x, float y, Vector4f color) {
        // Simplified text rendering - in a real implementation, this would use a font system
        // For now, we'll just log the text that would be rendered
        // logManager.debug("ProfilerUI", "Rendering text", "text", text, "x", x, "y", y);
    }
    
    private void handleUIInput(int key, int action) {
        if (action != 1) return; // Only handle key press
        
        switch (key) {
            case 293: // F4 - Reset data
                reset();
                break;
            case 294: // F5 - Export report
                exportReport();
                break;
        }
    }
    
    private void exportReport() {
        if (profilerManager.isEnabled()) {
            ProfilerReport report = profilerManager.generateReport();
            // Export logic would go here
            logManager.info("ProfilerUI", "Performance report exported");
        }
    }
    
    private void cleanupRenderingResources() {
        if (uiVAO != 0) {
            GL30.glDeleteVertexArrays(uiVAO);
            GL15.glDeleteBuffers(uiVBO);
        }
        
        if (textVAO != 0) {
            GL30.glDeleteVertexArrays(textVAO);
            GL15.glDeleteBuffers(textVBO);
        }
    }
    
    // Helper classes
    private static class UIPanel {
        Vector2f position = new Vector2f();
        Vector2f size = new Vector2f();
        Vector4f backgroundColor = new Vector4f();
        String title = "";
    }
    
    private static class UIGraph {
        String title = "";
        Vector2f position = new Vector2f();
        Vector2f size = new Vector2f();
        Vector4f color = new Vector4f();
        float minValue = 0.0f;
        float maxValue = 100.0f;
        Queue<Float> dataPoints = new ConcurrentLinkedQueue<>();
        
        void addDataPoint(float value) {
            dataPoints.offer(value);
            
            // Keep only last 100 data points for performance
            while (dataPoints.size() > 100) {
                dataPoints.poll();
            }
        }
        
        void update() {
            // Update graph animations or effects if needed
        }
        
        void reset() {
            dataPoints.clear();
        }
    }
    
    private static class UIMetricDisplay {
        String id = "";
        String label = "";
        String unit = "";
        Vector2f position = new Vector2f();
        float value = 0.0f;
        
        void update() {
            // Update display animations or formatting if needed
        }
        
        void reset() {
            value = 0.0f;
        }
    }
    
    private static class GraphDataPoint {
        final long timestamp;
        final float value;
        
        GraphDataPoint(long timestamp, float value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }
}