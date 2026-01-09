package fps.anticheat.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.io.*;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;

/**
 * Manages log archiving, compression, and retention policies.
 */
public class LogArchiveManager {
    private final AntiCheatLoggingManager loggingManager;
    private final ScheduledExecutorService archiveExecutor;
    private final Map<String, ArchiveInfo> archiveIndex;
    private final String archiveBasePath;
    private final int retentionDays;
    private final long maxArchiveSize;
    private final boolean compressionEnabled;
    
    public LogArchiveManager(AntiCheatLoggingManager loggingManager, String archiveBasePath) {
        this.loggingManager = loggingManager;
        this.archiveExecutor = Executors.newScheduledThreadPool(2);
        this.archiveIndex = new ConcurrentHashMap<>();
        this.archiveBasePath = archiveBasePath;
        this.retentionDays = 90; // 90 days retention
        this.maxArchiveSize = 1024L * 1024L * 1024L; // 1GB per archive
        this.compressionEnabled = true;
        
        // Schedule periodic archiving
        schedulePeriodicArchiving();
    }
    
    /**
     * Archive logs older than specified days
     */
    public CompletableFuture<ArchiveResult> archiveOldLogs(int olderThanDays) {
        return CompletableFuture.supplyAsync(() -> {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(olderThanDays);
            
            try {
                // Get logs to archive
                List<LogEntry> logsToArchive = getLogsOlderThan(cutoffDate);
                
                if (logsToArchive.isEmpty()) {
                    return new ArchiveResult(true, "No logs to archive", 0, 0);
                }
                
                // Group logs by date for efficient archiving
                Map<String, List<LogEntry>> logsByDate = groupLogsByDate(logsToArchive);
                
                int archivedFiles = 0;
                long totalSize = 0;
                
                for (Map.Entry<String, List<LogEntry>> entry : logsByDate.entrySet()) {
                    String date = entry.getKey();
                    List<LogEntry> logs = entry.getValue();
                    
                    String archiveFileName = generateArchiveFileName(date);
                    long archiveSize = createArchive(archiveFileName, logs);
                    
                    if (archiveSize > 0) {
                        archivedFiles++;
                        totalSize += archiveSize;
                        
                        // Update archive index
                        ArchiveInfo archiveInfo = new ArchiveInfo(
                                archiveFileName,
                                date,
                                logs.size(),
                                archiveSize,
                                LocalDateTime.now()
                        );
                        archiveIndex.put(archiveFileName, archiveInfo);
                        
                        // Remove archived logs from active storage
                        removeArchivedLogs(logs);
                    }
                }
                
                // Log archiving completion
                LogEntry archiveLog = new LogEntry.Builder()
                        .level(LogLevel.INFO)
                        .category("LOG_ARCHIVE")
                        .message("Log archiving completed")
                        .metadata("archivedFiles", archivedFiles)
                        .metadata("totalSize", totalSize)
                        .metadata("cutoffDate", cutoffDate.toString())
                        .build();
                
                loggingManager.logEntry(archiveLog);
                
                return new ArchiveResult(true, "Archiving completed successfully", 
                                       archivedFiles, totalSize);
                
            } catch (Exception e) {
                // Log error
                LogEntry errorLog = new LogEntry.Builder()
                        .level(LogLevel.ERROR)
                        .category("ARCHIVE_ERROR")
                        .message("Failed to archive logs")
                        .metadata("error", e.getMessage())
                        .stackTrace(getStackTrace(e))
                        .build();
                
                loggingManager.logEntry(errorLog);
                
                return new ArchiveResult(false, "Archiving failed: " + e.getMessage(), 0, 0);
            }
        });
    }
    
