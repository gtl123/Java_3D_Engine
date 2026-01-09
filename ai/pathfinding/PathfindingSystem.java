package engine.ai.pathfinding;

import engine.logging.LogManager;
import engine.config.ConfigurationManager;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Advanced pathfinding system with A* algorithm and hierarchical optimization
 * Supports multi-threaded pathfinding to prevent frame drops
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class PathfindingSystem {
    
    private static final LogManager logger = LogManager.getInstance();
    private static final ConfigurationManager config = ConfigurationManager.getInstance();
    
    private final ExecutorService pathfindingExecutor;
    private final Map<String, PathfindingRequest> activeRequests;
    private final PathCache pathCache;
    private final HierarchicalPathfinder hierarchicalPathfinder;
    
    // Configuration
    private final int maxPathLength;
    private final float heuristicWeight;
    private final boolean enableHierarchical;
    private final int cacheSize;
    
    public PathfindingSystem() {
        // Load configuration
        this.maxPathLength = config.getInt("ai.pathfinding.maxPathLength", 1000);
        this.heuristicWeight = (float) config.getDouble("ai.pathfinding.heuristicWeight", 1.2);
        this.enableHierarchical = config.getBoolean("ai.pathfinding.enableHierarchical", true);
        this.cacheSize = config.getInt("ai.pathfinding.cacheSize", 1000);
        
        // Initialize thread pool for async pathfinding
        int threadCount = config.getInt("ai.pathfinding.threadCount", 2);
        this.pathfindingExecutor = Executors.newFixedThreadPool(threadCount);
        
        this.activeRequests = new ConcurrentHashMap<>();
        this.pathCache = new PathCache(cacheSize);
        this.hierarchicalPathfinder = new HierarchicalPathfinder();
        
        logger.info("PathfindingSystem initialized with " + threadCount + " threads");
    }
    
    /**
     * Find a path from start to goal asynchronously
     * @param start Starting position
     * @param goal Goal position
     * @param callback Callback to receive the result
     * @param obstacleChecker Function to check if a position is blocked
     * @return Request ID for tracking
     */
    public String findPathAsync(Vector3f start, Vector3f goal, PathfindingCallback callback, ObstacleChecker obstacleChecker) {
        String requestId = UUID.randomUUID().toString();
        
        PathfindingRequest request = new PathfindingRequest(requestId, start, goal, callback, obstacleChecker);
        activeRequests.put(requestId, request);
        
        Future<Path> future = pathfindingExecutor.submit(() -> {
            try {
                Path result = findPath(start, goal, obstacleChecker);
                callback.onPathFound(requestId, result);
                return result;
            } catch (Exception e) {
                logger.error("Pathfinding error for request " + requestId, e);
                callback.onPathFailed(requestId, e.getMessage());
                return null;
            } finally {
                activeRequests.remove(requestId);
            }
        });
        
        request.setFuture(future);
        return requestId;
    }
    
    /**
     * Find a path synchronously using A* algorithm
     * @param start Starting position
     * @param goal Goal position
     * @param obstacleChecker Function to check if a position is blocked
     * @return The found path, or null if no path exists
     */
    public Path findPath(Vector3f start, Vector3f goal, ObstacleChecker obstacleChecker) {
        long startTime = System.nanoTime();
        
        // Check cache first
        String cacheKey = getCacheKey(start, goal);
        Path cachedPath = pathCache.get(cacheKey);
        if (cachedPath != null && isPathValid(cachedPath, obstacleChecker)) {
            logger.debug("Using cached path for " + cacheKey);
            return cachedPath;
        }
        
        Path result;
        if (enableHierarchical && getDistance(start, goal) > 50.0f) {
            // Use hierarchical pathfinding for long distances
            result = hierarchicalPathfinder.findPath(start, goal, obstacleChecker);
        } else {
            // Use standard A* for shorter distances
            result = findPathAStar(start, goal, obstacleChecker);
        }
        
        // Cache the result if valid
        if (result != null && !result.isEmpty()) {
            pathCache.put(cacheKey, result);
        }
        
        long endTime = System.nanoTime();
        float duration = (endTime - startTime) / 1_000_000.0f; // Convert to milliseconds
        
        logger.debug("Pathfinding completed in " + duration + "ms, path length: " + 
                    (result != null ? result.getWaypoints().size() : 0));
        
        return result;
    }
    
    /**
     * A* pathfinding algorithm implementation
     */
    private Path findPathAStar(Vector3f start, Vector3f goal, ObstacleChecker obstacleChecker) {
        Vector3i startNode = worldToGrid(start);
        Vector3i goalNode = worldToGrid(goal);
        
        PriorityQueue<AStarNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fCost));
        Set<Vector3i> closedSet = new HashSet<>();
        Map<Vector3i, AStarNode> allNodes = new HashMap<>();
        
        AStarNode startAStarNode = new AStarNode(startNode, 0, heuristic(startNode, goalNode), null);
        openSet.add(startAStarNode);
        allNodes.put(startNode, startAStarNode);
        
        int iterations = 0;
        while (!openSet.isEmpty() && iterations < maxPathLength) {
            iterations++;
            
            AStarNode current = openSet.poll();
            closedSet.add(current.position);
            
            // Check if we reached the goal
            if (current.position.equals(goalNode)) {
                return reconstructPath(current, start, goal);
            }
            
            // Explore neighbors
            for (Vector3i neighbor : getNeighbors(current.position)) {
                if (closedSet.contains(neighbor) || obstacleChecker.isBlocked(gridToWorld(neighbor))) {
                    continue;
                }
                
                double tentativeGCost = current.gCost + getMovementCost(current.position, neighbor);
                
                AStarNode neighborNode = allNodes.get(neighbor);
                if (neighborNode == null) {
                    neighborNode = new AStarNode(neighbor, tentativeGCost, heuristic(neighbor, goalNode), current);
                    allNodes.put(neighbor, neighborNode);
                    openSet.add(neighborNode);
                } else if (tentativeGCost < neighborNode.gCost) {
                    neighborNode.gCost = tentativeGCost;
                    neighborNode.fCost = neighborNode.gCost + neighborNode.hCost;
                    neighborNode.parent = current;
                    
                    // Re-add to priority queue with updated cost
                    openSet.remove(neighborNode);
                    openSet.add(neighborNode);
                }
            }
        }
        
        logger.debug("No path found from " + start + " to " + goal + " after " + iterations + " iterations");
        return null; // No path found
    }
    
    /**
     * Reconstruct the path from A* result
     */
    private Path reconstructPath(AStarNode goalNode, Vector3f start, Vector3f goal) {
        List<Vector3f> waypoints = new ArrayList<>();
        AStarNode current = goalNode;
        
        while (current != null) {
            waypoints.add(gridToWorld(current.position));
            current = current.parent;
        }
        
        Collections.reverse(waypoints);
        
        // Replace first and last waypoints with exact start/goal positions
        if (!waypoints.isEmpty()) {
            waypoints.set(0, new Vector3f(start));
            waypoints.set(waypoints.size() - 1, new Vector3f(goal));
        }
        
        Path path = new Path(waypoints);
        return smoothPath(path);
    }
    
    /**
     * Smooth the path using simple line-of-sight optimization
     */
    private Path smoothPath(Path originalPath) {
        List<Vector3f> waypoints = originalPath.getWaypoints();
        if (waypoints.size() <= 2) {
            return originalPath;
        }
        
        List<Vector3f> smoothedWaypoints = new ArrayList<>();
        smoothedWaypoints.add(waypoints.get(0));
        
        int current = 0;
        while (current < waypoints.size() - 1) {
            int farthest = current + 1;
            
            // Find the farthest waypoint we can reach directly
            for (int i = current + 2; i < waypoints.size(); i++) {
                if (hasLineOfSight(waypoints.get(current), waypoints.get(i))) {
                    farthest = i;
                } else {
                    break;
                }
            }
            
            smoothedWaypoints.add(waypoints.get(farthest));
            current = farthest;
        }
        
        return new Path(smoothedWaypoints);
    }
    
    /**
     * Check if there's a clear line of sight between two points
     */
    private boolean hasLineOfSight(Vector3f from, Vector3f to) {
        // Simple implementation - can be enhanced with proper ray casting
        Vector3f direction = new Vector3f(to).sub(from);
        float distance = direction.length();
        direction.normalize();
        
        int steps = (int) Math.ceil(distance);
        for (int i = 1; i < steps; i++) {
            Vector3f testPoint = new Vector3f(from).add(new Vector3f(direction).mul(i));
            // This would need integration with the actual obstacle checker
            // For now, assume line of sight is clear
        }
        
        return true;
    }
    
    /**
     * Get neighboring grid positions
     */
    private List<Vector3i> getNeighbors(Vector3i position) {
        List<Vector3i> neighbors = new ArrayList<>();
        
        // 3D neighbors (26 directions)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    
                    neighbors.add(new Vector3i(
                        position.x + dx,
                        position.y + dy,
                        position.z + dz
                    ));
                }
            }
        }
        
        return neighbors;
    }
    
    /**
     * Calculate movement cost between two adjacent grid positions
     */
    private double getMovementCost(Vector3i from, Vector3i to) {
        Vector3i diff = new Vector3i(to).sub(from);
        
        // Diagonal movement costs more
        int nonZeroComponents = 0;
        if (diff.x != 0) nonZeroComponents++;
        if (diff.y != 0) nonZeroComponents++;
        if (diff.z != 0) nonZeroComponents++;
        
        switch (nonZeroComponents) {
            case 1: return 1.0; // Straight movement
            case 2: return 1.414; // Diagonal (2D)
            case 3: return 1.732; // Diagonal (3D)
            default: return 1.0;
        }
    }
    
    /**
     * Heuristic function for A* (Manhattan distance with slight overestimate)
     */
    private double heuristic(Vector3i from, Vector3i to) {
        double dx = Math.abs(to.x - from.x);
        double dy = Math.abs(to.y - from.y);
        double dz = Math.abs(to.z - from.z);
        return (dx + dy + dz) * heuristicWeight;
    }
    
    /**
     * Convert world position to grid coordinates
     */
    private Vector3i worldToGrid(Vector3f worldPos) {
        return new Vector3i(
            (int) Math.floor(worldPos.x),
            (int) Math.floor(worldPos.y),
            (int) Math.floor(worldPos.z)
        );
    }
    
    /**
     * Convert grid coordinates to world position
     */
    private Vector3f gridToWorld(Vector3i gridPos) {
        return new Vector3f(gridPos.x + 0.5f, gridPos.y + 0.5f, gridPos.z + 0.5f);
    }
    
    /**
     * Calculate distance between two world positions
     */
    private float getDistance(Vector3f a, Vector3f b) {
        return a.distance(b);
    }
    
    /**
     * Generate cache key for a path request
     */
    private String getCacheKey(Vector3f start, Vector3f goal) {
        Vector3i startGrid = worldToGrid(start);
        Vector3i goalGrid = worldToGrid(goal);
        return startGrid.x + "," + startGrid.y + "," + startGrid.z + "->" +
               goalGrid.x + "," + goalGrid.y + "," + goalGrid.z;
    }
    
    /**
     * Check if a cached path is still valid
     */
    private boolean isPathValid(Path path, ObstacleChecker obstacleChecker) {
        for (Vector3f waypoint : path.getWaypoints()) {
            if (obstacleChecker.isBlocked(waypoint)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Cancel a pathfinding request
     */
    public boolean cancelRequest(String requestId) {
        PathfindingRequest request = activeRequests.remove(requestId);
        if (request != null && request.getFuture() != null) {
            return request.getFuture().cancel(true);
        }
        return false;
    }
    
    /**
     * Get the number of active pathfinding requests
     */
    public int getActiveRequestCount() {
        return activeRequests.size();
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        pathfindingExecutor.shutdown();
        activeRequests.clear();
        pathCache.clear();
        logger.info("PathfindingSystem cleaned up");
    }
    
    /**
     * A* node for pathfinding algorithm
     */
    private static class AStarNode {
        final Vector3i position;
        double gCost; // Cost from start
        final double hCost; // Heuristic cost to goal
        double fCost; // Total cost (g + h)
        AStarNode parent;
        
        AStarNode(Vector3i position, double gCost, double hCost, AStarNode parent) {
            this.position = position;
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
            this.parent = parent;
        }
    }
}