package engine.profiler;

import engine.plugins.*;
import engine.plugins.types.GameLogicPlugin;
import engine.io.Window;
import engine.logging.LogManager;
import engine.config.ConfigurationManager;

/**
 * Profiler Plugin for the Engine Plugin System.
 * Provides comprehensive performance profiling capabilities as a loadable plugin.
 * Integrates with the existing profiler system to enable runtime profiling control.
 */
public class ProfilerPlugin implements Plugin, GameLogicPlugin {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final PluginMetadata metadata;
    private PluginState state = PluginState.UNLOADED;
    private PluginContext context;
    
    // Profiler components
    private ProfilerManager profilerManager;
    private ProfilerUI profilerUI;
    private boolean profilerEnabled = false;
    private boolean uiEnabled = false;
    
    // Plugin statistics
    private long initializationTime = 0;
    private long totalUpdateTime = 0;
    private int updateCount = 0;
    
    public ProfilerPlugin() {
        // Build plugin metadata
        this.metadata = PluginMetadata.builder()
                .id("engine.profiler")
                .name("Engine Profiler")
                .version("1.0.0")
                .description("Comprehensive performance profiling and debugging tools for the engine")
                .author("Engine Team")
                .engineVersion("1.0.0")
                .addPermission("profiler.access")
                .addPermission("profiler.ui")
                .addPermission("profiler.export")
                .addPermission("system.performance")
                .addPermission("system.memory")
                .addPermission("network.monitor")
                .addPermission("render.debug")
                .setSandboxed(false) // Profiler needs full system access
                .setProperty("category", "development")
                .setProperty("priority", "high")
                .build();
    }
    
    @Override
    public PluginMetadata getMetadata() {
        return metadata;
    }
    
    @Override
    public void initialize(PluginContext context) throws PluginException {
        if (state != PluginState.UNLOADED) {
            throw new PluginException("Plugin already initialized");
        }
        
        long startTime = System.currentTimeMillis();
        this.context = context;
        
        try {
            logManager.info("ProfilerPlugin", "Initializing profiler plugin",
                           "pluginId", metadata.getId());
            
            // Check permissions
            if (!context.hasPermission("profiler.access")) {
                throw new PluginException("Insufficient permissions for profiler access");
            }
            
            // Initialize profiler manager
            profilerManager = ProfilerManager.getInstance();
            
            // Configure profiler from plugin settings
            ConfigurationManager configManager = context.getConfigurationManager();
            profilerEnabled = configManager.getBoolean("profiler.plugin.enabled", true);
            uiEnabled = configManager.getBoolean("profiler.plugin.ui.enabled", true);
            
            // Initialize profiler if enabled
            if (profilerEnabled && !profilerManager.isInitialized()) {
                profilerManager.initialize();
                logManager.info("ProfilerPlugin", "Profiler manager initialized by plugin");
            }
            
            // Initialize UI if enabled and permissions allow
            if (uiEnabled && context.hasPermission("profiler.ui")) {
                profilerUI = profilerManager.getProfilerUI();
                if (profilerUI != null) {
                    logManager.info("ProfilerPlugin", "Profiler UI initialized");
                }
            }
            
            // Register plugin services
            context.registerService(ProfilerManager.class, profilerManager);
            if (profilerUI != null) {
                context.registerService(ProfilerUI.class, profilerUI);
            }
            
            // Set plugin configuration
            context.setPluginConfig("profiler.enabled", profilerEnabled);
            context.setPluginConfig("ui.enabled", uiEnabled);
            context.setPluginConfig("initialization.time", System.currentTimeMillis() - startTime);
            
            state = PluginState.INITIALIZED;
            initializationTime = System.currentTimeMillis() - startTime;
            
            logManager.info("ProfilerPlugin", "Profiler plugin initialized successfully",
                           "initTime", initializationTime + "ms",
                           "profilerEnabled", profilerEnabled,
                           "uiEnabled", uiEnabled);
            
        } catch (Exception e) {
            state = PluginState.ERROR;
            logManager.error("ProfilerPlugin", "Failed to initialize profiler plugin", e);
            throw new PluginException("Profiler plugin initialization failed", e);
        }
    }
    
