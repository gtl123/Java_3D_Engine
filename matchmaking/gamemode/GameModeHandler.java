package fps.matchmaking.gamemode;

import fps.matchmaking.MatchmakingTypes.*;

import java.util.List;
import java.util.Map;

/**
 * Interface for game mode specific logic and rules
 */
public interface GameModeHandler {
    
    /**
     * Get game mode configuration
     */
    GameModeConfig getConfig();
    
    /**
     * Validate team setup for this game mode
     */
    boolean validateTeamSetup(List<String> teamA, List<String> teamB);
    
    /**
     * Calculate rating changes based on match result
     */
    Map<String, Double> calculateRatingChanges(MatchResult matchResult);
    
    /**
     * Get recommended team size
     */
    int getRecommendedTeamSize();
    
    /**
     * Get match duration in milliseconds
     */
    long getMatchDurationMs();
    
    /**
     * Check if this game mode supports ranked play
     */
    boolean supportsRankedPlay();
    
    /**
     * Get minimum rating required for this game mode
     */
    double getMinimumRating();
    
    /**
     * Get maximum rating difference allowed in matches
     */
    double getMaxRatingDifference();
    
    /**
     * Calculate performance multiplier based on game mode specific metrics
     */
    double calculatePerformanceMultiplier(PlayerMatchResult playerResult);
    
    /**
     * Get role importance weights for this game mode
     */
    Map<PlayerRole, Double> getRoleWeights();
    
    /**
     * Validate match result for this game mode
     */
    boolean validateMatchResult(MatchResult matchResult);
    
    /**
     * Get game mode specific objectives
     */
    List<String> getObjectives();
    
    /**
     * Calculate objective completion bonus
     */
    double calculateObjectiveBonus(PlayerMatchResult playerResult);
}