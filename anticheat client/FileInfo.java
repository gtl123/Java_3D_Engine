package fps.anticheat.client;

/**
 * Represents information about a monitored file.
 * Contains file metadata, integrity hashes, and monitoring state.
 */
public class FileInfo {
    
    private final String name;
    private final String absolutePath;
    private final long creationTime;
    
    private long size;
    private long lastModified;
    private String originalHash;
    private String currentHash;
    private boolean critical;
    private boolean suspicious;
    private boolean monitored;
    private long lastCheckTime;
    private int violationCount;
    private String fileType;
    private boolean executable;
    private boolean readOnly;
    
    /**
     * Create file info with basic information
     */
    public FileInfo(String name, String absolutePath, long size, long lastModified) {
        this.name = name;
        this.absolutePath = absolutePath;
        this.size = size;
        this.lastModified = lastModified;
        this.creationTime = System.currentTimeMillis();
        this.critical = false;
        this.suspicious = false;
        this.monitored = true;
        this.violationCount = 0;
        this.lastCheckTime = 0;
        this.fileType = determineFileType(name);
        this.executable = isExecutableFile(name);
        this.readOnly = false;
    }
    
    /**
     * Create file info with extended information
     */
    public FileInfo(String name, String absolutePath, long size, long lastModified,
                   boolean critical, boolean suspicious, String originalHash) {
        this(name, absolutePath, size, lastModified);
        this.critical = critical;
        this.suspicious = suspicious;
        this.originalHash = originalHash;
        this.currentHash = originalHash;
    }
    
    /**
     * Determine file type based on extension
     */
    private String determineFileType(String fileName) {
        String lowerName = fileName.toLowerCase();
        
        if (lowerName.endsWith(".exe")) {
            return "EXECUTABLE";
        } else if (lowerName.endsWith(".dll")) {
            return "LIBRARY";
        } else if (lowerName.endsWith(".jar")) {
            return "JAVA_ARCHIVE";
        } else if (lowerName.endsWith(".cfg") || lowerName.endsWith(".ini") || lowerName.endsWith(".config")) {
            return "CONFIGURATION";
        } else if (lowerName.endsWith(".log")) {
            return "LOG";
        } else if (lowerName.endsWith(".txt")) {
            return "TEXT";
        } else if (lowerName.endsWith(".xml") || lowerName.endsWith(".json")) {
            return "DATA";
        } else if (lowerName.endsWith(".tmp") || lowerName.endsWith(".temp")) {
            return "TEMPORARY";
        } else {
            return "UNKNOWN";
        }
    }
    
