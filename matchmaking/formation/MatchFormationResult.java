package fps.matchmaking.formation;

import fps.matchmaking.MatchmakingTypes.MatchInfo;

/**
 * Result of match formation attempt
 */
public class MatchFormationResult {
    
    private final boolean success;
    private final String errorMessage;
    private final MatchInfo matchInfo;
    private final double matchQuality;
    private final int availablePlayers;
    private final int requiredPlayers;
    private final long formationTimeMs;
    
    private MatchFormationResult(boolean success, String errorMessage, MatchInfo matchInfo,
                               double matchQuality, int availablePlayers, int requiredPlayers) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.matchInfo = matchInfo;
        this.matchQuality = matchQuality;
        this.availablePlayers = availablePlayers;
        this.requiredPlayers = requiredPlayers;
        this.formationTimeMs = System.currentTimeMillis();
    }
    
    /**
     * Create successful formation result
     */
    public static MatchFormationResult success(MatchInfo matchInfo, double matchQuality) {
        return new MatchFormationResult(true, null, matchInfo, matchQuality, 0, 0);
    }
    
    /**
     * Create failed formation result
     */
    public static MatchFormationResult failure(String errorMessage, int availablePlayers, int requiredPlayers) {
        return new MatchFormationResult(false, errorMessage, null, 0.0, availablePlayers, requiredPlayers);
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public MatchInfo getMatchInfo() { return matchInfo; }
    public double getMatchQuality() { return matchQuality; }
    public int getAvailablePlayers() { return availablePlayers; }
    public int getRequiredPlayers() { return requiredPlayers; }
    public long getFormationTimeMs() { return formationTimeMs; }
    
    @Override
    public String toString() {
        if (success) {
            return String.format("Formation Success: Match %s, Quality %.2f", 
                               matchInfo.getMatchId(), matchQuality);
        } else {
            return String.format("Formation Failed: %s (%d/%d players)", 
                               errorMessage, availablePlayers, requiredPlayers);
        }
    }
}