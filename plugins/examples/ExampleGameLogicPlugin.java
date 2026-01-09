package engine.plugins.examples;

import engine.io.Input;
import engine.io.Window;
import engine.plugins.*;
import engine.plugins.types.GameLogicPlugin;

/**
 * Example game logic plugin demonstrating the plugin system capabilities.
 * This plugin adds simple game mechanics and demonstrates plugin lifecycle.
 */
public class ExampleGameLogicPlugin implements GameLogicPlugin {
    
    private PluginMetadata metadata;
    private PluginState state = PluginState.LOADED;
    private PluginContext context;
    
    private boolean gameStarted = false;
    private float gameTime = 0.0f;
    private int updateCount = 0;
    
    public ExampleGameLogicPlugin() {
        // Build plugin metadata
        this.metadata = PluginMetadata.builder()
                .id("example-game-logic")
                .name("Example Game Logic Plugin")
                .version("1.0.0")
                .description("Demonstrates game logic plugin capabilities")
                .author("Engine Team")
                .license("MIT")
                .engineVersion("1.0.0")
                .addPermission("engine.game.update")
                .addPermission("engine.input.handle")
                .setProperty("category", "example")
                .setProperty("experimental", true)
                .build();
    }
    
    @Override
    public PluginMetadata getMetadata() {
        return metadata;
    }
    
    @Override
    public void initialize(PluginContext context) throws PluginException {
        this.context = context;
        this.state = PluginState.INITIALIZED;
        
        context.getLogManager().info("ExampleGameLogicPlugin", "Plugin initialized",
                                   "pluginId", metadata.getId());
        
        // Register for game events
        context.getEventBus().subscribe(GameEvent.class, this::onGameEvent);
        
        // Set some plugin configuration
        context.setPluginConfig("enabled", true);
        context.setPluginConfig("updateInterval", 1.0f);
    }
    
    @Override
    public void start() throws PluginException {
        this.state = PluginState.STARTED;
        
        context.getLogManager().info("ExampleGameLogicPlugin", "Plugin started",
                                   "pluginId", metadata.getId());
        
        // Publish plugin started event
        context.getEventBus().publish(new PluginEvent("plugin.started", metadata.getId()));
    }
    
    @Override
    public void stop() throws PluginException {
        this.state = PluginState.STOPPED;
        
        context.getLogManager().info("ExampleGameLogicPlugin", "Plugin stopped",
                                   "pluginId", metadata.getId(),
                                   "totalUpdates", updateCount,
                                   "totalGameTime", gameTime);
    }
    
    @Override
    public void cleanup() throws PluginException {
        this.state = PluginState.UNLOADED;
        
        context.getLogManager().info("ExampleGameLogicPlugin", "Plugin cleaned up",
                                   "pluginId", metadata.getId());
    }
    
    @Override
    public PluginState getState() {
        return state;
    }
    
    @Override
    public boolean isCompatible(String engineVersion) {
        // Simple version compatibility check
        return "1.0.0".equals(engineVersion);
    }
    
    // GameLogicPlugin implementation
    
    @Override
    public void initializeGameLogic() throws PluginException {
        context.getLogManager().info("ExampleGameLogicPlugin", "Game logic initialized");
        
        // Initialize game-specific systems
        gameStarted = false;
        gameTime = 0.0f;
        updateCount = 0;
    }
    
    @Override
    public void update(float deltaTime) {
        if (state != PluginState.STARTED) {
            return;
        }
        
        gameTime += deltaTime;
        updateCount++;
        
        // Log periodic updates
        if (updateCount % 1000 == 0) {
            context.getLogManager().debug("ExampleGameLogicPlugin", "Periodic update",
                                        "updates", updateCount,
                                        "gameTime", String.format("%.2f", gameTime));
        }
        
        // Example game logic: check for milestone events
        if (gameTime > 60.0f && updateCount % 100 == 0) {
            context.getEventBus().publish(new GameEvent("milestone.reached", 
                    "One minute of gameplay completed"));
        }
    }
    
    @Override
    public void handleInput(Window window, Input input) {
        if (state != PluginState.STARTED || !handlesInput()) {
            return;
        }
        
        // Example input handling - could add custom key bindings
        // This is just a demonstration
    }
    
    @Override
    public void onGamePaused() {
        context.getLogManager().info("ExampleGameLogicPlugin", "Game paused",
                                   "gameTime", gameTime);
    }
    
