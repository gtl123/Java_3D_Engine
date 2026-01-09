package fps.anticheat.punishment;

/**
 * Types of punishments that can be issued to players.
 */
public enum PunishmentType {
    /**
     * Warning - no gameplay restriction
     */
    WARNING,
    
    /**
     * Temporary ban with expiration
     */
    TEMPORARY_BAN,
    
    /**
     * Permanent account ban
     */
    PERMANENT_BAN,
    
    /**
     * Hardware-level ban
     */
    HARDWARE_BAN,
    
    /**
     * Player is already banned
     */
    ALREADY_BANNED
}