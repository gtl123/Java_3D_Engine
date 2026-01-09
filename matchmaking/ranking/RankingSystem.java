package fps.matchmaking.ranking;

import engine.logging.LogManager;
import fps.matchmaking.MatchmakingConfiguration.RankingConfiguration;
import fps.matchmaking.rating.HiddenBattleRatingSystem;
import fps.matchmaking.rating.PlayerRating;
import fps.matchmaking.MatchmakingTypes.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Manages visible player ranks and progression system
 */
public class RankingSystem {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final RankingConfiguration config;
    private final HiddenBattleRatingSystem hbrSystem;
    private final Map<Integer, PlayerRank> playerRanks = new ConcurrentHashMap<>();
    private final SeasonManager seasonManager;
    private final LeaderboardManager leaderboardManager;
    
    private volatile boolean initialized = false;
    
    public RankingSystem(RankingConfiguration config, HiddenBattleRatingSystem hbrSystem) {
        this.config = config;
        this.hbrSystem = hbrSystem;
        this.seasonManager = new SeasonManager(config);
        this.leaderboardManager = new LeaderboardManager();
        
        logManager.info("RankingSystem", "Ranking system created");
    }
    
    /**
     * Initialize the ranking system
     */
    public void initialize() throws Exception {
        if (initialized) {
            return;
        }
        
        logManager.info("RankingSystem", "Initializing ranking system");
        
        try {
            seasonManager.initialize();
            leaderboardManager.initialize();
            
            initialized = true;
            
            logManager.info("RankingSystem", "Ranking system initialization complete");
            
        } catch (Exception e) {
            logManager.error("RankingSystem", "Failed to initialize ranking system", e);
            throw e;
        }
    }
    
    /**
     * Get player rank, creating a new one if it doesn't exist
     */
    public PlayerRank getPlayerRank(int playerId) {
        return playerRanks.computeIfAbsent(playerId, id -> createNewPlayerRank(id));
    }
    
    /**
     * Create a new player rank based on HBR
     */
    private PlayerRank createNewPlayerRank(int playerId) {
        PlayerRating rating = hbrSystem.getPlayerRating(playerId);
        RankTier initialTier = calculateRankTier(rating);
        
        PlayerRank rank = new PlayerRank(
            playerId,
            initialTier,
            0, // rank points
            0, // wins
            0, // losses
            0, // win streak
            0, // loss streak
            System.currentTimeMillis(), // last played
            seasonManager.getCurrentSeason(),
            rating.isInPlacement()
        );
        
        logManager.info("RankingSystem", "Created new player rank",
                       "playerId", playerId,
                       "initialTier", initialTier,
                       "hbrRating", rating.getRating());
        
        return rank;
    }
    
    /**
     * Update player ranks based on match results
     */
    public void updateRanksFromMatch(List<MatchmakingPlayer> players, MatchResult matchResult) {
        if (!initialized) {
            logManager.warn("RankingSystem", "Attempted to update ranks before initialization");
            return;
        }
        
        try {
            // Update winner ranks
            for (MatchmakingPlayer player : matchResult.getWinners()) {
                updatePlayerRankFromWin(player, matchResult);
            }
            
            // Update loser ranks
            for (MatchmakingPlayer player : matchResult.getLosers()) {
                updatePlayerRankFromLoss(player, matchResult);
            }
            
            // Update leaderboards
            leaderboardManager.updateLeaderboards(playerRanks);
            
            logManager.info("RankingSystem", "Updated ranks from match",
                           "matchId", matchResult.getMatchId(),
                           "playerCount", players.size());
            
        } catch (Exception e) {
            logManager.error("RankingSystem", "Error updating ranks from match", e,
                           "matchId", matchResult.getMatchId());
        }
    }
    
    /**
     * Update player rank from a win
     */
    private void updatePlayerRankFromWin(MatchmakingPlayer player, MatchResult matchResult) {
        PlayerRank currentRank = getPlayerRank(player.getPlayerId());
        PlayerRating rating = hbrSystem.getPlayerRating(player.getPlayerId());
        
        // Calculate rank points gained
        int pointsGained = calculateRankPointsGained(currentRank, rating, true, matchResult);
        
        // Update rank data
        PlayerRank newRank = currentRank.withMatchResult(
            true,
            pointsGained,
            System.currentTimeMillis(),
            seasonManager.getCurrentSeason()
        );
        
        // Check for promotion
        newRank = checkForPromotion(newRank, rating);
        
        playerRanks.put(player.getPlayerId(), newRank);
        
        logManager.debug("RankingSystem", "Updated rank from win",
                        "playerId", player.getPlayerId(),
                        "oldTier", currentRank.getRankTier(),
                        "newTier", newRank.getRankTier(),
                        "pointsGained", pointsGained,
                        "winStreak", newRank.getWinStreak());
    }
    
