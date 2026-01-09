package engine.rendering.advanced;

import engine.assets.AssetManager;
import engine.config.ConfigurationManager;
import engine.logging.LogManager;
import engine.logging.MetricsCollector;
import engine.raster.Texture;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Physically-Based Rendering (PBR) Material System.
 * Manages PBR materials with metallic/roughness workflow, normal mapping, and material properties.
 * Integrates with the asset system for texture loading and provides material management for the advanced rendering pipeline.
 */
public class PBRMaterialSystem {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = LogManager.getInstance().getMetricsCollector();
    private static final ConfigurationManager configManager = ConfigurationManager.getInstance();
    private static final AssetManager assetManager = AssetManager.getInstance();
    
    private final Map<String, PBRMaterial> materials = new ConcurrentHashMap<>();
    private final Map<String, MaterialTemplate> templates = new ConcurrentHashMap<>();
    
    private boolean initialized = false;
    
    /**
     * PBR Material representation with all necessary properties.
     */
    public static class PBRMaterial {
        private final String id;
        private final String name;
        
        // Base material properties
        private Vector3f albedo = new Vector3f(1.0f, 1.0f, 1.0f);
        private float metallic = 0.0f;
        private float roughness = 0.5f;
        private float ao = 1.0f; // Ambient occlusion
        private Vector3f emissive = new Vector3f(0.0f, 0.0f, 0.0f);
        private float alpha = 1.0f;
        
        // Texture maps
        private Texture albedoMap;
        private Texture normalMap;
        private Texture metallicRoughnessMap; // R=unused, G=roughness, B=metallic
        private Texture aoMap;
        private Texture emissiveMap;
        private Texture heightMap;
        
        // Material flags
        private boolean useAlbedoMap = false;
        private boolean useNormalMap = false;
        private boolean useMetallicRoughnessMap = false;
        private boolean useAOMap = false;
        private boolean useEmissiveMap = false;
        private boolean useHeightMap = false;
        private boolean isTransparent = false;
        private boolean doubleSided = false;
        
        // Advanced properties
        private float normalScale = 1.0f;
        private float heightScale = 0.05f;
        private float emissiveStrength = 1.0f;
        
        public PBRMaterial(String id, String name) {
            this.id = id;
            this.name = name;
        }
        
        // Getters and setters
        public String getId() { return id; }
        public String getName() { return name; }
        
        public Vector3f getAlbedo() { return new Vector3f(albedo); }
        public PBRMaterial setAlbedo(Vector3f albedo) { this.albedo.set(albedo); return this; }
        public PBRMaterial setAlbedo(float r, float g, float b) { this.albedo.set(r, g, b); return this; }
        
        public float getMetallic() { return metallic; }
        public PBRMaterial setMetallic(float metallic) { this.metallic = Math.max(0.0f, Math.min(1.0f, metallic)); return this; }
        
        public float getRoughness() { return roughness; }
        public PBRMaterial setRoughness(float roughness) { this.roughness = Math.max(0.0f, Math.min(1.0f, roughness)); return this; }
        
        public float getAO() { return ao; }
        public PBRMaterial setAO(float ao) { this.ao = Math.max(0.0f, Math.min(1.0f, ao)); return this; }
        
        public Vector3f getEmissive() { return new Vector3f(emissive); }
        public PBRMaterial setEmissive(Vector3f emissive) { this.emissive.set(emissive); return this; }
        public PBRMaterial setEmissive(float r, float g, float b) { this.emissive.set(r, g, b); return this; }
        
        public float getAlpha() { return alpha; }
        public PBRMaterial setAlpha(float alpha) { 
            this.alpha = Math.max(0.0f, Math.min(1.0f, alpha)); 
            this.isTransparent = alpha < 1.0f;
            return this; 
        }
        
        // Texture setters
        public PBRMaterial setAlbedoMap(Texture texture) { 
            this.albedoMap = texture; 
            this.useAlbedoMap = texture != null; 
            return this; 
        }
        
