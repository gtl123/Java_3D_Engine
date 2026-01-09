package engine.rendering;

import engine.rendering.advanced.*;
import engine.rendering.advanced.passes.*;
import engine.gfx.RenderSystem;
import engine.gfx.RenderPass;
import engine.raster.Renderer;
import engine.io.Window;
import engine.camera.Camera;
import engine.config.ConfigurationManager;
import engine.logging.LogManager;
import engine.logging.MetricsCollector;
import engine.assets.AssetManager;
import engine.plugins.PluginManager;
import engine.profiler.ProfilerManager;
import engine.profiler.RenderProfiler;
import engine.profiler.PerformanceProfiler;
import engine.profiler.DebugRenderer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced Render System that integrates the new advanced rendering pipeline
 * with the existing rendering infrastructure. Provides seamless transition
 * between legacy and advanced rendering modes.
 */
public class AdvancedRenderSystem {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = LogManager.getInstance().getMetricsCollector();
    private static final ConfigurationManager configManager = ConfigurationManager.getInstance();
    private static final AssetManager assetManager = AssetManager.getInstance();
    
    // Profiler components
    private ProfilerManager profilerManager;
    private RenderProfiler renderProfiler;
    private PerformanceProfiler performanceProfiler;
    private DebugRenderer debugRenderer;
    
    // Core systems
    private final RenderSystem legacyRenderSystem;
    private final Renderer legacyRenderer;
    
    // Advanced rendering components
    private RenderGraph renderGraph;
    private PBRMaterialSystem pbrMaterialSystem;
    private DeferredRenderer deferredRenderer;
    private LightingSystem lightingSystem;
    private PostProcessingPipeline postProcessingPipeline;
    private TextureAtlasManager textureAtlasManager;
    private ShaderCompiler shaderCompiler;
    
    // Render passes
    private GeometryPass geometryPass;
    private LightingPass lightingPass;
    private PostProcessingPass postProcessingPass;
    private PresentationPass presentationPass;
    
    // Configuration
    private RenderingMode renderingMode;
    private boolean initialized = false;
    private final Map<String, Object> renderingSettings = new ConcurrentHashMap<>();
    
    /**
     * Rendering modes supported by the system.
     */
    public enum RenderingMode {
        LEGACY,           // Use existing forward renderer
        FORWARD_PBR,      // Forward PBR rendering
        DEFERRED_PBR,     // Deferred PBR rendering (recommended)
        HYBRID            // Mix of legacy and advanced features
    }
    
    /**
     * Initialize the advanced render system.
     */
    public AdvancedRenderSystem(RenderSystem legacyRenderSystem, Renderer legacyRenderer) {
        this.legacyRenderSystem = legacyRenderSystem;
        this.legacyRenderer = legacyRenderer;
        
        // Load configuration
        String modeStr = configManager.getString("rendering.mode", "DEFERRED_PBR");
        try {
            this.renderingMode = RenderingMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logManager.warn("AdvancedRenderSystem", "Invalid rendering mode, using default",
                           "mode", modeStr);
            this.renderingMode = RenderingMode.DEFERRED_PBR;
        }
        
        logManager.info("AdvancedRenderSystem", "Advanced render system created",
                       "mode", renderingMode);
    }
    
    /**
     * Set the profiler manager for profiling integration.
     */
    public void setProfilerManager(ProfilerManager profilerManager) {
        this.profilerManager = profilerManager;
        if (profilerManager != null && profilerManager.isInitialized()) {
            this.renderProfiler = profilerManager.getRenderProfiler();
            this.performanceProfiler = profilerManager.getPerformanceProfiler();
            this.debugRenderer = profilerManager.getDebugRenderer();
            
            logManager.debug("AdvancedRenderSystem", "Profiler integration enabled");
        }
    }
    
    /**
     * Initialize all advanced rendering components.
     */
    public void initialize(Window window) {
        if (initialized) {
            logManager.warn("AdvancedRenderSystem", "Already initialized");
            return;
        }
        
        logManager.info("AdvancedRenderSystem", "Initializing advanced render system",
                       "mode", renderingMode, "width", window.getWidth(), "height", window.getHeight());
        
        // Start profiling initialization if available
        if (performanceProfiler != null) {
            performanceProfiler.startMethodProfiling("AdvancedRenderSystem.initialize");
        }
        
        try {
            // Initialize core systems
            initializeCoreComponents();
            
            // Initialize rendering pipeline based on mode
            switch (renderingMode) {
                case LEGACY:
                    initializeLegacyMode();
                    break;
                case FORWARD_PBR:
                    initializeForwardPBRMode(window);
                    break;
                case DEFERRED_PBR:
                    initializeDeferredPBRMode(window);
                    break;
                case HYBRID:
                    initializeHybridMode(window);
                    break;
            }
            
            initialized = true;
            
            logManager.info("AdvancedRenderSystem", "Advanced render system initialized successfully");
            metricsCollector.incrementCounter("advancedRenderSystem.initializations");
            
        } catch (Exception e) {
            logManager.error("AdvancedRenderSystem", "Failed to initialize advanced render system", e);
            throw new RuntimeException("Advanced render system initialization failed", e);
        } finally {
            // End profiling initialization
            if (performanceProfiler != null) {
                performanceProfiler.endMethodProfiling("AdvancedRenderSystem.initialize");
            }
        }
    }
    
