package fps.matchmaking.formation;

import engine.logging.LogManager;
import fps.matchmaking.MatchmakingConfiguration.MatchFormationConfiguration;
import fps.matchmaking.MatchmakingTypes.*;
import fps.matchmaking.queue.QueueEntry;
import fps.matchmaking.rating.PlayerRating;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Advanced match formation engine with team balancing algorithms
 */
public class MatchFormationEngine {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final MatchFormationConfiguration config;
    private final TeamBalancer teamBalancer;
    private final MatchQualityCalculator qualityCalculator;
    
    // Match formation statistics
    private volatile long totalMatchesFormed = 0;
    private volatile double averageMatchQuality = 0.0;
    private volatile long averageFormationTimeMs = 0;
    
    public MatchFormationEngine(MatchFormationConfiguration config) {
        this.config = config;
        this.teamBalancer = new TeamBalancer(config);
        this.qualityCalculator = new MatchQualityCalculator(config);
    }
    
    /**
     * Attempt to form a match from available queue entries
     */
    public MatchFormationResult attemptMatchFormation(List<QueueEntry> availableEntries, 
                                                     GameMode gameMode) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Check if we have enough players
            int requiredPlayers = getRequiredPlayersForMode(gameMode);
            if (availableEntries.size() < requiredPlayers) {
                return MatchFormationResult.failure("Insufficient players", 
                                                   availableEntries.size(), requiredPlayers);
            }
            
            // Filter compatible entries
            List<QueueEntry> compatibleEntries = filterCompatibleEntries(availableEntries, gameMode);
            if (compatibleEntries.size() < requiredPlayers) {
                return MatchFormationResult.failure("Insufficient compatible players",
                                                   compatibleEntries.size(), requiredPlayers);
            }
            
            // Find best match combination
            MatchCombination bestMatch = findBestMatchCombination(compatibleEntries, 
                                                                gameMode, requiredPlayers);
            
            if (bestMatch == null || bestMatch.getQuality() < config.getMinMatchQuality()) {
                return MatchFormationResult.failure("No suitable match found",
                                                   compatibleEntries.size(), requiredPlayers);
            }
            
            // Form teams
            TeamFormationResult teamResult = teamBalancer.formBalancedTeams(bestMatch.getPlayers(), 
                                                                           gameMode);
            
            if (!teamResult.isSuccess()) {
                return MatchFormationResult.failure("Team balancing failed",
                                                   bestMatch.getPlayers().size(), requiredPlayers);
            }
            
            // Create match
            MatchInfo matchInfo = createMatchInfo(teamResult, gameMode, bestMatch.getQuality());
            
            // Update statistics
            updateStatistics(bestMatch.getQuality(), System.currentTimeMillis() - startTime);
            
            logManager.info("MatchFormationEngine", "Match formed successfully",
                           "gameMode", gameMode,
                           "players", matchInfo.getAllPlayers().size(),
                           "quality", bestMatch.getQuality(),
                           "formationTimeMs", System.currentTimeMillis() - startTime);
            
