package engine.rendering.advanced;

import engine.config.ConfigurationManager;
import engine.logging.LogManager;
import engine.logging.MetricsCollector;
import engine.camera.Camera;
import engine.shaders.ShaderProgram;
import engine.utils.Utils;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.*;

/**
 * Advanced Lighting System with multiple light types and shadow mapping.
 * Supports directional, point, and spot lights with cascaded shadow maps for directional lights.
 * Integrates with the deferred renderer for efficient lighting calculations.
 */
public class LightingSystem {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = LogManager.getInstance().getMetricsCollector();
    private static final ConfigurationManager configManager = ConfigurationManager.getInstance();
    
    // Light type constants
    public static final int LIGHT_TYPE_DIRECTIONAL = 0;
    public static final int LIGHT_TYPE_POINT = 1;
    public static final int LIGHT_TYPE_SPOT = 2;
    
    // Shadow mapping configuration
    private static final int SHADOW_MAP_SIZE = 2048;
    private static final int MAX_CASCADES = 4;
    private static final int MAX_POINT_LIGHTS = 64;
    private static final int MAX_SPOT_LIGHTS = 32;
    
    // Light storage
    private final List<DirectionalLight> directionalLights = new ArrayList<>();
    private final List<PointLight> pointLights = new ArrayList<>();
    private final List<SpotLight> spotLights = new ArrayList<>();
    
    // Shadow mapping resources
    private int shadowMapFBO = 0;
    private int[] cascadeShadowMaps = new int[MAX_CASCADES];
    private int pointShadowMapFBO = 0;
    private int[] pointShadowMaps = new int[MAX_POINT_LIGHTS];
    private int spotShadowMapFBO = 0;
    private int[] spotShadowMaps = new int[MAX_SPOT_LIGHTS];
    
    // Shadow mapping matrices
    private Matrix4f[] cascadeViewProjectionMatrices = new Matrix4f[MAX_CASCADES];
    private float[] cascadeSplits = new float[MAX_CASCADES + 1];
    
    // Shader programs for shadow mapping
    private ShaderProgram shadowMapShader;
    private ShaderProgram pointShadowMapShader;
    private ShaderProgram spotShadowMapShader;
    
    // System state
    private boolean initialized = false;
    private boolean shadowsEnabled = true;
    private int activeCascades = 3;
    private float shadowBias = 0.005f;
    private float shadowNormalBias = 0.1f;
    
    // Performance tracking
    private int shadowMapUpdates = 0;
    private long lastShadowUpdateTime = 0;
    
    /**
     * Directional Light representation.
     */
    public static class DirectionalLight {
        private final String id;
        private Vector3f direction = new Vector3f(0, -1, 0);
        private Vector3f color = new Vector3f(1, 1, 1);
        private float intensity = 1.0f;
        private boolean castsShadows = true;
        private boolean enabled = true;
        
        // Shadow mapping properties
        private float shadowDistance = 100.0f;
        private float shadowFadeDistance = 10.0f;
        
        public DirectionalLight(String id) {
            this.id = id;
        }
        
        // Getters and setters
        public String getId() { return id; }
        
        public Vector3f getDirection() { return new Vector3f(direction); }
        public DirectionalLight setDirection(Vector3f direction) { 
            this.direction.set(direction).normalize(); 
            return this; 
        }
        public DirectionalLight setDirection(float x, float y, float z) { 
            this.direction.set(x, y, z).normalize(); 
            return this; 
        }
        
        public Vector3f getColor() { return new Vector3f(color); }
        public DirectionalLight setColor(Vector3f color) { this.color.set(color); return this; }
        public DirectionalLight setColor(float r, float g, float b) { this.color.set(r, g, b); return this; }
        
        public float getIntensity() { return intensity; }
        public DirectionalLight setIntensity(float intensity) { this.intensity = Math.max(0, intensity); return this; }
        
        public boolean castsShadows() { return castsShadows; }
        public DirectionalLight setCastsShadows(boolean castsShadows) { this.castsShadows = castsShadows; return this; }
        
        public boolean isEnabled() { return enabled; }
        public DirectionalLight setEnabled(boolean enabled) { this.enabled = enabled; return this; }
        
        public float getShadowDistance() { return shadowDistance; }
        public DirectionalLight setShadowDistance(float distance) { this.shadowDistance = Math.max(1, distance); return this; }
        
        public float getShadowFadeDistance() { return shadowFadeDistance; }
        public DirectionalLight setShadowFadeDistance(float distance) { this.shadowFadeDistance = Math.max(0, distance); return this; }
    }
    
