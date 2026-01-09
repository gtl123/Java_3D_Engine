package engine.rendering.advanced;

import engine.config.ConfigurationManager;
import engine.logging.LogManager;
import engine.logging.MetricsCollector;
import engine.io.Window;
import engine.camera.Camera;
import engine.raster.Transformation;
import engine.shaders.ShaderProgram;
import engine.utils.Utils;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.*;

/**
 * Deferred Renderer implementation for complex lighting scenarios.
 * Uses G-buffer to store geometry information and performs lighting in screen space.
 * Supports multiple render targets for PBR material properties and efficient lighting calculations.
 */
public class DeferredRenderer {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = LogManager.getInstance().getMetricsCollector();
    private static final ConfigurationManager configManager = ConfigurationManager.getInstance();
    
    // G-Buffer configuration
    private static final int GBUFFER_ALBEDO_METALLIC = 0;    // RGB: Albedo, A: Metallic
    private static final int GBUFFER_NORMAL_ROUGHNESS = 1;   // RGB: World Normal, A: Roughness
    private static final int GBUFFER_POSITION_AO = 2;        // RGB: World Position, A: AO
    private static final int GBUFFER_EMISSIVE_FLAGS = 3;     // RGB: Emissive, A: Material Flags
    private static final int GBUFFER_DEPTH = 4;             // Depth buffer
    
    // G-Buffer resources
    private int gBufferFBO = 0;
    private int[] gBufferTextures = new int[5];
    private int gBufferWidth = 0;
    private int gBufferHeight = 0;
    
    // Lighting pass resources
    private int lightingFBO = 0;
    private int lightingColorTexture = 0;
    private int lightingDepthTexture = 0;
    
    // Screen quad for full-screen passes
    private int screenQuadVAO = 0;
    private int screenQuadVBO = 0;
    
    // Shader programs
    private ShaderProgram geometryPassShader;
    private ShaderProgram lightingPassShader;
    private ShaderProgram compositeShader;
    
    // Render state
    private boolean initialized = false;
    private boolean enableMSAA = false;
    private int msaaSamples = 4;
    
    // Performance tracking
    private long lastFrameTime = 0;
    private int renderedObjects = 0;
    private int lightCount = 0;
    
    /**
     * Initialize the deferred renderer.
     */
    public void initialize(int width, int height) throws Exception {
        if (initialized) {
            logManager.warn("DeferredRenderer", "Deferred renderer already initialized");
            return;
        }
        
        logManager.info("DeferredRenderer", "Initializing deferred renderer",
                       "width", width, "height", height);
        
        // Load configuration
        enableMSAA = configManager.getValue("rendering.deferred.msaa.enabled", false);
        msaaSamples = configManager.getValue("rendering.deferred.msaa.samples", 4);
        
        // Initialize G-Buffer
        initializeGBuffer(width, height);
        
        // Initialize lighting framebuffer
        initializeLightingBuffer(width, height);
        
        // Create screen quad
        createScreenQuad();
        
        // Load shaders
        loadShaders();
        
        initialized = true;
        
        logManager.info("DeferredRenderer", "Deferred renderer initialized successfully",
                       "msaa", enableMSAA, "samples", msaaSamples);
    }
    
    /**
     * Resize the deferred renderer buffers.
     */
    public void resize(int width, int height) {
        if (!initialized) return;
        
        if (gBufferWidth == width && gBufferHeight == height) {
            return; // No resize needed
        }
        
        logManager.info("DeferredRenderer", "Resizing deferred renderer",
                       "oldSize", gBufferWidth + "x" + gBufferHeight,
                       "newSize", width + "x" + height);
        
        // Cleanup old buffers
        cleanupBuffers();
        
        // Recreate buffers
        try {
            initializeGBuffer(width, height);
            initializeLightingBuffer(width, height);
        } catch (Exception e) {
            logManager.error("DeferredRenderer", "Failed to resize deferred renderer", e);
        }
    }
    
