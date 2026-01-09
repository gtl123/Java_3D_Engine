package fps.anticheat.client;

import engine.logging.LogManager;
import fps.anticheat.*;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * Monitors running processes to detect known cheat software and suspicious applications.
 * Maintains blacklists and whitelists of processes and performs pattern matching.
 */
public class ProcessMonitor {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Configuration
    private AntiCheatConfiguration config;
    
    // Process blacklists and whitelists
    private final Set<String> blacklistedProcesses = new HashSet<>();
    private final Set<String> blacklistedPatterns = new HashSet<>();
    private final Set<String> whitelistedProcesses = new HashSet<>();
    private final Set<String> suspiciousProcesses = new HashSet<>();
    
    // Process monitoring state
    private final Map<String, ProcessInfo> runningProcesses = new ConcurrentHashMap<>();
    private long lastScanTime = 0;
    private int scanInterval = 5000; // 5 seconds
    
    // Detection statistics
    private long totalScans = 0;
    private long totalViolations = 0;
    private long totalProcessesScanned = 0;
    
    // System state
    private boolean initialized = false;
    
    public ProcessMonitor() {
        logManager.debug("ProcessMonitor", "Process monitor created");
    }
    
    /**
     * Initialize the process monitor
     */
    public void initialize() throws Exception {
        logManager.info("ProcessMonitor", "Initializing process monitor");
        
        try {
            // Load default blacklists
            loadDefaultBlacklists();
            
            // Load default whitelists
            loadDefaultWhitelists();
            
            // Perform initial scan
            performInitialScan();
            
            initialized = true;
            
            logManager.info("ProcessMonitor", "Process monitor initialization complete");
            
        } catch (Exception e) {
            logManager.error("ProcessMonitor", "Failed to initialize process monitor", e);
            throw e;
        }
    }
    
    /**
     * Set configuration
     */
    public void setConfiguration(AntiCheatConfiguration config) {
        this.config = config;
        
        if (config != null) {
            // Update scan interval based on configuration
            // Configuration could include process monitoring settings
        }
    }
    
    /**
     * Perform process scan
     */
    public ProcessScanResult scan() {
        if (!initialized) {
            return ProcessScanResult.error("Process monitor not initialized");
        }
        
        long startTime = System.nanoTime();
        totalScans++;
        
        try {
            ProcessScanResult result = new ProcessScanResult();
            
            // Get current running processes
            List<ProcessInfo> currentProcesses = getCurrentProcesses();
            totalProcessesScanned += currentProcesses.size();
            
            // Update running processes map
            updateRunningProcesses(currentProcesses);
            
            // Check each process against blacklists
            for (ProcessInfo process : currentProcesses) {
                ProcessViolation violation = checkProcess(process);
                if (violation != null) {
                    result.addViolation(violation);
                    totalViolations++;
                }
            }
            
            // Check for suspicious process patterns
            List<ProcessViolation> patternViolations = checkSuspiciousPatterns(currentProcesses);
            for (ProcessViolation violation : patternViolations) {
                result.addViolation(violation);
                totalViolations++;
            }
            
            // Calculate scan time
            long scanTime = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds
            result.setScanTime(scanTime);
            
            lastScanTime = System.currentTimeMillis();
            
            return result;
            
        } catch (Exception e) {
            logManager.error("ProcessMonitor", "Error performing process scan", e);
            return ProcessScanResult.error("Process scan error: " + e.getMessage());
        }
    }
    
