package fps.anticheat.hardware;

import fps.anticheat.AntiCheatConfiguration;
import fps.anticheat.ViolationType;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Validates system environment and detects suspicious configurations.
 * Identifies virtual machines, sandboxes, debuggers, and other analysis environments.
 */
public class SystemValidator {
    
    private final AntiCheatConfiguration config;
    private final Map<String, ValidationResult> validationCache;
    
    // VM detection patterns
    private static final String[] VM_PROCESS_PATTERNS = {
        "vmware", "virtualbox", "vbox", "qemu", "xen", "hyper-v", "parallels",
        "vmtoolsd", "vboxservice", "vboxtray", "vmwareuser", "vmwaretray"
    };
    
    private static final String[] VM_FILE_PATTERNS = {
        "vmware", "virtualbox", "vbox", "qemu", "xen", "parallels",
        "vm", "virtual", "sandbox", "analysis"
    };
    
    private static final String[] ANALYSIS_TOOLS = {
        "ollydbg", "x64dbg", "ida", "ghidra", "cheat engine", "process hacker",
        "wireshark", "fiddler", "burp", "procmon", "regmon", "filemon"
    };
    
    // Sandbox indicators
    private static final String[] SANDBOX_INDICATORS = {
        "sandbox", "malware", "analysis", "cuckoo", "anubis", "joebox",
        "threatexpert", "comodo", "sunbelt", "cwsandbox"
    };
    
    // Cache settings
    private static final long VALIDATION_CACHE_DURATION = 300000; // 5 minutes
    
    /**
     * Result of system validation
     */
    public static class ValidationResult {
        private final boolean isVirtualMachine;
        private final boolean isSandbox;
        private final boolean hasDebugger;
        private final boolean hasAnalysisTools;
        private final float suspiciousScore;
        private final List<String> detectedIndicators;
        private final long timestamp;
        
        public ValidationResult(boolean isVirtualMachine, boolean isSandbox, boolean hasDebugger,
                              boolean hasAnalysisTools, float suspiciousScore, List<String> detectedIndicators) {
            this.isVirtualMachine = isVirtualMachine;
            this.isSandbox = isSandbox;
            this.hasDebugger = hasDebugger;
            this.hasAnalysisTools = hasAnalysisTools;
            this.suspiciousScore = suspiciousScore;
            this.detectedIndicators = new ArrayList<>(detectedIndicators);
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isSuspicious() {
            return isVirtualMachine || isSandbox || hasDebugger || hasAnalysisTools || suspiciousScore > 0.5f;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > VALIDATION_CACHE_DURATION;
        }
        
        // Getters
        public boolean isVirtualMachine() { return isVirtualMachine; }
        public boolean isSandbox() { return isSandbox; }
        public boolean hasDebugger() { return hasDebugger; }
        public boolean hasAnalysisTools() { return hasAnalysisTools; }
        public float getSuspiciousScore() { return suspiciousScore; }
        public List<String> getDetectedIndicators() { return new ArrayList<>(detectedIndicators); }
        public long getTimestamp() { return timestamp; }
    }
    
    public SystemValidator(AntiCheatConfiguration config) {
        this.config = config;
        this.validationCache = new HashMap<>();
    }
    
    /**
     * Perform comprehensive system validation
     */
    public ValidationResult validateSystem(HardwareFingerprint fingerprint) {
        // Check cache first
        String cacheKey = "system_validation";
        ValidationResult cached = validationCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }
        
        List<String> indicators = new ArrayList<>();
        boolean isVM = false;
        boolean isSandbox = false;
        boolean hasDebugger = false;
        boolean hasAnalysisTools = false;
        float suspiciousScore = 0.0f;
        
        try {
            // Check for virtual machine
            VMDetectionResult vmResult = detectVirtualMachine(fingerprint);
            isVM = vmResult.isVirtualMachine;
            indicators.addAll(vmResult.indicators);
            suspiciousScore += vmResult.confidence * 0.3f;
            
            // Check for sandbox environment
            SandboxDetectionResult sandboxResult = detectSandbox();
            isSandbox = sandboxResult.isSandbox;
            indicators.addAll(sandboxResult.indicators);
            suspiciousScore += sandboxResult.confidence * 0.25f;
            
            // Check for debuggers
            DebuggerDetectionResult debuggerResult = detectDebuggers();
            hasDebugger = debuggerResult.hasDebugger;
            indicators.addAll(debuggerResult.indicators);
            suspiciousScore += debuggerResult.confidence * 0.2f;
            
            // Check for analysis tools
            AnalysisToolsResult analysisResult = detectAnalysisTools();
            hasAnalysisTools = analysisResult.hasAnalysisTools;
            indicators.addAll(analysisResult.indicators);
            suspiciousScore += analysisResult.confidence * 0.25f;
            
            // Additional system checks
            suspiciousScore += performAdditionalChecks(indicators);
            
        } catch (Exception e) {
            indicators.add("Validation error: " + e.getMessage());
            suspiciousScore += 0.1f;
        }
        
