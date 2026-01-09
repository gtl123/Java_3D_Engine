package fps.map.objectives;

import org.joml.Vector3f;
import engine.logging.LogManager;
import fps.map.geometry.MapGeometry;
import fps.map.spawns.SpawnPointSystem;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * Manages objectives for different game modes including bomb sites, capture points,
 * flags, and other interactive objectives that drive competitive gameplay.
 */
public class ObjectiveSystem {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Objective collections by type
    private final Map<ObjectiveType, List<Objective>> objectivesByType;
    private final Map<String, Objective> objectivesById;
    private final Map<String, List<Objective>> objectivesByGameMode;
    
    // System components
    private final ObjectiveValidator objectiveValidator;
    private final ObjectiveBalancer objectiveBalancer;
    private final ObjectiveNetworkSync networkSync;
    
    // Runtime state
    private final Map<String, ObjectiveState> objectiveStates;
    private final ObjectiveConfiguration configuration;
    private boolean initialized = false;
    
    public ObjectiveSystem(Builder builder) {
        this.objectivesByType = new HashMap<>(builder.objectivesByType);
        this.objectivesById = new HashMap<>(builder.objectivesById);
        this.objectivesByGameMode = new HashMap<>(builder.objectivesByGameMode);
        this.objectiveValidator = builder.objectiveValidator != null ? builder.objectiveValidator : new DefaultObjectiveValidator();
        this.objectiveBalancer = builder.objectiveBalancer != null ? builder.objectiveBalancer : new DefaultObjectiveBalancer();
        this.networkSync = builder.networkSync != null ? builder.networkSync : new DefaultObjectiveNetworkSync();
        this.objectiveStates = new HashMap<>();
        this.configuration = builder.configuration != null ? builder.configuration : ObjectiveConfiguration.createDefault();
        
        logManager.info("ObjectiveSystem", "Objective system created", 
                       "objectives", objectivesById.size(), 
                       "types", objectivesByType.size());
    }
    
    /**
     * Initialize the objective system
     */
    public void initialize() throws Exception {
        logManager.info("ObjectiveSystem", "Initializing objective system");
        
        try {
            // Initialize components
            objectiveValidator.initialize();
            objectiveBalancer.initialize();
            networkSync.initialize();
            
            // Initialize objective states
            initializeObjectiveStates();
            
            initialized = true;
            logManager.info("ObjectiveSystem", "Objective system initialization complete");
            
        } catch (Exception e) {
            logManager.error("ObjectiveSystem", "Failed to initialize objective system", e);
            throw e;
        }
    }
    
    /**
     * Update objective system (called each frame)
     */
    public void update(float deltaTime) {
        if (!initialized) return;
        
        try {
            // Update objective states
            for (ObjectiveState state : objectiveStates.values()) {
                state.update(deltaTime);
            }
            
            // Update network synchronization
            networkSync.update(deltaTime);
            
        } catch (Exception e) {
            logManager.error("ObjectiveSystem", "Error during objective update", e);
        }
    }
    
    /**
     * Get objectives for a specific game mode
     */
    public List<Objective> getObjectivesForGameMode(String gameMode) {
        return new ArrayList<>(objectivesByGameMode.getOrDefault(gameMode, new ArrayList<>()));
    }
    
    /**
     * Get objectives by type
     */
    public List<Objective> getObjectivesByType(ObjectiveType type) {
        return new ArrayList<>(objectivesByType.getOrDefault(type, new ArrayList<>()));
    }
    
    /**
     * Get objective by ID
     */
    public Objective getObjective(String objectiveId) {
        return objectivesById.get(objectiveId);
    }
    
    /**
     * Get objective state
     */
    public ObjectiveState getObjectiveState(String objectiveId) {
        return objectiveStates.get(objectiveId);
    }
    
