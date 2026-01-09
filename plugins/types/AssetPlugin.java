package engine.plugins.types;

import engine.assets.Asset;
import engine.assets.AssetManager;
import engine.plugins.Plugin;
import engine.plugins.PluginException;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for plugins that extend the asset system.
 * Allows plugins to add custom asset loaders, processors, and formats.
 */
public interface AssetPlugin extends Plugin {
    
    /**
     * Initialize asset processing systems.
     * Called after the plugin is initialized.
     * @param assetManager The engine's asset manager
     * @throws PluginException if initialization fails
     */
    void initializeAssetProcessing(AssetManager assetManager) throws PluginException;
    
    /**
     * Get the file extensions supported by this plugin.
     * @return Array of supported file extensions (e.g., ".obj", ".fbx")
     */
    String[] getSupportedExtensions();
    
    /**
     * Get the asset types that this plugin can handle.
     * @return Array of asset type names
     */
    String[] getSupportedAssetTypes();
    
    /**
     * Check if this plugin can load a specific file.
     * @param filePath Path to the file
     * @return true if plugin can load the file
     */
    boolean canLoadAsset(Path filePath);
    
    /**
     * Load an asset from a file asynchronously.
     * @param assetId Asset identifier
     * @param filePath Path to the asset file
     * @param assetType Expected asset type
     * @return CompletableFuture that completes with the loaded asset
     */
    CompletableFuture<Asset> loadAssetAsync(String assetId, Path filePath, String assetType);
    
    /**
     * Load an asset from a file synchronously.
     * @param assetId Asset identifier
     * @param filePath Path to the asset file
     * @param assetType Expected asset type
     * @return Loaded asset
     * @throws PluginException if loading fails
     */
    Asset loadAsset(String assetId, Path filePath, String assetType) throws PluginException;
    
    /**
     * Process an asset after loading (e.g., optimization, conversion).
     * @param asset Asset to process
     * @return Processed asset
     * @throws PluginException if processing fails
     */
    Asset processAsset(Asset asset) throws PluginException;
    
    /**
     * Validate an asset file before loading.
     * @param filePath Path to the asset file
     * @return Validation result
     */
    AssetValidationResult validateAsset(Path filePath);
    
    /**
     * Get metadata from an asset file without fully loading it.
     * @param filePath Path to the asset file
     * @return Asset metadata
     * @throws PluginException if metadata extraction fails
     */
    AssetMetadata extractMetadata(Path filePath) throws PluginException;
    
    /**
     * Convert an asset to a different format.
     * @param asset Source asset
     * @param targetFormat Target format
     * @return Converted asset
     * @throws PluginException if conversion fails
     */
    Asset convertAsset(Asset asset, String targetFormat) throws PluginException;
    
    /**
     * Get the supported conversion formats for an asset type.
     * @param sourceFormat Source asset format
     * @return Array of supported target formats
     */
    String[] getSupportedConversions(String sourceFormat);
    
    /**
     * Compress an asset for storage or transmission.
     * @param asset Asset to compress
     * @return Compressed asset data
     * @throws PluginException if compression fails
     */
    byte[] compressAsset(Asset asset) throws PluginException;
    
    /**
     * Decompress asset data.
     * @param compressedData Compressed asset data
     * @param assetType Asset type
     * @return Decompressed asset
     * @throws PluginException if decompression fails
     */
    Asset decompressAsset(byte[] compressedData, String assetType) throws PluginException;
    
    /**
     * Check if this plugin supports asset compression.
     * @return true if compression is supported
     */
    boolean supportsCompression();
    
    /**
     * Get the priority for asset loading.
     * Higher priority plugins are tried first.
     * @return Loading priority
     */
    int getLoadingPriority();
    
    /**
     * Called when an asset is about to be unloaded.
     * @param asset Asset being unloaded
     */
    void onAssetUnloading(Asset asset);
    
    /**
     * Get custom asset processors provided by this plugin.
     * @return Array of processor names
     */
    String[] getProvidedProcessors();
    
    /**
     * Get an asset processor by name.
     * @param processorName Name of the processor
     * @return Processor instance or null if not found
     */
    AssetProcessor getAssetProcessor(String processorName);
    
    /**
     * Cleanup asset processing resources.
     * Called when the plugin is being unloaded.
     */
    void cleanupAssetProcessing();
    
    /**
     * Asset validation result.
     */
    class AssetValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        public AssetValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }
        
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }
    
    /**
     * Asset metadata extracted from files.
     */
    class AssetMetadata {
        private final String format;
        private final long fileSize;
        private final String version;
        private final java.util.Map<String, Object> properties;
        
        public AssetMetadata(String format, long fileSize, String version, java.util.Map<String, Object> properties) {
            this.format = format;
            this.fileSize = fileSize;
            this.version = version;
            this.properties = properties;
        }
        
        public String getFormat() { return format; }
        public long getFileSize() { return fileSize; }
        public String getVersion() { return version; }
        public java.util.Map<String, Object> getProperties() { return properties; }
        
        @SuppressWarnings("unchecked")
        public <T> T getProperty(String key, T defaultValue) {
            Object value = properties.get(key);
            return value != null ? (T) value : defaultValue;
        }
    }
    
    /**
     * Asset processor interface.
     */
    interface AssetProcessor {
        /**
         * Process an asset.
         * @param asset Asset to process
         * @return Processed asset
         * @throws PluginException if processing fails
         */
        Asset process(Asset asset) throws PluginException;
        
        /**
         * Get the processor name.
         * @return Processor name
         */
        String getName();
        
        /**
         * Get supported asset types for this processor.
         * @return Array of supported asset types
         */
        String[] getSupportedTypes();
    }
}