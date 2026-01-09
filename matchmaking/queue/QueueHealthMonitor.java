package fps.matchmaking.queue;

import engine.logging.LogManager;
import fps.matchmaking.MatchmakingTypes.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

/**
 * Monitors queue health and performance metrics
 */
public class QueueHealthMonitor {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final Map<QueueType, QueueHealthData> queueHealthData = new ConcurrentHashMap<>();
    private final AtomicLong lastUpdateTime = new AtomicLong(0);
    
    private volatile boolean initialized = false;
    
    public void initialize() {
        // Initialize health data for all queue types
        for (QueueType queueType : QueueType.values()) {
            queueHealthData.put(queueType, new QueueHealthData(queueType));
        }
        
        lastUpdateTime.set(System.currentTimeMillis());
        initialized = true;
        
        logManager.info("QueueHealthMonitor", "Queue health monitor initialized");
    }
    
    /**
     * Update queue metrics from a matchmaking queue
     */
    public void updateQueueMetrics(QueueType queueType, MatchmakingQueue queue) {
        if (!initialized) {
            return;
        }
        
        QueueHealthData healthData = queueHealthData.get(queueType);
        if (healthData != null) {
            MatchmakingQueue.QueueMetrics metrics = queue.getMetrics();
            healthData.updateMetrics(metrics);
        }
    }
    
    /**
     * Get queue health for a specific queue type
     */
    public QueueHealth getQueueHealth(QueueType queueType) {
        QueueHealthData healthData = queueHealthData.get(queueType);
        if (healthData == null) {
            return new QueueHealth(queueType, 0, 0, 0.0f, 0);
        }
        
        return healthData.getQueueHealth();
    }
    
    /**
     * Get overall system health
     */
    public SystemHealth getSystemHealth() {
        int totalPlayers = 0;
        long totalWaitTime = 0;
        float totalSuccessRate = 0.0f;
        int totalMatches = 0;
        int activeQueues = 0;
        
        for (QueueHealthData healthData : queueHealthData.values()) {
            QueueHealth health = healthData.getQueueHealth();
            
            totalPlayers += health.getQueuedPlayerCount();
            totalWaitTime += health.getAverageWaitTime();
            totalSuccessRate += health.getMatchSuccessRate();
            totalMatches += health.getMatchesFormedLastHour();
            
            if (health.getQueuedPlayerCount() > 0) {
                activeQueues++;
            }
        }
        
        long avgWaitTime = activeQueues > 0 ? totalWaitTime / activeQueues : 0;
        float avgSuccessRate = activeQueues > 0 ? totalSuccessRate / activeQueues : 0.0f;
        
        return new SystemHealth(
            totalPlayers,
            avgWaitTime,
            avgSuccessRate,
            totalMatches,
            activeQueues,
            System.currentTimeMillis()
        );
    }
    
