package fps.anticheat.logging;

import fps.anticheat.*;
import fps.anticheat.punishment.*;
import fps.anticheat.security.*;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.util.zip.GZIPOutputStream;

/**
 * Comprehensive logging and evidence collection system for the anti-cheat engine.
 * Provides detailed audit trails, forensic evidence collection, and secure log storage.
 */
public class AntiCheatLoggingManager {
    
    private final AntiCheatConfiguration config;
    private final ExecutorService loggingExecutor;
    private final ScheduledExecutorService scheduledExecutor;
    
    // Logging components
    private final ViolationLogger violationLogger;
    private final EvidenceCollector evidenceCollector;
    private final AuditTrailManager auditTrailManager;
    private final ForensicDataCollector forensicCollector;
    private final LogArchiveManager archiveManager;
    
    // Log storage
    private final Map<LogLevel, LogWriter> logWriters;
    private final Queue<LogEntry> pendingLogs;
    private final SecurityObfuscator securityObfuscator;
    
    // Configuration
    private final Path logDirectory;
    private final boolean encryptLogs;
    private final boolean compressLogs;
    private final long maxLogFileSize;
    private final int maxLogFiles;
    
    public AntiCheatLoggingManager(AntiCheatConfiguration config, SecurityObfuscator securityObfuscator) {
        this.config = config;
        this.securityObfuscator = securityObfuscator;
        this.loggingExecutor = Executors.newFixedThreadPool(4);
        this.scheduledExecutor = Executors.newScheduledThreadPool(2);
        
        // Initialize logging components
        this.violationLogger = new ViolationLogger(this);
        this.evidenceCollector = new EvidenceCollector(this);
        this.auditTrailManager = new AuditTrailManager(this);
        this.forensicCollector = new ForensicDataCollector(this);
        this.archiveManager = new LogArchiveManager(this);
        
        // Initialize storage
        this.logWriters = new ConcurrentHashMap<>();
        this.pendingLogs = new ConcurrentLinkedQueue<>();
        
        // Configuration
        this.logDirectory = Paths.get("logs", "anticheat");
        this.encryptLogs = true;
        this.compressLogs = true;
        this.maxLogFileSize = 100 * 1024 * 1024; // 100MB
        this.maxLogFiles = 50;
        
        // Initialize logging system
        initializeLoggingSystem();
        startPeriodicTasks();
    }
    
    /**
     * Initialize the logging system
     */
    private void initializeLoggingSystem() {
        try {
            // Create log directory
            Files.createDirectories(logDirectory);
            
            // Initialize log writers for each level
            for (LogLevel level : LogLevel.values()) {
                Path logFile = logDirectory.resolve(level.name().toLowerCase() + ".log");
                LogWriter writer = new LogWriter(logFile, level, this);
                logWriters.put(level, writer);
            }
            
            // Log system initialization
            logSystemEvent(LogLevel.INFO, "AntiCheat logging system initialized", 
                         Map.of("logDirectory", logDirectory.toString(),
                               "encryptLogs", encryptLogs,
                               "compressLogs", compressLogs));
            
        } catch (Exception e) {
            System.err.println("Failed to initialize logging system: " + e.getMessage());
        }
    }
    
    /**
     * Start periodic logging tasks
     */
    private void startPeriodicTasks() {
        // Process pending logs
        scheduledExecutor.scheduleAtFixedRate(this::processPendingLogs, 1, 1, TimeUnit.SECONDS);
        
        // Rotate log files
        scheduledExecutor.scheduleAtFixedRate(this::rotateLogFiles, 1, 1, TimeUnit.HOURS);
        
        // Archive old logs
        scheduledExecutor.scheduleAtFixedRate(archiveManager::archiveOldLogs, 1, 24, TimeUnit.HOURS);
        
        // Generate periodic reports
        scheduledExecutor.scheduleAtFixedRate(this::generatePeriodicReports, 5, 60, TimeUnit.MINUTES);
    }
    
