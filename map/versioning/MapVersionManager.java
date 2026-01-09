package fps.map.versioning;

import fps.map.data.MapDefinition;
import fps.map.data.MapVersion;
import engine.logging.LogManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Manages map versions, updates, and compatibility checking.
 * Handles version control, rollbacks, and migration between map versions.
 */
public class MapVersionManager {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final Map<String, MapVersionHistory> mapHistories;
    private final Map<String, MapDefinition> versionedMaps;
    private final VersionCompatibilityChecker compatibilityChecker;
    private final MapMigrationManager migrationManager;
    
    // Version control settings
    private final int maxVersionsPerMap;
    private final boolean autoBackup;
    private final String backupDirectory;
    
    public MapVersionManager(int maxVersionsPerMap, boolean autoBackup, String backupDirectory) {
        this.mapHistories = new ConcurrentHashMap<>();
        this.versionedMaps = new ConcurrentHashMap<>();
        this.compatibilityChecker = new VersionCompatibilityChecker();
        this.migrationManager = new MapMigrationManager();
        this.maxVersionsPerMap = maxVersionsPerMap;
        this.autoBackup = autoBackup;
        this.backupDirectory = backupDirectory;
        
        logManager.info("MapVersionManager", "Map version manager initialized", 
                       "maxVersions", maxVersionsPerMap, "autoBackup", autoBackup);
    }
    
    /**
     * Register a new map version
     */
    public MapVersion registerMapVersion(MapDefinition mapDefinition, String changeDescription, String author) {
        String mapId = mapDefinition.getMapId();
        
        // Get or create version history for this map
        MapVersionHistory history = mapHistories.computeIfAbsent(mapId, k -> new MapVersionHistory(mapId));
        
        // Create new version
        MapVersion newVersion = createNewVersion(history, mapDefinition, changeDescription, author);
        
        // Add to history
        history.addVersion(newVersion, mapDefinition);
        
        // Store versioned map
        String versionKey = mapId + ":" + newVersion.toString();
        versionedMaps.put(versionKey, mapDefinition);
        
        // Cleanup old versions if needed
        if (history.getVersionCount() > maxVersionsPerMap) {
            cleanupOldVersions(history);
        }
        
        // Create backup if enabled
        if (autoBackup) {
            createBackup(mapDefinition, newVersion);
        }
        
        logManager.info("MapVersionManager", "Registered new map version", 
                       "mapId", mapId, "version", newVersion.toString(), "author", author);
        
        return newVersion;
    }
    
    /**
     * Get a specific version of a map
     */
    public MapDefinition getMapVersion(String mapId, MapVersion version) {
        String versionKey = mapId + ":" + version.toString();
        return versionedMaps.get(versionKey);
    }
    
    /**
     * Get the latest version of a map
     */
    public MapDefinition getLatestMapVersion(String mapId) {
        MapVersionHistory history = mapHistories.get(mapId);
        if (history == null) {
            return null;
        }
        
        MapVersion latestVersion = history.getLatestVersion();
        return latestVersion != null ? getMapVersion(mapId, latestVersion) : null;
    }
    
    /**
     * Get version history for a map
     */
    public MapVersionHistory getVersionHistory(String mapId) {
        return mapHistories.get(mapId);
    }
    
    /**
     * Check if two map versions are compatible
     */
    public CompatibilityResult checkCompatibility(String mapId, MapVersion version1, MapVersion version2) {
        MapDefinition map1 = getMapVersion(mapId, version1);
        MapDefinition map2 = getMapVersion(mapId, version2);
        
        if (map1 == null || map2 == null) {
            return new CompatibilityResult(false, "One or both map versions not found", CompatibilityLevel.INCOMPATIBLE);
        }
        
        return compatibilityChecker.checkCompatibility(map1, map2);
    }
    
    /**
     * Migrate a map from one version to another
     */
    public MigrationResult migrateMap(String mapId, MapVersion fromVersion, MapVersion toVersion) {
        MapDefinition sourceMap = getMapVersion(mapId, fromVersion);
        MapDefinition targetMap = getMapVersion(mapId, toVersion);
        
        if (sourceMap == null || targetMap == null) {
            return new MigrationResult(false, "Source or target map version not found", null);
        }
        
        // Check compatibility first
        CompatibilityResult compatibility = checkCompatibility(mapId, fromVersion, toVersion);
        if (compatibility.getLevel() == CompatibilityLevel.INCOMPATIBLE) {
            return new MigrationResult(false, "Map versions are incompatible: " + compatibility.getReason(), null);
        }
        
        return migrationManager.migrateMap(sourceMap, targetMap, fromVersion, toVersion);
    }
    
