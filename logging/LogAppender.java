package engine.logging;

/**
 * Interface for log output destinations.
 * Implementations handle writing log messages to different outputs like console, files, or network.
 */
public interface LogAppender {
    
    /**
     * Append a log message to the output destination.
     * @param message The formatted log message to append
     */
    void append(String message);
    
    /**
     * Flush any buffered output.
     */
    void flush();
    
    /**
     * Close the appender and release any resources.
     */
    void close();
    
    /**
     * Check if this appender is currently available for writing.
     * @return true if the appender can accept log messages
     */
    boolean isAvailable();
}