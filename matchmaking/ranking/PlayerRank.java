package fps.matchmaking.ranking;

import fps.matchmaking.rating.PlayerRating;

/**
 * Represents a player's visible rank and progression data
 */
public class PlayerRank {
    
    private final int playerId;
    private final RankTier rankTier;
    private final int rankPoints;
    private final int wins;
    private final int losses;
    private final int winStreak;
    private final int lossStreak;
    private final long lastPlayed;
    private final int season;
    private final boolean inPlacement;
    
    // Additional progression data
    private final int peakRankTier;
    private final int totalGamesPlayed;
    private final long seasonStartTime;
    
    public PlayerRank(int playerId, RankTier rankTier, int rankPoints, int wins, int losses,
                     int winStreak, int lossStreak, long lastPlayed, int season, boolean inPlacement) {
        this(playerId, rankTier, rankPoints, wins, losses, winStreak, lossStreak, lastPlayed,
             season, inPlacement, rankTier.ordinal(), wins + losses, System.currentTimeMillis());
    }
    
    public PlayerRank(int playerId, RankTier rankTier, int rankPoints, int wins, int losses,
                     int winStreak, int lossStreak, long lastPlayed, int season, boolean inPlacement,
                     int peakRankTier, int totalGamesPlayed, long seasonStartTime) {
        this.playerId = playerId;
        this.rankTier = rankTier;
        this.rankPoints = rankPoints;
        this.wins = wins;
        this.losses = losses;
        this.winStreak = winStreak;
        this.lossStreak = lossStreak;
        this.lastPlayed = lastPlayed;
        this.season = season;
        this.inPlacement = inPlacement;
        this.peakRankTier = Math.max(peakRankTier, rankTier.ordinal());
        this.totalGamesPlayed = totalGamesPlayed;
        this.seasonStartTime = seasonStartTime;
    }
    
    /**
     * Get win rate as a percentage
     */
    public float getWinRate() {
        int totalGames = wins + losses;
        return totalGames > 0 ? (float) wins / totalGames * 100.0f : 0.0f;
    }
    
    /**
     * Get rank progress within current tier (0.0 to 1.0)
     */
    public float getRankProgress() {
        int maxPoints = getRankTier().getMaxPoints();
        int minPoints = getRankTier().getMinPoints();
        
        if (maxPoints == minPoints) {
            return 1.0f; // Max tier
        }
        
        return Math.max(0.0f, Math.min(1.0f, 
            (float) (rankPoints - minPoints) / (maxPoints - minPoints)));
    }
    
    /**
     * Get points needed for next tier
     */
    public int getPointsToNextTier() {
        RankTier nextTier = getNextTier();
        if (nextTier == null) {
            return 0; // Already at max tier
        }
        
        return nextTier.getMinPoints() - rankPoints;
    }
    
    /**
     * Get next rank tier
     */
    public RankTier getNextTier() {
        RankTier[] tiers = RankTier.values();
        int currentIndex = rankTier.ordinal();
        
        if (currentIndex < tiers.length - 1) {
            return tiers[currentIndex + 1];
        }
        
        return null; // Already at max tier
    }
    
    /**
     * Get previous rank tier
     */
    public RankTier getPreviousTier() {
        RankTier[] tiers = RankTier.values();
        int currentIndex = rankTier.ordinal();
        
        if (currentIndex > 0) {
            return tiers[currentIndex - 1];
        }
        
        return null; // Already at min tier
    }
    
    /**
     * Get peak rank tier as enum
     */
    public RankTier getPeakRankTierEnum() {
        RankTier[] tiers = RankTier.values();
        if (peakRankTier >= 0 && peakRankTier < tiers.length) {
            return tiers[peakRankTier];
        }
        return rankTier;
    }
    
    /**
     * Check if player is on a winning streak
     */
    public boolean isOnWinStreak() {
        return winStreak > 0;
    }
    
    /**
     * Check if player is on a losing streak
     */
    public boolean isOnLossStreak() {
        return lossStreak > 0;
    }
    
    /**
     * Create a copy with match result applied
     */
    public PlayerRank withMatchResult(boolean won, int pointsChange, long matchTime, int currentSeason) {
        int newWins = won ? wins + 1 : wins;
        int newLosses = won ? losses : losses + 1;
        int newWinStreak = won ? winStreak + 1 : 0;
        int newLossStreak = won ? 0 : lossStreak + 1;
        int newRankPoints = Math.max(0, rankPoints + pointsChange);
        
        return new PlayerRank(
            playerId,
            rankTier,
            newRankPoints,
            newWins,
            newLosses,
            newWinStreak,
            newLossStreak,
            matchTime,
            currentSeason,
            inPlacement && (newWins + newLosses) < 10, // Exit placement after 10 games
            peakRankTier,
            totalGamesPlayed + 1,
            seasonStartTime
        );
    }
    
    /**
     * Create a copy with promotion applied
     */
    public PlayerRank withPromotion(RankTier newTier) {
        return new PlayerRank(
            playerId,
            newTier,
            newTier.getMinPoints(), // Start at minimum points of new tier
            wins,
            losses,
            winStreak,
            lossStreak,
            lastPlayed,
            season,
            false, // No longer in placement after promotion
            Math.max(peakRankTier, newTier.ordinal()),
            totalGamesPlayed,
            seasonStartTime
        );
    }
    
