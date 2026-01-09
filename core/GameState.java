package fps.core;

/**
 * Enumeration of possible game states in the FPS game.
 * Used to control game flow and system behavior.
 */
public enum GameState {
    /**
     * Main menu state - showing menus, settings, etc.
     */
    MENU,
    
    /**
     * Loading state - loading maps, assets, connecting to server
     */
    LOADING,
    
    /**
     * In-game state - active gameplay
     */
    IN_GAME,
    
    /**
     * Paused state - game is paused but still in session
     */
    PAUSED,
    
    /**
     * Spectating state - watching other players
     */
    SPECTATING,
    
    /**
     * End game state - showing match results
     */
    END_GAME,
    
    /**
     * Disconnected state - lost connection to server
     */
    DISCONNECTED
}