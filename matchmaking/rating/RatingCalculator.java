package fps.matchmaking.rating;

import engine.logging.LogManager;
import fps.matchmaking.MatchmakingConfiguration.HBRConfiguration;

/**
 * Calculates rating changes based on match outcomes and performance
 */
public class RatingCalculator {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final HBRConfiguration config;
    private volatile boolean initialized = false;
    
    public RatingCalculator(HBRConfiguration config) {
        this.config = config;
    }
    
    public void initialize() {
        initialized = true;
        logManager.info("RatingCalculator", "Rating calculator initialized");
    }
    
    /**
     * Calculate rating change for a player based on match outcome
     */
    public RatingChange calculateRatingChange(PlayerRating playerRating, float teamRating, 
                                            float opponentRating, float score, 
                                            float performanceMultiplier) {
        if (!initialized) {
            throw new IllegalStateException("RatingCalculator not initialized");
        }
        
        // Calculate expected score using Elo formula
        float expectedScore = calculateExpectedScore(teamRating, opponentRating);
        
        // Calculate base K-factor
        float kFactor = calculateKFactor(playerRating);
        
        // Apply performance multiplier
        float adjustedKFactor = kFactor * performanceMultiplier;
        
        // Calculate rating change
        float ratingDelta = adjustedKFactor * (score - expectedScore);
        
        // Apply uncertainty bonus for new players
        if (playerRating.isInPlacement()) {
            ratingDelta *= calculatePlacementMultiplier(playerRating.getGamesPlayed());
        }
        
        // Clamp rating change to maximum allowed
        ratingDelta = Math.max(-config.getMaxRatingChange(), 
                              Math.min(config.getMaxRatingChange(), ratingDelta));
        
        // Calculate confidence change
        float confidenceChange = calculateConfidenceChange(playerRating, Math.abs(ratingDelta));
        
        return new RatingChange(
            ratingDelta,
            confidenceChange,
            expectedScore,
            score,
            adjustedKFactor,
            performanceMultiplier
        );
    }
    
    /**
     * Calculate expected score using Elo rating system
     */
    private float calculateExpectedScore(float teamRating, float opponentRating) {
        float ratingDifference = opponentRating - teamRating;
        return 1.0f / (1.0f + (float) Math.pow(10.0, ratingDifference / 400.0));
    }
    
    /**
     * Calculate K-factor based on player rating and games played
     */
    private float calculateKFactor(PlayerRating playerRating) {
        float baseKFactor = config.getKFactor();
        
        // Higher K-factor for new players
        if (playerRating.isInPlacement()) {
            return baseKFactor * 1.5f;
        }
        
        // Reduce K-factor for experienced players
        if (playerRating.getGamesPlayed() > 100) {
            return baseKFactor * 0.8f;
        }
        
        // Increase K-factor for high uncertainty
        float uncertaintyMultiplier = 1.0f + (playerRating.getUncertainty() / 350.0f) * 0.5f;
        
        return baseKFactor * uncertaintyMultiplier;
    }
    
    /**
     * Calculate placement match multiplier
     */
    private float calculatePlacementMultiplier(int gamesPlayed) {
        if (gamesPlayed >= config.getPlacementMatchCount()) {
            return 1.0f;
        }
        
        // Higher multiplier for early placement matches
        float progress = (float) gamesPlayed / config.getPlacementMatchCount();
        return 2.0f - progress; // Ranges from 2.0 to 1.0
    }
    
    /**
     * Calculate confidence change based on rating change magnitude
     */
    private float calculateConfidenceChange(PlayerRating playerRating, float ratingChangeMagnitude) {
        // Larger rating changes indicate less predictable performance, reducing confidence
        float confidenceReduction = ratingChangeMagnitude / config.getMaxRatingChange() * 10.0f;
        
        // But also consider that playing games should generally increase confidence
        float baseConfidenceIncrease = 2.0f;
        
        return baseConfidenceIncrease - confidenceReduction;
    }
    
    /**
     * Calculate team rating from individual player ratings
     */
    public float calculateTeamRating(PlayerRating[] playerRatings) {
        if (playerRatings.length == 0) {
            return config.getInitialRating();
        }
        
        float totalRating = 0.0f;
        float totalWeight = 0.0f;
        
        for (PlayerRating rating : playerRatings) {
            // Weight by confidence (inverse of uncertainty)
            float weight = Math.max(0.1f, 1.0f - (rating.getUncertainty() / 350.0f));
            totalRating += rating.getRating() * weight;
            totalWeight += weight;
        }
        
        return totalWeight > 0 ? totalRating / totalWeight : config.getInitialRating();
    }
    
    /**
     * Calculate match quality prediction
     */
    public float calculateMatchQuality(float team1Rating, float team2Rating, 
                                     float team1Uncertainty, float team2Uncertainty) {
        // Rating difference factor (closer ratings = higher quality)
        float ratingDiff = Math.abs(team1Rating - team2Rating);
        float ratingQuality = Math.max(0.0f, 1.0f - (ratingDiff / 400.0f));
        
        // Uncertainty factor (lower uncertainty = higher quality)
        float avgUncertainty = (team1Uncertainty + team2Uncertainty) / 2.0f;
        float uncertaintyQuality = Math.max(0.0f, 1.0f - (avgUncertainty / 350.0f));
        
        // Combined quality score
        return (ratingQuality * 0.7f) + (uncertaintyQuality * 0.3f);
    }
    
    public void cleanup() {
        initialized = false;
        logManager.info("RatingCalculator", "Rating calculator cleaned up");
    }
    
    public boolean isInitialized() { return initialized; }
}

/**
 * Represents a rating change calculation result
 */
class RatingChange {
    private final float ratingDelta;
    private final float confidenceChange;
    private final float expectedScore;
    private final float actualScore;
    private final float kFactor;
    private final float performanceMultiplier;
    
    public RatingChange(float ratingDelta, float confidenceChange, float expectedScore,
                       float actualScore, float kFactor, float performanceMultiplier) {
        this.ratingDelta = ratingDelta;
        this.confidenceChange = confidenceChange;
        this.expectedScore = expectedScore;
        this.actualScore = actualScore;
        this.kFactor = kFactor;
        this.performanceMultiplier = performanceMultiplier;
    }
    
    // Getters
    public float getRatingDelta() { return ratingDelta; }
    public float getConfidenceChange() { return confidenceChange; }
    public float getExpectedScore() { return expectedScore; }
    public float getActualScore() { return actualScore; }
    public float getKFactor() { return kFactor; }
    public float getPerformanceMultiplier() { return performanceMultiplier; }
    
    @Override
    public String toString() {
        return String.format("RatingChange{delta=%.1f, expected=%.2f, actual=%.2f, k=%.1f}",
                           ratingDelta, expectedScore, actualScore, kFactor);
    }
}