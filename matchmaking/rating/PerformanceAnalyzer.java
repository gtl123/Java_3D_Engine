package fps.matchmaking.rating;

import engine.logging.LogManager;
import fps.matchmaking.MatchmakingConfiguration.HBRConfiguration;
import fps.matchmaking.MatchmakingTypes.PlayerMatchPerformance;

/**
 * Analyzes player performance to adjust rating calculations
 */
public class PerformanceAnalyzer {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final HBRConfiguration config;
    private volatile boolean initialized = false;
    
    public PerformanceAnalyzer(HBRConfiguration config) {
        this.config = config;
    }
    
    public void initialize() {
        initialized = true;
        logManager.info("PerformanceAnalyzer", "Performance analyzer initialized");
    }
    
    /**
     * Calculate performance multiplier based on player's match performance
     */
    public float calculatePerformanceMultiplier(PlayerMatchPerformance performance) {
        if (!initialized) {
            throw new IllegalStateException("PerformanceAnalyzer not initialized");
        }
        
        if (performance == null) {
            return 1.0f; // Neutral multiplier if no performance data
        }
        
        float multiplier = 1.0f;
        
        // K/D ratio factor
        multiplier += calculateKDFactor(performance);
        
        // Damage factor
        multiplier += calculateDamageFactor(performance);
        
        // Accuracy factor
        multiplier += calculateAccuracyFactor(performance);
        
        // Objective score factor
        multiplier += calculateObjectiveFactor(performance);
        
        // MVP bonus
        if (performance.isMvp()) {
            multiplier += 0.15f;
        }
        
        // Clamp multiplier to reasonable range
        return Math.max(0.5f, Math.min(2.0f, multiplier));
    }
    
    /**
     * Calculate K/D ratio contribution to performance multiplier
     */
    private float calculateKDFactor(PlayerMatchPerformance performance) {
        float kdRatio = performance.getKDRatio();
        
        // Normalize K/D ratio (1.0 = neutral, >1.0 = positive, <1.0 = negative)
        if (kdRatio >= 1.0f) {
            return Math.min(0.3f, (kdRatio - 1.0f) * 0.2f);
        } else {
            return Math.max(-0.3f, (kdRatio - 1.0f) * 0.3f);
        }
    }
    
    /**
     * Calculate damage contribution to performance multiplier
     */
    private float calculateDamageFactor(PlayerMatchPerformance performance) {
        float damage = performance.getDamageDealt();
        
        // Normalize damage (assume 1000 damage is average)
        float normalizedDamage = damage / 1000.0f;
        
        if (normalizedDamage >= 1.0f) {
            return Math.min(0.2f, (normalizedDamage - 1.0f) * 0.15f);
        } else {
            return Math.max(-0.2f, (normalizedDamage - 1.0f) * 0.2f);
        }
    }
    
    /**
     * Calculate accuracy contribution to performance multiplier
     */
    private float calculateAccuracyFactor(PlayerMatchPerformance performance) {
        float accuracy = performance.getAccuracy();
        
        // Normalize accuracy (assume 25% is average)
        float normalizedAccuracy = accuracy / 25.0f;
        
        if (normalizedAccuracy >= 1.0f) {
            return Math.min(0.15f, (normalizedAccuracy - 1.0f) * 0.1f);
        } else {
            return Math.max(-0.15f, (normalizedAccuracy - 1.0f) * 0.15f);
        }
    }
    
    /**
     * Calculate objective score contribution to performance multiplier
     */
    private float calculateObjectiveFactor(PlayerMatchPerformance performance) {
        int objectiveScore = performance.getObjectiveScore();
        
        // Normalize objective score (assume 100 is average)
        float normalizedScore = objectiveScore / 100.0f;
        
        if (normalizedScore >= 1.0f) {
            return Math.min(0.25f, (normalizedScore - 1.0f) * 0.2f);
        } else {
            return Math.max(-0.25f, (normalizedScore - 1.0f) * 0.25f);
        }
    }
    
    /**
     * Analyze performance trends over multiple matches
     */
    public PerformanceTrend analyzePerformanceTrend(PlayerMatchPerformance[] recentPerformances) {
        if (!initialized) {
            throw new IllegalStateException("PerformanceAnalyzer not initialized");
        }
        
        if (recentPerformances.length < 3) {
            return PerformanceTrend.STABLE; // Not enough data
        }
        
        // Calculate performance scores for each match
        float[] performanceScores = new float[recentPerformances.length];
        for (int i = 0; i < recentPerformances.length; i++) {
            performanceScores[i] = calculatePerformanceScore(recentPerformances[i]);
        }
        
        // Analyze trend
        return calculateTrend(performanceScores);
    }
    
    /**
     * Calculate overall performance score for a match
     */
    private float calculatePerformanceScore(PlayerMatchPerformance performance) {
        float score = 0.0f;
        
        // K/D contribution (30%)
        score += performance.getKDRatio() * 0.3f;
        
        // Damage contribution (25%)
        score += (performance.getDamageDealt() / 1000.0f) * 0.25f;
        
        // Accuracy contribution (20%)
        score += (performance.getAccuracy() / 100.0f) * 0.2f;
        
        // Objective contribution (25%)
        score += (performance.getObjectiveScore() / 100.0f) * 0.25f;
        
        return score;
    }
    
    /**
     * Calculate performance trend from scores
     */
    private PerformanceTrend calculateTrend(float[] scores) {
        // Simple linear regression to determine trend
        int n = scores.length;
        float sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        
        for (int i = 0; i < n; i++) {
            float x = i;
            float y = scores[i];
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        
        float slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        
        if (slope > 0.05f) {
            return PerformanceTrend.IMPROVING;
        } else if (slope < -0.05f) {
            return PerformanceTrend.DECLINING;
        } else {
            return PerformanceTrend.STABLE;
        }
    }
    
    /**
     * Detect potential smurfing based on performance
     */
    public boolean detectPotentialSmurfing(PlayerRating playerRating, 
                                         PlayerMatchPerformance[] recentPerformances) {
        if (!initialized || recentPerformances.length < 5) {
            return false;
        }
        
        // Check if player is new but performing exceptionally well
        if (playerRating.getGamesPlayed() < 20) {
            float avgPerformanceScore = 0.0f;
            for (PlayerMatchPerformance performance : recentPerformances) {
                avgPerformanceScore += calculatePerformanceScore(performance);
            }
            avgPerformanceScore /= recentPerformances.length;
            
            // If new player consistently performs at high level, might be smurf
            return avgPerformanceScore > 1.5f;
        }
        
        return false;
    }
    
    public void cleanup() {
        initialized = false;
        logManager.info("PerformanceAnalyzer", "Performance analyzer cleaned up");
    }
    
    public boolean isInitialized() { return initialized; }
    
    /**
     * Performance trend enumeration
     */
    public enum PerformanceTrend {
        IMPROVING("Improving"),
        STABLE("Stable"),
        DECLINING("Declining");
        
        private final String displayName;
        
        PerformanceTrend(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
}