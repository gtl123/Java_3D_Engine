package fps.anticheat.punishment;

/**
 * Statistics about escalation system performance.
 */
public class EscalationStatistics {
    
    private final int totalProfiles;
    private final int activeProfiles;
    private final int criticalProfiles;
    private final int highRiskProfiles;
    private final long timestamp;
    
    public EscalationStatistics(int totalProfiles, int activeProfiles, int criticalProfiles, int highRiskProfiles) {
        this.totalProfiles = totalProfiles;
        this.activeProfiles = activeProfiles;
        this.criticalProfiles = criticalProfiles;
        this.highRiskProfiles = highRiskProfiles;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Get percentage of active profiles that are critical
     */
    public float getCriticalProfileRate() {
        if (activeProfiles == 0) return 0.0f;
        return (float) criticalProfiles / activeProfiles * 100.0f;
    }
    
    /**
     * Get percentage of active profiles that are high risk
     */
    public float getHighRiskProfileRate() {
        if (activeProfiles == 0) return 0.0f;
        return (float) highRiskProfiles / activeProfiles * 100.0f;
    }
    
    /**
     * Get percentage of total profiles that are active
     */
    public float getActiveProfileRate() {
        if (totalProfiles == 0) return 0.0f;
        return (float) activeProfiles / totalProfiles * 100.0f;
    }
    
    /**
     * Get combined risk profile count (critical + high risk)
     */
    public int getRiskProfileCount() {
        return criticalProfiles + highRiskProfiles;
    }
    
    /**
     * Get combined risk profile rate
     */
    public float getRiskProfileRate() {
        if (activeProfiles == 0) return 0.0f;
        return (float) getRiskProfileCount() / activeProfiles * 100.0f;
    }
    
    // Getters
    public int getTotalProfiles() { return totalProfiles; }
    public int getActiveProfiles() { return activeProfiles; }
    public int getCriticalProfiles() { return criticalProfiles; }
    public int getHighRiskProfiles() { return highRiskProfiles; }
    public long getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("EscalationStatistics{total=%d, active=%d (%.1f%%), critical=%d (%.1f%%), highRisk=%d (%.1f%%)}", 
                           totalProfiles, activeProfiles, getActiveProfileRate(), 
                           criticalProfiles, getCriticalProfileRate(), 
                           highRiskProfiles, getHighRiskProfileRate());
    }
}