package engine.ai.navigation;

import engine.logging.LogManager;
import engine.config.ConfigurationManager;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3D Navigation mesh for AI pathfinding and spatial queries
 * Generates walkable surfaces from world geometry and provides efficient queries
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class NavigationMesh {
    
    private static final LogManager logger = LogManager.getInstance();
    private static final ConfigurationManager config = ConfigurationManager.getInstance();
    
    private final Map<Vector3i, NavMeshTile> tiles;
    private final NavMeshGenerator generator;
    private final float tileSize;
    private final float agentRadius;
    private final float agentHeight;
    private final float maxSlope;
    private final float stepHeight;
    
    // Spatial indexing for fast queries
    private final Map<Vector3i, Set<NavMeshPolygon>> spatialIndex;
    private final int spatialGridSize;
    
    /**
     * Create a new navigation mesh
     * @param tileSize Size of each navigation tile
     * @param agentRadius Radius of agents using this navmesh
     * @param agentHeight Height of agents using this navmesh
     */
    public NavigationMesh(float tileSize, float agentRadius, float agentHeight) {
        this.tileSize = tileSize;
        this.agentRadius = agentRadius;
        this.agentHeight = agentHeight;
        this.maxSlope = (float) Math.toRadians(config.getDouble("ai.navigation.maxSlope", 45.0));
        this.stepHeight = config.getFloat("ai.navigation.stepHeight", 0.5f);
        this.spatialGridSize = config.getInt("ai.navigation.spatialGridSize", 4);
        
        this.tiles = new ConcurrentHashMap<>();
        this.spatialIndex = new ConcurrentHashMap<>();
        this.generator = new NavMeshGenerator(this);
        
        logger.info("NavigationMesh initialized - TileSize: " + tileSize + 
                   ", AgentRadius: " + agentRadius + ", AgentHeight: " + agentHeight);
    }
    
    /**
     * Create a navigation mesh with default parameters
     */
    public NavigationMesh() {
        this(16.0f, 0.5f, 2.0f);
    }
    
    /**
     * Generate navigation mesh for a world region
     * @param worldBounds Bounding box of the world region
     * @param obstacleChecker Function to check if a position is blocked
     */
    public void generateMesh(BoundingBox worldBounds, ObstacleChecker obstacleChecker) {
        logger.info("Generating navigation mesh for bounds: " + worldBounds);
        
        long startTime = System.currentTimeMillis();
        
        // Clear existing mesh
        clear();
        
        // Generate tiles for the world bounds
        Vector3i minTile = worldToTile(worldBounds.getMin());
        Vector3i maxTile = worldToTile(worldBounds.getMax());
        
        int tilesGenerated = 0;
        for (int x = minTile.x; x <= maxTile.x; x++) {
            for (int z = minTile.z; z <= maxTile.z; z++) {
                for (int y = minTile.y; y <= maxTile.y; y++) {
                    Vector3i tileCoord = new Vector3i(x, y, z);
                    NavMeshTile tile = generator.generateTile(tileCoord, obstacleChecker);
                    
                    if (tile != null && !tile.getPolygons().isEmpty()) {
                        tiles.put(tileCoord, tile);
                        indexTile(tile);
                        tilesGenerated++;
                    }
                }
            }
        }
        
        // Connect adjacent tiles
        connectTiles();
        
        long endTime = System.currentTimeMillis();
        logger.info("Navigation mesh generation completed - " + tilesGenerated + 
                   " tiles generated in " + (endTime - startTime) + "ms");
    }
    
    /**
     * Find the nearest point on the navigation mesh
     * @param position World position to query
     * @param searchRadius Maximum search radius
     * @return Nearest point on navmesh, or null if none found
     */
    public Vector3f findNearestPoint(Vector3f position, float searchRadius) {
        Vector3f nearestPoint = null;
        float nearestDistance = Float.MAX_VALUE;
        
        // Get tiles within search radius
        Set<NavMeshTile> searchTiles = getTilesInRadius(position, searchRadius);
        
        for (NavMeshTile tile : searchTiles) {
            for (NavMeshPolygon polygon : tile.getPolygons()) {
                Vector3f pointOnPoly = polygon.getClosestPoint(position);
                if (pointOnPoly != null) {
                    float distance = position.distance(pointOnPoly);
                    if (distance <= searchRadius && distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestPoint = new Vector3f(pointOnPoly);
                    }
                }
            }
        }
        
        return nearestPoint;
    }
    
    /**
     * Check if a position is on the navigation mesh
     * @param position Position to check
     * @param tolerance Distance tolerance
     * @return true if position is on navmesh
     */
    public boolean isOnNavMesh(Vector3f position, float tolerance) {
        Vector3f nearestPoint = findNearestPoint(position, tolerance);
        return nearestPoint != null && position.distance(nearestPoint) <= tolerance;
    }
    
    /**
     * Find a path between two points on the navigation mesh
     * @param start Start position
     * @param end End position
     * @return List of waypoints, or null if no path found
     */
    public List<Vector3f> findPath(Vector3f start, Vector3f end) {
        // Find polygons containing start and end points
        NavMeshPolygon startPoly = findPolygonContaining(start);
        NavMeshPolygon endPoly = findPolygonContaining(end);
        
        if (startPoly == null || endPoly == null) {
            return null;
        }
        
        if (startPoly == endPoly) {
            // Same polygon, direct path
            return Arrays.asList(new Vector3f(start), new Vector3f(end));
        }
        
        // Use A* pathfinding on polygon graph
        return findPolygonPath(startPoly, endPoly, start, end);
    }
    
    /**
     * Get the polygon containing a world position
     * @param position World position
     * @return Containing polygon, or null if none found
     */
    public NavMeshPolygon findPolygonContaining(Vector3f position) {
        Vector3i spatialCoord = worldToSpatialGrid(position);
        Set<NavMeshPolygon> candidates = spatialIndex.get(spatialCoord);
        
        if (candidates != null) {
            for (NavMeshPolygon polygon : candidates) {
                if (polygon.containsPoint(position)) {
                    return polygon;
                }
            }
        }
        
        // Check adjacent spatial grid cells
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                
                Vector3i adjCoord = new Vector3i(spatialCoord.x + dx, spatialCoord.y, spatialCoord.z + dz);
                Set<NavMeshPolygon> adjCandidates = spatialIndex.get(adjCoord);
                
                if (adjCandidates != null) {
                    for (NavMeshPolygon polygon : adjCandidates) {
                        if (polygon.containsPoint(position)) {
                            return polygon;
                        }
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Get all tiles within a radius of a position
     */
    private Set<NavMeshTile> getTilesInRadius(Vector3f position, float radius) {
        Set<NavMeshTile> result = new HashSet<>();
        
        Vector3i centerTile = worldToTile(position);
        int tileRadius = (int) Math.ceil(radius / tileSize);
        
        for (int dx = -tileRadius; dx <= tileRadius; dx++) {
            for (int dy = -tileRadius; dy <= tileRadius; dy++) {
                for (int dz = -tileRadius; dz <= tileRadius; dz++) {
                    Vector3i tileCoord = new Vector3i(centerTile.x + dx, centerTile.y + dy, centerTile.z + dz);
                    NavMeshTile tile = tiles.get(tileCoord);
                    if (tile != null) {
                        result.add(tile);
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Convert world position to tile coordinates
     */
    private Vector3i worldToTile(Vector3f worldPos) {
        return new Vector3i(
            (int) Math.floor(worldPos.x / tileSize),
            (int) Math.floor(worldPos.y / tileSize),
            (int) Math.floor(worldPos.z / tileSize)
        );
    }
    
    /**
     * Convert world position to spatial grid coordinates
     */
    private Vector3i worldToSpatialGrid(Vector3f worldPos) {
        float gridSize = tileSize / spatialGridSize;
        return new Vector3i(
            (int) Math.floor(worldPos.x / gridSize),
            (int) Math.floor(worldPos.y / gridSize),
            (int) Math.floor(worldPos.z / gridSize)
        );
    }
    
    /**
     * Index a tile's polygons in the spatial grid
     */
    private void indexTile(NavMeshTile tile) {
        for (NavMeshPolygon polygon : tile.getPolygons()) {
            BoundingBox polyBounds = polygon.getBounds();
            
            Vector3i minGrid = worldToSpatialGrid(polyBounds.getMin());
            Vector3i maxGrid = worldToSpatialGrid(polyBounds.getMax());
            
            for (int x = minGrid.x; x <= maxGrid.x; x++) {
                for (int y = minGrid.y; y <= maxGrid.y; y++) {
                    for (int z = minGrid.z; z <= maxGrid.z; z++) {
                        Vector3i gridCoord = new Vector3i(x, y, z);
                        spatialIndex.computeIfAbsent(gridCoord, k -> new HashSet<>()).add(polygon);
                    }
                }
            }
        }
    }
    
    /**
     * Connect adjacent tiles by linking their edge polygons
     */
    private void connectTiles() {
        for (Map.Entry<Vector3i, NavMeshTile> entry : tiles.entrySet()) {
            Vector3i tileCoord = entry.getKey();
            NavMeshTile tile = entry.getValue();
            
            // Check all 6 adjacent directions
            Vector3i[] directions = {
                new Vector3i(1, 0, 0), new Vector3i(-1, 0, 0),
                new Vector3i(0, 1, 0), new Vector3i(0, -1, 0),
                new Vector3i(0, 0, 1), new Vector3i(0, 0, -1)
            };
            
            for (Vector3i dir : directions) {
                Vector3i adjCoord = new Vector3i(tileCoord).add(dir);
                NavMeshTile adjTile = tiles.get(adjCoord);
                
                if (adjTile != null) {
                    generator.connectTiles(tile, adjTile);
                }
            }
        }
    }
    
    /**
     * Find path between polygons using A*
     */
    private List<Vector3f> findPolygonPath(NavMeshPolygon startPoly, NavMeshPolygon endPoly, 
                                          Vector3f start, Vector3f end) {
        // Simplified polygon pathfinding - in a full implementation,
        // this would use A* on the polygon adjacency graph
        List<Vector3f> path = new ArrayList<>();
        path.add(new Vector3f(start));
        
        // For now, add polygon centers as waypoints
        NavMeshPolygon current = startPoly;
        Set<NavMeshPolygon> visited = new HashSet<>();
        
        while (current != endPoly && visited.size() < 100) { // Prevent infinite loops
            visited.add(current);
            
            // Find the best adjacent polygon towards the goal
            NavMeshPolygon next = null;
            float bestDistance = Float.MAX_VALUE;
            
            for (NavMeshPolygon neighbor : current.getNeighbors()) {
                if (!visited.contains(neighbor)) {
                    float distance = neighbor.getCenter().distance(end);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        next = neighbor;
                    }
                }
            }
            
            if (next == null) break;
            
            path.add(new Vector3f(next.getCenter()));
            current = next;
        }
        
        path.add(new Vector3f(end));
        return path;
    }
    
    /**
     * Clear the navigation mesh
     */
    public void clear() {
        tiles.clear();
        spatialIndex.clear();
    }
    
    /**
     * Get navigation mesh statistics
     * @return Statistics string
     */
    public String getStats() {
        int totalPolygons = tiles.values().stream()
                                .mapToInt(tile -> tile.getPolygons().size())
                                .sum();
        
        return String.format("NavigationMesh Stats: %d tiles, %d polygons, %d spatial cells",
                           tiles.size(), totalPolygons, spatialIndex.size());
    }
    
    // Getters
    public float getTileSize() { return tileSize; }
    public float getAgentRadius() { return agentRadius; }
    public float getAgentHeight() { return agentHeight; }
    public float getMaxSlope() { return maxSlope; }
    public float getStepHeight() { return stepHeight; }
    public Map<Vector3i, NavMeshTile> getTiles() { return new HashMap<>(tiles); }
    
    /**
     * Functional interface for checking obstacles
     */
    @FunctionalInterface
    public interface ObstacleChecker {
        boolean isBlocked(Vector3f position);
    }
}