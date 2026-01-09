package fps.anticheat.integration;

import fps.core.GameEngine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optimizes anti-cheat performance to minimize impact on game performance.
 * Dynamically adjusts detection intensity based on system resources and game load.
 */
public class PerformanceOptimizer {
    
    private final AntiCheatIntegrationManager integrationManager;
    private volatile boolean performanceMode = false;
    
    // Performance metrics
    private final Map<String, PerformanceMetric> performanceMetrics;
    private final PerformanceProfiler profiler;
    
    // Optimization settings
    private OptimizationLevel currentOptimizationLevel;
    private long lastOptimizationTime;
    
    // Performance thresholds
    private static final double CPU_USAGE_THRESHOLD_HIGH = 80.0;
    private static final double CPU_USAGE_THRESHOLD_CRITICAL = 95.0;
    private static final double MEMORY_USAGE_THRESHOLD_HIGH = 85.0;
    private static final double MEMORY_USAGE_THRESHOLD_CRITICAL = 95.0;
    private static final double FPS_THRESHOLD_LOW = 30.0;
    private static final double FPS_THRESHOLD_CRITICAL = 15.0;
    
    public PerformanceOptimizer(AntiCheatIntegrationManager integrationManager) {
        this.integrationManager = integrationManager;
        this.performanceMetrics = new ConcurrentHashMap<>();
        this.profiler = new PerformanceProfiler();
        this.currentOptimizationLevel = OptimizationLevel.NORMAL;
        this.lastOptimizationTime = System.currentTimeMillis();
        
        initializePerformanceMetrics();
    }
    
    /**
     * Initialize performance metrics tracking
     */
    private void initializePerformanceMetrics() {
        performanceMetrics.put("cpu_usage", new PerformanceMetric("CPU Usage", "%"));
        performanceMetrics.put("memory_usage", new PerformanceMetric("Memory Usage", "%"));
        performanceMetrics.put("fps", new PerformanceMetric("FPS", "fps"));
        performanceMetrics.put("anticheat_overhead", new PerformanceMetric("Anti-cheat Overhead", "ms"));
        performanceMetrics.put("validation_time", new PerformanceMetric("Validation Time", "ms"));
        performanceMetrics.put("detection_time", new PerformanceMetric("Detection Time", "ms"));
    }
    
    /**
     * Perform performance optimization
     */
    public void optimizePerformance() {
        try {
            // Collect current performance metrics
            collectPerformanceMetrics();
            
            // Analyze performance and determine optimization level
            OptimizationLevel newLevel = determineOptimizationLevel();
            
            if (newLevel != currentOptimizationLevel) {
                applyOptimizationLevel(newLevel);
                currentOptimizationLevel = newLevel;
            }
            
            // Apply specific optimizations
            applyDynamicOptimizations();
            
            lastOptimizationTime = System.currentTimeMillis();
            
        } catch (Exception e) {
            System.err.println("Error during performance optimization: " + e.getMessage());
        }
    }
    
    /**
     * Collect current performance metrics
     */
    private void collectPerformanceMetrics() {
        GameEngine gameEngine = integrationManager.getGameEngine();
        
        // CPU usage
        double cpuUsage = profiler.getCPUUsage();
        performanceMetrics.get("cpu_usage").addValue(cpuUsage);
        
        // Memory usage
        double memoryUsage = profiler.getMemoryUsage();
        performanceMetrics.get("memory_usage").addValue(memoryUsage);
        
        // FPS
        double fps = gameEngine.getCurrentFPS();
        performanceMetrics.get("fps").addValue(fps);
        
        // Anti-cheat overhead
        double antiCheatOverhead = profiler.getAntiCheatOverhead();
        performanceMetrics.get("anticheat_overhead").addValue(antiCheatOverhead);
        
        // Validation time
        double validationTime = profiler.getAverageValidationTime();
        performanceMetrics.get("validation_time").addValue(validationTime);
        
        // Detection time
        double detectionTime = profiler.getAverageDetectionTime();
        performanceMetrics.get("detection_time").addValue(detectionTime);
    }
    
