package engine.profiler;

import engine.logging.LogManager;
import engine.logging.MetricsCollector;
import engine.camera.Camera;
import engine.raster.Mesh;
import engine.shaders.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.FloatBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;

/**
 * Debug renderer for 3D visualization tools.
 * Provides wireframes, bounding boxes, collision shapes, and debug overlays.
 */
public class DebugRenderer implements IProfiler {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = logManager.getMetricsCollector();
    
    private final ProfilerConfiguration config;
    private final AtomicBoolean active = new AtomicBoolean(false);
    
    // Debug rendering state
    private boolean wireframeEnabled = false;
    private boolean boundingBoxesEnabled = false;
    private boolean collisionShapesEnabled = false;
    private boolean aiDebugEnabled = false;
    private boolean physicsDebugEnabled = false;
    private boolean networkDebugEnabled = false;
    
    // Debug objects
    private final Map<String, DebugObject> debugObjects = new ConcurrentHashMap<>();
    private final Queue<DebugLine> debugLines = new ConcurrentLinkedQueue<>();
    private final Queue<DebugText> debugTexts = new ConcurrentLinkedQueue<>();
    
    // Rendering resources
    private ShaderProgram debugShader;
    private int lineVAO, lineVBO;
    private int boxVAO, boxVBO, boxEBO;
    private int sphereVAO, sphereVBO, sphereEBO;
    
    // Debug colors
    private static final Vector4f COLOR_WIREFRAME = new Vector4f(0.0f, 1.0f, 0.0f, 1.0f);
    private static final Vector4f COLOR_BOUNDING_BOX = new Vector4f(1.0f, 1.0f, 0.0f, 1.0f);
    private static final Vector4f COLOR_COLLISION_SHAPE = new Vector4f(1.0f, 0.0f, 0.0f, 1.0f);
    private static final Vector4f COLOR_AI_PATH = new Vector4f(0.0f, 0.0f, 1.0f, 1.0f);
    private static final Vector4f COLOR_PHYSICS_FORCE = new Vector4f(1.0f, 0.0f, 1.0f, 1.0f);
    private static final Vector4f COLOR_NETWORK_CONNECTION = new Vector4f(0.0f, 1.0f, 1.0f, 1.0f);
    
    // Performance tracking
    private long debugDrawCalls = 0;
    private long debugVertices = 0;
    
    public DebugRenderer(ProfilerConfiguration config) {
        this.config = config;
    }
    
    @Override
    public void initialize() {
        logManager.info("DebugRenderer", "Initializing debug renderer",
                       "wireframeRendering", config.isWireframeRendering(),
                       "boundingBoxRendering", config.isBoundingBoxRendering(),
                       "collisionShapeRendering", config.isCollisionShapeRendering(),
                       "aiDebugVisualization", config.isAiDebugVisualization());
        
        // Initialize OpenGL resources
        initializeRenderingResources();
        
        // Set initial debug states
        wireframeEnabled = config.isWireframeRendering();
        boundingBoxesEnabled = config.isBoundingBoxRendering();
        collisionShapesEnabled = config.isCollisionShapeRendering();
        aiDebugEnabled = config.isAiDebugVisualization();
        
        logManager.info("DebugRenderer", "Debug renderer initialized");
    }
    
    @Override
    public void start() {
        if (active.get()) {
            return;
        }
        
        logManager.info("DebugRenderer", "Starting debug rendering");
        
        // Clear all debug objects
        debugObjects.clear();
        debugLines.clear();
        debugTexts.clear();
        
        // Reset performance counters
        debugDrawCalls = 0;
        debugVertices = 0;
        
        active.set(true);
        
        logManager.info("DebugRenderer", "Debug rendering started");
    }
    
    @Override
    public void stop() {
        if (!active.get()) {
            return;
        }
        
        logManager.info("DebugRenderer", "Stopping debug rendering");
        
        active.set(false);
        
        logManager.info("DebugRenderer", "Debug rendering stopped");
    }
    
    @Override
    public void update(float deltaTime) {
        if (!active.get()) {
            return;
        }
        
        // Clean up expired debug objects
        cleanupExpiredObjects();
        
        // Update debug object animations
        updateDebugObjects(deltaTime);
    }
    
