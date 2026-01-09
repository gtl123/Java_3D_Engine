package fps.anticheat.punishment;

import fps.anticheat.*;
import fps.anticheat.hardware.HardwareFingerprint;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages punishment escalation for repeat offenders with sophisticated tracking.
 */
public class PunishmentEscalationManager {
    
    private final AntiCheatConfiguration config;
    private final Map<String, PlayerEscalationProfile> escalationProfiles;
    
    // Escalation thresholds
    private static final float SEVERITY_THRESHOLD_WARNING = 0.3f;
    private static final float SEVERITY_THRESHOLD_TEMP_BAN = 0.6f;
    private static final float SEVERITY_THRESHOLD_PERM_BAN = 0.8f;
    
    // Time windows for escalation (in milliseconds)
    private static final long ESCALATION_WINDOW_SHORT = 3600000; // 1 hour
    private static final long ESCALATION_WINDOW_MEDIUM = 86400000; // 24 hours
    private static final long ESCALATION_WINDOW_LONG = 604800000; // 7 days
    
    public PunishmentEscalationManager(AntiCheatConfiguration config) {
        this.config = config;
        this.escalationProfiles = new ConcurrentHashMap<>();
    }
    
    /**
     * Evaluate escalation for a player based on their violation history
     */
    public EscalationDecision evaluateEscalation(String playerId, List<ValidationResult> violations, 
                                               List<PunishmentRecord> punishmentHistory) {
        PlayerEscalationProfile profile = getOrCreateProfile(playerId);
        
        // Update profile with new violations
        profile.addViolations(violations);
        
        // Calculate escalation factors
        EscalationFactors factors = calculateEscalationFactors(profile, punishmentHistory);
        
        // Determine escalation level
        EscalationLevel escalationLevel = determineEscalationLevel(factors);
        
        // Calculate punishment multiplier
        float punishmentMultiplier = calculatePunishmentMultiplier(factors, escalationLevel);
        
        return new EscalationDecision(escalationLevel, punishmentMultiplier, factors);
    }
    
    /**
     * Get or create escalation profile for player
     */
    private PlayerEscalationProfile getOrCreateProfile(String playerId) {
        return escalationProfiles.computeIfAbsent(playerId, k -> new PlayerEscalationProfile(playerId));
    }
    
    /**
     * Calculate escalation factors based on player history
     */
    private EscalationFactors calculateEscalationFactors(PlayerEscalationProfile profile, 
                                                       List<PunishmentRecord> punishmentHistory) {
        long currentTime = System.currentTimeMillis();
        
        // Recent violation frequency
        int recentViolations = profile.getViolationCount(currentTime - ESCALATION_WINDOW_SHORT);
        int mediumTermViolations = profile.getViolationCount(currentTime - ESCALATION_WINDOW_MEDIUM);
        int longTermViolations = profile.getViolationCount(currentTime - ESCALATION_WINDOW_LONG);
        
        // Punishment history analysis
        int recentPunishments = 0;
        int totalBans = 0;
        float averageSeverity = 0.0f;
        
        for (PunishmentRecord record : punishmentHistory) {
            if (currentTime - record.getTimestamp() <= ESCALATION_WINDOW_MEDIUM) {
                recentPunishments++;
            }
            
            if (record.isBan()) {
                totalBans++;
            }
            
            averageSeverity += record.getSeverityScore();
        }
        
        if (!punishmentHistory.isEmpty()) {
            averageSeverity /= punishmentHistory.size();
        }
        
        // Violation pattern analysis
        float violationTrend = profile.getViolationTrend();
        float severityTrend = profile.getSeverityTrend();
        
        // Repeat offense detection
        boolean isRepeatOffender = totalBans >= 2 || recentPunishments >= 3;
        
        // Ban evasion indicators
        boolean suspectedBanEvasion = profile.hasSuspiciousPatterns();
        
        return new EscalationFactors(recentViolations, mediumTermViolations, longTermViolations,
                                   recentPunishments, totalBans, averageSeverity, violationTrend,
                                   severityTrend, isRepeatOffender, suspectedBanEvasion);
    }
    
