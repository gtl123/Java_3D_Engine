package fps.matchmaking.formation;

import engine.logging.LogManager;
import fps.matchmaking.MatchmakingConfiguration.MatchFormationConfiguration;
import fps.matchmaking.MatchmakingTypes.*;
import fps.matchmaking.queue.QueueEntry;
import fps.matchmaking.rating.PlayerRating;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Calculates match quality based on multiple factors
 */
public class MatchQualityCalculator {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final MatchFormationConfiguration config;
    
    // Quality calculation weights
    private static final double SKILL_BALANCE_WEIGHT = 0.35;
    private static final double UNCERTAINTY_WEIGHT = 0.25;
    private static final double ROLE_BALANCE_WEIGHT = 0.20;
    private static final double REGION_WEIGHT = 0.10;
    private static final double WAIT_TIME_WEIGHT = 0.10;
    
    public MatchQualityCalculator(MatchFormationConfiguration config) {
        this.config = config;
    }
    
    /**
     * Calculate overall match quality (0.0 to 1.0, higher is better)
     */
    public double calculateMatchQuality(List<QueueEntry> players, GameMode gameMode) {
        if (players.size() < 2) return 0.0;
        
        try {
            // Calculate individual quality components
            double skillBalance = calculateSkillBalance(players);
            double uncertaintyFactor = calculateUncertaintyFactor(players);
            double roleBalance = calculateRoleBalance(players, gameMode);
            double regionCompatibility = calculateRegionCompatibility(players);
            double waitTimeFactor = calculateWaitTimeFactor(players);
            
            // Weighted combination
            double quality = (skillBalance * SKILL_BALANCE_WEIGHT) +
                           (uncertaintyFactor * UNCERTAINTY_WEIGHT) +
                           (roleBalance * ROLE_BALANCE_WEIGHT) +
                           (regionCompatibility * REGION_WEIGHT) +
                           (waitTimeFactor * WAIT_TIME_WEIGHT);
            
            // Apply game mode specific adjustments
            quality = applyGameModeAdjustments(quality, players, gameMode);
            
            // Clamp to valid range
            return Math.max(0.0, Math.min(1.0, quality));
            
        } catch (Exception e) {
            logManager.error("MatchQualityCalculator", "Quality calculation failed", e,
                           "players", players.size(),
                           "gameMode", gameMode);
            return 0.0;
        }
    }
    
    /**
     * Calculate skill balance quality (how evenly matched players are)
     */
    private double calculateSkillBalance(List<QueueEntry> players) {
        if (players.size() < 2) return 0.0;
        
        List<Double> ratings = players.stream()
                                    .map(entry -> entry.getRating().getEffectiveRating())
                                    .collect(Collectors.toList());
        
        // Calculate standard deviation of ratings
        double mean = ratings.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = ratings.stream()
                                .mapToDouble(rating -> Math.pow(rating - mean, 2))
                                .average().orElse(0.0);
        double stdDev = Math.sqrt(variance);
        
        // Convert to quality score (lower std dev = higher quality)
        // Normalize based on expected rating range (0-3000)
        double normalizedStdDev = stdDev / 300.0; // 300 is reasonable std dev for good matches
        return Math.max(0.0, 1.0 - normalizedStdDev);
    }
    
    /**
     * Calculate uncertainty factor (lower uncertainty = higher quality)
     */
    private double calculateUncertaintyFactor(List<QueueEntry> players) {
        double avgUncertainty = players.stream()
                                     .mapToDouble(entry -> entry.getRating().getUncertainty())
                                     .average().orElse(0.0);
        
        // Normalize uncertainty (typical range 0-200)
        double normalizedUncertainty = avgUncertainty / 200.0;
        return Math.max(0.0, 1.0 - normalizedUncertainty);
    }
    
