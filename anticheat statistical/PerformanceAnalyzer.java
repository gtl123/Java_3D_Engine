package fps.anticheat.statistical;

import fps.anticheat.PlayerStatistics;
import fps.anticheat.ViolationType;
import fps.anticheat.AntiCheatConfiguration;

import java.util.*;

/**
 * Analyzes overall player performance metrics to detect impossible or suspicious performance patterns.
 * Detects various cheats through statistical analysis of KDR, damage output, and other performance indicators.
 */
public class PerformanceAnalyzer {
    
    private final AntiCheatConfiguration config;
    private final Map<String, List<PerformanceSnapshot>> playerPerformanceHistory;
    private final Map<String, PerformanceProfile> playerProfiles;
    
    // Thresholds for performance analysis
    private static final float IMPOSSIBLE_KDR_THRESHOLD = 20.0f;
    private static final float SUSPICIOUS_KDR_THRESHOLD = 10.0f;
    private static final float IMPOSSIBLE_DPS_THRESHOLD = 500.0f; // Damage per second
    private static final float SUSPICIOUS_DPS_THRESHOLD = 300.0f;
    private static final int MIN_SAMPLES_FOR_ANALYSIS = 20;
    private static final long ANALYSIS_WINDOW_MS = 900000; // 15 minutes
    
    // Professional benchmarks
    private static final float PRO_AVERAGE_KDR = 2.5f;
    private static final float PRO_MAX_KDR = 8.0f;
    private static final float PRO_AVERAGE_DPS = 150.0f;
    
    /**
     * Snapshot of player performance at a specific time
     */
    private static class PerformanceSnapshot {
        final float kdr;
        final float dps;
        final float accuracy;
        final float headshotRate;
        final long timestamp;
        
        PerformanceSnapshot(PlayerStatistics stats) {
            this.kdr = stats.getKillDeathRatio();
            this.dps = calculateDPS(stats);
            this.accuracy = stats.getAverageAccuracy();
            this.headshotRate = stats.getHeadshotPercentage();
            this.timestamp = System.currentTimeMillis();
        }
        
        private float calculateDPS(PlayerStatistics stats) {
            // Estimate DPS based on damage per shot and fire rate
            float damagePerShot = stats.getAverageDamagePerShot();
            float shotsPerSecond = stats.getShotsPerSecond();
            return damagePerShot * shotsPerSecond * stats.getAverageAccuracy();
        }
    }
    
    /**
     * Profile for tracking player performance patterns
     */
    private static class PerformanceProfile {
        float averageKDR;
        float averageDPS;
        float kdrVariance;
        float dpsVariance;
        float performanceConsistency;
        float improvementRate;
        int sampleCount;
        long lastUpdate;
        boolean flagged;
        
        // Performance correlation metrics
        float accuracyKDRCorrelation;
        float headshotKDRCorrelation;
        
        PerformanceProfile() {
            this.averageKDR = 0.0f;
            this.averageDPS = 0.0f;
            this.kdrVariance = 0.0f;
            this.dpsVariance = 0.0f;
            this.performanceConsistency = 0.0f;
            this.improvementRate = 0.0f;
            this.sampleCount = 0;
            this.lastUpdate = System.currentTimeMillis();
            this.flagged = false;
            this.accuracyKDRCorrelation = 0.0f;
            this.headshotKDRCorrelation = 0.0f;
        }
    }
    
    public PerformanceAnalyzer(AntiCheatConfiguration config) {
        this.config = config;
        this.playerPerformanceHistory = new HashMap<>();
        this.playerProfiles = new HashMap<>();
    }
    
    /**
     * Analyze player performance statistics for anomalies
     */
    public List<StatisticalViolation> analyzePerformance(String playerId, PlayerStatistics statistics) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        // Create performance snapshot
        PerformanceSnapshot snapshot = new PerformanceSnapshot(statistics);
        
        // Update player performance history
        updatePerformanceHistory(playerId, snapshot);
        
        // Get or create player profile
        PerformanceProfile profile = playerProfiles.computeIfAbsent(playerId, k -> new PerformanceProfile());
        updateProfile(profile, snapshot);
        
