package fps.matchmaking.formation;

import engine.logging.LogManager;
import fps.matchmaking.MatchmakingConfiguration.MatchFormationConfiguration;
import fps.matchmaking.MatchmakingTypes.*;
import fps.matchmaking.queue.QueueEntry;
import fps.matchmaking.rating.PlayerRating;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced team balancing algorithms for fair match formation
 */
public class TeamBalancer {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final MatchFormationConfiguration config;
    
    // Balancing algorithm weights
    private static final double RATING_WEIGHT = 0.4;
    private static final double ROLE_WEIGHT = 0.3;
    private static final double SYNERGY_WEIGHT = 0.2;
    private static final double RANDOMNESS_WEIGHT = 0.1;
    
    public TeamBalancer(MatchFormationConfiguration config) {
        this.config = config;
    }
    
    /**
     * Form balanced teams from a list of players
     */
    public TeamFormationResult formBalancedTeams(List<QueueEntry> players, GameMode gameMode) {
        if (players.size() % 2 != 0) {
            return TeamFormationResult.failure("Odd number of players");
        }
        
        int teamSize = players.size() / 2;
        
        // Try multiple balancing algorithms and pick the best result
        TeamFormationResult bestResult = null;
        double bestBalance = Double.MAX_VALUE;
        
        // Algorithm 1: Rating-based balancing
        TeamFormationResult ratingResult = balanceByRating(players, teamSize, gameMode);
        if (ratingResult.isSuccess()) {
            double balance = calculateTeamBalance(ratingResult);
            if (balance < bestBalance) {
                bestBalance = balance;
                bestResult = ratingResult;
            }
        }
        
        // Algorithm 2: Role-based balancing
        TeamFormationResult roleResult = balanceByRoles(players, teamSize, gameMode);
        if (roleResult.isSuccess()) {
            double balance = calculateTeamBalance(roleResult);
            if (balance < bestBalance) {
                bestBalance = balance;
                bestResult = roleResult;
            }
        }
        
        // Algorithm 3: Hybrid balancing
        TeamFormationResult hybridResult = balanceHybrid(players, teamSize, gameMode);
        if (hybridResult.isSuccess()) {
            double balance = calculateTeamBalance(hybridResult);
            if (balance < bestBalance) {
                bestBalance = balance;
                bestResult = hybridResult;
            }
        }
        
        if (bestResult == null) {
            return TeamFormationResult.failure("No suitable team balance found");
        }
        
        // Calculate win probability
        double winProbability = calculateWinProbability(bestResult.getTeamA(), bestResult.getTeamB());
        
        return TeamFormationResult.success(
            bestResult.getTeamA(),
            bestResult.getTeamB(),
            bestBalance,
            winProbability
        );
    }
    
    /**
     * Balance teams primarily by player ratings
     */
    private TeamFormationResult balanceByRating(List<QueueEntry> players, int teamSize, GameMode gameMode) {
        // Sort players by effective rating
        List<QueueEntry> sortedPlayers = players.stream()
                                               .sorted((a, b) -> Double.compare(
                                                   b.getRating().getEffectiveRating(),
                                                   a.getRating().getEffectiveRating()))
                                               .collect(Collectors.toList());
        
        List<QueueEntry> teamA = new ArrayList<>();
        List<QueueEntry> teamB = new ArrayList<>();
        
        // Snake draft: A, B, B, A, A, B, B, A, ...
        boolean teamATurn = true;
        for (QueueEntry player : sortedPlayers) {
            if (teamATurn) {
                if (teamA.size() < teamSize) {
                    teamA.add(player);
                } else {
                    teamB.add(player);
                }
            } else {
                if (teamB.size() < teamSize) {
                    teamB.add(player);
                } else {
                    teamA.add(player);
                }
            }
            
            // Switch teams every 2 picks for better balance
            if ((teamA.size() + teamB.size()) % 2 == 0) {
                teamATurn = !teamATurn;
            }
        }
        
        if (teamA.size() != teamSize || teamB.size() != teamSize) {
            return TeamFormationResult.failure("Uneven team sizes");
        }
        
        return TeamFormationResult.success(teamA, teamB, 0.0, 0.5);
    }
    