    /**
     * Calculate role balance quality
     */
    private double calculateRoleBalance(List<QueueEntry> players, GameMode gameMode) {
        Map<PlayerRole, Long> roleCounts = players.stream()
                .collect(Collectors.groupingBy(
                    entry -> entry.getPreferences().getPreferredRole(),
                    Collectors.counting()));
        
        // Calculate ideal distribution for game mode
        Map<PlayerRole, Integer> idealDistribution = getIdealRoleDistribution(players.size(), gameMode);
        
        // Calculate deviation from ideal
        double totalDeviation = 0.0;
        for (PlayerRole role : PlayerRole.values()) {
            int actual = roleCounts.getOrDefault(role, 0L).intValue();
            int ideal = idealDistribution.getOrDefault(role, 0);
            totalDeviation += Math.abs(actual - ideal);
        }
        
        // Normalize (max deviation would be all players in one role)
        double maxDeviation = players.size() - 1;
        return maxDeviation > 0 ? 1.0 - (totalDeviation / maxDeviation) : 1.0;
    }
    
    /**
     * Get ideal role distribution for game mode
     */
    private Map<PlayerRole, Integer> getIdealRoleDistribution(int playerCount, GameMode gameMode) {
        Map<PlayerRole, Integer> distribution = new HashMap<>();
        
        switch (gameMode) {
            case SEARCH_AND_DESTROY:
                // More support and assault for tactical gameplay
                distribution.put(PlayerRole.ASSAULT, playerCount / 3);
                distribution.put(PlayerRole.SUPPORT, playerCount / 3);
                distribution.put(PlayerRole.SNIPER, playerCount / 6);
                distribution.put(PlayerRole.TANK, playerCount / 6);
                break;
                
            case CAPTURE_THE_FLAG:
                // More assault and tank for objective control
                distribution.put(PlayerRole.ASSAULT, playerCount / 2);
                distribution.put(PlayerRole.TANK, playerCount / 4);
                distribution.put(PlayerRole.SUPPORT, playerCount / 6);
                distribution.put(PlayerRole.SNIPER, playerCount / 12);
                break;
                
            case KING_OF_THE_HILL:
                // Balanced for area control
                distribution.put(PlayerRole.TANK, playerCount / 3);
                distribution.put(PlayerRole.ASSAULT, playerCount / 3);
                distribution.put(PlayerRole.SUPPORT, playerCount / 6);
                distribution.put(PlayerRole.SNIPER, playerCount / 6);
                break;
                
            default: // TEAM_DEATHMATCH, CLAN_WARFARE
                // Balanced distribution
                int perRole = playerCount / PlayerRole.values().length;
                for (PlayerRole role : PlayerRole.values()) {
                    distribution.put(role, perRole);
                }
                break;
        }
        
        return distribution;
    }
    
    /**
     * Calculate region compatibility
     */
    private double calculateRegionCompatibility(List<QueueEntry> players) {
        Map<Region, Long> regionCounts = players.stream()
                .collect(Collectors.groupingBy(QueueEntry::getRegion, Collectors.counting()));
        
        if (regionCounts.size() == 1) {
            return 1.0; // All same region = perfect
        }
        
        // Calculate how spread out regions are
        double maxRegionRatio = regionCounts.values().stream()
                                          .mapToDouble(count -> (double) count / players.size())
                                          .max().orElse(0.0);
        
        return maxRegionRatio; // Higher if most players from same region
    }
    
    /**
     * Calculate wait time factor (longer wait = more tolerance for lower quality)
     */
    private double calculateWaitTimeFactor(List<QueueEntry> players) {
        double avgWaitTime = players.stream()
                                  .mapToDouble(QueueEntry::getWaitTimeMs)
                                  .average().orElse(0.0);
        
        // Convert to minutes
        double waitMinutes = avgWaitTime / (60 * 1000);
        
        // Gradual increase in tolerance
        if (waitMinutes < 1.0) return 0.5; // Low tolerance for quick matches
        if (waitMinutes < 3.0) return 0.7; // Medium tolerance
        if (waitMinutes < 5.0) return 0.9; // High tolerance
        return 1.0; // Maximum tolerance for long waits
    }
    
    /**
     * Apply game mode specific quality adjustments
     */
    private double applyGameModeAdjustments(double baseQuality, List<QueueEntry> players, GameMode gameMode) {
        double adjustedQuality = baseQuality;
        
        switch (gameMode) {
            case SEARCH_AND_DESTROY:
            case CLAN_WARFARE:
                // Competitive modes require higher quality
                adjustedQuality *= 0.9; // Stricter requirements
                break;
                
            case TEAM_DEATHMATCH:
                // Casual mode is more forgiving
                adjustedQuality = Math.min(1.0, adjustedQuality * 1.1);
                break;
                
            default:
                // No adjustment for other modes
                break;
        }
        
        return adjustedQuality;
    }
    
