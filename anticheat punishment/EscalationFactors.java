package fps.anticheat.punishment;

/**
 * Factors used to determine punishment escalation level.
 */
public class EscalationFactors {
    
    private final int recentViolations;
    private final int mediumTermViolations;
    private final int longTermViolations;
    private final int recentPunishments;
    private final int totalBans;
    private final float averageSeverity;
    private final float violationTrend;
    private final float severityTrend;
    private final boolean isRepeatOffender;
    private final boolean suspectedBanEvasion;
    private EscalationLevel escalationLevel;
    
    public EscalationFactors(int recentViolations, int mediumTermViolations, int longTermViolations,
                           int recentPunishments, int totalBans, float averageSeverity,
                           float violationTrend, float severityTrend, boolean isRepeatOffender,
                           boolean suspectedBanEvasion) {
        this.recentViolations = recentViolations;
        this.mediumTermViolations = mediumTermViolations;
        this.longTermViolations = longTermViolations;
        this.recentPunishments = recentPunishments;
        this.totalBans = totalBans;
        this.averageSeverity = averageSeverity;
        this.violationTrend = violationTrend;
        this.severityTrend = severityTrend;
        this.isRepeatOffender = isRepeatOffender;
        this.suspectedBanEvasion = suspectedBanEvasion;
    }
    
    /**
     * Calculate overall risk score
     */
    public float getRiskScore() {
        float score = 0.0f;
        
        // Recent activity weight (40%)
        score += (recentViolations * 0.1f) * 0.4f;
        
        // Historical activity weight (20%)
        score += (mediumTermViolations * 0.05f + longTermViolations * 0.02f) * 0.2f;
        
        // Punishment history weight (20%)
        score += (recentPunishments * 0.2f + totalBans * 0.3f) * 0.2f;
        
        // Severity weight (10%)
        score += averageSeverity * 0.1f;
        
        // Trend weight (10%)
        score += (violationTrend * 0.05f + severityTrend * 0.05f) * 0.1f;
        
        // Multipliers for critical factors
        if (isRepeatOffender) {
            score *= 1.5f;
        }
        
        if (suspectedBanEvasion) {
            score *= 2.0f;
        }
        
        return Math.min(score, 1.0f);
    }
    
    /**
     * Check if factors indicate immediate action needed
     */
    public boolean requiresImmediateAction() {
        return suspectedBanEvasion || 
               recentViolations >= 15 || 
               (isRepeatOffender && recentViolations >= 5) ||
               averageSeverity >= 0.9f;
    }
    
    // Getters
    public int getRecentViolations() { return recentViolations; }
    public int getMediumTermViolations() { return mediumTermViolations; }
    public int getLongTermViolations() { return longTermViolations; }
    public int getRecentPunishments() { return recentPunishments; }
    public int getTotalBans() { return totalBans; }
    public float getAverageSeverity() { return averageSeverity; }
    public float getViolationTrend() { return violationTrend; }
    public float getSeverityTrend() { return severityTrend; }
    public boolean isRepeatOffender() { return isRepeatOffender; }
    public boolean isSuspectedBanEvasion() { return suspectedBanEvasion; }
    public EscalationLevel getEscalationLevel() { return escalationLevel; }
    
    public void setEscalationLevel(EscalationLevel escalationLevel) {
        this.escalationLevel = escalationLevel;
    }
    
    @Override
    public String toString() {
        return String.format("EscalationFactors{recent=%d, medium=%d, long=%d, punishments=%d, bans=%d, " +
                           "avgSeverity=%.2f, violationTrend=%.2f, severityTrend=%.2f, repeat=%s, banEvasion=%s, risk=%.2f}", 
                           recentViolations, mediumTermViolations, longTermViolations, recentPunishments, 
                           totalBans, averageSeverity, violationTrend, severityTrend, isRepeatOffender, 
                           suspectedBanEvasion, getRiskScore());
    }
}