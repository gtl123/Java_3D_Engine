package engine.assets;

import engine.logging.LogManager;
import engine.logging.MetricsCollector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dependency management and resolution system for assets.
 * Provides topological sorting, circular dependency detection, and
 * efficient dependency resolution for complex asset hierarchies.
 */
public class AssetDependencyGraph {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = LogManager.getInstance().getMetricsCollector();
    
    // Graph structure
    private final ConcurrentHashMap<String, Set<String>> dependencies; // assetId -> set of dependencies
    private final ConcurrentHashMap<String, Set<String>> dependents;   // assetId -> set of dependents
    private final ConcurrentHashMap<String, AssetNode> nodes;          // assetId -> node info
    private final ReadWriteLock graphLock = new ReentrantReadWriteLock();
    
    // Statistics
    private final AtomicLong totalNodes = new AtomicLong(0);
    private final AtomicLong totalEdges = new AtomicLong(0);
    private final AtomicLong circularDependencies = new AtomicLong(0);
    private final AtomicLong resolutionOperations = new AtomicLong(0);
    
    /**
     * Asset node information in the dependency graph.
     */
    public static class AssetNode {
        private final String assetId;
        private final AssetType type;
        private final int priority;
        private volatile long lastAccessed;
        private volatile boolean resolved;
        private volatile boolean loading;
        
        AssetNode(String assetId, AssetType type, int priority) {
            this.assetId = assetId;
            this.type = type;
            this.priority = priority;
            this.lastAccessed = System.currentTimeMillis();
            this.resolved = false;
            this.loading = false;
        }
        
        public String getAssetId() { return assetId; }
        public AssetType getType() { return type; }
        public int getPriority() { return priority; }
        public long getLastAccessed() { return lastAccessed; }
        public boolean isResolved() { return resolved; }
        public boolean isLoading() { return loading; }
        
        void markAccessed() { this.lastAccessed = System.currentTimeMillis(); }
        void setResolved(boolean resolved) { this.resolved = resolved; }
        void setLoading(boolean loading) { this.loading = loading; }
        
        @Override
        public String toString() {
            return String.format("AssetNode{id='%s', type=%s, priority=%d, resolved=%s, loading=%s}",
                               assetId, type, priority, resolved, loading);
        }
    }
    
    /**
     * Dependency resolution result.
     */
    public static class ResolutionResult {
        private final List<String> loadOrder;
        private final Set<String> circularDependencies;
        private final Map<String, Set<String>> dependencyLevels;
        private final long resolutionTime;
        
        ResolutionResult(List<String> loadOrder, Set<String> circularDependencies,
                        Map<String, Set<String>> dependencyLevels, long resolutionTime) {
            this.loadOrder = Collections.unmodifiableList(new ArrayList<>(loadOrder));
            this.circularDependencies = Collections.unmodifiableSet(new HashSet<>(circularDependencies));
            this.dependencyLevels = Collections.unmodifiableMap(new HashMap<>(dependencyLevels));
            this.resolutionTime = resolutionTime;
        }
        
        public List<String> getLoadOrder() { return loadOrder; }
        public Set<String> getCircularDependencies() { return circularDependencies; }
        public Map<String, Set<String>> getDependencyLevels() { return dependencyLevels; }
        public long getResolutionTime() { return resolutionTime; }
        public boolean hasCircularDependencies() { return !circularDependencies.isEmpty(); }
        
        @Override
        public String toString() {
            return String.format("ResolutionResult{loadOrder=%d assets, circular=%d, levels=%d, time=%dms}",
                               loadOrder.size(), circularDependencies.size(), dependencyLevels.size(), resolutionTime);
        }
    }
    
    /**
     * Dependency statistics.
     */
    public static class DependencyStatistics {
        public final long totalNodes;
        public final long totalEdges;
        public final long circularDependencies;
        public final long resolutionOperations;
        public final double averageNodeDegree;
        public final int maxDependencyDepth;
        
