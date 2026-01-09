package fps.anticheat.punishment;

/**
 * Escalation levels for punishment decisions.
 */
public enum EscalationLevel {
    /**
     * No escalation needed
     */
    NONE,
    
    /**
     * Low escalation - minor increase in punishment severity
     */
    LOW,
    
    /**
     * Medium escalation - moderate increase in punishment severity
     */
    MEDIUM,
    
    /**
     * High escalation - significant increase in punishment severity
     */
    HIGH,
    
    /**
     * Critical escalation - maximum punishment severity, potential hardware ban
     */
    CRITICAL
}