    @Override
    public void start() throws PluginException {
        if (state != PluginState.INITIALIZED) {
            throw new PluginException("Plugin must be initialized before starting");
        }
        
        try {
            logManager.info("ProfilerPlugin", "Starting profiler plugin");
            
            // Start profiler if enabled
            if (profilerEnabled && profilerManager != null) {
                profilerManager.startProfiling();
                logManager.info("ProfilerPlugin", "Profiling started by plugin");
            }
            
            // Enable UI if available
            if (uiEnabled && profilerUI != null) {
                profilerUI.setVisible(true);
                logManager.info("ProfilerPlugin", "Profiler UI enabled");
            }
            
            state = PluginState.STARTED;
            
            // Publish plugin started event
            context.getEventBus().publish(new ProfilerPluginEvent("profiler.plugin.started", metadata.getId()));
            
            logManager.info("ProfilerPlugin", "Profiler plugin started successfully");
            
        } catch (Exception e) {
            state = PluginState.ERROR;
            logManager.error("ProfilerPlugin", "Failed to start profiler plugin", e);
            throw new PluginException("Profiler plugin start failed", e);
        }
    }
    
    @Override
    public void stop() throws PluginException {
        if (state != PluginState.STARTED) {
            return; // Already stopped or not started
        }
        
        try {
            logManager.info("ProfilerPlugin", "Stopping profiler plugin",
                           "totalUpdates", updateCount,
                           "totalUpdateTime", totalUpdateTime + "ms");
            
            // Hide UI if visible
            if (profilerUI != null) {
                profilerUI.setVisible(false);
                logManager.info("ProfilerPlugin", "Profiler UI hidden");
            }
            
            // Stop profiling if we started it
            if (profilerEnabled && profilerManager != null && profilerManager.isProfiling()) {
                profilerManager.stopProfiling();
                logManager.info("ProfilerPlugin", "Profiling stopped by plugin");
            }
            
            state = PluginState.STOPPED;
            
            // Publish plugin stopped event
            context.getEventBus().publish(new ProfilerPluginEvent("profiler.plugin.stopped", metadata.getId()));
            
            logManager.info("ProfilerPlugin", "Profiler plugin stopped successfully");
            
        } catch (Exception e) {
            state = PluginState.ERROR;
            logManager.error("ProfilerPlugin", "Failed to stop profiler plugin", e);
            throw new PluginException("Profiler plugin stop failed", e);
        }
    }
    
    @Override
    public void cleanup() throws PluginException {
        try {
            logManager.info("ProfilerPlugin", "Cleaning up profiler plugin");
            
            // Clear references
            profilerUI = null;
            profilerManager = null;
            context = null;
            
            state = PluginState.UNLOADED;
            
            logManager.info("ProfilerPlugin", "Profiler plugin cleanup complete");
            
        } catch (Exception e) {
            logManager.error("ProfilerPlugin", "Error during profiler plugin cleanup", e);
            throw new PluginException("Profiler plugin cleanup failed", e);
        }
    }
    
    @Override
    public PluginState getState() {
        return state;
    }
    
    @Override
    public boolean isCompatible(String engineVersion) {
        // Simple version compatibility check
        return engineVersion != null && engineVersion.startsWith("1.");
    }
    
    // GameLogicPlugin implementation
    
    @Override
    public void initializeGameLogic() throws PluginException {
        // Game logic initialization handled in main initialize method
        logManager.debug("ProfilerPlugin", "Game logic initialization complete");
    }
    
    @Override
    public void updateGameLogic(float deltaTime) {
        if (state != PluginState.STARTED) {
            return;
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            updateCount++;
            
            // Update profiler manager
            if (profilerManager != null && profilerManager.isProfiling()) {
                profilerManager.update(deltaTime);
            }
            
            // Update UI if enabled
            if (profilerUI != null && profilerUI.isVisible()) {
                profilerUI.update(deltaTime);
            }
            
            // Periodic statistics logging (every 5 seconds)
            if (updateCount % 300 == 0) { // Assuming 60 FPS
                logProfilerStatistics();
            }
            
        } catch (Exception e) {
            logManager.error("ProfilerPlugin", "Error during profiler plugin update", e);
        } finally {
            totalUpdateTime += System.currentTimeMillis() - startTime;
        }
    }
    
