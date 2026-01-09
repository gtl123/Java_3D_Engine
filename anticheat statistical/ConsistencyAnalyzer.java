package fps.anticheat.statistical;

import fps.anticheat.PlayerStatistics;
import fps.anticheat.ViolationType;
import fps.anticheat.AntiCheatConfiguration;

import java.util.*;

/**
 * Analyzes player performance consistency to detect unnatural patterns that indicate cheating.
 * Legitimate players show natural variance in performance, while cheats often produce unnaturally consistent results.
 */
public class ConsistencyAnalyzer {
    
    private final AntiCheatConfiguration config;
    private final Map<String, List<ConsistencyDataPoint>> playerConsistencyHistory;
    private final Map<String, ConsistencyProfile> playerProfiles;
    
    // Thresholds for consistency analysis
    private static final float UNNATURAL_CONSISTENCY_THRESHOLD = 0.02f; // Very low variance
    private static final float SUSPICIOUS_CONSISTENCY_THRESHOLD = 0.05f;
    private static final int MIN_SAMPLES_FOR_ANALYSIS = 25;
    private static final long ANALYSIS_WINDOW_MS = 1200000; // 20 minutes
    
    // Natural variance ranges for different metrics
    private static final float NATURAL_ACCURACY_VARIANCE = 0.08f;
    private static final float NATURAL_REACTION_VARIANCE = 0.15f;
    private static final float NATURAL_KDR_VARIANCE = 1.5f;
    
    /**
     * Data point for consistency analysis
     */
    private static class ConsistencyDataPoint {
        final float accuracy;
        final float reactionTime;
        final float kdr;
        final float headshotRate;
        final float aimPrecision;
        final long timestamp;
        
        ConsistencyDataPoint(PlayerStatistics stats) {
            this.accuracy = stats.getAverageAccuracy();
            this.reactionTime = stats.getAverageReactionTime();
            this.kdr = stats.getKillDeathRatio();
            this.headshotRate = stats.getHeadshotPercentage();
            this.aimPrecision = calculateAimPrecision(stats);
            this.timestamp = System.currentTimeMillis();
        }
        
        private float calculateAimPrecision(PlayerStatistics stats) {
            // Combine accuracy and headshot rate for aim precision metric
            return (stats.getAverageAccuracy() * 0.7f) + (stats.getHeadshotPercentage() * 0.3f);
        }
    }
    
    /**
     * Profile for tracking player consistency patterns
     */
    private static class ConsistencyProfile {
        // Variance metrics for different performance indicators
        float accuracyVariance;
        float reactionTimeVariance;
        float kdrVariance;
        float headshotVariance;
        float aimPrecisionVariance;
        
        // Consistency scores (lower = more consistent)
        float overallConsistency;
        float suspiciousConsistencyScore;
        
        // Pattern detection
        float periodicityScore; // Detects robotic patterns
        float entropyScore; // Measures randomness in performance
        
        int sampleCount;
        long lastUpdate;
        boolean flagged;
        
        ConsistencyProfile() {
            this.accuracyVariance = 0.0f;
            this.reactionTimeVariance = 0.0f;
            this.kdrVariance = 0.0f;
            this.headshotVariance = 0.0f;
            this.aimPrecisionVariance = 0.0f;
            this.overallConsistency = 0.0f;
            this.suspiciousConsistencyScore = 0.0f;
            this.periodicityScore = 0.0f;
            this.entropyScore = 1.0f;
            this.sampleCount = 0;
            this.lastUpdate = System.currentTimeMillis();
            this.flagged = false;
        }
    }
    
    public ConsistencyAnalyzer(AntiCheatConfiguration config) {
        this.config = config;
        this.playerConsistencyHistory = new HashMap<>();
        this.playerProfiles = new HashMap<>();
    }
    
    /**
     * Analyze player consistency for anomalies
     */
    public List<StatisticalViolation> analyzeConsistency(String playerId, PlayerStatistics statistics) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        // Create consistency data point
        ConsistencyDataPoint dataPoint = new ConsistencyDataPoint(statistics);
        
        // Update player consistency history
        updateConsistencyHistory(playerId, dataPoint);
        
