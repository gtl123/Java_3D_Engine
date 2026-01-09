package fps.matchmaking.gamemode;

import fps.matchmaking.MatchmakingConfiguration;
import fps.matchmaking.MatchmakingTypes.*;

import java.util.*;

/**
 * Capture the Flag game mode handler
 * Focus: Objective-based gameplay with flag capturing and defense
 */
public class CaptureTheFlagHandler extends BaseGameModeHandler {
    
    private static final int TEAM_SIZE = 5;
    private static final long MATCH_DURATION_MS = 15 * 60 * 1000; // 15 minutes
    private static final double K_FACTOR = 35.0;
    
    public CaptureTheFlagHandler(MatchmakingConfiguration config) {
        super(config, createGameModeConfig());
    }
    
    private static GameModeConfig createGameModeConfig() {
        return new GameModeConfig(
            GameMode.CAPTURE_THE_FLAG,
            TEAM_SIZE,
            MATCH_DURATION_MS,
            true, // Supports ranked
            900.0, // Min rating
            450.0, // Max rating difference
            Arrays.asList("CTF_Dust", "CTF_Mirage", "CTF_Inferno", "CTF_Cache", "CTF_Overpass", "CTF_Aztec"),
            "Capture the enemy flag and return it to your base while defending your own flag. First to 5 captures wins."
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
        // CTF role importance
        weights.put(PlayerRole.ASSAULT, 1.3); // Flag runners and attackers
        weights.put(PlayerRole.SUPPORT, 1.1); // Team coordination and utility
        weights.put(PlayerRole.SNIPER, 0.9); // Less mobile for flag objectives
        weights.put(PlayerRole.TANK, 1.2); // Flag defenders and area control
        return weights;
    }
    
    @Override
    public List<String> getObjectives() {
        return Arrays.asList(
            "Capture enemy flags",
            "Defend your flag",
            "Return dropped flags",
            "Escort flag carriers",
            "Control key map areas",
            "Eliminate flag carriers",
            "Coordinate team pushes"
        );
    }
    
    @Override
    public double calculatePerformanceMultiplier(PlayerMatchResult playerResult) {
        // CTF heavily weights objective play
        double kd = playerResult.getKills() > 0 ? 
                   (double) playerResult.getKills() / Math.max(1, playerResult.getDeaths()) : 0.0;
        
        double damage = playerResult.getDamageDealt();
        double accuracy = playerResult.getAccuracy();
        double objectiveScore = playerResult.getObjectiveScore();
        
        // Heavy emphasis on objectives in CTF
        double kdScore = Math.min(kd / 1.8, 1.0) * 0.25; // 25% weight on K/D
        double damageScore = Math.min(damage / 2200.0, 1.0) * 0.2; // 20% weight on damage
        double accuracyScore = accuracy * 0.15; // 15% weight on accuracy
        double objectiveScoreNorm = Math.min(objectiveScore / 1200.0, 1.0) * 0.4; // 40% weight on objectives
        
        double performanceScore = kdScore + damageScore + accuracyScore + objectiveScoreNorm;
        
        // Convert to multiplier (0.6x to 1.4x)
        return 0.6 + (performanceScore * 0.8);
    }
    
    @Override
    public double calculateObjectiveBonus(PlayerMatchResult playerResult) {
        double bonus = 0.0;
        
        // Flag captures (primary objective)
        int flagCaptures = playerResult.getFlagCaptures();
        bonus += flagCaptures * 15.0; // 15 points per capture
        
        // Flag returns (defensive objective)
        int flagReturns = playerResult.getFlagReturns();
        bonus += flagReturns * 8.0; // 8 points per return
        
        // Flag carrier kills (stopping enemy objectives)
        int carrierKills = playerResult.getCarrierKills();
        bonus += carrierKills * 10.0; // 10 points per carrier kill
        
        // Flag assists (helping with captures)
        int flagAssists = playerResult.getFlagAssists();
        bonus += flagAssists * 6.0; // 6 points per flag assist
        
        // Defensive kills (kills near own flag)
        int defensiveKills = playerResult.getDefensiveKills();
        bonus += defensiveKills * 4.0; // 4 points per defensive kill
        
        // Flag pickup bonus (attempting objectives)
        int flagPickups = playerResult.getFlagPickups();
        bonus += Math.min(flagPickups * 2.0, 10.0); // Up to 10 points for pickups
        
        // Time with flag bonus
        long timeWithFlag = playerResult.getTimeWithFlag();
        bonus += Math.min(timeWithFlag / 30000.0, 8.0); // Up to 8 points for 4+ minutes with flag
        
        return Math.min(bonus, 30.0); // Cap at 30 points
    }
    
    @Override
    protected double applyGameModeAdjustments(double baseChange, PlayerMatchResult playerResult, 
                                            MatchResult matchResult) {
        double adjustedChange = baseChange;
        
        // Score difference adjustment
        if (!playerResult.isWinner()) {
            int teamAScore = getTeamScore(matchResult, 0);
            int teamBScore = getTeamScore(matchResult, 1);
            int scoreDifference = Math.abs(teamAScore - teamBScore);
            
            // Reduce rating loss for close matches
            if (scoreDifference <= 1) {
                adjustedChange *= 0.7; // 30% reduction for 1-point games
            } else if (scoreDifference <= 2) {
                adjustedChange *= 0.85; // 15% reduction for 2-point games
            }
        }
        
        // Objective MVP bonus
        if (isObjectiveMVP(playerResult, matchResult)) {
            adjustedChange = Math.abs(adjustedChange) * 1.3; // 30% bonus for objective MVP
            if (baseChange < 0) adjustedChange = -adjustedChange;
        }
        
        // Flag carrier bonus (high risk, high reward)
        if (isActiveCarrier(playerResult)) {
            adjustedChange = Math.abs(adjustedChange) * 1.2; // 20% bonus for active carriers
            if (baseChange < 0) adjustedChange = -adjustedChange;
        }
        
        // Defender bonus for strong defensive play
        if (isStrongDefender(playerResult)) {
            adjustedChange = Math.abs(adjustedChange) * 1.15; // 15% bonus for defenders
            if (baseChange < 0) adjustedChange = -adjustedChange;
        }
        
        return adjustedChange;
    }
    
    /**
     * Get team score (flag captures)
     */
    private int getTeamScore(MatchResult matchResult, int team) {
        return matchResult.getPlayerResults().values().stream()
                .filter(p -> p.getTeam() == team)
                .mapToInt(PlayerMatchResult::getFlagCaptures)
                .sum();
    }
    
    /**
     * Check if player was objective MVP
     */
    private boolean isObjectiveMVP(PlayerMatchResult playerResult, MatchResult matchResult) {
        // Get team members
        List<PlayerMatchResult> teamMembers = matchResult.getPlayerResults().values().stream()
                .filter(p -> p.getTeam() == playerResult.getTeam())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        if (teamMembers.size() < 2) return false;
        
        // Calculate objective impact
        double playerObjectiveImpact = calculateObjectiveImpact(playerResult);
        double avgTeamObjectiveImpact = teamMembers.stream()
                .mapToDouble(this::calculateObjectiveImpact)
                .average().orElse(0.0);
        
        return playerObjectiveImpact > avgTeamObjectiveImpact * 1.5 && playerObjectiveImpact > 30.0;
    }
    
    /**
     * Check if player was an active flag carrier
     */
    private boolean isActiveCarrier(PlayerMatchResult playerResult) {
        return playerResult.getFlagCaptures() >= 2 || 
               (playerResult.getFlagPickups() >= 3 && playerResult.getTimeWithFlag() > 60000);
    }
    
    /**
     * Check if player was a strong defender
     */
    private boolean isStrongDefender(PlayerMatchResult playerResult) {
        return playerResult.getFlagReturns() >= 3 || 
               (playerResult.getDefensiveKills() >= 5 && playerResult.getCarrierKills() >= 2);
    }
    
    /**
     * Calculate objective impact score
     */
    private double calculateObjectiveImpact(PlayerMatchResult playerResult) {
        double impact = 0.0;
        
        // Primary objectives
        impact += playerResult.getFlagCaptures() * 20.0;
        impact += playerResult.getFlagReturns() * 12.0;
        
        // Secondary objectives
        impact += playerResult.getCarrierKills() * 8.0;
        impact += playerResult.getFlagAssists() * 6.0;
        impact += playerResult.getDefensiveKills() * 3.0;
        
        // Participation
        impact += Math.min(playerResult.getFlagPickups() * 2.0, 10.0);
        impact += Math.min(playerResult.getTimeWithFlag() / 20000.0, 15.0);
        
        return impact;
    }
    
    @Override
    public boolean validateMatchResult(MatchResult matchResult) {
        if (!super.validateMatchResult(matchResult)) return false;
        
        // CTF specific validation
        int totalCaptures = 0;
        for (PlayerMatchResult playerResult : matchResult.getPlayerResults().values()) {
            totalCaptures += playerResult.getFlagCaptures();
            
            // Validate flag statistics are reasonable
            if (playerResult.getFlagCaptures() > 10 || 
                playerResult.getFlagReturns() > 20 ||
                playerResult.getFlagPickups() > 30) {
                return false;
            }
            
            // Validate time with flag doesn't exceed match duration
            if (playerResult.getTimeWithFlag() > MATCH_DURATION_MS) {
                return false;
            }
        }
        
        // Validate total captures indicate a completed match
        if (totalCaptures < 5) { // At least one team should reach the win condition
            // Check if match ended due to time limit
            if (matchResult.getMatchDurationMs() < MATCH_DURATION_MS - 30000) {
                return false; // Match ended too early without reaching score limit
            }
        }
        
        return true;
    }
}