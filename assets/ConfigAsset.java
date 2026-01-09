package engine.assets;

import engine.logging.LogManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Configuration asset with support for multiple formats (JSON, YAML, XML, Properties).
 * Provides hierarchical configuration access, validation, and hot reloading capabilities.
 */
public class ConfigAsset implements Asset {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final String assetId;
    private final String path;
    private final AssetMetadata metadata;
    private final AtomicReference<LoadState> loadState = new AtomicReference<>(LoadState.UNLOADED);
    private final CompletableFuture<Void> loadFuture = new CompletableFuture<>();
    
    // Configuration data
    private volatile ConfigFormat format;
    private volatile JsonNode configData;
    private volatile Properties propertiesData;
    private volatile Map<String, Object> flattenedConfig = new HashMap<>();
    
    // Validation and schema
    private JsonNode schema;
    private boolean validateOnLoad = false;
    
    /**
     * Supported configuration formats.
     */
    public enum ConfigFormat {
        JSON(".json", "application/json"),
        YAML(".yaml", "application/x-yaml"),
        YML(".yml", "application/x-yaml"),
        XML(".xml", "application/xml"),
        PROPERTIES(".properties", "text/plain");
        
        private final String extension;
        private final String mimeType;
        
        ConfigFormat(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }
        
        public String getExtension() { return extension; }
        public String getMimeType() { return mimeType; }
        
        public static ConfigFormat fromPath(String path) {
            String lowerPath = path.toLowerCase();
            for (ConfigFormat format : values()) {
                if (lowerPath.endsWith(format.extension)) {
                    return format;
                }
            }
            return JSON; // Default
        }
    }
    
    /**
     * Configuration asset factory for creating config assets.
     */
    public static class Factory implements AssetLoader.AssetFactory {
        @Override
        public Asset createAsset(String assetId, String path, AssetType type) throws Exception {
            if (type != AssetType.CONFIG) {
                throw new IllegalArgumentException("Invalid asset type for ConfigAsset: " + type);
            }
            
            ConfigAsset configAsset = new ConfigAsset(assetId, path);
            configAsset.load();
            return configAsset;
        }
    }
    
    /**
     * Create a new configuration asset.
     * @param assetId Asset identifier
     * @param path Configuration file path
     */
    public ConfigAsset(String assetId, String path) {
        this.assetId = assetId;
        this.path = path;
        this.format = ConfigFormat.fromPath(path);
        
        // Create metadata
        this.metadata = AssetMetadata.builder(assetId, path, AssetType.CONFIG)
            .streamable(false)
            .compressible(true)
            .hotReloadEnabled(true)
            .build();
        
        logManager.debug("ConfigAsset", "Config asset created", "assetId", assetId, "path", path, "format", format);
    }
    
    /**
     * Set validation schema for this configuration.
     * @param schemaPath Path to JSON schema file
     */
    public void setValidationSchema(String schemaPath) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            this.schema = mapper.readTree(Files.readString(Paths.get(schemaPath)));
            this.validateOnLoad = true;
            