    /**
     * Determine escalation level based on factors
     */
    private EscalationLevel determineEscalationLevel(EscalationFactors factors) {
        // Critical escalation conditions
        if (factors.isSuspectedBanEvasion() || factors.getTotalBans() >= 3) {
            return EscalationLevel.CRITICAL;
        }
        
        // High escalation conditions
        if (factors.isRepeatOffender() || 
            factors.getRecentViolations() >= 10 ||
            factors.getRecentPunishments() >= 2 ||
            factors.getAverageSeverity() >= SEVERITY_THRESHOLD_PERM_BAN) {
            return EscalationLevel.HIGH;
        }
        
        // Medium escalation conditions
        if (factors.getRecentViolations() >= 5 ||
            factors.getMediumTermViolations() >= 15 ||
            factors.getViolationTrend() > 0.5f ||
            factors.getAverageSeverity() >= SEVERITY_THRESHOLD_TEMP_BAN) {
            return EscalationLevel.MEDIUM;
        }
        
        // Low escalation conditions
        if (factors.getRecentViolations() >= 2 ||
            factors.getLongTermViolations() >= 10 ||
            factors.getAverageSeverity() >= SEVERITY_THRESHOLD_WARNING) {
            return EscalationLevel.LOW;
        }
        
        return EscalationLevel.NONE;
    }
    
    /**
     * Calculate punishment multiplier based on escalation
     */
    private float calculatePunishmentMultiplier(EscalationFactors factors, EscalationLevel escalationLevel) {
        float baseMultiplier = 1.0f;
        
        // Base escalation multiplier
        switch (escalationLevel) {
            case NONE:
                baseMultiplier = 1.0f;
                break;
            case LOW:
                baseMultiplier = 1.2f;
                break;
            case MEDIUM:
                baseMultiplier = 1.5f;
                break;
            case HIGH:
                baseMultiplier = 2.0f;
                break;
            case CRITICAL:
                baseMultiplier = 3.0f;
                break;
        }
        
        // Additional multipliers for specific factors
        if (factors.isRepeatOffender()) {
            baseMultiplier *= 1.3f;
        }
        
        if (factors.isSuspectedBanEvasion()) {
            baseMultiplier *= 2.0f;
        }
        
        // Trend-based adjustments
        if (factors.getViolationTrend() > 0.7f) {
            baseMultiplier *= 1.2f;
        }
        
        if (factors.getSeverityTrend() > 0.7f) {
            baseMultiplier *= 1.3f;
        }
        
        // Cap the multiplier
        return Math.min(baseMultiplier, 5.0f);
    }
    
    /**
     * Check if player should receive immediate hardware ban
     */
    public boolean shouldIssueHardwareBan(String playerId, EscalationFactors factors) {
        return factors.getEscalationLevel() == EscalationLevel.CRITICAL &&
               (factors.isSuspectedBanEvasion() || factors.getTotalBans() >= 3);
    }
    
    /**
     * Get escalation profile for player
     */
    public PlayerEscalationProfile getEscalationProfile(String playerId) {
        return escalationProfiles.get(playerId);
    }
    
    /**
     * Clean up old escalation profiles
     */
    public void cleanup() {
        long cutoffTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000); // 30 days
        
        escalationProfiles.entrySet().removeIf(entry -> {
            PlayerEscalationProfile profile = entry.getValue();
            return profile.getLastActivity() < cutoffTime && profile.getTotalViolations() == 0;
        });
    }
    
    /**
     * Get escalation statistics
     */
    public EscalationStatistics getEscalationStatistics() {
        int totalProfiles = escalationProfiles.size();
        int activeProfiles = 0;
        int criticalProfiles = 0;
        int highRiskProfiles = 0;
        
        long currentTime = System.currentTimeMillis();
        long activeThreshold = currentTime - ESCALATION_WINDOW_MEDIUM;
        
        for (PlayerEscalationProfile profile : escalationProfiles.values()) {
            if (profile.getLastActivity() > activeThreshold) {
                activeProfiles++;
                
                int recentViolations = profile.getViolationCount(currentTime - ESCALATION_WINDOW_SHORT);
                if (recentViolations >= 10 || profile.hasSuspiciousPatterns()) {
                    criticalProfiles++;
                } else if (recentViolations >= 5) {
                    highRiskProfiles++;
                }
            }
        }
        
        return new EscalationStatistics(totalProfiles, activeProfiles, criticalProfiles, highRiskProfiles);
    }
}