package engine.ai.behavior;

import engine.ai.AIBlackboard;

/**
 * Abstract base class for behavior tree nodes
 * Provides common functionality for all behavior nodes
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public abstract class AbstractBehaviorNode implements BehaviorNode {
    
    protected String name;
    protected Status lastStatus;
    protected boolean isRunning;
    protected long startTime;
    protected long lastExecutionTime;
    
    /**
     * Create a new behavior node
     * @param name Name of the node for debugging
     */
    public AbstractBehaviorNode(String name) {
        this.name = name;
        this.lastStatus = Status.SUCCESS;
        this.isRunning = false;
        this.startTime = 0;
        this.lastExecutionTime = 0;
    }
    
    @Override
    public final Status execute(AIBlackboard blackboard, float deltaTime) {
        long currentTime = System.currentTimeMillis();
        
        // Track execution timing
        if (!isRunning) {
            startTime = currentTime;
        }
        lastExecutionTime = currentTime;
        
        // Execute the node-specific logic
        Status status = doExecute(blackboard, deltaTime);
        
        // Update running state
        isRunning = (status == Status.RUNNING);
        lastStatus = status;
        
        return status;
    }
    
    /**
     * Node-specific execution logic to be implemented by subclasses
     * @param blackboard Shared data storage
     * @param deltaTime Time elapsed since last update
     * @return Execution status
     */
    protected abstract Status doExecute(AIBlackboard blackboard, float deltaTime);
    
    @Override
    public void reset() {
        isRunning = false;
        lastStatus = Status.SUCCESS;
        startTime = 0;
        lastExecutionTime = 0;
        doReset();
    }
    
    /**
     * Node-specific reset logic to be implemented by subclasses
     */
    protected void doReset() {
        // Default implementation does nothing
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public void setName(String name) {
        this.name = name;
    }
    
    @Override
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Get the last execution status
     * @return Last status
     */
    public Status getLastStatus() {
        return lastStatus;
    }
    
    /**
     * Get the time this node has been running (in milliseconds)
     * @return Running time, or 0 if not running
     */
    public long getRunningTime() {
        if (isRunning && startTime > 0) {
            return System.currentTimeMillis() - startTime;
        }
        return 0;
    }
    
    /**
     * Get the time since last execution (in milliseconds)
     * @return Time since last execution
     */
    public long getTimeSinceLastExecution() {
        if (lastExecutionTime > 0) {
            return System.currentTimeMillis() - lastExecutionTime;
        }
        return 0;
    }
    
    @Override
    public String getDebugInfo() {
        return String.format("%s [%s] - Running: %s, Time: %dms", 
                            name, lastStatus, isRunning, getRunningTime());
    }
    
    @Override
    public String toString() {
        return name + " (" + getClass().getSimpleName() + ")";
    }
}