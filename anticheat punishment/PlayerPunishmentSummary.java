package fps.anticheat.punishment;

import fps.anticheat.ViolationType;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive summary of a player's punishment history and current status.
 */
public class PlayerPunishmentSummary {
    
    private final String playerId;
    private final PlayerBan activeBan;
    private final List<PunishmentRecord> punishmentHistory;
    private final PlayerEscalationProfile escalationProfile;
    private final long timestamp;
    
    public PlayerPunishmentSummary(String playerId, PlayerBan activeBan, 
                                 List<PunishmentRecord> punishmentHistory, 
                                 PlayerEscalationProfile escalationProfile) {
        this.playerId = playerId;
        this.activeBan = activeBan;
        this.punishmentHistory = punishmentHistory;
        this.escalationProfile = escalationProfile;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Check if player is currently banned
     */
    public boolean isCurrentlyBanned() {
        return activeBan != null && !activeBan.isExpired();
    }
    
    /**
     * Get total number of punishments
     */
    public int getTotalPunishments() {
        return punishmentHistory != null ? punishmentHistory.size() : 0;
    }
    
    /**
     * Get number of bans (excluding warnings)
     */
    public int getTotalBans() {
        if (punishmentHistory == null) return 0;
        
        return (int) punishmentHistory.stream()
                .filter(PunishmentRecord::isBan)
                .count();
    }
    
    /**
     * Get number of warnings
     */
    public int getTotalWarnings() {
        if (punishmentHistory == null) return 0;
        
        return (int) punishmentHistory.stream()
                .filter(record -> record.getPunishmentType() == PunishmentType.WARNING)
                .count();
    }
    
    /**
     * Get average punishment severity
     */
    public float getAveragePunishmentSeverity() {
        if (punishmentHistory == null || punishmentHistory.isEmpty()) return 0.0f;
        
        float totalSeverity = 0.0f;
        for (PunishmentRecord record : punishmentHistory) {
            totalSeverity += record.getSeverityScore();
        }
        
        return totalSeverity / punishmentHistory.size();
    }
    
    /**
     * Get most recent punishment
     */
    public PunishmentRecord getMostRecentPunishment() {
        if (punishmentHistory == null || punishmentHistory.isEmpty()) return null;
        
        return punishmentHistory.stream()
                .max((r1, r2) -> Long.compare(r1.getTimestamp(), r2.getTimestamp()))
                .orElse(null);
    }
    
    /**
     * Get risk assessment
     */
    public RiskAssessment getRiskAssessment() {
        if (escalationProfile == null) {
            return new RiskAssessment(RiskLevel.UNKNOWN, 0.0f, "No escalation profile available");
        }
        
        // Calculate risk factors
        int totalViolations = escalationProfile.getTotalViolations();
        boolean hasSuspiciousPatterns = escalationProfile.hasSuspiciousPatterns();
        float violationTrend = escalationProfile.getViolationTrend();
        int totalBans = getTotalBans();
        
        // Determine risk level
        RiskLevel riskLevel;
        float riskScore = 0.0f;
        StringBuilder riskFactors = new StringBuilder();
        
        // Base risk from violations
        if (totalViolations > 100) {
            riskScore += 0.4f;
            riskFactors.append("High violation count (").append(totalViolations).append("); ");
        } else if (totalViolations > 50) {
            riskScore += 0.2f;
            riskFactors.append("Moderate violation count (").append(totalViolations).append("); ");
        }
        
        // Risk from bans
        if (totalBans >= 3) {
            riskScore += 0.3f;
            riskFactors.append("Multiple bans (").append(totalBans).append("); ");
        } else if (totalBans >= 1) {
            riskScore += 0.1f;
            riskFactors.append("Previous bans (").append(totalBans).append("); ");
        }
        
        // Risk from patterns
        if (hasSuspiciousPatterns) {
            riskScore += 0.2f;
            riskFactors.append("Suspicious patterns detected; ");
        }
        
        // Risk from trends
        if (violationTrend > 1.5f) {
            riskScore += 0.1f;
            riskFactors.append("Increasing violation trend; ");
        }
        
        // Current ban status
        if (isCurrentlyBanned()) {
            riskScore += 0.1f;
            riskFactors.append("Currently banned; ");
        }
        
        // Determine risk level
        if (riskScore >= 0.8f) {
            riskLevel = RiskLevel.CRITICAL;
        } else if (riskScore >= 0.6f) {
            riskLevel = RiskLevel.HIGH;
        } else if (riskScore >= 0.4f) {
            riskLevel = RiskLevel.MEDIUM;
        } else if (riskScore >= 0.2f) {
            riskLevel = RiskLevel.LOW;
        } else {
            riskLevel = RiskLevel.MINIMAL;
        }
        
        String riskDescription = riskFactors.length() > 0 ? 
            riskFactors.substring(0, riskFactors.length() - 2) : "No significant risk factors";
        
        return new RiskAssessment(riskLevel, riskScore, riskDescription);
    }
    
    /**
     * Get most common violation type
     */
    public ViolationType getMostCommonViolationType() {
        if (escalationProfile != null) {
            return escalationProfile.getMostCommonViolationType();
        }
        return null;
    }
    
    /**
     * Get detailed summary report
     */
    public String getDetailedSummaryReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== Player Punishment Summary ===\n");
        report.append("Player ID: ").append(playerId).append("\n");
        report.append("Current Status: ").append(isCurrentlyBanned() ? "BANNED" : "ACTIVE").append("\n");
        
        if (activeBan != null) {
            report.append("Active Ban: ").append(activeBan.getBanType().name())
                  .append(" - ").append(activeBan.getReason()).append("\n");
            if (activeBan.getBanType() == BanType.TEMPORARY) {
                report.append("Ban Expires: ").append(formatDuration(activeBan.getRemainingTime())).append("\n");
            }
        }
        
        report.append("\nPunishment History:\n");
        report.append("- Total Punishments: ").append(getTotalPunishments()).append("\n");
        report.append("- Total Bans: ").append(getTotalBans()).append("\n");
        report.append("- Total Warnings: ").append(getTotalWarnings()).append("\n");
        report.append("- Average Severity: ").append(String.format("%.2f", getAveragePunishmentSeverity())).append("\n");
        
        if (escalationProfile != null) {
            report.append("\nEscalation Profile:\n");
            report.append("- Total Violations: ").append(escalationProfile.getTotalViolations()).append("\n");
            report.append("- Violation Trend: ").append(String.format("%.2f", escalationProfile.getViolationTrend())).append("\n");
            report.append("- Severity Trend: ").append(String.format("%.2f", escalationProfile.getSeverityTrend())).append("\n");
            report.append("- Suspicious Patterns: ").append(escalationProfile.hasSuspiciousPatterns() ? "YES" : "NO").append("\n");
            
            ViolationType commonType = getMostCommonViolationType();
            if (commonType != null) {
                report.append("- Most Common Violation: ").append(commonType.name()).append("\n");
            }
        }
        
        RiskAssessment risk = getRiskAssessment();
        report.append("\nRisk Assessment:\n");
        report.append("- Risk Level: ").append(risk.getRiskLevel().name()).append("\n");
        report.append("- Risk Score: ").append(String.format("%.2f", risk.getRiskScore())).append("\n");
        report.append("- Risk Factors: ").append(risk.getDescription()).append("\n");
        
        return report.toString();
    }
    
