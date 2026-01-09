package fps.map.boundaries;

import fps.core.math.Vector3f;
import fps.core.math.BoundingBox;
import fps.game.Player;
import fps.game.GameMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Manages all map boundaries and handles boundary violations.
 * Provides efficient spatial queries and real-time boundary checking.
 */
public class BoundarySystem {
    private static final Logger logger = Logger.getLogger(BoundarySystem.class.getName());
    
    private final Map<String, MapBoundary> boundaries;
    private final List<MapBoundary> activeBoundaries;
    private final Map<String, BoundaryViolation> activeViolations;
    private final List<BoundaryEventListener> listeners;
    
    // Spatial optimization
    private final Map<String, List<MapBoundary>> spatialGrid;
    private final float gridSize;
    private final BoundingBox worldBounds;
    
    // Performance tracking
    private long lastUpdateTime;
    private int checksPerFrame;
    private float averageCheckTime;
    
    public BoundarySystem(BoundingBox worldBounds, float gridSize) {
        this.boundaries = new ConcurrentHashMap<>();
        this.activeBoundaries = new CopyOnWriteArrayList<>();
        this.activeViolations = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.spatialGrid = new ConcurrentHashMap<>();
        this.worldBounds = worldBounds;
        this.gridSize = gridSize;
        this.lastUpdateTime = System.currentTimeMillis();
        
        logger.info("BoundarySystem initialized with world bounds: " + worldBounds + ", grid size: " + gridSize);
    }
    
    /**
     * Add a boundary to the system
     */
    public void addBoundary(MapBoundary boundary) {
        if (boundary == null) {
            throw new IllegalArgumentException("Boundary cannot be null");
        }
        
        boundaries.put(boundary.getId(), boundary);
        
        if (boundary.isEnabled()) {
            activeBoundaries.add(boundary);
            addToSpatialGrid(boundary);
        }
        
        logger.info("Added boundary: " + boundary.getName() + " (" + boundary.getType() + ")");
        notifyBoundaryAdded(boundary);
    }
    
    /**
     * Remove a boundary from the system
     */
    public void removeBoundary(String boundaryId) {
        MapBoundary boundary = boundaries.remove(boundaryId);
        if (boundary != null) {
            activeBoundaries.remove(boundary);
            removeFromSpatialGrid(boundary);
            
            // Remove any active violations for this boundary
            activeViolations.entrySet().removeIf(entry -> 
                entry.getValue().getBoundary().getId().equals(boundaryId));
            
            logger.info("Removed boundary: " + boundary.getName());
            notifyBoundaryRemoved(boundary);
        }
    }
    
