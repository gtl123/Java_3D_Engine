package fps.anticheat.security;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/**
 * Anti-debugging and tamper protection system.
 * Detects debuggers, profilers, and other reverse engineering tools.
 */
public class AntiDebuggingManager {
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final ScheduledExecutorService scheduler;
    private final Map<String, Integer> detectionCounts;
    private final List<AntiDebugCheck> antiDebugChecks;
    
    private volatile boolean debuggerDetected = false;
    private volatile boolean tamperingDetected = false;
    private volatile long lastCheckTime = 0;
    
    // Detection thresholds
    private static final int MAX_DETECTION_COUNT = 5;
    private static final long CHECK_INTERVAL = 5000; // 5 seconds
    
    public AntiDebuggingManager() {
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.detectionCounts = new ConcurrentHashMap<>();
        this.antiDebugChecks = new ArrayList<>();
        
        initializeAntiDebugChecks();
        startPeriodicChecks();
    }
    
    /**
     * Initialize anti-debugging checks
     */
    private void initializeAntiDebugChecks() {
        // JVM argument detection
        antiDebugChecks.add(new AntiDebugCheck("JVM_DEBUG_ARGS", this::checkJVMDebugArguments));
        
        // Debugger attachment detection
        antiDebugChecks.add(new AntiDebugCheck("DEBUGGER_ATTACHMENT", this::checkDebuggerAttachment));
        
        // Timing-based detection
        antiDebugChecks.add(new AntiDebugCheck("TIMING_ANALYSIS", this::checkTimingAnomalies));
        
        // Thread analysis
        antiDebugChecks.add(new AntiDebugCheck("THREAD_ANALYSIS", this::checkSuspiciousThreads));
        
        // Memory analysis
        antiDebugChecks.add(new AntiDebugCheck("MEMORY_ANALYSIS", this::checkMemoryAnomalies));
        
        // Class loader detection
        antiDebugChecks.add(new AntiDebugCheck("CLASSLOADER_ANALYSIS", this::checkClassLoaderAnomalies));
        
        // System property analysis
        antiDebugChecks.add(new AntiDebugCheck("SYSTEM_PROPERTIES", this::checkSystemProperties));
        
        // Stack trace analysis
        antiDebugChecks.add(new AntiDebugCheck("STACK_TRACE", this::checkStackTraceAnomalies));
    }
    
    /**
     * Start periodic anti-debugging checks
     */
    private void startPeriodicChecks() {
        scheduler.scheduleAtFixedRate(this::performAntiDebugChecks, 
                                    SECURE_RANDOM.nextInt(5000) + 1000, // Random initial delay
                                    CHECK_INTERVAL + SECURE_RANDOM.nextInt(2000), // Random interval
                                    TimeUnit.MILLISECONDS);
    }
    
    /**
     * Perform all anti-debugging checks
     */
    private void performAntiDebugChecks() {
        lastCheckTime = System.currentTimeMillis();
        
        for (AntiDebugCheck check : antiDebugChecks) {
            try {
                boolean detected = check.getCheckFunction().call();
                if (detected) {
                    handleDetection(check.getName());
                }
            } catch (Exception e) {
                // Ignore exceptions in checks to avoid revealing detection methods
            }
        }
    }
    
