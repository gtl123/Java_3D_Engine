package fps.anticheat.statistical;

import fps.anticheat.PlayerStatistics;
import fps.anticheat.ViolationType;
import fps.anticheat.AntiCheatConfiguration;

import java.util.*;

/**
 * Analyzes player headshot statistics to detect impossible or suspicious headshot patterns.
 * Detects aimbots and aim assistance through statistical analysis of headshot rates and patterns.
 */
public class HeadshotAnalyzer {
    
    private final AntiCheatConfiguration config;
    private final Map<String, List<Float>> playerHeadshotHistory;
    private final Map<String, HeadshotProfile> playerProfiles;
    
    // Thresholds for headshot analysis
    private static final float IMPOSSIBLE_HEADSHOT_THRESHOLD = 0.8f;
    private static final float SUSPICIOUS_HEADSHOT_THRESHOLD = 0.6f;
    private static final float CONSISTENCY_THRESHOLD = 0.03f; // Very low variance indicates aimbot
    private static final int MIN_SAMPLES_FOR_ANALYSIS = 15;
    private static final long ANALYSIS_WINDOW_MS = 600000; // 10 minutes
    
    // Professional player benchmarks for comparison
    private static final float PRO_AVERAGE_HEADSHOT_RATE = 0.35f;
    private static final float PRO_MAX_HEADSHOT_RATE = 0.55f;
    
    /**
     * Profile for tracking player headshot patterns
     */
    private static class HeadshotProfile {
        float averageHeadshotRate;
        float variance;
        float trend;
        float streakiness; // Measure of headshot streaks vs random distribution
        int sampleCount;
        int maxConsecutiveHeadshots;
        long lastUpdate;
        boolean flagged;
        
        HeadshotProfile() {
            this.averageHeadshotRate = 0.0f;
            this.variance = 0.0f;
            this.trend = 0.0f;
            this.streakiness = 0.0f;
            this.sampleCount = 0;
            this.maxConsecutiveHeadshots = 0;
            this.lastUpdate = System.currentTimeMillis();
            this.flagged = false;
        }
    }
    
    public HeadshotAnalyzer(AntiCheatConfiguration config) {
        this.config = config;
        this.playerHeadshotHistory = new HashMap<>();
        this.playerProfiles = new HashMap<>();
    }
    
    /**
     * Analyze player headshot statistics for anomalies
     */
    public List<StatisticalViolation> analyzeHeadshots(String playerId, PlayerStatistics statistics) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        // Update player headshot history
        updateHeadshotHistory(playerId, statistics.getHeadshotPercentage());
        
        // Get or create player profile
        HeadshotProfile profile = playerProfiles.computeIfAbsent(playerId, k -> new HeadshotProfile());
        updateProfile(profile, statistics.getHeadshotPercentage());
        
        // Perform various headshot analyses
        violations.addAll(checkImpossibleHeadshotRate(playerId, statistics));
        violations.addAll(checkSuspiciousConsistency(playerId, profile));
        violations.addAll(checkSuddenImprovement(playerId, profile));
        violations.addAll(checkHeadshotStreaks(playerId, statistics, profile));
        violations.addAll(checkDistanceBasedHeadshots(playerId, statistics));
        
