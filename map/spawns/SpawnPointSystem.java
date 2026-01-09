package fps.map.spawns;

import org.joml.Vector3f;
import engine.logging.LogManager;
import fps.map.geometry.MapGeometry;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Manages spawn points for different teams and game modes.
 * Handles spawn selection, validation, and balancing for competitive gameplay.
 */
public class SpawnPointSystem {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Spawn point collections
    private final Map<String, List<SpawnPoint>> teamSpawnPoints;
    private final List<SpawnPoint> neutralSpawnPoints;
    private final Map<String, List<SpawnPoint>> gameModeSpawnPoints;
    
    // Spawn selection and balancing
    private final SpawnSelector spawnSelector;
    private final SpawnValidator spawnValidator;
    private final SpawnBalancer spawnBalancer;
    
    // Configuration
    private final SpawnConfiguration configuration;
    private final Random random;
    
    // Runtime state
    private final Map<Integer, SpawnHistory> playerSpawnHistory;
    private final Map<String, SpawnStatistics> teamStatistics;
    
    public SpawnPointSystem(Builder builder) {
        this.teamSpawnPoints = new HashMap<>(builder.teamSpawnPoints);
        this.neutralSpawnPoints = new ArrayList<>(builder.neutralSpawnPoints);
        this.gameModeSpawnPoints = new HashMap<>(builder.gameModeSpawnPoints);
        this.spawnSelector = builder.spawnSelector != null ? builder.spawnSelector : new DefaultSpawnSelector();
        this.spawnValidator = builder.spawnValidator != null ? builder.spawnValidator : new DefaultSpawnValidator();
        this.spawnBalancer = builder.spawnBalancer != null ? builder.spawnBalancer : new DefaultSpawnBalancer();
        this.configuration = builder.configuration != null ? builder.configuration : SpawnConfiguration.createDefault();
        this.random = new Random();
        this.playerSpawnHistory = new HashMap<>();
        this.teamStatistics = new HashMap<>();
        
        logManager.info("SpawnPointSystem", "Spawn point system created", 
                       "teams", teamSpawnPoints.size(), 
                       "neutral", neutralSpawnPoints.size());
    }
    
    /**
     * Select a spawn point for a player
     */
    public SpawnResult selectSpawnPoint(int playerId, String teamId, String gameMode) {
        return selectSpawnPoint(playerId, teamId, gameMode, null);
    }
    
    /**
     * Select a spawn point for a player with preferences
     */
    public SpawnResult selectSpawnPoint(int playerId, String teamId, String gameMode, SpawnPreferences preferences) {
        try {
            // Get available spawn points for the team/game mode
            List<SpawnPoint> availableSpawns = getAvailableSpawnPoints(teamId, gameMode);
            
            if (availableSpawns.isEmpty()) {
                logManager.warn("SpawnPointSystem", "No available spawn points", 
                              "playerId", playerId, "teamId", teamId, "gameMode", gameMode);
                return SpawnResult.failure("No available spawn points");
            }
            
            // Filter valid spawn points
            List<SpawnPoint> validSpawns = spawnValidator.filterValidSpawns(availableSpawns, playerId, teamId);
            
            if (validSpawns.isEmpty()) {
                logManager.warn("SpawnPointSystem", "No valid spawn points after filtering", 
                              "playerId", playerId, "teamId", teamId);
                return SpawnResult.failure("No valid spawn points");
            }
            
            // Select best spawn point
            SpawnPoint selectedSpawn = spawnSelector.selectSpawn(validSpawns, playerId, teamId, preferences);
            
            if (selectedSpawn == null) {
                return SpawnResult.failure("Spawn selection failed");
            }
            
            // Update spawn history and statistics
            updateSpawnHistory(playerId, selectedSpawn);
            updateTeamStatistics(teamId, selectedSpawn);
            
            logManager.debug("SpawnPointSystem", "Selected spawn point", 
                           "playerId", playerId, "spawnId", selectedSpawn.getSpawnId(), 
                           "position", selectedSpawn.getPosition());
            
            return SpawnResult.success(selectedSpawn);
            
        } catch (Exception e) {
            logManager.error("SpawnPointSystem", "Error selecting spawn point", 
                           "playerId", playerId, "teamId", teamId, e);
            return SpawnResult.failure("Internal error: " + e.getMessage());
        }
    }
    