        public PBRMaterial setNormalMap(Texture texture) { 
            this.normalMap = texture; 
            this.useNormalMap = texture != null; 
            return this; 
        }
        
        public PBRMaterial setMetallicRoughnessMap(Texture texture) { 
            this.metallicRoughnessMap = texture; 
            this.useMetallicRoughnessMap = texture != null; 
            return this; 
        }
        
        public PBRMaterial setAOMap(Texture texture) { 
            this.aoMap = texture; 
            this.useAOMap = texture != null; 
            return this; 
        }
        
        public PBRMaterial setEmissiveMap(Texture texture) { 
            this.emissiveMap = texture; 
            this.useEmissiveMap = texture != null; 
            return this; 
        }
        
        public PBRMaterial setHeightMap(Texture texture) { 
            this.heightMap = texture; 
            this.useHeightMap = texture != null; 
            return this; 
        }
        
        // Texture getters
        public Texture getAlbedoMap() { return albedoMap; }
        public Texture getNormalMap() { return normalMap; }
        public Texture getMetallicRoughnessMap() { return metallicRoughnessMap; }
        public Texture getAOMap() { return aoMap; }
        public Texture getEmissiveMap() { return emissiveMap; }
        public Texture getHeightMap() { return heightMap; }
        
        // Flag getters
        public boolean usesAlbedoMap() { return useAlbedoMap; }
        public boolean usesNormalMap() { return useNormalMap; }
        public boolean usesMetallicRoughnessMap() { return useMetallicRoughnessMap; }
        public boolean usesAOMap() { return useAOMap; }
        public boolean usesEmissiveMap() { return useEmissiveMap; }
        public boolean usesHeightMap() { return useHeightMap; }
        public boolean isTransparent() { return isTransparent; }
        public boolean isDoubleSided() { return doubleSided; }
        
        public PBRMaterial setDoubleSided(boolean doubleSided) { this.doubleSided = doubleSided; return this; }
        
        // Advanced property getters/setters
        public float getNormalScale() { return normalScale; }
        public PBRMaterial setNormalScale(float scale) { this.normalScale = Math.max(0.0f, scale); return this; }
        
        public float getHeightScale() { return heightScale; }
        public PBRMaterial setHeightScale(float scale) { this.heightScale = scale; return this; }
        
        public float getEmissiveStrength() { return emissiveStrength; }
        public PBRMaterial setEmissiveStrength(float strength) { this.emissiveStrength = Math.max(0.0f, strength); return this; }
        
        /**
         * Get material variant flags for shader compilation.
         */
        public int getShaderVariantFlags() {
            int flags = 0;
            if (useAlbedoMap) flags |= 1;
            if (useNormalMap) flags |= 2;
            if (useMetallicRoughnessMap) flags |= 4;
            if (useAOMap) flags |= 8;
            if (useEmissiveMap) flags |= 16;
            if (useHeightMap) flags |= 32;
            if (isTransparent) flags |= 64;
            if (doubleSided) flags |= 128;
            return flags;
        }
    }
    
    /**
     * Material template for creating materials with predefined properties.
     */
    public static class MaterialTemplate {
        private final String name;
        private final Vector3f baseAlbedo;
        private final float baseMetallic;
        private final float baseRoughness;
        private final Vector3f baseEmissive;
        
        public MaterialTemplate(String name, Vector3f albedo, float metallic, float roughness) {
            this(name, albedo, metallic, roughness, new Vector3f(0.0f));
        }
        
        public MaterialTemplate(String name, Vector3f albedo, float metallic, float roughness, Vector3f emissive) {
            this.name = name;
            this.baseAlbedo = new Vector3f(albedo);
            this.baseMetallic = metallic;
            this.baseRoughness = roughness;
            this.baseEmissive = new Vector3f(emissive);
        }
        
        public PBRMaterial createMaterial(String id, String materialName) {
            return new PBRMaterial(id, materialName)
                .setAlbedo(baseAlbedo)
                .setMetallic(baseMetallic)
                .setRoughness(baseRoughness)
                .setEmissive(baseEmissive);
        }
        
