package fps.anticheat.logging;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of a log search operation.
 */
public class LogSearchResult {
    private final List<LogEntry> entries;
    private final int totalMatches;
    private final boolean hasMore;
    private final LocalDateTime searchTime;
    private final long searchDurationMs;
    private final LogSearchCriteria criteria;
    private final Map<String, Object> statistics;
    
    public LogSearchResult(List<LogEntry> entries, int totalMatches, boolean hasMore,
                          LocalDateTime searchTime, long searchDurationMs, 
                          LogSearchCriteria criteria) {
        this.entries = new ArrayList<>(entries);
        this.totalMatches = totalMatches;
        this.hasMore = hasMore;
        this.searchTime = searchTime;
        this.searchDurationMs = searchDurationMs;
        this.criteria = criteria;
        this.statistics = calculateStatistics();
    }
    
    /**
     * Get the log entries found
     */
    public List<LogEntry> getEntries() {
        return new ArrayList<>(entries);
    }
    
    /**
     * Get total number of matches (may be more than entries returned due to limits)
     */
    public int getTotalMatches() {
        return totalMatches;
    }
    
    /**
     * Check if there are more results available
     */
    public boolean hasMore() {
        return hasMore;
    }
    
    /**
     * Get the time when search was performed
     */
    public LocalDateTime getSearchTime() {
        return searchTime;
    }
    
    /**
     * Get search duration in milliseconds
     */
    public long getSearchDurationMs() {
        return searchDurationMs;
    }
    
    /**
     * Get the search criteria used
     */
    public LogSearchCriteria getCriteria() {
        return criteria;
    }
    
    /**
     * Get search result statistics
     */
    public Map<String, Object> getStatistics() {
        return new HashMap<>(statistics);
    }
    
    /**
     * Get entries grouped by log level
     */
    public Map<LogLevel, List<LogEntry>> getEntriesByLevel() {
        Map<LogLevel, List<LogEntry>> grouped = new HashMap<>();
        
        for (LogEntry entry : entries) {
            grouped.computeIfAbsent(entry.getLevel(), k -> new ArrayList<>()).add(entry);
        }
        
        return grouped;
    }
    
    /**
     * Get entries grouped by category
     */
    public Map<String, List<LogEntry>> getEntriesByCategory() {
        Map<String, List<LogEntry>> grouped = new HashMap<>();
        
        for (LogEntry entry : entries) {
            String category = entry.getCategory() != null ? entry.getCategory() : "UNKNOWN";
            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(entry);
        }
        
        return grouped;
    }
    
    /**
     * Get entries for a specific player
     */
    public List<LogEntry> getEntriesForPlayer(String playerId) {
        return entries.stream()
                .filter(entry -> playerId.equals(entry.getPlayerId()))
                .toList();
    }
    
    /**
     * Get unique player IDs in results
     */
    public List<String> getUniquePlayerIds() {
        return entries.stream()
                .map(LogEntry::getPlayerId)
                .filter(playerId -> playerId != null)
                .distinct()
                .toList();
    }
    
    /**
     * Get time range of results
     */
    public TimeRange getTimeRange() {
        if (entries.isEmpty()) {
            return null;
        }
        
        LocalDateTime earliest = entries.stream()
                .map(LogEntry::getTimestamp)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        
        LocalDateTime latest = entries.stream()
                .map(LogEntry::getTimestamp)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        
        return new TimeRange(earliest, latest);
    }
    
    /**
     * Export results to formatted string
     */
    public String exportToString(ExportFormat format) {
        switch (format) {
            case CSV:
                return exportToCsv();
            case JSON:
                return exportToJson();
            case TEXT:
            default:
                return exportToText();
        }
    }
    
