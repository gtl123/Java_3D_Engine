package engine.profiler;

import engine.config.ConfigSource;
import engine.logging.LogManager;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration source for profiler settings.
 * Integrates with the engine's configuration system to provide profiler-specific settings.
 */
public class ProfilerConfigSource implements ConfigSource {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final Map<String, String> profilerConfig = new ConcurrentHashMap<>();
    private volatile long lastModified;
    private volatile boolean available = true;
    
    public ProfilerConfigSource() {
        initializeDefaultConfiguration();
        this.lastModified = System.currentTimeMillis();
    }
    
    @Override
    public String getName() {
        return "ProfilerConfigSource";
    }
    
    @Override
    public int getPriority() {
        return 100; // Medium priority - can be overridden by system properties or environment
    }
    
    @Override
    public Optional<String> getValue(String key) {
        return Optional.ofNullable(profilerConfig.get(key));
    }
    
    @Override
    public Map<String, String> getAllValues() {
        return new ConcurrentHashMap<>(profilerConfig);
    }
    
    @Override
    public boolean isAvailable() {
        return available;
    }
    
    @Override
    public boolean reload() {
        try {
            // In a real implementation, this would reload from file/database
            logManager.info("ProfilerConfigSource", "Configuration reloaded");
            lastModified = System.currentTimeMillis();
            return true;
        } catch (Exception e) {
            logManager.error("ProfilerConfigSource", "Failed to reload configuration", e);
            return false;
        }
    }
    
    @Override
    public boolean supportsHotReload() {
        return true;
    }
    
    @Override
    public long getLastModified() {
        return lastModified;
    }
    
    /**
     * Initialize default profiler configuration values.
     */
    private void initializeDefaultConfiguration() {
        // General profiler settings
        profilerConfig.put("profiler.enabled", "false");
        profilerConfig.put("profiler.autoStart", "false");
        profilerConfig.put("profiler.updateIntervalMs", "100");
        profilerConfig.put("profiler.maxHistorySize", "1000");
        profilerConfig.put("profiler.exportFormat", "json");
        profilerConfig.put("profiler.exportPath", "./profiler-reports/");
        
        // Performance profiler settings
        profilerConfig.put("profiler.performance.enabled", "true");
        profilerConfig.put("profiler.performance.cpuProfiling", "true");
        profilerConfig.put("profiler.performance.gpuProfiling", "true");
        profilerConfig.put("profiler.performance.methodProfiling", "false");
        profilerConfig.put("profiler.performance.callStackDepth", "10");
        profilerConfig.put("profiler.performance.samplingIntervalMs", "10");
        
        // Memory profiler settings
        profilerConfig.put("profiler.memory.enabled", "true");
        profilerConfig.put("profiler.memory.heapAnalysis", "true");
        profilerConfig.put("profiler.memory.allocationTracking", "false");
        profilerConfig.put("profiler.memory.leakDetection", "true");
        profilerConfig.put("profiler.memory.gcMonitoring", "true");
        profilerConfig.put("profiler.memory.maxAllocationHistory", "10000");
        
        // Network profiler settings
        profilerConfig.put("profiler.network.enabled", "true");
        profilerConfig.put("profiler.network.bandwidthTracking", "true");
        profilerConfig.put("profiler.network.latencyMeasurement", "true");
        profilerConfig.put("profiler.network.packetInspection", "false");
        profilerConfig.put("profiler.network.connectionQuality", "true");
        profilerConfig.put("profiler.network.maxPacketHistory", "1000");
        
        // Render profiler settings
        profilerConfig.put("profiler.render.enabled", "true");
        profilerConfig.put("profiler.render.drawCallTracking", "true");
        profilerConfig.put("profiler.render.triangleCount", "true");
        profilerConfig.put("profiler.render.textureUsage", "true");
        profilerConfig.put("profiler.render.gpuMemoryTracking", "true");
        profilerConfig.put("profiler.render.shaderProfiling", "false");
        
        // Debug renderer settings
        profilerConfig.put("profiler.debug.enabled", "false");
        profilerConfig.put("profiler.debug.wireframes", "true");
        profilerConfig.put("profiler.debug.boundingBoxes", "true");
        profilerConfig.put("profiler.debug.collisionShapes", "false");
        profilerConfig.put("profiler.debug.aiPaths", "false");
        profilerConfig.put("profiler.debug.physicsForces", "false");
        
        // Performance analyzer settings
        profilerConfig.put("profiler.analyzer.enabled", "true");
        profilerConfig.put("profiler.analyzer.regressionDetection", "true");
        profilerConfig.put("profiler.analyzer.bottleneckIdentification", "true");
        profilerConfig.put("profiler.analyzer.optimizationSuggestions", "true");
        profilerConfig.put("profiler.analyzer.statisticalAnalysis", "true");
        profilerConfig.put("profiler.analyzer.regressionThreshold", "0.1");
        
        // UI settings
        profilerConfig.put("profiler.ui.enabled", "false");
        profilerConfig.put("profiler.ui.autoShow", "false");
        profilerConfig.put("profiler.ui.position.x", "10");
        profilerConfig.put("profiler.ui.position.y", "10");
        profilerConfig.put("profiler.ui.size.width", "400");
        profilerConfig.put("profiler.ui.size.height", "600");
        profilerConfig.put("profiler.ui.updateIntervalMs", "100");
        profilerConfig.put("profiler.ui.graphHistorySize", "100");
        
        // Export settings
        profilerConfig.put("profiler.export.autoExport", "false");
        profilerConfig.put("profiler.export.exportIntervalMinutes", "5");
        profilerConfig.put("profiler.export.includeMetadata", "true");
        profilerConfig.put("profiler.export.compressionEnabled", "true");
        profilerConfig.put("profiler.export.maxFileSize", "10485760"); // 10MB
        
        logManager.info("ProfilerConfigSource", "Default profiler configuration initialized",
                       "configCount", profilerConfig.size());
    }
    
