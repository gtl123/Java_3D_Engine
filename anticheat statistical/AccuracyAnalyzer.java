package fps.anticheat.statistical;

import fps.anticheat.PlayerStatistics;
import fps.anticheat.ViolationType;
import fps.anticheat.AntiCheatConfiguration;

import java.util.*;

/**
 * Analyzes player accuracy statistics to detect impossible or suspicious accuracy patterns.
 * Detects aimbots and aim assistance through statistical analysis of shooting accuracy.
 */
public class AccuracyAnalyzer {
    
    private final AntiCheatConfiguration config;
    private final Map<String, List<Float>> playerAccuracyHistory;
    private final Map<String, AccuracyProfile> playerProfiles;
    
    // Thresholds for accuracy analysis
    private static final float IMPOSSIBLE_ACCURACY_THRESHOLD = 0.95f;
    private static final float SUSPICIOUS_ACCURACY_THRESHOLD = 0.85f;
    private static final float CONSISTENCY_THRESHOLD = 0.02f; // Very low variance indicates aimbot
    private static final int MIN_SAMPLES_FOR_ANALYSIS = 10;
    private static final long ANALYSIS_WINDOW_MS = 300000; // 5 minutes
    
    /**
     * Profile for tracking player accuracy patterns
     */
    private static class AccuracyProfile {
        float averageAccuracy;
        float variance;
        float trend;
        int sampleCount;
        long lastUpdate;
        boolean flagged;
        
        AccuracyProfile() {
            this.averageAccuracy = 0.0f;
            this.variance = 0.0f;
            this.trend = 0.0f;
            this.sampleCount = 0;
            this.lastUpdate = System.currentTimeMillis();
            this.flagged = false;
        }
    }
    
    public AccuracyAnalyzer(AntiCheatConfiguration config) {
        this.config = config;
        this.playerAccuracyHistory = new HashMap<>();
        this.playerProfiles = new HashMap<>();
    }
    
    /**
     * Analyze player accuracy statistics for anomalies
     */
    public List<StatisticalViolation> analyzeAccuracy(String playerId, PlayerStatistics statistics) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        // Update player accuracy history
        updateAccuracyHistory(playerId, statistics.getAverageAccuracy());
        
        // Get or create player profile
        AccuracyProfile profile = playerProfiles.computeIfAbsent(playerId, k -> new AccuracyProfile());
        updateProfile(profile, statistics.getAverageAccuracy());
        
        // Perform various accuracy analyses
        violations.addAll(checkImpossibleAccuracy(playerId, statistics));
        violations.addAll(checkSuspiciousConsistency(playerId, profile));
        violations.addAll(checkSuddenImprovement(playerId, profile));
        violations.addAll(checkWeaponSpecificAccuracy(playerId, statistics));
        