    /**
     * Update player rank from a loss
     */
    private void updatePlayerRankFromLoss(MatchmakingPlayer player, MatchResult matchResult) {
        PlayerRank currentRank = getPlayerRank(player.getPlayerId());
        PlayerRating rating = hbrSystem.getPlayerRating(player.getPlayerId());
        
        // Calculate rank points lost
        int pointsLost = calculateRankPointsGained(currentRank, rating, false, matchResult);
        
        // Update rank data
        PlayerRank newRank = currentRank.withMatchResult(
            false,
            pointsLost, // Will be negative
            System.currentTimeMillis(),
            seasonManager.getCurrentSeason()
        );
        
        // Check for demotion
        newRank = checkForDemotion(newRank, rating);
        
        playerRanks.put(player.getPlayerId(), newRank);
        
        logManager.debug("RankingSystem", "Updated rank from loss",
                        "playerId", player.getPlayerId(),
                        "oldTier", currentRank.getRankTier(),
                        "newTier", newRank.getRankTier(),
                        "pointsLost", Math.abs(pointsLost),
                        "lossStreak", newRank.getLossStreak());
    }
    
    /**
     * Calculate rank points gained/lost from a match
     */
    private int calculateRankPointsGained(PlayerRank currentRank, PlayerRating rating, 
                                        boolean won, MatchResult matchResult) {
        // Base points
        int basePoints = won ? 25 : -20;
        
        // Performance modifier
        PlayerMatchPerformance performance = matchResult.getPlayerPerformances().get(currentRank.getPlayerId());
        float performanceMultiplier = calculatePerformanceMultiplier(performance, won);
        
        // Streak modifier
        float streakMultiplier = calculateStreakMultiplier(currentRank, won);
        
        // Placement modifier
        float placementMultiplier = rating.isInPlacement() ? 2.0f : 1.0f;
        
        // Calculate final points
        int finalPoints = Math.round(basePoints * performanceMultiplier * streakMultiplier * placementMultiplier);
        
        // Clamp to reasonable range
        return Math.max(-50, Math.min(50, finalPoints));
    }
    
    /**
     * Calculate performance multiplier for rank points
     */
    private float calculatePerformanceMultiplier(PlayerMatchPerformance performance, boolean won) {
        if (performance == null) {
            return 1.0f;
        }
        
        float multiplier = 1.0f;
        
        // K/D ratio factor
        float kdRatio = performance.getKDRatio();
        if (won) {
            multiplier += Math.min(0.5f, (kdRatio - 1.0f) * 0.3f);
        } else {
            // Reduce point loss for good performance in a loss
            multiplier -= Math.min(0.3f, Math.max(0.0f, (kdRatio - 0.5f) * 0.2f));
        }
        
        // MVP bonus/penalty
        if (performance.isMvp()) {
            multiplier += won ? 0.2f : -0.1f;
        }
        
        return Math.max(0.5f, Math.min(2.0f, multiplier));
    }
    
    /**
     * Calculate streak multiplier for rank points
     */
    private float calculateStreakMultiplier(PlayerRank currentRank, boolean won) {
        if (won) {
            int winStreak = currentRank.getWinStreak();
            return 1.0f + Math.min(0.3f, winStreak * 0.05f);
        } else {
            int lossStreak = currentRank.getLossStreak();
            return Math.max(0.7f, 1.0f - (lossStreak * 0.05f));
        }
    }
    
    /**
     * Check if player should be promoted
     */
    private PlayerRank checkForPromotion(PlayerRank currentRank, PlayerRating rating) {
        RankTier currentTier = currentRank.getRankTier();
        RankTier targetTier = calculateRankTier(rating);
        
        // Check if HBR suggests promotion
        if (targetTier.ordinal() > currentTier.ordinal()) {
            // Check promotion requirements
            if (currentRank.getWinStreak() >= config.getPromotionWinStreak() ||
                currentRank.getRankPoints() >= getPromotionThreshold(currentTier)) {
                
                logManager.info("RankingSystem", "Player promoted",
                               "playerId", currentRank.getPlayerId(),
                               "fromTier", currentTier,
                               "toTier", targetTier,
                               "winStreak", currentRank.getWinStreak(),
                               "rankPoints", currentRank.getRankPoints());
                
                return currentRank.withPromotion(targetTier);
            }
        }
        
        return currentRank;
    }
    
