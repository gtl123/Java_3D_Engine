package fps.core;

import engine.io.Window;
import engine.logging.LogManager;
import fps.match.MatchManager;
import fps.match.GameMode;
import fps.player.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for FPS game state and coordination between systems.
 * Handles game flow, match management, and system coordination.
 */
public class FPSGameManager {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Game state management
    private GameState currentState = GameState.MENU;
    private GameState previousState = GameState.MENU;
    
    // Match management
    private MatchManager matchManager;
    private GameMode currentGameMode;
    
    // Player management
    private final Map<Integer, Player> players = new ConcurrentHashMap<>();
    private int localPlayerId = -1;
    
    // Game settings
    private GameSettings gameSettings;
    
    // Initialization flag
    private boolean initialized = false;
    
    public FPSGameManager() {
        logManager.info("FPSGameManager", "FPS Game Manager created");
    }
    
    /**
     * Initialize the FPS Game Manager
     */
    public void initialize() throws Exception {
        logManager.info("FPSGameManager", "Initializing FPS Game Manager");
        
        try {
            // Initialize game settings
            gameSettings = new GameSettings();
            gameSettings.loadDefaults();
            
            // Initialize match manager
            matchManager = new MatchManager();
            matchManager.initialize();
            
            initialized = true;
            
            logManager.info("FPSGameManager", "FPS Game Manager initialization complete");
            
        } catch (Exception e) {
            logManager.error("FPSGameManager", "Failed to initialize FPS Game Manager", e);
            throw e;
        }
    }
    
    /**
     * Update menu state
     */
    public void updateMenuState(float deltaTime) {
        // Handle menu updates
        // This could include menu animations, background effects, etc.
    }
    
    /**
     * Update gameplay state
     */
    public void updateGameplayState(float deltaTime) {
        if (matchManager != null) {
            matchManager.update(deltaTime);
        }
        
        // Update all players
        for (Player player : players.values()) {
            player.update(deltaTime);
        }
    }
    
    /**
     * Update paused state
     */
    public void updatePausedState(float deltaTime) {
        // Handle paused state updates
        // Game logic is paused but UI might still animate
    }
    
    /**
     * Render menu
     */
    public void renderMenu(Window window) {
        // Render menu UI elements
        // This would integrate with the UI system when implemented
    }
    
    /**
     * Render gameplay
     */
    public void renderGameplay(Window window) {
        // Render game world, players, effects, etc.
        if (matchManager != null) {
            matchManager.render(window);
        }
        
        // Render all players
        for (Player player : players.values()) {
            player.render(window);
        }
    }
    
    /**
     * Render paused state
     */
    public void renderPaused(Window window) {
        // Render paused overlay
        renderGameplay(window); // Still show the game behind pause menu
        // Render pause menu UI
    }
    
    /**
     * Handle game state changes
     */
    public void onGameStateChanged(GameState from, GameState to) {
        previousState = from;
        currentState = to;
        
        logManager.info("FPSGameManager", "Handling game state change",
                       "from", from,
                       "to", to);
        
        switch (to) {
            case MENU:
                enterMenuState();
                break;
            case LOADING:
                enterLoadingState();
                break;
            case IN_GAME:
                enterGameplayState();
                break;
            case PAUSED:
                enterPausedState();
                break;
            case SPECTATING:
                enterSpectatingState();
                break;
            case END_GAME:
                enterEndGameState();
                break;
            case DISCONNECTED:
                enterDisconnectedState();
                break;
        }
    }
    
    private void enterMenuState() {
        // Clean up any active match
        if (matchManager != null) {
            matchManager.endMatch();
        }
        
        // Clear players
        players.clear();
        localPlayerId = -1;
    }
    
    private void enterLoadingState() {
        // Start loading assets, connecting to server, etc.
    }
    
    private void enterGameplayState() {
        // Start or resume gameplay
        if (matchManager != null && currentGameMode != null) {
            matchManager.startMatch(currentGameMode);
        }
    }
    
    private void enterPausedState() {
        // Pause game systems
        if (matchManager != null) {
            matchManager.pauseMatch();
        }
    }
    
    private void enterSpectatingState() {
        // Enter spectator mode
    }
    
    private void enterEndGameState() {
        // Show match results, statistics, etc.
        if (matchManager != null) {
            matchManager.endMatch();
        }
    }
    
    private void enterDisconnectedState() {
        // Handle disconnection
        enterMenuState(); // Return to menu
    }
    
    /**
     * Start a new match with specified game mode
     */
    public void startMatch(GameMode gameMode) {
        this.currentGameMode = gameMode;
        
        if (matchManager != null) {
            matchManager.startMatch(gameMode);
        }
        
        logManager.info("FPSGameManager", "Starting new match",
                       "gameMode", gameMode);
    }
    
    /**
     * End the current match
     */
    public void endMatch() {
        if (matchManager != null) {
            matchManager.endMatch();
        }
        
        currentGameMode = null;
        
        logManager.info("FPSGameManager", "Match ended");
    }
    
    /**
     * Add a player to the game
     */
    public void addPlayer(Player player) {
        players.put(player.getPlayerId(), player);
        
        logManager.info("FPSGameManager", "Player added",
                       "playerId", player.getPlayerId(),
                       "totalPlayers", players.size());
    }
    
    /**
     * Remove a player from the game
     */
    public void removePlayer(int playerId) {
        Player removed = players.remove(playerId);
        
        if (removed != null) {
            logManager.info("FPSGameManager", "Player removed",
                           "playerId", playerId,
                           "totalPlayers", players.size());
        }
    }
    
    /**
     * Get a player by ID
     */
    public Player getPlayer(int playerId) {
        return players.get(playerId);
    }
    
    /**
     * Set the local player ID
     */
    public void setLocalPlayerId(int playerId) {
        this.localPlayerId = playerId;
        
        logManager.info("FPSGameManager", "Local player ID set",
                       "playerId", playerId);
    }
    
    /**
     * Get the local player
     */
    public Player getLocalPlayer() {
        return localPlayerId >= 0 ? players.get(localPlayerId) : null;
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        logManager.info("FPSGameManager", "Cleaning up FPS Game Manager");
        
        try {
            if (matchManager != null) {
                matchManager.cleanup();
            }
            
            players.clear();
            initialized = false;
            
            logManager.info("FPSGameManager", "FPS Game Manager cleanup complete");
            
        } catch (Exception e) {
            logManager.error("FPSGameManager", "Error during cleanup", e);
        }
    }
    
    // Getters
    public GameState getCurrentState() { return currentState; }
    public GameState getPreviousState() { return previousState; }
    public MatchManager getMatchManager() { return matchManager; }
    public GameMode getCurrentGameMode() { return currentGameMode; }
    public GameSettings getGameSettings() { return gameSettings; }
    public Map<Integer, Player> getPlayers() { return new ConcurrentHashMap<>(players); }
    public int getLocalPlayerId() { return localPlayerId; }
    public boolean isInitialized() { return initialized; }
}