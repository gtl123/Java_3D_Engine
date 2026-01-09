package fps.anticheat.realtime;

import java.util.*;

/**
 * Overall statistics for the real-time detection system.
 * Provides insights into system performance and detection patterns.
 */
public class RealtimeDetectionStats {
    
    private final long totalDetections;
    private final long totalViolations;
    private final int activePlayerProfiles;
    private final Map<String, Long> detectionCounts;
    private final float detectionRate;
    private final float averageProcessingTime;
    private final long timestamp;
    
    // Calculated metrics
    private final float violationRate;
    private final String mostCommonViolationType;
    private final long detectionsPerMinute;
    
    public RealtimeDetectionStats(long totalDetections, long totalViolations, int activePlayerProfiles,
                                 Map<String, Long> detectionCounts, float detectionRate, float averageProcessingTime) {
        this.totalDetections = totalDetections;
        this.totalViolations = totalViolations;
        this.activePlayerProfiles = activePlayerProfiles;
        this.detectionCounts = new HashMap<>(detectionCounts);
        this.detectionRate = detectionRate;
        this.averageProcessingTime = averageProcessingTime;
        this.timestamp = System.currentTimeMillis();
        
        // Calculate derived metrics
        this.violationRate = totalDetections > 0 ? (float) totalViolations / totalDetections : 0.0f;
        this.mostCommonViolationType = calculateMostCommonViolationType();
        this.detectionsPerMinute = calculateDetectionsPerMinute();
    }
    
    /**
     * Calculate most common violation type
     */
    private String calculateMostCommonViolationType() {
        return detectionCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NONE");
    }
    
    /**
     * Calculate detections per minute (estimated)
     */
    private long calculateDetectionsPerMinute() {
        // This is a simplified calculation - in a real system, this would be based on actual time windows
        return Math.round(detectionRate * 60);
    }
    
    /**
     * Get system health status
     */
    public String getHealthStatus() {
        if (averageProcessingTime > 100.0f) {
            return "DEGRADED"; // Processing too slow
        } else if (violationRate > 0.5f) {
            return "HIGH_ACTIVITY"; // High violation rate
        } else if (activePlayerProfiles > 800) {
            return "HIGH_LOAD"; // Many active players
        } else {
            return "HEALTHY";
        }
    }
    
