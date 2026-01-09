package engine.plugins;

import engine.logging.LogManager;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of ModSupport for user-generated content management.
 * Note: This is a simplified implementation for demonstration purposes.
 */
public class DefaultModSupport implements ModSupport {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final ConcurrentHashMap<String, ModPackage> installedMods = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ModPackage> enabledMods = new ConcurrentHashMap<>();
    private final List<ModSupportListener> listeners = new CopyOnWriteArrayList<>();
    private final List<ModRepository> repositories = new CopyOnWriteArrayList<>();
    
    private volatile boolean initialized = false;
    
    // Statistics
    private long totalDownloads = 0;
    
    public void initialize() throws PluginException {
        if (initialized) {
            return;
        }
        
        logManager.info("ModSupport", "Initializing mod support system");
        
        // Add default local repository
        repositories.add(new LocalModRepository());
        
        initialized = true;
        logManager.info("ModSupport", "Mod support system initialized");
    }
    
    public void shutdown() {
        if (!initialized) {
            return;
        }
        
        logManager.info("ModSupport", "Shutting down mod support system");
        installedMods.clear();
        enabledMods.clear();
        listeners.clear();
        repositories.clear();
        initialized = false;
        logManager.info("ModSupport", "Mod support system shutdown complete");
    }
    
    @Override
    public List<ModPackage> discoverMods(Path modDirectory) {
        List<ModPackage> discoveredMods = new ArrayList<>();
        
        if (!initialized) {
            return discoveredMods;
        }
        
        logManager.info("ModSupport", "Discovering mods", "directory", modDirectory.toString());
        
        // For demonstration, return empty list
        // In a real implementation, this would scan the directory for mod files
        
        return discoveredMods;
    }
    
