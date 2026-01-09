package fps.matchmaking.rating;

import engine.logging.LogManager;
import fps.matchmaking.MatchmakingConfiguration.HBRConfiguration;
import fps.matchmaking.MatchmakingTypes.MatchmakingPlayer;
import fps.matchmaking.MatchmakingTypes.MatchResult;
import fps.matchmaking.MatchmakingTypes.PlayerMatchPerformance;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;

/**
 * Hidden Battle Rating system for skill-based matchmaking.
 * Implements a modified Elo rating system with uncertainty and performance factors.
 */
public class HiddenBattleRatingSystem {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final HBRConfiguration config;
    private final Map<Integer, PlayerRating> playerRatings = new ConcurrentHashMap<>();
    private final RatingCalculator ratingCalculator;
    private final UncertaintyManager uncertaintyManager;
    private final PerformanceAnalyzer performanceAnalyzer;
    
    private volatile boolean initialized = false;
    
    public HiddenBattleRatingSystem(HBRConfiguration config) {
        this.config = config;
        this.ratingCalculator = new RatingCalculator(config);
        this.uncertaintyManager = new UncertaintyManager(config);
        this.performanceAnalyzer = new PerformanceAnalyzer(config);
        
        logManager.info("HiddenBattleRatingSystem", "HBR system created");
    }
    
    /**
     * Initialize the HBR system
     */
    public void initialize() throws Exception {
        if (initialized) {
            return;
        }
        
        logManager.info("HiddenBattleRatingSystem", "Initializing HBR system");
        
        try {
            ratingCalculator.initialize();
            uncertaintyManager.initialize();
            performanceAnalyzer.initialize();
            
            initialized = true;
            
            logManager.info("HiddenBattleRatingSystem", "HBR system initialization complete");
            
        } catch (Exception e) {
            logManager.error("HiddenBattleRatingSystem", "Failed to initialize HBR system", e);
            throw e;
        }
    }
    
    /**
     * Get player rating, creating a new one if it doesn't exist
     */
    public PlayerRating getPlayerRating(int playerId) {
        return playerRatings.computeIfAbsent(playerId, id -> createNewPlayerRating(id));
    }
    
    /**
     * Create a new player rating with initial values
     */
    private PlayerRating createNewPlayerRating(int playerId) {
        PlayerRating rating = new PlayerRating(
            playerId,
            config.getInitialRating(),
            config.getInitialUncertainty(),
            0, // games played
            System.currentTimeMillis(), // last updated
            true // is placement
        );
        
        logManager.info("HiddenBattleRatingSystem", "Created new player rating",
                       "playerId", playerId,
                       "initialRating", config.getInitialRating(),
                       "initialUncertainty", config.getInitialUncertainty());
        
        return rating;
    }
    
    /**
     * Update player ratings based on match results
     */
    public void updateRatingsFromMatch(List<MatchmakingPlayer> players, MatchResult matchResult) {
        if (!initialized) {
            logManager.warn("HiddenBattleRatingSystem", "Attempted to update ratings before initialization");
            return;
        }
        
        try {
            // Calculate team ratings
            float team1Rating = calculateTeamRating(matchResult.getWinners());
            float team2Rating = calculateTeamRating(matchResult.getLosers());
            
            // Update winner ratings
            for (MatchmakingPlayer player : matchResult.getWinners()) {
                updatePlayerRating(player, team1Rating, team2Rating, 1.0f, matchResult);
            }
            
            // Update loser ratings
            for (MatchmakingPlayer player : matchResult.getLosers()) {
                updatePlayerRating(player, team2Rating, team1Rating, 0.0f, matchResult);
            }
            
            logManager.info("HiddenBattleRatingSystem", "Updated ratings from match",
                           "matchId", matchResult.getMatchId(),
                           "playerCount", players.size());
            
        } catch (Exception e) {
            logManager.error("HiddenBattleRatingSystem", "Error updating ratings from match", e,
                           "matchId", matchResult.getMatchId());
        }
    }
    
    /**
     * Update individual player rating
     */
    private void updatePlayerRating(MatchmakingPlayer player, float teamRating, float opponentRating, 
                                  float score, MatchResult matchResult) {
        PlayerRating currentRating = getPlayerRating(player.getPlayerId());
        PlayerMatchPerformance performance = matchResult.getPlayerPerformances().get(player.getPlayerId());
        
        // Calculate performance multiplier
        float performanceMultiplier = performanceAnalyzer.calculatePerformanceMultiplier(performance);
        
        // Calculate new rating
        RatingChange change = ratingCalculator.calculateRatingChange(
            currentRating, teamRating, opponentRating, score, performanceMultiplier
        );
        
        // Update uncertainty
        float newUncertainty = uncertaintyManager.updateUncertainty(currentRating, change);
        
        // Apply changes
        PlayerRating newRating = new PlayerRating(
            player.getPlayerId(),
            currentRating.getRating() + change.getRatingDelta(),
            newUncertainty,
            currentRating.getGamesPlayed() + 1,
            System.currentTimeMillis(),
            currentRating.getGamesPlayed() < config.getPlacementMatchCount()
        );
        
        playerRatings.put(player.getPlayerId(), newRating);
        
        logManager.debug("HiddenBattleRatingSystem", "Updated player rating",
                        "playerId", player.getPlayerId(),
                        "oldRating", currentRating.getRating(),
                        "newRating", newRating.getRating(),
                        "ratingChange", change.getRatingDelta(),
                        "newUncertainty", newUncertainty,
                        "gamesPlayed", newRating.getGamesPlayed());
    }
    
