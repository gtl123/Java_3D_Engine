package fps.anticheat.security;

/**
 * Security threat levels for the anti-cheat system.
 */
public enum SecurityThreatLevel {
    /**
     * Low threat - normal operation
     */
    LOW,
    
    /**
     * Elevated threat - increased monitoring
     */
    ELEVATED,
    
    /**
     * Medium threat - enhanced protection enabled
     */
    MEDIUM,
    
    /**
     * High threat - maximum protection, prepare for shutdown
     */
    HIGH,
    
    /**
     * Critical threat - emergency measures, immediate response required
     */
    CRITICAL
}