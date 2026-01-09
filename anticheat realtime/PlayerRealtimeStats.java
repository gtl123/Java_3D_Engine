package fps.anticheat.realtime;

import java.util.*;

/**
 * Statistics for a player's real-time detection profile.
 * Provides insights into player behavior and detection patterns.
 */
public class PlayerRealtimeStats {
    
    private final String playerId;
    private final int actionCount;
    private final int violationCount;
    private final float suspiciousScore;
    private final long lastActionTime;
    private final Queue<CheatDetection> detectionHistory;
    
    // Calculated metrics
    private final float violationRate;
    private final long profileAge;
    private final Map<String, Integer> violationsByType;
    private final Map<String, Float> confidenceByType;
    
    public PlayerRealtimeStats(String playerId, int actionCount, int violationCount, 
                              float suspiciousScore, long lastActionTime, 
                              Queue<CheatDetection> detectionHistory) {
        this.playerId = playerId;
        this.actionCount = actionCount;
        this.violationCount = violationCount;
        this.suspiciousScore = suspiciousScore;
        this.lastActionTime = lastActionTime;
        this.detectionHistory = new LinkedList<>(detectionHistory);
        
        // Calculate derived metrics
        this.violationRate = actionCount > 0 ? (float) violationCount / actionCount : 0.0f;
        this.profileAge = System.currentTimeMillis() - lastActionTime;
        this.violationsByType = calculateViolationsByType();
        this.confidenceByType = calculateConfidenceByType();
    }
    
    /**
     * Calculate violations grouped by type
     */
    private Map<String, Integer> calculateViolationsByType() {
        Map<String, Integer> violations = new HashMap<>();
        
        for (CheatDetection detection : detectionHistory) {
            String type = detection.getViolationType().name();
            violations.merge(type, 1, Integer::sum);
        }
        
        return violations;
    }
    
    /**
     * Calculate average confidence by violation type
     */
    private Map<String, Float> calculateConfidenceByType() {
        Map<String, Float> totalConfidence = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        
        for (CheatDetection detection : detectionHistory) {
            String type = detection.getViolationType().name();
            totalConfidence.merge(type, detection.getConfidence(), Float::sum);
            counts.merge(type, 1, Integer::sum);
        }
        
        Map<String, Float> averageConfidence = new HashMap<>();
        for (Map.Entry<String, Float> entry : totalConfidence.entrySet()) {
            String type = entry.getKey();
            float total = entry.getValue();
            int count = counts.get(type);
            averageConfidence.put(type, total / count);
        }
        
        return averageConfidence;
    }
    