    @Override
    public ProfilerData collectData() {
        ProfilerData data = new ProfilerData("debug");
        
        // Debug rendering state
        data.addMetric("wireframeEnabled", wireframeEnabled);
        data.addMetric("boundingBoxesEnabled", boundingBoxesEnabled);
        data.addMetric("collisionShapesEnabled", collisionShapesEnabled);
        data.addMetric("aiDebugEnabled", aiDebugEnabled);
        data.addMetric("physicsDebugEnabled", physicsDebugEnabled);
        data.addMetric("networkDebugEnabled", networkDebugEnabled);
        
        // Debug object counts
        data.addMetric("debugObjectCount", debugObjects.size());
        data.addMetric("debugLineCount", debugLines.size());
        data.addMetric("debugTextCount", debugTexts.size());
        
        // Performance metrics
        data.addMetric("debugDrawCalls", debugDrawCalls);
        data.addMetric("debugVertices", debugVertices);
        
        // Debug object type breakdown
        Map<String, Integer> typeCounts = new ConcurrentHashMap<>();
        for (DebugObject obj : debugObjects.values()) {
            typeCounts.merge(obj.type.name(), 1, Integer::sum);
        }
        
        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            data.addMetric("debugObjects." + entry.getKey(), entry.getValue());
        }
        
        // Add metadata
        data.addMetadata("wireframeRendering", config.isWireframeRendering());
        data.addMetadata("boundingBoxRendering", config.isBoundingBoxRendering());
        data.addMetadata("collisionShapeRendering", config.isCollisionShapeRendering());
        data.addMetadata("aiDebugVisualization", config.isAiDebugVisualization());
        
