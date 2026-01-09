package fps.map.geometry;

import org.joml.Vector3f;

/**
 * Represents a surface in the map with gameplay properties like friction,
 * walkability, and surface type for audio and visual effects.
 */
public class Surface {
    
    private final SurfaceType surfaceType;
    private final float friction;
    private final float bounciness;
    private final boolean walkable;
    private final boolean climbable;
    private final float movementSpeedMultiplier;
    private final String soundMaterial;
    private final Vector3f normal;
    private final float area;
    
    // Visual properties
    private final String textureName;
    private final float textureScale;
    private final Vector3f textureOffset;
    
    // Gameplay properties
    private final boolean causesFootsteps;
    private final float footstepVolume;
    private final boolean causesParticles;
    private final String particleEffect;
    
    public Surface(Builder builder) {
        this.surfaceType = builder.surfaceType;
        this.friction = builder.friction;
        this.bounciness = builder.bounciness;
        this.walkable = builder.walkable;
        this.climbable = builder.climbable;
        this.movementSpeedMultiplier = builder.movementSpeedMultiplier;
        this.soundMaterial = builder.soundMaterial;
        this.normal = builder.normal != null ? new Vector3f(builder.normal) : new Vector3f(0, 1, 0);
        this.area = builder.area;
        this.textureName = builder.textureName;
        this.textureScale = builder.textureScale;
        this.textureOffset = builder.textureOffset != null ? new Vector3f(builder.textureOffset) : new Vector3f();
        this.causesFootsteps = builder.causesFootsteps;
        this.footstepVolume = builder.footstepVolume;
        this.causesParticles = builder.causesParticles;
        this.particleEffect = builder.particleEffect;
    }
    
    // Getters
    public SurfaceType getSurfaceType() { return surfaceType; }
    public float getFriction() { return friction; }
    public float getBounciness() { return bounciness; }
    public boolean isWalkable() { return walkable; }
    public boolean isClimbable() { return climbable; }
    public float getMovementSpeedMultiplier() { return movementSpeedMultiplier; }
    public String getSoundMaterial() { return soundMaterial; }
    public Vector3f getNormal() { return new Vector3f(normal); }
    public float getArea() { return area; }
    public String getTextureName() { return textureName; }
    public float getTextureScale() { return textureScale; }
    public Vector3f getTextureOffset() { return new Vector3f(textureOffset); }
    public boolean causesFootsteps() { return causesFootsteps; }
    public float getFootstepVolume() { return footstepVolume; }
    public boolean causesParticles() { return causesParticles; }
    public String getParticleEffect() { return particleEffect; }
    
    /**
     * Check if this surface is suitable for player movement
     */
    public boolean isSuitableForMovement() {
        return walkable && friction > 0.1f;
    }
    
    /**
     * Check if this surface should generate sound effects
     */
    public boolean shouldGenerateSound() {
        return causesFootsteps && footstepVolume > 0.0f;
    }
    
    /**
     * Get the effective movement speed on this surface
     */
    public float getEffectiveMovementSpeed(float baseSpeed) {
        return baseSpeed * movementSpeedMultiplier;
    }
    
    /**
     * Check if the surface normal indicates a slope
     */
    public boolean isSloped(float maxSlopeAngle) {
        float angle = (float) Math.acos(normal.y);
        return Math.toDegrees(angle) > maxSlopeAngle;
    }
    
    /**
     * Get the slope angle in degrees
     */
    public float getSlopeAngle() {
        return (float) Math.toDegrees(Math.acos(normal.y));
    }
    
    /**
     * Create a default floor surface
     */
    public static Surface createDefaultFloor() {
        return new Builder(SurfaceType.CONCRETE)
            .friction(0.7f)
            .walkable(true)
            .soundMaterial("concrete")
            .build();
    }
    
    /**
     * Create a default wall surface
     */
    public static Surface createDefaultWall() {
        return new Builder(SurfaceType.CONCRETE)
            .friction(0.8f)
            .walkable(false)
            .normal(new Vector3f(1, 0, 0))
            .soundMaterial("concrete")
            .build();
    }
    
