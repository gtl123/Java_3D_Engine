package engine.rendering.advanced;

import engine.config.ConfigurationManager;
import engine.logging.LogManager;
import engine.logging.MetricsCollector;
import engine.shaders.ShaderProgram;
import engine.utils.Utils;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.*;

/**
 * Configurable Post-Processing Pipeline for advanced visual effects.
 * Supports SSAO, bloom, tone mapping, color grading, and other screen-space effects.
 * Provides a flexible framework for chaining multiple post-processing effects.
 */
public class PostProcessingPipeline {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = LogManager.getInstance().getMetricsCollector();
    private static final ConfigurationManager configManager = ConfigurationManager.getInstance();
    
    // Pipeline configuration
    private final List<PostProcessingEffect> effects = new ArrayList<>();
    private final Map<String, PostProcessingEffect> effectMap = new ConcurrentHashMap<>();
    
    // Framebuffer ping-pong for multi-pass effects
    private int[] framebuffers = new int[2];
    private int[] colorTextures = new int[2];
    private int currentBuffer = 0;
    
    // Screen quad for full-screen effects
    private int screenQuadVAO = 0;
    private int screenQuadVBO = 0;
    
    // Pipeline state
    private boolean initialized = false;
    private int width = 0;
    private int height = 0;
    
    // Built-in effects
    private SSAOEffect ssaoEffect;
    private BloomEffect bloomEffect;
    private ToneMappingEffect toneMappingEffect;
    private ColorGradingEffect colorGradingEffect;
    private FXAAEffect fxaaEffect;
    
    // Performance tracking
    private long lastProcessingTime = 0;
    private int effectsProcessed = 0;
    
    /**
     * Base class for post-processing effects.
     */
    public abstract static class PostProcessingEffect {
        protected final String name;
        protected boolean enabled = true;
        protected int priority = 100; // Lower = earlier in pipeline
        
        public PostProcessingEffect(String name) {
            this.name = name;
        }
        
        public abstract void initialize(int width, int height) throws Exception;
        public abstract void process(int inputTexture, int outputFramebuffer, int width, int height);
        public abstract void cleanup();
        public abstract void resize(int width, int height);
        
        public String getName() { return name; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
    }
    
    /**
     * Screen Space Ambient Occlusion (SSAO) effect.
     */
    public static class SSAOEffect extends PostProcessingEffect {
        private ShaderProgram ssaoShader;
        private ShaderProgram blurShader;
        private int ssaoFBO = 0;
        private int ssaoColorTexture = 0;
        private int blurFBO = 0;
        private int blurColorTexture = 0;
        private int noiseTexture = 0;
        
        // SSAO parameters
        private float radius = 0.5f;
        private float bias = 0.025f;
        private int kernelSize = 64;
        private float power = 2.0f;
        
        public SSAOEffect() {
            super("SSAO");
            this.priority = 10; // Early in pipeline
        }
        
        @Override
        public void initialize(int width, int height) throws Exception {
            // Create SSAO framebuffer
            ssaoFBO = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, ssaoFBO);
            
            ssaoColorTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, ssaoColorTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, width, height, 0, GL_RED, GL_FLOAT, (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, ssaoColorTexture, 0);
            
            // Create blur framebuffer
            blurFBO = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, blurFBO);
            
            blurColorTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, blurColorTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, width, height, 0, GL_RED, GL_FLOAT, (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, blurColorTexture, 0);
            
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            
            // Generate noise texture for SSAO
            generateNoiseTexture();
            
            // TODO: Load SSAO shaders when shader files are created
            logManager.debug("SSAOEffect", "SSAO effect initialized", "width", width, "height", height);
        }
        
        @Override
        public void process(int inputTexture, int outputFramebuffer, int width, int height) {
            if (ssaoShader == null) return;
            
            // SSAO pass
            glBindFramebuffer(GL_FRAMEBUFFER, ssaoFBO);
            glViewport(0, 0, width, height);
            glClear(GL_COLOR_BUFFER_BIT);
            
            ssaoShader.bind();
            // Bind G-Buffer textures and noise texture
            // TODO: Set SSAO uniforms and render
            ssaoShader.unbind();
            
            // Blur pass
            glBindFramebuffer(GL_FRAMEBUFFER, blurFBO);
            glClear(GL_COLOR_BUFFER_BIT);
            
            if (blurShader != null) {
                blurShader.bind();
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, ssaoColorTexture);
                // TODO: Render blur
                blurShader.unbind();
            }
            
