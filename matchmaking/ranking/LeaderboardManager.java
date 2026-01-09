package fps.matchmaking.ranking;

import engine.logging.LogManager;
import fps.matchmaking.MatchmakingConfiguration.RankingConfiguration;
import fps.matchmaking.MatchmakingTypes.Region;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Manages competitive leaderboards and rankings
 */
public class LeaderboardManager {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final RankingConfiguration config;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Global leaderboards
    private final Map<String, List<LeaderboardEntry>> globalLeaderboards = new ConcurrentHashMap<>();
    
    // Regional leaderboards
    private final Map<Region, Map<String, List<LeaderboardEntry>>> regionalLeaderboards = new ConcurrentHashMap<>();
    
    // Seasonal leaderboards
    private final Map<Integer, Map<String, List<LeaderboardEntry>>> seasonalLeaderboards = new ConcurrentHashMap<>();
    
    // Leaderboard update tracking
    private final Map<String, Long> lastUpdateTimes = new ConcurrentHashMap<>();
    
    private volatile boolean initialized = false;
    
    public LeaderboardManager(RankingConfiguration config) {
        this.config = config;
        
        // Initialize regional leaderboards
        for (Region region : Region.values()) {
            regionalLeaderboards.put(region, new ConcurrentHashMap<>());
        }
    }
    
    public void initialize() {
        // Initialize default leaderboards
        initializeLeaderboard("overall");
        initializeLeaderboard("seasonal");
        initializeLeaderboard("weekly");
        initializeLeaderboard("daily");
        
        initialized = true;
        
        logManager.info("LeaderboardManager", "Leaderboard manager initialized");
    }
    
    /**
     * Initialize a leaderboard category
     */
    private void initializeLeaderboard(String category) {
        globalLeaderboards.put(category, new ArrayList<>());
        
        for (Region region : Region.values()) {
            regionalLeaderboards.get(region).put(category, new ArrayList<>());
        }
        
        lastUpdateTimes.put(category, System.currentTimeMillis());
    }
    
