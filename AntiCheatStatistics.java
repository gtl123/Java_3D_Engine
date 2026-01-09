package fps.anticheat;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive statistics and metrics for the anti-cheat system.
 * Tracks performance, detection rates, and system health.
 */
public class AntiCheatStatistics {
    
    // System status
    public boolean systemEnabled = false;
    public boolean systemInitialized = false;
    public long systemUptime = 0;
    public long lastUpdateTime = 0;
    
    // Player monitoring
    public long totalPlayersMonitored = 0;
    public int activePlayersMonitored = 0;
    public long totalSessionsTracked = 0;
    
    // Action processing
    public long totalChecksPerformed = 0;
    public long totalActionsValidated = 0;
    public long totalActionsBlocked = 0;
    public float averageValidationTime = 0.0f;
    
    // Violation detection
    public long totalViolationsDetected = 0;
    public long totalFalsePositives = 0;
    public long totalTruePositives = 0;
    public float falsePositiveRate = 0.0f;
    public float detectionAccuracy = 0.0f;
    
    // Violation breakdown by type
    public final Map<ViolationType, Long> violationsByType = new HashMap<>();
    public final Map<ViolationType.Category, Long> violationsByCategory = new HashMap<>();
    
    // Punishment statistics
    public long totalPunishmentsIssued = 0;
    public long totalWarningsIssued = 0;
    public long totalTemporaryBans = 0;
    public long totalPermanentBans = 0;
    public long totalHardwareBans = 0;
    public long totalAppealsSubmitted = 0;
    public long totalAppealsApproved = 0;
    public long totalAppealsDenied = 0;
    
    // Performance metrics
    public float cpuUsagePercent = 0.0f;
    public long memoryUsageMB = 0;
    public int activeThreads = 0;
    public long totalMemoryAllocated = 0;
    public long totalGarbageCollections = 0;
    
    // Component-specific statistics
    public ComponentStatistics serverValidation = new ComponentStatistics();
    public ComponentStatistics clientMonitoring = new ComponentStatistics();
    public ComponentStatistics behavioralAnalysis = new ComponentStatistics();
    public ComponentStatistics statisticalAnalysis = new ComponentStatistics();
    public ComponentStatistics hardwareValidation = new ComponentStatistics();
    public ComponentStatistics realtimeDetection = new ComponentStatistics();
    
    // Network and latency
    public float averagePlayerPing = 0.0f;
    public float averagePacketLoss = 0.0f;
    public long totalNetworkEvents = 0;
    public long totalNetworkAnomalies = 0;
    
    // Time-based metrics (last 24 hours)
    public long violationsLast24Hours = 0;
    public long punishmentsLast24Hours = 0;
    public long playersMonitoredLast24Hours = 0;
    
