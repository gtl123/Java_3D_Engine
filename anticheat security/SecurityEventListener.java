package fps.anticheat.security;

/**
 * Event listener interface for security system events.
 */
public interface SecurityEventListener {
    
    /**
     * Called when security threat level changes
     */
    void onThreatLevelChanged(SecurityThreatLevel oldLevel, SecurityThreatLevel newLevel);
    
    /**
     * Called when critical security threat is detected
     */
    void onCriticalThreatDetected(SecurityThreatLevel threatLevel);
    
    /**
     * Called when security is compromised
     */
    void onSecurityCompromised();
    
    /**
     * Called when debugger is detected
     */
    default void onDebuggerDetected(String detectionMethod) {
        // Default implementation - can be overridden
    }
    
    /**
     * Called when tampering is detected
     */
    default void onTamperingDetected(String tamperType, String details) {
        // Default implementation - can be overridden
    }
    
    /**
     * Called when obfuscation effectiveness drops below threshold
     */
    default void onObfuscationEffectivenessLow(float effectiveness) {
        // Default implementation - can be overridden
    }
    
    /**
     * Called when security validation fails
     */
    default void onSecurityValidationFailed(SecurityValidationResult result) {
        // Default implementation - can be overridden
    }
}