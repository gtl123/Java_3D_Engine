package engine.rendering.advanced;

import engine.config.ConfigurationManager;
import engine.logging.LogManager;
import engine.logging.MetricsCollector;
import engine.io.Window;
import engine.camera.Camera;
import org.joml.Vector2i;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Flexible Render Graph for orchestrating render passes and managing dependencies.
 * Provides automatic dependency resolution, resource management, and pass scheduling.
 * Supports both forward and deferred rendering pipelines with dynamic pass configuration.
 */
public class RenderGraph {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = LogManager.getInstance().getMetricsCollector();
    private static final ConfigurationManager configManager = ConfigurationManager.getInstance();
    
    // Graph structure
    private final Map<String, RenderPass> passes = new ConcurrentHashMap<>();
    private final Map<String, RenderResource> resources = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> dependencies = new ConcurrentHashMap<>();
    private final List<String> executionOrder = new ArrayList<>();
    
    // Graph state
    private boolean initialized = false;
    private boolean graphBuilt = false;
    private int frameWidth = 0;
    private int frameHeight = 0;
    
    // Performance tracking
    private long lastExecutionTime = 0;
    private int passesExecuted = 0;
    private Map<String, Long> passExecutionTimes = new ConcurrentHashMap<>();
    
    /**
     * Render resource representation for textures, buffers, etc.
     */
    public static class RenderResource {
        private final String name;
        private final ResourceType type;
        private final ResourceFormat format;
        private final Vector2i size;
        private final boolean persistent;
        
        private int resourceId = 0;
        private boolean allocated = false;
        
        public enum ResourceType {
            TEXTURE_2D,
            TEXTURE_CUBE,
            RENDER_BUFFER,
            FRAMEBUFFER
        }
        
        public enum ResourceFormat {
            RGBA8,
            RGBA16F,
            RGBA32F,
            RGB8,
            RGB16F,
            RGB32F,
            RG8,
            RG16F,
            RG32F,
            R8,
            R16F,
            R32F,
            DEPTH24,
            DEPTH32F,
            DEPTH24_STENCIL8
        }
        
        public RenderResource(String name, ResourceType type, ResourceFormat format, 
                            Vector2i size, boolean persistent) {
            this.name = name;
            this.type = type;
            this.format = format;
            this.size = new Vector2i(size);
            this.persistent = persistent;
        }
        
        // Getters
        public String getName() { return name; }
        public ResourceType getType() { return type; }
        public ResourceFormat getFormat() { return format; }
        public Vector2i getSize() { return new Vector2i(size); }
        public boolean isPersistent() { return persistent; }
        public int getResourceId() { return resourceId; }
        public boolean isAllocated() { return allocated; }
        
        // Internal setters
        void setResourceId(int id) { this.resourceId = id; }
        void setAllocated(boolean allocated) { this.allocated = allocated; }
    }
    
    /**
     * Base class for render passes in the graph.
     */
    public abstract static class RenderPass {
        protected final String name;
        protected final List<String> inputs = new ArrayList<>();
        protected final List<String> outputs = new ArrayList<>();
        protected boolean enabled = true;
        protected int priority = 100;
        
        public RenderPass(String name) {
            this.name = name;
        }
        
        /**
         * Setup the pass (called once during graph building).
         */
        public abstract void setup(RenderGraphBuilder builder);
        
        /**
         * Execute the pass.
         */
        public abstract void execute(RenderGraphContext context);
        
        /**
         * Cleanup the pass.
         */
        public void cleanup() {}
        
        // Getters and setters
        public String getName() { return name; }
        public List<String> getInputs() { return new ArrayList<>(inputs); }
        public List<String> getOutputs() { return new ArrayList<>(outputs); }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        
        protected void addInput(String resourceName) {
            inputs.add(resourceName);
        }
        
        protected void addOutput(String resourceName) {
            outputs.add(resourceName);
        }
    }
    
    /**
     * Context provided to render passes during execution.
     */
    public static class RenderGraphContext {
        private final Map<String, RenderResource> resources;
        private final Window window;
        private final Camera camera;
        private final float deltaTime;
        
