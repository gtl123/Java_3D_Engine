package fps.matchmaking.gamemode;

import engine.logging.LogManager;
import fps.matchmaking.MatchmakingConfiguration;
import fps.matchmaking.MatchmakingTypes.*;

import java.util.*;

/**
 * Base implementation for game mode handlers with common functionality
 */
public abstract class BaseGameModeHandler implements GameModeHandler {
    
    protected static final LogManager logManager = LogManager.getInstance();
    
    protected final MatchmakingConfiguration config;
    protected final GameModeConfig gameModeConfig;
    
    public BaseGameModeHandler(MatchmakingConfiguration config, GameModeConfig gameModeConfig) {
        this.config = config;
        this.gameModeConfig = gameModeConfig;
    }
    
    @Override
    public GameModeConfig getConfig() {
        return gameModeConfig;
    }
    
    @Override
    public boolean validateTeamSetup(List<String> teamA, List<String> teamB) {
        // Basic validation
        if (teamA == null || teamB == null) return false;
        if (teamA.isEmpty() || teamB.isEmpty()) return false;
        
        int recommendedSize = getRecommendedTeamSize();
        if (teamA.size() != recommendedSize || teamB.size() != recommendedSize) {
            return false;
        }
        
        // Check for duplicate players
        Set<String> allPlayers = new HashSet<>(teamA);
        allPlayers.addAll(teamB);
        return allPlayers.size() == (teamA.size() + teamB.size());
    }
    
    @Override
    public Map<String, Double> calculateRatingChanges(MatchResult matchResult) {
        Map<String, Double> ratingChanges = new HashMap<>();
        
        for (Map.Entry<String, PlayerMatchResult> entry : matchResult.getPlayerResults().entrySet()) {
            String playerId = entry.getKey();
            PlayerMatchResult playerResult = entry.getValue();
            
            // Base rating change calculation
            double baseChange = calculateBaseRatingChange(playerResult, matchResult);
            
            // Apply performance multiplier
            double performanceMultiplier = calculatePerformanceMultiplier(playerResult);
            
            // Apply objective bonus
            double objectiveBonus = calculateObjectiveBonus(playerResult);
            
            // Calculate final change
            double finalChange = baseChange * performanceMultiplier + objectiveBonus;
            
            // Apply game mode specific adjustments
            finalChange = applyGameModeAdjustments(finalChange, playerResult, matchResult);
            
            ratingChanges.put(playerId, finalChange);
        }
        
        return ratingChanges;
    }
    
    /**
     * Calculate base rating change using Elo-like system
     */
    protected double calculateBaseRatingChange(PlayerMatchResult playerResult, MatchResult matchResult) {
        boolean won = playerResult.isWinner();
        double expectedWinProbability = matchResult.getPredictedWinProbability();
        
        // Adjust expected probability based on team
        if (!playerResult.isWinner()) {
            expectedWinProbability = 1.0 - expectedWinProbability;
        }
        
        // K-factor based on game mode
        double kFactor = getKFactor();
        
        // Calculate change
        double actualResult = won ? 1.0 : 0.0;
        return kFactor * (actualResult - expectedWinProbability);
    }
    
    /**
     * Get K-factor for rating calculations
     */
    protected abstract double getKFactor();
    
    /**
     * Apply game mode specific adjustments to rating change
     */
    protected double applyGameModeAdjustments(double baseChange, PlayerMatchResult playerResult, 
                                            MatchResult matchResult) {
        // Default implementation - can be overridden by specific game modes
        return baseChange;
    }
    
    @Override
    public boolean validateMatchResult(MatchResult matchResult) {
        if (matchResult == null) return false;
        if (matchResult.getPlayerResults().isEmpty()) return false;
        
        // Check team sizes
        long teamACount = matchResult.getPlayerResults().values().stream()
                                   .filter(result -> result.getTeam() == 0)
                                   .count();
        long teamBCount = matchResult.getPlayerResults().values().stream()
                                   .filter(result -> result.getTeam() == 1)
                                   .count();
        
        int expectedTeamSize = getRecommendedTeamSize();
        return teamACount == expectedTeamSize && teamBCount == expectedTeamSize;
    }
    
    @Override
    public double getMinimumRating() {
        return gameModeConfig.getMinRating();
    }
    
    @Override
    public double getMaxRatingDifference() {
        return gameModeConfig.getMaxRatingDifference();
    }
    
    @Override
    public long getMatchDurationMs() {
        return gameModeConfig.getMatchDurationMs();
    }
    
    @Override
    public boolean supportsRankedPlay() {
        return gameModeConfig.isRankedSupported();
    }
    
    /**
     * Calculate performance score based on common FPS metrics
     */
    protected double calculatePerformanceScore(PlayerMatchResult playerResult) {
        double kd = playerResult.getKills() > 0 ? 
                   (double) playerResult.getKills() / Math.max(1, playerResult.getDeaths()) : 0.0;
        
        double damage = playerResult.getDamageDealt();
        double accuracy = playerResult.getAccuracy();
        double objectiveScore = playerResult.getObjectiveScore();
        
        // Normalize and weight the metrics
        double kdScore = Math.min(kd / 2.0, 1.0) * 0.3; // Cap at 2.0 K/D
        double damageScore = Math.min(damage / 3000.0, 1.0) * 0.25; // Cap at 3000 damage
        double accuracyScore = accuracy * 0.2;
        double objectiveScoreNorm = Math.min(objectiveScore / 1000.0, 1.0) * 0.25;
        
        return kdScore + damageScore + accuracyScore + objectiveScoreNorm;
    }
    
    /**
     * Get default role weights (can be overridden by specific game modes)
     */
    @Override
    public Map<PlayerRole, Double> getRoleWeights() {
        Map<PlayerRole, Double> weights = new HashMap<>();
        weights.put(PlayerRole.ASSAULT, 1.0);
        weights.put(PlayerRole.SUPPORT, 1.0);
        weights.put(PlayerRole.SNIPER, 1.0);
        weights.put(PlayerRole.TANK, 1.0);
        return weights;
    }
    
    /**
     * Get default objectives (can be overridden by specific game modes)
     */
    @Override
    public List<String> getObjectives() {
        return Arrays.asList("Eliminate enemies", "Support teammates", "Win the match");
    }
    
    /**
     * Default objective bonus calculation
     */
    @Override
    public double calculateObjectiveBonus(PlayerMatchResult playerResult) {
        // Base objective bonus based on objective score
        double objectiveScore = playerResult.getObjectiveScore();
        return Math.min(objectiveScore / 1000.0, 0.5) * 10.0; // Max 5 point bonus
    }
    
    /**
     * Default performance multiplier
     */
    @Override
    public double calculatePerformanceMultiplier(PlayerMatchResult playerResult) {
        double performanceScore = calculatePerformanceScore(playerResult);
        
        // Convert to multiplier (0.5x to 1.5x based on performance)
        return 0.5 + performanceScore;
    }
}