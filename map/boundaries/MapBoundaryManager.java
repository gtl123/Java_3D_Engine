package fps.map.boundaries;

import fps.map.data.MapDefinition;
import fps.core.math.Vector3f;
import fps.core.math.BoundingBox;
import fps.game.Player;
import engine.logging.LogManager;
import java.util.*;

/**
 * Manages map boundaries as a separate system that can be integrated with MapManager.
 * Provides boundary checking, violation handling, and safe position finding.
 */
public class MapBoundaryManager implements BoundaryEventListener {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private BoundarySystem boundarySystem;
    private MapDefinition currentMap;
    private boolean initialized = false;
    
    // Default world bounds for boundary system
    private static final BoundingBox DEFAULT_WORLD_BOUNDS = new BoundingBox(
        new Vector3f(-2000, -200, -2000),
        new Vector3f(2000, 400, 2000)
    );
    
    private static final float DEFAULT_GRID_SIZE = 100.0f;
    
    public MapBoundaryManager() {
        this.boundarySystem = new BoundarySystem(DEFAULT_WORLD_BOUNDS, DEFAULT_GRID_SIZE);
        this.boundarySystem.addListener(this);
        
        logManager.info("MapBoundaryManager", "Boundary manager created");
    }
    
    /**
     * Initialize the boundary manager
     */
    public void initialize() {
        if (initialized) {
            return;
        }
        
        try {
            initialized = true;
            logManager.info("MapBoundaryManager", "Boundary manager initialized");
            
        } catch (Exception e) {
            logManager.error("MapBoundaryManager", "Failed to initialize boundary manager", e);
            throw e;
        }
    }
    
    /**
     * Setup boundaries for a loaded map
     */
    public void setupMapBoundaries(MapDefinition mapDefinition) {
        if (!initialized || mapDefinition == null) {
            return;
        }
        
        logManager.info("MapBoundaryManager", "Setting up boundaries for map", 
                       "mapId", mapDefinition.getMapId());
        
        try {
            // Clear existing boundaries
            boundarySystem.clear();
            this.currentMap = mapDefinition;
            
            // Create default boundaries based on map geometry
            createDefaultBoundaries(mapDefinition);
            
            // Load custom boundaries if they exist in map definition
            loadCustomBoundaries(mapDefinition);
            
            logManager.info("MapBoundaryManager", "Map boundaries setup complete", 
                           "boundaryCount", boundarySystem.getBoundaryCount());
            
        } catch (Exception e) {
            logManager.error("MapBoundaryManager", "Failed to setup map boundaries", e);
        }
    }
    
    /**
     * Update boundary system - check players against boundaries
     */
    public void update(float deltaTime, Collection<Player> players) {
        if (!initialized || boundarySystem == null) {
            return;
        }
        
        try {
            boundarySystem.update(deltaTime, players);
            
        } catch (Exception e) {
            logManager.error("MapBoundaryManager", "Error during boundary update", e);
        }
    }
    
    /**
     * Check if a position is within playable area
     */
    public boolean isInPlayableArea(Vector3f position) {
        if (!initialized || boundarySystem == null) {
            return true; // Default to allowing movement if system not ready
        }
        
        return boundarySystem.isInPlayableArea(position);
    }
    
    /**
     * Find a safe position near the given position
     */
    public Vector3f findSafePosition(Vector3f position, float searchRadius) {
        if (!initialized || boundarySystem == null) {
            return position;
        }
        
        return boundarySystem.findSafePosition(position, searchRadius);
    }
    
    /**
     * Get boundary severity at a position
     */
    public BoundarySeverity getBoundarySeverity(Vector3f position) {
        if (!initialized || boundarySystem == null) {
            return BoundarySeverity.LOW;
        }
        
        return boundarySystem.getBoundarySeverity(position);
    }
    
    /**
     * Add a custom boundary to the system
     */
    public void addBoundary(MapBoundary boundary) {
        if (initialized && boundarySystem != null) {
            boundarySystem.addBoundary(boundary);
        }
    }
    
    /**
     * Remove a boundary from the system
     */
    public void removeBoundary(String boundaryId) {
        if (initialized && boundarySystem != null) {
            boundarySystem.removeBoundary(boundaryId);
        }
    }
    
    /**
     * Enable or disable a boundary
     */
    public void setBoundaryEnabled(String boundaryId, boolean enabled) {
        if (initialized && boundarySystem != null) {
            boundarySystem.setBoundaryEnabled(boundaryId, enabled);
        }
    }
    
