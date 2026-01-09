package fps.matchmaking.rating;

/**
 * Represents a player's Hidden Battle Rating and associated metadata
 */
public class PlayerRating {
    
    private final int playerId;
    private final float rating;
    private final float uncertainty;
    private final int gamesPlayed;
    private final long lastUpdated;
    private final boolean inPlacement;
    
    // Additional rating metrics
    private final float peakRating;
    private final int winStreak;
    private final int lossStreak;
    private final float recentPerformance;
    
    public PlayerRating(int playerId, float rating, float uncertainty, int gamesPlayed, 
                       long lastUpdated, boolean inPlacement) {
        this(playerId, rating, uncertainty, gamesPlayed, lastUpdated, inPlacement,
             rating, 0, 0, 1.0f);
    }
    
    public PlayerRating(int playerId, float rating, float uncertainty, int gamesPlayed,
                       long lastUpdated, boolean inPlacement, float peakRating,
                       int winStreak, int lossStreak, float recentPerformance) {
        this.playerId = playerId;
        this.rating = rating;
        this.uncertainty = uncertainty;
        this.gamesPlayed = gamesPlayed;
        this.lastUpdated = lastUpdated;
        this.inPlacement = inPlacement;
        this.peakRating = peakRating;
        this.winStreak = winStreak;
        this.lossStreak = lossStreak;
        this.recentPerformance = recentPerformance;
    }
    
    /**
     * Get effective rating (rating adjusted for uncertainty)
     */
    public float getEffectiveRating() {
        // Conservative estimate: rating minus uncertainty
        return rating - (uncertainty * 0.5f);
    }
    
    /**
     * Get rating confidence (inverse of uncertainty)
     */
    public float getConfidence() {
        return Math.max(0.0f, 1.0f - (uncertainty / 350.0f));
    }
    
    /**
     * Check if player is still in placement matches
     */
    public boolean isInPlacement() {
        return inPlacement;
    }
    
    /**
     * Get rating tier (for display purposes)
     */
    public RatingTier getRatingTier() {
        return RatingTier.fromRating(rating);
    }
    
    /**
     * Create a copy with updated values
     */
    public PlayerRating withUpdatedRating(float newRating, float newUncertainty, 
                                        int newGamesPlayed, boolean newInPlacement) {
        return new PlayerRating(
            playerId,
            newRating,
            newUncertainty,
            newGamesPlayed,
            System.currentTimeMillis(),
            newInPlacement,
            Math.max(peakRating, newRating),
            winStreak,
            lossStreak,
            recentPerformance
        );
    }
    
    /**
     * Create a copy with updated streak information
     */
    public PlayerRating withUpdatedStreaks(int newWinStreak, int newLossStreak, 
                                         float newRecentPerformance) {
        return new PlayerRating(
            playerId,
            rating,
            uncertainty,
            gamesPlayed,
            lastUpdated,
            inPlacement,
            peakRating,
            newWinStreak,
            newLossStreak,
            newRecentPerformance
        );
    }
    
    // Getters
    public int getPlayerId() { return playerId; }
    public float getRating() { return rating; }
    public float getUncertainty() { return uncertainty; }
    public int getGamesPlayed() { return gamesPlayed; }
    public long getLastUpdated() { return lastUpdated; }
    public float getPeakRating() { return peakRating; }
    public int getWinStreak() { return winStreak; }
    public int getLossStreak() { return lossStreak; }
    public float getRecentPerformance() { return recentPerformance; }
    
    @Override
    public String toString() {
        return String.format("PlayerRating{playerId=%d, rating=%.1f, uncertainty=%.1f, games=%d, tier=%s}",
                           playerId, rating, uncertainty, gamesPlayed, getRatingTier());
    }
    
    /**
     * Rating tiers for display and matchmaking purposes
     */
    public enum RatingTier {
        BRONZE(0, 1199, "Bronze"),
        SILVER(1200, 1599, "Silver"),
        GOLD(1600, 1999, "Gold"),
        PLATINUM(2000, 2399, "Platinum"),
        DIAMOND(2400, 2799, "Diamond"),
        MASTER(2800, 3199, "Master"),
        GRANDMASTER(3200, Integer.MAX_VALUE, "Grandmaster");
        
        private final int minRating;
        private final int maxRating;
        private final String displayName;
        
        RatingTier(int minRating, int maxRating, String displayName) {
            this.minRating = minRating;
            this.maxRating = maxRating;
            this.displayName = displayName;
        }
        
        public static RatingTier fromRating(float rating) {
            int intRating = (int) rating;
            for (RatingTier tier : values()) {
                if (intRating >= tier.minRating && intRating <= tier.maxRating) {
                    return tier;
                }
            }
            return BRONZE; // Default fallback
        }
        
        public int getMinRating() { return minRating; }
        public int getMaxRating() { return maxRating; }
        public String getDisplayName() { return displayName; }
    }
}