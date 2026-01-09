package fps.matchmaking;

import fps.matchmaking.rating.PlayerRating;
import fps.matchmaking.ranking.PlayerRank;
import fps.matchmaking.statistics.PlayerStatistics;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Core data types and enums for the matchmaking system
 */
public class MatchmakingTypes {
    
    /**
     * Different types of matchmaking queues
     */
    public enum QueueType {
        // Ranked queues
        RANKED_CLAN_WARFARE(true, 32, "Ranked Clan Warfare"),
        RANKED_TEAM_DEATHMATCH(true, 8, "Ranked Team Deathmatch"),
        RANKED_SEARCH_DESTROY(true, 10, "Ranked Search & Destroy"),
        RANKED_CAPTURE_FLAG(true, 12, "Ranked Capture the Flag"),
        RANKED_KING_HILL(true, 8, "Ranked King of the Hill"),
        
        // Casual queues
        CASUAL_CLAN_WARFARE(false, 32, "Casual Clan Warfare"),
        CASUAL_TEAM_DEATHMATCH(false, 8, "Casual Team Deathmatch"),
        CASUAL_SEARCH_DESTROY(false, 10, "Casual Search & Destroy"),
        CASUAL_CAPTURE_FLAG(false, 12, "Casual Capture the Flag"),
        CASUAL_KING_HILL(false, 8, "Casual King of the Hill"),
        
        // Special queues
        CUSTOM_GAMES(false, 32, "Custom Games"),
        TOURNAMENT(true, 32, "Tournament"),
        TRAINING(false, 16, "Training");
        
        private final boolean ranked;
        private final int maxPlayers;
        private final String displayName;
        
        QueueType(boolean ranked, int maxPlayers, String displayName) {
            this.ranked = ranked;
            this.maxPlayers = maxPlayers;
            this.displayName = displayName;
        }
        
        public boolean isRanked() { return ranked; }
        public int getMaxPlayers() { return maxPlayers; }
        public String getDisplayName() { return displayName; }
    }
    
    /**
     * Game modes supported by matchmaking
     */
    public enum GameMode {
        CLAN_WARFARE("Clan Warfare", 32, 2),
        TEAM_DEATHMATCH("Team Deathmatch", 8, 2),
        SEARCH_DESTROY("Search & Destroy", 10, 2),
        CAPTURE_FLAG("Capture the Flag", 12, 2),
        KING_HILL("King of the Hill", 8, 2),
        FREE_FOR_ALL("Free for All", 16, 0),
        CUSTOM("Custom", 32, 2);
        
        private final String displayName;
        private final int maxPlayers;
        private final int teamCount;
        
        GameMode(String displayName, int maxPlayers, int teamCount) {
            this.displayName = displayName;
            this.maxPlayers = maxPlayers;
            this.teamCount = teamCount;
        }
        
        public String getDisplayName() { return displayName; }
        public int getMaxPlayers() { return maxPlayers; }
        public int getTeamCount() { return teamCount; }
    }
    
    /**
     * Regions for matchmaking
     */
    public enum Region {
        NORTH_AMERICA_EAST("NA East", "us-east"),
        NORTH_AMERICA_WEST("NA West", "us-west"),
        EUROPE_WEST("EU West", "eu-west"),
        EUROPE_EAST("EU East", "eu-east"),
        ASIA_PACIFIC("Asia Pacific", "ap"),
        SOUTH_AMERICA("South America", "sa"),
        OCEANIA("Oceania", "oce"),
        AUTO("Auto Select", "auto");
        
        private final String displayName;
        private final String code;
        
        Region(String displayName, String code) {
            this.displayName = displayName;
            this.code = code;
        }
        
        public String getDisplayName() { return displayName; }
        public String getCode() { return code; }
    }
    
    /**
     * Match quality levels
     */
    public enum MatchQuality {
        EXCELLENT(0.9f, 1.0f, "Excellent"),
        GOOD(0.7f, 0.89f, "Good"),
        FAIR(0.5f, 0.69f, "Fair"),
        POOR(0.3f, 0.49f, "Poor"),
        UNACCEPTABLE(0.0f, 0.29f, "Unacceptable");
        
        private final float minScore;
        private final float maxScore;
        private final String displayName;
        
        MatchQuality(float minScore, float maxScore, String displayName) {
            this.minScore = minScore;
            this.maxScore = maxScore;
            this.displayName = displayName;
        }
        
        public static MatchQuality fromScore(float score) {
            for (MatchQuality quality : values()) {
                if (score >= quality.minScore && score <= quality.maxScore) {
                    return quality;
                }
            }
            return UNACCEPTABLE;
        }
        
        public float getMinScore() { return minScore; }
        public float getMaxScore() { return maxScore; }
        public String getDisplayName() { return displayName; }
    }
    
    /**
     * Player preferences for matchmaking
     */
    public static class QueuePreferences {
        private Region preferredRegion = Region.AUTO;
        private int maxPing = 100;
        private boolean allowCrossPlatform = true;
        private boolean prioritizeSkillBalance = true;
        private boolean prioritizeFastMatching = false;
        private String preferredMap = null;
        