    /**
     * Point Light representation.
     */
    public static class PointLight {
        private final String id;
        private Vector3f position = new Vector3f(0, 0, 0);
        private Vector3f color = new Vector3f(1, 1, 1);
        private float intensity = 1.0f;
        private float radius = 10.0f;
        private boolean castsShadows = false; // Expensive for point lights
        private boolean enabled = true;
        
        // Attenuation parameters
        private float constant = 1.0f;
        private float linear = 0.09f;
        private float quadratic = 0.032f;
        
        public PointLight(String id) {
            this.id = id;
        }
        
        // Getters and setters
        public String getId() { return id; }
        
        public Vector3f getPosition() { return new Vector3f(position); }
        public PointLight setPosition(Vector3f position) { this.position.set(position); return this; }
        public PointLight setPosition(float x, float y, float z) { this.position.set(x, y, z); return this; }
        
        public Vector3f getColor() { return new Vector3f(color); }
        public PointLight setColor(Vector3f color) { this.color.set(color); return this; }
        public PointLight setColor(float r, float g, float b) { this.color.set(r, g, b); return this; }
        
        public float getIntensity() { return intensity; }
        public PointLight setIntensity(float intensity) { this.intensity = Math.max(0, intensity); return this; }
        
        public float getRadius() { return radius; }
        public PointLight setRadius(float radius) { this.radius = Math.max(0.1f, radius); return this; }
        
        public boolean castsShadows() { return castsShadows; }
        public PointLight setCastsShadows(boolean castsShadows) { this.castsShadows = castsShadows; return this; }
        
        public boolean isEnabled() { return enabled; }
        public PointLight setEnabled(boolean enabled) { this.enabled = enabled; return this; }
        
        public float getConstant() { return constant; }
        public PointLight setConstant(float constant) { this.constant = Math.max(0, constant); return this; }
        
        public float getLinear() { return linear; }
        public PointLight setLinear(float linear) { this.linear = Math.max(0, linear); return this; }
        
        public float getQuadratic() { return quadratic; }
        public PointLight setQuadratic(float quadratic) { this.quadratic = Math.max(0, quadratic); return this; }
    }
    
    /**
     * Spot Light representation.
     */
    public static class SpotLight {
        private final String id;
        private Vector3f position = new Vector3f(0, 0, 0);
        private Vector3f direction = new Vector3f(0, -1, 0);
        private Vector3f color = new Vector3f(1, 1, 1);
        private float intensity = 1.0f;
        private float radius = 10.0f;
        private float innerConeAngle = 30.0f; // degrees
        private float outerConeAngle = 45.0f; // degrees
        private boolean castsShadows = true;
        private boolean enabled = true;
        
        // Attenuation parameters
        private float constant = 1.0f;
        private float linear = 0.09f;
        private float quadratic = 0.032f;
        
        public SpotLight(String id) {
            this.id = id;
        }
        
        // Getters and setters
        public String getId() { return id; }
        
        public Vector3f getPosition() { return new Vector3f(position); }
        public SpotLight setPosition(Vector3f position) { this.position.set(position); return this; }
        public SpotLight setPosition(float x, float y, float z) { this.position.set(x, y, z); return this; }
        
        public Vector3f getDirection() { return new Vector3f(direction); }
        public SpotLight setDirection(Vector3f direction) { 
            this.direction.set(direction).normalize(); 
            return this; 
        }
        public SpotLight setDirection(float x, float y, float z) { 
            this.direction.set(x, y, z).normalize(); 
            return this; 
        }
        
        public Vector3f getColor() { return new Vector3f(color); }
        public SpotLight setColor(Vector3f color) { this.color.set(color); return this; }
        public SpotLight setColor(float r, float g, float b) { this.color.set(r, g, b); return this; }
        
        public float getIntensity() { return intensity; }
        public SpotLight setIntensity(float intensity) { this.intensity = Math.max(0, intensity); return this; }
        
        public float getRadius() { return radius; }
        public SpotLight setRadius(float radius) { this.radius = Math.max(0.1f, radius); return this; }
        
        public float getInnerConeAngle() { return innerConeAngle; }
        public SpotLight setInnerConeAngle(float angle) { 
            this.innerConeAngle = Math.max(0, Math.min(90, angle)); 
            return this; 
        }
        
        public float getOuterConeAngle() { return outerConeAngle; }
        public SpotLight setOuterConeAngle(float angle) { 
            this.outerConeAngle = Math.max(innerConeAngle, Math.min(90, angle)); 
            return this; 
        }
        
