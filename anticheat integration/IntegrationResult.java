package fps.anticheat.integration;

/**
 * Result of integration operations (activation/deactivation).
 */
public class IntegrationResult {
    
    private final boolean success;
    private final String message;
    private final long timestamp;
    
    public IntegrationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return String.format("IntegrationResult{success=%s, message='%s', timestamp=%d}", 
                           success, message, timestamp);
    }
}