    /**
     * Check if player should be demoted
     */
    private PlayerRank checkForDemotion(PlayerRank currentRank, PlayerRating rating) {
        RankTier currentTier = currentRank.getRankTier();
        RankTier targetTier = calculateRankTier(rating);
        
        // Check if HBR suggests demotion
        if (targetTier.ordinal() < currentTier.ordinal()) {
            // Check demotion requirements (with protection)
            if (!config.isEnableRankProtection() || 
                currentRank.getLossStreak() >= config.getDemotionLossStreak() ||
                currentRank.getRankPoints() <= getDemotionThreshold(currentTier)) {
                
                logManager.info("RankingSystem", "Player demoted",
                               "playerId", currentRank.getPlayerId(),
                               "fromTier", currentTier,
                               "toTier", targetTier,
                               "lossStreak", currentRank.getLossStreak(),
                               "rankPoints", currentRank.getRankPoints());
                
                return currentRank.withDemotion(targetTier);
            }
        }
        
        return currentRank;
    }
    
    /**
     * Calculate rank tier from HBR rating
     */
    private RankTier calculateRankTier(PlayerRating rating) {
        return RankTier.fromRating(rating.getRating());
    }
    
    /**
     * Get promotion threshold for a rank tier
     */
    private int getPromotionThreshold(RankTier tier) {
        return 100; // Standard promotion threshold
    }
    
    /**
     * Get demotion threshold for a rank tier
     */
    private int getDemotionThreshold(RankTier tier) {
        return 0; // Demote at 0 points
    }
    
    /**
     * Check if player can join ranked queue
     */
    public boolean canPlayerJoinRankedQueue(PlayerRank rank) {
        // Check if player is banned or has penalties
        // Check if player meets minimum requirements
        return rank != null && !rank.isInPlacement();
    }
    
    /**
     * Apply rank decay for inactive players
     */
    public void applyRankDecay() {
        if (!config.isEnableRankDecay()) {
            return;
        }
        
        logManager.info("RankingSystem", "Applying rank decay to inactive players");
        
        long currentTime = System.currentTimeMillis();
        long decayPeriod = config.getRankDecayPeriodMs();
        float decayAmount = config.getRankDecayAmount();
        
        int playersAffected = 0;
        
        for (Map.Entry<Integer, PlayerRank> entry : playerRanks.entrySet()) {
            PlayerRank rank = entry.getValue();
            
            // Check if player is inactive
            if (currentTime - rank.getLastPlayed() > decayPeriod) {
                // Apply decay
                int pointsLost = Math.round(rank.getRankPoints() * decayAmount);
                PlayerRank decayedRank = rank.withRankDecay(pointsLost);
                
                entry.setValue(decayedRank);
                playersAffected++;
                
                logManager.debug("RankingSystem", "Applied rank decay",
                               "playerId", rank.getPlayerId(),
                               "pointsLost", pointsLost,
                               "newPoints", decayedRank.getRankPoints());
            }
        }
        
        logManager.info("RankingSystem", "Rank decay applied",
                       "playersAffected", playersAffected);
    }
    
    /**
     * Get leaderboard for a specific type
     */
    public List<PlayerRank> getLeaderboard(LeaderboardType type, int limit) {
        return leaderboardManager.getLeaderboard(type, limit);
    }
    
    /**
     * Get player's leaderboard position
     */
    public int getPlayerLeaderboardPosition(int playerId, LeaderboardType type) {
        return leaderboardManager.getPlayerPosition(playerId, type);
    }
    
    /**
     * Start new season
     */
    public void startNewSeason() {
        seasonManager.startNewSeason();
        
        // Reset all player ranks for new season
        for (Map.Entry<Integer, PlayerRank> entry : playerRanks.entrySet()) {
            PlayerRank currentRank = entry.getValue();
            PlayerRank resetRank = currentRank.withSeasonReset(seasonManager.getCurrentSeason());
            entry.setValue(resetRank);
        }
        
        // Clear leaderboards
        leaderboardManager.clearLeaderboards();
        
        logManager.info("RankingSystem", "New season started",
                       "season", seasonManager.getCurrentSeason(),
                       "playersReset", playerRanks.size());
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        logManager.info("RankingSystem", "Cleaning up ranking system");
        
        try {
            playerRanks.clear();
            
            if (seasonManager != null) seasonManager.cleanup();
            if (leaderboardManager != null) leaderboardManager.cleanup();
            
            initialized = false;
            
            logManager.info("RankingSystem", "Ranking system cleanup complete");
            
        } catch (Exception e) {
            logManager.error("RankingSystem", "Error during cleanup", e);
        }
    }
    
    // Getters
    public boolean isInitialized() { return initialized; }
    public int getPlayerCount() { return playerRanks.size(); }
    public SeasonManager getSeasonManager() { return seasonManager; }
    public LeaderboardManager getLeaderboardManager() { return leaderboardManager; }
    
    /**
     * Leaderboard types
     */
    public enum LeaderboardType {
        GLOBAL("Global"),
        REGIONAL("Regional"),
        SEASONAL("Seasonal"),
        CLAN("Clan");
        
        private final String displayName;
        
        LeaderboardType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
}