package fps.anticheat.punishment;

/**
 * Decision result from escalation analysis.
 */
public class EscalationDecision {
    
    private final EscalationLevel escalationLevel;
    private final float punishmentMultiplier;
    private final EscalationFactors factors;
    private final long timestamp;
    
    public EscalationDecision(EscalationLevel escalationLevel, float punishmentMultiplier, 
                            EscalationFactors factors) {
        this.escalationLevel = escalationLevel;
        this.punishmentMultiplier = punishmentMultiplier;
        this.factors = factors;
        this.timestamp = System.currentTimeMillis();
        
        // Set escalation level in factors for reference
        factors.setEscalationLevel(escalationLevel);
    }
    
    /**
     * Check if escalation requires immediate action
     */
    public boolean requiresImmediateAction() {
        return escalationLevel == EscalationLevel.CRITICAL || factors.requiresImmediateAction();
    }
    
    /**
     * Check if escalation suggests hardware ban
     */
    public boolean suggestsHardwareBan() {
        return escalationLevel == EscalationLevel.CRITICAL && 
               (factors.isSuspectedBanEvasion() || factors.getTotalBans() >= 3);
    }
    
    /**
     * Get recommended punishment duration multiplier
     */
    public float getDurationMultiplier() {
        // Duration multiplier is typically higher than base punishment multiplier
        return Math.min(punishmentMultiplier * 1.2f, 10.0f);
    }
    
    /**
     * Get escalation summary message
     */
    public String getEscalationSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Escalation Level: ").append(escalationLevel.name()).append("\n");
        summary.append("Risk Score: ").append(String.format("%.2f", factors.getRiskScore())).append("\n");
        summary.append("Punishment Multiplier: ").append(String.format("%.2fx", punishmentMultiplier)).append("\n");
        
        if (factors.isRepeatOffender()) {
            summary.append("- Repeat offender detected\n");
        }
        
        if (factors.isSuspectedBanEvasion()) {
            summary.append("- Suspected ban evasion\n");
        }
        
        if (factors.getRecentViolations() > 10) {
            summary.append("- High recent violation count (").append(factors.getRecentViolations()).append(")\n");
        }
        
        if (factors.getViolationTrend() > 1.5f) {
            summary.append("- Increasing violation trend\n");
        }
        
        if (factors.getSeverityTrend() > 1.3f) {
            summary.append("- Increasing severity trend\n");
        }
        
        return summary.toString();
    }
    
    // Getters
    public EscalationLevel getEscalationLevel() { return escalationLevel; }
    public float getPunishmentMultiplier() { return punishmentMultiplier; }
    public EscalationFactors getFactors() { return factors; }
    public long getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("EscalationDecision{level=%s, multiplier=%.2f, riskScore=%.2f, timestamp=%d}", 
                           escalationLevel, punishmentMultiplier, factors.getRiskScore(), timestamp);
    }
}