package engine.rendering;

import engine.plugins.Plugin;
import engine.plugins.PluginContext;
import engine.plugins.PluginMetadata;
import engine.plugins.types.RenderPlugin;
import engine.rendering.advanced.*;
import engine.config.ConfigurationManager;
import engine.logging.LogManager;
import engine.assets.AssetManager;

/**
 * Plugin implementation for the Advanced Rendering Pipeline.
 * Integrates seamlessly with the existing plugin architecture.
 */
public class RenderingPlugin implements RenderPlugin {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final ConfigurationManager configManager = ConfigurationManager.getInstance();
    private static final AssetManager assetManager = AssetManager.getInstance();
    
    private AdvancedRenderSystem advancedRenderSystem;
    private PluginContext context;
    private boolean initialized = false;
    
    @Override
    public PluginMetadata getMetadata() {
        return PluginMetadata.builder()
                .name("Advanced Rendering Pipeline")
                .version("1.0.0")
                .description("Enterprise-grade PBR rendering with deferred shading, advanced lighting, and post-processing")
                .author("Engine Team")
                .dependencies(new String[]{})
                .build();
    }
    
    @Override
    public void initialize(PluginContext context) {
        this.context = context;
        
        logManager.info("RenderingPlugin", "Initializing Advanced Rendering Pipeline plugin");
        
        try {
            // Register configuration defaults
            registerConfigurationDefaults();
            
            // Register asset types
            registerAssetTypes();
            
            // Initialize advanced render system (will be done when needed)
            logManager.info("RenderingPlugin", "Advanced Rendering Pipeline plugin initialized successfully");
            initialized = true;
            
        } catch (Exception e) {
            logManager.error("RenderingPlugin", "Failed to initialize Advanced Rendering Pipeline plugin", e);
            throw new RuntimeException("Plugin initialization failed", e);
        }
    }
    
    @Override
    public void shutdown() {
        logManager.info("RenderingPlugin", "Shutting down Advanced Rendering Pipeline plugin");
        
        if (advancedRenderSystem != null) {
            advancedRenderSystem.cleanup();
            advancedRenderSystem = null;
        }
        
        initialized = false;
        logManager.info("RenderingPlugin", "Advanced Rendering Pipeline plugin shutdown complete");
    }
    
    @Override
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Get the advanced render system instance.
     */
    public AdvancedRenderSystem getAdvancedRenderSystem() {
        return advancedRenderSystem;
    }
    
    /**
     * Set the advanced render system instance.
     */
    public void setAdvancedRenderSystem(AdvancedRenderSystem advancedRenderSystem) {
        this.advancedRenderSystem = advancedRenderSystem;
    }
    
    private void registerConfigurationDefaults() {
        // Rendering mode configuration
        configManager.setDefault("rendering.mode", "DEFERRED_PBR");
        configManager.setDefault("rendering.vsync", true);
        configManager.setDefault("rendering.maxFPS", 144);
        
        // PBR Material System
        configManager.setDefault("rendering.pbr.enabled", true);
        configManager.setDefault("rendering.pbr.maxMaterials", 1000);
        configManager.setDefault("rendering.pbr.cacheSize", 256);
        
        // Lighting System
        configManager.setDefault("rendering.lighting.maxDirectionalLights", 4);
        configManager.setDefault("rendering.lighting.maxPointLights", 32);
        configManager.setDefault("rendering.lighting.maxSpotLights", 16);
        configManager.setDefault("rendering.lighting.shadowMapSize", 2048);
        configManager.setDefault("rendering.lighting.shadowCascades", 4);
        
        // Post-Processing
        configManager.setDefault("rendering.postprocess.enabled", true);
        configManager.setDefault("rendering.postprocess.ssao.enabled", true);
        configManager.setDefault("rendering.postprocess.ssao.radius", 0.5f);
        configManager.setDefault("rendering.postprocess.ssao.bias", 0.025f);
        configManager.setDefault("rendering.postprocess.ssao.samples", 64);
        
        configManager.setDefault("rendering.postprocess.bloom.enabled", true);
        configManager.setDefault("rendering.postprocess.bloom.threshold", 1.0f);
        configManager.setDefault("rendering.postprocess.bloom.intensity", 1.0f);
        configManager.setDefault("rendering.postprocess.bloom.radius", 1.0f);
        
        configManager.setDefault("rendering.postprocess.toneMapping.enabled", true);
        configManager.setDefault("rendering.postprocess.toneMapping.mode", "ACES");
        configManager.setDefault("rendering.postprocess.toneMapping.exposure", 1.0f);
        configManager.setDefault("rendering.postprocess.toneMapping.gamma", 2.2f);
        
        configManager.setDefault("rendering.postprocess.fxaa.enabled", true);
        configManager.setDefault("rendering.postprocess.fxaa.lumaThreshold", 0.0312f);
        configManager.setDefault("rendering.postprocess.fxaa.mulReduce", 0.125f);
        
        // Texture Atlas Manager
        configManager.setDefault("rendering.atlas.enabled", true);
        configManager.setDefault("rendering.atlas.maxSize", 4096);
        configManager.setDefault("rendering.atlas.defaultSize", 2048);
        configManager.setDefault("rendering.atlas.mipmaps", true);
        configManager.setDefault("rendering.atlas.anisotropic", true);
        configManager.setDefault("rendering.atlas.maxAnisotropy", 16.0f);
        
        // Shader Compiler
        configManager.setDefault("rendering.shaders.hotReload", true);
        configManager.setDefault("rendering.shaders.optimization", true);
        configManager.setDefault("rendering.shaders.debugInfo", false);
        configManager.setDefault("rendering.shaders.directory", "assets/shaders");
        
        // Render Graph
        configManager.setDefault("rendering.renderGraph.enabled", true);
        configManager.setDefault("rendering.renderGraph.validation", true);
        
        logManager.debug("RenderingPlugin", "Configuration defaults registered");
    }
    
    private void registerAssetTypes() {
        // Register shader asset types
        assetManager.registerAssetType("shader", "glsl", "vert", "frag", "geom", "comp", "tesc", "tese");
        
        // Register material asset types
        assetManager.registerAssetType("material", "mat", "json");
        
        // Register texture asset types (if not already registered)
        assetManager.registerAssetType("texture", "png", "jpg", "jpeg", "tga", "bmp", "dds", "hdr", "exr");
        
        // Register model asset types (if not already registered)
        assetManager.registerAssetType("model", "obj", "fbx", "gltf", "glb", "dae");
        
        logManager.debug("RenderingPlugin", "Asset types registered");
    }
}