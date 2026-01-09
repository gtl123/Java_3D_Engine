package fps.anticheat.punishment;

import fps.anticheat.hardware.HardwareFingerprint;

/**
 * Represents a player ban with type, duration, and associated information.
 */
public class PlayerBan {
    
    private final String playerId;
    private final BanType banType;
    private final String reason;
    private final long duration; // -1 for permanent
    private final long timestamp;
    private final HardwareFingerprint hardwareFingerprint;
    
    public PlayerBan(String playerId, BanType banType, String reason, long duration, 
                    long timestamp, HardwareFingerprint hardwareFingerprint) {
        this.playerId = playerId;
        this.banType = banType;
        this.reason = reason;
        this.duration = duration;
        this.timestamp = timestamp;
        this.hardwareFingerprint = hardwareFingerprint;
    }
    
    /**
     * Check if ban has expired
     */
    public boolean isExpired() {
        if (banType == BanType.PERMANENT || banType == BanType.HARDWARE) {
            return false; // Never expires
        }
        
        if (duration == -1) {
            return false; // Permanent
        }
        
        return System.currentTimeMillis() > (timestamp + duration);
    }
    
    /**
     * Get remaining ban time in milliseconds
     */
    public long getRemainingTime() {
        if (banType == BanType.PERMANENT || banType == BanType.HARDWARE || duration == -1) {
            return -1; // Permanent
        }
        
        long remaining = (timestamp + duration) - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
    
    /**
     * Get ban expiry timestamp
     */
    public long getExpiryTime() {
        if (banType == BanType.PERMANENT || banType == BanType.HARDWARE || duration == -1) {
            return -1; // Never expires
        }
        
        return timestamp + duration;
    }
    
    // Getters
    public String getPlayerId() { return playerId; }
    public BanType getBanType() { return banType; }
    public String getReason() { return reason; }
    public long getDuration() { return duration; }
    public long getTimestamp() { return timestamp; }
    public HardwareFingerprint getHardwareFingerprint() { return hardwareFingerprint; }
    
    @Override
    public String toString() {
        return String.format("PlayerBan{playerId='%s', type=%s, reason='%s', duration=%d, timestamp=%d}", 
                           playerId, banType, reason, duration, timestamp);
    }
}