package fps.map.data;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Optimization data and performance hints for map rendering and streaming.
 * Contains precomputed data to improve runtime performance.
 */
public class MapOptimizationData {
    
    // Rendering optimization
    private final int estimatedTriangleCount;
    private final int estimatedDrawCalls;
    private final List<LODLevel> lodLevels;
    private final OcclusionCullingData occlusionData;
    
    // Memory optimization
    private final long estimatedVRAMUsage; // bytes
    private final long estimatedRAMUsage; // bytes
    private final TextureStreamingHints textureHints;
    
    // Performance targets
    private final PerformanceTarget targetPerformance;
    private final Map<String, Integer> performanceMetrics;
    
    // Streaming optimization
    private final List<StreamingRegion> streamingRegions;
    private final int maxConcurrentStreams;
    private final long streamingBufferSize;
    
    // Compilation data
    private final boolean precompiledLightmaps;
    private final boolean precompiledOcclusion;
    private final boolean precompiledNavMesh;
    private final long compilationTimestamp;
    
    public MapOptimizationData() {
        this(new Builder());
    }
    
    private MapOptimizationData(Builder builder) {
        this.estimatedTriangleCount = builder.estimatedTriangleCount;
        this.estimatedDrawCalls = builder.estimatedDrawCalls;
        this.lodLevels = new ArrayList<>(builder.lodLevels);
        this.occlusionData = builder.occlusionData;
        this.estimatedVRAMUsage = builder.estimatedVRAMUsage;
        this.estimatedRAMUsage = builder.estimatedRAMUsage;
        this.textureHints = builder.textureHints;
        this.targetPerformance = builder.targetPerformance;
        this.performanceMetrics = new HashMap<>(builder.performanceMetrics);
        this.streamingRegions = new ArrayList<>(builder.streamingRegions);
        this.maxConcurrentStreams = builder.maxConcurrentStreams;
        this.streamingBufferSize = builder.streamingBufferSize;
        this.precompiledLightmaps = builder.precompiledLightmaps;
        this.precompiledOcclusion = builder.precompiledOcclusion;
        this.precompiledNavMesh = builder.precompiledNavMesh;
        this.compilationTimestamp = builder.compilationTimestamp;
    }
    
    // Getters
    public int getEstimatedTriangleCount() { return estimatedTriangleCount; }
    public int getEstimatedDrawCalls() { return estimatedDrawCalls; }
    public List<LODLevel> getLodLevels() { return new ArrayList<>(lodLevels); }
    public OcclusionCullingData getOcclusionData() { return occlusionData; }
    public long getEstimatedVRAMUsage() { return estimatedVRAMUsage; }
    public long getEstimatedRAMUsage() { return estimatedRAMUsage; }
    public TextureStreamingHints getTextureHints() { return textureHints; }
    public PerformanceTarget getTargetPerformance() { return targetPerformance; }
    public Map<String, Integer> getPerformanceMetrics() { return new HashMap<>(performanceMetrics); }
    public List<StreamingRegion> getStreamingRegions() { return new ArrayList<>(streamingRegions); }
    public int getMaxConcurrentStreams() { return maxConcurrentStreams; }
    public long getStreamingBufferSize() { return streamingBufferSize; }
    public boolean hasPrecompiledLightmaps() { return precompiledLightmaps; }
    public boolean hasPrecompiledOcclusion() { return precompiledOcclusion; }
    public boolean hasPrecompiledNavMesh() { return precompiledNavMesh; }
    public long getCompilationTimestamp() { return compilationTimestamp; }
    
    /**
     * Get performance metric value
     */
    public int getPerformanceMetric(String metricName) {
        return performanceMetrics.getOrDefault(metricName, 0);
    }
    
    /**
     * Check if map meets performance target
     */
    public boolean meetsPerformanceTarget() {
        return targetPerformance != null && 
               estimatedTriangleCount <= targetPerformance.maxTriangles &&
               estimatedDrawCalls <= targetPerformance.maxDrawCalls;
    }
    