    /**
     * Update a configuration value.
     */
    public void updateValue(String key, String value) {
        String oldValue = profilerConfig.get(key);
        profilerConfig.put(key, value);
        lastModified = System.currentTimeMillis();
        
        logManager.debug("ProfilerConfigSource", "Configuration value updated",
                        "key", key, "oldValue", oldValue, "newValue", value);
    }
    
    /**
     * Remove a configuration value.
     */
    public void removeValue(String key) {
        String removedValue = profilerConfig.remove(key);
        if (removedValue != null) {
            lastModified = System.currentTimeMillis();
            logManager.debug("ProfilerConfigSource", "Configuration value removed",
                            "key", key, "removedValue", removedValue);
        }
    }
    
    /**
     * Load configuration from a map (e.g., from file or external source).
     */
    public void loadConfiguration(Map<String, String> config) {
        profilerConfig.clear();
        profilerConfig.putAll(config);
        lastModified = System.currentTimeMillis();
        
        logManager.info("ProfilerConfigSource", "Configuration loaded from external source",
                       "configCount", config.size());
    }
    
    /**
     * Reset to default configuration.
     */
    public void resetToDefaults() {
        profilerConfig.clear();
        initializeDefaultConfiguration();
        
        logManager.info("ProfilerConfigSource", "Configuration reset to defaults");
    }
    
    /**
     * Validate configuration values.
     */
    public boolean validateConfiguration() {
        try {
            // Validate numeric values
            validateIntegerValue("profiler.updateIntervalMs", 1, 10000);
            validateIntegerValue("profiler.maxHistorySize", 100, 100000);
            validateIntegerValue("profiler.performance.callStackDepth", 1, 100);
            validateIntegerValue("profiler.performance.samplingIntervalMs", 1, 1000);
            validateIntegerValue("profiler.memory.maxAllocationHistory", 1000, 1000000);
            validateIntegerValue("profiler.network.maxPacketHistory", 100, 100000);
            validateDoubleValue("profiler.analyzer.regressionThreshold", 0.01, 1.0);
            validateIntegerValue("profiler.export.exportIntervalMinutes", 1, 1440);
            validateIntegerValue("profiler.export.maxFileSize", 1048576, 104857600); // 1MB to 100MB
            
            // Validate enum values
            validateEnumValue("profiler.exportFormat", "json", "csv", "xml");
            
            logManager.info("ProfilerConfigSource", "Configuration validation passed");
            return true;
            
        } catch (Exception e) {
            logManager.error("ProfilerConfigSource", "Configuration validation failed", e);
            return false;
        }
    }
    
    private void validateIntegerValue(String key, int min, int max) {
        String value = profilerConfig.get(key);
        if (value != null) {
            try {
                int intValue = Integer.parseInt(value);
                if (intValue < min || intValue > max) {
                    throw new IllegalArgumentException(
                        String.format("Value for %s must be between %d and %d, got %d", key, min, max, intValue));
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(String.format("Invalid integer value for %s: %s", key, value));
            }
        }
    }
    
    private void validateDoubleValue(String key, double min, double max) {
        String value = profilerConfig.get(key);
        if (value != null) {
            try {
                double doubleValue = Double.parseDouble(value);
                if (doubleValue < min || doubleValue > max) {
                    throw new IllegalArgumentException(
                        String.format("Value for %s must be between %.2f and %.2f, got %.2f", key, min, max, doubleValue));
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(String.format("Invalid double value for %s: %s", key, value));
            }
        }
    }
    
    private void validateEnumValue(String key, String... validValues) {
        String value = profilerConfig.get(key);
        if (value != null) {
            for (String validValue : validValues) {
                if (validValue.equals(value)) {
                    return;
                }
            }
            throw new IllegalArgumentException(
                String.format("Invalid value for %s: %s. Valid values: %s", key, value, String.join(", ", validValues)));
        }
    }
}