    /**
     * Determine appropriate optimization level based on performance metrics
     */
    private OptimizationLevel determineOptimizationLevel() {
        double cpuUsage = performanceMetrics.get("cpu_usage").getAverageValue();
        double memoryUsage = performanceMetrics.get("memory_usage").getAverageValue();
        double fps = performanceMetrics.get("fps").getAverageValue();
        double antiCheatOverhead = performanceMetrics.get("anticheat_overhead").getAverageValue();
        
        // Critical performance issues - maximum optimization
        if (cpuUsage > CPU_USAGE_THRESHOLD_CRITICAL || 
            memoryUsage > MEMORY_USAGE_THRESHOLD_CRITICAL || 
            fps < FPS_THRESHOLD_CRITICAL) {
            return OptimizationLevel.MAXIMUM;
        }
        
        // High performance issues - aggressive optimization
        if (cpuUsage > CPU_USAGE_THRESHOLD_HIGH || 
            memoryUsage > MEMORY_USAGE_THRESHOLD_HIGH || 
            fps < FPS_THRESHOLD_LOW || 
            antiCheatOverhead > 50.0) {
            return OptimizationLevel.AGGRESSIVE;
        }
        
        // Moderate performance issues - moderate optimization
        if (cpuUsage > 60.0 || memoryUsage > 70.0 || fps < 45.0 || antiCheatOverhead > 25.0) {
            return OptimizationLevel.MODERATE;
        }
        
        // Performance mode enabled - light optimization
        if (performanceMode) {
            return OptimizationLevel.LIGHT;
        }
        
        // Normal performance - no optimization needed
        return OptimizationLevel.NORMAL;
    }
    
    /**
     * Apply optimization level settings
     */
    private void applyOptimizationLevel(OptimizationLevel level) {
        switch (level) {
            case NORMAL:
                applyNormalSettings();
                break;
            case LIGHT:
                applyLightOptimization();
                break;
            case MODERATE:
                applyModerateOptimization();
                break;
            case AGGRESSIVE:
                applyAggressiveOptimization();
                break;
            case MAXIMUM:
                applyMaximumOptimization();
                break;
        }
        
        System.out.println("Applied optimization level: " + level.name());
    }
    
    /**
     * Apply normal settings (full anti-cheat functionality)
     */
    private void applyNormalSettings() {
        // Enable all anti-cheat features
        // Set normal detection intervals and thresholds
        // This would configure the anti-cheat engine for full functionality
    }
    
    /**
     * Apply light optimization
     */
    private void applyLightOptimization() {
        // Slightly reduce detection frequency
        // Increase some detection thresholds slightly
        // Reduce background monitoring intensity
    }
    
    /**
     * Apply moderate optimization
     */
    private void applyModerateOptimization() {
        // Reduce detection frequency by 25%
        // Increase detection thresholds by 10%
        // Disable some non-critical monitoring
        // Reduce statistical analysis frequency
    }
    
    /**
     * Apply aggressive optimization
     */
    private void applyAggressiveOptimization() {
        // Reduce detection frequency by 50%
        // Increase detection thresholds by 20%
        // Disable behavioral analysis for low-risk players
        // Reduce real-time monitoring intensity
        // Disable some statistical checks
    }
    
    /**
     * Apply maximum optimization
     */
    private void applyMaximumOptimization() {
        // Reduce detection frequency by 75%
        // Increase detection thresholds by 30%
        // Disable all non-essential monitoring
        // Keep only critical cheat detection (aimbots, speed hacks)
        // Minimal statistical analysis
        // Emergency performance mode
    }
    
    /**
     * Apply dynamic optimizations based on current conditions
     */
    private void applyDynamicOptimizations() {
        GameEngine gameEngine = integrationManager.getGameEngine();
        
        // Adjust based on player count
        int playerCount = gameEngine.getPlayerCount();
        if (playerCount > 50) {
            // High player count - reduce per-player monitoring intensity
            reducePerPlayerMonitoring();
        } else if (playerCount < 10) {
            // Low player count - can afford more intensive monitoring
            increasePerPlayerMonitoring();
        }
        
        // Adjust based on game phase
        if (gameEngine.isInLobby()) {
            // In lobby - minimal monitoring needed
            enableLobbyMode();
        } else if (gameEngine.isInActiveGame()) {
            // Active game - full monitoring
            enableActiveGameMode();
        }
        
        // Adjust based on time of day / server load
        adjustForServerLoad();
    }
    
    /**
     * Reduce per-player monitoring intensity
     */
    private void reducePerPlayerMonitoring() {
        // Implement player-specific monitoring reduction
        // Focus on high-risk players only
        // Reduce monitoring frequency for established players
    }
    
    /**
     * Increase per-player monitoring intensity
     */
    private void increasePerPlayerMonitoring() {
        // Implement enhanced monitoring for all players
        // Enable additional behavioral analysis
        // Increase detection sensitivity
    }
    
    /**
     * Enable lobby mode optimizations
     */
    private void enableLobbyMode() {
        // Minimal anti-cheat activity in lobby
        // Focus on connection validation and basic checks
        // Disable gameplay-specific monitoring
    }
    