    /**
     * Load default process blacklists
     */
    private void loadDefaultBlacklists() {
        // Known cheat software executables
        blacklistedProcesses.add("cheatengine.exe");
        blacklistedProcesses.add("cheatengine-x86_64.exe");
        blacklistedProcesses.add("cheatengine-i386.exe");
        blacklistedProcesses.add("artmoney.exe");
        blacklistedProcesses.add("speedhack.exe");
        blacklistedProcesses.add("gameguardian.exe");
        blacklistedProcesses.add("memoryhacker.exe");
        blacklistedProcesses.add("trainer.exe");
        blacklistedProcesses.add("hack.exe");
        blacklistedProcesses.add("aimbot.exe");
        blacklistedProcesses.add("wallhack.exe");
        blacklistedProcesses.add("speedhack.exe");
        blacklistedProcesses.add("norecoil.exe");
        blacklistedProcesses.add("triggerbot.exe");
        
        // Known injection tools
        blacklistedProcesses.add("injector.exe");
        blacklistedProcesses.add("dllinjector.exe");
        blacklistedProcesses.add("processinjector.exe");
        blacklistedProcesses.add("codecave.exe");
        blacklistedProcesses.add("memoryeditor.exe");
        
        // Debugging and reverse engineering tools
        blacklistedProcesses.add("ollydbg.exe");
        blacklistedProcesses.add("x64dbg.exe");
        blacklistedProcesses.add("x32dbg.exe");
        blacklistedProcesses.add("ida.exe");
        blacklistedProcesses.add("ida64.exe");
        blacklistedProcesses.add("windbg.exe");
        blacklistedProcesses.add("processhacker.exe");
        blacklistedProcesses.add("procmon.exe");
        blacklistedProcesses.add("apimonitor.exe");
        
        // Pattern-based detection
        blacklistedPatterns.add(".*cheat.*");
        blacklistedPatterns.add(".*hack.*");
        blacklistedPatterns.add(".*bot.*");
        blacklistedPatterns.add(".*trainer.*");
        blacklistedPatterns.add(".*inject.*");
        blacklistedPatterns.add(".*memory.*hack.*");
        blacklistedPatterns.add(".*speed.*hack.*");
        blacklistedPatterns.add(".*wall.*hack.*");
        blacklistedPatterns.add(".*aim.*bot.*");
        blacklistedPatterns.add(".*trigger.*bot.*");
        
        // Suspicious processes that warrant investigation
        suspiciousProcesses.add("autohotkey.exe");
        suspiciousProcesses.add("autoit3.exe");
        suspiciousProcesses.add("python.exe");
        suspiciousProcesses.add("powershell.exe");
        suspiciousProcesses.add("cmd.exe");
        suspiciousProcesses.add("wscript.exe");
        suspiciousProcesses.add("cscript.exe");
        
        logManager.info("ProcessMonitor", "Loaded default blacklists",
                       "blacklistedProcesses", blacklistedProcesses.size(),
                       "blacklistedPatterns", blacklistedPatterns.size(),
                       "suspiciousProcesses", suspiciousProcesses.size());
    }
    
    /**
     * Load default process whitelists
     */
    private void loadDefaultWhitelists() {
        // System processes
        whitelistedProcesses.add("system");
        whitelistedProcesses.add("smss.exe");
        whitelistedProcesses.add("csrss.exe");
        whitelistedProcesses.add("wininit.exe");
        whitelistedProcesses.add("winlogon.exe");
        whitelistedProcesses.add("services.exe");
        whitelistedProcesses.add("lsass.exe");
        whitelistedProcesses.add("svchost.exe");
        whitelistedProcesses.add("explorer.exe");
        whitelistedProcesses.add("dwm.exe");
        
        // Common legitimate software
        whitelistedProcesses.add("chrome.exe");
        whitelistedProcesses.add("firefox.exe");
        whitelistedProcesses.add("notepad.exe");
        whitelistedProcesses.add("calc.exe");
        whitelistedProcesses.add("mspaint.exe");
        whitelistedProcesses.add("winword.exe");
        whitelistedProcesses.add("excel.exe");
        whitelistedProcesses.add("outlook.exe");
        whitelistedProcesses.add("steam.exe");
        whitelistedProcesses.add("discord.exe");
        whitelistedProcesses.add("spotify.exe");
        
        // Java and game-related processes
        whitelistedProcesses.add("java.exe");
        whitelistedProcesses.add("javaw.exe");
        whitelistedProcesses.add("minecraft.exe");
        
        logManager.info("ProcessMonitor", "Loaded default whitelists",
                       "whitelistedProcesses", whitelistedProcesses.size());
    }
    
    /**
     * Perform initial process scan
     */
    private void performInitialScan() {
        ProcessScanResult result = scan();
        
        if (result.hasViolations()) {
            logManager.warn("ProcessMonitor", "Initial process scan detected violations",
                           "violations", result.getViolations().size());
        }
        
        logManager.info("ProcessMonitor", "Initial process scan complete",
                       "processesScanned", result.getProcessesScanned(),
                       "violations", result.getViolations().size());
    }
    