        // Getters and setters
        public Region getPreferredRegion() { return preferredRegion; }
        public void setPreferredRegion(Region preferredRegion) { this.preferredRegion = preferredRegion; }
        
        public int getMaxPing() { return maxPing; }
        public void setMaxPing(int maxPing) { this.maxPing = maxPing; }
        
        public boolean isAllowCrossPlatform() { return allowCrossPlatform; }
        public void setAllowCrossPlatform(boolean allowCrossPlatform) { this.allowCrossPlatform = allowCrossPlatform; }
        
        public boolean isPrioritizeSkillBalance() { return prioritizeSkillBalance; }
        public void setPrioritizeSkillBalance(boolean prioritizeSkillBalance) { this.prioritizeSkillBalance = prioritizeSkillBalance; }
        
        public boolean isPrioritizeFastMatching() { return prioritizeFastMatching; }
        public void setPrioritizeFastMatching(boolean prioritizeFastMatching) { this.prioritizeFastMatching = prioritizeFastMatching; }
        
        public String getPreferredMap() { return preferredMap; }
        public void setPreferredMap(String preferredMap) { this.preferredMap = preferredMap; }
    }
    
    /**
     * Represents a player in the matchmaking system
     */
    public static class MatchmakingPlayer {
        private final int playerId;
        private final PlayerRating rating;
        private final PlayerRank rank;
        private final PlayerStatistics statistics;
        private final long joinTime;
        private QueuePreferences preferences;
        private Region currentRegion;
        private int currentPing;
        
        public MatchmakingPlayer(int playerId, PlayerRating rating, PlayerRank rank, PlayerStatistics statistics) {
            this.playerId = playerId;
            this.rating = rating;
            this.rank = rank;
            this.statistics = statistics;
            this.joinTime = System.currentTimeMillis();
            this.preferences = new QueuePreferences();
            this.currentRegion = Region.AUTO;
            this.currentPing = 0;
        }
        
        public long getWaitTime() {
            return System.currentTimeMillis() - joinTime;
        }
        
        // Getters and setters
        public int getPlayerId() { return playerId; }
        public PlayerRating getRating() { return rating; }
        public PlayerRank getRank() { return rank; }
        public PlayerStatistics getStatistics() { return statistics; }
        public long getJoinTime() { return joinTime; }
        public QueuePreferences getPreferences() { return preferences; }
        public void setPreferences(QueuePreferences preferences) { this.preferences = preferences; }
        public Region getCurrentRegion() { return currentRegion; }
        public void setCurrentRegion(Region currentRegion) { this.currentRegion = currentRegion; }
        public int getCurrentPing() { return currentPing; }
        public void setCurrentPing(int currentPing) { this.currentPing = currentPing; }
    }
    
    /**
     * Result of a matchmaking operation
     */
    public static class MatchmakingResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> data;
        private long estimatedWaitTime = -1;
        