        public RenderGraphContext(Map<String, RenderResource> resources, Window window, 
                                Camera camera, float deltaTime) {
            this.resources = resources;
            this.window = window;
            this.camera = camera;
            this.deltaTime = deltaTime;
        }
        
        public RenderResource getResource(String name) {
            return resources.get(name);
        }
        
        public int getResourceId(String name) {
            RenderResource resource = resources.get(name);
            return resource != null ? resource.getResourceId() : 0;
        }
        
        public Window getWindow() { return window; }
        public Camera getCamera() { return camera; }
        public float getDeltaTime() { return deltaTime; }
    }
    
    /**
     * Builder for constructing render graphs.
     */
    public static class RenderGraphBuilder {
        private final RenderGraph graph;
        
        public RenderGraphBuilder(RenderGraph graph) {
            this.graph = graph;
        }
        
        /**
         * Create a texture resource.
         */
        public RenderGraphBuilder createTexture(String name, RenderResource.ResourceFormat format, 
                                              Vector2i size, boolean persistent) {
            RenderResource resource = new RenderResource(name, RenderResource.ResourceType.TEXTURE_2D, 
                                                       format, size, persistent);
            graph.resources.put(name, resource);
            return this;
        }
        
        /**
         * Create a texture resource with screen size.
         */
        public RenderGraphBuilder createTexture(String name, RenderResource.ResourceFormat format, 
                                              boolean persistent) {
            return createTexture(name, format, new Vector2i(graph.frameWidth, graph.frameHeight), persistent);
        }
        
        /**
         * Create a framebuffer resource.
         */
        public RenderGraphBuilder createFramebuffer(String name, boolean persistent) {
            RenderResource resource = new RenderResource(name, RenderResource.ResourceType.FRAMEBUFFER, 
                                                       RenderResource.ResourceFormat.RGBA8, 
                                                       new Vector2i(0, 0), persistent);
            graph.resources.put(name, resource);
            return this;
        }
        
        /**
         * Import an external resource.
         */
        public RenderGraphBuilder importResource(String name, int resourceId, 
                                                RenderResource.ResourceType type,
                                                RenderResource.ResourceFormat format,
                                                Vector2i size) {
            RenderResource resource = new RenderResource(name, type, format, size, true);
            resource.setResourceId(resourceId);
            resource.setAllocated(true);
            graph.resources.put(name, resource);
            return this;
        }
    }
    
    /**
     * Initialize the render graph.
     */
    public void initialize(int width, int height) {
        if (initialized) {
            logManager.warn("RenderGraph", "Render graph already initialized");
            return;
        }
        
        this.frameWidth = width;
        this.frameHeight = height;
        
        logManager.info("RenderGraph", "Initializing render graph",
                       "width", width, "height", height);
        
        initialized = true;
        
        logManager.info("RenderGraph", "Render graph initialized");
    }
    
    /**
     * Add a render pass to the graph.
     */
    public void addPass(RenderPass pass) {
        if (pass == null) return;
        
        passes.put(pass.getName(), pass);
        graphBuilt = false; // Need to rebuild graph
        
        logManager.debug("RenderGraph", "Render pass added", "name", pass.getName());
    }
    
    /**
     * Remove a render pass from the graph.
     */
    public boolean removePass(String passName) {
        RenderPass removed = passes.remove(passName);
        if (removed != null) {
            removed.cleanup();
            graphBuilt = false; // Need to rebuild graph
            logManager.debug("RenderGraph", "Render pass removed", "name", passName);
            return true;
        }
        return false;
    }
    
    /**
     * Build the render graph (resolve dependencies and create execution order).
     */
    public void build() {
        if (!initialized) {
            logManager.error("RenderGraph", "Cannot build graph - not initialized");
            return;
        }
        
        logManager.info("RenderGraph", "Building render graph", "passCount", passes.size());
        
        // Clear previous build
        dependencies.clear();
        executionOrder.clear();
        resources.clear();
        
        // Setup passes and collect resources
        RenderGraphBuilder builder = new RenderGraphBuilder(this);
        for (RenderPass pass : passes.values()) {
            if (pass.isEnabled()) {
                pass.setup(builder);
            }
        }
        
        // Build dependency graph
        buildDependencyGraph();
        
        // Resolve execution order using topological sort
        resolveExecutionOrder();
        
        // Allocate resources
        allocateResources();
        
        graphBuilt = true;
        
        logManager.info("RenderGraph", "Render graph built successfully",
                       "passCount", executionOrder.size(),
                       "resourceCount", resources.size());
    }
    
