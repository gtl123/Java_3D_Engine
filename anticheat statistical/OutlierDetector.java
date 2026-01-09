package fps.anticheat.statistical;

import fps.anticheat.PlayerStatistics;
import fps.anticheat.ViolationType;
import fps.anticheat.AntiCheatConfiguration;

import java.util.*;

/**
 * Detects statistical outliers in player performance using various statistical methods.
 * Identifies players whose performance significantly deviates from normal population distributions.
 */
public class OutlierDetector {
    
    private final AntiCheatConfiguration config;
    private final Map<String, List<OutlierDataPoint>> playerDataHistory;
    private final Map<String, OutlierProfile> playerProfiles;
    private final PopulationStatistics populationStats;
    
    // Outlier detection thresholds
    private static final float Z_SCORE_THRESHOLD = 3.0f; // 3 standard deviations
    private static final float EXTREME_Z_SCORE_THRESHOLD = 4.0f; // 4 standard deviations
    private static final float IQR_MULTIPLIER = 2.5f; // Interquartile range multiplier
    private static final int MIN_POPULATION_SIZE = 100;
    private static final int MIN_SAMPLES_FOR_ANALYSIS = 15;
    private static final long ANALYSIS_WINDOW_MS = 1800000; // 30 minutes
    
    /**
     * Data point for outlier analysis
     */
    private static class OutlierDataPoint {
        final float accuracy;
        final float kdr;
        final float headshotRate;
        final float reactionTime;
        final float damagePerSecond;
        final float killsPerMinute;
        final long timestamp;
        
        OutlierDataPoint(PlayerStatistics stats) {
            this.accuracy = stats.getAverageAccuracy();
            this.kdr = stats.getKillDeathRatio();
            this.headshotRate = stats.getHeadshotPercentage();
            this.reactionTime = stats.getAverageReactionTime();
            this.damagePerSecond = calculateDPS(stats);
            this.killsPerMinute = stats.getKillsPerMinute();
            this.timestamp = System.currentTimeMillis();
        }
        
        private float calculateDPS(PlayerStatistics stats) {
            return stats.getAverageDamagePerShot() * stats.getShotsPerSecond() * stats.getAverageAccuracy();
        }
    }
    
    /**
     * Profile for tracking player outlier patterns
     */
    private static class OutlierProfile {
        float[] zScores; // Z-scores for different metrics
        float[] outlierScores; // Outlier scores for different metrics
        float compositeOutlierScore;
        int outlierCount;
        int extremeOutlierCount;
        boolean isConsistentOutlier;
        long lastUpdate;
        
        OutlierProfile() {
            this.zScores = new float[6]; // 6 metrics
            this.outlierScores = new float[6];
            this.compositeOutlierScore = 0.0f;
            this.outlierCount = 0;
            this.extremeOutlierCount = 0;
            this.isConsistentOutlier = false;
            this.lastUpdate = System.currentTimeMillis();
        }
    }
    
    /**
     * Population statistics for comparison
     */
    private static class PopulationStatistics {
        final Map<String, Float> means;
        final Map<String, Float> standardDeviations;
        final Map<String, Float> medians;
        final Map<String, Float> q1Values; // First quartile
        final Map<String, Float> q3Values; // Third quartile
        int sampleSize;
        long lastUpdate;
        
        PopulationStatistics() {
            this.means = new HashMap<>();
            this.standardDeviations = new HashMap<>();
            this.medians = new HashMap<>();
            this.q1Values = new HashMap<>();
            this.q3Values = new HashMap<>();
            this.sampleSize = 0;
            this.lastUpdate = System.currentTimeMillis();
        }
    }
    
    public OutlierDetector(AntiCheatConfiguration config) {
        this.config = config;
        this.playerDataHistory = new HashMap<>();
        this.playerProfiles = new HashMap<>();
        this.populationStats = new PopulationStatistics();
        
        // Initialize with default population statistics
        initializeDefaultPopulationStats();
    }
    