        return violations;
    }
    
    /**
     * Update headshot history for a player
     */
    private void updateHeadshotHistory(String playerId, float headshotRate) {
        List<Float> history = playerHeadshotHistory.computeIfAbsent(playerId, k -> new ArrayList<>());
        
        // Add new headshot rate
        history.add(headshotRate);
        
        // Remove old entries outside analysis window
        while (history.size() > MIN_SAMPLES_FOR_ANALYSIS * 3) {
            history.remove(0);
        }
    }
    
    /**
     * Update player headshot profile
     */
    private void updateProfile(HeadshotProfile profile, float newHeadshotRate) {
        profile.sampleCount++;
        
        // Update average using exponential moving average
        float alpha = 0.15f;
        if (profile.sampleCount == 1) {
            profile.averageHeadshotRate = newHeadshotRate;
        } else {
            float oldAverage = profile.averageHeadshotRate;
            profile.averageHeadshotRate = (alpha * newHeadshotRate) + ((1 - alpha) * oldAverage);
            
            // Update variance
            float diff = newHeadshotRate - profile.averageHeadshotRate;
            profile.variance = (alpha * diff * diff) + ((1 - alpha) * profile.variance);
            
            // Update trend
            profile.trend = newHeadshotRate - oldAverage;
            
            // Update streakiness (measure of how "streaky" headshots are)
            updateStreakiness(profile, newHeadshotRate);
        }
        
        profile.lastUpdate = System.currentTimeMillis();
    }
    
    /**
     * Update streakiness metric based on headshot patterns
     */
    private void updateStreakiness(HeadshotProfile profile, float headshotRate) {
        // Simple streakiness calculation - high values indicate unnatural patterns
        if (headshotRate > profile.averageHeadshotRate * 1.5f) {
            profile.streakiness += 0.1f;
        } else if (headshotRate < profile.averageHeadshotRate * 0.5f) {
            profile.streakiness += 0.05f;
        } else {
            profile.streakiness *= 0.95f; // Decay streakiness for normal patterns
        }
        
        profile.streakiness = Math.max(0.0f, Math.min(1.0f, profile.streakiness));
    }
    
    /**
     * Check for impossible headshot rates
     */
    private List<StatisticalViolation> checkImpossibleHeadshotRate(String playerId, PlayerStatistics statistics) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        float headshotRate = statistics.getHeadshotPercentage();
        
        // Check for impossible headshot rate
        if (headshotRate >= IMPOSSIBLE_HEADSHOT_THRESHOLD) {
            float severity = Math.min(1.0f, (headshotRate - IMPOSSIBLE_HEADSHOT_THRESHOLD) / (1.0f - IMPOSSIBLE_HEADSHOT_THRESHOLD));
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.IMPOSSIBLE_HEADSHOT_RATE,
                String.format("Player headshot rate %.1f%% exceeds human limits", headshotRate * 100),
                severity,
                "HeadshotAnalyzer"
            );
            
            violation.addEvidence(String.format("Headshot rate: %.3f, Threshold: %.3f", headshotRate, IMPOSSIBLE_HEADSHOT_THRESHOLD));
            violations.add(violation);
        }
        
        // Check for suspicious headshot rate
        else if (headshotRate >= SUSPICIOUS_HEADSHOT_THRESHOLD) {
            float severity = (headshotRate - SUSPICIOUS_HEADSHOT_THRESHOLD) / (IMPOSSIBLE_HEADSHOT_THRESHOLD - SUSPICIOUS_HEADSHOT_THRESHOLD) * 0.7f;
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                String.format("Player headshot rate %.1f%% is suspiciously high", headshotRate * 100),
                severity,
                "HeadshotAnalyzer"
            );
            
            violation.addEvidence(String.format("Headshot rate: %.3f, Pro max: %.3f", headshotRate, PRO_MAX_HEADSHOT_RATE));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check for suspicious consistency in headshot rates
     */
    private List<StatisticalViolation> checkSuspiciousConsistency(String playerId, HeadshotProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        if (profile.sampleCount < MIN_SAMPLES_FOR_ANALYSIS) {
            return violations;
        }
        
        // Check if variance is suspiciously low for high headshot rates
        if (profile.variance < CONSISTENCY_THRESHOLD && profile.averageHeadshotRate > 0.4f) {
            float severity = Math.min(1.0f, (CONSISTENCY_THRESHOLD - profile.variance) / CONSISTENCY_THRESHOLD);
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                String.format("Player headshot rate is unnaturally consistent (variance: %.4f)", profile.variance),
                severity,
                "HeadshotAnalyzer"
            );
            
            violation.addEvidence(String.format("Variance: %.4f, Threshold: %.4f, Average: %.3f", 
                                               profile.variance, CONSISTENCY_THRESHOLD, profile.averageHeadshotRate));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check for sudden dramatic improvement in headshot rate
     */
    private List<StatisticalViolation> checkSuddenImprovement(String playerId, HeadshotProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        if (profile.sampleCount < MIN_SAMPLES_FOR_ANALYSIS) {
            return violations;
        }
        
        // Check for sudden improvement
        if (profile.trend > 0.25f && profile.averageHeadshotRate > 0.5f) {
            float severity = Math.min(1.0f, profile.trend / 0.4f);
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                String.format("Sudden headshot rate improvement detected (trend: +%.1f%%)", profile.trend * 100),
                severity,
                "HeadshotAnalyzer"
            );
            
            violation.addEvidence(String.format("Trend: %.3f, Current rate: %.3f", profile.trend, profile.averageHeadshotRate));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check for suspicious headshot streaks
     */
    private List<StatisticalViolation> checkHeadshotStreaks(String playerId, PlayerStatistics statistics, HeadshotProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        // Check streakiness metric
        if (profile.streakiness > 0.7f && profile.averageHeadshotRate > 0.4f) {
            float severity = profile.streakiness * 0.8f;
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                String.format("Unnatural headshot patterns detected (streakiness: %.2f)", profile.streakiness),
                severity,
                "HeadshotAnalyzer"
            );
            
            violation.addEvidence(String.format("Streakiness: %.3f, Average rate: %.3f", 
                                               profile.streakiness, profile.averageHeadshotRate));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check headshot rates at different distances
     */
    private List<StatisticalViolation> checkDistanceBasedHeadshots(String playerId, PlayerStatistics statistics) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        float headshotRate = statistics.getHeadshotPercentage();
        float averageKillDistance = statistics.getAverageKillDistance();
        
        // High headshot rate at long distances is very suspicious
        if (headshotRate > 0.5f && averageKillDistance > 50.0f) {
            float distanceFactor = Math.min(1.0f, averageKillDistance / 100.0f);
            float severity = (headshotRate - 0.3f) / 0.5f * distanceFactor;
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                String.format("High headshot rate %.1f%% at long distances (avg: %.1fm)", 
                             headshotRate * 100, averageKillDistance),
                severity,
                "HeadshotAnalyzer"
            );
            
            violation.addEvidence(String.format("Headshot rate: %.3f, Avg distance: %.1f", 
                                               headshotRate, averageKillDistance));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Get headshot statistics for a player
     */
    public HeadshotProfile getPlayerProfile(String playerId) {
        return playerProfiles.get(playerId);
    }
    
    /**
     * Calculate expected headshot rate based on player skill and conditions
     */
    public float calculateExpectedHeadshotRate(String playerId, float playerSkill, float averageDistance) {
        // Base expected rate for average player
        float baseRate = 0.25f;
        
        // Adjust for skill level
        float skillMultiplier = 1.0f + (playerSkill - 0.5f) * 0.6f;
        
        // Adjust for distance (closer = higher headshot rate)
        float distanceMultiplier = Math.max(0.3f, 1.0f - (averageDistance / 100.0f) * 0.4f);
        
        return baseRate * skillMultiplier * distanceMultiplier;
    }
    
    /**
     * Clear old data to prevent memory leaks
     */
    public void cleanup() {
        long cutoffTime = System.currentTimeMillis() - (ANALYSIS_WINDOW_MS * 2);
        
        playerProfiles.entrySet().removeIf(entry -> 
            entry.getValue().lastUpdate < cutoffTime);
        
        playerHeadshotHistory.entrySet().removeIf(entry -> {
            List<Float> history = entry.getValue();
            return history.isEmpty() || history.size() < MIN_SAMPLES_FOR_ANALYSIS / 2;
        });
    }
}