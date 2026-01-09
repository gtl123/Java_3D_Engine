package fps.anticheat.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/**
 * Tamper protection system to detect modifications to anti-cheat files and memory.
 * Implements integrity checking, self-verification, and anti-modification measures.
 */
public class TamperProtectionManager {
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final ScheduledExecutorService scheduler;
    private final Map<String, FileIntegrityInfo> fileIntegrityMap;
    private final Map<String, MemoryRegionInfo> memoryRegionMap;
    private final Set<String> criticalClasses;
    
    private volatile boolean tamperingDetected = false;
    private volatile int tamperAttempts = 0;
    private final long checkInterval = 10000; // 10 seconds
    
    public TamperProtectionManager() {
        this.scheduler = Executors.newScheduledThreadPool(3);
        this.fileIntegrityMap = new ConcurrentHashMap<>();
        this.memoryRegionMap = new ConcurrentHashMap<>();
        this.criticalClasses = new HashSet<>();
        
        initializeCriticalClasses();
        initializeIntegrityChecking();
        startPeriodicChecks();
    }
    
    /**
     * Initialize list of critical classes to monitor
     */
    private void initializeCriticalClasses() {
        criticalClasses.add("fps.anticheat.AntiCheatEngine");
        criticalClasses.add("fps.anticheat.server.ServerValidator");
        criticalClasses.add("fps.anticheat.client.ClientMonitor");
        criticalClasses.add("fps.anticheat.behavioral.BehavioralAnalysisEngine");
        criticalClasses.add("fps.anticheat.statistical.StatisticalAnalysisEngine");
        criticalClasses.add("fps.anticheat.hardware.HardwareFingerprintManager");
        criticalClasses.add("fps.anticheat.realtime.RealtimeCheatDetector");
        criticalClasses.add("fps.anticheat.punishment.BanManager");
        criticalClasses.add("fps.anticheat.security.SecurityObfuscator");
        criticalClasses.add("fps.anticheat.security.AntiDebuggingManager");
    }
    
    /**
     * Initialize integrity checking for critical files
     */
    private void initializeIntegrityChecking() {
        // In a real implementation, this would scan for actual JAR files and class files
        // For this example, we'll simulate the process
        
        try {
            // Get the current JAR file path (simulated)
            String jarPath = getJarFilePath();
            if (jarPath != null) {
                calculateFileIntegrity(jarPath);
            }
            
            // Calculate integrity for critical class files
            for (String className : criticalClasses) {
                calculateClassIntegrity(className);
            }
            
        } catch (Exception e) {
            // Log error but don't fail initialization
            System.err.println("Failed to initialize integrity checking: " + e.getMessage());
        }
    }
    
    /**
     * Get the path to the current JAR file
     */
    private String getJarFilePath() {
        try {
            return TamperProtectionManager.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Calculate file integrity hash
     */
    private void calculateFileIntegrity(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                byte[] fileBytes = Files.readAllBytes(path);
                String hash = calculateSHA256Hash(fileBytes);
                long size = Files.size(path);
                long lastModified = Files.getLastModifiedTime(path).toMillis();
                
                FileIntegrityInfo info = new FileIntegrityInfo(filePath, hash, size, lastModified);
                fileIntegrityMap.put(filePath, info);
            }
        } catch (Exception e) {
            // Ignore errors for individual files
        }
    }
    
