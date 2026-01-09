package engine.rendering.advanced.passes;

import engine.rendering.advanced.RenderGraph;
import engine.rendering.advanced.PBRMaterialSystem;
import engine.logging.LogManager;
import org.joml.Matrix4f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Geometry pass for deferred rendering pipeline.
 * Renders scene geometry to G-buffer textures containing:
 * - Albedo + Metallic (RGBA)
 * - Normal + Roughness (RGBA) 
 * - Position + AO (RGBA)
 * - Depth buffer
 */
public class GeometryPass extends RenderGraph.RenderPass {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final PBRMaterialSystem materialSystem;
    private int framebuffer = 0;
    private int shaderProgram = 0;
    
    public GeometryPass(PBRMaterialSystem materialSystem) {
        super("GeometryPass");
        this.materialSystem = materialSystem;
        this.priority = 10; // Early in pipeline
    }
    
    @Override
    public void setup(RenderGraph.RenderGraphBuilder builder) {
        // Create G-buffer textures
        builder.createTexture("gAlbedoMetallic", RenderGraph.RenderResource.ResourceFormat.RGBA8, false)
               .createTexture("gNormalRoughness", RenderGraph.RenderResource.ResourceFormat.RGBA16F, false)
               .createTexture("gPositionAO", RenderGraph.RenderResource.ResourceFormat.RGBA32F, false)
               .createTexture("gDepth", RenderGraph.RenderResource.ResourceFormat.DEPTH24, false)
               .createFramebuffer("gBuffer", false);
        
        // Define outputs
        addOutput("gAlbedoMetallic");
        addOutput("gNormalRoughness");
        addOutput("gPositionAO");
        addOutput("gDepth");
        addOutput("gBuffer");
        
        logManager.debug("GeometryPass", "Setup completed");
    }
    
    @Override
    public void execute(RenderGraph.RenderGraphContext context) {
        // Get G-buffer resources
        int gBuffer = context.getResourceId("gBuffer");
        int albedoTexture = context.getResourceId("gAlbedoMetallic");
        int normalTexture = context.getResourceId("gNormalRoughness");
        int positionTexture = context.getResourceId("gPositionAO");
        int depthTexture = context.getResourceId("gDepth");
        
        // Setup framebuffer
        glBindFramebuffer(GL_FRAMEBUFFER, gBuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, albedoTexture, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, normalTexture, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT2, GL_TEXTURE_2D, positionTexture, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0);
        
        // Set draw buffers
        int[] drawBuffers = {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1, GL_COLOR_ATTACHMENT2};
        glDrawBuffers(drawBuffers);
        
        // Clear G-buffer
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        
        // Enable depth testing
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        
        // Render geometry here
        // This would integrate with the existing mesh rendering system
        // and use PBR materials from the material system
        
        logManager.debug("GeometryPass", "Geometry pass executed");
    }
    
    @Override
    public void cleanup() {
        if (framebuffer != 0) {
            glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
        }
        
        logManager.debug("GeometryPass", "Cleanup completed");
    }
}