package fps.anticheat.punishment;

/**
 * Types of bans that can be issued to players.
 */
public enum BanType {
    /**
     * Temporary ban with expiration time
     */
    TEMPORARY,
    
    /**
     * Permanent ban (account-level)
     */
    PERMANENT,
    
    /**
     * Hardware-level ban (prevents ban evasion)
     */
    HARDWARE
}