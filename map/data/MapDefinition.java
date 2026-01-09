package fps.map.data;

import org.joml.Vector3f;
import fps.map.geometry.MapGeometry;
import fps.map.spawns.SpawnPointSystem;
import fps.map.objectives.ObjectiveSystem;
import fps.map.boundaries.MapBoundaries;
import fps.map.entities.DynamicMapEntity;
import fps.map.competitive.CompetitiveFeatures;
import fps.map.environment.EnvironmentalSystem;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Complete definition of a map including all geometry, entities, and metadata.
 * This is the core data structure that represents everything about a map.
 */
public class MapDefinition {
    
    // Core identification
    private final String mapId;
    private final MapMetadata metadata;
    private final MapVersion version;
    
    // Geometry and collision
    private final MapGeometry geometry;
    private final MapBoundaries boundaries;
    
    // Gameplay systems
    private final SpawnPointSystem spawnSystem;
    private final ObjectiveSystem objectiveSystem;
    private final CompetitiveFeatures competitiveFeatures;
    
    // Dynamic elements
    private final List<DynamicMapEntity> dynamicEntities;
    private final EnvironmentalSystem environmentalSystem;
    
    // Performance and optimization
    private final MapOptimizationData optimizationData;
    private final Map<String, Object> customProperties;
    
    // File paths and resources
    private final String mapFilePath;
    private final List<String> assetPaths;
    
    public MapDefinition(Builder builder) {
        this.mapId = builder.mapId;
        this.metadata = builder.metadata;
        this.version = builder.version;
        this.geometry = builder.geometry;
        this.boundaries = builder.boundaries;
        this.spawnSystem = builder.spawnSystem;
        this.objectiveSystem = builder.objectiveSystem;
        this.competitiveFeatures = builder.competitiveFeatures;
        this.dynamicEntities = new ArrayList<>(builder.dynamicEntities);
        this.environmentalSystem = builder.environmentalSystem;
        this.optimizationData = builder.optimizationData;
        this.customProperties = new HashMap<>(builder.customProperties);
        this.mapFilePath = builder.mapFilePath;
        this.assetPaths = new ArrayList<>(builder.assetPaths);
    }
    
    // Getters
    public String getMapId() { return mapId; }
    public MapMetadata getMetadata() { return metadata; }
    public MapVersion getVersion() { return version; }
    public MapGeometry getGeometry() { return geometry; }
    public MapBoundaries getBoundaries() { return boundaries; }
    public SpawnPointSystem getSpawnSystem() { return spawnSystem; }
    public ObjectiveSystem getObjectiveSystem() { return objectiveSystem; }
    public CompetitiveFeatures getCompetitiveFeatures() { return competitiveFeatures; }
    public List<DynamicMapEntity> getDynamicEntities() { return new ArrayList<>(dynamicEntities); }
    public EnvironmentalSystem getEnvironmentalSystem() { return environmentalSystem; }
    public MapOptimizationData getOptimizationData() { return optimizationData; }
    public Map<String, Object> getCustomProperties() { return new HashMap<>(customProperties); }
    public String getMapFilePath() { return mapFilePath; }
    public List<String> getAssetPaths() { return new ArrayList<>(assetPaths); }
    
    /**
     * Get a custom property value
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
     * Check if map supports a specific game mode
     */
    public boolean supportsGameMode(String gameMode) {
        return metadata.getSupportedGameModes().contains(gameMode);
    }
    
    /**
     * Check if map is suitable for competitive play
     */
    public boolean isCompetitiveReady() {
        return competitiveFeatures != null && competitiveFeatures.isCompetitiveReady();
    }
    
    /**
     * Get recommended player count for this map
     */
    public int getRecommendedPlayerCount() {
        return metadata.getRecommendedPlayerCount();
    }
    
    /**
     * Get map size category
     */
    public MapSize getMapSize() {
        return metadata.getMapSize();
    }
    
    /**
     * Builder pattern for creating MapDefinition instances
     */
    public static class Builder {
        private String mapId;
        private MapMetadata metadata;
        private MapVersion version;
        private MapGeometry geometry;
        private MapBoundaries boundaries;
        private SpawnPointSystem spawnSystem;
        private ObjectiveSystem objectiveSystem;
        private CompetitiveFeatures competitiveFeatures;
        private List<DynamicMapEntity> dynamicEntities = new ArrayList<>();
        private EnvironmentalSystem environmentalSystem;
        private MapOptimizationData optimizationData;
        private Map<String, Object> customProperties = new HashMap<>();
        private String mapFilePath;
        private List<String> assetPaths = new ArrayList<>();
        
        public Builder(String mapId) {
            this.mapId = mapId;
        }
        
        public Builder metadata(MapMetadata metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public Builder version(MapVersion version) {
            this.version = version;
            return this;
        }
        
        public Builder geometry(MapGeometry geometry) {
            this.geometry = geometry;
            return this;
        }
        
        public Builder boundaries(MapBoundaries boundaries) {
            this.boundaries = boundaries;
            return this;
        }
        
        public Builder spawnSystem(SpawnPointSystem spawnSystem) {
            this.spawnSystem = spawnSystem;
            return this;
        }
        
        public Builder objectiveSystem(ObjectiveSystem objectiveSystem) {
            this.objectiveSystem = objectiveSystem;
            return this;
        }
        
        public Builder competitiveFeatures(CompetitiveFeatures competitiveFeatures) {
            this.competitiveFeatures = competitiveFeatures;
            return this;
        }
        
        public Builder addDynamicEntity(DynamicMapEntity entity) {
            this.dynamicEntities.add(entity);
            return this;
        }
        
        public Builder dynamicEntities(List<DynamicMapEntity> entities) {
            this.dynamicEntities = new ArrayList<>(entities);
            return this;
        }
        
        public Builder environmentalSystem(EnvironmentalSystem environmentalSystem) {
            this.environmentalSystem = environmentalSystem;
            return this;
        }
        
        public Builder optimizationData(MapOptimizationData optimizationData) {
            this.optimizationData = optimizationData;
            return this;
        }
        
        public Builder customProperty(String key, Object value) {
            this.customProperties.put(key, value);
            return this;
        }
        
        public Builder customProperties(Map<String, Object> properties) {
            this.customProperties = new HashMap<>(properties);
            return this;
        }
        
        public Builder mapFilePath(String filePath) {
            this.mapFilePath = filePath;
            return this;
        }
        
        public Builder addAssetPath(String assetPath) {
            this.assetPaths.add(assetPath);
            return this;
        }
        
        public Builder assetPaths(List<String> paths) {
            this.assetPaths = new ArrayList<>(paths);
            return this;
        }
        
        public MapDefinition build() {
            // Validate required fields
            if (mapId == null || mapId.isEmpty()) {
                throw new IllegalStateException("Map ID is required");
            }
            if (metadata == null) {
                throw new IllegalStateException("Map metadata is required");
            }
            if (geometry == null) {
                throw new IllegalStateException("Map geometry is required");
            }
            
            // Set defaults for optional fields
            if (version == null) {
                version = new MapVersion(1, 0, 0);
            }
            if (boundaries == null) {
                boundaries = MapBoundaries.createDefault();
            }
            if (optimizationData == null) {
                optimizationData = new MapOptimizationData();
            }
            
            return new MapDefinition(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("MapDefinition{mapId='%s', name='%s', version=%s}", 
                           mapId, metadata.getDisplayName(), version);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        MapDefinition that = (MapDefinition) obj;
        return mapId.equals(that.mapId) && version.equals(that.version);
    }
    
    @Override
    public int hashCode() {
        return mapId.hashCode() * 31 + version.hashCode();
    }
}