    private Map<String, Object> calculateStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Count by level
        Map<LogLevel, Long> levelCounts = entries.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        LogEntry::getLevel,
                        java.util.stream.Collectors.counting()
                ));
        stats.put("levelCounts", levelCounts);
        
        // Count by category
        Map<String, Long> categoryCounts = entries.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        entry -> entry.getCategory() != null ? entry.getCategory() : "UNKNOWN",
                        java.util.stream.Collectors.counting()
                ));
        stats.put("categoryCounts", categoryCounts);
        
        // Unique players
        long uniquePlayers = entries.stream()
                .map(LogEntry::getPlayerId)
                .filter(playerId -> playerId != null)
                .distinct()
                .count();
        stats.put("uniquePlayers", uniquePlayers);
        
        // Time span
        TimeRange timeRange = getTimeRange();
        if (timeRange != null) {
            stats.put("timeSpan", timeRange);
        }
        
        return stats;
    }
    
    private String exportToCsv() {
        StringBuilder csv = new StringBuilder();
        
        // Header
        csv.append("ID,Timestamp,Level,Category,Message,PlayerID,SessionID\n");
        
        // Data
        for (LogEntry entry : entries) {
            csv.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    escapeForCsv(entry.getId()),
                    entry.getTimestamp().toString(),
                    entry.getLevel().toString(),
                    escapeForCsv(entry.getCategory()),
                    escapeForCsv(entry.getMessage()),
                    escapeForCsv(entry.getPlayerId()),
                    escapeForCsv(entry.getSessionId())
            ));
        }
        
        return csv.toString();
    }
    
    private String exportToJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"searchTime\": \"").append(searchTime).append("\",\n");
        json.append("  \"searchDurationMs\": ").append(searchDurationMs).append(",\n");
        json.append("  \"totalMatches\": ").append(totalMatches).append(",\n");
        json.append("  \"hasMore\": ").append(hasMore).append(",\n");
        json.append("  \"entries\": [\n");
        
        for (int i = 0; i < entries.size(); i++) {
            LogEntry entry = entries.get(i);
            json.append("    {\n");
            json.append("      \"id\": \"").append(escapeForJson(entry.getId())).append("\",\n");
            json.append("      \"timestamp\": \"").append(entry.getTimestamp()).append("\",\n");
            json.append("      \"level\": \"").append(entry.getLevel()).append("\",\n");
            json.append("      \"category\": \"").append(escapeForJson(entry.getCategory())).append("\",\n");
            json.append("      \"message\": \"").append(escapeForJson(entry.getMessage())).append("\",\n");
            json.append("      \"playerId\": \"").append(escapeForJson(entry.getPlayerId())).append("\",\n");
            json.append("      \"sessionId\": \"").append(escapeForJson(entry.getSessionId())).append("\"\n");
            json.append("    }");
            
            if (i < entries.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        
        json.append("  ]\n");
        json.append("}");
        
        return json.toString();
    }
    
    private String exportToText() {
        StringBuilder text = new StringBuilder();
        
        text.append("Log Search Results\n");
        text.append("==================\n");
        text.append("Search Time: ").append(searchTime).append("\n");
        text.append("Duration: ").append(searchDurationMs).append(" ms\n");
        text.append("Total Matches: ").append(totalMatches).append("\n");
        text.append("Returned: ").append(entries.size()).append("\n");
        text.append("Has More: ").append(hasMore).append("\n\n");
        
        for (LogEntry entry : entries) {
            text.append(entry.toString()).append("\n");
        }
        
        return text.toString();
    }
    
    private String escapeForCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }
    
    private String escapeForJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    /**
     * Time range helper class
     */
    public static class TimeRange {
        private final LocalDateTime start;
        private final LocalDateTime end;
        
        public TimeRange(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
        
        public LocalDateTime getStart() { return start; }
        public LocalDateTime getEnd() { return end; }
        
        public long getDurationMinutes() {
            if (start != null && end != null) {
                return java.time.Duration.between(start, end).toMinutes();
            }
            return 0;
        }
        
        @Override
        public String toString() {
            return String.format("%s to %s (%d minutes)", start, end, getDurationMinutes());
        }
    }
    
    /**
     * Export format options
     */
    public enum ExportFormat {
        TEXT,
        CSV,
        JSON
    }
}