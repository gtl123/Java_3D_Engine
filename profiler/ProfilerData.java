package engine.profiler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Container for profiler metrics and metadata.
 * Stores performance data collected by profiler components.
 */
public class ProfilerData {
    
    private final String profilerType;
    private final long timestamp;
    private final Map<String, Object> metrics = new ConcurrentHashMap<>();
    private final Map<String, Object> metadata = new ConcurrentHashMap<>();
    
    public ProfilerData(String profilerType) {
        this.profilerType = profilerType;
        this.timestamp = System.currentTimeMillis();
    }
    
    public ProfilerData(String profilerType, long timestamp) {
        this.profilerType = profilerType;
        this.timestamp = timestamp;
    }
    
    /**
     * Add a metric value.
     */
    public void addMetric(String key, Object value) {
        metrics.put(key, value);
    }
    
    /**
     * Get a metric value with type casting.
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetric(String key, Class<T> type) {
        Object value = metrics.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Get a metric value as Object.
     */
    public Object getMetric(String key) {
        return metrics.get(key);
    }
    
    /**
     * Check if a metric exists.
     */
    public boolean hasMetric(String key) {
        return metrics.containsKey(key);
    }
    
    /**
     * Get all metrics.
     */
    public Map<String, Object> getMetrics() {
        return new ConcurrentHashMap<>(metrics);
    }
    
    /**
     * Add metadata.
     */
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    /**
     * Get metadata value.
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    /**
     * Get all metadata.
     */
    public Map<String, Object> getMetadata() {
        return new ConcurrentHashMap<>(metadata);
    }
    
    /**
     * Get the profiler type.
     */
    public String getProfilerType() {
        return profilerType;
    }
    
    /**
     * Get the timestamp when this data was collected.
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Get the number of metrics.
     */
    public int getMetricCount() {
        return metrics.size();
    }
    
    /**
     * Clear all metrics.
     */
    public void clearMetrics() {
        metrics.clear();
    }
    
    /**
     * Clear all metadata.
     */
    public void clearMetadata() {
        metadata.clear();
    }
    
    /**
     * Clear all data.
     */
    public void clear() {
        metrics.clear();
        metadata.clear();
    }
    
    /**
     * Merge another ProfilerData into this one.
     */
    public void merge(ProfilerData other) {
        if (other != null && other.profilerType.equals(this.profilerType)) {
            metrics.putAll(other.metrics);
            metadata.putAll(other.metadata);
        }
    }
    
    /**
     * Create a copy of this ProfilerData.
     */
    public ProfilerData copy() {
        ProfilerData copy = new ProfilerData(profilerType, timestamp);
        copy.metrics.putAll(this.metrics);
        copy.metadata.putAll(this.metadata);
        return copy;
    }
    
    @Override
    public String toString() {
        return String.format("ProfilerData{type='%s', timestamp=%d, metrics=%d, metadata=%d}",
                           profilerType, timestamp, metrics.size(), metadata.size());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ProfilerData that = (ProfilerData) obj;
        return timestamp == that.timestamp &&
               profilerType.equals(that.profilerType) &&
               metrics.equals(that.metrics) &&
               metadata.equals(that.metadata);
    }
    
    @Override
    public int hashCode() {
        int result = profilerType.hashCode();
        result = 31 * result + Long.hashCode(timestamp);
        result = 31 * result + metrics.hashCode();
        result = 31 * result + metadata.hashCode();
        return result;
    }
}