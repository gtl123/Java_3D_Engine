package fps.anticheat.hardware;

/**
 * Represents a GPU device in the system.
 * Contains GPU identification and characteristics for fingerprinting.
 */
public class GPUInfo {
    
    private final String model;
    private final String vendor;
    private final String deviceId;
    private final long memory; // Memory in bytes
    private final String architecture;
    private final boolean isPrimary;
    
    private String driverVersion;
    private String biosVersion;
    private int coreCount;
    private long coreClock;
    private long memoryClock;
    private float temperature;
    private float powerUsage;
    private String busType; // PCIe, AGP, etc.
    private int busWidth;
    
    public GPUInfo(String model, String vendor, String deviceId, long memory) {
        this.model = model != null ? model : "Unknown";
        this.vendor = vendor != null ? vendor : "Unknown";
        this.deviceId = deviceId != null ? deviceId : "Unknown";
        this.memory = memory;
        this.architecture = "Unknown";
        this.isPrimary = false;
        this.temperature = -1.0f;
        this.powerUsage = -1.0f;
        this.busType = "Unknown";
    }
    
    public GPUInfo(String model, String vendor, String deviceId, long memory, 
                   String architecture, boolean isPrimary) {
        this.model = model != null ? model : "Unknown";
        this.vendor = vendor != null ? vendor : "Unknown";
        this.deviceId = deviceId != null ? deviceId : "Unknown";
        this.memory = memory;
        this.architecture = architecture != null ? architecture : "Unknown";
        this.isPrimary = isPrimary;
        this.temperature = -1.0f;
        this.powerUsage = -1.0f;
        this.busType = "Unknown";
    }
    
    /**
     * Get GPU memory in human-readable format
     */
    public String getFormattedMemory() {
        if (memory <= 0) return "Unknown";
        
        if (memory >= 1024 * 1024 * 1024) {
            return String.format("%.1f GB", memory / (1024.0 * 1024.0 * 1024.0));
        } else if (memory >= 1024 * 1024) {
            return String.format("%.0f MB", memory / (1024.0 * 1024.0));
        } else {
            return String.format("%d KB", memory / 1024);
        }
    }
    
    /**
     * Check if this is an NVIDIA GPU
     */
    public boolean isNvidia() {
        return vendor.toUpperCase().contains("NVIDIA") || 
               model.toUpperCase().contains("GEFORCE") ||
               model.toUpperCase().contains("QUADRO") ||
               model.toUpperCase().contains("TESLA");
    }
    
    /**
     * Check if this is an AMD GPU
     */
    public boolean isAMD() {
        return vendor.toUpperCase().contains("AMD") || 
               vendor.toUpperCase().contains("ATI") ||
               model.toUpperCase().contains("RADEON") ||
               model.toUpperCase().contains("RYZEN");
    }
    
    /**
     * Check if this is an Intel GPU
     */
    public boolean isIntel() {
        return vendor.toUpperCase().contains("INTEL") ||
               model.toUpperCase().contains("INTEL") ||
               model.toUpperCase().contains("HD GRAPHICS") ||
               model.toUpperCase().contains("UHD GRAPHICS");
    }
    
    /**
     * Check if this is an integrated GPU
     */
    public boolean isIntegrated() {
        return model.toUpperCase().contains("INTEGRATED") ||
               model.toUpperCase().contains("HD GRAPHICS") ||
               model.toUpperCase().contains("UHD GRAPHICS") ||
               model.toUpperCase().contains("VEGA") ||
               (isIntel() && !model.toUpperCase().contains("ARC"));
    }
    
    /**
     * Check if this is a discrete/dedicated GPU
     */
    public boolean isDiscrete() {
        return !isIntegrated() && memory > 512 * 1024 * 1024; // > 512MB
    }
    
    /**
     * Check if GPU has valid identification
     */
    public boolean hasValidIdentification() {
        return !model.equals("Unknown") && 
               !vendor.equals("Unknown") && 
               !deviceId.equals("Unknown") &&
               memory > 0;
    }
    
    /**
     * Get GPU fingerprint for identification
     */
    public String getDeviceFingerprint() {
        StringBuilder fingerprint = new StringBuilder();
        fingerprint.append(model);
        fingerprint.append("|");
        fingerprint.append(vendor);
        fingerprint.append("|");
        fingerprint.append(deviceId);
        fingerprint.append("|");
        fingerprint.append(memory);
        fingerprint.append("|");
        fingerprint.append(architecture);
        
        return fingerprint.toString();
    }
    
