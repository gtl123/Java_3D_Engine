package engine.ai.pathfinding;

import org.joml.Vector3f;
import java.util.concurrent.Future;

/**
 * Represents a pathfinding request with associated metadata
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class PathfindingRequest {
    
    private final String requestId;
    private final Vector3f start;
    private final Vector3f goal;
    private final PathfindingCallback callback;
    private final ObstacleChecker obstacleChecker;
    private final long creationTime;
    private Future<Path> future;
    
    /**
     * Create a new pathfinding request
     * @param requestId Unique identifier for this request
     * @param start Starting position
     * @param goal Goal position
     * @param callback Callback to receive results
     * @param obstacleChecker Function to check obstacles
     */
    public PathfindingRequest(String requestId, Vector3f start, Vector3f goal, 
                             PathfindingCallback callback, ObstacleChecker obstacleChecker) {
        this.requestId = requestId;
        this.start = new Vector3f(start);
        this.goal = new Vector3f(goal);
        this.callback = callback;
        this.obstacleChecker = obstacleChecker;
        this.creationTime = System.currentTimeMillis();
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public Vector3f getStart() {
        return new Vector3f(start);
    }
    
    public Vector3f getGoal() {
        return new Vector3f(goal);
    }
    
    public PathfindingCallback getCallback() {
        return callback;
    }
    
    public ObstacleChecker getObstacleChecker() {
        return obstacleChecker;
    }
    
    public long getCreationTime() {
        return creationTime;
    }
    
    public Future<Path> getFuture() {
        return future;
    }
    
    public void setFuture(Future<Path> future) {
        this.future = future;
    }
    
    public long getAge() {
        return System.currentTimeMillis() - creationTime;
    }
    
    @Override
    public String toString() {
        return "PathfindingRequest{" +
                "requestId='" + requestId + '\'' +
                ", start=" + start +
                ", goal=" + goal +
                ", age=" + getAge() + "ms" +
                '}';
    }
}