    /**
     * Initialize with reasonable default population statistics
     */
    private void initializeDefaultPopulationStats() {
        // Default means
        populationStats.means.put("accuracy", 0.35f);
        populationStats.means.put("kdr", 1.2f);
        populationStats.means.put("headshotRate", 0.25f);
        populationStats.means.put("reactionTime", 0.25f);
        populationStats.means.put("damagePerSecond", 120.0f);
        populationStats.means.put("killsPerMinute", 1.5f);
        
        // Default standard deviations
        populationStats.standardDeviations.put("accuracy", 0.15f);
        populationStats.standardDeviations.put("kdr", 0.8f);
        populationStats.standardDeviations.put("headshotRate", 0.12f);
        populationStats.standardDeviations.put("reactionTime", 0.08f);
        populationStats.standardDeviations.put("damagePerSecond", 40.0f);
        populationStats.standardDeviations.put("killsPerMinute", 0.6f);
        
        populationStats.sampleSize = MIN_POPULATION_SIZE;
    }
    
    /**
     * Analyze player for statistical outliers
     */
    public List<StatisticalViolation> analyzeOutliers(String playerId, PlayerStatistics statistics) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        // Create outlier data point
        OutlierDataPoint dataPoint = new OutlierDataPoint(statistics);
        
        // Update player data history
        updateDataHistory(playerId, dataPoint);
        
        // Update population statistics
        updatePopulationStatistics(dataPoint);
        
        // Get or create player profile
        OutlierProfile profile = playerProfiles.computeIfAbsent(playerId, k -> new OutlierProfile());
        updateProfile(profile, dataPoint);
        
        // Perform outlier analyses
        violations.addAll(checkZScoreOutliers(playerId, profile));
        violations.addAll(checkIQROutliers(playerId, dataPoint));
        violations.addAll(checkConsistentOutliers(playerId, profile));
        violations.addAll(checkMultiMetricOutliers(playerId, profile));
        violations.addAll(checkExtremeOutliers(playerId, profile));
        