    @Override
    public void renderGameLogic(Window window, float deltaTime) {
        if (state != PluginState.STARTED) {
            return;
        }
        
        try {
            // Render profiler UI if enabled
            if (profilerUI != null && profilerUI.isVisible()) {
                profilerUI.render();
            }
            
            // Render debug visualization if enabled
            if (profilerManager != null) {
                DebugRenderer debugRenderer = profilerManager.getDebugRenderer();
                if (debugRenderer != null && debugRenderer.isEnabled()) {
                    // Debug rendering is handled by the render system integration
                }
            }
            
        } catch (Exception e) {
            logManager.error("ProfilerPlugin", "Error during profiler plugin rendering", e);
        }
    }
    
    @Override
    public void cleanupGameLogic() {
        // Cleanup handled in main cleanup method
        logManager.debug("ProfilerPlugin", "Game logic cleanup complete");
    }
    
    @Override
    public int getUpdatePriority() {
        return 1000; // High priority for profiling
    }
    
    @Override
    public boolean handlesInput() {
        return profilerUI != null && profilerUI.isVisible();
    }
    
    @Override
    public boolean updatesWhenPaused() {
        return true; // Profiler should continue when game is paused
    }
    
    @Override
    public String[] getProvidedSystems() {
        return new String[]{
            "ProfilerManager",
            "PerformanceProfiler", 
            "MemoryProfiler",
            "NetworkProfiler",
            "RenderProfiler",
            "DebugRenderer",
            "ProfilerUI"
        };
    }
    
    @Override
    public String[] getProvidedEvents() {
        return new String[]{
            "profiler.plugin.started",
            "profiler.plugin.stopped",
            "profiler.data.collected",
            "profiler.report.generated"
        };
    }
    
    // Plugin-specific methods
    
    /**
     * Get the profiler manager instance.
     * @return Profiler manager or null if not available
     */
    public ProfilerManager getProfilerManager() {
        return profilerManager;
    }
    
    /**
     * Get the profiler UI instance.
     * @return Profiler UI or null if not available
     */
    public ProfilerUI getProfilerUI() {
        return profilerUI;
    }
    
    /**
     * Check if profiling is currently enabled.
     * @return true if profiling is enabled
     */
    public boolean isProfilingEnabled() {
        return profilerEnabled && profilerManager != null && profilerManager.isProfiling();
    }
    
    /**
     * Enable or disable profiling at runtime.
     * @param enabled Whether to enable profiling
     */
    public void setProfilingEnabled(boolean enabled) {
        if (state != PluginState.STARTED) {
            return;
        }
        
        try {
            if (enabled && !isProfilingEnabled()) {
                if (profilerManager != null) {
                    profilerManager.startProfiling();
                    logManager.info("ProfilerPlugin", "Profiling enabled by plugin");
                }
            } else if (!enabled && isProfilingEnabled()) {
                if (profilerManager != null) {
                    profilerManager.stopProfiling();
                    logManager.info("ProfilerPlugin", "Profiling disabled by plugin");
                }
            }
            
            profilerEnabled = enabled;
            context.setPluginConfig("profiler.enabled", enabled);
            
        } catch (Exception e) {
            logManager.error("ProfilerPlugin", "Failed to change profiling state", e);
        }
    }
    
    /**
     * Toggle profiler UI visibility.
     * @param visible Whether UI should be visible
     */
    public void setUIVisible(boolean visible) {
        if (profilerUI != null && context.hasPermission("profiler.ui")) {
            profilerUI.setVisible(visible);
            context.setPluginConfig("ui.visible", visible);
            
            logManager.info("ProfilerPlugin", "Profiler UI visibility changed", "visible", visible);
        }
    }
    
