package fps.anticheat.punishment;

/**
 * Comprehensive statistics for the entire punishment system.
 */
public class PunishmentSystemStatistics {
    
    private final BanStatistics banStatistics;
    private final EscalationStatistics escalationStatistics;
    private final long timestamp;
    
    public PunishmentSystemStatistics(BanStatistics banStatistics, EscalationStatistics escalationStatistics) {
        this.banStatistics = banStatistics;
        this.escalationStatistics = escalationStatistics;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Get overall system health score (0.0 to 1.0)
     */
    public float getSystemHealthScore() {
        float score = 1.0f;
        
        // Reduce score based on ban rates
        float banRate = banStatistics.getBanRate();
        if (banRate > 50.0f) {
            score -= 0.3f; // High ban rate indicates problems
        } else if (banRate > 25.0f) {
            score -= 0.1f;
        }
        
        // Reduce score based on critical profiles
        float criticalRate = escalationStatistics.getCriticalProfileRate();
        if (criticalRate > 10.0f) {
            score -= 0.2f; // High critical rate indicates active threats
        } else if (criticalRate > 5.0f) {
            score -= 0.1f;
        }
        
        // Reduce score based on appeal rate (high appeal rate might indicate false positives)
        float appealRate = banStatistics.getAppealRate();
        if (appealRate > 30.0f) {
            score -= 0.2f;
        } else if (appealRate > 15.0f) {
            score -= 0.1f;
        }
        
        return Math.max(0.0f, score);
    }
    
    /**
     * Get system activity level
     */
    public SystemActivityLevel getActivityLevel() {
        int activeBans = banStatistics.getActiveBans();
        int activeProfiles = escalationStatistics.getActiveProfiles();
        int criticalProfiles = escalationStatistics.getCriticalProfiles();
        
        if (criticalProfiles > 50 || activeBans > 1000) {
            return SystemActivityLevel.CRITICAL;
        } else if (criticalProfiles > 20 || activeBans > 500) {
            return SystemActivityLevel.HIGH;
        } else if (criticalProfiles > 5 || activeBans > 100) {
            return SystemActivityLevel.MEDIUM;
        } else if (activeProfiles > 10 || activeBans > 10) {
            return SystemActivityLevel.LOW;
        } else {
            return SystemActivityLevel.MINIMAL;
        }
    }
    
    /**
     * Get effectiveness ratio (bans vs total punishments)
     */
    public float getEffectivenessRatio() {
        int totalPunishments = banStatistics.getTotalPunishments();
        int activeBans = banStatistics.getActiveBans();
        
        if (totalPunishments == 0) return 0.0f;
        
        return (float) activeBans / totalPunishments;
    }
    
    /**
     * Get system summary report
     */
    public String getSystemSummaryReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== Punishment System Statistics ===\n");
        report.append("System Health Score: ").append(String.format("%.1f%%", getSystemHealthScore() * 100)).append("\n");
        report.append("Activity Level: ").append(getActivityLevel().name()).append("\n");
        report.append("Effectiveness Ratio: ").append(String.format("%.2f", getEffectivenessRatio())).append("\n\n");
        
        report.append("Ban Statistics:\n");
        report.append("- Active Bans: ").append(banStatistics.getActiveBans()).append("\n");
        report.append("- Temporary: ").append(banStatistics.getTemporaryBans()).append("\n");
        report.append("- Permanent: ").append(banStatistics.getPermanentBans()).append("\n");
        report.append("- Hardware: ").append(banStatistics.getHardwareBans()).append("\n");
        report.append("- Total Punishments: ").append(banStatistics.getTotalPunishments()).append("\n");
        report.append("- Active Appeals: ").append(banStatistics.getActiveAppeals()).append("\n\n");
        
        report.append("Escalation Statistics:\n");
        report.append("- Total Profiles: ").append(escalationStatistics.getTotalProfiles()).append("\n");
        report.append("- Active Profiles: ").append(escalationStatistics.getActiveProfiles()).append("\n");
        report.append("- Critical Profiles: ").append(escalationStatistics.getCriticalProfiles()).append("\n");
        report.append("- High Risk Profiles: ").append(escalationStatistics.getHighRiskProfiles()).append("\n");
        
        return report.toString();
    }
    
    // Getters
    public BanStatistics getBanStatistics() { return banStatistics; }
    public EscalationStatistics getEscalationStatistics() { return escalationStatistics; }
    public long getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("PunishmentSystemStatistics{health=%.1f%%, activity=%s, bans=%d, profiles=%d, timestamp=%d}", 
                           getSystemHealthScore() * 100, getActivityLevel(), 
                           banStatistics.getActiveBans(), escalationStatistics.getActiveProfiles(), timestamp);
    }
    
    /**
     * System activity levels
     */
    public enum SystemActivityLevel {
        MINIMAL,    // Very low activity
        LOW,        // Low activity
        MEDIUM,     // Normal activity
        HIGH,       // High activity - increased monitoring recommended
        CRITICAL    // Critical activity - immediate attention required
    }
}