        public String getName() { return name; }
        public Vector3f getBaseAlbedo() { return new Vector3f(baseAlbedo); }
        public float getBaseMetallic() { return baseMetallic; }
        public float getBaseRoughness() { return baseRoughness; }
        public Vector3f getBaseEmissive() { return new Vector3f(baseEmissive); }
    }
    
    /**
     * Initialize the PBR material system.
     */
    public synchronized void initialize() {
        if (initialized) {
            logManager.warn("PBRMaterialSystem", "Material system already initialized");
            return;
        }
        
        logManager.info("PBRMaterialSystem", "Initializing PBR material system");
        
        // Register default material templates
        registerDefaultTemplates();
        
        // Create default materials
        createDefaultMaterials();
        
        initialized = true;
        
        logManager.info("PBRMaterialSystem", "PBR material system initialized",
                       "templateCount", templates.size(),
                       "materialCount", materials.size());
    }
    
    /**
     * Create a new PBR material.
     */
    public PBRMaterial createMaterial(String id, String name) {
        if (materials.containsKey(id)) {
            logManager.warn("PBRMaterialSystem", "Material already exists", "id", id);
            return materials.get(id);
        }
        
        PBRMaterial material = new PBRMaterial(id, name);
        materials.put(id, material);
        
        metricsCollector.incrementCounter("pbr.materials.created");
        logManager.debug("PBRMaterialSystem", "Material created", "id", id, "name", name);
        
        return material;
    }
    
    /**
     * Create a material from a template.
     */
    public PBRMaterial createMaterialFromTemplate(String id, String name, String templateName) {
        MaterialTemplate template = templates.get(templateName);
        if (template == null) {
            logManager.error("PBRMaterialSystem", "Template not found", "templateName", templateName);
            return createMaterial(id, name);
        }
        
        PBRMaterial material = template.createMaterial(id, name);
        materials.put(id, material);
        
        metricsCollector.incrementCounter("pbr.materials.created");
        logManager.debug("PBRMaterialSystem", "Material created from template", 
                        "id", id, "name", name, "template", templateName);
        
        return material;
    }
    
    /**
     * Get a material by ID.
     */
    public Optional<PBRMaterial> getMaterial(String id) {
        return Optional.ofNullable(materials.get(id));
    }
    
    /**
     * Remove a material.
     */
    public boolean removeMaterial(String id) {
        PBRMaterial removed = materials.remove(id);
        if (removed != null) {
            metricsCollector.incrementCounter("pbr.materials.removed");
            logManager.debug("PBRMaterialSystem", "Material removed", "id", id);
            return true;
        }
        return false;
    }
    
    /**
     * Register a material template.
     */
    public void registerTemplate(String name, MaterialTemplate template) {
        templates.put(name, template);
        logManager.debug("PBRMaterialSystem", "Template registered", "name", name);
    }
    
    /**
     * Get all material IDs.
     */
    public String[] getAllMaterialIds() {
        return materials.keySet().toArray(new String[0]);
    }
    
    /**
     * Get material count.
     */
    public int getMaterialCount() {
        return materials.size();
    }
    
    /**
     * Load material textures asynchronously.
     */
    public void loadMaterialTextures(PBRMaterial material, String basePath) {
        String materialId = material.getId();
        
        // Load textures asynchronously
        if (configManager.getValue("pbr.autoLoadTextures", true)) {
            // Try to load standard PBR texture maps
            loadTextureIfExists(basePath + "_albedo.png").ifPresent(material::setAlbedoMap);
            loadTextureIfExists(basePath + "_normal.png").ifPresent(material::setNormalMap);
            loadTextureIfExists(basePath + "_metallic_roughness.png").ifPresent(material::setMetallicRoughnessMap);
            loadTextureIfExists(basePath + "_ao.png").ifPresent(material::setAOMap);
            loadTextureIfExists(basePath + "_emissive.png").ifPresent(material::setEmissiveMap);
            loadTextureIfExists(basePath + "_height.png").ifPresent(material::setHeightMap);
            
            logManager.debug("PBRMaterialSystem", "Loaded textures for material", 
                           "materialId", materialId, "basePath", basePath);
        }
    }
    