    /**
     * Get all spawn points for a team
     */
    public List<SpawnPoint> getTeamSpawnPoints(String teamId) {
        return new ArrayList<>(teamSpawnPoints.getOrDefault(teamId, new ArrayList<>()));
    }
    
    /**
     * Get spawn points for a specific game mode
     */
    public List<SpawnPoint> getGameModeSpawnPoints(String gameMode) {
        return new ArrayList<>(gameModeSpawnPoints.getOrDefault(gameMode, new ArrayList<>()));
    }
    
    /**
     * Add a spawn point to the system
     */
    public void addSpawnPoint(SpawnPoint spawnPoint) {
        if (spawnPoint.getTeamId() != null) {
            teamSpawnPoints.computeIfAbsent(spawnPoint.getTeamId(), k -> new ArrayList<>()).add(spawnPoint);
        } else {
            neutralSpawnPoints.add(spawnPoint);
        }
        
        // Add to game mode specific spawns if specified
        for (String gameMode : spawnPoint.getSupportedGameModes()) {
            gameModeSpawnPoints.computeIfAbsent(gameMode, k -> new ArrayList<>()).add(spawnPoint);
        }
        
        logManager.debug("SpawnPointSystem", "Added spawn point", 
                       "spawnId", spawnPoint.getSpawnId(), 
                       "teamId", spawnPoint.getTeamId());
    }
    
    /**
     * Remove a spawn point from the system
     */
    public void removeSpawnPoint(String spawnId) {
        // Remove from team spawns
        for (List<SpawnPoint> spawns : teamSpawnPoints.values()) {
            spawns.removeIf(spawn -> spawn.getSpawnId().equals(spawnId));
        }
        
        // Remove from neutral spawns
        neutralSpawnPoints.removeIf(spawn -> spawn.getSpawnId().equals(spawnId));
        
        // Remove from game mode spawns
        for (List<SpawnPoint> spawns : gameModeSpawnPoints.values()) {
            spawns.removeIf(spawn -> spawn.getSpawnId().equals(spawnId));
        }
        
        logManager.debug("SpawnPointSystem", "Removed spawn point", "spawnId", spawnId);
    }
    
    /**
     * Validate all spawn points against map geometry
     */
    public SpawnValidationReport validateSpawnPoints(MapGeometry mapGeometry) {
        return spawnValidator.validateAllSpawns(getAllSpawnPoints(), mapGeometry);
    }
    
    /**
     * Balance spawn points for competitive fairness
     */
    public SpawnBalanceReport balanceSpawnPoints() {
        return spawnBalancer.analyzeBalance(teamSpawnPoints, configuration);
    }
    
    /**
     * Get spawn statistics for a team
     */
    public SpawnStatistics getTeamStatistics(String teamId) {
        return teamStatistics.get(teamId);
    }
    
    /**
     * Get spawn history for a player
     */
    public SpawnHistory getPlayerSpawnHistory(int playerId) {
        return playerSpawnHistory.get(playerId);
    }
    
    /**
     * Clear spawn history (useful for new rounds)
     */
    public void clearSpawnHistory() {
        playerSpawnHistory.clear();
        teamStatistics.clear();
        logManager.debug("SpawnPointSystem", "Cleared spawn history");
    }
    
    /**
     * Get configuration
     */
    public SpawnConfiguration getConfiguration() {
        return configuration;
    }
    
    /**
     * Update spawn point enabled/disabled state
     */
    public void setSpawnPointEnabled(String spawnId, boolean enabled) {
        SpawnPoint spawn = findSpawnPoint(spawnId);
        if (spawn != null) {
            spawn.setEnabled(enabled);
            logManager.debug("SpawnPointSystem", "Updated spawn point state", 
                           "spawnId", spawnId, "enabled", enabled);
        }
    }
    
