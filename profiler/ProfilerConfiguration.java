package engine.profiler;

import engine.config.ConfigurationManager;
import engine.config.FeatureFlags;
import engine.logging.LogManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration settings for the profiling system.
 * Provides centralized configuration management for all profiler components.
 * Integrates with the engine's configuration system and feature flags.
 */
public class ProfilerConfiguration {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final ConfigurationManager configManager;
    private final FeatureFlags featureFlags;
    private final ProfilerConfigSource configSource;
    
    // Configuration cache for performance
    private final Map<String, Object> configCache = new ConcurrentHashMap<>();
    private volatile long lastCacheUpdate = 0;
    private static final long CACHE_VALIDITY_MS = 5000; // 5 seconds
    
    public ProfilerConfiguration() {
        this.configManager = ConfigurationManager.getInstance();
        this.featureFlags = new FeatureFlags("profiler");
        this.configSource = new ProfilerConfigSource();
        
        // Initialize profiler-specific feature flags
        initializeFeatureFlags();
        
        // Load configuration from sources
        loadConfiguration();
        
        logManager.info("ProfilerConfiguration", "Profiler configuration initialized with configuration system integration");
    }
    
    public ProfilerConfiguration(ConfigurationManager configManager, FeatureFlags featureFlags) {
        this.configManager = configManager;
        this.featureFlags = featureFlags;
        this.configSource = new ProfilerConfigSource();
        
        initializeFeatureFlags();
        loadConfiguration();
        
        logManager.info("ProfilerConfiguration", "Profiler configuration initialized with provided managers");
    }
    
    /**
     * Initialize profiler-specific feature flags.
     */
    private void initializeFeatureFlags() {
        // Core profiler flags
        featureFlags.defineFlag("profiler.core.enabled", "Enable core profiling system", false);
        featureFlags.defineFlag("profiler.performance.enabled", "Enable performance profiling", true);
        featureFlags.defineFlag("profiler.memory.enabled", "Enable memory profiling", true);
        featureFlags.defineFlag("profiler.network.enabled", "Enable network profiling", true);
        featureFlags.defineFlag("profiler.render.enabled", "Enable render profiling", true);
        featureFlags.defineFlag("profiler.debug.enabled", "Enable debug rendering", false);
        featureFlags.defineFlag("profiler.analyzer.enabled", "Enable performance analysis", true);
        featureFlags.defineFlag("profiler.ui.enabled", "Enable profiler UI", false);
        
        // Advanced features
        featureFlags.defineFlag("profiler.advanced.methodProfiling", "Enable method-level profiling", false);
        featureFlags.defineFlag("profiler.advanced.allocationTracking", "Enable allocation tracking", false);
        featureFlags.defineFlag("profiler.advanced.packetInspection", "Enable network packet inspection", false);
        featureFlags.defineFlag("profiler.advanced.shaderProfiling", "Enable shader profiling", false);
        
        logManager.debug("ProfilerConfiguration", "Profiler feature flags initialized");
    }
    
    /**
     * Load configuration from all sources.
     */
    private void loadConfiguration() {
        // Load from profiler config source
        Map<String, String> profilerConfig = configSource.getAllValues();
        
        // Load feature flag overrides from configuration
        featureFlags.loadFromConfiguration(profilerConfig);
        
        // Cache configuration values
        refreshConfigCache();
        
        logManager.info("ProfilerConfiguration", "Configuration loaded from sources",
                       "configCount", profilerConfig.size());
    }
    
