package engine.plugins;

import java.io.File;
import java.net.URL;
import java.security.Permission;
import java.util.List;
import java.util.Set;

/**
 * Security sandbox for plugin execution with permission management and resource isolation.
 * Provides controlled access to system resources and engine APIs for untrusted plugins.
 */
public interface PluginSandbox {
    
    /**
     * Initialize the sandbox system.
     * @throws PluginException if initialization fails
     */
    void initialize() throws PluginException;
    
    /**
     * Shutdown the sandbox system.
     */
    void shutdown();
    
    /**
     * Create a sandboxed execution context for a plugin.
     * @param plugin Plugin to create context for
     * @return Sandboxed execution context
     * @throws PluginException if context creation fails
     */
    SandboxContext createSandboxContext(Plugin plugin) throws PluginException;
    
    /**
     * Execute code within a sandbox context.
     * @param context Sandbox context
     * @param code Code to execute
     * @return Execution result
     * @throws SecurityException if execution is denied
     */
    <T> T executeInSandbox(SandboxContext context, SandboxedCode<T> code) throws SecurityException;
    
    /**
     * Check if a plugin has a specific permission.
     * @param pluginId Plugin ID
     * @param permission Permission to check
     * @return true if plugin has permission
     */
    boolean hasPermission(String pluginId, String permission);
    
    /**
     * Grant a permission to a plugin.
     * @param pluginId Plugin ID
     * @param permission Permission to grant
     * @throws SecurityException if permission cannot be granted
     */
    void grantPermission(String pluginId, String permission) throws SecurityException;
    
    /**
     * Revoke a permission from a plugin.
     * @param pluginId Plugin ID
     * @param permission Permission to revoke
     */
    void revokePermission(String pluginId, String permission);
    
    /**
     * Get all permissions for a plugin.
     * @param pluginId Plugin ID
     * @return Set of permissions
     */
    Set<String> getPluginPermissions(String pluginId);
    
    /**
     * Validate file access for a plugin.
     * @param pluginId Plugin ID
     * @param file File to access
     * @param accessType Type of access (read, write, execute)
     * @return true if access is allowed
     */
    boolean validateFileAccess(String pluginId, File file, FileAccessType accessType);
    
    /**
     * Validate network access for a plugin.
     * @param pluginId Plugin ID
     * @param url URL to access
     * @param accessType Type of access (connect, resolve)
     * @return true if access is allowed
     */
    boolean validateNetworkAccess(String pluginId, URL url, NetworkAccessType accessType);
    
    /**
     * Set resource limits for a plugin.
     * @param pluginId Plugin ID
     * @param limits Resource limits
     */
    void setResourceLimits(String pluginId, ResourceLimits limits);
    
    /**
     * Get current resource usage for a plugin.
     * @param pluginId Plugin ID
     * @return Current resource usage
     */
    ResourceUsage getResourceUsage(String pluginId);
    
    /**
     * Enable or disable sandbox enforcement.
     * @param enabled Whether sandbox is enforced
     */
    void setSandboxEnabled(boolean enabled);
    
    /**
     * Check if sandbox is enabled.
     * @return true if sandbox is enabled
     */
    boolean isSandboxEnabled();
    
    /**
     * Add a security policy for plugins.
     * @param policy Security policy
     */
    void addSecurityPolicy(SecurityPolicy policy);
    
    /**
     * Remove a security policy.
     * @param policy Security policy to remove
     */
    void removeSecurityPolicy(SecurityPolicy policy);
    
    /**
     * Get sandbox statistics.
     * @return Sandbox statistics
     */
    SandboxStatistics getStatistics();
    
    /**
     * Sandboxed execution context for a plugin.
     */
    interface SandboxContext {
        /**
         * Get the plugin this context belongs to.
         * @return Plugin instance
         */
        Plugin getPlugin();
        
        /**
         * Get the plugin ID.
         * @return Plugin ID
         */
        String getPluginId();
        
        /**
         * Get the security manager for this context.
         * @return Security manager
         */
        SecurityManager getSecurityManager();
        
        /**
         * Get the classloader for this context.
         * @return Plugin classloader
         */
        ClassLoader getClassLoader();
        
        /**
         * Check if a permission is granted in this context.
         * @param permission Permission to check
         * @return true if permission is granted
         */
        boolean isPermissionGranted(String permission);
        
        /**
         * Get the allowed file access paths.
         * @return List of allowed file paths
         */
        List<String> getAllowedFilePaths();
        
        /**
         * Get the allowed network hosts.
         * @return List of allowed network hosts
         */
        List<String> getAllowedNetworkHosts();
        
        /**
         * Get resource limits for this context.
         * @return Resource limits
         */
        ResourceLimits getResourceLimits();
        
        /**
         * Close the sandbox context and clean up resources.
         */
        void close();
    }
    
    /**
     * Code to be executed in a sandbox.
     */
    @FunctionalInterface
    interface SandboxedCode<T> {
        /**
         * Execute the code.
         * @return Execution result
         * @throws Exception if execution fails
         */
        T execute() throws Exception;
    }
    
    /**
     * File access types.
     */
    enum FileAccessType {
        READ, WRITE, EXECUTE, DELETE, CREATE
    }
    