        return violations;
    }
    
    /**
     * Update data history for a player
     */
    private void updateDataHistory(String playerId, OutlierDataPoint dataPoint) {
        List<OutlierDataPoint> history = playerDataHistory.computeIfAbsent(playerId, k -> new ArrayList<>());
        
        // Add new data point
        history.add(dataPoint);
        
        // Remove old entries outside analysis window
        long cutoffTime = System.currentTimeMillis() - ANALYSIS_WINDOW_MS;
        history.removeIf(dp -> dp.timestamp < cutoffTime);
        
        // Limit history size
        while (history.size() > MIN_SAMPLES_FOR_ANALYSIS * 3) {
            history.remove(0);
        }
    }
    
    /**
     * Update population statistics with new data
     */
    private void updatePopulationStatistics(OutlierDataPoint dataPoint) {
        // Simple exponential moving average update
        float alpha = 0.01f; // Slow adaptation to prevent manipulation
        
        updatePopulationMean("accuracy", dataPoint.accuracy, alpha);
        updatePopulationMean("kdr", dataPoint.kdr, alpha);
        updatePopulationMean("headshotRate", dataPoint.headshotRate, alpha);
        updatePopulationMean("reactionTime", dataPoint.reactionTime, alpha);
        updatePopulationMean("damagePerSecond", dataPoint.damagePerSecond, alpha);
        updatePopulationMean("killsPerMinute", dataPoint.killsPerMinute, alpha);
        
        populationStats.sampleSize++;
        populationStats.lastUpdate = System.currentTimeMillis();
    }
    
    /**
     * Update population mean for a metric
     */
    private void updatePopulationMean(String metric, float value, float alpha) {
        float currentMean = populationStats.means.getOrDefault(metric, value);
        float newMean = (alpha * value) + ((1 - alpha) * currentMean);
        populationStats.means.put(metric, newMean);
        
        // Update standard deviation estimate
        float currentStdDev = populationStats.standardDeviations.getOrDefault(metric, Math.abs(value - newMean));
        float diff = Math.abs(value - newMean);
        float newStdDev = (alpha * diff) + ((1 - alpha) * currentStdDev);
        populationStats.standardDeviations.put(metric, newStdDev);
    }
    
    /**
     * Update player outlier profile
     */
    private void updateProfile(OutlierProfile profile, OutlierDataPoint dataPoint) {
        // Calculate Z-scores for each metric
        profile.zScores[0] = calculateZScore("accuracy", dataPoint.accuracy);
        profile.zScores[1] = calculateZScore("kdr", dataPoint.kdr);
        profile.zScores[2] = calculateZScore("headshotRate", dataPoint.headshotRate);
        profile.zScores[3] = calculateZScore("reactionTime", dataPoint.reactionTime);
        profile.zScores[4] = calculateZScore("damagePerSecond", dataPoint.damagePerSecond);
        profile.zScores[5] = calculateZScore("killsPerMinute", dataPoint.killsPerMinute);
        
        // Calculate outlier scores
        for (int i = 0; i < profile.zScores.length; i++) {
            profile.outlierScores[i] = Math.abs(profile.zScores[i]) / Z_SCORE_THRESHOLD;
        }
        
        // Calculate composite outlier score
        updateCompositeScore(profile);
        
        // Update outlier counts
        updateOutlierCounts(profile);
        
        // Check for consistent outlier behavior
        updateConsistentOutlierStatus(profile);
        
        profile.lastUpdate = System.currentTimeMillis();
    }
    
    /**
     * Calculate Z-score for a metric
     */
    private float calculateZScore(String metric, float value) {
        Float mean = populationStats.means.get(metric);
        Float stdDev = populationStats.standardDeviations.get(metric);
        
        if (mean == null || stdDev == null || stdDev == 0) {
            return 0.0f;
        }
        
        return (value - mean) / stdDev;
    }
    
    /**
     * Update composite outlier score
     */
    private void updateCompositeScore(OutlierProfile profile) {
        float sum = 0.0f;
        float maxScore = 0.0f;
        
        for (float score : profile.outlierScores) {
            sum += score;
            maxScore = Math.max(maxScore, score);
        }
        
        float avgScore = sum / profile.outlierScores.length;
        
        // Composite score combines average and maximum (weighted toward max for safety)
        profile.compositeOutlierScore = (maxScore * 0.7f) + (avgScore * 0.3f);
    }
    
    /**
     * Update outlier counts
     */
    private void updateOutlierCounts(OutlierProfile profile) {
        profile.outlierCount = 0;
        profile.extremeOutlierCount = 0;
        
        for (float zScore : profile.zScores) {
            float absZScore = Math.abs(zScore);
            if (absZScore >= Z_SCORE_THRESHOLD) {
                profile.outlierCount++;
            }
            if (absZScore >= EXTREME_Z_SCORE_THRESHOLD) {
                profile.extremeOutlierCount++;
            }
        }
    }
    
    /**
     * Update consistent outlier status
     */
    private void updateConsistentOutlierStatus(OutlierProfile profile) {
        List<OutlierDataPoint> history = playerDataHistory.values().stream()
                .flatMap(List::stream)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        if (history.size() >= MIN_SAMPLES_FOR_ANALYSIS) {
            // Check if player has been consistently an outlier
            int consistentOutlierSamples = 0;
            for (int i = Math.max(0, history.size() - MIN_SAMPLES_FOR_ANALYSIS); i < history.size(); i++) {
                OutlierDataPoint dp = history.get(i);
                if (isOutlierDataPoint(dp)) {
                    consistentOutlierSamples++;
                }
            }
            
            profile.isConsistentOutlier = consistentOutlierSamples >= MIN_SAMPLES_FOR_ANALYSIS * 0.7f;
        }
    }
    
    /**
     * Check if a data point represents outlier behavior
     */
    private boolean isOutlierDataPoint(OutlierDataPoint dp) {
        float accuracyZ = Math.abs(calculateZScore("accuracy", dp.accuracy));
        float kdrZ = Math.abs(calculateZScore("kdr", dp.kdr));
        float headshotZ = Math.abs(calculateZScore("headshotRate", dp.headshotRate));
        
        return accuracyZ >= Z_SCORE_THRESHOLD || kdrZ >= Z_SCORE_THRESHOLD || headshotZ >= Z_SCORE_THRESHOLD;
    }
    
    /**
     * Check for Z-score based outliers
     */
    private List<StatisticalViolation> checkZScoreOutliers(String playerId, OutlierProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        if (profile.outlierCount >= 2) {
            float severity = Math.min(1.0f, profile.outlierCount / 6.0f);
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.OUTLIER_BEHAVIOR,
                String.format("Player is statistical outlier in %d metrics", profile.outlierCount),
                severity,
                "OutlierDetector"
            );
            
            violation.addEvidence(String.format("Outlier metrics: %d/6, Composite score: %.2f", 
                                               profile.outlierCount, profile.compositeOutlierScore));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check for IQR (Interquartile Range) based outliers
     */
    private List<StatisticalViolation> checkIQROutliers(String playerId, OutlierDataPoint dataPoint) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        // This would require maintaining quartile statistics
        // For now, we'll use a simplified approach based on extreme values
        
        if (dataPoint.accuracy > 0.9f || dataPoint.kdr > 15.0f || dataPoint.headshotRate > 0.8f) {
            float severity = 0.8f;
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.OUTLIER_BEHAVIOR,
                "Player performance exceeds expected ranges",
                severity,
                "OutlierDetector"
            );
            
            violation.addEvidence(String.format("Accuracy: %.3f, KDR: %.2f, Headshot: %.3f", 
                                               dataPoint.accuracy, dataPoint.kdr, dataPoint.headshotRate));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check for consistent outlier behavior
     */
    private List<StatisticalViolation> checkConsistentOutliers(String playerId, OutlierProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        if (profile.isConsistentOutlier) {
            float severity = 0.9f;
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.OUTLIER_BEHAVIOR,
                "Player shows consistent outlier behavior over time",
                severity,
                "OutlierDetector"
            );
            
            violation.addEvidence(String.format("Consistent outlier: true, Composite score: %.2f", 
                                               profile.compositeOutlierScore));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check for multi-metric outliers
     */
    private List<StatisticalViolation> checkMultiMetricOutliers(String playerId, OutlierProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        if (profile.compositeOutlierScore >= 1.5f) {
            float severity = Math.min(1.0f, profile.compositeOutlierScore / 2.0f);
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.OUTLIER_BEHAVIOR,
                String.format("High composite outlier score (%.2f)", profile.compositeOutlierScore),
                severity,
                "OutlierDetector"
            );
            
            violation.addEvidence(String.format("Composite score: %.2f, Outlier count: %d", 
                                               profile.compositeOutlierScore, profile.outlierCount));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Check for extreme outliers
     */
    private List<StatisticalViolation> checkExtremeOutliers(String playerId, OutlierProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        if (profile.extremeOutlierCount > 0) {
            float severity = Math.min(1.0f, profile.extremeOutlierCount / 3.0f);
            
            StatisticalViolation violation = new StatisticalViolation(
                playerId,
                ViolationType.OUTLIER_BEHAVIOR,
                String.format("Extreme statistical outlier in %d metrics", profile.extremeOutlierCount),
                severity,
                "OutlierDetector"
            );
            
            violation.addEvidence(String.format("Extreme outliers: %d, Max Z-score: %.2f", 
                                               profile.extremeOutlierCount, getMaxAbsZScore(profile)));
            violations.add(violation);
        }
        
        return violations;
    }
    
    /**
     * Get maximum absolute Z-score from profile
     */
    private float getMaxAbsZScore(OutlierProfile profile) {
        float maxZ = 0.0f;
        for (float z : profile.zScores) {
            maxZ = Math.max(maxZ, Math.abs(z));
        }
        return maxZ;
    }
    
    /**
     * Get outlier profile for a player
     */
    public OutlierProfile getPlayerProfile(String playerId) {
        return playerProfiles.get(playerId);
    }
    
    /**
     * Get current population statistics
     */
    public PopulationStatistics getPopulationStatistics() {
        return populationStats;
    }
    
    /**
     * Clear old data to prevent memory leaks
     */
    public void cleanup() {
        long cutoffTime = System.currentTimeMillis() - (ANALYSIS_WINDOW_MS * 2);
        
        playerProfiles.entrySet().removeIf(entry -> 
            entry.getValue().lastUpdate < cutoffTime);
        
        playerDataHistory.entrySet().removeIf(entry -> {
            List<OutlierDataPoint> history = entry.getValue();
            return history.isEmpty() || history.size() < MIN_SAMPLES_FOR_ANALYSIS / 2;
        });
    }
}