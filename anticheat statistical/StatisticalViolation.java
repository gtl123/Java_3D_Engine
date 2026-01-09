package fps.anticheat.statistical;

import fps.anticheat.ViolationType;

/**
 * Represents a statistical violation detected by the statistical analysis system.
 * Contains detailed information about statistical anomalies and their significance.
 */
public class StatisticalViolation {
    
    private final String playerId;
    private final ViolationType violationType;
    private final String description;
    private final float severity;
    private final long timestamp;
    private final String analyzerType;
    
    private String evidence;
    private float confidence;
    private boolean confirmed;
    private String additionalContext;
    
    /**
     * Create a statistical violation
     */
    public StatisticalViolation(String playerId, ViolationType violationType, String description, 
                               float severity, String analyzerType) {
        this.playerId = playerId;
        this.violationType = violationType;
        this.description = description;
        this.severity = Math.max(0.0f, Math.min(1.0f, severity));
        this.analyzerType = analyzerType;
        this.timestamp = System.currentTimeMillis();
        this.confidence = calculateInitialConfidence(severity, violationType);
        this.confirmed = false;
    }
    
    /**
     * Calculate initial confidence based on severity and violation type
     */
    private float calculateInitialConfidence(float severity, ViolationType violationType) {
        float baseConfidence = severity * 0.8f;
        
        // Adjust confidence based on violation type reliability
        switch (violationType) {
            case STATISTICAL_ANOMALY:
                baseConfidence *= 0.9f;
                break;
            case IMPOSSIBLE_ACCURACY:
                baseConfidence *= 0.95f;
                break;
            case IMPOSSIBLE_HEADSHOT_RATE:
                baseConfidence *= 0.95f;
                break;
            case PERFORMANCE_INCONSISTENCY:
                baseConfidence *= 0.85f;
                break;
            case OUTLIER_BEHAVIOR:
                baseConfidence *= 0.8f;
                break;
            default:
                baseConfidence *= 0.7f;
                break;
        }
        
        return Math.max(0.0f, Math.min(1.0f, baseConfidence));
    }
    
    /**
     * Get violation age in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - timestamp;
    }
    
    /**
     * Check if violation is recent (within specified time)
     */
    public boolean isRecent(long maxAge) {
        return getAge() <= maxAge;
    }
    
    /**
     * Check if violation is critical (high severity and confidence)
     */
    public boolean isCritical() {
        return severity >= 0.8f && confidence >= 0.8f;
    }
    
    /**
     * Check if violation is actionable (sufficient confidence for action)
     */
    public boolean isActionable() {
        return confidence >= 0.7f;
    }
    
    /**
     * Update confidence based on additional evidence
     */
    public void updateConfidence(float newEvidence) {
        // Weighted average with existing confidence
        this.confidence = (confidence * 0.7f) + (newEvidence * 0.3f);
        this.confidence = Math.max(0.0f, Math.min(1.0f, this.confidence));
    }
    
    /**
     * Add evidence to this violation
     */
    public void addEvidence(String newEvidence) {
        if (this.evidence == null || this.evidence.isEmpty()) {
            this.evidence = newEvidence;
        } else {
            this.evidence += "; " + newEvidence;
        }
    }
    
    /**
     * Confirm this violation as valid
     */
    public void confirm() {
        this.confirmed = true;
        // Increase confidence when confirmed
        this.confidence = Math.min(1.0f, this.confidence + 0.1f);
    }
    
    /**
     * Get risk score combining severity and confidence
     */
    public float getRiskScore() {
        return (severity * 0.6f) + (confidence * 0.4f);
    }
    
    /**
     * Get priority for processing (higher is more urgent)
     */
    public int getPriority() {
        float riskScore = getRiskScore();
        
        if (riskScore >= 0.9f) return 5; // Critical
        if (riskScore >= 0.8f) return 4; // High
        if (riskScore >= 0.6f) return 3; // Medium
        if (riskScore >= 0.4f) return 2; // Low
        return 1; // Minimal
    }
    
    /**
     * Create a detailed summary of this violation
     */
    public String getDetailedSummary() {
        return String.format(
            "StatisticalViolation{player=%s, type=%s, analyzer=%s, severity=%.2f, confidence=%.2f, " +
            "risk=%.2f, priority=%d, confirmed=%s, age=%dms, description='%s'}",
            playerId, violationType, analyzerType, severity, confidence, 
            getRiskScore(), getPriority(), confirmed, getAge(), description
        );
    }
    
    /**
     * Create a brief summary of this violation
     */
    public String getSummary() {
        return String.format("StatisticalViolation{player=%s, type=%s, risk=%.2f}",
                           playerId, violationType, getRiskScore());
    }
    
    @Override
    public String toString() {
        return getSummary();
    }
    
    // Getters and setters
    public String getPlayerId() { return playerId; }
    public ViolationType getViolationType() { return violationType; }
    public String getDescription() { return description; }
    public float getSeverity() { return severity; }
    public long getTimestamp() { return timestamp; }
    public String getAnalyzerType() { return analyzerType; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public float getConfidence() { return confidence; }
    public void setConfidence(float confidence) { this.confidence = Math.max(0.0f, Math.min(1.0f, confidence)); }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
    public String getAdditionalContext() { return additionalContext; }
    public void setAdditionalContext(String additionalContext) { this.additionalContext = additionalContext; }
}