    private Optional<Texture> loadTextureIfExists(String path) {
        try {
            // Check if texture exists and load it
            var asset = assetManager.loadAsset(path);
            if (asset instanceof engine.assets.TextureAsset) {
                return Optional.of(((engine.assets.TextureAsset) asset).getTexture());
            }
        } catch (Exception e) {
            // Texture doesn't exist or failed to load, which is fine
            logManager.debug("PBRMaterialSystem", "Texture not found or failed to load", "path", path);
        }
        return Optional.empty();
    }
    
    private void registerDefaultTemplates() {
        // Metal templates
        registerTemplate("iron", new MaterialTemplate("Iron", 
            new Vector3f(0.56f, 0.57f, 0.58f), 1.0f, 0.1f));
        registerTemplate("gold", new MaterialTemplate("Gold", 
            new Vector3f(1.0f, 0.86f, 0.57f), 1.0f, 0.07f));
        registerTemplate("copper", new MaterialTemplate("Copper", 
            new Vector3f(0.95f, 0.64f, 0.54f), 1.0f, 0.15f));
        registerTemplate("aluminum", new MaterialTemplate("Aluminum", 
            new Vector3f(0.91f, 0.92f, 0.92f), 1.0f, 0.05f));
        
        // Dielectric templates
        registerTemplate("plastic", new MaterialTemplate("Plastic", 
            new Vector3f(0.8f, 0.8f, 0.8f), 0.0f, 0.5f));
        registerTemplate("rubber", new MaterialTemplate("Rubber", 
            new Vector3f(0.2f, 0.2f, 0.2f), 0.0f, 0.9f));
        registerTemplate("wood", new MaterialTemplate("Wood", 
            new Vector3f(0.6f, 0.4f, 0.2f), 0.0f, 0.7f));
        registerTemplate("concrete", new MaterialTemplate("Concrete", 
            new Vector3f(0.7f, 0.7f, 0.7f), 0.0f, 0.8f));
        
        // Glass and transparent materials
        registerTemplate("glass", new MaterialTemplate("Glass", 
            new Vector3f(0.95f, 0.95f, 0.95f), 0.0f, 0.0f));
        
        // Emissive templates
        registerTemplate("emissive_white", new MaterialTemplate("Emissive White", 
            new Vector3f(1.0f, 1.0f, 1.0f), 0.0f, 0.0f, new Vector3f(1.0f, 1.0f, 1.0f)));
        registerTemplate("emissive_red", new MaterialTemplate("Emissive Red", 
            new Vector3f(1.0f, 0.1f, 0.1f), 0.0f, 0.0f, new Vector3f(1.0f, 0.0f, 0.0f)));
        
        logManager.debug("PBRMaterialSystem", "Registered default material templates", 
                        "count", templates.size());
    }
    
    private void createDefaultMaterials() {
        // Create a default material
        createMaterialFromTemplate("default", "Default Material", "plastic");
        
        // Create some basic materials for testing
        createMaterialFromTemplate("metal", "Metal Material", "iron");
        createMaterialFromTemplate("wood", "Wood Material", "wood");
        createMaterialFromTemplate("glass", "Glass Material", "glass")
            .setAlpha(0.3f);
        
        logManager.debug("PBRMaterialSystem", "Created default materials", 
                        "count", materials.size());
    }
    
    /**
     * Shutdown the material system.
     */
    public synchronized void shutdown() {
        if (!initialized) {
            return;
        }
        
        logManager.info("PBRMaterialSystem", "Shutting down PBR material system");
        
        materials.clear();
        templates.clear();
        initialized = false;
        
        logManager.info("PBRMaterialSystem", "PBR material system shutdown complete");
    }
    
    /**
     * Check if the system is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
}