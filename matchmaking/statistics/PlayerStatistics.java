package fps.matchmaking.statistics;

import fps.matchmaking.MatchmakingTypes.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive player statistics tracking
 */
public class PlayerStatistics {
    
    private final String playerId;
    private final long createdTime;
    
    // Overall statistics
    private final AtomicInteger totalMatches = new AtomicInteger(0);
    private final AtomicInteger totalWins = new AtomicInteger(0);
    private final AtomicInteger totalLosses = new AtomicInteger(0);
    private final AtomicInteger totalKills = new AtomicInteger(0);
    private final AtomicInteger totalDeaths = new AtomicInteger(0);
    private final AtomicInteger totalAssists = new AtomicInteger(0);
    private final AtomicLong totalDamage = new AtomicLong(0);
    private final AtomicLong totalPlayTime = new AtomicLong(0);
    
    // Performance metrics
    private volatile double averageAccuracy = 0.0;
    private volatile double averageObjectiveScore = 0.0;
    private volatile int longestKillStreak = 0;
    private volatile int totalMultiKills = 0;
    private volatile int totalClutchWins = 0;
    
    // Game mode specific statistics
    private final Map<GameMode, GameModePlayerStats> gameModeStats = new ConcurrentHashMap<>();
    
    // Recent performance tracking
    private final List<MatchPerformance> recentMatches = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_RECENT_MATCHES = 50;
    
    // Streaks and achievements
    private volatile int currentWinStreak = 0;
    private volatile int longestWinStreak = 0;
    private volatile int currentLossStreak = 0;
    private volatile long lastMatchTime = 0;
    
    public PlayerStatistics(String playerId) {
        this.playerId = playerId;
        this.createdTime = System.currentTimeMillis();
        
        // Initialize game mode stats
        for (GameMode mode : GameMode.values()) {
            gameModeStats.put(mode, new GameModePlayerStats(mode));
        }
    }
    
    /**
     * Record a match result
     */
    public synchronized void recordMatch(PlayerMatchResult playerResult, MatchResult matchResult) {
        // Update overall statistics
        totalMatches.incrementAndGet();
        
        if (playerResult.isWinner()) {
            totalWins.incrementAndGet();
            currentWinStreak++;
            longestWinStreak = Math.max(longestWinStreak, currentWinStreak);
            currentLossStreak = 0;
        } else {
            totalLosses.incrementAndGet();
            currentLossStreak++;
            currentWinStreak = 0;
        }
        
        // Update combat statistics
        totalKills.addAndGet(playerResult.getKills());
        totalDeaths.addAndGet(playerResult.getDeaths());
        totalAssists.addAndGet(playerResult.getAssists());
        totalDamage.addAndGet((long) playerResult.getDamageDealt());
        totalPlayTime.addAndGet(matchResult.getMatchDurationMs());
        
        // Update performance metrics
        updatePerformanceMetrics(playerResult);
        
        // Update game mode specific stats
        GameModePlayerStats modeStats = gameModeStats.get(matchResult.getGameMode());
        if (modeStats != null) {
            modeStats.recordMatch(playerResult, matchResult);
        }
        
        // Track recent performance
        addRecentMatch(playerResult, matchResult);
        
        lastMatchTime = System.currentTimeMillis();
    }
    
    /**
     * Update performance metrics with rolling averages
     */
    private void updatePerformanceMetrics(PlayerMatchResult playerResult) {
        int matches = totalMatches.get();
        
        // Update rolling averages
        averageAccuracy = ((averageAccuracy * (matches - 1)) + playerResult.getAccuracy()) / matches;
        averageObjectiveScore = ((averageObjectiveScore * (matches - 1)) + playerResult.getObjectiveScore()) / matches;
        
        // Update maximums
        longestKillStreak = Math.max(longestKillStreak, playerResult.getKillStreak());
        totalMultiKills += playerResult.getMultiKills();
        totalClutchWins += playerResult.getClutchWins();
    }
    
    /**
     * Add match to recent performance tracking
     */
    private void addRecentMatch(PlayerMatchResult playerResult, MatchResult matchResult) {
        MatchPerformance performance = new MatchPerformance(
            matchResult.getMatchId(),
            matchResult.getGameMode(),
            playerResult.isWinner(),
            playerResult.getKills(),
            playerResult.getDeaths(),
            playerResult.getAssists(),
            playerResult.getDamageDealt(),
            playerResult.getAccuracy(),
            playerResult.getObjectiveScore(),
            matchResult.getMatchDurationMs(),
            System.currentTimeMillis()
        );
        
        recentMatches.add(performance);
        
        // Keep only recent matches
        if (recentMatches.size() > MAX_RECENT_MATCHES) {
            recentMatches.remove(0);
        }
    }
    
    /**
     * Get win rate percentage
     */
    public double getWinRate() {
        int matches = totalMatches.get();
        return matches > 0 ? (double) totalWins.get() / matches : 0.0;
    }
    
    /**
     * Get overall K/D ratio
     */
    public double getOverallKdRatio() {
        int deaths = totalDeaths.get();
        return deaths > 0 ? (double) totalKills.get() / deaths : totalKills.get();
    }
    
