package engine.assets;

import engine.logging.LogManager;
import engine.logging.MetricsCollector;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.*;

/**
 * Intelligent caching system with LRU eviction and memory management.
 * Provides high-performance asset caching with configurable size limits,
 * automatic eviction policies, and comprehensive cache statistics.
 */
public class AssetCache {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = LogManager.getInstance().getMetricsCollector();
    
    // Cache configuration
    private final long maxMemoryBytes;
    private final int maxEntries;
    private final float evictionThreshold;
    private final boolean enableStatistics;
    
    // Cache storage
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> accessOrder = new ConcurrentLinkedQueue<>();
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    
    // Memory tracking
    private final AtomicLong currentMemoryUsage = new AtomicLong(0);
    private final AtomicLong totalEntries = new AtomicLong(0);
    
    // Statistics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);
    private final AtomicLong memoryEvictions = new AtomicLong(0);
    
    /**
     * Cache entry wrapper with access tracking.
     */
    private static class CacheEntry {
        final Asset asset;
        final long memorySize;
        volatile long lastAccessed;
        volatile long accessCount;
        
        CacheEntry(Asset asset, long memorySize) {
            this.asset = asset;
            this.memorySize = memorySize;
            this.lastAccessed = System.currentTimeMillis();
            this.accessCount = 1;
        }
        
        void recordAccess() {
            this.lastAccessed = System.currentTimeMillis();
            this.accessCount++;
        }
    }
    
    /**
     * Cache configuration builder.
     */
    public static class Config {
        private long maxMemoryBytes = 512 * 1024 * 1024; // 512MB default
        private int maxEntries = 10000;
        private float evictionThreshold = 0.8f; // Start evicting at 80% capacity
        private boolean enableStatistics = true;
        
        public Config maxMemoryMB(int megabytes) {
            this.maxMemoryBytes = megabytes * 1024L * 1024L;
            return this;
        }
        
        public Config maxMemoryBytes(long bytes) {
            this.maxMemoryBytes = bytes;
            return this;
        }
        
        public Config maxEntries(int entries) {
            this.maxEntries = entries;
            return this;
        }
        
        public Config evictionThreshold(float threshold) {
            this.evictionThreshold = Math.max(0.1f, Math.min(1.0f, threshold));
            return this;
        }
        
        public Config enableStatistics(boolean enable) {
            this.enableStatistics = enable;
            return this;
        }
        
        public AssetCache build() {
            return new AssetCache(this);
        }
    }
    
    /**
     * Create a new asset cache with default configuration.
     */
    public AssetCache() {
        this(new Config());
    }
    
    /**
     * Create a new asset cache with the specified configuration.
     */
    private AssetCache(Config config) {
        this.maxMemoryBytes = config.maxMemoryBytes;
        this.maxEntries = config.maxEntries;
        this.evictionThreshold = config.evictionThreshold;
        this.enableStatistics = config.enableStatistics;
        
        logManager.info("AssetCache", "Asset cache initialized",
                       "maxMemoryMB", maxMemoryBytes / (1024 * 1024),
                       "maxEntries", maxEntries,
                       "evictionThreshold", evictionThreshold);
    }
    
    /**
     * Get an asset from the cache.
     * @param assetId Asset identifier
     * @return Asset if found, null otherwise
     */
    public Asset get(String assetId) {
        if (assetId == null) return null;
        
        cacheLock.readLock().lock();
        try {
            CacheEntry entry = cache.get(assetId);
            if (entry != null) {
                entry.recordAccess();
                updateAccessOrder(assetId);
                
                if (enableStatistics) {
                    cacheHits.incrementAndGet();
                    metricsCollector.incrementCounter("asset.cache.hits");
                    entry.asset.getMetadata().recordAccess();
                }
                
                return entry.asset;
            } else {
                if (enableStatistics) {
                    cacheMisses.incrementAndGet();
                    metricsCollector.incrementCounter("asset.cache.misses");
                }
                return null;
            }
        } finally {
            cacheLock.readLock().unlock();
        }
    }
    
