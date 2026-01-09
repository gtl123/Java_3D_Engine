package fps.map;

import engine.logging.LogManager;
import engine.physics.AABB;
import fps.map.data.MapDefinition;
import fps.map.data.MapMetadata;
import fps.map.loading.MapLoader;
import fps.map.loading.AssetStreamer;
import fps.map.validation.MapValidator;
import fps.map.compilation.MapCompiler;
import fps.map.streaming.MapStreamingManager;
import fps.map.network.MapNetworkSync;
import fps.core.GameState;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * Central map management system for the AAA competitive FPS shooter.
 * Handles map loading, streaming, validation, and integration with all game systems.
 */
public class MapManager {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Core components
    private final Map<String, MapDefinition> availableMaps;
    private final MapLoader mapLoader;
    private final AssetStreamer assetStreamer;
    private final MapValidator mapValidator;
    private final MapCompiler mapCompiler;
    private final MapStreamingManager streamingManager;
    private final MapNetworkSync networkSync;
    
    // Current state
    private MapDefinition currentMap;
    private String currentMapId;
    private boolean initialized = false;
    private MapLoadingState loadingState;
    
    public enum MapLoadingState {
        IDLE, LOADING, LOADED, UNLOADING, ERROR
    }
    
    public MapManager() {
        this.availableMaps = new HashMap<>();
        this.mapLoader = new MapLoader();
        this.assetStreamer = new AssetStreamer();
        this.mapValidator = new MapValidator();
        this.mapCompiler = new MapCompiler();
        this.streamingManager = new MapStreamingManager();
        this.networkSync = new MapNetworkSync();
        this.loadingState = MapLoadingState.IDLE;
        
        logManager.info("MapManager", "Map Manager created");
    }
    
    /**
     * Initialize the map management system
     */
    public void initialize() throws Exception {
        logManager.info("MapManager", "Initializing Map Manager");
        
        try {
            // Initialize all subsystems
            mapLoader.initialize();
            assetStreamer.initialize();
            mapValidator.initialize();
            mapCompiler.initialize();
            streamingManager.initialize();
            networkSync.initialize();
            
            // Load available maps metadata
            loadAvailableMaps();
            
            initialized = true;
            logManager.info("MapManager", "Map Manager initialization complete");
            
        } catch (Exception e) {
            logManager.error("MapManager", "Failed to initialize Map Manager", e);
            throw e;
        }
    }
    