    /**
     * Get memory usage in MB
     */
    public long getVRAMUsageMB() {
        return estimatedVRAMUsage / (1024 * 1024);
    }
    
    /**
     * Get memory usage in MB
     */
    public long getRAMUsageMB() {
        return estimatedRAMUsage / (1024 * 1024);
    }
    
    /**
     * Level of Detail information
     */
    public static class LODLevel {
        private final int level;
        private final float distance;
        private final int triangleCount;
        private final float qualityReduction;
        
        public LODLevel(int level, float distance, int triangleCount, float qualityReduction) {
            this.level = level;
            this.distance = distance;
            this.triangleCount = triangleCount;
            this.qualityReduction = qualityReduction;
        }
        
        public int getLevel() { return level; }
        public float getDistance() { return distance; }
        public int getTriangleCount() { return triangleCount; }
        public float getQualityReduction() { return qualityReduction; }
    }
    
    /**
     * Occlusion culling optimization data
     */
    public static class OcclusionCullingData {
        private final boolean enabled;
        private final int occluderCount;
        private final float cullingEfficiency;
        private final long precomputedDataSize;
        
        public OcclusionCullingData(boolean enabled, int occluderCount, float cullingEfficiency, long precomputedDataSize) {
            this.enabled = enabled;
            this.occluderCount = occluderCount;
            this.cullingEfficiency = cullingEfficiency;
            this.precomputedDataSize = precomputedDataSize;
        }
        
        public boolean isEnabled() { return enabled; }
        public int getOccluderCount() { return occluderCount; }
        public float getCullingEfficiency() { return cullingEfficiency; }
        public long getPrecomputedDataSize() { return precomputedDataSize; }
    }
    
    /**
     * Texture streaming hints
     */
    public static class TextureStreamingHints {
        private final int highResTextureCount;
        private final int mediumResTextureCount;
        private final int lowResTextureCount;
        private final long totalTextureMemory;
        private final int streamingPriority;
        
        public TextureStreamingHints(int highRes, int mediumRes, int lowRes, long totalMemory, int priority) {
            this.highResTextureCount = highRes;
            this.mediumResTextureCount = mediumRes;
            this.lowResTextureCount = lowRes;
            this.totalTextureMemory = totalMemory;
            this.streamingPriority = priority;
        }
        
        public int getHighResTextureCount() { return highResTextureCount; }
        public int getMediumResTextureCount() { return mediumResTextureCount; }
        public int getLowResTextureCount() { return lowResTextureCount; }
        public long getTotalTextureMemory() { return totalTextureMemory; }
        public int getStreamingPriority() { return streamingPriority; }
        public int getTotalTextureCount() { return highResTextureCount + mediumResTextureCount + lowResTextureCount; }
    }
    
    /**
     * Performance target specifications
     */
    public static class PerformanceTarget {
        private final int targetFPS;
        private final int maxTriangles;
        private final int maxDrawCalls;
        private final long maxVRAMUsage;
        private final long maxRAMUsage;
        
        public PerformanceTarget(int targetFPS, int maxTriangles, int maxDrawCalls, long maxVRAMUsage, long maxRAMUsage) {
            this.targetFPS = targetFPS;
            this.maxTriangles = maxTriangles;
            this.maxDrawCalls = maxDrawCalls;
            this.maxVRAMUsage = maxVRAMUsage;
            this.maxRAMUsage = maxRAMUsage;
        }
        
        public int getTargetFPS() { return targetFPS; }
        public int getMaxTriangles() { return maxTriangles; }
        public int getMaxDrawCalls() { return maxDrawCalls; }
        public long getMaxVRAMUsage() { return maxVRAMUsage; }
        public long getMaxRAMUsage() { return maxRAMUsage; }
    }
    
