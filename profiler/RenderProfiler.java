package engine.profiler;

import engine.logging.LogManager;
import engine.logging.MetricsCollector;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Render profiler for tracking rendering performance metrics.
 * Monitors draw calls, triangle counts, texture usage, and shader performance.
 */
public class RenderProfiler implements IProfiler {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = logManager.getMetricsCollector();
    
    private final ProfilerConfiguration config;
    private final AtomicBoolean active = new AtomicBoolean(false);
    
    // Draw call tracking
    private final AtomicLong totalDrawCalls = new AtomicLong(0);
    private final AtomicLong totalTriangles = new AtomicLong(0);
    private final AtomicLong totalVertices = new AtomicLong(0);
    private final AtomicLong totalIndices = new AtomicLong(0);
    
    // Per-frame tracking
    private final AtomicLong frameDrawCalls = new AtomicLong(0);
    private final AtomicLong frameTriangles = new AtomicLong(0);
    private final AtomicLong frameVertices = new AtomicLong(0);
    
    // Texture tracking
    private final AtomicLong totalTextureBinds = new AtomicLong(0);
    private final AtomicLong totalTextureMemory = new AtomicLong(0);
    private final Map<Integer, TextureProfile> textureProfiles = new ConcurrentHashMap<>();
    
    // Shader tracking
    private final Map<Integer, ShaderProfile> shaderProfiles = new ConcurrentHashMap<>();
    private final AtomicLong totalShaderSwitches = new AtomicLong(0);
    
    // Render state tracking
    private final Map<String, RenderStateProfile> renderStateProfiles = new ConcurrentHashMap<>();
    private final AtomicLong totalStateChanges = new AtomicLong(0);
    
    // GPU memory tracking
    private final AtomicLong totalVBOMemory = new AtomicLong(0);
    private final AtomicLong totalIBOMemory = new AtomicLong(0);
    private final AtomicLong totalFBOMemory = new AtomicLong(0);
    
    // Performance samples
    private final Queue<RenderSample> renderSamples = new ConcurrentLinkedQueue<>();
    
    // Current frame tracking
    private long frameStartTime = 0;
    private int currentShaderProgram = -1;
    private final Map<String, Object> currentRenderState = new ConcurrentHashMap<>();
    
    public RenderProfiler(ProfilerConfiguration config) {
        this.config = config;
    }
    
    @Override
    public void initialize() {
        logManager.info("RenderProfiler", "Initializing render profiler",
                       "drawCallTracking", config.isDrawCallTracking(),
                       "triangleCountTracking", config.isTriangleCountTracking(),
                       "textureUsageTracking", config.isTextureUsageTracking(),
                       "shaderPerformanceTracking", config.isShaderPerformanceTracking());
        
        logManager.info("RenderProfiler", "Render profiler initialized");
    }
    
    @Override
    public void start() {
        if (active.get()) {
            return;
        }
        
        logManager.info("RenderProfiler", "Starting render profiling");
        
        // Reset all tracking data
        totalDrawCalls.set(0);
        totalTriangles.set(0);
        totalVertices.set(0);
        totalIndices.set(0);
        
        frameDrawCalls.set(0);
        frameTriangles.set(0);
        frameVertices.set(0);
        
        totalTextureBinds.set(0);
        totalTextureMemory.set(0);
        totalShaderSwitches.set(0);
        totalStateChanges.set(0);
        
        totalVBOMemory.set(0);
        totalIBOMemory.set(0);
        totalFBOMemory.set(0);
        
        textureProfiles.clear();
        shaderProfiles.clear();
        renderStateProfiles.clear();
        renderSamples.clear();
        currentRenderState.clear();
        
        currentShaderProgram = -1;
        
        active.set(true);
        
        logManager.info("RenderProfiler", "Render profiling started");
    }
    
    @Override
    public void stop() {
        if (!active.get()) {
            return;
        }
        
        logManager.info("RenderProfiler", "Stopping render profiling");
        
        active.set(false);
        
        logManager.info("RenderProfiler", "Render profiling stopped");
    }
    
    @Override
    public void update(float deltaTime) {
        if (!active.get()) {
            return;
        }
        
        // Clean up old samples
        cleanupOldSamples();
        
        // Update GPU memory usage
        updateGPUMemoryUsage();
    }
    
