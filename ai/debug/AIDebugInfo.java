package engine.ai.debug;

import org.joml.Vector3f;

/**
 * Debug information container for AI entities
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class AIDebugInfo {
    
    private final String entityId;
    private String currentState;
    private boolean active;
    private int updateCount;
    private long lastUpdateTime;
    
    // Behavior tree info
    private long behaviorTreeExecutions;
    private double behaviorTreeAvgTime;
    
    // Decision info
    private String currentDecision;
    
    // Pathfinding info
    private boolean hasTarget;
    private Vector3f targetPosition;
    
    // Performance info
    private long totalUpdateTime;
    private int framesSinceLastUpdate;
    
    public AIDebugInfo(String entityId) {
        this.entityId = entityId;
        this.currentState = "UNKNOWN";
        this.active = false;
        this.updateCount = 0;
        this.lastUpdateTime = System.currentTimeMillis();
        this.behaviorTreeExecutions = 0;
        this.behaviorTreeAvgTime = 0.0;
        this.hasTarget = false;
        this.totalUpdateTime = 0;
        this.framesSinceLastUpdate = 0;
    }
    
    // Getters and setters
    public String getEntityId() { return entityId; }
    
    public String getCurrentState() { return currentState; }
    public void setCurrentState(String currentState) { this.currentState = currentState; }
    
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    
    public int getUpdateCount() { return updateCount; }
    public void setUpdateCount(int updateCount) { this.updateCount = updateCount; }
    
    public long getLastUpdateTime() { return lastUpdateTime; }
    public void setLastUpdateTime(long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
    
    public long getBehaviorTreeExecutions() { return behaviorTreeExecutions; }
    public void setBehaviorTreeExecutions(long behaviorTreeExecutions) { 
        this.behaviorTreeExecutions = behaviorTreeExecutions; 
    }
    
    public double getBehaviorTreeAvgTime() { return behaviorTreeAvgTime; }
    public void setBehaviorTreeAvgTime(double behaviorTreeAvgTime) { 
        this.behaviorTreeAvgTime = behaviorTreeAvgTime; 
    }
    
    public String getCurrentDecision() { return currentDecision; }
    public void setCurrentDecision(String currentDecision) { this.currentDecision = currentDecision; }
    
    public boolean hasTarget() { return hasTarget; }
    public void setHasTarget(boolean hasTarget) { this.hasTarget = hasTarget; }
    
    public Vector3f getTargetPosition() { return targetPosition; }
    public void setTargetPosition(Vector3f targetPosition) { 
        this.targetPosition = targetPosition != null ? new Vector3f(targetPosition) : null; 
    }
    
    public long getTotalUpdateTime() { return totalUpdateTime; }
    public void addUpdateTime(long updateTime) { this.totalUpdateTime += updateTime; }
    
    public int getFramesSinceLastUpdate() { return framesSinceLastUpdate; }
    public void incrementFramesSinceLastUpdate() { this.framesSinceLastUpdate++; }
    public void resetFramesSinceLastUpdate() { this.framesSinceLastUpdate = 0; }
    
    /**
     * Get the time since last update in milliseconds
     * @return Time since last update
     */
    public long getTimeSinceLastUpdate() {
        return System.currentTimeMillis() - lastUpdateTime;
    }
    
    /**
     * Get average update time per frame
     * @return Average update time in milliseconds
     */
    public double getAverageUpdateTime() {
        return updateCount > 0 ? (double) totalUpdateTime / updateCount : 0.0;
    }
    
    /**
     * Reset all debug information
     */
    public void reset() {
        currentState = "UNKNOWN";
        active = false;
        updateCount = 0;
        lastUpdateTime = System.currentTimeMillis();
        behaviorTreeExecutions = 0;
        behaviorTreeAvgTime = 0.0;
        currentDecision = null;
        hasTarget = false;
        targetPosition = null;
        totalUpdateTime = 0;
        framesSinceLastUpdate = 0;
    }
    
    @Override
    public String toString() {
        return "AIDebugInfo{" +
                "entityId='" + entityId + '\'' +
                ", currentState='" + currentState + '\'' +
                ", active=" + active +
                ", updateCount=" + updateCount +
                ", hasTarget=" + hasTarget +
                ", currentDecision='" + currentDecision + '\'' +
                '}';
    }
}