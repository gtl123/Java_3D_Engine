package fps.map.geometry;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Defines material properties for surfaces including visual, audio, and gameplay characteristics.
 * Materials determine how bullets interact with surfaces, what sounds are made, and visual effects.
 */
public class MaterialDefinition {
    
    // Basic identification
    private final String materialId;
    private final String displayName;
    private final String description;
    
    // Visual properties
    private final String diffuseTexture;
    private final String normalTexture;
    private final String specularTexture;
    private final String emissiveTexture;
    private final float roughness;
    private final float metallic;
    private final float emissiveStrength;
    
    // Physics properties
    private final float density;
    private final float hardness;
    private final float penetrationResistance;
    private final float bounceFactor;
    private final boolean blocksBullets;
    private final boolean blocksExplosions;
    
    // Audio properties
    private final String soundMaterial;
    private final float soundAbsorption;
    private final float soundReflection;
    private final Map<String, String> impactSounds;
    
    // Gameplay properties
    private final boolean destructible;
    private final float destructionThreshold;
    private final String destructionEffect;
    private final List<String> spawnedDebris;
    
    // Environmental effects
    private final boolean causesRicochet;
    private final String ricochetEffect;
    private final boolean causesSparks;
    private final String sparkEffect;
    private final boolean causesSmoke;
    private final String smokeEffect;
    
    // Performance properties
    private final int renderPriority;
    private final boolean castsShadows;
    private final boolean receivesShadows;
    private final float lodBias;
    
    public MaterialDefinition(Builder builder) {
        this.materialId = builder.materialId;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.diffuseTexture = builder.diffuseTexture;
        this.normalTexture = builder.normalTexture;
        this.specularTexture = builder.specularTexture;
        this.emissiveTexture = builder.emissiveTexture;
        this.roughness = builder.roughness;
        this.metallic = builder.metallic;
        this.emissiveStrength = builder.emissiveStrength;
        this.density = builder.density;
        this.hardness = builder.hardness;
        this.penetrationResistance = builder.penetrationResistance;
        this.bounceFactor = builder.bounceFactor;
        this.blocksBullets = builder.blocksBullets;
        this.blocksExplosions = builder.blocksExplosions;
        this.soundMaterial = builder.soundMaterial;
        this.soundAbsorption = builder.soundAbsorption;
        this.soundReflection = builder.soundReflection;
        this.impactSounds = new HashMap<>(builder.impactSounds);
        this.destructible = builder.destructible;
        this.destructionThreshold = builder.destructionThreshold;
        this.destructionEffect = builder.destructionEffect;
        this.spawnedDebris = new ArrayList<>(builder.spawnedDebris);
        this.causesRicochet = builder.causesRicochet;
        this.ricochetEffect = builder.ricochetEffect;
        this.causesSparks = builder.causesSparks;
        this.sparkEffect = builder.sparkEffect;
        this.causesSmoke = builder.causesSmoke;
        this.smokeEffect = builder.smokeEffect;
        this.renderPriority = builder.renderPriority;
        this.castsShadows = builder.castsShadows;
        this.receivesShadows = builder.receivesShadows;
        this.lodBias = builder.lodBias;
    }
    