    @Override
    public ProfilerData collectData() {
        ProfilerData data = new ProfilerData("render");
        
        // Draw call metrics
        if (config.isDrawCallTracking()) {
            collectDrawCallMetrics(data);
        }
        
        // Triangle count metrics
        if (config.isTriangleCountTracking()) {
            collectTriangleMetrics(data);
        }
        
        // Texture usage metrics
        if (config.isTextureUsageTracking()) {
            collectTextureMetrics(data);
        }
        
        // Shader performance metrics
        if (config.isShaderPerformanceTracking()) {
            collectShaderMetrics(data);
        }
        
        // Render state metrics
        collectRenderStateMetrics(data);
        
        // GPU memory metrics
        collectGPUMemoryMetrics(data);
        
        // Performance samples
        collectPerformanceSamples(data);
        
        // Add metadata
        data.addMetadata("drawCallTracking", config.isDrawCallTracking());
        data.addMetadata("triangleCountTracking", config.isTriangleCountTracking());
        data.addMetadata("textureUsageTracking", config.isTextureUsageTracking());
        data.addMetadata("shaderPerformanceTracking", config.isShaderPerformanceTracking());
        
        return data;
    }
    
    @Override
    public void reset() {
        logManager.info("RenderProfiler", "Resetting render profiler");
        
        totalDrawCalls.set(0);
        totalTriangles.set(0);
        totalVertices.set(0);
        totalIndices.set(0);
        
        frameDrawCalls.set(0);
        frameTriangles.set(0);
        frameVertices.set(0);
        
        totalTextureBinds.set(0);
        totalTextureMemory.set(0);
        totalShaderSwitches.set(0);
        totalStateChanges.set(0);
        
        totalVBOMemory.set(0);
        totalIBOMemory.set(0);
        totalFBOMemory.set(0);
        
        textureProfiles.clear();
        shaderProfiles.clear();
        renderStateProfiles.clear();
        renderSamples.clear();
        currentRenderState.clear();
    }
    
    @Override
    public void cleanup() {
        logManager.info("RenderProfiler", "Cleaning up render profiler");
        
        stop();
        
        textureProfiles.clear();
        shaderProfiles.clear();
        renderStateProfiles.clear();
        renderSamples.clear();
        currentRenderState.clear();
        
        logManager.info("RenderProfiler", "Render profiler cleanup complete");
    }
    
    @Override
    public boolean isActive() {
        return active.get();
    }
    
    @Override
    public String getProfilerType() {
        return "render";
    }
    
    /**
     * Start frame profiling.
     */
    public void startFrame() {
        if (!active.get()) {
            return;
        }
        
        frameStartTime = System.nanoTime();
        
        // Reset per-frame counters
        frameDrawCalls.set(0);
        frameTriangles.set(0);
        frameVertices.set(0);
    }
    
    /**
     * End frame profiling.
     */
    public void endFrame() {
        if (!active.get()) {
            return;
        }
        
        long frameEndTime = System.nanoTime();
        double frameTime = (frameEndTime - frameStartTime) / 1_000_000.0; // Convert to milliseconds
        
        // Create render sample
        RenderSample sample = new RenderSample();
        sample.timestamp = System.currentTimeMillis();
        sample.frameTime = frameTime;
        sample.drawCalls = frameDrawCalls.get();
        sample.triangles = frameTriangles.get();
        sample.vertices = frameVertices.get();
        sample.textureBinds = getCurrentFrameTextureBinds();
        sample.shaderSwitches = getCurrentFrameShaderSwitches();
        sample.stateChanges = getCurrentFrameStateChanges();
        
        renderSamples.offer(sample);
        
        // Record metrics
        metricsCollector.recordTime("render.frameTime", (long)frameTime);
        metricsCollector.recordTime("render.drawCalls", frameDrawCalls.get());
        metricsCollector.recordTime("render.triangles", frameTriangles.get());
    }
    
    /**
     * Record a draw call.
     */
    public void recordDrawCall(int primitiveType, int vertexCount, int indexCount) {
        if (!active.get() || !config.isDrawCallTracking()) {
            return;
        }
        
        totalDrawCalls.incrementAndGet();
        frameDrawCalls.incrementAndGet();
        
        if (config.isTriangleCountTracking()) {
            int triangleCount = calculateTriangleCount(primitiveType, vertexCount, indexCount);
            totalTriangles.addAndGet(triangleCount);
            frameTriangles.addAndGet(triangleCount);
        }
        
        totalVertices.addAndGet(vertexCount);
        frameVertices.addAndGet(vertexCount);
        
        if (indexCount > 0) {
            totalIndices.addAndGet(indexCount);
        }
        
        // Record metrics
        metricsCollector.incrementCounter("render.drawCalls");
        metricsCollector.incrementCounter("render.vertices", vertexCount);
    }
    
