package engine.plugins;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Support system for user-generated content and modifications.
 * Provides tools for mod discovery, validation, installation, and management.
 */
public interface ModSupport {
    
    /**
     * Initialize the mod support system.
     * @throws PluginException if initialization fails
     */
    void initialize() throws PluginException;
    
    /**
     * Shutdown the mod support system.
     */
    void shutdown();
    
    /**
     * Discover mods in the specified directory.
     * @param modDirectory Directory to scan for mods
     * @return List of discovered mod packages
     */
    List<ModPackage> discoverMods(Path modDirectory);
    
    /**
     * Install a mod from a package file.
     * @param modPackagePath Path to the mod package file
     * @return CompletableFuture that completes when installation is done
     */
    CompletableFuture<ModInstallationResult> installMod(Path modPackagePath);
    
    /**
     * Uninstall a mod by ID.
     * @param modId Mod ID to uninstall
     * @return CompletableFuture that completes when uninstallation is done
     */
    CompletableFuture<Boolean> uninstallMod(String modId);
    
    /**
     * Enable a mod.
     * @param modId Mod ID to enable
     * @return true if mod was enabled successfully
     */
    boolean enableMod(String modId);
    
    /**
     * Disable a mod.
     * @param modId Mod ID to disable
     * @return true if mod was disabled successfully
     */
    boolean disableMod(String modId);
    
    /**
     * Validate a mod package before installation.
     * @param modPackagePath Path to the mod package
     * @return Validation result
     */
    ModValidationResult validateMod(Path modPackagePath);
    
    /**
     * Get all installed mods.
     * @return List of installed mods
     */
    List<ModPackage> getInstalledMods();
    
    /**
     * Get enabled mods.
     * @return List of enabled mods
     */
    List<ModPackage> getEnabledMods();
    
    /**
     * Get a mod by ID.
     * @param modId Mod ID
     * @return Mod package or null if not found
     */
    ModPackage getMod(String modId);
    
    /**
     * Check if a mod is installed.
     * @param modId Mod ID to check
     * @return true if mod is installed
     */
    boolean isModInstalled(String modId);
    
    /**
     * Check if a mod is enabled.
     * @param modId Mod ID to check
     * @return true if mod is enabled
     */
    boolean isModEnabled(String modId);
    
    /**
     * Update a mod to a newer version.
     * @param modId Mod ID to update
     * @param newPackagePath Path to the new mod package
     * @return CompletableFuture that completes when update is done
     */
    CompletableFuture<ModInstallationResult> updateMod(String modId, Path newPackagePath);
    
    /**
     * Check for mod updates from repositories.
     * @return List of available updates
     */
    List<ModUpdate> checkForUpdates();
    
    /**
     * Create a mod package from a directory.
     * @param modDirectory Directory containing mod files
     * @param outputPath Output path for the package
     * @param metadata Mod metadata
     * @return Created mod package
     * @throws PluginException if packaging fails
     */
    ModPackage createModPackage(Path modDirectory, Path outputPath, ModMetadata metadata) throws PluginException;
    
    /**
     * Get mod dependencies and conflicts.
     * @param modId Mod ID
     * @return Dependency information
     */
    ModDependencyInfo getModDependencies(String modId);
    
    /**
     * Resolve mod load order based on dependencies.
     * @param modIds Mod IDs to resolve order for
     * @return Ordered list of mod IDs
     * @throws PluginException if circular dependencies are detected
     */
    List<String> resolveModLoadOrder(List<String> modIds) throws PluginException;
    
    /**
     * Add a mod repository for updates and downloads.
     * @param repository Mod repository
     */
    void addModRepository(ModRepository repository);
    
    /**
     * Remove a mod repository.
     * @param repository Mod repository to remove
     */
    void removeModRepository(ModRepository repository);
    
    /**
     * Search for mods in repositories.
     * @param query Search query
     * @return List of matching mods
     */
    List<ModSearchResult> searchMods(String query);
    
    /**
     * Download and install a mod from a repository.
     * @param modId Mod ID to download
     * @param repositoryId Repository ID
     * @return CompletableFuture that completes when installation is done
     */
    CompletableFuture<ModInstallationResult> downloadAndInstallMod(String modId, String repositoryId);
    
    /**
     * Get mod support statistics.
     * @return Mod support statistics
     */
    ModSupportStatistics getStatistics();
    
    /**
     * Add a mod support listener.
     * @param listener Listener to add
     */
    void addModSupportListener(ModSupportListener listener);
    
    /**
     * Remove a mod support listener.
     * @param listener Listener to remove
     */
    void removeModSupportListener(ModSupportListener listener);
    
    /**
     * Mod package information.
     */
    class ModPackage {
        private final String id;
        private final ModMetadata metadata;
        private final Path packagePath;
        private final Path installPath;
        private final boolean enabled;
        private final ModStatus status;
        
