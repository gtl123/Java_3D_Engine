package fps.anticheat.logging;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * Represents a single log entry in the anti-cheat system.
 */
public class LogEntry {
    private final String id;
    private final LocalDateTime timestamp;
    private final LogLevel level;
    private final String category;
    private final String message;
    private final String playerId;
    private final String sessionId;
    private final Map<String, Object> metadata;
    private final String stackTrace;
    
    private LogEntry(Builder builder) {
        this.id = builder.id;
        this.timestamp = builder.timestamp;
        this.level = builder.level;
        this.category = builder.category;
        this.message = builder.message;
        this.playerId = builder.playerId;
        this.sessionId = builder.sessionId;
        this.metadata = new HashMap<>(builder.metadata);
        this.stackTrace = builder.stackTrace;
    }
    
    // Getters
    public String getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public LogLevel getLevel() { return level; }
    public String getCategory() { return category; }
    public String getMessage() { return message; }
    public String getPlayerId() { return playerId; }
    public String getSessionId() { return sessionId; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
    public String getStackTrace() { return stackTrace; }
    
    /**
     * Builder for creating log entries
     */
    public static class Builder {
        private String id;
        private LocalDateTime timestamp = LocalDateTime.now();
        private LogLevel level = LogLevel.INFO;
        private String category;
        private String message;
        private String playerId;
        private String sessionId;
        private Map<String, Object> metadata = new HashMap<>();
        private String stackTrace;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder level(LogLevel level) {
            this.level = level;
            return this;
        }
        
        public Builder category(String category) {
            this.category = category;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder playerId(String playerId) {
            this.playerId = playerId;
            return this;
        }
        
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }
        
        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }
        
        public Builder stackTrace(String stackTrace) {
            this.stackTrace = stackTrace;
            return this;
        }
        
        public LogEntry build() {
            if (id == null) {
                id = java.util.UUID.randomUUID().toString();
            }
            return new LogEntry(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s - %s: %s (Player: %s, Session: %s)",
                timestamp, level, category, message, playerId, sessionId);
    }
}