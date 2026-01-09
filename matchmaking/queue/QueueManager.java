package fps.matchmaking.queue;

import engine.logging.LogManager;
import fps.matchmaking.MatchmakingConfiguration.QueueConfiguration;
import fps.matchmaking.MatchmakingTypes.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Manages matchmaking queues for different game modes and regions
 */
public class QueueManager {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final QueueConfiguration config;
    private final Map<QueueType, MatchmakingQueue> queues = new ConcurrentHashMap<>();
    private final Map<Integer, QueueEntry> playerQueueEntries = new ConcurrentHashMap<>();
    private final QueueHealthMonitor healthMonitor;
    private final AtomicLong nextQueueEntryId = new AtomicLong(1);
    
    private volatile boolean initialized = false;
    
    public QueueManager(QueueConfiguration config) {
        this.config = config;
        this.healthMonitor = new QueueHealthMonitor();
        
        logManager.info("QueueManager", "Queue manager created");
    }
    
    /**
     * Initialize the queue manager
     */
    public void initialize() throws Exception {
        if (initialized) {
            return;
        }
        
        logManager.info("QueueManager", "Initializing queue manager");
        
        try {
            // Initialize queues for each queue type
            for (QueueType queueType : QueueType.values()) {
                queues.put(queueType, new MatchmakingQueue(queueType, config));
            }
            
            healthMonitor.initialize();
            
            initialized = true;
            
            logManager.info("QueueManager", "Queue manager initialization complete");
            
        } catch (Exception e) {
            logManager.error("QueueManager", "Failed to initialize queue manager", e);
            throw e;
        }
    }
    
    /**
     * Join a player to a queue
     */
    public QueueResult joinQueue(MatchmakingPlayer player, QueueType queueType, 
                               QueuePreferences preferences) {
        if (!initialized) {
            return QueueResult.failure("Queue manager not initialized");
        }
        
        try {
            // Check if player is already in a queue
            if (isPlayerInQueue(player)) {
                return QueueResult.failure("Player is already in a queue");
            }
            
            MatchmakingQueue queue = queues.get(queueType);
            if (queue == null) {
                return QueueResult.failure("Invalid queue type");
            }
            
            // Check queue capacity
            if (queue.getPlayerCount() >= config.getMaxPlayersPerQueue()) {
                return QueueResult.failure("Queue is full");
            }
            
            // Create queue entry
            QueueEntry entry = new QueueEntry(
                nextQueueEntryId.getAndIncrement(),
                player,
                queueType,
                preferences,
                System.currentTimeMillis()
            );
            
            // Add to queue
            boolean added = queue.addPlayer(entry);
            if (added) {
                playerQueueEntries.put(player.getPlayerId(), entry);
                
                // Calculate estimated wait time
                long estimatedWaitTime = calculateEstimatedWaitTime(queueType, player);
                
                logManager.info("QueueManager", "Player joined queue",
                               "playerId", player.getPlayerId(),
                               "queueType", queueType,
                               "queueSize", queue.getPlayerCount(),
                               "estimatedWaitTime", estimatedWaitTime);
                
                return QueueResult.success(estimatedWaitTime);
            } else {
                return QueueResult.failure("Failed to add player to queue");
            }
            
        } catch (Exception e) {
            logManager.error("QueueManager", "Error joining queue", e,
                           "playerId", player.getPlayerId(),
                           "queueType", queueType);
            return QueueResult.failure("Internal error joining queue");
        }
    }
    
    /**
     * Remove a player from a queue
     */
    public QueueResult leaveQueue(MatchmakingPlayer player, QueueType queueType) {
        if (!initialized) {
            return QueueResult.failure("Queue manager not initialized");
        }
        
        try {
            QueueEntry entry = playerQueueEntries.remove(player.getPlayerId());
            if (entry == null) {
                return QueueResult.failure("Player not found in queue");
            }
            
            MatchmakingQueue queue = queues.get(queueType);
            if (queue != null) {
                boolean removed = queue.removePlayer(entry);
                
                if (removed) {
                    logManager.info("QueueManager", "Player left queue",
                                   "playerId", player.getPlayerId(),
                                   "queueType", queueType,
                                   "waitTime", System.currentTimeMillis() - entry.getJoinTime());
                    
                    return QueueResult.success(-1);
                }
            }
            
            return QueueResult.failure("Failed to remove player from queue");
            
        } catch (Exception e) {
            logManager.error("QueueManager", "Error leaving queue", e,
                           "playerId", player.getPlayerId(),
                           "queueType", queueType);
            return QueueResult.failure("Internal error leaving queue");
        }
    }
    
    /**
     * Remove a player from queue (used by matchmaking system)
     */
    public void removeFromQueue(MatchmakingPlayer player, QueueType queueType) {
        QueueEntry entry = playerQueueEntries.remove(player.getPlayerId());
        if (entry != null) {
            MatchmakingQueue queue = queues.get(queueType);
            if (queue != null) {
                queue.removePlayer(entry);
            }
        }
    }
    