        public ModPackage(String id, ModMetadata metadata, Path packagePath, Path installPath, 
                         boolean enabled, ModStatus status) {
            this.id = id;
            this.metadata = metadata;
            this.packagePath = packagePath;
            this.installPath = installPath;
            this.enabled = enabled;
            this.status = status;
        }
        
        public String getId() { return id; }
        public ModMetadata getMetadata() { return metadata; }
        public Path getPackagePath() { return packagePath; }
        public Path getInstallPath() { return installPath; }
        public boolean isEnabled() { return enabled; }
        public ModStatus getStatus() { return status; }
        
        @Override
        public String toString() {
            return String.format("ModPackage{id='%s', name='%s', version='%s', enabled=%s}", 
                               id, metadata.getName(), metadata.getVersion(), enabled);
        }
    }
    
    /**
     * Mod metadata information.
     */
    class ModMetadata {
        private final String name;
        private final String version;
        private final String description;
        private final String author;
        private final String website;
        private final String license;
        private final List<String> dependencies;
        private final List<String> conflicts;
        private final String minEngineVersion;
        private final String maxEngineVersion;
        private final Map<String, Object> customProperties;
        
        public ModMetadata(String name, String version, String description, String author, String website,
                          String license, List<String> dependencies, List<String> conflicts,
                          String minEngineVersion, String maxEngineVersion, Map<String, Object> customProperties) {
            this.name = name;
            this.version = version;
            this.description = description;
            this.author = author;
            this.website = website;
            this.license = license;
            this.dependencies = dependencies;
            this.conflicts = conflicts;
            this.minEngineVersion = minEngineVersion;
            this.maxEngineVersion = maxEngineVersion;
            this.customProperties = customProperties;
        }
        
        public String getName() { return name; }
        public String getVersion() { return version; }
        public String getDescription() { return description; }
        public String getAuthor() { return author; }
        public String getWebsite() { return website; }
        public String getLicense() { return license; }
        public List<String> getDependencies() { return dependencies; }
        public List<String> getConflicts() { return conflicts; }
        public String getMinEngineVersion() { return minEngineVersion; }
        public String getMaxEngineVersion() { return maxEngineVersion; }
        public Map<String, Object> getCustomProperties() { return customProperties; }
        
        @SuppressWarnings("unchecked")
        public <T> T getProperty(String key, T defaultValue) {
            Object value = customProperties.get(key);
            return value != null ? (T) value : defaultValue;
        }
    }
    
    /**
     * Mod status enumeration.
     */
    enum ModStatus {
        INSTALLED, ENABLED, DISABLED, ERROR, UPDATING, UNINSTALLING
    }
    
    /**
     * Mod validation result.
     */
    class ModValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        private final ModMetadata metadata;
        