    /**
     * Update boundary system - check all players against boundaries
     */
    public void update(float deltaTime, Collection<Player> players) {
        long startTime = System.nanoTime();
        checksPerFrame = 0;
        
        for (Player player : players) {
            checkPlayerBoundaries(player, deltaTime);
        }
        
        // Update active violations
        updateViolations(deltaTime);
        
        // Update performance metrics
        long endTime = System.nanoTime();
        float checkTime = (endTime - startTime) / 1_000_000.0f; // Convert to milliseconds
        averageCheckTime = (averageCheckTime * 0.9f) + (checkTime * 0.1f);
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Check a specific player against all relevant boundaries
     */
    public List<BoundaryViolation> checkPlayerBoundaries(Player player, float deltaTime) {
        Vector3f playerPosition = player.getPosition();
        List<BoundaryViolation> violations = new ArrayList<>();
        
        // Get boundaries in the player's area using spatial grid
        List<MapBoundary> nearbyBoundaries = getBoundariesNear(playerPosition);
        
        for (MapBoundary boundary : nearbyBoundaries) {
            checksPerFrame++;
            
            if (!boundary.isEnabled()) {
                continue;
            }
            
            // Check if boundary should trigger for this player
            if (boundary.shouldTrigger(playerPosition)) {
                BoundaryViolation violation = getOrCreateViolation(player, boundary);
                violation.update(deltaTime);
                violations.add(violation);
                
                // Check if action should be executed
                if (violation.shouldExecuteAction()) {
                    executeBoundaryAction(player, boundary, violation);
                }
            } else {
                // Remove violation if player is no longer in boundary
                removeViolation(player, boundary);
            }
        }
        
        return violations;
    }
    
    /**
     * Get the closest boundary to a point
     */
    public MapBoundary getClosestBoundary(Vector3f position, BoundaryType type) {
        MapBoundary closest = null;
        float minDistance = Float.MAX_VALUE;
        
        for (MapBoundary boundary : activeBoundaries) {
            if (type != null && boundary.getType() != type) {
                continue;
            }
            
            float distance = boundary.getDistanceToEdge(position);
            if (distance < minDistance) {
                minDistance = distance;
                closest = boundary;
            }
        }
        
        return closest;
    }
    
    /**
     * Get all boundaries of a specific type
     */
    public List<MapBoundary> getBoundariesByType(BoundaryType type) {
        return activeBoundaries.stream()
            .filter(boundary -> boundary.getType() == type)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Check if a position is within the playable area
     */
    public boolean isInPlayableArea(Vector3f position) {
        List<MapBoundary> playableAreas = getBoundariesByType(BoundaryType.PLAYABLE_AREA);
        
        for (MapBoundary boundary : playableAreas) {
            if (boundary.containsPoint(position)) {
                return true;
            }
        }
        
        return playableAreas.isEmpty(); // If no playable areas defined, assume all is playable
    }
    
    /**
     * Get the severity of boundary violations at a position
     */
    public BoundarySeverity getBoundarySeverity(Vector3f position) {
        BoundarySeverity maxSeverity = BoundarySeverity.LOW;
        
        List<MapBoundary> nearbyBoundaries = getBoundariesNear(position);
        for (MapBoundary boundary : nearbyBoundaries) {
            if (boundary.shouldTrigger(position)) {
                BoundarySeverity severity = boundary.getSeverity(position);
                if (severity.isHigherThan(maxSeverity)) {
                    maxSeverity = severity;
                }
            }
        }
        
        return maxSeverity;
    }
    
    /**
     * Find a safe position near the given position
     */
    public Vector3f findSafePosition(Vector3f position, float searchRadius) {
        // First check if current position is already safe
        if (isInPlayableArea(position) && getBoundarySeverity(position) == BoundarySeverity.LOW) {
            return position;
        }
        
        // Search in expanding circles
        int samples = 16;
        for (float radius = 5.0f; radius <= searchRadius; radius += 5.0f) {
            for (int i = 0; i < samples; i++) {
                float angle = (float) (2.0 * Math.PI * i / samples);
                Vector3f testPos = new Vector3f(
                    position.x + radius * (float) Math.cos(angle),
                    position.y,
                    position.z + radius * (float) Math.sin(angle)
                );
                
                if (isInPlayableArea(testPos) && getBoundarySeverity(testPos) == BoundarySeverity.LOW) {
                    return testPos;
                }
            }
        }
        
        // If no safe position found, return closest playable area boundary
        MapBoundary playableArea = getClosestBoundary(position, BoundaryType.PLAYABLE_AREA);
        if (playableArea != null) {
            return playableArea.getClosestPointOnBoundary(position);
        }
        
        return position; // Fallback to original position
    }
    
    /**
     * Enable or disable a boundary
     */
    public void setBoundaryEnabled(String boundaryId, boolean enabled) {
        MapBoundary boundary = boundaries.get(boundaryId);
        if (boundary != null) {
            if (enabled && !activeBoundaries.contains(boundary)) {
                activeBoundaries.add(boundary);
                addToSpatialGrid(boundary);
            } else if (!enabled && activeBoundaries.contains(boundary)) {
                activeBoundaries.remove(boundary);
                removeFromSpatialGrid(boundary);
            }
            
            logger.info("Boundary " + boundary.getName() + " " + (enabled ? "enabled" : "disabled"));
        }
    }
    
    /**
     * Get boundaries near a position using spatial grid
     */
    private List<MapBoundary> getBoundariesNear(Vector3f position) {
        List<MapBoundary> nearby = new ArrayList<>();
        
        // Calculate grid coordinates
        int gridX = (int) Math.floor((position.x - worldBounds.getMin().x) / gridSize);
        int gridZ = (int) Math.floor((position.z - worldBounds.getMin().z) / gridSize);
        
        // Check surrounding grid cells
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                String gridKey = (gridX + dx) + "," + (gridZ + dz);
                List<MapBoundary> cellBoundaries = spatialGrid.get(gridKey);
                if (cellBoundaries != null) {
                    nearby.addAll(cellBoundaries);
                }
            }
        }
        
        return nearby;
    }
    