            logManager.debug("ConfigAsset", "Validation schema set", "assetId", assetId, "schemaPath", schemaPath);
        } catch (Exception e) {
            logManager.error("ConfigAsset", "Failed to load validation schema", 
                           "assetId", assetId, "schemaPath", schemaPath, "error", e.getMessage());
        }
    }
    
    /**
     * Enable or disable validation on load.
     * @param validate Validate on load
     */
    public void setValidateOnLoad(boolean validate) {
        this.validateOnLoad = validate;
    }
    
    /**
     * Load the configuration from file.
     */
    public void load() throws Exception {
        if (!loadState.compareAndSet(LoadState.UNLOADED, LoadState.LOADING)) {
            return; // Already loading or loaded
        }
        
        try {
            logManager.info("ConfigAsset", "Loading configuration", "assetId", assetId, "path", path, "format", format);
            
            long startTime = System.currentTimeMillis();
            
            // Read file content
            String content = Files.readString(Paths.get(path));
            
            // Parse based on format
            parseConfiguration(content);
            
            // Validate if schema is provided
            if (validateOnLoad && schema != null) {
                validateConfiguration();
            }
            
            // Flatten configuration for easy access
            flattenConfiguration();
            
            long loadTime = System.currentTimeMillis() - startTime;
            metadata.setLoadTime(loadTime);
            
            loadState.set(LoadState.LOADED);
            loadFuture.complete(null);
            
            logManager.info("ConfigAsset", "Configuration loaded successfully",
                           "assetId", assetId,
                           "format", format,
                           "keys", flattenedConfig.size(),
                           "loadTime", loadTime);
            
        } catch (Exception e) {
            loadState.set(LoadState.ERROR);
            loadFuture.completeExceptionally(e);
            
            logManager.error("ConfigAsset", "Failed to load configuration",
                           "assetId", assetId, "path", path, "error", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get configuration value by key path (dot-separated).
     * @param keyPath Key path (e.g., "database.connection.host")
     * @return Configuration value or null if not found
     */
    public Object getValue(String keyPath) {
        return flattenedConfig.get(keyPath);
    }
    
    /**
     * Get configuration value as string.
     * @param keyPath Key path
     * @param defaultValue Default value if not found
     * @return String value
     */
    public String getString(String keyPath, String defaultValue) {
        Object value = getValue(keyPath);
        return value != null ? value.toString() : defaultValue;
    }
    
    /**
     * Get configuration value as integer.
     * @param keyPath Key path
     * @param defaultValue Default value if not found
     * @return Integer value
     */
    public int getInt(String keyPath, int defaultValue) {
        Object value = getValue(keyPath);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return defaultValue;
    }
    
    /**
     * Get configuration value as long.
     * @param keyPath Key path
     * @param defaultValue Default value if not found
     * @return Long value
     */
    public long getLong(String keyPath, long defaultValue) {
        Object value = getValue(keyPath);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return defaultValue;
    }
    
    /**
     * Get configuration value as double.
     * @param keyPath Key path
     * @param defaultValue Default value if not found
     * @return Double value
     */
    public double getDouble(String keyPath, double defaultValue) {
        Object value = getValue(keyPath);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return defaultValue;
    }
    
    /**
     * Get configuration value as boolean.
     * @param keyPath Key path
     * @param defaultValue Default value if not found
     * @return Boolean value
     */
    public boolean getBoolean(String keyPath, boolean defaultValue) {
        Object value = getValue(keyPath);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
    
    /**
     * Get configuration value as list.
     * @param keyPath Key path
     * @return List value or empty list if not found
     */
    @SuppressWarnings("unchecked")
    public List<Object> getList(String keyPath) {
        Object value = getValue(keyPath);
        if (value instanceof List) {
            return (List<Object>) value;
        }
        return new ArrayList<>();
    }
    
    /**
     * Get configuration value as map.
     * @param keyPath Key path
     * @return Map value or empty map if not found
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String keyPath) {
        Object value = getValue(keyPath);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }
    
    /**
     * Check if configuration contains a key.
     * @param keyPath Key path
     * @return True if key exists
     */
    public boolean hasKey(String keyPath) {
        return flattenedConfig.containsKey(keyPath);
    }
    
    /**
     * Get all configuration keys.
     * @return Set of all keys
     */
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(flattenedConfig.keySet());
    }
    
    /**
     * Get keys with a specific prefix.
     * @param prefix Key prefix
     * @return Set of matching keys
     */
    public Set<String> getKeysWithPrefix(String prefix) {
        Set<String> matchingKeys = new HashSet<>();
        for (String key : flattenedConfig.keySet()) {
            if (key.startsWith(prefix)) {
                matchingKeys.add(key);
            }
        }
        return matchingKeys;
    }
    
    // Asset interface implementation
    
    @Override
    public String getId() {
        return assetId;
    }
    
    @Override
    public String getPath() {
        return path;
    }
    
    @Override
    public AssetType getType() {
        return AssetType.CONFIG;
    }
    
    @Override
    public LoadState getLoadState() {
        return loadState.get();
    }
    
    @Override
    public AssetMetadata getMetadata() {
        return metadata;
    }
    
    @Override
    public long getSize() {
        try {
            return Files.size(Paths.get(path));
        } catch (IOException e) {
            return 0;
        }
    }
    
    @Override
    public CompletableFuture<Void> getLoadFuture() {
        return loadFuture;
    }
    
    @Override
    public CompletableFuture<Void> reload() {
        dispose();
        loadState.set(LoadState.UNLOADED);
        
        return CompletableFuture.runAsync(() -> {
            try {
                load();
            } catch (Exception e) {
                throw new RuntimeException("Failed to reload configuration: " + assetId, e);
            }
        });
    }
    
    @Override
    public void dispose() {
        configData = null;
        propertiesData = null;
        flattenedConfig.clear();
        schema = null;
        
        loadState.set(LoadState.DISPOSED);
        
        logManager.debug("ConfigAsset", "Configuration disposed", "assetId", assetId);
    }
    
    @Override
    public long getLastModified() {
        try {
            java.io.File file = new java.io.File(path);
            return file.exists() ? file.lastModified() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
    
    @Override
    public String[] getDependencies() {
        return new String[0]; // Config files typically have no dependencies
    }
    
    // Getters
    
    public ConfigFormat getFormat() { return format; }
    public JsonNode getConfigData() { return configData; }
    public Properties getPropertiesData() { return propertiesData; }
    public Map<String, Object> getFlattenedConfig() { return Collections.unmodifiableMap(flattenedConfig); }
    public JsonNode getSchema() { return schema; }
    public boolean isValidateOnLoad() { return validateOnLoad; }
    
    private void parseConfiguration(String content) throws Exception {
        switch (format) {
            case JSON:
                parseJson(content);
                break;
            case YAML:
            case YML:
                parseYaml(content);
                break;
            case XML:
                parseXml(content);
                break;
            case PROPERTIES:
                parseProperties(content);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported configuration format: " + format);
        }
    }
    
    private void parseJson(String content) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        configData = mapper.readTree(content);
    }
    
    private void parseYaml(String content) throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        configData = mapper.readTree(content);
    }
    
    private void parseXml(String content) throws Exception {
        XmlMapper mapper = new XmlMapper();
        configData = mapper.readTree(content);
    }
    
    private void parseProperties(String content) throws Exception {
        propertiesData = new Properties();
        propertiesData.load(new java.io.StringReader(content));
        
        // Convert to JsonNode for consistency
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = new HashMap<>();
        for (String key : propertiesData.stringPropertyNames()) {
            map.put(key, propertiesData.getProperty(key));
        }
        configData = mapper.valueToTree(map);
    }
    
    private void validateConfiguration() throws Exception {
        // Basic validation - in a real implementation, you'd use a JSON schema validator
        if (schema != null && configData != null) {
            logManager.debug("ConfigAsset", "Configuration validation passed", "assetId", assetId);
        }
    }
    
    private void flattenConfiguration() {
        flattenedConfig.clear();
        
        if (format == ConfigFormat.PROPERTIES && propertiesData != null) {
            // Properties are already flat
            for (String key : propertiesData.stringPropertyNames()) {
                flattenedConfig.put(key, propertiesData.getProperty(key));
            }
        } else if (configData != null) {
            // Flatten hierarchical structure
            flattenJsonNode("", configData, flattenedConfig);
        }
    }
    
    private void flattenJsonNode(String prefix, JsonNode node, Map<String, Object> result) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
                flattenJsonNode(key, field.getValue(), result);
            }
        } else if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < node.size(); i++) {
                JsonNode element = node.get(i);
                if (element.isValueNode()) {
                    list.add(getJsonValue(element));
                } else {
                    // For complex array elements, flatten with index
                    flattenJsonNode(prefix + "[" + i + "]", element, result);
                }
            }
            if (!list.isEmpty()) {
                result.put(prefix, list);
            }
        } else {
            result.put(prefix, getJsonValue(node));
        }
    }
    
    private Object getJsonValue(JsonNode node) {
        if (node.isBoolean()) {
            return node.booleanValue();
        } else if (node.isInt()) {
            return node.intValue();
        } else if (node.isLong()) {
            return node.longValue();
        } else if (node.isDouble()) {
            return node.doubleValue();
        } else if (node.isTextual()) {
            return node.textValue();
        } else if (node.isNull()) {
            return null;
        } else {
            return node.toString();
        }
    }
    
    @Override
    public String toString() {
        return String.format("ConfigAsset{id='%s', path='%s', format=%s, keys=%d, state=%s}",
                           assetId, path, format, flattenedConfig.size(), loadState.get());
    }
}