    /**
     * Render a frame using the configured rendering mode.
     */
    public void render(Window window, Camera camera, float deltaTime, RenderContext context) {
        if (!initialized) {
            logManager.warn("AdvancedRenderSystem", "Render called before initialization");
            return;
        }
        
        long startTime = System.nanoTime();
        
        // Start render profiling
        if (renderProfiler != null) {
            renderProfiler.beginFrame();
        }
        if (performanceProfiler != null) {
            performanceProfiler.startMethodProfiling("AdvancedRenderSystem.render");
        }
        
        try {
            switch (renderingMode) {
                case LEGACY:
                    renderLegacy(window, deltaTime);
                    break;
                case FORWARD_PBR:
                case DEFERRED_PBR:
                case HYBRID:
                    renderAdvanced(window, camera, deltaTime, context);
                    break;
            }
            
            long endTime = System.nanoTime();
            double frameTime = (endTime - startTime) / 1_000_000.0; // Convert to milliseconds
            
            metricsCollector.recordHistogram("advancedRenderSystem.frameTime", frameTime);
            metricsCollector.incrementCounter("advancedRenderSystem.framesRendered");
            
            // Record profiling data
            if (renderProfiler != null) {
                renderProfiler.recordFrameTime(frameTime);
            }
            
        } catch (Exception e) {
            logManager.error("AdvancedRenderSystem", "Error during rendering", e);
            metricsCollector.incrementCounter("advancedRenderSystem.renderErrors");
        } finally {
            // End render profiling
            if (performanceProfiler != null) {
                performanceProfiler.endMethodProfiling("AdvancedRenderSystem.render");
            }
            if (renderProfiler != null) {
                renderProfiler.endFrame();
            }
        }
    }
    
    /**
     * Handle window resize.
     */
    public void resize(int width, int height) {
        if (!initialized) return;
        
        logManager.debug("AdvancedRenderSystem", "Resizing render system", 
                        "width", width, "height", height);
        
        if (renderGraph != null) {
            renderGraph.resize(width, height);
        }
        
        if (deferredRenderer != null) {
            deferredRenderer.resize(width, height);
        }
        
        if (postProcessingPipeline != null) {
            postProcessingPipeline.resize(width, height);
        }
    }
    
    /**
     * Switch rendering mode at runtime.
     */
    public void setRenderingMode(RenderingMode mode, Window window) {
        if (mode == this.renderingMode) return;
        
        logManager.info("AdvancedRenderSystem", "Switching rendering mode", 
                       "from", this.renderingMode, "to", mode);
        
        // Cleanup current mode
        cleanup();
        
        // Set new mode and reinitialize
        this.renderingMode = mode;
        this.initialized = false;
        initialize(window);
    }
    
    /**
     * Get the current PBR material system.
     */
    public PBRMaterialSystem getPBRMaterialSystem() {
        return pbrMaterialSystem;
    }
    
    /**
     * Get the current lighting system.
     */
    public LightingSystem getLightingSystem() {
        return lightingSystem;
    }
    
    /**
     * Get the texture atlas manager.
     */
    public TextureAtlasManager getTextureAtlasManager() {
        return textureAtlasManager;
    }
    
    /**
     * Get the shader compiler.
     */
    public ShaderCompiler getShaderCompiler() {
        return shaderCompiler;
    }
    
    /**
     * Get the render graph.
     */
    public RenderGraph getRenderGraph() {
        return renderGraph;
    }
    
    /**
     * Get rendering statistics.
     */
    public AdvancedRenderingStatistics getStatistics() {
        AdvancedRenderingStatistics stats = new AdvancedRenderingStatistics();
        
        stats.renderingMode = renderingMode;
        stats.initialized = initialized;
        
        if (renderGraph != null) {
            stats.renderGraphStats = renderGraph.getStatistics();
        }
        
        if (pbrMaterialSystem != null) {
            stats.materialStats = pbrMaterialSystem.getStatistics();
        }
        
        if (lightingSystem != null) {
            stats.lightingStats = lightingSystem.getStatistics();
        }
        
        if (postProcessingPipeline != null) {
            stats.postProcessingStats = postProcessingPipeline.getStatistics();
        }
        
        if (textureAtlasManager != null) {
            stats.atlasStats = textureAtlasManager.getStatistics();
        }
        
        if (shaderCompiler != null) {
            stats.shaderStats = shaderCompiler.getStatistics();
        }
        
        return stats;
    }
    
