package fps.ballistics;

import org.joml.Vector3f;

/**
 * Represents the result of a hit detection operation.
 * Contains information about what was hit and where.
 */
public class HitResult {
    
    private boolean hit = false;
    private int targetId = -1;
    private Vector3f hitPoint = new Vector3f();
    private Vector3f hitNormal = new Vector3f();
    private HitLocation hitLocation = null;
    private float distance = 0.0f;
    private TargetType targetType = TargetType.NONE;
    private SurfaceMaterial surfaceMaterial = SurfaceMaterial.UNKNOWN;
    private Object hitObject = null;
    
    public HitResult() {
        // Default constructor creates a "no hit" result
    }
    
    public HitResult(boolean hit) {
        this.hit = hit;
    }
    
    /**
     * Create a hit result for a successful hit
     */
    public static HitResult createHit(int targetId, Vector3f hitPoint, Vector3f hitNormal, 
                                     float distance, HitLocation hitLocation, TargetType targetType) {
        HitResult result = new HitResult(true);
        result.targetId = targetId;
        result.hitPoint.set(hitPoint);
        result.hitNormal.set(hitNormal);
        result.distance = distance;
        result.hitLocation = hitLocation;
        result.targetType = targetType;
        return result;
    }
    
    /**
     * Create a miss result
     */
    public static HitResult createMiss() {
        return new HitResult(false);
    }
    
    /**
     * Check if this represents a valid hit
     */
    public boolean isValidHit() {
        return hit && targetType != TargetType.NONE;
    }
    
    /**
     * Check if this hit was on a player
     */
    public boolean isPlayerHit() {
        return hit && targetType == TargetType.PLAYER && targetId >= 0;
    }
    
    /**
     * Check if this was a headshot
     */
    public boolean isHeadshot() {
        return isPlayerHit() && hitLocation == HitLocation.HEAD;
    }
    
    /**
     * Get damage multiplier for this hit location
     */
    public float getDamageMultiplier() {
        return hitLocation != null ? hitLocation.getDamageMultiplier() : 1.0f;
    }
    
    // Getters and setters
    public boolean isHit() { return hit; }
    public void setHit(boolean hit) { this.hit = hit; }
    
    public int getTargetId() { return targetId; }
    public void setTargetId(int targetId) { this.targetId = targetId; }
    
    public Vector3f getHitPoint() { return new Vector3f(hitPoint); }
    public void setHitPoint(Vector3f hitPoint) { this.hitPoint.set(hitPoint); }
    
    public Vector3f getHitNormal() { return new Vector3f(hitNormal); }
    public void setHitNormal(Vector3f hitNormal) { this.hitNormal.set(hitNormal); }
    
    public HitLocation getHitLocation() { return hitLocation; }
    public void setHitLocation(HitLocation hitLocation) { this.hitLocation = hitLocation; }
    
    public float getDistance() { return distance; }
    public void setDistance(float distance) { this.distance = distance; }
    
    public TargetType getTargetType() { return targetType; }
    public void setTargetType(TargetType targetType) { this.targetType = targetType; }
    
    public SurfaceMaterial getSurfaceMaterial() { return surfaceMaterial; }
    public void setSurfaceMaterial(SurfaceMaterial surfaceMaterial) { this.surfaceMaterial = surfaceMaterial; }
    
    public Object getHitObject() { return hitObject; }
    public void setHitObject(Object hitObject) { this.hitObject = hitObject; }
    
    @Override
    public String toString() {
        if (!hit) {
            return "HitResult{miss}";
        }
        
        return String.format("HitResult{hit=true, targetType=%s, targetId=%d, location=%s, distance=%.2f}",
                           targetType, targetId, hitLocation, distance);
    }
    
    /**
     * Types of targets that can be hit
     */
    public enum TargetType {
        NONE,           // No hit
        PLAYER,         // Hit a player
        ENVIRONMENT,    // Hit world geometry
        DESTRUCTIBLE,   // Hit destructible object
        VEHICLE,        // Hit a vehicle
        PROJECTILE      // Hit another projectile
    }
}