package engine.config;

import engine.logging.LogManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration source that reads from environment variables.
 * Environment variables are prefixed with a configurable prefix (e.g., ENGINE_).
 */
public class EnvironmentConfigSource implements ConfigSource {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final String name;
    private final String prefix;
    private final int priority;
    private final Map<String, String> environmentVars = new HashMap<>();
    
    public EnvironmentConfigSource(String name, String prefix, int priority) {
        this.name = name;
        this.prefix = prefix != null ? prefix : "";
        this.priority = priority;
        reload();
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public int getPriority() {
        return priority;
    }
    
    @Override
    public Optional<String> getValue(String key) {
        // Try with prefix first, then without prefix
        String envKey = prefix + key.toUpperCase().replace('.', '_');
        String value = System.getenv(envKey);
        
        if (value == null && !prefix.isEmpty()) {
            // Try without prefix as fallback
            value = System.getenv(key.toUpperCase().replace('.', '_'));
        }
        
        return Optional.ofNullable(value);
    }
    
    @Override
    public Map<String, String> getAllValues() {
        Map<String, String> result = new HashMap<>();
        Map<String, String> env = System.getenv();
        
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String envKey = entry.getKey();
            String value = entry.getValue();
            
            // Convert environment variable name to configuration key
            String configKey;
            if (!prefix.isEmpty() && envKey.startsWith(prefix)) {
                configKey = envKey.substring(prefix.length()).toLowerCase().replace('_', '.');
            } else if (prefix.isEmpty()) {
                configKey = envKey.toLowerCase().replace('_', '.');
            } else {
                continue; // Skip variables that don't match our prefix
            }
            
            result.put(configKey, value);
        }
        
        return result;
    }
    
    @Override
    public boolean isAvailable() {
        return true; // Environment variables are always available
    }
    
    @Override
    public boolean reload() {
        environmentVars.clear();
        environmentVars.putAll(getAllValues());
        
        logManager.debug("EnvironmentConfigSource", "Environment variables loaded",
                        "name", name, "prefix", prefix, "variableCount", environmentVars.size());
        
        return true;
    }
    
    @Override
    public boolean supportsHotReload() {
        return false; // Environment variables don't change during runtime
    }
    
    @Override
    public long getLastModified() {
        return -1; // Not applicable for environment variables
    }
    
    /**
     * Get the environment variable prefix.
     * @return The prefix used for environment variables
     */
    public String getPrefix() {
        return prefix;
    }
}