    public AntiCheatStatistics() {
        // Initialize violation type counters
        for (ViolationType type : ViolationType.values()) {
            violationsByType.put(type, 0L);
        }
        
        // Initialize category counters
        for (ViolationType.Category category : ViolationType.Category.values()) {
            violationsByCategory.put(category, 0L);
        }
        
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Record a violation detection
     */
    public synchronized void recordViolation(ViolationType type, boolean isTruePositive) {
        totalViolationsDetected++;
        
        if (isTruePositive) {
            totalTruePositives++;
        } else {
            totalFalsePositives++;
        }
        
        // Update violation counters
        violationsByType.put(type, violationsByType.get(type) + 1);
        violationsByCategory.put(type.getCategory(), violationsByCategory.get(type.getCategory()) + 1);
        
        // Update accuracy metrics
        updateAccuracyMetrics();
    }
    
    /**
     * Record a punishment issued
     */
    public synchronized void recordPunishment(ViolationType.PunishmentType type) {
        totalPunishmentsIssued++;
        
        switch (type) {
            case WARNING:
                totalWarningsIssued++;
                break;
            case TEMPORARY_BAN:
            case ACCOUNT_SUSPENSION:
                totalTemporaryBans++;
                break;
            case PERMANENT_BAN:
                totalPermanentBans++;
                break;
            case HARDWARE_BAN:
                totalHardwareBans++;
                break;
        }
    }
    
    /**
     * Record an appeal
     */
    public synchronized void recordAppeal(boolean approved) {
        totalAppealsSubmitted++;
        
        if (approved) {
            totalAppealsApproved++;
        } else {
            totalAppealsDenied++;
        }
    }
    
    /**
     * Record action validation
     */
    public synchronized void recordActionValidation(boolean blocked, float validationTimeMs) {
        totalActionsValidated++;
        totalChecksPerformed++;
        
        if (blocked) {
            totalActionsBlocked++;
        }
        
        // Update average validation time
        averageValidationTime = (averageValidationTime * (totalActionsValidated - 1) + validationTimeMs) / totalActionsValidated;
    }
    
    /**
     * Update accuracy metrics
     */
    private void updateAccuracyMetrics() {
        long totalClassified = totalTruePositives + totalFalsePositives;
        
        if (totalClassified > 0) {
            falsePositiveRate = (float) totalFalsePositives / totalClassified;
            detectionAccuracy = (float) totalTruePositives / totalClassified;
        }
    }
    
    /**
     * Update performance metrics
     */
    public void updatePerformanceMetrics() {
        // Get runtime information
        Runtime runtime = Runtime.getRuntime();
        
        // Memory usage
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        memoryUsageMB = (totalMemory - freeMemory) / (1024 * 1024);
        totalMemoryAllocated = totalMemory / (1024 * 1024);
        
        // Thread count
        activeThreads = Thread.activeCount();
        
        // Update timestamp
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Get detection rate (violations per hour)
     */
    public float getDetectionRate() {
        if (systemUptime == 0) {
            return 0.0f;
        }
        
        float hoursUptime = systemUptime / (1000.0f * 60.0f * 60.0f);
        return totalViolationsDetected / hoursUptime;
    }
    
    /**
     * Get blocking rate (percentage of actions blocked)
     */
    public float getBlockingRate() {
        if (totalActionsValidated == 0) {
            return 0.0f;
        }
        
        return (float) totalActionsBlocked / totalActionsValidated * 100.0f;
    }
    
    /**
     * Get appeal success rate
     */
    public float getAppealSuccessRate() {
        if (totalAppealsSubmitted == 0) {
            return 0.0f;
        }
        
        return (float) totalAppealsApproved / totalAppealsSubmitted * 100.0f;
    }
    
    /**
     * Get most common violation type
     */
    public ViolationType getMostCommonViolationType() {
        ViolationType mostCommon = null;
        long maxCount = 0;
        
        for (Map.Entry<ViolationType, Long> entry : violationsByType.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                mostCommon = entry.getKey();
            }
        }
        
        return mostCommon;
    }
    
    /**
     * Get system health score (0.0 to 1.0)
     */
    public float getSystemHealthScore() {
        float score = 1.0f;
        
        // Penalize high false positive rate
        if (falsePositiveRate > 0.1f) {
            score -= (falsePositiveRate - 0.1f) * 2.0f;
        }
        
        // Penalize high CPU usage
        if (cpuUsagePercent > 5.0f) {
            score -= (cpuUsagePercent - 5.0f) / 100.0f;
        }
        
        // Penalize high memory usage
        if (memoryUsageMB > 256) {
            score -= (memoryUsageMB - 256) / 1024.0f;
        }
        
        // Bonus for high detection accuracy
        if (detectionAccuracy > 0.9f) {
            score += (detectionAccuracy - 0.9f) * 0.5f;
        }
        
        return Math.max(0.0f, Math.min(1.0f, score));
    }
    
    /**
     * Generate summary report
     */
    public String generateSummaryReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== Anti-Cheat System Statistics ===\n");
        report.append(String.format("System Status: %s\n", systemEnabled ? "Enabled" : "Disabled"));
        report.append(String.format("Players Monitored: %d active, %d total\n", activePlayersMonitored, totalPlayersMonitored));
        report.append(String.format("Actions Validated: %d (%d blocked)\n", totalActionsValidated, totalActionsBlocked));
        report.append(String.format("Violations Detected: %d (%.1f%% accuracy)\n", totalViolationsDetected, detectionAccuracy * 100));
        report.append(String.format("Punishments Issued: %d total (%d warnings, %d temp bans, %d perm bans)\n", 
                                   totalPunishmentsIssued, totalWarningsIssued, totalTemporaryBans, totalPermanentBans));
        report.append(String.format("Performance: %.1f%% CPU, %d MB memory\n", cpuUsagePercent, memoryUsageMB));
        report.append(String.format("System Health: %.1f%%\n", getSystemHealthScore() * 100));
        
        return report.toString();
    }
    
    /**
     * Reset statistics
     */
    public synchronized void reset() {
        // Reset counters
        totalPlayersMonitored = 0;
        totalChecksPerformed = 0;
        totalActionsValidated = 0;
        totalActionsBlocked = 0;
        totalViolationsDetected = 0;
        totalFalsePositives = 0;
        totalTruePositives = 0;
        totalPunishmentsIssued = 0;
        totalWarningsIssued = 0;
        totalTemporaryBans = 0;
        totalPermanentBans = 0;
        totalHardwareBans = 0;
        totalAppealsSubmitted = 0;
        totalAppealsApproved = 0;
        totalAppealsDenied = 0;
        
        // Reset violation counters
        for (ViolationType type : ViolationType.values()) {
            violationsByType.put(type, 0L);
        }
        
        for (ViolationType.Category category : ViolationType.Category.values()) {
            violationsByCategory.put(category, 0L);
        }
        
        // Reset component statistics
        serverValidation.reset();
        clientMonitoring.reset();
        behavioralAnalysis.reset();
        statisticalAnalysis.reset();
        hardwareValidation.reset();
        realtimeDetection.reset();
        
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Statistics for individual components
     */
    public static class ComponentStatistics {
        public final AtomicLong checksPerformed = new AtomicLong(0);
        public final AtomicLong violationsDetected = new AtomicLong(0);
        public final AtomicLong falsePositives = new AtomicLong(0);
        public final AtomicLong processingTimeMs = new AtomicLong(0);
        public boolean enabled = true;
        public long lastUpdateTime = 0;
        
        public void recordCheck(boolean violationDetected, boolean isFalsePositive, long processingTime) {
            checksPerformed.incrementAndGet();
            processingTimeMs.addAndGet(processingTime);
            
            if (violationDetected) {
                violationsDetected.incrementAndGet();
                
                if (isFalsePositive) {
                    falsePositives.incrementAndGet();
                }
            }
            
            lastUpdateTime = System.currentTimeMillis();
        }
        
        public float getAccuracy() {
            long total = violationsDetected.get();
            if (total == 0) return 1.0f;
            
            long correct = total - falsePositives.get();
            return (float) correct / total;
        }
        
        public float getAverageProcessingTime() {
            long checks = checksPerformed.get();
            if (checks == 0) return 0.0f;
            
            return (float) processingTimeMs.get() / checks;
        }
        
        public void reset() {
            checksPerformed.set(0);
            violationsDetected.set(0);
            falsePositives.set(0);
            processingTimeMs.set(0);
            lastUpdateTime = System.currentTimeMillis();
        }
    }
}