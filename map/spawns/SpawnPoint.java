package fps.map.spawns;

import org.joml.Vector3f;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents a spawn point in the map with position, orientation, and gameplay properties.
 * Supports team-based spawning, game mode restrictions, and competitive balancing.
 */
public class SpawnPoint {
    
    // Basic identification
    private final String spawnId;
    private final String displayName;
    private final String description;
    
    // Position and orientation
    private final Vector3f position;
    private final Vector3f rotation; // Euler angles (pitch, yaw, roll)
    private final Vector3f forward;  // Calculated forward direction
    
    // Team and game mode assignment
    private final String teamId;
    private final Set<String> supportedGameModes;
    private final SpawnType spawnType;
    
    // Gameplay properties
    private final int priority;
    private final float safetyRadius;
    private final boolean protectedSpawn;
    private final float protectionTime;
    
    // Validation and balancing
    private final SpawnConstraints constraints;
    private final List<String> nearbyCallouts;
    
    // Runtime state
    private boolean enabled;
    private long lastUsedTime;
    private int usageCount;
    private final SpawnMetrics metrics;
    
    public SpawnPoint(Builder builder) {
        this.spawnId = builder.spawnId;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.position = new Vector3f(builder.position);
        this.rotation = new Vector3f(builder.rotation);
        this.forward = calculateForwardDirection(builder.rotation);
        this.teamId = builder.teamId;
        this.supportedGameModes = new HashSet<>(builder.supportedGameModes);
        this.spawnType = builder.spawnType;
        this.priority = builder.priority;
        this.safetyRadius = builder.safetyRadius;
        this.protectedSpawn = builder.protectedSpawn;
        this.protectionTime = builder.protectionTime;
        this.constraints = builder.constraints;
        this.nearbyCallouts = new ArrayList<>(builder.nearbyCallouts);
        this.enabled = builder.enabled;
        this.lastUsedTime = 0;
        this.usageCount = 0;
        this.metrics = new SpawnMetrics();
    }
    