    /**
     * Rollback to a previous version
     */
    public boolean rollbackToVersion(String mapId, MapVersion targetVersion, String reason, String author) {
        MapVersionHistory history = mapHistories.get(mapId);
        if (history == null) {
            logManager.error("MapVersionManager", "Cannot rollback - no history found", "mapId", mapId);
            return false;
        }
        
        MapDefinition targetMap = getMapVersion(mapId, targetVersion);
        if (targetMap == null) {
            logManager.error("MapVersionManager", "Cannot rollback - target version not found", 
                           "mapId", mapId, "version", targetVersion.toString());
            return false;
        }
        
        // Create rollback entry
        String rollbackDescription = String.format("Rollback to version %s: %s", targetVersion.toString(), reason);
        MapVersion rollbackVersion = registerMapVersion(targetMap, rollbackDescription, author);
        
        // Mark as rollback in history
        history.markAsRollback(rollbackVersion, targetVersion);
        
        logManager.info("MapVersionManager", "Rolled back map version", 
                       "mapId", mapId, "fromVersion", rollbackVersion.toString(), 
                       "toVersion", targetVersion.toString(), "reason", reason);
        
        return true;
    }
    
    /**
     * Create a branch from an existing version
     */
    public MapVersion createBranch(String mapId, MapVersion baseVersion, String branchName, String description, String author) {
        MapDefinition baseMap = getMapVersion(mapId, baseVersion);
        if (baseMap == null) {
            throw new IllegalArgumentException("Base version not found: " + baseVersion);
        }
        
        // Create branch version
        MapVersion branchVersion = new MapVersion.Builder()
            .major(baseVersion.getMajor())
            .minor(baseVersion.getMinor())
            .patch(baseVersion.getPatch() + 1)
            .preRelease(branchName)
            .build();
        
        // Register the branch
        MapVersion newVersion = registerMapVersion(baseMap, description, author);
        
        // Update history to mark as branch
        MapVersionHistory history = mapHistories.get(mapId);
        if (history != null) {
            history.markAsBranch(newVersion, baseVersion, branchName);
        }
        
        logManager.info("MapVersionManager", "Created map branch", 
                       "mapId", mapId, "baseVersion", baseVersion.toString(), 
                       "branchVersion", newVersion.toString(), "branchName", branchName);
        
        return newVersion;
    }
    
    /**
     * Merge a branch back into main version line
     */
    public MergeResult mergeBranch(String mapId, MapVersion branchVersion, MapVersion targetVersion, String author) {
        MapDefinition branchMap = getMapVersion(mapId, branchVersion);
        MapDefinition targetMap = getMapVersion(mapId, targetVersion);
        
        if (branchMap == null || targetMap == null) {
            return new MergeResult(false, "Branch or target version not found", null);
        }
        
        // Perform merge using migration manager
        MigrationResult migrationResult = migrationManager.mergeMapVersions(branchMap, targetMap, branchVersion, targetVersion);
        
        if (migrationResult.isSuccess()) {
            // Register merged version
            String mergeDescription = String.format("Merged branch %s into %s", branchVersion.toString(), targetVersion.toString());
            MapVersion mergedVersion = registerMapVersion(migrationResult.getResultMap(), mergeDescription, author);
            
            return new MergeResult(true, "Branch merged successfully", mergedVersion);
        } else {
            return new MergeResult(false, "Merge failed: " + migrationResult.getErrorMessage(), null);
        }
    }
    
    /**
     * Get all versions of a map that are compatible with a specific version
     */
    public List<MapVersion> getCompatibleVersions(String mapId, MapVersion referenceVersion, CompatibilityLevel minLevel) {
        MapVersionHistory history = mapHistories.get(mapId);
        if (history == null) {
            return new ArrayList<>();
        }
        
        List<MapVersion> compatibleVersions = new ArrayList<>();
        
        for (MapVersion version : history.getAllVersions()) {
            if (!version.equals(referenceVersion)) {
                CompatibilityResult compatibility = checkCompatibility(mapId, referenceVersion, version);
                if (compatibility.getLevel().ordinal() >= minLevel.ordinal()) {
                    compatibleVersions.add(version);
                }
            }
        }
        
        return compatibleVersions;
    }
    
    /**
     * Get version statistics for a map
     */
    public VersionStatistics getVersionStatistics(String mapId) {
        MapVersionHistory history = mapHistories.get(mapId);
        if (history == null) {
            return new VersionStatistics(mapId, 0, null, null, new ArrayList<>());
        }
        
        return new VersionStatistics(
            mapId,
            history.getVersionCount(),
            history.getFirstVersion(),
            history.getLatestVersion(),
            history.getAllVersions()
        );
    }
    
    /**
     * Create a new version number
     */
    private MapVersion createNewVersion(MapVersionHistory history, MapDefinition mapDefinition, String changeDescription, String author) {
        MapVersion latestVersion = history.getLatestVersion();
        
        if (latestVersion == null) {
            // First version
            return new MapVersion.Builder()
                .major(1)
                .minor(0)
                .patch(0)
                .build();
        }
        
        // Determine version increment based on change type
        VersionChangeType changeType = analyzeChangeType(mapDefinition, changeDescription);
        
        switch (changeType) {
            case MAJOR:
                return new MapVersion.Builder()
                    .major(latestVersion.getMajor() + 1)
                    .minor(0)
                    .patch(0)
                    .build();
            case MINOR:
                return new MapVersion.Builder()
                    .major(latestVersion.getMajor())
                    .minor(latestVersion.getMinor() + 1)
                    .patch(0)
                    .build();
            case PATCH:
            default:
                return new MapVersion.Builder()
                    .major(latestVersion.getMajor())
                    .minor(latestVersion.getMinor())
                    .patch(latestVersion.getPatch() + 1)
                    .build();
        }
    }
    