    /**
     * Create default boundaries based on map geometry
     */
    private void createDefaultBoundaries(MapDefinition mapDefinition) {
        // Create playable area boundary from map bounds
        if (mapDefinition.getGeometry() != null && mapDefinition.getGeometry().getBounds() != null) {
            BoundingBox mapBounds = mapDefinition.getGeometry().getBounds();
            
            // Expand bounds slightly for playable area
            Vector3f min = mapBounds.getMin();
            Vector3f max = mapBounds.getMax();
            float expansion = 50.0f; // 50 units expansion
            
            List<Vector3f> playableVertices = Arrays.asList(
                new Vector3f(min.x - expansion, min.y, min.z - expansion),
                new Vector3f(max.x + expansion, min.y, min.z - expansion),
                new Vector3f(max.x + expansion, min.y, max.z + expansion),
                new Vector3f(min.x - expansion, min.y, max.z + expansion)
            );
            
            MapBoundary playableArea = MapBoundary.createPlayableArea(
                "default_playable_area", 
                "Default Playable Area", 
                playableVertices
            );
            
            boundarySystem.addBoundary(playableArea);
            
            // Create out-of-bounds areas around the playable area
            float oobExpansion = 200.0f;
            BoundingBox oobBounds = new BoundingBox(
                new Vector3f(min.x - oobExpansion, min.y - 50, min.z - oobExpansion),
                new Vector3f(max.x + oobExpansion, max.y + 100, max.z + oobExpansion)
            );
            
            MapBoundary outOfBounds = MapBoundary.createOutOfBounds(
                "default_out_of_bounds",
                "Out of Bounds Area",
                oobBounds
            );
            
            boundarySystem.addBoundary(outOfBounds);
            
            logManager.info("MapBoundaryManager", "Created default boundaries", 
                           "playableArea", playableArea.getBounds().toString(),
                           "outOfBounds", oobBounds.toString());
        }
    }
    
    /**
     * Load custom boundaries from map definition
     */
    private void loadCustomBoundaries(MapDefinition mapDefinition) {
        // This would load boundaries from map definition if they exist
        // For now, we'll create some example boundaries for competitive maps
        
        if (isCompetitiveMap(mapDefinition)) {
            createCompetitiveBoundaries(mapDefinition);
        }
    }
    
    /**
     * Check if this is a competitive map
     */
    private boolean isCompetitiveMap(MapDefinition mapDefinition) {
        return mapDefinition.getMetadata().getSupportedGameModes().contains("competitive") ||
               mapDefinition.getMetadata().getSupportedGameModes().contains("search_and_destroy") ||
               mapDefinition.getMetadata().getSupportedGameModes().contains("team_deathmatch");
    }
    
    /**
     * Create competitive-specific boundaries
     */
    private void createCompetitiveBoundaries(MapDefinition mapDefinition) {
        // Create warning zones near map edges for competitive play
        if (mapDefinition.getGeometry() != null && mapDefinition.getGeometry().getBounds() != null) {
            BoundingBox mapBounds = mapDefinition.getGeometry().getBounds();
            Vector3f center = mapBounds.getCenter();
            float warningDistance = 30.0f;
            
            // Create warning zones at map edges
            List<Vector3f> warningVertices = Arrays.asList(
                new Vector3f(mapBounds.getMin().x + warningDistance, mapBounds.getMin().y, mapBounds.getMin().z + warningDistance),
                new Vector3f(mapBounds.getMax().x - warningDistance, mapBounds.getMin().y, mapBounds.getMin().z + warningDistance),
                new Vector3f(mapBounds.getMax().x - warningDistance, mapBounds.getMin().y, mapBounds.getMax().z - warningDistance),
                new Vector3f(mapBounds.getMin().x + warningDistance, mapBounds.getMin().y, mapBounds.getMax().z - warningDistance)
            );
            
            MapBoundary warningZone = MapBoundary.createWarningZone(
                "competitive_warning_zone",
                "Competitive Edge Warning",
                warningVertices
            );
            
            boundarySystem.addBoundary(warningZone);
            
            logManager.info("MapBoundaryManager", "Created competitive boundaries");
        }
    }
    
    /**
     * Clear all boundaries
     */
    public void clearBoundaries() {
        if (boundarySystem != null) {
            boundarySystem.clear();
        }
        currentMap = null;
    }
    
    /**
     * Cleanup boundary manager
     */
    public void cleanup() {
        logManager.info("MapBoundaryManager", "Cleaning up boundary manager");
        
        try {
            if (boundarySystem != null) {
                boundarySystem.clear();
                boundarySystem = null;
            }
            
            currentMap = null;
            initialized = false;
            
            logManager.info("MapBoundaryManager", "Boundary manager cleanup complete");
            
        } catch (Exception e) {
            logManager.error("MapBoundaryManager", "Error during cleanup", e);
        }
    }
    
    // BoundaryEventListener implementation
    @Override
    public void onBoundaryAdded(MapBoundary boundary) {
        logManager.info("MapBoundaryManager", "Boundary added", 
                       "id", boundary.getId(), 
                       "type", boundary.getType().toString());
    }
    
    @Override
    public void onBoundaryRemoved(MapBoundary boundary) {
        logManager.info("MapBoundaryManager", "Boundary removed", 
                       "id", boundary.getId());
    }
    
    @Override
    public void onActionExecuted(Player player, MapBoundary boundary, BoundaryAction action) {
        logManager.info("MapBoundaryManager", "Boundary action executed", 
                       "player", player.getName(),
                       "boundary", boundary.getName(),
                       "action", action.toString());
    }
    
    @Override
    public void onViolationEnded(BoundaryViolation violation) {
        logManager.info("MapBoundaryManager", "Boundary violation ended", 
                       "player", violation.getPlayer().getName(),
                       "boundary", violation.getBoundary().getName(),
                       "duration", String.format("%.2f", violation.getDuration()));
    }
    
    // Getters
    public BoundarySystem getBoundarySystem() { return boundarySystem; }
    public MapDefinition getCurrentMap() { return currentMap; }
    public boolean isInitialized() { return initialized; }
    
    /**
     * Get system statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("initialized", initialized);
        stats.put("currentMap", currentMap != null ? currentMap.getMapId() : "none");
        
        if (boundarySystem != null) {
            stats.putAll(boundarySystem.getStatistics());
        }
        
        return stats;
    }
}