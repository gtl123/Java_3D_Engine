package fps.anticheat.punishment;

import fps.anticheat.ValidationResult;
import fps.anticheat.ViolationType;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tracks escalation-related data for a specific player.
 */
public class PlayerEscalationProfile {
    
    private final String playerId;
    private final Queue<ViolationEntry> violationHistory;
    private final Map<ViolationType, Integer> violationTypeCounts;
    private final long creationTime;
    
    private long lastActivity;
    private int totalViolations;
    private float totalSeverity;
    
    // Pattern detection
    private final Queue<Long> violationTimestamps;
    private final Queue<Float> severityHistory;
    
    private static final int MAX_HISTORY_SIZE = 1000;
    private static final int PATTERN_ANALYSIS_SIZE = 50;
    
    public PlayerEscalationProfile(String playerId) {
        this.playerId = playerId;
        this.violationHistory = new ConcurrentLinkedQueue<>();
        this.violationTypeCounts = new HashMap<>();
        this.violationTimestamps = new ConcurrentLinkedQueue<>();
        this.severityHistory = new ConcurrentLinkedQueue<>();
        this.creationTime = System.currentTimeMillis();
        this.lastActivity = creationTime;
    }
    
    /**
     * Add new violations to the profile
     */
    public synchronized void addViolations(List<ValidationResult> violations) {
        long timestamp = System.currentTimeMillis();
        this.lastActivity = timestamp;
        
        for (ValidationResult violation : violations) {
            addViolation(violation, timestamp);
        }
        
        // Clean up old entries
        cleanup();
    }
    
    /**
     * Add a single violation
     */
    private void addViolation(ValidationResult violation, long timestamp) {
        ViolationEntry entry = new ViolationEntry(violation, timestamp);
        violationHistory.offer(entry);
        
        // Update counters
        totalViolations++;
        totalSeverity += violation.getSeverity();
        
        // Update type counts
        violationTypeCounts.merge(violation.getViolationType(), 1, Integer::sum);
        
        // Update pattern tracking
        violationTimestamps.offer(timestamp);
        severityHistory.offer(violation.getSeverity());
        
        // Maintain size limits
        if (violationHistory.size() > MAX_HISTORY_SIZE) {
            violationHistory.poll();
        }
        
        if (violationTimestamps.size() > PATTERN_ANALYSIS_SIZE) {
            violationTimestamps.poll();
        }
        
        if (severityHistory.size() > PATTERN_ANALYSIS_SIZE) {
            severityHistory.poll();
        }
    }
    
    /**
     * Get violation count within time window
     */
    public int getViolationCount(long sinceTimestamp) {
        return (int) violationHistory.stream()
                .filter(entry -> entry.getTimestamp() >= sinceTimestamp)
                .count();
    }
    
    /**
     * Get average severity within time window
     */
    public float getAverageSeverity(long sinceTimestamp) {
        List<ViolationEntry> recentViolations = violationHistory.stream()
                .filter(entry -> entry.getTimestamp() >= sinceTimestamp)
                .collect(ArrayList::new, (list, entry) -> list.add(entry), ArrayList::addAll);
        
        if (recentViolations.isEmpty()) {
            return 0.0f;
        }
        
        float totalSeverity = 0.0f;
        for (ViolationEntry entry : recentViolations) {
            totalSeverity += entry.getViolation().getSeverity();
        }
        
        return totalSeverity / recentViolations.size();
    }
    
    /**
     * Calculate violation trend (increasing/decreasing pattern)
     */
    public float getViolationTrend() {
        if (violationTimestamps.size() < 10) {
            return 0.0f; // Not enough data
        }
        
        List<Long> timestamps = new ArrayList<>(violationTimestamps);
        
        // Calculate violations per time period
        long timeSpan = timestamps.get(timestamps.size() - 1) - timestamps.get(0);
        if (timeSpan <= 0) return 0.0f;
        
        // Split into two halves and compare rates
        int midPoint = timestamps.size() / 2;
        long firstHalfTime = timestamps.get(midPoint) - timestamps.get(0);
        long secondHalfTime = timestamps.get(timestamps.size() - 1) - timestamps.get(midPoint);
        
        if (firstHalfTime <= 0 || secondHalfTime <= 0) return 0.0f;
        
        float firstHalfRate = (float) midPoint / firstHalfTime * 3600000; // violations per hour
        float secondHalfRate = (float) (timestamps.size() - midPoint) / secondHalfTime * 3600000;
        
        // Return trend as ratio (>1 = increasing, <1 = decreasing)
        return firstHalfRate > 0 ? secondHalfRate / firstHalfRate : 0.0f;
    }
    