        return data;
    }
    
    @Override
    public void reset() {
        logManager.info("DebugRenderer", "Resetting debug renderer");
        
        debugObjects.clear();
        debugLines.clear();
        debugTexts.clear();
        
        debugDrawCalls = 0;
        debugVertices = 0;
    }
    
    @Override
    public void cleanup() {
        logManager.info("DebugRenderer", "Cleaning up debug renderer");
        
        stop();
        
        // Cleanup OpenGL resources
        cleanupRenderingResources();
        
        debugObjects.clear();
        debugLines.clear();
        debugTexts.clear();
        
        logManager.info("DebugRenderer", "Debug renderer cleanup complete");
    }
    
    @Override
    public boolean isActive() {
        return active.get();
    }
    
    @Override
    public String getProfilerType() {
        return "debug";
    }
    
    /**
     * Render all debug objects.
     */
    public void render(Camera camera, Matrix4f projectionMatrix, Matrix4f viewMatrix) {
        if (!active.get()) {
            return;
        }
        
        // Enable debug rendering state
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glLineWidth(2.0f);
        
        // Use debug shader
        debugShader.bind();
        debugShader.setUniform("projectionMatrix", projectionMatrix);
        debugShader.setUniform("viewMatrix", viewMatrix);
        
        debugDrawCalls = 0;
        debugVertices = 0;
        
        // Render debug objects
        renderDebugObjects();
        
        // Render debug lines
        renderDebugLines();
        
        // Render wireframes if enabled
        if (wireframeEnabled) {
            renderWireframes();
        }
        
        debugShader.unbind();
        
        // Restore rendering state
        GL11.glLineWidth(1.0f);
        GL11.glEnable(GL11.GL_CULL_FACE);
        
        // Record metrics
        metricsCollector.recordTime("debug.drawCalls", debugDrawCalls);
        metricsCollector.recordTime("debug.vertices", debugVertices);
    }
    
    /**
     * Add a bounding box for debug visualization.
     */
    public void addBoundingBox(String id, Vector3f min, Vector3f max, Vector4f color, float duration) {
        if (!active.get() || !boundingBoxesEnabled) {
            return;
        }
        
        DebugObject obj = new DebugObject();
        obj.id = id;
        obj.type = DebugObjectType.BOUNDING_BOX;
        obj.position = new Vector3f(min).add(max).mul(0.5f);
        obj.scale = new Vector3f(max).sub(min);
        obj.color = color != null ? color : COLOR_BOUNDING_BOX;
        obj.duration = duration;
        obj.creationTime = System.currentTimeMillis();
        
        debugObjects.put(id, obj);
        
        logManager.debug("DebugRenderer", "Added bounding box", "id", id);
    }
    
    /**
     * Add a collision shape for debug visualization.
     */
    public void addCollisionShape(String id, CollisionShapeType shapeType, Vector3f position, 
                                 Vector3f size, Vector4f color, float duration) {
        if (!active.get() || !collisionShapesEnabled) {
            return;
        }
        
        DebugObject obj = new DebugObject();
        obj.id = id;
        obj.type = DebugObjectType.COLLISION_SHAPE;
        obj.shapeType = shapeType;
        obj.position = new Vector3f(position);
        obj.scale = new Vector3f(size);
        obj.color = color != null ? color : COLOR_COLLISION_SHAPE;
        obj.duration = duration;
        obj.creationTime = System.currentTimeMillis();
        
        debugObjects.put(id, obj);
        
        logManager.debug("DebugRenderer", "Added collision shape", "id", id, "type", shapeType);
    }
    
    /**
     * Add an AI pathfinding route for debug visualization.
     */
    public void addAIPath(String id, List<Vector3f> waypoints, Vector4f color, float duration) {
        if (!active.get() || !aiDebugEnabled) {
            return;
        }
        
        // Add lines between waypoints
        for (int i = 0; i < waypoints.size() - 1; i++) {
            Vector3f start = waypoints.get(i);
            Vector3f end = waypoints.get(i + 1);
            
            DebugLine line = new DebugLine();
            line.id = id + "_segment_" + i;
            line.start = new Vector3f(start);
            line.end = new Vector3f(end);
            line.color = color != null ? color : COLOR_AI_PATH;
            line.duration = duration;
            line.creationTime = System.currentTimeMillis();
            
            debugLines.offer(line);
        }
        
        // Add waypoint markers
        for (int i = 0; i < waypoints.size(); i++) {
            DebugObject obj = new DebugObject();
            obj.id = id + "_waypoint_" + i;
            obj.type = DebugObjectType.SPHERE;
            obj.position = new Vector3f(waypoints.get(i));
            obj.scale = new Vector3f(0.2f);
            obj.color = color != null ? color : COLOR_AI_PATH;
            obj.duration = duration;
            obj.creationTime = System.currentTimeMillis();
            
            debugObjects.put(obj.id, obj);
        }
        
        logManager.debug("DebugRenderer", "Added AI path", "id", id, "waypoints", waypoints.size());
    }
    
    /**
     * Toggle wireframe rendering.
     */
    public void setWireframeEnabled(boolean enabled) {
        wireframeEnabled = enabled;
        logManager.debug("DebugRenderer", "Wireframe rendering", "enabled", enabled);
    }
    
    /**
     * Toggle bounding box rendering.
     */
    public void setBoundingBoxesEnabled(boolean enabled) {
        boundingBoxesEnabled = enabled;
        logManager.debug("DebugRenderer", "Bounding box rendering", "enabled", enabled);
    }
    
    /**
     * Toggle collision shape rendering.
     */
    public void setCollisionShapesEnabled(boolean enabled) {
        collisionShapesEnabled = enabled;
        logManager.debug("DebugRenderer", "Collision shape rendering", "enabled", enabled);
    }
    
    /**
     * Toggle AI debug visualization.
     */
    public void setAIDebugEnabled(boolean enabled) {
        aiDebugEnabled = enabled;
        logManager.debug("DebugRenderer", "AI debug visualization", "enabled", enabled);
    }
    
    /**
     * Toggle physics debug visualization.
     */
    public void setPhysicsDebugEnabled(boolean enabled) {
        physicsDebugEnabled = enabled;
        logManager.debug("DebugRenderer", "Physics debug visualization", "enabled", enabled);
    }
    
    /**
     * Toggle network debug visualization.
     */
    public void setNetworkDebugEnabled(boolean enabled) {
        networkDebugEnabled = enabled;
        logManager.debug("DebugRenderer", "Network debug visualization", "enabled", enabled);
    }
    
    private void initializeRenderingResources() {
        // Create debug shader
        createDebugShader();
        
        // Create line rendering resources
        createLineResources();
        
        // Create box rendering resources
        createBoxResources();
        
        // Create sphere rendering resources
        createSphereResources();
    }
    
    private void createDebugShader() {
        String vertexShader = 
            "#version 330 core\n" +
            "layout (location = 0) in vec3 position;\n" +
            "uniform mat4 projectionMatrix;\n" +
            "uniform mat4 viewMatrix;\n" +
            "uniform mat4 modelMatrix;\n" +
            "void main() {\n" +
            "    gl_Position = projectionMatrix * viewMatrix * modelMatrix * vec4(position, 1.0);\n" +
            "}\n";
        
        String fragmentShader = 
            "#version 330 core\n" +
            "uniform vec4 color;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = color;\n" +
            "}\n";
        
        try {
            debugShader = new ShaderProgram();
            debugShader.createVertexShader(vertexShader);
            debugShader.createFragmentShader(fragmentShader);
            debugShader.link();
        } catch (Exception e) {
            logManager.error("DebugRenderer", "Failed to create debug shader", e);
        }
    }
    
    private void createLineResources() {
        lineVAO = GL30.glGenVertexArrays();
        lineVBO = GL15.glGenBuffers();
        
        GL30.glBindVertexArray(lineVAO);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, lineVBO);
        
        // Allocate buffer for line vertices (2 vertices * 3 components)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 6 * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
        
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);
        GL20.glEnableVertexAttribArray(0);
        
        GL30.glBindVertexArray(0);
    }
    
    private void createBoxResources() {
        // Create box vertices (wireframe cube)
        float[] boxVertices = {
            // Bottom face
            -0.5f, -0.5f, -0.5f,  0.5f, -0.5f, -0.5f,
             0.5f, -0.5f, -0.5f,  0.5f, -0.5f,  0.5f,
             0.5f, -0.5f,  0.5f, -0.5f, -0.5f,  0.5f,
            -0.5f, -0.5f,  0.5f, -0.5f, -0.5f, -0.5f,
            // Top face
            -0.5f,  0.5f, -0.5f,  0.5f,  0.5f, -0.5f,
             0.5f,  0.5f, -0.5f,  0.5f,  0.5f,  0.5f,
             0.5f,  0.5f,  0.5f, -0.5f,  0.5f,  0.5f,
            -0.5f,  0.5f,  0.5f, -0.5f,  0.5f, -0.5f,
            // Vertical edges
            -0.5f, -0.5f, -0.5f, -0.5f,  0.5f, -0.5f,
             0.5f, -0.5f, -0.5f,  0.5f,  0.5f, -0.5f,
             0.5f, -0.5f,  0.5f,  0.5f,  0.5f,  0.5f,
            -0.5f, -0.5f,  0.5f, -0.5f,  0.5f,  0.5f
        };
        
        boxVAO = GL30.glGenVertexArrays();
        boxVBO = GL15.glGenBuffers();
        
        GL30.glBindVertexArray(boxVAO);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, boxVBO);
        
        FloatBuffer buffer = BufferUtils.createFloatBuffer(boxVertices.length);
        buffer.put(boxVertices).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);
        GL20.glEnableVertexAttribArray(0);
        
        GL30.glBindVertexArray(0);
    }
    
    private void createSphereResources() {
        // Create simple sphere wireframe (simplified for debug purposes)
        List<Float> vertices = new ArrayList<>();
        int segments = 16;
        
        // Create horizontal circles
        for (int i = 0; i <= segments; i++) {
            float theta = (float)(i * Math.PI / segments);
            float y = (float)Math.cos(theta) * 0.5f;
            float radius = (float)Math.sin(theta) * 0.5f;
            
            for (int j = 0; j < segments; j++) {
                float phi1 = (float)(j * 2 * Math.PI / segments);
                float phi2 = (float)((j + 1) * 2 * Math.PI / segments);
                
                float x1 = (float)Math.cos(phi1) * radius;
                float z1 = (float)Math.sin(phi1) * radius;
                float x2 = (float)Math.cos(phi2) * radius;
                float z2 = (float)Math.sin(phi2) * radius;
                
                vertices.add(x1); vertices.add(y); vertices.add(z1);
                vertices.add(x2); vertices.add(y); vertices.add(z2);
            }
        }
        
        sphereVAO = GL30.glGenVertexArrays();
        sphereVBO = GL15.glGenBuffers();
        
        GL30.glBindVertexArray(sphereVAO);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, sphereVBO);
        
        float[] vertexArray = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            vertexArray[i] = vertices.get(i);
        }
        
        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertexArray.length);
        buffer.put(vertexArray).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);
        GL20.glEnableVertexAttribArray(0);
        
        GL30.glBindVertexArray(0);
    }
    
    private void renderDebugObjects() {
        for (DebugObject obj : debugObjects.values()) {
            Matrix4f modelMatrix = new Matrix4f()
                .translate(obj.position)
                .scale(obj.scale);
            
            debugShader.setUniform("modelMatrix", modelMatrix);
            debugShader.setUniform("color", obj.color);
            
            switch (obj.type) {
                case BOUNDING_BOX:
                    GL30.glBindVertexArray(boxVAO);
                    GL11.glDrawArrays(GL11.GL_LINES, 0, 24);
                    debugDrawCalls++;
                    debugVertices += 24;
                    break;
                    
                case SPHERE:
                    GL30.glBindVertexArray(sphereVAO);
                    GL11.glDrawArrays(GL11.GL_LINES, 0, 32 * 16 * 2);
                    debugDrawCalls++;
                    debugVertices += 32 * 16 * 2;
                    break;
                    
                case COLLISION_SHAPE:
                    if (obj.shapeType == CollisionShapeType.BOX) {
                        GL30.glBindVertexArray(boxVAO);
                        GL11.glDrawArrays(GL11.GL_LINES, 0, 24);
                    } else if (obj.shapeType == CollisionShapeType.SPHERE) {
                        GL30.glBindVertexArray(sphereVAO);
                        GL11.glDrawArrays(GL11.GL_LINES, 0, 32 * 16 * 2);
                    }
                    debugDrawCalls++;
                    debugVertices += 24;
                    break;
            }
        }
        
        GL30.glBindVertexArray(0);
    }
    
    private void renderDebugLines() {
        GL30.glBindVertexArray(lineVAO);
        
        for (DebugLine line : debugLines) {
            float[] vertices = {
                line.start.x, line.start.y, line.start.z,
                line.end.x, line.end.y, line.end.z
            };
            
            FloatBuffer buffer = BufferUtils.createFloatBuffer(6);
            buffer.put(vertices).flip();
            
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, lineVBO);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, buffer);
            
            Matrix4f modelMatrix = new Matrix4f();
            debugShader.setUniform("modelMatrix", modelMatrix);
            debugShader.setUniform("color", line.color);
            
            GL11.glDrawArrays(GL11.GL_LINES, 0, 2);
            debugDrawCalls++;
            debugVertices += 2;
        }
        
        GL30.glBindVertexArray(0);
    }
    
    private void renderWireframes() {
        // Enable wireframe mode
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        
        // Render wireframes for all visible objects
        // This would integrate with the main rendering system
        
        // Restore fill mode
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
    }
    
    private void cleanupExpiredObjects() {
        long currentTime = System.currentTimeMillis();
        
        // Remove expired debug objects
        debugObjects.entrySet().removeIf(entry -> {
            DebugObject obj = entry.getValue();
            return obj.duration > 0 && (currentTime - obj.creationTime) > obj.duration * 1000;
        });
        
        // Remove expired debug lines
        debugLines.removeIf(line -> 
            line.duration > 0 && (currentTime - line.creationTime) > line.duration * 1000);
        
        // Remove expired debug text
        debugTexts.removeIf(text -> 
            text.duration > 0 && (currentTime - text.creationTime) > text.duration * 1000);
    }
    
    private void updateDebugObjects(float deltaTime) {
        // Update animations or dynamic properties of debug objects
        for (DebugObject obj : debugObjects.values()) {
            // Add any animation logic here
        }
    }
    
    private void cleanupRenderingResources() {
        if (debugShader != null) {
            debugShader.cleanup();
        }
        
        if (lineVAO != 0) {
            GL30.glDeleteVertexArrays(lineVAO);
            GL15.glDeleteBuffers(lineVBO);
        }
        
        if (boxVAO != 0) {
            GL30.glDeleteVertexArrays(boxVAO);
            GL15.glDeleteBuffers(boxVBO);
        }
        
        if (sphereVAO != 0) {
            GL30.glDeleteVertexArrays(sphereVAO);
            GL15.glDeleteBuffers(sphereVBO);
        }
    }
    
    // Helper classes and enums
    public enum DebugObjectType {
        BOUNDING_BOX,
        SPHERE,
        COLLISION_SHAPE,
        LINE,
        TEXT
    }
    
    public enum CollisionShapeType {
        BOX,
        SPHERE,
        CAPSULE,
        MESH
    }
    
    private static class DebugObject {
        String id;
        DebugObjectType type;
        CollisionShapeType shapeType;
        Vector3f position = new Vector3f();
        Vector3f scale = new Vector3f(1.0f);
        Vector4f color = new Vector4f(1.0f);
        float duration = 0.0f; // 0 = permanent
        long creationTime = 0;
        Map<String, Object> metadata = new ConcurrentHashMap<>();
    }
    
    private static class DebugLine {
        String id;
        Vector3f start = new Vector3f();
        Vector3f end = new Vector3f();
        Vector4f color = new Vector4f(1.0f);
        float duration = 0.0f; // 0 = permanent
        long creationTime = 0;
        Map<String, Object> metadata = new ConcurrentHashMap<>();
    }
    
    private static class DebugText {
        String id;
        Vector3f position = new Vector3f();
        String text;
        Vector4f color = new Vector4f(1.0f);
        float duration = 0.0f; // 0 = permanent
        long creationTime = 0;
        Map<String, Object> metadata = new ConcurrentHashMap<>();
    }
}