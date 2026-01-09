package fps.matchmaking.gamemode;

import engine.logging.LogManager;
import fps.matchmaking.MatchmakingConfiguration;
import fps.matchmaking.MatchmakingTypes.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages different game modes and their specific rules
 */
public class GameModeManager {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final MatchmakingConfiguration config;
    private final Map<GameMode, GameModeHandler> gameModeHandlers = new ConcurrentHashMap<>();
    
    // Game mode statistics
    private final Map<GameMode, GameModeStats> gameModeStats = new ConcurrentHashMap<>();
    
    private volatile boolean initialized = false;
    
    public GameModeManager(MatchmakingConfiguration config) {
        this.config = config;
    }
    
    public void initialize() {
        // Initialize game mode handlers
        gameModeHandlers.put(GameMode.TEAM_DEATHMATCH, new TeamDeathmatchHandler(config));
        gameModeHandlers.put(GameMode.SEARCH_AND_DESTROY, new SearchAndDestroyHandler(config));
        gameModeHandlers.put(GameMode.CAPTURE_THE_FLAG, new CaptureTheFlagHandler(config));
        gameModeHandlers.put(GameMode.KING_OF_THE_HILL, new KingOfTheHillHandler(config));
        gameModeHandlers.put(GameMode.CLAN_WARFARE, new ClanWarfareHandler(config));
        
        // Initialize statistics
        for (GameMode mode : GameMode.values()) {
            gameModeStats.put(mode, new GameModeStats(mode));
        }
        
        initialized = true;
        
        logManager.info("GameModeManager", "Game mode manager initialized",
                       "supportedModes", gameModeHandlers.size());
    }
    
    /**
     * Get handler for specific game mode
     */
    public GameModeHandler getHandler(GameMode gameMode) {
        return gameModeHandlers.get(gameMode);
    }
    
    /**
     * Check if game mode is supported
     */
    public boolean isSupported(GameMode gameMode) {
        return gameModeHandlers.containsKey(gameMode);
    }
    
    /**
     * Get game mode configuration
     */
    public GameModeConfig getGameModeConfig(GameMode gameMode) {
        GameModeHandler handler = gameModeHandlers.get(gameMode);
        return handler != null ? handler.getConfig() : null;
    }
    
    /**
     * Validate match setup for game mode
     */
    public boolean validateMatchSetup(GameMode gameMode, List<String> teamA, List<String> teamB) {
        GameModeHandler handler = gameModeHandlers.get(gameMode);
        if (handler == null) return false;
        
        return handler.validateTeamSetup(teamA, teamB);
    }
    
    /**
     * Calculate rating changes for game mode
     */
    public Map<String, Double> calculateRatingChanges(GameMode gameMode, MatchResult matchResult) {
        GameModeHandler handler = gameModeHandlers.get(gameMode);
        if (handler == null) return new HashMap<>();
        
        Map<String, Double> changes = handler.calculateRatingChanges(matchResult);
        
        // Update statistics
        updateGameModeStats(gameMode, matchResult);
        
        return changes;
    }
    
    /**
     * Get recommended team size for game mode
     */
    public int getRecommendedTeamSize(GameMode gameMode) {
        GameModeHandler handler = gameModeHandlers.get(gameMode);
        return handler != null ? handler.getRecommendedTeamSize() : 5;
    }
    
    /**
     * Get match duration for game mode
     */
    public long getMatchDurationMs(GameMode gameMode) {
        GameModeHandler handler = gameModeHandlers.get(gameMode);
        return handler != null ? handler.getMatchDurationMs() : 600000; // 10 minutes default
    }
    
    /**
     * Check if game mode supports ranked play
     */
    public boolean supportsRankedPlay(GameMode gameMode) {
        GameModeHandler handler = gameModeHandlers.get(gameMode);
        return handler != null && handler.supportsRankedPlay();
    }
    
    /**
     * Get game mode popularity (based on recent matches)
     */
    public double getGameModePopularity(GameMode gameMode) {
        GameModeStats stats = gameModeStats.get(gameMode);
        if (stats == null) return 0.0;
        
        long totalMatches = gameModeStats.values().stream()
                                        .mapToLong(GameModeStats::getTotalMatches)
                                        .sum();
        
        return totalMatches > 0 ? (double) stats.getTotalMatches() / totalMatches : 0.0;
    }
    