            // Final composite
            glBindFramebuffer(GL_FRAMEBUFFER, outputFramebuffer);
            // TODO: Composite SSAO with input texture
        }
        
        @Override
        public void resize(int width, int height) {
            cleanup();
            try {
                initialize(width, height);
            } catch (Exception e) {
                logManager.error("SSAOEffect", "Failed to resize SSAO effect", e);
            }
        }
        
        @Override
        public void cleanup() {
            if (ssaoFBO != 0) {
                glDeleteFramebuffers(ssaoFBO);
                ssaoFBO = 0;
            }
            if (ssaoColorTexture != 0) {
                glDeleteTextures(ssaoColorTexture);
                ssaoColorTexture = 0;
            }
            if (blurFBO != 0) {
                glDeleteFramebuffers(blurFBO);
                blurFBO = 0;
            }
            if (blurColorTexture != 0) {
                glDeleteTextures(blurColorTexture);
                blurColorTexture = 0;
            }
            if (noiseTexture != 0) {
                glDeleteTextures(noiseTexture);
                noiseTexture = 0;
            }
            if (ssaoShader != null) {
                ssaoShader.cleanup();
                ssaoShader = null;
            }
            if (blurShader != null) {
                blurShader.cleanup();
                blurShader = null;
            }
        }
        
        private void generateNoiseTexture() {
            // Generate 4x4 noise texture for SSAO
            float[] noise = new float[16 * 3]; // 4x4 RGB
            for (int i = 0; i < 16; i++) {
                noise[i * 3] = (float) Math.random() * 2.0f - 1.0f; // x
                noise[i * 3 + 1] = (float) Math.random() * 2.0f - 1.0f; // y
                noise[i * 3 + 2] = 0.0f; // z
            }
            
            noiseTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, noiseTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB32F, 4, 4, 0, GL_RGB, GL_FLOAT, noise);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        }
        
        // Getters and setters for SSAO parameters
        public float getRadius() { return radius; }
        public void setRadius(float radius) { this.radius = Math.max(0.1f, radius); }
        
        public float getBias() { return bias; }
        public void setBias(float bias) { this.bias = Math.max(0.0f, bias); }
        
        public int getKernelSize() { return kernelSize; }
        public void setKernelSize(int kernelSize) { this.kernelSize = Math.max(1, Math.min(128, kernelSize)); }
        
        public float getPower() { return power; }
        public void setPower(float power) { this.power = Math.max(0.1f, power); }
    }
    
    /**
     * Bloom effect for bright areas.
     */
    public static class BloomEffect extends PostProcessingEffect {
        private ShaderProgram brightPassShader;
        private ShaderProgram blurShader;
        private ShaderProgram compositeShader;
        
        private int[] bloomFBOs = new int[2];
        private int[] bloomTextures = new int[2];
        private int brightPassFBO = 0;
        private int brightPassTexture = 0;
        
        // Bloom parameters
        private float threshold = 1.0f;
        private float intensity = 1.0f;
        private int blurPasses = 5;
        
        public BloomEffect() {
            super("Bloom");
            this.priority = 50; // Mid pipeline
        }
        
        @Override
        public void initialize(int width, int height) throws Exception {
            // Create bright pass framebuffer
            brightPassFBO = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, brightPassFBO);
            
            brightPassTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, brightPassTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width / 2, height / 2, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, brightPassTexture, 0);
            
            // Create bloom blur framebuffers (ping-pong)
            for (int i = 0; i < 2; i++) {
                bloomFBOs[i] = glGenFramebuffers();
                glBindFramebuffer(GL_FRAMEBUFFER, bloomFBOs[i]);
                
                bloomTextures[i] = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, bloomTextures[i]);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width / 2, height / 2, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, bloomTextures[i], 0);
            }
            
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            
            // TODO: Load bloom shaders when shader files are created
            logManager.debug("BloomEffect", "Bloom effect initialized", "width", width, "height", height);
        }
        
        @Override
        public void process(int inputTexture, int outputFramebuffer, int width, int height) {
            // Implementation would go here - simplified for brevity
            logManager.debug("BloomEffect", "Processing bloom effect");
        }
        
        @Override
        public void resize(int width, int height) {
            cleanup();
            try {
                initialize(width, height);
            } catch (Exception e) {
                logManager.error("BloomEffect", "Failed to resize bloom effect", e);
            }
        }
        
        @Override
        public void cleanup() {
            if (brightPassFBO != 0) {
                glDeleteFramebuffers(brightPassFBO);
                brightPassFBO = 0;
            }
            if (brightPassTexture != 0) {
                glDeleteTextures(brightPassTexture);
                brightPassTexture = 0;
            }
            
            for (int i = 0; i < 2; i++) {
                if (bloomFBOs[i] != 0) {
                    glDeleteFramebuffers(bloomFBOs[i]);
                    bloomFBOs[i] = 0;
                }
                if (bloomTextures[i] != 0) {
                    glDeleteTextures(bloomTextures[i]);
                    bloomTextures[i] = 0;
                }
            }
            
            if (brightPassShader != null) {
                brightPassShader.cleanup();
                brightPassShader = null;
            }
            if (blurShader != null) {
                blurShader.cleanup();
                blurShader = null;
            }
            if (compositeShader != null) {
                compositeShader.cleanup();
                compositeShader = null;
            }
        }
        
        // Getters and setters for bloom parameters
        public float getThreshold() { return threshold; }
        public void setThreshold(float threshold) { this.threshold = Math.max(0.0f, threshold); }
        
        public float getIntensity() { return intensity; }
        public void setIntensity(float intensity) { this.intensity = Math.max(0.0f, intensity); }
        
        public int getBlurPasses() { return blurPasses; }
        public void setBlurPasses(int passes) { this.blurPasses = Math.max(1, Math.min(10, passes)); }
    }
    
    /**
     * Tone mapping effect for HDR to LDR conversion.
     */
    public static class ToneMappingEffect extends PostProcessingEffect {
        private ShaderProgram toneMappingShader;
        
        // Tone mapping parameters
        private float exposure = 1.0f;
        private float gamma = 2.2f;
        private int toneMappingMode = 0; // 0=Reinhard, 1=ACES, 2=Uncharted2
        
        public ToneMappingEffect() {
            super("ToneMapping");
            this.priority = 80; // Late in pipeline
        }
        
        @Override
        public void initialize(int width, int height) throws Exception {
            // TODO: Load tone mapping shader when shader files are created
            logManager.debug("ToneMappingEffect", "Tone mapping effect initialized");
        }
        
        @Override
        public void process(int inputTexture, int outputFramebuffer, int width, int height) {
            logManager.debug("ToneMappingEffect", "Processing tone mapping effect");
        }
        
        @Override
        public void resize(int width, int height) {
            // No resize needed for tone mapping
        }
        
        @Override
        public void cleanup() {
            if (toneMappingShader != null) {
                toneMappingShader.cleanup();
                toneMappingShader = null;
            }
        }
        
        // Getters and setters
        public float getExposure() { return exposure; }
        public void setExposure(float exposure) { this.exposure = Math.max(0.1f, exposure); }
        
        public float getGamma() { return gamma; }
        public void setGamma(float gamma) { this.gamma = Math.max(1.0f, Math.min(3.0f, gamma)); }
        
        public int getToneMappingMode() { return toneMappingMode; }
        public void setToneMappingMode(int mode) { this.toneMappingMode = Math.max(0, Math.min(2, mode)); }
    }
    
    /**
     * Color grading effect for artistic color adjustments.
     */
    public static class ColorGradingEffect extends PostProcessingEffect {
        private ShaderProgram colorGradingShader;
        
        // Color grading parameters
        private Vector3f colorFilter = new Vector3f(1.0f, 1.0f, 1.0f);
        private float saturation = 1.0f;
        private float contrast = 1.0f;
        private float brightness = 0.0f;
        private Vector3f colorBalance = new Vector3f(1.0f, 1.0f, 1.0f);
        
        public ColorGradingEffect() {
            super("ColorGrading");
            this.priority = 90; // Very late in pipeline
        }
        
        @Override
        public void initialize(int width, int height) throws Exception {
            // TODO: Load color grading shader when shader files are created
            logManager.debug("ColorGradingEffect", "Color grading effect initialized");
        }
        
        @Override
        public void process(int inputTexture, int outputFramebuffer, int width, int height) {
            logManager.debug("ColorGradingEffect", "Processing color grading effect");
        }
        
        @Override
        public void resize(int width, int height) {
            // No resize needed for color grading
        }
        
        @Override
        public void cleanup() {
            if (colorGradingShader != null) {
                colorGradingShader.cleanup();
                colorGradingShader = null;
            }
        }
        
        // Getters and setters
        public Vector3f getColorFilter() { return new Vector3f(colorFilter); }
        public void setColorFilter(Vector3f filter) { this.colorFilter.set(filter); }
        
        public float getSaturation() { return saturation; }
        public void setSaturation(float saturation) { this.saturation = Math.max(0.0f, Math.min(2.0f, saturation)); }
        
        public float getContrast() { return contrast; }
        public void setContrast(float contrast) { this.contrast = Math.max(0.0f, Math.min(2.0f, contrast)); }
        
        public float getBrightness() { return brightness; }
        public void setBrightness(float brightness) { this.brightness = Math.max(-1.0f, Math.min(1.0f, brightness)); }
        
        public Vector3f getColorBalance() { return new Vector3f(colorBalance); }
        public void setColorBalance(Vector3f balance) { this.colorBalance.set(balance); }
    }
    
    /**
     * Fast Approximate Anti-Aliasing (FXAA) effect.
     */
    public static class FXAAEffect extends PostProcessingEffect {
        private ShaderProgram fxaaShader;
        
        // FXAA parameters
        private float edgeThreshold = 0.166f;
        private float edgeThresholdMin = 0.0833f;
        private float subpixelQuality = 0.75f;
        
        public FXAAEffect() {
            super("FXAA");
            this.priority = 95; // Very late in pipeline
        }
        
        @Override
        public void initialize(int width, int height) throws Exception {
            // TODO: Load FXAA shader when shader files are created
            logManager.debug("FXAAEffect", "FXAA effect initialized");
        }
        
        @Override
        public void process(int inputTexture, int outputFramebuffer, int width, int height) {
            logManager.debug("FXAAEffect", "Processing FXAA effect");
        }
        
        @Override
        public void resize(int width, int height) {
            // No resize needed for FXAA
        }
        
        @Override
        public void cleanup() {
            if (fxaaShader != null) {
                fxaaShader.cleanup();
                fxaaShader = null;
            }
        }
        
        // Getters and setters
        public float getEdgeThreshold() { return edgeThreshold; }
        public void setEdgeThreshold(float threshold) { this.edgeThreshold = Math.max(0.063f, Math.min(0.333f, threshold)); }
        
        public float getEdgeThresholdMin() { return edgeThresholdMin; }
        public void setEdgeThresholdMin(float threshold) { this.edgeThresholdMin = Math.max(0.0312f, Math.min(0.25f, threshold)); }
        
        public float getSubpixelQuality() { return subpixelQuality; }
        public void setSubpixelQuality(float quality) { this.subpixelQuality = Math.max(0.0f, Math.min(1.0f, quality)); }
    }
    
    /**
     * Initialize the post-processing pipeline.
     */
    public void initialize(int width, int height) throws Exception {
        if (initialized) {
            logManager.warn("PostProcessingPipeline", "Post-processing pipeline already initialized");
            return;
        }
        
        this.width = width;
        this.height = height;
        
        logManager.info("PostProcessingPipeline", "Initializing post-processing pipeline",
                       "width", width, "height", height);
        
        // Create ping-pong framebuffers
        createFramebuffers(width, height);
        
        // Create screen quad
        createScreenQuad();
        
        // Initialize built-in effects
        initializeBuiltInEffects();
        
        initialized = true;
        
        logManager.info("PostProcessingPipeline", "Post-processing pipeline initialized",
                       "effectCount", effects.size());
    }
    
    /**
     * Process the entire post-processing pipeline.
     */
    public void process(int inputTexture, int outputFramebuffer) {
        if (!initialized || effects.isEmpty()) {
            // No effects, just copy input to output
            copyTexture(inputTexture, outputFramebuffer);
            return;
        }
        
        long startTime = System.nanoTime();
        effectsProcessed = 0;
        
        // Sort effects by priority
        effects.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));
        
        int currentInput = inputTexture;
        currentBuffer = 0;
        
        // Process each enabled effect
        for (int i = 0; i < effects.size(); i++) {
            PostProcessingEffect effect = effects.get(i);
            
            if (!effect.isEnabled()) {
                continue;
            }
            
            int targetFramebuffer;
            if (i == effects.size() - 1) {
                // Last effect outputs to final framebuffer
                targetFramebuffer = outputFramebuffer;
            } else {
                // Intermediate effects use ping-pong buffers
                targetFramebuffer = framebuffers[currentBuffer];
            }
            
            effect.process(currentInput, targetFramebuffer, width, height);
            
            // Next effect uses current output as input
            if (i < effects.size() - 1) {
                currentInput = colorTextures[currentBuffer];
                currentBuffer = 1 - currentBuffer; // Ping-pong
            }
            
            effectsProcessed++;
        }
        
        long endTime = System.nanoTime();
        lastProcessingTime = endTime - startTime;
        
        metricsCollector.recordHistogram("postProcessing.totalTime", 
                                        lastProcessingTime / 1_000_000.0); // Convert to milliseconds
        metricsCollector.setGauge("postProcessing.effectsProcessed", effectsProcessed);
        
        logManager.debug("PostProcessingPipeline", "Post-processing completed",
                        "effects", effectsProcessed, 
                        "timeMs", lastProcessingTime / 1_000_000.0);
    }
    
    /**
     * Add a custom post-processing effect.
     */
    public void addEffect(PostProcessingEffect effect) {
        if (effect == null) return;
        
        effects.add(effect);
        effectMap.put(effect.getName(), effect);
        
        if (initialized) {
            try {
                effect.initialize(width, height);
            } catch (Exception e) {
                logManager.error("PostProcessingPipeline", "Failed to initialize effect", e,
                               "effectName", effect.getName());
                effects.remove(effect);
                effectMap.remove(effect.getName());
            }
        }
        
        logManager.debug("PostProcessingPipeline", "Effect added", "name", effect.getName());
    }
    
    /**
     * Remove a post-processing effect.
     */
    public boolean removeEffect(String name) {
        PostProcessingEffect effect = effectMap.remove(name);
        if (effect != null) {
            effects.remove(effect);
            effect.cleanup();
            logManager.debug("PostProcessingPipeline", "Effect removed", "name", name);
            return true;
        }
        return false;
    }
    
    /**
     * Get a post-processing effect by name.
     */
    public PostProcessingEffect getEffect(String name) {
        return effectMap.get(name);
    }
    
    /**
     * Resize the post-processing pipeline.
     */
    public void resize(int width, int height) {
        if (!initialized) return;
        
        this.width = width;
        this.height = height;
        
        // Resize framebuffers
        cleanupFramebuffers();
        createFramebuffers(width, height);
        
        // Resize all effects
        for (PostProcessingEffect effect : effects) {
            effect.resize(width, height);
        }
        
        logManager.debug("PostProcessingPipeline", "Pipeline resized", 
                        "width", width, "height", height);
    }
    
    /**
     * Get built-in SSAO effect.
     */
    public SSAOEffect getSSAOEffect() {
        return ssaoEffect;
    }
    
    /**
     * Get built-in bloom effect.
     */
    public BloomEffect getBloomEffect() {
        return bloomEffect;
    }
    
    /**
     * Get built-in tone mapping effect.
     */
    public ToneMappingEffect getToneMappingEffect() {
        return toneMappingEffect;
    }
    
    /**
     * Get built-in color grading effect.
     */
    public ColorGradingEffect getColorGradingEffect() {
        return colorGradingEffect;
    }
    
    /**
     * Get built-in FXAA effect.
     */
    public FXAAEffect getFXAAEffect() {
        return fxaaEffect;
    }
    private void createFramebuffers(int width, int height) {
        for (int i = 0; i < 2; i++) {
            framebuffers[i] = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffers[i]);
            
            colorTextures[i] = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, colorTextures[i]);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTextures[i], 0);
            
            int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                throw new RuntimeException("Post-processing framebuffer incomplete: " + status);
            }
        }
        
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
    
    private void cleanupFramebuffers() {
        for (int i = 0; i < 2; i++) {
            if (framebuffers[i] != 0) {
                glDeleteFramebuffers(framebuffers[i]);
                framebuffers[i] = 0;
            }
            if (colorTextures[i] != 0) {
                glDeleteTextures(colorTextures[i]);
                colorTextures[i] = 0;
            }
        }
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
    
    private void initializeBuiltInEffects() {
        // Load configuration for built-in effects
        boolean enableSSAO = configManager.getValue("postProcessing.ssao.enabled", true);
        boolean enableBloom = configManager.getValue("postProcessing.bloom.enabled", true);
        boolean enableToneMapping = configManager.getValue("postProcessing.toneMapping.enabled", true);
        boolean enableColorGrading = configManager.getValue("postProcessing.colorGrading.enabled", false);
        boolean enableFXAA = configManager.getValue("postProcessing.fxaa.enabled", true);
        
        // Create and add built-in effects
        if (enableSSAO) {
            ssaoEffect = new SSAOEffect();
            addEffect(ssaoEffect);
        }
        
        if (enableBloom) {
            bloomEffect = new BloomEffect();
            addEffect(bloomEffect);
        }
        
        if (enableToneMapping) {
            toneMappingEffect = new ToneMappingEffect();
            addEffect(toneMappingEffect);
        }
        
        if (enableColorGrading) {
            colorGradingEffect = new ColorGradingEffect();
            addEffect(colorGradingEffect);
        }
        
        if (enableFXAA) {
            fxaaEffect = new FXAAEffect();
            addEffect(fxaaEffect);
        }
        
        logManager.debug("PostProcessingPipeline", "Built-in effects initialized",
                        "ssao", enableSSAO, "bloom", enableBloom, "toneMapping", enableToneMapping,
                        "colorGrading", enableColorGrading, "fxaa", enableFXAA);
    }
    
    private void copyTexture(int inputTexture, int outputFramebuffer) {
        // Simple texture copy when no effects are enabled
        glBindFramebuffer(GL_FRAMEBUFFER, outputFramebuffer);
        glViewport(0, 0, width, height);
        
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        
        // TODO: Use a simple copy shader when available
        // For now, just clear the framebuffer
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        
        logManager.debug("PostProcessingPipeline", "Texture copied (no effects)");
    }
    
    /**
     * Get pipeline statistics.
     */
    public PostProcessingStatistics getStatistics() {
        return new PostProcessingStatistics(
            effects.size(),
            effectsProcessed,
            lastProcessingTime / 1_000_000.0 // Convert to milliseconds
        );
    }
    
    /**
     * Cleanup the post-processing pipeline.
     */
    public void cleanup() {
        if (!initialized) return;
        
        logManager.info("PostProcessingPipeline", "Cleaning up post-processing pipeline");
        
        // Cleanup all effects
        for (PostProcessingEffect effect : effects) {
            effect.cleanup();
        }
        effects.clear();
        effectMap.clear();
        
        // Cleanup framebuffers
        cleanupFramebuffers();
        
        // Cleanup screen quad
        if (screenQuadVAO != 0) {
            glDeleteVertexArrays(screenQuadVAO);
            screenQuadVAO = 0;
        }
        if (screenQuadVBO != 0) {
            glDeleteBuffers(screenQuadVBO);
            screenQuadVBO = 0;
        }
        
        // Reset built-in effect references
        ssaoEffect = null;
        bloomEffect = null;
        toneMappingEffect = null;
        colorGradingEffect = null;
        fxaaEffect = null;
        
        initialized = false;
        
        logManager.info("PostProcessingPipeline", "Post-processing pipeline cleanup complete");
    }
    
    /**
     * Check if the pipeline is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Post-processing pipeline statistics.
     */
    public static class PostProcessingStatistics {
        public final int totalEffects;
        public final int effectsProcessed;
        public final double lastProcessingTimeMs;
        
        public PostProcessingStatistics(int totalEffects, int effectsProcessed, double lastProcessingTimeMs) {
            this.totalEffects = totalEffects;
            this.effectsProcessed = effectsProcessed;
            this.lastProcessingTimeMs = lastProcessingTimeMs;
        }
        
        @Override
        public String toString() {
            return String.format("PostProcessingStats{total=%d, processed=%d, timeMs=%.2f}",
                               totalEffects, effectsProcessed, lastProcessingTimeMs);
        }
    }
}
    