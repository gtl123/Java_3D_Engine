package engine.ai.debug;

import engine.logging.LogManager;
import org.joml.Vector3f;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debug renderer for AI visualization
 * Provides visual debugging capabilities for AI systems
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class AIDebugRenderer {
    
    private static final LogManager logger = LogManager.getInstance();
    
    // Debug visualization data
    private final Map<String, List<DebugLine>> debugLines;
    private final Map<String, List<DebugSphere>> debugSpheres;
    private final Map<String, List<DebugText>> debugTexts;
    private final Map<String, List<DebugPath>> debugPaths;
    
    // Rendering settings
    private boolean enableRendering = true;
    private float lineWidth = 2.0f;
    private float sphereRadius = 0.5f;
    private float textScale = 1.0f;
    
    public AIDebugRenderer() {
        this.debugLines = new ConcurrentHashMap<>();
        this.debugSpheres = new ConcurrentHashMap<>();
        this.debugTexts = new ConcurrentHashMap<>();
        this.debugPaths = new ConcurrentHashMap<>();
        
        logger.info("AIDebugRenderer initialized");
    }
    
    /**
     * Add a debug line for an entity
     * @param entityId Entity identifier
     * @param start Start position
     * @param end End position
     * @param color Color (RGB)
     */
    public void addDebugLine(String entityId, Vector3f start, Vector3f end, Vector3f color) {
        if (!enableRendering) return;
        
        debugLines.computeIfAbsent(entityId, k -> new ArrayList<>())
                  .add(new DebugLine(start, end, color));
    }
    
    /**
     * Add a debug sphere for an entity
     * @param entityId Entity identifier
     * @param position Sphere position
     * @param radius Sphere radius
     * @param color Color (RGB)
     */
    public void addDebugSphere(String entityId, Vector3f position, float radius, Vector3f color) {
        if (!enableRendering) return;
        
        debugSpheres.computeIfAbsent(entityId, k -> new ArrayList<>())
                   .add(new DebugSphere(position, radius, color));
    }
    
    /**
     * Add debug text for an entity
     * @param entityId Entity identifier
     * @param position Text position
     * @param text Text content
     * @param color Color (RGB)
     */
    public void addDebugText(String entityId, Vector3f position, String text, Vector3f color) {
        if (!enableRendering) return;
        
        debugTexts.computeIfAbsent(entityId, k -> new ArrayList<>())
                  .add(new DebugText(position, text, color));
    }
    
    /**
     * Add a debug path for an entity
     * @param entityId Entity identifier
     * @param waypoints Path waypoints
     * @param color Path color
     */
    public void addDebugPath(String entityId, List<Vector3f> waypoints, Vector3f color) {
        if (!enableRendering || waypoints.isEmpty()) return;
        
        debugPaths.computeIfAbsent(entityId, k -> new ArrayList<>())
                  .add(new DebugPath(new ArrayList<>(waypoints), color));
    }
    
    /**
     * Clear all debug data for an entity
     * @param entityId Entity identifier
     */
    public void clearEntityDebugData(String entityId) {
        debugLines.remove(entityId);
        debugSpheres.remove(entityId);
        debugTexts.remove(entityId);
        debugPaths.remove(entityId);
    }
    
    /**
     * Clear all debug data
     */
    public void clearAllDebugData() {
        debugLines.clear();
        debugSpheres.clear();
        debugTexts.clear();
        debugPaths.clear();
    }
    
    /**
     * Render all debug data (placeholder - would integrate with actual renderer)
     * In a real implementation, this would interface with the engine's rendering system
     */
    public void render() {
        if (!enableRendering) return;
        
        // This is a placeholder implementation
        // In a real engine, this would:
        // 1. Interface with the graphics API (OpenGL/Vulkan/DirectX)
        // 2. Create vertex buffers for lines and shapes
        // 3. Use appropriate shaders for debug rendering
        // 4. Handle depth testing and blending for debug overlays
        
        renderDebugLines();
        renderDebugSpheres();
        renderDebugTexts();
        renderDebugPaths();
    }
    
    /**
     * Render debug lines (placeholder)
     */
    private void renderDebugLines() {
        for (Map.Entry<String, List<DebugLine>> entry : debugLines.entrySet()) {
            for (DebugLine line : entry.getValue()) {
                // Placeholder: In real implementation, would render line from start to end
                // with specified color and line width
            }
        }
    }
    
    /**
     * Render debug spheres (placeholder)
     */
    private void renderDebugSpheres() {
        for (Map.Entry<String, List<DebugSphere>> entry : debugSpheres.entrySet()) {
            for (DebugSphere sphere : entry.getValue()) {
                // Placeholder: In real implementation, would render wireframe sphere
                // at position with specified radius and color
            }
        }
    }
    
    /**
     * Render debug texts (placeholder)
     */
    private void renderDebugTexts() {
        for (Map.Entry<String, List<DebugText>> entry : debugTexts.entrySet()) {
            for (DebugText text : entry.getValue()) {
                // Placeholder: In real implementation, would render text
                // at world position with specified color and scale
            }
        }
    }
    
    /**
     * Render debug paths (placeholder)
     */
    private void renderDebugPaths() {
        for (Map.Entry<String, List<DebugPath>> entry : debugPaths.entrySet()) {
            for (DebugPath path : entry.getValue()) {
                List<Vector3f> waypoints = path.getWaypoints();
                
                // Render path as connected line segments
                for (int i = 0; i < waypoints.size() - 1; i++) {
                    Vector3f start = waypoints.get(i);
                    Vector3f end = waypoints.get(i + 1);
                    // Placeholder: render line from start to end with path color
                }
                
                // Render waypoint markers
                for (Vector3f waypoint : waypoints) {
                    // Placeholder: render small sphere at waypoint position
                }
            }
        }
    }
    
    /**
     * Get debug statistics
     * @return Debug statistics string
     */
    public String getDebugStats() {
        int totalLines = debugLines.values().stream().mapToInt(List::size).sum();
        int totalSpheres = debugSpheres.values().stream().mapToInt(List::size).sum();
        int totalTexts = debugTexts.values().stream().mapToInt(List::size).sum();
        int totalPaths = debugPaths.values().stream().mapToInt(List::size).sum();
        
        return String.format("Debug Renderer Stats: %d lines, %d spheres, %d texts, %d paths",
                           totalLines, totalSpheres, totalTexts, totalPaths);
    }
    
    // Getters and setters
    public boolean isRenderingEnabled() { return enableRendering; }
    public void setRenderingEnabled(boolean enabled) { this.enableRendering = enabled; }
    
    public float getLineWidth() { return lineWidth; }
    public void setLineWidth(float lineWidth) { this.lineWidth = lineWidth; }
    
    public float getSphereRadius() { return sphereRadius; }
    public void setSphereRadius(float sphereRadius) { this.sphereRadius = sphereRadius; }
    
    public float getTextScale() { return textScale; }
    public void setTextScale(float textScale) { this.textScale = textScale; }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        clearAllDebugData();
        logger.info("AIDebugRenderer cleaned up");
    }
    
    // Debug primitive classes
    public static class DebugLine {
        private final Vector3f start;
        private final Vector3f end;
        private final Vector3f color;
        
        public DebugLine(Vector3f start, Vector3f end, Vector3f color) {
            this.start = new Vector3f(start);
            this.end = new Vector3f(end);
            this.color = new Vector3f(color);
        }
        
        public Vector3f getStart() { return start; }
        public Vector3f getEnd() { return end; }
        public Vector3f getColor() { return color; }
    }
    
    public static class DebugSphere {
        private final Vector3f position;
        private final float radius;
        private final Vector3f color;
        
        public DebugSphere(Vector3f position, float radius, Vector3f color) {
            this.position = new Vector3f(position);
            this.radius = radius;
            this.color = new Vector3f(color);
        }
        
        public Vector3f getPosition() { return position; }
        public float getRadius() { return radius; }
        public Vector3f getColor() { return color; }
    }
    
    public static class DebugText {
        private final Vector3f position;
        private final String text;
        private final Vector3f color;
        
        public DebugText(Vector3f position, String text, Vector3f color) {
            this.position = new Vector3f(position);
            this.text = text;
            this.color = new Vector3f(color);
        }
        
        public Vector3f getPosition() { return position; }
        public String getText() { return text; }
        public Vector3f getColor() { return color; }
    }
    
    public static class DebugPath {
        private final List<Vector3f> waypoints;
        private final Vector3f color;
        
        public DebugPath(List<Vector3f> waypoints, Vector3f color) {
            this.waypoints = new ArrayList<>();
            for (Vector3f waypoint : waypoints) {
                this.waypoints.add(new Vector3f(waypoint));
            }
            this.color = new Vector3f(color);
        }
        
        public List<Vector3f> getWaypoints() { return waypoints; }
        public Vector3f getColor() { return color; }
    }
}