package fps.map.objectives;

import org.joml.Vector3f;
import engine.physics.AABB;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents an objective in the map such as bomb sites, capture points, flags, etc.
 * Objectives define interactive elements that drive competitive gameplay.
 */
public class Objective {
    
    // Basic identification
    private final String objectiveId;
    private final String displayName;
    private final String description;
    private final ObjectiveType objectiveType;
    
    // Position and area
    private final Vector3f position;
    private final Vector3f rotation;
    private final AABB interactionBounds;
    private final float interactionRadius;
    
    // Game mode support
    private final Set<String> supportedGameModes;
    private final Map<String, ObjectiveGameModeConfig> gameModeConfigs;
    
    // Interaction properties
    private final ObjectiveInteractionType interactionType;
    private final float interactionTime;
    private final int maxSimultaneousUsers;
    private final boolean requiresLineOfSight;
    
    // Team and ownership
    private final String initialOwner;
    private final boolean canBeNeutral;
    private final Set<String> allowedTeams;
    
    // Visual and audio
    private final String modelPath;
    private final String iconPath;
    private final Map<String, String> soundEffects;
    private final ObjectiveVisualConfig visualConfig;
    
    // Gameplay mechanics
    private final ObjectiveMechanics mechanics;
    private final List<ObjectiveTrigger> triggers;
    private final Map<String, Object> customProperties;
    
    public Objective(Builder builder) {
        this.objectiveId = builder.objectiveId;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.objectiveType = builder.objectiveType;
        this.position = new Vector3f(builder.position);
        this.rotation = new Vector3f(builder.rotation);
        this.interactionBounds = builder.interactionBounds;
        this.interactionRadius = builder.interactionRadius;
        this.supportedGameModes = new HashSet<>(builder.supportedGameModes);
        this.gameModeConfigs = new HashMap<>(builder.gameModeConfigs);
        this.interactionType = builder.interactionType;
        this.interactionTime = builder.interactionTime;
        this.maxSimultaneousUsers = builder.maxSimultaneousUsers;
        this.requiresLineOfSight = builder.requiresLineOfSight;
        this.initialOwner = builder.initialOwner;
        this.canBeNeutral = builder.canBeNeutral;
        this.allowedTeams = new HashSet<>(builder.allowedTeams);
        this.modelPath = builder.modelPath;
        this.iconPath = builder.iconPath;
        this.soundEffects = new HashMap<>(builder.soundEffects);
        this.visualConfig = builder.visualConfig;
        this.mechanics = builder.mechanics;
        this.triggers = new ArrayList<>(builder.triggers);
        this.customProperties = new HashMap<>(builder.customProperties);
    }
    