    /**
     * Log a violation with full context and evidence
     */
    public CompletableFuture<Void> logViolationAsync(String playerId, ValidationResult violation, 
                                                   Map<String, Object> context) {
        return CompletableFuture.runAsync(() -> {
            try {
                violationLogger.logViolation(playerId, violation, context);
            } catch (Exception e) {
                logSystemEvent(LogLevel.ERROR, "Failed to log violation", 
                             Map.of("playerId", playerId, "error", e.getMessage()));
            }
        }, loggingExecutor);
    }
    
    /**
     * Log a punishment decision
     */
    public CompletableFuture<Void> logPunishmentAsync(String playerId, PunishmentDecision punishment, 
                                                    EscalationDecision escalation) {
        return CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> context = new HashMap<>();
                context.put("punishmentType", punishment.getPunishmentType().name());
                context.put("reason", punishment.getReason());
                if (escalation != null) {
                    context.put("escalationLevel", escalation.getEscalationLevel().name());
                    context.put("escalationMultiplier", escalation.getPunishmentMultiplier());
                }
                
                logEvent(LogLevel.WARNING, "PUNISHMENT_ISSUED", playerId, 
                        "Punishment issued: " + punishment.getPunishmentType().name(), context);
                
                // Collect evidence for punishment
                evidenceCollector.collectPunishmentEvidence(playerId, punishment, escalation);
                
            } catch (Exception e) {
                logSystemEvent(LogLevel.ERROR, "Failed to log punishment", 
                             Map.of("playerId", playerId, "error", e.getMessage()));
            }
        }, loggingExecutor);
    }
    
    /**
     * Log a security event
     */
    public CompletableFuture<Void> logSecurityEventAsync(SecurityThreatLevel threatLevel, String eventType, 
                                                        String description, Map<String, Object> context) {
        return CompletableFuture.runAsync(() -> {
            try {
                LogLevel logLevel = mapThreatLevelToLogLevel(threatLevel);
                
                Map<String, Object> securityContext = new HashMap<>(context);
                securityContext.put("threatLevel", threatLevel.name());
                securityContext.put("eventType", eventType);
                
                logEvent(logLevel, "SECURITY_EVENT", null, description, securityContext);
                
                // Collect forensic data for high-level threats
                if (threatLevel.ordinal() >= SecurityThreatLevel.HIGH.ordinal()) {
                    forensicCollector.collectSecurityForensics(threatLevel, eventType, description, context);
                }
                
            } catch (Exception e) {
                logSystemEvent(LogLevel.ERROR, "Failed to log security event", 
                             Map.of("eventType", eventType, "error", e.getMessage()));
            }
        }, loggingExecutor);
    }
    
    /**
     * Log a system event
     */
    public void logSystemEvent(LogLevel level, String message, Map<String, Object> context) {
        try {
            logEvent(level, "SYSTEM_EVENT", null, message, context);
        } catch (Exception e) {
            // Fallback to console logging if system logging fails
            System.err.println("LOGGING FAILURE: " + e.getMessage() + " - Original message: " + message);
        }
    }
    
    /**
     * Log a player action for audit trail
     */
    public void logPlayerAction(String playerId, String actionType, String description, 
                              Map<String, Object> context) {
        CompletableFuture.runAsync(() -> {
            try {
                auditTrailManager.logPlayerAction(playerId, actionType, description, context);
            } catch (Exception e) {
                logSystemEvent(LogLevel.ERROR, "Failed to log player action", 
                             Map.of("playerId", playerId, "actionType", actionType, "error", e.getMessage()));
            }
        }, loggingExecutor);
    }
    
    /**
     * Core event logging method
     */
    private void logEvent(LogLevel level, String category, String playerId, String message, 
                         Map<String, Object> context) {
        try {
            LogEntry entry = new LogEntry(
                level,
                category,
                playerId,
                message,
                context,
                System.currentTimeMillis(),
                Thread.currentThread().getName()
            );
            
            // Add to pending logs for processing
            pendingLogs.offer(entry);
            
            // For critical events, process immediately
            if (level == LogLevel.CRITICAL || level == LogLevel.ERROR) {
                processPendingLogs();
            }
            
        } catch (Exception e) {
            // Fallback logging to prevent infinite recursion
            System.err.println("CRITICAL LOGGING FAILURE: " + e.getMessage());
        }
    }
    
    /**
     * Process pending log entries
     */
    private void processPendingLogs() {
        try {
            List<LogEntry> batch = new ArrayList<>();
            
            // Collect batch of pending logs
            LogEntry entry;
            while ((entry = pendingLogs.poll()) != null && batch.size() < 100) {
                batch.add(entry);
            }
            
            if (batch.isEmpty()) return;
            
            // Process batch
            for (LogEntry logEntry : batch) {
                processLogEntry(logEntry);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing pending logs: " + e.getMessage());
        }
    }
    
    /**
     * Process individual log entry
     */
    private void processLogEntry(LogEntry entry) {
        try {
            LogWriter writer = logWriters.get(entry.getLevel());
            if (writer != null) {
                writer.writeLog(entry);
            }
            
            // Also write to audit trail if it's a player-related event
            if (entry.getPlayerId() != null) {
                auditTrailManager.addToAuditTrail(entry);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing log entry: " + e.getMessage());
        }
    }
    
    /**
     * Map security threat level to log level
     */
    private LogLevel mapThreatLevelToLogLevel(SecurityThreatLevel threatLevel) {
        switch (threatLevel) {
            case LOW:
                return LogLevel.INFO;
            case ELEVATED:
                return LogLevel.WARNING;
            case MEDIUM:
                return LogLevel.WARNING;
            case HIGH:
                return LogLevel.ERROR;
            case CRITICAL:
                return LogLevel.CRITICAL;
            default:
                return LogLevel.INFO;
        }
    }
    
    /**
     * Rotate log files when they become too large
     */
    private void rotateLogFiles() {
        try {
            for (LogWriter writer : logWriters.values()) {
                writer.rotateIfNeeded(maxLogFileSize, maxLogFiles);
            }
        } catch (Exception e) {
            System.err.println("Error rotating log files: " + e.getMessage());
        }
    }
    
    /**
     * Generate periodic reports
     */
    private void generatePeriodicReports() {
        try {
            // Generate violation summary report
            ViolationSummaryReport violationReport = violationLogger.generateSummaryReport();
            
            // Generate security summary report
            SecuritySummaryReport securityReport = generateSecuritySummaryReport();
            
            // Generate audit summary report
            AuditSummaryReport auditReport = auditTrailManager.generateSummaryReport();
            
            // Log the reports
            logSystemEvent(LogLevel.INFO, "Periodic reports generated", 
                         Map.of("violationReport", violationReport.toString(),
                               "securityReport", securityReport.toString(),
                               "auditReport", auditReport.toString()));
            
        } catch (Exception e) {
            logSystemEvent(LogLevel.ERROR, "Failed to generate periodic reports", 
                         Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Generate security summary report
     */
    private SecuritySummaryReport generateSecuritySummaryReport() {
        // This would aggregate security events and generate a summary
        return new SecuritySummaryReport(0, 0, 0, System.currentTimeMillis());
    }
    
    /**
     * Search logs by criteria
     */
    public CompletableFuture<List<LogEntry>> searchLogsAsync(LogSearchCriteria criteria) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return searchLogs(criteria);
            } catch (Exception e) {
                logSystemEvent(LogLevel.ERROR, "Failed to search logs", 
                             Map.of("criteria", criteria.toString(), "error", e.getMessage()));
                return new ArrayList<>();
            }
        }, loggingExecutor);
    }
    
    /**
     * Search logs synchronously
     */
    private List<LogEntry> searchLogs(LogSearchCriteria criteria) {
        List<LogEntry> results = new ArrayList<>();
        
        // Search through log files based on criteria
        for (LogWriter writer : logWriters.values()) {
            if (criteria.getLogLevels().isEmpty() || criteria.getLogLevels().contains(writer.getLogLevel())) {
                results.addAll(writer.searchLogs(criteria));
            }
        }
        
        // Sort results by timestamp
        results.sort(Comparator.comparing(LogEntry::getTimestamp));
        
        // Apply limit if specified
        if (criteria.getLimit() > 0 && results.size() > criteria.getLimit()) {
            results = results.subList(0, criteria.getLimit());
        }
        
        return results;
    }
    
    /**
     * Export logs for external analysis
     */
    public CompletableFuture<Path> exportLogsAsync(LogExportRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return exportLogs(request);
            } catch (Exception e) {
                logSystemEvent(LogLevel.ERROR, "Failed to export logs", 
                             Map.of("request", request.toString(), "error", e.getMessage()));
                return null;
            }
        }, loggingExecutor);
    }
    
    /**
     * Export logs to file
     */
    private Path exportLogs(LogExportRequest request) throws IOException {
        Path exportFile = logDirectory.resolve("exports").resolve(
            "export_" + System.currentTimeMillis() + ".json");
        
        Files.createDirectories(exportFile.getParent());
        
        List<LogEntry> logs = searchLogs(request.getSearchCriteria());
        
        // Export to JSON format
        try (FileWriter writer = new FileWriter(exportFile.toFile())) {
            writer.write("[\n");
            for (int i = 0; i < logs.size(); i++) {
                if (i > 0) writer.write(",\n");
                writer.write(logs.get(i).toJson());
            }
            writer.write("\n]");
        }
        
        // Compress if requested
        if (request.isCompress()) {
            Path compressedFile = Paths.get(exportFile.toString() + ".gz");
            try (FileInputStream fis = new FileInputStream(exportFile.toFile());
                 FileOutputStream fos = new FileOutputStream(compressedFile.toFile());
                 GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
                
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    gzos.write(buffer, 0, len);
                }
            }
            
            Files.delete(exportFile);
            exportFile = compressedFile;
        }
        
        logSystemEvent(LogLevel.INFO, "Logs exported", 
                     Map.of("exportFile", exportFile.toString(), "logCount", logs.size()));
        
        return exportFile;
    }
    
    /**
     * Get logging statistics
     */
    public LoggingStatistics getLoggingStatistics() {
        Map<LogLevel, Long> logCounts = new HashMap<>();
        long totalLogSize = 0;
        
        for (Map.Entry<LogLevel, LogWriter> entry : logWriters.entrySet()) {
            LogWriter writer = entry.getValue();
            logCounts.put(entry.getKey(), writer.getLogCount());
            totalLogSize += writer.getLogFileSize();
        }
        
        return new LoggingStatistics(
            logCounts,
            totalLogSize,
            pendingLogs.size(),
            violationLogger.getViolationCount(),
            auditTrailManager.getAuditEntryCount(),
            evidenceCollector.getEvidenceCount()
        );
    }
    
    /**
     * Shutdown logging manager
     */
    public void shutdown() {
        try {
            // Process remaining pending logs
            processPendingLogs();
            
            // Close all log writers
            for (LogWriter writer : logWriters.values()) {
                writer.close();
            }
            
            // Shutdown executors
            loggingExecutor.shutdown();
            scheduledExecutor.shutdown();
            
            if (!loggingExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                loggingExecutor.shutdownNow();
            }
            
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
        } catch (Exception e) {
            System.err.println("Error shutting down logging manager: " + e.getMessage());
        }
    }
    
    // Getters for components
    public ViolationLogger getViolationLogger() { return violationLogger; }
    public EvidenceCollector getEvidenceCollector() { return evidenceCollector; }
    public AuditTrailManager getAuditTrailManager() { return auditTrailManager; }
    public ForensicDataCollector getForensicCollector() { return forensicCollector; }
    public LogArchiveManager getArchiveManager() { return archiveManager; }
    public SecurityObfuscator getSecurityObfuscator() { return securityObfuscator; }
    public Path getLogDirectory() { return logDirectory; }
    public boolean isEncryptLogs() { return encryptLogs; }
    public boolean isCompressLogs() { return compressLogs; }
}