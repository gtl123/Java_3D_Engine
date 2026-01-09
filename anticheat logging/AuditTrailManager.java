package fps.anticheat.logging;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages audit trails for compliance and investigation purposes.
 */
public class AuditTrailManager {
    private final AntiCheatLoggingManager loggingManager;
    private final Map<String, List<AuditEvent>> playerAuditTrails;
    private final AtomicLong auditEventCounter;
    private final int maxAuditEventsPerPlayer;
    
    public AuditTrailManager(AntiCheatLoggingManager loggingManager) {
        this.loggingManager = loggingManager;
        this.playerAuditTrails = new ConcurrentHashMap<>();
        this.auditEventCounter = new AtomicLong(0);
        this.maxAuditEventsPerPlayer = 10000;
    }
    
    /**
     * Record player login event
     */
    public void recordPlayerLogin(String playerId, String sessionId, String ipAddress, 
                                String userAgent, Map<String, Object> systemInfo) {
        AuditEvent event = new AuditEvent.Builder()
                .id(generateAuditId())
                .playerId(playerId)
                .sessionId(sessionId)
                .eventType(AuditEventType.PLAYER_LOGIN)
                .description("Player logged in")
                .metadata("ipAddress", ipAddress)
                .metadata("userAgent", userAgent)
                .metadata("systemInfo", systemInfo)
                .build();
        
        recordAuditEvent(event);
    }
    
    /**
     * Record player logout event
     */
    public void recordPlayerLogout(String playerId, String sessionId, String reason) {
        AuditEvent event = new AuditEvent.Builder()
                .id(generateAuditId())
                .playerId(playerId)
                .sessionId(sessionId)
                .eventType(AuditEventType.PLAYER_LOGOUT)
                .description("Player logged out")
                .metadata("reason", reason)
                .build();
        
        recordAuditEvent(event);
    }
    
    /**
     * Record game action
     */
    public void recordGameAction(String playerId, String sessionId, String action, 
                               Map<String, Object> actionData) {
        AuditEvent event = new AuditEvent.Builder()
                .id(generateAuditId())
                .playerId(playerId)
                .sessionId(sessionId)
                .eventType(AuditEventType.GAME_ACTION)
                .description("Game action: " + action)
                .metadata("action", action)
                .metadata("actionData", actionData)
                .build();
        
        recordAuditEvent(event);
    }
    
    /**
     * Record anti-cheat action
     */
    public void recordAntiCheatAction(String playerId, String sessionId, String action, 
                                    String component, Map<String, Object> details) {
        AuditEvent event = new AuditEvent.Builder()
                .id(generateAuditId())
                .playerId(playerId)
                .sessionId(sessionId)
                .eventType(AuditEventType.ANTICHEAT_ACTION)
                .description("Anti-cheat action: " + action)
                .metadata("action", action)
                .metadata("component", component)
                .metadata("details", details)
                .build();
        
        recordAuditEvent(event);
    }
    
    /**
     * Record system event
     */
    public void recordSystemEvent(String eventType, String description, Map<String, Object> details) {
        AuditEvent event = new AuditEvent.Builder()
                .id(generateAuditId())
                .eventType(AuditEventType.SYSTEM_EVENT)
                .description(description)
                .metadata("systemEventType", eventType)
                .metadata("details", details)
                .build();
        
        recordAuditEvent(event);
    }
    
    /**
     * Record administrative action
     */
    public void recordAdminAction(String adminId, String action, String targetPlayerId, 
                                String reason, Map<String, Object> details) {
        AuditEvent event = new AuditEvent.Builder()
                .id(generateAuditId())
                .playerId(targetPlayerId)
                .eventType(AuditEventType.ADMIN_ACTION)
                .description("Admin action: " + action)
                .metadata("adminId", adminId)
                .metadata("action", action)
                .metadata("reason", reason)
                .metadata("details", details)
                .build();
        
        recordAuditEvent(event);
    }
    