        DependencyStatistics(long totalNodes, long totalEdges, long circularDependencies,
                           long resolutionOperations, double averageNodeDegree, int maxDependencyDepth) {
            this.totalNodes = totalNodes;
            this.totalEdges = totalEdges;
            this.circularDependencies = circularDependencies;
            this.resolutionOperations = resolutionOperations;
            this.averageNodeDegree = averageNodeDegree;
            this.maxDependencyDepth = maxDependencyDepth;
        }
        
        @Override
        public String toString() {
            return String.format("DependencyStats{nodes=%d, edges=%d, circular=%d, avgDegree=%.1f, maxDepth=%d}",
                               totalNodes, totalEdges, circularDependencies, averageNodeDegree, maxDependencyDepth);
        }
    }
    
    /**
     * Create a new asset dependency graph.
     */
    public AssetDependencyGraph() {
        this.dependencies = new ConcurrentHashMap<>();
        this.dependents = new ConcurrentHashMap<>();
        this.nodes = new ConcurrentHashMap<>();
        
        logManager.info("AssetDependencyGraph", "Asset dependency graph initialized");
    }
    
    /**
     * Add an asset node to the graph.
     * @param assetId Asset identifier
     * @param type Asset type
     * @param priority Asset priority
     */
    public void addNode(String assetId, AssetType type, int priority) {
        if (assetId == null || type == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        
        graphLock.writeLock().lock();
        try {
            AssetNode existingNode = nodes.get(assetId);
            if (existingNode == null) {
                AssetNode node = new AssetNode(assetId, type, priority);
                nodes.put(assetId, node);
                dependencies.putIfAbsent(assetId, ConcurrentHashMap.newKeySet());
                dependents.putIfAbsent(assetId, ConcurrentHashMap.newKeySet());
                
                totalNodes.incrementAndGet();
                
                logManager.debug("AssetDependencyGraph", "Asset node added",
                               "assetId", assetId, "type", type.getTypeName(), "priority", priority);
            } else {
                existingNode.markAccessed();
            }
        } finally {
            graphLock.writeLock().unlock();
        }
    }
    
    /**
     * Remove an asset node from the graph.
     * @param assetId Asset identifier
     * @return true if removed, false if not found
     */
    public boolean removeNode(String assetId) {
        if (assetId == null) return false;
        
        graphLock.writeLock().lock();
        try {
            AssetNode node = nodes.remove(assetId);
            if (node != null) {
                // Remove all dependencies and dependents
                Set<String> assetDependencies = dependencies.remove(assetId);
                Set<String> assetDependents = dependents.remove(assetId);
                
                // Update reverse references
                if (assetDependencies != null) {
                    for (String dependency : assetDependencies) {
                        Set<String> depDependents = dependents.get(dependency);
                        if (depDependents != null) {
                            depDependents.remove(assetId);
                        }
                        totalEdges.decrementAndGet();
                    }
                }
                
                if (assetDependents != null) {
                    for (String dependent : assetDependents) {
                        Set<String> depDependencies = dependencies.get(dependent);
                        if (depDependencies != null) {
                            depDependencies.remove(assetId);
                        }
                    }
                }
                
                totalNodes.decrementAndGet();
                
                logManager.debug("AssetDependencyGraph", "Asset node removed", "assetId", assetId);
                return true;
            }
            return false;
        } finally {
            graphLock.writeLock().unlock();
        }
    }
    
    /**
     * Add a dependency relationship between two assets.
     * @param assetId Asset that depends on another
     * @param dependencyId Asset that is depended upon
     */
    public void addDependency(String assetId, String dependencyId) {
        if (assetId == null || dependencyId == null || assetId.equals(dependencyId)) {
            return; // Ignore self-dependencies
        }
        
        graphLock.writeLock().lock();
        try {
            // Ensure both nodes exist
            if (!nodes.containsKey(assetId) || !nodes.containsKey(dependencyId)) {
                logManager.warn("AssetDependencyGraph", "Cannot add dependency - nodes not found",
                               "assetId", assetId, "dependencyId", dependencyId);
                return;
            }
            
            // Add dependency
            Set<String> assetDeps = dependencies.get(assetId);
            if (assetDeps != null && assetDeps.add(dependencyId)) {
                // Add reverse reference
                Set<String> depDependents = dependents.get(dependencyId);
                if (depDependents != null) {
                    depDependents.add(assetId);
                }
                
                totalEdges.incrementAndGet();
                
                logManager.debug("AssetDependencyGraph", "Dependency added",
                               "assetId", assetId, "dependencyId", dependencyId);
                
                // Check for circular dependencies
                if (hasCircularDependency(assetId, dependencyId)) {
                    circularDependencies.incrementAndGet();
                    logManager.warn("AssetDependencyGraph", "Circular dependency detected",
                                   "assetId", assetId, "dependencyId", dependencyId);
                }
            }
        } finally {
            graphLock.writeLock().unlock();
        }
    }
    
    /**
     * Remove a dependency relationship.
     * @param assetId Asset that depends on another
     * @param dependencyId Asset that is depended upon
     * @return true if removed, false if not found
     */
    public boolean removeDependency(String assetId, String dependencyId) {
        if (assetId == null || dependencyId == null) return false;
        
        graphLock.writeLock().lock();
        try {
            Set<String> assetDeps = dependencies.get(assetId);
            if (assetDeps != null && assetDeps.remove(dependencyId)) {
                // Remove reverse reference
                Set<String> depDependents = dependents.get(dependencyId);
                if (depDependents != null) {
                    depDependents.remove(assetId);
                }
                
                totalEdges.decrementAndGet();
                
                logManager.debug("AssetDependencyGraph", "Dependency removed",
                               "assetId", assetId, "dependencyId", dependencyId);
                return true;
            }
            return false;
        } finally {
            graphLock.writeLock().unlock();
        }
    }
    
    /**
     * Get direct dependencies of an asset.
     * @param assetId Asset identifier
     * @return Set of dependency IDs
     */
    public Set<String> getDependencies(String assetId) {
        if (assetId == null) return Collections.emptySet();
        
        graphLock.readLock().lock();
        try {
            Set<String> deps = dependencies.get(assetId);
            return deps != null ? new HashSet<>(deps) : Collections.emptySet();
        } finally {
            graphLock.readLock().unlock();
        }
    }
    
    /**
     * Get direct dependents of an asset.
     * @param assetId Asset identifier
     * @return Set of dependent IDs
     */
    public Set<String> getDependents(String assetId) {
        if (assetId == null) return Collections.emptySet();
        
        graphLock.readLock().lock();
        try {
            Set<String> deps = dependents.get(assetId);
            return deps != null ? new HashSet<>(deps) : Collections.emptySet();
        } finally {
            graphLock.readLock().unlock();
        }
    }
    
    /**
     * Get all transitive dependencies of an asset.
     * @param assetId Asset identifier
     * @return Set of all dependency IDs (direct and indirect)
     */
    public Set<String> getAllDependencies(String assetId) {
        if (assetId == null) return Collections.emptySet();
        
        graphLock.readLock().lock();
        try {
            Set<String> allDeps = new HashSet<>();
            Set<String> visited = new HashSet<>();
            collectDependencies(assetId, allDeps, visited);
            return allDeps;
        } finally {
            graphLock.readLock().unlock();
        }
    }
    
    /**
     * Resolve dependencies and return optimal loading order.
     * @param assetIds Assets to resolve dependencies for
     * @return Resolution result with loading order
     */
    public ResolutionResult resolveDependencies(Collection<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return new ResolutionResult(Collections.emptyList(), Collections.emptySet(),
                                      Collections.emptyMap(), 0);
        }
        
        long startTime = System.currentTimeMillis();
        
        graphLock.readLock().lock();
        try {
            // Collect all assets that need to be loaded (including dependencies)
            Set<String> allAssets = new HashSet<>();
            Set<String> circularDeps = new HashSet<>();
            
            for (String assetId : assetIds) {
                collectAllDependencies(assetId, allAssets, circularDeps);
            }
            
            // Perform topological sort
            List<String> loadOrder = topologicalSort(allAssets, circularDeps);
            
            // Create dependency levels for parallel loading
            Map<String, Set<String>> dependencyLevels = createDependencyLevels(loadOrder);
            
            long resolutionTime = System.currentTimeMillis() - startTime;
            resolutionOperations.incrementAndGet();
            
            ResolutionResult result = new ResolutionResult(loadOrder, circularDeps, dependencyLevels, resolutionTime);
            
            metricsCollector.incrementCounter("asset.dependency.resolutions");
            metricsCollector.recordTimer("asset.dependency.resolution.time", resolutionTime);
            
            logManager.info("AssetDependencyGraph", "Dependencies resolved",
                           "requestedAssets", assetIds.size(),
                           "totalAssets", allAssets.size(),
                           "circularDependencies", circularDeps.size(),
                           "resolutionTime", resolutionTime);
            
            return result;
            
        } finally {
            graphLock.readLock().unlock();
        }
    }
    