    /**
     * Check for JVM debug arguments
     */
    private boolean checkJVMDebugArguments() {
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        List<String> jvmArgs = runtimeBean.getInputArguments();
        
        for (String arg : jvmArgs) {
            String lowerArg = arg.toLowerCase();
            if (lowerArg.contains("jdwp") || 
                lowerArg.contains("debug") || 
                lowerArg.contains("agentlib") ||
                lowerArg.contains("xrunjdwp") ||
                lowerArg.contains("javaagent")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check for debugger attachment
     */
    private boolean checkDebuggerAttachment() {
        // Check if management interface indicates debugging
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        
        // Debuggers often change the JVM name or add specific properties
        String vmName = runtimeBean.getVmName();
        if (vmName != null && vmName.toLowerCase().contains("debug")) {
            return true;
        }
        
        // Check for common debugger system properties
        String[] debugProperties = {
            "java.compiler",
            "sun.management.compiler",
            "com.sun.management.jmxremote"
        };
        
        for (String property : debugProperties) {
            String value = System.getProperty(property);
            if (value != null && value.toLowerCase().contains("debug")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check for timing anomalies that indicate debugging
     */
    private boolean checkTimingAnomalies() {
        long startTime = System.nanoTime();
        
        // Perform a simple operation
        int dummy = 0;
        for (int i = 0; i < 1000; i++) {
            dummy += i * i;
        }
        
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        
        // If operation takes too long, might indicate stepping through debugger
        // Normal execution should be very fast (< 100 microseconds)
        return duration > 100000; // 100 microseconds in nanoseconds
    }
    
    /**
     * Check for suspicious threads
     */
    private boolean checkSuspiciousThreads() {
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        
        for (Thread thread : threads) {
            String threadName = thread.getName().toLowerCase();
            
            // Check for debugger-related thread names
            if (threadName.contains("jdwp") ||
                threadName.contains("debug") ||
                threadName.contains("profiler") ||
                threadName.contains("agent") ||
                threadName.contains("jdi")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check for memory anomalies
     */
    private boolean checkMemoryAnomalies() {
        Runtime runtime = Runtime.getRuntime();
        
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        
        // Debuggers and profilers often increase memory usage significantly
        double memoryUsageRatio = (double)(totalMemory - freeMemory) / maxMemory;
        
        // If memory usage is unusually high (>90%), might indicate debugging tools
        return memoryUsageRatio > 0.9;
    }
    
    /**
     * Check for class loader anomalies
     */
    private boolean checkClassLoaderAnomalies() {
        ClassLoader currentClassLoader = this.getClass().getClassLoader();
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        
        // Check if current class loader is different from system class loader
        // This might indicate agent injection or custom class loading
        if (currentClassLoader != systemClassLoader && 
            currentClassLoader != null && 
            !currentClassLoader.getClass().getName().startsWith("java.")) {
            
            String loaderName = currentClassLoader.getClass().getName().toLowerCase();
            if (loaderName.contains("agent") || 
                loaderName.contains("debug") || 
                loaderName.contains("profiler")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check system properties for debugging indicators
     */
    private boolean checkSystemProperties() {
        Properties props = System.getProperties();
        
        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            String lowerKey = key.toLowerCase();
            String lowerValue = value != null ? value.toLowerCase() : "";
            
            if (lowerKey.contains("debug") || 
                lowerKey.contains("jdwp") || 
                lowerKey.contains("agent") ||
                lowerValue.contains("debug") || 
                lowerValue.contains("jdwp")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check stack trace for debugging indicators
     */
    private boolean checkStackTraceAnomalies() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName().toLowerCase();
            String methodName = element.getMethodName().toLowerCase();
            
            if (className.contains("debug") || 
                className.contains("jdwp") || 
                className.contains("profiler") ||
                methodName.contains("debug") || 
                methodName.contains("breakpoint")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Handle detection of debugging/tampering
     */
    private void handleDetection(String checkName) {
        int count = detectionCounts.merge(checkName, 1, Integer::sum);
        
        if (count >= MAX_DETECTION_COUNT) {
            if (checkName.contains("DEBUG") || checkName.contains("TIMING")) {
                debuggerDetected = true;
            } else {
                tamperingDetected = true;
            }
            
            // Trigger security response
            triggerSecurityResponse(checkName, count);
        }
    }
    
    /**
     * Trigger security response to detected threats
     */
    private void triggerSecurityResponse(String checkName, int detectionCount) {
        // Log the detection (in a real implementation, this would be more sophisticated)
        System.err.println("SECURITY ALERT: " + checkName + " detected " + detectionCount + " times");
        
        // In a real implementation, this could:
        // 1. Terminate the application
        // 2. Send alert to server
        // 3. Corrupt anti-cheat data to make reverse engineering harder
        // 4. Enable additional security measures
        
        // For now, we'll just set flags that other systems can check
    }
    
    /**
     * Check if debugger is detected
     */
    public boolean isDebuggerDetected() {
        return debuggerDetected;
    }
    
    /**
     * Check if tampering is detected
     */
    public boolean isTamperingDetected() {
        return tamperingDetected;
    }
    
    /**
     * Check if any security threat is detected
     */
    public boolean isSecurityThreatDetected() {
        return debuggerDetected || tamperingDetected;
    }
    
    /**
     * Get total detection count across all checks
     */
    public int getDetectionCount() {
        return detectionCounts.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    /**
     * Get detection counts by check type
     */
    public Map<String, Integer> getDetectionCounts() {
        return new HashMap<>(detectionCounts);
    }
    
    /**
     * Reset detection state
     */
    public void resetDetectionState() {
        debuggerDetected = false;
        tamperingDetected = false;
        detectionCounts.clear();
    }
    
    /**
     * Shutdown the anti-debugging manager
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
     * Anti-debug check definition
     */
    private static class AntiDebugCheck {
        private final String name;
        private final Callable<Boolean> checkFunction;
        
        public AntiDebugCheck(String name, Callable<Boolean> checkFunction) {
            this.name = name;
            this.checkFunction = checkFunction;
        }
        
        public String getName() {
            return name;
        }
        
        public Callable<Boolean> getCheckFunction() {
            return checkFunction;
        }
    }
}