            return MatchFormationResult.success(matchInfo, bestMatch.getQuality());
            
        } catch (Exception e) {
            logManager.error("MatchFormationEngine", "Match formation failed", e,
                           "gameMode", gameMode,
                           "availableEntries", availableEntries.size());
            
            return MatchFormationResult.failure("Formation error: " + e.getMessage(),
                                               availableEntries.size(), 
                                               getRequiredPlayersForMode(gameMode));
        }
    }
    
    /**
     * Filter entries for compatibility
     */
    private List<QueueEntry> filterCompatibleEntries(List<QueueEntry> entries, GameMode gameMode) {
        return entries.stream()
                     .filter(entry -> isEntryCompatible(entry, gameMode))
                     .collect(Collectors.toList());
    }
    
    /**
     * Check if entry is compatible with game mode
     */
    private boolean isEntryCompatible(QueueEntry entry, GameMode gameMode) {
        PlayerPreferences prefs = entry.getPreferences();
        
        // Check game mode preference
        if (!prefs.getPreferredGameModes().isEmpty() && 
            !prefs.getPreferredGameModes().contains(gameMode)) {
            return false;
        }
        
        // Check rating range compatibility
        PlayerRating rating = entry.getRating();
        if (rating.getRating() < config.getMinRatingForMode(gameMode) ||
            rating.getRating() > config.getMaxRatingForMode(gameMode)) {
            return false;
        }
        
        // Check region compatibility
        if (prefs.getRegion() != null && !isRegionCompatible(prefs.getRegion(), entry.getRegion())) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Check region compatibility
     */
    private boolean isRegionCompatible(Region preferred, Region actual) {
        if (preferred == actual) return true;
        
        // Allow cross-region matching with higher ping tolerance
        return config.isAllowCrossRegionMatching();
    }
    
    /**
     * Find the best match combination from available players
     */
    private MatchCombination findBestMatchCombination(List<QueueEntry> entries, 
                                                    GameMode gameMode, int requiredPlayers) {
        
        // Sort by priority (wait time, rating uncertainty, premium status)
        List<QueueEntry> sortedEntries = entries.stream()
                                               .sorted(this::compareEntryPriority)
                                               .collect(Collectors.toList());
        
        MatchCombination bestCombination = null;
        double bestQuality = 0.0;
        
        // Try different combinations starting with highest priority players
        int maxCombinations = Math.min(config.getMaxCombinationsToTest(), 
                                     factorial(Math.min(entries.size(), requiredPlayers + 5)));
        
        for (int attempt = 0; attempt < maxCombinations; attempt++) {
            List<QueueEntry> candidatePlayers = selectCandidatePlayers(sortedEntries, 
                                                                      requiredPlayers, attempt);
            
            if (candidatePlayers.size() < requiredPlayers) continue;
            
            double quality = qualityCalculator.calculateMatchQuality(candidatePlayers, gameMode);
            
            if (quality > bestQuality && quality >= config.getMinMatchQuality()) {
                bestQuality = quality;
                bestCombination = new MatchCombination(candidatePlayers, quality);
                
                // If we found a very high quality match, use it
                if (quality >= config.getTargetMatchQuality()) {
                    break;
                }
            }
        }
        
        return bestCombination;
    }
    
    /**
     * Select candidate players for a match attempt
     */
    private List<QueueEntry> selectCandidatePlayers(List<QueueEntry> sortedEntries, 
                                                   int requiredPlayers, int attempt) {
        List<QueueEntry> candidates = new ArrayList<>();
        
        if (attempt == 0) {
            // First attempt: take highest priority players
            candidates.addAll(sortedEntries.subList(0, Math.min(requiredPlayers, sortedEntries.size())));
        } else {
            // Subsequent attempts: introduce some randomization for variety
            List<QueueEntry> pool = new ArrayList<>(sortedEntries.subList(0, 
                Math.min(requiredPlayers + 5, sortedEntries.size())));
            
            Collections.shuffle(pool, ThreadLocalRandom.current());
            candidates.addAll(pool.subList(0, Math.min(requiredPlayers, pool.size())));
        }
        
        return candidates;
    }
    
    /**
     * Compare queue entries by priority
     */
    private int compareEntryPriority(QueueEntry a, QueueEntry b) {
        // Higher priority = lower number (sorted ascending)
        
        // 1. Premium players first
        if (a.isPremium() != b.isPremium()) {
            return a.isPremium() ? -1 : 1;
        }
        
        // 2. Longer wait time
        long waitTimeDiff = b.getWaitTimeMs() - a.getWaitTimeMs();
        if (Math.abs(waitTimeDiff) > 30000) { // 30 second threshold
            return Long.compare(b.getWaitTimeMs(), a.getWaitTimeMs());
        }
        
        // 3. Higher rating uncertainty (new/returning players)
        double uncertaintyDiff = b.getRating().getUncertainty() - a.getRating().getUncertainty();
        if (Math.abs(uncertaintyDiff) > 50) {
            return Double.compare(b.getRating().getUncertainty(), a.getRating().getUncertainty());
        }
        
        // 4. Similar rating (for better matches)
        return Double.compare(Math.abs(a.getRating().getRating() - 1500), 
                            Math.abs(b.getRating().getRating() - 1500));
    }
    
    /**
     * Create match info from team formation result
     */
    private MatchInfo createMatchInfo(TeamFormationResult teamResult, GameMode gameMode, double quality) {
        String matchId = generateMatchId();
        
        return new MatchInfo(
            matchId,
            gameMode,
            teamResult.getTeamA(),
            teamResult.getTeamB(),
            quality,
            teamResult.getPredictedWinProbability(),
            System.currentTimeMillis()
        );
    }
    
    /**
     * Generate unique match ID
     */
    private String generateMatchId() {
        return "MATCH_" + System.currentTimeMillis() + "_" + 
               ThreadLocalRandom.current().nextInt(1000, 9999);
    }
    
    /**
     * Get required players for game mode
     */
    private int getRequiredPlayersForMode(GameMode gameMode) {
        switch (gameMode) {
            case TEAM_DEATHMATCH:
            case SEARCH_AND_DESTROY:
            case CAPTURE_THE_FLAG:
            case KING_OF_THE_HILL:
                return 10; // 5v5
            case CLAN_WARFARE:
                return 12; // 6v6
            default:
                return 10;
        }
    }
    
    /**
     * Update formation statistics
     */
    private void updateStatistics(double matchQuality, long formationTimeMs) {
        totalMatchesFormed++;
        
        // Update rolling average
        averageMatchQuality = ((averageMatchQuality * (totalMatchesFormed - 1)) + matchQuality) / totalMatchesFormed;
        averageFormationTimeMs = ((averageFormationTimeMs * (totalMatchesFormed - 1)) + formationTimeMs) / totalMatchesFormed;
    }
    
    /**
     * Calculate factorial (with limit for performance)
     */
    private int factorial(int n) {
        if (n <= 1) return 1;
        if (n > 10) return 3628800; // 10! - reasonable limit
        
        int result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }
    
    /**
     * Get formation statistics
     */
    public FormationStatistics getStatistics() {
        return new FormationStatistics(
            totalMatchesFormed,
            averageMatchQuality,
            averageFormationTimeMs
        );
    }
    
    /**
     * Reset statistics
     */
    public void resetStatistics() {
        totalMatchesFormed = 0;
        averageMatchQuality = 0.0;
        averageFormationTimeMs = 0;
        
        logManager.info("MatchFormationEngine", "Statistics reset");
    }
    
    // Getters
    public MatchFormationConfiguration getConfig() { return config; }
    public TeamBalancer getTeamBalancer() { return teamBalancer; }
    public MatchQualityCalculator getQualityCalculator() { return qualityCalculator; }
    
    /**
     * Match combination data class
     */
    private static class MatchCombination {
        private final List<QueueEntry> players;
        private final double quality;
        
        public MatchCombination(List<QueueEntry> players, double quality) {
            this.players = new ArrayList<>(players);
            this.quality = quality;
        }
        
        public List<QueueEntry> getPlayers() { return players; }
        public double getQuality() { return quality; }
    }
    
    /**
     * Formation statistics data class
     */
    public static class FormationStatistics {
        private final long totalMatches;
        private final double averageQuality;
        private final long averageFormationTime;
        
        public FormationStatistics(long totalMatches, double averageQuality, long averageFormationTime) {
            this.totalMatches = totalMatches;
            this.averageQuality = averageQuality;
            this.averageFormationTime = averageFormationTime;
        }
        
        public long getTotalMatches() { return totalMatches; }
        public double getAverageQuality() { return averageQuality; }
        public long getAverageFormationTime() { return averageFormationTime; }
        
        @Override
        public String toString() {
            return String.format("Formation Stats: %d matches, %.2f avg quality, %dms avg time",
                               totalMatches, averageQuality, averageFormationTime);
        }
    }
}