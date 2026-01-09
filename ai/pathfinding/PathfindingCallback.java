package engine.ai.pathfinding;

/**
 * Callback interface for asynchronous pathfinding operations
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public interface PathfindingCallback {
    
    /**
     * Called when a path is successfully found
     * @param requestId The ID of the pathfinding request
     * @param path The found path, or null if no path exists
     */
    void onPathFound(String requestId, Path path);
    
    /**
     * Called when pathfinding fails
     * @param requestId The ID of the pathfinding request
     * @param errorMessage Description of the error
     */
    void onPathFailed(String requestId, String errorMessage);
}