        // Normalize suspicious score
        suspiciousScore = Math.min(1.0f, suspiciousScore);
        
        ValidationResult result = new ValidationResult(isVM, isSandbox, hasDebugger, 
                                                     hasAnalysisTools, suspiciousScore, indicators);
        
        // Cache the result
        validationCache.put(cacheKey, result);
        
        return result;
    }
    
    /**
     * Detect virtual machine environment
     */
    private VMDetectionResult detectVirtualMachine(HardwareFingerprint fingerprint) {
        List<String> indicators = new ArrayList<>();
        boolean isVM = false;
        float confidence = 0.0f;
        
        // Check hardware fingerprint for VM indicators
        if (fingerprint != null && fingerprint.isVirtualMachine()) {
            isVM = true;
            confidence += 0.8f;
            indicators.add("Hardware fingerprint indicates VM");
        }
        
        // Check running processes
        VMProcessCheck processCheck = checkVMProcesses();
        if (processCheck.detected) {
            isVM = true;
            confidence += 0.6f;
            indicators.addAll(processCheck.indicators);
        }
        
        // Check system files
        VMFileCheck fileCheck = checkVMFiles();
        if (fileCheck.detected) {
            isVM = true;
            confidence += 0.4f;
            indicators.addAll(fileCheck.indicators);
        }
        
        // Check registry (Windows only)
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            VMRegistryCheck registryCheck = checkVMRegistry();
            if (registryCheck.detected) {
                isVM = true;
                confidence += 0.5f;
                indicators.addAll(registryCheck.indicators);
            }
        }
        
        // Check system properties
        VMPropertyCheck propertyCheck = checkVMProperties();
        if (propertyCheck.detected) {
            confidence += 0.3f;
            indicators.addAll(propertyCheck.indicators);
        }
        
