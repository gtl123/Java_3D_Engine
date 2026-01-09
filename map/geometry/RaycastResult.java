package fps.map.geometry;

import org.joml.Vector3f;

/**
 * Result of a raycast operation against map geometry.
 * Contains hit information including point, normal, distance, and surface data.
 */
public class RaycastResult {
    
    private final boolean hit;
    private final Vector3f hitPoint;
    private final Vector3f hitNormal;
    private final float distance;
    private final Surface surface;
    private final MaterialDefinition material;
    private final GeometryChunk hitChunk;
    private final String hitObjectId;
    private final int triangleIndex;
    
    // UV coordinates at hit point
    private final float u, v;
    
    public RaycastResult(boolean hit, Vector3f hitPoint, Vector3f hitNormal, float distance,
                        Surface surface, MaterialDefinition material, GeometryChunk hitChunk,
                        String hitObjectId, int triangleIndex, float u, float v) {
        this.hit = hit;
        this.hitPoint = hitPoint != null ? new Vector3f(hitPoint) : null;
        this.hitNormal = hitNormal != null ? new Vector3f(hitNormal) : null;
        this.distance = distance;
        this.surface = surface;
        this.material = material;
        this.hitChunk = hitChunk;
        this.hitObjectId = hitObjectId;
        this.triangleIndex = triangleIndex;
        this.u = u;
        this.v = v;
    }
    
    /**
     * Create a miss result
     */
    public static RaycastResult miss() {
        return new RaycastResult(false, null, null, Float.MAX_VALUE, null, null, null, null, -1, 0, 0);
    }
    
    /**
     * Create a hit result
     */
    public static RaycastResult hit(Vector3f hitPoint, Vector3f hitNormal, float distance,
                                   Surface surface, MaterialDefinition material, GeometryChunk chunk) {
        return new RaycastResult(true, hitPoint, hitNormal, distance, surface, material, chunk, null, -1, 0, 0);
    }
    
    /**
     * Create a detailed hit result
     */
    public static RaycastResult detailedHit(Vector3f hitPoint, Vector3f hitNormal, float distance,
                                          Surface surface, MaterialDefinition material, GeometryChunk chunk,
                                          String objectId, int triangleIndex, float u, float v) {
        return new RaycastResult(true, hitPoint, hitNormal, distance, surface, material, chunk, objectId, triangleIndex, u, v);
    }
    
    // Getters
    public boolean isHit() { return hit; }
    public Vector3f getHitPoint() { return hitPoint != null ? new Vector3f(hitPoint) : null; }
    public Vector3f getHitNormal() { return hitNormal != null ? new Vector3f(hitNormal) : null; }
    public float getDistance() { return distance; }
    public Surface getSurface() { return surface; }
    public MaterialDefinition getMaterial() { return material; }
    public GeometryChunk getHitChunk() { return hitChunk; }
    public String getHitObjectId() { return hitObjectId; }
    public int getTriangleIndex() { return triangleIndex; }
    public float getU() { return u; }
    public float getV() { return v; }
    
    /**
     * Get the surface type at the hit point
     */
    public SurfaceType getSurfaceType() {
        return surface != null ? surface.getSurfaceType() : SurfaceType.UNKNOWN;
    }
    
    /**
     * Check if the hit surface is walkable
     */
    public boolean isWalkable() {
        return surface != null && surface.isWalkable();
    }
    
    /**
     * Check if the hit surface blocks bullets
     */
    public boolean blocksBullets() {
        return material != null && material.blocksBullets();
    }
    
    /**
     * Get the penetration resistance of the hit surface
     */
    public float getPenetrationResistance() {
        return material != null ? material.getPenetrationResistance() : 1.0f;
    }
    
    /**
     * Get the bounce factor for projectiles
     */
    public float getBounceFactor() {
        return material != null ? material.getBounceFactor() : 0.0f;
    }
    
    /**
     * Get the friction coefficient of the surface
     */
    public float getFriction() {
        return surface != null ? surface.getFriction() : 0.7f;
    }
    
    /**
     * Get the sound material for audio effects
     */
    public String getSoundMaterial() {
        return material != null ? material.getSoundMaterial() : "default";
    }
    
    /**
     * Calculate reflected ray direction
     */
    public Vector3f getReflectedDirection(Vector3f incomingDirection) {
        if (hitNormal == null) {
            return null;
        }
        
        Vector3f reflected = new Vector3f();
        Vector3f normal = new Vector3f(hitNormal);
        
        // R = I - 2 * (I · N) * N
        float dot = incomingDirection.dot(normal);
        reflected.set(incomingDirection).sub(normal.mul(2 * dot));
        
        return reflected;
    }
    
    /**
     * Check if this hit is closer than another hit
     */
    public boolean isCloserThan(RaycastResult other) {
        if (!this.hit) return false;
        if (!other.hit) return true;
        return this.distance < other.distance;
    }
    
    @Override
    public String toString() {
        if (!hit) {
            return "RaycastResult{miss}";
        }
        
        return String.format("RaycastResult{hit=true, point=%s, distance=%.2f, surface=%s}", 
                           hitPoint, distance, surface != null ? surface.getSurfaceType() : "null");
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        RaycastResult that = (RaycastResult) obj;
        return hit == that.hit &&
               Float.compare(that.distance, distance) == 0 &&
               java.util.Objects.equals(hitPoint, that.hitPoint) &&
               java.util.Objects.equals(hitNormal, that.hitNormal);
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(hit, hitPoint, hitNormal, distance);
    }
}