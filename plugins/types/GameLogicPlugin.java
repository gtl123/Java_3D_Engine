package engine.plugins.types;

import engine.io.Input;
import engine.io.Window;
import engine.plugins.Plugin;
import engine.plugins.PluginException;

/**
 * Interface for plugins that extend game logic and mechanics.
 * Allows plugins to add custom game behaviors, systems, and interactions.
 */
public interface GameLogicPlugin extends Plugin {
    
    /**
     * Initialize game logic systems.
     * Called after the plugin is initialized.
     * @throws PluginException if initialization fails
     */
    void initializeGameLogic() throws PluginException;
    
    /**
     * Update game logic.
     * Called every game update tick.
     * @param deltaTime Time since last update in seconds
     */
    void update(float deltaTime);
    
    /**
     * Handle input events.
     * Called every frame to process input.
     * @param window Game window
     * @param input Input system
     */
    void handleInput(Window window, Input input);
    
    /**
     * Called when the game is paused.
     */
    void onGamePaused();
    
    /**
     * Called when the game is resumed.
     */
    void onGameResumed();
    
    /**
     * Called when a new game is started.
     */
    void onGameStarted();
    
    /**
     * Called when the game is ended.
     */
    void onGameEnded();
    
    /**
     * Called when the game world is loaded.
     * @param worldName Name of the loaded world
     */
    void onWorldLoaded(String worldName);
    
    /**
     * Called when the game world is unloaded.
     * @param worldName Name of the unloaded world
     */
    void onWorldUnloaded(String worldName);
    
    /**
     * Called when a player joins the game (multiplayer).
     * @param playerId Player identifier
     * @param playerName Player name
     */
    void onPlayerJoined(String playerId, String playerName);
    
    /**
     * Called when a player leaves the game (multiplayer).
     * @param playerId Player identifier
     */
    void onPlayerLeft(String playerId);
    
    /**
     * Get the update priority for this plugin.
     * Higher priority plugins update first.
     * @return Update priority
     */
    int getUpdatePriority();
    
    /**
     * Check if this plugin should receive input events.
     * @return true if plugin handles input
     */
    boolean handlesInput();
    
    /**
     * Check if this plugin should update when the game is paused.
     * @return true if plugin updates when paused
     */
    boolean updatesWhenPaused();
    
    /**
     * Get the game systems provided by this plugin.
     * @return Array of system names
     */
    String[] getProvidedSystems();
    
    /**
     * Get a game system by name.
     * @param systemName Name of the system
     * @return System instance or null if not found
     */
    Object getGameSystem(String systemName);
    
    /**
     * Register custom commands with the game.
     * @return Array of command names
     */
    String[] getProvidedCommands();
    
    /**
     * Execute a custom command.
     * @param command Command name
     * @param args Command arguments
     * @return Command result or null
     */
    Object executeCommand(String command, String[] args);
    
    /**
     * Get custom game events that this plugin can generate.
     * @return Array of event type names
     */
    String[] getProvidedEvents();
    
    /**
     * Cleanup game logic resources.
     * Called when the plugin is being unloaded.
     */
    void cleanupGameLogic();
}