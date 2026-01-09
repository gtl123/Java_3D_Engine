package fps.anticheat.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPOutputStream;

/**
 * High-performance log writer with buffering, rotation, and compression.
 */
public class LogWriter {
    private final String logDirectory;
    private final String logFilePrefix;
    private final long maxFileSize;
    private final int maxFiles;
    private final boolean compressionEnabled;
    private final boolean asyncWriting;
    
    private final ExecutorService writerExecutor;
    private final BlockingQueue<LogWriteTask> writeQueue;
    private final Map<String, FileWriter> activeWriters;
    private final AtomicLong currentFileSize;
    private final AtomicLong fileCounter;
    
    private volatile boolean shutdown = false;
    private String currentLogFile;
    
    public LogWriter(String logDirectory, String logFilePrefix) {
        this.logDirectory = logDirectory;
        this.logFilePrefix = logFilePrefix;
        this.maxFileSize = 100 * 1024 * 1024; // 100MB
        this.maxFiles = 10;
        this.compressionEnabled = true;
        this.asyncWriting = true;
        
        this.writerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LogWriter-Thread");
            t.setDaemon(true);
            return t;
        });
        this.writeQueue = new LinkedBlockingQueue<>(10000);
        this.activeWriters = new ConcurrentHashMap<>();
        this.currentFileSize = new AtomicLong(0);
        this.fileCounter = new AtomicLong(0);
        
        // Ensure log directory exists
        createLogDirectory();
        
        // Initialize current log file
        initializeCurrentLogFile();
        
        // Start async writer if enabled
        if (asyncWriting) {
            startAsyncWriter();
        }
    }
    
    /**
     * Write a log entry
     */
    public CompletableFuture<Boolean> writeLog(LogEntry logEntry) {
        if (shutdown) {
            return CompletableFuture.completedFuture(false);
        }
        
        if (asyncWriting) {
            return writeLogAsync(logEntry);
        } else {
            return writeLogSync(logEntry);
        }
    }
    
    /**
     * Write multiple log entries in batch
     */
    public CompletableFuture<Integer> writeBatch(List<LogEntry> logEntries) {
        if (shutdown || logEntries.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            int written = 0;
            for (LogEntry entry : logEntries) {
                try {
                    if (writeLogSync(entry).get()) {
                        written++;
                    }
                } catch (Exception e) {
                    // Log write failed, continue with next entry
                }
            }
            return written;
        });
    }
    
    /**
     * Force rotation of current log file
     */
    public CompletableFuture<String> rotateLogFile() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String oldFile = currentLogFile;
                
                // Close current writer
                closeCurrentWriter();
                
                // Compress old file if enabled
                if (compressionEnabled && oldFile != null) {
                    compressLogFile(oldFile);
                }
                
                // Create new log file
                initializeCurrentLogFile();
                
                // Clean up old files
                cleanupOldFiles();
                
                return currentLogFile;
                
            } catch (Exception e) {
                return null;
            }
        });
    }
    
    /**
     * Flush all pending writes
     */
    public CompletableFuture<Void> flush() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Flush write queue
                if (asyncWriting) {
                    // Add flush marker to queue
                    CountDownLatch flushLatch = new CountDownLatch(1);
                    writeQueue.offer(new FlushTask(flushLatch));
                    flushLatch.await(30, TimeUnit.SECONDS);
                }
                
                // Flush all active writers
                for (FileWriter writer : activeWriters.values()) {
                    writer.flush();
                }
                
            } catch (Exception e) {
                // Flush failed
            }
        });
    }
    
    /**
     * Get log writer statistics
     */
    public LogWriterStats getStatistics() {
        return new LogWriterStats(
                currentLogFile,
                currentFileSize.get(),
                maxFileSize,
                writeQueue.size(),
                activeWriters.size(),
                fileCounter.get()
        );
    }
    
    /**
     * Shutdown the log writer
     */
    public void shutdown() {
        shutdown = true;
        
        // Stop accepting new writes
        writeQueue.offer(new ShutdownTask());
        
        // Shutdown executor
        writerExecutor.shutdown();
        try {
            if (!writerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                writerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writerExecutor.shutdownNow();
        }
        
        // Close all writers
        closeAllWriters();
    }
    
    private CompletableFuture<Boolean> writeLogAsync(LogEntry logEntry) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        LogWriteTask task = new LogWriteTask(logEntry, future);
        
        if (!writeQueue.offer(task)) {
            // Queue is full, write synchronously as fallback
            return writeLogSync(logEntry);
        }
        
        return future;
    }
    
    private CompletableFuture<Boolean> writeLogSync(LogEntry logEntry) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if rotation is needed
                if (needsRotation()) {
                    rotateLogFile().get();
                }
                
                // Get or create writer for current file
                FileWriter writer = getOrCreateWriter(currentLogFile);
                
                // Format and write log entry
                String formattedLog = formatLogEntry(logEntry);
                writer.write(formattedLog);
                writer.write(System.lineSeparator());
                
                // Update file size
                currentFileSize.addAndGet(formattedLog.length() + System.lineSeparator().length());
                
                return true;
                
            } catch (Exception e) {
                return false;
            }
        });
    }
    
    private void startAsyncWriter() {
        writerExecutor.submit(() -> {
            while (!shutdown) {
                try {
                    LogWriteTask task = writeQueue.poll(1, TimeUnit.SECONDS);
                    if (task != null) {
                        task.execute();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Log write error
                }
            }
            
            // Process remaining tasks
            List<LogWriteTask> remainingTasks = new ArrayList<>();
            writeQueue.drainTo(remainingTasks);
            
            for (LogWriteTask task : remainingTasks) {
                try {
                    task.execute();
                } catch (Exception e) {
                    // Ignore errors during shutdown
                }
            }
        });
    }
    
    private void createLogDirectory() {
        try {
            Path logPath = Paths.get(logDirectory);
            if (!Files.exists(logPath)) {
                Files.createDirectories(logPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create log directory: " + logDirectory, e);
        }
    }
    
    private void initializeCurrentLogFile() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        long counter = fileCounter.incrementAndGet();
        
        currentLogFile = String.format("%s/%s_%s_%03d.log", 
                logDirectory, logFilePrefix, timestamp, counter);
        
        currentFileSize.set(0);
    }
    
    private boolean needsRotation() {
        return currentFileSize.get() >= maxFileSize;
    }
    
    private FileWriter getOrCreateWriter(String fileName) throws IOException {
        return activeWriters.computeIfAbsent(fileName, key -> {
            try {
                return new FileWriter(key, true); // Append mode
            } catch (IOException e) {
                throw new RuntimeException("Failed to create file writer for: " + key, e);
            }
        });
    }
    
    private void closeCurrentWriter() {
        if (currentLogFile != null) {
            FileWriter writer = activeWriters.remove(currentLogFile);
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    // Ignore close errors
                }
            }
        }
    }
    
    private void closeAllWriters() {
        for (FileWriter writer : activeWriters.values()) {
            try {
                writer.close();
            } catch (IOException e) {
                // Ignore close errors
            }
        }
        activeWriters.clear();
    }
    
    private void compressLogFile(String fileName) {
        try {
            String compressedFileName = fileName + ".gz";
            
            try (FileInputStream fis = new FileInputStream(fileName);
                 FileOutputStream fos = new FileOutputStream(compressedFileName);
                 GZIPOutputStream gzipOut = new GZIPOutputStream(fos)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    gzipOut.write(buffer, 0, bytesRead);
                }
            }
            
            // Delete original file after compression
            Files.deleteIfExists(Paths.get(fileName));
            
        } catch (IOException e) {
            // Compression failed, keep original file
        }
    }
    
    private void cleanupOldFiles() {
        try {
            Path logDir = Paths.get(logDirectory);
            
            // Get all log files (including compressed)
            List<Path> logFiles = Files.list(logDir)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return fileName.startsWith(logFilePrefix) && 
                               (fileName.endsWith(".log") || fileName.endsWith(".log.gz"));
                    })
                    .sorted((p1, p2) -> {
                        try {
                            return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .toList();
            
            // Delete files beyond maxFiles limit
            if (logFiles.size() > maxFiles) {
                for (int i = maxFiles; i < logFiles.size(); i++) {
                    try {
                        Files.deleteIfExists(logFiles.get(i));
                    } catch (IOException e) {
                        // Ignore deletion errors
                    }
                }
            }
            
        } catch (IOException e) {
            // Cleanup failed
        }
    }
    
    private String formatLogEntry(LogEntry logEntry) {
        // Format: [TIMESTAMP] [LEVEL] [CATEGORY] PLAYER_ID SESSION_ID MESSAGE
        return String.format("[%s] [%s] [%s] %s %s %s",
                logEntry.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                logEntry.getLevel(),
                logEntry.getCategory(),
                logEntry.getPlayerId() != null ? logEntry.getPlayerId() : "SYSTEM",
                logEntry.getSessionId() != null ? logEntry.getSessionId() : "NO_SESSION",
                logEntry.getMessage()
        );
    }
    
    // Task classes for async writing
    private abstract class WriteTask {
        public abstract void execute();
    }
    
    private class LogWriteTask extends WriteTask {
        private final LogEntry logEntry;
        private final CompletableFuture<Boolean> future;
        
        public LogWriteTask(LogEntry logEntry, CompletableFuture<Boolean> future) {
            this.logEntry = logEntry;
            this.future = future;
        }
        
        @Override
        public void execute() {
            try {
                boolean success = writeLogSync(logEntry).get();
                future.complete(success);
            } catch (Exception e) {
                future.complete(false);
            }
        }
    }
    
    private class FlushTask extends WriteTask {
        private final CountDownLatch latch;
        
        public FlushTask(CountDownLatch latch) {
            this.latch = latch;
        }
        
        @Override
        public void execute() {
            try {
                // Flush all active writers
                for (FileWriter writer : activeWriters.values()) {
                    writer.flush();
                }
            } catch (Exception e) {
                // Ignore flush errors
            } finally {
                latch.countDown();
            }
        }
    }
    
    private class ShutdownTask extends WriteTask {
        @Override
        public void execute() {
            // Marker task to indicate shutdown
        }
    }
    
    /**
     * Log writer statistics
     */
    public static class LogWriterStats {
        private final String currentFile;
        private final long currentFileSize;
        private final long maxFileSize;
        private final int queueSize;
        private final int activeWriters;
        private final long totalFiles;
        
        public LogWriterStats(String currentFile, long currentFileSize, long maxFileSize,
                             int queueSize, int activeWriters, long totalFiles) {
            this.currentFile = currentFile;
            this.currentFileSize = currentFileSize;
            this.maxFileSize = maxFileSize;
            this.queueSize = queueSize;
            this.activeWriters = activeWriters;
            this.totalFiles = totalFiles;
        }
        
        // Getters
        public String getCurrentFile() { return currentFile; }
        public long getCurrentFileSize() { return currentFileSize; }
        public long getMaxFileSize() { return maxFileSize; }
        public int getQueueSize() { return queueSize; }
        public int getActiveWriters() { return activeWriters; }
        public long getTotalFiles() { return totalFiles; }
        
        public double getFileSizePercentage() {
            return maxFileSize > 0 ? (double) currentFileSize / maxFileSize * 100.0 : 0.0;
        }
    }
}