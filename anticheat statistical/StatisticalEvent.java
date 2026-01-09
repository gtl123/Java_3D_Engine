package fps.anticheat.statistical;

import fps.anticheat.PlayerStatistics;

/**
 * Represents a statistical event for analysis.
 * Contains player statistics data and contextual information for statistical pattern detection.
 */
public class StatisticalEvent {
    
    private final String playerId;
    private final PlayerStatistics statistics;
    private final long timestamp;
    private final String eventType;
    
    private String context;
    private float significance;
    private boolean analyzed;
    
    /**
     * Create a statistical event
     */
    public StatisticalEvent(String playerId, PlayerStatistics statistics, long timestamp) {
        this.playerId = playerId;
        this.statistics = statistics;
        this.timestamp = timestamp;
        this.eventType = determineEventType(statistics);
        this.significance = calculateSignificance(statistics);
        this.analyzed = false;
    }
    
    /**
     * Determine event type based on statistics
     */
    private String determineEventType(PlayerStatistics statistics) {
        if (statistics.isSuspicious()) {
            return "SUSPICIOUS_STATISTICS";
        } else if (statistics.getStatisticalSignificance() > 0.8f) {
            return "SIGNIFICANT_UPDATE";
        } else {
            return "ROUTINE_UPDATE";
        }
    }
    
    /**
     * Calculate event significance for analysis priority
     */
    private float calculateSignificance(PlayerStatistics statistics) {
        float significance = statistics.getStatisticalSignificance();
        
        // Increase significance for suspicious statistics
        if (statistics.isSuspicious()) {
            significance += 0.3f;
        }
        
        // Increase significance for high performance metrics
        if (statistics.getAverageAccuracy() > 0.8f) {
            significance += 0.2f;
        }
        
        if (statistics.getHeadshotPercentage() > 0.6f) {
            significance += 0.2f;
        }
        
        if (statistics.getKillDeathRatio() > 10.0f) {
            significance += 0.2f;
        }
        
        return Math.min(1.0f, significance);
    }
    
    /**
     * Get event age in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - timestamp;
    }
    
    /**
     * Check if event is recent (within specified time)
     */
    public boolean isRecent(long maxAge) {
        return getAge() <= maxAge;
    }
    
    /**
     * Check if event is significant for analysis
     */
    public boolean isSignificant() {
        return significance >= 0.7f;
    }
    
    /**
     * Check if event indicates suspicious activity
     */
    public boolean isSuspicious() {
        return eventType.equals("SUSPICIOUS_STATISTICS");
    }
    
    /**
     * Mark event as analyzed
     */
    public void markAnalyzed() {
        this.analyzed = true;
    }
    
    /**
     * Create a summary of this event
     */
    public String getSummary() {
        return String.format("StatisticalEvent{player=%s, type=%s, significance=%.2f, age=%dms}",
                           playerId, eventType, significance, getAge());
    }
    
    @Override
    public String toString() {
        return getSummary();
    }
    
    // Getters and setters
    public String getPlayerId() { return playerId; }
    public PlayerStatistics getStatistics() { return statistics; }
    public long getTimestamp() { return timestamp; }
    public String getEventType() { return eventType; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public float getSignificance() { return significance; }
    public void setSignificance(float significance) { this.significance = Math.max(0.0f, Math.min(1.0f, significance)); }
    public boolean isAnalyzed() { return analyzed; }
    public void setAnalyzed(boolean analyzed) { this.analyzed = analyzed; }
}