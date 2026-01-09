package fps.anticheat.hardware;

import java.util.*;

/**
 * Contains comprehensive hardware information collected from the system.
 * Used for generating hardware fingerprints and detecting system characteristics.
 */
public class HardwareInfo {
    
    // CPU Information
    private String cpuModel;
    private String cpuVendor;
    private String cpuArchitecture;
    private int cpuCores;
    private int cpuThreads;
    private long cpuFrequency;
    private String cpuFeatures;
    private long cpuCacheSize;
    
    // Motherboard Information
    private String motherboardModel;
    private String motherboardManufacturer;
    private String motherboardSerial;
    private String biosVersion;
    private String biosDate;
    private String chipsetModel;
    
    // Memory Information
    private long totalMemory;
    private String memoryType;
    private long memorySpeed;
    private int memoryModules;
    private List<String> memoryModuleSerials;
    
    // Storage Information
    private String primaryStorageModel;
    private String primaryStorageSerial;
    private long primaryStorageSize;
    private String primaryStorageType;
    private List<StorageDevice> storageDevices;
    
    // GPU Information
    private String gpuModel;
    private String gpuVendor;
    private long gpuMemory;
    private String gpuDriverVersion;
    private String gpuBiosVersion;
    private List<GPUInfo> gpuDevices;
    
    // Network Information
    private List<NetworkAdapter> networkAdapters;
    private String hostname;
    private String domainName;
    
    // System Information
    private String operatingSystem;
    private String osVersion;
    private String systemManufacturer;
    private String systemModel;
    private String systemSerial;
    private long systemUptime;
    
    // Collection metadata
    private long collectionTime;
    private boolean collectionSuccessful;
    private List<String> collectionErrors;
    
    public HardwareInfo() {
        this.memoryModuleSerials = new ArrayList<>();
        this.storageDevices = new ArrayList<>();
        this.gpuDevices = new ArrayList<>();
        this.networkAdapters = new ArrayList<>();
        this.collectionErrors = new ArrayList<>();
        this.collectionTime = System.currentTimeMillis();
        this.collectionSuccessful = true;
    }
    
    /**
     * Add a collection error
     */
    public void addCollectionError(String error) {
        collectionErrors.add(error);
        collectionSuccessful = false;
    }
    
    /**
     * Check if hardware info collection was successful
     */
    public boolean isCollectionSuccessful() {
        return collectionSuccessful && collectionErrors.isEmpty();
    }
    
    /**
     * Get collection age in milliseconds
     */
    public long getCollectionAge() {
        return System.currentTimeMillis() - collectionTime;
    }
    
    /**
     * Check if collection is recent (within specified time)
     */
    public boolean isCollectionRecent(long maxAge) {
        return getCollectionAge() <= maxAge;
    }
    
    /**
     * Add a storage device
     */
    public void addStorageDevice(StorageDevice device) {
        if (device != null) {
            storageDevices.add(device);
        }
    }
    
    /**
     * Add a GPU device
     */
    public void addGpuDevice(GPUInfo gpu) {
        if (gpu != null) {
            gpuDevices.add(gpu);
        }
    }
    
    /**
     * Add a network adapter
     */
    public void addNetworkAdapter(NetworkAdapter adapter) {
        if (adapter != null) {
            networkAdapters.add(adapter);
        }
    }
    
    /**
     * Add a memory module serial
     */
    public void addMemoryModuleSerial(String serial) {
        if (serial != null && !serial.isEmpty()) {
            memoryModuleSerials.add(serial);
        }
    }
    
    /**
     * Get primary storage device
     */
    public StorageDevice getPrimaryStorageDevice() {
        if (storageDevices.isEmpty()) {
            return null;
        }
        
        // Find the largest storage device as primary
        StorageDevice primary = storageDevices.get(0);
        for (StorageDevice device : storageDevices) {
            if (device.getSize() > primary.getSize()) {
                primary = device;
            }
        }
        
        return primary;
    }
    
    /**
     * Get primary GPU device
     */
    public GPUInfo getPrimaryGpuDevice() {
        if (gpuDevices.isEmpty()) {
            return null;
        }
        
        // Find the GPU with most memory as primary
        GPUInfo primary = gpuDevices.get(0);
        for (GPUInfo gpu : gpuDevices) {
            if (gpu.getMemory() > primary.getMemory()) {
                primary = gpu;
            }
        }
        
        return primary;
    }
    