    /**
     * Add boundary to spatial grid
     */
    private void addToSpatialGrid(MapBoundary boundary) {
        BoundingBox bounds = boundary.getBounds();
        
        int minGridX = (int) Math.floor((bounds.getMin().x - worldBounds.getMin().x) / gridSize);
        int maxGridX = (int) Math.ceil((bounds.getMax().x - worldBounds.getMin().x) / gridSize);
        int minGridZ = (int) Math.floor((bounds.getMin().z - worldBounds.getMin().z) / gridSize);
        int maxGridZ = (int) Math.ceil((bounds.getMax().z - worldBounds.getMin().z) / gridSize);
        
        for (int x = minGridX; x <= maxGridX; x++) {
            for (int z = minGridZ; z <= maxGridZ; z++) {
                String gridKey = x + "," + z;
                spatialGrid.computeIfAbsent(gridKey, k -> new ArrayList<>()).add(boundary);
            }
        }
    }
    
    /**
     * Remove boundary from spatial grid
     */
    private void removeFromSpatialGrid(MapBoundary boundary) {
        spatialGrid.values().forEach(list -> list.remove(boundary));
    }
    
    /**
     * Get or create a boundary violation for a player
     */
    private BoundaryViolation getOrCreateViolation(Player player, MapBoundary boundary) {
        String key = player.getId() + ":" + boundary.getId();
        return activeViolations.computeIfAbsent(key, k -> 
            new BoundaryViolation(player, boundary, System.currentTimeMillis()));
    }
    
    /**
     * Remove a boundary violation
     */
    private void removeViolation(Player player, MapBoundary boundary) {
        String key = player.getId() + ":" + boundary.getId();
        BoundaryViolation violation = activeViolations.remove(key);
        if (violation != null) {
            notifyViolationEnded(violation);
        }
    }
    
    /**
     * Update all active violations
     */
    private void updateViolations(float deltaTime) {
        Iterator<BoundaryViolation> iterator = activeViolations.values().iterator();
        while (iterator.hasNext()) {
            BoundaryViolation violation = iterator.next();
            violation.update(deltaTime);
            
            // Remove expired violations
            if (violation.isExpired()) {
                iterator.remove();
                notifyViolationEnded(violation);
            }
        }
    }
    
