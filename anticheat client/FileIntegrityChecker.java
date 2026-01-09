package fps.anticheat.client;

import engine.logging.LogManager;
import fps.anticheat.*;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

/**
 * Monitors file system integrity to detect unauthorized modifications,
 * injected DLLs, and other file-based attacks.
 */
public class FileIntegrityChecker {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Configuration
    private AntiCheatConfiguration config;
    
    // File monitoring state
    private final Map<String, FileInfo> monitoredFiles = new ConcurrentHashMap<>();
    private final Map<String, String> fileHashes = new ConcurrentHashMap<>();
    private final Set<String> criticalFiles = new HashSet<>();
    private final Set<String> suspiciousFiles = new HashSet<>();
    private final List<FileViolation> detectedViolations = new ArrayList<>();
    
    // File system monitoring
    private WatchService watchService;
    private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();
    private Thread fileWatcherThread;
    private boolean watchingActive = false;
    
    // Check intervals and timing
    private long lastIntegrityCheck = 0;
    private int integrityCheckInterval = 30000; // 30 seconds
    private long lastFileSystemScan = 0;
    private int fileSystemScanInterval = 300000; // 5 minutes
    
    // Detection statistics
    private long totalChecks = 0;
    private long totalViolations = 0;
    private long totalFilesMonitored = 0;
    
    // System state
    private boolean initialized = false;
    
    public FileIntegrityChecker() {
        logManager.debug("FileIntegrityChecker", "File integrity checker created");
    }
    
    /**
     * Initialize the file integrity checker
     */
    public void initialize() throws Exception {
        logManager.info("FileIntegrityChecker", "Initializing file integrity checker");
        
        try {
            // Initialize watch service
            watchService = FileSystems.getDefault().newWatchService();
            
            // Initialize critical files list
            initializeCriticalFiles();
            
            // Perform initial file scan
            performInitialFileScan();
            
            // Start file system watcher
            startFileSystemWatcher();
            
            initialized = true;
            
            logManager.info("FileIntegrityChecker", "File integrity checker initialization complete");
            
        } catch (Exception e) {
            logManager.error("FileIntegrityChecker", "Failed to initialize file integrity checker", e);
            throw e;
        }
    }
    
    /**
     * Set configuration
     */
    public void setConfiguration(AntiCheatConfiguration config) {
        this.config = config;
        
        if (config != null) {
            // Update check intervals based on configuration
            // Configuration could include file monitoring settings
        }
    }
    
    /**
     * Perform file integrity check
     */
    public FileIntegrityResult checkIntegrity() {
        if (!initialized) {
            return FileIntegrityResult.error("File integrity checker not initialized");
        }
        
        long startTime = System.nanoTime();
        totalChecks++;
        
        try {
            FileIntegrityResult result = new FileIntegrityResult();
            
            // Check monitored files for modifications
            List<FileViolation> modificationViolations = checkFileModifications();
            result.addViolations(modificationViolations);
            
            // Check for suspicious new files
            List<FileViolation> newFileViolations = checkSuspiciousNewFiles();
            result.addViolations(newFileViolations);
            
            // Check for missing critical files
            List<FileViolation> missingFileViolations = checkMissingCriticalFiles();
            result.addViolations(missingFileViolations);
            
            // Check for unauthorized DLL injections
            List<FileViolation> dllViolations = checkUnauthorizedDLLs();
            result.addViolations(dllViolations);
            
            // Check file permissions and attributes
            List<FileViolation> permissionViolations = checkFilePermissions();
            result.addViolations(permissionViolations);
            
            // Update statistics
            totalViolations += result.getViolations().size();
            result.setFilesChecked(monitoredFiles.size());
            
            // Calculate check time
            long checkTime = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds
            result.setCheckTime(checkTime);
            
            lastIntegrityCheck = System.currentTimeMillis();
            
            return result;
            
        } catch (Exception e) {
            logManager.error("FileIntegrityChecker", "Error performing file integrity check", e);
            return FileIntegrityResult.error("File integrity check error: " + e.getMessage());
        }
    }
    
