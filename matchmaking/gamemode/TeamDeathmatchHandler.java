package fps.matchmaking.gamemode;

import fps.matchmaking.MatchmakingConfiguration;
import fps.matchmaking.MatchmakingTypes.*;

import java.util.*;

/**
 * Team Deathmatch game mode handler
 * Focus: Elimination-based gameplay with balanced team combat
 */
public class TeamDeathmatchHandler extends BaseGameModeHandler {
    
    private static final int TEAM_SIZE = 5;
    private static final long MATCH_DURATION_MS = 10 * 60 * 1000; // 10 minutes
    private static final double K_FACTOR = 32.0;
    
    public TeamDeathmatchHandler(MatchmakingConfiguration config) {
        super(config, createGameModeConfig());
    }
    
    private static GameModeConfig createGameModeConfig() {
        return new GameModeConfig(
            GameMode.TEAM_DEATHMATCH,
            TEAM_SIZE,
            MATCH_DURATION_MS,
            true, // Supports ranked
            800.0, // Min rating
            500.0, // Max rating difference
            Arrays.asList("Dust2", "Mirage", "Inferno", "Cache", "Overpass"),
            "Eliminate enemy team members to reach the kill limit or have the most kills when time expires"
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
        // Balanced role importance for TDM
        weights.put(PlayerRole.ASSAULT, 1.2); // Slightly higher for aggressive play
        weights.put(PlayerRole.SUPPORT, 1.0);
        weights.put(PlayerRole.SNIPER, 1.1); // Good for picks
        weights.put(PlayerRole.TANK, 0.9); // Less important in TDM
        return weights;
    }
    
    @Override
    public List<String> getObjectives() {
        return Arrays.asList(
            "Eliminate enemy players",
            "Maintain positive K/D ratio",
            "Support teammates",
            "Control key map areas",
            "Reach kill limit first"
        );
    }
    
    @Override
    public double calculatePerformanceMultiplier(PlayerMatchResult playerResult) {
        // TDM focuses heavily on kills and K/D ratio
        double kd = playerResult.getKills() > 0 ? 
                   (double) playerResult.getKills() / Math.max(1, playerResult.getDeaths()) : 0.0;
        
        double damage = playerResult.getDamageDealt();
        double accuracy = playerResult.getAccuracy();
        
        // Weight K/D more heavily in TDM
        double kdScore = Math.min(kd / 2.0, 1.0) * 0.5; // 50% weight on K/D
        double damageScore = Math.min(damage / 2500.0, 1.0) * 0.3; // 30% weight on damage
        double accuracyScore = accuracy * 0.2; // 20% weight on accuracy
        
        double performanceScore = kdScore + damageScore + accuracyScore;
        
        // Convert to multiplier (0.6x to 1.4x)
        return 0.6 + (performanceScore * 0.8);
    }
    
    @Override
    public double calculateObjectiveBonus(PlayerMatchResult playerResult) {
        double bonus = 0.0;
        
        // Kill streak bonus
        int killStreak = playerResult.getKillStreak();
        if (killStreak >= 3) {
            bonus += Math.min(killStreak - 2, 5) * 2.0; // Up to 10 point bonus
        }
        
        // Multi-kill bonus
        int multiKills = playerResult.getMultiKills();
        bonus += multiKills * 3.0;
        
        // Damage bonus for high damage dealers
        if (playerResult.getDamageDealt() > 3000) {
            bonus += 5.0;
        }
        
        // Accuracy bonus for precise players
        if (playerResult.getAccuracy() > 0.6) {
            bonus += 3.0;
        }
        
        return Math.min(bonus, 15.0); // Cap at 15 points
    }
    
    @Override
    protected double applyGameModeAdjustments(double baseChange, PlayerMatchResult playerResult, 
                                            MatchResult matchResult) {
        double adjustedChange = baseChange;
        
        // Reduce rating loss for close matches
        if (!playerResult.isWinner()) {
            int teamAKills = matchResult.getPlayerResults().values().stream()
                           .filter(p -> p.getTeam() == 0)
                           .mapToInt(PlayerMatchResult::getKills)
                           .sum();
            
            int teamBKills = matchResult.getPlayerResults().values().stream()
                           .filter(p -> p.getTeam() == 1)
                           .mapToInt(PlayerMatchResult::getKills)
                           .sum();
            
            int killDifference = Math.abs(teamAKills - teamBKills);
            
            // If match was close (within 10 kills), reduce rating loss
            if (killDifference <= 10) {
                adjustedChange *= 0.8; // 20% reduction in rating loss
            }
        }
        
        // Bonus for MVP performance
        if (isMVPPerformance(playerResult, matchResult)) {
            adjustedChange = Math.abs(adjustedChange) * 1.2; // 20% bonus
            if (baseChange < 0) adjustedChange = -adjustedChange;
        }
        
        return adjustedChange;
    }
    
    /**
     * Check if player had MVP performance
     */
    private boolean isMVPPerformance(PlayerMatchResult playerResult, MatchResult matchResult) {
        // Get team members
        List<PlayerMatchResult> teamMembers = matchResult.getPlayerResults().values().stream()
                .filter(p -> p.getTeam() == playerResult.getTeam())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        if (teamMembers.size() < 2) return false;
        
        // Check if player has highest kills on team
        boolean highestKills = teamMembers.stream()
                .noneMatch(p -> p.getKills() > playerResult.getKills());
        
        // Check if player has good K/D (above 1.5)
        double kd = playerResult.getKills() > 0 ? 
                   (double) playerResult.getKills() / Math.max(1, playerResult.getDeaths()) : 0.0;
        
        return highestKills && kd >= 1.5;
    }
    
    @Override
    public boolean validateMatchResult(MatchResult matchResult) {
        if (!super.validateMatchResult(matchResult)) return false;
        
        // TDM specific validation
        // Check that match ended due to kill limit or time limit
        long duration = matchResult.getMatchDurationMs();
        if (duration > MATCH_DURATION_MS + 30000) { // 30 second grace period
            return false;
        }
        
        // Validate kill counts are reasonable
        for (PlayerMatchResult playerResult : matchResult.getPlayerResults().values()) {
            if (playerResult.getKills() > 50 || playerResult.getDeaths() > 50) {
                return false; // Unrealistic numbers
            }
        }
        
        return true;
    }
}