    private void initializeCoreComponents() {
        // Initialize shader compiler
        shaderCompiler = new ShaderCompiler();
        
        // Initialize texture atlas manager
        textureAtlasManager = new TextureAtlasManager();
        
        // Initialize PBR material system
        pbrMaterialSystem = new PBRMaterialSystem();
        pbrMaterialSystem.initialize();
        
        // Initialize lighting system
        lightingSystem = new LightingSystem();
        lightingSystem.initialize();
        
        logManager.debug("AdvancedRenderSystem", "Core components initialized");
    }
    
    private void initializeLegacyMode() {
        // Legacy mode uses existing render system as-is
        logManager.info("AdvancedRenderSystem", "Legacy rendering mode initialized");
    }
    
    private void initializeForwardPBRMode(Window window) {
        // Forward PBR mode uses advanced materials and lighting with forward rendering
        initializePostProcessing(window);
        
        logManager.info("AdvancedRenderSystem", "Forward PBR rendering mode initialized");
    }
    
    private void initializeDeferredPBRMode(Window window) {
        // Initialize deferred renderer
        deferredRenderer = new DeferredRenderer();
        deferredRenderer.initialize(window.getWidth(), window.getHeight());
        
        // Initialize post-processing
        initializePostProcessing(window);
        
        // Initialize render graph
        initializeRenderGraph(window);
        
        logManager.info("AdvancedRenderSystem", "Deferred PBR rendering mode initialized");
    }
    
    private void initializeHybridMode(Window window) {
        // Hybrid mode combines legacy and advanced features
        initializePostProcessing(window);
        
        logManager.info("AdvancedRenderSystem", "Hybrid rendering mode initialized");
    }
    
    private void initializePostProcessing(Window window) {
        postProcessingPipeline = new PostProcessingPipeline();
        postProcessingPipeline.initialize(window.getWidth(), window.getHeight());
        
        // Configure post-processing effects based on settings
        boolean enableSSAO = configManager.getBoolean("rendering.postprocess.ssao.enabled", true);
        boolean enableBloom = configManager.getBoolean("rendering.postprocess.bloom.enabled", true);
        boolean enableToneMapping = configManager.getBoolean("rendering.postprocess.toneMapping.enabled", true);
        boolean enableFXAA = configManager.getBoolean("rendering.postprocess.fxaa.enabled", true);
        
        postProcessingPipeline.setEffectEnabled(PostProcessingPipeline.EffectType.SSAO, enableSSAO);
        postProcessingPipeline.setEffectEnabled(PostProcessingPipeline.EffectType.BLOOM, enableBloom);
        postProcessingPipeline.setEffectEnabled(PostProcessingPipeline.EffectType.TONE_MAPPING, enableToneMapping);
        postProcessingPipeline.setEffectEnabled(PostProcessingPipeline.EffectType.FXAA, enableFXAA);
    }
    
    private void initializeRenderGraph(Window window) {
        renderGraph = new RenderGraph();
        renderGraph.initialize(window.getWidth(), window.getHeight());
        
        // Create and add render passes
        geometryPass = new GeometryPass(pbrMaterialSystem);
        lightingPass = new LightingPass(lightingSystem);
        postProcessingPass = new PostProcessingPass(postProcessingPipeline);
        presentationPass = new PresentationPass();
        
        renderGraph.addPass(geometryPass);
        renderGraph.addPass(lightingPass);
        renderGraph.addPass(postProcessingPass);
        renderGraph.addPass(presentationPass);
        
        // Build the render graph
        renderGraph.build();
    }
    
    private void renderLegacy(Window window, float deltaTime) {
        // Profile legacy rendering
        if (renderProfiler != null) {
            renderProfiler.beginRenderPass("Legacy");
        }
        
        try {
            // Use legacy render system
            legacyRenderSystem.renderAll(window, deltaTime);
        } finally {
            if (renderProfiler != null) {
                renderProfiler.endRenderPass("Legacy");
            }
        }
    }
    
