package engine.rendering.advanced.passes;

import engine.rendering.advanced.RenderGraph;
import engine.rendering.advanced.LightingSystem;
import engine.logging.LogManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Lighting pass for deferred rendering pipeline.
 * Reads from G-buffer and applies PBR lighting calculations.
 * Outputs final lit scene to HDR render target.
 */
public class LightingPass extends RenderGraph.RenderPass {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final LightingSystem lightingSystem;
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
    
    public LightingPass(LightingSystem lightingSystem) {
        super("LightingPass");
        this.lightingSystem = lightingSystem;
        this.priority = 50; // After geometry pass
    }
    
    @Override
    public void setup(RenderGraph.RenderGraphBuilder builder) {
        // Create HDR render target
        builder.createTexture("hdrColor", RenderGraph.RenderResource.ResourceFormat.RGBA16F, false)
               .createFramebuffer("hdrFramebuffer", false);
        
        // Define inputs from geometry pass
        addInput("gAlbedoMetallic");
        addInput("gNormalRoughness");
        addInput("gPositionAO");
        addInput("gDepth");
        
        // Define outputs
        addOutput("hdrColor");
        addOutput("hdrFramebuffer");
        
        // Setup fullscreen quad
        setupQuad();
        
        logManager.debug("LightingPass", "Setup completed");
    }
    
    @Override
    public void execute(RenderGraph.RenderGraphContext context) {
        // Get resources
        int hdrFramebuffer = context.getResourceId("hdrFramebuffer");
        int hdrColorTexture = context.getResourceId("hdrColor");
        int albedoTexture = context.getResourceId("gAlbedoMetallic");
        int normalTexture = context.getResourceId("gNormalRoughness");
        int positionTexture = context.getResourceId("gPositionAO");
        int depthTexture = context.getResourceId("gDepth");
        
        // Setup HDR framebuffer
        glBindFramebuffer(GL_FRAMEBUFFER, hdrFramebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, hdrColorTexture, 0);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0);
        
        // Set viewport
        int width = context.getWindow().getWidth();
        int height = context.getWindow().getHeight();
        glViewport(0, 0, width, height);
        
        // Clear HDR buffer
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        
        // Disable depth writing (we're using the depth from geometry pass)
        glDepthMask(false);
        glDisable(GL_DEPTH_TEST);
        
        // Use lighting shader (would be loaded from shader compiler)
        if (shaderProgram != 0) {
            glUseProgram(shaderProgram);
            
            // Bind G-buffer textures
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, albedoTexture);
            glUniform1i(glGetUniformLocation(shaderProgram, "gAlbedoMetallic"), 0);
            
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, normalTexture);
            glUniform1i(glGetUniformLocation(shaderProgram, "gNormalRoughness"), 1);
            
            glActiveTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D, positionTexture);
            glUniform1i(glGetUniformLocation(shaderProgram, "gPositionAO"), 2);
            
            // Set camera uniforms
            Matrix4f viewMatrix = context.getCamera().getViewMatrix();
            Matrix4f projMatrix = context.getCamera().getProjectionMatrix();
            Vector3f cameraPos = context.getCamera().getPosition();
            
            int viewLoc = glGetUniformLocation(shaderProgram, "u_viewMatrix");
            int projLoc = glGetUniformLocation(shaderProgram, "u_projMatrix");
            int cameraPosLoc = glGetUniformLocation(shaderProgram, "u_cameraPos");
            
            if (viewLoc != -1) {
                float[] viewArray = new float[16];
                viewMatrix.get(viewArray);
                glUniformMatrix4fv(viewLoc, false, viewArray);
            }
            
            if (projLoc != -1) {
                float[] projArray = new float[16];
                projMatrix.get(projArray);
                glUniformMatrix4fv(projLoc, false, projArray);
            }
            
            if (cameraPosLoc != -1) {
                glUniform3f(cameraPosLoc, cameraPos.x, cameraPos.y, cameraPos.z);
            }
            
            // Set lighting uniforms (would integrate with LightingSystem)
            setLightingUniforms();
            
            // Render fullscreen quad
            glBindVertexArray(quadVAO);
            glDrawArrays(GL_TRIANGLES, 0, 6);
            glBindVertexArray(0);
            
            glUseProgram(0);
        }
        
        // Re-enable depth testing for subsequent passes
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
        
        logManager.debug("LightingPass", "Lighting pass executed");
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
        
        logManager.debug("LightingPass", "Fullscreen quad setup completed");
    }
    
    private void setLightingUniforms() {
        // This would integrate with the LightingSystem to set:
        // - Light positions, colors, intensities
        // - Shadow map textures
        // - Environment maps
        // - PBR parameters
        
        // Example light setup (would be dynamic from LightingSystem)
        int numLightsLoc = glGetUniformLocation(shaderProgram, "u_numLights");
        if (numLightsLoc != -1) {
            glUniform1i(numLightsLoc, lightingSystem.getActiveLightCount());
        }
        
        // Set directional light
        if (lightingSystem.getDirectionalLight() != null) {
            LightingSystem.DirectionalLight dirLight = lightingSystem.getDirectionalLight();
            
            int dirLoc = glGetUniformLocation(shaderProgram, "u_directionalLight.direction");
            int colorLoc = glGetUniformLocation(shaderProgram, "u_directionalLight.color");
            int intensityLoc = glGetUniformLocation(shaderProgram, "u_directionalLight.intensity");
            
            if (dirLoc != -1) {
                Vector3f dir = dirLight.getDirection();
                glUniform3f(dirLoc, dir.x, dir.y, dir.z);
            }
            
            if (colorLoc != -1) {
                Vector3f color = dirLight.getColor();
                glUniform3f(colorLoc, color.x, color.y, color.z);
            }
            
            if (intensityLoc != -1) {
                glUniform1f(intensityLoc, dirLight.getIntensity());
            }
        }
        
        // Set point lights (would iterate through active point lights)
        // Set spot lights (would iterate through active spot lights)
        // Set shadow maps
        // Set environment lighting parameters
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
        
        logManager.debug("LightingPass", "Cleanup completed");
    }
    
    /**
     * Set the shader program for this pass.
     */
    public void setShaderProgram(int shaderProgram) {
        this.shaderProgram = shaderProgram;
    }
}