package engine.rendering.advanced.passes;

import engine.rendering.advanced.RenderGraph;
import engine.logging.LogManager;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Final presentation pass that renders the processed image to the screen.
 * This is typically the last pass in the render graph that outputs to the default framebuffer.
 */
public class PresentationPass extends RenderGraph.RenderPass {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private int shaderProgram = 0;
    private int quadVAO = 0;
    private int quadVBO = 0;
    
    // Fullscreen quad vertices
    private static final float[] QUAD_VERTICES = {
        -1.0f,  1.0f, 0.0f, 1.0f,
        -1.0f, -1.0f, 0.0f, 0.0f,
         1.0f, -1.0f, 1.0f, 0.0f,
        -1.0f,  1.0f, 0.0f, 1.0f,
         1.0f, -1.0f, 1.0f, 0.0f,
         1.0f,  1.0f, 1.0f, 1.0f
    };
    
    public PresentationPass() {
        super("PresentationPass");
        this.priority = 100; // Final pass in pipeline
    }
    
    @Override
    public void setup(RenderGraph.RenderGraphBuilder builder) {
        // Import the default framebuffer (screen)
        builder.importResource("screenBuffer", 0, 
                              RenderGraph.RenderResource.ResourceType.FRAMEBUFFER,
                              RenderGraph.RenderResource.ResourceFormat.RGBA8,
                              new org.joml.Vector2i(0, 0));
        
        // Define inputs from post-processing pass
        addInput("finalColor");
        
        // Define outputs (screen)
        addOutput("screenBuffer");
        
        // Setup fullscreen quad
        setupQuad();
        
        logManager.debug("PresentationPass", "Setup completed");
    }
    
    @Override
    public void execute(RenderGraph.RenderGraphContext context) {
        // Get final processed image
        int finalColorTexture = context.getResourceId("finalColor");
        
        // Bind default framebuffer (screen)
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        
        // Set viewport to window size
        int width = context.getWindow().getWidth();
        int height = context.getWindow().getHeight();
        glViewport(0, 0, width, height);
        
        // Clear screen
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        
        // Disable depth testing for screen quad
        glDisable(GL_DEPTH_TEST);
        
        // Use presentation shader (simple texture copy)
        if (shaderProgram != 0) {
            glUseProgram(shaderProgram);
            
            // Bind final color texture
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, finalColorTexture);
            glUniform1i(glGetUniformLocation(shaderProgram, "u_texture"), 0);
            
            // Set any additional uniforms (gamma correction, exposure, etc.)
            setPresentationUniforms();
            
            // Render fullscreen quad
            glBindVertexArray(quadVAO);
            glDrawArrays(GL_TRIANGLES, 0, 6);
            glBindVertexArray(0);
            
            glUseProgram(0);
        } else {
            // Fallback: just clear screen with a color
            glClearColor(0.2f, 0.3f, 0.3f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
        }
        
        logManager.debug("PresentationPass", "Presentation pass executed");
    }
    
    private void setupQuad() {
        // Generate VAO and VBO for fullscreen quad
        quadVAO = glGenVertexArrays();
        quadVBO = glGenBuffers();
        
        glBindVertexArray(quadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        glBufferData(GL_ARRAY_BUFFER, QUAD_VERTICES, GL_STATIC_DRAW);
        
        // Position attribute
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        
        // Texture coordinate attribute
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);
        
        glBindVertexArray(0);
        
        logManager.debug("PresentationPass", "Fullscreen quad setup completed");
    }
    
    private void setPresentationUniforms() {
        // Set gamma correction
        int gammaLoc = glGetUniformLocation(shaderProgram, "u_gamma");
        if (gammaLoc != -1) {
            glUniform1f(gammaLoc, 2.2f); // Standard gamma correction
        }
        
        // Set exposure for HDR displays
        int exposureLoc = glGetUniformLocation(shaderProgram, "u_exposure");
        if (exposureLoc != -1) {
            glUniform1f(exposureLoc, 1.0f); // Default exposure
        }
        
        // Additional presentation parameters could be set here
        // (brightness, contrast, saturation, etc.)
    }
    
    @Override
    public void cleanup() {
        if (quadVAO != 0) {
            glDeleteVertexArrays(quadVAO);
            quadVAO = 0;
        }
        
        if (quadVBO != 0) {
            glDeleteBuffers(quadVBO);
            quadVBO = 0;
        }
        
        if (shaderProgram != 0) {
            glDeleteProgram(shaderProgram);
            shaderProgram = 0;
        }
        
        logManager.debug("PresentationPass", "Cleanup completed");
    }
    
    /**
     * Set the shader program for this pass.
     */
    public void setShaderProgram(int shaderProgram) {
        this.shaderProgram = shaderProgram;
    }
}