    /**
     * Check if any queues are unhealthy
     */
    public boolean hasUnhealthyQueues() {
        for (QueueHealthData healthData : queueHealthData.values()) {
            if (!healthData.isHealthy()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get unhealthy queues
     */
    public Map<QueueType, String> getUnhealthyQueues() {
        Map<QueueType, String> unhealthyQueues = new ConcurrentHashMap<>();
        
        for (Map.Entry<QueueType, QueueHealthData> entry : queueHealthData.entrySet()) {
            QueueHealthData healthData = entry.getValue();
            if (!healthData.isHealthy()) {
                unhealthyQueues.put(entry.getKey(), healthData.getHealthIssue());
            }
        }
        
        return unhealthyQueues;
    }
    
    /**
     * Reset health statistics
     */
    public void resetStatistics() {
        for (QueueHealthData healthData : queueHealthData.values()) {
            healthData.reset();
        }
        
        logManager.info("QueueHealthMonitor", "Queue health statistics reset");
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        queueHealthData.clear();
        initialized = false;
        
        logManager.info("QueueHealthMonitor", "Queue health monitor cleaned up");
    }
    
    public boolean isInitialized() { return initialized; }
    
    /**
     * Internal class to track health data for a single queue
     */
    private static class QueueHealthData {
        private final QueueType queueType;
        private volatile int queuedPlayerCount = 0;
        private volatile long averageWaitTime = 0;
        private volatile float matchSuccessRate = 0.0f;
        private volatile int matchesFormedLastHour = 0;
        private volatile long lastUpdateTime = 0;
        
        // Health thresholds
        private static final long MAX_HEALTHY_WAIT_TIME = 300000; // 5 minutes
        private static final float MIN_HEALTHY_SUCCESS_RATE = 0.7f; // 70%
        private static final int MAX_HEALTHY_QUEUE_SIZE = 1000;
        
        public QueueHealthData(QueueType queueType) {
            this.queueType = queueType;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        public void updateMetrics(MatchmakingQueue.QueueMetrics metrics) {
            this.queuedPlayerCount = metrics.getPlayerCount();
            this.averageWaitTime = metrics.getAverageWaitTime();
            this.matchSuccessRate = metrics.getMatchSuccessRate();
            this.matchesFormedLastHour = metrics.getMatchesFormedLastHour();
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        public QueueHealth getQueueHealth() {
            return new QueueHealth(
                queueType,
                queuedPlayerCount,
                averageWaitTime,
                matchSuccessRate,
                matchesFormedLastHour
            );
        }
        
        public boolean isHealthy() {
            // Check various health indicators
            
            // Wait time check
            if (averageWaitTime > MAX_HEALTHY_WAIT_TIME) {
                return false;
            }
            
            // Success rate check (only if we have enough data)
            if (matchesFormedLastHour > 5 && matchSuccessRate < MIN_HEALTHY_SUCCESS_RATE) {
                return false;
            }
            
            // Queue size check
            if (queuedPlayerCount > MAX_HEALTHY_QUEUE_SIZE) {
                return false;
            }
            
            // Stale data check (no updates in last 10 minutes)
            long timeSinceUpdate = System.currentTimeMillis() - lastUpdateTime;
            if (timeSinceUpdate > 600000) { // 10 minutes
                return false;
            }
            
            return true;
        }
        
        public String getHealthIssue() {
            if (averageWaitTime > MAX_HEALTHY_WAIT_TIME) {
                return "High wait time: " + (averageWaitTime / 1000) + "s";
            }
            
            if (matchesFormedLastHour > 5 && matchSuccessRate < MIN_HEALTHY_SUCCESS_RATE) {
                return "Low success rate: " + (matchSuccessRate * 100) + "%";
            }
            
            if (queuedPlayerCount > MAX_HEALTHY_QUEUE_SIZE) {
                return "Queue overloaded: " + queuedPlayerCount + " players";
            }
            
            long timeSinceUpdate = System.currentTimeMillis() - lastUpdateTime;
            if (timeSinceUpdate > 600000) {
                return "Stale data: " + (timeSinceUpdate / 60000) + " minutes old";
            }
            
            return "Unknown issue";
        }
        
        public void reset() {
            this.queuedPlayerCount = 0;
            this.averageWaitTime = 0;
            this.matchSuccessRate = 0.0f;
            this.matchesFormedLastHour = 0;
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }
    
    /**
     * System-wide health metrics
     */
    public static class SystemHealth {
        private final int totalQueuedPlayers;
        private final long averageWaitTime;
        private final float averageSuccessRate;
        private final int totalMatchesLastHour;
        private final int activeQueues;
        private final long timestamp;
        
        public SystemHealth(int totalQueuedPlayers, long averageWaitTime, float averageSuccessRate,
                          int totalMatchesLastHour, int activeQueues, long timestamp) {
            this.totalQueuedPlayers = totalQueuedPlayers;
            this.averageWaitTime = averageWaitTime;
            this.averageSuccessRate = averageSuccessRate;
            this.totalMatchesLastHour = totalMatchesLastHour;
            this.activeQueues = activeQueues;
            this.timestamp = timestamp;
        }
        
        public boolean isHealthy() {
            return averageWaitTime < 300000 && // Less than 5 minutes
                   averageSuccessRate > 0.7f && // More than 70% success rate
                   activeQueues > 0; // At least one active queue
        }
        
        // Getters
        public int getTotalQueuedPlayers() { return totalQueuedPlayers; }
        public long getAverageWaitTime() { return averageWaitTime; }
        public float getAverageSuccessRate() { return averageSuccessRate; }
        public int getTotalMatchesLastHour() { return totalMatchesLastHour; }
        public int getActiveQueues() { return activeQueues; }
        public long getTimestamp() { return timestamp; }
    }
}