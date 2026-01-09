package engine.config;

import engine.logging.LogManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Runtime feature toggles with type safety for A/B testing and gradual rollouts.
 * Supports boolean flags, percentage rollouts, and change listeners.
 */
public class FeatureFlags {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    /**
     * Represents a feature flag with metadata.
     */
    public static class Flag {
        private final String name;
        private final String description;
        private volatile boolean enabled;
        private volatile double rolloutPercentage; // 0.0 to 1.0
        private final Map<String, String> metadata = new ConcurrentHashMap<>();
        private volatile long lastModified;
        
        public Flag(String name, String description, boolean enabled) {
            this.name = name;
            this.description = description;
            this.enabled = enabled;
            this.rolloutPercentage = enabled ? 1.0 : 0.0;
            this.lastModified = System.currentTimeMillis();
        }
        
        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isEnabled() { return enabled; }
        public double getRolloutPercentage() { return rolloutPercentage; }
        public long getLastModified() { return lastModified; }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
            this.rolloutPercentage = enabled ? 1.0 : 0.0;
            this.lastModified = System.currentTimeMillis();
        }
        
        public void setRolloutPercentage(double percentage) {
            this.rolloutPercentage = Math.max(0.0, Math.min(1.0, percentage));
            this.enabled = this.rolloutPercentage > 0.0;
            this.lastModified = System.currentTimeMillis();
        }
        
        public void setMetadata(String key, String value) {
            metadata.put(key, value);
        }
        
        public Optional<String> getMetadata(String key) {
            return Optional.ofNullable(metadata.get(key));
        }
        
        public Map<String, String> getAllMetadata() {
            return new ConcurrentHashMap<>(metadata);
        }
        