        // Perform various performance analyses
        violations.addAll(checkImpossibleKDR(playerId, statistics));
        violations.addAll(checkImpossibleDPS(playerId, statistics));
        violations.addAll(checkSuspiciousConsistency(playerId, profile));
        violations.addAll(checkRapidImprovement(playerId, profile));
        violations.addAll(checkPerformanceCorrelations(playerId, profile));
        violations.addAll(checkOutlierPerformance(playerId, statistics, profile));
        
        return violations;
    }
    
    /**
     * Update performance history for a player
     */
    private void updatePerformanceHistory(String playerId, PerformanceSnapshot snapshot) {
        List<PerformanceSnapshot> history = playerPerformanceHistory.computeIfAbsent(playerId, k -> new ArrayList<>());
        
        // Add new snapshot
        history.add(snapshot);
        
        // Remove old entries outside analysis window
        long cutoffTime = System.currentTimeMillis() - ANALYSIS_WINDOW_MS;
        history.removeIf(s -> s.timestamp < cutoffTime);
        
        // Limit history size
        while (history.size() > MIN_SAMPLES_FOR_ANALYSIS * 2) {
            history.remove(0);
        }
    }
    
    /**
     * Update player performance profile
     */
    private void updateProfile(PerformanceProfile profile, PerformanceSnapshot snapshot) {
        profile.sampleCount++;
        
        // Update averages using exponential moving average
        float alpha = 0.1f;
        if (profile.sampleCount == 1) {
            profile.averageKDR = snapshot.kdr;
            profile.averageDPS = snapshot.dps;
        } else {
            float oldKDR = profile.averageKDR;
            float oldDPS = profile.averageDPS;
            
            profile.averageKDR = (alpha * snapshot.kdr) + ((1 - alpha) * oldKDR);
            profile.averageDPS = (alpha * snapshot.dps) + ((1 - alpha) * oldDPS);
            
            // Update variances
            float kdrDiff = snapshot.kdr - profile.averageKDR;
            float dpsDiff = snapshot.dps - profile.averageDPS;
            
            profile.kdrVariance = (alpha * kdrDiff * kdrDiff) + ((1 - alpha) * profile.kdrVariance);
            profile.dpsVariance = (alpha * dpsDiff * dpsDiff) + ((1 - alpha) * profile.dpsVariance);
            
            // Update improvement rate
            profile.improvementRate = (snapshot.kdr - oldKDR) + (snapshot.dps - oldDPS) * 0.01f;
        }
        
        // Update consistency metric
        updateConsistency(profile);
        
        // Update correlations
        updateCorrelations(profile, snapshot);
        
        profile.lastUpdate = System.currentTimeMillis();
    }
    
    /**
     * Update performance consistency metric
     */
    private void updateConsistency(PerformanceProfile profile) {
        // Lower variance indicates higher consistency
        float kdrConsistency = 1.0f / (1.0f + profile.kdrVariance);
        float dpsConsistency = 1.0f / (1.0f + profile.dpsVariance * 0.01f);
        
        profile.performanceConsistency = (kdrConsistency + dpsConsistency) / 2.0f;
    }
    
    /**
     * Update correlation metrics between different performance indicators
     */
    private void updateCorrelations(PerformanceProfile profile, PerformanceSnapshot snapshot) {
        // Simple correlation tracking - in a real implementation, this would be more sophisticated
        if (profile.sampleCount > 5) {
            // High accuracy should correlate with high KDR for legitimate players
            float expectedKDR = snapshot.accuracy * PRO_MAX_KDR;
            float accuracyCorrelation = 1.0f - Math.abs(snapshot.kdr - expectedKDR) / PRO_MAX_KDR;
            
            profile.accuracyKDRCorrelation = (profile.accuracyKDRCorrelation * 0.9f) + (accuracyCorrelation * 0.1f);
            
            // Similar for headshot rate
            float expectedHeadshotKDR = snapshot.headshotRate * PRO_MAX_KDR * 1.5f;
            float headshotCorrelation = 1.0f - Math.abs(snapshot.kdr - expectedHeadshotKDR) / PRO_MAX_KDR;
            
            profile.headshotKDRCorrelation = (profile.headshotKDRCorrelation * 0.9f) + (headshotCorrelation * 0.1f);
        }
    }
    
    /**
     * Check for impossible KDR values
     */
    private List<StatisticalViolation> checkImpossibleKDR(String playerId, PlayerStatistics statistics) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        float kdr = statistics.getKillDeathRatio();
        
        // Check for impossible KDR
        if (kdr >= IMPOSSIBLE_KDR_THRESHOLD) {
            float severity = Math.min(1.0f, (kdr - IMPOSSIBLE_KDR_THRESHOLD) / IMPOSSIBLE_KDR_THRESHOLD);
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                String.format("Player KDR %.1f exceeds human limits", kdr),
                severity,
                "PerformanceAnalyzer"
            );
            
            violation.addEvidence(String.format("KDR: %.2f, Threshold: %.2f", kdr, IMPOSSIBLE_KDR_THRESHOLD));
            violations.add(violation);
        }
        
        // Check for suspicious KDR
        else if (kdr >= SUSPICIOUS_KDR_THRESHOLD) {
            float severity = (kdr - SUSPICIOUS_KDR_THRESHOLD) / (IMPOSSIBLE_KDR_THRESHOLD - SUSPICIOUS_KDR_THRESHOLD) * 0.7f;
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                String.format("Player KDR %.1f is suspiciously high", kdr),
                severity,
                "PerformanceAnalyzer"
            );
            
            violation.addEvidence(String.format("KDR: %.2f, Pro max: %.2f", kdr, PRO_MAX_KDR));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check for impossible damage per second values
     */
    private List<StatisticalViolation> checkImpossibleDPS(String playerId, PlayerStatistics statistics) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        float dps = calculateDPS(statistics);
        
        // Check for impossible DPS
        if (dps >= IMPOSSIBLE_DPS_THRESHOLD) {
            float severity = Math.min(1.0f, (dps - IMPOSSIBLE_DPS_THRESHOLD) / IMPOSSIBLE_DPS_THRESHOLD);
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                String.format("Player DPS %.1f exceeds weapon limits", dps),
                severity,
                "PerformanceAnalyzer"
            );
            
            violation.addEvidence(String.format("DPS: %.1f, Threshold: %.1f", dps, IMPOSSIBLE_DPS_THRESHOLD));
            violations.add(violation);
        }
        
        // Check for suspicious DPS
        else if (dps >= SUSPICIOUS_DPS_THRESHOLD) {
            float severity = (dps - SUSPICIOUS_DPS_THRESHOLD) / (IMPOSSIBLE_DPS_THRESHOLD - SUSPICIOUS_DPS_THRESHOLD) * 0.6f;
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                String.format("Player DPS %.1f is suspiciously high", dps),
                severity,
                "PerformanceAnalyzer"
            );
            
            violation.addEvidence(String.format("DPS: %.1f, Pro average: %.1f", dps, PRO_AVERAGE_DPS));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check for suspicious performance consistency
     */
    private List<StatisticalViolation> checkSuspiciousConsistency(String playerId, PerformanceProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        if (profile.sampleCount < MIN_SAMPLES_FOR_ANALYSIS) {
            return violations;
        }
        
        // Very high consistency with high performance indicates possible cheating
        if (profile.performanceConsistency > 0.9f && profile.averageKDR > SUSPICIOUS_KDR_THRESHOLD * 0.7f) {
            float severity = (profile.performanceConsistency - 0.8f) / 0.2f * 0.8f;
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.PERFORMANCE_INCONSISTENCY,
                String.format("Player performance is unnaturally consistent (%.1f%%)", profile.performanceConsistency * 100),
                severity,
                "PerformanceAnalyzer"
            );
            
            violation.addEvidence(String.format("Consistency: %.3f, KDR: %.2f, DPS: %.1f", 
                                               profile.performanceConsistency, profile.averageKDR, profile.averageDPS));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check for rapid performance improvement
     */
    private List<StatisticalViolation> checkRapidImprovement(String playerId, PerformanceProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        if (profile.sampleCount < MIN_SAMPLES_FOR_ANALYSIS) {
            return violations;
        }
        
        // Rapid improvement in performance
        if (profile.improvementRate > 5.0f && profile.averageKDR > PRO_AVERAGE_KDR) {
            float severity = Math.min(1.0f, profile.improvementRate / 10.0f);
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                String.format("Rapid performance improvement detected (rate: %.1f)", profile.improvementRate),
                severity,
                "PerformanceAnalyzer"
            );
            
            violation.addEvidence(String.format("Improvement rate: %.2f, Current KDR: %.2f", 
                                               profile.improvementRate, profile.averageKDR));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check performance correlations for anomalies
     */
    private List<StatisticalViolation> checkPerformanceCorrelations(String playerId, PerformanceProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        if (profile.sampleCount < MIN_SAMPLES_FOR_ANALYSIS) {
            return violations;
        }
        
        // Poor correlation between accuracy and KDR might indicate wallhacks or other cheats
        if (profile.accuracyKDRCorrelation < 0.3f && profile.averageKDR > PRO_AVERAGE_KDR) {
            float severity = (1.0f - profile.accuracyKDRCorrelation) * 0.6f;
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                String.format("Poor correlation between accuracy and performance (%.2f)", profile.accuracyKDRCorrelation),
                severity,
                "PerformanceAnalyzer"
            );
            
            violation.addEvidence(String.format("Accuracy-KDR correlation: %.3f, KDR: %.2f", 
                                               profile.accuracyKDRCorrelation, profile.averageKDR));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check for outlier performance compared to player base
     */
    private List<StatisticalViolation> checkOutlierPerformance(String playerId, PlayerStatistics statistics, PerformanceProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        // Check if player is a significant outlier in multiple metrics
        boolean kdrOutlier = statistics.getKillDeathRatio() > PRO_MAX_KDR;
        boolean accuracyOutlier = statistics.getAverageAccuracy() > 0.8f;
        boolean headshotOutlier = statistics.getHeadshotPercentage() > 0.6f;
        
        int outlierCount = (kdrOutlier ? 1 : 0) + (accuracyOutlier ? 1 : 0) + (headshotOutlier ? 1 : 0);
        
        if (outlierCount >= 2) {
            float severity = outlierCount / 3.0f * 0.8f;
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.OUTLIER_BEHAVIOR,
                String.format("Player is outlier in %d performance metrics", outlierCount),
                severity,
                "PerformanceAnalyzer"
            );
            
            violation.addEvidence(String.format("KDR: %.2f, Accuracy: %.3f, Headshot: %.3f", 
                                               statistics.getKillDeathRatio(), statistics.getAverageAccuracy(), 
                                               statistics.getHeadshotPercentage()));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Calculate damage per second from statistics
     */
    private float calculateDPS(PlayerStatistics statistics) {
        float damagePerShot = statistics.getAverageDamagePerShot();
        float shotsPerSecond = statistics.getShotsPerSecond();
        float accuracy = statistics.getAverageAccuracy();
        
        return damagePerShot * shotsPerSecond * accuracy;
    }
    
    /**
     * Get performance profile for a player
     */
    public PerformanceProfile getPlayerProfile(String playerId) {
        return playerProfiles.get(playerId);
    }
    
    /**
     * Clear old data to prevent memory leaks
     */
    public void cleanup() {
        long cutoffTime = System.currentTimeMillis() - (ANALYSIS_WINDOW_MS * 2);
        
        playerProfiles.entrySet().removeIf(entry -> 
            entry.getValue().lastUpdate < cutoffTime);
        
        playerPerformanceHistory.entrySet().removeIf(entry -> {
            List<PerformanceSnapshot> history = entry.getValue();
            return history.isEmpty() || history.size() < MIN_SAMPLES_FOR_ANALYSIS / 2;
        });
    }
}