    /**
     * Get average damage per match
     */
    public double getAverageDamage() {
        int matches = totalMatches.get();
        return matches > 0 ? (double) totalDamage.get() / matches : 0.0;
    }
    
    /**
     * Get average playtime per match (in minutes)
     */
    public double getAveragePlayTime() {
        int matches = totalMatches.get();
        return matches > 0 ? (double) totalPlayTime.get() / (matches * 60000.0) : 0.0;
    }
    
    /**
     * Get recent performance trend
     */
    public PerformanceTrend getRecentTrend(int matchCount) {
        int count = Math.min(matchCount, recentMatches.size());
        if (count == 0) return new PerformanceTrend(0, 0.0, 0.0, 0.0);
        
        List<MatchPerformance> recent = recentMatches.subList(
            Math.max(0, recentMatches.size() - count), recentMatches.size());
        
        int wins = (int) recent.stream().mapToInt(m -> m.isWin() ? 1 : 0).sum();
        double avgKd = recent.stream().mapToDouble(m -> 
            m.getDeaths() > 0 ? (double) m.getKills() / m.getDeaths() : m.getKills()).average().orElse(0.0);
        double avgDamage = recent.stream().mapToDouble(MatchPerformance::getDamage).average().orElse(0.0);
        double avgAccuracy = recent.stream().mapToDouble(MatchPerformance::getAccuracy).average().orElse(0.0);
        
        return new PerformanceTrend(count, (double) wins / count, avgKd, avgDamage, avgAccuracy);
    }
    
    /**
     * Check if player has played specific game mode
     */
    public boolean hasPlayedGameMode(GameMode gameMode) {
        GameModePlayerStats stats = gameModeStats.get(gameMode);
        return stats != null && stats.getMatchesPlayed() > 0;
    }
    
    /**
     * Get game mode statistics
     */
    public GameModePlayerStats getGameModeStats(GameMode gameMode) {
        return gameModeStats.get(gameMode);
    }
    
    /**
     * Get performance rating based on multiple factors
     */
    public double getPerformanceRating() {
        if (totalMatches.get() == 0) return 0.0;
        
        double winRateScore = getWinRate() * 100;
        double kdScore = Math.min(getOverallKdRatio() * 50, 100);
        double accuracyScore = averageAccuracy * 100;
        double objectiveScore = Math.min(averageObjectiveScore / 10.0, 100);
        
        // Weighted average
        return (winRateScore * 0.3) + (kdScore * 0.25) + (accuracyScore * 0.25) + (objectiveScore * 0.2);
    }
    
    /**
     * Get activity level based on recent matches
     */
    public ActivityLevel getActivityLevel() {
        long timeSinceLastMatch = System.currentTimeMillis() - lastMatchTime;
        long daysSinceLastMatch = timeSinceLastMatch / (24 * 60 * 60 * 1000);
        
        if (daysSinceLastMatch <= 1) return ActivityLevel.VERY_ACTIVE;
        if (daysSinceLastMatch <= 3) return ActivityLevel.ACTIVE;
        if (daysSinceLastMatch <= 7) return ActivityLevel.MODERATE;
        if (daysSinceLastMatch <= 30) return ActivityLevel.INACTIVE;
        return ActivityLevel.DORMANT;
    }
    
    /**
     * Reset all statistics
     */
    public synchronized void reset() {
        totalMatches.set(0);
        totalWins.set(0);
        totalLosses.set(0);
        totalKills.set(0);
        totalDeaths.set(0);
        totalAssists.set(0);
        totalDamage.set(0);
        totalPlayTime.set(0);
        
        averageAccuracy = 0.0;
        averageObjectiveScore = 0.0;
        longestKillStreak = 0;
        totalMultiKills = 0;
        totalClutchWins = 0;
        
        currentWinStreak = 0;
        longestWinStreak = 0;
        currentLossStreak = 0;
        
        gameModeStats.values().forEach(GameModePlayerStats::reset);
        recentMatches.clear();
    }
    
