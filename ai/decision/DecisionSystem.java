package engine.ai.decision;

import engine.ai.AIBlackboard;
import engine.logging.LogManager;
import engine.config.ConfigurationManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Utility-based decision making system for AI entities
 * Evaluates multiple considerations to make intelligent decisions
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class DecisionSystem {
    
    private static final LogManager logger = LogManager.getInstance();
    private static final ConfigurationManager config = ConfigurationManager.getInstance();
    
    private final Map<String, List<Decision>> entityDecisions;
    private final Map<String, DecisionContext> entityContexts;
    private final float decisionThreshold;
    private final boolean enableLogging;
    
    public DecisionSystem() {
        this.entityDecisions = new ConcurrentHashMap<>();
        this.entityContexts = new ConcurrentHashMap<>();
        this.decisionThreshold = config.getFloat("ai.decision.threshold", 0.1f);
        this.enableLogging = config.getBoolean("ai.decision.enableLogging", false);
        
        logger.info("DecisionSystem initialized with threshold: " + decisionThreshold);
    }
    
    /**
     * Register a decision for an entity
     * @param entityId Entity identifier
     * @param decision Decision to register
     */
    public void registerDecision(String entityId, Decision decision) {
        entityDecisions.computeIfAbsent(entityId, k -> new ArrayList<>()).add(decision);
        
        if (enableLogging) {
            logger.debug("Registered decision '" + decision.getName() + "' for entity: " + entityId);
        }
    }
    
    /**
     * Remove a decision from an entity
     * @param entityId Entity identifier
     * @param decisionName Name of the decision to remove
     * @return true if removed successfully
     */
    public boolean removeDecision(String entityId, String decisionName) {
        List<Decision> decisions = entityDecisions.get(entityId);
        if (decisions != null) {
            return decisions.removeIf(d -> d.getName().equals(decisionName));
        }
        return false;
    }
    
    /**
     * Update decision making for an entity
     * @param entityId Entity identifier
     * @param blackboard Entity's blackboard
     * @param deltaTime Time elapsed since last update
     */
    public void updateEntity(String entityId, AIBlackboard blackboard, float deltaTime) {
        List<Decision> decisions = entityDecisions.get(entityId);
        if (decisions == null || decisions.isEmpty()) {
            return;
        }
        
        DecisionContext context = entityContexts.computeIfAbsent(entityId, 
            k -> new DecisionContext(entityId));
        
        // Evaluate all decisions
        List<DecisionResult> results = new ArrayList<>();
        for (Decision decision : decisions) {
            try {
                float utility = decision.evaluate(blackboard, context, deltaTime);
                results.add(new DecisionResult(decision, utility));
            } catch (Exception e) {
                logger.error("Error evaluating decision '" + decision.getName() + 
                           "' for entity " + entityId, e);
            }
        }
        
        // Sort by utility (highest first)
        results.sort(Comparator.comparingDouble(DecisionResult::getUtility).reversed());
        
        // Execute the best decision if it meets the threshold
        if (!results.isEmpty()) {
            DecisionResult best = results.get(0);
            
            if (best.getUtility() > decisionThreshold) {
                // Check if this is a different decision than currently executing
                if (!best.getDecision().getName().equals(context.getCurrentDecision())) {
                    context.setCurrentDecision(best.getDecision().getName());
                    context.setLastDecisionTime(System.currentTimeMillis());
                    
                    if (enableLogging) {
                        logger.debug("Entity " + entityId + " executing decision: " + 
                                   best.getDecision().getName() + " (utility: " + 
                                   String.format("%.3f", best.getUtility()) + ")");
                    }
                }
                
                // Execute the decision
                best.getDecision().execute(blackboard, context, deltaTime);
                context.incrementExecutionCount();
            }
        }
        
        context.setLastUpdateTime(System.currentTimeMillis());
    }
    
    /**
     * Get the current decision for an entity
     * @param entityId Entity identifier
     * @return Current decision name, or null if none
     */
    public String getCurrentDecision(String entityId) {
        DecisionContext context = entityContexts.get(entityId);
        return context != null ? context.getCurrentDecision() : null;
    }
    
    /**
     * Get decision context for an entity
     * @param entityId Entity identifier
     * @return Decision context, or null if not found
     */
    public DecisionContext getDecisionContext(String entityId) {
        return entityContexts.get(entityId);
    }
    
    /**
     * Clear all decisions for an entity
     * @param entityId Entity identifier
     */
    public void clearEntityDecisions(String entityId) {
        entityDecisions.remove(entityId);
        entityContexts.remove(entityId);
    }
    
    /**
     * Get debug information for an entity's decisions
     * @param entityId Entity identifier
     * @return Debug information string
     */
    public String getEntityDebugInfo(String entityId) {
        StringBuilder sb = new StringBuilder();
        sb.append("Decision System - Entity: ").append(entityId).append("\n");
        
        List<Decision> decisions = entityDecisions.get(entityId);
        if (decisions != null) {
            sb.append("Registered Decisions: ").append(decisions.size()).append("\n");
            for (Decision decision : decisions) {
                sb.append("  - ").append(decision.getName()).append("\n");
            }
        } else {
            sb.append("No decisions registered\n");
        }
        
        DecisionContext context = entityContexts.get(entityId);
        if (context != null) {
            sb.append("Current Decision: ").append(context.getCurrentDecision()).append("\n");
            sb.append("Executions: ").append(context.getExecutionCount()).append("\n");
            sb.append("Last Update: ").append(System.currentTimeMillis() - context.getLastUpdateTime()).append("ms ago\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Get overall debug information
     * @return Debug information string
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("DecisionSystem Debug Info:\n");
        sb.append("Entities with decisions: ").append(entityDecisions.size()).append("\n");
        sb.append("Decision threshold: ").append(decisionThreshold).append("\n");
        sb.append("Logging enabled: ").append(enableLogging).append("\n");
        
        int totalDecisions = entityDecisions.values().stream()
                                          .mapToInt(List::size)
                                          .sum();
        sb.append("Total decisions: ").append(totalDecisions).append("\n");
        
        return sb.toString();
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        entityDecisions.clear();
        entityContexts.clear();
        logger.info("DecisionSystem cleaned up");
    }
    
    /**
     * Result of a decision evaluation
     */
    public static class DecisionResult {
        private final Decision decision;
        private final float utility;
        
        public DecisionResult(Decision decision, float utility) {
            this.decision = decision;
            this.utility = utility;
        }
        
        public Decision getDecision() { return decision; }
        public float getUtility() { return utility; }
    }
    
    /**
     * Context for decision making
     */
    public static class DecisionContext {
        private final String entityId;
        private String currentDecision;
        private long lastDecisionTime;
        private long lastUpdateTime;
        private int executionCount;
        private final Map<String, Object> contextData;
        
        public DecisionContext(String entityId) {
            this.entityId = entityId;
            this.contextData = new ConcurrentHashMap<>();
            this.lastUpdateTime = System.currentTimeMillis();
            this.executionCount = 0;
        }
        
        // Getters and setters
        public String getEntityId() { return entityId; }
        
        public String getCurrentDecision() { return currentDecision; }
        public void setCurrentDecision(String currentDecision) { this.currentDecision = currentDecision; }
        
        public long getLastDecisionTime() { return lastDecisionTime; }
        public void setLastDecisionTime(long lastDecisionTime) { this.lastDecisionTime = lastDecisionTime; }
        
        public long getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
        
        public int getExecutionCount() { return executionCount; }
        public void incrementExecutionCount() { this.executionCount++; }
        
        /**
         * Store context data
         * @param key Data key
         * @param value Data value
         */
        public void setContextData(String key, Object value) {
            contextData.put(key, value);
        }
        
        /**
         * Retrieve context data
         * @param key Data key
         * @param type Expected type
         * @return Data value, or null if not found
         */
        @SuppressWarnings("unchecked")
        public <T> T getContextData(String key, Class<T> type) {
            Object value = contextData.get(key);
            if (value != null && type.isAssignableFrom(value.getClass())) {
                return (T) value;
            }
            return null;
        }
        
        /**
         * Check if context data exists
         * @param key Data key
         * @return true if exists
         */
        public boolean hasContextData(String key) {
            return contextData.containsKey(key);
        }
        
        /**
         * Get time since last decision change
         * @return Time in milliseconds
         */
        public long getTimeSinceLastDecision() {
            return System.currentTimeMillis() - lastDecisionTime;
        }
    }
}