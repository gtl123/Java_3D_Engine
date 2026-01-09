package fps.map.elements;

import fps.core.math.Vector3f;
import fps.core.math.BoundingBox;
import fps.game.Player;
import java.util.*;

/**
 * Base class for all dynamic map elements that can change state during gameplay.
 * Includes doors, elevators, destructible objects, moving platforms, etc.
 */
public abstract class DynamicElement {
    
    protected final String id;
    protected final String name;
    protected final ElementType type;
    protected final Vector3f position;
    protected final Vector3f rotation;
    protected final BoundingBox bounds;
    protected final Map<String, Object> properties;
    
    // State management
    protected ElementState currentState;
    protected ElementState previousState;
    protected float stateTransitionTime;
    protected float stateTransitionDuration;
    protected boolean isTransitioning;
    
    // Interaction properties
    protected final Set<String> allowedInteractors;
    protected final float interactionRange;
    protected final boolean requiresLineOfSight;
    protected final String interactionPrompt;
    
    // Network synchronization
    protected boolean networkSynced;
    protected long lastNetworkUpdate;
    protected int networkPriority;
    
    // Performance optimization
    protected boolean isActive;
    protected float lastUpdateTime;
    protected BoundingBox activationBounds;
    
    protected DynamicElement(Builder<?> builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.type = builder.type;
        this.position = builder.position;
        this.rotation = builder.rotation;
        this.bounds = builder.bounds;
        this.properties = new HashMap<>(builder.properties);
        
        this.currentState = builder.initialState;
        this.previousState = builder.initialState;
        this.stateTransitionTime = 0.0f;
        this.stateTransitionDuration = builder.transitionDuration;
        this.isTransitioning = false;
        
        this.allowedInteractors = new HashSet<>(builder.allowedInteractors);
        this.interactionRange = builder.interactionRange;
        this.requiresLineOfSight = builder.requiresLineOfSight;
        this.interactionPrompt = builder.interactionPrompt;
        
        this.networkSynced = builder.networkSynced;
        this.networkPriority = builder.networkPriority;
        
        this.isActive = true;
        this.lastUpdateTime = 0.0f;
        this.activationBounds = calculateActivationBounds();
    }
    
    /**
     * Update the dynamic element
     */
    public void update(float deltaTime, Collection<Player> nearbyPlayers) {
        lastUpdateTime += deltaTime;
        
        // Update state transition
        if (isTransitioning) {
            updateStateTransition(deltaTime);
        }
        
        // Update element-specific logic
        updateElement(deltaTime, nearbyPlayers);
        
        // Check for player interactions
        checkPlayerInteractions(nearbyPlayers);
        
        // Update network synchronization if needed
        if (networkSynced && shouldSyncToNetwork()) {
            syncToNetwork();
        }
    }
    
    /**
     * Abstract method for element-specific update logic
     */
    protected abstract void updateElement(float deltaTime, Collection<Player> nearbyPlayers);
    
    /**
     * Attempt to interact with this element
     */
    public InteractionResult interact(Player player, String action) {
        // Check if player can interact
        if (!canPlayerInteract(player)) {
            return new InteractionResult(false, "Cannot interact with " + name);
        }
        
        // Check interaction range
        if (position.distance(player.getPosition()) > interactionRange) {
            return new InteractionResult(false, "Too far from " + name);
        }
        
        // Check line of sight if required
        if (requiresLineOfSight && !hasLineOfSight(player)) {
            return new InteractionResult(false, "No line of sight to " + name);
        }
        
        // Perform element-specific interaction
        return performInteraction(player, action);
    }
    
    /**
     * Abstract method for element-specific interaction logic
     */
    protected abstract InteractionResult performInteraction(Player player, String action);
    
