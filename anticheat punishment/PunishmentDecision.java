package fps.anticheat.punishment;

/**
 * Represents a punishment decision made by the ban manager.
 */
public class PunishmentDecision {
    
    private final PunishmentType punishmentType;
    private final PlayerBan ban; // null for warnings
    private final String reason;
    private final long timestamp;
    
    public PunishmentDecision(PunishmentType punishmentType, PlayerBan ban, String reason) {
        this.punishmentType = punishmentType;
        this.ban = ban;
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Check if punishment requires immediate disconnection
     */
    public boolean requiresDisconnection() {
        return punishmentType == PunishmentType.TEMPORARY_BAN ||
               punishmentType == PunishmentType.PERMANENT_BAN ||
               punishmentType == PunishmentType.HARDWARE_BAN ||
               punishmentType == PunishmentType.ALREADY_BANNED;
    }
    
    /**
     * Get user-friendly punishment message
     */
    public String getPunishmentMessage() {
        switch (punishmentType) {
            case WARNING:
                return "Warning: " + reason + ". Continued violations may result in a ban.";
                
            case TEMPORARY_BAN:
                if (ban != null) {
                    long remainingTime = ban.getRemainingTime();
                    String timeStr = formatDuration(remainingTime);
                    return "You have been temporarily banned for: " + reason + 
                           ". Ban expires in: " + timeStr;
                }
                return "You have been temporarily banned for: " + reason;
                
            case PERMANENT_BAN:
                return "You have been permanently banned for: " + reason + 
                       ". Contact support if you believe this is an error.";
                
            case HARDWARE_BAN:
                return "You have been hardware banned for: " + reason + 
                       ". This ban cannot be appealed.";
                
            case ALREADY_BANNED:
                if (ban != null) {
                    if (ban.getBanType() == BanType.TEMPORARY) {
                        long remainingTime = ban.getRemainingTime();
                        String timeStr = formatDuration(remainingTime);
                        return "You are currently banned. Ban expires in: " + timeStr + 
                               ". Reason: " + ban.getReason();
                    } else {
                        return "You are currently banned. Reason: " + ban.getReason();
                    }
                }
                return "You are currently banned.";
                
            default:
                return "Punishment issued: " + reason;
        }
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
    
    // Getters
    public PunishmentType getPunishmentType() { return punishmentType; }
    public PlayerBan getBan() { return ban; }
    public String getReason() { return reason; }
    public long getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("PunishmentDecision{type=%s, reason='%s', timestamp=%d}", 
                           punishmentType, reason, timestamp);
    }
}