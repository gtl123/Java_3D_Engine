package fps.matchmaking.statistics;

import engine.logging.LogManager;
import fps.matchmaking.MatchmakingConfiguration;
import fps.matchmaking.MatchmakingTypes.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Comprehensive player statistics and progression tracking system
 */
public class PlayerStatisticsManager {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final MatchmakingConfiguration config;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Player statistics storage
    private final Map<String, PlayerStatistics> playerStats = new ConcurrentHashMap<>();
    
    // Achievement and progression tracking
    private final AchievementManager achievementManager;
    private final ProgressionManager progressionManager;
    private final PerformanceAnalytics performanceAnalytics;
    
    // Statistics aggregation
    private final Map<GameMode, GameModeStatistics> gameModeStats = new ConcurrentHashMap<>();
    private final Map<String, SeasonStatistics> seasonStats = new ConcurrentHashMap<>();
    
    private volatile boolean initialized = false;
    
    public PlayerStatisticsManager(MatchmakingConfiguration config) {
        this.config = config;
        this.achievementManager = new AchievementManager(config);
        this.progressionManager = new ProgressionManager(config);
        this.performanceAnalytics = new PerformanceAnalytics(config);
    }
    
    public void initialize() {
        achievementManager.initialize();
        progressionManager.initialize();
        performanceAnalytics.initialize();
        
        // Initialize game mode statistics
        for (GameMode mode : GameMode.values()) {
            gameModeStats.put(mode, new GameModeStatistics(mode));
        }
        
        initialized = true;
        
        logManager.info("PlayerStatisticsManager", "Player statistics manager initialized");
    }
    