    /**
     * Execute the render graph.
     */
    public void execute(Window window, Camera camera, float deltaTime) {
        if (!initialized || !graphBuilt) {
            logManager.warn("RenderGraph", "Cannot execute graph - not built");
            return;
        }
        
        long startTime = System.nanoTime();
        passesExecuted = 0;
        passExecutionTimes.clear();
        
        // Create execution context
        RenderGraphContext context = new RenderGraphContext(resources, window, camera, deltaTime);
        
        // Execute passes in dependency order
        for (String passName : executionOrder) {
            RenderPass pass = passes.get(passName);
            if (pass != null && pass.isEnabled()) {
                long passStartTime = System.nanoTime();
                
                try {
                    pass.execute(context);
                    passesExecuted++;
                    
                    long passEndTime = System.nanoTime();
                    long passTime = passEndTime - passStartTime;
                    passExecutionTimes.put(passName, passTime);
                    
                    metricsCollector.recordHistogram("renderGraph.pass." + passName + ".time", 
                                                    passTime / 1_000_000.0); // Convert to milliseconds
                    
                } catch (Exception e) {
                    logManager.error("RenderGraph", "Error executing render pass", e,
                                   "passName", passName);
                    metricsCollector.incrementCounter("renderGraph.passErrors");
                }
            }
        }
        
        long endTime = System.nanoTime();
        lastExecutionTime = endTime - startTime;
        
        metricsCollector.recordHistogram("renderGraph.totalExecutionTime", 
                                        lastExecutionTime / 1_000_000.0); // Convert to milliseconds
        metricsCollector.setGauge("renderGraph.passesExecuted", passesExecuted);
        
        logManager.debug("RenderGraph", "Graph execution completed",
                        "passes", passesExecuted, 
                        "timeMs", lastExecutionTime / 1_000_000.0);
    }
    
    /**
     * Resize the render graph.
     */
    public void resize(int width, int height) {
        if (!initialized) return;
        
        this.frameWidth = width;
        this.frameHeight = height;
        
        // Deallocate non-persistent resources
        deallocateResources(false);
        
        // Rebuild graph with new dimensions
        build();
        
        logManager.debug("RenderGraph", "Render graph resized", 
                        "width", width, "height", height);
    }
    
    /**
     * Get a render pass by name.
     */
    public RenderPass getPass(String name) {
        return passes.get(name);
    }
    
    /**
     * Get a resource by name.
     */
    public RenderResource getResource(String name) {
        return resources.get(name);
    }
    
    /**
     * Get execution order.
     */
    public List<String> getExecutionOrder() {
        return new ArrayList<>(executionOrder);
    }
    
    private void buildDependencyGraph() {
        // Build dependencies based on resource inputs/outputs
        for (RenderPass pass : passes.values()) {
            if (!pass.isEnabled()) continue;
            
            Set<String> passDependencies = new HashSet<>();
            
            // For each input resource, find the pass that produces it
            for (String input : pass.getInputs()) {
                for (RenderPass otherPass : passes.values()) {
                    if (otherPass != pass && otherPass.isEnabled() && 
                        otherPass.getOutputs().contains(input)) {
                        passDependencies.add(otherPass.getName());
                    }
                }
            }
            
            dependencies.put(pass.getName(), passDependencies);
        }
        
        logManager.debug("RenderGraph", "Dependency graph built", 
                        "dependencies", dependencies.size());
    }
    
