package engine.ai.debug;

import engine.ai.AIManager;
import engine.ai.AIEntity;
import engine.ai.AIBlackboard;
import engine.ai.behavior.BehaviorTreeSystem;
import engine.ai.pathfinding.PathfindingSystem;
import engine.ai.navigation.NavigationMesh;
import engine.ai.decision.DecisionSystem;
import engine.logging.LogManager;
import engine.config.ConfigurationManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/**
 * AI debugging and visualization system
 * Provides comprehensive debugging tools for AI systems
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class AIDebugger {
    
    private static final LogManager logger = LogManager.getInstance();
    private static final ConfigurationManager config = ConfigurationManager.getInstance();
    
    private final AIManager aiManager;
    private final Map<String, AIDebugInfo> debugInfo;
    private final boolean enableVisualization;
    private final boolean enableProfiling;
    private final AIDebugRenderer debugRenderer;
    
    // Debug categories
    private boolean showPathfinding = true;
    private boolean showBehaviorTrees = true;
    private boolean showDecisions = true;
    private boolean showNavMesh = false;
    private boolean showEntityStates = true;
    private boolean showPerformanceMetrics = true;
    
    public AIDebugger(AIManager aiManager) {
        this.aiManager = aiManager;
        this.debugInfo = new ConcurrentHashMap<>();
        this.enableVisualization = config.getBoolean("ai.debug.enableVisualization", true);
        this.enableProfiling = config.getBoolean("ai.debug.enableProfiling", true);
        this.debugRenderer = new AIDebugRenderer();
        
        logger.info("AIDebugger initialized - Visualization: " + enableVisualization + 
                   ", Profiling: " + enableProfiling);
    }
    
    /**
     * Update debug information for all AI entities
     * @param deltaTime Time elapsed since last update
     */
    public void update(float deltaTime) {
        if (!enableProfiling) return;
        
        // Update debug info for all entities
        for (String entityId : aiManager.getRegisteredEntities()) {
            updateEntityDebugInfo(entityId, deltaTime);
        }
        
        // Update system-wide debug info
        updateSystemDebugInfo();
    }
    
    /**
     * Update debug information for a specific entity
     */
    private void updateEntityDebugInfo(String entityId, float deltaTime) {
        AIEntity aiEntity = aiManager.getAIEntity(entityId);
        AIBlackboard blackboard = aiManager.getEntityBlackboard(entityId);
        
        if (aiEntity == null || blackboard == null) return;
        
        AIDebugInfo info = debugInfo.computeIfAbsent(entityId, k -> new AIDebugInfo(entityId));
        
        // Update entity state
        info.setCurrentState(aiEntity.getCurrentState().toString());
        info.setActive(aiEntity.isActive());
        info.setUpdateCount(aiEntity.getUpdateCount());
        
        // Update behavior tree info
        if (showBehaviorTrees) {
            String behaviorTreeId = aiEntity.getBehaviorTreeId();
            if (behaviorTreeId != null) {
                BehaviorTreeSystem.BehaviorTreeStats stats = 
                    aiManager.getBehaviorTreeSystem().getTreeStats(behaviorTreeId);
                if (stats != null) {
                    info.setBehaviorTreeExecutions(stats.getExecutionCount());
                    info.setBehaviorTreeAvgTime(stats.getAverageExecutionTime());
                }
            }
        }
        
        // Update decision info
        if (showDecisions) {
            DecisionSystem decisionSystem = aiManager.getDecisionSystem();
            String currentDecision = decisionSystem.getCurrentDecision(entityId);
            info.setCurrentDecision(currentDecision);
        }
        
        // Update pathfinding info
        if (showPathfinding) {
            // Check if entity has active pathfinding
            org.joml.Vector3f target = blackboard.getValue("moveTarget", org.joml.Vector3f.class);
            info.setHasTarget(target != null);
            if (target != null) {
                info.setTargetPosition(target);
            }
        }
        
        info.setLastUpdateTime(System.currentTimeMillis());
    }
    
    /**
     * Update system-wide debug information
     */
    private void updateSystemDebugInfo() {
        // This could collect system-wide metrics, performance data, etc.
    }
    
    /**
     * Get debug information for a specific entity
     * @param entityId Entity identifier
     * @return Debug information, or null if not found
     */
    public AIDebugInfo getEntityDebugInfo(String entityId) {
        return debugInfo.get(entityId);
    }
    
    /**
     * Get debug information for all entities
     * @return Map of entity ID to debug info
     */
    public Map<String, AIDebugInfo> getAllDebugInfo() {
        return new ConcurrentHashMap<>(debugInfo);
    }
    
    /**
     * Generate a comprehensive debug report
     * @return Debug report string
     */
    public String generateDebugReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== AI Framework Debug Report ===\n\n");
        
        // System overview
        sb.append("System Overview:\n");
        sb.append(aiManager.getDebugInfo()).append("\n");
        
        // Pathfinding system
        if (showPathfinding) {
            sb.append("Pathfinding System:\n");
            PathfindingSystem pathfinding = aiManager.getPathfindingSystem();
            sb.append("  Active Requests: ").append(pathfinding.getActiveRequestCount()).append("\n");
        }
        
        // Behavior tree system
        if (showBehaviorTrees) {
            sb.append("\nBehavior Tree System:\n");
            sb.append(aiManager.getBehaviorTreeSystem().getDebugInfo()).append("\n");
        }
        
        // Decision system
        if (showDecisions) {
            sb.append("Decision System:\n");
            sb.append(aiManager.getDecisionSystem().getDebugInfo()).append("\n");
        }
        
        // Navigation mesh
        if (showNavMesh) {
            sb.append("Navigation Mesh:\n");
            sb.append("  ").append(aiManager.getNavigationMesh().getStats()).append("\n");
        }
        
        // Entity details
        if (showEntityStates) {
            sb.append("\nEntity States:\n");
            for (Map.Entry<String, AIDebugInfo> entry : debugInfo.entrySet()) {
                String entityId = entry.getKey();
                AIDebugInfo info = entry.getValue();
                
                sb.append("  ").append(entityId).append(":\n");
                sb.append("    State: ").append(info.getCurrentState()).append("\n");
                sb.append("    Active: ").append(info.isActive()).append("\n");
                sb.append("    Updates: ").append(info.getUpdateCount()).append("\n");
                
                if (info.getCurrentDecision() != null) {
                    sb.append("    Decision: ").append(info.getCurrentDecision()).append("\n");
                }
                
                if (info.hasTarget()) {
                    sb.append("    Target: ").append(info.getTargetPosition()).append("\n");
                }
                
                sb.append("    BT Executions: ").append(info.getBehaviorTreeExecutions()).append("\n");
                sb.append("    BT Avg Time: ").append(String.format("%.2f", info.getBehaviorTreeAvgTime())).append("ms\n");
                sb.append("\n");
            }
        }
        
        // Performance metrics
        if (showPerformanceMetrics) {
            sb.append("Performance Metrics:\n");
            AIManager.AIPerformanceMonitor monitor = aiManager.getPerformanceMonitor();
            sb.append("  Average Update Time: ").append(String.format("%.2f", monitor.getAverageUpdateTime())).append("ms\n");
            sb.append("  Average Entities/Update: ").append(String.format("%.1f", monitor.getAverageEntitiesPerUpdate())).append("\n");
            sb.append("  Total Updates: ").append(monitor.getUpdateCount()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Generate a summary debug report
     * @return Summary debug report
     */
    public String generateSummaryReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("AI Framework Summary:\n");
        sb.append("Entities: ").append(aiManager.getEntityCount());
        sb.append(" (").append(aiManager.getActiveEntityCount()).append(" active)\n");
        
        PathfindingSystem pathfinding = aiManager.getPathfindingSystem();
        sb.append("Pathfinding Requests: ").append(pathfinding.getActiveRequestCount()).append("\n");
        
        BehaviorTreeSystem behaviorTrees = aiManager.getBehaviorTreeSystem();
        sb.append("Behavior Trees: ").append(behaviorTrees.getRegisteredTrees().size()).append("\n");
        
        AIManager.AIPerformanceMonitor monitor = aiManager.getPerformanceMonitor();
        sb.append("Avg Update Time: ").append(String.format("%.2f", monitor.getAverageUpdateTime())).append("ms\n");
        
        return sb.toString();
    }
    
    /**
     * Clear debug information for a specific entity
     * @param entityId Entity identifier
     */
    public void clearEntityDebugInfo(String entityId) {
        debugInfo.remove(entityId);
    }
    
    /**
     * Clear all debug information
     */
    public void clearAllDebugInfo() {
        debugInfo.clear();
    }
    
    // Debug category toggles
    public void setShowPathfinding(boolean show) { this.showPathfinding = show; }
    public void setShowBehaviorTrees(boolean show) { this.showBehaviorTrees = show; }
    public void setShowDecisions(boolean show) { this.showDecisions = show; }
    public void setShowNavMesh(boolean show) { this.showNavMesh = show; }
    public void setShowEntityStates(boolean show) { this.showEntityStates = show; }
    public void setShowPerformanceMetrics(boolean show) { this.showPerformanceMetrics = show; }
    
    // Getters for debug categories
    public boolean isShowPathfinding() { return showPathfinding; }
    public boolean isShowBehaviorTrees() { return showBehaviorTrees; }
    public boolean isShowDecisions() { return showDecisions; }
    public boolean isShowNavMesh() { return showNavMesh; }
    public boolean isShowEntityStates() { return showEntityStates; }
    public boolean isShowPerformanceMetrics() { return showPerformanceMetrics; }
    
    /**
     * Get the debug renderer
     * @return Debug renderer
     */
    public AIDebugRenderer getDebugRenderer() {
        return debugRenderer;
    }
    
    /**
     * Check if visualization is enabled
     * @return true if enabled
     */
    public boolean isVisualizationEnabled() {
        return enableVisualization;
    }
    
    /**
     * Check if profiling is enabled
     * @return true if enabled
     */
    public boolean isProfilingEnabled() {
        return enableProfiling;
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        debugInfo.clear();
        debugRenderer.cleanup();
        logger.info("AIDebugger cleaned up");
    }
}