package fps.matchmaking.gamemode;

import fps.matchmaking.MatchmakingConfiguration;
import fps.matchmaking.MatchmakingTypes.*;

import java.util.*;

/**
 * King of the Hill game mode handler
 * Focus: Area control and territorial domination gameplay
 */
public class KingOfTheHillHandler extends BaseGameModeHandler {
    
    private static final int TEAM_SIZE = 5;
    private static final long MATCH_DURATION_MS = 12 * 60 * 1000; // 12 minutes
    private static final double K_FACTOR = 33.0;
    
    public KingOfTheHillHandler(MatchmakingConfiguration config) {
        super(config, createGameModeConfig());
    }
    
    private static GameModeConfig createGameModeConfig() {
        return new GameModeConfig(
            GameMode.KING_OF_THE_HILL,
            TEAM_SIZE,
            MATCH_DURATION_MS,
            true, // Supports ranked
            850.0, // Min rating
            475.0, // Max rating difference
            Arrays.asList("KOTH_Dust", "KOTH_Mirage", "KOTH_Inferno", "KOTH_Cache", "KOTH_Overpass", "KOTH_Nuke"),
            "Control the hill area to earn points. First team to 100 points or most points when time expires wins."
        );
    }
    
    @Override
    public int getRecommendedTeamSize() {
        return TEAM_SIZE;
    }
    
    @Override
    protected double getKFactor() {
        return K_FACTOR;
    }
    
    @Override
    public Map<PlayerRole, Double> getRoleWeights() {
        Map<PlayerRole, Double> weights = new HashMap<>();
        // KOTH role importance for area control
        weights.put(PlayerRole.ASSAULT, 1.2); // Pushing onto the hill
        weights.put(PlayerRole.SUPPORT, 1.1); // Utility and team coordination
        weights.put(PlayerRole.SNIPER, 1.0); // Overwatch and picks
        weights.put(PlayerRole.TANK, 1.3); // Critical for holding the hill
        return weights;
    }
    
    @Override
    public List<String> getObjectives() {
        return Arrays.asList(
            "Control the hill area",
            "Defend captured territory",
            "Contest enemy control",
            "Eliminate enemies on hill",
            "Support hill pushes",
            "Maintain area dominance",
            "Coordinate team rotations"
        );
    }
    
    @Override
    public double calculatePerformanceMultiplier(PlayerMatchResult playerResult) {
        // KOTH balances fragging with objective control
        double kd = playerResult.getKills() > 0 ? 
                   (double) playerResult.getKills() / Math.max(1, playerResult.getDeaths()) : 0.0;
        
        double damage = playerResult.getDamageDealt();
        double accuracy = playerResult.getAccuracy();
        double objectiveScore = playerResult.getObjectiveScore();
        
        // Balanced weighting for area control gameplay
        double kdScore = Math.min(kd / 1.6, 1.0) * 0.3; // 30% weight on K/D
        double damageScore = Math.min(damage / 2400.0, 1.0) * 0.25; // 25% weight on damage
        double accuracyScore = accuracy * 0.15; // 15% weight on accuracy
        double objectiveScoreNorm = Math.min(objectiveScore / 1000.0, 1.0) * 0.3; // 30% weight on objectives
        
        double performanceScore = kdScore + damageScore + accuracyScore + objectiveScoreNorm;
        
        // Convert to multiplier (0.65x to 1.35x)
        return 0.65 + (performanceScore * 0.7);
    }
    
    @Override
    public double calculateObjectiveBonus(PlayerMatchResult playerResult) {
        double bonus = 0.0;
        
        // Hill control time (primary objective)
        long hillControlTime = playerResult.getHillControlTime();
        bonus += Math.min(hillControlTime / 30000.0, 20.0); // Up to 20 points for 10+ minutes control
        
        // Hill captures (taking control)
        int hillCaptures = playerResult.getHillCaptures();
        bonus += hillCaptures * 8.0; // 8 points per capture
        
        // Hill defenses (maintaining control)
        int hillDefenses = playerResult.getHillDefenses();
        bonus += hillDefenses * 6.0; // 6 points per successful defense
        
        // Contested kills (kills while hill is contested)
        int contestedKills = playerResult.getContestedKills();
        bonus += contestedKills * 5.0; // 5 points per contested kill
        
        // Hill assists (helping teammates on hill)
        int hillAssists = playerResult.getHillAssists();
        bonus += hillAssists * 3.0; // 3 points per hill assist
        
        // Area denial (preventing enemy access)
        int areaDenialKills = playerResult.getAreaDenialKills();
        bonus += areaDenialKills * 4.0; // 4 points per area denial kill
        
        // Control point bonus (maintaining presence)
        int controlPointTicks = playerResult.getControlPointTicks();
        bonus += Math.min(controlPointTicks / 10.0, 12.0); // Up to 12 points for consistent presence
        
        return Math.min(bonus, 35.0); // Cap at 35 points
    }
    
