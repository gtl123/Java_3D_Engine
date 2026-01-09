package engine.plugins;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Dynamic plugin loading system with classloader isolation.
 * Handles loading plugins from JAR files, managing classloaders, and resolving dependencies.
 */
public interface PluginLoader {
    
    /**
     * Load a plugin from a JAR file asynchronously.
     * @param pluginPath Path to the plugin JAR file
     * @return CompletableFuture that completes with the loaded plugin
     */
    CompletableFuture<Plugin> loadPluginAsync(Path pluginPath);
    
    /**
     * Load a plugin from a JAR file synchronously.
     * @param pluginPath Path to the plugin JAR file
     * @return Loaded plugin instance
     * @throws PluginException if loading fails
     */
    Plugin loadPlugin(Path pluginPath) throws PluginException;
    
    /**
     * Load plugin metadata without loading the full plugin.
     * @param pluginPath Path to the plugin JAR file
     * @return Plugin metadata
     * @throws PluginException if metadata loading fails
     */
    PluginMetadata loadPluginMetadata(Path pluginPath) throws PluginException;
    
    /**
     * Unload a plugin and clean up its classloader.
     * @param plugin Plugin to unload
     * @throws PluginException if unloading fails
     */
    void unloadPlugin(Plugin plugin) throws PluginException;
    
    /**
     * Create an isolated classloader for a plugin.
     * @param pluginPath Path to the plugin JAR file
     * @param parentClassLoader Parent classloader
     * @return Isolated plugin classloader
     * @throws PluginException if classloader creation fails
     */
    PluginClassLoader createPluginClassLoader(Path pluginPath, ClassLoader parentClassLoader) throws PluginException;
    
    /**
     * Validate a plugin JAR file.
     * @param pluginPath Path to the plugin JAR file
     * @return Validation result
     */
    PluginValidationResult validatePlugin(Path pluginPath);
    
    /**
     * Discover plugins in a directory.
     * @param directory Directory to scan for plugins
     * @return List of discovered plugin paths
     */
    List<Path> discoverPlugins(Path directory);
    
    /**
     * Check if a file is a valid plugin JAR.
     * @param filePath Path to check
     * @return true if file is a valid plugin JAR
     */
    boolean isValidPluginJar(Path filePath);
    
    /**
     * Get the classloader for a specific plugin.
     * @param pluginId Plugin ID
     * @return Plugin classloader or null if not found
     */
    PluginClassLoader getPluginClassLoader(String pluginId);
    
    /**
     * Get loader statistics.
     * @return Loader statistics
     */
    PluginLoaderStatistics getStatistics();
    
    /**
     * Set the parent classloader for plugin isolation.
     * @param parentClassLoader Parent classloader
     */
    void setParentClassLoader(ClassLoader parentClassLoader);
    
    /**
     * Enable or disable plugin signature verification.
     * @param enabled Whether to verify plugin signatures
     */
    void setSignatureVerificationEnabled(boolean enabled);
    
    /**
     * Add a trusted certificate for plugin signature verification.
     * @param certificatePath Path to the certificate file
     * @throws PluginException if certificate loading fails
     */
    void addTrustedCertificate(Path certificatePath) throws PluginException;
    
    /**
     * Plugin classloader with isolation and security features.
     */
    interface PluginClassLoader extends AutoCloseable {
        
        /**
         * Get the plugin ID this classloader belongs to.
         * @return Plugin ID
         */
        String getPluginId();
        
        /**
         * Get the plugin JAR path.
         * @return JAR file path
         */
        Path getPluginPath();
        
        /**
         * Load a class from the plugin.
         * @param className Class name to load
         * @return Loaded class
         * @throws ClassNotFoundException if class not found
         */
        Class<?> loadPluginClass(String className) throws ClassNotFoundException;
        
        /**
         * Get a resource from the plugin.
         * @param resourceName Resource name
         * @return Resource URL or null if not found
         */
        URL getPluginResource(String resourceName);
        
        /**
         * Check if this classloader is isolated.
         * @return true if isolated from other plugins
         */
        boolean isIsolated();
        
        /**
         * Get the parent classloader.
         * @return Parent classloader
         */
        ClassLoader getParent();
        
        /**
         * Add a dependency classloader.
         * @param dependencyClassLoader Dependency classloader
         */
        void addDependency(PluginClassLoader dependencyClassLoader);
        
        /**
         * Remove a dependency classloader.
         * @param dependencyClassLoader Dependency classloader to remove
         */
        void removeDependency(PluginClassLoader dependencyClassLoader);
        
        /**
         * Get all dependency classloaders.
         * @return List of dependency classloaders
         */
        List<PluginClassLoader> getDependencies();
        
        /**
         * Close the classloader and release resources.
         */
        @Override
        void close();
    }
    
    /**
     * Plugin validation result.
     */
    class PluginValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        private final PluginMetadata metadata;
        
        public PluginValidationResult(boolean valid, List<String> errors, 
                                    List<String> warnings, PluginMetadata metadata) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
            this.metadata = metadata;
        }
        
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public PluginMetadata getMetadata() { return metadata; }
        
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        
        @Override
        public String toString() {
            return String.format("PluginValidation{valid=%s, errors=%d, warnings=%d}", 
                               valid, errors.size(), warnings.size());
        }
    }
    
    /**
     * Plugin loader statistics.
     */
    class PluginLoaderStatistics {
        private final int totalPluginsLoaded;
        private final int totalPluginsUnloaded;
        private final int activeClassLoaders;
        private final long totalLoadTime;
        private final int loadFailures;
        private final int signatureVerifications;
        
        public PluginLoaderStatistics(int totalPluginsLoaded, int totalPluginsUnloaded, 
                                    int activeClassLoaders, long totalLoadTime, 
                                    int loadFailures, int signatureVerifications) {
            this.totalPluginsLoaded = totalPluginsLoaded;
            this.totalPluginsUnloaded = totalPluginsUnloaded;
            this.activeClassLoaders = activeClassLoaders;
            this.totalLoadTime = totalLoadTime;
            this.loadFailures = loadFailures;
            this.signatureVerifications = signatureVerifications;
        }
        
        public int getTotalPluginsLoaded() { return totalPluginsLoaded; }
        public int getTotalPluginsUnloaded() { return totalPluginsUnloaded; }
        public int getActiveClassLoaders() { return activeClassLoaders; }
        public long getTotalLoadTime() { return totalLoadTime; }
        public int getLoadFailures() { return loadFailures; }
        public int getSignatureVerifications() { return signatureVerifications; }
        
        public double getAverageLoadTime() {
            return totalPluginsLoaded > 0 ? (double) totalLoadTime / totalPluginsLoaded : 0.0;
        }
        
        public double getSuccessRate() {
            int totalAttempts = totalPluginsLoaded + loadFailures;
            return totalAttempts > 0 ? (double) totalPluginsLoaded / totalAttempts : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("LoaderStats{loaded=%d, unloaded=%d, active=%d, failures=%d, avgTime=%.2fms}",
                               totalPluginsLoaded, totalPluginsUnloaded, activeClassLoaders, 
                               loadFailures, getAverageLoadTime());
        }
    }
}