    /**
     * Put an asset into the cache.
     * @param asset Asset to cache
     * @return true if successfully cached, false if rejected
     */
    public boolean put(Asset asset) {
        if (asset == null || asset.getId() == null) return false;
        
        long memorySize = asset.getMetadata().getEstimatedMemoryUsage();
        
        // Check if asset is too large for cache
        if (memorySize > maxMemoryBytes) {
            logManager.warn("AssetCache", "Asset too large for cache",
                           "assetId", asset.getId(),
                           "assetSize", memorySize,
                           "maxCacheSize", maxMemoryBytes);
            return false;
        }
        
        cacheLock.writeLock().lock();
        try {
            // Remove existing entry if present
            CacheEntry existing = cache.get(asset.getId());
            if (existing != null) {
                currentMemoryUsage.addAndGet(-existing.memorySize);
                totalEntries.decrementAndGet();
            }
            
            // Ensure we have space for the new asset
            ensureCapacity(memorySize);
            
            // Add new entry
            CacheEntry entry = new CacheEntry(asset, memorySize);
            cache.put(asset.getId(), entry);
            currentMemoryUsage.addAndGet(memorySize);
            totalEntries.incrementAndGet();
            updateAccessOrder(asset.getId());
            
            if (enableStatistics) {
                metricsCollector.recordGauge("asset.cache.memory.used", currentMemoryUsage.get());
                metricsCollector.recordGauge("asset.cache.entries", totalEntries.get());
            }
            
            logManager.debug("AssetCache", "Asset cached",
                           "assetId", asset.getId(),
                           "memorySize", memorySize,
                           "totalMemory", currentMemoryUsage.get(),
                           "totalEntries", totalEntries.get());
            
            return true;
            
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Remove an asset from the cache.
     * @param assetId Asset identifier
     * @return Removed asset, or null if not found
     */
    public Asset remove(String assetId) {
        if (assetId == null) return null;
        
        cacheLock.writeLock().lock();
        try {
            CacheEntry entry = cache.remove(assetId);
            if (entry != null) {
                currentMemoryUsage.addAndGet(-entry.memorySize);
                totalEntries.decrementAndGet();
                accessOrder.remove(assetId);
                
                logManager.debug("AssetCache", "Asset removed from cache",
                               "assetId", assetId,
                               "memoryFreed", entry.memorySize);
                
                return entry.asset;
            }
            return null;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Check if an asset is cached.
     * @param assetId Asset identifier
     * @return true if cached
     */
    public boolean contains(String assetId) {
        return assetId != null && cache.containsKey(assetId);
    }
    
    /**
     * Clear all cached assets.
     */
    public void clear() {
        cacheLock.writeLock().lock();
        try {
            cache.clear();
            accessOrder.clear();
            currentMemoryUsage.set(0);
            totalEntries.set(0);
            
            logManager.info("AssetCache", "Cache cleared");
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Ensure there's enough capacity for a new asset.
     * Evicts assets using LRU policy if necessary.
     */
    private void ensureCapacity(long requiredMemory) {
        // Check memory threshold
        long memoryThreshold = (long) (maxMemoryBytes * evictionThreshold);
        boolean needsMemoryEviction = currentMemoryUsage.get() + requiredMemory > memoryThreshold;
        
        // Check entry count threshold
        int entryThreshold = (int) (maxEntries * evictionThreshold);
        boolean needsCountEviction = totalEntries.get() >= entryThreshold;
        
        if (!needsMemoryEviction && !needsCountEviction) {
            return; // No eviction needed
        }
        
        logManager.debug("AssetCache", "Starting cache eviction",
                        "currentMemory", currentMemoryUsage.get(),
                        "requiredMemory", requiredMemory,
                        "memoryThreshold", memoryThreshold,
                        "currentEntries", totalEntries.get(),
                        "entryThreshold", entryThreshold);
        
        // Evict assets using LRU policy
        int evicted = 0;
        long memoryFreed = 0;
        
        Iterator<String> accessIterator = accessOrder.iterator();
        while (accessIterator.hasNext() && (needsMemoryEviction || needsCountEviction)) {
            String assetId = accessIterator.next();
            CacheEntry entry = cache.get(assetId);
            
            if (entry != null) {
                // Don't evict recently accessed assets (within last 5 seconds)
                if (System.currentTimeMillis() - entry.lastAccessed < 5000) {
                    continue;
                }
                
                cache.remove(assetId);
                accessIterator.remove();
                
                currentMemoryUsage.addAndGet(-entry.memorySize);
                totalEntries.decrementAndGet();
                memoryFreed += entry.memorySize;
                evicted++;
                
                // Dispose the asset if it supports it
                try {
                    entry.asset.dispose();
                } catch (Exception e) {
                    logManager.warn("AssetCache", "Error disposing evicted asset",
                                   "assetId", assetId, "error", e.getMessage());
                }
                
                // Check if we've freed enough space
                needsMemoryEviction = currentMemoryUsage.get() + requiredMemory > memoryThreshold;
                needsCountEviction = totalEntries.get() >= entryThreshold;
            }
        }
        
        if (enableStatistics) {
            evictions.addAndGet(evicted);
            if (needsMemoryEviction) {
                memoryEvictions.incrementAndGet();
            }
            metricsCollector.incrementCounter("asset.cache.evictions", evicted);
        }
        
        logManager.info("AssetCache", "Cache eviction completed",
                       "assetsEvicted", evicted,
                       "memoryFreed", memoryFreed,
                       "currentMemory", currentMemoryUsage.get(),
                       "currentEntries", totalEntries.get());
    }
    
    /**
     * Update access order for LRU tracking.
     */
    private void updateAccessOrder(String assetId) {
        // Remove from current position and add to end
        accessOrder.remove(assetId);
        accessOrder.offer(assetId);
    }
    
    /**
     * Get cache statistics.
     */
    public CacheStatistics getStatistics() {
        return new CacheStatistics(
            totalEntries.get(),
            currentMemoryUsage.get(),
            maxEntries,
            maxMemoryBytes,
            cacheHits.get(),
            cacheMisses.get(),
            evictions.get(),
            memoryEvictions.get()
        );
    }
    
    /**
     * Cache statistics data class.
     */
    public static class CacheStatistics {
        public final long entries;
        public final long memoryUsed;
        public final long maxEntries;
        public final long maxMemory;
        public final long hits;
        public final long misses;
        public final long evictions;
        public final long memoryEvictions;
        public final double hitRatio;
        public final double memoryUtilization;
        
        CacheStatistics(long entries, long memoryUsed, long maxEntries, long maxMemory,
                       long hits, long misses, long evictions, long memoryEvictions) {
            this.entries = entries;
            this.memoryUsed = memoryUsed;
            this.maxEntries = maxEntries;
            this.maxMemory = maxMemory;
            this.hits = hits;
            this.misses = misses;
            this.evictions = evictions;
            this.memoryEvictions = memoryEvictions;
            
            long totalAccesses = hits + misses;
            this.hitRatio = totalAccesses > 0 ? (double) hits / totalAccesses : 0.0;
            this.memoryUtilization = maxMemory > 0 ? (double) memoryUsed / maxMemory : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("CacheStats{entries=%d/%d, memory=%dMB/%dMB (%.1f%%), hitRatio=%.2f, evictions=%d}",
                               entries, maxEntries,
                               memoryUsed / (1024 * 1024), maxMemory / (1024 * 1024),
                               memoryUtilization * 100.0, hitRatio, evictions);
        }
    }
    
    /**
     * Create a new cache configuration builder.
     */
    public static Config config() {
        return new Config();
    }
}