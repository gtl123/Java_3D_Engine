package fps.map.data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

/**
 * Metadata information for a map including display information,
 * supported game modes, player counts, and other descriptive data.
 */
public class MapMetadata {
    
    // Display information
    private final String displayName;
    private final String description;
    private final String author;
    private final String thumbnailPath;
    private final List<String> screenshotPaths;
    
    // Game mode support
    private final Set<String> supportedGameModes;
    private final int minPlayerCount;
    private final int maxPlayerCount;
    private final int recommendedPlayerCount;
    
    // Map characteristics
    private final MapSize mapSize;
    private final MapTheme theme;
    private final MapComplexity complexity;
    private final Set<String> tags;
    
    // Version and creation info
    private final LocalDateTime createdDate;
    private final LocalDateTime lastModified;
    private final String creatorId;
    private final boolean isOfficial;
    
    // Competitive information
    private final boolean competitiveApproved;
    private final CompetitiveRating competitiveRating;
    private final Set<String> competitiveGameModes;
    
    // Performance hints
    private final PerformanceProfile performanceProfile;
    private final int estimatedMemoryUsage; // MB
    private final int estimatedLoadTime; // seconds
    
    public MapMetadata(Builder builder) {
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.author = builder.author;
        this.thumbnailPath = builder.thumbnailPath;
        this.screenshotPaths = new ArrayList<>(builder.screenshotPaths);
        this.supportedGameModes = new HashSet<>(builder.supportedGameModes);
        this.minPlayerCount = builder.minPlayerCount;
        this.maxPlayerCount = builder.maxPlayerCount;
        this.recommendedPlayerCount = builder.recommendedPlayerCount;
        this.mapSize = builder.mapSize;
        this.theme = builder.theme;
        this.complexity = builder.complexity;
        this.tags = new HashSet<>(builder.tags);
        this.createdDate = builder.createdDate;
        this.lastModified = builder.lastModified;
        this.creatorId = builder.creatorId;
        this.isOfficial = builder.isOfficial;
        this.competitiveApproved = builder.competitiveApproved;
        this.competitiveRating = builder.competitiveRating;
        this.competitiveGameModes = new HashSet<>(builder.competitiveGameModes);
        this.performanceProfile = builder.performanceProfile;
        this.estimatedMemoryUsage = builder.estimatedMemoryUsage;
        this.estimatedLoadTime = builder.estimatedLoadTime;
    }
    
    // Getters
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
    public String getThumbnailPath() { return thumbnailPath; }
    public List<String> getScreenshotPaths() { return new ArrayList<>(screenshotPaths); }
    public Set<String> getSupportedGameModes() { return new HashSet<>(supportedGameModes); }
    public int getMinPlayerCount() { return minPlayerCount; }
    public int getMaxPlayerCount() { return maxPlayerCount; }
    public int getRecommendedPlayerCount() { return recommendedPlayerCount; }
    public MapSize getMapSize() { return mapSize; }
    public MapTheme getTheme() { return theme; }
    public MapComplexity getComplexity() { return complexity; }
    public Set<String> getTags() { return new HashSet<>(tags); }
    public LocalDateTime getCreatedDate() { return createdDate; }
    public LocalDateTime getLastModified() { return lastModified; }
    public String getCreatorId() { return creatorId; }
    public boolean isOfficial() { return isOfficial; }
    public boolean isCompetitiveApproved() { return competitiveApproved; }
    public CompetitiveRating getCompetitiveRating() { return competitiveRating; }
    public Set<String> getCompetitiveGameModes() { return new HashSet<>(competitiveGameModes); }
    public PerformanceProfile getPerformanceProfile() { return performanceProfile; }
    public int getEstimatedMemoryUsage() { return estimatedMemoryUsage; }
    public int getEstimatedLoadTime() { return estimatedLoadTime; }
    
    /**
     * Check if map supports a specific game mode
     */
    public boolean supportsGameMode(String gameMode) {
        return supportedGameModes.contains(gameMode);
    }
    
    /**
     * Check if map is approved for competitive play in a specific game mode
     */
    public boolean isCompetitiveForGameMode(String gameMode) {
        return competitiveApproved && competitiveGameModes.contains(gameMode);
    }
    
    /**
     * Check if player count is within supported range
     */
    public boolean supportsPlayerCount(int playerCount) {
        return playerCount >= minPlayerCount && playerCount <= maxPlayerCount;
    }
    
    /**
     * Check if map has a specific tag
     */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
    
    /**
     * Map size categories
     */
    public enum MapSize {
        SMALL("Small", "Compact maps for close-quarters combat"),
        MEDIUM("Medium", "Balanced maps for various gameplay styles"),
        LARGE("Large", "Expansive maps for long-range engagements"),
        EXTRA_LARGE("Extra Large", "Massive maps for large-scale battles");
        
        private final String displayName;
        private final String description;
        