    /**
     * Check if there's a circular dependency between two assets.
     * @param assetId First asset
     * @param dependencyId Second asset
     * @return true if circular dependency exists
     */
    public boolean hasCircularDependency(String assetId, String dependencyId) {
        if (assetId == null || dependencyId == null) return false;
        
        graphLock.readLock().lock();
        try {
            return hasPath(dependencyId, assetId, new HashSet<>());
        } finally {
            graphLock.readLock().unlock();
        }
    }
    
    /**
     * Get asset node information.
     * @param assetId Asset identifier
     * @return Asset node, or null if not found
     */
    public AssetNode getNode(String assetId) {
        return nodes.get(assetId);
    }
    
    /**
     * Mark an asset as resolved.
     * @param assetId Asset identifier
     */
    public void markResolved(String assetId) {
        AssetNode node = nodes.get(assetId);
        if (node != null) {
            node.setResolved(true);
            node.setLoading(false);
            node.markAccessed();
        }
    }
    
    /**
     * Mark an asset as loading.
     * @param assetId Asset identifier
     */
    public void markLoading(String assetId) {
        AssetNode node = nodes.get(assetId);
        if (node != null) {
            node.setLoading(true);
            node.markAccessed();
        }
    }
    
    /**
     * Get dependency statistics.
     */
    public DependencyStatistics getStatistics() {
        graphLock.readLock().lock();
        try {
            long nodeCount = totalNodes.get();
            long edgeCount = totalEdges.get();
            double avgDegree = nodeCount > 0 ? (double) edgeCount / nodeCount : 0.0;
            int maxDepth = calculateMaxDependencyDepth();
            
            return new DependencyStatistics(
                nodeCount, edgeCount, circularDependencies.get(),
                resolutionOperations.get(), avgDegree, maxDepth
            );
        } finally {
            graphLock.readLock().unlock();
        }
    }
    
