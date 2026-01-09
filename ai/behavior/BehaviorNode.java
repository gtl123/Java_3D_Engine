package engine.ai.behavior;

import engine.ai.AIBlackboard;

/**
 * Base interface for all behavior tree nodes
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public interface BehaviorNode {
    
    /**
     * Possible execution states for behavior nodes
     */
    enum Status {
        SUCCESS,    // Node completed successfully
        FAILURE,    // Node failed to complete
        RUNNING     // Node is still executing
    }
    
    /**
     * Execute this behavior node
     * @param blackboard Shared data storage for the AI entity
     * @param deltaTime Time elapsed since last update
     * @return The execution status
     */
    Status execute(AIBlackboard blackboard, float deltaTime);
    
    /**
     * Reset the node to its initial state
     */
    void reset();
    
    /**
     * Get the name of this behavior node
     * @return Node name for debugging
     */
    String getName();
    
    /**
     * Set the name of this behavior node
     * @param name Node name
     */
    void setName(String name);
    
    /**
     * Check if this node is currently running
     * @return true if running
     */
    boolean isRunning();
    
    /**
     * Get debug information about this node
     * @return Debug string
     */
    String getDebugInfo();
}