    /**
     * Export statistics to map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        
        // Basic info
        map.put("playerId", playerId);
        map.put("createdTime", createdTime);
        map.put("lastMatchTime", lastMatchTime);
        
        // Overall stats
        map.put("totalMatches", totalMatches.get());
        map.put("totalWins", totalWins.get());
        map.put("totalLosses", totalLosses.get());
        map.put("winRate", getWinRate());
        
        // Combat stats
        map.put("totalKills", totalKills.get());
        map.put("totalDeaths", totalDeaths.get());
        map.put("totalAssists", totalAssists.get());
        map.put("kdRatio", getOverallKdRatio());
        
        // Performance stats
        map.put("totalDamage", totalDamage.get());
        map.put("averageDamage", getAverageDamage());
        map.put("averageAccuracy", averageAccuracy);
        map.put("averageObjectiveScore", averageObjectiveScore);
        map.put("performanceRating", getPerformanceRating());
        
        // Achievements
        map.put("longestKillStreak", longestKillStreak);
        map.put("longestWinStreak", longestWinStreak);
        map.put("totalMultiKills", totalMultiKills);
        map.put("totalClutchWins", totalClutchWins);
        
        // Current streaks
        map.put("currentWinStreak", currentWinStreak);
        map.put("currentLossStreak", currentLossStreak);
        
        // Activity
        map.put("activityLevel", getActivityLevel());
        map.put("totalPlayTime", totalPlayTime.get());
        map.put("averagePlayTime", getAveragePlayTime());
        
        // Game mode stats
        Map<String, Object> gameModeData = new HashMap<>();
        for (Map.Entry<GameMode, GameModePlayerStats> entry : gameModeStats.entrySet()) {
            if (entry.getValue().getMatchesPlayed() > 0) {
                gameModeData.put(entry.getKey().toString(), entry.getValue().toMap());
            }
        }
        map.put("gameModeStats", gameModeData);
        
        return map;
    }
    
    // Getters
    public String getPlayerId() { return playerId; }
    public long getCreatedTime() { return createdTime; }
    public int getTotalMatches() { return totalMatches.get(); }
    public int getTotalWins() { return totalWins.get(); }
    public int getTotalLosses() { return totalLosses.get(); }
    public int getTotalKills() { return totalKills.get(); }
    public int getTotalDeaths() { return totalDeaths.get(); }
    public int getTotalAssists() { return totalAssists.get(); }
    public long getTotalDamage() { return totalDamage.get(); }
    public long getTotalPlayTime() { return totalPlayTime.get(); }
    public double getAverageAccuracy() { return averageAccuracy; }
    public double getAverageObjectiveScore() { return averageObjectiveScore; }
    public int getLongestKillStreak() { return longestKillStreak; }
    public int getTotalMultiKills() { return totalMultiKills; }
    public int getTotalClutchWins() { return totalClutchWins; }
    public int getCurrentWinStreak() { return currentWinStreak; }
    public int getLongestWinStreak() { return longestWinStreak; }
    public int getCurrentLossStreak() { return currentLossStreak; }
    public long getLastMatchTime() { return lastMatchTime; }
    public List<MatchPerformance> getRecentMatches() { return new ArrayList<>(recentMatches); }
    
    /**
     * Activity level enumeration
     */
    public enum ActivityLevel {
        VERY_ACTIVE,
        ACTIVE,
        MODERATE,
        INACTIVE,
        DORMANT
    }
    
    /**
     * Performance trend data class
     */
    public static class PerformanceTrend {
        private final int matchCount;
        private final double winRate;
        private final double averageKd;
        private final double averageDamage;
        private final double averageAccuracy;
        
        public PerformanceTrend(int matchCount, double winRate, double averageKd, 
                              double averageDamage, double averageAccuracy) {
            this.matchCount = matchCount;
            this.winRate = winRate;
            this.averageKd = averageKd;
            this.averageDamage = averageDamage;
            this.averageAccuracy = averageAccuracy;
        }
        
        public PerformanceTrend(int matchCount, double winRate, double averageKd, double averageDamage) {
            this(matchCount, winRate, averageKd, averageDamage, 0.0);
        }
        
        // Getters
        public int getMatchCount() { return matchCount; }
        public double getWinRate() { return winRate; }
        public double getAverageKd() { return averageKd; }
        public double getAverageDamage() { return averageDamage; }
        public double getAverageAccuracy() { return averageAccuracy; }
        
        @Override
        public String toString() {
            return String.format("Trend (%d matches): %.1f%% WR, %.2f K/D, %.0f DMG, %.1f%% ACC",
                               matchCount, winRate * 100, averageKd, averageDamage, averageAccuracy * 100);
        }
    }
    
    /**
     * Individual match performance record
     */
    public static class MatchPerformance {
        private final String matchId;
        private final GameMode gameMode;
        private final boolean win;
        private final int kills;
        private final int deaths;
        private final int assists;
        private final double damage;
        private final double accuracy;
        private final double objectiveScore;
        private final long duration;
        private final long timestamp;
        
        public MatchPerformance(String matchId, GameMode gameMode, boolean win, int kills, int deaths,
                              int assists, double damage, double accuracy, double objectiveScore,
                              long duration, long timestamp) {
            this.matchId = matchId;
            this.gameMode = gameMode;
            this.win = win;
            this.kills = kills;
            this.deaths = deaths;
            this.assists = assists;
            this.damage = damage;
            this.accuracy = accuracy;
            this.objectiveScore = objectiveScore;
            this.duration = duration;
            this.timestamp = timestamp;
        }
        
        // Getters
        public String getMatchId() { return matchId; }
        public GameMode getGameMode() { return gameMode; }
        public boolean isWin() { return win; }
        public int getKills() { return kills; }
        public int getDeaths() { return deaths; }
        public int getAssists() { return assists; }
        public double getDamage() { return damage; }
        public double getAccuracy() { return accuracy; }
        public double getObjectiveScore() { return objectiveScore; }
        public long getDuration() { return duration; }
        public long getTimestamp() { return timestamp; }
        
        public double getKdRatio() {
            return deaths > 0 ? (double) kills / deaths : kills;
        }
    }
}