package fps.anticheat.punishment;

/**
 * Result of processing violations through the punishment system.
 */
public class PunishmentResult {
    
    private final boolean success;
    private final PunishmentDecision punishmentDecision;
    private final PlayerBan resultingBan;
    private final String message;
    private final long timestamp;
    
    public PunishmentResult(boolean success, PunishmentDecision punishmentDecision, 
                          PlayerBan resultingBan, String message) {
        this.success = success;
        this.punishmentDecision = punishmentDecision;
        this.resultingBan = resultingBan;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Check if player should be disconnected
     */
    public boolean shouldDisconnectPlayer() {
        return success && punishmentDecision != null && punishmentDecision.requiresDisconnection();
    }
    
    /**
     * Get user-friendly message for the player
     */
    public String getPlayerMessage() {
        if (punishmentDecision != null) {
            return punishmentDecision.getPunishmentMessage();
        }
        return message;
    }
    
    /**
     * Check if punishment was issued (not just a warning or error)
     */
    public boolean wasPunishmentIssued() {
        return success && punishmentDecision != null && 
               punishmentDecision.getPunishmentType() != PunishmentType.WARNING;
    }
    
    /**
     * Get punishment severity level
     */
    public int getSeverityLevel() {
        if (punishmentDecision == null) return 0;
        
        switch (punishmentDecision.getPunishmentType()) {
            case WARNING:
                return 1;
            case TEMPORARY_BAN:
                return 2;
            case PERMANENT_BAN:
                return 3;
            case HARDWARE_BAN:
                return 4;
            case ALREADY_BANNED:
                return 2; // Treat as temp ban level
            default:
                return 0;
        }
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public PunishmentDecision getPunishmentDecision() { return punishmentDecision; }
    public PlayerBan getResultingBan() { return resultingBan; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("PunishmentResult{success=%s, punishment=%s, message='%s', timestamp=%d}", 
                           success, punishmentDecision != null ? punishmentDecision.getPunishmentType() : "none", 
                           message, timestamp);
    }
}