    /**
     * Calculate class integrity hash
     */
    private void calculateClassIntegrity(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            
            // Get class bytecode (simulated - in reality this would be more complex)
            String classInfo = clazz.getName() + clazz.getMethods().length + 
                             clazz.getFields().length + clazz.getDeclaredMethods().length;
            
            String hash = calculateSHA256Hash(classInfo.getBytes(StandardCharsets.UTF_8));
            
            MemoryRegionInfo info = new MemoryRegionInfo(className, hash, System.currentTimeMillis());
            memoryRegionMap.put(className, info);
            
        } catch (ClassNotFoundException e) {
            // Class not found - might indicate tampering or normal absence
        } catch (Exception e) {
            // Other errors - log but continue
        }
    }
    
    /**
     * Calculate SHA-256 hash
     */
    private String calculateSHA256Hash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            
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
            throw new SecurityException("Failed to calculate hash", e);
        }
    }
    
    /**
     * Start periodic integrity checks
     */
    private void startPeriodicChecks() {
        // File integrity checks
        scheduler.scheduleAtFixedRate(this::checkFileIntegrity, 
                                    SECURE_RANDOM.nextInt(5000) + 5000, // Random initial delay
                                    checkInterval + SECURE_RANDOM.nextInt(5000), // Random interval
                                    TimeUnit.MILLISECONDS);
        
        // Memory integrity checks
        scheduler.scheduleAtFixedRate(this::checkMemoryIntegrity, 
                                    SECURE_RANDOM.nextInt(3000) + 2000,
                                    checkInterval / 2 + SECURE_RANDOM.nextInt(3000),
                                    TimeUnit.MILLISECONDS);
        
        // Self-verification checks
        scheduler.scheduleAtFixedRate(this::performSelfVerification, 
                                    SECURE_RANDOM.nextInt(10000) + 10000,
                                    checkInterval * 2 + SECURE_RANDOM.nextInt(10000),
                                    TimeUnit.MILLISECONDS);
    }
    
    /**
     * Check file integrity
     */
    private void checkFileIntegrity() {
        for (Map.Entry<String, FileIntegrityInfo> entry : fileIntegrityMap.entrySet()) {
            String filePath = entry.getKey();
            FileIntegrityInfo originalInfo = entry.getValue();
            
            try {
                Path path = Paths.get(filePath);
                if (!Files.exists(path)) {
                    handleTamperDetection("FILE_DELETED", filePath);
                    continue;
                }
                
                long currentSize = Files.size(path);
                long currentLastModified = Files.getLastModifiedTime(path).toMillis();
                
                // Check if file has been modified
                if (currentSize != originalInfo.getSize() || 
                    currentLastModified != originalInfo.getLastModified()) {
                    
                    // Recalculate hash to confirm tampering
                    byte[] fileBytes = Files.readAllBytes(path);
                    String currentHash = calculateSHA256Hash(fileBytes);
                    
                    if (!currentHash.equals(originalInfo.getHash())) {
                        handleTamperDetection("FILE_MODIFIED", filePath);
                    }
                }
                
            } catch (Exception e) {
                handleTamperDetection("FILE_ACCESS_ERROR", filePath);
            }
        }
    }
    
    /**
     * Check memory integrity
     */
    private void checkMemoryIntegrity() {
        for (Map.Entry<String, MemoryRegionInfo> entry : memoryRegionMap.entrySet()) {
            String className = entry.getKey();
            MemoryRegionInfo originalInfo = entry.getValue();
            
            try {
                Class<?> clazz = Class.forName(className);
                
                // Recalculate class information
                String classInfo = clazz.getName() + clazz.getMethods().length + 
                                 clazz.getFields().length + clazz.getDeclaredMethods().length;
                
                String currentHash = calculateSHA256Hash(classInfo.getBytes(StandardCharsets.UTF_8));
                
                if (!currentHash.equals(originalInfo.getHash())) {
                    handleTamperDetection("CLASS_MODIFIED", className);
                }
                
            } catch (ClassNotFoundException e) {
                handleTamperDetection("CLASS_MISSING", className);
            } catch (Exception e) {
                handleTamperDetection("CLASS_ACCESS_ERROR", className);
            }
        }
    }
    
    /**
     * Perform self-verification
     */
    private void performSelfVerification() {
        try {
            // Verify that this class itself hasn't been tampered with
            Class<?> selfClass = TamperProtectionManager.class;
            
            // Check class loader
            ClassLoader loader = selfClass.getClassLoader();
            if (loader != null && !loader.getClass().getName().startsWith("java.")) {
                String loaderName = loader.getClass().getName().toLowerCase();
                if (loaderName.contains("agent") || loaderName.contains("instrument")) {
                    handleTamperDetection("INSTRUMENTATION_DETECTED", selfClass.getName());
                }
            }
            
            // Check for unexpected annotations or modifications
            if (selfClass.getDeclaredAnnotations().length > 0) {
                // Unexpected annotations might indicate instrumentation
                handleTamperDetection("UNEXPECTED_ANNOTATIONS", selfClass.getName());
            }
            
            // Verify critical methods exist and have expected signatures
            verifyCriticalMethods();
            
        } catch (Exception e) {
            handleTamperDetection("SELF_VERIFICATION_ERROR", "TamperProtectionManager");
        }
    }
    
    /**
     * Verify critical methods haven't been tampered with
     */
    private void verifyCriticalMethods() {
        try {
            Class<?> selfClass = TamperProtectionManager.class;
            
            // Check that critical methods exist
            selfClass.getDeclaredMethod("checkFileIntegrity");
            selfClass.getDeclaredMethod("checkMemoryIntegrity");
            selfClass.getDeclaredMethod("handleTamperDetection", String.class, String.class);
            
            // Check method count - if significantly different, might indicate tampering
            int methodCount = selfClass.getDeclaredMethods().length;
            if (methodCount < 10 || methodCount > 50) { // Expected range
                handleTamperDetection("METHOD_COUNT_ANOMALY", "Expected 10-50 methods, found " + methodCount);
            }
            
        } catch (NoSuchMethodException e) {
            handleTamperDetection("CRITICAL_METHOD_MISSING", e.getMessage());
        }
    }
    
    /**
     * Handle tamper detection
     */
    private void handleTamperDetection(String tamperType, String details) {
        tamperingDetected = true;
        tamperAttempts++;
        
        // Log the detection
        System.err.println("TAMPER DETECTED: " + tamperType + " - " + details);
        
        // Implement countermeasures based on tamper type
        switch (tamperType) {
            case "FILE_MODIFIED":
            case "FILE_DELETED":
                handleFileTampering(details);
                break;
                
            case "CLASS_MODIFIED":
            case "CLASS_MISSING":
                handleClassTampering(details);
                break;
                
            case "INSTRUMENTATION_DETECTED":
                handleInstrumentationTampering(details);
                break;
                
            default:
                handleGenericTampering(tamperType, details);
                break;
        }
        
        // If too many tamper attempts, trigger severe response
        if (tamperAttempts >= 5) {
            triggerSevereResponse();
        }
    }
    
    /**
     * Handle file tampering
     */
    private void handleFileTampering(String filePath) {
        // In a real implementation, this could:
        // 1. Attempt to restore from backup
        // 2. Download fresh copy from server
        // 3. Terminate application
        // 4. Report to server
        
        System.err.println("File tampering detected: " + filePath);
    }
    
    /**
     * Handle class tampering
     */
    private void handleClassTampering(String className) {
        // In a real implementation, this could:
        // 1. Reload class from trusted source
        // 2. Disable affected functionality
        // 3. Switch to backup implementation
        
        System.err.println("Class tampering detected: " + className);
    }
    
    /**
     * Handle instrumentation tampering
     */
    private void handleInstrumentationTampering(String details) {
        // This is serious - indicates active debugging/modification
        System.err.println("Instrumentation tampering detected: " + details);
        
        // Could trigger immediate shutdown or corruption of sensitive data
    }
    
    /**
     * Handle generic tampering
     */
    private void handleGenericTampering(String tamperType, String details) {
        System.err.println("Generic tampering detected - Type: " + tamperType + ", Details: " + details);
    }
    
    /**
     * Trigger severe response to repeated tampering
     */
    private void triggerSevereResponse() {
        System.err.println("SEVERE TAMPER RESPONSE TRIGGERED - Multiple tampering attempts detected");
        
        // In a real implementation, this could:
        // 1. Corrupt all anti-cheat data
        // 2. Send emergency alert to server
        // 3. Terminate application immediately
        // 4. Lock out user account
        
        // For this example, we'll just set a flag
        tamperingDetected = true;
    }
    
    /**
     * Add file to integrity monitoring
     */
    public void addFileToMonitoring(String filePath) {
        calculateFileIntegrity(filePath);
    }
    
    /**
     * Add class to integrity monitoring
     */
    public void addClassToMonitoring(String className) {
        calculateClassIntegrity(className);
        criticalClasses.add(className);
    }
    
    /**
     * Check if tampering has been detected
     */
    public boolean isTamperingDetected() {
        return tamperingDetected;
    }
    
    /**
     * Get number of tamper attempts
     */
    public int getTamperAttempts() {
        return tamperAttempts;
    }
    
    /**
     * Get integrity statistics
     */
    public TamperProtectionStatistics getStatistics() {
        return new TamperProtectionStatistics(
            fileIntegrityMap.size(),
            memoryRegionMap.size(),
            tamperAttempts,
            tamperingDetected
        );
    }
    
    /**
     * Reset tamper detection state
     */
    public void resetTamperState() {
        tamperingDetected = false;
        tamperAttempts = 0;
    }
    
    /**
     * Shutdown tamper protection
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * File integrity information
     */
    private static class FileIntegrityInfo {
        private final String filePath;
        private final String hash;
        private final long size;
        private final long lastModified;
        
        public FileIntegrityInfo(String filePath, String hash, long size, long lastModified) {
            this.filePath = filePath;
            this.hash = hash;
            this.size = size;
            this.lastModified = lastModified;
        }
        
        public String getFilePath() { return filePath; }
        public String getHash() { return hash; }
        public long getSize() { return size; }
        public long getLastModified() { return lastModified; }
    }
    
    /**
     * Memory region integrity information
     */
    private static class MemoryRegionInfo {
        private final String identifier;
        private final String hash;
        private final long timestamp;
        
        public MemoryRegionInfo(String identifier, String hash, long timestamp) {
            this.identifier = identifier;
            this.hash = hash;
            this.timestamp = timestamp;
        }
        
        public String getIdentifier() { return identifier; }
        public String getHash() { return hash; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Tamper protection statistics
     */
    public static class TamperProtectionStatistics {
        private final int monitoredFiles;
        private final int monitoredClasses;
        private final int tamperAttempts;
        private final boolean tamperingDetected;
        
        public TamperProtectionStatistics(int monitoredFiles, int monitoredClasses, 
                                        int tamperAttempts, boolean tamperingDetected) {
            this.monitoredFiles = monitoredFiles;
            this.monitoredClasses = monitoredClasses;
            this.tamperAttempts = tamperAttempts;
            this.tamperingDetected = tamperingDetected;
        }
        
        public int getMonitoredFiles() { return monitoredFiles; }
        public int getMonitoredClasses() { return monitoredClasses; }
        public int getTamperAttempts() { return tamperAttempts; }
        public boolean isTamperingDetected() { return tamperingDetected; }
        
        @Override
        public String toString() {
            return String.format("TamperProtectionStatistics{files=%d, classes=%d, attempts=%d, detected=%s}", 
                               monitoredFiles, monitoredClasses, tamperAttempts, tamperingDetected);
        }
    }
}