    /**
     * Record texture binding.
     */
    public void recordTextureBind(int textureId, int textureUnit, int width, int height, int format) {
        if (!active.get() || !config.isTextureUsageTracking()) {
            return;
        }
        
        totalTextureBinds.incrementAndGet();
        
        // Update texture profile
        TextureProfile profile = textureProfiles.computeIfAbsent(textureId, 
                                                                k -> new TextureProfile(k, width, height, format));
        profile.recordBind();
        
        // Calculate texture memory usage
        long textureSize = calculateTextureSize(width, height, format);
        totalTextureMemory.addAndGet(textureSize);
        
        // Record metrics
        metricsCollector.incrementCounter("render.textureBinds");
        metricsCollector.incrementCounter("render.textureMemory", textureSize);
    }
    
    /**
     * Record shader program usage.
     */
    public void recordShaderUse(int shaderProgram) {
        if (!active.get() || !config.isShaderPerformanceTracking()) {
            return;
        }
        
        if (currentShaderProgram != shaderProgram) {
            totalShaderSwitches.incrementAndGet();
            currentShaderProgram = shaderProgram;
            
            // Update shader profile
            ShaderProfile profile = shaderProfiles.computeIfAbsent(shaderProgram, 
                                                                  k -> new ShaderProfile(k));
            profile.recordUse();
            
            // Record metrics
            metricsCollector.incrementCounter("render.shaderSwitches");
        }
    }
    
    /**
     * Record render state change.
     */
    public void recordStateChange(String stateName, Object oldValue, Object newValue) {
        if (!active.get()) {
            return;
        }
        
        totalStateChanges.incrementAndGet();
        
        // Update render state profile
        RenderStateProfile profile = renderStateProfiles.computeIfAbsent(stateName, 
                                                                        k -> new RenderStateProfile(k));
        profile.recordChange(oldValue, newValue);
        
        // Update current state
        currentRenderState.put(stateName, newValue);
        
        // Record metrics
        metricsCollector.incrementCounter("render.stateChanges");
    }
    
    /**
     * Record buffer creation.
     */
    public void recordBufferCreation(int bufferId, int target, long size) {
        if (!active.get()) {
            return;
        }
        
        switch (target) {
            case GL15.GL_ARRAY_BUFFER:
                totalVBOMemory.addAndGet(size);
                metricsCollector.incrementCounter("render.vboMemory", size);
                break;
            case GL15.GL_ELEMENT_ARRAY_BUFFER:
                totalIBOMemory.addAndGet(size);
                metricsCollector.incrementCounter("render.iboMemory", size);
                break;
        }
    }
    
    /**
     * Record framebuffer creation.
     */
    public void recordFramebufferCreation(int framebufferId, int width, int height, int colorAttachments) {
        if (!active.get()) {
            return;
        }
        
        // Estimate framebuffer memory usage
        long fbSize = (long)width * height * 4 * (colorAttachments + 1); // RGBA + depth
        totalFBOMemory.addAndGet(fbSize);
        
        metricsCollector.incrementCounter("render.fboMemory", fbSize);
    }
    
    private int calculateTriangleCount(int primitiveType, int vertexCount, int indexCount) {
        int count = indexCount > 0 ? indexCount : vertexCount;
        
        switch (primitiveType) {
            case GL11.GL_TRIANGLES:
                return count / 3;
            case GL11.GL_TRIANGLE_STRIP:
            case GL11.GL_TRIANGLE_FAN:
                return Math.max(0, count - 2);
            case GL11.GL_QUADS:
                return (count / 4) * 2;
            case GL11.GL_QUAD_STRIP:
                return Math.max(0, (count - 2) * 2);
            default:
                return 0; // Lines, points, etc.
        }
    }
    
    private long calculateTextureSize(int width, int height, int format) {
        int bytesPerPixel;
        
        switch (format) {
            case GL11.GL_RGB:
                bytesPerPixel = 3;
                break;
            case GL11.GL_RGBA:
                bytesPerPixel = 4;
                break;
            case GL11.GL_LUMINANCE:
            case GL11.GL_ALPHA:
                bytesPerPixel = 1;
                break;
            case GL11.GL_LUMINANCE_ALPHA:
                bytesPerPixel = 2;
                break;
            default:
                bytesPerPixel = 4; // Assume RGBA
                break;
        }
        
        return (long)width * height * bytesPerPixel;
    }
    
