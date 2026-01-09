package fps.map.elements;

import fps.core.math.Vector3f;
import fps.core.math.BoundingBox;
import fps.game.Player;
import engine.logging.LogManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages all dynamic elements in a map.
 * Handles updates, interactions, spatial optimization, and network synchronization.
 */
public class DynamicElementSystem {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final Map<String, DynamicElement> elements;
    private final List<DynamicElement> activeElements;
    private final Map<String, List<DynamicElement>> spatialGrid;
    private final List<ElementEventListener> listeners;
    
    // Spatial optimization
    private final BoundingBox worldBounds;
    private final float gridSize;
    
    // Performance tracking
    private long lastUpdateTime;
    private float averageUpdateTime;
    private int updateCount;
    private int activeElementCount;
    
    // Update optimization
    private final Map<String, Long> lastElementUpdate;
    private final Set<String> elementsNeedingUpdate;
    
    public DynamicElementSystem(BoundingBox worldBounds, float gridSize) {
        this.elements = new ConcurrentHashMap<>();
        this.activeElements = new CopyOnWriteArrayList<>();
        this.spatialGrid = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.worldBounds = worldBounds;
        this.gridSize = gridSize;
        
        this.lastUpdateTime = System.currentTimeMillis();
        this.averageUpdateTime = 0.0f;
        this.updateCount = 0;
        this.activeElementCount = 0;
        
        this.lastElementUpdate = new ConcurrentHashMap<>();
        this.elementsNeedingUpdate = ConcurrentHashMap.newKeySet();
        
        logManager.info("DynamicElementSystem", "Dynamic element system initialized", 
                       "worldBounds", worldBounds.toString(), "gridSize", gridSize);
    }
    
    /**
     * Add a dynamic element to the system
     */
    public void addElement(DynamicElement element) {
        if (element == null) {
            throw new IllegalArgumentException("Element cannot be null");
        }
        
        elements.put(element.getId(), element);
        
        if (element.isActive()) {
            activeElements.add(element);
            addToSpatialGrid(element);
        }
        
        elementsNeedingUpdate.add(element.getId());
        
        logManager.info("DynamicElementSystem", "Added dynamic element", 
                       "id", element.getId(), "type", element.getType().toString());
        
        notifyElementAdded(element);
    }
    
    /**
     * Remove a dynamic element from the system
     */
    public void removeElement(String elementId) {
        DynamicElement element = elements.remove(elementId);
        if (element != null) {
            activeElements.remove(element);
            removeFromSpatialGrid(element);
            elementsNeedingUpdate.remove(elementId);
            lastElementUpdate.remove(elementId);
            
            logManager.info("DynamicElementSystem", "Removed dynamic element", "id", elementId);
            notifyElementRemoved(element);
        }
    }
    
    /**
     * Update all dynamic elements
     */
    public void update(float deltaTime, Collection<Player> players) {
        long startTime = System.nanoTime();
        
        // Update active elements
        updateActiveElements(deltaTime, players);
        
        // Optimize element activation based on player proximity
        optimizeElementActivation(players);
        
        // Update performance metrics
        long endTime = System.nanoTime();
        float updateTime = (endTime - startTime) / 1_000_000.0f; // Convert to milliseconds
        averageUpdateTime = (averageUpdateTime * updateCount + updateTime) / (updateCount + 1);
        updateCount++;
        lastUpdateTime = System.currentTimeMillis();
        activeElementCount = activeElements.size();
    }
    
    /**
     * Update active elements
     */
    private void updateActiveElements(float deltaTime, Collection<Player> players) {
        for (DynamicElement element : activeElements) {
            try {
                // Get nearby players for this element
                Collection<Player> nearbyPlayers = getPlayersNear(element, players);
                
                // Update the element
                element.update(deltaTime, nearbyPlayers);
                
                // Track update time
                lastElementUpdate.put(element.getId(), System.currentTimeMillis());
                
            } catch (Exception e) {
                logManager.error("DynamicElementSystem", "Error updating element", 
                               "elementId", element.getId(), e);
            }
        }
    }
    
    /**
     * Optimize element activation based on player proximity
     */
    private void optimizeElementActivation(Collection<Player> players) {
        for (DynamicElement element : elements.values()) {
            boolean shouldBeActive = element.shouldBeActive(players);
            
            if (shouldBeActive && !element.isActive()) {
                activateElement(element);
            } else if (!shouldBeActive && element.isActive()) {
                deactivateElement(element);
            }
        }
    }
    
