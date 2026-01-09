package fps.matchmaking.queue;

import fps.matchmaking.MatchmakingTypes.*;

/**
 * Represents a player's entry in a matchmaking queue
 */
public class QueueEntry {
    
    private final long entryId;
    private final MatchmakingPlayer player;
    private final QueueType queueType;
    private final QueuePreferences preferences;
    private final long joinTime;
    
    // Dynamic properties
    private volatile int priority;
    private volatile long lastPriorityUpdate;
    
    public QueueEntry(long entryId, MatchmakingPlayer player, QueueType queueType,
                     QueuePreferences preferences, long joinTime) {
        this.entryId = entryId;
        this.player = player;
        this.queueType = queueType;
        this.preferences = preferences;
        this.joinTime = joinTime;
        this.priority = 0;
        this.lastPriorityUpdate = joinTime;
    }
    
    /**
     * Get current wait time in milliseconds
     */
    public long getWaitTime() {
        return System.currentTimeMillis() - joinTime;
    }
    
    /**
     * Get wait time in seconds
     */
    public long getWaitTimeSeconds() {
        return getWaitTime() / 1000;
    }
    
    /**
     * Check if entry has expired based on timeout
     */
    public boolean isExpired(long timeoutMs) {
        return getWaitTime() > timeoutMs;
    }
    
    /**
     * Update priority (used by queue management)
     */
    public void updatePriority(int newPriority) {
        this.priority = newPriority;
        this.lastPriorityUpdate = System.currentTimeMillis();
    }
    
    /**
     * Check if player preferences match given criteria
     */
    public boolean matchesPreferences(Region region, int maxPing) {
        // Check region preference
        Region preferredRegion = preferences.getPreferredRegion();
        if (preferredRegion != Region.AUTO && preferredRegion != region) {
            return false;
        }
        
        // Check ping preference
        if (preferences.getMaxPing() < maxPing) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if this entry is compatible with another for team formation
     */
    public boolean isCompatibleWith(QueueEntry other) {
        // Check if both players can be in the same match
        
        // Cross-platform check
        if (!preferences.isAllowCrossPlatform() || !other.preferences.isAllowCrossPlatform()) {
            // Would need platform information to check compatibility
            // For now, assume compatible
        }
        
        // Region compatibility
        Region thisRegion = preferences.getPreferredRegion();
        Region otherRegion = other.preferences.getPreferredRegion();
        
        if (thisRegion != Region.AUTO && otherRegion != Region.AUTO && thisRegion != otherRegion) {
            return false;
        }
        
        // Ping compatibility (both players should be able to play on same server)
        int maxPing = Math.max(preferences.getMaxPing(), other.preferences.getMaxPing());
        // This would need server location data to validate properly
        
        return true;
    }
    
    // Getters
    public long getEntryId() { return entryId; }
    public MatchmakingPlayer getPlayer() { return player; }
    public QueueType getQueueType() { return queueType; }
    public QueuePreferences getPreferences() { return preferences; }
    public long getJoinTime() { return joinTime; }
    public int getPriority() { return priority; }
    public long getLastPriorityUpdate() { return lastPriorityUpdate; }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        QueueEntry that = (QueueEntry) obj;
        return entryId == that.entryId;
    }
    
    @Override
    public int hashCode() {
        return Long.hashCode(entryId);
    }
    
    @Override
    public String toString() {
        return String.format("QueueEntry{id=%d, playerId=%d, queueType=%s, waitTime=%ds}",
                           entryId, player.getPlayerId(), queueType, getWaitTimeSeconds());
    }
}