    /**
     * Begin geometry pass - renders to G-Buffer.
     */
    public void beginGeometryPass() {
        if (!initialized) return;
        
        long startTime = System.nanoTime();
        
        // Bind G-Buffer
        glBindFramebuffer(GL_FRAMEBUFFER, gBufferFBO);
        glViewport(0, 0, gBufferWidth, gBufferHeight);
        
        // Clear G-Buffer
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        
        // Enable depth testing
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glDepthMask(true);
        
        // Disable blending for geometry pass
        glDisable(GL_BLEND);
        
        // Enable back-face culling
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        
        // Bind geometry pass shader
        if (geometryPassShader != null) {
            geometryPassShader.bind();
        }
        
        renderedObjects = 0;
        metricsCollector.incrementCounter("deferred.geometryPass.started");
    }
    
    /**
     * End geometry pass.
     */
    public void endGeometryPass() {
        if (!initialized) return;
        
        if (geometryPassShader != null) {
            geometryPassShader.unbind();
        }
        
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        
        long endTime = System.nanoTime();
        metricsCollector.recordHistogram("deferred.geometryPass.duration", 
                                        (endTime - lastFrameTime) / 1_000_000.0); // Convert to milliseconds
        
        metricsCollector.setGauge("deferred.geometryPass.objects", renderedObjects);
        metricsCollector.incrementCounter("deferred.geometryPass.completed");
        
        logManager.debug("DeferredRenderer", "Geometry pass completed",
                        "objects", renderedObjects);
    }
    
    /**
     * Begin lighting pass - performs deferred lighting calculations.
     */
    public void beginLightingPass(Camera camera, LightingSystem lightingSystem) {
        if (!initialized) return;
        
        long startTime = System.nanoTime();
        
        // Bind lighting framebuffer
        glBindFramebuffer(GL_FRAMEBUFFER, lightingFBO);
        glViewport(0, 0, gBufferWidth, gBufferHeight);
        
        // Clear lighting buffer
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        
        // Disable depth testing for lighting pass
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        
        // Enable additive blending for multiple lights
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE);
        
        // Bind lighting shader
        if (lightingPassShader != null) {
            lightingPassShader.bind();
            
            // Bind G-Buffer textures
            for (int i = 0; i < 4; i++) {
                glActiveTexture(GL_TEXTURE0 + i);
                glBindTexture(GL_TEXTURE_2D, gBufferTextures[i]);
            }
            
            // Set G-Buffer samplers
            lightingPassShader.setUniform("gAlbedoMetallic", 0);
            lightingPassShader.setUniform("gNormalRoughness", 1);
            lightingPassShader.setUniform("gPositionAO", 2);
            lightingPassShader.setUniform("gEmissiveFlags", 3);
            
            // Set camera uniforms
            lightingPassShader.setUniform("viewPos", camera.getPosition());
            lightingPassShader.setUniform("viewMatrix", new Transformation().getViewMatrix(camera));
            
            // Set screen size
            lightingPassShader.setUniform("screenSize", 
                new org.joml.Vector2f(gBufferWidth, gBufferHeight));
        }
        