    private void resolveExecutionOrder() {
        // Topological sort using Kahn's algorithm
        Map<String, Integer> inDegree = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        
        // Calculate in-degrees
        for (String passName : passes.keySet()) {
            if (passes.get(passName).isEnabled()) {
                inDegree.put(passName, 0);
            }
        }
        
        for (String passName : dependencies.keySet()) {
            for (String dependency : dependencies.get(passName)) {
                inDegree.put(dependency, inDegree.getOrDefault(dependency, 0) + 1);
            }
        }
        
        // Find passes with no dependencies
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        
        // Process passes in topological order
        while (!queue.isEmpty()) {
            String currentPass = queue.poll();
            executionOrder.add(currentPass);
            
            // Reduce in-degree for dependent passes
            Set<String> currentDependencies = dependencies.get(currentPass);
            if (currentDependencies != null) {
                for (String dependent : currentDependencies) {
                    int newInDegree = inDegree.get(dependent) - 1;
                    inDegree.put(dependent, newInDegree);
                    
                    if (newInDegree == 0) {
                        queue.offer(dependent);
                    }
                }
            }
        }
        
        // Check for cycles
        if (executionOrder.size() != inDegree.size()) {
            logManager.error("RenderGraph", "Circular dependency detected in render graph");
            throw new RuntimeException("Circular dependency in render graph");
        }
        
        // Sort by priority within dependency constraints
        executionOrder.sort((a, b) -> {
            RenderPass passA = passes.get(a);
            RenderPass passB = passes.get(b);
            return Integer.compare(passA.getPriority(), passB.getPriority());
        });
        
        logManager.debug("RenderGraph", "Execution order resolved", 
                        "order", executionOrder);
    }
    
    private void allocateResources() {
        for (RenderResource resource : resources.values()) {
            if (!resource.isAllocated()) {
                allocateResource(resource);
            }
        }
        
        logManager.debug("RenderGraph", "Resources allocated", 
                        "count", resources.size());
    }
    
    private void allocateResource(RenderResource resource) {
        int resourceId = 0;
        
        switch (resource.getType()) {
            case TEXTURE_2D:
                resourceId = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, resourceId);
                
                // Determine OpenGL format
                int internalFormat = getGLInternalFormat(resource.getFormat());
                int format = getGLFormat(resource.getFormat());
                int type = getGLType(resource.getFormat());
                
                glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, 
                           resource.getSize().x, resource.getSize().y, 
                           0, format, type, 0);
                
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                break;
                
            case FRAMEBUFFER:
                resourceId = glGenFramebuffers();
                break;
                