    /**
     * Streaming region for asset loading
     */
    public static class StreamingRegion {
        private final String regionId;
        private final float centerX, centerY, centerZ;
        private final float radius;
        private final int priority;
        private final long estimatedSize;
        
        public StreamingRegion(String regionId, float centerX, float centerY, float centerZ, 
                             float radius, int priority, long estimatedSize) {
            this.regionId = regionId;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.radius = radius;
            this.priority = priority;
            this.estimatedSize = estimatedSize;
        }
        
        public String getRegionId() { return regionId; }
        public float getCenterX() { return centerX; }
        public float getCenterY() { return centerY; }
        public float getCenterZ() { return centerZ; }
        public float getRadius() { return radius; }
        public int getPriority() { return priority; }
        public long getEstimatedSize() { return estimatedSize; }
    }
    
    /**
     * Builder for MapOptimizationData
     */
    public static class Builder {
        private int estimatedTriangleCount = 100000;
        private int estimatedDrawCalls = 500;
        private List<LODLevel> lodLevels = new ArrayList<>();
        private OcclusionCullingData occlusionData;
        private long estimatedVRAMUsage = 256 * 1024 * 1024; // 256MB
        private long estimatedRAMUsage = 128 * 1024 * 1024; // 128MB
        private TextureStreamingHints textureHints;
        private PerformanceTarget targetPerformance;
        private Map<String, Integer> performanceMetrics = new HashMap<>();
        private List<StreamingRegion> streamingRegions = new ArrayList<>();
        private int maxConcurrentStreams = 4;
        private long streamingBufferSize = 64 * 1024 * 1024; // 64MB
        private boolean precompiledLightmaps = false;
        private boolean precompiledOcclusion = false;
        private boolean precompiledNavMesh = false;
        private long compilationTimestamp = System.currentTimeMillis();
        
        public Builder estimatedTriangleCount(int count) {
            this.estimatedTriangleCount = count;
            return this;
        }
        
        public Builder estimatedDrawCalls(int calls) {
            this.estimatedDrawCalls = calls;
            return this;
        }
        
        public Builder addLODLevel(LODLevel lodLevel) {
            this.lodLevels.add(lodLevel);
            return this;
        }
        
        public Builder occlusionData(OcclusionCullingData data) {
            this.occlusionData = data;
            return this;
        }
        
        public Builder memoryUsage(long vramBytes, long ramBytes) {
            this.estimatedVRAMUsage = vramBytes;
            this.estimatedRAMUsage = ramBytes;
            return this;
        }
        
        public Builder textureHints(TextureStreamingHints hints) {
            this.textureHints = hints;
            return this;
        }
        
        public Builder targetPerformance(PerformanceTarget target) {
            this.targetPerformance = target;
            return this;
        }
        
        public Builder addPerformanceMetric(String name, int value) {
            this.performanceMetrics.put(name, value);
            return this;
        }
        
        public Builder addStreamingRegion(StreamingRegion region) {
            this.streamingRegions.add(region);
            return this;
        }
        
        public Builder streamingConfig(int maxStreams, long bufferSize) {
            this.maxConcurrentStreams = maxStreams;
            this.streamingBufferSize = bufferSize;
            return this;
        }
        
        public Builder precompiledData(boolean lightmaps, boolean occlusion, boolean navMesh) {
            this.precompiledLightmaps = lightmaps;
            this.precompiledOcclusion = occlusion;
            this.precompiledNavMesh = navMesh;
            return this;
        }
        
        public Builder compilationTimestamp(long timestamp) {
            this.compilationTimestamp = timestamp;
            return this;
        }
        
        public MapOptimizationData build() {
            return new MapOptimizationData(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("MapOptimizationData{triangles=%d, drawCalls=%d, vramMB=%d, ramMB=%d}", 
                           estimatedTriangleCount, estimatedDrawCalls, getVRAMUsageMB(), getRAMUsageMB());
    }
}