    /**
     * Balance teams by player roles and preferences
     */
    private TeamFormationResult balanceByRoles(List<QueueEntry> players, int teamSize, GameMode gameMode) {
        // Group players by preferred roles
        Map<PlayerRole, List<QueueEntry>> roleGroups = players.stream()
                .collect(Collectors.groupingBy(entry -> 
                    entry.getPreferences().getPreferredRole()));
        
        List<QueueEntry> teamA = new ArrayList<>();
        List<QueueEntry> teamB = new ArrayList<>();
        
        // Distribute roles evenly
        for (PlayerRole role : PlayerRole.values()) {
            List<QueueEntry> rolePlayers = roleGroups.getOrDefault(role, new ArrayList<>());
            
            // Sort by rating within role
            rolePlayers.sort((a, b) -> Double.compare(
                b.getRating().getEffectiveRating(),
                a.getRating().getEffectiveRating()));
            
            // Alternate assignment
            for (int i = 0; i < rolePlayers.size(); i++) {
                if (i % 2 == 0 && teamA.size() < teamSize) {
                    teamA.add(rolePlayers.get(i));
                } else if (teamB.size() < teamSize) {
                    teamB.add(rolePlayers.get(i));
                } else if (teamA.size() < teamSize) {
                    teamA.add(rolePlayers.get(i));
                }
            }
        }
        
        if (teamA.size() != teamSize || teamB.size() != teamSize) {
            return TeamFormationResult.failure("Uneven team sizes after role balancing");
        }
        
        return TeamFormationResult.success(teamA, teamB, 0.0, 0.5);
    }
    
    /**
     * Hybrid balancing using multiple factors
     */
    private TeamFormationResult balanceHybrid(List<QueueEntry> players, int teamSize, GameMode gameMode) {
        List<QueueEntry> teamA = new ArrayList<>();
        List<QueueEntry> teamB = new ArrayList<>();
        List<QueueEntry> remaining = new ArrayList<>(players);
        
        // Calculate player values for balancing
        Map<QueueEntry, Double> playerValues = calculatePlayerValues(players, gameMode);
        
        // Greedy assignment to minimize team difference
        while (!remaining.isEmpty() && (teamA.size() < teamSize || teamB.size() < teamSize)) {
            QueueEntry bestPlayer = null;
            boolean assignToTeamA = true;
            double bestImprovement = Double.NEGATIVE_INFINITY;
            
            for (QueueEntry player : remaining) {
                if (teamA.size() < teamSize) {
                    double improvement = calculateAssignmentImprovement(player, teamA, teamB, playerValues);
                    if (improvement > bestImprovement) {
                        bestImprovement = improvement;
                        bestPlayer = player;
                        assignToTeamA = true;
                    }
                }
                
                if (teamB.size() < teamSize) {
                    double improvement = calculateAssignmentImprovement(player, teamB, teamA, playerValues);
                    if (improvement > bestImprovement) {
                        bestImprovement = improvement;
                        bestPlayer = player;
                        assignToTeamA = false;
                    }
                }
            }
            
            if (bestPlayer == null) break;
            
            if (assignToTeamA) {
                teamA.add(bestPlayer);
            } else {
                teamB.add(bestPlayer);
            }
            remaining.remove(bestPlayer);
        }
        
        if (teamA.size() != teamSize || teamB.size() != teamSize) {
            return TeamFormationResult.failure("Hybrid balancing failed");
        }
        
        return TeamFormationResult.success(teamA, teamB, 0.0, 0.5);
    }
    
    /**
     * Calculate player values for balancing
     */
    private Map<QueueEntry, Double> calculatePlayerValues(List<QueueEntry> players, GameMode gameMode) {
        Map<QueueEntry, Double> values = new HashMap<>();
        
        for (QueueEntry player : players) {
            double value = 0.0;
            
            // Rating component
            value += player.getRating().getEffectiveRating() * RATING_WEIGHT;
            
            // Role component (bonus for needed roles)
            value += getRoleValue(player.getPreferences().getPreferredRole(), gameMode) * ROLE_WEIGHT;
            
            // Experience component
            value += Math.min(player.getRating().getGamesPlayed() / 100.0, 1.0) * 100 * SYNERGY_WEIGHT;
            
            // Small random component for variety
            value += (Math.random() - 0.5) * 50 * RANDOMNESS_WEIGHT;
            
            values.put(player, value);
        }
        
        return values;
    }
    
    /**
     * Get role value for game mode
     */
    private double getRoleValue(PlayerRole role, GameMode gameMode) {
        switch (gameMode) {
            case SEARCH_AND_DESTROY:
                return role == PlayerRole.SUPPORT ? 120 : 100;
            case CAPTURE_THE_FLAG:
                return role == PlayerRole.ASSAULT ? 120 : 100;
            case KING_OF_THE_HILL:
                return role == PlayerRole.TANK ? 120 : 100;
            default:
                return 100;
        }
    }
    