        // Get or create player profile
        ConsistencyProfile profile = playerProfiles.computeIfAbsent(playerId, k -> new ConsistencyProfile());
        updateProfile(profile, playerId);
        
        // Perform consistency analyses
        violations.addAll(checkUnnaturalConsistency(playerId, profile));
        violations.addAll(checkRoboticPatterns(playerId, profile));
        violations.addAll(checkLowEntropy(playerId, profile));
        violations.addAll(checkMetricCorrelations(playerId, profile));
        
        return violations;
    }
    
    /**
     * Update consistency history for a player
     */
    private void updateConsistencyHistory(String playerId, ConsistencyDataPoint dataPoint) {
        List<ConsistencyDataPoint> history = playerConsistencyHistory.computeIfAbsent(playerId, k -> new ArrayList<>());
        
        // Add new data point
        history.add(dataPoint);
        
        // Remove old entries outside analysis window
        long cutoffTime = System.currentTimeMillis() - ANALYSIS_WINDOW_MS;
        history.removeIf(dp -> dp.timestamp < cutoffTime);
        
        // Limit history size
        while (history.size() > MIN_SAMPLES_FOR_ANALYSIS * 2) {
            history.remove(0);
        }
    }
    
    /**
     * Update player consistency profile
     */
    private void updateProfile(ConsistencyProfile profile, String playerId) {
        List<ConsistencyDataPoint> history = playerConsistencyHistory.get(playerId);
        if (history == null || history.size() < MIN_SAMPLES_FOR_ANALYSIS) {
            return;
        }
        
        profile.sampleCount = history.size();
        
        // Calculate variances for each metric
        profile.accuracyVariance = calculateVariance(history, dp -> dp.accuracy);
        profile.reactionTimeVariance = calculateVariance(history, dp -> dp.reactionTime);
        profile.kdrVariance = calculateVariance(history, dp -> dp.kdr);
        profile.headshotVariance = calculateVariance(history, dp -> dp.headshotRate);
        profile.aimPrecisionVariance = calculateVariance(history, dp -> dp.aimPrecision);
        
        // Calculate overall consistency score
        updateOverallConsistency(profile);
        
        // Calculate suspicious consistency score
        updateSuspiciousConsistencyScore(profile);
        
        // Calculate periodicity and entropy scores
        updatePatternScores(profile, history);
        
        profile.lastUpdate = System.currentTimeMillis();
    }
    
    /**
     * Calculate variance for a specific metric
     */
    private float calculateVariance(List<ConsistencyDataPoint> history, java.util.function.Function<ConsistencyDataPoint, Float> extractor) {
        if (history.size() < 2) return 0.0f;
        
        // Calculate mean
        float sum = 0.0f;
        for (ConsistencyDataPoint dp : history) {
            sum += extractor.apply(dp);
        }
        float mean = sum / history.size();
        
        // Calculate variance
        float varianceSum = 0.0f;
        for (ConsistencyDataPoint dp : history) {
            float diff = extractor.apply(dp) - mean;
            varianceSum += diff * diff;
        }
        
        return varianceSum / (history.size() - 1);
    }
    
    /**
     * Update overall consistency score
     */
    private void updateOverallConsistency(ConsistencyProfile profile) {
        // Normalize variances and combine them
        float normalizedAccuracyVar = profile.accuracyVariance / NATURAL_ACCURACY_VARIANCE;
        float normalizedReactionVar = profile.reactionTimeVariance / NATURAL_REACTION_VARIANCE;
        float normalizedKdrVar = profile.kdrVariance / NATURAL_KDR_VARIANCE;
        
        // Lower values indicate higher consistency
        profile.overallConsistency = (normalizedAccuracyVar + normalizedReactionVar + normalizedKdrVar) / 3.0f;
    }
    
    /**
     * Update suspicious consistency score
     */
    private void updateSuspiciousConsistencyScore(ConsistencyProfile profile) {
        float score = 0.0f;
        
        // Check each variance against suspicious thresholds
        if (profile.accuracyVariance < UNNATURAL_CONSISTENCY_THRESHOLD) {
            score += 0.3f;
        } else if (profile.accuracyVariance < SUSPICIOUS_CONSISTENCY_THRESHOLD) {
            score += 0.15f;
        }
        
        if (profile.reactionTimeVariance < UNNATURAL_CONSISTENCY_THRESHOLD * 2) {
            score += 0.25f;
        } else if (profile.reactionTimeVariance < SUSPICIOUS_CONSISTENCY_THRESHOLD * 2) {
            score += 0.1f;
        }
        
        if (profile.headshotVariance < UNNATURAL_CONSISTENCY_THRESHOLD) {
            score += 0.25f;
        } else if (profile.headshotVariance < SUSPICIOUS_CONSISTENCY_THRESHOLD) {
            score += 0.1f;
        }
        
        if (profile.aimPrecisionVariance < UNNATURAL_CONSISTENCY_THRESHOLD) {
            score += 0.2f;
        }
        
        profile.suspiciousConsistencyScore = Math.min(1.0f, score);
    }
    
    /**
     * Update pattern detection scores
     */
    private void updatePatternScores(ConsistencyProfile profile, List<ConsistencyDataPoint> history) {
        // Calculate periodicity score (detects robotic patterns)
        profile.periodicityScore = calculatePeriodicity(history);
        
        // Calculate entropy score (measures randomness)
        profile.entropyScore = calculateEntropy(history);
    }
    
    /**
     * Calculate periodicity in performance data
     */
    private float calculatePeriodicity(List<ConsistencyDataPoint> history) {
        if (history.size() < 10) return 0.0f;
        
        // Simple periodicity detection using autocorrelation
        float maxCorrelation = 0.0f;
        
        for (int lag = 2; lag <= Math.min(10, history.size() / 3); lag++) {
            float correlation = calculateAutocorrelation(history, lag, dp -> dp.accuracy);
            maxCorrelation = Math.max(maxCorrelation, Math.abs(correlation));
        }
        
        return maxCorrelation;
    }
    
    /**
     * Calculate autocorrelation for a given lag
     */
    private float calculateAutocorrelation(List<ConsistencyDataPoint> history, int lag, 
                                         java.util.function.Function<ConsistencyDataPoint, Float> extractor) {
        if (history.size() <= lag) return 0.0f;
        
        // Calculate means
        float sum1 = 0.0f, sum2 = 0.0f;
        int count = history.size() - lag;
        
        for (int i = 0; i < count; i++) {
            sum1 += extractor.apply(history.get(i));
            sum2 += extractor.apply(history.get(i + lag));
        }
        
        float mean1 = sum1 / count;
        float mean2 = sum2 / count;
        
        // Calculate correlation
        float numerator = 0.0f;
        float denom1 = 0.0f, denom2 = 0.0f;
        
        for (int i = 0; i < count; i++) {
            float diff1 = extractor.apply(history.get(i)) - mean1;
            float diff2 = extractor.apply(history.get(i + lag)) - mean2;
            
            numerator += diff1 * diff2;
            denom1 += diff1 * diff1;
            denom2 += diff2 * diff2;
        }
        
        float denominator = (float) Math.sqrt(denom1 * denom2);
        return denominator > 0 ? numerator / denominator : 0.0f;
    }
    
    /**
     * Calculate entropy (randomness) in performance data
     */
    private float calculateEntropy(List<ConsistencyDataPoint> history) {
        if (history.size() < 10) return 1.0f;
        
        // Discretize accuracy values and calculate entropy
        Map<Integer, Integer> buckets = new HashMap<>();
        
        for (ConsistencyDataPoint dp : history) {
            int bucket = (int) (dp.accuracy * 20); // 20 buckets for accuracy
            buckets.put(bucket, buckets.getOrDefault(bucket, 0) + 1);
        }
        
        // Calculate entropy
        float entropy = 0.0f;
        int total = history.size();
        
        for (int count : buckets.values()) {
            if (count > 0) {
                float probability = (float) count / total;
                entropy -= probability * Math.log(probability) / Math.log(2);
            }
        }
        
        // Normalize entropy (max entropy for 20 buckets is log2(20) ≈ 4.32)
        return entropy / 4.32f;
    }
    
    /**
     * Check for unnaturally consistent performance
     */
    private List<StatisticalViolation> checkUnnaturalConsistency(String playerId, ConsistencyProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        if (profile.sampleCount < MIN_SAMPLES_FOR_ANALYSIS) {
            return violations;
        }
        
        if (profile.suspiciousConsistencyScore >= 0.7f) {
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.PERFORMANCE_INCONSISTENCY,
                String.format("Unnaturally consistent performance detected (score: %.2f)", profile.suspiciousConsistencyScore),
                profile.suspiciousConsistencyScore,
                "ConsistencyAnalyzer"
            );
            
            violation.addEvidence(String.format("Consistency score: %.3f, Accuracy variance: %.4f, Reaction variance: %.4f", 
                                               profile.suspiciousConsistencyScore, profile.accuracyVariance, profile.reactionTimeVariance));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check for robotic patterns in performance
     */
    private List<StatisticalViolation> checkRoboticPatterns(String playerId, ConsistencyProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        if (profile.sampleCount < MIN_SAMPLES_FOR_ANALYSIS) {
            return violations;
        }
        
        if (profile.periodicityScore > 0.8f) {
            float severity = profile.periodicityScore * 0.9f;
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                String.format("Robotic performance patterns detected (periodicity: %.2f)", profile.periodicityScore),
                severity,
                "ConsistencyAnalyzer"
            );
            
            violation.addEvidence(String.format("Periodicity score: %.3f, Consistency: %.3f", 
                                               profile.periodicityScore, profile.overallConsistency));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check for low entropy (lack of randomness) in performance
     */
    private List<StatisticalViolation> checkLowEntropy(String playerId, ConsistencyProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        if (profile.sampleCount < MIN_SAMPLES_FOR_ANALYSIS) {
            return violations;
        }
        
        if (profile.entropyScore < 0.3f) {
            float severity = (0.5f - profile.entropyScore) / 0.5f * 0.8f;
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                String.format("Low performance entropy detected (%.2f)", profile.entropyScore),
                severity,
                "ConsistencyAnalyzer"
            );
            
            violation.addEvidence(String.format("Entropy score: %.3f, Expected: >0.5", profile.entropyScore));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check correlations between different consistency metrics
     */
    private List<StatisticalViolation> checkMetricCorrelations(String playerId, ConsistencyProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        if (profile.sampleCount < MIN_SAMPLES_FOR_ANALYSIS) {
            return violations;
        }
        
        // If multiple metrics are all unnaturally consistent, it's very suspicious
        int suspiciousMetrics = 0;
        
        if (profile.accuracyVariance < UNNATURAL_CONSISTENCY_THRESHOLD) suspiciousMetrics++;
        if (profile.reactionTimeVariance < UNNATURAL_CONSISTENCY_THRESHOLD * 2) suspiciousMetrics++;
        if (profile.headshotVariance < UNNATURAL_CONSISTENCY_THRESHOLD) suspiciousMetrics++;
        if (profile.aimPrecisionVariance < UNNATURAL_CONSISTENCY_THRESHOLD) suspiciousMetrics++;
        
        if (suspiciousMetrics >= 3) {
            float severity = suspiciousMetrics / 4.0f;
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.STATISTICAL_ANOMALY,
                String.format("Multiple metrics show unnatural consistency (%d/4)", suspiciousMetrics),
                severity,
                "ConsistencyAnalyzer"
            );
            
            violation.addEvidence(String.format("Suspicious metrics: %d, Overall consistency: %.3f", 
                                               suspiciousMetrics, profile.overallConsistency));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Get consistency profile for a player
     */
    public ConsistencyProfile getPlayerProfile(String playerId) {
        return playerProfiles.get(playerId);
    }
    
    /**
     * Clear old data to prevent memory leaks
     */
    public void cleanup() {
        long cutoffTime = System.currentTimeMillis() - (ANALYSIS_WINDOW_MS * 2);
        
        playerProfiles.entrySet().removeIf(entry -> 
            entry.getValue().lastUpdate < cutoffTime);
        
        playerConsistencyHistory.entrySet().removeIf(entry -> {
            List<ConsistencyDataPoint> history = entry.getValue();
            return history.isEmpty() || history.size() < MIN_SAMPLES_FOR_ANALYSIS / 2;
        });
    }
}