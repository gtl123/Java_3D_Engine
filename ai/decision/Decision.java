package engine.ai.decision;

import engine.ai.AIBlackboard;

/**
 * Interface for AI decisions in the utility-based decision system
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public interface Decision {
    
    /**
     * Get the name of this decision
     * @return Decision name
     */
    String getName();
    
    /**
     * Evaluate the utility of this decision
     * @param blackboard Entity's blackboard
     * @param context Decision context
     * @param deltaTime Time elapsed since last update
     * @return Utility value (0.0 to 1.0, higher is better)
     */
    float evaluate(AIBlackboard blackboard, DecisionSystem.DecisionContext context, float deltaTime);
    
    /**
     * Execute this decision
     * @param blackboard Entity's blackboard
     * @param context Decision context
     * @param deltaTime Time elapsed since last update
     */
    void execute(AIBlackboard blackboard, DecisionSystem.DecisionContext context, float deltaTime);
    
    /**
     * Check if this decision can be interrupted
     * @return true if interruptible
     */
    default boolean isInterruptible() {
        return true;
    }
    
    /**
     * Get the priority of this decision (higher = more important)
     * @return Priority value
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * Get the cooldown time for this decision in milliseconds
     * @return Cooldown time, or 0 for no cooldown
     */
    default long getCooldownTime() {
        return 0;
    }
}