    @Override
    public CompletableFuture<ModInstallationResult> installMod(Path modPackagePath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate mod package
                ModValidationResult validation = validateMod(modPackagePath);
                if (!validation.isValid()) {
                    return ModInstallationResult.failure("unknown", 
                            "Validation failed: " + validation.getErrors(), null);
                }
                
                ModMetadata metadata = validation.getMetadata();
                String modId = metadata.getName().toLowerCase().replace(" ", "-");
                
                // Create mod package
                ModPackage modPackage = new ModPackage(
                        modId,
                        metadata,
                        modPackagePath,
                        modPackagePath.getParent().resolve(modId),
                        false,
                        ModStatus.INSTALLED
                );
                
                installedMods.put(modId, modPackage);
                
                logManager.info("ModSupport", "Mod installed successfully",
                               "modId", modId,
                               "name", metadata.getName());
                
                // Notify listeners
                listeners.forEach(l -> l.onModInstalled(modPackage));
                
                return ModInstallationResult.success(modId, "Mod installed successfully");
                
            } catch (Exception e) {
                logManager.error("ModSupport", "Failed to install mod", e,
                               "path", modPackagePath.toString());
                return ModInstallationResult.failure("unknown", 
                        "Installation failed: " + e.getMessage(), e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> uninstallMod(String modId) {
        return CompletableFuture.supplyAsync(() -> {
            ModPackage modPackage = installedMods.remove(modId);
            if (modPackage != null) {
                enabledMods.remove(modId);
                
                logManager.info("ModSupport", "Mod uninstalled", "modId", modId);
                
                // Notify listeners
                listeners.forEach(l -> l.onModUninstalled(modId));
                
                return true;
            }
            return false;
        });
    }
    
    @Override
    public boolean enableMod(String modId) {
        ModPackage modPackage = installedMods.get(modId);
        if (modPackage != null && !enabledMods.containsKey(modId)) {
            // Create enabled version
            ModPackage enabledPackage = new ModPackage(
                    modPackage.getId(),
                    modPackage.getMetadata(),
                    modPackage.getPackagePath(),
                    modPackage.getInstallPath(),
                    true,
                    ModStatus.ENABLED
            );
            
            enabledMods.put(modId, enabledPackage);
            installedMods.put(modId, enabledPackage);
            
            logManager.info("ModSupport", "Mod enabled", "modId", modId);
            
            // Notify listeners
            listeners.forEach(l -> l.onModEnabled(enabledPackage));
            
            return true;
        }
        return false;
    }
    
    @Override
    public boolean disableMod(String modId) {
        ModPackage modPackage = enabledMods.remove(modId);
        if (modPackage != null) {
            // Create disabled version
            ModPackage disabledPackage = new ModPackage(
                    modPackage.getId(),
                    modPackage.getMetadata(),
                    modPackage.getPackagePath(),
                    modPackage.getInstallPath(),
                    false,
                    ModStatus.DISABLED
            );
            
            installedMods.put(modId, disabledPackage);
            
            logManager.info("ModSupport", "Mod disabled", "modId", modId);
            
            // Notify listeners
            listeners.forEach(l -> l.onModDisabled(disabledPackage));
            
            return true;
        }
        return false;
    }
    
    @Override
    public ModValidationResult validateMod(Path modPackagePath) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Basic validation
        if (!java.nio.file.Files.exists(modPackagePath)) {
            errors.add("Mod package file does not exist");
        }
        
        // Create dummy metadata for demonstration
        ModMetadata metadata = new ModMetadata(
                "Example Mod",
                "1.0.0",
                "Example mod description",
                "Unknown Author",
                "",
                "MIT",
                Collections.emptyList(),
                Collections.emptyList(),
                "1.0.0",
                "2.0.0",
                Collections.emptyMap()
        );
        
        boolean valid = errors.isEmpty();
        return new ModValidationResult(valid, errors, warnings, metadata);
    }
    
    @Override
    public List<ModPackage> getInstalledMods() {
        return new ArrayList<>(installedMods.values());
    }
    
    @Override
    public List<ModPackage> getEnabledMods() {
        return new ArrayList<>(enabledMods.values());
    }
    
    @Override
    public ModPackage getMod(String modId) {
        return installedMods.get(modId);
    }
    
    @Override
    public boolean isModInstalled(String modId) {
        return installedMods.containsKey(modId);
    }
    
    @Override
    public boolean isModEnabled(String modId) {
        return enabledMods.containsKey(modId);
    }
    
    @Override
    public CompletableFuture<ModInstallationResult> updateMod(String modId, Path newPackagePath) {
        return CompletableFuture.supplyAsync(() -> {
            // Uninstall old version and install new version
            uninstallMod(modId).join();
            return installMod(newPackagePath).join();
        });
    }
    
    @Override
    public List<ModUpdate> checkForUpdates() {
        // For demonstration, return empty list
        return new ArrayList<>();
    }
    
    @Override
    public ModPackage createModPackage(Path modDirectory, Path outputPath, ModMetadata metadata) throws PluginException {
        // For demonstration, create a basic mod package
        String modId = metadata.getName().toLowerCase().replace(" ", "-");
        
        ModPackage modPackage = new ModPackage(
                modId,
                metadata,
                outputPath,
                modDirectory,
                false,
                ModStatus.INSTALLED
        );
        
        logManager.info("ModSupport", "Mod package created",
                       "modId", modId,
                       "outputPath", outputPath.toString());
        
        return modPackage;
    }
    
    @Override
    public ModDependencyInfo getModDependencies(String modId) {
        ModPackage modPackage = installedMods.get(modId);
        if (modPackage == null) {
            return new ModDependencyInfo(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }
        
        ModMetadata metadata = modPackage.getMetadata();
        return new ModDependencyInfo(
                metadata.getDependencies(),
                Collections.emptyList(), // Optional dependencies not implemented
                metadata.getConflicts(),
                Collections.emptyList(), // Missing dependencies check not implemented
                Collections.emptyList()  // Conflicting mods check not implemented
        );
    }
    
    @Override
    public List<String> resolveModLoadOrder(List<String> modIds) throws PluginException {
        // Simple implementation - return as-is
        // In a real implementation, this would perform topological sorting
        return new ArrayList<>(modIds);
    }
    
    @Override
    public void addModRepository(ModRepository repository) {
        repositories.add(repository);
        logManager.info("ModSupport", "Mod repository added",
                       "repositoryId", repository.getId(),
                       "name", repository.getName());
    }
    
    @Override
    public void removeModRepository(ModRepository repository) {
        repositories.remove(repository);
        logManager.info("ModSupport", "Mod repository removed",
                       "repositoryId", repository.getId());
    }
    
    @Override
    public List<ModSearchResult> searchMods(String query) {
        List<ModSearchResult> results = new ArrayList<>();
        
        for (ModRepository repository : repositories) {
            results.addAll(repository.search(query));
        }
        
        return results;
    }
    
    @Override
    public CompletableFuture<ModInstallationResult> downloadAndInstallMod(String modId, String repositoryId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ModRepository repository = repositories.stream()
                        .filter(repo -> repo.getId().equals(repositoryId))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Repository not found: " + repositoryId));
                
                ModSearchResult modInfo = repository.getModInfo(modId);
                if (modInfo == null) {
                    return ModInstallationResult.failure(modId, "Mod not found in repository", null);
                }
                
                Path downloadedPath = repository.downloadMod(modId, modInfo.getVersion());
                totalDownloads++;
                
                return installMod(downloadedPath).join();
                
            } catch (Exception e) {
                return ModInstallationResult.failure(modId, "Download failed: " + e.getMessage(), e);
            }
        });
    }
    
    @Override
    public ModSupportStatistics getStatistics() {
        return new ModSupportStatistics(
                installedMods.size(),
                enabledMods.size(),
                installedMods.size() - enabledMods.size(),
                0, // Error mods not tracked in this implementation
                totalDownloads,
                repositories.size()
        );
    }
    
    @Override
    public void addModSupportListener(ModSupportListener listener) {
        listeners.add(listener);
    }
    
    @Override
    public void removeModSupportListener(ModSupportListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Basic local mod repository implementation.
     */
    private static class LocalModRepository implements ModRepository {
        
        @Override
        public String getId() {
            return "local";
        }
        
        @Override
        public String getName() {
            return "Local Repository";
        }
        
        @Override
        public String getUrl() {
            return "file://local";
        }
        
        @Override
        public List<ModSearchResult> search(String query) {
            // Return empty results for local repository
            return new ArrayList<>();
        }
        
        @Override
        public ModSearchResult getModInfo(String modId) {
            return null; // No mods in local repository
        }
        
        @Override
        public Path downloadMod(String modId, String version) throws PluginException {
            throw new PluginException("Local repository does not support downloads");
        }
    }
}