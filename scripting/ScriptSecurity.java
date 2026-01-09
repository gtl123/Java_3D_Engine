package engine.scripting;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Security manager for script execution with permission-based access control
 * and resource usage monitoring.
 */
public class ScriptSecurity {
    
    public enum Permission {
        FILE_READ,
        FILE_WRITE,
        NETWORK_ACCESS,
        SYSTEM_COMMANDS,
        ENGINE_API_FULL,
        ENGINE_API_READ_ONLY,
        REFLECTION_ACCESS,
        THREAD_CREATION,
        NATIVE_CODE_ACCESS
    }
    
    public static class SecurityProfile {
        private final String name;
        private final Set<Permission> allowedPermissions;
        private final long maxMemoryBytes;
        private final long maxExecutionTimeMs;
        private final int maxThreads;
        
        public SecurityProfile(String name, Set<Permission> permissions, 
                             long maxMemoryBytes, long maxExecutionTimeMs, int maxThreads) {
            this.name = name;
            this.allowedPermissions = new HashSet<>(permissions);
            this.maxMemoryBytes = maxMemoryBytes;
            this.maxExecutionTimeMs = maxExecutionTimeMs;
            this.maxThreads = maxThreads;
        }
        
        public String getName() { return name; }
        public Set<Permission> getAllowedPermissions() { return new HashSet<>(allowedPermissions); }
        public long getMaxMemoryBytes() { return maxMemoryBytes; }
        public long getMaxExecutionTimeMs() { return maxExecutionTimeMs; }
        public int getMaxThreads() { return maxThreads; }
        
        public boolean hasPermission(Permission permission) {
            return allowedPermissions.contains(permission);
        }
    }
    
    public static class ResourceUsage {
        private final AtomicLong memoryUsed = new AtomicLong(0);
        private final AtomicLong executionTime = new AtomicLong(0);
        private final AtomicLong threadsCreated = new AtomicLong(0);
        private final Map<String, AtomicLong> apiCallCounts = new ConcurrentHashMap<>();
        private volatile long startTime = 0;
        
        public void startExecution() {
            startTime = System.currentTimeMillis();
        }
        
        public void endExecution() {
            if (startTime > 0) {
                executionTime.addAndGet(System.currentTimeMillis() - startTime);
                startTime = 0;
            }
        }
        
        public void addMemoryUsage(long bytes) {
            memoryUsed.addAndGet(bytes);
        }
        
        public void incrementThreadCount() {
            threadsCreated.incrementAndGet();
        }
        
