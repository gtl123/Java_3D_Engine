package fps.matchmaking.gamemode;

import fps.matchmaking.MatchmakingConfiguration;
import fps.matchmaking.MatchmakingTypes.*;

import java.util.*;

/**
 * Clan Warfare game mode handler
 * Focus: Large-scale tactical gameplay with clan-based competition
 */
public class ClanWarfareHandler extends BaseGameModeHandler {
    
    private static final int TEAM_SIZE = 6; // Larger teams for clan warfare
    private static final long MATCH_DURATION_MS = 45 * 60 * 1000; // 45 minutes (longer matches)
    private static final double K_FACTOR = 45.0; // Higher stakes
    
    public ClanWarfareHandler(MatchmakingConfiguration config) {
        super(config, createGameModeConfig());
    }
    
    private static GameModeConfig createGameModeConfig() {
        return new GameModeConfig(
            GameMode.CLAN_WARFARE,
            TEAM_SIZE,
            MATCH_DURATION_MS,
            true, // Supports ranked
            1200.0, // Higher min rating for competitive clan play
            350.0, // Stricter rating difference for balanced clan matches
            Arrays.asList("CW_Dust2", "CW_Mirage", "CW_Inferno", "CW_Cache", "CW_Overpass", "CW_Train", "CW_Nuke"),
            "Multi-objective clan warfare with strategic gameplay. Control objectives, eliminate enemies, and coordinate as a clan."
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
        // Clan warfare values all roles highly due to tactical depth
        weights.put(PlayerRole.ASSAULT, 1.2); // Entry fraggers and site takes
        weights.put(PlayerRole.SUPPORT, 1.3); // Critical for team coordination and utility
        weights.put(PlayerRole.SNIPER, 1.2); // Important for picks and area denial
        weights.put(PlayerRole.TANK, 1.1); // Anchors and site holders
        return weights;
    }
    
    @Override
    public List<String> getObjectives() {
        return Arrays.asList(
            "Control strategic objectives",
            "Coordinate clan tactics",
            "Execute strategic plays",
            "Defend key positions",
            "Support clan members",
            "Maintain map control",
            "Win tactical engagements",
            "Adapt to enemy strategies"
        );
    }
    
    @Override
    public double calculatePerformanceMultiplier(PlayerMatchResult playerResult) {
        // Clan warfare values tactical play and teamwork
        double kd = playerResult.getKills() > 0 ? 
                   (double) playerResult.getKills() / Math.max(1, playerResult.getDeaths()) : 0.0;
        
        double damage = playerResult.getDamageDealt();
        double accuracy = playerResult.getAccuracy();
        double objectiveScore = playerResult.getObjectiveScore();
        double teamworkScore = calculateTeamworkScore(playerResult);
        
        // Balanced weighting emphasizing teamwork and tactics
        double kdScore = Math.min(kd / 1.4, 1.0) * 0.25; // 25% weight on K/D
        double damageScore = Math.min(damage / 3000.0, 1.0) * 0.2; // 20% weight on damage
        double accuracyScore = accuracy * 0.15; // 15% weight on accuracy
        double objectiveScoreNorm = Math.min(objectiveScore / 1500.0, 1.0) * 0.25; // 25% weight on objectives
        double teamworkScoreNorm = Math.min(teamworkScore / 100.0, 1.0) * 0.15; // 15% weight on teamwork
        
        double performanceScore = kdScore + damageScore + accuracyScore + objectiveScoreNorm + teamworkScoreNorm;
        
        // Convert to multiplier (0.7x to 1.3x) - less variance for competitive play
        return 0.7 + (performanceScore * 0.6);
    }
    
    /**
     * Calculate teamwork score based on assists, utility usage, and support actions
     */
    private double calculateTeamworkScore(PlayerMatchResult playerResult) {
        double score = 0.0;
        
        // Assists (helping teammates)
        score += Math.min(playerResult.getAssists() * 3.0, 30.0);
        
        // Utility usage (tactical equipment)
        score += Math.min(playerResult.getUtilityUsage() * 2.0, 20.0);
        
        // Revives/rescues (supporting downed teammates)
        score += playerResult.getRevives() * 8.0;
        
        // Callouts/communication (if tracked)
        score += Math.min(playerResult.getCallouts() * 1.5, 15.0);
        
        // Trade kills (avenging teammates)
        score += playerResult.getTradeKills() * 5.0;
        
        return score;
    }
    
    @Override
    public double calculateObjectiveBonus(PlayerMatchResult playerResult) {
        double bonus = 0.0;
        
        // Strategic objective captures
        int objectiveCaptures = playerResult.getObjectiveCaptures();
        bonus += objectiveCaptures * 12.0; // 12 points per objective
        
        // Objective defenses
        int objectiveDefenses = playerResult.getObjectiveDefenses();
        bonus += objectiveDefenses * 8.0; // 8 points per defense
        
        // Leadership actions (if player is clan leader/caller)
        int leadershipActions = playerResult.getLeadershipActions();
        bonus += leadershipActions * 6.0; // 6 points per leadership action
        
        // Strategic kills (eliminating key targets)
        int strategicKills = playerResult.getStrategicKills();
        bonus += strategicKills * 7.0; // 7 points per strategic kill
        
        // Team support actions
        bonus += Math.min(playerResult.getRevives() * 10.0, 30.0); // Up to 30 points for revives
        bonus += Math.min(playerResult.getTradeKills() * 5.0, 25.0); // Up to 25 points for trades
        
        // Tactical utility effectiveness
        int utilityKills = playerResult.getUtilityKills();
        bonus += utilityKills * 8.0; // 8 points per utility kill
        
        // Map control contributions
        int areaControlTime = (int) (playerResult.getAreaControlTime() / 60000); // Minutes
        bonus += Math.min(areaControlTime * 2.0, 20.0); // Up to 20 points for area control
        
        // Clutch situations in clan warfare
        int clutchWins = playerResult.getClutchWins();
        bonus += clutchWins * 15.0; // 15 points per clutch (higher stakes)
        
        return Math.min(bonus, 40.0); // Cap at 40 points
    }
    
    @Override
    protected double applyGameModeAdjustments(double baseChange, PlayerMatchResult playerResult, 
                                            MatchResult matchResult) {
        double adjustedChange = baseChange;
        
        // Clan warfare specific adjustments
        if (!playerResult.isWinner()) {
            // Check match closeness based on objectives and rounds
            double matchCloseness = calculateMatchCloseness(matchResult);
            
            if (matchCloseness > 0.8) { // Very close match
                adjustedChange *= 0.6; // 40% reduction in rating loss
            } else if (matchCloseness > 0.6) { // Close match
                adjustedChange *= 0.8; // 20% reduction in rating loss
            }
        }
        
        // Clan leader bonus
        if (isClanLeader(playerResult)) {
            adjustedChange = Math.abs(adjustedChange) * 1.2; // 20% bonus for leadership
            if (baseChange < 0) adjustedChange = -adjustedChange;
        }
        
        // Tactical MVP bonus
        if (isTacticalMVP(playerResult, matchResult)) {
            adjustedChange = Math.abs(adjustedChange) * 1.3; // 30% bonus for tactical MVP
            if (baseChange < 0) adjustedChange = -adjustedChange;
        }
        
        // Team player bonus
        if (isExceptionalTeamPlayer(playerResult)) {
            adjustedChange = Math.abs(adjustedChange) * 1.15; // 15% bonus for teamwork
            if (baseChange < 0) adjustedChange = -adjustedChange;
        }
        
        // Clutch performer bonus (higher in clan warfare)
        if (isClutchPerformer(playerResult)) {
            adjustedChange = Math.abs(adjustedChange) * 1.25; // 25% bonus for clutch performance
            if (baseChange < 0) adjustedChange = -adjustedChange;
        }
        
        return adjustedChange;
    }
    
    /**
     * Calculate match closeness based on various factors
     */
    private double calculateMatchCloseness(MatchResult matchResult) {
        // Get team scores and statistics
        Map<Integer, List<PlayerMatchResult>> teams = new HashMap<>();
        teams.put(0, new ArrayList<>());
        teams.put(1, new ArrayList<>());
        
        for (PlayerMatchResult result : matchResult.getPlayerResults().values()) {
            teams.get(result.getTeam()).add(result);
        }
        
        // Calculate team performance metrics
        double team0Objectives = teams.get(0).stream().mapToInt(PlayerMatchResult::getObjectiveCaptures).sum();
        double team1Objectives = teams.get(1).stream().mapToInt(PlayerMatchResult::getObjectiveCaptures).sum();
        
        double team0Kills = teams.get(0).stream().mapToInt(PlayerMatchResult::getKills).sum();
        double team1Kills = teams.get(1).stream().mapToInt(PlayerMatchResult::getKills).sum();
        
        // Calculate closeness (0.0 to 1.0)
        double objectiveCloseness = 1.0 - Math.abs(team0Objectives - team1Objectives) / Math.max(team0Objectives + team1Objectives, 1.0);
        double killCloseness = 1.0 - Math.abs(team0Kills - team1Kills) / Math.max(team0Kills + team1Kills, 1.0);
        
        return (objectiveCloseness + killCloseness) / 2.0;
    }
    
    /**
     * Check if player was a clan leader
     */
    private boolean isClanLeader(PlayerMatchResult playerResult) {
        return playerResult.getLeadershipActions() >= 5 && 
               playerResult.getCallouts() >= 10;
    }
    
    /**
     * Check if player was tactical MVP
     */
    private boolean isTacticalMVP(PlayerMatchResult playerResult, MatchResult matchResult) {
        double tacticalImpact = calculateTacticalImpact(playerResult);
        
        // Get team members for comparison
        List<PlayerMatchResult> teamMembers = matchResult.getPlayerResults().values().stream()
                .filter(p -> p.getTeam() == playerResult.getTeam())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        double avgTeamTacticalImpact = teamMembers.stream()
                .mapToDouble(this::calculateTacticalImpact)
                .average().orElse(0.0);
        
        return tacticalImpact > avgTeamTacticalImpact * 1.4 && tacticalImpact > 80.0;
    }
    
    /**
     * Check if player was exceptional team player
     */
    private boolean isExceptionalTeamPlayer(PlayerMatchResult playerResult) {
        return playerResult.getAssists() >= 15 && 
               playerResult.getRevives() >= 3 && 
               playerResult.getTradeKills() >= 5 &&
               playerResult.getUtilityUsage() >= 20;
    }
    
    /**
     * Check if player was clutch performer
     */
    private boolean isClutchPerformer(PlayerMatchResult playerResult) {
        return playerResult.getClutchWins() >= 3 || 
               (playerResult.getClutchWins() >= 2 && playerResult.getStrategicKills() >= 8);
    }
    
    /**
     * Calculate tactical impact score
     */
    private double calculateTacticalImpact(PlayerMatchResult playerResult) {
        double impact = 0.0;
        
        // Objective contributions
        impact += playerResult.getObjectiveCaptures() * 15.0;
        impact += playerResult.getObjectiveDefenses() * 10.0;
        
        // Leadership and coordination
        impact += playerResult.getLeadershipActions() * 8.0;
        impact += playerResult.getCallouts() * 2.0;
        
        // Team support
        impact += playerResult.getAssists() * 3.0;
        impact += playerResult.getRevives() * 12.0;
        impact += playerResult.getTradeKills() * 6.0;
        
        // Tactical effectiveness
        impact += playerResult.getUtilityKills() * 10.0;
        impact += playerResult.getStrategicKills() * 8.0;
        impact += playerResult.getClutchWins() * 20.0;
        
        return impact;
    }
    
    @Override
    public boolean validateMatchResult(MatchResult matchResult) {
        if (!super.validateMatchResult(matchResult)) return false;
        
        // Clan warfare specific validation
        for (PlayerMatchResult playerResult : matchResult.getPlayerResults().values()) {
            // Validate objective counts are reasonable for longer matches
            if (playerResult.getObjectiveCaptures() > 15 || 
                playerResult.getObjectiveDefenses() > 25) {
                return false;
            }
            
            // Validate teamwork statistics
            if (playerResult.getRevives() > 20 || 
                playerResult.getTradeKills() > 30 ||
                playerResult.getUtilityUsage() > 50) {
                return false;
            }
            
            // Validate leadership actions
            if (playerResult.getLeadershipActions() > 30 ||
                playerResult.getCallouts() > 100) {
                return false;
            }
        }
        
        // Validate match duration is appropriate for clan warfare
        if (matchResult.getMatchDurationMs() < 20 * 60 * 1000) { // At least 20 minutes
            return false;
        }
        
        return true;
    }
}