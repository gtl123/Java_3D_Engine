package engine.logging;

import java.io.PrintStream;

/**
 * Log appender that writes messages to the console (System.out or System.err).
 * Thread-safe implementation for concurrent logging.
 */
public class ConsoleAppender implements LogAppender {
    
    private final PrintStream outputStream;
    private final PrintStream errorStream;
    private volatile boolean closed = false;
    
    public ConsoleAppender() {
        this.outputStream = System.out;
        this.errorStream = System.err;
    }
    
    @Override
    public synchronized void append(String message) {
        if (closed) {
            return;
        }
        
        try {
            // Use stderr for ERROR and FATAL levels, stdout for others
            if (message.contains("\"level\":\"ERROR\"") || message.contains("\"level\":\"FATAL\"")) {
                errorStream.println(message);
            } else {
                outputStream.println(message);
            }
        } catch (Exception e) {
            // Fallback to stderr if there's an issue
            System.err.println("ConsoleAppender error: " + e.getMessage());
            System.err.println("Original message: " + message);
        }
    }
    
    @Override
    public void flush() {
        if (!closed) {
            outputStream.flush();
            errorStream.flush();
        }
    }
    
    @Override
    public void close() {
        closed = true;
        flush();
    }
    
    @Override
    public boolean isAvailable() {
        return !closed;
    }
}