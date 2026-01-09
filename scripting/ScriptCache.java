package engine.scripting;

import engine.logging.LogManager;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LRU cache for scripts with expiration support.
 */
public class ScriptCache {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final int maxSize;
    private final Duration expiration;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    public ScriptCache(int maxSize, Duration expiration) {
        this.maxSize = maxSize;
        this.expiration = expiration;
    }
    
    /**
     * Get a script from cache.
     */
    public Script get(String scriptId) {
        lock.readLock().lock();
        try {
            CacheEntry entry = cache.get(scriptId);
            if (entry == null) {
                return null;
            }
            
            // Check if expired
            if (isExpired(entry)) {
                cache.remove(scriptId);
                return null;
            }
            
            // Update access time for LRU
            entry.updateAccessTime();
            
            return entry.script;
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Put a script in cache.
     */
    public void put(Script script) {
        lock.writeLock().lock();
        try {
            // Check if we need to evict entries
            if (cache.size() >= maxSize) {
                evictLRU();
            }
            
            CacheEntry entry = new CacheEntry(script);
            cache.put(script.getId(), entry);
            
            logManager.debug("ScriptCache", "Script cached", "scriptId", script.getId());
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Remove a script from cache.
     */
    public void remove(String scriptId) {
        lock.writeLock().lock();
        try {
            CacheEntry removed = cache.remove(scriptId);
            if (removed != null) {
                logManager.debug("ScriptCache", "Script removed from cache", "scriptId", scriptId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Clear all cached scripts.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            logManager.debug("ScriptCache", "Cache cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Clean up expired entries.
     */
    public void cleanup() {
        lock.writeLock().lock();
        try {
            cache.entrySet().removeIf(entry -> isExpired(entry.getValue()));
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get current cache size.
     */
    public int getSize() {
        return cache.size();
    }
    
    /**
     * Get maximum cache size.
     */
    public int getMaxSize() {
        return maxSize;
    }
    
    private boolean isExpired(CacheEntry entry) {
        return Duration.between(entry.creationTime, Instant.now()).compareTo(expiration) > 0;
    }
    
    private void evictLRU() {
        // Find the least recently used entry
        String lruKey = null;
        Instant oldestAccess = Instant.now();
        
        for (var entry : cache.entrySet()) {
            if (entry.getValue().lastAccessTime.isBefore(oldestAccess)) {
                oldestAccess = entry.getValue().lastAccessTime;
                lruKey = entry.getKey();
            }
        }
        
        if (lruKey != null) {
            cache.remove(lruKey);
            logManager.debug("ScriptCache", "Evicted LRU script", "scriptId", lruKey);
        }
    }
    
    /**
     * Cache entry with metadata.
     */
    private static class CacheEntry {
        final Script script;
        final Instant creationTime;
        volatile Instant lastAccessTime;
        
        CacheEntry(Script script) {
            this.script = script;
            this.creationTime = Instant.now();
            this.lastAccessTime = creationTime;
        }
        
        void updateAccessTime() {
            this.lastAccessTime = Instant.now();
        }
    }
}