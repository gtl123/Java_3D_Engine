package engine.ai;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Shared data storage system for AI entities
 * Provides thread-safe access to shared AI data and state information
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class AIBlackboard {
    
    private final Map<String, Object> data;
    private final Map<String, Class<?>> typeRegistry;
    private final String ownerId;
    
    /**
     * Create a new AI blackboard
     * @param ownerId Unique identifier for the owner of this blackboard
     */
    public AIBlackboard(String ownerId) {
        this.ownerId = ownerId;
        this.data = new ConcurrentHashMap<>();
        this.typeRegistry = new ConcurrentHashMap<>();
    }
    
    /**
     * Set a value in the blackboard
     * @param key The key to store the value under
     * @param value The value to store
     * @param <T> The type of the value
     */
    public <T> void setValue(String key, T value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key and value cannot be null");
        }
        
        data.put(key, value);
        typeRegistry.put(key, value.getClass());
    }
    
    /**
     * Get a value from the blackboard
     * @param key The key to retrieve
     * @param type The expected type of the value
     * @param <T> The type to cast to
     * @return The value, or null if not found or wrong type
     */
    @SuppressWarnings("unchecked")
    public <T> T getValue(String key, Class<T> type) {
        if (key == null || type == null) {
            return null;
        }
        
        Object value = data.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Get a value with a default if not found
     * @param key The key to retrieve
     * @param type The expected type
     * @param defaultValue Default value to return if key not found
     * @param <T> The type to cast to
     * @return The value or default
     */
    public <T> T getValue(String key, Class<T> type, T defaultValue) {
        T value = getValue(key, type);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Get a value with a lazy default supplier
     * @param key The key to retrieve
     * @param type The expected type
     * @param defaultSupplier Supplier for default value
     * @param <T> The type to cast to
     * @return The value or supplied default
     */
    public <T> T getValue(String key, Class<T> type, Supplier<T> defaultSupplier) {
        T value = getValue(key, type);
        return value != null ? value : defaultSupplier.get();
    }
    
    /**
     * Check if a key exists in the blackboard
     * @param key The key to check
     * @return true if the key exists
     */
    public boolean hasKey(String key) {
        return data.containsKey(key);
    }
    
    /**
     * Remove a value from the blackboard
     * @param key The key to remove
     * @return The removed value, or null if not found
     */
    public Object removeValue(String key) {
        typeRegistry.remove(key);
        return data.remove(key);
    }
    
    /**
     * Clear all values from the blackboard
     */
    public void clear() {
        data.clear();
        typeRegistry.clear();
    }
    
    /**
     * Get all keys in the blackboard
     * @return Set of all keys
     */
    public Set<String> getKeys() {
        return data.keySet();
    }
    
    /**
     * Get the number of entries in the blackboard
     * @return Number of key-value pairs
     */
    public int size() {
        return data.size();
    }
    
    /**
     * Check if the blackboard is empty
     * @return true if empty
     */
    public boolean isEmpty() {
        return data.isEmpty();
    }
    
    /**
     * Get the owner ID of this blackboard
     * @return The owner identifier
     */
    public String getOwnerId() {
        return ownerId;
    }
    
    /**
     * Get the registered type for a key
     * @param key The key to check
     * @return The registered type, or null if key not found
     */
    public Class<?> getType(String key) {
        return typeRegistry.get(key);
    }
    
    /**
     * Copy all values from another blackboard
     * @param other The blackboard to copy from
     */
    public void copyFrom(AIBlackboard other) {
        if (other != null) {
            this.data.putAll(other.data);
            this.typeRegistry.putAll(other.typeRegistry);
        }
    }
    
    /**
     * Create a snapshot of the current blackboard state
     * @return A new blackboard with copied values
     */
    public AIBlackboard createSnapshot() {
        AIBlackboard snapshot = new AIBlackboard(ownerId + "_snapshot");
        snapshot.copyFrom(this);
        return snapshot;
    }
    
    @Override
    public String toString() {
        return "AIBlackboard{" +
                "ownerId='" + ownerId + '\'' +
                ", entries=" + data.size() +
                '}';
    }
}