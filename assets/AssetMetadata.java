package engine.assets;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asset metadata and versioning information.
 * Contains comprehensive information about an asset including dependencies, 
 * compression settings, streaming configuration, and custom properties.
 */
public class AssetMetadata {
    
    private final String assetId;
    private final String path;
    private final AssetType type;
    private final long fileSize;
    private final long lastModified;
    private final String checksum;
    private final int version;
    
    // Dependencies
    private final Set<String> dependencies;
    private final Set<String> dependents;
    
    // Loading configuration
    private final int priority;
    private final boolean streamable;
    private final boolean compressible;
    private final long streamingThreshold;
    
    // Compression settings
    private final CompressionType compressionType;
    private final int compressionLevel;
    private final long compressedSize;
    
    // Custom properties
    private final Map<String, Object> properties;
    
    // Runtime metadata
    private volatile long loadTime;
    private volatile long accessCount;
    private volatile long lastAccessed;
    private volatile boolean hotReloadEnabled;
    
    private AssetMetadata(Builder builder) {
        this.assetId = builder.assetId;
        this.path = builder.path;
        this.type = builder.type;
        this.fileSize = builder.fileSize;
        this.lastModified = builder.lastModified;
        this.checksum = builder.checksum;
        this.version = builder.version;
        this.dependencies = Collections.unmodifiableSet(new HashSet<>(builder.dependencies));
        this.dependents = Collections.unmodifiableSet(new HashSet<>(builder.dependents));
        this.priority = builder.priority;
        this.streamable = builder.streamable;
        this.compressible = builder.compressible;
        this.streamingThreshold = builder.streamingThreshold;
        this.compressionType = builder.compressionType;
        this.compressionLevel = builder.compressionLevel;
        this.compressedSize = builder.compressedSize;
        this.properties = Collections.unmodifiableMap(new HashMap<>(builder.properties));
        this.loadTime = 0;
        this.accessCount = 0;
        this.lastAccessed = 0;
        this.hotReloadEnabled = builder.hotReloadEnabled;
    }
    
    /**
     * Compression types supported by the asset pipeline.
     */
    public enum CompressionType {
        NONE("none", 1.0f),
        LZ4("lz4", 0.6f),
        GZIP("gzip", 0.4f),
        ZSTD("zstd", 0.3f),
        CUSTOM("custom", 0.5f);
        
        private final String name;
        private final float estimatedRatio;
        
        CompressionType(String name, float estimatedRatio) {
            this.name = name;
            this.estimatedRatio = estimatedRatio;
        }
        
        public String getName() { return name; }
        public float getEstimatedRatio() { return estimatedRatio; }
    }
    
    // Getters
    public String getAssetId() { return assetId; }
    public String getPath() { return path; }
    public AssetType getType() { return type; }
    public long getFileSize() { return fileSize; }
    public long getLastModified() { return lastModified; }
    public String getChecksum() { return checksum; }
    public int getVersion() { return version; }
    public Set<String> getDependencies() { return dependencies; }
    public Set<String> getDependents() { return dependents; }
    public int getPriority() { return priority; }
    public boolean isStreamable() { return streamable; }
    public boolean isCompressible() { return compressible; }
    public long getStreamingThreshold() { return streamingThreshold; }
    public CompressionType getCompressionType() { return compressionType; }
    public int getCompressionLevel() { return compressionLevel; }
    public long getCompressedSize() { return compressedSize; }
    public Map<String, Object> getProperties() { return properties; }
    public long getLoadTime() { return loadTime; }
    public long getAccessCount() { return accessCount; }
    public long getLastAccessed() { return lastAccessed; }
    public boolean isHotReloadEnabled() { return hotReloadEnabled; }
    
    /**
     * Get a custom property value.
     * @param key Property key
     * @param defaultValue Default value if not found
     * @return Property value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, T defaultValue) {
        Object value = properties.get(key);
        if (value != null && defaultValue != null && defaultValue.getClass().isInstance(value)) {
            return (T) value;
        }
        return defaultValue;
    }
    
    /**
     * Check if the asset should be streamed based on size threshold.
     * @return true if asset should be streamed
     */
    public boolean shouldStream() {
        return streamable && fileSize > streamingThreshold;
    }
    
