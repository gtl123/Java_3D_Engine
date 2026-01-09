package engine.plugins;

import engine.logging.LogManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Default implementation of PluginRegistry.
 * Manages plugin registration, discovery, and dependency resolution.
 */
public class DefaultPluginRegistry implements PluginRegistry {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final ConcurrentHashMap<String, Plugin> plugins = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PluginMetadata> pluginMetadata = new ConcurrentHashMap<>();
    private final List<RegistryListener> listeners = new CopyOnWriteArrayList<>();
    
    private volatile boolean initialized = false;
    
    /**
     * Initialize the plugin registry.
     */
    public void initialize() {
        if (initialized) {
            return;
        }
        
        logManager.info("PluginRegistry", "Initializing plugin registry");
        initialized = true;
        logManager.info("PluginRegistry", "Plugin registry initialized");
    }
    
    /**
     * Shutdown the plugin registry.
     */
    public void shutdown() {
        if (!initialized) {
            return;
        }
        
        logManager.info("PluginRegistry", "Shutting down plugin registry");
        plugins.clear();
        pluginMetadata.clear();
        listeners.clear();
        initialized = false;
        logManager.info("PluginRegistry", "Plugin registry shutdown complete");
    }
    
    @Override
    public void registerPlugin(Plugin plugin) throws PluginException {
        if (!initialized) {
            throw new PluginException("Plugin registry not initialized");
        }
        
        String pluginId = plugin.getMetadata().getId();
        
        if (plugins.containsKey(pluginId)) {
            throw new PluginException("Plugin already registered: " + pluginId);
        }
        
        plugins.put(pluginId, plugin);
        pluginMetadata.put(pluginId, plugin.getMetadata());
        
        logManager.info("PluginRegistry", "Plugin registered", 
                       "pluginId", pluginId, "name", plugin.getMetadata().getName());
        
        // Notify listeners
        listeners.forEach(l -> l.onPluginRegistered(plugin));
    }
    
    @Override
    public boolean unregisterPlugin(String pluginId) {
        Plugin plugin = plugins.remove(pluginId);
        if (plugin != null) {
            pluginMetadata.remove(pluginId);
            
            logManager.info("PluginRegistry", "Plugin unregistered", "pluginId", pluginId);
            
            // Notify listeners
            listeners.forEach(l -> l.onPluginUnregistered(pluginId));
            return true;
        }
        return false;
    }
    
    @Override
    public Optional<Plugin> getPlugin(String pluginId) {
        return Optional.ofNullable(plugins.get(pluginId));
    }
    
    @Override
    public Optional<PluginMetadata> getPluginMetadata(String pluginId) {
        return Optional.ofNullable(pluginMetadata.get(pluginId));
    }
    
    @Override
    public Collection<Plugin> getAllPlugins() {
        return new ArrayList<>(plugins.values());
    }
    