        MapSize(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    /**
     * Map themes for visual categorization
     */
    public enum MapTheme {
        URBAN("Urban", "City and urban environments"),
        INDUSTRIAL("Industrial", "Factories and industrial complexes"),
        MILITARY("Military", "Military bases and installations"),
        DESERT("Desert", "Arid and desert environments"),
        FOREST("Forest", "Woodland and forest areas"),
        ARCTIC("Arctic", "Snow and ice environments"),
        FUTURISTIC("Futuristic", "Sci-fi and futuristic settings"),
        UNDERGROUND("Underground", "Tunnels and underground facilities");
        
        private final String displayName;
        private final String description;
        
        MapTheme(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    /**
     * Map complexity levels
     */
    public enum MapComplexity {
        SIMPLE("Simple", "Easy to learn layout"),
        MODERATE("Moderate", "Balanced complexity"),
        COMPLEX("Complex", "Intricate multi-level design"),
        EXPERT("Expert", "Highly complex professional maps");
        
        private final String displayName;
        private final String description;
        
        MapComplexity(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    /**
     * Competitive rating system
     */
    public enum CompetitiveRating {
        UNRATED("Unrated", 0),
        BRONZE("Bronze", 1),
        SILVER("Silver", 2),
        GOLD("Gold", 3),
        PLATINUM("Platinum", 4),
        DIAMOND("Diamond", 5);
        
        private final String displayName;
        private final int level;
        
        CompetitiveRating(String displayName, int level) {
            this.displayName = displayName;
            this.level = level;
        }
        
        public String getDisplayName() { return displayName; }
        public int getLevel() { return level; }
    }
    
    /**
     * Performance profile for optimization hints
     */
    public enum PerformanceProfile {
        LOW("Low", "Optimized for lower-end hardware"),
        MEDIUM("Medium", "Balanced performance requirements"),
        HIGH("High", "Requires high-end hardware"),
        ULTRA("Ultra", "Maximum quality, top-tier hardware only");
        
        private final String displayName;
        private final String description;
        
        PerformanceProfile(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }
    
    /**
     * Builder pattern for creating MapMetadata instances
     */
    public static class Builder {
        private String displayName;
        private String description = "";
        private String author = "Unknown";
        private String thumbnailPath;
        private List<String> screenshotPaths = new ArrayList<>();
        private Set<String> supportedGameModes = new HashSet<>();
        private int minPlayerCount = 2;
        private int maxPlayerCount = 32;
        private int recommendedPlayerCount = 16;
        private MapSize mapSize = MapSize.MEDIUM;
        private MapTheme theme = MapTheme.URBAN;
        private MapComplexity complexity = MapComplexity.MODERATE;
        private Set<String> tags = new HashSet<>();
        private LocalDateTime createdDate = LocalDateTime.now();
        private LocalDateTime lastModified = LocalDateTime.now();
        private String creatorId;
        private boolean isOfficial = false;
        private boolean competitiveApproved = false;
        private CompetitiveRating competitiveRating = CompetitiveRating.UNRATED;
        private Set<String> competitiveGameModes = new HashSet<>();
        private PerformanceProfile performanceProfile = PerformanceProfile.MEDIUM;
        private int estimatedMemoryUsage = 256; // MB
        private int estimatedLoadTime = 30; // seconds
        
        public Builder(String displayName) {
            this.displayName = displayName;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder author(String author) {
            this.author = author;
            return this;
        }
        
        public Builder thumbnailPath(String thumbnailPath) {
            this.thumbnailPath = thumbnailPath;
            return this;
        }
        
        public Builder addScreenshot(String screenshotPath) {
            this.screenshotPaths.add(screenshotPath);
            return this;
        }
        
        public Builder addSupportedGameMode(String gameMode) {
            this.supportedGameModes.add(gameMode);
            return this;
        }
        
        public Builder playerCount(int min, int max, int recommended) {
            this.minPlayerCount = min;
            this.maxPlayerCount = max;
            this.recommendedPlayerCount = recommended;
            return this;
        }
        
        public Builder mapSize(MapSize mapSize) {
            this.mapSize = mapSize;
            return this;
        }
        
        public Builder theme(MapTheme theme) {
            this.theme = theme;
            return this;
        }
        
        public Builder complexity(MapComplexity complexity) {
            this.complexity = complexity;
            return this;
        }
        
        public Builder addTag(String tag) {
            this.tags.add(tag);
            return this;
        }
        
        public Builder createdDate(LocalDateTime createdDate) {
            this.createdDate = createdDate;
            return this;
        }
        
        public Builder lastModified(LocalDateTime lastModified) {
            this.lastModified = lastModified;
            return this;
        }
        
        public Builder creatorId(String creatorId) {
            this.creatorId = creatorId;
            return this;
        }
        
        public Builder official(boolean isOfficial) {
            this.isOfficial = isOfficial;
            return this;
        }
        
        public Builder competitiveApproved(boolean approved) {
            this.competitiveApproved = approved;
            return this;
        }
        
        public Builder competitiveRating(CompetitiveRating rating) {
            this.competitiveRating = rating;
            return this;
        }
        
        public Builder addCompetitiveGameMode(String gameMode) {
            this.competitiveGameModes.add(gameMode);
            return this;
        }
        
        public Builder performanceProfile(PerformanceProfile profile) {
            this.performanceProfile = profile;
            return this;
        }
        
        public Builder estimatedMemoryUsage(int memoryMB) {
            this.estimatedMemoryUsage = memoryMB;
            return this;
        }
        
        public Builder estimatedLoadTime(int loadTimeSeconds) {
            this.estimatedLoadTime = loadTimeSeconds;
            return this;
        }
        
        public MapMetadata build() {
            if (displayName == null || displayName.isEmpty()) {
                throw new IllegalStateException("Display name is required");
            }
            return new MapMetadata(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("MapMetadata{name='%s', author='%s', size=%s, theme=%s}", 
                           displayName, author, mapSize, theme);
    }
}