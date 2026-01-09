package engine.ai.pathfinding;

import org.joml.Vector3f;
import org.joml.Vector3i;
import java.util.*;

/**
 * Hierarchical pathfinding implementation (HPA*) for large-scale pathfinding optimization
 * Divides the world into clusters and finds paths between cluster entrances
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class HierarchicalPathfinder {
    
    private static final int CLUSTER_SIZE = 16; // Size of each cluster in world units
    private final Map<Vector3i, Cluster> clusters;
    private final Map<String, List<Vector3f>> clusterConnections;
    
    public HierarchicalPathfinder() {
        this.clusters = new HashMap<>();
        this.clusterConnections = new HashMap<>();
    }
    
    /**
     * Find a path using hierarchical pathfinding
     * @param start Starting position
     * @param goal Goal position
     * @param obstacleChecker Function to check obstacles
     * @return The found path, or null if no path exists
     */
    public Path findPath(Vector3f start, Vector3f goal, ObstacleChecker obstacleChecker) {
        Vector3i startCluster = getClusterCoords(start);
        Vector3i goalCluster = getClusterCoords(goal);
        
        // If start and goal are in the same cluster, use regular A*
        if (startCluster.equals(goalCluster)) {
            return findPathWithinCluster(start, goal, obstacleChecker);
        }
        
        // Find path between clusters
        List<Vector3i> clusterPath = findClusterPath(startCluster, goalCluster, obstacleChecker);
        if (clusterPath == null || clusterPath.isEmpty()) {
            return null;
        }
        
        // Convert cluster path to world path
        return buildHierarchicalPath(start, goal, clusterPath, obstacleChecker);
    }
    
    /**
     * Get cluster coordinates for a world position
     */
    private Vector3i getClusterCoords(Vector3f worldPos) {
        return new Vector3i(
            (int) Math.floor(worldPos.x / CLUSTER_SIZE),
            (int) Math.floor(worldPos.y / CLUSTER_SIZE),
            (int) Math.floor(worldPos.z / CLUSTER_SIZE)
        );
    }
    
    /**
     * Find path between clusters using A*
     */
    private List<Vector3i> findClusterPath(Vector3i startCluster, Vector3i goalCluster, ObstacleChecker obstacleChecker) {
        PriorityQueue<ClusterNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fCost));
        Set<Vector3i> closedSet = new HashSet<>();
        Map<Vector3i, ClusterNode> allNodes = new HashMap<>();
        
        ClusterNode startNode = new ClusterNode(startCluster, 0, clusterHeuristic(startCluster, goalCluster), null);
        openSet.add(startNode);
        allNodes.put(startCluster, startNode);
        
        while (!openSet.isEmpty()) {
            ClusterNode current = openSet.poll();
            closedSet.add(current.cluster);
            
            if (current.cluster.equals(goalCluster)) {
                return reconstructClusterPath(current);
            }
            
            // Explore neighboring clusters
            for (Vector3i neighbor : getNeighboringClusters(current.cluster)) {
                if (closedSet.contains(neighbor) || !isClusterPassable(neighbor, obstacleChecker)) {
                    continue;
                }
                
                double tentativeGCost = current.gCost + 1.0; // Simplified cost between clusters
                
                ClusterNode neighborNode = allNodes.get(neighbor);
                if (neighborNode == null) {
                    neighborNode = new ClusterNode(neighbor, tentativeGCost, clusterHeuristic(neighbor, goalCluster), current);
                    allNodes.put(neighbor, neighborNode);
                    openSet.add(neighborNode);
                } else if (tentativeGCost < neighborNode.gCost) {
                    neighborNode.gCost = tentativeGCost;
                    neighborNode.fCost = neighborNode.gCost + neighborNode.hCost;
                    neighborNode.parent = current;
                    
                    openSet.remove(neighborNode);
                    openSet.add(neighborNode);
                }
            }
        }
        
        return null; // No path found
    }
    
    /**
     * Reconstruct the cluster path
     */
    private List<Vector3i> reconstructClusterPath(ClusterNode goalNode) {
        List<Vector3i> path = new ArrayList<>();
        ClusterNode current = goalNode;
        
        while (current != null) {
            path.add(current.cluster);
            current = current.parent;
        }
        
        Collections.reverse(path);
        return path;
    }
    
    /**
     * Build the final hierarchical path from cluster path
     */
    private Path buildHierarchicalPath(Vector3f start, Vector3f goal, List<Vector3i> clusterPath, ObstacleChecker obstacleChecker) {
        List<Vector3f> waypoints = new ArrayList<>();
        waypoints.add(new Vector3f(start));
        
        // Add waypoints for cluster transitions
        for (int i = 0; i < clusterPath.size() - 1; i++) {
            Vector3i currentCluster = clusterPath.get(i);
            Vector3i nextCluster = clusterPath.get(i + 1);
            
            // Find connection point between clusters
            Vector3f connectionPoint = findClusterConnection(currentCluster, nextCluster, obstacleChecker);
            if (connectionPoint != null) {
                waypoints.add(connectionPoint);
            }
        }
        
        waypoints.add(new Vector3f(goal));
        
        return new Path(waypoints);
    }
    
    /**
     * Find a connection point between two adjacent clusters
     */
    private Vector3f findClusterConnection(Vector3i cluster1, Vector3i cluster2, ObstacleChecker obstacleChecker) {
        // Calculate the boundary between clusters
        Vector3f cluster1Center = getClusterCenter(cluster1);
        Vector3f cluster2Center = getClusterCenter(cluster2);
        
        // Find a passable point on the boundary
        Vector3f midpoint = new Vector3f(cluster1Center).add(cluster2Center).mul(0.5f);
        
        // Try to find a clear connection point near the midpoint
        for (int attempts = 0; attempts < 10; attempts++) {
            Vector3f testPoint = new Vector3f(midpoint);
            testPoint.add(
                (float) (Math.random() - 0.5) * CLUSTER_SIZE * 0.5f,
                (float) (Math.random() - 0.5) * CLUSTER_SIZE * 0.5f,
                (float) (Math.random() - 0.5) * CLUSTER_SIZE * 0.5f
            );
            
            if (!obstacleChecker.isBlocked(testPoint)) {
                return testPoint;
            }
        }
        
        return midpoint; // Fallback to midpoint
    }
    
    /**
     * Get the center position of a cluster
     */
    private Vector3f getClusterCenter(Vector3i clusterCoords) {
        return new Vector3f(
            clusterCoords.x * CLUSTER_SIZE + CLUSTER_SIZE * 0.5f,
            clusterCoords.y * CLUSTER_SIZE + CLUSTER_SIZE * 0.5f,
            clusterCoords.z * CLUSTER_SIZE + CLUSTER_SIZE * 0.5f
        );
    }
    
    /**
     * Get neighboring cluster coordinates
     */
    private List<Vector3i> getNeighboringClusters(Vector3i cluster) {
        List<Vector3i> neighbors = new ArrayList<>();
        
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    
                    neighbors.add(new Vector3i(
                        cluster.x + dx,
                        cluster.y + dy,
                        cluster.z + dz
                    ));
                }
            }
        }
        
        return neighbors;
    }
    
    /**
     * Check if a cluster is generally passable
     */
    private boolean isClusterPassable(Vector3i cluster, ObstacleChecker obstacleChecker) {
        Vector3f clusterCenter = getClusterCenter(cluster);
        
        // Sample a few points in the cluster to determine passability
        int passableCount = 0;
        int totalSamples = 8;
        
        for (int i = 0; i < totalSamples; i++) {
            Vector3f samplePoint = new Vector3f(clusterCenter);
            samplePoint.add(
                (float) (Math.random() - 0.5) * CLUSTER_SIZE,
                (float) (Math.random() - 0.5) * CLUSTER_SIZE,
                (float) (Math.random() - 0.5) * CLUSTER_SIZE
            );
            
            if (!obstacleChecker.isBlocked(samplePoint)) {
                passableCount++;
            }
        }
        
        // Consider cluster passable if at least half the samples are clear
        return passableCount >= totalSamples / 2;
    }
    
    /**
     * Heuristic function for cluster pathfinding
     */
    private double clusterHeuristic(Vector3i from, Vector3i to) {
        return Math.abs(to.x - from.x) + Math.abs(to.y - from.y) + Math.abs(to.z - from.z);
    }
    
    /**
     * Find path within a single cluster using regular A*
     */
    private Path findPathWithinCluster(Vector3f start, Vector3f goal, ObstacleChecker obstacleChecker) {
        // This would use the regular A* algorithm from PathfindingSystem
        // For now, return a simple direct path
        List<Vector3f> waypoints = Arrays.asList(new Vector3f(start), new Vector3f(goal));
        return new Path(waypoints);
    }
    
    /**
     * Node for cluster-level pathfinding
     */
    private static class ClusterNode {
        final Vector3i cluster;
        double gCost;
        final double hCost;
        double fCost;
        ClusterNode parent;
        
        ClusterNode(Vector3i cluster, double gCost, double hCost, ClusterNode parent) {
            this.cluster = cluster;
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
            this.parent = parent;
        }
    }
    
    /**
     * Cluster data structure
     */
    private static class Cluster {
        final Vector3i coords;
        final Set<Vector3f> entrances;
        
        Cluster(Vector3i coords) {
            this.coords = coords;
            this.entrances = new HashSet<>();
        }
    }
}