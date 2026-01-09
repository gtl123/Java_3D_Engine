package fps.anticheat.client;

import engine.logging.LogManager;
import fps.anticheat.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

/**
 * Client-side monitoring system for detecting cheat software and modifications.
 * Monitors processes, memory integrity, and system modifications.
 */
public class ClientSideMonitor {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Configuration
    private AntiCheatConfiguration config;
    
    // Monitoring components
    private ProcessMonitor processMonitor;
    private MemoryIntegrityChecker memoryChecker;
    private FileIntegrityChecker fileChecker;
    private DLLInjectionDetector dllDetector;
    private HookDetector hookDetector;
    private ScreenCaptureDetector screenDetector;
    
    // Player monitoring state
    private final Map<Integer, ClientMonitoringState> playerStates = new ConcurrentHashMap<>();
    
    // Background monitoring
    private ScheduledExecutorService monitoringExecutor;
    
    // System state
    private boolean initialized = false;
    private boolean monitoringEnabled = true;
    
    // Performance metrics
    private long totalScansPerformed = 0;
    private long totalViolationsDetected = 0;
    private float averageScanTime = 0.0f;
    
    public ClientSideMonitor() {
        logManager.info("ClientSideMonitor", "Client-side monitor created");
    }
    
    /**
     * Initialize the client-side monitor
     */
    public void initialize() throws Exception {
        logManager.info("ClientSideMonitor", "Initializing client-side monitor");
        
        try {
            // Initialize monitoring components
            processMonitor = new ProcessMonitor();
            processMonitor.initialize();
            
            memoryChecker = new MemoryIntegrityChecker();
            memoryChecker.initialize();
            
            fileChecker = new FileIntegrityChecker();
            fileChecker.initialize();
            
            dllDetector = new DLLInjectionDetector();
            dllDetector.initialize();
            
            hookDetector = new HookDetector();
            hookDetector.initialize();
            
            screenDetector = new ScreenCaptureDetector();
            screenDetector.initialize();
            
            // Initialize background monitoring
            monitoringExecutor = Executors.newScheduledThreadPool(2);
            
            // Start background monitoring tasks
            startBackgroundMonitoring();
            
            initialized = true;
            
            logManager.info("ClientSideMonitor", "Client-side monitor initialization complete");
            
        } catch (Exception e) {
            logManager.error("ClientSideMonitor", "Failed to initialize client-side monitor", e);
            throw e;
        }
    }
    
    /**
     * Set configuration
     */
    public void setConfiguration(AntiCheatConfiguration config) {
        this.config = config;
        
        // Update component configurations
        if (processMonitor != null) processMonitor.setConfiguration(config);
        if (memoryChecker != null) memoryChecker.setConfiguration(config);
        if (fileChecker != null) fileChecker.setConfiguration(config);
        if (dllDetector != null) dllDetector.setConfiguration(config);
        if (hookDetector != null) hookDetector.setConfiguration(config);
        if (screenDetector != null) screenDetector.setConfiguration(config);
    }
    
    /**
     * Start monitoring a player
     */
    public void startMonitoring(int playerId) {
        if (!initialized || !monitoringEnabled) {
            return;
        }
        
        ClientMonitoringState state = new ClientMonitoringState(playerId);
        playerStates.put(playerId, state);
        
        // Perform initial scan
        performInitialScan(playerId);
        
        logManager.info("ClientSideMonitor", "Started monitoring player",
                       "playerId", playerId);
    }
    
    /**
     * Stop monitoring a player
     */
    public void stopMonitoring(int playerId) {
        ClientMonitoringState state = playerStates.remove(playerId);
        
        if (state != null) {
            // Generate final monitoring report
            generateMonitoringReport(state);
            
            logManager.info("ClientSideMonitor", "Stopped monitoring player",
                           "playerId", playerId);
        }
    }
    
    /**
     * Perform comprehensive scan for a player
     */
    public ClientScanResult performScan(int playerId) {
        if (!initialized || !monitoringEnabled) {
            return ClientScanResult.clean("Monitoring disabled");
        }
        
        long startTime = System.nanoTime();
        totalScansPerformed++;
        
        try {
            ClientMonitoringState state = playerStates.get(playerId);
            if (state == null) {
                return ClientScanResult.error("Player not being monitored");
            }
            
            ClientScanResult result = new ClientScanResult();
            
            // Process monitoring
            ProcessScanResult processResult = processMonitor.scan();
            result.addProcessResult(processResult);
            
            // Memory integrity check
            MemoryIntegrityResult memoryResult = memoryChecker.checkIntegrity();
            result.addMemoryResult(memoryResult);
            
            // File integrity check
            FileIntegrityResult fileResult = fileChecker.checkIntegrity();
            result.addFileResult(fileResult);
            
            // DLL injection detection
            DLLInjectionResult dllResult = dllDetector.detectInjection();
            result.addDLLResult(dllResult);
            
            // Hook detection
            HookDetectionResult hookResult = hookDetector.detectHooks();
            result.addHookResult(hookResult);
            
            // Screen capture detection
            ScreenCaptureResult screenResult = screenDetector.detectCapture();
            result.addScreenResult(screenResult);
            
            // Update state with scan results
            state.updateWithScanResult(result);
            
            // Calculate scan time
            long scanTime = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds
            averageScanTime = (averageScanTime * (totalScansPerformed - 1) + scanTime) / totalScansPerformed;
            
            // Check for violations
            if (result.hasViolations()) {
                totalViolationsDetected++;
                handleViolations(playerId, result);
            }
            
            return result;
            
        } catch (Exception e) {
            logManager.error("ClientSideMonitor", "Error performing scan", e);
            return ClientScanResult.error("Scan error: " + e.getMessage());
        }
    }
    
