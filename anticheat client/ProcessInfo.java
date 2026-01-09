package fps.anticheat.client;

/**
 * Represents information about a running process.
 * Contains process identification, metadata, and timing information.
 */
public class ProcessInfo {
    
    private final int pid;
    private final String name;
    private final String commandLine;
    private final long detectionTime;
    private String path;
    private long memoryUsage;
    private long cpuUsage;
    private String parentProcess;
    private int parentPid;
    private String user;
    private long startTime;
    
    /**
     * Create process info with basic information
     */
    public ProcessInfo(int pid, String name, String commandLine) {
        this.pid = pid;
        this.name = name != null ? name.toLowerCase() : "";
        this.commandLine = commandLine != null ? commandLine : "";
        this.detectionTime = System.currentTimeMillis();
        this.memoryUsage = 0;
        this.cpuUsage = 0;
        this.parentPid = -1;
        this.startTime = System.currentTimeMillis();
    }
    
    /**
     * Create process info with extended information
     */
    public ProcessInfo(int pid, String name, String commandLine, String path, 
                      long memoryUsage, long cpuUsage, String parentProcess, 
                      int parentPid, String user, long startTime) {
        this.pid = pid;
        this.name = name != null ? name.toLowerCase() : "";
        this.commandLine = commandLine != null ? commandLine : "";
        this.path = path;
        this.memoryUsage = memoryUsage;
        this.cpuUsage = cpuUsage;
        this.parentProcess = parentProcess;
        this.parentPid = parentPid;
        this.user = user;
        this.startTime = startTime;
        this.detectionTime = System.currentTimeMillis();
    }
    
    /**
     * Check if this process matches a pattern
     */
    public boolean matchesPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        
        // Check name match
        if (name.matches(pattern)) {
            return true;
        }
        
        // Check command line match
        if (commandLine.toLowerCase().matches(pattern)) {
            return true;
        }
        
        // Check path match
        if (path != null && path.toLowerCase().matches(pattern)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if this process is suspicious based on various criteria
     */
    public boolean isSuspicious() {
        // Check for suspicious naming patterns
        String lowerName = name.toLowerCase();
        
        // Suspicious keywords in process name
        String[] suspiciousKeywords = {
            "cheat", "hack", "bot", "trainer", "inject", "memory", 
            "speed", "wall", "aim", "trigger", "norecoil", "esp"
        };
        
        for (String keyword : suspiciousKeywords) {
            if (lowerName.contains(keyword)) {
                return true;
            }
        }
        
        // Check command line for suspicious arguments
        String lowerCommandLine = commandLine.toLowerCase();
        String[] suspiciousArgs = {
            "--inject", "--hook", "--attach", "--debug", "--memory",
            "-inject", "-hook", "-attach", "-debug", "-memory"
        };
        
        for (String arg : suspiciousArgs) {
            if (lowerCommandLine.contains(arg)) {
                return true;
            }
        }
        
        // Check for suspicious file paths
        if (path != null) {
            String lowerPath = path.toLowerCase();
            if (lowerPath.contains("temp") || lowerPath.contains("appdata\\local\\temp") ||
                lowerPath.contains("/tmp/") || lowerPath.contains("downloads")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get process age in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * Get detection age in milliseconds
     */
    public long getDetectionAge() {
        return System.currentTimeMillis() - detectionTime;
    }
    
    /**
     * Check if process is a system process
     */
    public boolean isSystemProcess() {
        String[] systemProcesses = {
            "system", "smss.exe", "csrss.exe", "wininit.exe", "winlogon.exe",
            "services.exe", "lsass.exe", "svchost.exe", "explorer.exe", "dwm.exe",
            "kernel", "init", "kthreadd", "systemd"
        };
        
        for (String sysProc : systemProcesses) {
            if (name.equals(sysProc)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get process signature for comparison
     */
    public String getSignature() {
        StringBuilder signature = new StringBuilder();
        signature.append(name);
        
        if (path != null && !path.isEmpty()) {
            signature.append("|").append(path);
        }
        
        // Include relevant command line arguments (excluding dynamic parts)
        if (commandLine != null && !commandLine.isEmpty()) {
            String cleanedCommandLine = commandLine.replaceAll("\\d+", "X"); // Replace numbers
            signature.append("|").append(cleanedCommandLine);
        }
        
        return signature.toString();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ProcessInfo that = (ProcessInfo) obj;
        return pid == that.pid && name.equals(that.name);
    }
    
    @Override
    public int hashCode() {
        return Integer.hashCode(pid) * 31 + name.hashCode();
    }
    
    @Override
    public String toString() {
        return String.format("ProcessInfo{pid=%d, name='%s', commandLine='%s', path='%s', memory=%d, cpu=%d}",
                           pid, name, commandLine, path, memoryUsage, cpuUsage);
    }
    
    /**
     * Create a detailed string representation
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Process Details:\n");
        sb.append("  PID: ").append(pid).append("\n");
        sb.append("  Name: ").append(name).append("\n");
        sb.append("  Command Line: ").append(commandLine).append("\n");
        
        if (path != null) {
            sb.append("  Path: ").append(path).append("\n");
        }
        
        sb.append("  Memory Usage: ").append(memoryUsage).append(" bytes\n");
        sb.append("  CPU Usage: ").append(cpuUsage).append("%\n");
        
        if (parentProcess != null) {
            sb.append("  Parent Process: ").append(parentProcess).append(" (PID: ").append(parentPid).append(")\n");
        }
        
        if (user != null) {
            sb.append("  User: ").append(user).append("\n");
        }
        
        sb.append("  Start Time: ").append(new java.util.Date(startTime)).append("\n");
        sb.append("  Detection Time: ").append(new java.util.Date(detectionTime)).append("\n");
        sb.append("  Age: ").append(getAge()).append(" ms\n");
        sb.append("  Is System Process: ").append(isSystemProcess()).append("\n");
        sb.append("  Is Suspicious: ").append(isSuspicious()).append("\n");
        
        return sb.toString();
    }
    
    // Getters and setters
    public int getPid() { return pid; }
    public String getName() { return name; }
    public String getCommandLine() { return commandLine; }
    public long getDetectionTime() { return detectionTime; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public long getMemoryUsage() { return memoryUsage; }
    public void setMemoryUsage(long memoryUsage) { this.memoryUsage = memoryUsage; }
    public long getCpuUsage() { return cpuUsage; }
    public void setCpuUsage(long cpuUsage) { this.cpuUsage = cpuUsage; }
    public String getParentProcess() { return parentProcess; }
    public void setParentProcess(String parentProcess) { this.parentProcess = parentProcess; }
    public int getParentPid() { return parentPid; }
    public void setParentPid(int parentPid) { this.parentPid = parentPid; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
}