        confidence = Math.min(1.0f, confidence);
        return new VMDetectionResult(isVM, confidence, indicators);
    }
    
    /**
     * Check for VM-related processes
     */
    private VMProcessCheck checkVMProcesses() {
        List<String> indicators = new ArrayList<>();
        boolean detected = false;
        
        try {
            String processes = getRunningProcesses();
            String processesLower = processes.toLowerCase();
            
            for (String pattern : VM_PROCESS_PATTERNS) {
                if (processesLower.contains(pattern)) {
                    detected = true;
                    indicators.add("VM process detected: " + pattern);
                }
            }
        } catch (Exception e) {
            indicators.add("Process check failed: " + e.getMessage());
        }
        
        return new VMProcessCheck(detected, indicators);
    }
    
    /**
     * Check for VM-related files
     */
    private VMFileCheck checkVMFiles() {
        List<String> indicators = new ArrayList<>();
        boolean detected = false;
        
        String[] checkPaths = {
            "C:\\Program Files\\VMware",
            "C:\\Program Files\\Oracle\\VirtualBox",
            "C:\\Windows\\System32\\drivers\\vmmouse.sys",
            "C:\\Windows\\System32\\drivers\\vmhgfs.sys",
            "/usr/bin/vmware-toolbox-cmd",
            "/usr/bin/VBoxClient"
        };
        
        for (String path : checkPaths) {
            File file = new File(path);
            if (file.exists()) {
                detected = true;
                indicators.add("VM file detected: " + path);
            }
        }
        
        return new VMFileCheck(detected, indicators);
    }
    
    /**
     * Check Windows registry for VM indicators
     */
    private VMRegistryCheck checkVMRegistry() {
        List<String> indicators = new ArrayList<>();
        boolean detected = false;
        
        try {
            // Check for VM-related registry keys
            String[] registryChecks = {
                "reg query \"HKLM\\SOFTWARE\\VMware, Inc.\\VMware Tools\"",
                "reg query \"HKLM\\SOFTWARE\\Oracle\\VirtualBox Guest Additions\"",
                "reg query \"HKLM\\SYSTEM\\ControlSet001\\Services\\VBoxGuest\""
            };
            
            for (String command : registryChecks) {
                String result = executeCommand(command);
                if (!result.isEmpty() && !result.contains("ERROR")) {
                    detected = true;
                    indicators.add("VM registry key found");
                }
            }
        } catch (Exception e) {
            indicators.add("Registry check failed: " + e.getMessage());
        }
        
        return new VMRegistryCheck(detected, indicators);
    }
    
    /**
     * Check system properties for VM indicators
     */
    private VMPropertyCheck checkVMProperties() {
        List<String> indicators = new ArrayList<>();
        boolean detected = false;
        
        // Check Java system properties
        String osName = System.getProperty("os.name", "").toLowerCase();
        String osVersion = System.getProperty("os.version", "").toLowerCase();
        String javaVendor = System.getProperty("java.vendor", "").toLowerCase();
        
        if (osName.contains("virtual") || osVersion.contains("virtual")) {
            detected = true;
            indicators.add("Virtual OS detected in system properties");
        }
        
        // Check environment variables
        Map<String, String> env = System.getenv();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String key = entry.getKey().toLowerCase();
            String value = entry.getValue().toLowerCase();
            
            if (key.contains("vm") || key.contains("virtual") || 
                value.contains("vmware") || value.contains("virtualbox")) {
                detected = true;
                indicators.add("VM environment variable: " + key);
            }
        }
        
        return new VMPropertyCheck(detected, indicators);
    }
    
    /**
     * Detect sandbox environment
     */
    private SandboxDetectionResult detectSandbox() {
        List<String> indicators = new ArrayList<>();
        boolean isSandbox = false;
        float confidence = 0.0f;
        
        // Check for sandbox-specific indicators
        String hostname = System.getProperty("user.name", "").toLowerCase();
        String computerName = System.getenv("COMPUTERNAME");
        if (computerName != null) {
            computerName = computerName.toLowerCase();
        }
        
        for (String indicator : SANDBOX_INDICATORS) {
            if (hostname.contains(indicator) || 
                (computerName != null && computerName.contains(indicator))) {
                isSandbox = true;
                confidence += 0.3f;
                indicators.add("Sandbox indicator in hostname/computer name: " + indicator);
            }
        }
        
        // Check for limited resources (common in sandboxes)
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        int processors = runtime.availableProcessors();
        
        if (maxMemory < 2L * 1024 * 1024 * 1024 && processors <= 2) { // < 2GB RAM and <= 2 cores
            confidence += 0.2f;
            indicators.add("Limited system resources detected");
        }
        
        // Check system uptime (sandboxes often have very low uptime)
        long uptime = getSystemUptime();
        if (uptime > 0 && uptime < 300000) { // Less than 5 minutes
            confidence += 0.3f;
            indicators.add("Very low system uptime: " + uptime + "ms");
        }
        
        confidence = Math.min(1.0f, confidence);
        return new SandboxDetectionResult(isSandbox, confidence, indicators);
    }
    
    /**
     * Detect debuggers
     */
    private DebuggerDetectionResult detectDebuggers() {
        List<String> indicators = new ArrayList<>();
        boolean hasDebugger = false;
        float confidence = 0.0f;
        
        try {
            // Check for debugger processes
            String processes = getRunningProcesses();
            String processesLower = processes.toLowerCase();
            
            String[] debuggerPatterns = {
                "ollydbg", "x64dbg", "x32dbg", "ida", "ghidra", "windbg", "gdb"
            };
            
            for (String pattern : debuggerPatterns) {
                if (processesLower.contains(pattern)) {
                    hasDebugger = true;
                    confidence += 0.4f;
                    indicators.add("Debugger process detected: " + pattern);
                }
            }
            
            // Check for debugger detection using Java management
            if (java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toString().contains("jdwp")) {
                hasDebugger = true;
                confidence += 0.6f;
                indicators.add("Java debugger detected");
            }
            
        } catch (Exception e) {
            indicators.add("Debugger check failed: " + e.getMessage());
        }
        
        confidence = Math.min(1.0f, confidence);
        return new DebuggerDetectionResult(hasDebugger, confidence, indicators);
    }
    
    /**
     * Detect analysis tools
     */
    private AnalysisToolsResult detectAnalysisTools() {
        List<String> indicators = new ArrayList<>();
        boolean hasAnalysisTools = false;
        float confidence = 0.0f;
        
        try {
            String processes = getRunningProcesses();
            String processesLower = processes.toLowerCase();
            
            for (String tool : ANALYSIS_TOOLS) {
                if (processesLower.contains(tool.replace(" ", ""))) {
                    hasAnalysisTools = true;
                    confidence += 0.3f;
                    indicators.add("Analysis tool detected: " + tool);
                }
            }
        } catch (Exception e) {
            indicators.add("Analysis tools check failed: " + e.getMessage());
        }
        
        confidence = Math.min(1.0f, confidence);
        return new AnalysisToolsResult(hasAnalysisTools, confidence, indicators);
    }
    
    /**
     * Perform additional system checks
     */
    private float performAdditionalChecks(List<String> indicators) {
        float suspiciousScore = 0.0f;
        
        try {
            // Check for unusual system configuration
            String osArch = System.getProperty("os.arch");
            String javaVersion = System.getProperty("java.version");
            
            // Check for development/testing environments
            String userDir = System.getProperty("user.dir", "").toLowerCase();
            if (userDir.contains("test") || userDir.contains("debug") || userDir.contains("dev")) {
                suspiciousScore += 0.1f;
                indicators.add("Development/test environment detected");
            }
            
            // Check for unusual Java properties
            if (System.getProperty("java.awt.headless", "false").equals("true")) {
                suspiciousScore += 0.1f;
                indicators.add("Headless Java environment");
            }
            
        } catch (Exception e) {
            indicators.add("Additional checks failed: " + e.getMessage());
        }
        
        return suspiciousScore;
    }
    
    /**
     * Get running processes
     */
    private String getRunningProcesses() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String command;
            
            if (os.contains("windows")) {
                command = "tasklist /fo csv";
            } else if (os.contains("linux") || os.contains("mac")) {
                command = "ps aux";
            } else {
                return "";
            }
            
            return executeCommand(command);
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Get system uptime
     */
    private long getSystemUptime() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            
            if (os.contains("windows")) {
                String result = executeCommand("wmic os get LastBootUpTime /value");
                // Parse Windows boot time and calculate uptime
                return System.currentTimeMillis(); // Placeholder
            } else if (os.contains("linux")) {
                String result = executeCommand("cat /proc/uptime");
                String[] parts = result.trim().split("\\s+");
                if (parts.length > 0) {
                    return (long)(Float.parseFloat(parts[0]) * 1000);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return -1L;
    }
    
    /**
     * Execute system command
     */
    private String executeCommand(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                pb.command("cmd", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            
            Process process = pb.start();
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "";
            }
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            return output.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Clean up validation cache
     */
    public void cleanup() {
        validationCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    // Helper classes for detection results
    private static class VMDetectionResult {
        final boolean isVirtualMachine;
        final float confidence;
        final List<String> indicators;
        
        VMDetectionResult(boolean isVirtualMachine, float confidence, List<String> indicators) {
            this.isVirtualMachine = isVirtualMachine;
            this.confidence = confidence;
            this.indicators = indicators;
        }
    }
    
    private static class VMProcessCheck {
        final boolean detected;
        final List<String> indicators;
        
        VMProcessCheck(boolean detected, List<String> indicators) {
            this.detected = detected;
            this.indicators = indicators;
        }
    }
    
    private static class VMFileCheck {
        final boolean detected;
        final List<String> indicators;
        
        VMFileCheck(boolean detected, List<String> indicators) {
            this.detected = detected;
            this.indicators = indicators;
        }
    }
    
    private static class VMRegistryCheck {
        final boolean detected;
        final List<String> indicators;
        
        VMRegistryCheck(boolean detected, List<String> indicators) {
            this.detected = detected;
            this.indicators = indicators;
        }
    }
    
    private static class VMPropertyCheck {
        final boolean detected;
        final List<String> indicators;
        
        VMPropertyCheck(boolean detected, List<String> indicators) {
            this.detected = detected;
            this.indicators = indicators;
        }
    }
    
    private static class SandboxDetectionResult {
        final boolean isSandbox;
        final float confidence;
        final List<String> indicators;
        
        SandboxDetectionResult(boolean isSandbox, float confidence, List<String> indicators) {
            this.isSandbox = isSandbox;
            this.confidence = confidence;
            this.indicators = indicators;
        }
    }
    
    private static class DebuggerDetectionResult {
        final boolean hasDebugger;
        final float confidence;
        final List<String> indicators;
        
        DebuggerDetectionResult(boolean hasDebugger, float confidence, List<String> indicators) {
            this.hasDebugger = hasDebugger;
            this.confidence = confidence;
            this.indicators = indicators;
        }
    }
    
    private static class AnalysisToolsResult {
        final boolean hasAnalysisTools;
        final float confidence;
        final List<String> indicators;
        
        AnalysisToolsResult(boolean hasAnalysisTools, float confidence, List<String> indicators) {
            this.hasAnalysisTools = hasAnalysisTools;
            this.confidence = confidence;
            this.indicators = indicators;
        }
    }
}