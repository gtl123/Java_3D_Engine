package fps.anticheat.security;

/**
 * Result of security validation operations.
 */
public class SecurityValidationResult {
    
    private final boolean valid;
    private final String message;
    private final long timestamp;
    
    public SecurityValidationResult(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Check if security validation passed
     */
    public boolean isValid() {
        return valid;
    }
    
    /**
     * Get validation message
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * Get validation timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Check if validation indicates critical security issue
     */
    public boolean isCriticalIssue() {
        return !valid && (message.toLowerCase().contains("critical") || 
                         message.toLowerCase().contains("compromised") ||
                         message.toLowerCase().contains("tamper"));
    }
    
    @Override
    public String toString() {
        return String.format("SecurityValidationResult{valid=%s, message='%s', timestamp=%d}", 
                           valid, message, timestamp);
    }
}