    /**
     * Refresh the configuration cache.
     */
    private void refreshConfigCache() {
        configCache.clear();
        
        // Cache all configuration values for performance
        Map<String, String> allConfig = configSource.getAllValues();
        for (Map.Entry<String, String> entry : allConfig.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            // Parse and cache typed values
            if (key.endsWith(".enabled") || key.contains(".auto") || key.contains("Enabled")) {
                configCache.put(key, Boolean.parseBoolean(value));
            } else if (key.endsWith("Ms") || key.endsWith("Size") || key.endsWith("Depth") || 
                      key.endsWith("History") || key.endsWith("Minutes") || key.contains("Interval")) {
                try {
                    configCache.put(key, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    logManager.warn("ProfilerConfiguration", "Invalid integer config value", 
                                   "key", key, "value", value);
                }
            } else if (key.endsWith("Threshold") || key.contains("Percentage")) {
                try {
                    configCache.put(key, Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    logManager.warn("ProfilerConfiguration", "Invalid double config value",
                                   "key", key, "value", value);
                }
            } else {
                configCache.put(key, value);
            }
        }
        
        lastCacheUpdate = System.currentTimeMillis();
    }
    
    /**
     * Get a configuration value with caching.
     */
    private <T> T getConfigValue(String key, Class<T> type, T defaultValue) {
        // Check if cache needs refresh
        if (System.currentTimeMillis() - lastCacheUpdate > CACHE_VALIDITY_MS) {
            refreshConfigCache();
        }
        
        Object value = configCache.get(key);
        if (value != null && type.isInstance(value)) {
            return type.cast(value);
        }
        
        return defaultValue;
    }
    
    /**
     * Update a configuration value.
     */
    public void setConfigValue(String key, Object value) {
        configSource.updateValue(key, value.toString());
        configCache.put(key, value);
        
        logManager.debug("ProfilerConfiguration", "Configuration value updated",
                        "key", key, "value", value);
    }
    
    // General profiler settings
    public boolean isEnabled() {
        return featureFlags.isEnabled("profiler.core.enabled") || 
               getConfigValue("profiler.enabled", Boolean.class, false);
    }
    
    public void setEnabled(boolean enabled) {
        setConfigValue("profiler.enabled", enabled);
        featureFlags.setEnabled("profiler.core.enabled", enabled);
    }
    
    public boolean isAutoStart() {
        return getConfigValue("profiler.autoStart", Boolean.class, false);
    }
    
    public void setAutoStart(boolean autoStart) {
        setConfigValue("profiler.autoStart", autoStart);
    }
    
    public int getUpdateIntervalMs() {
        return getConfigValue("profiler.updateIntervalMs", Integer.class, 100);
    }
    
    public void setUpdateIntervalMs(int intervalMs) {
        setConfigValue("profiler.updateIntervalMs", intervalMs);
    }
    
    public int getMaxHistorySize() {
        return getConfigValue("profiler.maxHistorySize", Integer.class, 1000);
    }
    
    public void setMaxHistorySize(int maxSize) {
        setConfigValue("profiler.maxHistorySize", maxSize);
    }
    
    public String getExportFormat() {
        return getConfigValue("profiler.exportFormat", String.class, "json");
    }
    
    public void setExportFormat(String format) {
        setConfigValue("profiler.exportFormat", format);
    }
    
    public String getExportPath() {
        return getConfigValue("profiler.exportPath", String.class, "./profiler-reports/");
    }
    
    public void setExportPath(String path) {
        setConfigValue("profiler.exportPath", path);
    }
    
    // Performance profiler settings
    public boolean isPerformanceEnabled() {
        return featureFlags.isEnabled("profiler.performance.enabled") &&
               getConfigValue("profiler.performance.enabled", Boolean.class, true);
    }
    
    public void setPerformanceEnabled(boolean enabled) {
        setConfigValue("profiler.performance.enabled", enabled);
        featureFlags.setEnabled("profiler.performance.enabled", enabled);
    }
    
    public boolean isCpuProfiling() {
        return getConfigValue("profiler.performance.cpuProfiling", Boolean.class, true);
    }
    
    public void setCpuProfiling(boolean enabled) {
        setConfigValue("profiler.performance.cpuProfiling", enabled);
    }
    
    public boolean isGpuProfiling() {
        return getConfigValue("profiler.performance.gpuProfiling", Boolean.class, true);
    }
    
    public void setGpuProfiling(boolean enabled) {
        setConfigValue("profiler.performance.gpuProfiling", enabled);
    }
    
    public boolean isMethodProfiling() {
        return featureFlags.isEnabled("profiler.advanced.methodProfiling") &&
               getConfigValue("profiler.performance.methodProfiling", Boolean.class, false);
    }
    
    public void setMethodProfiling(boolean enabled) {
        setConfigValue("profiler.performance.methodProfiling", enabled);
        featureFlags.setEnabled("profiler.advanced.methodProfiling", enabled);
    }
    
    public int getCallStackDepth() {
        return getConfigValue("profiler.performance.callStackDepth", Integer.class, 10);
    }
    
    public void setCallStackDepth(int depth) {
        setConfigValue("profiler.performance.callStackDepth", depth);
    }
    
    public int getSamplingIntervalMs() {
        return getConfigValue("profiler.performance.samplingIntervalMs", Integer.class, 10);
    }
    
    public void setSamplingIntervalMs(int intervalMs) {
        setConfigValue("profiler.performance.samplingIntervalMs", intervalMs);
    }
    
    // Memory profiler settings
    public boolean isMemoryEnabled() {
        return featureFlags.isEnabled("profiler.memory.enabled") &&
               getConfigValue("profiler.memory.enabled", Boolean.class, true);
    }
    
    public void setMemoryEnabled(boolean enabled) {
        setConfigValue("profiler.memory.enabled", enabled);
        featureFlags.setEnabled("profiler.memory.enabled", enabled);
    }
    
    public boolean isHeapAnalysis() {
        return getConfigValue("profiler.memory.heapAnalysis", Boolean.class, true);
    }
    
    public void setHeapAnalysis(boolean enabled) {
        setConfigValue("profiler.memory.heapAnalysis", enabled);
    }
    
    public boolean isAllocationTracking() {
        return featureFlags.isEnabled("profiler.advanced.allocationTracking") &&
               getConfigValue("profiler.memory.allocationTracking", Boolean.class, false);
    }
    
    public void setAllocationTracking(boolean enabled) {
        setConfigValue("profiler.memory.allocationTracking", enabled);
        featureFlags.setEnabled("profiler.advanced.allocationTracking", enabled);
    }
    
    public boolean isLeakDetection() {
        return getConfigValue("profiler.memory.leakDetection", Boolean.class, true);
    }
    
    public void setLeakDetection(boolean enabled) {
        setConfigValue("profiler.memory.leakDetection", enabled);
    }
    
    public boolean isGcMonitoring() {
        return getConfigValue("profiler.memory.gcMonitoring", Boolean.class, true);
    }
    
    public void setGcMonitoring(boolean enabled) {
        setConfigValue("profiler.memory.gcMonitoring", enabled);
    }
    
    public int getMaxAllocationHistory() {
        return getConfigValue("profiler.memory.maxAllocationHistory", Integer.class, 10000);
    }
    
    public void setMaxAllocationHistory(int maxHistory) {
        setConfigValue("profiler.memory.maxAllocationHistory", maxHistory);
    }
    
    // Network profiler settings
    public boolean isNetworkEnabled() {
        return featureFlags.isEnabled("profiler.network.enabled") &&
               getConfigValue("profiler.network.enabled", Boolean.class, true);
    }
    
    public void setNetworkEnabled(boolean enabled) {
        setConfigValue("profiler.network.enabled", enabled);
        featureFlags.setEnabled("profiler.network.enabled", enabled);
    }
    
    public boolean isBandwidthTracking() {
        return getConfigValue("profiler.network.bandwidthTracking", Boolean.class, true);
    }
    
    public void setBandwidthTracking(boolean enabled) {
        setConfigValue("profiler.network.bandwidthTracking", enabled);
    }
    
    public boolean isLatencyMeasurement() {
        return getConfigValue("profiler.network.latencyMeasurement", Boolean.class, true);
    }
    
    public void setLatencyMeasurement(boolean enabled) {
        setConfigValue("profiler.network.latencyMeasurement", enabled);
    }
    
    public boolean isPacketInspection() {
        return featureFlags.isEnabled("profiler.advanced.packetInspection") &&
               getConfigValue("profiler.network.packetInspection", Boolean.class, false);
    }
    
    public void setPacketInspection(boolean enabled) {
        setConfigValue("profiler.network.packetInspection", enabled);
        featureFlags.setEnabled("profiler.advanced.packetInspection", enabled);
    }
    
    public boolean isConnectionQuality() {
        return getConfigValue("profiler.network.connectionQuality", Boolean.class, true);
    }
    
    public void setConnectionQuality(boolean enabled) {
        setConfigValue("profiler.network.connectionQuality", enabled);
    }
    
    public int getMaxPacketHistory() {
        return getConfigValue("profiler.network.maxPacketHistory", Integer.class, 1000);
    }
    
    public void setMaxPacketHistory(int maxHistory) {
        setConfigValue("profiler.network.maxPacketHistory", maxHistory);
    }
    
    // Render profiler settings
    public boolean isRenderEnabled() {
        return featureFlags.isEnabled("profiler.render.enabled") &&
               getConfigValue("profiler.render.enabled", Boolean.class, true);
    }
    
    public void setRenderEnabled(boolean enabled) {
        setConfigValue("profiler.render.enabled", enabled);
        featureFlags.setEnabled("profiler.render.enabled", enabled);
    }
    
    public boolean isDrawCallTracking() {
        return getConfigValue("profiler.render.drawCallTracking", Boolean.class, true);
    }
    
    public void setDrawCallTracking(boolean enabled) {
        setConfigValue("profiler.render.drawCallTracking", enabled);
    }
    
    public boolean isTriangleCount() {
        return getConfigValue("profiler.render.triangleCount", Boolean.class, true);
    }
    
    public void setTriangleCount(boolean enabled) {
        setConfigValue("profiler.render.triangleCount", enabled);
    }
    
    public boolean isTextureUsage() {
        return getConfigValue("profiler.render.textureUsage", Boolean.class, true);
    }
    
    public void setTextureUsage(boolean enabled) {
        setConfigValue("profiler.render.textureUsage", enabled);
    }
    
    public boolean isGpuMemoryTracking() {
        return getConfigValue("profiler.render.gpuMemoryTracking", Boolean.class, true);
    }
    
    public void setGpuMemoryTracking(boolean enabled) {
        setConfigValue("profiler.render.gpuMemoryTracking", enabled);
    }
    
    public boolean isShaderProfiling() {
        return featureFlags.isEnabled("profiler.advanced.shaderProfiling") &&
               getConfigValue("profiler.render.shaderProfiling", Boolean.class, false);
    }
    
    public void setShaderProfiling(boolean enabled) {
        setConfigValue("profiler.render.shaderProfiling", enabled);
        featureFlags.setEnabled("profiler.advanced.shaderProfiling", enabled);
    }
    
    // Debug renderer settings
    public boolean isDebugEnabled() {
        return featureFlags.isEnabled("profiler.debug.enabled") &&
               getConfigValue("profiler.debug.enabled", Boolean.class, false);
    }
    
    public void setDebugEnabled(boolean enabled) {
        setConfigValue("profiler.debug.enabled", enabled);
        featureFlags.setEnabled("profiler.debug.enabled", enabled);
    }
    
    public boolean isWireframes() {
        return getConfigValue("profiler.debug.wireframes", Boolean.class, true);
    }
    
    public void setWireframes(boolean enabled) {
        setConfigValue("profiler.debug.wireframes", enabled);
    }
    
    public boolean isBoundingBoxes() {
        return getConfigValue("profiler.debug.boundingBoxes", Boolean.class, true);
    }
    
    public void setBoundingBoxes(boolean enabled) {
        setConfigValue("profiler.debug.boundingBoxes", enabled);
    }
    
    public boolean isCollisionShapes() {
        return getConfigValue("profiler.debug.collisionShapes", Boolean.class, false);
    }
    
    public void setCollisionShapes(boolean enabled) {
        setConfigValue("profiler.debug.collisionShapes", enabled);
    }
    
    public boolean isAiPaths() {
        return getConfigValue("profiler.debug.aiPaths", Boolean.class, false);
    }
    
    public void setAiPaths(boolean enabled) {
        setConfigValue("profiler.debug.aiPaths", enabled);
    }
    
    public boolean isPhysicsForces() {
        return getConfigValue("profiler.debug.physicsForces", Boolean.class, false);
    }
    
    public void setPhysicsForces(boolean enabled) {
        setConfigValue("profiler.debug.physicsForces", enabled);
    }
    
    // Performance analyzer settings
    public boolean isAnalyzerEnabled() {
        return featureFlags.isEnabled("profiler.analyzer.enabled") &&
               getConfigValue("profiler.analyzer.enabled", Boolean.class, true);
    }
    
    public void setAnalyzerEnabled(boolean enabled) {
        setConfigValue("profiler.analyzer.enabled", enabled);
        featureFlags.setEnabled("profiler.analyzer.enabled", enabled);
    }
    
    public boolean isRegressionDetection() {
        return getConfigValue("profiler.analyzer.regressionDetection", Boolean.class, true);
    }
    
    public void setRegressionDetection(boolean enabled) {
        setConfigValue("profiler.analyzer.regressionDetection", enabled);
    }
    
    public boolean isBottleneckIdentification() {
        return getConfigValue("profiler.analyzer.bottleneckIdentification", Boolean.class, true);
    }
    
    public void setBottleneckIdentification(boolean enabled) {
        setConfigValue("profiler.analyzer.bottleneckIdentification", enabled);
    }
    
    public boolean isOptimizationSuggestions() {
        return getConfigValue("profiler.analyzer.optimizationSuggestions", Boolean.class, true);
    }
    
    public void setOptimizationSuggestions(boolean enabled) {
        setConfigValue("profiler.analyzer.optimizationSuggestions", enabled);
    }
    
    public boolean isStatisticalAnalysis() {
        return getConfigValue("profiler.analyzer.statisticalAnalysis", Boolean.class, true);
    }
    
    public void setStatisticalAnalysis(boolean enabled) {
        setConfigValue("profiler.analyzer.statisticalAnalysis", enabled);
    }
    
    public double getRegressionThreshold() {
        return getConfigValue("profiler.analyzer.regressionThreshold", Double.class, 0.1);
    }
    
    public void setRegressionThreshold(double threshold) {
        setConfigValue("profiler.analyzer.regressionThreshold", threshold);
    }
    
    // UI settings
    public boolean isUiEnabled() {
        return featureFlags.isEnabled("profiler.ui.enabled") &&
               getConfigValue("profiler.ui.enabled", Boolean.class, false);
    }
    
    public void setUiEnabled(boolean enabled) {
        setConfigValue("profiler.ui.enabled", enabled);
        featureFlags.setEnabled("profiler.ui.enabled", enabled);
    }
    
    public boolean isAutoShowUI() {
        return getConfigValue("profiler.ui.autoShow", Boolean.class, false);
    }
    
    public void setAutoShowUI(boolean autoShow) {
        setConfigValue("profiler.ui.autoShow", autoShow);
    }
    
    public int getUiPositionX() {
        return getConfigValue("profiler.ui.position.x", Integer.class, 10);
    }
    
    public void setUiPositionX(int x) {
        setConfigValue("profiler.ui.position.x", x);
    }
    
    public int getUiPositionY() {
        return getConfigValue("profiler.ui.position.y", Integer.class, 10);
    }
    
    public void setUiPositionY(int y) {
        setConfigValue("profiler.ui.position.y", y);
    }
    
    public int getUiWidth() {
        return getConfigValue("profiler.ui.size.width", Integer.class, 400);
    }
    
    public void setUiWidth(int width) {
        setConfigValue("profiler.ui.size.width", width);
    }
    
    public int getUiHeight() {
        return getConfigValue("profiler.ui.size.height", Integer.class, 600);
    }
    
    public void setUiHeight(int height) {
        setConfigValue("profiler.ui.size.height", height);
    }
    
    public int getUiUpdateIntervalMs() {
        return getConfigValue("profiler.ui.updateIntervalMs", Integer.class, 100);
    }
    
    public void setUiUpdateIntervalMs(int intervalMs) {
        setConfigValue("profiler.ui.updateIntervalMs", intervalMs);
    }
    
    public int getGraphHistorySize() {
        return getConfigValue("profiler.ui.graphHistorySize", Integer.class, 100);
    }
    
    public void setGraphHistorySize(int historySize) {
        setConfigValue("profiler.ui.graphHistorySize", historySize);
    }
    
    // Export settings
    public boolean isAutoExport() {
        return getConfigValue("profiler.export.autoExport", Boolean.class, false);
    }
    
    public void setAutoExport(boolean autoExport) {
        setConfigValue("profiler.export.autoExport", autoExport);
    }
    
    public int getExportIntervalMinutes() {
        return getConfigValue("profiler.export.exportIntervalMinutes", Integer.class, 5);
    }
    
    public void setExportIntervalMinutes(int intervalMinutes) {
        setConfigValue("profiler.export.exportIntervalMinutes", intervalMinutes);
    }
    
    public boolean isIncludeMetadata() {
        return getConfigValue("profiler.export.includeMetadata", Boolean.class, true);
    }
    
    public void setIncludeMetadata(boolean includeMetadata) {
        setConfigValue("profiler.export.includeMetadata", includeMetadata);
    }
    
    public boolean isCompressionEnabled() {
        return getConfigValue("profiler.export.compressionEnabled", Boolean.class, true);
    }
    
    public void setCompressionEnabled(boolean compressionEnabled) {
        setConfigValue("profiler.export.compressionEnabled", compressionEnabled);
    }
    
    public long getMaxFileSize() {
        return getConfigValue("profiler.export.maxFileSize", Long.class, 10485760L); // 10MB
    }
    
    public void setMaxFileSize(long maxFileSize) {
        setConfigValue("profiler.export.maxFileSize", maxFileSize);
    }
    
    /**
     * Reload configuration from all sources.
     */
    public void reload() {
        loadConfiguration();
        logManager.info("ProfilerConfiguration", "Configuration reloaded");
    }
    
    /**
     * Validate all configuration values.
     */
    public boolean validate() {
        return configSource.validateConfiguration();
    }
    
    /**
     * Reset configuration to defaults.
     */
    public void resetToDefaults() {
        configSource.resetToDefaults();
        refreshConfigCache();
        logManager.info("ProfilerConfiguration", "Configuration reset to defaults");
    }
    
    /**
     * Get the feature flags instance.
     */
    public FeatureFlags getFeatureFlags() {
        return featureFlags;
    }
    
    /**
     * Get the configuration source.
     */
    public ProfilerConfigSource getConfigSource() {
        return configSource;
    }
    
    /**
     * Get all configuration values as a map.
     */
    public Map<String, Object> getAllConfiguration() {
        return new ConcurrentHashMap<>(configCache);
    }
}