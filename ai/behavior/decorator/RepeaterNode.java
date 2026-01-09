package engine.ai.behavior.decorator;

import engine.ai.AIBlackboard;
import engine.ai.behavior.AbstractBehaviorNode;
import engine.ai.behavior.BehaviorNode;

/**
 * Repeater decorator node - repeats its child a specified number of times or infinitely
 * Can be configured to repeat until failure, success, or a fixed number of times
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class RepeaterNode extends AbstractBehaviorNode {
    
    /**
     * Repeat mode for the repeater node
     */
    public enum RepeatMode {
        FIXED_COUNT,        // Repeat a fixed number of times
        UNTIL_FAILURE,      // Repeat until child fails
        UNTIL_SUCCESS,      // Repeat until child succeeds
        INFINITE           // Repeat forever (always returns RUNNING)
    }
    
    private final BehaviorNode child;
    private final RepeatMode mode;
    private final int maxRepeats;
    private int currentRepeats;
    
    /**
     * Create a repeater that repeats infinitely
     * @param name Name of the node
     * @param child Child node to repeat
     */
    public RepeaterNode(String name, BehaviorNode child) {
        this(name, child, RepeatMode.INFINITE, -1);
    }
    
    /**
     * Create a repeater with a fixed count
     * @param name Name of the node
     * @param child Child node to repeat
     * @param count Number of times to repeat
     */
    public RepeaterNode(String name, BehaviorNode child, int count) {
        this(name, child, RepeatMode.FIXED_COUNT, count);
    }
    
    /**
     * Create a repeater with specified mode
     * @param name Name of the node
     * @param child Child node to repeat
     * @param mode Repeat mode
     * @param maxRepeats Maximum repeats (ignored for some modes)
     */
    public RepeaterNode(String name, BehaviorNode child, RepeatMode mode, int maxRepeats) {
        super(name);
        this.child = child;
        this.mode = mode;
        this.maxRepeats = Math.max(-1, maxRepeats);
        this.currentRepeats = 0;
    }
    
    /**
     * Get the child node
     * @return Child node
     */
    public BehaviorNode getChild() {
        return child;
    }
    
    /**
     * Get the repeat mode
     * @return Repeat mode
     */
    public RepeatMode getMode() {
        return mode;
    }
    
    /**
     * Get the maximum number of repeats
     * @return Max repeats, or -1 for infinite
     */
    public int getMaxRepeats() {
        return maxRepeats;
    }
    
    /**
     * Get the current repeat count
     * @return Current repeats
     */
    public int getCurrentRepeats() {
        return currentRepeats;
    }
    
    @Override
    protected Status doExecute(AIBlackboard blackboard, float deltaTime) {
        if (child == null) {
            return Status.FAILURE;
        }
        
        while (true) {
            Status childStatus = child.execute(blackboard, deltaTime);
            
            // If child is still running, return running
            if (childStatus == Status.RUNNING) {
                return Status.RUNNING;
            }
            
            // Child completed, check repeat conditions
            switch (mode) {
                case FIXED_COUNT:
                    currentRepeats++;
                    if (currentRepeats >= maxRepeats) {
                        return childStatus; // Return the last child status
                    }
                    break;
                    
                case UNTIL_FAILURE:
                    if (childStatus == Status.FAILURE) {
                        return Status.SUCCESS; // Successfully repeated until failure
                    }
                    currentRepeats++;
                    break;
                    
                case UNTIL_SUCCESS:
                    if (childStatus == Status.SUCCESS) {
                        return Status.SUCCESS; // Successfully repeated until success
                    }
                    currentRepeats++;
                    break;
                    
                case INFINITE:
                    currentRepeats++;
                    // Continue repeating forever
                    break;
            }
            
            // Reset child for next iteration
            child.reset();
            
            // For infinite mode, we need to return RUNNING to continue next frame
            if (mode == RepeatMode.INFINITE) {
                return Status.RUNNING;
            }
            
            // Check if we have a maximum repeat limit (safety check)
            if (maxRepeats > 0 && currentRepeats >= maxRepeats * 1000) {
                // Prevent infinite loops in case of misconfiguration
                return Status.FAILURE;
            }
        }
    }
    
    @Override
    protected void doReset() {
        currentRepeats = 0;
        if (child != null) {
            child.reset();
        }
    }
    
    @Override
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDebugInfo());
        sb.append(" - Mode: ").append(mode);
        sb.append(", Repeats: ").append(currentRepeats);
        if (maxRepeats > 0) {
            sb.append("/").append(maxRepeats);
        }
        if (child != null) {
            sb.append(" - Child: [").append(child.getName()).append("]");
        }
        return sb.toString();
    }
    
    /**
     * Create a repeater that repeats a fixed number of times
     * @param name Name of the repeater
     * @param child Child node to repeat
     * @param count Number of times to repeat
     * @return New repeater node
     */
    public static RepeaterNode fixedCount(String name, BehaviorNode child, int count) {
        return new RepeaterNode(name, child, count);
    }
    
    /**
     * Create a repeater that repeats until the child fails
     * @param name Name of the repeater
     * @param child Child node to repeat
     * @return New repeater node
     */
    public static RepeaterNode untilFailure(String name, BehaviorNode child) {
        return new RepeaterNode(name, child, RepeatMode.UNTIL_FAILURE, -1);
    }
    
    /**
     * Create a repeater that repeats until the child succeeds
     * @param name Name of the repeater
     * @param child Child node to repeat
     * @return New repeater node
     */
    public static RepeaterNode untilSuccess(String name, BehaviorNode child) {
        return new RepeaterNode(name, child, RepeatMode.UNTIL_SUCCESS, -1);
    }
    
    /**
     * Create a repeater that repeats infinitely
     * @param name Name of the repeater
     * @param child Child node to repeat
     * @return New repeater node
     */
    public static RepeaterNode infinite(String name, BehaviorNode child) {
        return new RepeaterNode(name, child);
    }
}