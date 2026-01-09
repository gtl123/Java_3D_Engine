package engine.ai.behavior;

import engine.ai.AIBlackboard;
import engine.logging.LogManager;
import engine.config.ConfigurationManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * System for managing and executing behavior trees
 * Provides centralized execution, debugging, and performance monitoring
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class BehaviorTreeSystem {
    
    private static final LogManager logger = LogManager.getInstance();
    private static final ConfigurationManager config = ConfigurationManager.getInstance();
    
    private final Map<String, BehaviorTree> behaviorTrees;
    private final Map<String, BehaviorTreeExecutionContext> executionContexts;
    private final AtomicLong executionCounter;
    
    // Performance monitoring
    private final boolean enableProfiling;
    private final Map<String, BehaviorTreeStats> treeStats;
    
    public BehaviorTreeSystem() {
        this.behaviorTrees = new ConcurrentHashMap<>();
        this.executionContexts = new ConcurrentHashMap<>();
        this.executionCounter = new AtomicLong(0);
        this.enableProfiling = config.getBoolean("ai.behavior.enableProfiling", true);
        this.treeStats = new ConcurrentHashMap<>();
        
        logger.info("BehaviorTreeSystem initialized");
    }
    
    /**
     * Register a behavior tree with the system
     * @param treeId Unique identifier for the tree
     * @param rootNode Root node of the behavior tree
     * @return The created behavior tree
     */
    public BehaviorTree registerTree(String treeId, BehaviorNode rootNode) {
        BehaviorTree tree = new BehaviorTree(treeId, rootNode);
        behaviorTrees.put(treeId, tree);
        
        if (enableProfiling) {
            treeStats.put(treeId, new BehaviorTreeStats(treeId));
        }
        
        logger.info("Registered behavior tree: " + treeId);
        return tree;
    }
    
    /**
     * Unregister a behavior tree from the system
     * @param treeId Tree identifier
     * @return true if removed successfully
     */
    public boolean unregisterTree(String treeId) {
        BehaviorTree removed = behaviorTrees.remove(treeId);
        executionContexts.remove(treeId);
        treeStats.remove(treeId);
        
        if (removed != null) {
            logger.info("Unregistered behavior tree: " + treeId);
            return true;
        }
        return false;
    }
    
    /**
     * Get a registered behavior tree
     * @param treeId Tree identifier
     * @return The behavior tree, or null if not found
     */
    public BehaviorTree getTree(String treeId) {
        return behaviorTrees.get(treeId);
    }
    
    /**
     * Execute a behavior tree
     * @param treeId Tree identifier
     * @param blackboard Blackboard for the execution
     * @param deltaTime Time elapsed since last update
     * @return Execution status
     */
    public BehaviorNode.Status executeTree(String treeId, AIBlackboard blackboard, float deltaTime) {
        BehaviorTree tree = behaviorTrees.get(treeId);
        if (tree == null) {
            logger.warn("Attempted to execute unknown behavior tree: " + treeId);
            return BehaviorNode.Status.FAILURE;
        }
        
        long startTime = System.nanoTime();
        BehaviorNode.Status result;
        
        try {
            result = tree.execute(blackboard, deltaTime);
            executionCounter.incrementAndGet();
            
            // Update execution context
            BehaviorTreeExecutionContext context = executionContexts.computeIfAbsent(
                treeId, k -> new BehaviorTreeExecutionContext(treeId));
            context.updateExecution(result, deltaTime);
            
        } catch (Exception e) {
            logger.error("Error executing behavior tree " + treeId, e);
            result = BehaviorNode.Status.FAILURE;
        }
        
        // Performance monitoring
        if (enableProfiling) {
            long executionTime = System.nanoTime() - startTime;
            BehaviorTreeStats stats = treeStats.get(treeId);
            if (stats != null) {
                stats.recordExecution(executionTime, result);
            }
        }
        
        return result;
    }
    
    /**
     * Reset a behavior tree to its initial state
     * @param treeId Tree identifier
     */
    public void resetTree(String treeId) {
        BehaviorTree tree = behaviorTrees.get(treeId);
        if (tree != null) {
            tree.reset();
            
            BehaviorTreeExecutionContext context = executionContexts.get(treeId);
            if (context != null) {
                context.reset();
            }
        }
    }
    
    /**
     * Get execution statistics for a behavior tree
     * @param treeId Tree identifier
     * @return Statistics, or null if not found
     */
    public BehaviorTreeStats getTreeStats(String treeId) {
        return treeStats.get(treeId);
    }
    
    /**
     * Get execution context for a behavior tree
     * @param treeId Tree identifier
     * @return Execution context, or null if not found
     */
    public BehaviorTreeExecutionContext getExecutionContext(String treeId) {
        return executionContexts.get(treeId);
    }
    
    /**
     * Get all registered tree IDs
     * @return Set of tree identifiers
     */
    public Set<String> getRegisteredTrees() {
        return behaviorTrees.keySet();
    }
    
    /**
     * Get the total number of tree executions
     * @return Total executions
     */
    public long getTotalExecutions() {
        return executionCounter.get();
    }
    
    /**
     * Update all registered behavior trees
     * @param deltaTime Time elapsed since last update
     */
    public void updateAll(float deltaTime) {
        // This method could be used to update trees that need continuous execution
        // For now, trees are executed on-demand via executeTree()
        
        // Clean up old execution contexts if needed
        if (enableProfiling && executionCounter.get() % 1000 == 0) {
            cleanupOldStats();
        }
    }
    
    /**
     * Clean up old statistics to prevent memory leaks
     */
    private void cleanupOldStats() {
        long currentTime = System.currentTimeMillis();
        long maxAge = config.getInt("ai.behavior.statsMaxAge", 300000); // 5 minutes default
        
        treeStats.entrySet().removeIf(entry -> {
            BehaviorTreeStats stats = entry.getValue();
            return currentTime - stats.getLastExecutionTime() > maxAge;
        });
    }
    
    /**
     * Get debug information for all trees
     * @return Debug information string
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("BehaviorTreeSystem Debug Info:\n");
        sb.append("Registered Trees: ").append(behaviorTrees.size()).append("\n");
        sb.append("Total Executions: ").append(executionCounter.get()).append("\n");
        sb.append("Profiling Enabled: ").append(enableProfiling).append("\n\n");
        
        for (Map.Entry<String, BehaviorTree> entry : behaviorTrees.entrySet()) {
            String treeId = entry.getKey();
            BehaviorTree tree = entry.getValue();
            
            sb.append("Tree: ").append(treeId).append("\n");
            sb.append("  Status: ").append(tree.getLastStatus()).append("\n");
            sb.append("  Running: ").append(tree.isRunning()).append("\n");
            
            BehaviorTreeStats stats = treeStats.get(treeId);
            if (stats != null) {
                sb.append("  Executions: ").append(stats.getExecutionCount()).append("\n");
                sb.append("  Avg Time: ").append(String.format("%.2f", stats.getAverageExecutionTime())).append("ms\n");
                sb.append("  Success Rate: ").append(String.format("%.1f", stats.getSuccessRate() * 100)).append("%\n");
            }
            
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        behaviorTrees.clear();
        executionContexts.clear();
        treeStats.clear();
        logger.info("BehaviorTreeSystem cleaned up");
    }
    
    /**
     * Execution context for tracking tree state
     */
    public static class BehaviorTreeExecutionContext {
        private final String treeId;
        private BehaviorNode.Status lastStatus;
        private float totalTime;
        private int executionCount;
        private long lastExecutionTime;
        
        public BehaviorTreeExecutionContext(String treeId) {
            this.treeId = treeId;
            this.lastStatus = BehaviorNode.Status.SUCCESS;
            this.totalTime = 0.0f;
            this.executionCount = 0;
            this.lastExecutionTime = System.currentTimeMillis();
        }
        
        public void updateExecution(BehaviorNode.Status status, float deltaTime) {
            this.lastStatus = status;
            this.totalTime += deltaTime;
            this.executionCount++;
            this.lastExecutionTime = System.currentTimeMillis();
        }
        
        public void reset() {
            this.totalTime = 0.0f;
            this.executionCount = 0;
            this.lastExecutionTime = System.currentTimeMillis();
        }
        
        // Getters
        public String getTreeId() { return treeId; }
        public BehaviorNode.Status getLastStatus() { return lastStatus; }
        public float getTotalTime() { return totalTime; }
        public int getExecutionCount() { return executionCount; }
        public long getLastExecutionTime() { return lastExecutionTime; }
    }
    
    /**
     * Statistics for behavior tree performance
     */
    public static class BehaviorTreeStats {
        private final String treeId;
        private long executionCount;
        private long totalExecutionTime; // in nanoseconds
        private long successCount;
        private long failureCount;
        private long runningCount;
        private long lastExecutionTime;
        private long minExecutionTime;
        private long maxExecutionTime;
        
        public BehaviorTreeStats(String treeId) {
            this.treeId = treeId;
            this.minExecutionTime = Long.MAX_VALUE;
            this.maxExecutionTime = 0;
            this.lastExecutionTime = System.currentTimeMillis();
        }
        
        public void recordExecution(long executionTimeNanos, BehaviorNode.Status status) {
            executionCount++;
            totalExecutionTime += executionTimeNanos;
            lastExecutionTime = System.currentTimeMillis();
            
            minExecutionTime = Math.min(minExecutionTime, executionTimeNanos);
            maxExecutionTime = Math.max(maxExecutionTime, executionTimeNanos);
            
            switch (status) {
                case SUCCESS: successCount++; break;
                case FAILURE: failureCount++; break;
                case RUNNING: runningCount++; break;
            }
        }
        
        public double getAverageExecutionTime() {
            return executionCount > 0 ? (totalExecutionTime / 1_000_000.0) / executionCount : 0.0;
        }
        
        public double getSuccessRate() {
            long completedExecutions = successCount + failureCount;
            return completedExecutions > 0 ? (double) successCount / completedExecutions : 0.0;
        }
        
        // Getters
        public String getTreeId() { return treeId; }
        public long getExecutionCount() { return executionCount; }
        public long getTotalExecutionTime() { return totalExecutionTime; }
        public long getSuccessCount() { return successCount; }
        public long getFailureCount() { return failureCount; }
        public long getRunningCount() { return runningCount; }
        public long getLastExecutionTime() { return lastExecutionTime; }
        public long getMinExecutionTime() { return minExecutionTime; }
        public long getMaxExecutionTime() { return maxExecutionTime; }
    }
}