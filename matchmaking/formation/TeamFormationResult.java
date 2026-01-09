package fps.matchmaking.formation;

import fps.matchmaking.queue.QueueEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of team formation/balancing attempt
 */
public class TeamFormationResult {
    
    private final boolean success;
    private final String errorMessage;
    private final List<QueueEntry> teamA;
    private final List<QueueEntry> teamB;
    private final double balanceScore;
    private final double predictedWinProbability;
    private final long formationTimeMs;
    
    private TeamFormationResult(boolean success, String errorMessage, List<QueueEntry> teamA,
                              List<QueueEntry> teamB, double balanceScore, double predictedWinProbability) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.teamA = teamA != null ? new ArrayList<>(teamA) : new ArrayList<>();
        this.teamB = teamB != null ? new ArrayList<>(teamB) : new ArrayList<>();
        this.balanceScore = balanceScore;
        this.predictedWinProbability = predictedWinProbability;
        this.formationTimeMs = System.currentTimeMillis();
    }
    
    /**
     * Create successful team formation result
     */
    public static TeamFormationResult success(List<QueueEntry> teamA, List<QueueEntry> teamB,
                                            double balanceScore, double predictedWinProbability) {
        return new TeamFormationResult(true, null, teamA, teamB, balanceScore, predictedWinProbability);
    }
    
    /**
     * Create failed team formation result
     */
    public static TeamFormationResult failure(String errorMessage) {
        return new TeamFormationResult(false, errorMessage, null, null, 0.0, 0.5);
    }
    
    /**
     * Get team A average rating
     */
    public double getTeamAAvgRating() {
        if (teamA.isEmpty()) return 0.0;
        return teamA.stream()
                   .mapToDouble(entry -> entry.getRating().getEffectiveRating())
                   .average()
                   .orElse(0.0);
    }
    
    /**
     * Get team B average rating
     */
    public double getTeamBAvgRating() {
        if (teamB.isEmpty()) return 0.0;
        return teamB.stream()
                   .mapToDouble(entry -> entry.getRating().getEffectiveRating())
                   .average()
                   .orElse(0.0);
    }
    
    /**
     * Get rating difference between teams
     */
    public double getRatingDifference() {
        return Math.abs(getTeamAAvgRating() - getTeamBAvgRating());
    }
    
    /**
     * Check if teams are balanced (rating difference within threshold)
     */
    public boolean isBalanced(double maxRatingDifference) {
        return getRatingDifference() <= maxRatingDifference;
    }
    
    /**
     * Get total players in both teams
     */
    public int getTotalPlayers() {
        return teamA.size() + teamB.size();
    }
    
    /**
     * Validate team formation result
     */
    public boolean isValid() {
        if (!success) return false;
        if (teamA.isEmpty() || teamB.isEmpty()) return false;
        if (teamA.size() != teamB.size()) return false;
        
        // Check for duplicate players
        List<String> allPlayerIds = new ArrayList<>();
        teamA.forEach(entry -> allPlayerIds.add(entry.getPlayerId()));
        teamB.forEach(entry -> allPlayerIds.add(entry.getPlayerId()));
        
        return allPlayerIds.size() == allPlayerIds.stream().distinct().count();
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public List<QueueEntry> getTeamA() { return new ArrayList<>(teamA); }
    public List<QueueEntry> getTeamB() { return new ArrayList<>(teamB); }
    public double getBalanceScore() { return balanceScore; }
    public double getPredictedWinProbability() { return predictedWinProbability; }
    public long getFormationTimeMs() { return formationTimeMs; }
    
    @Override
    public String toString() {
        if (success) {
            return String.format("Team Formation Success: %dv%d, Balance %.2f, Win Prob %.2f",
                               teamA.size(), teamB.size(), balanceScore, predictedWinProbability);
        } else {
            return String.format("Team Formation Failed: %s", errorMessage);
        }
    }
}