    /**
     * Clear all dependencies.
     */
    public void clear() {
        graphLock.writeLock().lock();
        try {
            dependencies.clear();
            dependents.clear();
            nodes.clear();
            totalNodes.set(0);
            totalEdges.set(0);
            
            logManager.info("AssetDependencyGraph", "Dependency graph cleared");
        } finally {
            graphLock.writeLock().unlock();
        }
    }
    
    private void collectDependencies(String assetId, Set<String> allDeps, Set<String> visited) {
        if (visited.contains(assetId)) return;
        visited.add(assetId);
        
        Set<String> deps = dependencies.get(assetId);
        if (deps != null) {
            for (String dep : deps) {
                allDeps.add(dep);
                collectDependencies(dep, allDeps, visited);
            }
        }
    }
    
    private void collectAllDependencies(String assetId, Set<String> allAssets, Set<String> circularDeps) {
        if (allAssets.contains(assetId)) return;
        
        allAssets.add(assetId);
        Set<String> deps = dependencies.get(assetId);
        if (deps != null) {
            for (String dep : deps) {
                if (hasCircularDependency(assetId, dep)) {
                    circularDeps.add(assetId);
                    circularDeps.add(dep);
                }
                collectAllDependencies(dep, allAssets, circularDeps);
            }
        }
    }
    
