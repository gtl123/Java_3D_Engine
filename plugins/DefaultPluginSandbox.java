package engine.plugins;

import engine.logging.LogManager;

import java.io.File;
import java.net.URL;
import java.security.Permission;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of PluginSandbox with basic security controls.
 * Note: This is a simplified implementation for demonstration purposes.
 */
public class DefaultPluginSandbox implements PluginSandbox {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final ConcurrentHashMap<String, Set<String>> pluginPermissions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ResourceLimits> pluginLimits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ResourceUsage> pluginUsage = new ConcurrentHashMap<>();
    private final List<SecurityPolicy> securityPolicies = new CopyOnWriteArrayList<>();
    
    private volatile boolean sandboxEnabled = true;
    private volatile boolean initialized = false;
    
    // Statistics
    private int totalPermissionChecks = 0;
    private int deniedPermissions = 0;
    private int resourceLimitViolations = 0;
    private long totalSandboxedExecutions = 0;
    
    public void initialize() throws PluginException {
        if (initialized) {
            return;
        }
        
        logManager.info("PluginSandbox", "Initializing plugin sandbox");
        
        // Add default security policies
        addSecurityPolicy(new DefaultSecurityPolicy());
        
        initialized = true;
        logManager.info("PluginSandbox", "Plugin sandbox initialized");
    }
    
    public void shutdown() {
        if (!initialized) {
            return;
        }
        
        logManager.info("PluginSandbox", "Shutting down plugin sandbox");
        pluginPermissions.clear();
        pluginLimits.clear();
        pluginUsage.clear();
        securityPolicies.clear();
        initialized = false;
        logManager.info("PluginSandbox", "Plugin sandbox shutdown complete");
    }
    
    @Override
    public SandboxContext createSandboxContext(Plugin plugin) throws PluginException {
        if (!initialized) {
            throw new PluginException("Plugin sandbox not initialized");
        }
        
        String pluginId = plugin.getMetadata().getId();
        
        // Initialize plugin permissions from metadata
        Set<String> permissions = new HashSet<>(plugin.getMetadata().getPermissions());
        pluginPermissions.put(pluginId, permissions);
        
        // Set default resource limits
        ResourceLimits limits = plugin.getMetadata().isSandboxed() ? 
                ResourceLimits.defaultLimits() : ResourceLimits.trustedLimits();
        pluginLimits.put(pluginId, limits);
        
        // Initialize usage tracking
        pluginUsage.put(pluginId, new ResourceUsage(0, 0, 0, 0, 0));
        
        logManager.info("PluginSandbox", "Sandbox context created",
                       "pluginId", pluginId,
                       "sandboxed", plugin.getMetadata().isSandboxed(),
                       "permissions", permissions.size());
        
        return new SandboxContextImpl(plugin, this);
    }
    
