package fps.anticheat.integration;

import fps.anticheat.integration.PerformanceOptimizer.PerformanceSummary;

/**
 * Comprehensive statistics about the anti-cheat integration system.
 */
public class IntegrationStatistics {
    
    private final boolean integrationActive;
    private final boolean performanceMode;
    private final int activePlayerProfiles;
    private final long interceptedEvents;
    private final long interceptedPackets;
    private final long validatedActions;
    private final PerformanceSummary performanceMetrics;
    private final long timestamp;
    
    public IntegrationStatistics(boolean integrationActive, boolean performanceMode,
                               int activePlayerProfiles, long interceptedEvents,
                               long interceptedPackets, long validatedActions,
                               PerformanceSummary performanceMetrics) {
        this.integrationActive = integrationActive;
        this.performanceMode = performanceMode;
        this.activePlayerProfiles = activePlayerProfiles;
        this.interceptedEvents = interceptedEvents;
        this.interceptedPackets = interceptedPackets;
        this.validatedActions = validatedActions;
        this.performanceMetrics = performanceMetrics;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Get integration efficiency score (0.0 to 1.0)
     */
    public float getIntegrationEfficiency() {
        if (!integrationActive) return 0.0f;
        
        float score = 1.0f;
        
        // Reduce score if performance mode is enabled (reduced functionality)
        if (performanceMode) {
            score *= 0.8f;
        }
        
        // Factor in performance metrics
        if (performanceMetrics != null) {
            // Good FPS indicates efficient integration
            if (performanceMetrics.getFps() >= 60.0) {
                score *= 1.0f;
            } else if (performanceMetrics.getFps() >= 30.0) {
                score *= 0.9f;
            } else {
                score *= 0.7f;
            }
            
            // Low overhead indicates efficient integration
            if (performanceMetrics.getAntiCheatOverhead() <= 5.0) {
                score *= 1.0f;
            } else if (performanceMetrics.getAntiCheatOverhead() <= 15.0) {
                score *= 0.9f;
            } else {
                score *= 0.8f;
            }
        }
        
        return Math.max(0.0f, Math.min(1.0f, score));
    }
    
    /**
     * Get activity level based on processed data
     */
    public ActivityLevel getActivityLevel() {
        long totalActivity = interceptedEvents + interceptedPackets + validatedActions;
        
        if (totalActivity > 100000) {
            return ActivityLevel.VERY_HIGH;
        } else if (totalActivity > 50000) {
            return ActivityLevel.HIGH;
        } else if (totalActivity > 10000) {
            return ActivityLevel.MEDIUM;
        } else if (totalActivity > 1000) {
            return ActivityLevel.LOW;
        } else {
            return ActivityLevel.MINIMAL;
        }
    }
    
    /**
     * Get integration status summary
     */
    public String getStatusSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("=== Anti-Cheat Integration Status ===\n");
        summary.append("Status: ").append(integrationActive ? "ACTIVE" : "INACTIVE").append("\n");
        summary.append("Performance Mode: ").append(performanceMode ? "ENABLED" : "DISABLED").append("\n");
        summary.append("Active Players: ").append(activePlayerProfiles).append("\n");
        summary.append("Activity Level: ").append(getActivityLevel().name()).append("\n");
        summary.append("Efficiency: ").append(String.format("%.1f%%", getIntegrationEfficiency() * 100)).append("\n\n");
        
        summary.append("Processing Statistics:\n");
        summary.append("- Intercepted Events: ").append(interceptedEvents).append("\n");
        summary.append("- Intercepted Packets: ").append(interceptedPackets).append("\n");
        summary.append("- Validated Actions: ").append(validatedActions).append("\n\n");
        
        if (performanceMetrics != null) {
            summary.append("Performance Metrics:\n");
            summary.append("- FPS: ").append(String.format("%.1f", performanceMetrics.getFps())).append("\n");
            summary.append("- CPU Usage: ").append(String.format("%.1f%%", performanceMetrics.getCpuUsage())).append("\n");
            summary.append("- Memory Usage: ").append(String.format("%.1f%%", performanceMetrics.getMemoryUsage())).append("\n");
            summary.append("- Anti-cheat Overhead: ").append(String.format("%.1fms", performanceMetrics.getAntiCheatOverhead())).append("\n");
            summary.append("- Optimization Level: ").append(performanceMetrics.getOptimizationLevel().name()).append("\n");
        }
        
        return summary.toString();
    }
    
    // Getters
    public boolean isIntegrationActive() { return integrationActive; }
    public boolean isPerformanceMode() { return performanceMode; }
    public int getActivePlayerProfiles() { return activePlayerProfiles; }
    public long getInterceptedEvents() { return interceptedEvents; }
    public long getInterceptedPackets() { return interceptedPackets; }
    public long getValidatedActions() { return validatedActions; }
    public PerformanceSummary getPerformanceMetrics() { return performanceMetrics; }
    public long getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("IntegrationStatistics{active=%s, players=%d, events=%d, packets=%d, actions=%d, efficiency=%.2f}", 
                           integrationActive, activePlayerProfiles, interceptedEvents, 
                           interceptedPackets, validatedActions, getIntegrationEfficiency());
    }
    
    /**
     * Activity levels for the integration system
     */
    public enum ActivityLevel {
        MINIMAL,    // Very low activity
        LOW,        // Low activity
        MEDIUM,     // Normal activity
        HIGH,       // High activity
        VERY_HIGH   // Very high activity
    }
}