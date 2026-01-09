package engine.rendering.advanced.passes;

import engine.rendering.advanced.RenderGraph;
import engine.rendering.advanced.PostProcessingPipeline;
import engine.logging.LogManager;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Post-processing pass that integrates the PostProcessingPipeline with the RenderGraph.
 * Takes HDR input from lighting pass and applies post-processing effects.
 * Outputs final LDR result to screen or next pass.
 */
public class PostProcessingPass extends RenderGraph.RenderPass {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final PostProcessingPipeline postProcessingPipeline;
    
    public PostProcessingPass(PostProcessingPipeline postProcessingPipeline) {
        super("PostProcessingPass");
        this.postProcessingPipeline = postProcessingPipeline;
        this.priority = 80; // Late in pipeline, after lighting
    }
    
    @Override
    public void setup(RenderGraph.RenderGraphBuilder builder) {
        // Create final output texture
        builder.createTexture("finalColor", RenderGraph.RenderResource.ResourceFormat.RGBA8, false)
               .createFramebuffer("finalFramebuffer", false);
        
        // Define inputs from lighting pass
        addInput("hdrColor");
        addInput("gDepth"); // For depth-based effects like SSAO
        
        // Define outputs
        addOutput("finalColor");
        addOutput("finalFramebuffer");
        
        logManager.debug("PostProcessingPass", "Setup completed");
    }
    
    @Override
    public void execute(RenderGraph.RenderGraphContext context) {
        // Get input resources
        int hdrColorTexture = context.getResourceId("hdrColor");
        int depthTexture = context.getResourceId("gDepth");
        
        // Get output resources
        int finalFramebuffer = context.getResourceId("finalFramebuffer");
        int finalColorTexture = context.getResourceId("finalColor");
        
        // Setup final framebuffer
        glBindFramebuffer(GL_FRAMEBUFFER, finalFramebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, finalColorTexture, 0);
        
        // Set viewport
        int width = context.getWindow().getWidth();
        int height = context.getWindow().getHeight();
        glViewport(0, 0, width, height);
        
        // Execute post-processing pipeline
        try {
            // Create a temporary context for the post-processing pipeline
            PostProcessingPipeline.PostProcessingContext ppContext = 
                new PostProcessingPipeline.PostProcessingContext(
                    context.getWindow(),
                    context.getCamera(),
                    context.getDeltaTime()
                );
            
            // Execute the post-processing pipeline
            // The pipeline will handle ping-pong rendering between its internal buffers
            // and output the final result to the bound framebuffer
            postProcessingPipeline.execute(ppContext, hdrColorTexture, depthTexture);
            
            logManager.debug("PostProcessingPass", "Post-processing executed successfully");
            
        } catch (Exception e) {
            logManager.error("PostProcessingPass", "Error during post-processing execution", e);
            
            // Fallback: just copy HDR input to output with simple tone mapping
            copyHDRToLDR(hdrColorTexture, width, height);
        }
        
        logManager.debug("PostProcessingPass", "Post-processing pass executed");
    }
    
    /**
     * Fallback method to copy HDR input to LDR output with simple tone mapping.
     */
    private void copyHDRToLDR(int hdrTexture, int width, int height) {
        // This would use a simple shader to tone map HDR to LDR
        // For now, just clear the buffer
        glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        
        logManager.debug("PostProcessingPass", "Fallback HDR to LDR copy executed");
    }
    
    @Override
    public void cleanup() {
        // Post-processing pipeline handles its own cleanup
        logManager.debug("PostProcessingPass", "Cleanup completed");
    }
}