    /**
     * Change the element's state
     */
    public boolean changeState(ElementState newState, float transitionDuration) {
        if (newState == currentState || isTransitioning) {
            return false;
        }
        
        previousState = currentState;
        currentState = newState;
        this.stateTransitionDuration = transitionDuration;
        stateTransitionTime = 0.0f;
        isTransitioning = transitionDuration > 0.0f;
        
        onStateChanged(previousState, newState);
        return true;
    }
    
    /**
     * Force set state without transition
     */
    public void setState(ElementState state) {
        previousState = currentState;
        currentState = state;
        isTransitioning = false;
        stateTransitionTime = 0.0f;
        
        onStateChanged(previousState, currentState);
    }
    
    /**
     * Called when state changes
     */
    protected void onStateChanged(ElementState oldState, ElementState newState) {
        // Override in subclasses for specific behavior
    }
    
    /**
     * Update state transition
     */
    private void updateStateTransition(float deltaTime) {
        stateTransitionTime += deltaTime;
        
        if (stateTransitionTime >= stateTransitionDuration) {
            isTransitioning = false;
            stateTransitionTime = stateTransitionDuration;
            onTransitionCompleted();
        }
        
        // Update transition progress
        float progress = stateTransitionTime / stateTransitionDuration;
        onTransitionUpdate(progress);
    }
    
    /**
     * Called during state transition
     */
    protected void onTransitionUpdate(float progress) {
        // Override in subclasses for transition animations
    }
    
    /**
     * Called when transition is completed
     */
    protected void onTransitionCompleted() {
        // Override in subclasses for completion logic
    }
    