    @Override
    public void onGameResumed() {
        context.getLogManager().info("ExampleGameLogicPlugin", "Game resumed",
                                   "gameTime", gameTime);
    }
    
    @Override
    public void onGameStarted() {
        gameStarted = true;
        context.getLogManager().info("ExampleGameLogicPlugin", "Game started");
        
        // Publish game started event
        context.getEventBus().publish(new GameEvent("game.started", "New game session"));
    }
    
    @Override
    public void onGameEnded() {
        gameStarted = false;
        context.getLogManager().info("ExampleGameLogicPlugin", "Game ended",
                                   "finalTime", gameTime,
                                   "totalUpdates", updateCount);
        
        // Publish game ended event with statistics
        context.getEventBus().publish(new GameEvent("game.ended", 
                String.format("Game session ended after %.2f seconds", gameTime)));
    }
    
    @Override
    public void onWorldLoaded(String worldName) {
        context.getLogManager().info("ExampleGameLogicPlugin", "World loaded",
                                   "worldName", worldName);
    }
    
    @Override
    public void onWorldUnloaded(String worldName) {
        context.getLogManager().info("ExampleGameLogicPlugin", "World unloaded",
                                   "worldName", worldName);
    }
    
    @Override
    public void onPlayerJoined(String playerId, String playerName) {
        context.getLogManager().info("ExampleGameLogicPlugin", "Player joined",
                                   "playerId", playerId,
                                   "playerName", playerName);
    }
    
    @Override
    public void onPlayerLeft(String playerId) {
        context.getLogManager().info("ExampleGameLogicPlugin", "Player left",
                                   "playerId", playerId);
    }
    
    @Override
    public int getUpdatePriority() {
        return 100; // Medium priority
    }
    
    @Override
    public boolean handlesInput() {
        return context.getPluginConfig("handleInput", false);
    }
    
    @Override
    public boolean updatesWhenPaused() {
        return false; // Don't update when game is paused
    }
    
    @Override
    public String[] getProvidedSystems() {
        return new String[]{"example.timer", "example.events"};
    }
    
    @Override
    public Object getGameSystem(String systemName) {
        switch (systemName) {
            case "example.timer":
                return new GameTimer(gameTime);
            case "example.events":
                return new EventTracker(updateCount);
            default:
                return null;
        }
    }
    
    @Override
    public String[] getProvidedCommands() {
        return new String[]{"example.status", "example.reset"};
    }
    
    @Override
    public Object executeCommand(String command, String[] args) {
        switch (command) {
            case "example.status":
                return String.format("Plugin Status: gameTime=%.2f, updates=%d, started=%s",
                        gameTime, updateCount, gameStarted);
            case "example.reset":
                gameTime = 0.0f;
                updateCount = 0;
                return "Plugin state reset";
            default:
                return "Unknown command: " + command;
        }
    }
    
    @Override
    public String[] getProvidedEvents() {
        return new String[]{"example.milestone", "example.status"};
    }
    
    @Override
    public void cleanupGameLogic() {
        context.getLogManager().info("ExampleGameLogicPlugin", "Game logic cleaned up");
    }
    
    // Event handlers
    
    private void onGameEvent(GameEvent event) {
        context.getLogManager().debug("ExampleGameLogicPlugin", "Received game event",
                                    "eventType", event.getType(),
                                    "message", event.getMessage());
    }
    
    // Helper classes for demonstration
    
    public static class GameTimer {
        private final float currentTime;
        
        public GameTimer(float currentTime) {
            this.currentTime = currentTime;
        }
        
        public float getCurrentTime() {
            return currentTime;
        }
        
        public String getFormattedTime() {
            return String.format("%.2f seconds", currentTime);
        }
    }
    
    public static class EventTracker {
        private final int eventCount;
        
        public EventTracker(int eventCount) {
            this.eventCount = eventCount;
        }
        
        public int getEventCount() {
            return eventCount;
        }
    }
    
    // Event classes for demonstration
    
    public static class GameEvent {
        private final String type;
        private final String message;
        
        public GameEvent(String type, String message) {
            this.type = type;
            this.message = message;
        }
        
        public String getType() { return type; }
        public String getMessage() { return message; }
    }
    
    public static class PluginEvent {
        private final String type;
        private final String pluginId;
        
        public PluginEvent(String type, String pluginId) {
            this.type = type;
            this.pluginId = pluginId;
        }
        
        public String getType() { return type; }
        public String getPluginId() { return pluginId; }
    }
}