    @Override
    protected double applyGameModeAdjustments(double baseChange, PlayerMatchResult playerResult, 
                                            MatchResult matchResult) {
        double adjustedChange = baseChange;
        
        // Score difference adjustment
        if (!playerResult.isWinner()) {
            int teamAScore = getTeamControlScore(matchResult, 0);
            int teamBScore = getTeamControlScore(matchResult, 1);
            int scoreDifference = Math.abs(teamAScore - teamBScore);
            
            // Reduce rating loss for close matches
            if (scoreDifference <= 10) {
                adjustedChange *= 0.7; // 30% reduction for very close games
            } else if (scoreDifference <= 25) {
                adjustedChange *= 0.85; // 15% reduction for close games
            }
        }
        
        // Hill controller bonus
        if (isHillController(playerResult)) {
            adjustedChange = Math.abs(adjustedChange) * 1.25; // 25% bonus for hill controllers
            if (baseChange < 0) adjustedChange = -adjustedChange;
        }
        
        // Area specialist bonus
        if (isAreaSpecialist(playerResult)) {
            adjustedChange = Math.abs(adjustedChange) * 1.2; // 20% bonus for area specialists
            if (baseChange < 0) adjustedChange = -adjustedChange;
        }
        
        // Clutch performer bonus (winning contested situations)
        if (isClutchPerformer(playerResult)) {
            adjustedChange = Math.abs(adjustedChange) * 1.15; // 15% bonus for clutch performers
            if (baseChange < 0) adjustedChange = -adjustedChange;
        }
        
        return adjustedChange;
    }
    
    /**
     * Get team control score (total hill control time)
     */
    private int getTeamControlScore(MatchResult matchResult, int team) {
        return (int) matchResult.getPlayerResults().values().stream()
                .filter(p -> p.getTeam() == team)
                .mapToLong(PlayerMatchResult::getHillControlTime)
                .sum() / 1000; // Convert to seconds
    }
    
    /**
     * Check if player was a hill controller
     */
    private boolean isHillController(PlayerMatchResult playerResult) {
        return playerResult.getHillControlTime() > 180000 && // 3+ minutes control
               playerResult.getHillCaptures() >= 3;
    }
    
    /**
     * Check if player was an area specialist
     */
    private boolean isAreaSpecialist(PlayerMatchResult playerResult) {
        return playerResult.getHillDefenses() >= 4 && 
               playerResult.getAreaDenialKills() >= 6 &&
               playerResult.getContestedKills() >= 5;
    }
    
    /**
     * Check if player was a clutch performer
     */
    private boolean isClutchPerformer(PlayerMatchResult playerResult) {
        return playerResult.getContestedKills() >= 8 && 
               playerResult.getHillCaptures() >= 4 &&
               playerResult.getKills() > playerResult.getDeaths();
    }
    
    /**
     * Calculate area control impact
     */
    private double calculateAreaControlImpact(PlayerMatchResult playerResult) {
        double impact = 0.0;
        
        // Time-based control
        impact += playerResult.getHillControlTime() / 10000.0; // 1 point per 10 seconds
        
        // Active control actions
        impact += playerResult.getHillCaptures() * 15.0;
        impact += playerResult.getHillDefenses() * 10.0;
        
        // Combat effectiveness on objective
        impact += playerResult.getContestedKills() * 8.0;
        impact += playerResult.getAreaDenialKills() * 6.0;
        
        // Team support
        impact += playerResult.getHillAssists() * 4.0;
        
        return impact;
    }
    
    @Override
    public boolean validateMatchResult(MatchResult matchResult) {
        if (!super.validateMatchResult(matchResult)) return false;
        
        // KOTH specific validation
        long totalControlTime = 0;
        for (PlayerMatchResult playerResult : matchResult.getPlayerResults().values()) {
            totalControlTime += playerResult.getHillControlTime();
            
            // Validate hill statistics are reasonable
            if (playerResult.getHillCaptures() > 20 || 
                playerResult.getHillDefenses() > 30 ||
                playerResult.getContestedKills() > 40) {
                return false;
            }
            
            // Validate control time doesn't exceed match duration per player
            if (playerResult.getHillControlTime() > MATCH_DURATION_MS) {
                return false;
            }
        }
        
        // Validate total control time is reasonable (hill should be contested)
        if (totalControlTime > MATCH_DURATION_MS * 2) { // Allow some overlap for contested periods
            return false;
        }
        
        // Check that someone controlled the hill (not empty match)
        if (totalControlTime < 30000) { // At least 30 seconds total control
            return false;
        }
        
        return true;
    }
}