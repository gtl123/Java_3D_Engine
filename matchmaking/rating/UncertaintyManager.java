package fps.matchmaking.rating;

import engine.logging.LogManager;
import fps.matchmaking.MatchmakingConfiguration.HBRConfiguration;

/**
 * Manages player rating uncertainty calculations
 */
public class UncertaintyManager {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final HBRConfiguration config;
    private volatile boolean initialized = false;
    
    public UncertaintyManager(HBRConfiguration config) {
        this.config = config;
    }
    
    public void initialize() {
        initialized = true;
        logManager.info("UncertaintyManager", "Uncertainty manager initialized");
    }
    
    /**
     * Update player uncertainty based on rating change
     */
    public float updateUncertainty(PlayerRating playerRating, RatingChange ratingChange) {
        if (!initialized) {
            throw new IllegalStateException("UncertaintyManager not initialized");
        }
        
        float currentUncertainty = playerRating.getUncertainty();
        
        // Base uncertainty reduction from playing a game
        float uncertaintyReduction = calculateBaseUncertaintyReduction(playerRating);
        
        // Adjust based on rating change magnitude
        float ratingChangeFactor = calculateRatingChangeFactor(ratingChange);
        
        // Adjust based on expected vs actual performance
        float performanceFactor = calculatePerformanceFactor(ratingChange);
        
        // Calculate new uncertainty
        float newUncertainty = currentUncertainty * config.getUncertaintyDecayRate();
        newUncertainty -= uncertaintyReduction;
        newUncertainty += ratingChangeFactor;
        newUncertainty += performanceFactor;
        
        // Clamp to valid range
        newUncertainty = Math.max(config.getMinUncertainty(), 
                                 Math.min(config.getInitialUncertainty(), newUncertainty));
        
        return newUncertainty;
    }
    
    /**
     * Calculate base uncertainty reduction from playing a game
     */
    private float calculateBaseUncertaintyReduction(PlayerRating playerRating) {
        // More reduction for newer players
        if (playerRating.isInPlacement()) {
            return 15.0f;
        }
        
        // Standard reduction for experienced players
        return 5.0f;
    }
    
    /**
     * Calculate uncertainty adjustment based on rating change magnitude
     */
    private float calculateRatingChangeFactor(RatingChange ratingChange) {
        // Large rating changes indicate unpredictable performance
        float ratingChangeMagnitude = Math.abs(ratingChange.getRatingDelta());
        return (ratingChangeMagnitude / config.getMaxRatingChange()) * 10.0f;
    }
    
    /**
     * Calculate uncertainty adjustment based on performance vs expectation
     */
    private float calculatePerformanceFactor(RatingChange ratingChange) {
        // If performance was very different from expected, increase uncertainty
        float performanceDifference = Math.abs(ratingChange.getActualScore() - ratingChange.getExpectedScore());
        return performanceDifference * 8.0f;
    }
    
    /**
     * Calculate uncertainty for inactive players (time-based decay)
     */
    public float calculateInactivityUncertainty(PlayerRating playerRating, long timeSinceLastGame) {
        if (!initialized) {
            throw new IllegalStateException("UncertaintyManager not initialized");
        }
        
        // Increase uncertainty over time for inactive players
        long daysInactive = timeSinceLastGame / (24 * 60 * 60 * 1000);
        
        if (daysInactive <= 7) {
            return playerRating.getUncertainty(); // No change for first week
        }
        
        // Gradual increase after first week
        float uncertaintyIncrease = (daysInactive - 7) * 2.0f;
        float newUncertainty = playerRating.getUncertainty() + uncertaintyIncrease;
        
        // Cap at initial uncertainty
        return Math.min(config.getInitialUncertainty(), newUncertainty);
    }
    
    /**
     * Calculate uncertainty for team matchmaking
     */
    public float calculateTeamUncertainty(PlayerRating[] teamRatings) {
        if (teamRatings.length == 0) {
            return config.getInitialUncertainty();
        }
        
        // Use root mean square of individual uncertainties
        float sumSquaredUncertainties = 0.0f;
        for (PlayerRating rating : teamRatings) {
            float uncertainty = rating.getUncertainty();
            sumSquaredUncertainties += uncertainty * uncertainty;
        }
        
        return (float) Math.sqrt(sumSquaredUncertainties / teamRatings.length);
    }
    
    public void cleanup() {
        initialized = false;
        logManager.info("UncertaintyManager", "Uncertainty manager cleaned up");
    }
    
    public boolean isInitialized() { return initialized; }
}