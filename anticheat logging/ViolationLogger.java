package fps.anticheat.logging;

import fps.anticheat.detection.ViolationData;
import fps.anticheat.detection.ViolationType;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Specialized logger for anti-cheat violations.
 */
public class ViolationLogger {
    private final AntiCheatLoggingManager loggingManager;
    private final Map<String, ViolationStats> playerViolationStats;
    private final AtomicLong violationCounter;
    
    public ViolationLogger(AntiCheatLoggingManager loggingManager) {
        this.loggingManager = loggingManager;
        this.playerViolationStats = new ConcurrentHashMap<>();
        this.violationCounter = new AtomicLong(0);
    }
    
    /**
     * Log a violation with detailed information
     */
    public void logViolation(ViolationData violation) {
        long violationId = violationCounter.incrementAndGet();
        
        LogEntry entry = new LogEntry.Builder()
                .id("VIOLATION_" + violationId)
                .level(getLogLevelForViolation(violation))
                .category("VIOLATION")
                .message(formatViolationMessage(violation))
                .playerId(violation.getPlayerId())
                .sessionId(violation.getSessionId())
                .metadata("violationType", violation.getType().name())
                .metadata("severity", violation.getSeverity())
                .metadata("confidence", violation.getConfidence())
                .metadata("violationId", violationId)
                .metadata("gameMode", violation.getGameMode())
                .metadata("mapName", violation.getMapName())
                .metadata("playerPosition", violation.getPlayerPosition())
                .metadata("additionalData", violation.getAdditionalData())
                .build();
        
        loggingManager.logEntry(entry);
        updateViolationStats(violation.getPlayerId(), violation.getType());
    }
    
    /**
     * Log a suspected violation that needs further investigation
     */
    public void logSuspiciousActivity(String playerId, String sessionId, ViolationType type, 
                                    String description, double suspicionLevel) {
        LogEntry entry = new LogEntry.Builder()
                .level(LogLevel.WARNING)
                .category("SUSPICIOUS_ACTIVITY")
                .message("Suspicious activity detected: " + description)
                .playerId(playerId)
                .sessionId(sessionId)
                .metadata("violationType", type.name())
                .metadata("suspicionLevel", suspicionLevel)
                .metadata("description", description)
                .build();
        
        loggingManager.logEntry(entry);
    }
    
    /**
     * Log false positive detection
     */
    public void logFalsePositive(String playerId, String sessionId, ViolationType type, 
                               String reason, String correctionAction) {
        LogEntry entry = new LogEntry.Builder()
                .level(LogLevel.INFO)
                .category("FALSE_POSITIVE")
                .message("False positive detected and corrected")
                .playerId(playerId)
                .sessionId(sessionId)
                .metadata("violationType", type.name())
                .metadata("reason", reason)
                .metadata("correctionAction", correctionAction)
                .build();
        
        loggingManager.logEntry(entry);
    }
    
    /**
     * Log violation pattern detection
     */
    public void logViolationPattern(String playerId, ViolationType type, int count, 
                                  LocalDateTime timeWindow, String pattern) {
        LogEntry entry = new LogEntry.Builder()
                .level(LogLevel.ERROR)
                .category("VIOLATION_PATTERN")
                .message("Violation pattern detected")
                .playerId(playerId)
                .metadata("violationType", type.name())
                .metadata("violationCount", count)
                .metadata("timeWindow", timeWindow.toString())
                .metadata("pattern", pattern)
                .build();
        
        loggingManager.logEntry(entry);
    }
    
    /**
     * Get violation statistics for a player
     */
    public ViolationStats getPlayerViolationStats(String playerId) {
        return playerViolationStats.getOrDefault(playerId, new ViolationStats());
    }
    
    /**
     * Clear violation statistics for a player
     */
    public void clearPlayerStats(String playerId) {
        playerViolationStats.remove(playerId);
    }
    
    private LogLevel getLogLevelForViolation(ViolationData violation) {
        double severity = violation.getSeverity();
        if (severity >= 0.9) return LogLevel.CRITICAL;
        if (severity >= 0.7) return LogLevel.ERROR;
        if (severity >= 0.5) return LogLevel.WARNING;
        return LogLevel.INFO;
    }
    
    private String formatViolationMessage(ViolationData violation) {
        return String.format("Violation detected: %s (Severity: %.2f, Confidence: %.2f)",
                violation.getType().name(), violation.getSeverity(), violation.getConfidence());
    }
    
    private void updateViolationStats(String playerId, ViolationType type) {
        playerViolationStats.computeIfAbsent(playerId, k -> new ViolationStats())
                .incrementViolation(type);
    }
    
    /**
     * Statistics for player violations
     */
    public static class ViolationStats {
        private final Map<ViolationType, AtomicLong> violationCounts;
        private final LocalDateTime firstViolation;
        private volatile LocalDateTime lastViolation;
        
        public ViolationStats() {
            this.violationCounts = new ConcurrentHashMap<>();
            this.firstViolation = LocalDateTime.now();
            this.lastViolation = LocalDateTime.now();
        }
        
        public void incrementViolation(ViolationType type) {
            violationCounts.computeIfAbsent(type, k -> new AtomicLong(0)).incrementAndGet();
            lastViolation = LocalDateTime.now();
        }
        
        public long getViolationCount(ViolationType type) {
            return violationCounts.getOrDefault(type, new AtomicLong(0)).get();
        }
        
        public long getTotalViolations() {
            return violationCounts.values().stream()
                    .mapToLong(AtomicLong::get)
                    .sum();
        }
        
        public LocalDateTime getFirstViolation() { return firstViolation; }
        public LocalDateTime getLastViolation() { return lastViolation; }
        public Map<ViolationType, AtomicLong> getAllViolationCounts() { 
            return new ConcurrentHashMap<>(violationCounts); 
        }
    }
}