package fps.matchmaking.queue;

import engine.logging.LogManager;
import fps.matchmaking.MatchmakingConfiguration.QueueConfiguration;
import fps.matchmaking.MatchmakingTypes.*;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

/**
 * Individual matchmaking queue for a specific queue type
 */
public class MatchmakingQueue {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final QueueType queueType;
    private final QueueConfiguration config;
    private final PriorityBlockingQueue<QueueEntry> priorityQueue;
    private final Map<Long, QueueEntry> entryMap = new ConcurrentHashMap<>();
    
    // Statistics
    private final AtomicInteger playerCount = new AtomicInteger(0);
    private final AtomicLong totalWaitTime = new AtomicLong(0);
    private final AtomicInteger matchesFormed = new AtomicInteger(0);
    private final AtomicLong lastMatchTime = new AtomicLong(0);
    
    public MatchmakingQueue(QueueType queueType, QueueConfiguration config) {
        this.queueType = queueType;
        this.config = config;
        
        // Create priority queue with custom comparator
        this.priorityQueue = new PriorityBlockingQueue<>(1000, createQueueComparator());
        
        logManager.debug("MatchmakingQueue", "Queue created", "queueType", queueType);
    }
    
    /**
     * Create comparator for queue priority
     */
    private Comparator<QueueEntry> createQueueComparator() {
        return (entry1, entry2) -> {
            // Priority factors (lower value = higher priority):
            // 1. Premium players get priority
            // 2. Longer wait time gets priority
            // 3. Higher rating uncertainty gets priority (placement players)
            
            float priority1 = calculatePriority(entry1);
            float priority2 = calculatePriority(entry2);
            
            return Float.compare(priority1, priority2);
        };
    }
    
    /**
     * Calculate priority score for queue entry (lower = higher priority)
     */
    private float calculatePriority(QueueEntry entry) {
        float priority = 0.0f;
        
        // Wait time factor (longer wait = higher priority)
        long waitTime = System.currentTimeMillis() - entry.getJoinTime();
        priority -= waitTime / 1000.0f; // Subtract seconds waited
        
        // Premium player factor
        if (isPremiumPlayer(entry.getPlayer())) {
            priority -= 1000.0f; // High priority boost
        }
        
        // Placement player factor (higher uncertainty = higher priority)
        if (entry.getPlayer().getRating().isInPlacement()) {
            priority -= 500.0f;
        }
        
        // Rating uncertainty factor
        float uncertainty = entry.getPlayer().getRating().getUncertainty();
        priority -= uncertainty; // Higher uncertainty = higher priority
        
        return priority;
    }
    
    /**
     * Check if player is premium (placeholder implementation)
     */
    private boolean isPremiumPlayer(MatchmakingPlayer player) {
        // This would integrate with a premium/subscription system
        return false; // Default implementation
    }
    