        /**
         * Check if this flag is enabled for a specific user/session.
         * Uses consistent hashing for percentage rollouts.
         */
        public boolean isEnabledFor(String identifier) {
            if (!enabled) return false;
            if (rolloutPercentage >= 1.0) return true;
            if (rolloutPercentage <= 0.0) return false;
            
            // Use consistent hashing to determine if user is in rollout
            int hash = (name + identifier).hashCode();
            double userPercentile = Math.abs(hash % 10000) / 10000.0;
            return userPercentile < rolloutPercentage;
        }
    }
    
    private final Map<String, Flag> flags = new ConcurrentHashMap<>();
    private final List<Consumer<Flag>> changeListeners = new ArrayList<>();
    private final String sessionId;
    
    public FeatureFlags(String sessionId) {
        this.sessionId = sessionId != null ? sessionId : "default";
        initializeDefaultFlags();
    }
    
    /**
     * Initialize default feature flags.
     */
    private void initializeDefaultFlags() {
        // Engine feature flags
        defineFlag("engine.advanced.rendering", "Enable advanced rendering features", false);
        defineFlag("engine.hot.reload", "Enable configuration hot reloading", true);
        defineFlag("engine.performance.profiling", "Enable performance profiling", false);
        defineFlag("engine.debug.ui", "Show debug UI overlay", false);
        defineFlag("engine.experimental.physics", "Enable experimental physics features", false);
        
        // Game feature flags
        defineFlag("game.weather.system", "Enable weather system", true);
        defineFlag("game.advanced.lighting", "Enable advanced lighting", false);
        defineFlag("game.multiplayer", "Enable multiplayer features", false);
        defineFlag("game.mod.support", "Enable mod support", false);
        defineFlag("game.analytics", "Enable analytics collection", false);
        
        logManager.info("FeatureFlags", "Default feature flags initialized",
                       "flagCount", flags.size(), "sessionId", sessionId);
    }
    
    /**
     * Define a new feature flag.
     */
    public Flag defineFlag(String name, String description, boolean defaultEnabled) {
        Flag flag = new Flag(name, description, defaultEnabled);
        flags.put(name, flag);
        
        logManager.debug("FeatureFlags", "Feature flag defined",
                        "name", name, "description", description, "enabled", defaultEnabled);
        
        return flag;
    }
    
    /**
     * Check if a feature flag is enabled.
     */
    public boolean isEnabled(String flagName) {
        Flag flag = flags.get(flagName);
        if (flag == null) {
            logManager.warn("FeatureFlags", "Unknown feature flag requested",
                           "flagName", flagName);
            return false;
        }
        
        return flag.isEnabledFor(sessionId);
    }
    
    /**
     * Enable or disable a feature flag.
     */
    public void setEnabled(String flagName, boolean enabled) {
        Flag flag = flags.get(flagName);
        if (flag == null) {
            logManager.warn("FeatureFlags", "Attempted to set unknown feature flag",
                           "flagName", flagName, "enabled", enabled);
            return;
        }
        
        boolean wasEnabled = flag.isEnabled();
        flag.setEnabled(enabled);
        
        logManager.info("FeatureFlags", "Feature flag updated",
                       "flagName", flagName, "wasEnabled", wasEnabled, "nowEnabled", enabled);
        
        notifyListeners(flag);
    }
    
    /**
     * Set rollout percentage for gradual feature rollouts.
     */
    public void setRolloutPercentage(String flagName, double percentage) {
        Flag flag = flags.get(flagName);
        if (flag == null) {
            logManager.warn("FeatureFlags", "Attempted to set rollout for unknown feature flag",
                           "flagName", flagName, "percentage", percentage);
            return;
        }
        
        double oldPercentage = flag.getRolloutPercentage();
        flag.setRolloutPercentage(percentage);
        
        logManager.info("FeatureFlags", "Feature flag rollout updated",
                       "flagName", flagName, "oldPercentage", oldPercentage, "newPercentage", percentage);
        
        notifyListeners(flag);
    }
    
    /**
     * Get a feature flag by name.
     */
    public Optional<Flag> getFlag(String flagName) {
        return Optional.ofNullable(flags.get(flagName));
    }
    
    /**
     * Get all feature flags.
     */
    public Map<String, Flag> getAllFlags() {
        return new ConcurrentHashMap<>(flags);
    }
    
    /**
     * Add a change listener for feature flag updates.
     */
    public void addChangeListener(Consumer<Flag> listener) {
        synchronized (changeListeners) {
            changeListeners.add(listener);
        }
    }
    
    /**
     * Remove a change listener.
     */
    public void removeChangeListener(Consumer<Flag> listener) {
        synchronized (changeListeners) {
            changeListeners.remove(listener);
        }
    }
    
    /**
     * Notify all change listeners of a flag update.
     */
    private void notifyListeners(Flag flag) {
        synchronized (changeListeners) {
            for (Consumer<Flag> listener : changeListeners) {
                try {
                    listener.accept(flag);
                } catch (Exception e) {
                    logManager.error("FeatureFlags", "Error in feature flag change listener", e,
                                   "flagName", flag.getName());
                }
            }
        }
    }
    
    /**
     * Load feature flags from configuration.
     */
    public void loadFromConfiguration(Map<String, String> config) {
        for (Map.Entry<String, String> entry : config.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            if (key.startsWith("feature.")) {
                String flagName = key.substring(8); // Remove "feature." prefix
                
                try {
                    if (value.contains("%")) {
                        // Percentage rollout
                        double percentage = Double.parseDouble(value.replace("%", "")) / 100.0;
                        setRolloutPercentage(flagName, percentage);
                    } else {
                        // Boolean flag
                        boolean enabled = Boolean.parseBoolean(value);
                        setEnabled(flagName, enabled);
                    }
                } catch (NumberFormatException e) {
                    logManager.warn("FeatureFlags", "Invalid feature flag value",
                                   "flagName", flagName, "value", value);
                }
            }
        }
    }
    
    /**
     * Export feature flags to configuration format.
     */
    public Map<String, String> exportToConfiguration() {
        Map<String, String> config = new ConcurrentHashMap<>();
        
        for (Flag flag : flags.values()) {
            String key = "feature." + flag.getName();
            String value;
            
            if (flag.getRolloutPercentage() < 1.0 && flag.getRolloutPercentage() > 0.0) {
                value = String.format("%.1f%%", flag.getRolloutPercentage() * 100);
            } else {
                value = String.valueOf(flag.isEnabled());
            }
            
            config.put(key, value);
        }
        
        return config;
    }
}