    /**
     * Initialize critical files list
     */
    private void initializeCriticalFiles() {
        // Game executable files
        criticalFiles.add("game.exe");
        criticalFiles.add("engine.dll");
        criticalFiles.add("physics.dll");
        criticalFiles.add("renderer.dll");
        criticalFiles.add("audio.dll");
        criticalFiles.add("network.dll");
        
        // Anti-cheat files
        criticalFiles.add("anticheat.dll");
        criticalFiles.add("security.dll");
        criticalFiles.add("integrity.dll");
        
        // System libraries
        criticalFiles.add("kernel32.dll");
        criticalFiles.add("user32.dll");
        criticalFiles.add("ntdll.dll");
        criticalFiles.add("advapi32.dll");
        
        // Configuration files
        criticalFiles.add("config.ini");
        criticalFiles.add("settings.cfg");
        criticalFiles.add("anticheat.cfg");
        
        logManager.info("FileIntegrityChecker", "Initialized critical files list",
                       "criticalFiles", criticalFiles.size());
    }
    
    /**
     * Perform initial file system scan
     */
    private void performInitialFileScan() throws IOException {
        // Scan current directory and subdirectories
        Path currentDir = Paths.get(".");
        scanDirectory(currentDir);
        
        // Scan system directories (if accessible)
        try {
            Path systemDir = Paths.get(System.getProperty("java.home"));
            scanDirectory(systemDir);
        } catch (Exception e) {
            logManager.debug("FileIntegrityChecker", "Could not scan system directory", e);
        }
        
        logManager.info("FileIntegrityChecker", "Initial file scan complete",
                       "monitoredFiles", monitoredFiles.size());
    }
    