    private void renderAdvanced(Window window, Camera camera, float deltaTime, RenderContext context) {
        if (renderGraph != null) {
            // Profile render graph execution
            if (renderProfiler != null) {
                renderProfiler.beginRenderPass("RenderGraph");
            }
            
            try {
                // Use render graph for advanced rendering
                renderGraph.execute(window, camera, deltaTime);
            } finally {
                if (renderProfiler != null) {
                    renderProfiler.endRenderPass("RenderGraph");
                }
            }
        } else {
            // Fallback to manual rendering for forward/hybrid modes
            renderManualAdvanced(window, camera, deltaTime, context);
        }
        
        // Render debug visualization if enabled
        if (debugRenderer != null && debugRenderer.isEnabled()) {
            if (renderProfiler != null) {
                renderProfiler.beginRenderPass("DebugVisualization");
            }
            
            try {
                debugRenderer.render(camera);
            } finally {
                if (renderProfiler != null) {
                    renderProfiler.endRenderPass("DebugVisualization");
                }
            }
        }
    }
    
    private void renderManualAdvanced(Window window, Camera camera, float deltaTime, RenderContext context) {
        // Manual advanced rendering for forward/hybrid modes
        // This would integrate with the existing Renderer class
        
        if (renderProfiler != null) {
            renderProfiler.beginRenderPass("ManualAdvanced");
        }
        
        try {
            // Apply post-processing if available
            if (postProcessingPipeline != null && context != null) {
                if (renderProfiler != null) {
                    renderProfiler.beginRenderPass("PostProcessing");
                }
                
                try {
                    PostProcessingPipeline.PostProcessingContext ppContext =
                        new PostProcessingPipeline.PostProcessingContext(window, camera, deltaTime);
                    
                    // Get the scene texture from legacy renderer
                    int sceneTexture = legacyRenderer.getSceneColorTex();
                    int depthTexture = legacyRenderer.getSceneDepthTex();
                    
                    postProcessingPipeline.execute(ppContext, sceneTexture, depthTexture);
                } finally {
                    if (renderProfiler != null) {
                        renderProfiler.endRenderPass("PostProcessing");
                    }
                }
            }
        } finally {
            if (renderProfiler != null) {
                renderProfiler.endRenderPass("ManualAdvanced");
            }
        }
    }
    
    /**
     * Cleanup all resources.
     */
    public void cleanup() {
        logManager.info("AdvancedRenderSystem", "Cleaning up advanced render system");
        
        // Profile cleanup process
        if (performanceProfiler != null) {
            performanceProfiler.startMethodProfiling("AdvancedRenderSystem.cleanup");
        }
        
        try {
            if (renderGraph != null) {
                renderGraph.cleanup();
                renderGraph = null;
            }
            
            if (deferredRenderer != null) {
                deferredRenderer.cleanup();
                deferredRenderer = null;
            }
            
            if (postProcessingPipeline != null) {
                postProcessingPipeline.cleanup();
                postProcessingPipeline = null;
            }
            
            if (lightingSystem != null) {
                lightingSystem.cleanup();
                lightingSystem = null;
            }
            
            if (pbrMaterialSystem != null) {
                pbrMaterialSystem.cleanup();
                pbrMaterialSystem = null;
            }
            
            if (textureAtlasManager != null) {
                textureAtlasManager.cleanup();
                textureAtlasManager = null;
            }
            
            if (shaderCompiler != null) {
                shaderCompiler.cleanup();
                shaderCompiler = null;
            }
            
            // Clear profiler references
            renderProfiler = null;
            performanceProfiler = null;
            debugRenderer = null;
            profilerManager = null;
            
            initialized = false;
            
            logManager.info("AdvancedRenderSystem", "Advanced render system cleanup complete");
            
        } finally {
            if (performanceProfiler != null) {
                performanceProfiler.endMethodProfiling("AdvancedRenderSystem.cleanup");
            }
        }
    }
    
    /**
     * Render context for passing scene data to the advanced renderer.
     */
    public static class RenderContext {
        public Object sceneData;
        public Object lightData;
        public Object materialData;
        
        public RenderContext() {}
        
        public RenderContext(Object sceneData, Object lightData, Object materialData) {
            this.sceneData = sceneData;
            this.lightData = lightData;
            this.materialData = materialData;
        }
    }
    
    /**
     * Statistics for the advanced rendering system.
     */
    public static class AdvancedRenderingStatistics {
        public RenderingMode renderingMode;
        public boolean initialized;
        public RenderGraph.RenderGraphStatistics renderGraphStats;
        public PBRMaterialSystem.MaterialSystemStatistics materialStats;
        public LightingSystem.LightingStatistics lightingStats;
        public PostProcessingPipeline.PostProcessingStatistics postProcessingStats;
        public TextureAtlasManager.TextureAtlasStatistics atlasStats;
        public ShaderCompiler.ShaderCompilerStatistics shaderStats;
        
        @Override
        public String toString() {
            return String.format("AdvancedRenderingStats{mode=%s, initialized=%s}", 
                               renderingMode, initialized);
        }
    }
}