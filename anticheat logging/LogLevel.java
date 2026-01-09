package fps.anticheat.logging;

/**
 * Log levels for the anti-cheat logging system.
 */
public enum LogLevel {
    /**
     * Debug information for development
     */
    DEBUG(0),
    
    /**
     * General information
     */
    INFO(1),
    
    /**
     * Warning conditions
     */
    WARNING(2),
    
    /**
     * Error conditions
     */
    ERROR(3),
    
    /**
     * Critical conditions requiring immediate attention
     */
    CRITICAL(4);
    
    private final int priority;
    
    LogLevel(int priority) {
        this.priority = priority;
    }
    
    public int getPriority() {
        return priority;
    }
    
    /**
     * Check if this level is at least as severe as the specified level
     */
    public boolean isAtLeast(LogLevel level) {
        return this.priority >= level.priority;
    }
}