    /**
     * Check if a player can interact with this element
     */
    protected boolean canPlayerInteract(Player player) {
        if (!isActive || isTransitioning) {
            return false;
        }
        
        // Check if player type is allowed
        if (!allowedInteractors.isEmpty()) {
            String playerType = player.getProperty("type", String.class);
            if (playerType == null || !allowedInteractors.contains(playerType)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check line of sight to player
     */
    protected boolean hasLineOfSight(Player player) {
        // This would use the map's geometry system for raycast
        // For now, return true as a placeholder
        return true;
    }
    
    /**
     * Check for player interactions
     */
    private void checkPlayerInteractions(Collection<Player> nearbyPlayers) {
        for (Player player : nearbyPlayers) {
            if (isPlayerInInteractionRange(player)) {
                notifyPlayerOfInteraction(player);
            }
        }
    }
    
    /**
     * Check if player is in interaction range
     */
    private boolean isPlayerInInteractionRange(Player player) {
        return position.distance(player.getPosition()) <= interactionRange;
    }
    
    /**
     * Notify player that they can interact
     */
    private void notifyPlayerOfInteraction(Player player) {
        if (canPlayerInteract(player)) {
            // This would send UI prompt to player
            // player.showInteractionPrompt(interactionPrompt, this);
        }
    }
    
    /**
     * Check if element should sync to network
     */
    private boolean shouldSyncToNetwork() {
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastNetworkUpdate) > getNetworkSyncInterval();
    }
    
    /**
     * Get network sync interval based on priority
     */
    private long getNetworkSyncInterval() {
        switch (networkPriority) {
            case 0: return 16; // 60 FPS for critical elements
            case 1: return 33; // 30 FPS for important elements
            case 2: return 100; // 10 FPS for normal elements
            default: return 500; // 2 FPS for low priority elements
        }
    }
    
    /**
     * Sync element state to network
     */
    private void syncToNetwork() {
        lastNetworkUpdate = System.currentTimeMillis();
        // This would send network update
        // NetworkManager.sendElementUpdate(this);
    }
    
    /**
     * Calculate activation bounds for optimization
     */
    private BoundingBox calculateActivationBounds() {
        float expansion = Math.max(interactionRange * 2, 50.0f);
        Vector3f min = bounds.getMin().subtract(new Vector3f(expansion, expansion, expansion));
        Vector3f max = bounds.getMax().add(new Vector3f(expansion, expansion, expansion));
        return new BoundingBox(min, max);
    }
    
    /**
     * Check if element should be active based on player proximity
     */
    public boolean shouldBeActive(Collection<Player> allPlayers) {
        for (Player player : allPlayers) {
            if (activationBounds.contains(player.getPosition())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get current transition progress (0.0 to 1.0)
     */
    public float getTransitionProgress() {
        if (!isTransitioning || stateTransitionDuration <= 0) {
            return 1.0f;
        }
        return Math.min(1.0f, stateTransitionTime / stateTransitionDuration);
    }
    
    /**
     * Get interpolated position during transitions
     */
    public Vector3f getInterpolatedPosition() {
        // Override in subclasses that move during transitions
        return position;
    }
    
    /**
     * Get interpolated rotation during transitions
     */
    public Vector3f getInterpolatedRotation() {
        // Override in subclasses that rotate during transitions
        return rotation;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public ElementType getType() { return type; }
    public Vector3f getPosition() { return position; }
    public Vector3f getRotation() { return rotation; }
    public BoundingBox getBounds() { return bounds; }
    public ElementState getCurrentState() { return currentState; }
    public ElementState getPreviousState() { return previousState; }
    public boolean isTransitioning() { return isTransitioning; }
    public float getInteractionRange() { return interactionRange; }
    public String getInteractionPrompt() { return interactionPrompt; }
    public boolean isActive() { return isActive; }
    public boolean isNetworkSynced() { return networkSynced; }
    public int getNetworkPriority() { return networkPriority; }
    public BoundingBox getActivationBounds() { return activationBounds; }
    
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, Class<T> type) {
        Object value = properties.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }
    
    public void setActive(boolean active) {
        this.isActive = active;
    }
    
    // Builder pattern for flexible construction
    public abstract static class Builder<T extends Builder<T>> {
        protected String id;
        protected String name;
        protected ElementType type;
        protected Vector3f position = new Vector3f(0, 0, 0);
        protected Vector3f rotation = new Vector3f(0, 0, 0);
        protected BoundingBox bounds;
        protected Map<String, Object> properties = new HashMap<>();
        protected ElementState initialState = ElementState.IDLE;
        protected float transitionDuration = 1.0f;
        protected Set<String> allowedInteractors = new HashSet<>();
        protected float interactionRange = 5.0f;
        protected boolean requiresLineOfSight = false;
        protected String interactionPrompt = "Press E to interact";
        protected boolean networkSynced = true;
        protected int networkPriority = 1;
        
        public Builder(String id, String name, ElementType type) {
            this.id = id;
            this.name = name;
            this.type = type;
        }
        
        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
        }
        
        public T position(Vector3f position) {
            this.position = position;
            return self();
        }
        
        public T rotation(Vector3f rotation) {
            this.rotation = rotation;
            return self();
        }
        
        public T bounds(BoundingBox bounds) {
            this.bounds = bounds;
            return self();
        }
        
        public T property(String key, Object value) {
            this.properties.put(key, value);
            return self();
        }
        
        public T initialState(ElementState state) {
            this.initialState = state;
            return self();
        }
        
        public T transitionDuration(float duration) {
            this.transitionDuration = duration;
            return self();
        }
        
        public T allowedInteractor(String interactorType) {
            this.allowedInteractors.add(interactorType);
            return self();
        }
        
        public T interactionRange(float range) {
            this.interactionRange = range;
            return self();
        }
        
        public T requiresLineOfSight(boolean requires) {
            this.requiresLineOfSight = requires;
            return self();
        }
        
        public T interactionPrompt(String prompt) {
            this.interactionPrompt = prompt;
            return self();
        }
        
        public T networkSynced(boolean synced) {
            this.networkSynced = synced;
            return self();
        }
        
        public T networkPriority(int priority) {
            this.networkPriority = priority;
            return self();
        }
        
        public abstract DynamicElement build();
    }
}