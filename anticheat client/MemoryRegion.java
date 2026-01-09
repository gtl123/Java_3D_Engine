package fps.anticheat.client;

/**
 * Represents a memory region being monitored for integrity.
 * Contains region metadata, protection information, and monitoring state.
 */
public class MemoryRegion {
    
    private final String name;
    private final String description;
    private final long startAddress;
    private final long size;
    private final boolean executable;
    private final long creationTime;
    
    private boolean monitored;
    private String originalHash;
    private String currentHash;
    private long lastCheckTime;
    private int violationCount;
    private boolean readOnly;
    private String protectionFlags;
    private String moduleOwner;
    
    /**
     * Create a memory region
     */
    public MemoryRegion(String name, String description, long startAddress, long size, boolean executable) {
        this.name = name;
        this.description = description;
        this.startAddress = startAddress;
        this.size = size;
        this.executable = executable;
        this.creationTime = System.currentTimeMillis();
        this.monitored = true;
        this.violationCount = 0;
        this.readOnly = false;
        this.lastCheckTime = 0;
    }
    
    /**
     * Create a memory region with extended information
     */
    public MemoryRegion(String name, String description, long startAddress, long size, 
                       boolean executable, boolean readOnly, String protectionFlags, String moduleOwner) {
        this(name, description, startAddress, size, executable);
        this.readOnly = readOnly;
        this.protectionFlags = protectionFlags;
        this.moduleOwner = moduleOwner;
    }
    
    /**
     * Get end address of the region
     */
    public long getEndAddress() {
        return startAddress + size;
    }
    
    /**
     * Check if an address is within this region
     */
    public boolean containsAddress(long address) {
        return address >= startAddress && address < getEndAddress();
    }
    
    /**
     * Check if this region overlaps with another region
     */
    public boolean overlapsWith(MemoryRegion other) {
        if (other == null) return false;
        
        return !(getEndAddress() <= other.getStartAddress() || 
                 other.getEndAddress() <= getStartAddress());
    }
    
    /**
     * Get region age in milliseconds
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
     * Check if region is critical (executable or system-related)
     */
    public boolean isCritical() {
        return executable || 
               name.toLowerCase().contains("system") ||
               name.toLowerCase().contains("kernel") ||
               name.toLowerCase().contains("anti") ||
               name.toLowerCase().contains("security");
    }
    
    /**
     * Check if region is suspicious based on characteristics
     */
    public boolean isSuspicious() {
        // Check for suspicious naming patterns
        String lowerName = name.toLowerCase();
        String[] suspiciousKeywords = {
            "temp", "tmp", "inject", "hook", "patch", "cheat", "hack", "bot"
        };
        
        for (String keyword : suspiciousKeywords) {
            if (lowerName.contains(keyword)) {
                return true;
            }
        }
        
        // Check for suspicious characteristics
        if (executable && !readOnly) {
            return true; // Writable executable memory is suspicious
        }
        
        // Check for unusual size patterns
        if (size > 0 && (size < 4096 || size % 4096 != 0)) {
            return true; // Non-page-aligned regions can be suspicious
        }
        
        return false;
    }
    
    /**
     * Get protection level (0-10, higher is more protected)
     */
    public int getProtectionLevel() {
        int level = 0;
        
        if (readOnly) level += 3;
        if (!executable) level += 2;
        if (isCritical()) level += 3;
        if (monitored) level += 2;
        
        return Math.min(10, level);
    }
    
    /**
     * Get region type based on characteristics
     */
    public String getRegionType() {
        if (executable) {
            if (readOnly) {
                return "CODE_READONLY";
            } else {
                return "CODE_WRITABLE";
            }
        } else {
            if (readOnly) {
                return "DATA_READONLY";
            } else {
                return "DATA_WRITABLE";
            }
        }
    }
    
    /**
     * Create a signature for this region
     */
    public String getSignature() {
        StringBuilder signature = new StringBuilder();
        signature.append(name).append("|");
        signature.append(Long.toHexString(startAddress)).append("|");
        signature.append(size).append("|");
        signature.append(executable ? "X" : "-");
        signature.append(readOnly ? "R" : "W");
        
        if (protectionFlags != null) {
            signature.append("|").append(protectionFlags);
        }
        
        return signature.toString();
    }
    