    @Override
    public <T> T executeInSandbox(SandboxContext context, SandboxedCode<T> code) throws SecurityException {
        if (!sandboxEnabled) {
            try {
                return code.execute();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        
        String pluginId = context.getPluginId();
        totalSandboxedExecutions++;
        
        try {
            // Check resource limits before execution
            ResourceUsage usage = pluginUsage.get(pluginId);
            ResourceLimits limits = pluginLimits.get(pluginId);
            
            if (usage != null && limits != null && usage.exceedsLimits(limits)) {
                resourceLimitViolations++;
                throw new SecurityException("Plugin exceeds resource limits: " + pluginId);
            }
            
            long startTime = System.currentTimeMillis();
            T result = code.execute();
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Update usage statistics
            updateResourceUsage(pluginId, executionTime);
            
            return result;
            
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Sandboxed execution failed", e);
        }
    }
    
    @Override
    public boolean hasPermission(String pluginId, String permission) {
        totalPermissionChecks++;
        
        Set<String> permissions = pluginPermissions.get(pluginId);
        boolean hasPermission = permissions != null && permissions.contains(permission);
        
        if (!hasPermission) {
            deniedPermissions++;
            logManager.debug("PluginSandbox", "Permission denied",
                           "pluginId", pluginId,
                           "permission", permission);
        }
        
        return hasPermission;
    }
    
    @Override
    public void grantPermission(String pluginId, String permission) throws SecurityException {
        // Check security policies
        for (SecurityPolicy policy : securityPolicies) {
            if (!policy.shouldGrantPermission(pluginId, new BasicPermission(permission))) {
                throw new SecurityException("Security policy denied permission: " + permission);
            }
        }
        
        pluginPermissions.computeIfAbsent(pluginId, k -> new HashSet<>()).add(permission);
        
        logManager.info("PluginSandbox", "Permission granted",
                       "pluginId", pluginId,
                       "permission", permission);
    }
    
    @Override
    public void revokePermission(String pluginId, String permission) {
        Set<String> permissions = pluginPermissions.get(pluginId);
        if (permissions != null) {
            permissions.remove(permission);
            
            logManager.info("PluginSandbox", "Permission revoked",
                           "pluginId", pluginId,
                           "permission", permission);
        }
    }
    
    @Override
    public Set<String> getPluginPermissions(String pluginId) {
        Set<String> permissions = pluginPermissions.get(pluginId);
        return permissions != null ? new HashSet<>(permissions) : new HashSet<>();
    }
    
    @Override
    public boolean validateFileAccess(String pluginId, File file, FileAccessType accessType) {
        // Basic file access validation
        String requiredPermission = "file." + accessType.name().toLowerCase();
        
        if (!hasPermission(pluginId, requiredPermission)) {
            return false;
        }
        
        // Check if file is in allowed plugin directory
        String pluginDataDir = "plugins/" + pluginId + "/";
        String filePath = file.getAbsolutePath();
        
        return filePath.contains(pluginDataDir) || hasPermission(pluginId, "file.system.access");
    }
    
    @Override
    public boolean validateNetworkAccess(String pluginId, URL url, NetworkAccessType accessType) {
        String requiredPermission = "network." + accessType.name().toLowerCase();
        return hasPermission(pluginId, requiredPermission);
    }
    
    @Override
    public void setResourceLimits(String pluginId, ResourceLimits limits) {
        pluginLimits.put(pluginId, limits);
        
        logManager.info("PluginSandbox", "Resource limits updated",
                       "pluginId", pluginId,
                       "maxMemoryMB", limits.getMaxMemoryMB());
    }
    
    @Override
    public ResourceUsage getResourceUsage(String pluginId) {
        return pluginUsage.get(pluginId);
    }
    
    @Override
    public void setSandboxEnabled(boolean enabled) {
        this.sandboxEnabled = enabled;
        logManager.info("PluginSandbox", "Sandbox " + (enabled ? "enabled" : "disabled"));
    }
    
    @Override
    public boolean isSandboxEnabled() {
        return sandboxEnabled;
    }
    
    @Override
    public void addSecurityPolicy(SecurityPolicy policy) {
        securityPolicies.add(policy);
        securityPolicies.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        
        logManager.info("PluginSandbox", "Security policy added",
                       "policyName", policy.getPolicyName(),
                       "priority", policy.getPriority());
    }
    
    @Override
    public void removeSecurityPolicy(SecurityPolicy policy) {
        securityPolicies.remove(policy);
        
        logManager.info("PluginSandbox", "Security policy removed",
                       "policyName", policy.getPolicyName());
    }
    
    @Override
    public SandboxStatistics getStatistics() {
        return new SandboxStatistics(
                pluginPermissions.size(),
                totalPermissionChecks,
                deniedPermissions,
                resourceLimitViolations,
                totalSandboxedExecutions
        );
    }
    
    private void updateResourceUsage(String pluginId, long executionTime) {
        ResourceUsage currentUsage = pluginUsage.get(pluginId);
        if (currentUsage != null) {
            ResourceUsage newUsage = new ResourceUsage(
                    currentUsage.getCurrentMemoryMB(),
                    currentUsage.getCurrentThreads(),
                    currentUsage.getCurrentOpenFiles(),
                    currentUsage.getCurrentNetworkConnections(),
                    currentUsage.getExecutionTimeMs() + executionTime
            );
            pluginUsage.put(pluginId, newUsage);
        }
    }
    
    /**
     * Default sandbox context implementation.
     */
    private static class SandboxContextImpl implements SandboxContext {
        private final Plugin plugin;
        private final DefaultPluginSandbox sandbox;
        
        public SandboxContextImpl(Plugin plugin, DefaultPluginSandbox sandbox) {
            this.plugin = plugin;
            this.sandbox = sandbox;
        }
        
        @Override
        public Plugin getPlugin() {
            return plugin;
        }
        
        @Override
        public String getPluginId() {
            return plugin.getMetadata().getId();
        }
        
        @Override
        public SecurityManager getSecurityManager() {
            return System.getSecurityManager(); // Use system security manager
        }
        
        @Override
        public ClassLoader getClassLoader() {
            return plugin.getClass().getClassLoader();
        }
        
        @Override
        public boolean isPermissionGranted(String permission) {
            return sandbox.hasPermission(getPluginId(), permission);
        }
        
        @Override
        public List<String> getAllowedFilePaths() {
            return Arrays.asList("plugins/" + getPluginId() + "/");
        }
        
        @Override
        public List<String> getAllowedNetworkHosts() {
            return Arrays.asList("localhost", "127.0.0.1");
        }
        
        @Override
        public ResourceLimits getResourceLimits() {
            return sandbox.pluginLimits.get(getPluginId());
        }
        
        @Override
        public void close() {
            // Cleanup context resources
        }
    }
    
    /**
     * Default security policy implementation.
     */
    private static class DefaultSecurityPolicy implements SecurityPolicy {
        
        @Override
        public boolean shouldGrantPermission(String pluginId, Permission permission) {
            // Allow basic permissions by default
            String permName = permission.getName();
            return permName.startsWith("engine.") || 
                   permName.startsWith("file.read") ||
                   permName.startsWith("network.connect");
        }
        
        @Override
        public String getPolicyName() {
            return "DefaultSecurityPolicy";
        }
        
        @Override
        public int getPriority() {
            return 0; // Lowest priority
        }
    }
    
    /**
     * Basic permission implementation.
     */
    private static class BasicPermission extends Permission {
        public BasicPermission(String name) {
            super(name);
        }
        
        @Override
        public boolean implies(Permission permission) {
            return getName().equals(permission.getName());
        }
        
        @Override
        public boolean equals(Object obj) {
            return obj instanceof BasicPermission && 
                   getName().equals(((BasicPermission) obj).getName());
        }
        
        @Override
        public int hashCode() {
            return getName().hashCode();
        }
        
        @Override
        public String getActions() {
            return "";
        }
    }
}