    /**
     * Get available game modes for queue type
     */
    public List<GameMode> getAvailableGameModes(QueueType queueType) {
        List<GameMode> availableModes = new ArrayList<>();
        
        for (Map.Entry<GameMode, GameModeHandler> entry : gameModeHandlers.entrySet()) {
            GameMode mode = entry.getKey();
            GameModeHandler handler = entry.getValue();
            
            switch (queueType) {
                case RANKED:
                    if (handler.supportsRankedPlay()) {
                        availableModes.add(mode);
                    }
                    break;
                case CASUAL:
                    availableModes.add(mode);
                    break;
                case COMPETITIVE:
                    if (handler.supportsRankedPlay()) {
                        availableModes.add(mode);
                    }
                    break;
            }
        }
        
        return availableModes;
    }
    
    /**
     * Update game mode statistics
     */
    private void updateGameModeStats(GameMode gameMode, MatchResult matchResult) {
        GameModeStats stats = gameModeStats.get(gameMode);
        if (stats != null) {
            stats.recordMatch(matchResult);
        }
    }
    
    /**
     * Get game mode statistics
     */
    public GameModeStats getGameModeStats(GameMode gameMode) {
        return gameModeStats.get(gameMode);
    }
    
    /**
     * Get all game mode statistics
     */
    public Map<GameMode, GameModeStats> getAllGameModeStats() {
        return new HashMap<>(gameModeStats);
    }
    
    /**
     * Reset statistics for game mode
     */
    public void resetGameModeStats(GameMode gameMode) {
        GameModeStats stats = gameModeStats.get(gameMode);
        if (stats != null) {
            stats.reset();
        }
        
        logManager.info("GameModeManager", "Game mode statistics reset",
                       "gameMode", gameMode);
    }
    
    /**
     * Get most popular game modes
     */
    public List<GameMode> getMostPopularGameModes(int limit) {
        return gameModeStats.entrySet().stream()
                           .sorted((a, b) -> Long.compare(b.getValue().getTotalMatches(), 
                                                        a.getValue().getTotalMatches()))
                           .limit(limit)
                           .map(Map.Entry::getKey)
                           .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    public void cleanup() {
        gameModeHandlers.clear();
        gameModeStats.clear();
        initialized = false;
        
        logManager.info("GameModeManager", "Game mode manager cleaned up");
    }
    
    // Getters
    public boolean isInitialized() { return initialized; }
    public Set<GameMode> getSupportedGameModes() { return new HashSet<>(gameModeHandlers.keySet()); }
    
    /**
     * Game mode statistics tracking
     */
    public static class GameModeStats {
        private final GameMode gameMode;
        private volatile long totalMatches = 0;
        private volatile long totalPlayTime = 0;
        private volatile double averageMatchDuration = 0.0;
        private volatile double averageRatingChange = 0.0;
        private final Map<String, Long> mapPlayCounts = new ConcurrentHashMap<>();
        
        public GameModeStats(GameMode gameMode) {
            this.gameMode = gameMode;
        }
        
        public void recordMatch(MatchResult matchResult) {
            totalMatches++;
            
            long matchDuration = matchResult.getMatchDurationMs();
            totalPlayTime += matchDuration;
            averageMatchDuration = (double) totalPlayTime / totalMatches;
            
            // Track map usage
            String mapName = matchResult.getMapName();
            if (mapName != null) {
                mapPlayCounts.merge(mapName, 1L, Long::sum);
            }
            
            // Calculate average rating change
            double totalRatingChange = matchResult.getPlayerResults().values().stream()
                                                 .mapToDouble(PlayerMatchResult::getRatingChange)
                                                 .map(Math::abs)
                                                 .sum();
            
            averageRatingChange = ((averageRatingChange * (totalMatches - 1)) + 
                                 (totalRatingChange / matchResult.getPlayerResults().size())) / totalMatches;
        }
        
        public void reset() {
            totalMatches = 0;
            totalPlayTime = 0;
            averageMatchDuration = 0.0;
            averageRatingChange = 0.0;
            mapPlayCounts.clear();
        }
        
        // Getters
        public GameMode getGameMode() { return gameMode; }
        public long getTotalMatches() { return totalMatches; }
        public long getTotalPlayTime() { return totalPlayTime; }
        public double getAverageMatchDuration() { return averageMatchDuration; }
        public double getAverageRatingChange() { return averageRatingChange; }
        public Map<String, Long> getMapPlayCounts() { return new HashMap<>(mapPlayCounts); }
        
        @Override
        public String toString() {
            return String.format("%s: %d matches, %.1f min avg duration, %.1f avg rating change",
                               gameMode, totalMatches, averageMatchDuration / 60000.0, averageRatingChange);
        }
    }
}