    private void updateGPUMemoryUsage() {
        // Query OpenGL for current memory usage if available
        try {
            // This is vendor-specific and may not be available on all systems
            // We'll use our tracked values as estimates
        } catch (Exception e) {
            // Ignore - not all drivers support memory queries
        }
    }
    
    private void cleanupOldSamples() {
        long cutoffTime = System.currentTimeMillis() - 60000; // Keep 1 minute of samples
        
        while (!renderSamples.isEmpty()) {
            RenderSample sample = renderSamples.peek();
            if (sample != null && sample.timestamp < cutoffTime) {
                renderSamples.poll();
            } else {
                break;
            }
        }
    }
    
    private long getCurrentFrameTextureBinds() {
        // This would need to be tracked per-frame
        return 0; // Simplified for now
    }
    
    private long getCurrentFrameShaderSwitches() {
        // This would need to be tracked per-frame
        return 0; // Simplified for now
    }
    
    private long getCurrentFrameStateChanges() {
        // This would need to be tracked per-frame
        return 0; // Simplified for now
    }
    
    private void collectDrawCallMetrics(ProfilerData data) {
        data.addMetric("totalDrawCalls", totalDrawCalls.get());
        data.addMetric("frameDrawCalls", frameDrawCalls.get());
        
        // Calculate average draw calls per frame
        if (!renderSamples.isEmpty()) {
            long totalFrameDrawCalls = 0;
            int sampleCount = 0;
            
            for (RenderSample sample : renderSamples) {
                totalFrameDrawCalls += sample.drawCalls;
                sampleCount++;
            }
            
            if (sampleCount > 0) {
                data.addMetric("averageDrawCallsPerFrame", (double)totalFrameDrawCalls / sampleCount);
            }
        }
    }
    
    private void collectTriangleMetrics(ProfilerData data) {
        data.addMetric("totalTriangles", totalTriangles.get());
        data.addMetric("totalVertices", totalVertices.get());
        data.addMetric("totalIndices", totalIndices.get());
        data.addMetric("frameTriangles", frameTriangles.get());
        data.addMetric("frameVertices", frameVertices.get());
        
        // Calculate triangles per draw call
        long drawCalls = totalDrawCalls.get();
        if (drawCalls > 0) {
            data.addMetric("averageTrianglesPerDrawCall", (double)totalTriangles.get() / drawCalls);
        }
    }
    
    private void collectTextureMetrics(ProfilerData data) {
        data.addMetric("totalTextureBinds", totalTextureBinds.get());
        data.addMetric("totalTextureMemoryMB", totalTextureMemory.get() / (1024 * 1024));
        data.addMetric("uniqueTextures", textureProfiles.size());
        
        if (!textureProfiles.isEmpty()) {
            // Most used texture
            TextureProfile mostUsed = textureProfiles.values().stream()
                .max((a, b) -> Long.compare(a.bindCount, b.bindCount))
                .orElse(null);
            
            if (mostUsed != null) {
                data.addMetric("mostUsedTextureId", mostUsed.textureId);
                data.addMetric("mostUsedTextureBinds", mostUsed.bindCount);
            }
            
            // Largest texture
            TextureProfile largest = textureProfiles.values().stream()
                .max((a, b) -> Long.compare(a.memorySize, b.memorySize))
                .orElse(null);
            
            if (largest != null) {
                data.addMetric("largestTextureId", largest.textureId);
                data.addMetric("largestTextureSizeMB", largest.memorySize / (1024 * 1024));
            }
        }
    }
    
    private void collectShaderMetrics(ProfilerData data) {
        data.addMetric("totalShaderSwitches", totalShaderSwitches.get());
        data.addMetric("uniqueShaders", shaderProfiles.size());
        
        if (!shaderProfiles.isEmpty()) {
            // Most used shader
            ShaderProfile mostUsed = shaderProfiles.values().stream()
                .max((a, b) -> Long.compare(a.useCount, b.useCount))
                .orElse(null);
            
            if (mostUsed != null) {
                data.addMetric("mostUsedShaderId", mostUsed.shaderProgram);
                data.addMetric("mostUsedShaderCount", mostUsed.useCount);
            }
        }
        
        // Calculate shader switches per frame
        if (!renderSamples.isEmpty()) {
            long totalShaderSwitchesInSamples = 0;
            int sampleCount = 0;
            
            for (RenderSample sample : renderSamples) {
                totalShaderSwitchesInSamples += sample.shaderSwitches;
                sampleCount++;
            }
            
            if (sampleCount > 0) {
                data.addMetric("averageShaderSwitchesPerFrame", (double)totalShaderSwitchesInSamples / sampleCount);
            }
        }
    }
    