    /**
     * Record configuration change
     */
    public void recordConfigurationChange(String adminId, String configKey, Object oldValue, 
                                        Object newValue, String reason) {
        AuditEvent event = new AuditEvent.Builder()
                .id(generateAuditId())
                .eventType(AuditEventType.CONFIG_CHANGE)
                .description("Configuration changed: " + configKey)
                .metadata("adminId", adminId)
                .metadata("configKey", configKey)
                .metadata("oldValue", oldValue)
                .metadata("newValue", newValue)
                .metadata("reason", reason)
                .build();
        
        recordAuditEvent(event);
    }
    
    /**
     * Get audit trail for a player
     */
    public List<AuditEvent> getPlayerAuditTrail(String playerId, LocalDateTime startTime, 
                                              LocalDateTime endTime) {
        List<AuditEvent> playerEvents = playerAuditTrails.getOrDefault(playerId, new ArrayList<>());
        
        return playerEvents.stream()
                .filter(event -> event.getTimestamp().isAfter(startTime) && 
                               event.getTimestamp().isBefore(endTime))
                .sorted(Comparator.comparing(AuditEvent::getTimestamp))
                .toList();
    }
    
    /**
     * Search audit events by criteria
     */
    public CompletableFuture<List<AuditEvent>> searchAuditEvents(AuditSearchCriteria criteria) {
        return CompletableFuture.supplyAsync(() -> {
            List<AuditEvent> results = new ArrayList<>();
            
            for (List<AuditEvent> playerEvents : playerAuditTrails.values()) {
                for (AuditEvent event : playerEvents) {
                    if (matchesCriteria(event, criteria)) {
                        results.add(event);
                    }
                }
            }
            
            return results.stream()
                    .sorted(Comparator.comparing(AuditEvent::getTimestamp).reversed())
                    .limit(criteria.getMaxResults())
                    .toList();
        });
    }
    