    /**
     * Check if this GPU is suitable for fingerprinting
     */
    public boolean isSuitableForFingerprinting() {
        return hasValidIdentification() && 
               (isDiscrete() || isPrimary) &&
               memory > 128 * 1024 * 1024; // At least 128MB
    }
    
    /**
     * Get GPU priority for fingerprinting (higher = more important)
     */
    public int getFingerprintPriority() {
        int priority = 0;
        
        // Primary GPU gets highest priority
        if (isPrimary) priority += 100;
        
        // Discrete GPUs get higher priority than integrated
        if (isDiscrete()) priority += 50;
        else if (isIntegrated()) priority += 20;
        
        // More memory = higher priority
        if (memory > 8L * 1024 * 1024 * 1024) priority += 40; // > 8GB
        else if (memory > 4L * 1024 * 1024 * 1024) priority += 30; // > 4GB
        else if (memory > 2L * 1024 * 1024 * 1024) priority += 20; // > 2GB
        else if (memory > 1L * 1024 * 1024 * 1024) priority += 10; // > 1GB
        
        // Valid identification increases priority
        if (hasValidIdentification()) priority += 25;
        
        // Known vendors get slight priority boost
        if (isNvidia() || isAMD()) priority += 10;
        else if (isIntel()) priority += 5;
        
        return priority;
    }
    
    /**
     * Get GPU performance tier estimate
     */
    public String getPerformanceTier() {
        if (!isDiscrete()) {
            return "Integrated";
        }
        
        // Rough performance estimation based on memory and model name
        if (memory >= 16L * 1024 * 1024 * 1024) { // >= 16GB
            return "Enthusiast";
        } else if (memory >= 8L * 1024 * 1024 * 1024) { // >= 8GB
            if (model.toUpperCase().contains("RTX 40") || 
                model.toUpperCase().contains("RTX 30") ||
                model.toUpperCase().contains("RX 7") ||
                model.toUpperCase().contains("RX 6")) {
                return "High-End";
            }
            return "Mid-High";
        } else if (memory >= 4L * 1024 * 1024 * 1024) { // >= 4GB
            return "Mid-Range";
        } else if (memory >= 2L * 1024 * 1024 * 1024) { // >= 2GB
            return "Entry-Level";
        } else {
            return "Low-End";
        }
    }
    
    /**
     * Check if GPU supports hardware-accelerated features that might be used by cheats
     */
    public boolean supportsComputeShaders() {
        // Modern GPUs generally support compute shaders
        return isDiscrete() && memory > 1024 * 1024 * 1024; // > 1GB
    }
    
    @Override
    public String toString() {
        return String.format("GPUInfo{model='%s', vendor='%s', memory=%s, tier='%s', primary=%s}", 
                           model, vendor, getFormattedMemory(), getPerformanceTier(), isPrimary);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        GPUInfo that = (GPUInfo) obj;
        return model.equals(that.model) && 
               vendor.equals(that.vendor) && 
               deviceId.equals(that.deviceId) &&
               memory == that.memory;
    }
    
    @Override
    public int hashCode() {
        return model.hashCode() + vendor.hashCode() + deviceId.hashCode() + Long.hashCode(memory);
    }
    
    // Getters and setters
    public String getModel() { return model; }
    public String getVendor() { return vendor; }
    public String getDeviceId() { return deviceId; }
    public long getMemory() { return memory; }
    public String getArchitecture() { return architecture; }
    public boolean isPrimary() { return isPrimary; }
    
    public String getDriverVersion() { return driverVersion; }
    public void setDriverVersion(String driverVersion) { this.driverVersion = driverVersion; }
    
    public String getBiosVersion() { return biosVersion; }
    public void setBiosVersion(String biosVersion) { this.biosVersion = biosVersion; }
    
    public int getCoreCount() { return coreCount; }
    public void setCoreCount(int coreCount) { this.coreCount = coreCount; }
    
    public long getCoreClock() { return coreClock; }
    public void setCoreClock(long coreClock) { this.coreClock = coreClock; }
    
    public long getMemoryClock() { return memoryClock; }
    public void setMemoryClock(long memoryClock) { this.memoryClock = memoryClock; }
    
    public float getTemperature() { return temperature; }
    public void setTemperature(float temperature) { this.temperature = temperature; }
    
    public float getPowerUsage() { return powerUsage; }
    public void setPowerUsage(float powerUsage) { this.powerUsage = powerUsage; }
    
    public String getBusType() { return busType; }
    public void setBusType(String busType) { this.busType = busType; }
    
    public int getBusWidth() { return busWidth; }
    public void setBusWidth(int busWidth) { this.busWidth = busWidth; }
}