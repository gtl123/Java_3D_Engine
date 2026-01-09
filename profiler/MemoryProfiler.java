package engine.profiler;

import engine.logging.LogManager;
import engine.logging.MetricsCollector;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.GarbageCollectorMXBean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Memory profiler for allocation tracking and leak detection.
 * Monitors heap usage, garbage collection, and potential memory leaks.
 */
public class MemoryProfiler implements IProfiler {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = logManager.getMetricsCollector();
    
    private final ProfilerConfiguration config;
    private final AtomicBoolean active = new AtomicBoolean(false);
    
    // Memory management beans
    private final MemoryMXBean memoryBean;
    private final List<GarbageCollectorMXBean> gcBeans;
    
    // Memory tracking
    private final AtomicLong totalAllocations = new AtomicLong(0);
    private final AtomicLong totalDeallocations = new AtomicLong(0);
    private final AtomicLong peakHeapUsage = new AtomicLong(0);
    private final AtomicLong peakNonHeapUsage = new AtomicLong(0);
    
    // Allocation tracking
    private final Map<String, AllocationProfile> allocationProfiles = new ConcurrentHashMap<>();
    private final Set<ObjectReference> trackedObjects = new ConcurrentSkipListSet<>();
    
    // Memory snapshots
    private final List<MemorySnapshot> snapshots = new ArrayList<>();
    private long lastSnapshotTime = 0;
    
    // Garbage collection tracking
    private final Map<String, GCProfile> gcProfiles = new ConcurrentHashMap<>();
    private final Map<String, Long> lastGcCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastGcTimes = new ConcurrentHashMap<>();
    
    // Leak detection
    private final Map<String, LeakCandidate> leakCandidates = new ConcurrentHashMap<>();
    private final AtomicLong leakDetectionRuns = new AtomicLong(0);
    