    /**
     * Load a map asynchronously
     */
    public CompletableFuture<Boolean> loadMapAsync(String mapId) {
        if (!initialized) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadMap(mapId);
            } catch (Exception e) {
                logManager.error("MapManager", "Failed to load map asynchronously", "mapId", mapId, e);
                return false;
            }
        });
    }
    
    /**
     * Load a map synchronously
     */
    public boolean loadMap(String mapId) {
        if (!initialized || mapId == null || mapId.isEmpty()) {
            return false;
        }
        
        logManager.info("MapManager", "Loading map", "mapId", mapId);
        loadingState = MapLoadingState.LOADING;
        
        try {
            // Validate map exists
            if (!availableMaps.containsKey(mapId)) {
                logManager.error("MapManager", "Map not found", "mapId", mapId);
                loadingState = MapLoadingState.ERROR;
                return false;
            }
            
            // Unload current map if any
            if (currentMap != null) {
                unloadCurrentMap();
            }
            
            // Load map definition
            MapDefinition mapDef = availableMaps.get(mapId);
            
            // Validate map integrity
            if (!mapValidator.validateMap(mapDef)) {
                logManager.error("MapManager", "Map validation failed", "mapId", mapId);
                loadingState = MapLoadingState.ERROR;
                return false;
            }
            
            // Load map assets
            if (!mapLoader.loadMapAssets(mapDef)) {
                logManager.error("MapManager", "Failed to load map assets", "mapId", mapId);
                loadingState = MapLoadingState.ERROR;
                return false;
            }
            
            // Initialize streaming regions
            streamingManager.initializeForMap(mapDef);
            
            // Setup network synchronization
            networkSync.setupMapSync(mapDef);
            
            // Set as current map
            currentMap = mapDef;
            currentMapId = mapId;
            loadingState = MapLoadingState.LOADED;
            
            logManager.info("MapManager", "Map loaded successfully", "mapId", mapId);
            return true;
            
        } catch (Exception e) {
            logManager.error("MapManager", "Exception during map loading", "mapId", mapId, e);
            loadingState = MapLoadingState.ERROR;
            return false;
        }
    }
    
    /**
     * Unload the current map
     */
    public void unloadCurrentMap() {
        if (currentMap == null) {
            return;
        }
        
        logManager.info("MapManager", "Unloading current map", "mapId", currentMapId);
        loadingState = MapLoadingState.UNLOADING;
        
        try {
            // Stop streaming
            streamingManager.stopStreaming();
            
            // Cleanup network sync
            networkSync.cleanupMapSync();
            
            // Unload assets
            mapLoader.unloadMapAssets(currentMap);
            
            // Clear current map
            currentMap = null;
            currentMapId = null;
            loadingState = MapLoadingState.IDLE;
            
            logManager.info("MapManager", "Map unloaded successfully");
            
        } catch (Exception e) {
            logManager.error("MapManager", "Error during map unloading", e);
            loadingState = MapLoadingState.ERROR;
        }
    }
    
    /**
     * Preload map assets for faster loading
     */
    public CompletableFuture<Boolean> preloadMapAssets(String mapId) {
        if (!initialized || !availableMaps.containsKey(mapId)) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                MapDefinition mapDef = availableMaps.get(mapId);
                return assetStreamer.preloadAssets(mapDef);
            } catch (Exception e) {
                logManager.error("MapManager", "Failed to preload map assets", "mapId", mapId, e);
                return false;
            }
        });
    }
    
    /**
     * Update map systems (called each frame)
     */
    public void update(float deltaTime) {
        if (!initialized) {
            return;
        }
        
        try {
            // Update streaming system
            if (currentMap != null) {
                streamingManager.update(deltaTime);
            }
            
            // Update network synchronization
            networkSync.update(deltaTime);
            
        } catch (Exception e) {
            logManager.error("MapManager", "Error during map update", e);
        }
    }
    
    /**
     * Get list of available maps
     */
    public List<MapDefinition> getAvailableMaps() {
        return new ArrayList<>(availableMaps.values());
    }
    
    /**
     * Get list of available maps for a specific game mode
     */
    public List<MapDefinition> getAvailableMapsForGameMode(String gameMode) {
        return availableMaps.values().stream()
            .filter(map -> map.getMetadata().getSupportedGameModes().contains(gameMode))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Validate a map for competitive play
     */
    public boolean validateMapForCompetitive(String mapId) {
        if (!availableMaps.containsKey(mapId)) {
            return false;
        }
        
        MapDefinition mapDef = availableMaps.get(mapId);
        return mapValidator.validateForCompetitive(mapDef);
    }
    
    /**
     * Get current map definition
     */
    public MapDefinition getCurrentMap() {
        return currentMap;
    }
    
    /**
     * Get current map ID
     */
    public String getCurrentMapId() {
        return currentMapId;
    }
    
    /**
     * Get current loading state
     */
    public MapLoadingState getLoadingState() {
        return loadingState;
    }
    
    /**
     * Check if a map is currently loaded
     */
    public boolean isMapLoaded() {
        return currentMap != null && loadingState == MapLoadingState.LOADED;
    }
    
    /**
     * Get map loading progress (0.0 to 1.0)
     */
    public float getLoadingProgress() {
        return mapLoader.getLoadingProgress();
    }
    
    /**
     * Hot-swap map for development/testing
     */
    public boolean hotSwapMap(String mapId) {
        if (!initialized) {
            return false;
        }
        
        logManager.info("MapManager", "Hot-swapping map", "mapId", mapId);
        
        try {
            // Compile map if needed
            MapDefinition mapDef = availableMaps.get(mapId);
            if (mapDef != null && mapCompiler.needsRecompilation(mapDef)) {
                mapCompiler.compileMap(mapDef);
            }
            
            // Load the map
            return loadMap(mapId);
            
        } catch (Exception e) {
            logManager.error("MapManager", "Failed to hot-swap map", "mapId", mapId, e);
            return false;
        }
    }
    
    /**
     * Cleanup map manager
     */
    public void cleanup() {
        logManager.info("MapManager", "Cleaning up Map Manager");
        
        try {
            // Unload current map
            if (currentMap != null) {
                unloadCurrentMap();
            }
            
            // Cleanup subsystems
            if (networkSync != null) {
                networkSync.cleanup();
            }
            if (streamingManager != null) {
                streamingManager.cleanup();
            }
            if (mapCompiler != null) {
                mapCompiler.cleanup();
            }
            if (mapValidator != null) {
                mapValidator.cleanup();
            }
            if (assetStreamer != null) {
                assetStreamer.cleanup();
            }
            if (mapLoader != null) {
                mapLoader.cleanup();
            }
            
            // Clear data
            availableMaps.clear();
            initialized = false;
            
            logManager.info("MapManager", "Map Manager cleanup complete");
            
        } catch (Exception e) {
            logManager.error("MapManager", "Error during cleanup", e);
        }
    }
    
    /**
     * Load available maps from the maps directory
     */
    private void loadAvailableMaps() {
        logManager.info("MapManager", "Loading available maps");
        
        try {
            List<MapDefinition> maps = mapLoader.discoverMaps();
            
            for (MapDefinition map : maps) {
                availableMaps.put(map.getMapId(), map);
                logManager.info("MapManager", "Discovered map", 
                              "mapId", map.getMapId(), 
                              "name", map.getMetadata().getDisplayName());
            }
            
            logManager.info("MapManager", "Loaded available maps", "count", availableMaps.size());
            
        } catch (Exception e) {
            logManager.error("MapManager", "Failed to load available maps", e);
        }
    }
    
    // Getters for subsystems
    public MapLoader getMapLoader() { return mapLoader; }
    public AssetStreamer getAssetStreamer() { return assetStreamer; }
    public MapValidator getMapValidator() { return mapValidator; }
    public MapCompiler getMapCompiler() { return mapCompiler; }
    public MapStreamingManager getStreamingManager() { return streamingManager; }
    public MapNetworkSync getNetworkSync() { return networkSync; }
}