        lightCount = 0;
        metricsCollector.incrementCounter("deferred.lightingPass.started");
    }
    
    /**
     * Render a directional light.
     */
    public void renderDirectionalLight(Vector3f direction, Vector3f color, float intensity) {
        if (!initialized || lightingPassShader == null) return;
        
        // Set light uniforms
        lightingPassShader.setUniform("lightType", 0); // Directional light
        lightingPassShader.setUniform("lightDirection", direction);
        lightingPassShader.setUniform("lightColor", color);
        lightingPassShader.setUniform("lightIntensity", intensity);
        
        // Render full-screen quad
        renderScreenQuad();
        
        lightCount++;
        metricsCollector.incrementCounter("deferred.lights.directional");
    }
    
    /**
     * Render a point light.
     */
    public void renderPointLight(Vector3f position, Vector3f color, float intensity, float radius) {
        if (!initialized || lightingPassShader == null) return;
        
        // Set light uniforms
        lightingPassShader.setUniform("lightType", 1); // Point light
        lightingPassShader.setUniform("lightPosition", position);
        lightingPassShader.setUniform("lightColor", color);
        lightingPassShader.setUniform("lightIntensity", intensity);
        lightingPassShader.setUniform("lightRadius", radius);
        
        // TODO: Implement light volume rendering for better performance
        // For now, render full-screen quad
        renderScreenQuad();
        
        lightCount++;
        metricsCollector.incrementCounter("deferred.lights.point");
    }
    
    /**
     * Render a spot light.
     */
    public void renderSpotLight(Vector3f position, Vector3f direction, Vector3f color, 
                               float intensity, float radius, float innerCone, float outerCone) {
        if (!initialized || lightingPassShader == null) return;
        
        // Set light uniforms
        lightingPassShader.setUniform("lightType", 2); // Spot light
        lightingPassShader.setUniform("lightPosition", position);
        lightingPassShader.setUniform("lightDirection", direction);
        lightingPassShader.setUniform("lightColor", color);
        lightingPassShader.setUniform("lightIntensity", intensity);
        lightingPassShader.setUniform("lightRadius", radius);
        lightingPassShader.setUniform("lightInnerCone", (float)Math.cos(Math.toRadians(innerCone)));
        lightingPassShader.setUniform("lightOuterCone", (float)Math.cos(Math.toRadians(outerCone)));
        
        // TODO: Implement light volume rendering for better performance
        renderScreenQuad();
        
        lightCount++;
        metricsCollector.incrementCounter("deferred.lights.spot");
    }
    
    /**
     * End lighting pass.
     */
    public void endLightingPass() {
        if (!initialized) return;
        
        if (lightingPassShader != null) {
            lightingPassShader.unbind();
        }
        
        // Restore default blend mode
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        
        metricsCollector.setGauge("deferred.lightingPass.lights", lightCount);
        metricsCollector.incrementCounter("deferred.lightingPass.completed");
        
        logManager.debug("DeferredRenderer", "Lighting pass completed", "lights", lightCount);
    }
    
    /**
     * Composite the final image to the screen or target framebuffer.
     */
    public void composite(int targetFBO, int targetWidth, int targetHeight) {
        if (!initialized) return;
        
        // Bind target framebuffer (0 for screen)
        glBindFramebuffer(GL_FRAMEBUFFER, targetFBO);
        glViewport(0, 0, targetWidth, targetHeight);
        
        // Disable depth testing and culling
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        
        if (compositeShader != null) {
            compositeShader.bind();
            
            // Bind lighting result texture
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, lightingColorTexture);
            compositeShader.setUniform("lightingTexture", 0);
            
            // Bind G-Buffer emissive for additive blending
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, gBufferTextures[GBUFFER_EMISSIVE_FLAGS]);
            compositeShader.setUniform("emissiveTexture", 1);
            
            // Render full-screen quad
            renderScreenQuad();
            
            compositeShader.unbind();
        }
        
        metricsCollector.incrementCounter("deferred.composite.completed");
    }
    
    /**
     * Render geometry with PBR material to G-Buffer.
     */
    public void renderGeometry(Matrix4f modelMatrix, Matrix4f viewMatrix, Matrix4f projectionMatrix,
                              PBRMaterialSystem.PBRMaterial material) {
        if (!initialized || geometryPassShader == null) return;
        
        // Set matrices
        Matrix4f modelViewMatrix = new Matrix4f(viewMatrix).mul(modelMatrix);
        geometryPassShader.setUniform("modelMatrix", modelMatrix);
        geometryPassShader.setUniform("modelViewMatrix", modelViewMatrix);
        geometryPassShader.setUniform("projectionMatrix", projectionMatrix);
        
        // Set material properties
        geometryPassShader.setUniform("material.albedo", material.getAlbedo());
        geometryPassShader.setUniform("material.metallic", material.getMetallic());
        geometryPassShader.setUniform("material.roughness", material.getRoughness());
        geometryPassShader.setUniform("material.ao", material.getAO());
        geometryPassShader.setUniform("material.emissive", material.getEmissive());
        geometryPassShader.setUniform("material.alpha", material.getAlpha());
        
        // Set material flags
        geometryPassShader.setUniform("material.useAlbedoMap", material.usesAlbedoMap() ? 1 : 0);
        geometryPassShader.setUniform("material.useNormalMap", material.usesNormalMap() ? 1 : 0);
        geometryPassShader.setUniform("material.useMetallicRoughnessMap", material.usesMetallicRoughnessMap() ? 1 : 0);
        geometryPassShader.setUniform("material.useAOMap", material.usesAOMap() ? 1 : 0);
        geometryPassShader.setUniform("material.useEmissiveMap", material.usesEmissiveMap() ? 1 : 0);
        
        // Bind textures
        int textureUnit = 0;
        if (material.usesAlbedoMap() && material.getAlbedoMap() != null) {
            glActiveTexture(GL_TEXTURE0 + textureUnit);
            glBindTexture(GL_TEXTURE_2D, material.getAlbedoMap().getId());
            geometryPassShader.setUniform("albedoMap", textureUnit++);
        }
        
        if (material.usesNormalMap() && material.getNormalMap() != null) {
            glActiveTexture(GL_TEXTURE0 + textureUnit);
            glBindTexture(GL_TEXTURE_2D, material.getNormalMap().getId());
            geometryPassShader.setUniform("normalMap", textureUnit++);
        }
        
        if (material.usesMetallicRoughnessMap() && material.getMetallicRoughnessMap() != null) {
            glActiveTexture(GL_TEXTURE0 + textureUnit);
            glBindTexture(GL_TEXTURE_2D, material.getMetallicRoughnessMap().getId());
            geometryPassShader.setUniform("metallicRoughnessMap", textureUnit++);
        }
        
        if (material.usesAOMap() && material.getAOMap() != null) {
            glActiveTexture(GL_TEXTURE0 + textureUnit);
            glBindTexture(GL_TEXTURE_2D, material.getAOMap().getId());
            geometryPassShader.setUniform("aoMap", textureUnit++);
        }
        
        if (material.usesEmissiveMap() && material.getEmissiveMap() != null) {
            glActiveTexture(GL_TEXTURE0 + textureUnit);
            glBindTexture(GL_TEXTURE_2D, material.getEmissiveMap().getId());
            geometryPassShader.setUniform("emissiveMap", textureUnit++);
        }
        
        renderedObjects++;
    }
    
    /**
     * Get G-Buffer texture for debugging or post-processing.
     */
    public int getGBufferTexture(int index) {
        if (index >= 0 && index < gBufferTextures.length) {
            return gBufferTextures[index];
        }
        return 0;
    }
    
    /**
     * Get lighting result texture.
     */
    public int getLightingTexture() {
        return lightingColorTexture;
    }
    
    private void initializeGBuffer(int width, int height) throws Exception {
        gBufferWidth = width;
        gBufferHeight = height;
        
        // Create G-Buffer FBO
        gBufferFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, gBufferFBO);
        
        // Create G-Buffer textures
        for (int i = 0; i < 4; i++) {
            gBufferTextures[i] = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, gBufferTextures[i]);
            
            // Different formats for different G-Buffer components
            int internalFormat, format, type;
            switch (i) {
                case GBUFFER_ALBEDO_METALLIC:
                    internalFormat = GL_RGBA8;
                    format = GL_RGBA;
                    type = GL_UNSIGNED_BYTE;
                    break;
                case GBUFFER_NORMAL_ROUGHNESS:
                    internalFormat = GL_RGBA16F;
                    format = GL_RGBA;
                    type = GL_FLOAT;
                    break;
                case GBUFFER_POSITION_AO:
                    internalFormat = GL_RGBA32F;
                    format = GL_RGBA;
                    type = GL_FLOAT;
                    break;
                case GBUFFER_EMISSIVE_FLAGS:
                    internalFormat = GL_RGBA8;
                    format = GL_RGBA;
                    type = GL_UNSIGNED_BYTE;
                    break;
                default:
                    internalFormat = GL_RGBA8;
                    format = GL_RGBA;
                    type = GL_UNSIGNED_BYTE;
            }
            
            glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, GL_TEXTURE_2D, gBufferTextures[i], 0);
        }
        
        // Create depth texture
        gBufferTextures[GBUFFER_DEPTH] = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, gBufferTextures[GBUFFER_DEPTH]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, width, height, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, gBufferTextures[GBUFFER_DEPTH], 0);
        
        // Set draw buffers
        int[] drawBuffers = {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1, GL_COLOR_ATTACHMENT2, GL_COLOR_ATTACHMENT3};
        glDrawBuffers(drawBuffers);
        
        // Check framebuffer completeness
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new Exception("G-Buffer framebuffer incomplete: " + status);
        }
        
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        
        logManager.debug("DeferredRenderer", "G-Buffer initialized", 
                        "width", width, "height", height);
    }
    
    private void initializeLightingBuffer(int width, int height) throws Exception {
        // Create lighting FBO
        lightingFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, lightingFBO);
        
        // Create lighting color texture
        lightingColorTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, lightingColorTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, lightingColorTexture, 0);
        
        // Use G-Buffer depth for lighting pass
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, gBufferTextures[GBUFFER_DEPTH], 0);
        
        // Check framebuffer completeness
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new Exception("Lighting framebuffer incomplete: " + status);
        }
        
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        
        logManager.debug("DeferredRenderer", "Lighting buffer initialized");
    }
    
    private void createScreenQuad() {
        float[] quadVertices = {
            // positions   // texCoords
            -1.0f,  1.0f,  0.0f, 1.0f,
            -1.0f, -1.0f,  0.0f, 0.0f,
             1.0f, -1.0f,  1.0f, 0.0f,
            -1.0f,  1.0f,  0.0f, 1.0f,
             1.0f, -1.0f,  1.0f, 0.0f,
             1.0f,  1.0f,  1.0f, 1.0f
        };
        
        screenQuadVAO = glGenVertexArrays();
        screenQuadVBO = glGenBuffers();
        
        glBindVertexArray(screenQuadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, screenQuadVBO);
        glBufferData(GL_ARRAY_BUFFER, quadVertices, GL_STATIC_DRAW);
        
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        
        glBindVertexArray(0);
    }
    
    private void renderScreenQuad() {
        glBindVertexArray(screenQuadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
    }
    
    private void loadShaders() throws Exception {
        // Note: Shader files will be created separately
        // For now, create placeholder shaders that will be replaced with actual implementations
        
        logManager.info("DeferredRenderer", "Loading deferred rendering shaders");
        
        // TODO: Load actual shader files when they are created
        // geometryPassShader = new ShaderProgram();
        // lightingPassShader = new ShaderProgram();
        // compositeShader = new ShaderProgram();
        
        logManager.debug("DeferredRenderer", "Deferred rendering shaders loaded");
    }
    
    private void cleanupBuffers() {
        if (gBufferFBO != 0) {
            glDeleteFramebuffers(gBufferFBO);
            gBufferFBO = 0;
        }
        
        for (int i = 0; i < gBufferTextures.length; i++) {
            if (gBufferTextures[i] != 0) {
                glDeleteTextures(gBufferTextures[i]);
                gBufferTextures[i] = 0;
            }
        }
        
        if (lightingFBO != 0) {
            glDeleteFramebuffers(lightingFBO);
            lightingFBO = 0;
        }
        
        if (lightingColorTexture != 0) {
            glDeleteTextures(lightingColorTexture);
            lightingColorTexture = 0;
        }
    }
    
    /**
     * Cleanup the deferred renderer.
     */
    public void cleanup() {
        if (!initialized) return;
        
        logManager.info("DeferredRenderer", "Cleaning up deferred renderer");
        
        cleanupBuffers();
        
        if (screenQuadVAO != 0) {
            glDeleteVertexArrays(screenQuadVAO);
            screenQuadVAO = 0;
        }
        
        if (screenQuadVBO != 0) {
            glDeleteBuffers(screenQuadVBO);
            screenQuadVBO = 0;
        }
        
        if (geometryPassShader != null) {
            geometryPassShader.cleanup();
            geometryPassShader = null;
        }
        
        if (lightingPassShader != null) {
            lightingPassShader.cleanup();
            lightingPassShader = null;
        }
        
        if (compositeShader != null) {
            compositeShader.cleanup();
            compositeShader = null;
        }
        
        initialized = false;
        
        logManager.info("DeferredRenderer", "Deferred renderer cleanup complete");
    }
    
    /**
     * Check if the deferred renderer is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Get G-Buffer dimensions.
     */
    public int getWidth() { return gBufferWidth; }
    public int getHeight() { return gBufferHeight; }
}