    /**
     * Generate audit report
     */
    public CompletableFuture<AuditReport> generateAuditReport(String playerId, 
                                                            LocalDateTime startTime, 
                                                            LocalDateTime endTime) {
        return CompletableFuture.supplyAsync(() -> {
            List<AuditEvent> events = getPlayerAuditTrail(playerId, startTime, endTime);
            
            Map<AuditEventType, Long> eventCounts = events.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            AuditEvent::getEventType,
                            java.util.stream.Collectors.counting()
                    ));
            
            return new AuditReport(playerId, startTime, endTime, events, eventCounts);
        });
    }
    
    /**
     * Clean up old audit events
     */
    public void cleanupOldAuditEvents(int retentionDays) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
        
        for (String playerId : playerAuditTrails.keySet()) {
            List<AuditEvent> playerEvents = playerAuditTrails.get(playerId);
            playerEvents.removeIf(event -> event.getTimestamp().isBefore(cutoffTime));
            
            if (playerEvents.isEmpty()) {
                playerAuditTrails.remove(playerId);
            }
        }
    }
    
    private void recordAuditEvent(AuditEvent event) {
        // Add to player-specific trail
        if (event.getPlayerId() != null) {
            playerAuditTrails.computeIfAbsent(event.getPlayerId(), k -> new ArrayList<>())
                    .add(event);
            
            // Limit events per player
            List<AuditEvent> playerEvents = playerAuditTrails.get(event.getPlayerId());
            if (playerEvents.size() > maxAuditEventsPerPlayer) {
                playerEvents.remove(0); // Remove oldest
            }
        }
        
        // Log to main logging system
        LogEntry logEntry = new LogEntry.Builder()
                .id("AUDIT_" + event.getId())
                .level(LogLevel.INFO)
                .category("AUDIT_TRAIL")
                .message(event.getDescription())
                .playerId(event.getPlayerId())
                .sessionId(event.getSessionId())
                .metadata("auditEventType", event.getEventType().name())
                .metadata("auditEventId", event.getId())
                .metadata(event.getMetadata())
                .build();
        
        loggingManager.logEntry(logEntry);
    }
    
    private boolean matchesCriteria(AuditEvent event, AuditSearchCriteria criteria) {
        if (criteria.getPlayerId() != null && !criteria.getPlayerId().equals(event.getPlayerId())) {
            return false;
        }
        
        if (criteria.getEventType() != null && !criteria.getEventType().equals(event.getEventType())) {
            return false;
        }
        
        if (criteria.getStartTime() != null && event.getTimestamp().isBefore(criteria.getStartTime())) {
            return false;
        }
        
        if (criteria.getEndTime() != null && event.getTimestamp().isAfter(criteria.getEndTime())) {
            return false;
        }
        
        if (criteria.getSearchText() != null && 
            !event.getDescription().toLowerCase().contains(criteria.getSearchText().toLowerCase())) {
            return false;
        }
        
        return true;
    }
    
    private String generateAuditId() {
        return "AUD_" + auditEventCounter.incrementAndGet();
    }
    
    /**
     * Audit event types
     */
    public enum AuditEventType {
        PLAYER_LOGIN,
        PLAYER_LOGOUT,
        GAME_ACTION,
        ANTICHEAT_ACTION,
        SYSTEM_EVENT,
        ADMIN_ACTION,
        CONFIG_CHANGE,
        VIOLATION_DETECTED,
        PUNISHMENT_APPLIED,
        APPEAL_SUBMITTED,
        EVIDENCE_COLLECTED
    }
    
    /**
     * Audit event data structure
     */
    public static class AuditEvent {
        private final String id;
        private final LocalDateTime timestamp;
        private final String playerId;
        private final String sessionId;
        private final AuditEventType eventType;
        private final String description;
        private final Map<String, Object> metadata;
        
        private AuditEvent(Builder builder) {
            this.id = builder.id;
            this.timestamp = builder.timestamp;
            this.playerId = builder.playerId;
            this.sessionId = builder.sessionId;
            this.eventType = builder.eventType;
            this.description = builder.description;
            this.metadata = new HashMap<>(builder.metadata);
        }
        
        // Getters
        public String getId() { return id; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getPlayerId() { return playerId; }
        public String getSessionId() { return sessionId; }
        public AuditEventType getEventType() { return eventType; }
        public String getDescription() { return description; }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        
        public static class Builder {
            private String id;
            private LocalDateTime timestamp = LocalDateTime.now();
            private String playerId;
            private String sessionId;
            private AuditEventType eventType;
            private String description;
            private Map<String, Object> metadata = new HashMap<>();
            
            public Builder id(String id) { this.id = id; return this; }
            public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
            public Builder playerId(String playerId) { this.playerId = playerId; return this; }
            public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
            public Builder eventType(AuditEventType eventType) { this.eventType = eventType; return this; }
            public Builder description(String description) { this.description = description; return this; }
            public Builder metadata(String key, Object value) { this.metadata.put(key, value); return this; }
            public Builder metadata(Map<String, Object> metadata) { this.metadata.putAll(metadata); return this; }
            
            public AuditEvent build() {
                return new AuditEvent(this);
            }
        }
    }
    
    /**
     * Audit search criteria
     */
    public static class AuditSearchCriteria {
        private String playerId;
        private AuditEventType eventType;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String searchText;
        private int maxResults = 1000;
        
        // Getters and setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        
        public AuditEventType getEventType() { return eventType; }
        public void setEventType(AuditEventType eventType) { this.eventType = eventType; }
        
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        
        public String getSearchText() { return searchText; }
        public void setSearchText(String searchText) { this.searchText = searchText; }
        
        public int getMaxResults() { return maxResults; }
        public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
    }
    
    /**
     * Audit report
     */
    public static class AuditReport {
        private final String playerId;
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;
        private final List<AuditEvent> events;
        private final Map<AuditEventType, Long> eventCounts;
        private final LocalDateTime generatedAt;
        
        public AuditReport(String playerId, LocalDateTime startTime, LocalDateTime endTime,
                          List<AuditEvent> events, Map<AuditEventType, Long> eventCounts) {
            this.playerId = playerId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.events = new ArrayList<>(events);
            this.eventCounts = new HashMap<>(eventCounts);
            this.generatedAt = LocalDateTime.now();
        }
        
        // Getters
        public String getPlayerId() { return playerId; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public List<AuditEvent> getEvents() { return new ArrayList<>(events); }
        public Map<AuditEventType, Long> getEventCounts() { return new HashMap<>(eventCounts); }
        public LocalDateTime getGeneratedAt() { return generatedAt; }
    }
}