    /**
     * Scan directory for files to monitor
     */
    private void scanDirectory(Path directory) throws IOException {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return;
        }
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    addFileToMonitoring(entry);
                } else if (Files.isDirectory(entry) && shouldScanSubdirectory(entry)) {
                    // Recursively scan subdirectories (with depth limit)
                    scanDirectory(entry);
                }
            }
        }
        
        // Add directory to watch service
        addDirectoryToWatcher(directory);
    }
    
    /**
     * Check if subdirectory should be scanned
     */
    private boolean shouldScanSubdirectory(Path directory) {
        String dirName = directory.getFileName().toString().toLowerCase();
        
        // Skip system and temporary directories
        if (dirName.equals("temp") || dirName.equals("tmp") || 
            dirName.equals(".git") || dirName.equals("node_modules") ||
            dirName.startsWith(".")) {
            return false;
        }
        
        // Limit recursion depth
        try {
            Path currentDir = Paths.get(".").toAbsolutePath().normalize();
            Path targetDir = directory.toAbsolutePath().normalize();
            
            if (!targetDir.startsWith(currentDir)) {
                return false; // Outside current directory tree
            }
            
            int depth = targetDir.getNameCount() - currentDir.getNameCount();
            return depth < 5; // Limit to 5 levels deep
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Add file to monitoring
     */
    private void addFileToMonitoring(Path filePath) {
        try {
            String fileName = filePath.getFileName().toString();
            String absolutePath = filePath.toAbsolutePath().toString();
            
            // Create file info
            FileInfo fileInfo = new FileInfo(fileName, absolutePath, Files.size(filePath), 
                                           Files.getLastModifiedTime(filePath).toMillis());
            
            // Check if file is critical
            if (isCriticalFile(fileName)) {
                fileInfo.setCritical(true);
            }
            
            // Check if file is suspicious
            if (isSuspiciousFile(fileName, filePath)) {
                fileInfo.setSuspicious(true);
                suspiciousFiles.add(absolutePath);
            }
            
            // Calculate file hash
            String hash = calculateFileHash(filePath);
            if (hash != null) {
                fileInfo.setOriginalHash(hash);
                fileHashes.put(absolutePath, hash);
            }
            
            monitoredFiles.put(absolutePath, fileInfo);
            totalFilesMonitored++;
            
        } catch (Exception e) {
            logManager.debug("FileIntegrityChecker", "Error adding file to monitoring", e,
                           "file", filePath.toString());
        }
    }
    
    /**
     * Check if file is critical
     */
    private boolean isCriticalFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        
        // Check exact matches
        if (criticalFiles.contains(lowerName)) {
            return true;
        }
        
        // Check patterns
        if (lowerName.endsWith(".exe") || lowerName.endsWith(".dll") ||
            lowerName.contains("anticheat") || lowerName.contains("security") ||
            lowerName.contains("config") || lowerName.contains("setting")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if file is suspicious
     */
    private boolean isSuspiciousFile(String fileName, Path filePath) {
        String lowerName = fileName.toLowerCase();
        
        // Check for suspicious file names
        String[] suspiciousKeywords = {
            "cheat", "hack", "bot", "trainer", "inject", "hook", "patch",
            "crack", "keygen", "loader", "bypass"
        };
        
        for (String keyword : suspiciousKeywords) {
            if (lowerName.contains(keyword)) {
                return true;
            }
        }
        
        // Check for suspicious file extensions
        if (lowerName.endsWith(".tmp") || lowerName.endsWith(".temp") ||
            lowerName.endsWith(".bak") || lowerName.endsWith(".old")) {
            return true;
        }
        
        // Check for files in suspicious locations
        try {
            String parentDir = filePath.getParent().getFileName().toString().toLowerCase();
            if (parentDir.contains("temp") || parentDir.contains("tmp") ||
                parentDir.contains("download") || parentDir.contains("cache")) {
                return true;
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return false;
    }
    
    /**
     * Calculate file hash
     */
    private String calculateFileHash(Path filePath) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            
            try (InputStream is = Files.newInputStream(filePath);
                 DigestInputStream dis = new DigestInputStream(is, md)) {
                
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // Reading file to calculate hash
                }
            }
            
            byte[] hash = md.digest();
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (Exception e) {
            logManager.debug("FileIntegrityChecker", "Error calculating file hash", e,
                           "file", filePath.toString());
            return null;
        }
    }
    
    /**
     * Check monitored files for modifications
     */
    private List<FileViolation> checkFileModifications() {
        List<FileViolation> violations = new ArrayList<>();
        
        for (Map.Entry<String, FileInfo> entry : monitoredFiles.entrySet()) {
            String filePath = entry.getKey();
            FileInfo fileInfo = entry.getValue();
            
            try {
                Path path = Paths.get(filePath);
                
                if (!Files.exists(path)) {
                    // File was deleted
                    if (fileInfo.isCritical()) {
                        violations.add(new FileViolation(
                            ViolationType.CLIENT_MODIFICATION,
                            "Critical file deleted: " + fileInfo.getName(),
                            filePath,
                            1.0f,
                            "file_deletion_check"
                        ));
                    }
                    continue;
                }
                
                // Check file size
                long currentSize = Files.size(path);
                if (currentSize != fileInfo.getSize()) {
                    violations.add(new FileViolation(
                        fileInfo.isCritical() ? ViolationType.CLIENT_MODIFICATION : ViolationType.SUSPICIOUS_PATTERNS,
                        "File size changed: " + fileInfo.getName() + " (was " + fileInfo.getSize() + ", now " + currentSize + ")",
                        filePath,
                        fileInfo.isCritical() ? 0.9f : 0.6f,
                        "file_size_check"
                    ));
                }
                
                // Check modification time
                long currentModTime = Files.getLastModifiedTime(path).toMillis();
                if (currentModTime != fileInfo.getLastModified()) {
                    // Calculate new hash to confirm modification
                    String currentHash = calculateFileHash(path);
                    String originalHash = fileInfo.getOriginalHash();
                    
                    if (currentHash != null && originalHash != null && !currentHash.equals(originalHash)) {
                        violations.add(new FileViolation(
                            fileInfo.isCritical() ? ViolationType.CLIENT_MODIFICATION : ViolationType.SUSPICIOUS_PATTERNS,
                            "File content modified: " + fileInfo.getName(),
                            filePath,
                            fileInfo.isCritical() ? 0.9f : 0.7f,
                            "file_hash_check"
                        ));
                        
                        // Update stored hash
                        fileInfo.setCurrentHash(currentHash);
                        fileHashes.put(filePath, currentHash);
                    }
                    
                    // Update modification time
                    fileInfo.setLastModified(currentModTime);
                }
                
            } catch (Exception e) {
                logManager.debug("FileIntegrityChecker", "Error checking file modification", e,
                               "file", filePath);
            }
        }
        
        return violations;
    }
    
    /**
     * Check for suspicious new files
     */
    private List<FileViolation> checkSuspiciousNewFiles() {
        List<FileViolation> violations = new ArrayList<>();
        
        // This would be implemented with file system watcher events
        // For now, we'll check for new files in monitored directories
        
        return violations;
    }
    
    /**
     * Check for missing critical files
     */
    private List<FileViolation> checkMissingCriticalFiles() {
        List<FileViolation> violations = new ArrayList<>();
        
        for (String criticalFile : criticalFiles) {
            boolean found = false;
            
            for (FileInfo fileInfo : monitoredFiles.values()) {
                if (fileInfo.getName().toLowerCase().equals(criticalFile)) {
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                violations.add(new FileViolation(
                    ViolationType.CLIENT_MODIFICATION,
                    "Critical file missing: " + criticalFile,
                    criticalFile,
                    0.8f,
                    "critical_file_check"
                ));
            }
        }
        
        return violations;
    }
    
    /**
     * Check for unauthorized DLL injections
     */
    private List<FileViolation> checkUnauthorizedDLLs() {
        List<FileViolation> violations = new ArrayList<>();
        
        // Check for new DLL files that weren't present during initialization
        for (Map.Entry<String, FileInfo> entry : monitoredFiles.entrySet()) {
            FileInfo fileInfo = entry.getValue();
            
            if (fileInfo.getName().toLowerCase().endsWith(".dll") && 
                fileInfo.getCreationTime() > System.currentTimeMillis() - integrityCheckInterval) {
                
                // New DLL file detected
                violations.add(new FileViolation(
                    ViolationType.PROCESS_INJECTION,
                    "New DLL file detected: " + fileInfo.getName(),
                    entry.getKey(),
                    0.8f,
                    "dll_injection_check"
                ));
            }
        }
        
        return violations;
    }
    
    /**
     * Check file permissions and attributes
     */
    private List<FileViolation> checkFilePermissions() {
        List<FileViolation> violations = new ArrayList<>();
        
        for (Map.Entry<String, FileInfo> entry : monitoredFiles.entrySet()) {
            String filePath = entry.getKey();
            FileInfo fileInfo = entry.getValue();
            
            try {
                Path path = Paths.get(filePath);
                
                if (!Files.exists(path)) {
                    continue;
                }
                
                // Check if critical files have been made writable
                if (fileInfo.isCritical() && Files.isWritable(path)) {
                    violations.add(new FileViolation(
                        ViolationType.CLIENT_MODIFICATION,
                        "Critical file is writable: " + fileInfo.getName(),
                        filePath,
                        0.7f,
                        "file_permission_check"
                    ));
                }
                
            } catch (Exception e) {
                logManager.debug("FileIntegrityChecker", "Error checking file permissions", e,
                               "file", filePath);
            }
        }
        
        return violations;
    }
    
    /**
     * Add directory to file system watcher
     */
    private void addDirectoryToWatcher(Path directory) {
        try {
            WatchKey key = directory.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
            
            watchKeys.put(key, directory);
            
        } catch (Exception e) {
            logManager.debug("FileIntegrityChecker", "Error adding directory to watcher", e,
                           "directory", directory.toString());
        }
    }
    
    /**
     * Start file system watcher thread
     */
    private void startFileSystemWatcher() {
        watchingActive = true;
        
        fileWatcherThread = new Thread(() -> {
            while (watchingActive) {
                try {
                    WatchKey key = watchService.take();
                    Path directory = watchKeys.get(key);
                    
                    if (directory != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            processWatchEvent(directory, event);
                        }
                    }
                    
                    key.reset();
                    
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    logManager.error("FileIntegrityChecker", "Error in file watcher thread", e);
                }
            }
        });
        
        fileWatcherThread.setDaemon(true);
        fileWatcherThread.setName("FileIntegrityWatcher");
        fileWatcherThread.start();
        
        logManager.info("FileIntegrityChecker", "File system watcher started");
    }
    
    /**
     * Process file system watch event
     */
    private void processWatchEvent(Path directory, WatchEvent<?> event) {
        try {
            Path fileName = (Path) event.context();
            Path fullPath = directory.resolve(fileName);
            
            WatchEvent.Kind<?> kind = event.kind();
            
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                // New file created
                if (Files.isRegularFile(fullPath)) {
                    addFileToMonitoring(fullPath);
                    
                    // Check if it's suspicious
                    if (isSuspiciousFile(fileName.toString(), fullPath)) {
                        logManager.warn("FileIntegrityChecker", "Suspicious file created",
                                       "file", fullPath.toString());
                    }
                }
            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                // File deleted
                String absolutePath = fullPath.toAbsolutePath().toString();
                FileInfo fileInfo = monitoredFiles.remove(absolutePath);
                
                if (fileInfo != null && fileInfo.isCritical()) {
                    logManager.warn("FileIntegrityChecker", "Critical file deleted",
                                   "file", fullPath.toString());
                }
            } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                // File modified
                String absolutePath = fullPath.toAbsolutePath().toString();
                FileInfo fileInfo = monitoredFiles.get(absolutePath);
                
                if (fileInfo != null && fileInfo.isCritical()) {
                    logManager.warn("FileIntegrityChecker", "Critical file modified",
                                   "file", fullPath.toString());
                }
            }
            
        } catch (Exception e) {
            logManager.debug("FileIntegrityChecker", "Error processing watch event", e);
        }
    }
    
    /**
     * Get file integrity statistics
     */
    public FileIntegrityStatistics getStatistics() {
        FileIntegrityStatistics stats = new FileIntegrityStatistics();
        stats.totalChecks = totalChecks;
        stats.totalViolations = totalViolations;
        stats.totalFilesMonitored = totalFilesMonitored;
        stats.currentMonitoredFiles = monitoredFiles.size();
        stats.criticalFiles = (int) monitoredFiles.values().stream().filter(FileInfo::isCritical).count();
        stats.suspiciousFiles = suspiciousFiles.size();
        stats.lastIntegrityCheck = lastIntegrityCheck;
        stats.watchingActive = watchingActive;
        
        return stats;
    }
    
    /**
     * Update file integrity checker
     */
    public void update(float deltaTime) {
        if (!initialized) {
            return;
        }
        
        // Perform periodic checks if needed
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastIntegrityCheck > integrityCheckInterval) {
            // Background check would be handled by ClientSideMonitor
        }
        
        if (currentTime - lastFileSystemScan > fileSystemScanInterval) {
            // Periodic file system scan would be handled by ClientSideMonitor
        }
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        watchingActive = false;
        
        if (fileWatcherThread != null) {
            fileWatcherThread.interrupt();
        }
        
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            logManager.error("FileIntegrityChecker", "Error closing watch service", e);
        }
        
        monitoredFiles.clear();
        fileHashes.clear();
        suspiciousFiles.clear();
        detectedViolations.clear();
        watchKeys.clear();
        initialized = false;
        
        logManager.debug("FileIntegrityChecker", "File integrity checker cleaned up");
    }
    
    // Getters and setters
    public Map<String, FileInfo> getMonitoredFiles() { return new ConcurrentHashMap<>(monitoredFiles); }
    public Set<String> getCriticalFiles() { return new HashSet<>(criticalFiles); }
    public Set<String> getSuspiciousFiles() { return new HashSet<>(suspiciousFiles); }
    public int getIntegrityCheckInterval() { return integrityCheckInterval; }
    public void setIntegrityCheckInterval(int integrityCheckInterval) { this.integrityCheckInterval = integrityCheckInterval; }
    public int getFileSystemScanInterval() { return fileSystemScanInterval; }
    public void setFileSystemScanInterval(int fileSystemScanInterval) { this.fileSystemScanInterval = fileSystemScanInterval; }
    public boolean isInitialized() { return initialized; }
    public boolean isWatchingActive() { return watchingActive; }
    
    /**
     * File integrity statistics
     */
    public static class FileIntegrityStatistics {
        public long totalChecks = 0;
        public long totalViolations = 0;
        public long totalFilesMonitored = 0;
        public int currentMonitoredFiles = 0;
        public int criticalFiles = 0;
        public int suspiciousFiles = 0;
        public long lastIntegrityCheck = 0;
        public boolean watchingActive = false;
        
        @Override
        public String toString() {
            return String.format("FileIntegrityStatistics{checks=%d, violations=%d, monitored=%d, critical=%d, suspicious=%d, watching=%s}",
                               totalChecks, totalViolations, currentMonitoredFiles, criticalFiles, suspiciousFiles, watchingActive);
        }
    }
}