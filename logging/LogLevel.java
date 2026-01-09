package engine.logging;

/**
 * Enumeration of logging levels in order of severity.
 * Used to control which log messages are processed and output.
 */
public enum LogLevel {
    TRACE(0, "TRACE"),
    DEBUG(1, "DEBUG"),
    INFO(2, "INFO"),
    WARN(3, "WARN"),
    ERROR(4, "ERROR"),
    FATAL(5, "FATAL");

    private final int level;
    private final String name;

    LogLevel(int level, String name) {
        this.level = level;
        this.name = name;
    }

    public int getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    /**
     * Check if this log level should be logged given the minimum level.
     * @param minLevel The minimum level to log
     * @return true if this level should be logged
     */
    public boolean shouldLog(LogLevel minLevel) {
        return this.level >= minLevel.level;
    }

    /**
     * Parse a log level from string, case-insensitive.
     * @param levelStr The string representation
     * @return The corresponding LogLevel, or INFO if not found
     */
    public static LogLevel fromString(String levelStr) {
        if (levelStr == null) return INFO;
        
        try {
            return LogLevel.valueOf(levelStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return INFO;
        }
    }
}