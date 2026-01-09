package engine.ai.behavior.action;

import engine.ai.AIBlackboard;
import engine.ai.behavior.AbstractBehaviorNode;
import engine.ai.pathfinding.Path;
import engine.ai.pathfinding.PathfindingSystem;
import engine.ai.pathfinding.PathfindingCallback;
import engine.ai.pathfinding.ObstacleChecker;
import engine.entity.Entity;
import org.joml.Vector3f;

/**
 * Action node that moves an entity to a target position using pathfinding
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class MoveToAction extends AbstractBehaviorNode {
    
    private static final String TARGET_KEY = "moveTarget";
    private static final String ENTITY_KEY = "entity";
    private static final String PATHFINDING_SYSTEM_KEY = "pathfindingSystem";
    private static final String OBSTACLE_CHECKER_KEY = "obstacleChecker";
    
    private final float moveSpeed;
    private final float arrivalThreshold;
    private Path currentPath;
    private String pathfindingRequestId;
    private boolean pathRequested;
    private Vector3f lastTarget;
    
    /**
     * Create a new move to action
     * @param name Name of the action
     * @param moveSpeed Movement speed in units per second
     * @param arrivalThreshold Distance threshold for arrival
     */
    public MoveToAction(String name, float moveSpeed, float arrivalThreshold) {
        super(name);
        this.moveSpeed = moveSpeed;
        this.arrivalThreshold = arrivalThreshold;
        this.pathRequested = false;
    }
    
    /**
     * Create a move to action with default values
     * @param name Name of the action
     */
    public MoveToAction(String name) {
        this(name, 5.0f, 1.0f);
    }
    
    @Override
    protected Status doExecute(AIBlackboard blackboard, float deltaTime) {
        // Get required components from blackboard
        Entity entity = blackboard.getValue(ENTITY_KEY, Entity.class);
        Vector3f target = blackboard.getValue(TARGET_KEY, Vector3f.class);
        PathfindingSystem pathfindingSystem = blackboard.getValue(PATHFINDING_SYSTEM_KEY, PathfindingSystem.class);
        ObstacleChecker obstacleChecker = blackboard.getValue(OBSTACLE_CHECKER_KEY, ObstacleChecker.class);
        
        if (entity == null || target == null) {
            return Status.FAILURE;
        }
        
        Vector3f currentPosition = getCurrentPosition(entity);
        
        // Check if we've arrived
        if (currentPosition.distance(target) <= arrivalThreshold) {
            return Status.SUCCESS;
        }
        
        // Check if target has changed
        if (lastTarget == null || !lastTarget.equals(target)) {
            lastTarget = new Vector3f(target);
            currentPath = null;
            pathRequested = false;
            
            // Cancel any existing pathfinding request
            if (pathfindingRequestId != null && pathfindingSystem != null) {
                pathfindingSystem.cancelRequest(pathfindingRequestId);
                pathfindingRequestId = null;
            }
        }
        
        // Request pathfinding if needed
        if (currentPath == null && !pathRequested && pathfindingSystem != null && obstacleChecker != null) {
            pathRequested = true;
            pathfindingRequestId = pathfindingSystem.findPathAsync(
                currentPosition, 
                target, 
                new PathfindingCallbackImpl(), 
                obstacleChecker
            );
        }
        
        // Move along current path
        if (currentPath != null) {
            return followPath(entity, currentPath, deltaTime);
        }
        
        // No path yet, wait for pathfinding
        return Status.RUNNING;
    }
    
    /**
     * Follow the current path
     */
    private Status followPath(Entity entity, Path path, float deltaTime) {
        Vector3f currentPosition = getCurrentPosition(entity);
        Vector3f currentWaypoint = path.getCurrentWaypoint();
        
        if (currentWaypoint == null) {
            // Path completed
            return Status.SUCCESS;
        }
        
        // Check if we've reached the current waypoint
        if (currentPosition.distance(currentWaypoint) <= arrivalThreshold) {
            if (!path.advanceWaypoint()) {
                // Reached the end of the path
                return Status.SUCCESS;
            }
            currentWaypoint = path.getCurrentWaypoint();
        }
        
        if (currentWaypoint != null) {
            // Move towards the current waypoint
            Vector3f direction = new Vector3f(currentWaypoint).sub(currentPosition).normalize();
            Vector3f movement = new Vector3f(direction).mul(moveSpeed * deltaTime);
            
            // Apply movement to entity
            moveEntity(entity, movement);
        }
        
        return Status.RUNNING;
    }
    
    /**
     * Get the current position of the entity
     */
    private Vector3f getCurrentPosition(Entity entity) {
        // Extract position from entity's world transform
        return entity.getWorldTransform().getTranslation(new Vector3f());
    }
    
    /**
     * Move the entity by the given movement vector
     */
    private void moveEntity(Entity entity, Vector3f movement) {
        Vector3f currentPos = getCurrentPosition(entity);
        currentPos.add(movement);
        entity.setLocalPosition(currentPos.x, currentPos.y, currentPos.z);
    }
    
    @Override
    protected void doReset() {
        currentPath = null;
        pathRequested = false;
        lastTarget = null;
        
        // Cancel any active pathfinding request
        if (pathfindingRequestId != null) {
            // Note: We can't access pathfinding system here without blackboard
            // The system should handle cleanup of abandoned requests
            pathfindingRequestId = null;
        }
    }
    
    @Override
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDebugInfo());
        sb.append(" - Speed: ").append(moveSpeed);
        
        if (currentPath != null) {
            sb.append(", Path: ").append(currentPath.getCurrentWaypointIndex())
              .append("/").append(currentPath.getWaypointCount());
        } else if (pathRequested) {
            sb.append(", Pathfinding...");
        } else {
            sb.append(", No path");
        }
        
        return sb.toString();
    }
    
    /**
     * Pathfinding callback implementation
     */
    private class PathfindingCallbackImpl implements PathfindingCallback {
        @Override
        public void onPathFound(String requestId, Path path) {
            if (requestId.equals(pathfindingRequestId)) {
                currentPath = path;
                pathfindingRequestId = null;
                pathRequested = false;
            }
        }
        
        @Override
        public void onPathFailed(String requestId, String errorMessage) {
            if (requestId.equals(pathfindingRequestId)) {
                pathfindingRequestId = null;
                pathRequested = false;
                // Could set a flag to indicate pathfinding failed
            }
        }
    }
    
    /**
     * Create a move to action
     * @param name Name of the action
     * @param moveSpeed Movement speed
     * @param arrivalThreshold Arrival threshold
     * @return New move to action
     */
    public static MoveToAction create(String name, float moveSpeed, float arrivalThreshold) {
        return new MoveToAction(name, moveSpeed, arrivalThreshold);
    }
    
    /**
     * Create a move to action with default speed and threshold
     * @param name Name of the action
     * @return New move to action
     */
    public static MoveToAction create(String name) {
        return new MoveToAction(name);
    }
}