    /**
     * Update player position in leaderboards
     */
    public void updatePlayerRanking(String playerId, PlayerRank playerRank, Region region, int season) {
        lock.writeLock().lock();
        try {
            LeaderboardEntry entry = new LeaderboardEntry(
                playerId,
                playerRank.getRank(),
                playerRank.getTier(),
                playerRank.getPoints(),
                playerRank.getWins(),
                playerRank.getLosses(),
                playerRank.getWinStreak(),
                region,
                System.currentTimeMillis()
            );
            
            // Update global leaderboards
            updateLeaderboardEntry("overall", entry);
            updateLeaderboardEntry("seasonal", entry);
            
            // Update regional leaderboards
            Map<String, List<LeaderboardEntry>> regionBoards = regionalLeaderboards.get(region);
            updateLeaderboardEntry(regionBoards, "overall", entry);
            updateLeaderboardEntry(regionBoards, "seasonal", entry);
            
            // Update seasonal leaderboards
            seasonalLeaderboards.computeIfAbsent(season, k -> new ConcurrentHashMap<>())
                               .computeIfAbsent("overall", k -> new ArrayList<>());
            updateLeaderboardEntry(seasonalLeaderboards.get(season), "overall", entry);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Update leaderboard entry in global boards
     */
    private void updateLeaderboardEntry(String category, LeaderboardEntry entry) {
        List<LeaderboardEntry> leaderboard = globalLeaderboards.get(category);
        updateLeaderboardEntry(leaderboard, entry);
        lastUpdateTimes.put(category, System.currentTimeMillis());
    }
    
    /**
     * Update leaderboard entry in category map
     */
    private void updateLeaderboardEntry(Map<String, List<LeaderboardEntry>> boards, 
                                      String category, LeaderboardEntry entry) {
        List<LeaderboardEntry> leaderboard = boards.get(category);
        if (leaderboard != null) {
            updateLeaderboardEntry(leaderboard, entry);
        }
    }
    
    /**
     * Update leaderboard entry in specific list
     */
    private void updateLeaderboardEntry(List<LeaderboardEntry> leaderboard, LeaderboardEntry entry) {
        // Remove existing entry for this player
        leaderboard.removeIf(e -> e.getPlayerId().equals(entry.getPlayerId()));
        
        // Add new entry
        leaderboard.add(entry);
        
        // Sort by rank and points
        leaderboard.sort((a, b) -> {
            int rankCompare = Integer.compare(b.getRank().ordinal(), a.getRank().ordinal());
            if (rankCompare != 0) return rankCompare;
            
            int tierCompare = Integer.compare(b.getTier(), a.getTier());
            if (tierCompare != 0) return tierCompare;
            
            return Integer.compare(b.getPoints(), a.getPoints());
        });
        
        // Limit leaderboard size
        if (leaderboard.size() > config.getMaxLeaderboardSize()) {
            leaderboard.subList(config.getMaxLeaderboardSize(), leaderboard.size()).clear();
        }
    }
    
    /**
     * Get global leaderboard
     */
    public List<LeaderboardEntry> getGlobalLeaderboard(String category, int limit) {
        lock.readLock().lock();
        try {
            List<LeaderboardEntry> leaderboard = globalLeaderboards.get(category);
            if (leaderboard == null) return new ArrayList<>();
            
            return leaderboard.stream()
                             .limit(Math.min(limit, leaderboard.size()))
                             .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get regional leaderboard
     */
    public List<LeaderboardEntry> getRegionalLeaderboard(Region region, String category, int limit) {
        lock.readLock().lock();
        try {
            Map<String, List<LeaderboardEntry>> regionBoards = regionalLeaderboards.get(region);
            if (regionBoards == null) return new ArrayList<>();
            
            List<LeaderboardEntry> leaderboard = regionBoards.get(category);
            if (leaderboard == null) return new ArrayList<>();
            
            return leaderboard.stream()
                             .limit(Math.min(limit, leaderboard.size()))
                             .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get seasonal leaderboard
     */
    public List<LeaderboardEntry> getSeasonalLeaderboard(int season, String category, int limit) {
        lock.readLock().lock();
        try {
            Map<String, List<LeaderboardEntry>> seasonBoards = seasonalLeaderboards.get(season);
            if (seasonBoards == null) return new ArrayList<>();
            
            List<LeaderboardEntry> leaderboard = seasonBoards.get(category);
            if (leaderboard == null) return new ArrayList<>();
            
            return leaderboard.stream()
                             .limit(Math.min(limit, leaderboard.size()))
                             .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get player position in leaderboard
     */
    public int getPlayerPosition(String playerId, String category) {
        lock.readLock().lock();
        try {
            List<LeaderboardEntry> leaderboard = globalLeaderboards.get(category);
            if (leaderboard == null) return -1;
            
            for (int i = 0; i < leaderboard.size(); i++) {
                if (leaderboard.get(i).getPlayerId().equals(playerId)) {
                    return i + 1; // 1-based position
                }
            }
            
            return -1; // Not found
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get player position in regional leaderboard
     */
    public int getPlayerRegionalPosition(String playerId, Region region, String category) {
        lock.readLock().lock();
        try {
            Map<String, List<LeaderboardEntry>> regionBoards = regionalLeaderboards.get(region);
            if (regionBoards == null) return -1;
            
            List<LeaderboardEntry> leaderboard = regionBoards.get(category);
            if (leaderboard == null) return -1;
            
            for (int i = 0; i < leaderboard.size(); i++) {
                if (leaderboard.get(i).getPlayerId().equals(playerId)) {
                    return i + 1; // 1-based position
                }
            }
            
            return -1; // Not found
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get leaderboard statistics
     */
    public LeaderboardStats getLeaderboardStats(String category) {
        lock.readLock().lock();
        try {
            List<LeaderboardEntry> leaderboard = globalLeaderboards.get(category);
            if (leaderboard == null || leaderboard.isEmpty()) {
                return new LeaderboardStats(0, 0, 0, 0, 0);
            }
            
            int totalPlayers = leaderboard.size();
            int totalPoints = leaderboard.stream().mapToInt(LeaderboardEntry::getPoints).sum();
            int averagePoints = totalPoints / totalPlayers;
            int topPlayerPoints = leaderboard.get(0).getPoints();
            long lastUpdate = lastUpdateTimes.getOrDefault(category, 0L);
            
            return new LeaderboardStats(totalPlayers, totalPoints, averagePoints, 
                                      topPlayerPoints, lastUpdate);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Clear seasonal leaderboards
     */
    public void clearSeasonalLeaderboards(int season) {
        lock.writeLock().lock();
        try {
            seasonalLeaderboards.remove(season);
            
            logManager.info("LeaderboardManager", "Cleared seasonal leaderboards",
                           "season", season);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Reset all leaderboards
     */
    public void resetAllLeaderboards() {
        lock.writeLock().lock();
        try {
            globalLeaderboards.clear();
            regionalLeaderboards.values().forEach(Map::clear);
            seasonalLeaderboards.clear();
            lastUpdateTimes.clear();
            
            // Reinitialize
            initialize();
            
            logManager.info("LeaderboardManager", "All leaderboards reset");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void cleanup() {
        lock.writeLock().lock();
        try {
            globalLeaderboards.clear();
            regionalLeaderboards.clear();
            seasonalLeaderboards.clear();
            lastUpdateTimes.clear();
            initialized = false;
            
            logManager.info("LeaderboardManager", "Leaderboard manager cleaned up");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // Getters
    public boolean isInitialized() { return initialized; }
    
    /**
     * Leaderboard entry data class
     */
    public static class LeaderboardEntry {
        private final String playerId;
        private final PlayerRank.Rank rank;
        private final int tier;
        private final int points;
        private final int wins;
        private final int losses;
        private final int winStreak;
        private final Region region;
        private final long timestamp;
        
        public LeaderboardEntry(String playerId, PlayerRank.Rank rank, int tier, int points,
                              int wins, int losses, int winStreak, Region region, long timestamp) {
            this.playerId = playerId;
            this.rank = rank;
            this.tier = tier;
            this.points = points;
            this.wins = wins;
            this.losses = losses;
            this.winStreak = winStreak;
            this.region = region;
            this.timestamp = timestamp;
        }
        
        // Getters
        public String getPlayerId() { return playerId; }
        public PlayerRank.Rank getRank() { return rank; }
        public int getTier() { return tier; }
        public int getPoints() { return points; }
        public int getWins() { return wins; }
        public int getLosses() { return losses; }
        public int getWinStreak() { return winStreak; }
        public Region getRegion() { return region; }
        public long getTimestamp() { return timestamp; }
        
        public double getWinRate() {
            int totalGames = wins + losses;
            return totalGames > 0 ? (double) wins / totalGames : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("%s: %s %d (%d pts, %d-%d, %.1f%% WR)",
                               playerId, rank, tier, points, wins, losses, getWinRate() * 100);
        }
    }
    
    /**
     * Leaderboard statistics data class
     */
    public static class LeaderboardStats {
        private final int totalPlayers;
        private final int totalPoints;
        private final int averagePoints;
        private final int topPlayerPoints;
        private final long lastUpdate;
        
        public LeaderboardStats(int totalPlayers, int totalPoints, int averagePoints,
                              int topPlayerPoints, long lastUpdate) {
            this.totalPlayers = totalPlayers;
            this.totalPoints = totalPoints;
            this.averagePoints = averagePoints;
            this.topPlayerPoints = topPlayerPoints;
            this.lastUpdate = lastUpdate;
        }
        
        // Getters
        public int getTotalPlayers() { return totalPlayers; }
        public int getTotalPoints() { return totalPoints; }
        public int getAveragePoints() { return averagePoints; }
        public int getTopPlayerPoints() { return topPlayerPoints; }
        public long getLastUpdate() { return lastUpdate; }
        
        @Override
        public String toString() {
            return String.format("Leaderboard: %d players, avg %d pts, top %d pts",
                               totalPlayers, averagePoints, topPlayerPoints);
        }
    }
}