    // Getters
    public String getMaterialId() { return materialId; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getDiffuseTexture() { return diffuseTexture; }
    public String getNormalTexture() { return normalTexture; }
    public String getSpecularTexture() { return specularTexture; }
    public String getEmissiveTexture() { return emissiveTexture; }
    public float getRoughness() { return roughness; }
    public float getMetallic() { return metallic; }
    public float getEmissiveStrength() { return emissiveStrength; }
    public float getDensity() { return density; }
    public float getHardness() { return hardness; }
    public float getPenetrationResistance() { return penetrationResistance; }
    public float getBounceFactor() { return bounceFactor; }
    public boolean blocksBullets() { return blocksBullets; }
    public boolean blocksExplosions() { return blocksExplosions; }
    public String getSoundMaterial() { return soundMaterial; }
    public float getSoundAbsorption() { return soundAbsorption; }
    public float getSoundReflection() { return soundReflection; }
    public Map<String, String> getImpactSounds() { return new HashMap<>(impactSounds); }
    public boolean isDestructible() { return destructible; }
    public float getDestructionThreshold() { return destructionThreshold; }
    public String getDestructionEffect() { return destructionEffect; }
    public List<String> getSpawnedDebris() { return new ArrayList<>(spawnedDebris); }
    public boolean causesRicochet() { return causesRicochet; }
    public String getRicochetEffect() { return ricochetEffect; }
    public boolean causesSparks() { return causesSparks; }
    public String getSparkEffect() { return sparkEffect; }
    public boolean causesSmoke() { return causesSmoke; }
    public String getSmokeEffect() { return smokeEffect; }
    public int getRenderPriority() { return renderPriority; }
    public boolean castsShadows() { return castsShadows; }
    public boolean receivesShadows() { return receivesShadows; }
    public float getLodBias() { return lodBias; }
    
    /**
     * Get impact sound for a specific weapon type
     */
    public String getImpactSound(String weaponType) {
        return impactSounds.getOrDefault(weaponType, impactSounds.get("default"));
    }
    
    /**
     * Calculate bullet penetration based on weapon power
     */
    public boolean canPenetrate(float weaponPenetrationPower) {
        return weaponPenetrationPower > penetrationResistance;
    }
    
    /**
     * Calculate damage reduction for penetrating bullets
     */
    public float calculatePenetrationDamageReduction() {
        return Math.min(0.9f, penetrationResistance * 0.1f);
    }
    
    /**
     * Check if material should cause ricochet based on angle
     */
    public boolean shouldRicochet(float impactAngle) {
        if (!causesRicochet) return false;
        
        // Shallow angles more likely to ricochet
        float ricochetThreshold = hardness * 0.1f + 0.1f; // 0.1 to 1.0 range
        return impactAngle < ricochetThreshold;
    }
    
    /**
     * Get the material category for gameplay logic
     */
    public MaterialCategory getCategory() {
        if (metallic > 0.8f) return MaterialCategory.METAL;
        if (hardness > 0.8f) return MaterialCategory.HARD;
        if (hardness < 0.3f) return MaterialCategory.SOFT;
        return MaterialCategory.MEDIUM;
    }
    
    /**
     * Check if this is a transparent material
     */
    public boolean isTransparent() {
        return materialId.contains("glass") || materialId.contains("water");
    }
    
    /**
     * Create a concrete material
     */
    public static MaterialDefinition createConcrete() {
        return new Builder("concrete", "Concrete")
            .description("Standard concrete material")
            .diffuseTexture("textures/concrete_diffuse.png")
            .normalTexture("textures/concrete_normal.png")
            .roughness(0.8f)
            .metallic(0.0f)
            .density(2.4f)
            .hardness(0.8f)
            .penetrationResistance(0.7f)
            .blocksBullets(true)
            .soundMaterial("concrete")
            .impactSound("default", "impact_concrete")
            .causesSparks(false)
            .causesSmoke(true)
            .build();
    }
    
    /**
     * Create a metal material
     */
    public static MaterialDefinition createMetal() {
        return new Builder("metal", "Metal")
            .description("Standard metal material")
            .diffuseTexture("textures/metal_diffuse.png")
            .normalTexture("textures/metal_normal.png")
            .specularTexture("textures/metal_specular.png")
            .roughness(0.2f)
            .metallic(1.0f)
            .density(7.8f)
            .hardness(0.9f)
            .penetrationResistance(0.9f)
            .blocksBullets(true)
            .soundMaterial("metal")
            .impactSound("default", "impact_metal")
            .causesRicochet(true)
            .causesSparks(true)
            .build();
    }
    
    /**
     * Create a glass material
     */
    public static MaterialDefinition createGlass() {
        return new Builder("glass", "Glass")
            .description("Transparent glass material")
            .diffuseTexture("textures/glass_diffuse.png")
            .roughness(0.0f)
            .metallic(0.0f)
            .density(2.5f)
            .hardness(0.3f)
            .penetrationResistance(0.2f)
            .blocksBullets(false)
            .destructible(true)
            .destructionThreshold(10.0f)
            .soundMaterial("glass")
            .impactSound("default", "impact_glass")
            .build();
    }
    
    /**
     * Material categories for gameplay logic
     */
    public enum MaterialCategory {
        SOFT, MEDIUM, HARD, METAL, LIQUID, GAS
    }
    
    /**
     * Builder for MaterialDefinition
     */
    public static class Builder {
        private String materialId;
        private String displayName;
        private String description = "";
        private String diffuseTexture;
        private String normalTexture;
        private String specularTexture;
        private String emissiveTexture;
        private float roughness = 0.5f;
        private float metallic = 0.0f;
        private float emissiveStrength = 0.0f;
        private float density = 1.0f;
        private float hardness = 0.5f;
        private float penetrationResistance = 0.5f;
        private float bounceFactor = 0.0f;
        private boolean blocksBullets = true;
        private boolean blocksExplosions = true;
        private String soundMaterial = "default";
        private float soundAbsorption = 0.5f;
        private float soundReflection = 0.5f;
        private Map<String, String> impactSounds = new HashMap<>();
        private boolean destructible = false;
        private float destructionThreshold = 100.0f;
        private String destructionEffect = "debris";
        private List<String> spawnedDebris = new ArrayList<>();
        private boolean causesRicochet = false;
        private String ricochetEffect = "ricochet_spark";
        private boolean causesSparks = false;
        private String sparkEffect = "impact_spark";
        private boolean causesSmoke = false;
        private String smokeEffect = "impact_smoke";
        private int renderPriority = 0;
        private boolean castsShadows = true;
        private boolean receivesShadows = true;
        private float lodBias = 1.0f;
        
        public Builder(String materialId, String displayName) {
            this.materialId = materialId;
            this.displayName = displayName;
            // Set default impact sound
            this.impactSounds.put("default", "impact_" + materialId);
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder diffuseTexture(String texture) {
            this.diffuseTexture = texture;
            return this;
        }
        
        public Builder normalTexture(String texture) {
            this.normalTexture = texture;
            return this;
        }
        
        public Builder specularTexture(String texture) {
            this.specularTexture = texture;
            return this;
        }
        
        public Builder emissiveTexture(String texture) {
            this.emissiveTexture = texture;
            return this;
        }
        
        public Builder roughness(float roughness) {
            this.roughness = Math.max(0.0f, Math.min(1.0f, roughness));
            return this;
        }
        
        public Builder metallic(float metallic) {
            this.metallic = Math.max(0.0f, Math.min(1.0f, metallic));
            return this;
        }
        
        public Builder emissiveStrength(float strength) {
            this.emissiveStrength = Math.max(0.0f, strength);
            return this;
        }
        
        public Builder density(float density) {
            this.density = Math.max(0.1f, density);
            return this;
        }
        
        public Builder hardness(float hardness) {
            this.hardness = Math.max(0.0f, Math.min(1.0f, hardness));
            return this;
        }
        
        public Builder penetrationResistance(float resistance) {
            this.penetrationResistance = Math.max(0.0f, Math.min(1.0f, resistance));
            return this;
        }
        
        public Builder bounceFactor(float bounce) {
            this.bounceFactor = Math.max(0.0f, Math.min(1.0f, bounce));
            return this;
        }
        
        public Builder blocksBullets(boolean blocks) {
            this.blocksBullets = blocks;
            return this;
        }
        
        public Builder blocksExplosions(boolean blocks) {
            this.blocksExplosions = blocks;
            return this;
        }
        
        public Builder soundMaterial(String material) {
            this.soundMaterial = material;
            return this;
        }
        
        public Builder soundProperties(float absorption, float reflection) {
            this.soundAbsorption = Math.max(0.0f, Math.min(1.0f, absorption));
            this.soundReflection = Math.max(0.0f, Math.min(1.0f, reflection));
            return this;
        }
        
        public Builder impactSound(String weaponType, String soundName) {
            this.impactSounds.put(weaponType, soundName);
            return this;
        }
        
        public Builder destructible(boolean destructible) {
            this.destructible = destructible;
            return this;
        }
        
        public Builder destructionThreshold(float threshold) {
            this.destructionThreshold = Math.max(0.0f, threshold);
            return this;
        }
        
        public Builder destructionEffect(String effect) {
            this.destructionEffect = effect;
            return this;
        }
        
        public Builder addDebris(String debrisType) {
            this.spawnedDebris.add(debrisType);
            return this;
        }
        
        public Builder causesRicochet(boolean causes) {
            this.causesRicochet = causes;
            return this;
        }
        
        public Builder ricochetEffect(String effect) {
            this.ricochetEffect = effect;
            return this;
        }
        
        public Builder causesSparks(boolean causes) {
            this.causesSparks = causes;
            return this;
        }
        
        public Builder sparkEffect(String effect) {
            this.sparkEffect = effect;
            return this;
        }
        
        public Builder causesSmoke(boolean causes) {
            this.causesSmoke = causes;
            return this;
        }
        
        public Builder smokeEffect(String effect) {
            this.smokeEffect = effect;
            return this;
        }
        
        public Builder renderProperties(int priority, boolean castsShadows, boolean receivesShadows, float lodBias) {
            this.renderPriority = priority;
            this.castsShadows = castsShadows;
            this.receivesShadows = receivesShadows;
            this.lodBias = Math.max(0.1f, lodBias);
            return this;
        }
        
        public MaterialDefinition build() {
            if (materialId == null || materialId.isEmpty()) {
                throw new IllegalStateException("Material ID is required");
            }
            if (displayName == null || displayName.isEmpty()) {
                throw new IllegalStateException("Display name is required");
            }
            return new MaterialDefinition(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("MaterialDefinition{id='%s', name='%s', hardness=%.2f, penetration=%.2f}", 
                           materialId, displayName, hardness, penetrationResistance);
    }
}