    /**
     * Retrieve logs from archive
     */
    public CompletableFuture<List<LogEntry>> retrieveFromArchive(String archiveFileName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ArchiveInfo archiveInfo = archiveIndex.get(archiveFileName);
                if (archiveInfo == null) {
                    return new ArrayList<>();
                }
                
                return readArchive(archiveFileName);
                
            } catch (Exception e) {
                // Log error
                LogEntry errorLog = new LogEntry.Builder()
                        .level(LogLevel.ERROR)
                        .category("ARCHIVE_RETRIEVAL_ERROR")
                        .message("Failed to retrieve logs from archive")
                        .metadata("archiveFileName", archiveFileName)
                        .metadata("error", e.getMessage())
                        .build();
                
                loggingManager.logEntry(errorLog);
                
                return new ArrayList<>();
            }
        });
    }
    
    /**
     * Search archived logs
     */
    public CompletableFuture<List<LogEntry>> searchArchives(ArchiveSearchCriteria criteria) {
        return CompletableFuture.supplyAsync(() -> {
            List<LogEntry> results = new ArrayList<>();
            
            try {
                // Find relevant archives based on date range
                List<String> relevantArchives = findRelevantArchives(criteria);
                
                for (String archiveFileName : relevantArchives) {
                    List<LogEntry> archiveLogs = readArchive(archiveFileName);
                    
                    // Filter logs based on criteria
                    List<LogEntry> filteredLogs = filterLogs(archiveLogs, criteria);
                    results.addAll(filteredLogs);
                    
                    // Limit results to prevent memory issues
                    if (results.size() >= criteria.getMaxResults()) {
                        results = results.subList(0, criteria.getMaxResults());
                        break;
                    }
                }
                
            } catch (Exception e) {
                // Log error
                LogEntry errorLog = new LogEntry.Builder()
                        .level(LogLevel.ERROR)
                        .category("ARCHIVE_SEARCH_ERROR")
                        .message("Failed to search archives")
                        .metadata("error", e.getMessage())
                        .build();
                
                loggingManager.logEntry(errorLog);
            }
            
            return results;
        });
    }
    
    /**
     * Clean up old archives based on retention policy
     */
    public CompletableFuture<CleanupResult> cleanupOldArchives() {
        return CompletableFuture.supplyAsync(() -> {
            LocalDateTime retentionCutoff = LocalDateTime.now().minusDays(retentionDays);
            
            int deletedArchives = 0;
            long freedSpace = 0;
            
            try {
                List<String> archivesToDelete = new ArrayList<>();
                
                for (Map.Entry<String, ArchiveInfo> entry : archiveIndex.entrySet()) {
                    ArchiveInfo archiveInfo = entry.getValue();
                    if (archiveInfo.getCreatedAt().isBefore(retentionCutoff)) {
                        archivesToDelete.add(entry.getKey());
                    }
                }
                
                for (String archiveFileName : archivesToDelete) {
                    ArchiveInfo archiveInfo = archiveIndex.get(archiveFileName);
                    if (deleteArchive(archiveFileName)) {
                        deletedArchives++;
                        freedSpace += archiveInfo.getSize();
                        archiveIndex.remove(archiveFileName);
                    }
                }
                
                // Log cleanup completion
                LogEntry cleanupLog = new LogEntry.Builder()
                        .level(LogLevel.INFO)
                        .category("ARCHIVE_CLEANUP")
                        .message("Archive cleanup completed")
                        .metadata("deletedArchives", deletedArchives)
                        .metadata("freedSpace", freedSpace)
                        .metadata("retentionDays", retentionDays)
                        .build();
                
                loggingManager.logEntry(cleanupLog);
                
                return new CleanupResult(true, deletedArchives, freedSpace);
                
            } catch (Exception e) {
                // Log error
                LogEntry errorLog = new LogEntry.Builder()
                        .level(LogLevel.ERROR)
                        .category("CLEANUP_ERROR")
                        .message("Failed to cleanup old archives")
                        .metadata("error", e.getMessage())
                        .build();
                
                loggingManager.logEntry(errorLog);
                
                return new CleanupResult(false, 0, 0);
            }
        });
    }
    
    /**
     * Get archive statistics
     */
    public ArchiveStatistics getArchiveStatistics() {
        long totalSize = archiveIndex.values().stream()
                .mapToLong(ArchiveInfo::getSize)
                .sum();
        
        int totalArchives = archiveIndex.size();
        
        long totalEntries = archiveIndex.values().stream()
                .mapToLong(ArchiveInfo::getEntryCount)
                .sum();
        
        Optional<LocalDateTime> oldestArchive = archiveIndex.values().stream()
                .map(ArchiveInfo::getCreatedAt)
                .min(LocalDateTime::compareTo);
        
        Optional<LocalDateTime> newestArchive = archiveIndex.values().stream()
                .map(ArchiveInfo::getCreatedAt)
                .max(LocalDateTime::compareTo);
        
        return new ArchiveStatistics(
                totalArchives,
                totalEntries,
                totalSize,
                oldestArchive.orElse(null),
                newestArchive.orElse(null)
        );
    }
    
    /**
     * Export archive index
     */
    public CompletableFuture<String> exportArchiveIndex() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StringBuilder indexData = new StringBuilder();
                indexData.append("Archive Index Export - ").append(LocalDateTime.now()).append("\n");
                indexData.append("=".repeat(50)).append("\n\n");
                
                for (ArchiveInfo archiveInfo : archiveIndex.values()) {
                    indexData.append("Archive: ").append(archiveInfo.getFileName()).append("\n");
                    indexData.append("Date: ").append(archiveInfo.getDateRange()).append("\n");
                    indexData.append("Entries: ").append(archiveInfo.getEntryCount()).append("\n");
                    indexData.append("Size: ").append(archiveInfo.getSize()).append(" bytes\n");
                    indexData.append("Created: ").append(archiveInfo.getCreatedAt()).append("\n");
                    indexData.append("-".repeat(30)).append("\n");
                }
                
                String exportFileName = "archive_index_" + 
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt";
                
                // Write to file (implementation would depend on file system)
                String exportPath = archiveBasePath + "/exports/" + exportFileName;
                
                return exportPath;
                
            } catch (Exception e) {
                return null;
            }
        });
    }
    
    private void schedulePeriodicArchiving() {
        // Archive logs daily at 2 AM
        archiveExecutor.scheduleAtFixedRate(() -> {
            archiveOldLogs(7); // Archive logs older than 7 days
        }, 1, 24, TimeUnit.HOURS);
        
        // Cleanup old archives weekly
        archiveExecutor.scheduleAtFixedRate(() -> {
            cleanupOldArchives();
        }, 1, 7 * 24, TimeUnit.HOURS);
    }
    
    private List<LogEntry> getLogsOlderThan(LocalDateTime cutoffDate) {
        // Implementation would query the active log storage
        // This is a placeholder - actual implementation would depend on storage system
        return new ArrayList<>();
    }
    
    private Map<String, List<LogEntry>> groupLogsByDate(List<LogEntry> logs) {
        Map<String, List<LogEntry>> grouped = new HashMap<>();
        
        for (LogEntry log : logs) {
            String date = log.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            grouped.computeIfAbsent(date, k -> new ArrayList<>()).add(log);
        }
        
        return grouped;
    }
    
    private String generateArchiveFileName(String date) {
        return String.format("anticheat_logs_%s.%s", date, compressionEnabled ? "gz" : "log");
    }
    
    private long createArchive(String fileName, List<LogEntry> logs) throws IOException {
        String fullPath = archiveBasePath + "/" + fileName;
        
        try (FileOutputStream fos = new FileOutputStream(fullPath);
             OutputStream os = compressionEnabled ? new GZIPOutputStream(fos) : fos;
             PrintWriter writer = new PrintWriter(os)) {
            
            for (LogEntry log : logs) {
                writer.println(serializeLogEntry(log));
            }
        }
        
        return new File(fullPath).length();
    }
    
    private List<LogEntry> readArchive(String fileName) throws IOException {
        String fullPath = archiveBasePath + "/" + fileName;
        List<LogEntry> logs = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(fullPath);
             InputStream is = compressionEnabled ? new GZIPInputStream(fis) : fis;
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                LogEntry log = deserializeLogEntry(line);
                if (log != null) {
                    logs.add(log);
                }
            }
        }
        
        return logs;
    }
    
    private void removeArchivedLogs(List<LogEntry> logs) {
        // Implementation would remove logs from active storage
        // This is a placeholder - actual implementation would depend on storage system
    }
    
    private boolean deleteArchive(String fileName) {
        String fullPath = archiveBasePath + "/" + fileName;
        return new File(fullPath).delete();
    }
    
    private List<String> findRelevantArchives(ArchiveSearchCriteria criteria) {
        return archiveIndex.entrySet().stream()
                .filter(entry -> {
                    ArchiveInfo info = entry.getValue();
                    // Check if archive date range overlaps with search criteria
                    return isDateRangeRelevant(info.getDateRange(), criteria.getStartDate(), criteria.getEndDate());
                })
                .map(Map.Entry::getKey)
                .toList();
    }
    
    private boolean isDateRangeRelevant(String archiveDateRange, LocalDateTime startDate, LocalDateTime endDate) {
        // Simplified implementation - would need proper date range parsing
        return true;
    }
    
    private List<LogEntry> filterLogs(List<LogEntry> logs, ArchiveSearchCriteria criteria) {
        return logs.stream()
                .filter(log -> {
                    if (criteria.getPlayerId() != null && 
                        !criteria.getPlayerId().equals(log.getPlayerId())) {
                        return false;
                    }
                    
                    if (criteria.getLogLevel() != null && 
                        !log.getLevel().isAtLeast(criteria.getLogLevel())) {
                        return false;
                    }
                    
                    if (criteria.getCategory() != null && 
                        !criteria.getCategory().equals(log.getCategory())) {
                        return false;
                    }
                    
                    if (criteria.getSearchText() != null && 
                        !log.getMessage().toLowerCase().contains(criteria.getSearchText().toLowerCase())) {
                        return false;
                    }
                    
                    return true;
                })
                .toList();
    }
    
    private String serializeLogEntry(LogEntry log) {
        // Simplified serialization - would use proper JSON/binary serialization
        return String.format("%s|%s|%s|%s|%s|%s|%s",
                log.getId(),
                log.getTimestamp(),
                log.getLevel(),
                log.getCategory(),
                log.getMessage(),
                log.getPlayerId(),
                log.getSessionId()
        );
    }
    
    private LogEntry deserializeLogEntry(String serialized) {
        // Simplified deserialization - would use proper JSON/binary deserialization
        try {
            String[] parts = serialized.split("\\|");
            if (parts.length >= 7) {
                return new LogEntry.Builder()
                        .id(parts[0])
                        .timestamp(LocalDateTime.parse(parts[1]))
                        .level(LogLevel.valueOf(parts[2]))
                        .category(parts[3])
                        .message(parts[4])
                        .playerId(parts[5])
                        .sessionId(parts[6])
                        .build();
            }
        } catch (Exception e) {
            // Log parsing error
        }
        return null;
    }
    
    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    public void shutdown() {
        archiveExecutor.shutdown();
        try {
            if (!archiveExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                archiveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            archiveExecutor.shutdownNow();
        }
    }
    
    // Data classes
    public static class ArchiveInfo {
        private final String fileName;
        private final String dateRange;
        private final long entryCount;
        private final long size;
        private final LocalDateTime createdAt;
        
        public ArchiveInfo(String fileName, String dateRange, long entryCount, 
                          long size, LocalDateTime createdAt) {
            this.fileName = fileName;
            this.dateRange = dateRange;
            this.entryCount = entryCount;
            this.size = size;
            this.createdAt = createdAt;
        }
        
        // Getters
        public String getFileName() { return fileName; }
        public String getDateRange() { return dateRange; }
        public long getEntryCount() { return entryCount; }
        public long getSize() { return size; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
    
    public static class ArchiveResult {
        private final boolean success;
        private final String message;
        private final int archivedFiles;
        private final long totalSize;
        
        public ArchiveResult(boolean success, String message, int archivedFiles, long totalSize) {
            this.success = success;
            this.message = message;
            this.archivedFiles = archivedFiles;
            this.totalSize = totalSize;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getArchivedFiles() { return archivedFiles; }
        public long getTotalSize() { return totalSize; }
    }
    
    public static class CleanupResult {
        private final boolean success;
        private final int deletedArchives;
        private final long freedSpace;
        
        public CleanupResult(boolean success, int deletedArchives, long freedSpace) {
            this.success = success;
            this.deletedArchives = deletedArchives;
            this.freedSpace = freedSpace;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public int getDeletedArchives() { return deletedArchives; }
        public long getFreedSpace() { return freedSpace; }
    }
    
    public static class ArchiveSearchCriteria {
        private String playerId;
        private LogLevel logLevel;
        private String category;
        private String searchText;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private int maxResults = 1000;
        
        // Getters and setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        
        public LogLevel getLogLevel() { return logLevel; }
        public void setLogLevel(LogLevel logLevel) { this.logLevel = logLevel; }
        
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        
        public String getSearchText() { return searchText; }
        public void setSearchText(String searchText) { this.searchText = searchText; }
        
        public LocalDateTime getStartDate() { return startDate; }
        public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
        
        public LocalDateTime getEndDate() { return endDate; }
        public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
        
        public int getMaxResults() { return maxResults; }
        public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
    }
    
    public static class ArchiveStatistics {
        private final int totalArchives;
        private final long totalEntries;
        private final long totalSize;
        private final LocalDateTime oldestArchive;
        private final LocalDateTime newestArchive;
        
        public ArchiveStatistics(int totalArchives, long totalEntries, long totalSize,
                               LocalDateTime oldestArchive, LocalDateTime newestArchive) {
            this.totalArchives = totalArchives;
            this.totalEntries = totalEntries;
            this.totalSize = totalSize;
            this.oldestArchive = oldestArchive;
            this.newestArchive = newestArchive;
        }
        
        // Getters
        public int getTotalArchives() { return totalArchives; }
        public long getTotalEntries() { return totalEntries; }
        public long getTotalSize() { return totalSize; }
        public LocalDateTime getOldestArchive() { return oldestArchive; }
        public LocalDateTime getNewestArchive() { return newestArchive; }
    }
}