    public MemoryProfiler(ProfilerConfiguration config) {
        this.config = config;
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        // Initialize GC tracking
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String gcName = gcBean.getName();
            lastGcCounts.put(gcName, gcBean.getCollectionCount());
            lastGcTimes.put(gcName, gcBean.getCollectionTime());
            gcProfiles.put(gcName, new GCProfile(gcName));
        }
    }
    
    @Override
    public void initialize() {
        logManager.info("MemoryProfiler", "Initializing memory profiler",
                       "allocationTracking", config.isAllocationTracking(),
                       "leakDetection", config.isLeakDetection(),
                       "heapAnalysis", config.isHeapAnalysis());
        
        // Enable verbose GC if supported (for better tracking)
        try {
            System.setProperty("java.lang.management.MemoryMXBean.verbose", "true");
        } catch (Exception e) {
            logManager.debug("MemoryProfiler", "Could not enable verbose GC", e);
        }
        
        logManager.info("MemoryProfiler", "Memory profiler initialized");
    }
    
    @Override
    public void start() {
        if (active.get()) {
            return;
        }
        
        logManager.info("MemoryProfiler", "Starting memory profiling");
        
        // Reset all tracking data
        totalAllocations.set(0);
        totalDeallocations.set(0);
        peakHeapUsage.set(0);
        peakNonHeapUsage.set(0);
        
        allocationProfiles.clear();
        trackedObjects.clear();
        snapshots.clear();
        leakCandidates.clear();
        
        // Take initial snapshot
        takeMemorySnapshot();
        lastSnapshotTime = System.currentTimeMillis();
        
        active.set(true);
        
        logManager.info("MemoryProfiler", "Memory profiling started");
    }
    
    @Override
    public void stop() {
        if (!active.get()) {
            return;
        }
        
        logManager.info("MemoryProfiler", "Stopping memory profiling");
        
        // Take final snapshot
        takeMemorySnapshot();
        
        // Run final leak detection
        if (config.isLeakDetection()) {
            detectMemoryLeaks();
        }
        
        active.set(false);
        
        logManager.info("MemoryProfiler", "Memory profiling stopped");
    }
    
    @Override
    public void update(float deltaTime) {
        if (!active.get()) {
            return;
        }
        
        // Update memory usage tracking
        updateMemoryUsage();
        
        // Update GC tracking
        updateGCTracking();
        
        // Take periodic snapshots
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSnapshotTime >= config.getMemorySnapshotIntervalMs()) {
            takeMemorySnapshot();
            lastSnapshotTime = currentTime;
        }
        
        // Run periodic leak detection
        if (config.isLeakDetection() && snapshots.size() >= 3) {
            detectMemoryLeaks();
        }
    }
    
    @Override
    public ProfilerData collectData() {
        ProfilerData data = new ProfilerData("memory");
        
        // Current memory usage
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        data.addMetric("heapUsedMB", heapUsage.getUsed() / (1024 * 1024));
        data.addMetric("heapMaxMB", heapUsage.getMax() / (1024 * 1024));
        data.addMetric("heapCommittedMB", heapUsage.getCommitted() / (1024 * 1024));
        data.addMetric("heapUsagePercent", (double) heapUsage.getUsed() / heapUsage.getMax() * 100.0);
        
        data.addMetric("nonHeapUsedMB", nonHeapUsage.getUsed() / (1024 * 1024));
        data.addMetric("nonHeapMaxMB", nonHeapUsage.getMax() / (1024 * 1024));
        data.addMetric("nonHeapCommittedMB", nonHeapUsage.getCommitted() / (1024 * 1024));
        
        // Peak usage
        data.addMetric("peakHeapUsageMB", peakHeapUsage.get() / (1024 * 1024));
        data.addMetric("peakNonHeapUsageMB", peakNonHeapUsage.get() / (1024 * 1024));
        
        // Allocation tracking
        if (config.isAllocationTracking()) {
            collectAllocationMetrics(data);
        }
        
        // GC metrics
        collectGCMetrics(data);
        
        // Leak detection results
        if (config.isLeakDetection()) {
            collectLeakDetectionMetrics(data);
        }
        
        // Snapshot metrics
        data.addMetric("snapshotCount", snapshots.size());
        if (!snapshots.isEmpty()) {
            MemorySnapshot latest = snapshots.get(snapshots.size() - 1);
            data.addMetric("latestSnapshotTime", latest.timestamp);
            data.addMetric("memoryGrowthMB", calculateMemoryGrowth());
        }
        
        // Add metadata
        data.addMetadata("allocationTracking", config.isAllocationTracking());
        data.addMetadata("leakDetection", config.isLeakDetection());
        data.addMetadata("heapAnalysis", config.isHeapAnalysis());
        data.addMetadata("snapshotInterval", config.getMemorySnapshotIntervalMs());
        
        return data;
    }
    
    @Override
    public void reset() {
        logManager.info("MemoryProfiler", "Resetting memory profiler");
        
        totalAllocations.set(0);
        totalDeallocations.set(0);
        peakHeapUsage.set(0);
        peakNonHeapUsage.set(0);
        
        allocationProfiles.clear();
        trackedObjects.clear();
        snapshots.clear();
        leakCandidates.clear();
        
        // Reset GC tracking
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String gcName = gcBean.getName();
            lastGcCounts.put(gcName, gcBean.getCollectionCount());
            lastGcTimes.put(gcName, gcBean.getCollectionTime());
            gcProfiles.get(gcName).reset();
        }
    }
    
    @Override
    public void cleanup() {
        logManager.info("MemoryProfiler", "Cleaning up memory profiler");
        
        stop();
        
        allocationProfiles.clear();
        trackedObjects.clear();
        snapshots.clear();
        leakCandidates.clear();
        gcProfiles.clear();
        
        logManager.info("MemoryProfiler", "Memory profiler cleanup complete");
    }
    
    @Override
    public boolean isActive() {
        return active.get();
    }
    
    @Override
    public String getProfilerType() {
        return "memory";
    }
    
    /**
     * Track an object allocation.
     */
    public void trackAllocation(String type, Object object, long size) {
        if (!active.get() || !config.isAllocationTracking()) {
            return;
        }
        
        totalAllocations.incrementAndGet();
        
        // Update allocation profile
        AllocationProfile profile = allocationProfiles.computeIfAbsent(type, 
                                                                      k -> new AllocationProfile(k));
        profile.recordAllocation(size);
        
        // Track object reference for leak detection
        if (config.isLeakDetection()) {
            ObjectReference ref = new ObjectReference(object, type, size, System.currentTimeMillis());
            trackedObjects.add(ref);
        }
        
        // Record metrics
        metricsCollector.incrementCounter("memory.allocations." + type);
        metricsCollector.incrementCounter("memory.allocatedBytes", size);
    }
    
    /**
     * Track an object deallocation.
     */
    public void trackDeallocation(String type, long size) {
        if (!active.get() || !config.isAllocationTracking()) {
            return;
        }
        
        totalDeallocations.incrementAndGet();
        
        // Update allocation profile
        AllocationProfile profile = allocationProfiles.get(type);
        if (profile != null) {
            profile.recordDeallocation(size);
        }
        
        // Record metrics
        metricsCollector.incrementCounter("memory.deallocations." + type);
        metricsCollector.incrementCounter("memory.deallocatedBytes", size);
    }
    
    private void updateMemoryUsage() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        // Update peak usage
        peakHeapUsage.updateAndGet(current -> Math.max(current, heapUsage.getUsed()));
        peakNonHeapUsage.updateAndGet(current -> Math.max(current, nonHeapUsage.getUsed()));
        
        // Record current usage
        metricsCollector.recordTime("memory.heap.used", heapUsage.getUsed() / (1024 * 1024));
        metricsCollector.recordTime("memory.nonheap.used", nonHeapUsage.getUsed() / (1024 * 1024));
    }
    
    private void updateGCTracking() {
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String gcName = gcBean.getName();
            long currentCount = gcBean.getCollectionCount();
            long currentTime = gcBean.getCollectionTime();
            
            Long lastCount = lastGcCounts.get(gcName);
            Long lastTime = lastGcTimes.get(gcName);
            
            if (lastCount != null && lastTime != null) {
                long countDelta = currentCount - lastCount;
                long timeDelta = currentTime - lastTime;
                
                if (countDelta > 0) {
                    GCProfile profile = gcProfiles.get(gcName);
                    if (profile != null) {
                        profile.recordCollection(countDelta, timeDelta);
                    }
                    
                    // Record metrics
                    metricsCollector.incrementCounter("gc." + gcName + ".collections", countDelta);
                    metricsCollector.recordTime("gc." + gcName + ".time", timeDelta);
                }
            }
            
            lastGcCounts.put(gcName, currentCount);
            lastGcTimes.put(gcName, currentTime);
        }
    }
    
    private void takeMemorySnapshot() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        MemorySnapshot snapshot = new MemorySnapshot();
        snapshot.timestamp = System.currentTimeMillis();
        snapshot.heapUsed = heapUsage.getUsed();
        snapshot.heapMax = heapUsage.getMax();
        snapshot.heapCommitted = heapUsage.getCommitted();
        snapshot.nonHeapUsed = nonHeapUsage.getUsed();
        snapshot.nonHeapMax = nonHeapUsage.getMax();
        snapshot.nonHeapCommitted = nonHeapUsage.getCommitted();
        snapshot.objectCount = trackedObjects.size();
        
        // Calculate allocation rates
        if (!snapshots.isEmpty()) {
            MemorySnapshot previous = snapshots.get(snapshots.size() - 1);
            long timeDelta = snapshot.timestamp - previous.timestamp;
            if (timeDelta > 0) {
                snapshot.allocationRate = (double)(snapshot.heapUsed - previous.heapUsed) / timeDelta * 1000.0; // bytes/sec
            }
        }
        
        snapshots.add(snapshot);
        
        // Keep only recent snapshots to prevent memory growth
        if (snapshots.size() > 100) {
            snapshots.remove(0);
        }
        
        logManager.debug("MemoryProfiler", "Memory snapshot taken",
                        "heapUsedMB", snapshot.heapUsed / (1024 * 1024),
                        "objectCount", snapshot.objectCount,
                        "allocationRate", snapshot.allocationRate);
    }
    
    private void detectMemoryLeaks() {
        if (snapshots.size() < 3) {
            return;
        }
        
        leakDetectionRuns.incrementAndGet();
        
        // Analyze memory growth patterns
        MemorySnapshot current = snapshots.get(snapshots.size() - 1);
        MemorySnapshot previous = snapshots.get(snapshots.size() - 2);
        MemorySnapshot older = snapshots.get(snapshots.size() - 3);
        
        // Check for consistent memory growth
        long growth1 = current.heapUsed - previous.heapUsed;
        long growth2 = previous.heapUsed - older.heapUsed;
        
        if (growth1 > 0 && growth2 > 0) {
            double growthRate = (double)growth1 / (current.timestamp - previous.timestamp) * 1000.0; // bytes/sec
            
            if (growthRate > 1024 * 1024) { // More than 1MB/sec growth
                String leakId = "heap_growth_" + System.currentTimeMillis();
                LeakCandidate leak = leakCandidates.computeIfAbsent(leakId, k -> new LeakCandidate(k));
                leak.recordGrowth(growthRate, current.timestamp);
                
                logManager.warn("MemoryProfiler", "Potential memory leak detected",
                               "growthRate", growthRate / (1024 * 1024) + " MB/sec",
                               "heapUsed", current.heapUsed / (1024 * 1024) + " MB");
            }
        }
        
        // Analyze object allocation patterns
        analyzeObjectLeaks();
    }
    
    private void analyzeObjectLeaks() {
        Map<String, Integer> typeCounts = new ConcurrentHashMap<>();
        
        // Count objects by type
        for (ObjectReference ref : trackedObjects) {
            typeCounts.merge(ref.type, 1, Integer::sum);
        }
        
        // Look for types with excessive object counts
        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            String type = entry.getKey();
            int count = entry.getValue();
            
            if (count > 1000) { // Threshold for potential leak
                String leakId = "object_leak_" + type;
                LeakCandidate leak = leakCandidates.computeIfAbsent(leakId, k -> new LeakCandidate(k));
                leak.recordObjectCount(count, System.currentTimeMillis());
                
                logManager.warn("MemoryProfiler", "Potential object leak detected",
                               "type", type,
                               "count", count);
            }
        }
    }
    
    private double calculateMemoryGrowth() {
        if (snapshots.size() < 2) {
            return 0.0;
        }
        
        MemorySnapshot first = snapshots.get(0);
        MemorySnapshot last = snapshots.get(snapshots.size() - 1);
        
        return (double)(last.heapUsed - first.heapUsed) / (1024 * 1024); // MB
    }
    
    private void collectAllocationMetrics(ProfilerData data) {
        data.addMetric("totalAllocations", totalAllocations.get());
        data.addMetric("totalDeallocations", totalDeallocations.get());
        data.addMetric("netAllocations", totalAllocations.get() - totalDeallocations.get());
        
        // Allocation profiles
        int profileCount = allocationProfiles.size();
        data.addMetric("allocationProfileCount", profileCount);
        
        if (profileCount > 0) {
            // Find most allocated type
            AllocationProfile topProfile = allocationProfiles.values().stream()
                .max((a, b) -> Long.compare(a.totalAllocated, b.totalAllocated))
                .orElse(null);
            
            if (topProfile != null) {
                data.addMetric("topAllocationType", topProfile.type);
                data.addMetric("topAllocationBytes", topProfile.totalAllocated);
                data.addMetric("topAllocationCount", topProfile.allocationCount);
            }
        }
    }
    
    private void collectGCMetrics(ProfilerData data) {
        long totalCollections = 0;
        long totalGcTime = 0;
        
        for (GCProfile profile : gcProfiles.values()) {
            totalCollections += profile.totalCollections;
            totalGcTime += profile.totalTime;
        }
        
        data.addMetric("totalGcCollections", totalCollections);
        data.addMetric("totalGcTimeMs", totalGcTime);
        
        if (totalCollections > 0) {
            data.addMetric("averageGcTimeMs", (double)totalGcTime / totalCollections);
        }
        
        // Individual GC metrics
        for (Map.Entry<String, GCProfile> entry : gcProfiles.entrySet()) {
            String gcName = entry.getKey();
            GCProfile profile = entry.getValue();
            
            data.addMetric("gc." + gcName + ".collections", profile.totalCollections);
            data.addMetric("gc." + gcName + ".timeMs", profile.totalTime);
            if (profile.totalCollections > 0) {
                data.addMetric("gc." + gcName + ".avgTimeMs", (double)profile.totalTime / profile.totalCollections);
            }
        }
    }
    
    private void collectLeakDetectionMetrics(ProfilerData data) {
        data.addMetric("leakDetectionRuns", leakDetectionRuns.get());
        data.addMetric("leakCandidateCount", leakCandidates.size());
        data.addMetric("trackedObjectCount", trackedObjects.size());
        
        if (!leakCandidates.isEmpty()) {
            // Most severe leak candidate
            LeakCandidate severeLeak = leakCandidates.values().stream()
                .max((a, b) -> Double.compare(a.severity, b.severity))
                .orElse(null);
            
            if (severeLeak != null) {
                data.addMetric("severestLeakId", severeLeak.id);
                data.addMetric("severestLeakSeverity", severeLeak.severity);
            }
        }
    }
    
    // Helper classes
    private static class AllocationProfile {
        final String type;
        long allocationCount = 0;
        long deallocationCount = 0;
        long totalAllocated = 0;
        long totalDeallocated = 0;
        long peakAllocated = 0;
        
        AllocationProfile(String type) {
            this.type = type;
        }
        
        void recordAllocation(long size) {
            allocationCount++;
            totalAllocated += size;
            peakAllocated = Math.max(peakAllocated, totalAllocated - totalDeallocated);
        }
        
        void recordDeallocation(long size) {
            deallocationCount++;
            totalDeallocated += size;
        }
    }
    
    private static class GCProfile {
        final String name;
        long totalCollections = 0;
        long totalTime = 0;
        long minTime = Long.MAX_VALUE;
        long maxTime = 0;
        
        GCProfile(String name) {
            this.name = name;
        }
        
        void recordCollection(long collections, long time) {
            totalCollections += collections;
            totalTime += time;
            
            if (collections > 0) {
                long avgTime = time / collections;
                minTime = Math.min(minTime, avgTime);
                maxTime = Math.max(maxTime, avgTime);
            }
        }
        
        void reset() {
            totalCollections = 0;
            totalTime = 0;
            minTime = Long.MAX_VALUE;
            maxTime = 0;
        }
    }
    
    private static class MemorySnapshot {
        long timestamp;
        long heapUsed;
        long heapMax;
        long heapCommitted;
        long nonHeapUsed;
        long nonHeapMax;
        long nonHeapCommitted;
        int objectCount;
        double allocationRate; // bytes per second
    }
    
    private static class ObjectReference implements Comparable<ObjectReference> {
        final int hashCode;
        final String type;
        final long size;
        final long timestamp;
        
        ObjectReference(Object object, String type, long size, long timestamp) {
            this.hashCode = System.identityHashCode(object);
            this.type = type;
            this.size = size;
            this.timestamp = timestamp;
        }
        
        @Override
        public int compareTo(ObjectReference other) {
            int result = Integer.compare(this.hashCode, other.hashCode);
            if (result == 0) {
                result = Long.compare(this.timestamp, other.timestamp);
            }
            return result;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ObjectReference)) return false;
            ObjectReference other = (ObjectReference) obj;
            return hashCode == other.hashCode && timestamp == other.timestamp;
        }
        
        @Override
        public int hashCode() {
            return Integer.hashCode(hashCode) ^ Long.hashCode(timestamp);
        }
    }
    
    private static class LeakCandidate {
        final String id;
        double severity = 0.0;
        long lastUpdateTime = 0;
        int detectionCount = 0;
        
        LeakCandidate(String id) {
            this.id = id;
        }
        
        void recordGrowth(double growthRate, long timestamp) {
            detectionCount++;
            severity = Math.max(severity, growthRate / (1024 * 1024)); // MB/sec
            lastUpdateTime = timestamp;
        }
        
        void recordObjectCount(int count, long timestamp) {
            detectionCount++;
            severity = Math.max(severity, count / 1000.0); // Normalize to thousands
            lastUpdateTime = timestamp;
        }
    }
}