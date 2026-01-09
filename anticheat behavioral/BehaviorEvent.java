package fps.anticheat.behavioral;

import fps.anticheat.PlayerAction;

/**
 * Represents a behavioral event for analysis.
 * Contains player action data and contextual information for behavioral pattern detection.
 */
public class BehaviorEvent {
    
    private final String playerId;
    private final PlayerAction action;
    private final long timestamp;
    private final String eventType;
    
    private String context;
    private float significance;
    private boolean analyzed;
    
    /**
     * Create a behavior event
     */
    public BehaviorEvent(String playerId, PlayerAction action, long timestamp) {
        this.playerId = playerId;
        this.action = action;
        this.timestamp = timestamp;
        this.eventType = determineEventType(action);
        this.significance = calculateSignificance(action);
        this.analyzed = false;
    }
    
    /**
     * Create a behavior event with context
     */
    public BehaviorEvent(String playerId, PlayerAction action, long timestamp, String context) {
        this(playerId, action, timestamp);
        this.context = context;
    }
    
    /**
     * Determine event type based on action
     */
    private String determineEventType(PlayerAction action) {
        if (action.isShooting()) {
            return "COMBAT_SHOT";
        } else if (action.isAiming()) {
            return "COMBAT_AIM";
        } else if (action.isReloading()) {
            return "COMBAT_RELOAD";
        } else if (action.getVelocity().length() > 0.1f) {
            return "MOVEMENT";
        } else {
            return "GENERAL";
        }
    }
    
    /**
     * Calculate event significance for analysis priority
     */
    private float calculateSignificance(PlayerAction action) {
        float significance = 0.1f; // Base significance
        
        // Combat actions are more significant
        if (action.isShooting()) {
            significance += 0.4f;
        }
        if (action.isAiming()) {
            significance += 0.3f;
        }
        if (action.isReloading()) {
            significance += 0.2f;
        }
        
        // Rapid movements are significant
        float speed = action.getVelocity().length();
        if (speed > 5.0f) {
            significance += Math.min(0.3f, speed / 20.0f);
        }
        
        // Large rotation changes are significant
        float rotationMagnitude = action.getRotation().length();
        if (rotationMagnitude > 10.0f) {
            significance += Math.min(0.3f, rotationMagnitude / 50.0f);
        }
        
        return Math.min(1.0f, significance);
    }
    
    /**
     * Get event age in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - timestamp;
    }
    
    /**
     * Check if event is recent (within specified time)
     */
    public boolean isRecent(long maxAge) {
        return getAge() <= maxAge;
    }
    
    /**
     * Check if event is significant for analysis
     */
    public boolean isSignificant() {
        return significance >= 0.5f;
    }
    
    /**
     * Check if event is combat-related
     */
    public boolean isCombatEvent() {
        return eventType.startsWith("COMBAT_");
    }
    
    /**
     * Check if event is movement-related
     */
    public boolean isMovementEvent() {
        return eventType.equals("MOVEMENT");
    }
    
    /**
     * Mark event as analyzed
     */
    public void markAnalyzed() {
        this.analyzed = true;
    }
    
    /**
     * Create a summary of this event
     */
    public String getSummary() {
        return String.format("BehaviorEvent{player=%s, type=%s, significance=%.2f, age=%dms}",
                           playerId, eventType, significance, getAge());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        BehaviorEvent that = (BehaviorEvent) obj;
        return timestamp == that.timestamp &&
               playerId.equals(that.playerId) &&
               action.equals(that.action);
    }
    
    @Override
    public int hashCode() {
        int result = playerId.hashCode();
        result = 31 * result + action.hashCode();
        result = 31 * result + Long.hashCode(timestamp);
        return result;
    }
    
    @Override
    public String toString() {
        return getSummary();
    }
    
    // Getters and setters
    public String getPlayerId() { return playerId; }
    public PlayerAction getAction() { return action; }
    public long getTimestamp() { return timestamp; }
    public String getEventType() { return eventType; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public float getSignificance() { return significance; }
    public void setSignificance(float significance) { this.significance = Math.max(0.0f, Math.min(1.0f, significance)); }
    public boolean isAnalyzed() { return analyzed; }
    public void setAnalyzed(boolean analyzed) { this.analyzed = analyzed; }
}