        public boolean castsShadows() { return castsShadows; }
        public SpotLight setCastsShadows(boolean castsShadows) { this.castsShadows = castsShadows; return this; }
        
        public boolean isEnabled() { return enabled; }
        public SpotLight setEnabled(boolean enabled) { this.enabled = enabled; return this; }
        
        public float getConstant() { return constant; }
        public SpotLight setConstant(float constant) { this.constant = Math.max(0, constant); return this; }
        
        public float getLinear() { return linear; }
        public SpotLight setLinear(float linear) { this.linear = Math.max(0, linear); return this; }
        
        public float getQuadratic() { return quadratic; }
        public SpotLight setQuadratic(float quadratic) { this.quadratic = Math.max(0, quadratic); return this; }
    }
    
    /**
     * Initialize the lighting system.
     */
    public void initialize() throws Exception {
        if (initialized) {
            logManager.warn("LightingSystem", "Lighting system already initialized");
            return;
        }
        
        logManager.info("LightingSystem", "Initializing advanced lighting system");
        
        // Load configuration
        shadowsEnabled = configManager.getValue("rendering.shadows.enabled", true);
        activeCascades = configManager.getValue("rendering.shadows.cascades", 3);
        shadowBias = configManager.getValue("rendering.shadows.bias", 0.005f);
        shadowNormalBias = configManager.getValue("rendering.shadows.normalBias", 0.1f);
        
        // Initialize cascade matrices
        for (int i = 0; i < MAX_CASCADES; i++) {
            cascadeViewProjectionMatrices[i] = new Matrix4f();
        }
        
        // Initialize shadow mapping
        if (shadowsEnabled) {
            initializeShadowMapping();
        }
        
        // Load shadow mapping shaders
        loadShadowShaders();
        
        initialized = true;
        
        logManager.info("LightingSystem", "Advanced lighting system initialized",
                       "shadows", shadowsEnabled, "cascades", activeCascades);
    }
    
    /**
     * Add a directional light.
     */
    public DirectionalLight addDirectionalLight(String id) {
        DirectionalLight light = new DirectionalLight(id);
        directionalLights.add(light);
        
        metricsCollector.incrementCounter("lighting.directionalLights.added");
        logManager.debug("LightingSystem", "Directional light added", "id", id);
        
        return light;
    }
    
    /**
     * Add a point light.
     */
    public PointLight addPointLight(String id) {
        if (pointLights.size() >= MAX_POINT_LIGHTS) {
            logManager.warn("LightingSystem", "Maximum point lights reached", "max", MAX_POINT_LIGHTS);
            return null;
        }
        
        PointLight light = new PointLight(id);
        pointLights.add(light);
        
        metricsCollector.incrementCounter("lighting.pointLights.added");
        logManager.debug("LightingSystem", "Point light added", "id", id);
        
        return light;
    }
    
    /**
     * Add a spot light.
     */
    public SpotLight addSpotLight(String id) {
        if (spotLights.size() >= MAX_SPOT_LIGHTS) {
            logManager.warn("LightingSystem", "Maximum spot lights reached", "max", MAX_SPOT_LIGHTS);
            return null;
        }
        
        SpotLight light = new SpotLight(id);
        spotLights.add(light);
        
        metricsCollector.incrementCounter("lighting.spotLights.added");
        logManager.debug("LightingSystem", "Spot light added", "id", id);
        
        return light;
    }
    
    /**
     * Remove a light by ID.
     */
    public boolean removeLight(String id) {
        // Try directional lights
        if (directionalLights.removeIf(light -> light.getId().equals(id))) {
            metricsCollector.incrementCounter("lighting.directionalLights.removed");
            logManager.debug("LightingSystem", "Directional light removed", "id", id);
            return true;
        }
        
        // Try point lights
        if (pointLights.removeIf(light -> light.getId().equals(id))) {
            metricsCollector.incrementCounter("lighting.pointLights.removed");
            logManager.debug("LightingSystem", "Point light removed", "id", id);
            return true;
        }
        
        // Try spot lights
        if (spotLights.removeIf(light -> light.getId().equals(id))) {
            metricsCollector.incrementCounter("lighting.spotLights.removed");
            logManager.debug("LightingSystem", "Spot light removed", "id", id);
            return true;
        }
        
        return false;
    }
    
