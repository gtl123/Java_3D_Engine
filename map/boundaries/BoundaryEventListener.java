package fps.map.boundaries;

import fps.game.Player;

/**
 * Interface for listening to boundary system events.
 * Allows other systems to react to boundary violations and actions.
 */
public interface BoundaryEventListener {
    
    /**
     * Called when a new boundary is added to the system
     */
    default void onBoundaryAdded(MapBoundary boundary) {
        // Default implementation does nothing
    }
    
    /**
     * Called when a boundary is removed from the system
     */
    default void onBoundaryRemoved(MapBoundary boundary) {
        // Default implementation does nothing
    }
    
    /**
     * Called when a player starts violating a boundary
     */
    default void onViolationStarted(BoundaryViolation violation) {
        // Default implementation does nothing
    }
    
    /**
     * Called when a boundary violation ends (player leaves the boundary)
     */
    default void onViolationEnded(BoundaryViolation violation) {
        // Default implementation does nothing
    }
    
    /**
     * Called when a boundary action is executed on a player
     */
    default void onActionExecuted(Player player, MapBoundary boundary, BoundaryAction action) {
        // Default implementation does nothing
    }
    
    /**
     * Called when a boundary warning is shown to a player
     */
    default void onWarningShown(Player player, MapBoundary boundary, String message) {
        // Default implementation does nothing
    }
    
    /**
     * Called when the severity of a boundary violation changes
     */
    default void onSeverityChanged(BoundaryViolation violation, BoundarySeverity oldSeverity, BoundarySeverity newSeverity) {
        // Default implementation does nothing
    }
    
    /**
     * Called when a boundary is enabled or disabled
     */
    default void onBoundaryStateChanged(MapBoundary boundary, boolean enabled) {
        // Default implementation does nothing
    }
}