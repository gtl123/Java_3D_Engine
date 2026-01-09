package engine.ai.pathfinding;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU cache for storing computed paths
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class PathCache {
    
    private final Map<String, CachedPath> cache;
    private final int maxSize;
    private final long maxAge;
    
    /**
     * Create a new path cache
     * @param maxSize Maximum number of paths to cache
     */
    public PathCache(int maxSize) {
        this.maxSize = maxSize;
        this.maxAge = 30000; // 30 seconds default
        
        // LinkedHashMap with access-order for LRU behavior
        this.cache = new LinkedHashMap<String, CachedPath>(maxSize + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedPath> eldest) {
                return size() > PathCache.this.maxSize;
            }
        };
    }
    
    /**
     * Store a path in the cache
     * @param key Cache key
     * @param path Path to cache
     */
    public synchronized void put(String key, Path path) {
        if (key != null && path != null) {
            cache.put(key, new CachedPath(path, System.currentTimeMillis()));
        }
    }
    
    /**
     * Retrieve a path from the cache
     * @param key Cache key
     * @return Cached path, or null if not found or expired
     */
    public synchronized Path get(String key) {
        CachedPath cachedPath = cache.get(key);
        if (cachedPath != null) {
            // Check if path has expired
            if (System.currentTimeMillis() - cachedPath.timestamp > maxAge) {
                cache.remove(key);
                return null;
            }
            return cachedPath.path;
        }
        return null;
    }
    
    /**
     * Remove a path from the cache
     * @param key Cache key
     */
    public synchronized void remove(String key) {
        cache.remove(key);
    }
    
    /**
     * Clear all cached paths
     */
    public synchronized void clear() {
        cache.clear();
    }
    
    /**
     * Get the current cache size
     * @return Number of cached paths
     */
    public synchronized int size() {
        return cache.size();
    }
    
    /**
     * Clean up expired entries
     */
    public synchronized void cleanupExpired() {
        long currentTime = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().timestamp > maxAge);
    }
    
    /**
     * Cached path with timestamp
     */
    private static class CachedPath {
        final Path path;
        final long timestamp;
        
        CachedPath(Path path, long timestamp) {
            this.path = path;
            this.timestamp = timestamp;
        }
    }
}