    @Override
    public Collection<Plugin> getPluginsByState(PluginState state) {
        return plugins.values().stream()
                .filter(plugin -> plugin.getState() == state)
                .collect(Collectors.toList());
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> Collection<T> getPluginsByType(Class<T> type) {
        return plugins.values().stream()
                .filter(type::isInstance)
                .map(plugin -> (T) plugin)
                .collect(Collectors.toList());
    }
    
    @Override
    public Collection<Plugin> getDependentPlugins(String pluginId) {
        return plugins.values().stream()
                .filter(plugin -> plugin.getMetadata().getDependencies().stream()
                        .anyMatch(dep -> dep.getPluginId().equals(pluginId)))
                .collect(Collectors.toList());
    }
    
    @Override
    public List<String> getDependencyChain(String pluginId) {
        List<String> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        buildDependencyChain(pluginId, chain, visited);
        return chain;
    }
    
    private void buildDependencyChain(String pluginId, List<String> chain, Set<String> visited) {
        if (visited.contains(pluginId)) {
            return; // Avoid infinite loops
        }
        
        visited.add(pluginId);
        PluginMetadata metadata = pluginMetadata.get(pluginId);
        
        if (metadata != null) {
            for (PluginMetadata.PluginDependency dep : metadata.getDependencies()) {
                if (!dep.isOptional()) {
                    buildDependencyChain(dep.getPluginId(), chain, visited);
                }
            }
            chain.add(pluginId);
        }
    }
    
    @Override
    public boolean hasPlugin(String pluginId) {
        return plugins.containsKey(pluginId);
    }
    
    @Override
    public boolean isPluginActive(String pluginId) {
        Plugin plugin = plugins.get(pluginId);
        return plugin != null && plugin.getState().isActive();
    }
    
    @Override
    public DependencyValidationResult validateDependencies(String pluginId) {
        PluginMetadata metadata = pluginMetadata.get(pluginId);
        if (metadata == null) {
            return new DependencyValidationResult(false, 
                    Collections.singletonList("Plugin not found: " + pluginId),
                    Collections.emptyList(), Collections.emptyList());
        }
        
        List<String> missingDependencies = new ArrayList<>();
        List<String> conflictingDependencies = new ArrayList<>();
        List<String> circularDependencies = new ArrayList<>();
        
        // Check for missing dependencies
        for (PluginMetadata.PluginDependency dep : metadata.getDependencies()) {
            if (!dep.isOptional() && !hasPlugin(dep.getPluginId())) {
                missingDependencies.add(dep.getPluginId() + ":" + dep.getVersion());
            }
        }
        
        // Check for circular dependencies
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        if (hasCircularDependency(pluginId, visited, recursionStack)) {
            circularDependencies.add(pluginId);
        }
        
        boolean valid = missingDependencies.isEmpty() && conflictingDependencies.isEmpty() && circularDependencies.isEmpty();
        
        return new DependencyValidationResult(valid, missingDependencies, conflictingDependencies, circularDependencies);
    }
    
    private boolean hasCircularDependency(String pluginId, Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(pluginId)) {
            return true;
        }
        
        if (visited.contains(pluginId)) {
            return false;
        }
        
        visited.add(pluginId);
        recursionStack.add(pluginId);
        
        PluginMetadata metadata = pluginMetadata.get(pluginId);
        if (metadata != null) {
            for (PluginMetadata.PluginDependency dep : metadata.getDependencies()) {
                if (!dep.isOptional() && hasCircularDependency(dep.getPluginId(), visited, recursionStack)) {
                    return true;
                }
            }
        }
        
        recursionStack.remove(pluginId);
        return false;
    }
    
    @Override
    public List<String> resolveLoadOrder(Collection<String> pluginIds) throws PluginException {
        List<String> loadOrder = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String pluginId : pluginIds) {
            if (!visited.contains(pluginId)) {
                if (hasCircularDependency(pluginId, new HashSet<>(), new HashSet<>())) {
                    throw new PluginException("Circular dependency detected involving plugin: " + pluginId);
                }
                topologicalSort(pluginId, visited, loadOrder);
            }
        }
        
        return loadOrder;
    }
    
    private void topologicalSort(String pluginId, Set<String> visited, List<String> loadOrder) {
        visited.add(pluginId);
        
        PluginMetadata metadata = pluginMetadata.get(pluginId);
        if (metadata != null) {
            for (PluginMetadata.PluginDependency dep : metadata.getDependencies()) {
                if (!dep.isOptional() && !visited.contains(dep.getPluginId())) {
                    topologicalSort(dep.getPluginId(), visited, loadOrder);
                }
            }
        }
        
        loadOrder.add(pluginId);
    }
    
    @Override
    public Collection<Plugin> searchPlugins(String query) {
        String lowerQuery = query.toLowerCase();
        return plugins.values().stream()
                .filter(plugin -> {
                    PluginMetadata meta = plugin.getMetadata();
                    return meta.getName().toLowerCase().contains(lowerQuery) ||
                           meta.getDescription().toLowerCase().contains(lowerQuery) ||
                           meta.getId().toLowerCase().contains(lowerQuery);
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public Collection<Plugin> getPluginsByAuthor(String author) {
        return plugins.values().stream()
                .filter(plugin -> author.equals(plugin.getMetadata().getAuthor()))
                .collect(Collectors.toList());
    }
    
    @Override
    public RegistryStatistics getStatistics() {
        int totalPlugins = plugins.size();
        int activePlugins = (int) plugins.values().stream()
                .filter(p -> p.getState().isActive())
                .count();
        int failedPlugins = (int) plugins.values().stream()
                .filter(p -> p.getState() == PluginState.FAILED)
                .count();
        
        // For now, assume no dependency conflicts (would need more complex validation)
        int dependencyConflicts = 0;
        
        return new RegistryStatistics(totalPlugins, activePlugins, failedPlugins, dependencyConflicts);
    }
    
    @Override
    public void addRegistryListener(RegistryListener listener) {
        listeners.add(listener);
    }
    
    @Override
    public void removeRegistryListener(RegistryListener listener) {
        listeners.remove(listener);
    }
}