    /**
     * Calculate improvement of assigning player to team
     */
    private double calculateAssignmentImprovement(QueueEntry player, List<QueueEntry> targetTeam,
                                                List<QueueEntry> opposingTeam, Map<QueueEntry, Double> values) {
        double currentBalance = calculateTeamValueDifference(targetTeam, opposingTeam, values);
        
        List<QueueEntry> newTargetTeam = new ArrayList<>(targetTeam);
        newTargetTeam.add(player);
        
        double newBalance = calculateTeamValueDifference(newTargetTeam, opposingTeam, values);
        
        return Math.abs(currentBalance) - Math.abs(newBalance); // Improvement = reduction in imbalance
    }
    
    /**
     * Calculate team value difference
     */
    private double calculateTeamValueDifference(List<QueueEntry> teamA, List<QueueEntry> teamB,
                                              Map<QueueEntry, Double> values) {
        double teamAValue = teamA.stream().mapToDouble(values::get).sum();
        double teamBValue = teamB.stream().mapToDouble(values::get).sum();
        
        return teamAValue - teamBValue;
    }
    
    /**
     * Calculate overall team balance score (lower is better)
     */
    private double calculateTeamBalance(TeamFormationResult result) {
        List<QueueEntry> teamA = result.getTeamA();
        List<QueueEntry> teamB = result.getTeamB();
        
        // Rating balance
        double avgRatingA = teamA.stream().mapToDouble(e -> e.getRating().getEffectiveRating()).average().orElse(0);
        double avgRatingB = teamB.stream().mapToDouble(e -> e.getRating().getEffectiveRating()).average().orElse(0);
        double ratingImbalance = Math.abs(avgRatingA - avgRatingB);
        
        // Role balance
        double roleImbalance = calculateRoleImbalance(teamA, teamB);
        
        // Experience balance
        double avgExpA = teamA.stream().mapToDouble(e -> e.getRating().getGamesPlayed()).average().orElse(0);
        double avgExpB = teamB.stream().mapToDouble(e -> e.getRating().getGamesPlayed()).average().orElse(0);
        double experienceImbalance = Math.abs(avgExpA - avgExpB) / 100.0; // Normalize
        
        return ratingImbalance * 0.5 + roleImbalance * 0.3 + experienceImbalance * 0.2;
    }
    
    /**
     * Calculate role distribution imbalance
     */
    private double calculateRoleImbalance(List<QueueEntry> teamA, List<QueueEntry> teamB) {
        Map<PlayerRole, Long> rolesA = teamA.stream()
                .collect(Collectors.groupingBy(e -> e.getPreferences().getPreferredRole(),
                                             Collectors.counting()));
        
        Map<PlayerRole, Long> rolesB = teamB.stream()
                .collect(Collectors.groupingBy(e -> e.getPreferences().getPreferredRole(),
                                             Collectors.counting()));
        
        double imbalance = 0.0;
        for (PlayerRole role : PlayerRole.values()) {
            long countA = rolesA.getOrDefault(role, 0L);
            long countB = rolesB.getOrDefault(role, 0L);
            imbalance += Math.abs(countA - countB);
        }
        
        return imbalance;
    }
    
    /**
     * Calculate win probability for team A vs team B
     */
    private double calculateWinProbability(List<QueueEntry> teamA, List<QueueEntry> teamB) {
        double avgRatingA = teamA.stream().mapToDouble(e -> e.getRating().getEffectiveRating()).average().orElse(1500);
        double avgRatingB = teamB.stream().mapToDouble(e -> e.getRating().getEffectiveRating()).average().orElse(1500);
        
        // Use Elo formula for win probability
        double ratingDiff = avgRatingA - avgRatingB;
        return 1.0 / (1.0 + Math.pow(10, -ratingDiff / 400.0));
    }
    
    /**
     * Validate team composition
     */
    public boolean validateTeamComposition(List<QueueEntry> team, GameMode gameMode) {
        if (team.isEmpty()) return false;
        
        // Check role distribution
        Map<PlayerRole, Long> roleCounts = team.stream()
                .collect(Collectors.groupingBy(e -> e.getPreferences().getPreferredRole(),
                                             Collectors.counting()));
        
        // Ensure no role is completely missing (for competitive modes)
        if (gameMode == GameMode.SEARCH_AND_DESTROY || gameMode == GameMode.CLAN_WARFARE) {
            for (PlayerRole role : PlayerRole.values()) {
                if (roleCounts.getOrDefault(role, 0L) == 0) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    // Getters
    public MatchFormationConfiguration getConfig() { return config; }
}