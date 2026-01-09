package engine.config;

import engine.logging.LogManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Configuration source that reads from Java system properties.
 * System properties can be set via -D command line arguments or programmatically.
 */
public class SystemPropertiesConfigSource implements ConfigSource {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final String name;
    private final String prefix;
    private final int priority;
    
    public SystemPropertiesConfigSource(String name, String prefix, int priority) {
        this.name = name;
        this.prefix = prefix != null ? prefix : "";
        this.priority = priority;
        
        logManager.debug("SystemPropertiesConfigSource", "System properties source initialized",
                        "name", name, "prefix", prefix, "priority", priority);
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
        String systemKey = prefix + key;
        String value = System.getProperty(systemKey);
        
        if (value == null && !prefix.isEmpty()) {
            // Try without prefix as fallback
            value = System.getProperty(key);
        }
        
        return Optional.ofNullable(value);
    }
    
    @Override
    public Map<String, String> getAllValues() {
        Map<String, String> result = new HashMap<>();
        Properties systemProps = System.getProperties();
        
        for (String propName : systemProps.stringPropertyNames()) {
            String value = systemProps.getProperty(propName);
            
            // Convert system property name to configuration key
            String configKey;
            if (!prefix.isEmpty() && propName.startsWith(prefix)) {
                configKey = propName.substring(prefix.length());
            } else if (prefix.isEmpty()) {
                configKey = propName;
            } else {
                continue; // Skip properties that don't match our prefix
            }
            
            result.put(configKey, value);
        }
        
        return result;
    }
    
    @Override
    public boolean isAvailable() {
        return true; // System properties are always available
    }
    
    @Override
    public boolean reload() {
        // System properties are dynamic, no need to reload
        logManager.debug("SystemPropertiesConfigSource", "System properties reloaded",
                        "name", name, "propertyCount", getAllValues().size());
        return true;
    }
    
    @Override
    public boolean supportsHotReload() {
        return true; // System properties can be changed at runtime
    }
    
    @Override
    public long getLastModified() {
        return System.currentTimeMillis(); // Always consider as "just modified"
    }
    
    /**
     * Get the system property prefix.
     * @return The prefix used for system properties
     */
    public String getPrefix() {
        return prefix;
    }
}