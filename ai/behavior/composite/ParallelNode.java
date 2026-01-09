package engine.ai.behavior.composite;

import engine.ai.AIBlackboard;
import engine.ai.behavior.AbstractBehaviorNode;
import engine.ai.behavior.BehaviorNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parallel composite node - executes all children simultaneously
 * Success/failure policy can be configured (all, any, or threshold-based)
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class ParallelNode extends AbstractBehaviorNode {
    
    /**
     * Policy for determining when the parallel node succeeds or fails
     */
    public enum Policy {
        REQUIRE_ALL,    // All children must succeed
        REQUIRE_ONE,    // At least one child must succeed
        THRESHOLD       // A specific number of children must succeed
    }
    
    private final List<BehaviorNode> children;
    private final Map<BehaviorNode, Status> childStatuses;
    private final Policy successPolicy;
    private final Policy failurePolicy;
    private final int successThreshold;
    private final int failureThreshold;
    
    /**
     * Create a new parallel node with REQUIRE_ALL policy
     * @param name Name of the node
     */
    public ParallelNode(String name) {
        this(name, Policy.REQUIRE_ALL, Policy.REQUIRE_ONE);
    }
    
    /**
     * Create a new parallel node with specified policies
     * @param name Name of the node
     * @param successPolicy Policy for success condition
     * @param failurePolicy Policy for failure condition
     */
    public ParallelNode(String name, Policy successPolicy, Policy failurePolicy) {
        this(name, successPolicy, failurePolicy, 1, 1);
    }
    
    /**
     * Create a new parallel node with threshold policies
     * @param name Name of the node
     * @param successPolicy Policy for success condition
     * @param failurePolicy Policy for failure condition
     * @param successThreshold Number of children that must succeed (for THRESHOLD policy)
     * @param failureThreshold Number of children that must fail (for THRESHOLD policy)
     */
    public ParallelNode(String name, Policy successPolicy, Policy failurePolicy, 
                       int successThreshold, int failureThreshold) {
        super(name);
        this.children = new ArrayList<>();
        this.childStatuses = new HashMap<>();
        this.successPolicy = successPolicy;
        this.failurePolicy = failurePolicy;
        this.successThreshold = Math.max(1, successThreshold);
        this.failureThreshold = Math.max(1, failureThreshold);
    }
    
    /**
     * Add a child node to this parallel node
     * @param child Child node to add
     * @return This node for method chaining
     */
    public ParallelNode addChild(BehaviorNode child) {
        children.add(child);
        childStatuses.put(child, Status.SUCCESS);
        return this;
    }
    
    /**
     * Remove a child node from this parallel node
     * @param child Child node to remove
     * @return true if removed successfully
     */
    public boolean removeChild(BehaviorNode child) {
        childStatuses.remove(child);
        return children.remove(child);
    }
    
    /**
     * Get all child nodes
     * @return List of child nodes
     */
    public List<BehaviorNode> getChildren() {
        return new ArrayList<>(children);
    }
    
    /**
     * Get the number of child nodes
     * @return Number of children
     */
    public int getChildCount() {
        return children.size();
    }
    
    /**
     * Get the status of a specific child
     * @param child Child node
     * @return Status of the child
     */
    public Status getChildStatus(BehaviorNode child) {
        return childStatuses.getOrDefault(child, Status.SUCCESS);
    }
    
    /**
     * Get the number of children with a specific status
     * @param status Status to count
     * @return Number of children with that status
     */
    public int getChildrenWithStatus(Status status) {
        return (int) childStatuses.values().stream().filter(s -> s == status).count();
    }
    
    @Override
    protected Status doExecute(AIBlackboard blackboard, float deltaTime) {
        if (children.isEmpty()) {
            return Status.SUCCESS;
        }
        
        // Execute all children
        for (BehaviorNode child : children) {
            Status childStatus = child.execute(blackboard, deltaTime);
            childStatuses.put(child, childStatus);
        }
        
        // Count statuses
        int successCount = getChildrenWithStatus(Status.SUCCESS);
        int failureCount = getChildrenWithStatus(Status.FAILURE);
        int runningCount = getChildrenWithStatus(Status.RUNNING);
        
        // Check failure condition first
        if (checkFailureCondition(failureCount, successCount, runningCount)) {
            return Status.FAILURE;
        }
        
        // Check success condition
        if (checkSuccessCondition(successCount, failureCount, runningCount)) {
            return Status.SUCCESS;
        }
        
        // Still running
        return Status.RUNNING;
    }
    
    /**
     * Check if the failure condition is met
     */
    private boolean checkFailureCondition(int failureCount, int successCount, int runningCount) {
        switch (failurePolicy) {
            case REQUIRE_ALL:
                return failureCount == children.size();
            case REQUIRE_ONE:
                return failureCount > 0;
            case THRESHOLD:
                return failureCount >= failureThreshold;
            default:
                return false;
        }
    }
    
    /**
     * Check if the success condition is met
     */
    private boolean checkSuccessCondition(int successCount, int failureCount, int runningCount) {
        switch (successPolicy) {
            case REQUIRE_ALL:
                return successCount == children.size();
            case REQUIRE_ONE:
                return successCount > 0;
            case THRESHOLD:
                return successCount >= successThreshold;
            default:
                return false;
        }
    }
    
    @Override
    protected void doReset() {
        // Reset all children
        for (BehaviorNode child : children) {
            child.reset();
            childStatuses.put(child, Status.SUCCESS);
        }
    }
    
    @Override
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDebugInfo());
        
        int successCount = getChildrenWithStatus(Status.SUCCESS);
        int failureCount = getChildrenWithStatus(Status.FAILURE);
        int runningCount = getChildrenWithStatus(Status.RUNNING);
        
        sb.append(" - S:").append(successCount)
          .append(" F:").append(failureCount)
          .append(" R:").append(runningCount)
          .append("/").append(children.size());
        
        return sb.toString();
    }
    
    /**
     * Get detailed debug information including all children
     * @return Detailed debug string
     */
    public String getDetailedDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(getDebugInfo()).append("\n");
        sb.append("Success Policy: ").append(successPolicy);
        if (successPolicy == Policy.THRESHOLD) {
            sb.append(" (").append(successThreshold).append(")");
        }
        sb.append(", Failure Policy: ").append(failurePolicy);
        if (failurePolicy == Policy.THRESHOLD) {
            sb.append(" (").append(failureThreshold).append(")");
        }
        sb.append("\n");
        
        for (int i = 0; i < children.size(); i++) {
            BehaviorNode child = children.get(i);
            Status status = childStatuses.get(child);
            sb.append("   ").append(i).append(": [").append(status).append("] ")
              .append(child.getDebugInfo()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Create a parallel node that requires all children to succeed
     * @param name Name of the parallel node
     * @param children Child nodes
     * @return New parallel node
     */
    public static ParallelNode requireAll(String name, BehaviorNode... children) {
        ParallelNode parallel = new ParallelNode(name, Policy.REQUIRE_ALL, Policy.REQUIRE_ONE);
        for (BehaviorNode child : children) {
            parallel.addChild(child);
        }
        return parallel;
    }
    
    /**
     * Create a parallel node that requires only one child to succeed
     * @param name Name of the parallel node
     * @param children Child nodes
     * @return New parallel node
     */
    public static ParallelNode requireOne(String name, BehaviorNode... children) {
        ParallelNode parallel = new ParallelNode(name, Policy.REQUIRE_ONE, Policy.REQUIRE_ALL);
        for (BehaviorNode child : children) {
            parallel.addChild(child);
        }
        return parallel;
    }
    
    /**
     * Create a parallel node with threshold-based success/failure
     * @param name Name of the parallel node
     * @param successThreshold Number of children that must succeed
     * @param failureThreshold Number of children that must fail
     * @param children Child nodes
     * @return New parallel node
     */
    public static ParallelNode threshold(String name, int successThreshold, int failureThreshold, 
                                       BehaviorNode... children) {
        ParallelNode parallel = new ParallelNode(name, Policy.THRESHOLD, Policy.THRESHOLD, 
                                               successThreshold, failureThreshold);
        for (BehaviorNode child : children) {
            parallel.addChild(child);
        }
        return parallel;
    }
}