    // Getters
    public String getObjectiveId() { return objectiveId; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public ObjectiveType getObjectiveType() { return objectiveType; }
    public Vector3f getPosition() { return new Vector3f(position); }
    public Vector3f getRotation() { return new Vector3f(rotation); }
    public AABB getInteractionBounds() { return interactionBounds; }
    public float getInteractionRadius() { return interactionRadius; }
    public Set<String> getSupportedGameModes() { return new HashSet<>(supportedGameModes); }
    public Map<String, ObjectiveGameModeConfig> getGameModeConfigs() { return new HashMap<>(gameModeConfigs); }
    public ObjectiveInteractionType getInteractionType() { return interactionType; }
    public float getInteractionTime() { return interactionTime; }
    public int getMaxSimultaneousUsers() { return maxSimultaneousUsers; }
    public boolean requiresLineOfSight() { return requiresLineOfSight; }
    public String getInitialOwner() { return initialOwner; }
    public boolean canBeNeutral() { return canBeNeutral; }
    public Set<String> getAllowedTeams() { return new HashSet<>(allowedTeams); }
    public String getModelPath() { return modelPath; }
    public String getIconPath() { return iconPath; }
    public Map<String, String> getSoundEffects() { return new HashMap<>(soundEffects); }
    public ObjectiveVisualConfig getVisualConfig() { return visualConfig; }
    public ObjectiveMechanics getMechanics() { return mechanics; }
    public List<ObjectiveTrigger> getTriggers() { return new ArrayList<>(triggers); }
    public Map<String, Object> getCustomProperties() { return new HashMap<>(customProperties); }
    
    /**
     * Check if objective supports a specific game mode
     */
    public boolean supportsGameMode(String gameMode) {
        return supportedGameModes.isEmpty() || supportedGameModes.contains(gameMode);
    }
    
    /**
     * Get configuration for a specific game mode
     */
    public ObjectiveGameModeConfig getGameModeConfig(String gameMode) {
        return gameModeConfigs.get(gameMode);
    }
    
    /**
     * Check if a team can interact with this objective
     */
    public boolean canTeamInteract(String teamId) {
        return allowedTeams.isEmpty() || allowedTeams.contains(teamId);
    }
    
    /**
     * Check if position is within interaction range
     */
    public boolean isWithinInteractionRange(Vector3f testPosition) {
        if (interactionBounds != null) {
            return interactionBounds.contains(testPosition);
        } else {
            return position.distance(testPosition) <= interactionRadius;
        }
    }
    
    /**
     * Calculate distance to objective
     */
    public float distanceTo(Vector3f testPosition) {
        return position.distance(testPosition);
    }
    
    /**
     * Get sound effect for a specific action
     */
    public String getSoundEffect(String action) {
        return soundEffects.get(action);
    }
    
    /**
     * Get custom property value
     */
    @SuppressWarnings("unchecked")
    public <T> T getCustomProperty(String key, Class<T> type) {
        Object value = customProperties.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Create a bomb site objective
     */
    public static Objective createBombSite(String siteId, Vector3f position, String siteName) {
        return new Builder(siteId, ObjectiveType.BOMB_SITE, position)
            .displayName(siteName)
            .description("Bomb site for Search & Destroy")
            .addSupportedGameMode("search_destroy")
            .addSupportedGameMode("demolition")
            .interactionType(ObjectiveInteractionType.PLANT_DEFUSE)
            .interactionTime(5.0f)
            .interactionRadius(3.0f)
            .maxSimultaneousUsers(1)
            .requiresLineOfSight(true)
            .canBeNeutral(true)
            .addSoundEffect("plant", "bomb_plant")
            .addSoundEffect("defuse", "bomb_defuse")
            .addSoundEffect("explode", "bomb_explode")
            .build();
    }
    
    /**
     * Create a capture point objective
     */
    public static Objective createCapturePoint(String pointId, Vector3f position, String pointName) {
        return new Builder(pointId, ObjectiveType.CAPTURE_POINT, position)
            .displayName(pointName)
            .description("Capture point for domination modes")
            .addSupportedGameMode("domination")
            .addSupportedGameMode("king_of_hill")
            .interactionType(ObjectiveInteractionType.CAPTURE_HOLD)
            .interactionTime(10.0f)
            .interactionRadius(5.0f)
            .maxSimultaneousUsers(8)
            .requiresLineOfSight(false)
            .canBeNeutral(true)
            .addSoundEffect("capture_start", "capture_begin")
            .addSoundEffect("capture_complete", "capture_success")
            .addSoundEffect("contested", "capture_contested")
            .build();
    }
    
    /**
     * Create a flag objective
     */
    public static Objective createFlag(String flagId, Vector3f position, String teamId) {
        return new Builder(flagId, ObjectiveType.FLAG, position)
            .displayName(teamId + " Flag")
            .description("Team flag for Capture the Flag")
            .addSupportedGameMode("capture_flag")
            .interactionType(ObjectiveInteractionType.PICKUP_CARRY)
            .interactionTime(1.0f)
            .interactionRadius(2.0f)
            .maxSimultaneousUsers(1)
            .requiresLineOfSight(true)
            .initialOwner(teamId)
            .canBeNeutral(false)
            .addAllowedTeam(getOpposingTeam(teamId))
            .addSoundEffect("pickup", "flag_pickup")
            .addSoundEffect("drop", "flag_drop")
            .addSoundEffect("return", "flag_return")
            .build();
    }
    
    /**
     * Helper method to get opposing team (simplified)
     */
    private static String getOpposingTeam(String teamId) {
        return teamId.equals("team_a") ? "team_b" : "team_a";
    }
    
    /**
     * Builder for Objective
     */
    public static class Builder {
        private String objectiveId;
        private String displayName;
        private String description = "";
        private ObjectiveType objectiveType;
        private Vector3f position;
        private Vector3f rotation = new Vector3f(0, 0, 0);
        private AABB interactionBounds;
        private float interactionRadius = 3.0f;
        private Set<String> supportedGameModes = new HashSet<>();
        private Map<String, ObjectiveGameModeConfig> gameModeConfigs = new HashMap<>();
        private ObjectiveInteractionType interactionType = ObjectiveInteractionType.INSTANT;
        private float interactionTime = 0.0f;
        private int maxSimultaneousUsers = 1;
        private boolean requiresLineOfSight = false;
        private String initialOwner;
        private boolean canBeNeutral = true;
        private Set<String> allowedTeams = new HashSet<>();
        private String modelPath;
        private String iconPath;
        private Map<String, String> soundEffects = new HashMap<>();
        private ObjectiveVisualConfig visualConfig;
        private ObjectiveMechanics mechanics;
        private List<ObjectiveTrigger> triggers = new ArrayList<>();
        private Map<String, Object> customProperties = new HashMap<>();
        
        public Builder(String objectiveId, ObjectiveType objectiveType, Vector3f position) {
            this.objectiveId = objectiveId;
            this.objectiveType = objectiveType;
            this.position = new Vector3f(position);
        }
        
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder rotation(Vector3f rotation) {
            this.rotation = new Vector3f(rotation);
            return this;
        }
        
        public Builder interactionBounds(AABB bounds) {
            this.interactionBounds = bounds;
            return this;
        }
        
        public Builder interactionRadius(float radius) {
            this.interactionRadius = Math.max(0, radius);
            return this;
        }
        
        public Builder addSupportedGameMode(String gameMode) {
            this.supportedGameModes.add(gameMode);
            return this;
        }
        
        public Builder addGameModeConfig(String gameMode, ObjectiveGameModeConfig config) {
            this.gameModeConfigs.put(gameMode, config);
            return this;
        }
        
        public Builder interactionType(ObjectiveInteractionType type) {
            this.interactionType = type;
            return this;
        }
        
        public Builder interactionTime(float time) {
            this.interactionTime = Math.max(0, time);
            return this;
        }
        
        public Builder maxSimultaneousUsers(int max) {
            this.maxSimultaneousUsers = Math.max(1, max);
            return this;
        }
        
        public Builder requiresLineOfSight(boolean requires) {
            this.requiresLineOfSight = requires;
            return this;
        }
        
        public Builder initialOwner(String owner) {
            this.initialOwner = owner;
            return this;
        }
        
        public Builder canBeNeutral(boolean canBeNeutral) {
            this.canBeNeutral = canBeNeutral;
            return this;
        }
        
        public Builder addAllowedTeam(String teamId) {
            this.allowedTeams.add(teamId);
            return this;
        }
        
        public Builder modelPath(String path) {
            this.modelPath = path;
            return this;
        }
        
        public Builder iconPath(String path) {
            this.iconPath = path;
            return this;
        }
        
        public Builder addSoundEffect(String action, String soundName) {
            this.soundEffects.put(action, soundName);
            return this;
        }
        
        public Builder visualConfig(ObjectiveVisualConfig config) {
            this.visualConfig = config;
            return this;
        }
        
        public Builder mechanics(ObjectiveMechanics mechanics) {
            this.mechanics = mechanics;
            return this;
        }
        
        public Builder addTrigger(ObjectiveTrigger trigger) {
            this.triggers.add(trigger);
            return this;
        }
        
        public Builder addCustomProperty(String key, Object value) {
            this.customProperties.put(key, value);
            return this;
        }
        
        public Objective build() {
            if (objectiveId == null || objectiveId.isEmpty()) {
                throw new IllegalStateException("Objective ID is required");
            }
            if (objectiveType == null) {
                throw new IllegalStateException("Objective type is required");
            }
            if (position == null) {
                throw new IllegalStateException("Position is required");
            }
            if (displayName == null) {
                displayName = objectiveId;
            }
            
            return new Objective(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("Objective{id='%s', type=%s, position=%s}", 
                           objectiveId, objectiveType, position);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Objective that = (Objective) obj;
        return objectiveId.equals(that.objectiveId);
    }
    
    @Override
    public int hashCode() {
        return objectiveId.hashCode();
    }
}