    private List<String> topologicalSort(Set<String> assets, Set<String> circularDeps) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        
        // Sort by priority first
        List<String> sortedAssets = new ArrayList<>(assets);
        sortedAssets.sort((a, b) -> {
            AssetNode nodeA = nodes.get(a);
            AssetNode nodeB = nodes.get(b);
            int priorityA = nodeA != null ? nodeA.getPriority() : 0;
            int priorityB = nodeB != null ? nodeB.getPriority() : 0;
            return Integer.compare(priorityB, priorityA); // Higher priority first
        });
        
        for (String assetId : sortedAssets) {
            if (!visited.contains(assetId)) {
                topologicalSortVisit(assetId, visited, visiting, result, circularDeps);
            }
        }
        
        return result;
    }
    
    private void topologicalSortVisit(String assetId, Set<String> visited, Set<String> visiting,
                                    List<String> result, Set<String> circularDeps) {
        if (visiting.contains(assetId)) {
            circularDeps.add(assetId);
            return;
        }
        
        if (visited.contains(assetId)) return;
        
        visiting.add(assetId);
        
        Set<String> deps = dependencies.get(assetId);
        if (deps != null) {
            for (String dep : deps) {
                topologicalSortVisit(dep, visited, visiting, result, circularDeps);
            }
        }
        
        visiting.remove(assetId);
        visited.add(assetId);
        result.add(0, assetId); // Add to front for reverse topological order
    }
    
    private Map<String, Set<String>> createDependencyLevels(List<String> loadOrder) {
        Map<String, Set<String>> levels = new HashMap<>();
        Map<String, Integer> assetLevels = new HashMap<>();
        
        // Calculate level for each asset
        for (String assetId : loadOrder) {
            int level = calculateAssetLevel(assetId, assetLevels);
            assetLevels.put(assetId, level);
            
            levels.computeIfAbsent("level_" + level, k -> new HashSet<>()).add(assetId);
        }
        
        return levels;
    }
    
    private int calculateAssetLevel(String assetId, Map<String, Integer> assetLevels) {
        Integer cachedLevel = assetLevels.get(assetId);
        if (cachedLevel != null) return cachedLevel;
        
        Set<String> deps = dependencies.get(assetId);
        if (deps == null || deps.isEmpty()) {
            return 0; // No dependencies = level 0
        }
        
        int maxDepLevel = -1;
        for (String dep : deps) {
            int depLevel = calculateAssetLevel(dep, assetLevels);
            maxDepLevel = Math.max(maxDepLevel, depLevel);
        }
        
        return maxDepLevel + 1;
    }
    
    private boolean hasPath(String from, String to, Set<String> visited) {
        if (from.equals(to)) return true;
        if (visited.contains(from)) return false;
        
        visited.add(from);
        Set<String> deps = dependencies.get(from);
        if (deps != null) {
            for (String dep : deps) {
                if (hasPath(dep, to, visited)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private int calculateMaxDependencyDepth() {
        int maxDepth = 0;
        for (String assetId : nodes.keySet()) {
            maxDepth = Math.max(maxDepth, calculateDepth(assetId, new HashSet<>()));
        }
        return maxDepth;
    }
    
    private int calculateDepth(String assetId, Set<String> visited) {
        if (visited.contains(assetId)) return 0; // Circular dependency
        
        visited.add(assetId);
        Set<String> deps = dependencies.get(assetId);
        if (deps == null || deps.isEmpty()) {
            return 0;
        }
        
        int maxDepth = 0;
        for (String dep : deps) {
            maxDepth = Math.max(maxDepth, calculateDepth(dep, new HashSet<>(visited)));
        }
        
        return maxDepth + 1;
    }
}