            case RENDER_BUFFER:
                resourceId = glGenRenderbuffers();
                glBindRenderbuffer(GL_RENDERBUFFER, resourceId);
                glRenderbufferStorage(GL_RENDERBUFFER, getGLInternalFormat(resource.getFormat()),
                                    resource.getSize().x, resource.getSize().y);
                break;
        }
        
        resource.setResourceId(resourceId);
        resource.setAllocated(true);
        
        logManager.debug("RenderGraph", "Resource allocated", 
                        "name", resource.getName(), "id", resourceId, "type", resource.getType());
    }
    
    private void deallocateResources(boolean persistent) {
        for (RenderResource resource : resources.values()) {
            if (resource.isAllocated() && (!resource.isPersistent() || persistent)) {
                deallocateResource(resource);
            }
        }
    }
    
    private void deallocateResource(RenderResource resource) {
        if (!resource.isAllocated()) return;
        
        switch (resource.getType()) {
            case TEXTURE_2D:
            case TEXTURE_CUBE:
                glDeleteTextures(resource.getResourceId());
                break;
            case FRAMEBUFFER:
                glDeleteFramebuffers(resource.getResourceId());
                break;
            case RENDER_BUFFER:
                glDeleteRenderbuffers(resource.getResourceId());
                break;
        }
        
        resource.setResourceId(0);
        resource.setAllocated(false);
        
        logManager.debug("RenderGraph", "Resource deallocated", 
                        "name", resource.getName(), "type", resource.getType());
    }
    
    private int getGLInternalFormat(RenderResource.ResourceFormat format) {
        switch (format) {
            case RGBA8: return GL_RGBA8;
            case RGBA16F: return GL_RGBA16F;
            case RGBA32F: return GL_RGBA32F;
            case RGB8: return GL_RGB8;
            case RGB16F: return GL_RGB16F;
            case RGB32F: return GL_RGB32F;
            case RG8: return GL_RG8;
            case RG16F: return GL_RG16F;
            case RG32F: return GL_RG32F;
            case R8: return GL_R8;
            case R16F: return GL_R16F;
            case R32F: return GL_R32F;
            case DEPTH24: return GL_DEPTH_COMPONENT24;
            case DEPTH32F: return GL_DEPTH_COMPONENT32F;
            case DEPTH24_STENCIL8: return GL_DEPTH24_STENCIL8;
            default: return GL_RGBA8;
        }
    }
    
    private int getGLFormat(RenderResource.ResourceFormat format) {
        switch (format) {
            case RGBA8:
            case RGBA16F:
            case RGBA32F:
                return GL_RGBA;
            case RGB8:
            case RGB16F:
            case RGB32F:
                return GL_RGB;
            case RG8:
            case RG16F:
            case RG32F:
                return GL_RG;
            case R8:
            case R16F:
            case R32F:
                return GL_RED;
            case DEPTH24:
            case DEPTH32F:
                return GL_DEPTH_COMPONENT;
            case DEPTH24_STENCIL8:
                return GL_DEPTH_STENCIL;
            default:
                return GL_RGBA;
        }
    }
    
    private int getGLType(RenderResource.ResourceFormat format) {
        switch (format) {
            case RGBA8:
            case RGB8:
            case RG8:
            case R8:
                return GL_UNSIGNED_BYTE;
            case RGBA16F:
            case RGB16F:
            case RG16F:
            case R16F:
                return GL_HALF_FLOAT;
            case RGBA32F:
            case RGB32F:
            case RG32F:
            case R32F:
            case DEPTH32F:
                return GL_FLOAT;
            case DEPTH24:
                return GL_UNSIGNED_INT;
            case DEPTH24_STENCIL8:
                return GL_UNSIGNED_INT_24_8;
            default:
                return GL_UNSIGNED_BYTE;
        }
    }
    
    /**
     * Get render graph statistics.
     */
    public RenderGraphStatistics getStatistics() {
        return new RenderGraphStatistics(
            passes.size(),
            resources.size(),
            passesExecuted,
            lastExecutionTime / 1_000_000.0, // Convert to milliseconds
            new HashMap<>(passExecutionTimes)
        );
    }
    
    /**
     * Cleanup the render graph.
     */
    public void cleanup() {
        if (!initialized) return;
        
        logManager.info("RenderGraph", "Cleaning up render graph");
        
        // Cleanup all passes
        for (RenderPass pass : passes.values()) {
            pass.cleanup();
        }
        passes.clear();
        
        // Deallocate all resources
        deallocateResources(true);
        resources.clear();
        
        // Clear graph structure
        dependencies.clear();
        executionOrder.clear();
        passExecutionTimes.clear();
        
        initialized = false;
        graphBuilt = false;
        
        logManager.info("RenderGraph", "Render graph cleanup complete");
    }
    
    /**
     * Check if the render graph is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Check if the render graph is built.
     */
    public boolean isBuilt() {
        return graphBuilt;
    }
    
    /**
     * Render graph statistics.
     */
    public static class RenderGraphStatistics {
        public final int totalPasses;
        public final int totalResources;
        public final int passesExecuted;
        public final double lastExecutionTimeMs;
        public final Map<String, Long> passExecutionTimes;
        
        public RenderGraphStatistics(int totalPasses, int totalResources, int passesExecuted,
                                   double lastExecutionTimeMs, Map<String, Long> passExecutionTimes) {
            this.totalPasses = totalPasses;
            this.totalResources = totalResources;
            this.passesExecuted = passesExecuted;
            this.lastExecutionTimeMs = lastExecutionTimeMs;
            this.passExecutionTimes = passExecutionTimes;
        }
        
        @Override
        public String toString() {
            return String.format("RenderGraphStats{passes=%d/%d, resources=%d, timeMs=%.2f}",
                               passesExecuted, totalPasses, totalResources, lastExecutionTimeMs);
        }
    }
}