    /**
     * Update shadow maps for all shadow-casting lights.
     */
    public void updateShadowMaps(Camera camera, ShadowCasterRenderer shadowCasterRenderer) {
        if (!initialized || !shadowsEnabled) return;
        
        long startTime = System.nanoTime();
        shadowMapUpdates = 0;
        
        // Update directional light shadow maps (cascaded)
        for (DirectionalLight light : directionalLights) {
            if (light.isEnabled() && light.castsShadows()) {
                updateDirectionalLightShadowMap(light, camera, shadowCasterRenderer);
                shadowMapUpdates++;
            }
        }
        
        // Update spot light shadow maps
        for (int i = 0; i < spotLights.size(); i++) {
            SpotLight light = spotLights.get(i);
            if (light.isEnabled() && light.castsShadows()) {
                updateSpotLightShadowMap(light, i, shadowCasterRenderer);
                shadowMapUpdates++;
            }
        }
        
        // Update point light shadow maps (cube maps - expensive)
        for (int i = 0; i < pointLights.size(); i++) {
            PointLight light = pointLights.get(i);
            if (light.isEnabled() && light.castsShadows()) {
                updatePointLightShadowMap(light, i, shadowCasterRenderer);
                shadowMapUpdates++;
            }
        }
        
        long endTime = System.nanoTime();
        lastShadowUpdateTime = endTime - startTime;
        
        metricsCollector.recordHistogram("lighting.shadowMaps.updateTime", 
                                        lastShadowUpdateTime / 1_000_000.0); // Convert to milliseconds
        metricsCollector.setGauge("lighting.shadowMaps.updated", shadowMapUpdates);
        
        logManager.debug("LightingSystem", "Shadow maps updated", 
                        "count", shadowMapUpdates, 
                        "timeMs", lastShadowUpdateTime / 1_000_000.0);
    }
    
    /**
     * Get all enabled directional lights.
     */
    public List<DirectionalLight> getEnabledDirectionalLights() {
        return directionalLights.stream()
            .filter(DirectionalLight::isEnabled)
            .toList();
    }
    
    /**
     * Get all enabled point lights.
     */
    public List<PointLight> getEnabledPointLights() {
        return pointLights.stream()
            .filter(PointLight::isEnabled)
            .toList();
    }
    
    /**
     * Get all enabled spot lights.
     */
    public List<SpotLight> getEnabledSpotLights() {
        return spotLights.stream()
            .filter(SpotLight::isEnabled)
            .toList();
    }
    
    /**
     * Get cascade shadow map texture for directional lights.
     */
    public int getCascadeShadowMap(int cascade) {
        if (cascade >= 0 && cascade < cascadeShadowMaps.length) {
            return cascadeShadowMaps[cascade];
        }
        return 0;
    }
    
    /**
     * Get cascade view-projection matrix.
     */
    public Matrix4f getCascadeViewProjectionMatrix(int cascade) {
        if (cascade >= 0 && cascade < cascadeViewProjectionMatrices.length) {
            return new Matrix4f(cascadeViewProjectionMatrices[cascade]);
        }
        return new Matrix4f();
    }
    
    /**
     * Get cascade splits for shader uniforms.
     */
    public float[] getCascadeSplits() {
        return cascadeSplits.clone();
    }
    
    private void initializeShadowMapping() throws Exception {
        // Initialize directional light cascaded shadow maps
        shadowMapFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, shadowMapFBO);
        
        for (int i = 0; i < MAX_CASCADES; i++) {
            cascadeShadowMaps[i] = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, cascadeShadowMaps[i]);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE, 
                        0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
            
