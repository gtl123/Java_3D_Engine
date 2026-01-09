package fps.map.boundaries;

/**
 * Defines the type of map boundary and its intended behavior.
 * Each type has different default properties and actions.
 */
public enum BoundaryType {
    /**
     * Defines the main playable area of the map.
     * Players should stay within this boundary during normal gameplay.
     */
    PLAYABLE_AREA("Playable Area", true, false),
    
    /**
     * Areas that are outside the intended play space.
     * Usually triggers teleport back to playable area.
     */
    OUT_OF_BOUNDS("Out of Bounds", false, true),
    
    /**
     * Dangerous areas that kill players who enter them.
     * Examples: lava pits, bottomless chasms, radiation zones.
     */
    DEATH_ZONE("Death Zone", false, true),
    
    /**
     * Areas with restricted access for certain game modes or conditions.
     * May be temporarily inaccessible or require special permissions.
     */
    RESTRICTED_AREA("Restricted Area", false, true),
    
    /**
     * Areas that provide warnings but don't necessarily restrict movement.
     * Used for environmental hazards or tactical information.
     */
    WARNING_ZONE("Warning Zone", true, false),
    
    /**
     * Special zones for specific game mechanics.
     * Behavior depends on custom properties and actions.
     */
    CUSTOM_ZONE("Custom Zone", true, false),
    
    /**
     * Invisible boundaries used for technical purposes.
     * Usually not visible to players but affect gameplay mechanics.
     */
    INVISIBLE_BARRIER("Invisible Barrier", false, true),
    
    /**
     * Areas where spectators can move but players cannot.
     * Used for spectator-only zones in competitive matches.
     */
    SPECTATOR_ONLY("Spectator Only", false, true);
    
    private final String displayName;
    private final boolean allowsMovement;
    private final boolean requiresAction;
    
    BoundaryType(String displayName, boolean allowsMovement, boolean requiresAction) {
        this.displayName = displayName;
        this.allowsMovement = allowsMovement;
        this.requiresAction = requiresAction;
    }
    
    /**
     * Get the human-readable display name for this boundary type
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Check if this boundary type allows free movement within it
     */
    public boolean allowsMovement() {
        return allowsMovement;
    }
    
    /**
     * Check if this boundary type requires an action when triggered
     */
    public boolean requiresAction() {
        return requiresAction;
    }
    
    /**
     * Get the default boundary action for this type
     */
    public BoundaryAction getDefaultAction() {
        switch (this) {
            case PLAYABLE_AREA:
                return BoundaryAction.TELEPORT_BACK;
            case OUT_OF_BOUNDS:
                return BoundaryAction.TELEPORT_BACK;
            case DEATH_ZONE:
                return BoundaryAction.KILL_PLAYER;
            case RESTRICTED_AREA:
                return BoundaryAction.BLOCK_MOVEMENT;
            case WARNING_ZONE:
                return BoundaryAction.WARNING_ONLY;
            case CUSTOM_ZONE:
                return BoundaryAction.CUSTOM_ACTION;
            case INVISIBLE_BARRIER:
                return BoundaryAction.BLOCK_MOVEMENT;
            case SPECTATOR_ONLY:
                return BoundaryAction.TELEPORT_BACK;
            default:
                return BoundaryAction.WARNING_ONLY;
        }
    }
    
    /**
     * Get the default warning time for this boundary type
     */
    public float getDefaultWarningTime() {
        switch (this) {
            case DEATH_ZONE:
                return 1.0f; // Quick warning for immediate danger
            case OUT_OF_BOUNDS:
            case PLAYABLE_AREA:
                return 3.0f; // Standard warning time
            case RESTRICTED_AREA:
                return 2.0f; // Moderate warning
            case WARNING_ZONE:
                return 0.5f; // Brief notification
            default:
                return 2.0f;
        }
    }
    
    /**
     * Check if this boundary type should show visual effects
     */
    public boolean shouldShowVisualEffects() {
        return this != INVISIBLE_BARRIER;
    }
    
    /**
     * Check if this boundary type should be visible in the level editor
     */
    public boolean isVisibleInEditor() {
        return true; // All boundaries should be visible in editor
    }
}