    /**
     * Calculate average team rating
     */
    public float calculateTeamRating(List<MatchmakingPlayer> team) {
        if (team.isEmpty()) {
            return config.getInitialRating();
        }
        
        float totalRating = 0.0f;
        for (MatchmakingPlayer player : team) {
            totalRating += getPlayerRating(player.getPlayerId()).getRating();
        }
        
        return totalRating / team.size();
    }
    
    /**
     * Calculate rating difference between two players
     */
    public float calculateRatingDifference(int playerId1, int playerId2) {
        PlayerRating rating1 = getPlayerRating(playerId1);
        PlayerRating rating2 = getPlayerRating(playerId2);
        
        return Math.abs(rating1.getRating() - rating2.getRating());
    }
    
    /**
     * Check if two players are within acceptable rating range
     */
    public boolean arePlayersCompatible(int playerId1, int playerId2, float maxDifference) {
        return calculateRatingDifference(playerId1, playerId2) <= maxDifference;
    }
    
    /**
     * Apply seasonal decay to all player ratings
     */
    public void applySeasonalDecay() {
        if (!config.isEnableSeasonalDecay()) {
            return;
        }
        
        logManager.info("HiddenBattleRatingSystem", "Applying seasonal decay to all players");
        
        int playersAffected = 0;
        for (Map.Entry<Integer, PlayerRating> entry : playerRatings.entrySet()) {
            PlayerRating currentRating = entry.getValue();
            
            // Apply decay towards the mean
            float meanRating = config.getInitialRating();
            float decayedRating = currentRating.getRating() + 
                (meanRating - currentRating.getRating()) * (1.0f - config.getSeasonalDecayRate());
            
            // Increase uncertainty slightly
            float newUncertainty = Math.min(
                currentRating.getUncertainty() * 1.1f,
                config.getInitialUncertainty() * 0.5f
            );
            
            PlayerRating newRating = new PlayerRating(
                currentRating.getPlayerId(),
                decayedRating,
                newUncertainty,
                currentRating.getGamesPlayed(),
                System.currentTimeMillis(),
                currentRating.isInPlacement()
            );
            
            entry.setValue(newRating);
            playersAffected++;
        }
        
        logManager.info("HiddenBattleRatingSystem", "Seasonal decay applied",
                       "playersAffected", playersAffected);
    }
    
    /**
     * Get rating statistics
     */
    public RatingStatistics getRatingStatistics() {
        if (playerRatings.isEmpty()) {
            return new RatingStatistics(0, 0.0f, 0.0f, 0.0f, 0.0f);
        }
        
        float totalRating = 0.0f;
        float minRating = Float.MAX_VALUE;
        float maxRating = Float.MIN_VALUE;
        
        for (PlayerRating rating : playerRatings.values()) {
            float r = rating.getRating();
            totalRating += r;
            minRating = Math.min(minRating, r);
            maxRating = Math.max(maxRating, r);
        }
        
        float averageRating = totalRating / playerRatings.size();
        
        // Calculate standard deviation
        float sumSquaredDifferences = 0.0f;
        for (PlayerRating rating : playerRatings.values()) {
            float diff = rating.getRating() - averageRating;
            sumSquaredDifferences += diff * diff;
        }
        float standardDeviation = (float) Math.sqrt(sumSquaredDifferences / playerRatings.size());
        
        return new RatingStatistics(
            playerRatings.size(),
            averageRating,
            standardDeviation,
            minRating,
            maxRating
        );
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        logManager.info("HiddenBattleRatingSystem", "Cleaning up HBR system");
        
        try {
            playerRatings.clear();
            
            if (ratingCalculator != null) ratingCalculator.cleanup();
            if (uncertaintyManager != null) uncertaintyManager.cleanup();
            if (performanceAnalyzer != null) performanceAnalyzer.cleanup();
            
            initialized = false;
            
            logManager.info("HiddenBattleRatingSystem", "HBR system cleanup complete");
            
        } catch (Exception e) {
            logManager.error("HiddenBattleRatingSystem", "Error during cleanup", e);
        }
    }
    
    // Getters
    public boolean isInitialized() { return initialized; }
    public int getPlayerCount() { return playerRatings.size(); }
    public HBRConfiguration getConfig() { return config; }
    
    /**
     * Rating statistics data class
     */
    public static class RatingStatistics {
        private final int playerCount;
        private final float averageRating;
        private final float standardDeviation;
        private final float minRating;
        private final float maxRating;
        
        public RatingStatistics(int playerCount, float averageRating, float standardDeviation,
                              float minRating, float maxRating) {
            this.playerCount = playerCount;
            this.averageRating = averageRating;
            this.standardDeviation = standardDeviation;
            this.minRating = minRating;
            this.maxRating = maxRating;
        }
        
        // Getters
        public int getPlayerCount() { return playerCount; }
        public float getAverageRating() { return averageRating; }
        public float getStandardDeviation() { return standardDeviation; }
        public float getMinRating() { return minRating; }
        public float getMaxRating() { return maxRating; }
    }
}