    /**
     * Create a copy with demotion applied
     */
    public PlayerRank withDemotion(RankTier newTier) {
        return new PlayerRank(
            playerId,
            newTier,
            newTier.getMaxPoints(), // Start at maximum points of new tier
            wins,
            losses,
            0, // Reset win streak on demotion
            lossStreak,
            lastPlayed,
            season,
            inPlacement,
            peakRankTier, // Peak doesn't change on demotion
            totalGamesPlayed,
            seasonStartTime
        );
    }
    
    /**
     * Create a copy with rank decay applied
     */
    public PlayerRank withRankDecay(int pointsLost) {
        int newRankPoints = Math.max(0, rankPoints - pointsLost);
        
        return new PlayerRank(
            playerId,
            rankTier,
            newRankPoints,
            wins,
            losses,
            winStreak,
            lossStreak,
            lastPlayed,
            season,
            inPlacement,
            peakRankTier,
            totalGamesPlayed,
            seasonStartTime
        );
    }
    
    /**
     * Create a copy with season reset applied
     */
    public PlayerRank withSeasonReset(int newSeason) {
        // Soft reset: reduce rank points but keep some progress
        int resetPoints = Math.max(0, rankPoints / 2);
        RankTier resetTier = rankTier;
        
        // Demote high-tier players slightly
        if (rankTier.ordinal() > RankTier.GOLD_III.ordinal()) {
            RankTier[] tiers = RankTier.values();
            int newTierIndex = Math.max(RankTier.SILVER_I.ordinal(), rankTier.ordinal() - 2);
            resetTier = tiers[newTierIndex];
            resetPoints = resetTier.getMinPoints();
        }
        
        return new PlayerRank(
            playerId,
            resetTier,
            resetPoints,
            0, // Reset wins/losses for new season
            0,
            0, // Reset streaks
            0,
            lastPlayed,
            newSeason,
            true, // Back in placement for new season
            peakRankTier, // Keep peak from previous season
            0, // Reset games played for new season
            System.currentTimeMillis()
        );
    }
    
    // Getters
    public int getPlayerId() { return playerId; }
    public RankTier getRankTier() { return rankTier; }
    public int getRankPoints() { return rankPoints; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public int getWinStreak() { return winStreak; }
    public int getLossStreak() { return lossStreak; }
    public long getLastPlayed() { return lastPlayed; }
    public int getSeason() { return season; }
    public boolean isInPlacement() { return inPlacement; }
    public int getPeakRankTier() { return peakRankTier; }
    public int getTotalGamesPlayed() { return totalGamesPlayed; }
    public long getSeasonStartTime() { return seasonStartTime; }
    
    @Override
    public String toString() {
        return String.format("PlayerRank{playerId=%d, tier=%s, points=%d, record=%d-%d, streak=%s}",
                           playerId, rankTier, rankPoints, wins, losses,
                           isOnWinStreak() ? "W" + winStreak : (isOnLossStreak() ? "L" + lossStreak : "0"));
    }
    
    /**
     * Rank tiers with point thresholds
     */
    public enum RankTier {
        BRONZE_I("Bronze I", 0, 199),
        BRONZE_II("Bronze II", 200, 399),
        BRONZE_III("Bronze III", 400, 599),
        SILVER_I("Silver I", 600, 799),
        SILVER_II("Silver II", 800, 999),
        SILVER_III("Silver III", 1000, 1199),
        GOLD_I("Gold I", 1200, 1399),
        GOLD_II("Gold II", 1400, 1599),
        GOLD_III("Gold III", 1600, 1799),
        PLATINUM_I("Platinum I", 1800, 1999),
        PLATINUM_II("Platinum II", 2000, 2199),
        PLATINUM_III("Platinum III", 2200, 2399),
        DIAMOND_I("Diamond I", 2400, 2599),
        DIAMOND_II("Diamond II", 2600, 2799),
        DIAMOND_III("Diamond III", 2800, 2999),
        MASTER("Master", 3000, 3499),
        GRANDMASTER("Grandmaster", 3500, Integer.MAX_VALUE);
        
        private final String displayName;
        private final int minPoints;
        private final int maxPoints;
        
        RankTier(String displayName, int minPoints, int maxPoints) {
            this.displayName = displayName;
            this.minPoints = minPoints;
            this.maxPoints = maxPoints;
        }
        
        public static RankTier fromRating(float rating) {
            // Convert HBR rating to rank tier
            int points = Math.max(0, (int) ((rating - 1000) / 2)); // Rough conversion
            
            for (RankTier tier : values()) {
                if (points >= tier.minPoints && points <= tier.maxPoints) {
                    return tier;
                }
            }
            
            return BRONZE_I; // Default fallback
        }
        
        public static RankTier fromPoints(int points) {
            for (RankTier tier : values()) {
                if (points >= tier.minPoints && points <= tier.maxPoints) {
                    return tier;
                }
            }
            
            return GRANDMASTER; // If points exceed all tiers
        }
        
        public String getDisplayName() { return displayName; }
        public int getMinPoints() { return minPoints; }
        public int getMaxPoints() { return maxPoints; }
        
        public boolean isMetalTier() {
            return ordinal() <= SILVER_III.ordinal();
        }
        
        public boolean isHighTier() {
            return ordinal() >= DIAMOND_I.ordinal();
        }
    }
}