        private MatchmakingResult(boolean success, String message, Map<String, Object> data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
        
        public static MatchmakingResult success(String message) {
            return new MatchmakingResult(true, message, new HashMap<>());
        }
        
        public static MatchmakingResult failure(String message) {
            return new MatchmakingResult(false, message, new HashMap<>());
        }
        
        public MatchmakingResult withEstimatedWaitTime(long waitTime) {
            this.estimatedWaitTime = waitTime;
            return this;
        }
        
        public MatchmakingResult withData(String key, Object value) {
            this.data.put(key, value);
            return this;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Map<String, Object> getData() { return data; }
        public long getEstimatedWaitTime() { return estimatedWaitTime; }
    }
    
    /**
     * Result of a queue operation
     */
    public static class QueueResult {
        private final boolean success;
        private final String errorMessage;
        private final long estimatedWaitTime;
        
        private QueueResult(boolean success, String errorMessage, long estimatedWaitTime) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.estimatedWaitTime = estimatedWaitTime;
        }
        
        public static QueueResult success(long estimatedWaitTime) {
            return new QueueResult(true, null, estimatedWaitTime);
        }
        
        public static QueueResult failure(String errorMessage) {
            return new QueueResult(false, errorMessage, -1);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public long getEstimatedWaitTime() { return estimatedWaitTime; }
    }
    
    /**
     * Represents a potential match found by the matchmaking system
     */
    public static class PotentialMatch {
        private final List<MatchmakingPlayer> team1;
        private final List<MatchmakingPlayer> team2;
        private final float quality;
        private final long searchTime;
        private final String mapId;
        
        public PotentialMatch(List<MatchmakingPlayer> team1, List<MatchmakingPlayer> team2, 
                            float quality, long searchTime, String mapId) {
            this.team1 = team1;
            this.team2 = team2;
            this.quality = quality;
            this.searchTime = searchTime;
            this.mapId = mapId;
        }
        
        public List<MatchmakingPlayer> getAllPlayers() {
            List<MatchmakingPlayer> allPlayers = new java.util.ArrayList<>(team1);
            allPlayers.addAll(team2);
            return allPlayers;
        }
        
        // Getters
        public List<MatchmakingPlayer> getTeam1() { return team1; }
        public List<MatchmakingPlayer> getTeam2() { return team2; }
        public float getQuality() { return quality; }
        public long getSearchTime() { return searchTime; }
        public String getMapId() { return mapId; }
    }
    
    /**
     * Represents an active match
     */
    public static class ActiveMatch {
        private final String matchId;
        private final QueueType queueType;
        private final List<MatchmakingPlayer> allPlayers;
        private final List<MatchmakingPlayer> team1;
        private final List<MatchmakingPlayer> team2;
        private final float quality;
        private final long startTime;
        
        public ActiveMatch(String matchId, QueueType queueType, List<MatchmakingPlayer> allPlayers,
                          List<MatchmakingPlayer> team1, List<MatchmakingPlayer> team2, 
                          float quality, long startTime) {
            this.matchId = matchId;
            this.queueType = queueType;
            this.allPlayers = allPlayers;
            this.team1 = team1;
            this.team2 = team2;
            this.quality = quality;
            this.startTime = startTime;
        }
        
        public boolean hasPlayer(int playerId) {
            return allPlayers.stream().anyMatch(p -> p.getPlayerId() == playerId);
        }
        
        // Getters
        public String getMatchId() { return matchId; }
        public QueueType getQueueType() { return queueType; }
        public List<MatchmakingPlayer> getAllPlayers() { return allPlayers; }
        public List<MatchmakingPlayer> getTeam1() { return team1; }
        public List<MatchmakingPlayer> getTeam2() { return team2; }
        public float getQuality() { return quality; }
        public long getStartTime() { return startTime; }
    }
    
    /**
     * Result of a completed match
     */
    public static class MatchResult {
        private final String matchId;
        private final List<MatchmakingPlayer> winners;
        private final List<MatchmakingPlayer> losers;
        private final Map<Integer, PlayerMatchPerformance> playerPerformances;
        private final long matchDuration;
        private final boolean wasForfeited;
        
        public MatchResult(String matchId, List<MatchmakingPlayer> winners, List<MatchmakingPlayer> losers,
                          Map<Integer, PlayerMatchPerformance> playerPerformances, 
                          long matchDuration, boolean wasForfeited) {
            this.matchId = matchId;
            this.winners = winners;
            this.losers = losers;
            this.playerPerformances = playerPerformances;
            this.matchDuration = matchDuration;
            this.wasForfeited = wasForfeited;
        }
        
        // Getters
        public String getMatchId() { return matchId; }
        public List<MatchmakingPlayer> getWinners() { return winners; }
        public List<MatchmakingPlayer> getLosers() { return losers; }
        public Map<Integer, PlayerMatchPerformance> getPlayerPerformances() { return playerPerformances; }
        public long getMatchDuration() { return matchDuration; }
        public boolean wasForfeited() { return wasForfeited; }
    }
    
    /**
     * Player performance in a specific match
     */
    public static class PlayerMatchPerformance {
        private final int playerId;
        private final int kills;
        private final int deaths;
        private final int assists;
        private final float damageDealt;
        private final float accuracy;
        private final int objectiveScore;
        private final boolean mvp;
        
        public PlayerMatchPerformance(int playerId, int kills, int deaths, int assists,
                                    float damageDealt, float accuracy, int objectiveScore, boolean mvp) {
            this.playerId = playerId;
            this.kills = kills;
            this.deaths = deaths;
            this.assists = assists;
            this.damageDealt = damageDealt;
            this.accuracy = accuracy;
            this.objectiveScore = objectiveScore;
            this.mvp = mvp;
        }
        
        public float getKDRatio() {
            return deaths > 0 ? (float) kills / deaths : kills;
        }
        
        // Getters
        public int getPlayerId() { return playerId; }
        public int getKills() { return kills; }
        public int getDeaths() { return deaths; }
        public int getAssists() { return assists; }
        public float getDamageDealt() { return damageDealt; }
        public float getAccuracy() { return accuracy; }
        public int getObjectiveScore() { return objectiveScore; }
        public boolean isMvp() { return mvp; }
    }
    
    /**
     * Queue health metrics
     */
    public static class QueueHealth {
        private final QueueType queueType;
        private final int queuedPlayerCount;
        private final long averageWaitTime;
        private final float matchSuccessRate;
        private final int matchesFormedLastHour;
        
        public QueueHealth(QueueType queueType, int queuedPlayerCount, long averageWaitTime,
                          float matchSuccessRate, int matchesFormedLastHour) {
            this.queueType = queueType;
            this.queuedPlayerCount = queuedPlayerCount;
            this.averageWaitTime = averageWaitTime;
            this.matchSuccessRate = matchSuccessRate;
            this.matchesFormedLastHour = matchesFormedLastHour;
        }
        
        // Getters
        public QueueType getQueueType() { return queueType; }
        public int getQueuedPlayerCount() { return queuedPlayerCount; }
        public long getAverageWaitTime() { return averageWaitTime; }
        public float getMatchSuccessRate() { return matchSuccessRate; }
        public int getMatchesFormedLastHour() { return matchesFormedLastHour; }
    }
}