    /**
     * Builder for Surface
     */
    public static class Builder {
        private SurfaceType surfaceType;
        private float friction = 0.7f;
        private float bounciness = 0.0f;
        private boolean walkable = true;
        private boolean climbable = false;
        private float movementSpeedMultiplier = 1.0f;
        private String soundMaterial = "default";
        private Vector3f normal = new Vector3f(0, 1, 0);
        private float area = 1.0f;
        private String textureName = "default";
        private float textureScale = 1.0f;
        private Vector3f textureOffset = new Vector3f();
        private boolean causesFootsteps = true;
        private float footstepVolume = 1.0f;
        private boolean causesParticles = false;
        private String particleEffect = "dust";
        
        public Builder(SurfaceType surfaceType) {
            this.surfaceType = surfaceType;
        }
        
        public Builder friction(float friction) {
            this.friction = Math.max(0.0f, Math.min(1.0f, friction));
            return this;
        }
        
        public Builder bounciness(float bounciness) {
            this.bounciness = Math.max(0.0f, Math.min(1.0f, bounciness));
            return this;
        }
        
        public Builder walkable(boolean walkable) {
            this.walkable = walkable;
            return this;
        }
        
        public Builder climbable(boolean climbable) {
            this.climbable = climbable;
            return this;
        }
        
        public Builder movementSpeedMultiplier(float multiplier) {
            this.movementSpeedMultiplier = Math.max(0.0f, multiplier);
            return this;
        }
        
        public Builder soundMaterial(String soundMaterial) {
            this.soundMaterial = soundMaterial;
            return this;
        }
        
        public Builder normal(Vector3f normal) {
            this.normal = new Vector3f(normal).normalize();
            return this;
        }
        
        public Builder area(float area) {
            this.area = Math.max(0.0f, area);
            return this;
        }
        
        public Builder texture(String textureName, float scale, Vector3f offset) {
            this.textureName = textureName;
            this.textureScale = scale;
            this.textureOffset = offset != null ? new Vector3f(offset) : new Vector3f();
            return this;
        }
        
        public Builder footsteps(boolean causes, float volume) {
            this.causesFootsteps = causes;
            this.footstepVolume = Math.max(0.0f, Math.min(1.0f, volume));
            return this;
        }
        
        public Builder particles(boolean causes, String effect) {
            this.causesParticles = causes;
            this.particleEffect = effect;
            return this;
        }
        
        public Surface build() {
            return new Surface(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("Surface{type=%s, friction=%.2f, walkable=%s}", 
                           surfaceType, friction, walkable);
    }
}

/**
 * Enumeration of surface types for gameplay and audio/visual effects
 */
enum SurfaceType {
    // Hard surfaces
    CONCRETE("Concrete", "concrete", 0.7f, 0.1f),
    METAL("Metal", "metal", 0.6f, 0.2f),
    STONE("Stone", "stone", 0.8f, 0.05f),
    BRICK("Brick", "brick", 0.75f, 0.1f),
    TILE("Tile", "tile", 0.5f, 0.15f),
    
    // Soft surfaces
    DIRT("Dirt", "dirt", 0.9f, 0.0f),
    GRASS("Grass", "grass", 0.85f, 0.0f),
    SAND("Sand", "sand", 0.95f, 0.0f),
    MUD("Mud", "mud", 1.2f, 0.0f),
    SNOW("Snow", "snow", 1.1f, 0.0f),
    
    // Liquid surfaces
    WATER("Water", "water", 0.3f, 0.0f),
    ICE("Ice", "ice", 0.1f, 0.3f),
    
    // Special surfaces
    GLASS("Glass", "glass", 0.4f, 0.4f),
    WOOD("Wood", "wood", 0.8f, 0.1f),
    CARPET("Carpet", "carpet", 1.0f, 0.0f),
    RUBBER("Rubber", "rubber", 1.2f, 0.8f),
    
    // Unknown/default
    UNKNOWN("Unknown", "default", 0.7f, 0.0f);
    
    private final String displayName;
    private final String soundMaterial;
    private final float defaultFriction;
    private final float defaultBounciness;
    
    SurfaceType(String displayName, String soundMaterial, float defaultFriction, float defaultBounciness) {
        this.displayName = displayName;
        this.soundMaterial = soundMaterial;
        this.defaultFriction = defaultFriction;
        this.defaultBounciness = defaultBounciness;
    }
    
    public String getDisplayName() { return displayName; }
    public String getSoundMaterial() { return soundMaterial; }
    public float getDefaultFriction() { return defaultFriction; }
    public float getDefaultBounciness() { return defaultBounciness; }
}