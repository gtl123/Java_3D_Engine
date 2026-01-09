package engine.ai;

import engine.ai.behavior.BehaviorTreeSystem;
import engine.ai.pathfinding.PathfindingSystem;
import engine.ai.navigation.NavigationMesh;
import engine.ai.decision.DecisionSystem;
import engine.logging.LogManager;
import engine.config.ConfigurationManager;
import engine.entity.Entity;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central AI coordination system for managing AI entities and subsystems
 * Provides unified access to all AI systems and manages AI entity lifecycle
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class AIManager {
    
    private static final LogManager logger = LogManager.getInstance();
    private static final ConfigurationManager config = ConfigurationManager.getInstance();
    
    // AI Subsystems
    private final PathfindingSystem pathfindingSystem;
    private final BehaviorTreeSystem behaviorTreeSystem;
    private final NavigationMesh navigationMesh;
    private final DecisionSystem decisionSystem;
    
    // AI Entity Management
    private final Map<String, AIEntity> aiEntities;
    private final Map<String, AIBlackboard> entityBlackboards;
    private final AtomicLong entityIdCounter;
    
    // Update scheduling
    private final ScheduledExecutorService updateExecutor;
    private final float updateFrequency;
    private final boolean enableAsyncUpdates;
    
    // Performance monitoring
    private final AIPerformanceMonitor performanceMonitor;
    
    /**
     * Create a new AI Manager
     */
    public AIManager() {
        // Initialize subsystems
        this.pathfindingSystem = new PathfindingSystem();
        this.behaviorTreeSystem = new BehaviorTreeSystem();
        this.navigationMesh = new NavigationMesh();
        this.decisionSystem = new DecisionSystem();
        
        // Initialize entity management
        this.aiEntities = new ConcurrentHashMap<>();
        this.entityBlackboards = new ConcurrentHashMap<>();
        this.entityIdCounter = new AtomicLong(0);
        
        // Configuration
        this.updateFrequency = config.getFloat("ai.manager.updateFrequency", 30.0f); // 30 FPS default
        this.enableAsyncUpdates = config.getBoolean("ai.manager.enableAsyncUpdates", true);
        
        // Initialize update executor
        int threadCount = config.getInt("ai.manager.threadCount", 2);
        this.updateExecutor = Executors.newScheduledThreadPool(threadCount);
        
        // Performance monitoring
        this.performanceMonitor = new AIPerformanceMonitor();
        
        // Start async updates if enabled
        if (enableAsyncUpdates) {
            startAsyncUpdates();
        }
        
        logger.info("AIManager initialized with " + threadCount + " threads, " + 
                   updateFrequency + " Hz update frequency");
    }
    
    /**
     * Register an AI entity with the manager
     * @param entity The entity to add AI to
     * @param behaviorTreeId ID of the behavior tree to use
     * @return Unique AI entity ID
     */
    public String registerAIEntity(Entity entity, String behaviorTreeId) {
        String aiEntityId = "ai_entity_" + entityIdCounter.incrementAndGet();
        
        // Create AI entity wrapper
        AIEntity aiEntity = new AIEntity(aiEntityId, entity, behaviorTreeId);
        
        // Create blackboard for the entity
        AIBlackboard blackboard = new AIBlackboard(aiEntityId);
        
        // Initialize blackboard with common data
        blackboard.setValue("entity", entity);
        blackboard.setValue("pathfindingSystem", pathfindingSystem);
        blackboard.setValue("navigationMesh", navigationMesh);
        blackboard.setValue("decisionSystem", decisionSystem);
        blackboard.setValue("aiManager", this);
        
        // Register entity
        aiEntities.put(aiEntityId, aiEntity);
        entityBlackboards.put(aiEntityId, blackboard);
        
        logger.info("Registered AI entity: " + aiEntityId + " with behavior tree: " + behaviorTreeId);
        return aiEntityId;
    }
    
    /**
     * Unregister an AI entity
     * @param aiEntityId AI entity ID
     * @return true if removed successfully
     */
    public boolean unregisterAIEntity(String aiEntityId) {
        AIEntity removed = aiEntities.remove(aiEntityId);
        entityBlackboards.remove(aiEntityId);
        
        if (removed != null) {
            logger.info("Unregistered AI entity: " + aiEntityId);
            return true;
        }
        return false;
    }
    
    /**
     * Get an AI entity by ID
     * @param aiEntityId AI entity ID
     * @return AI entity, or null if not found
     */
    public AIEntity getAIEntity(String aiEntityId) {
        return aiEntities.get(aiEntityId);
    }
    
    /**
     * Get the blackboard for an AI entity
     * @param aiEntityId AI entity ID
     * @return Blackboard, or null if not found
     */
    public AIBlackboard getEntityBlackboard(String aiEntityId) {
        return entityBlackboards.get(aiEntityId);
    }
    
    /**
     * Update all AI entities (synchronous)
     * @param deltaTime Time elapsed since last update
     */
    public void updateAll(float deltaTime) {
        long startTime = System.nanoTime();
        
        int updatedEntities = 0;
        for (Map.Entry<String, AIEntity> entry : aiEntities.entrySet()) {
            String entityId = entry.getKey();
            AIEntity aiEntity = entry.getValue();
            AIBlackboard blackboard = entityBlackboards.get(entityId);
            
            if (blackboard != null && aiEntity.isActive()) {
                try {
                    updateAIEntity(aiEntity, blackboard, deltaTime);
                    updatedEntities++;
                } catch (Exception e) {
                    logger.error("Error updating AI entity " + entityId, e);
                }
            }
        }
        
        // Update subsystems
        behaviorTreeSystem.updateAll(deltaTime);
        
        // Performance monitoring
        long endTime = System.nanoTime();
        performanceMonitor.recordUpdate(endTime - startTime, updatedEntities);
    }
    
    /**
     * Update a single AI entity
     */
    private void updateAIEntity(AIEntity aiEntity, AIBlackboard blackboard, float deltaTime) {
        // Update blackboard with current entity state
        updateEntityBlackboard(aiEntity, blackboard);
        
        // Execute behavior tree
        String behaviorTreeId = aiEntity.getBehaviorTreeId();
        if (behaviorTreeId != null) {
            behaviorTreeSystem.executeTree(behaviorTreeId, blackboard, deltaTime);
        }
        
        // Update decision system if needed
        if (aiEntity.hasDecisionMaking()) {
            decisionSystem.updateEntity(aiEntity.getId(), blackboard, deltaTime);
        }
        
        aiEntity.incrementUpdateCount();
    }
    
    /**
     * Update entity blackboard with current state
     */
    private void updateEntityBlackboard(AIEntity aiEntity, AIBlackboard blackboard) {
        Entity entity = aiEntity.getEntity();
        
        // Update position
        if (entity != null) {
            blackboard.setValue("position", entity.getWorldTransform().getTranslation(new org.joml.Vector3f()));
        }
        
        // Update timestamp
        blackboard.setValue("currentTime", System.currentTimeMillis());
        blackboard.setValue("deltaTime", System.currentTimeMillis() - aiEntity.getLastUpdateTime());
        
        aiEntity.setLastUpdateTime(System.currentTimeMillis());
    }
    
    /**
     * Start asynchronous AI updates
     */
    private void startAsyncUpdates() {
        long updateIntervalMs = (long) (1000.0f / updateFrequency);
        
        updateExecutor.scheduleAtFixedRate(() -> {
            try {
                updateAll(1.0f / updateFrequency);
            } catch (Exception e) {
                logger.error("Error in async AI update", e);
            }
        }, 0, updateIntervalMs, TimeUnit.MILLISECONDS);
        
        logger.info("Started async AI updates at " + updateFrequency + " Hz");
    }
    
    /**
     * Set the target for an AI entity
     * @param aiEntityId AI entity ID
     * @param target Target position
     */
    public void setEntityTarget(String aiEntityId, org.joml.Vector3f target) {
        AIBlackboard blackboard = entityBlackboards.get(aiEntityId);
        if (blackboard != null) {
            blackboard.setValue("moveTarget", target);
        }
    }
    
    /**
     * Enable/disable an AI entity
     * @param aiEntityId AI entity ID
     * @param active Active state
     */
    public void setEntityActive(String aiEntityId, boolean active) {
        AIEntity aiEntity = aiEntities.get(aiEntityId);
        if (aiEntity != null) {
            aiEntity.setActive(active);
        }
    }
    
    /**
     * Get all registered AI entity IDs
     * @return Set of AI entity IDs
     */
    public Set<String> getRegisteredEntities() {
        return aiEntities.keySet();
    }
    
    /**
     * Get the number of registered AI entities
     * @return Entity count
     */
    public int getEntityCount() {
        return aiEntities.size();
    }
    
    /**
     * Get the number of active AI entities
     * @return Active entity count
     */
    public int getActiveEntityCount() {
        return (int) aiEntities.values().stream().filter(AIEntity::isActive).count();
    }
    
    // Subsystem getters
    public PathfindingSystem getPathfindingSystem() { return pathfindingSystem; }
    public BehaviorTreeSystem getBehaviorTreeSystem() { return behaviorTreeSystem; }
    public NavigationMesh getNavigationMesh() { return navigationMesh; }
    public DecisionSystem getDecisionSystem() { return decisionSystem; }
    public AIPerformanceMonitor getPerformanceMonitor() { return performanceMonitor; }
    
    /**
     * Get debug information about the AI manager
     * @return Debug information string
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("AIManager Debug Info:\n");
        sb.append("Entities: ").append(aiEntities.size()).append(" (").append(getActiveEntityCount()).append(" active)\n");
        sb.append("Update Frequency: ").append(updateFrequency).append(" Hz\n");
        sb.append("Async Updates: ").append(enableAsyncUpdates).append("\n");
        sb.append("Performance: ").append(performanceMonitor.getAverageUpdateTime()).append("ms avg\n");
        sb.append("\nSubsystems:\n");
        sb.append("- Pathfinding: ").append(pathfindingSystem.getActiveRequestCount()).append(" active requests\n");
        sb.append("- Behavior Trees: ").append(behaviorTreeSystem.getRegisteredTrees().size()).append(" registered\n");
        sb.append("- Navigation Mesh: ").append(navigationMesh.getStats()).append("\n");
        
        return sb.toString();
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        // Stop async updates
        updateExecutor.shutdown();
        try {
            if (!updateExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                updateExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            updateExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Cleanup subsystems
        pathfindingSystem.cleanup();
        behaviorTreeSystem.cleanup();
        navigationMesh.clear();
        
        // Clear entities
        aiEntities.clear();
        entityBlackboards.clear();
        
        logger.info("AIManager cleaned up");
    }
    
    /**
     * Performance monitoring for AI updates
     */
    public static class AIPerformanceMonitor {
        private long totalUpdateTime;
        private long updateCount;
        private int totalEntitiesUpdated;
        private long lastResetTime;
        
        public AIPerformanceMonitor() {
            reset();
        }
        
        public void recordUpdate(long updateTimeNanos, int entitiesUpdated) {
            totalUpdateTime += updateTimeNanos;
            updateCount++;
            totalEntitiesUpdated += entitiesUpdated;
        }
        
        public double getAverageUpdateTime() {
            return updateCount > 0 ? (totalUpdateTime / 1_000_000.0) / updateCount : 0.0;
        }
        
        public double getAverageEntitiesPerUpdate() {
            return updateCount > 0 ? (double) totalEntitiesUpdated / updateCount : 0.0;
        }
        
        public long getUpdateCount() {
            return updateCount;
        }
        
        public void reset() {
            totalUpdateTime = 0;
            updateCount = 0;
            totalEntitiesUpdated = 0;
            lastResetTime = System.currentTimeMillis();
        }
        
        public long getTimeSinceReset() {
            return System.currentTimeMillis() - lastResetTime;
        }
    }
}