    /**
     * Format duration in human-readable format
     */
    private String formatDuration(long milliseconds) {
        if (milliseconds <= 0) return "Expired";
        
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + " day" + (days != 1 ? "s" : "");
        } else if (hours > 0) {
            return hours + " hour" + (hours != 1 ? "s" : "");
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        } else {
            return seconds + " second" + (seconds != 1 ? "s" : "");
        }
    }
    
    // Getters
    public String getPlayerId() { return playerId; }
    public PlayerBan getActiveBan() { return activeBan; }
    public List<PunishmentRecord> getPunishmentHistory() { return punishmentHistory; }
    public PlayerEscalationProfile getEscalationProfile() { return escalationProfile; }
    public long getTimestamp() { return timestamp; }
    
    /**
     * Risk assessment result
     */
    public static class RiskAssessment {
        private final RiskLevel riskLevel;
        private final float riskScore;
        private final String description;
        
        public RiskAssessment(RiskLevel riskLevel, float riskScore, String description) {
            this.riskLevel = riskLevel;
            this.riskScore = riskScore;
            this.description = description;
        }
        
        public RiskLevel getRiskLevel() { return riskLevel; }
        public float getRiskScore() { return riskScore; }
        public String getDescription() { return description; }
    }
    
    /**
     * Risk levels for players
     */
    public enum RiskLevel {
        UNKNOWN,    // No data available
        MINIMAL,    // Very low risk
        LOW,        // Low risk
        MEDIUM,     // Medium risk - monitor
        HIGH,       // High risk - close monitoring
        CRITICAL    // Critical risk - immediate attention
    }
}