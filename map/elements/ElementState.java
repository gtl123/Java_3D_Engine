package fps.map.elements;

/**
 * Defines the possible states for dynamic elements.
 * States determine behavior, appearance, and interaction possibilities.
 */
public enum ElementState {
    /**
     * Element is inactive or idle
     */
    IDLE("Idle", false, true),
    
    /**
     * Element is active and functioning normally
     */
    ACTIVE("Active", true, true),
    
    /**
     * Element is opening (doors, barriers, etc.)
     */
    OPENING("Opening", true, false),
    
    /**
     * Element is fully open
     */
    OPEN("Open", true, true),
    
    /**
     * Element is closing
     */
    CLOSING("Closing", true, false),
    
    /**
     * Element is fully closed
     */
    CLOSED("Closed", false, true),
    
    /**
     * Element is moving up (elevators, platforms)
     */
    MOVING_UP("Moving Up", true, false),
    
    /**
     * Element is moving down
     */
    MOVING_DOWN("Moving Down", true, false),
    
    /**
     * Element is moving horizontally
     */
    MOVING_HORIZONTAL("Moving Horizontal", true, false),
    
    /**
     * Element is rotating
     */
    ROTATING("Rotating", true, false),
    
    /**
     * Element is locked and cannot be interacted with
     */
    LOCKED("Locked", false, false),
    
    /**
     * Element is damaged but still functional
     */
    DAMAGED("Damaged", true, true),
    
    /**
     * Element is destroyed and non-functional
     */
    DESTROYED("Destroyed", false, false),
    
    /**
     * Element is being repaired or restored
     */
    REPAIRING("Repairing", false, false),
    
    /**
     * Element is powered on
     */
    POWERED("Powered", true, true),
    
    /**
     * Element is powered off
     */
    UNPOWERED("Unpowered", false, true),
    
    /**
     * Element is overloaded or malfunctioning
     */
    OVERLOADED("Overloaded", false, false),
    
    /**
     * Element is in cooldown period
     */
    COOLDOWN("Cooldown", false, false),
    
    /**
     * Element is charging or preparing
     */
    CHARGING("Charging", false, false),
    
    /**
     * Element is ready for use
     */
    READY("Ready", true, true),
    
    /**
     * Element is in use by a player
     */
    IN_USE("In Use", true, false),
    
    /**
     * Element is temporarily disabled
     */
    DISABLED("Disabled", false, false),
    
    /**
     * Element is in maintenance mode
     */
    MAINTENANCE("Maintenance", false, false),
    
    /**
     * Element is in emergency state
     */
    EMERGENCY("Emergency", true, false),
    
    /**
     * Custom state for scripted behavior
     */
    CUSTOM("Custom", true, true);
    
    private final String displayName;
    private final boolean allowsInteraction;
    private final boolean allowsStateChange;
    
    ElementState(String displayName, boolean allowsInteraction, boolean allowsStateChange) {
        this.displayName = displayName;
        this.allowsInteraction = allowsInteraction;
        this.allowsStateChange = allowsStateChange;
    }
    
    /**
     * Get the human-readable display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Check if the element can be interacted with in this state
     */
    public boolean allowsInteraction() {
        return allowsInteraction;
    }
    
    /**
     * Check if the element can change state from this state
     */
    public boolean allowsStateChange() {
        return allowsStateChange;
    }
    