    /**
     * Get estimated memory usage for this asset.
     * @return Estimated memory usage in bytes
     */
    public long getEstimatedMemoryUsage() {
        long baseSize = compressedSize > 0 ? compressedSize : fileSize;
        return (long) (baseSize * type.getMemoryMultiplier());
    }
    
    /**
     * Update runtime statistics.
     */
    public void recordAccess() {
        accessCount++;
        lastAccessed = System.currentTimeMillis();
    }
    
    /**
     * Set load time.
     * @param loadTime Load time in milliseconds
     */
    public void setLoadTime(long loadTime) {
        this.loadTime = loadTime;
    }
    
    /**
     * Set hot reload enabled state.
     * @param enabled Hot reload enabled
     */
    public void setHotReloadEnabled(boolean enabled) {
        this.hotReloadEnabled = enabled;
    }
    
    /**
     * Create a new builder for AssetMetadata.
     * @param assetId Asset ID
     * @param path Asset path
     * @param type Asset type
     * @return New builder instance
     */
    public static Builder builder(String assetId, String path, AssetType type) {
        return new Builder(assetId, path, type);
    }
    
    /**
     * Builder for AssetMetadata.
     */
    public static class Builder {
        private final String assetId;
        private final String path;
        private final AssetType type;
        private long fileSize = 0;
        private long lastModified = System.currentTimeMillis();
        private String checksum = "";
        private int version = 1;
        private final Set<String> dependencies = new HashSet<>();
        private final Set<String> dependents = new HashSet<>();
        private int priority = -1; // Will use type default if not set
        private boolean streamable = false;
        private boolean compressible = false;
        private long streamingThreshold = 1024 * 1024; // 1MB default
        private CompressionType compressionType = CompressionType.NONE;
        private int compressionLevel = 6;
        private long compressedSize = 0;
        private final Map<String, Object> properties = new ConcurrentHashMap<>();
        private boolean hotReloadEnabled = false;
        
        private Builder(String assetId, String path, AssetType type) {
            this.assetId = assetId;
            this.path = path;
            this.type = type;
            // Set defaults based on asset type
            this.streamable = type.supportsStreaming();
            this.compressible = type.supportsCompression();
            this.priority = type.getLoadingPriority();
        }
        
        public Builder fileSize(long fileSize) { this.fileSize = fileSize; return this; }
        public Builder lastModified(long lastModified) { this.lastModified = lastModified; return this; }
        public Builder checksum(String checksum) { this.checksum = checksum; return this; }
        public Builder version(int version) { this.version = version; return this; }
        public Builder priority(int priority) { this.priority = priority; return this; }
        public Builder streamable(boolean streamable) { this.streamable = streamable; return this; }
        public Builder compressible(boolean compressible) { this.compressible = compressible; return this; }
        public Builder streamingThreshold(long threshold) { this.streamingThreshold = threshold; return this; }
        public Builder compressionType(CompressionType type) { this.compressionType = type; return this; }
        public Builder compressionLevel(int level) { this.compressionLevel = level; return this; }
        public Builder compressedSize(long size) { this.compressedSize = size; return this; }
        public Builder hotReloadEnabled(boolean enabled) { this.hotReloadEnabled = enabled; return this; }
        
        public Builder addDependency(String dependencyId) {
            this.dependencies.add(dependencyId);
            return this;
        }
        
        public Builder addDependent(String dependentId) {
            this.dependents.add(dependentId);
            return this;
        }
        
        public Builder property(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }
        
        public AssetMetadata build() {
            return new AssetMetadata(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("AssetMetadata{id='%s', path='%s', type=%s, size=%d, compressed=%d, deps=%d}", 
                           assetId, path, type, fileSize, compressedSize, dependencies.size());
    }
}