    /**
     * Get available spawn points for team and game mode
     */
    private List<SpawnPoint> getAvailableSpawnPoints(String teamId, String gameMode) {
        List<SpawnPoint> available = new ArrayList<>();
        
        // Add team-specific spawn points
        List<SpawnPoint> teamSpawns = teamSpawnPoints.get(teamId);
        if (teamSpawns != null) {
            available.addAll(teamSpawns.stream()
                .filter(spawn -> spawn.isEnabled() && spawn.supportsGameMode(gameMode))
                .collect(Collectors.toList()));
        }
        
        // Add neutral spawn points if allowed
        if (configuration.isAllowNeutralSpawns()) {
            available.addAll(neutralSpawnPoints.stream()
                .filter(spawn -> spawn.isEnabled() && spawn.supportsGameMode(gameMode))
                .collect(Collectors.toList()));
        }
        
        // Add game mode specific spawns
        List<SpawnPoint> gameModeSpawns = gameModeSpawnPoints.get(gameMode);
        if (gameModeSpawns != null) {
            available.addAll(gameModeSpawns.stream()
                .filter(spawn -> spawn.isEnabled() && 
                        (spawn.getTeamId() == null || spawn.getTeamId().equals(teamId)))
                .collect(Collectors.toList()));
        }
        
        return available;
    }
    
    /**
     * Get all spawn points in the system
     */
    private List<SpawnPoint> getAllSpawnPoints() {
        List<SpawnPoint> allSpawns = new ArrayList<>();
        
        for (List<SpawnPoint> spawns : teamSpawnPoints.values()) {
            allSpawns.addAll(spawns);
        }
        allSpawns.addAll(neutralSpawnPoints);
        
        return allSpawns;
    }
    
    /**
     * Find a spawn point by ID
     */
    private SpawnPoint findSpawnPoint(String spawnId) {
        return getAllSpawnPoints().stream()
            .filter(spawn -> spawn.getSpawnId().equals(spawnId))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Update spawn history for a player
     */
    private void updateSpawnHistory(int playerId, SpawnPoint spawnPoint) {
        SpawnHistory history = playerSpawnHistory.computeIfAbsent(playerId, k -> new SpawnHistory(playerId));
        history.addSpawn(spawnPoint);
    }
    
    /**
     * Update team statistics
     */
    private void updateTeamStatistics(String teamId, SpawnPoint spawnPoint) {
        SpawnStatistics stats = teamStatistics.computeIfAbsent(teamId, k -> new SpawnStatistics(teamId));
        stats.recordSpawn(spawnPoint);
    }
    
    /**
     * Builder for SpawnPointSystem
     */
    public static class Builder {
        private Map<String, List<SpawnPoint>> teamSpawnPoints = new HashMap<>();
        private List<SpawnPoint> neutralSpawnPoints = new ArrayList<>();
        private Map<String, List<SpawnPoint>> gameModeSpawnPoints = new HashMap<>();
        private SpawnSelector spawnSelector;
        private SpawnValidator spawnValidator;
        private SpawnBalancer spawnBalancer;
        private SpawnConfiguration configuration;
        
        public Builder addTeamSpawnPoint(String teamId, SpawnPoint spawnPoint) {
            teamSpawnPoints.computeIfAbsent(teamId, k -> new ArrayList<>()).add(spawnPoint);
            return this;
        }
        
        public Builder addNeutralSpawnPoint(SpawnPoint spawnPoint) {
            neutralSpawnPoints.add(spawnPoint);
            return this;
        }
        
        public Builder addGameModeSpawnPoint(String gameMode, SpawnPoint spawnPoint) {
            gameModeSpawnPoints.computeIfAbsent(gameMode, k -> new ArrayList<>()).add(spawnPoint);
            return this;
        }
        
        public Builder spawnSelector(SpawnSelector selector) {
            this.spawnSelector = selector;
            return this;
        }
        
        public Builder spawnValidator(SpawnValidator validator) {
            this.spawnValidator = validator;
            return this;
        }
        
        public Builder spawnBalancer(SpawnBalancer balancer) {
            this.spawnBalancer = balancer;
            return this;
        }
        
        public Builder configuration(SpawnConfiguration config) {
            this.configuration = config;
            return this;
        }
        
        public SpawnPointSystem build() {
            return new SpawnPointSystem(this);
        }
    }
    
    @Override
    public String toString() {
        int totalSpawns = teamSpawnPoints.values().stream().mapToInt(List::size).sum() + neutralSpawnPoints.size();
        return String.format("SpawnPointSystem{teams=%d, totalSpawns=%d}", 
                           teamSpawnPoints.size(), totalSpawns);
    }
}