            // Set border color to white (no shadow)
            float[] borderColor = {1.0f, 1.0f, 1.0f, 1.0f};
            glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, borderColor);
            
            // Enable shadow comparison
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
        }
        
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new Exception("Shadow map framebuffer incomplete: " + status);
        }
        
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        
        // Calculate cascade splits using practical split scheme
        calculateCascadeSplits(camera -> 0.1f, camera -> 100.0f); // Default values, will be updated per frame
        
        logManager.debug("LightingSystem", "Shadow mapping initialized", 
                        "shadowMapSize", SHADOW_MAP_SIZE, "cascades", MAX_CASCADES);
    }
    
    private void calculateCascadeSplits(java.util.function.Function<Camera, Float> nearPlane, 
                                      java.util.function.Function<Camera, Float> farPlane) {
        float near = nearPlane.apply(null); // Will be updated with actual camera
        float far = farPlane.apply(null);
        float lambda = 0.5f; // Practical split scheme parameter
        
        cascadeSplits[0] = near;
        cascadeSplits[activeCascades] = far;
        
        for (int i = 1; i < activeCascades; i++) {
            float p = (float) i / activeCascades;
            float log = near * (float) Math.pow(far / near, p);
            float uniform = near + (far - near) * p;
            cascadeSplits[i] = lambda * log + (1 - lambda) * uniform;
        }
    }
    
    private void updateDirectionalLightShadowMap(DirectionalLight light, Camera camera, 
                                               ShadowCasterRenderer shadowCasterRenderer) {
        // TODO: Implement cascaded shadow map rendering
        // This would involve:
        // 1. Calculate cascade frustums based on camera
        // 2. Calculate light view-projection matrices for each cascade
        // 3. Render shadow casters to each cascade
        
        logManager.debug("LightingSystem", "Updating directional light shadow map", 
                        "lightId", light.getId());
    }
    
    private void updateSpotLightShadowMap(SpotLight light, int lightIndex, 
                                        ShadowCasterRenderer shadowCasterRenderer) {
        // TODO: Implement spot light shadow map rendering
        logManager.debug("LightingSystem", "Updating spot light shadow map", 
                        "lightId", light.getId(), "index", lightIndex);
    }
    
    private void updatePointLightShadowMap(PointLight light, int lightIndex, 
                                         ShadowCasterRenderer shadowCasterRenderer) {
        // TODO: Implement point light cube shadow map rendering
        logManager.debug("LightingSystem", "Updating point light shadow map", 
                        "lightId", light.getId(), "index", lightIndex);
    }
    
    private void loadShadowShaders() throws Exception {
        // TODO: Load shadow mapping shaders when shader files are created
        logManager.debug("LightingSystem", "Shadow shaders loaded");
    }
    
    /**
     * Interface for rendering shadow casters.
     */
    public interface ShadowCasterRenderer {
        void renderShadowCasters(Matrix4f lightViewProjectionMatrix);
    }
    
    /**
     * Cleanup the lighting system.
     */
    public void cleanup() {
        if (!initialized) return;
        
        logManager.info("LightingSystem", "Cleaning up lighting system");
        
        // Cleanup shadow maps
        if (shadowMapFBO != 0) {
            glDeleteFramebuffers(shadowMapFBO);
            shadowMapFBO = 0;
        }
        
        for (int i = 0; i < cascadeShadowMaps.length; i++) {
            if (cascadeShadowMaps[i] != 0) {
                glDeleteTextures(cascadeShadowMaps[i]);
                cascadeShadowMaps[i] = 0;
            }
        }
        
        // Cleanup shaders
        if (shadowMapShader != null) {
            shadowMapShader.cleanup();
            shadowMapShader = null;
        }
        
        if (pointShadowMapShader != null) {
            pointShadowMapShader.cleanup();
            pointShadowMapShader = null;
        }
        
        if (spotShadowMapShader != null) {
            spotShadowMapShader.cleanup();
            spotShadowMapShader = null;
        }
        
        // Clear lights
        directionalLights.clear();
        pointLights.clear();
        spotLights.clear();
        
        initialized = false;
        
        logManager.info("LightingSystem", "Lighting system cleanup complete");
    }
    
    /**
     * Check if the lighting system is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Get lighting statistics.
     */
    public LightingStatistics getStatistics() {
        return new LightingStatistics(
            directionalLights.size(),
            pointLights.size(),
            spotLights.size(),
            shadowMapUpdates,
            lastShadowUpdateTime / 1_000_000.0 // Convert to milliseconds
        );
    }
    
    /**
     * Lighting system statistics.
     */
    public static class LightingStatistics {
        public final int directionalLights;
        public final int pointLights;
        public final int spotLights;
        public final int shadowMapUpdates;
        public final double lastShadowUpdateTimeMs;
        
        public LightingStatistics(int directionalLights, int pointLights, int spotLights,
                                int shadowMapUpdates, double lastShadowUpdateTimeMs) {
            this.directionalLights = directionalLights;
            this.pointLights = pointLights;
            this.spotLights = spotLights;
            this.shadowMapUpdates = shadowMapUpdates;
            this.lastShadowUpdateTimeMs = lastShadowUpdateTimeMs;
        }
        
        @Override
        public String toString() {
            return String.format("LightingStats{dir=%d, point=%d, spot=%d, shadowUpdates=%d, shadowTimeMs=%.2f}",
                               directionalLights, pointLights, spotLights, shadowMapUpdates, lastShadowUpdateTimeMs);
        }
    }
}