    /**
     * Activate an element
     */
    private void activateElement(DynamicElement element) {
        element.setActive(true);
        if (!activeElements.contains(element)) {
            activeElements.add(element);
            addToSpatialGrid(element);
        }
        elementsNeedingUpdate.add(element.getId());
    }
    
    /**
     * Deactivate an element
     */
    private void deactivateElement(DynamicElement element) {
        element.setActive(false);
        activeElements.remove(element);
        removeFromSpatialGrid(element);
        elementsNeedingUpdate.remove(element.getId());
    }
    
    /**
     * Get players near a specific element
     */
    private Collection<Player> getPlayersNear(DynamicElement element, Collection<Player> allPlayers) {
        List<Player> nearbyPlayers = new ArrayList<>();
        BoundingBox activationBounds = element.getActivationBounds();
        
        for (Player player : allPlayers) {
            if (activationBounds.contains(player.getPosition())) {
                nearbyPlayers.add(player);
            }
        }
        
        return nearbyPlayers;
    }
    
    /**
     * Attempt to interact with an element
     */
    public InteractionResult interactWithElement(String elementId, Player player, String action) {
        DynamicElement element = elements.get(elementId);
        if (element == null) {
            return new InteractionResult(false, "Element not found: " + elementId);
        }
        
        if (!element.isActive()) {
            return new InteractionResult(false, "Element is not active: " + element.getName());
        }
        
        try {
            InteractionResult result = element.interact(player, action);
            
            if (result.isSuccess()) {
                notifyElementInteracted(element, player, action, result);
            }
            
            return result;
            
        } catch (Exception e) {
            logManager.error("DynamicElementSystem", "Error during element interaction", 
                           "elementId", elementId, "player", player.getName(), e);
            return new InteractionResult(false, "Interaction failed due to error");
        }
    }
    
    /**
     * Get element by ID
     */
    public DynamicElement getElement(String elementId) {
        return elements.get(elementId);
    }
    
