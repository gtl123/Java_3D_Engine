package engine.plugins;

import engine.logging.LogManager;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Default implementation of PluginLoader with basic classloader isolation.
 * Note: This is a simplified implementation for demonstration purposes.
 */
public class DefaultPluginLoader implements PluginLoader {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final ConcurrentHashMap<String, PluginClassLoaderImpl> classLoaders = new ConcurrentHashMap<>();
    private final AtomicLong totalPluginsLoaded = new AtomicLong(0);
    private final AtomicLong totalPluginsUnloaded = new AtomicLong(0);
    private final AtomicLong totalLoadTime = new AtomicLong(0);
    private final AtomicLong loadFailures = new AtomicLong(0);
    
    private ClassLoader parentClassLoader = getClass().getClassLoader();
    private boolean signatureVerificationEnabled = false;
    private volatile boolean initialized = false;
    
    public void initialize() {
        if (initialized) {
            return;
        }
        
        logManager.info("PluginLoader", "Initializing plugin loader");
        initialized = true;
        logManager.info("PluginLoader", "Plugin loader initialized");
    }
    
    public void shutdown() {
        if (!initialized) {
            return;
        }
        
        logManager.info("PluginLoader", "Shutting down plugin loader");
        
        // Close all classloaders
        for (PluginClassLoaderImpl classLoader : classLoaders.values()) {
            try {
                classLoader.close();
            } catch (Exception e) {
                logManager.warn("PluginLoader", "Error closing classloader", 
                               "pluginId", classLoader.getPluginId());
            }
        }
        
        classLoaders.clear();
        initialized = false;
        logManager.info("PluginLoader", "Plugin loader shutdown complete");
    }
    
    @Override
    public CompletableFuture<Plugin> loadPluginAsync(Path pluginPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadPlugin(pluginPath);
            } catch (PluginException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    @Override
    public Plugin loadPlugin(Path pluginPath) throws PluginException {
        if (!initialized) {
            throw new PluginException("Plugin loader not initialized");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate plugin file
            PluginValidationResult validation = validatePlugin(pluginPath);
            if (!validation.isValid()) {
                loadFailures.incrementAndGet();
                throw new PluginException("Plugin validation failed: " + validation.getErrors());
            }
            
            // Load metadata first
            PluginMetadata metadata = loadPluginMetadata(pluginPath);
            String pluginId = metadata.getId();
            
            // Create isolated classloader
            PluginClassLoaderImpl classLoader = createPluginClassLoader(pluginPath, parentClassLoader);
            classLoaders.put(pluginId, classLoader);
            
            // Load main plugin class
            Class<?> pluginClass = classLoader.loadPluginClass(metadata.getMainClass());
            
            // Create plugin instance
            Plugin plugin = (Plugin) pluginClass.getDeclaredConstructor().newInstance();
            
            long loadTime = System.currentTimeMillis() - startTime;
            totalLoadTime.addAndGet(loadTime);
            totalPluginsLoaded.incrementAndGet();
            
            logManager.info("PluginLoader", "Plugin loaded successfully",
                           "pluginId", pluginId,
                           "loadTime", loadTime + "ms");
            
            return plugin;
            
        } catch (Exception e) {
            loadFailures.incrementAndGet();
            logManager.error("PluginLoader", "Failed to load plugin", e,
                           "path", pluginPath.toString());
            throw new PluginException("Failed to load plugin: " + pluginPath, e);
        }
    }
    
    @Override
    public PluginMetadata loadPluginMetadata(Path pluginPath) throws PluginException {
        try {
            // For demonstration, create basic metadata from JAR manifest
            // In a real implementation, this would read from plugin.json or similar
            try (JarFile jarFile = new JarFile(pluginPath.toFile())) {
                Manifest manifest = jarFile.getManifest();
                
                if (manifest == null) {
                    throw new PluginException("No manifest found in plugin JAR");
                }
                
                String pluginId = manifest.getMainAttributes().getValue("Plugin-Id");
                String pluginName = manifest.getMainAttributes().getValue("Plugin-Name");
                String pluginVersion = manifest.getMainAttributes().getValue("Plugin-Version");
                String mainClass = manifest.getMainAttributes().getValue("Main-Class");
                
                if (pluginId == null || pluginName == null || pluginVersion == null || mainClass == null) {
                    throw new PluginException("Missing required plugin metadata in manifest");
                }
                
                return PluginMetadata.builder()
                        .id(pluginId)
                        .name(pluginName)
                        .version(pluginVersion)
                        .mainClass(mainClass)
                        .description(manifest.getMainAttributes().getValue("Plugin-Description"))
                        .author(manifest.getMainAttributes().getValue("Plugin-Author"))
                        .setProperty("pluginPath", pluginPath.toString())
                        .build();
            }
        } catch (Exception e) {
            throw new PluginException("Failed to load plugin metadata: " + pluginPath, e);
        }
    }
    
    @Override
    public void unloadPlugin(Plugin plugin) throws PluginException {
        String pluginId = plugin.getMetadata().getId();
        PluginClassLoaderImpl classLoader = classLoaders.remove(pluginId);
        
        if (classLoader != null) {
            try {
                classLoader.close();
                totalPluginsUnloaded.incrementAndGet();
                
                logManager.info("PluginLoader", "Plugin unloaded",
                               "pluginId", pluginId);
            } catch (Exception e) {
                throw new PluginException("Failed to unload plugin: " + pluginId, e);
            }
        }
    }
    
    @Override
    public PluginClassLoaderImpl createPluginClassLoader(Path pluginPath, ClassLoader parentClassLoader) throws PluginException {
        try {
            URL[] urls = {pluginPath.toUri().toURL()};
            return new PluginClassLoaderImpl("unknown", pluginPath, urls, parentClassLoader);
        } catch (Exception e) {
            throw new PluginException("Failed to create plugin classloader", e);
        }
    }
    
    @Override
    public PluginValidationResult validatePlugin(Path pluginPath) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Basic validation
        if (!Files.exists(pluginPath)) {
            errors.add("Plugin file does not exist: " + pluginPath);
        } else if (!pluginPath.toString().endsWith(".jar")) {
            errors.add("Plugin file must be a JAR file: " + pluginPath);
        }
        
        // Try to load metadata for validation
        PluginMetadata metadata = null;
        try {
            metadata = loadPluginMetadata(pluginPath);
        } catch (PluginException e) {
            errors.add("Failed to load plugin metadata: " + e.getMessage());
        }
        
        boolean valid = errors.isEmpty();
        return new PluginValidationResult(valid, errors, warnings, metadata);
    }
    