    /**
     * Get recent violations within time window
     */
    public List<CheatDetection> getRecentViolations(long timeWindowMs) {
        long cutoffTime = System.currentTimeMillis() - timeWindowMs;
        return detectionHistory.stream()
                .filter(detection -> detection.getTimestamp() >= cutoffTime)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get violation rate in recent time window
     */
    public float getRecentViolationRate(long timeWindowMs) {
        List<CheatDetection> recentViolations = getRecentViolations(timeWindowMs);
        return recentViolations.size() / Math.max(1.0f, timeWindowMs / 1000.0f); // violations per second
    }
    
    /**
     * Check if player is currently active
     */
    public boolean isActive(long maxIdleTime) {
        return profileAge <= maxIdleTime;
    }
    
    /**
     * Check if player shows concerning patterns
     */
    public boolean isConcerning() {
        return suspiciousScore > 0.7f || 
               violationRate > 0.1f || 
               getRecentViolationRate(300000) > 0.01f; // > 0.01 violations/sec in last 5 minutes
    }
    
    /**
     * Get most common violation type
     */
    public String getMostCommonViolationType() {
        return violationsByType.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NONE");
    }
    
    /**
     * Get highest confidence violation type
     */
    public String getHighestConfidenceViolationType() {
        return confidenceByType.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NONE");
    }
    
    /**
     * Get statistics summary
     */
    public String getSummary() {
        return String.format(
            "PlayerRealtimeStats{player=%s, actions=%d, violations=%d, rate=%.3f, " +
            "suspicious=%.2f, age=%dms, active=%s}",
            playerId, actionCount, violationCount, violationRate, 
            suspiciousScore, profileAge, isActive(300000)
        );
    }
    
    /**
     * Get detailed statistics report
     */
    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Player Real-time Statistics ===\n");
        report.append("Player ID: ").append(playerId).append("\n");
        report.append("Total Actions: ").append(actionCount).append("\n");
        report.append("Total Violations: ").append(violationCount).append("\n");
        report.append("Violation Rate: ").append(String.format("%.3f", violationRate)).append("\n");
        report.append("Suspicious Score: ").append(String.format("%.2f", suspiciousScore)).append("\n");
        report.append("Profile Age: ").append(profileAge).append("ms\n");
        report.append("Last Action: ").append(new Date(lastActionTime)).append("\n");
        report.append("Active: ").append(isActive(300000)).append("\n");
        report.append("Concerning: ").append(isConcerning()).append("\n");
        
        if (!violationsByType.isEmpty()) {
            report.append("\nViolations by Type:\n");
            violationsByType.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> report.append("  ")
                            .append(entry.getKey())
                            .append(": ")
                            .append(entry.getValue())
                            .append("\n"));
        }
        
        if (!confidenceByType.isEmpty()) {
            report.append("\nAverage Confidence by Type:\n");
            confidenceByType.entrySet().stream()
                    .sorted(Map.Entry.<String, Float>comparingByValue().reversed())
                    .forEach(entry -> report.append("  ")
                            .append(entry.getKey())
                            .append(": ")
                            .append(String.format("%.2f", entry.getValue()))
                            .append("\n"));
        }
        
        // Recent activity
        List<CheatDetection> recent = getRecentViolations(300000); // Last 5 minutes
        if (!recent.isEmpty()) {
            report.append("\nRecent Violations (last 5 minutes): ").append(recent.size()).append("\n");
            report.append("Recent Violation Rate: ").append(String.format("%.4f", getRecentViolationRate(300000))).append(" /sec\n");
        }
        
        return report.toString();
    }
    
    /**
     * Convert to map for serialization
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("playerId", playerId);
        map.put("actionCount", actionCount);
        map.put("violationCount", violationCount);
        map.put("violationRate", violationRate);
        map.put("suspiciousScore", suspiciousScore);
        map.put("lastActionTime", lastActionTime);
        map.put("profileAge", profileAge);
        map.put("isActive", isActive(300000));
        map.put("isConcerning", isConcerning());
        map.put("mostCommonViolationType", getMostCommonViolationType());
        map.put("highestConfidenceViolationType", getHighestConfidenceViolationType());
        map.put("violationsByType", new HashMap<>(violationsByType));
        map.put("confidenceByType", new HashMap<>(confidenceByType));
        map.put("recentViolationCount", getRecentViolations(300000).size());
        map.put("recentViolationRate", getRecentViolationRate(300000));
        
        return map;
    }
    
    @Override
    public String toString() {
        return getSummary();
    }
    
    // Getters
    public String getPlayerId() { return playerId; }
    public int getActionCount() { return actionCount; }
    public int getViolationCount() { return violationCount; }
    public float getViolationRate() { return violationRate; }
    public float getSuspiciousScore() { return suspiciousScore; }
    public long getLastActionTime() { return lastActionTime; }
    public long getProfileAge() { return profileAge; }
    public Queue<CheatDetection> getDetectionHistory() { return new LinkedList<>(detectionHistory); }
    public Map<String, Integer> getViolationsByType() { return new HashMap<>(violationsByType); }
    public Map<String, Float> getConfidenceByType() { return new HashMap<>(confidenceByType); }
}