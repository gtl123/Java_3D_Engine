package engine.ai;

import engine.entity.Entity;

/**
 * Wrapper class for entities with AI capabilities
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class AIEntity {
    
    private final String id;
    private final Entity entity;
    private String behaviorTreeId;
    private boolean active;
    private boolean hasDecisionMaking;
    private long creationTime;
    private long lastUpdateTime;
    private int updateCount;
    
    // AI state
    private AIState currentState;
    private String currentGoal;
    private float alertness;
    private float aggression;
    
    /**
     * Create a new AI entity
     * @param id Unique identifier
     * @param entity The underlying entity
     * @param behaviorTreeId ID of the behavior tree to use
     */
    public AIEntity(String id, Entity entity, String behaviorTreeId) {
        this.id = id;
        this.entity = entity;
        this.behaviorTreeId = behaviorTreeId;
        this.active = true;
        this.hasDecisionMaking = false;
        this.creationTime = System.currentTimeMillis();
        this.lastUpdateTime = creationTime;
        this.updateCount = 0;
        
        // Initialize AI state
        this.currentState = AIState.IDLE;
        this.currentGoal = null;
        this.alertness = 0.5f;
        this.aggression = 0.3f;
    }
    
    /**
     * AI states for behavior management
     */
    public enum AIState {
        IDLE,           // Not doing anything specific
        MOVING,         // Moving to a target
        INVESTIGATING,  // Investigating something interesting
        PURSUING,       // Pursuing a target
        ATTACKING,      // In combat
        FLEEING,        // Fleeing from danger
        PATROLLING,     // Following a patrol route
        GUARDING,       // Guarding a location
        SEARCHING,      // Searching for something
        WAITING,        // Waiting for something
        DEAD            // Entity is dead/disabled
    }
    
    // Getters and setters
    public String getId() { return id; }
    public Entity getEntity() { return entity; }
    
    public String getBehaviorTreeId() { return behaviorTreeId; }
    public void setBehaviorTreeId(String behaviorTreeId) { this.behaviorTreeId = behaviorTreeId; }
    
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    
    public boolean hasDecisionMaking() { return hasDecisionMaking; }
    public void setHasDecisionMaking(boolean hasDecisionMaking) { this.hasDecisionMaking = hasDecisionMaking; }
    
    public long getCreationTime() { return creationTime; }
    public long getLastUpdateTime() { return lastUpdateTime; }
    public void setLastUpdateTime(long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
    
    public int getUpdateCount() { return updateCount; }
    public void incrementUpdateCount() { this.updateCount++; }
    
    public AIState getCurrentState() { return currentState; }
    public void setCurrentState(AIState currentState) { this.currentState = currentState; }
    
    public String getCurrentGoal() { return currentGoal; }
    public void setCurrentGoal(String currentGoal) { this.currentGoal = currentGoal; }
    
    public float getAlertness() { return alertness; }
    public void setAlertness(float alertness) { this.alertness = Math.max(0.0f, Math.min(1.0f, alertness)); }
    
    public float getAggression() { return aggression; }
    public void setAggression(float aggression) { this.aggression = Math.max(0.0f, Math.min(1.0f, aggression)); }
    
    /**
     * Get the age of this AI entity in milliseconds
     * @return Age in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - creationTime;
    }
    
    /**
     * Get the time since last update in milliseconds
     * @return Time since last update
     */
    public long getTimeSinceLastUpdate() {
        return System.currentTimeMillis() - lastUpdateTime;
    }
    
    /**
     * Check if the entity is in a combat state
     * @return true if in combat
     */
    public boolean isInCombat() {
        return currentState == AIState.ATTACKING || currentState == AIState.FLEEING;
    }
    
    /**
     * Check if the entity is moving
     * @return true if moving
     */
    public boolean isMoving() {
        return currentState == AIState.MOVING || currentState == AIState.PURSUING || 
               currentState == AIState.PATROLLING || currentState == AIState.FLEEING;
    }
    
    /**
     * Check if the entity is available for new tasks
     * @return true if available
     */
    public boolean isAvailable() {
        return active && (currentState == AIState.IDLE || currentState == AIState.WAITING);
    }
    
    /**
     * Reset the AI entity to initial state
     */
    public void reset() {
        currentState = AIState.IDLE;
        currentGoal = null;
        alertness = 0.5f;
        aggression = 0.3f;
        updateCount = 0;
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Get debug information about this AI entity
     * @return Debug string
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("AIEntity{");
        sb.append("id='").append(id).append('\'');
        sb.append(", active=").append(active);
        sb.append(", state=").append(currentState);
        sb.append(", behaviorTree='").append(behaviorTreeId).append('\'');
        sb.append(", updates=").append(updateCount);
        sb.append(", age=").append(getAge()).append("ms");
        sb.append(", alertness=").append(String.format("%.2f", alertness));
        sb.append(", aggression=").append(String.format("%.2f", aggression));
        
        if (currentGoal != null) {
            sb.append(", goal='").append(currentGoal).append('\'');
        }
        
        sb.append('}');
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return "AIEntity{" +
                "id='" + id + '\'' +
                ", active=" + active +
                ", state=" + currentState +
                ", updates=" + updateCount +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        AIEntity aiEntity = (AIEntity) obj;
        return id.equals(aiEntity.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}