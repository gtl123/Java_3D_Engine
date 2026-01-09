package fps.anticheat.logging;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Search criteria for querying logs.
 */
public class LogSearchCriteria {
    private String playerId;
    private String sessionId;
    private LogLevel minLevel;
    private String category;
    private String searchText;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Map<String, Object> metadataFilters;
    private int maxResults;
    private String sortBy;
    private boolean sortDescending;
    
    public LogSearchCriteria() {
        this.metadataFilters = new HashMap<>();
        this.maxResults = 1000;
        this.sortBy = "timestamp";
        this.sortDescending = true;
    }
    
    // Builder pattern for easy construction
    public static class Builder {
        private final LogSearchCriteria criteria = new LogSearchCriteria();
        
        public Builder playerId(String playerId) {
            criteria.playerId = playerId;
            return this;
        }
        
        public Builder sessionId(String sessionId) {
            criteria.sessionId = sessionId;
            return this;
        }
        
        public Builder minLevel(LogLevel minLevel) {
            criteria.minLevel = minLevel;
            return this;
        }
        
        public Builder category(String category) {
            criteria.category = category;
            return this;
        }
        
        public Builder searchText(String searchText) {
            criteria.searchText = searchText;
            return this;
        }
        
        public Builder timeRange(LocalDateTime startTime, LocalDateTime endTime) {
            criteria.startTime = startTime;
            criteria.endTime = endTime;
            return this;
        }
        
        public Builder metadataFilter(String key, Object value) {
            criteria.metadataFilters.put(key, value);
            return this;
        }
        
        public Builder maxResults(int maxResults) {
            criteria.maxResults = maxResults;
            return this;
        }
        
        public Builder sortBy(String sortBy, boolean descending) {
            criteria.sortBy = sortBy;
            criteria.sortDescending = descending;
            return this;
        }
        
        public LogSearchCriteria build() {
            return criteria;
        }
    }
    
    // Getters and setters
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public LogLevel getMinLevel() { return minLevel; }
    public void setMinLevel(LogLevel minLevel) { this.minLevel = minLevel; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public String getSearchText() { return searchText; }
    public void setSearchText(String searchText) { this.searchText = searchText; }
    
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    
    public Map<String, Object> getMetadataFilters() { return new HashMap<>(metadataFilters); }
    public void setMetadataFilters(Map<String, Object> metadataFilters) { 
        this.metadataFilters = new HashMap<>(metadataFilters); 
    }
    
    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
    
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    
    public boolean isSortDescending() { return sortDescending; }
    public void setSortDescending(boolean sortDescending) { this.sortDescending = sortDescending; }
    
    /**
     * Check if a log entry matches these criteria
     */
    public boolean matches(LogEntry logEntry) {
        if (playerId != null && !playerId.equals(logEntry.getPlayerId())) {
            return false;
        }
        
        if (sessionId != null && !sessionId.equals(logEntry.getSessionId())) {
            return false;
        }
        
        if (minLevel != null && !logEntry.getLevel().isAtLeast(minLevel)) {
            return false;
        }
        
        if (category != null && !category.equals(logEntry.getCategory())) {
            return false;
        }
        
        if (searchText != null && !logEntry.getMessage().toLowerCase()
                .contains(searchText.toLowerCase())) {
            return false;
        }
        
        if (startTime != null && logEntry.getTimestamp().isBefore(startTime)) {
            return false;
        }
        
        if (endTime != null && logEntry.getTimestamp().isAfter(endTime)) {
            return false;
        }
        
        // Check metadata filters
        for (Map.Entry<String, Object> filter : metadataFilters.entrySet()) {
            Object logValue = logEntry.getMetadata().get(filter.getKey());
            if (!Objects.equals(logValue, filter.getValue())) {
                return false;
            }
        }
        
        return true;
    }
}