    /**
     * Get elements by type
     */
    public List<DynamicElement> getElementsByType(ElementType type) {
        return elements.values().stream()
            .filter(element -> element.getType() == type)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get elements in a specific area
     */
    public List<DynamicElement> getElementsInArea(BoundingBox area) {
        List<DynamicElement> elementsInArea = new ArrayList<>();
        
        // Use spatial grid for efficient lookup
        Set<String> checkedGridCells = new HashSet<>();
        
        // Calculate grid cells that overlap with the area
        int minGridX = (int) Math.floor((area.getMin().x - worldBounds.getMin().x) / gridSize);
        int maxGridX = (int) Math.ceil((area.getMax().x - worldBounds.getMin().x) / gridSize);
        int minGridZ = (int) Math.floor((area.getMin().z - worldBounds.getMin().z) / gridSize);
        int maxGridZ = (int) Math.ceil((area.getMax().z - worldBounds.getMin().z) / gridSize);
        
        for (int x = minGridX; x <= maxGridX; x++) {
            for (int z = minGridZ; z <= maxGridZ; z++) {
                String gridKey = x + "," + z;
                if (checkedGridCells.add(gridKey)) {
                    List<DynamicElement> cellElements = spatialGrid.get(gridKey);
                    if (cellElements != null) {
                        for (DynamicElement element : cellElements) {
                            if (area.intersects(element.getBounds())) {
                                elementsInArea.add(element);
                            }
                        }
                    }
                }
            }
        }
        
        return elementsInArea;
    }
    
    /**
     * Get interactable elements near a position
     */
    public List<DynamicElement> getInteractableElementsNear(Vector3f position, float range) {
        List<DynamicElement> interactableElements = new ArrayList<>();
        
        BoundingBox searchArea = new BoundingBox(
            position.subtract(new Vector3f(range, range, range)),
            position.add(new Vector3f(range, range, range))
        );
        
        List<DynamicElement> nearbyElements = getElementsInArea(searchArea);
        
        for (DynamicElement element : nearbyElements) {
            if (element.getType().canBeInteracted() && 
                element.isActive() &&
                position.distance(element.getPosition()) <= element.getInteractionRange()) {
                interactableElements.add(element);
            }
        }
        
        // Sort by distance
        interactableElements.sort((a, b) -> 
            Float.compare(position.distance(a.getPosition()), position.distance(b.getPosition())));
        
        return interactableElements;
    }
    
    /**
     * Force update of specific element
     */
    public void forceUpdateElement(String elementId, float deltaTime, Collection<Player> players) {
        DynamicElement element = elements.get(elementId);
        if (element != null && element.isActive()) {
            Collection<Player> nearbyPlayers = getPlayersNear(element, players);
            element.update(deltaTime, nearbyPlayers);
            lastElementUpdate.put(elementId, System.currentTimeMillis());
        }
    }
    
    /**
     * Add element to spatial grid
     */
    private void addToSpatialGrid(DynamicElement element) {
        BoundingBox bounds = element.getActivationBounds();
        
        int minGridX = (int) Math.floor((bounds.getMin().x - worldBounds.getMin().x) / gridSize);
        int maxGridX = (int) Math.ceil((bounds.getMax().x - worldBounds.getMin().x) / gridSize);
        int minGridZ = (int) Math.floor((bounds.getMin().z - worldBounds.getMin().z) / gridSize);
        int maxGridZ = (int) Math.ceil((bounds.getMax().z - worldBounds.getMin().z) / gridSize);
        
        for (int x = minGridX; x <= maxGridX; x++) {
            for (int z = minGridZ; z <= maxGridZ; z++) {
                String gridKey = x + "," + z;
                spatialGrid.computeIfAbsent(gridKey, k -> new ArrayList<>()).add(element);
            }
        }
    }
    
    /**
     * Remove element from spatial grid
     */
    private void removeFromSpatialGrid(DynamicElement element) {
        spatialGrid.values().forEach(list -> list.remove(element));
    }
    
    /**
     * Clear all elements
     */
    public void clear() {
        elements.clear();
        activeElements.clear();
        spatialGrid.clear();
        elementsNeedingUpdate.clear();
        lastElementUpdate.clear();
        
        logManager.info("DynamicElementSystem", "Cleared all dynamic elements");
    }
    
    // Event listener management
    public void addListener(ElementEventListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(ElementEventListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyElementAdded(DynamicElement element) {
        for (ElementEventListener listener : listeners) {
            try {
                listener.onElementAdded(element);
            } catch (Exception e) {
                logManager.error("DynamicElementSystem", "Error in element event listener", e);
            }
        }
    }
    
    private void notifyElementRemoved(DynamicElement element) {
        for (ElementEventListener listener : listeners) {
            try {
                listener.onElementRemoved(element);
            } catch (Exception e) {
                logManager.error("DynamicElementSystem", "Error in element event listener", e);
            }
        }
    }
    
    private void notifyElementInteracted(DynamicElement element, Player player, String action, InteractionResult result) {
        for (ElementEventListener listener : listeners) {
            try {
                listener.onElementInteracted(element, player, action, result);
            } catch (Exception e) {
                logManager.error("DynamicElementSystem", "Error in element event listener", e);
            }
        }
    }
    
    // Getters and statistics
    public Collection<DynamicElement> getAllElements() {
        return new ArrayList<>(elements.values());
    }
    
    public Collection<DynamicElement> getActiveElements() {
        return new ArrayList<>(activeElements);
    }
    
    public int getElementCount() {
        return elements.size();
    }
    
    public int getActiveElementCount() {
        return activeElementCount;
    }
    
    public float getAverageUpdateTime() {
        return averageUpdateTime;
    }
    
    public int getUpdateCount() {
        return updateCount;
    }
    
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalElements", elements.size());
        stats.put("activeElements", activeElementCount);
        stats.put("averageUpdateTime", averageUpdateTime);
        stats.put("updateCount", updateCount);
        stats.put("gridCells", spatialGrid.size());
        stats.put("elementsNeedingUpdate", elementsNeedingUpdate.size());
        
        // Element type breakdown
        Map<ElementType, Integer> typeBreakdown = new HashMap<>();
        for (DynamicElement element : elements.values()) {
            typeBreakdown.merge(element.getType(), 1, Integer::sum);
        }
        stats.put("elementsByType", typeBreakdown);
        
        return stats;
    }
    
    /**
     * Interface for listening to element system events
     */
    public interface ElementEventListener {
        default void onElementAdded(DynamicElement element) {}
        default void onElementRemoved(DynamicElement element) {}
        default void onElementInteracted(DynamicElement element, Player player, String action, InteractionResult result) {}
        default void onElementStateChanged(DynamicElement element, ElementState oldState, ElementState newState) {}
    }
}