    /**
     * Calculate severity trend
     */
    public float getSeverityTrend() {
        if (severityHistory.size() < 10) {
            return 0.0f;
        }
        
        List<Float> severities = new ArrayList<>(severityHistory);
        
        // Calculate average severity for first and second half
        int midPoint = severities.size() / 2;
        
        float firstHalfAvg = 0.0f;
        for (int i = 0; i < midPoint; i++) {
            firstHalfAvg += severities.get(i);
        }
        firstHalfAvg /= midPoint;
        
        float secondHalfAvg = 0.0f;
        for (int i = midPoint; i < severities.size(); i++) {
            secondHalfAvg += severities.get(i);
        }
        secondHalfAvg /= (severities.size() - midPoint);
        
        return firstHalfAvg > 0 ? secondHalfAvg / firstHalfAvg : 0.0f;
    }
    
    /**
     * Check for suspicious patterns that might indicate ban evasion or coordinated cheating
     */
    public boolean hasSuspiciousPatterns() {
        // Check for rapid violation bursts
        if (hasRapidViolationBursts()) {
            return true;
        }
        
        // Check for consistent high-severity violations
        if (hasConsistentHighSeverityViolations()) {
            return true;
        }
        
        // Check for unusual violation type patterns
        if (hasUnusualViolationTypePatterns()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check for rapid violation bursts (many violations in short time)
     */
    private boolean hasRapidViolationBursts() {
        if (violationTimestamps.size() < 5) return false;
        
        List<Long> timestamps = new ArrayList<>(violationTimestamps);
        
        // Check for 5+ violations within 60 seconds
        for (int i = 0; i <= timestamps.size() - 5; i++) {
            long timeSpan = timestamps.get(i + 4) - timestamps.get(i);
            if (timeSpan <= 60000) { // 60 seconds
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check for consistent high-severity violations
     */
    private boolean hasConsistentHighSeverityViolations() {
        if (severityHistory.size() < 10) return false;
        
        int highSeverityCount = 0;
        for (Float severity : severityHistory) {
            if (severity >= 0.8f) {
                highSeverityCount++;
            }
        }
        
        // More than 70% high-severity violations
        return (float) highSeverityCount / severityHistory.size() > 0.7f;
    }
    
    /**
     * Check for unusual violation type patterns
     */
    private boolean hasUnusualViolationTypePatterns() {
        if (violationTypeCounts.size() < 2) return false;
        
        // Check if one violation type dominates (>90% of violations)
        int maxCount = violationTypeCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return (float) maxCount / totalViolations > 0.9f && totalViolations >= 10;
    }
    
    /**
     * Get most common violation type
     */
    public ViolationType getMostCommonViolationType() {
        return violationTypeCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
    
    /**
     * Clean up old violation entries
     */
    private void cleanup() {
        long cutoffTime = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000); // 7 days
        
        violationHistory.removeIf(entry -> entry.getTimestamp() < cutoffTime);
    }
    
    // Getters
    public String getPlayerId() { return playerId; }
    public long getLastActivity() { return lastActivity; }
    public int getTotalViolations() { return totalViolations; }
    public float getTotalSeverity() { return totalSeverity; }
    public long getCreationTime() { return creationTime; }
    public Map<ViolationType, Integer> getViolationTypeCounts() { return new HashMap<>(violationTypeCounts); }
    
    /**
     * Inner class to represent a violation entry with timestamp
     */
    private static class ViolationEntry {
        private final ValidationResult violation;
        private final long timestamp;
        
        public ViolationEntry(ValidationResult violation, long timestamp) {
            this.violation = violation;
            this.timestamp = timestamp;
        }
        
        public ValidationResult getViolation() { return violation; }
        public long getTimestamp() { return timestamp; }
    }
}