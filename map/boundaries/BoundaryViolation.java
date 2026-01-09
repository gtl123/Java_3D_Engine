package fps.map.boundaries;

import fps.game.Player;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an active boundary violation by a player.
 * Tracks timing, severity, and action execution state.
 */
public class BoundaryViolation {
    private final Player player;
    private final MapBoundary boundary;
    private final long startTime;
    private final Map<String, Object> properties;
    
    private float duration;
    private BoundarySeverity currentSeverity;
    private boolean actionExecuted;
    private boolean warningShown;
    private long lastWarningTime;
    private long lastActionTime;
    private int violationCount;
    
    public BoundaryViolation(Player player, MapBoundary boundary, long startTime) {
        this.player = player;
        this.boundary = boundary;
        this.startTime = startTime;
        this.properties = new HashMap<>();
        this.duration = 0.0f;
        this.currentSeverity = BoundarySeverity.LOW;
        this.actionExecuted = false;
        this.warningShown = false;
        this.lastWarningTime = 0;
        this.lastActionTime = 0;
        this.violationCount = 1;
    }
    
    /**
     * Update the violation state
     */
    public void update(float deltaTime) {
        duration += deltaTime;
        
        // Update severity based on current player position
        currentSeverity = boundary.getSeverity(player.getPosition());
        
        // Check if warning should be shown
        if (shouldShowWarning()) {
            lastWarningTime = System.currentTimeMillis();
            warningShown = true;
        }
    }
    
    /**
     * Check if the boundary action should be executed
     */
    public boolean shouldExecuteAction() {
        if (actionExecuted) {
            return false;
        }
        
        // Check if enough time has passed for the action delay
        float actionDelay = boundary.getActionDelay();
        if (duration < actionDelay) {
            return false;
        }
        
        // Check severity requirements
        BoundaryAction action = boundary.getAction();
        switch (action) {
            case WARNING_ONLY:
                return false; // Never execute action for warnings
            case KILL_PLAYER:
            case DAMAGE_OVER_TIME:
                return currentSeverity.requiresImmediateAction();
            case TELEPORT_BACK:
            case BLOCK_MOVEMENT:
                return duration >= actionDelay;
            default:
                return duration >= actionDelay;
        }
    }
    
    /**
     * Check if a warning should be shown
     */
    public boolean shouldShowWarning() {
        if (!boundary.shouldShowWarningUI()) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        float warningFrequency = currentSeverity.getWarningFrequency() * 1000; // Convert to milliseconds
        
        return (currentTime - lastWarningTime) >= warningFrequency;
    }
    
    /**
     * Mark the action as executed
     */
    public void markActionExecuted() {
        actionExecuted = true;
        lastActionTime = System.currentTimeMillis();
    }
    
    /**
     * Check if this violation has expired and should be removed
     */
    public boolean isExpired() {
        // Violations expire if the player is no longer triggering the boundary
        return !boundary.shouldTrigger(player.getPosition());
    }
    
    /**
     * Increment the violation count (for repeated violations)
     */
    public void incrementViolationCount() {
        violationCount++;
    }
    
    /**
     * Get the escalated severity based on violation count
     */
    public BoundarySeverity getEscalatedSeverity() {
        BoundarySeverity baseSeverity = currentSeverity;
        
        // Escalate severity for repeated violations
        if (violationCount > 3) {
            return baseSeverity.escalate().escalate();
        } else if (violationCount > 1) {
            return baseSeverity.escalate();
        }
        
        return baseSeverity;
    }
    
    /**
     * Get the time remaining before action execution
     */
    public float getTimeUntilAction() {
        if (actionExecuted) {
            return 0.0f;
        }
        
        return Math.max(0.0f, boundary.getActionDelay() - duration);
    }
    
    /**
     * Get the violation intensity (0.0 to 1.0)
     */
    public float getIntensity() {
        float maxDuration = boundary.getActionDelay() + 2.0f; // Add buffer time
        return Math.min(1.0f, duration / maxDuration);
    }
    
    /**
     * Check if this is a critical violation requiring immediate attention
     */
    public boolean isCritical() {
        return getEscalatedSeverity() == BoundarySeverity.CRITICAL || 
               boundary.getAction().canCauseDeath();
    }
    
    /**
     * Get a description of the current violation state
     */
    public String getDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append("Player ").append(player.getName());
        desc.append(" violating boundary ").append(boundary.getName());
        desc.append(" (").append(boundary.getType().getDisplayName()).append(")");
        desc.append(" for ").append(String.format("%.1f", duration)).append("s");
        desc.append(" - Severity: ").append(getEscalatedSeverity().getDisplayName());
        
        if (!actionExecuted && getTimeUntilAction() > 0) {
            desc.append(" - Action in ").append(String.format("%.1f", getTimeUntilAction())).append("s");
        }
        
        return desc.toString();
    }
    
    // Getters
    public Player getPlayer() { return player; }
    public MapBoundary getBoundary() { return boundary; }
    public long getStartTime() { return startTime; }
    public float getDuration() { return duration; }
    public BoundarySeverity getCurrentSeverity() { return currentSeverity; }
    public boolean isActionExecuted() { return actionExecuted; }
    public boolean isWarningShown() { return warningShown; }
    public long getLastWarningTime() { return lastWarningTime; }
    public long getLastActionTime() { return lastActionTime; }
    public int getViolationCount() { return violationCount; }
    
    // Property management
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, Class<T> type) {
        Object value = properties.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    public Map<String, Object> getProperties() {
        return new HashMap<>(properties);
    }
    
    @Override
    public String toString() {
        return "BoundaryViolation{" +
               "player=" + player.getName() +
               ", boundary=" + boundary.getName() +
               ", duration=" + duration +
               ", severity=" + currentSeverity +
               ", actionExecuted=" + actionExecuted +
               '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        BoundaryViolation that = (BoundaryViolation) obj;
        return player.getId().equals(that.player.getId()) &&
               boundary.getId().equals(that.boundary.getId());
    }
    
    @Override
    public int hashCode() {
        return (player.getId() + ":" + boundary.getId()).hashCode();
    }
}