    /**
     * Generate and export a profiling report.
     * @param format Report format ("json" or "csv")
     * @return Path to generated report file
     */
    public String generateReport(String format) {
        if (profilerManager == null || !context.hasPermission("profiler.export")) {
            return null;
        }
        
        try {
            ProfilerReport report = new ProfilerReport(profilerManager);
            String filename = "profiler_report_" + System.currentTimeMillis();
            
            if ("json".equalsIgnoreCase(format)) {
                return report.exportToJSON(filename + ".json");
            } else if ("csv".equalsIgnoreCase(format)) {
                return report.exportToCSV(filename + ".csv");
            }
            
            return null;
            
        } catch (Exception e) {
            logManager.error("ProfilerPlugin", "Failed to generate profiler report", e);
            return null;
        }
    }
    
    /**
     * Get plugin statistics.
     * @return Plugin statistics
     */
    public ProfilerPluginStatistics getStatistics() {
        return new ProfilerPluginStatistics(
            initializationTime,
            updateCount,
            totalUpdateTime,
            profilerEnabled,
            uiEnabled,
            isProfilingEnabled(),
            profilerUI != null && profilerUI.isVisible()
        );
    }
    
    private void logProfilerStatistics() {
        if (profilerManager != null) {
            try {
                ProfilerData data = profilerManager.collectData();
                
                logManager.info("ProfilerPlugin", "Profiler statistics",
                               "frameTime", data.getMetric("performance.frameTime", 0.0),
                               "memoryUsage", data.getMetric("memory.heapUsed", 0L),
                               "networkLatency", data.getMetric("network.latency", 0.0),
                               "drawCalls", data.getMetric("render.drawCalls", 0));
                
                // Publish data collected event
                context.getEventBus().publish(new ProfilerPluginEvent("profiler.data.collected", metadata.getId()));
                
            } catch (Exception e) {
                logManager.error("ProfilerPlugin", "Failed to log profiler statistics", e);
            }
        }
    }
    
    /**
     * Plugin event for profiler-related events.
     */
    public static class ProfilerPluginEvent {
        private final String type;
        private final String pluginId;
        private final long timestamp;
        
        public ProfilerPluginEvent(String type, String pluginId) {
            this.type = type;
            this.pluginId = pluginId;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getType() { return type; }
        public String getPluginId() { return pluginId; }
        public long getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format("ProfilerPluginEvent{type='%s', pluginId='%s', timestamp=%d}", 
                               type, pluginId, timestamp);
        }
    }
    
    /**
     * Statistics for the profiler plugin.
     */
    public static class ProfilerPluginStatistics {
        private final long initializationTime;
        private final int updateCount;
        private final long totalUpdateTime;
        private final boolean profilerEnabled;
        private final boolean uiEnabled;
        private final boolean currentlyProfiling;
        private final boolean uiVisible;
        
        public ProfilerPluginStatistics(long initializationTime, int updateCount, long totalUpdateTime,
                                      boolean profilerEnabled, boolean uiEnabled, 
                                      boolean currentlyProfiling, boolean uiVisible) {
            this.initializationTime = initializationTime;
            this.updateCount = updateCount;
            this.totalUpdateTime = totalUpdateTime;
            this.profilerEnabled = profilerEnabled;
            this.uiEnabled = uiEnabled;
            this.currentlyProfiling = currentlyProfiling;
            this.uiVisible = uiVisible;
        }
        
        public long getInitializationTime() { return initializationTime; }
        public int getUpdateCount() { return updateCount; }
        public long getTotalUpdateTime() { return totalUpdateTime; }
        public double getAverageUpdateTime() { 
            return updateCount > 0 ? (double) totalUpdateTime / updateCount : 0.0; 
        }
        public boolean isProfilerEnabled() { return profilerEnabled; }
        public boolean isUiEnabled() { return uiEnabled; }
        public boolean isCurrentlyProfiling() { return currentlyProfiling; }
        public boolean isUiVisible() { return uiVisible; }
        
        @Override
        public String toString() {
            return String.format("ProfilerPluginStats{initTime=%dms, updates=%d, avgUpdateTime=%.2fms, " +
                               "profiling=%s, uiVisible=%s}", 
                               initializationTime, updateCount, getAverageUpdateTime(), 
                               currentlyProfiling, uiVisible);
        }
    }
}