    // Getters
    public String getSpawnId() { return spawnId; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Vector3f getPosition() { return new Vector3f(position); }
    public Vector3f getRotation() { return new Vector3f(rotation); }
    public Vector3f getForward() { return new Vector3f(forward); }
    public String getTeamId() { return teamId; }
    public Set<String> getSupportedGameModes() { return new HashSet<>(supportedGameModes); }
    public SpawnType getSpawnType() { return spawnType; }
    public int getPriority() { return priority; }
    public float getSafetyRadius() { return safetyRadius; }
    public boolean isProtectedSpawn() { return protectedSpawn; }
    public float getProtectionTime() { return protectionTime; }
    public SpawnConstraints getConstraints() { return constraints; }
    public List<String> getNearbyCallouts() { return new ArrayList<>(nearbyCallouts); }
    public boolean isEnabled() { return enabled; }
    public long getLastUsedTime() { return lastUsedTime; }
    public int getUsageCount() { return usageCount; }
    public SpawnMetrics getMetrics() { return metrics; }
    
    /**
     * Check if this spawn point supports a specific game mode
     */
    public boolean supportsGameMode(String gameMode) {
        return supportedGameModes.isEmpty() || supportedGameModes.contains(gameMode);
    }
    
    /**
     * Check if this spawn point is available for use
     */
    public boolean isAvailable() {
        return enabled && !isOnCooldown();
    }
    
    /**
     * Check if spawn point is on cooldown
     */
    public boolean isOnCooldown() {
        if (constraints == null) return false;
        
        long cooldownTime = constraints.getCooldownTime();
        if (cooldownTime <= 0) return false;
        
        return (System.currentTimeMillis() - lastUsedTime) < cooldownTime;
    }
    
    /**
     * Get remaining cooldown time in milliseconds
     */
    public long getRemainingCooldown() {
        if (!isOnCooldown()) return 0;
        
        long cooldownTime = constraints.getCooldownTime();
        long elapsed = System.currentTimeMillis() - lastUsedTime;
        return Math.max(0, cooldownTime - elapsed);
    }
    
    /**
     * Calculate distance to another position
     */
    public float distanceTo(Vector3f otherPosition) {
        return position.distance(otherPosition);
    }
    
    /**
     * Calculate distance to another spawn point
     */
    public float distanceTo(SpawnPoint otherSpawn) {
        return distanceTo(otherSpawn.getPosition());
    }
    
    /**
     * Check if position is within safety radius
     */
    public boolean isWithinSafetyRadius(Vector3f testPosition) {
        return distanceTo(testPosition) <= safetyRadius;
    }
    
    /**
     * Get spawn score based on various factors
     */
    public float calculateSpawnScore(SpawnScoreContext context) {
        float score = priority; // Base score from priority
        
        // Distance from enemies (higher is better)
        if (context.getNearestEnemyDistance() > 0) {
            score += Math.min(50, context.getNearestEnemyDistance() * 2);
        }
        
        // Distance from teammates (moderate distance is better)
        float teammateDistance = context.getNearestTeammateDistance();
        if (teammateDistance > 5 && teammateDistance < 20) {
            score += 20;
        }
        
        // Recent usage penalty
        long timeSinceLastUse = System.currentTimeMillis() - lastUsedTime;
        if (timeSinceLastUse < 30000) { // 30 seconds
            score -= 30;
        }
        
        // Usage frequency penalty
        if (usageCount > context.getAverageUsageCount()) {
            score -= (usageCount - context.getAverageUsageCount()) * 5;
        }
        
        // Safety bonus
        if (protectedSpawn) {
            score += 15;
        }
        
        return Math.max(0, score);
    }
    
    /**
     * Mark spawn point as used
     */
    public void markAsUsed() {
        lastUsedTime = System.currentTimeMillis();
        usageCount++;
        metrics.recordUsage();
    }
    
    /**
     * Set enabled state
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Reset usage statistics
     */
    public void resetUsageStats() {
        lastUsedTime = 0;
        usageCount = 0;
        metrics.reset();
    }
    
    /**
     * Calculate forward direction from rotation
     */
    private Vector3f calculateForwardDirection(Vector3f rotation) {
        float yaw = (float) Math.toRadians(rotation.y);
        float pitch = (float) Math.toRadians(rotation.x);
        
        return new Vector3f(
            (float) (Math.cos(pitch) * Math.sin(yaw)),
            (float) (-Math.sin(pitch)),
            (float) (Math.cos(pitch) * Math.cos(yaw))
        );
    }
    
    /**
     * Spawn point types
     */
    public enum SpawnType {
        TEAM_SPAWN("Team Spawn", "Standard team spawn point"),
        NEUTRAL_SPAWN("Neutral Spawn", "Neutral spawn point for all teams"),
        INITIAL_SPAWN("Initial Spawn", "Round start spawn point"),
        RESPAWN_POINT("Respawn Point", "Mid-round respawn location"),
        OBJECTIVE_SPAWN("Objective Spawn", "Spawn near objectives"),
        FALLBACK_SPAWN("Fallback Spawn", "Emergency fallback spawn"),
        VIP_SPAWN("VIP Spawn", "Special spawn for VIP players"),
        SPECTATOR_SPAWN("Spectator Spawn", "Spawn for spectators");
        
        private final String displayName;
        private final String description;
        
        SpawnType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    /**
     * Builder for SpawnPoint
     */
    public static class Builder {
        private String spawnId;
        private String displayName;
        private String description = "";
        private Vector3f position;
        private Vector3f rotation = new Vector3f(0, 0, 0);
        private String teamId;
        private Set<String> supportedGameModes = new HashSet<>();
        private SpawnType spawnType = SpawnType.TEAM_SPAWN;
        private int priority = 100;
        private float safetyRadius = 5.0f;
        private boolean protectedSpawn = false;
        private float protectionTime = 3.0f;
        private SpawnConstraints constraints;
        private List<String> nearbyCallouts = new ArrayList<>();
        private boolean enabled = true;
        
        public Builder(String spawnId, Vector3f position) {
            this.spawnId = spawnId;
            this.position = new Vector3f(position);
        }
        
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder rotation(Vector3f rotation) {
            this.rotation = new Vector3f(rotation);
            return this;
        }
        
        public Builder rotation(float pitch, float yaw, float roll) {
            this.rotation = new Vector3f(pitch, yaw, roll);
            return this;
        }
        
        public Builder teamId(String teamId) {
            this.teamId = teamId;
            return this;
        }
        
        public Builder addSupportedGameMode(String gameMode) {
            this.supportedGameModes.add(gameMode);
            return this;
        }
        
        public Builder spawnType(SpawnType spawnType) {
            this.spawnType = spawnType;
            return this;
        }
        
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }
        
        public Builder safetyRadius(float radius) {
            this.safetyRadius = Math.max(0, radius);
            return this;
        }
        
        public Builder protectedSpawn(boolean protected, float protectionTime) {
            this.protectedSpawn = protected;
            this.protectionTime = protectionTime;
            return this;
        }
        
        public Builder constraints(SpawnConstraints constraints) {
            this.constraints = constraints;
            return this;
        }
        
        public Builder addNearbyCallout(String callout) {
            this.nearbyCallouts.add(callout);
            return this;
        }
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public SpawnPoint build() {
            if (spawnId == null || spawnId.isEmpty()) {
                throw new IllegalStateException("Spawn ID is required");
            }
            if (position == null) {
                throw new IllegalStateException("Position is required");
            }
            if (displayName == null) {
                displayName = spawnId;
            }
            
            return new SpawnPoint(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("SpawnPoint{id='%s', team='%s', type=%s, position=%s}", 
                           spawnId, teamId, spawnType, position);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        SpawnPoint that = (SpawnPoint) obj;
        return spawnId.equals(that.spawnId);
    }
    
    @Override
    public int hashCode() {
        return spawnId.hashCode();
    }
}