        public void recordApiCall(String apiMethod) {
            apiCallCounts.computeIfAbsent(apiMethod, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        public long getMemoryUsed() { return memoryUsed.get(); }
        public long getExecutionTime() { return executionTime.get(); }
        public long getThreadsCreated() { return threadsCreated.get(); }
        public Map<String, Long> getApiCallCounts() {
            Map<String, Long> result = new HashMap<>();
            apiCallCounts.forEach((k, v) -> result.put(k, v.get()));
            return result;
        }
        
        public void reset() {
            memoryUsed.set(0);
            executionTime.set(0);
            threadsCreated.set(0);
            apiCallCounts.clear();
            startTime = 0;
        }
    }
    
    // Predefined security profiles
    public static final SecurityProfile SANDBOX_PROFILE = new SecurityProfile(
        "sandbox",
        Set.of(Permission.ENGINE_API_READ_ONLY),
        16 * 1024 * 1024, // 16MB
        5000, // 5 seconds
        1 // Single thread
    );
    
    public static final SecurityProfile TRUSTED_PROFILE = new SecurityProfile(
        "trusted",
        Set.of(Permission.ENGINE_API_FULL, Permission.FILE_READ, Permission.THREAD_CREATION),
        64 * 1024 * 1024, // 64MB
        30000, // 30 seconds
        4 // Multiple threads
    );
    
    public static final SecurityProfile ADMIN_PROFILE = new SecurityProfile(
        "admin",
        Set.of(Permission.values()), // All permissions
        256 * 1024 * 1024, // 256MB
        Long.MAX_VALUE, // No time limit
        Integer.MAX_VALUE // No thread limit
    );
    
    private final Map<String, SecurityProfile> scriptProfiles = new ConcurrentHashMap<>();
    private final Map<String, ResourceUsage> scriptUsage = new ConcurrentHashMap<>();
    private final SecurityProfile defaultProfile;
    
    public ScriptSecurity() {
        this(SANDBOX_PROFILE);
    }
    
    public ScriptSecurity(SecurityProfile defaultProfile) {
        this.defaultProfile = defaultProfile;
    }
    
    /**
     * Set security profile for a specific script
     */
    public void setScriptProfile(String scriptId, SecurityProfile profile) {
        scriptProfiles.put(scriptId, profile);
        // Reset usage when profile changes
        scriptUsage.put(scriptId, new ResourceUsage());
    }
    
    /**
     * Get security profile for a script
     */
    public SecurityProfile getScriptProfile(String scriptId) {
        return scriptProfiles.getOrDefault(scriptId, defaultProfile);
    }
    
    /**
     * Get resource usage for a script
     */
    public ResourceUsage getScriptUsage(String scriptId) {
        return scriptUsage.computeIfAbsent(scriptId, k -> new ResourceUsage());
    }
    
    /**
     * Check if script has permission for an operation
     */
    public boolean checkPermission(String scriptId, Permission permission) {
        SecurityProfile profile = getScriptProfile(scriptId);
        return profile.hasPermission(permission);
    }
    
    /**
     * Validate resource usage against limits
     */
    public void validateResourceUsage(String scriptId) throws ScriptException {
        SecurityProfile profile = getScriptProfile(scriptId);
        ResourceUsage usage = getScriptUsage(scriptId);
        
        if (usage.getMemoryUsed() > profile.getMaxMemoryBytes()) {
            throw new ScriptException("Script exceeded memory limit: " + usage.getMemoryUsed() + 
                                    " > " + profile.getMaxMemoryBytes(), scriptId, null, 0, 0);
        }
        
        if (usage.getExecutionTime() > profile.getMaxExecutionTimeMs()) {
            throw new ScriptException("Script exceeded execution time limit: " + usage.getExecutionTime() + 
                                    " > " + profile.getMaxExecutionTimeMs(), scriptId, null, 0, 0);
        }
        
        if (usage.getThreadsCreated() > profile.getMaxThreads()) {
            throw new ScriptException("Script exceeded thread limit: " + usage.getThreadsCreated() + 
                                    " > " + profile.getMaxThreads(), scriptId, null, 0, 0);
        }
    }
    
    /**
     * Start monitoring script execution
     */
    public void startExecution(String scriptId) {
        getScriptUsage(scriptId).startExecution();
    }
    
    /**
     * End monitoring script execution
     */
    public void endExecution(String scriptId) {
        ResourceUsage usage = getScriptUsage(scriptId);
        usage.endExecution();
        
        try {
            validateResourceUsage(scriptId);
        } catch (ScriptException e) {
            System.err.println("Script security violation: " + e.getMessage());
        }
    }
    
    /**
     * Record API call for monitoring
     */
    public void recordApiCall(String scriptId, String apiMethod) {
        getScriptUsage(scriptId).recordApiCall(apiMethod);
    }
    
    /**
     * Reset usage statistics for a script
     */
    public void resetUsage(String scriptId) {
        ResourceUsage usage = scriptUsage.get(scriptId);
        if (usage != null) {
            usage.reset();
        }
    }
    
    /**
     * Get security report for all scripts
     */
    public Map<String, Map<String, Object>> getSecurityReport() {
        Map<String, Map<String, Object>> report = new HashMap<>();
        
        for (String scriptId : scriptProfiles.keySet()) {
            Map<String, Object> scriptReport = new HashMap<>();
            SecurityProfile profile = getScriptProfile(scriptId);
            ResourceUsage usage = getScriptUsage(scriptId);
            
            scriptReport.put("profile", profile.getName());
            scriptReport.put("permissions", profile.getAllowedPermissions());
            scriptReport.put("memoryUsed", usage.getMemoryUsed());
            scriptReport.put("memoryLimit", profile.getMaxMemoryBytes());
            scriptReport.put("executionTime", usage.getExecutionTime());
            scriptReport.put("executionLimit", profile.getMaxExecutionTimeMs());
            scriptReport.put("threadsCreated", usage.getThreadsCreated());
            scriptReport.put("threadLimit", profile.getMaxThreads());
            scriptReport.put("apiCalls", usage.getApiCallCounts());
            
            report.put(scriptId, scriptReport);
        }
        
        return report;
    }
    
    /**
     * Create a custom security profile
     */
    public static SecurityProfile createProfile(String name, Set<Permission> permissions,
                                              long maxMemoryMB, long maxExecutionSeconds, int maxThreads) {
        return new SecurityProfile(name, permissions, 
                                 maxMemoryMB * 1024 * 1024, 
                                 maxExecutionSeconds * 1000, 
                                 maxThreads);
    }
    
    /**
     * Cleanup security manager
     */
    public void cleanup() {
        scriptProfiles.clear();
        scriptUsage.clear();
    }
}