    /**
     * Get all active objectives
     */
    public List<Objective> getActiveObjectives() {
        return objectivesById.values().stream()
            .filter(obj -> {
                ObjectiveState state = objectiveStates.get(obj.getObjectiveId());
                return state != null && state.isActive();
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get objectives within radius of a position
     */
    public List<Objective> getObjectivesNearPosition(Vector3f position, float radius) {
        return objectivesById.values().stream()
            .filter(obj -> obj.getPosition().distance(position) <= radius)
            .collect(Collectors.toList());
    }
    
    /**
     * Get the closest objective to a position
     */
    public Objective getClosestObjective(Vector3f position) {
        return objectivesById.values().stream()
            .min((a, b) -> Float.compare(
                a.getPosition().distance(position),
                b.getPosition().distance(position)
            ))
            .orElse(null);
    }
    
    /**
     * Get objectives controlled by a team
     */
    public List<Objective> getTeamObjectives(String teamId) {
        return objectivesById.values().stream()
            .filter(obj -> {
                ObjectiveState state = objectiveStates.get(obj.getObjectiveId());
                return state != null && teamId.equals(state.getControllingTeam());
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get objectives that can be captured by a team
     */
    public List<Objective> getCapturable ObjectivesForTeam(String teamId) {
        return objectivesById.values().stream()
            .filter(obj -> {
                ObjectiveState state = objectiveStates.get(obj.getObjectiveId());
                return state != null && state.canBeCapturedBy(teamId);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Attempt to interact with an objective
     */
    public ObjectiveInteractionResult interactWithObjective(String objectiveId, int playerId, String teamId, ObjectiveAction action) {
        Objective objective = objectivesById.get(objectiveId);
        if (objective == null) {
            return ObjectiveInteractionResult.failure("Objective not found");
        }
        
        ObjectiveState state = objectiveStates.get(objectiveId);
        if (state == null) {
            return ObjectiveInteractionResult.failure("Objective state not found");
        }
        
        // Validate interaction
        ObjectiveInteractionValidation validation = objectiveValidator.validateInteraction(
            objective, state, playerId, teamId, action);
        
        if (!validation.isValid()) {
            return ObjectiveInteractionResult.failure(validation.getFailureReason());
        }
        
        // Process interaction
        ObjectiveInteractionResult result = state.processInteraction(playerId, teamId, action);
        
        // Sync with network if successful
        if (result.isSuccess()) {
            networkSync.syncObjectiveState(objectiveId, state);
            
            logManager.info("ObjectiveSystem", "Objective interaction processed", 
                          "objectiveId", objectiveId, "playerId", playerId, 
                          "teamId", teamId, "action", action);
        }
        
        return result;
    }
    
    /**
     * Set objective active/inactive
     */
    public void setObjectiveActive(String objectiveId, boolean active) {
        ObjectiveState state = objectiveStates.get(objectiveId);
        if (state != null) {
            state.setActive(active);
            networkSync.syncObjectiveState(objectiveId, state);
            
            logManager.info("ObjectiveSystem", "Objective state changed", 
                          "objectiveId", objectiveId, "active", active);
        }
    }
    
    /**
     * Reset all objectives to initial state
     */
    public void resetAllObjectives() {
        for (ObjectiveState state : objectiveStates.values()) {
            state.reset();
        }
        
        networkSync.syncAllObjectiveStates(objectiveStates);
        logManager.info("ObjectiveSystem", "All objectives reset");
    }
    
    /**
     * Reset objectives for a specific game mode
     */
    public void resetObjectivesForGameMode(String gameMode) {
        List<Objective> gameModeObjectives = getObjectivesForGameMode(gameMode);
        
        for (Objective objective : gameModeObjectives) {
            ObjectiveState state = objectiveStates.get(objective.getObjectiveId());
            if (state != null) {
                state.reset();
            }
        }
        
        logManager.info("ObjectiveSystem", "Objectives reset for game mode", "gameMode", gameMode);
    }
    
    /**
     * Validate objectives against map geometry and spawn points
     */
    public ObjectiveValidationReport validateObjectives(MapGeometry mapGeometry, SpawnPointSystem spawnSystem) {
        return objectiveValidator.validateAllObjectives(objectivesById.values(), mapGeometry, spawnSystem);
    }
    
    /**
     * Analyze objective balance for competitive play
     */
    public ObjectiveBalanceReport analyzeObjectiveBalance() {
        return objectiveBalancer.analyzeBalance(objectivesById.values(), objectiveStates, configuration);
    }
    
    /**
     * Add an objective to the system
     */
    public void addObjective(Objective objective) {
        objectivesById.put(objective.getObjectiveId(), objective);
        
        // Add to type collection
        objectivesByType.computeIfAbsent(objective.getObjectiveType(), k -> new ArrayList<>()).add(objective);
        
        // Add to game mode collections
        for (String gameMode : objective.getSupportedGameModes()) {
            objectivesByGameMode.computeIfAbsent(gameMode, k -> new ArrayList<>()).add(objective);
        }
        
        // Initialize state
        ObjectiveState state = new ObjectiveState(objective);
        objectiveStates.put(objective.getObjectiveId(), state);
        
        logManager.info("ObjectiveSystem", "Added objective", 
                       "objectiveId", objective.getObjectiveId(), 
                       "type", objective.getObjectiveType());
    }
    
    /**
     * Remove an objective from the system
     */
    public void removeObjective(String objectiveId) {
        Objective objective = objectivesById.remove(objectiveId);
        if (objective != null) {
            // Remove from type collection
            List<Objective> typeList = objectivesByType.get(objective.getObjectiveType());
            if (typeList != null) {
                typeList.removeIf(obj -> obj.getObjectiveId().equals(objectiveId));
            }
            
            // Remove from game mode collections
            for (List<Objective> gameModeList : objectivesByGameMode.values()) {
                gameModeList.removeIf(obj -> obj.getObjectiveId().equals(objectiveId));
            }
            
            // Remove state
            objectiveStates.remove(objectiveId);
            
            logManager.info("ObjectiveSystem", "Removed objective", "objectiveId", objectiveId);
        }
    }
    
    /**
     * Get objective statistics
     */
    public ObjectiveStatistics getObjectiveStatistics() {
        return new ObjectiveStatistics(objectivesById.values(), objectiveStates);
    }
    
    /**
     * Cleanup objective system
     */
    public void cleanup() {
        logManager.info("ObjectiveSystem", "Cleaning up objective system");
        
        try {
            if (networkSync != null) {
                networkSync.cleanup();
            }
            if (objectiveBalancer != null) {
                objectiveBalancer.cleanup();
            }
            if (objectiveValidator != null) {
                objectiveValidator.cleanup();
            }
            
            objectiveStates.clear();
            objectivesById.clear();
            objectivesByType.clear();
            objectivesByGameMode.clear();
            
            initialized = false;
            
            logManager.info("ObjectiveSystem", "Objective system cleanup complete");
            
        } catch (Exception e) {
            logManager.error("ObjectiveSystem", "Error during cleanup", e);
        }
    }
    
    /**
     * Initialize objective states
     */
    private void initializeObjectiveStates() {
        for (Objective objective : objectivesById.values()) {
            ObjectiveState state = new ObjectiveState(objective);
            objectiveStates.put(objective.getObjectiveId(), state);
        }
        
        logManager.debug("ObjectiveSystem", "Initialized objective states", "count", objectiveStates.size());
    }
    
    // Getters
    public Map<ObjectiveType, List<Objective>> getObjectivesByType() { return new HashMap<>(objectivesByType); }
    public Map<String, Objective> getObjectivesById() { return new HashMap<>(objectivesById); }
    public Map<String, List<Objective>> getObjectivesByGameMode() { return new HashMap<>(objectivesByGameMode); }
    public ObjectiveConfiguration getConfiguration() { return configuration; }
    public boolean isInitialized() { return initialized; }
    
    /**
     * Builder for ObjectiveSystem
     */
    public static class Builder {
        private Map<ObjectiveType, List<Objective>> objectivesByType = new HashMap<>();
        private Map<String, Objective> objectivesById = new HashMap<>();
        private Map<String, List<Objective>> objectivesByGameMode = new HashMap<>();
        private ObjectiveValidator objectiveValidator;
        private ObjectiveBalancer objectiveBalancer;
        private ObjectiveNetworkSync networkSync;
        private ObjectiveConfiguration configuration;
        
        public Builder addObjective(Objective objective) {
            objectivesById.put(objective.getObjectiveId(), objective);
            
            // Add to type collection
            objectivesByType.computeIfAbsent(objective.getObjectiveType(), k -> new ArrayList<>()).add(objective);
            
            // Add to game mode collections
            for (String gameMode : objective.getSupportedGameModes()) {
                objectivesByGameMode.computeIfAbsent(gameMode, k -> new ArrayList<>()).add(objective);
            }
            
            return this;
        }
        
        public Builder objectiveValidator(ObjectiveValidator validator) {
            this.objectiveValidator = validator;
            return this;
        }
        
        public Builder objectiveBalancer(ObjectiveBalancer balancer) {
            this.objectiveBalancer = balancer;
            return this;
        }
        
        public Builder networkSync(ObjectiveNetworkSync sync) {
            this.networkSync = sync;
            return this;
        }
        
        public Builder configuration(ObjectiveConfiguration config) {
            this.configuration = config;
            return this;
        }
        
        public ObjectiveSystem build() {
            return new ObjectiveSystem(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("ObjectiveSystem{objectives=%d, types=%d, gameModes=%d}", 
                           objectivesById.size(), objectivesByType.size(), objectivesByGameMode.size());
    }
}