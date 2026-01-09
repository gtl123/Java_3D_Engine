package engine.logging;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Log appender that writes messages to files with automatic rotation.
 * Supports configurable file size limits and rotation policies.
 */
public class FileAppender implements LogAppender {
    
    private static final long DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int DEFAULT_MAX_FILES = 5;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    private final String baseFileName;
    private final long maxFileSize;
    private final int maxFiles;
    private final Path logDirectory;
    
    private PrintWriter currentWriter;
    private long currentFileSize;
    private volatile boolean closed = false;
    
    public FileAppender(String fileName) {
        this(fileName, DEFAULT_MAX_FILE_SIZE, DEFAULT_MAX_FILES);
    }
    
    public FileAppender(String fileName, long maxFileSize, int maxFiles) {
        this.baseFileName = fileName;
        this.maxFileSize = maxFileSize;
        this.maxFiles = maxFiles;
        this.logDirectory = Paths.get("logs");
        
        try {
            Files.createDirectories(logDirectory);
            initializeWriter();
        } catch (IOException e) {
            System.err.println("Failed to initialize FileAppender: " + e.getMessage());
        }
    }
    
    private void initializeWriter() throws IOException {
        if (currentWriter != null) {
            currentWriter.close();
        }
        
        String timestamp = LocalDateTime.now().format(DATE_FORMAT);
        String fileName = baseFileName + "_" + timestamp + ".log";
        Path filePath = logDirectory.resolve(fileName);
        
        currentWriter = new PrintWriter(new BufferedWriter(new FileWriter(filePath.toFile(), true)));
        currentFileSize = Files.exists(filePath) ? Files.size(filePath) : 0;
    }
    
    @Override
    public synchronized void append(String message) {
        if (closed || currentWriter == null) {
            return;
        }
        
        try {
            // Check if rotation is needed
            if (currentFileSize + message.length() > maxFileSize) {
                rotateFile();
            }
            
            currentWriter.println(message);
            currentFileSize += message.length() + System.lineSeparator().length();
            
        } catch (IOException e) {
            System.err.println("FileAppender error: " + e.getMessage());
        }
    }
    
    private void rotateFile() throws IOException {
        if (currentWriter != null) {
            currentWriter.close();
        }
        
        // Clean up old files if we exceed the limit
        cleanupOldFiles();
        
        // Create new file
        initializeWriter();
    }
    
    private void cleanupOldFiles() {
        try {
            Files.list(logDirectory)
                .filter(path -> path.getFileName().toString().startsWith(baseFileName))
                .filter(path -> path.getFileName().toString().endsWith(".log"))
                .sorted((p1, p2) -> {
                    try {
                        return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .skip(maxFiles - 1) // Keep maxFiles - 1 (since we're creating a new one)
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        System.err.println("Failed to delete old log file: " + path);
                    }
                });
        } catch (IOException e) {
            System.err.println("Failed to cleanup old log files: " + e.getMessage());
        }
    }
    
    @Override
    public void flush() {
        if (currentWriter != null && !closed) {
            currentWriter.flush();
        }
    }
    
    @Override
    public void close() {
        closed = true;
        if (currentWriter != null) {
            currentWriter.close();
            currentWriter = null;
        }
    }
    
    @Override
    public boolean isAvailable() {
        return !closed && currentWriter != null;
    }
}