    /**
     * Add a player to the queue
     */
    public boolean addPlayer(QueueEntry entry) {
        try {
            if (entryMap.containsKey(entry.getEntryId())) {
                return false; // Already in queue
            }
            
            boolean added = priorityQueue.offer(entry);
            if (added) {
                entryMap.put(entry.getEntryId(), entry);
                playerCount.incrementAndGet();
                
                logManager.debug("MatchmakingQueue", "Player added to queue",
                               "queueType", queueType,
                               "playerId", entry.getPlayer().getPlayerId(),
                               "queueSize", playerCount.get());
                
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            logManager.error("MatchmakingQueue", "Error adding player to queue", e,
                           "queueType", queueType,
                           "playerId", entry.getPlayer().getPlayerId());
            return false;
        }
    }
    
    /**
     * Remove a player from the queue
     */
    public boolean removePlayer(QueueEntry entry) {
        try {
            QueueEntry removed = entryMap.remove(entry.getEntryId());
            if (removed != null) {
                boolean queueRemoved = priorityQueue.remove(entry);
                playerCount.decrementAndGet();
                
                // Update wait time statistics
                long waitTime = System.currentTimeMillis() - entry.getJoinTime();
                totalWaitTime.addAndGet(waitTime);
                
                logManager.debug("MatchmakingQueue", "Player removed from queue",
                               "queueType", queueType,
                               "playerId", entry.getPlayer().getPlayerId(),
                               "waitTime", waitTime,
                               "queueSize", playerCount.get());
                
                return queueRemoved;
            }
            
            return false;
            
        } catch (Exception e) {
            logManager.error("MatchmakingQueue", "Error removing player from queue", e,
                           "queueType", queueType,
                           "playerId", entry.getPlayer().getPlayerId());
            return false;
        }
    }
    
    /**
     * Get all players in the queue
     */
    public List<MatchmakingPlayer> getAllPlayers() {
        List<MatchmakingPlayer> players = new ArrayList<>();
        
        for (QueueEntry entry : priorityQueue) {
            players.add(entry.getPlayer());
        }
        
        return players;
    }
    
    /**
     * Get players sorted by priority
     */
    public List<QueueEntry> getPlayersByPriority() {
        return new ArrayList<>(priorityQueue);
    }
    
    /**
     * Get players within rating range
     */
    public List<MatchmakingPlayer> getPlayersInRatingRange(float minRating, float maxRating) {
        List<MatchmakingPlayer> players = new ArrayList<>();
        
        for (QueueEntry entry : priorityQueue) {
            float rating = entry.getPlayer().getRating().getRating();
            if (rating >= minRating && rating <= maxRating) {
                players.add(entry.getPlayer());
            }
        }
        
        return players;
    }
    
    /**
     * Get players by region
     */
    public List<MatchmakingPlayer> getPlayersByRegion(Region region) {
        List<MatchmakingPlayer> players = new ArrayList<>();
        
        for (QueueEntry entry : priorityQueue) {
            Region playerRegion = entry.getPreferences().getPreferredRegion();
            if (playerRegion == region || playerRegion == Region.AUTO) {
                players.add(entry.getPlayer());
            }
        }
        
        return players;
    }
    
    /**
     * Record match formation
     */
    public void recordMatchFormed() {
        matchesFormed.incrementAndGet();
        lastMatchTime.set(System.currentTimeMillis());
        
        logManager.debug("MatchmakingQueue", "Match formed",
                        "queueType", queueType,
                        "totalMatches", matchesFormed.get());
    }
    
    /**
     * Get average wait time
     */
    public long getAverageWaitTime() {
        int matches = matchesFormed.get();
        if (matches == 0) {
            return 0;
        }
        
        return totalWaitTime.get() / matches;
    }
    
    /**
     * Get queue health metrics
     */
    public QueueMetrics getMetrics() {
        long currentTime = System.currentTimeMillis();
        long avgWaitTime = getAverageWaitTime();
        
        // Calculate match success rate (matches formed vs players processed)
        float successRate = 0.0f;
        int totalProcessed = matchesFormed.get() * getMinPlayersForMatch();
        if (totalProcessed > 0) {
            successRate = (float) matchesFormed.get() / totalProcessed;
        }
        
        // Calculate matches formed in last hour
        int recentMatches = 0;
        long oneHourAgo = currentTime - 3600000; // 1 hour in milliseconds
        if (lastMatchTime.get() > oneHourAgo) {
            // Simplified calculation - in real implementation, would track detailed history
            recentMatches = Math.max(0, matchesFormed.get() - (int)((currentTime - oneHourAgo) / 300000)); // Estimate
        }
        
        return new QueueMetrics(
            queueType,
            playerCount.get(),
            avgWaitTime,
            successRate,
            recentMatches,
            currentTime
        );
    }
    
    /**
     * Get minimum players needed for a match in this queue
     */
    private int getMinPlayersForMatch() {
        switch (queueType) {
            case RANKED_CLAN_WARFARE:
            case CASUAL_CLAN_WARFARE:
                return 32;
            case RANKED_TEAM_DEATHMATCH:
            case CASUAL_TEAM_DEATHMATCH:
            case RANKED_KING_HILL:
            case CASUAL_KING_HILL:
                return 8;
            case RANKED_SEARCH_DESTROY:
            case CASUAL_SEARCH_DESTROY:
                return 10;
            case RANKED_CAPTURE_FLAG:
            case CASUAL_CAPTURE_FLAG:
                return 12;
            default:
                return 2;
        }
    }
    
    /**
     * Clear all players from queue
     */
    public void clear() {
        priorityQueue.clear();
        entryMap.clear();
        playerCount.set(0);
        
        logManager.info("MatchmakingQueue", "Queue cleared", "queueType", queueType);
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        clear();
        
        logManager.debug("MatchmakingQueue", "Queue cleaned up", "queueType", queueType);
    }
    
    // Getters
    public QueueType getQueueType() { return queueType; }
    public int getPlayerCount() { return playerCount.get(); }
    public int getMatchesFormed() { return matchesFormed.get(); }
    public long getLastMatchTime() { return lastMatchTime.get(); }
    public boolean isEmpty() { return playerCount.get() == 0; }
    
    /**
     * Queue metrics data class
     */
    public static class QueueMetrics {
        private final QueueType queueType;
        private final int playerCount;
        private final long averageWaitTime;
        private final float matchSuccessRate;
        private final int matchesFormedLastHour;
        private final long timestamp;
        
        public QueueMetrics(QueueType queueType, int playerCount, long averageWaitTime,
                          float matchSuccessRate, int matchesFormedLastHour, long timestamp) {
            this.queueType = queueType;
            this.playerCount = playerCount;
            this.averageWaitTime = averageWaitTime;
            this.matchSuccessRate = matchSuccessRate;
            this.matchesFormedLastHour = matchesFormedLastHour;
            this.timestamp = timestamp;
        }
        
        // Getters
        public QueueType getQueueType() { return queueType; }
        public int getPlayerCount() { return playerCount; }
        public long getAverageWaitTime() { return averageWaitTime; }
        public float getMatchSuccessRate() { return matchSuccessRate; }
        public int getMatchesFormedLastHour() { return matchesFormedLastHour; }
        public long getTimestamp() { return timestamp; }
    }
}