    /**
     * Enable active game mode
     */
    private void enableActiveGameMode() {
        // Full anti-cheat monitoring during gameplay
        // Enable all detection systems
        // Maximum protection during active gameplay
    }
    
    /**
     * Adjust for server load
     */
    private void adjustForServerLoad() {
        // This would integrate with server monitoring to adjust
        // anti-cheat intensity based on overall server performance
    }
    
    /**
     * Enable performance mode
     */
    public void enablePerformanceMode() {
        performanceMode = true;
        if (currentOptimizationLevel == OptimizationLevel.NORMAL) {
            applyOptimizationLevel(OptimizationLevel.LIGHT);
            currentOptimizationLevel = OptimizationLevel.LIGHT;
        }
    }
    
    /**
     * Disable performance mode
     */
    public void disablePerformanceMode() {
        performanceMode = false;
        // Will be adjusted in next optimization cycle
    }
    
    /**
     * Get performance metrics
     */
    public Map<String, PerformanceMetric> getPerformanceMetrics() {
        return new HashMap<>(performanceMetrics);
    }
    
    /**
     * Get current optimization level
     */
    public OptimizationLevel getCurrentOptimizationLevel() {
        return currentOptimizationLevel;
    }
    
    /**
     * Get performance summary
     */
    public PerformanceSummary getPerformanceSummary() {
        return new PerformanceSummary(
            currentOptimizationLevel,
            performanceMode,
            performanceMetrics.get("cpu_usage").getAverageValue(),
            performanceMetrics.get("memory_usage").getAverageValue(),
            performanceMetrics.get("fps").getAverageValue(),
            performanceMetrics.get("anticheat_overhead").getAverageValue(),
            System.currentTimeMillis() - lastOptimizationTime
        );
    }
    
    /**
     * Optimization levels
     */
    public enum OptimizationLevel {
        NORMAL,     // Full functionality
        LIGHT,      // Minor optimizations
        MODERATE,   // Moderate optimizations
        AGGRESSIVE, // Aggressive optimizations
        MAXIMUM     // Maximum optimizations (emergency mode)
    }
    
    /**
     * Performance metric tracking
     */
    public static class PerformanceMetric {
        private final String name;
        private final String unit;
        private final List<Double> values;
        private final int maxValues = 100; // Keep last 100 values
        
        public PerformanceMetric(String name, String unit) {
            this.name = name;
            this.unit = unit;
            this.values = new ArrayList<>();
        }
        
        public synchronized void addValue(double value) {
            values.add(value);
            if (values.size() > maxValues) {
                values.remove(0);
            }
        }
        
        public synchronized double getAverageValue() {
            if (values.isEmpty()) return 0.0;
            return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
        
        public synchronized double getLatestValue() {
            if (values.isEmpty()) return 0.0;
            return values.get(values.size() - 1);
        }
        
        public String getName() { return name; }
        public String getUnit() { return unit; }
    }
    
    /**
     * Performance summary
     */
    public static class PerformanceSummary {
        private final OptimizationLevel optimizationLevel;
        private final boolean performanceMode;
        private final double cpuUsage;
        private final double memoryUsage;
        private final double fps;
        private final double antiCheatOverhead;
        private final long lastOptimizationInterval;
        
        public PerformanceSummary(OptimizationLevel optimizationLevel, boolean performanceMode,
                                double cpuUsage, double memoryUsage, double fps,
                                double antiCheatOverhead, long lastOptimizationInterval) {
            this.optimizationLevel = optimizationLevel;
            this.performanceMode = performanceMode;
            this.cpuUsage = cpuUsage;
            this.memoryUsage = memoryUsage;
            this.fps = fps;
            this.antiCheatOverhead = antiCheatOverhead;
            this.lastOptimizationInterval = lastOptimizationInterval;
        }
        
        public OptimizationLevel getOptimizationLevel() { return optimizationLevel; }
        public boolean isPerformanceMode() { return performanceMode; }
        public double getCpuUsage() { return cpuUsage; }
        public double getMemoryUsage() { return memoryUsage; }
        public double getFps() { return fps; }
        public double getAntiCheatOverhead() { return antiCheatOverhead; }
        public long getLastOptimizationInterval() { return lastOptimizationInterval; }
        
        @Override
        public String toString() {
            return String.format("PerformanceSummary{level=%s, cpu=%.1f%%, memory=%.1f%%, fps=%.1f, overhead=%.1fms}", 
                               optimizationLevel, cpuUsage, memoryUsage, fps, antiCheatOverhead);
        }
    }
}