    @Override
    public List<Path> discoverPlugins(Path directory) {
        List<Path> plugins = new ArrayList<>();
        
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return plugins;
        }
        
        try {
            Files.list(directory)
                    .filter(path -> path.toString().endsWith(".jar"))
                    .filter(this::isValidPluginJar)
                    .forEach(plugins::add);
        } catch (Exception e) {
            logManager.error("PluginLoader", "Error discovering plugins", e,
                           "directory", directory.toString());
        }
        
        logManager.info("PluginLoader", "Discovered plugins",
                       "directory", directory.toString(),
                       "count", plugins.size());
        
        return plugins;
    }
    
    @Override
    public boolean isValidPluginJar(Path filePath) {
        try {
            return validatePlugin(filePath).isValid();
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public PluginClassLoader getPluginClassLoader(String pluginId) {
        return classLoaders.get(pluginId);
    }
    
    @Override
    public PluginLoaderStatistics getStatistics() {
        return new PluginLoaderStatistics(
                (int) totalPluginsLoaded.get(),
                (int) totalPluginsUnloaded.get(),
                classLoaders.size(),
                totalLoadTime.get(),
                (int) loadFailures.get(),
                0 // signature verifications not implemented
        );
    }
    
    @Override
    public void setParentClassLoader(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader;
    }
    
    @Override
    public void setSignatureVerificationEnabled(boolean enabled) {
        this.signatureVerificationEnabled = enabled;
        logManager.info("PluginLoader", "Signature verification " + (enabled ? "enabled" : "disabled"));
    }
    
    @Override
    public void addTrustedCertificate(Path certificatePath) throws PluginException {
        // Not implemented in this basic version
        logManager.warn("PluginLoader", "Certificate management not implemented");
    }
    
    /**
     * Basic plugin classloader implementation.
     */
    private static class PluginClassLoaderImpl extends URLClassLoader implements PluginClassLoader {
        private final String pluginId;
        private final Path pluginPath;
        private final List<PluginClassLoader> dependencies = new ArrayList<>();
        
        public PluginClassLoaderImpl(String pluginId, Path pluginPath, URL[] urls, ClassLoader parent) {
            super(urls, parent);
            this.pluginId = pluginId;
            this.pluginPath = pluginPath;
        }
        
        @Override
        public String getPluginId() {
            return pluginId;
        }
        
        @Override
        public Path getPluginPath() {
            return pluginPath;
        }
        
        @Override
        public Class<?> loadPluginClass(String className) throws ClassNotFoundException {
            return loadClass(className);
        }
        
        @Override
        public URL getPluginResource(String resourceName) {
            return getResource(resourceName);
        }
        
        @Override
        public boolean isIsolated() {
            return true; // Basic isolation
        }
        
        @Override
        public void addDependency(PluginClassLoader dependencyClassLoader) {
            dependencies.add(dependencyClassLoader);
        }
        
        @Override
        public void removeDependency(PluginClassLoader dependencyClassLoader) {
            dependencies.remove(dependencyClassLoader);
        }
        
        @Override
        public List<PluginClassLoader> getDependencies() {
            return new ArrayList<>(dependencies);
        }
    }
}