    /**
     * Check if region has been modified (based on hash comparison)
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
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        MemoryRegion that = (MemoryRegion) obj;
        return startAddress == that.startAddress &&
               size == that.size &&
               name.equals(that.name);
    }
    
    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + Long.hashCode(startAddress);
        result = 31 * result + Long.hashCode(size);
        return result;
    }
    
    @Override
    public String toString() {
        return String.format("MemoryRegion{name='%s', start=0x%x, size=%d, executable=%s, readOnly=%s, violations=%d}",
                           name, startAddress, size, executable, readOnly, violationCount);
    }
    
    /**
     * Create a detailed string representation
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Memory Region Details:\n");
        sb.append("  Name: ").append(name).append("\n");
        sb.append("  Description: ").append(description).append("\n");
        sb.append("  Start Address: 0x").append(Long.toHexString(startAddress)).append("\n");
        sb.append("  End Address: 0x").append(Long.toHexString(getEndAddress())).append("\n");
        sb.append("  Size: ").append(size).append(" bytes (").append(size / 1024).append(" KB)\n");
        sb.append("  Type: ").append(getRegionType()).append("\n");
        sb.append("  Executable: ").append(executable).append("\n");
        sb.append("  Read Only: ").append(readOnly).append("\n");
        sb.append("  Monitored: ").append(monitored).append("\n");
        sb.append("  Critical: ").append(isCritical()).append("\n");
        sb.append("  Suspicious: ").append(isSuspicious()).append("\n");
        sb.append("  Protection Level: ").append(getProtectionLevel()).append("/10\n");
        
        if (protectionFlags != null) {
            sb.append("  Protection Flags: ").append(protectionFlags).append("\n");
        }
        
        if (moduleOwner != null) {
            sb.append("  Module Owner: ").append(moduleOwner).append("\n");
        }
        
        sb.append("  Creation Time: ").append(new java.util.Date(creationTime)).append("\n");
        sb.append("  Age: ").append(getAge()).append(" ms\n");
        sb.append("  Last Check: ");
        
        if (lastCheckTime > 0) {
            sb.append(new java.util.Date(lastCheckTime)).append(" (").append(getTimeSinceLastCheck()).append(" ms ago)\n");
        } else {
            sb.append("Never\n");
        }
        
        sb.append("  Violation Count: ").append(violationCount).append("\n");
        sb.append("  Modification Status: ").append(getModificationStatus()).append("\n");
        
        if (originalHash != null) {
            sb.append("  Original Hash: ").append(originalHash.substring(0, Math.min(16, originalHash.length()))).append("...\n");
        }
        
        if (currentHash != null) {
            sb.append("  Current Hash: ").append(currentHash.substring(0, Math.min(16, currentHash.length()))).append("...\n");
        }
        
        return sb.toString();
    }
    
    // Getters and setters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public long getStartAddress() { return startAddress; }
    public long getSize() { return size; }
    public boolean isExecutable() { return executable; }
    public long getCreationTime() { return creationTime; }
    public boolean isMonitored() { return monitored; }
    public void setMonitored(boolean monitored) { this.monitored = monitored; }
    public String getOriginalHash() { return originalHash; }
    public void setOriginalHash(String originalHash) { this.originalHash = originalHash; }
    public String getCurrentHash() { return currentHash; }
    public void setCurrentHash(String currentHash) { this.currentHash = currentHash; }
    public long getLastCheckTime() { return lastCheckTime; }
    public int getViolationCount() { return violationCount; }
    public void setViolationCount(int violationCount) { this.violationCount = violationCount; }
    public boolean isReadOnly() { return readOnly; }
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }
    public String getProtectionFlags() { return protectionFlags; }
    public void setProtectionFlags(String protectionFlags) { this.protectionFlags = protectionFlags; }
    public String getModuleOwner() { return moduleOwner; }
    public void setModuleOwner(String moduleOwner) { this.moduleOwner = moduleOwner; }
}