    /**
     * Get all players in a specific queue
     */
    public List<MatchmakingPlayer> getQueuedPlayers(QueueType queueType) {
        MatchmakingQueue queue = queues.get(queueType);
        if (queue == null) {
            return new ArrayList<>();
        }
        
        return queue.getAllPlayers();
    }
    
    /**
     * Check if a player is in any queue
     */
    public boolean isPlayerInQueue(MatchmakingPlayer player) {
        return playerQueueEntries.containsKey(player.getPlayerId());
    }
    
    /**
     * Get queue health metrics
     */
    public QueueHealth getQueueHealth(QueueType queueType) {
        return healthMonitor.getQueueHealth(queueType);
    }
    
    /**
     * Calculate estimated wait time for a player
     */
    private long calculateEstimatedWaitTime(QueueType queueType, MatchmakingPlayer player) {
        MatchmakingQueue queue = queues.get(queueType);
        if (queue == null) {
            return 60000; // Default 1 minute
        }
        
        // Base wait time on queue size and recent match formation rate
        int queueSize = queue.getPlayerCount();
        int minPlayersNeeded = getMinPlayersForQueue(queueType);
        
        if (queueSize < minPlayersNeeded) {
            // Need more players
            long timeToFillQueue = (minPlayersNeeded - queueSize) * 10000; // 10 seconds per player
            return Math.min(300000, timeToFillQueue); // Cap at 5 minutes
        }
        
        // Queue has enough players, estimate based on recent performance
        QueueHealth health = healthMonitor.getQueueHealth(queueType);
        return Math.min(180000, health.getAverageWaitTime()); // Cap at 3 minutes
    }
    
    /**
     * Get minimum players needed for a queue type
     */
    private int getMinPlayersForQueue(QueueType queueType) {
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
     * Update queue statistics
     */
    public void updateQueueStatistics() {
        for (Map.Entry<QueueType, MatchmakingQueue> entry : queues.entrySet()) {
            QueueType queueType = entry.getKey();
            MatchmakingQueue queue = entry.getValue();
            
            healthMonitor.updateQueueMetrics(queueType, queue);
        }
    }
    
    /**
     * Clean up expired queue entries
     */
    public void cleanupExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        long timeoutMs = config.getQueueTimeoutMs();
        
        List<Integer> expiredPlayers = new ArrayList<>();
        
        for (Map.Entry<Integer, QueueEntry> entry : playerQueueEntries.entrySet()) {
            QueueEntry queueEntry = entry.getValue();
            if (currentTime - queueEntry.getJoinTime() > timeoutMs) {
                expiredPlayers.add(entry.getKey());
            }
        }
        
        // Remove expired entries
        for (Integer playerId : expiredPlayers) {
            QueueEntry expiredEntry = playerQueueEntries.remove(playerId);
            if (expiredEntry != null) {
                MatchmakingQueue queue = queues.get(expiredEntry.getQueueType());
                if (queue != null) {
                    queue.removePlayer(expiredEntry);
                }
                
                logManager.info("QueueManager", "Removed expired queue entry",
                               "playerId", playerId,
                               "queueType", expiredEntry.getQueueType(),
                               "waitTime", currentTime - expiredEntry.getJoinTime());
            }
        }
    }
    
    /**
     * Get queue statistics
     */
    public QueueStatistics getQueueStatistics() {
        Map<QueueType, Integer> queueSizes = new ConcurrentHashMap<>();
        int totalPlayers = 0;
        
        for (Map.Entry<QueueType, MatchmakingQueue> entry : queues.entrySet()) {
            int size = entry.getValue().getPlayerCount();
            queueSizes.put(entry.getKey(), size);
            totalPlayers += size;
        }
        
        return new QueueStatistics(queueSizes, totalPlayers, playerQueueEntries.size());
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        logManager.info("QueueManager", "Cleaning up queue manager");
        
        try {
            for (MatchmakingQueue queue : queues.values()) {
                queue.cleanup();
            }
            
            queues.clear();
            playerQueueEntries.clear();
            
            if (healthMonitor != null) {
                healthMonitor.cleanup();
            }
            
            initialized = false;
            
            logManager.info("QueueManager", "Queue manager cleanup complete");
            
        } catch (Exception e) {
            logManager.error("QueueManager", "Error during cleanup", e);
        }
    }
    
    // Getters
    public boolean isInitialized() { return initialized; }
    public int getTotalQueuedPlayers() { return playerQueueEntries.size(); }
    public QueueConfiguration getConfig() { return config; }
    
    /**
     * Queue statistics data class
     */
    public static class QueueStatistics {
        private final Map<QueueType, Integer> queueSizes;
        private final int totalPlayers;
        private final int totalEntries;
        
        public QueueStatistics(Map<QueueType, Integer> queueSizes, int totalPlayers, int totalEntries) {
            this.queueSizes = queueSizes;
            this.totalPlayers = totalPlayers;
            this.totalEntries = totalEntries;
        }
        
        // Getters
        public Map<QueueType, Integer> getQueueSizes() { return queueSizes; }
        public int getTotalPlayers() { return totalPlayers; }
        public int getTotalEntries() { return totalEntries; }
    }
}