    /**
     * Record match result and update player statistics
     */
    public void recordMatchResult(MatchResult matchResult) {
        lock.writeLock().lock();
        try {
            for (Map.Entry<String, PlayerMatchResult> entry : matchResult.getPlayerResults().entrySet()) {
                String playerId = entry.getKey();
                PlayerMatchResult playerResult = entry.getValue();
                
                // Update player statistics
                updatePlayerStatistics(playerId, playerResult, matchResult);
                
                // Check for achievements
                achievementManager.checkAchievements(playerId, playerResult, getPlayerStatistics(playerId));
                
                // Update progression
                progressionManager.updateProgression(playerId, playerResult, getPlayerStatistics(playerId));
                
                // Update analytics
                performanceAnalytics.recordPerformance(playerId, playerResult, matchResult);
            }
            
            // Update game mode statistics
            updateGameModeStatistics(matchResult);
            
            // Update seasonal statistics
            updateSeasonalStatistics(matchResult);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Update individual player statistics
     */
    private void updatePlayerStatistics(String playerId, PlayerMatchResult playerResult, MatchResult matchResult) {
        PlayerStatistics stats = playerStats.computeIfAbsent(playerId, k -> new PlayerStatistics(playerId));
        
        // Update match statistics
        stats.recordMatch(playerResult, matchResult);
        
        logManager.debug("PlayerStatisticsManager", "Updated player statistics",
                        "playerId", playerId,
                        "totalMatches", stats.getTotalMatches(),
                        "winRate", stats.getWinRate());
    }
    
    /**
     * Get player statistics
     */
    public PlayerStatistics getPlayerStatistics(String playerId) {
        lock.readLock().lock();
        try {
            return playerStats.get(playerId);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get player statistics for specific game mode
     */
    public GameModePlayerStats getPlayerGameModeStats(String playerId, GameMode gameMode) {
        PlayerStatistics stats = getPlayerStatistics(playerId);
        return stats != null ? stats.getGameModeStats(gameMode) : null;
    }
    
    /**
     * Get player performance trends
     */
    public PerformanceTrend getPlayerPerformanceTrend(String playerId, int recentMatches) {
        return performanceAnalytics.getPerformanceTrend(playerId, recentMatches);
    }
    
    /**
     * Get player achievements
     */
    public List<Achievement> getPlayerAchievements(String playerId) {
        return achievementManager.getPlayerAchievements(playerId);
    }
    
    /**
     * Get player progression status
     */
    public PlayerProgression getPlayerProgression(String playerId) {
        return progressionManager.getPlayerProgression(playerId);
    }
    
    /**
     * Get leaderboard for specific statistic
     */
    public List<LeaderboardEntry> getStatisticLeaderboard(StatisticType type, GameMode gameMode, int limit) {
        lock.readLock().lock();
        try {
            return playerStats.values().stream()
                             .filter(stats -> gameMode == null || stats.hasPlayedGameMode(gameMode))
                             .map(stats -> createLeaderboardEntry(stats, type, gameMode))
                             .filter(Objects::nonNull)
                             .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                             .limit(limit)
                             .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Create leaderboard entry for statistic
     */
    private LeaderboardEntry createLeaderboardEntry(PlayerStatistics stats, StatisticType type, GameMode gameMode) {
        double value = getStatisticValue(stats, type, gameMode);
        if (value < 0) return null; // Invalid statistic
        
        return new LeaderboardEntry(stats.getPlayerId(), type, gameMode, value, stats.getTotalMatches());
    }
    
    /**
     * Get statistic value for player
     */
    private double getStatisticValue(PlayerStatistics stats, StatisticType type, GameMode gameMode) {
        GameModePlayerStats modeStats = gameMode != null ? stats.getGameModeStats(gameMode) : null;
        
        switch (type) {
            case WIN_RATE:
                return gameMode != null ? modeStats.getWinRate() : stats.getWinRate();
            case KD_RATIO:
                return gameMode != null ? modeStats.getKdRatio() : stats.getOverallKdRatio();
            case AVERAGE_DAMAGE:
                return gameMode != null ? modeStats.getAverageDamage() : stats.getAverageDamage();
            case ACCURACY:
                return gameMode != null ? modeStats.getAverageAccuracy() : stats.getAverageAccuracy();
            case OBJECTIVE_SCORE:
                return gameMode != null ? modeStats.getAverageObjectiveScore() : stats.getAverageObjectiveScore();
            case TOTAL_KILLS:
                return gameMode != null ? modeStats.getTotalKills() : stats.getTotalKills();
            case TOTAL_MATCHES:
                return gameMode != null ? modeStats.getMatchesPlayed() : stats.getTotalMatches();
            default:
                return -1;
        }
    }
    
    /**
     * Update game mode statistics
     */
    private void updateGameModeStatistics(MatchResult matchResult) {
        GameModeStatistics stats = gameModeStats.get(matchResult.getGameMode());
        if (stats != null) {
            stats.recordMatch(matchResult);
        }
    }
    
    /**
     * Update seasonal statistics
     */
    private void updateSeasonalStatistics(MatchResult matchResult) {
        String seasonKey = getCurrentSeasonKey();
        SeasonStatistics seasonStats = this.seasonStats.computeIfAbsent(seasonKey, 
                                                                       k -> new SeasonStatistics(seasonKey));
        seasonStats.recordMatch(matchResult);
    }
    
    /**
     * Get current season key
     */
    private String getCurrentSeasonKey() {
        // Simple season key based on current time - could be enhanced with actual season system
        long currentTime = System.currentTimeMillis();
        long seasonLength = 90L * 24 * 60 * 60 * 1000; // 90 days
        long seasonNumber = currentTime / seasonLength;
        return "Season_" + seasonNumber;
    }
    
    /**
     * Get game mode statistics
     */
    public GameModeStatistics getGameModeStatistics(GameMode gameMode) {
        return gameModeStats.get(gameMode);
    }
    
    /**
     * Get seasonal statistics
     */
    public SeasonStatistics getSeasonalStatistics(String seasonKey) {
        return seasonStats.get(seasonKey);
    }
    
    /**
     * Get current season statistics
     */
    public SeasonStatistics getCurrentSeasonStatistics() {
        return getSeasonalStatistics(getCurrentSeasonKey());
    }
    
    /**
     * Get player comparison
     */
    public PlayerComparison comparePlayer(String playerId, String compareToId) {
        PlayerStatistics playerStats = getPlayerStatistics(playerId);
        PlayerStatistics compareStats = getPlayerStatistics(compareToId);
        
        if (playerStats == null || compareStats == null) {
            return null;
        }
        
        return new PlayerComparison(playerStats, compareStats);
    }
    
    /**
     * Get performance insights for player
     */
    public PerformanceInsights getPerformanceInsights(String playerId) {
        return performanceAnalytics.getPerformanceInsights(playerId);
    }
    
    /**
     * Reset player statistics (for new seasons, etc.)
     */
    public void resetPlayerStatistics(String playerId) {
        lock.writeLock().lock();
        try {
            PlayerStatistics stats = playerStats.get(playerId);
            if (stats != null) {
                stats.reset();
                logManager.info("PlayerStatisticsManager", "Reset player statistics",
                               "playerId", playerId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get top performers for period
     */
    public Map<StatisticType, List<LeaderboardEntry>> getTopPerformers(int limit) {
        Map<StatisticType, List<LeaderboardEntry>> topPerformers = new HashMap<>();
        
        for (StatisticType type : StatisticType.values()) {
            topPerformers.put(type, getStatisticLeaderboard(type, null, limit));
        }
        
        return topPerformers;
    }
    
    /**
     * Export player statistics
     */
    public Map<String, Object> exportPlayerStatistics(String playerId) {
        PlayerStatistics stats = getPlayerStatistics(playerId);
        if (stats == null) return new HashMap<>();
        
        Map<String, Object> export = new HashMap<>();
        export.put("playerId", playerId);
        export.put("statistics", stats.toMap());
        export.put("achievements", getPlayerAchievements(playerId));
        export.put("progression", getPlayerProgression(playerId));
        export.put("performanceTrend", getPlayerPerformanceTrend(playerId, 20));
        
        return export;
    }
    
    public void cleanup() {
        lock.writeLock().lock();
        try {
            playerStats.clear();
            gameModeStats.clear();
            seasonStats.clear();
            
            achievementManager.cleanup();
            progressionManager.cleanup();
            performanceAnalytics.cleanup();
            
            initialized = false;
            
            logManager.info("PlayerStatisticsManager", "Player statistics manager cleaned up");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // Getters
    public boolean isInitialized() { return initialized; }
    public AchievementManager getAchievementManager() { return achievementManager; }
    public ProgressionManager getProgressionManager() { return progressionManager; }
    public PerformanceAnalytics getPerformanceAnalytics() { return performanceAnalytics; }
    
    /**
     * Statistic types for leaderboards
     */
    public enum StatisticType {
        WIN_RATE,
        KD_RATIO,
        AVERAGE_DAMAGE,
        ACCURACY,
        OBJECTIVE_SCORE,
        TOTAL_KILLS,
        TOTAL_MATCHES
    }
    
    /**
     * Leaderboard entry data class
     */
    public static class LeaderboardEntry {
        private final String playerId;
        private final StatisticType type;
        private final GameMode gameMode;
        private final double value;
        private final int totalMatches;
        
        public LeaderboardEntry(String playerId, StatisticType type, GameMode gameMode, 
                              double value, int totalMatches) {
            this.playerId = playerId;
            this.type = type;
            this.gameMode = gameMode;
            this.value = value;
            this.totalMatches = totalMatches;
        }
        
        // Getters
        public String getPlayerId() { return playerId; }
        public StatisticType getType() { return type; }
        public GameMode getGameMode() { return gameMode; }
        public double getValue() { return value; }
        public int getTotalMatches() { return totalMatches; }
        
        @Override
        public String toString() {
            return String.format("%s: %.2f (%s, %d matches)", 
                               playerId, value, type, totalMatches);
        }
    }
}