package engine.ai.behavior;

import engine.ai.AIBlackboard;

/**
 * Represents a complete behavior tree with a root node
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class BehaviorTree {
    
    private final String treeId;
    private final BehaviorNode rootNode;
    private BehaviorNode.Status lastStatus;
    private boolean isRunning;
    private long creationTime;
    private long lastExecutionTime;
    private int executionCount;
    
    /**
     * Create a new behavior tree
     * @param treeId Unique identifier for this tree
     * @param rootNode Root node of the tree
     */
    public BehaviorTree(String treeId, BehaviorNode rootNode) {
        this.treeId = treeId;
        this.rootNode = rootNode;
        this.lastStatus = BehaviorNode.Status.SUCCESS;
        this.isRunning = false;
        this.creationTime = System.currentTimeMillis();
        this.lastExecutionTime = 0;
        this.executionCount = 0;
    }
    
    /**
     * Execute the behavior tree
     * @param blackboard Shared data storage
     * @param deltaTime Time elapsed since last update
     * @return Execution status
     */
    public BehaviorNode.Status execute(AIBlackboard blackboard, float deltaTime) {
        if (rootNode == null) {
            return BehaviorNode.Status.FAILURE;
        }
        
        lastExecutionTime = System.currentTimeMillis();
        executionCount++;
        
        lastStatus = rootNode.execute(blackboard, deltaTime);
        isRunning = (lastStatus == BehaviorNode.Status.RUNNING);
        
        return lastStatus;
    }
    
    /**
     * Reset the behavior tree to its initial state
     */
    public void reset() {
        if (rootNode != null) {
            rootNode.reset();
        }
        isRunning = false;
        lastStatus = BehaviorNode.Status.SUCCESS;
    }
    
    /**
     * Get the tree identifier
     * @return Tree ID
     */
    public String getTreeId() {
        return treeId;
    }
    
    /**
     * Get the root node
     * @return Root behavior node
     */
    public BehaviorNode getRootNode() {
        return rootNode;
    }
    
    /**
     * Get the last execution status
     * @return Last status
     */
    public BehaviorNode.Status getLastStatus() {
        return lastStatus;
    }
    
    /**
     * Check if the tree is currently running
     * @return true if running
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Get the creation time of this tree
     * @return Creation timestamp
     */
    public long getCreationTime() {
        return creationTime;
    }
    
    /**
     * Get the last execution time
     * @return Last execution timestamp
     */
    public long getLastExecutionTime() {
        return lastExecutionTime;
    }
    
    /**
     * Get the total number of executions
     * @return Execution count
     */
    public int getExecutionCount() {
        return executionCount;
    }
    
    /**
     * Get the age of this tree in milliseconds
     * @return Age in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - creationTime;
    }
    
    /**
     * Get debug information about this tree
     * @return Debug string
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("BehaviorTree{");
        sb.append("id='").append(treeId).append('\'');
        sb.append(", status=").append(lastStatus);
        sb.append(", running=").append(isRunning);
        sb.append(", executions=").append(executionCount);
        sb.append(", age=").append(getAge()).append("ms");
        sb.append('}');
        
        if (rootNode != null) {
            sb.append("\nRoot: ").append(rootNode.getDebugInfo());
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return "BehaviorTree{" +
                "treeId='" + treeId + '\'' +
                ", lastStatus=" + lastStatus +
                ", isRunning=" + isRunning +
                ", executionCount=" + executionCount +
                '}';
    }
}