        return violations;
    }
    
    /**
     * Update accuracy history for a player
     */
    private void updateAccuracyHistory(String playerId, float accuracy) {
        List<Float> history = playerAccuracyHistory.computeIfAbsent(playerId, k -> new ArrayList<>());
        
        // Add new accuracy value
        history.add(accuracy);
        
        // Remove old entries outside analysis window
        long cutoffTime = System.currentTimeMillis() - ANALYSIS_WINDOW_MS;
        while (history.size() > MIN_SAMPLES_FOR_ANALYSIS * 2) {
            history.remove(0);
        }
    }
    
    /**
     * Update player accuracy profile
     */
    private void updateProfile(AccuracyProfile profile, float newAccuracy) {
        profile.sampleCount++;
        
        // Update average using exponential moving average
        float alpha = 0.1f;
        if (profile.sampleCount == 1) {
            profile.averageAccuracy = newAccuracy;
        } else {
            float oldAverage = profile.averageAccuracy;
            profile.averageAccuracy = (alpha * newAccuracy) + ((1 - alpha) * oldAverage);
            
            // Update variance
            float diff = newAccuracy - profile.averageAccuracy;
            profile.variance = (alpha * diff * diff) + ((1 - alpha) * profile.variance);
            
            // Update trend
            profile.trend = newAccuracy - oldAverage;
        }
        
        profile.lastUpdate = System.currentTimeMillis();
    }
    
    /**
     * Check for impossible accuracy levels
     */
    private List<StatisticalViolation> checkImpossibleAccuracy(String playerId, PlayerStatistics statistics) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        float accuracy = statistics.getAverageAccuracy();
        
        // Check for impossible accuracy
        if (accuracy >= IMPOSSIBLE_ACCURACY_THRESHOLD) {
            float severity = Math.min(1.0f, (accuracy - IMPOSSIBLE_ACCURACY_THRESHOLD) / (1.0f - IMPOSSIBLE_ACCURACY_THRESHOLD));
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.IMPOSSIBLE_ACCURACY,
                String.format("Player accuracy %.1f%% exceeds human limits", accuracy * 100),
                severity,
                "AccuracyAnalyzer"
            );
            
            violation.addEvidence(String.format("Accuracy: %.3f, Threshold: %.3f", accuracy, IMPOSSIBLE_ACCURACY_THRESHOLD));
            violations.add(violation);
        }
        
        // Check for suspicious accuracy
        else if (accuracy >= SUSPICIOUS_ACCURACY_THRESHOLD) {
            float severity = (accuracy - SUSPICIOUS_ACCURACY_THRESHOLD) / (IMPOSSIBLE_ACCURACY_THRESHOLD - SUSPICIOUS_ACCURACY_THRESHOLD) * 0.7f;
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                String.format("Player accuracy %.1f%% is suspiciously high", accuracy * 100),
                severity,
                "AccuracyAnalyzer"
            );
            
            violation.addEvidence(String.format("Accuracy: %.3f, Suspicious threshold: %.3f", accuracy, SUSPICIOUS_ACCURACY_THRESHOLD));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check for suspicious consistency (too little variance indicates aimbot)
     */
    private List<StatisticalViolation> checkSuspiciousConsistency(String playerId, AccuracyProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        if (profile.sampleCount < MIN_SAMPLES_FOR_ANALYSIS) {
            return violations;
        }
        
        // Check if variance is suspiciously low
        if (profile.variance < CONSISTENCY_THRESHOLD && profile.averageAccuracy > 0.6f) {
            float severity = Math.min(1.0f, (CONSISTENCY_THRESHOLD - profile.variance) / CONSISTENCY_THRESHOLD);
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                String.format("Player accuracy is unnaturally consistent (variance: %.4f)", profile.variance),
                severity,
                "AccuracyAnalyzer"
            );
            
            violation.addEvidence(String.format("Variance: %.4f, Threshold: %.4f, Average: %.3f", 
                                               profile.variance, CONSISTENCY_THRESHOLD, profile.averageAccuracy));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check for sudden dramatic improvement in accuracy
     */
    private List<StatisticalViolation> checkSuddenImprovement(String playerId, AccuracyProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        if (profile.sampleCount < MIN_SAMPLES_FOR_ANALYSIS) {
            return violations;
        }
        
        // Check for sudden improvement
        if (profile.trend > 0.3f && profile.averageAccuracy > 0.7f) {
            float severity = Math.min(1.0f, profile.trend / 0.5f);
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                String.format("Sudden accuracy improvement detected (trend: +%.1f%%)", profile.trend * 100),
                severity,
                "AccuracyAnalyzer"
            );
            
            violation.addEvidence(String.format("Trend: %.3f, Current accuracy: %.3f", profile.trend, profile.averageAccuracy));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check weapon-specific accuracy patterns
     */
    private List<StatisticalViolation> checkWeaponSpecificAccuracy(String playerId, PlayerStatistics statistics) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        // This would analyze per-weapon accuracy if that data is available
        // For now, we'll do a basic check for unrealistic accuracy with different weapon types
        
        float accuracy = statistics.getAverageAccuracy();
        
        // If player has high accuracy but also high damage per shot, might indicate aimbot
        if (accuracy > 0.8f && statistics.getAverageDamagePerShot() > 80.0f) {
            float severity = (accuracy - 0.8f) / 0.2f * 0.6f;
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                "High accuracy combined with high damage suggests possible aimbot",
                severity,
                "AccuracyAnalyzer"
            );
            
            violation.addEvidence(String.format("Accuracy: %.3f, Avg damage/shot: %.1f", 
                                               accuracy, statistics.getAverageDamagePerShot()));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Get accuracy statistics for a player
     */
    public AccuracyProfile getPlayerProfile(String playerId) {
        return playerProfiles.get(playerId);
    }
    
    /**
     * Clear old data to prevent memory leaks
     */
    public void cleanup() {
        long cutoffTime = System.currentTimeMillis() - (ANALYSIS_WINDOW_MS * 2);
        
        playerProfiles.entrySet().removeIf(entry -> 
            entry.getValue().lastUpdate < cutoffTime);
        
        playerAccuracyHistory.entrySet().removeIf(entry -> {
            List<Float> history = entry.getValue();
            return history.isEmpty() || history.size() < MIN_SAMPLES_FOR_ANALYSIS / 2;
        });
    }
}