    /**
     * Get current running processes
     */
    private List<ProcessInfo> getCurrentProcesses() throws IOException {
        List<ProcessInfo> processes = new ArrayList<>();
        
        try {
            // Use system-specific command to get process list
            String os = System.getProperty("os.name").toLowerCase();
            Process proc;
            
            if (os.contains("win")) {
                // Windows: use tasklist command
                proc = Runtime.getRuntime().exec("tasklist /fo csv /nh");
            } else {
                // Unix/Linux: use ps command
                proc = Runtime.getRuntime().exec("ps -eo pid,comm,args");
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line;
            
            while ((line = reader.readLine()) != null) {
                ProcessInfo processInfo = parseProcessLine(line, os.contains("win"));
                if (processInfo != null) {
                    processes.add(processInfo);
                }
            }
            
            reader.close();
            proc.waitFor();
            
        } catch (Exception e) {
            logManager.error("ProcessMonitor", "Error getting process list", e);
            throw new IOException("Failed to get process list", e);
        }
        
        return processes;
    }
    
    /**
     * Parse process information from command output
     */
    private ProcessInfo parseProcessLine(String line, boolean isWindows) {
        try {
            if (isWindows) {
                // Parse Windows tasklist CSV output
                String[] parts = line.split("\",\"");
                if (parts.length >= 2) {
                    String name = parts[0].replace("\"", "").toLowerCase();
                    String pidStr = parts[1].replace("\"", "");
                    
                    try {
                        int pid = Integer.parseInt(pidStr);
                        return new ProcessInfo(pid, name, line);
                    } catch (NumberFormatException e) {
                        // Skip invalid PID
                        return null;
                    }
                }
            } else {
                // Parse Unix ps output
                String[] parts = line.trim().split("\\s+", 3);
                if (parts.length >= 2) {
                    try {
                        int pid = Integer.parseInt(parts[0]);
                        String name = parts[1].toLowerCase();
                        String args = parts.length > 2 ? parts[2] : "";
                        
                        return new ProcessInfo(pid, name, args);
                    } catch (NumberFormatException e) {
                        // Skip invalid PID
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            logManager.debug("ProcessMonitor", "Error parsing process line", e, "line", line);
        }
        
        return null;
    }
    
    /**
     * Update running processes map
     */
    private void updateRunningProcesses(List<ProcessInfo> currentProcesses) {
        // Clear old processes
        runningProcesses.clear();
        
        // Add current processes
        for (ProcessInfo process : currentProcesses) {
            runningProcesses.put(process.getName(), process);
        }
    }
    
    /**
     * Check a single process for violations
     */
    private ProcessViolation checkProcess(ProcessInfo process) {
        String processName = process.getName().toLowerCase();
        
        // Skip whitelisted processes
        if (whitelistedProcesses.contains(processName)) {
            return null;
        }
        
        // Check exact blacklist matches
        if (blacklistedProcesses.contains(processName)) {
            return new ProcessViolation(
                ViolationType.BANNED_SOFTWARE,
                "Blacklisted process detected: " + processName,
                process,
                1.0f,
                "exact_match"
            );
        }
        
        // Check pattern matches
        for (String pattern : blacklistedPatterns) {
            if (processName.matches(pattern)) {
                return new ProcessViolation(
                    ViolationType.BANNED_SOFTWARE,
                    "Process matches blacklisted pattern: " + processName + " (pattern: " + pattern + ")",
                    process,
                    0.8f,
                    "pattern_match"
                );
            }
        }
        
        // Check suspicious processes
        if (suspiciousProcesses.contains(processName)) {
            return new ProcessViolation(
                ViolationType.CLIENT_MODIFICATION,
                "Suspicious process detected: " + processName,
                process,
                0.5f,
                "suspicious_process"
            );
        }
        
        return null;
    }
    
    /**
     * Check for suspicious process patterns
     */
    private List<ProcessViolation> checkSuspiciousPatterns(List<ProcessInfo> processes) {
        List<ProcessViolation> violations = new ArrayList<>();
        
        // Check for multiple instances of the same suspicious process
        Map<String, Integer> processCount = new ConcurrentHashMap<>();
        for (ProcessInfo process : processes) {
            String name = process.getName();
            processCount.put(name, processCount.getOrDefault(name, 0) + 1);
        }
        
        for (Map.Entry<String, Integer> entry : processCount.entrySet()) {
            String processName = entry.getKey();
            int count = entry.getValue();
            
            // Check for suspicious multiple instances
            if (suspiciousProcesses.contains(processName) && count > 3) {
                violations.add(new ProcessViolation(
                    ViolationType.SUSPICIOUS_PATTERNS,
                    "Multiple instances of suspicious process: " + processName + " (" + count + " instances)",
                    new ProcessInfo(-1, processName, "multiple_instances"),
                    0.6f,
                    "multiple_instances"
                ));
            }
        }
        
        return violations;
    }
    
    /**
     * Add process to blacklist
     */
    public void addToBlacklist(String processName) {
        blacklistedProcesses.add(processName.toLowerCase());
        
        logManager.info("ProcessMonitor", "Added process to blacklist",
                       "processName", processName);
    }
    
    /**
     * Add pattern to blacklist
     */
    public void addPatternToBlacklist(String pattern) {
        blacklistedPatterns.add(pattern);
        
        logManager.info("ProcessMonitor", "Added pattern to blacklist",
                       "pattern", pattern);
    }
    
    /**
     * Add process to whitelist
     */
    public void addToWhitelist(String processName) {
        whitelistedProcesses.add(processName.toLowerCase());
        
        logManager.info("ProcessMonitor", "Added process to whitelist",
                       "processName", processName);
    }
    
    /**
     * Remove process from blacklist
     */
    public void removeFromBlacklist(String processName) {
        blacklistedProcesses.remove(processName.toLowerCase());
        
        logManager.info("ProcessMonitor", "Removed process from blacklist",
                       "processName", processName);
    }
    
    /**
     * Check if process is blacklisted
     */
    public boolean isBlacklisted(String processName) {
        return blacklistedProcesses.contains(processName.toLowerCase());
    }
    
    /**
     * Check if process is whitelisted
     */
    public boolean isWhitelisted(String processName) {
        return whitelistedProcesses.contains(processName.toLowerCase());
    }
    
    /**
     * Get process monitoring statistics
     */
    public ProcessMonitoringStatistics getStatistics() {
        ProcessMonitoringStatistics stats = new ProcessMonitoringStatistics();
        stats.totalScans = totalScans;
        stats.totalViolations = totalViolations;
        stats.totalProcessesScanned = totalProcessesScanned;
        stats.blacklistedProcessCount = blacklistedProcesses.size();
        stats.blacklistedPatternCount = blacklistedPatterns.size();
        stats.whitelistedProcessCount = whitelistedProcesses.size();
        stats.suspiciousProcessCount = suspiciousProcesses.size();
        stats.currentRunningProcesses = runningProcesses.size();
        stats.lastScanTime = lastScanTime;
        
        return stats;
    }
    
    /**
     * Update process monitor
     */
    public void update(float deltaTime) {
        // Update any time-based monitoring parameters
        
        // Perform periodic scans if needed
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScanTime > scanInterval) {
            // Background scan would be handled by ClientSideMonitor
        }
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        runningProcesses.clear();
        initialized = false;
        
        logManager.debug("ProcessMonitor", "Process monitor cleaned up");
    }
    
    // Getters and setters
    public Set<String> getBlacklistedProcesses() { return new HashSet<>(blacklistedProcesses); }
    public Set<String> getBlacklistedPatterns() { return new HashSet<>(blacklistedPatterns); }
    public Set<String> getWhitelistedProcesses() { return new HashSet<>(whitelistedProcesses); }
    public Set<String> getSuspiciousProcesses() { return new HashSet<>(suspiciousProcesses); }
    public Map<String, ProcessInfo> getRunningProcesses() { return new ConcurrentHashMap<>(runningProcesses); }
    public int getScanInterval() { return scanInterval; }
    public void setScanInterval(int scanInterval) { this.scanInterval = scanInterval; }
    public boolean isInitialized() { return initialized; }
    
    /**
     * Process monitoring statistics
     */
    public static class ProcessMonitoringStatistics {
        public long totalScans = 0;
        public long totalViolations = 0;
        public long totalProcessesScanned = 0;
        public int blacklistedProcessCount = 0;
        public int blacklistedPatternCount = 0;
        public int whitelistedProcessCount = 0;
        public int suspiciousProcessCount = 0;
        public int currentRunningProcesses = 0;
        public long lastScanTime = 0;
        
        @Override
        public String toString() {
            return String.format("ProcessMonitoringStatistics{scans=%d, violations=%d, processesScanned=%d, blacklisted=%d, whitelisted=%d, running=%d}",
                               totalScans, totalViolations, totalProcessesScanned, 
                               blacklistedProcessCount, whitelistedProcessCount, currentRunningProcesses);
        }
    }
}