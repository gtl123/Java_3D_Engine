package engine.config;

import engine.logging.LogManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Represents an environment-specific configuration profile (development, staging, production).
 * Each profile contains settings specific to that environment.
 */
public class EnvironmentProfile {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    public enum Environment {
        DEVELOPMENT("development", "dev"),
        STAGING("staging", "stage"),
        PRODUCTION("production", "prod");
        
        private final String name;
        private final String shortName;
        
        Environment(String name, String shortName) {
            this.name = name;
            this.shortName = shortName;
        }
        
        public String getName() {
            return name;
        }
        
        public String getShortName() {
            return shortName;
        }
        
        public static Environment fromString(String envStr) {
            if (envStr == null) return DEVELOPMENT;
            
            String normalized = envStr.toLowerCase().trim();
            for (Environment env : values()) {
                if (env.name.equals(normalized) || env.shortName.equals(normalized)) {
                    return env;
                }
            }
            return DEVELOPMENT; // Default fallback
        }
    }
    
    private final Environment environment;
    private final Map<String, Object> settings = new HashMap<>();
    private final Map<String, String> metadata = new HashMap<>();
    
    public EnvironmentProfile(Environment environment) {
        this.environment = environment;
        initializeDefaults();
    }
    
    /**
     * Initialize default settings for this environment.
     */
    private void initializeDefaults() {
        switch (environment) {
            case DEVELOPMENT:
                // Development defaults - more verbose logging, debug features enabled
                settings.put("engine.logging.level", "DEBUG");
                settings.put("engine.logging.console.enabled", true);
                settings.put("engine.logging.file.enabled", true);
                settings.put("engine.performance.monitoring.enabled", true);
                settings.put("engine.debug.enabled", true);
                settings.put("engine.hotreload.enabled", true);
                settings.put("engine.render.vsync", false);
                settings.put("engine.render.targetFps", 60);
                settings.put("engine.physics.debugDraw", true);
                break;
                
            case STAGING:
                // Staging defaults - production-like but with some debug features
                settings.put("engine.logging.level", "INFO");
                settings.put("engine.logging.console.enabled", true);
                settings.put("engine.logging.file.enabled", true);
                settings.put("engine.performance.monitoring.enabled", true);
                settings.put("engine.debug.enabled", false);
                settings.put("engine.hotreload.enabled", false);
                settings.put("engine.render.vsync", true);
                settings.put("engine.render.targetFps", 60);
                settings.put("engine.physics.debugDraw", false);
                break;
                
            case PRODUCTION:
                // Production defaults - optimized for performance
                settings.put("engine.logging.level", "WARN");
                settings.put("engine.logging.console.enabled", false);
                settings.put("engine.logging.file.enabled", true);
                settings.put("engine.performance.monitoring.enabled", false);
                settings.put("engine.debug.enabled", false);
                settings.put("engine.hotreload.enabled", false);
                settings.put("engine.render.vsync", true);
                settings.put("engine.render.targetFps", 60);
                settings.put("engine.physics.debugDraw", false);
                break;
        }
        
        // Common settings for all environments
        settings.put("engine.audio.masterVolume", 1.0f);
        settings.put("engine.render.renderDistance", 8);
        settings.put("engine.render.fov", 70.0f);
        settings.put("engine.input.mouseSensitivity", 1.0f);
        
        logManager.info("EnvironmentProfile", "Environment profile initialized",
                       "environment", environment.getName(),
                       "settingCount", settings.size());
    }
    
    /**
     * Get the environment type.
     * @return The environment
     */
    public Environment getEnvironment() {
        return environment;
    }
    
    /**
     * Get a setting value by key.
     * @param key The setting key
     * @return The value if present
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getSetting(String key) {
        Object value = settings.get(key);
        if (value == null) {
            return Optional.empty();
        }
        
        try {
            return Optional.of((T) value);
        } catch (ClassCastException e) {
            logManager.warn("EnvironmentProfile", "Type mismatch for setting",
                           "key", key, "expectedType", "T", "actualType", value.getClass().getSimpleName());
            return Optional.empty();
        }
    }
    
    /**
     * Set a setting value.
     * @param key The setting key
     * @param value The setting value
     */
    public void setSetting(String key, Object value) {
        Object oldValue = settings.put(key, value);
        
        logManager.debug("EnvironmentProfile", "Setting updated",
                        "environment", environment.getName(),
                        "key", key,
                        "oldValue", oldValue,
                        "newValue", value);
    }
    
    /**
     * Get all settings as a map.
     * @return Map of all settings
     */
    public Map<String, Object> getAllSettings() {
        return new HashMap<>(settings);
    }
    
    /**
     * Add metadata about this profile.
     * @param key The metadata key
     * @param value The metadata value
     */
    public void setMetadata(String key, String value) {
        metadata.put(key, value);
    }
    
    /**
     * Get metadata value.
     * @param key The metadata key
     * @return The metadata value if present
     */
    public Optional<String> getMetadata(String key) {
        return Optional.ofNullable(metadata.get(key));
    }
    
    /**
     * Get all metadata.
     * @return Map of all metadata
     */
    public Map<String, String> getAllMetadata() {
        return new HashMap<>(metadata);
    }
    
    /**
     * Check if this is a development environment.
     * @return true if development environment
     */
    public boolean isDevelopment() {
        return environment == Environment.DEVELOPMENT;
    }
    
    /**
     * Check if this is a production environment.
     * @return true if production environment
     */
    public boolean isProduction() {
        return environment == Environment.PRODUCTION;
    }
    
    /**
     * Check if debug features should be enabled.
     * @return true if debug features should be enabled
     */
    public boolean isDebugEnabled() {
        Optional<Boolean> debugSetting = getSetting("engine.debug.enabled");
        return debugSetting.orElse(false);
    }
    
    @Override
    public String toString() {
        return "EnvironmentProfile{" +
               "environment=" + environment.getName() +
               ", settingCount=" + settings.size() +
               '}';
    }
}