    /**
     * Network access types.
     */
    enum NetworkAccessType {
        CONNECT, RESOLVE, LISTEN, ACCEPT
    }
    
    /**
     * Resource limits for plugins.
     */
    class ResourceLimits {
        private final long maxMemoryMB;
        private final int maxThreads;
        private final long maxFileSize;
        private final int maxOpenFiles;
        private final long maxNetworkConnections;
        private final long maxExecutionTimeMs;
        
        public ResourceLimits(long maxMemoryMB, int maxThreads, long maxFileSize, 
                            int maxOpenFiles, long maxNetworkConnections, long maxExecutionTimeMs) {
            this.maxMemoryMB = maxMemoryMB;
            this.maxThreads = maxThreads;
            this.maxFileSize = maxFileSize;
            this.maxOpenFiles = maxOpenFiles;
            this.maxNetworkConnections = maxNetworkConnections;
            this.maxExecutionTimeMs = maxExecutionTimeMs;
        }
        
        public long getMaxMemoryMB() { return maxMemoryMB; }
        public int getMaxThreads() { return maxThreads; }
        public long getMaxFileSize() { return maxFileSize; }
        public int getMaxOpenFiles() { return maxOpenFiles; }
        public long getMaxNetworkConnections() { return maxNetworkConnections; }
        public long getMaxExecutionTimeMs() { return maxExecutionTimeMs; }
        
        public static ResourceLimits defaultLimits() {
            return new ResourceLimits(256, 10, 100 * 1024 * 1024, 50, 10, 30000);
        }
        
        public static ResourceLimits restrictedLimits() {
            return new ResourceLimits(64, 5, 10 * 1024 * 1024, 20, 5, 10000);
        }
        
        public static ResourceLimits trustedLimits() {
            return new ResourceLimits(1024, 50, 1024 * 1024 * 1024, 200, 50, 120000);
        }
    }
    
    /**
     * Current resource usage for a plugin.
     */
    class ResourceUsage {
        private final long currentMemoryMB;
        private final int currentThreads;
        private final int currentOpenFiles;
        private final long currentNetworkConnections;
        private final long executionTimeMs;
        
        public ResourceUsage(long currentMemoryMB, int currentThreads, int currentOpenFiles, 
                           long currentNetworkConnections, long executionTimeMs) {
            this.currentMemoryMB = currentMemoryMB;
            this.currentThreads = currentThreads;
            this.currentOpenFiles = currentOpenFiles;
            this.currentNetworkConnections = currentNetworkConnections;
            this.executionTimeMs = executionTimeMs;
        }
        
        public long getCurrentMemoryMB() { return currentMemoryMB; }
        public int getCurrentThreads() { return currentThreads; }
        public int getCurrentOpenFiles() { return currentOpenFiles; }
        public long getCurrentNetworkConnections() { return currentNetworkConnections; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        
        public boolean exceedsLimits(ResourceLimits limits) {
            return currentMemoryMB > limits.getMaxMemoryMB() ||
                   currentThreads > limits.getMaxThreads() ||
                   currentOpenFiles > limits.getMaxOpenFiles() ||
                   currentNetworkConnections > limits.getMaxNetworkConnections() ||
                   executionTimeMs > limits.getMaxExecutionTimeMs();
        }
    }
    
    /**
     * Security policy for plugin access control.
     */
    interface SecurityPolicy {
        /**
         * Check if a permission should be granted.
         * @param pluginId Plugin requesting permission
         * @param permission Permission being requested
         * @return true if permission should be granted
         */
        boolean shouldGrantPermission(String pluginId, Permission permission);
        
        /**
         * Get the policy name.
         * @return Policy name
         */
        String getPolicyName();
        
        /**
         * Get the policy priority (higher = evaluated first).
         * @return Policy priority
         */
        int getPriority();
    }
    
    /**
     * Sandbox statistics for monitoring.
     */
    class SandboxStatistics {
        private final int activeSandboxes;
        private final int totalPermissionChecks;
        private final int deniedPermissions;
        private final int resourceLimitViolations;
        private final long totalSandboxedExecutions;
        
        public SandboxStatistics(int activeSandboxes, int totalPermissionChecks, int deniedPermissions, 
                               int resourceLimitViolations, long totalSandboxedExecutions) {
            this.activeSandboxes = activeSandboxes;
            this.totalPermissionChecks = totalPermissionChecks;
            this.deniedPermissions = deniedPermissions;
            this.resourceLimitViolations = resourceLimitViolations;
            this.totalSandboxedExecutions = totalSandboxedExecutions;
        }
        
        public int getActiveSandboxes() { return activeSandboxes; }
        public int getTotalPermissionChecks() { return totalPermissionChecks; }
        public int getDeniedPermissions() { return deniedPermissions; }
        public int getResourceLimitViolations() { return resourceLimitViolations; }
        public long getTotalSandboxedExecutions() { return totalSandboxedExecutions; }
        
        public double getPermissionDenialRate() {
            return totalPermissionChecks > 0 ? (double) deniedPermissions / totalPermissionChecks : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("SandboxStats{active=%d, checks=%d, denied=%d, violations=%d, executions=%d}",
                               activeSandboxes, totalPermissionChecks, deniedPermissions, 
                               resourceLimitViolations, totalSandboxedExecutions);
        }
    }
}