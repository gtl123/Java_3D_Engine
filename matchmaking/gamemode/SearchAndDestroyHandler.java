package fps.matchmaking.gamemode;

import fps.matchmaking.MatchmakingConfiguration;
import fps.matchmaking.MatchmakingTypes.*;

import java.util.*;

/**
 * Search and Destroy game mode handler
 * Focus: Tactical round-based gameplay with bomb planting/defusing
 */
public class SearchAndDestroyHandler extends BaseGameModeHandler {
    
    private static final int TEAM_SIZE = 5;
    private static final long MATCH_DURATION_MS = 30 * 60 * 1000; // 30 minutes (multiple rounds)
    private static final double K_FACTOR = 40.0; // Higher for competitive mode
    
    public SearchAndDestroyHandler(MatchmakingConfiguration config) {
        super(config, createGameModeConfig());
    }
    
    private static GameModeConfig createGameModeConfig() {
        return new GameModeConfig(
            GameMode.SEARCH_AND_DESTROY,
            TEAM_SIZE,
            MATCH_DURATION_MS,
            true, // Supports ranked
            1000.0, // Higher min rating for competitive mode
            400.0, // Stricter rating difference
            Arrays.asList("Dust2", "Mirage", "Inferno", "Cache", "Overpass", "Train", "Cobblestone"),
            "Plant/defuse the bomb or eliminate the enemy team to win rounds. First to 16 rounds wins."
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
        // Tactical importance in S&D
        weights.put(PlayerRole.ASSAULT, 1.1); // Entry fraggers
        weights.put(PlayerRole.SUPPORT, 1.3); // Critical for utility and trades
        weights.put(PlayerRole.SNIPER, 1.2); // Important for picks and site holds
        weights.put(PlayerRole.TANK, 1.0); // Anchor players
        return weights;
    }
    
    @Override
    public List<String> getObjectives() {
        return Arrays.asList(
            "Plant the bomb (Terrorist)",
            "Defuse the bomb (Counter-Terrorist)",
            "Eliminate enemy team",
            "Defend bomb sites (CT)",
            "Execute site takes (T)",
            "Win clutch situations",
            "Support with utility usage"
        );
    }
    
    @Override
    public double calculatePerformanceMultiplier(PlayerMatchResult playerResult) {
        // S&D values tactical play over pure fragging
        double kd = playerResult.getKills() > 0 ? 
                   (double) playerResult.getKills() / Math.max(1, playerResult.getDeaths()) : 0.0;
        
        double damage = playerResult.getDamageDealt();
        double accuracy = playerResult.getAccuracy();
        double objectiveScore = playerResult.getObjectiveScore();
        
        // Balanced weighting for tactical gameplay
        double kdScore = Math.min(kd / 1.5, 1.0) * 0.3; // 30% weight on K/D (lower than TDM)
        double damageScore = Math.min(damage / 2000.0, 1.0) * 0.25; // 25% weight on damage
        double accuracyScore = accuracy * 0.2; // 20% weight on accuracy
        double objectiveScoreNorm = Math.min(objectiveScore / 800.0, 1.0) * 0.25; // 25% weight on objectives
        
        double performanceScore = kdScore + damageScore + accuracyScore + objectiveScoreNorm;
        
        // Convert to multiplier (0.7x to 1.3x) - less variance than TDM
        return 0.7 + (performanceScore * 0.6);
    }
    
    @Override
    public double calculateObjectiveBonus(PlayerMatchResult playerResult) {
        double bonus = 0.0;
        
        // Bomb plant/defuse bonuses
        int bombPlants = playerResult.getBombPlants();
        int bombDefuses = playerResult.getBombDefuses();
        
        bonus += bombPlants * 8.0; // 8 points per plant
        bonus += bombDefuses * 10.0; // 10 points per defuse (harder to achieve)
        
        // Clutch round bonuses (1vX situations)
        int clutchWins = playerResult.getClutchWins();
        bonus += clutchWins * 12.0; // 12 points per clutch win
        
        // First kill bonus (important in S&D)
        int firstKills = playerResult.getFirstKills();
        bonus += firstKills * 4.0; // 4 points per first kill
        
        // Entry kill bonus (first kill of the round for your team)
        int entryKills = playerResult.getEntryKills();
        bonus += entryKills * 6.0; // 6 points per entry kill
        
        // Utility usage bonus
        int utilityUsage = playerResult.getUtilityUsage();
        bonus += Math.min(utilityUsage / 10.0, 5.0); // Up to 5 points for utility
        
        // Team support bonus (assists, trades, etc.)
        int assists = playerResult.getAssists();
        bonus += Math.min(assists * 1.5, 8.0); // Up to 8 points for assists
        
        return Math.min(bonus, 25.0); // Cap at 25 points
    }
    
    @Override
    protected double applyGameModeAdjustments(double baseChange, PlayerMatchResult playerResult, 
                                            MatchResult matchResult) {
        double adjustedChange = baseChange;
        
        // Round difference adjustment
        if (!playerResult.isWinner()) {
            int roundsWon = playerResult.getRoundsWon();
            int roundsLost = playerResult.getRoundsLost();
            int roundDifference = Math.abs(roundsWon - roundsLost);
            
            // Reduce rating loss for close matches (within 4 rounds)
            if (roundDifference <= 4) {
                adjustedChange *= 0.75; // 25% reduction in rating loss
            }
            // Further reduce for overtime matches (very close)
            else if (roundsWon >= 15 && roundsLost >= 15) {
                adjustedChange *= 0.6; // 40% reduction for overtime losses
            }
        }
        
        // MVP bonus for exceptional performance
        if (isMVPPerformance(playerResult, matchResult)) {
            adjustedChange = Math.abs(adjustedChange) * 1.25; // 25% bonus
            if (baseChange < 0) adjustedChange = -adjustedChange;
        }
        
        // IGL (In-Game Leader) bonus for support players with high impact
        if (isIGLPerformance(playerResult)) {
            adjustedChange = Math.abs(adjustedChange) * 1.15; // 15% bonus
            if (baseChange < 0) adjustedChange = -adjustedChange;
        }
        
        return adjustedChange;
    }
    
    /**
     * Check if player had MVP performance in S&D
     */
    private boolean isMVPPerformance(PlayerMatchResult playerResult, MatchResult matchResult) {
        // Get team members
        List<PlayerMatchResult> teamMembers = matchResult.getPlayerResults().values().stream()
                .filter(p -> p.getTeam() == playerResult.getTeam())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        if (teamMembers.size() < 2) return false;
        
        // Calculate impact score
        double playerImpact = calculateImpactScore(playerResult);
        double avgTeamImpact = teamMembers.stream()
                .mapToDouble(this::calculateImpactScore)
                .average().orElse(0.0);
        
        // MVP if significantly above team average and good performance
        return playerImpact > avgTeamImpact * 1.3 && playerImpact > 50.0;
    }
    
    /**
     * Check if player had IGL (In-Game Leader) performance
     */
    private boolean isIGLPerformance(PlayerMatchResult playerResult) {
        // High utility usage, assists, and objective participation
        return playerResult.getUtilityUsage() >= 15 && 
               playerResult.getAssists() >= 8 && 
               (playerResult.getBombPlants() + playerResult.getBombDefuses()) >= 2;
    }
    
    /**
     * Calculate impact score for S&D
     */
    private double calculateImpactScore(PlayerMatchResult playerResult) {
        double score = 0.0;
        
        // Kills and deaths
        score += playerResult.getKills() * 3.0;
        score -= playerResult.getDeaths() * 2.0;
        
        // Objective actions
        score += playerResult.getBombPlants() * 8.0;
        score += playerResult.getBombDefuses() * 10.0;
        
        // Clutch situations
        score += playerResult.getClutchWins() * 15.0;
        
        // First kills (round openers)
        score += playerResult.getFirstKills() * 5.0;
        
        // Entry kills
        score += playerResult.getEntryKills() * 7.0;
        
        // Assists and support
        score += playerResult.getAssists() * 2.0;
        
        return Math.max(score, 0.0);
    }
    
    @Override
    public boolean validateMatchResult(MatchResult matchResult) {
        if (!super.validateMatchResult(matchResult)) return false;
        
        // S&D specific validation
        for (PlayerMatchResult playerResult : matchResult.getPlayerResults().values()) {
            // Validate round counts
            int totalRounds = playerResult.getRoundsWon() + playerResult.getRoundsLost();
            if (totalRounds < 16 || totalRounds > 30) { // 16-30 rounds (including overtime)
                return false;
            }
            
            // Validate bomb actions don't exceed round count
            if (playerResult.getBombPlants() > totalRounds || 
                playerResult.getBombDefuses() > totalRounds) {
                return false;
            }
            
            // Validate kill counts are reasonable for round-based gameplay
            if (playerResult.getKills() > totalRounds * 2) { // Max 2 kills per round average
                return false;
            }
        }
        
        return true;
    }
}