    private void collectRenderStateMetrics(ProfilerData data) {
        data.addMetric("totalStateChanges", totalStateChanges.get());
        data.addMetric("uniqueStates", renderStateProfiles.size());
        
        if (!renderStateProfiles.isEmpty()) {
            // Most changed state
            RenderStateProfile mostChanged = renderStateProfiles.values().stream()
                .max((a, b) -> Long.compare(a.changeCount, b.changeCount))
                .orElse(null);
            
            if (mostChanged != null) {
                data.addMetric("mostChangedState", mostChanged.stateName);
                data.addMetric("mostChangedStateCount", mostChanged.changeCount);
            }
        }
    }
    
    private void collectGPUMemoryMetrics(ProfilerData data) {
        data.addMetric("totalVBOMemoryMB", totalVBOMemory.get() / (1024 * 1024));
        data.addMetric("totalIBOMemoryMB", totalIBOMemory.get() / (1024 * 1024));
        data.addMetric("totalFBOMemoryMB", totalFBOMemory.get() / (1024 * 1024));
        
        long totalGPUMemory = totalVBOMemory.get() + totalIBOMemory.get() + 
                             totalFBOMemory.get() + totalTextureMemory.get();
        data.addMetric("totalGPUMemoryMB", totalGPUMemory / (1024 * 1024));
    }
    
    private void collectPerformanceSamples(ProfilerData data) {
        if (renderSamples.isEmpty()) {
            return;
        }
        
        double totalFrameTime = 0.0;
        double minFrameTime = Double.MAX_VALUE;
        double maxFrameTime = 0.0;
        int sampleCount = 0;
        
        for (RenderSample sample : renderSamples) {
            totalFrameTime += sample.frameTime;
            minFrameTime = Math.min(minFrameTime, sample.frameTime);
            maxFrameTime = Math.max(maxFrameTime, sample.frameTime);
            sampleCount++;
        }
        
        if (sampleCount > 0) {
            data.addMetric("averageRenderTime", totalFrameTime / sampleCount);
            data.addMetric("minRenderTime", minFrameTime);
            data.addMetric("maxRenderTime", maxFrameTime);
            data.addMetric("renderSampleCount", sampleCount);
        }
    }
    
    // Helper classes
    private static class TextureProfile {
        final int textureId;
        final int width;
        final int height;
        final int format;
        final long memorySize;
        long bindCount = 0;
        long lastBindTime = 0;
        
        TextureProfile(int textureId, int width, int height, int format) {
            this.textureId = textureId;
            this.width = width;
            this.height = height;
            this.format = format;
            this.memorySize = calculateSize(width, height, format);
        }
        
        void recordBind() {
            bindCount++;
            lastBindTime = System.currentTimeMillis();
        }
        
        private long calculateSize(int width, int height, int format) {
            int bytesPerPixel;
            switch (format) {
                case GL11.GL_RGB: bytesPerPixel = 3; break;
                case GL11.GL_RGBA: bytesPerPixel = 4; break;
                case GL11.GL_LUMINANCE:
                case GL11.GL_ALPHA: bytesPerPixel = 1; break;
                case GL11.GL_LUMINANCE_ALPHA: bytesPerPixel = 2; break;
                default: bytesPerPixel = 4; break;
            }
            return (long)width * height * bytesPerPixel;
        }
    }
    
    private static class ShaderProfile {
        final int shaderProgram;
        long useCount = 0;
        long totalUseTime = 0;
        long lastUseTime = 0;
        
        ShaderProfile(int shaderProgram) {
            this.shaderProgram = shaderProgram;
        }
        
        void recordUse() {
            useCount++;
            lastUseTime = System.currentTimeMillis();
        }
    }
    
    private static class RenderStateProfile {
        final String stateName;
        long changeCount = 0;
        Object currentValue;
        long lastChangeTime = 0;
        
        RenderStateProfile(String stateName) {
            this.stateName = stateName;
        }
        
        void recordChange(Object oldValue, Object newValue) {
            changeCount++;
            currentValue = newValue;
            lastChangeTime = System.currentTimeMillis();
        }
    }
    
    private static class RenderSample {
        long timestamp;
        double frameTime; // milliseconds
        long drawCalls;
        long triangles;
        long vertices;
        long textureBinds;
        long shaderSwitches;
        long stateChanges;
    }
}