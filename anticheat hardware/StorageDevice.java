package fps.anticheat.hardware;

/**
 * Represents a storage device (HDD, SSD, etc.) in the system.
 * Contains device identification and characteristics for fingerprinting.
 */
public class StorageDevice {
    
    private final String model;
    private final String serial;
    private final String vendor;
    private final long size; // Size in bytes
    private final String type; // HDD, SSD, NVMe, etc.
    private final String interfaceType; // SATA, NVMe, USB, etc.
    private final boolean isRemovable;
    private final boolean isSystemDrive;
    
    private String firmwareVersion;
    private long sectorsCount;
    private int sectorSize;
    private String healthStatus;
    private float temperature;
    
    public StorageDevice(String model, String serial, String vendor, long size, String type) {
        this.model = model != null ? model : "Unknown";
        this.serial = serial != null ? serial : "Unknown";
        this.vendor = vendor != null ? vendor : "Unknown";
        this.size = size;
        this.type = type != null ? type : "Unknown";
        this.interfaceType = "Unknown";
        this.isRemovable = false;
        this.isSystemDrive = false;
        this.healthStatus = "Unknown";
        this.temperature = -1.0f;
    }
    
    public StorageDevice(String model, String serial, String vendor, long size, String type, 
                        String interfaceType, boolean isRemovable, boolean isSystemDrive) {
        this.model = model != null ? model : "Unknown";
        this.serial = serial != null ? serial : "Unknown";
        this.vendor = vendor != null ? vendor : "Unknown";
        this.size = size;
        this.type = type != null ? type : "Unknown";
        this.interfaceType = interfaceType != null ? interfaceType : "Unknown";
        this.isRemovable = isRemovable;
        this.isSystemDrive = isSystemDrive;
        this.healthStatus = "Unknown";
        this.temperature = -1.0f;
    }
    
    /**
     * Get device capacity in human-readable format
     */
    public String getFormattedSize() {
        if (size <= 0) return "Unknown";
        
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double bytes = size;
        int unitIndex = 0;
        
        while (bytes >= 1024 && unitIndex < units.length - 1) {
            bytes /= 1024;
            unitIndex++;
        }
        
        return String.format("%.1f %s", bytes, units[unitIndex]);
    }
    
    /**
     * Check if this is a solid-state drive
     */
    public boolean isSSD() {
        return type.toUpperCase().contains("SSD") || 
               type.toUpperCase().contains("NVME") ||
               interfaceType.toUpperCase().contains("NVME");
    }
    
    /**
     * Check if this is a mechanical hard drive
     */
    public boolean isHDD() {
        return type.toUpperCase().contains("HDD") || 
               type.toUpperCase().contains("MECHANICAL");
    }
    
    /**
     * Check if device has valid identification
     */
    public boolean hasValidIdentification() {
        return !model.equals("Unknown") && 
               !serial.equals("Unknown") && 
               !serial.isEmpty() &&
               size > 0;
    }
    
    /**
     * Get device fingerprint for identification
     */
    public String getDeviceFingerprint() {
        StringBuilder fingerprint = new StringBuilder();
        fingerprint.append(model);
        fingerprint.append("|");
        fingerprint.append(serial);
        fingerprint.append("|");
        fingerprint.append(vendor);
        fingerprint.append("|");
        fingerprint.append(size);
        fingerprint.append("|");
        fingerprint.append(type);
        
        return fingerprint.toString();
    }
    
    /**
     * Check if this device is suitable for fingerprinting
     */
    public boolean isSuitableForFingerprinting() {
        return hasValidIdentification() && 
               !isRemovable && 
               size > 1024 * 1024 * 1024; // At least 1GB
    }
    
    /**
     * Get device priority for fingerprinting (higher = more important)
     */
    public int getFingerprintPriority() {
        int priority = 0;
        
        // System drive gets highest priority
        if (isSystemDrive) priority += 100;
        
        // Larger drives get higher priority
        if (size > 500L * 1024 * 1024 * 1024) priority += 50; // > 500GB
        else if (size > 100L * 1024 * 1024 * 1024) priority += 30; // > 100GB
        else if (size > 10L * 1024 * 1024 * 1024) priority += 10; // > 10GB
        
        // Non-removable drives get higher priority
        if (!isRemovable) priority += 20;
        
        // SSDs get slightly higher priority (more stable)
        if (isSSD()) priority += 10;
        
        // Valid identification increases priority
        if (hasValidIdentification()) priority += 25;
        
        return priority;
    }
    
    @Override
    public String toString() {
        return String.format("StorageDevice{model='%s', size=%s, type='%s', serial='%s'}", 
                           model, getFormattedSize(), type, 
                           serial.length() > 10 ? serial.substring(0, 10) + "..." : serial);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        StorageDevice that = (StorageDevice) obj;
        return model.equals(that.model) && 
               serial.equals(that.serial) && 
               size == that.size;
    }
    
    @Override
    public int hashCode() {
        return model.hashCode() + serial.hashCode() + Long.hashCode(size);
    }
    
    // Getters and setters
    public String getModel() { return model; }
    public String getSerial() { return serial; }
    public String getVendor() { return vendor; }
    public long getSize() { return size; }
    public String getType() { return type; }
    public String getInterfaceType() { return interfaceType; }
    public boolean isRemovable() { return isRemovable; }
    public boolean isSystemDrive() { return isSystemDrive; }
    
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    
    public long getSectorsCount() { return sectorsCount; }
    public void setSectorsCount(long sectorsCount) { this.sectorsCount = sectorsCount; }
    
    public int getSectorSize() { return sectorSize; }
    public void setSectorSize(int sectorSize) { this.sectorSize = sectorSize; }
    
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
    
    public float getTemperature() { return temperature; }
    public void setTemperature(float temperature) { this.temperature = temperature; }
}