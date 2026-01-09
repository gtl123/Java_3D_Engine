package fps.map.boundaries;

/**
 * Defines the action to take when a boundary is triggered.
 * Each action has different behavior and requirements.
 */
public enum BoundaryAction {
    /**
     * Show a warning message but take no other action.
     * Used for informational boundaries or soft warnings.
     */
    WARNING_ONLY("Warning Only", false, false),
    
    /**
     * Teleport the player back to a safe location.
     * Usually the last valid position or nearest spawn point.
     */
    TELEPORT_BACK("Teleport Back", true, false),
    
    /**
     * Prevent the player from moving further in the boundary direction.
     * Creates an invisible wall effect.
     */
    BLOCK_MOVEMENT("Block Movement", true, false),
    
    /**
     * Instantly kill the player.
     * Used for death zones like lava pits or bottomless chasms.
     */
    KILL_PLAYER("Kill Player", true, true),
    
    /**
     * Apply damage over time while in the boundary.
     * Used for hazardous areas that aren't instantly lethal.
     */
    DAMAGE_OVER_TIME("Damage Over Time", true, true),
    
    /**
     * Reduce player health by a fixed amount.
     * Used for environmental hazards with immediate but non-lethal damage.
     */
    APPLY_DAMAGE("Apply Damage", true, true),
    
    /**
     * Apply a status effect to the player.
     * Could be poison, slow, blind, etc.
     */
    APPLY_STATUS_EFFECT("Apply Status Effect", true, false),
    
    /**
     * Teleport to a specific location.
     * Used for portals or special transport zones.
     */
    TELEPORT_TO_LOCATION("Teleport to Location", true, false),
    
    /**
     * Respawn the player at their team's spawn point.
     * Used for certain game modes or special areas.
     */
    RESPAWN_PLAYER("Respawn Player", true, true),
    
    /**
     * Execute a custom action defined by the boundary properties.
     * Allows for game-specific or map-specific behaviors.
     */
    CUSTOM_ACTION("Custom Action", true, false),
    
    /**
     * Push the player away from the boundary.
     * Creates a repelling force effect.
     */
    PUSH_AWAY("Push Away", true, false),
    
    /**
     * Slow down the player's movement.
     * Used for areas that should be traversed carefully.
     */
    SLOW_MOVEMENT("Slow Movement", true, false),
    
    /**
     * Disable certain player abilities.
     * Could disable weapons, jumping, or special abilities.
     */
    DISABLE_ABILITIES("Disable Abilities", true, false),
    
    /**
     * Force the player to crouch or go prone.
     * Used for low-ceiling areas or stealth zones.
     */
    FORCE_CROUCH("Force Crouch", true, false);
    
    private final String displayName;
    private final boolean affectsPlayer;
    private final boolean canCauseDeath;
    
    BoundaryAction(String displayName, boolean affectsPlayer, boolean canCauseDeath) {
        this.displayName = displayName;
        this.affectsPlayer = affectsPlayer;
        this.canCauseDeath = canCauseDeath;
    }
    
    /**
     * Get the human-readable display name for this action
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Check if this action directly affects the player
     */
    public boolean affectsPlayer() {
        return affectsPlayer;
    }
    
    /**
     * Check if this action can potentially cause player death
     */
    public boolean canCauseDeath() {
        return canCauseDeath;
    }
    
    /**
     * Get the default delay before executing this action
     */
    public float getDefaultDelay() {
        switch (this) {
            case WARNING_ONLY:
                return 0.0f; // Immediate warning
            case KILL_PLAYER:
                return 0.5f; // Brief delay for dramatic effect
            case DAMAGE_OVER_TIME:
                return 0.1f; // Quick start for DOT
            case APPLY_DAMAGE:
                return 0.2f; // Small delay for impact
            case TELEPORT_BACK:
                return 1.0f; // Give player time to react
            case BLOCK_MOVEMENT:
                return 0.0f; // Immediate blocking
            case PUSH_AWAY:
                return 0.1f; // Quick push
            default:
                return 0.5f; // Default moderate delay
        }
    }
    
    /**
     * Check if this action requires additional parameters
     */
    public boolean requiresParameters() {
        switch (this) {
            case TELEPORT_TO_LOCATION:
            case APPLY_STATUS_EFFECT:
            case CUSTOM_ACTION:
            case DAMAGE_OVER_TIME:
            case APPLY_DAMAGE:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Get the severity level of this action
     */
    public ActionSeverity getSeverity() {
        switch (this) {
            case WARNING_ONLY:
                return ActionSeverity.NONE;
            case SLOW_MOVEMENT:
            case FORCE_CROUCH:
                return ActionSeverity.LOW;
            case BLOCK_MOVEMENT:
            case PUSH_AWAY:
            case DISABLE_ABILITIES:
                return ActionSeverity.MEDIUM;
            case APPLY_DAMAGE:
            case DAMAGE_OVER_TIME:
            case TELEPORT_BACK:
            case TELEPORT_TO_LOCATION:
                return ActionSeverity.HIGH;
            case KILL_PLAYER:
            case RESPAWN_PLAYER:
                return ActionSeverity.CRITICAL;
            default:
                return ActionSeverity.MEDIUM;
        }
    }
    
    /**
     * Check if this action should show a confirmation dialog in the editor
     */
    public boolean requiresConfirmation() {
        return canCauseDeath || this == RESPAWN_PLAYER;
    }
    
    /**
     * Get the default warning message for this action
     */
    public String getDefaultWarningMessage() {
        switch (this) {
            case WARNING_ONLY:
                return "Notice: Special area";
            case TELEPORT_BACK:
                return "Warning: Leaving play area";
            case BLOCK_MOVEMENT:
                return "Warning: Restricted area";
            case KILL_PLAYER:
                return "Danger: Lethal zone ahead";
            case DAMAGE_OVER_TIME:
                return "Warning: Hazardous area";
            case APPLY_DAMAGE:
                return "Caution: Environmental hazard";
            case PUSH_AWAY:
                return "Warning: Force field active";
            case SLOW_MOVEMENT:
                return "Notice: Difficult terrain";
            default:
                return "Warning: Special boundary";
        }
    }
    
    /**
     * Severity levels for boundary actions
     */
    public enum ActionSeverity {
        NONE(0, "No Effect"),
        LOW(1, "Minor Effect"),
        MEDIUM(2, "Moderate Effect"),
        HIGH(3, "Major Effect"),
        CRITICAL(4, "Critical Effect");
        
        private final int level;
        private final String description;
        
        ActionSeverity(int level, String description) {
            this.level = level;
            this.description = description;
        }
        
        public int getLevel() { return level; }
        public String getDescription() { return description; }
        
        public boolean isMoreSevereThan(ActionSeverity other) {
            return this.level > other.level;
        }
    }
}