    /**
     * Start background monitoring tasks
     */
    private void startBackgroundMonitoring() {
        // Continuous process monitoring (every 5 seconds)
        monitoringExecutor.scheduleAtFixedRate(() -> {
            try {
                performBackgroundProcessMonitoring();
            } catch (Exception e) {
                logManager.error("ClientSideMonitor", "Error in background process monitoring", e);
            }
        }, 5, 5, TimeUnit.SECONDS);
        
        // Periodic comprehensive scan (every 30 seconds)
        monitoringExecutor.scheduleAtFixedRate(() -> {
            try {
                performBackgroundComprehensiveScan();
            } catch (Exception e) {
                logManager.error("ClientSideMonitor", "Error in background comprehensive scan", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Perform initial scan when monitoring starts
     */
    private void performInitialScan(int playerId) {
        ClientScanResult result = performScan(playerId);
        
        if (result.hasViolations()) {
            logManager.warn("ClientSideMonitor", "Initial scan detected violations",
                           "playerId", playerId,
                           "violations", result.getViolationCount());
        }
    }
    
    /**
     * Background process monitoring
     */
    private void performBackgroundProcessMonitoring() {
        for (Integer playerId : playerStates.keySet()) {
            try {
                ProcessScanResult processResult = processMonitor.scan();
                
                if (processResult.hasViolations()) {
                    ClientScanResult scanResult = new ClientScanResult();
                    scanResult.addProcessResult(processResult);
                    handleViolations(playerId, scanResult);
                }
                
            } catch (Exception e) {
                logManager.error("ClientSideMonitor", "Error in background process monitoring for player", e,
                               "playerId", playerId);
            }
        }
    }
    
    /**
     * Background comprehensive scan
     */
    private void performBackgroundComprehensiveScan() {
        for (Integer playerId : playerStates.keySet()) {
            try {
                ClientScanResult result = performScan(playerId);
                
                // Results are already handled in performScan method
                
            } catch (Exception e) {
                logManager.error("ClientSideMonitor", "Error in background comprehensive scan for player", e,
                               "playerId", playerId);
            }
        }
    }
    
    /**
     * Handle detected violations
     */
    private void handleViolations(int playerId, ClientScanResult scanResult) {
        ClientMonitoringState state = playerStates.get(playerId);
        if (state == null) {
            return;
        }
        
        // Process each violation
        for (ClientViolation violation : scanResult.getViolations()) {
            // Create cheat violation for anti-cheat system
            CheatViolation cheatViolation = new CheatViolation(
                playerId,
                violation.getViolationType(),
                violation.getDescription(),
                violation.getEvidence(),
                violation.getConfidence(),
                "ClientSideMonitor"
            );
            
            // Add metadata
            cheatViolation.addMetadata("scan_type", violation.getScanType());
            cheatViolation.addMetadata("detection_method", violation.getDetectionMethod());
            cheatViolation.addMetadata("system_info", getSystemInfo());
            
            // Record violation in state
            state.addViolation(cheatViolation);
            
            logManager.warn("ClientSideMonitor", "Client-side violation detected",
                           "playerId", playerId,
                           "violationType", violation.getViolationType(),
                           "description", violation.getDescription(),
                           "confidence", violation.getConfidence());
        }
    }
    
    /**
     * Generate monitoring report for a player
     */
    private void generateMonitoringReport(ClientMonitoringState state) {
        ClientMonitoringReport report = new ClientMonitoringReport(state);
        
        logManager.info("ClientSideMonitor", "Generated monitoring report",
                       "playerId", state.getPlayerId(),
                       "totalScans", state.getTotalScans(),
                       "violations", state.getViolationCount(),
                       "monitoringDuration", state.getMonitoringDuration());
    }
    
    /**
     * Get system information
     */
    private SystemInfo getSystemInfo() {
        SystemInfo info = new SystemInfo();
        
        // Collect system information
        info.setOperatingSystem(System.getProperty("os.name"));
        info.setArchitecture(System.getProperty("os.arch"));
        info.setJavaVersion(System.getProperty("java.version"));
        info.setAvailableProcessors(Runtime.getRuntime().availableProcessors());
        info.setTotalMemory(Runtime.getRuntime().totalMemory());
        info.setFreeMemory(Runtime.getRuntime().freeMemory());
        
        return info;
    }
    
    /**
     * Update monitoring system
     */
    public void update(float deltaTime) {
        if (!initialized || !monitoringEnabled) {
            return;
        }
        
        try {
            // Update monitoring components
            if (processMonitor != null) processMonitor.update(deltaTime);
            if (memoryChecker != null) memoryChecker.update(deltaTime);
            if (fileChecker != null) fileChecker.update(deltaTime);
            if (dllDetector != null) dllDetector.update(deltaTime);
            if (hookDetector != null) hookDetector.update(deltaTime);
            if (screenDetector != null) screenDetector.update(deltaTime);
            
            // Update player states
            for (ClientMonitoringState state : playerStates.values()) {
                state.update(deltaTime);
            }
            
        } catch (Exception e) {
            logManager.error("ClientSideMonitor", "Error updating client-side monitor", e);
        }
    }
    
    /**
     * Get monitoring statistics
     */
    public ClientMonitoringStatistics getStatistics() {
        ClientMonitoringStatistics stats = new ClientMonitoringStatistics();
        stats.totalScansPerformed = totalScansPerformed;
        stats.totalViolationsDetected = totalViolationsDetected;
        stats.averageScanTime = averageScanTime;
        stats.activePlayersMonitored = playerStates.size();
        stats.monitoringEnabled = monitoringEnabled;
        stats.systemInitialized = initialized;
        
        return stats;
    }
    
    /**
     * Enable or disable monitoring
     */
    public void setMonitoringEnabled(boolean enabled) {
        this.monitoringEnabled = enabled;
        
        logManager.info("ClientSideMonitor", "Client-side monitoring " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        logManager.info("ClientSideMonitor", "Cleaning up client-side monitor");
        
        try {
            // Stop background monitoring
            if (monitoringExecutor != null) {
                monitoringExecutor.shutdown();
                monitoringExecutor.awaitTermination(5, TimeUnit.SECONDS);
            }
            
            // Cleanup monitoring components
            if (processMonitor != null) processMonitor.cleanup();
            if (memoryChecker != null) memoryChecker.cleanup();
            if (fileChecker != null) fileChecker.cleanup();
            if (dllDetector != null) dllDetector.cleanup();
            if (hookDetector != null) hookDetector.cleanup();
            if (screenDetector != null) screenDetector.cleanup();
            
            // Clear player states
            playerStates.clear();
            
            initialized = false;
            
            logManager.info("ClientSideMonitor", "Client-side monitor cleanup complete");
            
        } catch (Exception e) {
            logManager.error("ClientSideMonitor", "Error during cleanup", e);
        }
    }
    
    // Getters
    public boolean isInitialized() { return initialized; }
    public boolean isMonitoringEnabled() { return monitoringEnabled; }
    public ProcessMonitor getProcessMonitor() { return processMonitor; }
    public MemoryIntegrityChecker getMemoryChecker() { return memoryChecker; }
    public FileIntegrityChecker getFileChecker() { return fileChecker; }
    public DLLInjectionDetector getDllDetector() { return dllDetector; }
    public HookDetector getHookDetector() { return hookDetector; }
    public ScreenCaptureDetector getScreenDetector() { return screenDetector; }
    
    /**
     * Client monitoring statistics
     */
    public static class ClientMonitoringStatistics {
        public long totalScansPerformed = 0;
        public long totalViolationsDetected = 0;
        public float averageScanTime = 0.0f;
        public int activePlayersMonitored = 0;
        public boolean monitoringEnabled = false;
        public boolean systemInitialized = false;
        
        @Override
        public String toString() {
            return String.format("ClientMonitoringStatistics{scans=%d, violations=%d, avgTime=%.2fms, active=%d, enabled=%s}",
                               totalScansPerformed, totalViolationsDetected, averageScanTime, 
                               activePlayersMonitored, monitoringEnabled);
        }
    }
    
    /**
     * System information container
     */
    public static class SystemInfo {
        private String operatingSystem;
        private String architecture;
        private String javaVersion;
        private int availableProcessors;
        private long totalMemory;
        private long freeMemory;
        
        // Getters and setters
        public String getOperatingSystem() { return operatingSystem; }
        public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }
        
        public String getArchitecture() { return architecture; }
        public void setArchitecture(String architecture) { this.architecture = architecture; }
        
        public String getJavaVersion() { return javaVersion; }
        public void setJavaVersion(String javaVersion) { this.javaVersion = javaVersion; }
        
        public int getAvailableProcessors() { return availableProcessors; }
        public void setAvailableProcessors(int availableProcessors) { this.availableProcessors = availableProcessors; }
        
        public long getTotalMemory() { return totalMemory; }
        public void setTotalMemory(long totalMemory) { this.totalMemory = totalMemory; }
        
        public long getFreeMemory() { return freeMemory; }
        public void setFreeMemory(long freeMemory) { this.freeMemory = freeMemory; }
    }
}