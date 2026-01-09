package fps.anticheat.punishment;

/**
 * Result of checking if a player can connect to the server.
 */
public class ConnectionCheckResult {
    
    private final boolean allowed;
    private final String reason;
    private final PlayerBan activeBan;
    private final long timestamp;
    
    public ConnectionCheckResult(boolean allowed, String reason, PlayerBan activeBan) {
        this.allowed = allowed;
        this.reason = reason;
        this.activeBan = activeBan;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Get detailed connection message for the player
     */
    public String getConnectionMessage() {
        if (allowed) {
            return "Connection authorized";
        }
        
        if (activeBan != null) {
            StringBuilder message = new StringBuilder();
            message.append("Connection denied - ");
            
            switch (activeBan.getBanType()) {
                case TEMPORARY:
                    long remainingTime = activeBan.getRemainingTime();
                    if (remainingTime > 0) {
                        message.append("Temporarily banned for: ").append(activeBan.getReason())
                               .append(". Ban expires in: ").append(formatDuration(remainingTime));
                    } else {
                        message.append("Ban has expired, please reconnect");
                    }
                    break;
                    
                case PERMANENT:
                    message.append("Permanently banned for: ").append(activeBan.getReason())
                           .append(". Contact support if you believe this is an error.");
                    break;
                    
                case HARDWARE:
                    message.append("Hardware banned for: ").append(activeBan.getReason())
                           .append(". This ban cannot be appealed.");
                    break;
            }
            
            return message.toString();
        }
        
        return "Connection denied: " + reason;
    }
    
    /**
     * Format duration in human-readable format
     */
    private String formatDuration(long milliseconds) {
        if (milliseconds <= 0) {
            return "0 seconds";
        }
        
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + " day" + (days != 1 ? "s" : "") + 
                   (hours % 24 > 0 ? " " + (hours % 24) + " hour" + (hours % 24 != 1 ? "s" : "") : "");
        } else if (hours > 0) {
            return hours + " hour" + (hours != 1 ? "s" : "") + 
                   (minutes % 60 > 0 ? " " + (minutes % 60) + " minute" + (minutes % 60 != 1 ? "s" : "") : "");
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        } else {
            return seconds + " second" + (seconds != 1 ? "s" : "");
        }
    }
    
    /**
     * Check if ban is temporary and will expire
     */
    public boolean isTemporaryBan() {
        return activeBan != null && activeBan.getBanType() == BanType.TEMPORARY;
    }
    
    /**
     * Check if ban is permanent
     */
    public boolean isPermanentBan() {
        return activeBan != null && 
               (activeBan.getBanType() == BanType.PERMANENT || activeBan.getBanType() == BanType.HARDWARE);
    }
    
    // Getters
    public boolean isAllowed() { return allowed; }
    public String getReason() { return reason; }
    public PlayerBan getActiveBan() { return activeBan; }
    public long getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("ConnectionCheckResult{allowed=%s, reason='%s', ban=%s, timestamp=%d}", 
                           allowed, reason, activeBan != null ? activeBan.getBanType() : "none", timestamp);
    }
}