    /**
     * Get system load level
     */
    public String getLoadLevel() {
        if (activePlayerProfiles > 800) {
            return "HIGH";
        } else if (activePlayerProfiles > 400) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
    
    /**
     * Check if system is performing well
     */
    public boolean isPerformingWell() {
        return averageProcessingTime <= 50.0f && // Processing under 50ms
               violationRate <= 0.3f && // Reasonable violation rate
               activePlayerProfiles <= 1000; // Not overloaded
    }
    
    /**
     * Get top violation types by count
     */
    public List<Map.Entry<String, Long>> getTopViolationTypes(int limit) {
        return detectionCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get detection efficiency (violations per detection)
     */
    public float getDetectionEfficiency() {
        return violationRate;
    }
    
    /**
     * Get throughput (detections per second)
     */
    public float getThroughput() {
        return detectionRate;
    }
    
    /**
     * Get statistics age in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - timestamp;
    }
    
    /**
     * Check if statistics are recent
     */
    public boolean isRecent(long maxAge) {
        return getAge() <= maxAge;
    }
    
    /**
     * Get statistics summary
     */
    public String getSummary() {
        return String.format(
            "RealtimeDetectionStats{detections=%d, violations=%d, rate=%.3f, " +
            "players=%d, avgTime=%.1fms, health=%s}",
            totalDetections, totalViolations, violationRate, 
            activePlayerProfiles, averageProcessingTime, getHealthStatus()
        );
    }
    
    /**
     * Get detailed statistics report
     */
    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Real-time Detection System Statistics ===\n");
        report.append("Timestamp: ").append(new Date(timestamp)).append("\n");
        report.append("Age: ").append(getAge()).append("ms\n\n");
        
        // Core metrics
        report.append("Core Metrics:\n");
        report.append("  Total Detections: ").append(totalDetections).append("\n");
        report.append("  Total Violations: ").append(totalViolations).append("\n");
        report.append("  Violation Rate: ").append(String.format("%.3f", violationRate)).append("\n");
        report.append("  Detection Rate: ").append(String.format("%.3f", detectionRate)).append(" /sec\n");
        report.append("  Active Players: ").append(activePlayerProfiles).append("\n");
        report.append("  Avg Processing Time: ").append(String.format("%.1f", averageProcessingTime)).append("ms\n\n");
        
        // Performance metrics
        report.append("Performance:\n");
        report.append("  Health Status: ").append(getHealthStatus()).append("\n");
        report.append("  Load Level: ").append(getLoadLevel()).append("\n");
        report.append("  Performing Well: ").append(isPerformingWell()).append("\n");
        report.append("  Detection Efficiency: ").append(String.format("%.3f", getDetectionEfficiency())).append("\n");
        report.append("  Throughput: ").append(String.format("%.1f", getThroughput())).append(" detections/sec\n");
        report.append("  Est. Detections/min: ").append(detectionsPerMinute).append("\n\n");
        
        // Violation breakdown
        if (!detectionCounts.isEmpty()) {
            report.append("Violation Types:\n");
            report.append("  Most Common: ").append(mostCommonViolationType).append("\n");
            
            List<Map.Entry<String, Long>> topTypes = getTopViolationTypes(10);
            for (Map.Entry<String, Long> entry : topTypes) {
                float percentage = totalViolations > 0 ? 
                    (entry.getValue() * 100.0f) / totalViolations : 0.0f;
                report.append("  ")
                      .append(entry.getKey())
                      .append(": ")
                      .append(entry.getValue())
                      .append(" (")
                      .append(String.format("%.1f", percentage))
                      .append("%)\n");
            }
        }
        
        return report.toString();
    }
    
    /**
     * Get performance metrics
     */
    public Map<String, Object> getPerformanceMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("healthStatus", getHealthStatus());
        metrics.put("loadLevel", getLoadLevel());
        metrics.put("performingWell", isPerformingWell());
        metrics.put("detectionEfficiency", getDetectionEfficiency());
        metrics.put("throughput", getThroughput());
        metrics.put("averageProcessingTime", averageProcessingTime);
        metrics.put("violationRate", violationRate);
        metrics.put("detectionsPerMinute", detectionsPerMinute);
        return metrics;
    }
    
    /**
     * Convert to map for serialization
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("totalDetections", totalDetections);
        map.put("totalViolations", totalViolations);
        map.put("activePlayerProfiles", activePlayerProfiles);
        map.put("detectionRate", detectionRate);
        map.put("averageProcessingTime", averageProcessingTime);
        map.put("timestamp", timestamp);
        map.put("age", getAge());
        
        // Calculated metrics
        map.put("violationRate", violationRate);
        map.put("mostCommonViolationType", mostCommonViolationType);
        map.put("detectionsPerMinute", detectionsPerMinute);
        map.put("healthStatus", getHealthStatus());
        map.put("loadLevel", getLoadLevel());
        map.put("performingWell", isPerformingWell());
        
        // Detection counts
        map.put("detectionCounts", new HashMap<>(detectionCounts));
        
        // Top violation types
        List<Map<String, Object>> topTypes = new ArrayList<>();
        for (Map.Entry<String, Long> entry : getTopViolationTypes(5)) {
            Map<String, Object> typeInfo = new HashMap<>();
            typeInfo.put("type", entry.getKey());
            typeInfo.put("count", entry.getValue());
            typeInfo.put("percentage", totalViolations > 0 ? 
                (entry.getValue() * 100.0f) / totalViolations : 0.0f);
            topTypes.add(typeInfo);
        }
        map.put("topViolationTypes", topTypes);
        
        // Performance metrics
        map.put("performanceMetrics", getPerformanceMetrics());
        
        return map;
    }
    
    @Override
    public String toString() {
        return getSummary();
    }
    
    // Getters
    public long getTotalDetections() { return totalDetections; }
    public long getTotalViolations() { return totalViolations; }
    public int getActivePlayerProfiles() { return activePlayerProfiles; }
    public Map<String, Long> getDetectionCounts() { return new HashMap<>(detectionCounts); }
    public float getDetectionRate() { return detectionRate; }
    public float getAverageProcessingTime() { return averageProcessingTime; }
    public long getTimestamp() { return timestamp; }
    public float getViolationRate() { return violationRate; }
    public String getMostCommonViolationType() { return mostCommonViolationType; }
    public long getDetectionsPerMinute() { return detectionsPerMinute; }
}