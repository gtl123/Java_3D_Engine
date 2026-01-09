package fps.ballistics;

import engine.logging.LogManager;
import engine.physics.AABB;
import fps.physics.MovementPhysics;
import fps.weapon.WeaponDefinition;
import org.joml.Vector3f;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central ballistics system for handling projectiles, hit detection, and damage calculation.
 * Supports both hitscan and projectile-based weapons with lag compensation.
 */
public class BallisticsSystem {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static BallisticsSystem instance;
    
    // Active projectiles
    private final Map<Integer, Projectile> activeProjectiles = new ConcurrentHashMap<>();
    private final AtomicInteger nextProjectileId = new AtomicInteger(1);
    
    // Hit registration components
    private final HitRegistrationSystem hitRegistration;
    private final LagCompensationSystem lagCompensation;
    private final DamageSystem damageSystem;
    
    // Physics integration
    private MovementPhysics movementPhysics;
    
    // Performance settings
    private int maxActiveProjectiles = 1000;
    private float projectileCleanupDistance = 1000.0f;
    private float projectileMaxLifetime = 10.0f;
    
    // Initialization flag
    private boolean initialized = false;
    
    private BallisticsSystem() {
        this.hitRegistration = new HitRegistrationSystem();
        this.lagCompensation = new LagCompensationSystem();
        this.damageSystem = new DamageSystem();
        
        logManager.info("BallisticsSystem", "Ballistics system created");
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized BallisticsSystem getInstance() {
        if (instance == null) {
            instance = new BallisticsSystem();
        }
        return instance;
    }
    
    /**
     * Initialize the ballistics system
     */
    public void initialize() throws Exception {
        if (initialized) {
            return;
        }
        
        logManager.info("BallisticsSystem", "Initializing Ballistics System");
        
        try {
            // Initialize subsystems
            hitRegistration.initialize();
            lagCompensation.initialize();
            damageSystem.initialize();
            
            // Initialize physics integration
            movementPhysics = new MovementPhysics();
            
            initialized = true;
            
            logManager.info("BallisticsSystem", "Ballistics System initialization complete");
            
        } catch (Exception e) {
            logManager.error("BallisticsSystem", "Failed to initialize Ballistics System", e);
            throw e;
        }
    }
    
    /**
     * Update ballistics system
     */
    public void update(float deltaTime) {
        if (!initialized) return;
        
        try {
            // Update all active projectiles
            updateProjectiles(deltaTime);
            
            // Update subsystems
            lagCompensation.update(deltaTime);
            damageSystem.update(deltaTime);
            
            // Cleanup old projectiles
            cleanupProjectiles();
            
        } catch (Exception e) {
            logManager.error("BallisticsSystem", "Error updating ballistics system", e);
        }
    }
    
    /**
     * Perform hitscan hit detection
     */
    public HitResult performHitscan(Vector3f origin, Vector3f direction, float maxRange, int shooterId) {
        return performHitscan(origin, direction, maxRange, shooterId, System.currentTimeMillis());
    }
    
    /**
     * Perform hitscan hit detection with timestamp for lag compensation
     */
    public HitResult performHitscan(Vector3f origin, Vector3f direction, float maxRange, int shooterId, long timestamp) {
        // Apply lag compensation
        lagCompensation.rewindWorld(timestamp);
        
        try {
            // Perform raycast
            MovementPhysics.RaycastResult raycastResult = movementPhysics.raycast(origin, direction, maxRange);
            
            HitResult result = new HitResult();
            result.setHit(raycastResult.hit);
            
            if (raycastResult.hit) {
                result.setHitPoint(raycastResult.hitPoint);
                result.setHitNormal(raycastResult.normal);
                result.setDistance(raycastResult.distance);
                
                // Determine what was hit
                if (raycastResult.hitObject instanceof PlayerHitbox) {
                    PlayerHitbox hitbox = (PlayerHitbox) raycastResult.hitObject;
                    result.setTargetId(hitbox.getPlayerId());
                    result.setHitLocation(hitbox.getHitLocation(raycastResult.hitPoint));
                    result.setTargetType(HitResult.TargetType.PLAYER);
                } else {
                    result.setTargetType(HitResult.TargetType.ENVIRONMENT);
                }
                
                // Calculate surface material
                result.setSurfaceMaterial(determineSurfaceMaterial(raycastResult.hitPoint));
            }
            
            return result;
            
        } finally {
            // Restore world state
            lagCompensation.restoreWorld();
        }
    }
    
    /**
     * Create a projectile
     */
    public void createProjectile(Vector3f origin, Vector3f velocity, WeaponDefinition weapon, int shooterId) {
        if (activeProjectiles.size() >= maxActiveProjectiles) {
            logManager.warn("BallisticsSystem", "Maximum projectiles reached, cannot create new projectile");
            return;
        }
        
        int projectileId = nextProjectileId.getAndIncrement();
        
        Projectile projectile = new Projectile(
            projectileId,
            new Vector3f(origin),
            new Vector3f(velocity),
            weapon,
            shooterId,
            System.currentTimeMillis() / 1000.0f
        );
        
        activeProjectiles.put(projectileId, projectile);
        
        logManager.debug("BallisticsSystem", "Projectile created",
                        "projectileId", projectileId,
                        "shooterId", shooterId,
                        "weaponId", weapon.getWeaponId());
    }
    
    /**
     * Update all active projectiles
     */
    private void updateProjectiles(float deltaTime) {
        List<Integer> projectilesToRemove = new ArrayList<>();
        
        for (Projectile projectile : activeProjectiles.values()) {
            // Update projectile physics
            projectile.update(deltaTime);
            
            // Check for collisions
            HitResult hitResult = checkProjectileCollision(projectile);
            
            if (hitResult.isHit()) {
                // Handle projectile hit
                handleProjectileHit(projectile, hitResult);
                projectilesToRemove.add(projectile.getId());
            } else if (projectile.shouldDestroy()) {
                // Remove projectile if it should be destroyed
                projectilesToRemove.add(projectile.getId());
            }
        }
        
        // Remove destroyed projectiles
        for (int projectileId : projectilesToRemove) {
            activeProjectiles.remove(projectileId);
        }
    }
    
    /**
     * Check collision for a projectile
     */
    private HitResult checkProjectileCollision(Projectile projectile) {
        Vector3f currentPos = projectile.getPosition();
        Vector3f previousPos = projectile.getPreviousPosition();
        
        // Calculate movement vector
        Vector3f movement = new Vector3f(currentPos).sub(previousPos);
        float distance = movement.length();
        
        if (distance > 0) {
            Vector3f direction = movement.normalize();
            
            // Perform raycast from previous to current position
            MovementPhysics.RaycastResult raycastResult = movementPhysics.raycast(previousPos, direction, distance);
            
            if (raycastResult.hit) {
                HitResult result = new HitResult();
                result.setHit(true);
                result.setHitPoint(raycastResult.hitPoint);
                result.setHitNormal(raycastResult.normal);
                result.setDistance(raycastResult.distance);
                
                // Determine what was hit
                if (raycastResult.hitObject instanceof PlayerHitbox) {
                    PlayerHitbox hitbox = (PlayerHitbox) raycastResult.hitObject;
                    result.setTargetId(hitbox.getPlayerId());
                    result.setHitLocation(hitbox.getHitLocation(raycastResult.hitPoint));
                    result.setTargetType(HitResult.TargetType.PLAYER);
                } else {
                    result.setTargetType(HitResult.TargetType.ENVIRONMENT);
                }
                
                result.setSurfaceMaterial(determineSurfaceMaterial(raycastResult.hitPoint));
                return result;
            }
        }
        
        return new HitResult(); // No hit
    }
    
    /**
     * Handle projectile hit
     */
    private void handleProjectileHit(Projectile projectile, HitResult hitResult) {
        if (hitResult.getTargetType() == HitResult.TargetType.PLAYER) {
            // Calculate damage
            float damage = calculateProjectileDamage(projectile, hitResult);
            
            // Apply damage
            applyDamage(hitResult.getTargetId(), damage, projectile.getShooterId(), 
                       projectile.getWeapon().getWeaponId());
        }
        
        // Create impact effects
        createImpactEffects(hitResult.getHitPoint(), hitResult.getHitNormal(), 
                          hitResult.getSurfaceMaterial());
        
        logManager.debug("BallisticsSystem", "Projectile hit",
                        "projectileId", projectile.getId(),
                        "targetType", hitResult.getTargetType(),
                        "targetId", hitResult.getTargetId());
    }
    
    /**
     * Calculate damage for a projectile hit
     */
    private float calculateProjectileDamage(Projectile projectile, HitResult hitResult) {
        WeaponDefinition weapon = projectile.getWeapon();
        float distance = projectile.getDistanceTraveled();
        
        // Base damage with distance falloff
        float damage = weapon.getEffectiveDamage(distance);
        
        // Apply hit location multiplier
        if (hitResult.getHitLocation() != null) {
            damage *= hitResult.getHitLocation().getDamageMultiplier();
            
            // Apply headshot multiplier
            if (hitResult.getHitLocation() == HitLocation.HEAD) {
                damage *= weapon.getHeadshotMultiplier();
            }
        }
        
        return damage;
    }
    
    /**
     * Apply damage to a target
     */
    public void applyDamage(int targetId, float damage, int attackerId, String weaponId) {
        damageSystem.applyDamage(targetId, damage, attackerId, weaponId);
    }
    
    /**
     * Cleanup old projectiles
     */
    private void cleanupProjectiles() {
        float currentTime = System.currentTimeMillis() / 1000.0f;
        List<Integer> projectilesToRemove = new ArrayList<>();
        
        for (Projectile projectile : activeProjectiles.values()) {
            // Remove projectiles that are too old or too far
            if (currentTime - projectile.getCreationTime() > projectileMaxLifetime ||
                projectile.getDistanceTraveled() > projectileCleanupDistance) {
                projectilesToRemove.add(projectile.getId());
            }
        }
        
        for (int projectileId : projectilesToRemove) {
            activeProjectiles.remove(projectileId);
        }
    }
    
    /**
     * Determine surface material at a point
     */
    private SurfaceMaterial determineSurfaceMaterial(Vector3f point) {
        // This would integrate with the world/map system to determine material
        // For now, return a default material
        return SurfaceMaterial.CONCRETE;
    }
    
    /**
     * Create impact effects
     */
    private void createImpactEffects(Vector3f hitPoint, Vector3f normal, SurfaceMaterial material) {
        // This would create visual and audio effects for bullet impacts
        // Integration with effects system would go here
    }
    
    /**
     * Check line of sight between two points
     */
    public boolean hasLineOfSight(Vector3f from, Vector3f to, int ignoredEntityId) {
        return movementPhysics.hasLineOfSight(from, to);
    }
    
    /**
     * Get all active projectiles (for rendering/debugging)
     */
    public List<Projectile> getActiveProjectiles() {
        return new ArrayList<>(activeProjectiles.values());
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        logManager.info("BallisticsSystem", "Cleaning up Ballistics System");
        
        try {
            activeProjectiles.clear();
            
            if (hitRegistration != null) {
                hitRegistration.cleanup();
            }
            if (lagCompensation != null) {
                lagCompensation.cleanup();
            }
            if (damageSystem != null) {
                damageSystem.cleanup();
            }
            
            initialized = false;
            
            logManager.info("BallisticsSystem", "Ballistics System cleanup complete");
            
        } catch (Exception e) {
            logManager.error("BallisticsSystem", "Error during cleanup", e);
        }
    }
    
    // Getters and setters
    public boolean isInitialized() { return initialized; }
    public int getActiveProjectileCount() { return activeProjectiles.size(); }
    public int getMaxActiveProjectiles() { return maxActiveProjectiles; }
    public void setMaxActiveProjectiles(int max) { this.maxActiveProjectiles = max; }
    public float getProjectileCleanupDistance() { return projectileCleanupDistance; }
    public void setProjectileCleanupDistance(float distance) { this.projectileCleanupDistance = distance; }
    public float getProjectileMaxLifetime() { return projectileMaxLifetime; }
    public void setProjectileMaxLifetime(float lifetime) { this.projectileMaxLifetime = lifetime; }
    
    public HitRegistrationSystem getHitRegistration() { return hitRegistration; }
    public LagCompensationSystem getLagCompensation() { return lagCompensation; }
    public DamageSystem getDamageSystem() { return damageSystem; }
}