    /**
     * Calculate predicted match closeness (0.0 to 1.0, higher = closer match)
     */
    public double calculateMatchCloseness(List<QueueEntry> teamA, List<QueueEntry> teamB) {
        if (teamA.isEmpty() || teamB.isEmpty()) return 0.0;
        
        double avgRatingA = teamA.stream()
                                 .mapToDouble(entry -> entry.getRating().getEffectiveRating())
                                 .average().orElse(1500.0);
        
        double avgRatingB = teamB.stream()
                                 .mapToDouble(entry -> entry.getRating().getEffectiveRating())
                                 .average().orElse(1500.0);
        
        double ratingDiff = Math.abs(avgRatingA - avgRatingB);
        
        // Convert rating difference to closeness (smaller diff = closer match)
        // 100 rating difference = 90% closeness, 200 = 80%, etc.
        return Math.max(0.0, 1.0 - (ratingDiff / 500.0));
    }
    
    /**
     * Get detailed quality breakdown for analysis
     */
    public QualityBreakdown getQualityBreakdown(List<QueueEntry> players, GameMode gameMode) {
        double skillBalance = calculateSkillBalance(players);
        double uncertaintyFactor = calculateUncertaintyFactor(players);
        double roleBalance = calculateRoleBalance(players, gameMode);
        double regionCompatibility = calculateRegionCompatibility(players);
        double waitTimeFactor = calculateWaitTimeFactor(players);
        
        double overallQuality = (skillBalance * SKILL_BALANCE_WEIGHT) +
                              (uncertaintyFactor * UNCERTAINTY_WEIGHT) +
                              (roleBalance * ROLE_BALANCE_WEIGHT) +
                              (regionCompatibility * REGION_WEIGHT) +
                              (waitTimeFactor * WAIT_TIME_WEIGHT);
        
        return new QualityBreakdown(
            overallQuality,
            skillBalance,
            uncertaintyFactor,
            roleBalance,
            regionCompatibility,
            waitTimeFactor
        );
    }
    
    /**
     * Check if match quality meets minimum requirements
     */
    public boolean meetsMinimumQuality(List<QueueEntry> players, GameMode gameMode) {
        double quality = calculateMatchQuality(players, gameMode);
        return quality >= config.getMinMatchQuality();
    }
    
    // Getters
    public MatchFormationConfiguration getConfig() { return config; }
    
    /**
     * Quality breakdown data class
     */
    public static class QualityBreakdown {
        private final double overallQuality;
        private final double skillBalance;
        private final double uncertaintyFactor;
        private final double roleBalance;
        private final double regionCompatibility;
        private final double waitTimeFactor;
        
        public QualityBreakdown(double overallQuality, double skillBalance, double uncertaintyFactor,
                              double roleBalance, double regionCompatibility, double waitTimeFactor) {
            this.overallQuality = overallQuality;
            this.skillBalance = skillBalance;
            this.uncertaintyFactor = uncertaintyFactor;
            this.roleBalance = roleBalance;
            this.regionCompatibility = regionCompatibility;
            this.waitTimeFactor = waitTimeFactor;
        }
        
        // Getters
        public double getOverallQuality() { return overallQuality; }
        public double getSkillBalance() { return skillBalance; }
        public double getUncertaintyFactor() { return uncertaintyFactor; }
        public double getRoleBalance() { return roleBalance; }
        public double getRegionCompatibility() { return regionCompatibility; }
        public double getWaitTimeFactor() { return waitTimeFactor; }
        
        @Override
        public String toString() {
            return String.format("Quality: %.2f (Skill: %.2f, Uncertainty: %.2f, Roles: %.2f, Region: %.2f, Wait: %.2f)",
                               overallQuality, skillBalance, uncertaintyFactor, roleBalance, 
                               regionCompatibility, waitTimeFactor);
        }
    }
}