    /**
     * Get primary network adapter (excluding loopback and virtual adapters)
     */
    public NetworkAdapter getPrimaryNetworkAdapter() {
        for (NetworkAdapter adapter : networkAdapters) {
            if (!adapter.isLoopback() && !adapter.isVirtual() && adapter.isUp()) {
                return adapter;
            }
        }
        
        // Fallback to first adapter if no suitable one found
        return networkAdapters.isEmpty() ? null : networkAdapters.get(0);
    }
    
    /**
     * Calculate total storage capacity
     */
    public long getTotalStorageCapacity() {
        long total = 0;
        for (StorageDevice device : storageDevices) {
            total += device.getSize();
        }
        return total;
    }
    
    /**
     * Calculate total GPU memory
     */
    public long getTotalGpuMemory() {
        long total = 0;
        for (GPUInfo gpu : gpuDevices) {
            total += gpu.getMemory();
        }
        return total;
    }
    
    /**
     * Get hardware summary for logging
     */
    public String getHardwareSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Hardware Summary:\n");
        summary.append("CPU: ").append(cpuModel).append(" (").append(cpuCores).append(" cores)\n");
        summary.append("Memory: ").append(totalMemory / (1024 * 1024)).append(" MB\n");
        summary.append("GPU: ").append(gpuModel).append("\n");
        summary.append("Storage: ").append(storageDevices.size()).append(" devices, ")
               .append(getTotalStorageCapacity() / (1024 * 1024 * 1024)).append(" GB total\n");
        summary.append("Network: ").append(networkAdapters.size()).append(" adapters\n");
        summary.append("OS: ").append(operatingSystem).append(" ").append(osVersion).append("\n");
        return summary.toString();
    }
    
    /**
     * Get detailed hardware information
     */
    public String getDetailedInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== Detailed Hardware Information ===\n");
        
        // CPU Information
        info.append("CPU:\n");
        info.append("  Model: ").append(cpuModel).append("\n");
        info.append("  Vendor: ").append(cpuVendor).append("\n");
        info.append("  Architecture: ").append(cpuArchitecture).append("\n");
        info.append("  Cores: ").append(cpuCores).append("\n");
        info.append("  Threads: ").append(cpuThreads).append("\n");
        info.append("  Frequency: ").append(cpuFrequency).append(" Hz\n");
        info.append("  Cache: ").append(cpuCacheSize).append(" bytes\n");
        
        // Motherboard Information
        info.append("Motherboard:\n");
        info.append("  Model: ").append(motherboardModel).append("\n");
        info.append("  Manufacturer: ").append(motherboardManufacturer).append("\n");
        info.append("  BIOS: ").append(biosVersion).append(" (").append(biosDate).append(")\n");
        info.append("  Chipset: ").append(chipsetModel).append("\n");
        
        // Memory Information
        info.append("Memory:\n");
        info.append("  Total: ").append(totalMemory / (1024 * 1024)).append(" MB\n");
        info.append("  Type: ").append(memoryType).append("\n");
        info.append("  Speed: ").append(memorySpeed).append(" MHz\n");
        info.append("  Modules: ").append(memoryModules).append("\n");
        
        // Storage Information
        info.append("Storage:\n");
        for (StorageDevice device : storageDevices) {
            info.append("  ").append(device.toString()).append("\n");
        }
        
        // GPU Information
        info.append("GPU:\n");
        for (GPUInfo gpu : gpuDevices) {
            info.append("  ").append(gpu.toString()).append("\n");
        }
        
        // Network Information
        info.append("Network:\n");
        for (NetworkAdapter adapter : networkAdapters) {
            info.append("  ").append(adapter.toString()).append("\n");
        }
        
        // System Information
        info.append("System:\n");
        info.append("  OS: ").append(operatingSystem).append(" ").append(osVersion).append("\n");
        info.append("  Manufacturer: ").append(systemManufacturer).append("\n");
        info.append("  Model: ").append(systemModel).append("\n");
        info.append("  Uptime: ").append(systemUptime).append(" ms\n");
        
        // Collection Information
        info.append("Collection:\n");
        info.append("  Time: ").append(new Date(collectionTime)).append("\n");
        info.append("  Successful: ").append(collectionSuccessful).append("\n");
        if (!collectionErrors.isEmpty()) {
            info.append("  Errors: ").append(String.join(", ", collectionErrors)).append("\n");
        }
        
        return info.toString();
    }
    
    /**
     * Validate hardware information completeness
     */
    public boolean isComplete() {
        return cpuModel != null && !cpuModel.isEmpty() &&
               motherboardModel != null && !motherboardModel.isEmpty() &&
               totalMemory > 0 &&
               !storageDevices.isEmpty() &&
               !networkAdapters.isEmpty();
    }
    
    /**
     * Get completeness score (0.0 to 1.0)
     */
    public float getCompletenessScore() {
        float score = 0.0f;
        int totalComponents = 10;
        
        if (cpuModel != null && !cpuModel.isEmpty()) score += 0.15f;
        if (motherboardModel != null && !motherboardModel.isEmpty()) score += 0.15f;
        if (totalMemory > 0) score += 0.10f;
        if (!storageDevices.isEmpty()) score += 0.15f;
        if (gpuModel != null && !gpuModel.isEmpty()) score += 0.10f;
        if (!networkAdapters.isEmpty()) score += 0.10f;
        if (operatingSystem != null && !operatingSystem.isEmpty()) score += 0.05f;
        if (biosVersion != null && !biosVersion.isEmpty()) score += 0.05f;
        if (cpuCores > 0) score += 0.05f;
        if (systemManufacturer != null && !systemManufacturer.isEmpty()) score += 0.10f;
        
        return score;
    }
    
    @Override
    public String toString() {
        return getHardwareSummary();
    }
    
    // Getters and Setters
    public String getCpuModel() { return cpuModel; }
    public void setCpuModel(String cpuModel) { this.cpuModel = cpuModel; }
    
    public String getCpuVendor() { return cpuVendor; }
    public void setCpuVendor(String cpuVendor) { this.cpuVendor = cpuVendor; }
    
    public String getCpuArchitecture() { return cpuArchitecture; }
    public void setCpuArchitecture(String cpuArchitecture) { this.cpuArchitecture = cpuArchitecture; }
    
    public int getCpuCores() { return cpuCores; }
    public void setCpuCores(int cpuCores) { this.cpuCores = cpuCores; }
    
    public int getCpuThreads() { return cpuThreads; }
    public void setCpuThreads(int cpuThreads) { this.cpuThreads = cpuThreads; }
    
    public long getCpuFrequency() { return cpuFrequency; }
    public void setCpuFrequency(long cpuFrequency) { this.cpuFrequency = cpuFrequency; }
    
    public String getCpuFeatures() { return cpuFeatures; }
    public void setCpuFeatures(String cpuFeatures) { this.cpuFeatures = cpuFeatures; }
    
    public long getCpuCacheSize() { return cpuCacheSize; }
    public void setCpuCacheSize(long cpuCacheSize) { this.cpuCacheSize = cpuCacheSize; }
    
    public String getMotherboardModel() { return motherboardModel; }
    public void setMotherboardModel(String motherboardModel) { this.motherboardModel = motherboardModel; }
    
    public String getMotherboardManufacturer() { return motherboardManufacturer; }
    public void setMotherboardManufacturer(String motherboardManufacturer) { this.motherboardManufacturer = motherboardManufacturer; }
    
    public String getMotherboardSerial() { return motherboardSerial; }
    public void setMotherboardSerial(String motherboardSerial) { this.motherboardSerial = motherboardSerial; }
    
    public String getBiosVersion() { return biosVersion; }
    public void setBiosVersion(String biosVersion) { this.biosVersion = biosVersion; }
    
    public String getBiosDate() { return biosDate; }
    public void setBiosDate(String biosDate) { this.biosDate = biosDate; }
    
    public String getChipsetModel() { return chipsetModel; }
    public void setChipsetModel(String chipsetModel) { this.chipsetModel = chipsetModel; }
    
    public long getTotalMemory() { return totalMemory; }
    public void setTotalMemory(long totalMemory) { this.totalMemory = totalMemory; }
    
    public String getMemoryType() { return memoryType; }
    public void setMemoryType(String memoryType) { this.memoryType = memoryType; }
    
    public long getMemorySpeed() { return memorySpeed; }
    public void setMemorySpeed(long memorySpeed) { this.memorySpeed = memorySpeed; }
    
    public int getMemoryModules() { return memoryModules; }
    public void setMemoryModules(int memoryModules) { this.memoryModules = memoryModules; }
    
    public List<String> getMemoryModuleSerials() { return new ArrayList<>(memoryModuleSerials); }
    public void setMemoryModuleSerials(List<String> memoryModuleSerials) { this.memoryModuleSerials = new ArrayList<>(memoryModuleSerials); }
    
    public String getPrimaryStorageModel() { return primaryStorageModel; }
    public void setPrimaryStorageModel(String primaryStorageModel) { this.primaryStorageModel = primaryStorageModel; }
    
    public String getPrimaryStorageSerial() { return primaryStorageSerial; }
    public void setPrimaryStorageSerial(String primaryStorageSerial) { this.primaryStorageSerial = primaryStorageSerial; }
    
    public long getPrimaryStorageSize() { return primaryStorageSize; }
    public void setPrimaryStorageSize(long primaryStorageSize) { this.primaryStorageSize = primaryStorageSize; }
    
    public String getPrimaryStorageType() { return primaryStorageType; }
    public void setPrimaryStorageType(String primaryStorageType) { this.primaryStorageType = primaryStorageType; }
    
    public List<StorageDevice> getStorageDevices() { return new ArrayList<>(storageDevices); }
    public void setStorageDevices(List<StorageDevice> storageDevices) { this.storageDevices = new ArrayList<>(storageDevices); }
    
    public String getGpuModel() { return gpuModel; }
    public void setGpuModel(String gpuModel) { this.gpuModel = gpuModel; }
    
    public String getGpuVendor() { return gpuVendor; }
    public void setGpuVendor(String gpuVendor) { this.gpuVendor = gpuVendor; }
    
    public long getGpuMemory() { return gpuMemory; }
    public void setGpuMemory(long gpuMemory) { this.gpuMemory = gpuMemory; }
    
    public String getGpuDriverVersion() { return gpuDriverVersion; }
    public void setGpuDriverVersion(String gpuDriverVersion) { this.gpuDriverVersion = gpuDriverVersion; }
    
    public String getGpuBiosVersion() { return gpuBiosVersion; }
    public void setGpuBiosVersion(String gpuBiosVersion) { this.gpuBiosVersion = gpuBiosVersion; }
    
    public List<GPUInfo> getGpuDevices() { return new ArrayList<>(gpuDevices); }
    public void setGpuDevices(List<GPUInfo> gpuDevices) { this.gpuDevices = new ArrayList<>(gpuDevices); }
    
    public List<NetworkAdapter> getNetworkAdapters() { return new ArrayList<>(networkAdapters); }
    public void setNetworkAdapters(List<NetworkAdapter> networkAdapters) { this.networkAdapters = new ArrayList<>(networkAdapters); }
    
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }
    
    public String getDomainName() { return domainName; }
    public void setDomainName(String domainName) { this.domainName = domainName; }
    
    public String getOperatingSystem() { return operatingSystem; }
    public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }
    
    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }
    
    public String getSystemManufacturer() { return systemManufacturer; }
    public void setSystemManufacturer(String systemManufacturer) { this.systemManufacturer = systemManufacturer; }
    
    public String getSystemModel() { return systemModel; }
    public void setSystemModel(String systemModel) { this.systemModel = systemModel; }
    
    public String getSystemSerial() { return systemSerial; }
    public void setSystemSerial(String systemSerial) { this.systemSerial = systemSerial; }
    
    public long getSystemUptime() { return systemUptime; }
    public void setSystemUptime(long systemUptime) { this.systemUptime = systemUptime; }
    
    public long getCollectionTime() { return collectionTime; }
    public void setCollectionTime(long collectionTime) { this.collectionTime = collectionTime; }
    
    public List<String> getCollectionErrors() { return new ArrayList<>(collectionErrors); }
    public void setCollectionErrors(List<String> collectionErrors) { this.collectionErrors = new ArrayList<>(collectionErrors); }
}