    /**
     * Check if this is a transitional state (element is changing)
     */
    public boolean isTransitional() {
        switch (this) {
            case OPENING:
            case CLOSING:
            case MOVING_UP:
            case MOVING_DOWN:
            case MOVING_HORIZONTAL:
            case ROTATING:
            case REPAIRING:
            case CHARGING:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Check if this is a stable state (element is not changing)
     */
    public boolean isStable() {
        return !isTransitional();
    }
    
    /**
     * Check if this state indicates the element is functional
     */
    public boolean isFunctional() {
        switch (this) {
            case DESTROYED:
            case OVERLOADED:
            case DISABLED:
            case MAINTENANCE:
                return false;
            default:
                return true;
        }
    }
    
    /**
     * Check if this state indicates the element is accessible
     */
    public boolean isAccessible() {
        switch (this) {
            case OPEN:
            case ACTIVE:
            case READY:
            case POWERED:
                return true;
            case CLOSED:
            case LOCKED:
            case DESTROYED:
            case DISABLED:
                return false;
            default:
                return allowsInteraction;
        }
    }
    
    /**
     * Get the opposite state if applicable
     */
    public ElementState getOpposite() {
        switch (this) {
            case OPEN:
                return CLOSED;
            case CLOSED:
                return OPEN;
            case OPENING:
                return CLOSING;
            case CLOSING:
                return OPENING;
            case ACTIVE:
                return IDLE;
            case IDLE:
                return ACTIVE;
            case POWERED:
                return UNPOWERED;
            case UNPOWERED:
                return POWERED;
            case MOVING_UP:
                return MOVING_DOWN;
            case MOVING_DOWN:
                return MOVING_UP;
            default:
                return this; // No clear opposite
        }
    }
    
    /**
     * Get the completion state for transitional states
     */
    public ElementState getCompletionState() {
        switch (this) {
            case OPENING:
                return OPEN;
            case CLOSING:
                return CLOSED;
            case CHARGING:
                return READY;
            case REPAIRING:
                return ACTIVE;
            case MOVING_UP:
            case MOVING_DOWN:
            case MOVING_HORIZONTAL:
            case ROTATING:
                return ACTIVE;
            default:
                return this; // Already a completion state
        }
    }
    
    /**
     * Check if transition to another state is valid
     */
    public boolean canTransitionTo(ElementState newState) {
        // Can't transition if state changes are not allowed
        if (!allowsStateChange) {
            return false;
        }
        
        // Can't transition to the same state
        if (this == newState) {
            return false;
        }
        
        // Destroyed elements can only be repaired
        if (this == DESTROYED && newState != REPAIRING) {
            return false;
        }
        
        // Locked elements can only be unlocked to their previous state
        if (this == LOCKED) {
            return newState == CLOSED || newState == OPEN || newState == IDLE;
        }
        
        // Transitional states should complete before changing to other states
        if (isTransitional() && newState != getCompletionState()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Get the priority level for network synchronization
     */
    public int getNetworkPriority() {
        if (isTransitional()) {
            return 0; // High priority for moving elements
        }
        
        switch (this) {
            case OPEN:
            case CLOSED:
            case ACTIVE:
            case IDLE:
                return 1; // Medium priority for state changes
            case DESTROYED:
            case LOCKED:
            case EMERGENCY:
                return 0; // High priority for critical states
            default:
                return 2; // Low priority for other states
        }
    }
    
    /**
     * Get the visual effect associated with this state
     */
    public String getVisualEffect() {
        switch (this) {
            case OPENING:
            case CLOSING:
                return "door_movement";
            case MOVING_UP:
            case MOVING_DOWN:
            case MOVING_HORIZONTAL:
                return "platform_movement";
            case ROTATING:
                return "rotation_effect";
            case CHARGING:
                return "charge_buildup";
            case OVERLOADED:
                return "electrical_sparks";
            case DESTROYED:
                return "destruction_debris";
            case EMERGENCY:
                return "emergency_lights";
            case POWERED:
                return "power_glow";
            default:
                return null; // No special effect
        }
    }
    
    /**
     * Get the audio effect associated with this state
     */
    public String getAudioEffect() {
        switch (this) {
            case OPENING:
                return "door_open";
            case CLOSING:
                return "door_close";
            case MOVING_UP:
            case MOVING_DOWN:
                return "elevator_move";
            case MOVING_HORIZONTAL:
                return "platform_move";
            case ROTATING:
                return "mechanical_rotation";
            case CHARGING:
                return "power_charge";
            case OVERLOADED:
                return "electrical_overload";
            case DESTROYED:
                return "destruction_sound";
            case EMERGENCY:
                return "alarm_sound";
            case LOCKED:
                return "lock_sound";
            default:
                return null; // No special sound
        }
    }
}