    /**
     * Check if file is executable based on extension
     */
    private boolean isExecutableFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".exe") || lowerName.endsWith(".dll") || 
               lowerName.endsWith(".jar") || lowerName.endsWith(".bat") ||
               lowerName.endsWith(".cmd") || lowerName.endsWith(".sh");
    }
    
    /**
     * Get file age in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - creationTime;
    }
    
    /**
     * Get time since last check in milliseconds
     */
    public long getTimeSinceLastCheck() {
        if (lastCheckTime == 0) {
            return getAge();
        }
        return System.currentTimeMillis() - lastCheckTime;
    }
    
    /**
     * Update last check time
     */
    public void updateLastCheckTime() {
        this.lastCheckTime = System.currentTimeMillis();
    }
    
    /**
     * Increment violation count
     */
    public void incrementViolationCount() {
        this.violationCount++;
    }
    
    /**
     * Check if file has been modified (based on hash comparison)
     */
    public boolean isModified() {
        return originalHash != null && currentHash != null && 
               !originalHash.equals(currentHash);
    }
    
    /**
     * Get modification status
     */
    public String getModificationStatus() {
        if (originalHash == null) {
            return "NOT_HASHED";
        } else if (currentHash == null) {
            return "PENDING_CHECK";
        } else if (originalHash.equals(currentHash)) {
            return "UNMODIFIED";
        } else {
            return "MODIFIED";
        }
    }
    
    /**
     * Check if file is high risk
     */
    public boolean isHighRisk() {
        return (critical && (suspicious || isModified())) ||
               (executable && suspicious) ||
               violationCount > 0;
    }
    
    /**
     * Get risk level (0-10, higher is more risky)
     */
    public int getRiskLevel() {
        int risk = 0;
        
        if (critical) risk += 4;
        if (suspicious) risk += 3;
        if (executable) risk += 2;
        if (isModified()) risk += 3;
        if (violationCount > 0) risk += Math.min(3, violationCount);
        
        return Math.min(10, risk);
    }
    
    /**
     * Get file extension
     */
    public String getExtension() {
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0 && lastDot < name.length() - 1) {
            return name.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }
    
    /**
     * Get file size in human readable format
     */
    public String getFormattedSize() {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * Check if file matches a pattern
     */
    public boolean matchesPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        
        // Check name match
        if (name.toLowerCase().matches(pattern.toLowerCase())) {
            return true;
        }
        
        // Check path match
        if (absolutePath.toLowerCase().matches(pattern.toLowerCase())) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Create a signature for this file
     */
    public String getSignature() {
        StringBuilder signature = new StringBuilder();
        signature.append(name).append("|");
        signature.append(size).append("|");
        signature.append(fileType).append("|");
        
        if (originalHash != null) {
            signature.append(originalHash.substring(0, Math.min(16, originalHash.length())));
        }
        
        return signature.toString();
    }
    
    /**
     * Check if file should be monitored based on characteristics
     */
    public boolean shouldBeMonitored() {
        // Always monitor critical files
        if (critical) {
            return true;
        }
        
        // Monitor executable files
        if (executable) {
            return true;
        }
        
        // Monitor configuration files
        if (fileType.equals("CONFIGURATION")) {
            return true;
        }
        
        // Monitor suspicious files
        if (suspicious) {
            return true;
        }
        
        // Skip temporary files unless suspicious
        if (fileType.equals("TEMPORARY") && !suspicious) {
            return false;
        }
        
        // Skip log files unless critical
        if (fileType.equals("LOG") && !critical) {
            return false;
        }
        
        return true;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        FileInfo that = (FileInfo) obj;
        return absolutePath.equals(that.absolutePath);
    }
    
    @Override
    public int hashCode() {
        return absolutePath.hashCode();
    }
    
    @Override
    public String toString() {
        return String.format("FileInfo{name='%s', size=%s, type=%s, critical=%s, suspicious=%s, modified=%s, violations=%d}",
                           name, getFormattedSize(), fileType, critical, suspicious, isModified(), violationCount);
    }
    
    /**
     * Create a detailed string representation
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("File Details:\n");
        sb.append("  Name: ").append(name).append("\n");
        sb.append("  Path: ").append(absolutePath).append("\n");
        sb.append("  Size: ").append(getFormattedSize()).append(" (").append(size).append(" bytes)\n");
        sb.append("  Type: ").append(fileType).append("\n");
        sb.append("  Extension: ").append(getExtension()).append("\n");
        sb.append("  Executable: ").append(executable).append("\n");
        sb.append("  Read Only: ").append(readOnly).append("\n");
        sb.append("  Critical: ").append(critical).append("\n");
        sb.append("  Suspicious: ").append(suspicious).append("\n");
        sb.append("  Monitored: ").append(monitored).append("\n");
        sb.append("  Risk Level: ").append(getRiskLevel()).append("/10\n");
        sb.append("  Creation Time: ").append(new java.util.Date(creationTime)).append("\n");
        sb.append("  Last Modified: ").append(new java.util.Date(lastModified)).append("\n");
        sb.append("  Age: ").append(getAge()).append(" ms\n");
        
        if (lastCheckTime > 0) {
            sb.append("  Last Check: ").append(new java.util.Date(lastCheckTime)).append(" (").append(getTimeSinceLastCheck()).append(" ms ago)\n");
        } else {
            sb.append("  Last Check: Never\n");
        }
        
        sb.append("  Violation Count: ").append(violationCount).append("\n");
        sb.append("  Modification Status: ").append(getModificationStatus()).append("\n");
        
        if (originalHash != null) {
            sb.append("  Original Hash: ").append(originalHash.substring(0, Math.min(16, originalHash.length()))).append("...\n");
        }
        
        if (currentHash != null && !currentHash.equals(originalHash)) {
            sb.append("  Current Hash: ").append(currentHash.substring(0, Math.min(16, currentHash.length()))).append("...\n");
        }
        
        return sb.toString();
    }
    
    // Getters and setters
    public String getName() { return name; }
    public String getAbsolutePath() { return absolutePath; }
    public long getCreationTime() { return creationTime; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }
    public String getOriginalHash() { return originalHash; }
    public void setOriginalHash(String originalHash) { this.originalHash = originalHash; }
    public String getCurrentHash() { return currentHash; }
    public void setCurrentHash(String currentHash) { this.currentHash = currentHash; }
    public boolean isCritical() { return critical; }
    public void setCritical(boolean critical) { this.critical = critical; }
    public boolean isSuspicious() { return suspicious; }
    public void setSuspicious(boolean suspicious) { this.suspicious = suspicious; }
    public boolean isMonitored() { return monitored; }
    public void setMonitored(boolean monitored) { this.monitored = monitored; }
    public long getLastCheckTime() { return lastCheckTime; }
    public int getViolationCount() { return violationCount; }
    public void setViolationCount(int violationCount) { this.violationCount = violationCount; }
    public String getFileType() { return fileType; }
    public boolean isExecutable() { return executable; }
    public boolean isReadOnly() { return readOnly; }
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }
}