    /**
     * Analyze the type of change based on description and map content
     */
    private VersionChangeType analyzeChangeType(MapDefinition mapDefinition, String changeDescription) {
        String description = changeDescription.toLowerCase();
        
        // Major changes
        if (description.contains("breaking") || description.contains("incompatible") || 
            description.contains("major redesign") || description.contains("complete rewrite")) {
            return VersionChangeType.MAJOR;
        }
        
        // Minor changes
        if (description.contains("new feature") || description.contains("added") || 
            description.contains("enhancement") || description.contains("improved")) {
            return VersionChangeType.MINOR;
        }
        
        // Default to patch
        return VersionChangeType.PATCH;
    }
    
    /**
     * Cleanup old versions beyond the maximum limit
     */
    private void cleanupOldVersions(MapVersionHistory history) {
        List<MapVersion> versionsToRemove = history.getVersionsToCleanup(maxVersionsPerMap);
        
        for (MapVersion version : versionsToRemove) {
            String versionKey = history.getMapId() + ":" + version.toString();
            versionedMaps.remove(versionKey);
            
            logManager.info("MapVersionManager", "Cleaned up old version", 
                           "mapId", history.getMapId(), "version", version.toString());
        }
        
        history.removeOldVersions(maxVersionsPerMap);
    }
    
    /**
     * Create backup of map version
     */
    private void createBackup(MapDefinition mapDefinition, MapVersion version) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupFileName = String.format("%s_v%s_%s.backup", 
                                                mapDefinition.getMapId(), version.toString(), timestamp);
            
            // This would serialize the map to backup directory
            // backupManager.createBackup(mapDefinition, backupDirectory + "/" + backupFileName);
            
            logManager.info("MapVersionManager", "Created backup", 
                           "mapId", mapDefinition.getMapId(), "version", version.toString(), 
                           "backupFile", backupFileName);
            
        } catch (Exception e) {
            logManager.error("MapVersionManager", "Failed to create backup", 
                           "mapId", mapDefinition.getMapId(), "version", version.toString(), e);
        }
    }
    
    /**
     * Get all maps with their latest versions
     */
    public Map<String, MapVersion> getAllLatestVersions() {
        Map<String, MapVersion> latestVersions = new HashMap<>();
        
        for (MapVersionHistory history : mapHistories.values()) {
            MapVersion latest = history.getLatestVersion();
            if (latest != null) {
                latestVersions.put(history.getMapId(), latest);
            }
        }
        
        return latestVersions;
    }
    
    /**
     * Check if a map version exists
     */
    public boolean versionExists(String mapId, MapVersion version) {
        String versionKey = mapId + ":" + version.toString();
        return versionedMaps.containsKey(versionKey);
    }
    
    /**
     * Get version change log
     */
    public List<VersionChangeEntry> getChangeLog(String mapId) {
        MapVersionHistory history = mapHistories.get(mapId);
        return history != null ? history.getChangeLog() : new ArrayList<>();
    }
    
    /**
     * Clear all version data for a map
     */
    public void clearMapVersions(String mapId) {
        MapVersionHistory history = mapHistories.remove(mapId);
        if (history != null) {
            // Remove all versioned maps
            for (MapVersion version : history.getAllVersions()) {
                String versionKey = mapId + ":" + version.toString();
                versionedMaps.remove(versionKey);
            }
            
            logManager.info("MapVersionManager", "Cleared all versions for map", "mapId", mapId);
        }
    }
    
    /**
     * Get system statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMaps", mapHistories.size());
        stats.put("totalVersions", versionedMaps.size());
        stats.put("maxVersionsPerMap", maxVersionsPerMap);
        stats.put("autoBackupEnabled", autoBackup);
        
        // Version distribution
        Map<String, Integer> versionCounts = new HashMap<>();
        for (MapVersionHistory history : mapHistories.values()) {
            versionCounts.put(history.getMapId(), history.getVersionCount());
        }
        stats.put("versionsByMap", versionCounts);
        
        return stats;
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        mapHistories.clear();
        versionedMaps.clear();
        
        logManager.info("MapVersionManager", "Map version manager cleaned up");
    }
    
    // Getters
    public int getMaxVersionsPerMap() { return maxVersionsPerMap; }
    public boolean isAutoBackupEnabled() { return autoBackup; }
    public String getBackupDirectory() { return backupDirectory; }
    
    /**
     * Enum for version change types
     */
    public enum VersionChangeType {
        PATCH,  // Bug fixes, small changes
        MINOR,  // New features, enhancements
        MAJOR   // Breaking changes, major redesigns
    }
}