        public ModValidationResult(boolean valid, List<String> errors, List<String> warnings, ModMetadata metadata) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
            this.metadata = metadata;
        }
        
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public ModMetadata getMetadata() { return metadata; }
        
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }
    
    /**
     * Mod installation result.
     */
    class ModInstallationResult {
        private final boolean success;
        private final String modId;
        private final String message;
        private final Throwable error;
        
        public ModInstallationResult(boolean success, String modId, String message, Throwable error) {
            this.success = success;
            this.modId = modId;
            this.message = message;
            this.error = error;
        }
        
        public boolean isSuccess() { return success; }
        public String getModId() { return modId; }
        public String getMessage() { return message; }
        public Throwable getError() { return error; }
        
        public static ModInstallationResult success(String modId, String message) {
            return new ModInstallationResult(true, modId, message, null);
        }
        
        public static ModInstallationResult failure(String modId, String message, Throwable error) {
            return new ModInstallationResult(false, modId, message, error);
        }
    }
    
    /**
     * Mod update information.
     */
    class ModUpdate {
        private final String modId;
        private final String currentVersion;
        private final String availableVersion;
        private final String updateDescription;
        private final String downloadUrl;
        
        public ModUpdate(String modId, String currentVersion, String availableVersion, 
                        String updateDescription, String downloadUrl) {
            this.modId = modId;
            this.currentVersion = currentVersion;
            this.availableVersion = availableVersion;
            this.updateDescription = updateDescription;
            this.downloadUrl = downloadUrl;
        }
        
        public String getModId() { return modId; }
        public String getCurrentVersion() { return currentVersion; }
        public String getAvailableVersion() { return availableVersion; }
        public String getUpdateDescription() { return updateDescription; }
        public String getDownloadUrl() { return downloadUrl; }
    }
    
    /**
     * Mod dependency information.
     */
    class ModDependencyInfo {
        private final List<String> requiredDependencies;
        private final List<String> optionalDependencies;
        private final List<String> conflicts;
        private final List<String> missingDependencies;
        private final List<String> conflictingMods;
        
        public ModDependencyInfo(List<String> requiredDependencies, List<String> optionalDependencies,
                               List<String> conflicts, List<String> missingDependencies, List<String> conflictingMods) {
            this.requiredDependencies = requiredDependencies;
            this.optionalDependencies = optionalDependencies;
            this.conflicts = conflicts;
            this.missingDependencies = missingDependencies;
            this.conflictingMods = conflictingMods;
        }
        
        public List<String> getRequiredDependencies() { return requiredDependencies; }
        public List<String> getOptionalDependencies() { return optionalDependencies; }
        public List<String> getConflicts() { return conflicts; }
        public List<String> getMissingDependencies() { return missingDependencies; }
        public List<String> getConflictingMods() { return conflictingMods; }
        
        public boolean hasMissingDependencies() { return !missingDependencies.isEmpty(); }
        public boolean hasConflicts() { return !conflictingMods.isEmpty(); }
        public boolean isValid() { return !hasMissingDependencies() && !hasConflicts(); }
    }
    
    /**
     * Mod repository interface.
     */
    interface ModRepository {
        /**
         * Get the repository ID.
         * @return Repository ID
         */
        String getId();
        
        /**
         * Get the repository name.
         * @return Repository name
         */
        String getName();
        
        /**
         * Get the repository URL.
         * @return Repository URL
         */
        String getUrl();
        
        /**
         * Search for mods in this repository.
         * @param query Search query
         * @return List of search results
         */
        List<ModSearchResult> search(String query);
        
        /**
         * Get mod information.
         * @param modId Mod ID
         * @return Mod information or null if not found
         */
        ModSearchResult getModInfo(String modId);
        
        /**
         * Download a mod package.
         * @param modId Mod ID
         * @param version Mod version
         * @return Path to downloaded package
         * @throws PluginException if download fails
         */
        Path downloadMod(String modId, String version) throws PluginException;
    }
    
    /**
     * Mod search result.
     */
    class ModSearchResult {
        private final String id;
        private final String name;
        private final String version;
        private final String description;
        private final String author;
        private final String downloadUrl;
        private final long downloadCount;
        private final float rating;
        
        public ModSearchResult(String id, String name, String version, String description, String author,
                             String downloadUrl, long downloadCount, float rating) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.description = description;
            this.author = author;
            this.downloadUrl = downloadUrl;
            this.downloadCount = downloadCount;
            this.rating = rating;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getVersion() { return version; }
        public String getDescription() { return description; }
        public String getAuthor() { return author; }
        public String getDownloadUrl() { return downloadUrl; }
        public long getDownloadCount() { return downloadCount; }
        public float getRating() { return rating; }
    }
    
    /**
     * Mod support statistics.
     */
    class ModSupportStatistics {
        private final int totalMods;
        private final int enabledMods;
        private final int disabledMods;
        private final int errorMods;
        private final long totalDownloads;
        private final int repositories;
        
        public ModSupportStatistics(int totalMods, int enabledMods, int disabledMods, int errorMods,
                                  long totalDownloads, int repositories) {
            this.totalMods = totalMods;
            this.enabledMods = enabledMods;
            this.disabledMods = disabledMods;
            this.errorMods = errorMods;
            this.totalDownloads = totalDownloads;
            this.repositories = repositories;
        }
        
        public int getTotalMods() { return totalMods; }
        public int getEnabledMods() { return enabledMods; }
        public int getDisabledMods() { return disabledMods; }
        public int getErrorMods() { return errorMods; }
        public long getTotalDownloads() { return totalDownloads; }
        public int getRepositories() { return repositories; }
        
        @Override
        public String toString() {
            return String.format("ModStats{total=%d, enabled=%d, disabled=%d, errors=%d, downloads=%d, repos=%d}",
                               totalMods, enabledMods, disabledMods, errorMods, totalDownloads, repositories);
        }
    }
    
    /**
     * Mod support listener interface.
     */
    interface ModSupportListener {
        /**
         * Called when a mod is installed.
         * @param modPackage Installed mod package
         */
        default void onModInstalled(ModPackage modPackage) {}
        
        /**
         * Called when a mod is uninstalled.
         * @param modId Uninstalled mod ID
         */
        default void onModUninstalled(String modId) {}
        
        /**
         * Called when a mod is enabled.
         * @param modPackage Enabled mod package
         */
        default void onModEnabled(ModPackage modPackage) {}
        
        /**
         * Called when a mod is disabled.
         * @param modPackage Disabled mod package
         */
        default void onModDisabled(ModPackage modPackage) {}
        
        /**
         * Called when a mod update is available.
         * @param modUpdate Available update
         */
        default void onModUpdateAvailable(ModUpdate modUpdate) {}
        
        /**
         * Called when a mod encounters an error.
         * @param modId Mod ID with error
         * @param error Error that occurred
         */
        default void onModError(String modId, Throwable error) {}
    }
}