    /**
     * Execute boundary action for a player
     */
    private void executeBoundaryAction(Player player, MapBoundary boundary, BoundaryViolation violation) {
        BoundaryAction action = boundary.getAction();
        
        logger.info("Executing boundary action " + action + " for player " + player.getName() + 
                   " at boundary " + boundary.getName());
        
        switch (action) {
            case WARNING_ONLY:
                // Just show warning - no other action needed
                break;
                
            case TELEPORT_BACK:
                Vector3f safePosition = findSafePosition(player.getPosition(), 50.0f);
                player.setPosition(safePosition);
                break;
                
            case BLOCK_MOVEMENT:
                // This would be handled by the movement system
                break;
                
            case KILL_PLAYER:
                player.takeDamage(player.getHealth(), "Environmental");
                break;
                
            case DAMAGE_OVER_TIME:
                float damage = boundary.getProperty("damage", Float.class);
                if (damage != null) {
                    player.takeDamage(damage * violation.getDuration(), "Environmental");
                }
                break;
                
            case APPLY_DAMAGE:
                Float fixedDamage = boundary.getProperty("damage", Float.class);
                if (fixedDamage != null) {
                    player.takeDamage(fixedDamage, "Environmental");
                }
                break;
                
            case TELEPORT_TO_LOCATION:
                Vector3f targetLocation = boundary.getProperty("targetLocation", Vector3f.class);
                if (targetLocation != null) {
                    player.setPosition(targetLocation);
                }
                break;
                
            case RESPAWN_PLAYER:
                // This would trigger the respawn system
                break;
                
            default:
                logger.warning("Unhandled boundary action: " + action);
                break;
        }
        
        violation.markActionExecuted();
        notifyActionExecuted(player, boundary, action);
    }
    
    // Event listener management
    public void addListener(BoundaryEventListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(BoundaryEventListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyBoundaryAdded(MapBoundary boundary) {
        for (BoundaryEventListener listener : listeners) {
            try {
                listener.onBoundaryAdded(boundary);
            } catch (Exception e) {
                logger.warning("Error in boundary event listener: " + e.getMessage());
            }
        }
    }
    
    private void notifyBoundaryRemoved(MapBoundary boundary) {
        for (BoundaryEventListener listener : listeners) {
            try {
                listener.onBoundaryRemoved(boundary);
            } catch (Exception e) {
                logger.warning("Error in boundary event listener: " + e.getMessage());
            }
        }
    }
    
    private void notifyViolationEnded(BoundaryViolation violation) {
        for (BoundaryEventListener listener : listeners) {
            try {
                listener.onViolationEnded(violation);
            } catch (Exception e) {
                logger.warning("Error in boundary event listener: " + e.getMessage());
            }
        }
    }
    
    private void notifyActionExecuted(Player player, MapBoundary boundary, BoundaryAction action) {
        for (BoundaryEventListener listener : listeners) {
            try {
                listener.onActionExecuted(player, boundary, action);
            } catch (Exception e) {
                logger.warning("Error in boundary event listener: " + e.getMessage());
            }
        }
    }
    
    // Getters and utility methods
    public MapBoundary getBoundary(String id) {
        return boundaries.get(id);
    }
    
    public Collection<MapBoundary> getAllBoundaries() {
        return new ArrayList<>(boundaries.values());
    }
    
    public Collection<MapBoundary> getActiveBoundaries() {
        return new ArrayList<>(activeBoundaries);
    }
    
    public Collection<BoundaryViolation> getActiveViolations() {
        return new ArrayList<>(activeViolations.values());
    }
    
    public int getBoundaryCount() {
        return boundaries.size();
    }
    
    public int getActiveBoundaryCount() {
        return activeBoundaries.size();
    }
    
    public float getAverageCheckTime() {
        return averageCheckTime;
    }
    
    public int getChecksPerFrame() {
        return checksPerFrame;
    }
    
    /**
     * Clear all boundaries and violations
     */
    public void clear() {
        boundaries.clear();
        activeBoundaries.clear();
        activeViolations.clear();
        spatialGrid.clear();
        logger.info("BoundarySystem cleared");
    }
    
    /**
     * Get system statistics for debugging
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalBoundaries", boundaries.size());
        stats.put("activeBoundaries", activeBoundaries.size());
        stats.put("activeViolations", activeViolations.size());
        stats.put("averageCheckTime", averageCheckTime);
        stats.put("checksPerFrame", checksPerFrame);
        stats.put("gridCells", spatialGrid.size());
        return stats;
    }
}