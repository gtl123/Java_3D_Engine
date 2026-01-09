package engine.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simplified ConfigurationManager for scripting system integration
 */
public class ConfigurationManager {
    private static ConfigurationManager instance;
    private final Map<String, Object> config = new ConcurrentHashMap<>();
    
    private ConfigurationManager() {
        // Initialize with default values
        config.put("scripting.enabled", true);
        config.put("scripting.maxMemoryMB", 64);
        config.put("scripting.maxExecutionTimeSeconds", 30);
        config.put("scripting.hotReloadEnabled", true);
        config.put("scripting.debugMode", false);
        config.put("scripting.securityProfile", "sandbox");
    }
    
    public static ConfigurationManager getInstance() {
        if (instance == null) {
            instance = new ConfigurationManager();
        }
        return instance;
    }
    
    public String getString(String key) {
        Object value = config.get(key);
        return value != null ? value.toString() : null;
    }
    
    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value != null ? value : defaultValue;
    }
    
    public int getInt(String key) {
        Object value = config.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
    
    public int getInt(String key, int defaultValue) {
        try {
            Object value = config.get(key);
            if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof String) {
                return Integer.parseInt((String) value);
            }
        } catch (NumberFormatException e) {
            // Fall through to default
        }
        return defaultValue;
    }
    
    public boolean getBoolean(String key) {
        Object value = config.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return false;
    }
    
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
    
    public double getDouble(String key) {
        Object value = config.get(key);
        if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
    
    public double getDouble(String key, double defaultValue) {
        try {
            Object value = config.get(key);
            if (value instanceof Double) {
                return (Double) value;
            } else if (value instanceof Number) {
                return ((Number) value).doubleValue();
            } else if (value instanceof String) {
                return Double.parseDouble((String) value);
            }
        } catch (NumberFormatException e) {
            // Fall through to default
        }
        return defaultValue;
    }
    
    public void setProperty(String key, Object value) {
        config.put(key, value);
    }
    
    public Object getProperty(String key) {
        return config.get(key);
    }
    
    public boolean hasProperty(String key) {
        return config.containsKey(key);
    }
    
    public Map<String, Object> getAllProperties() {
        return new ConcurrentHashMap<>(config);
    }
}