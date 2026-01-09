package fps.map.elements;

/**
 * Defines the type of dynamic element and its general behavior category.
 */
public enum ElementType {
    /**
     * Doors that can be opened, closed, locked, or destroyed
     */
    DOOR("Door", true, true, false),
    
    /**
     * Elevators and moving platforms
     */
    ELEVATOR("Elevator", true, true, false),
    
    /**
     * Moving platforms (horizontal or vertical)
     */
    MOVING_PLATFORM("Moving Platform", true, true, false),
    
    /**
     * Destructible objects like walls, barriers, or cover
     */
    DESTRUCTIBLE("Destructible Object", false, false, true),
    
    /**
     * Switches, buttons, and control panels
     */
    SWITCH("Switch", true, false, false),
    
    /**
     * Rotating elements like bridges or barriers
     */
    ROTATING_ELEMENT("Rotating Element", true, true, false),
    
    /**
     * Breakable glass, windows, or barriers
     */
    BREAKABLE("Breakable Object", false, false, true),
    
    /**
     * Triggered traps or hazards
     */
    TRAP("Trap", true, false, true),
    
    /**
     * Spawners for items, enemies, or effects
     */
    SPAWNER("Spawner", true, false, false),
    
    /**
     * Teleporters and portals
     */
    TELEPORTER("Teleporter", true, true, false),
    
    /**
     * Environmental hazards like fire, electricity, or gas
     */
    HAZARD("Environmental Hazard", true, false, true),
    
    /**
     * Interactive terminals or computers
     */
    TERMINAL("Terminal", true, false, false),
    
    /**
     * Conveyor belts and transport systems
     */
    CONVEYOR("Conveyor Belt", true, true, false),
    
    /**
     * Retractable cover or barriers
     */
    RETRACTABLE_COVER("Retractable Cover", true, true, false),
    
    /**
     * Custom scripted elements
     */
    CUSTOM("Custom Element", true, true, true);
    
    private final String displayName;
    private final boolean canBeInteracted;
    private final boolean canMove;
    private final boolean canBeDestroyed;
    
    ElementType(String displayName, boolean canBeInteracted, boolean canMove, boolean canBeDestroyed) {
        this.displayName = displayName;
        this.canBeInteracted = canBeInteracted;
        this.canMove = canMove;
        this.canBeDestroyed = canBeDestroyed;
    }
    
    /**
     * Get the human-readable display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Check if this element type can be interacted with by players
     */
    public boolean canBeInteracted() {
        return canBeInteracted;
    }
    
    /**
     * Check if this element type can move or change position
     */
    public boolean canMove() {
        return canMove;
    }
    
    /**
     * Check if this element type can be destroyed
     */
    public boolean canBeDestroyed() {
        return canBeDestroyed;
    }
    
    /**
     * Get the default interaction range for this element type
     */
    public float getDefaultInteractionRange() {
        switch (this) {
            case DOOR:
            case SWITCH:
                return 3.0f;
            case ELEVATOR:
            case MOVING_PLATFORM:
                return 5.0f;
            case TERMINAL:
                return 2.0f;
            case TELEPORTER:
                return 4.0f;
            case DESTRUCTIBLE:
            case BREAKABLE:
                return 1.0f; // Usually destroyed by weapons, not interaction
            default:
                return 3.0f;
        }
    }
    
    /**
     * Get the default network priority for this element type
     */
    public int getDefaultNetworkPriority() {
        switch (this) {
            case DOOR:
            case ELEVATOR:
            case MOVING_PLATFORM:
            case TELEPORTER:
                return 0; // High priority - affects player movement
            case DESTRUCTIBLE:
            case BREAKABLE:
            case TRAP:
                return 1; // Medium-high priority - affects gameplay
            case SWITCH:
            case TERMINAL:
            case SPAWNER:
                return 2; // Medium priority - important but not movement-critical
            default:
                return 3; // Low priority
        }
    }
    
    /**
     * Check if this element type requires line of sight for interaction
     */
    public boolean requiresLineOfSight() {
        switch (this) {
            case DOOR:
            case SWITCH:
            case TERMINAL:
                return true;
            case ELEVATOR:
            case MOVING_PLATFORM:
            case TELEPORTER:
                return false; // Can interact even if partially obscured
            default:
                return true;
        }
    }
    
    /**
     * Get the default interaction prompt for this element type
     */
    public String getDefaultInteractionPrompt() {
        switch (this) {
            case DOOR:
                return "Press E to open/close door";
            case ELEVATOR:
                return "Press E to call elevator";
            case SWITCH:
                return "Press E to activate switch";
            case TERMINAL:
                return "Press E to use terminal";
            case TELEPORTER:
                return "Press E to teleport";
            case MOVING_PLATFORM:
                return "Press E to activate platform";
            default:
                return "Press E to interact";
        }
    }
    
    /**
     * Check if this element type should be visible in the level editor
     */
    public boolean isVisibleInEditor() {
        return true; // All element types should be visible in editor
    }
    
    /**
     * Get the default collision behavior for this element type
     */
    public CollisionBehavior getDefaultCollisionBehavior() {
        switch (this) {
            case DOOR:
                return CollisionBehavior.CONDITIONAL; // Blocks when closed
            case ELEVATOR:
            case MOVING_PLATFORM:
                return CollisionBehavior.SOLID; // Always solid
            case DESTRUCTIBLE:
            case BREAKABLE:
                return CollisionBehavior.SOLID; // Solid until destroyed
            case TELEPORTER:
            case HAZARD:
                return CollisionBehavior.TRIGGER; // Trigger events
            case SWITCH:
            case TERMINAL:
                return CollisionBehavior.NONE; // No collision, just interaction
            default:
                return CollisionBehavior.SOLID;
        }
    }
    
    /**
     * Collision behavior options for dynamic elements
     */
    public enum CollisionBehavior {
        NONE,        // No collision detection
        SOLID,       // Always blocks movement
        TRIGGER,     // Triggers events but doesn't block
        CONDITIONAL  // Collision depends on element state
    }
}