package fps.anticheat.hardware;

import fps.anticheat.AntiCheatConfiguration;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Generates unique hardware fingerprints to identify systems and prevent ban evasion.
 * Combines multiple hardware characteristics to create a stable, unique identifier.
 */
public class HardwareFingerprinter {
    
    private final AntiCheatConfiguration config;
    private final Map<String, HardwareFingerprint> cachedFingerprints;
    private final SystemInfoCollector systemInfoCollector;
    
    // Fingerprint component weights
    private static final float CPU_WEIGHT = 0.25f;
    private static final float MOTHERBOARD_WEIGHT = 0.20f;
    private static final float MEMORY_WEIGHT = 0.15f;
    private static final float STORAGE_WEIGHT = 0.15f;
    private static final float GPU_WEIGHT = 0.15f;
    private static final float NETWORK_WEIGHT = 0.10f;
    
    // Cache settings
    private static final long FINGERPRINT_CACHE_DURATION = 3600000; // 1 hour
    private static final int MAX_CACHED_FINGERPRINTS = 1000;
    
    public HardwareFingerprinter(AntiCheatConfiguration config) {
        this.config = config;
        this.cachedFingerprints = new HashMap<>();
        this.systemInfoCollector = new SystemInfoCollector();
    }
    
    /**
     * Generate a comprehensive hardware fingerprint for the current system
     */
    public HardwareFingerprint generateFingerprint() {
        try {
            // Check cache first
            String cacheKey = "current_system";
            HardwareFingerprint cached = cachedFingerprints.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return cached;
            }
            
            // Collect hardware information
            HardwareInfo hardwareInfo = systemInfoCollector.collectHardwareInfo();
            
            // Generate fingerprint components
            String cpuFingerprint = generateCPUFingerprint(hardwareInfo);
            String motherboardFingerprint = generateMotherboardFingerprint(hardwareInfo);
            String memoryFingerprint = generateMemoryFingerprint(hardwareInfo);
            String storageFingerprint = generateStorageFingerprint(hardwareInfo);
            String gpuFingerprint = generateGPUFingerprint(hardwareInfo);
            String networkFingerprint = generateNetworkFingerprint(hardwareInfo);
            
            // Combine fingerprints
            String combinedFingerprint = combineFingerprints(
                cpuFingerprint, motherboardFingerprint, memoryFingerprint,
                storageFingerprint, gpuFingerprint, networkFingerprint
            );
            
            // Create fingerprint object
            HardwareFingerprint fingerprint = new HardwareFingerprint(
                combinedFingerprint,
                cpuFingerprint,
                motherboardFingerprint,
                memoryFingerprint,
                storageFingerprint,
                gpuFingerprint,
                networkFingerprint,
                hardwareInfo
            );
            
            // Cache the fingerprint
            cachedFingerprints.put(cacheKey, fingerprint);
            
            return fingerprint;
            
        } catch (Exception e) {
            // Return a fallback fingerprint if hardware detection fails
            return createFallbackFingerprint(e);
        }
    }
    
    /**
     * Generate CPU-based fingerprint
     */
    private String generateCPUFingerprint(HardwareInfo hardwareInfo) {
        StringBuilder cpuData = new StringBuilder();
        
        // CPU model and specifications
        cpuData.append(hardwareInfo.getCpuModel());
        cpuData.append(hardwareInfo.getCpuCores());
        cpuData.append(hardwareInfo.getCpuThreads());
        cpuData.append(hardwareInfo.getCpuFrequency());
        cpuData.append(hardwareInfo.getCpuArchitecture());
        cpuData.append(hardwareInfo.getCpuVendor());
        
        // CPU features and capabilities
        cpuData.append(hardwareInfo.getCpuFeatures());
        cpuData.append(hardwareInfo.getCpuCacheSize());
        
        return hashString(cpuData.toString());
    }
    
    /**
     * Generate motherboard-based fingerprint
     */
    private String generateMotherboardFingerprint(HardwareInfo hardwareInfo) {
        StringBuilder motherboardData = new StringBuilder();
        
        motherboardData.append(hardwareInfo.getMotherboardModel());
        motherboardData.append(hardwareInfo.getMotherboardManufacturer());
        motherboardData.append(hardwareInfo.getMotherboardSerial());
        motherboardData.append(hardwareInfo.getBiosVersion());
        motherboardData.append(hardwareInfo.getBiosDate());
        motherboardData.append(hardwareInfo.getChipsetModel());
        
        return hashString(motherboardData.toString());
    }
    
    /**
     * Generate memory-based fingerprint
     */
    private String generateMemoryFingerprint(HardwareInfo hardwareInfo) {
        StringBuilder memoryData = new StringBuilder();
        
        memoryData.append(hardwareInfo.getTotalMemory());
        memoryData.append(hardwareInfo.getMemoryModules());
        memoryData.append(hardwareInfo.getMemorySpeed());
        memoryData.append(hardwareInfo.getMemoryType());
        
        // Memory module serial numbers (if available)
        for (String moduleSerial : hardwareInfo.getMemoryModuleSerials()) {
            memoryData.append(moduleSerial);
        }
        
        return hashString(memoryData.toString());
    }
    
    /**
     * Generate storage-based fingerprint
     */
    private String generateStorageFingerprint(HardwareInfo hardwareInfo) {
        StringBuilder storageData = new StringBuilder();
        
        // Primary storage device information
        storageData.append(hardwareInfo.getPrimaryStorageModel());
        storageData.append(hardwareInfo.getPrimaryStorageSerial());
        storageData.append(hardwareInfo.getPrimaryStorageSize());
        storageData.append(hardwareInfo.getPrimaryStorageType());
        
        // Additional storage devices
        for (StorageDevice device : hardwareInfo.getStorageDevices()) {
            storageData.append(device.getModel());
            storageData.append(device.getSerial());
            storageData.append(device.getSize());
        }
        
        return hashString(storageData.toString());
    }
    
    /**
     * Generate GPU-based fingerprint
     */
    private String generateGPUFingerprint(HardwareInfo hardwareInfo) {
        StringBuilder gpuData = new StringBuilder();
        
        gpuData.append(hardwareInfo.getGpuModel());
        gpuData.append(hardwareInfo.getGpuVendor());
        gpuData.append(hardwareInfo.getGpuMemory());
        gpuData.append(hardwareInfo.getGpuDriverVersion());
        gpuData.append(hardwareInfo.getGpuBiosVersion());
        
        // Multiple GPU support
        for (GPUInfo gpu : hardwareInfo.getGpuDevices()) {
            gpuData.append(gpu.getModel());
            gpuData.append(gpu.getMemory());
            gpuData.append(gpu.getDeviceId());
        }
        
        return hashString(gpuData.toString());
    }
    
    /**
     * Generate network-based fingerprint
     */
    private String generateNetworkFingerprint(HardwareInfo hardwareInfo) {
        StringBuilder networkData = new StringBuilder();
        
        // Network adapter information
        for (NetworkAdapter adapter : hardwareInfo.getNetworkAdapters()) {
            networkData.append(adapter.getName());
            networkData.append(adapter.getMacAddress());
            networkData.append(adapter.getVendor());
        }
        
        // Network configuration
        networkData.append(hardwareInfo.getHostname());
        networkData.append(hardwareInfo.getDomainName());
        
        return hashString(networkData.toString());
    }
    
    /**
     * Combine individual fingerprints into a master fingerprint
     */
    private String combineFingerprints(String cpu, String motherboard, String memory,
                                     String storage, String gpu, String network) {
        StringBuilder combined = new StringBuilder();
        
        // Weight the components based on their stability and uniqueness
        combined.append("CPU:").append(cpu).append("|");
        combined.append("MB:").append(motherboard).append("|");
        combined.append("MEM:").append(memory).append("|");
        combined.append("STOR:").append(storage).append("|");
        combined.append("GPU:").append(gpu).append("|");
        combined.append("NET:").append(network);
        
        return hashString(combined.toString());
    }
    
    /**
     * Hash a string using SHA-256
     */
    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (Exception e) {
            // Fallback to simple hash if SHA-256 fails
            return String.valueOf(input.hashCode());
        }
    }
    
    /**
     * Create a fallback fingerprint when hardware detection fails
     */
    private HardwareFingerprint createFallbackFingerprint(Exception error) {
        String fallbackId = "FALLBACK_" + System.currentTimeMillis() + "_" + error.getClass().getSimpleName();
        String hashedFallback = hashString(fallbackId);
        
        return new HardwareFingerprint(
            hashedFallback,
            "UNKNOWN_CPU",
            "UNKNOWN_MB",
            "UNKNOWN_MEM",
            "UNKNOWN_STOR",
            "UNKNOWN_GPU",
            "UNKNOWN_NET",
            new HardwareInfo() // Empty hardware info
        );
    }
    
    /**
     * Compare two fingerprints for similarity
     */
    public float compareFingerprints(HardwareFingerprint fp1, HardwareFingerprint fp2) {
        if (fp1 == null || fp2 == null) {
            return 0.0f;
        }
        
        float similarity = 0.0f;
        
        // Compare individual components
        similarity += compareComponent(fp1.getCpuFingerprint(), fp2.getCpuFingerprint()) * CPU_WEIGHT;
        similarity += compareComponent(fp1.getMotherboardFingerprint(), fp2.getMotherboardFingerprint()) * MOTHERBOARD_WEIGHT;
        similarity += compareComponent(fp1.getMemoryFingerprint(), fp2.getMemoryFingerprint()) * MEMORY_WEIGHT;
        similarity += compareComponent(fp1.getStorageFingerprint(), fp2.getStorageFingerprint()) * STORAGE_WEIGHT;
        similarity += compareComponent(fp1.getGpuFingerprint(), fp2.getGpuFingerprint()) * GPU_WEIGHT;
        similarity += compareComponent(fp1.getNetworkFingerprint(), fp2.getNetworkFingerprint()) * NETWORK_WEIGHT;
        
        return similarity;
    }
    
    /**
     * Compare individual fingerprint components
     */
    private float compareComponent(String component1, String component2) {
        if (component1 == null || component2 == null) {
            return 0.0f;
        }
        
        return component1.equals(component2) ? 1.0f : 0.0f;
    }
    
    /**
     * Check if a fingerprint matches any known banned fingerprints
     */
    public boolean isBannedFingerprint(HardwareFingerprint fingerprint, Set<String> bannedFingerprints) {
        if (fingerprint == null || bannedFingerprints == null || bannedFingerprints.isEmpty()) {
            return false;
        }
        
        // Check exact match
        if (bannedFingerprints.contains(fingerprint.getMasterFingerprint())) {
            return true;
        }
        
        // Check component matches (for partial bans)
        for (String bannedFp : bannedFingerprints) {
            if (bannedFp.startsWith("CPU:") && bannedFp.contains(fingerprint.getCpuFingerprint())) {
                return true;
            }
            if (bannedFp.startsWith("MB:") && bannedFp.contains(fingerprint.getMotherboardFingerprint())) {
                return true;
            }
            if (bannedFp.startsWith("STOR:") && bannedFp.contains(fingerprint.getStorageFingerprint())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Generate a partial fingerprint for less strict matching
     */
    public String generatePartialFingerprint(HardwareFingerprint fingerprint) {
        if (fingerprint == null) {
            return null;
        }
        
        // Use only the most stable components for partial matching
        StringBuilder partial = new StringBuilder();
        partial.append(fingerprint.getCpuFingerprint());
        partial.append(fingerprint.getMotherboardFingerprint());
        partial.append(fingerprint.getStorageFingerprint());
        
        return hashString(partial.toString());
    }
    
    /**
     * Clean up expired cached fingerprints
     */
    public void cleanup() {
        cachedFingerprints.entrySet().removeIf(entry -> entry.getValue().isExpired());
        
        // Limit cache size
        if (cachedFingerprints.size() > MAX_CACHED_FINGERPRINTS) {
            // Remove oldest entries
            List<Map.Entry<String, HardwareFingerprint>> entries = new ArrayList<>(cachedFingerprints.entrySet());
            entries.sort((e1, e2) -> Long.compare(e1.getValue().getCreationTime(), e2.getValue().getCreationTime()));
            
            int toRemove = cachedFingerprints.size() - MAX_CACHED_FINGERPRINTS;
            for (int i = 0; i < toRemove; i++) {
                cachedFingerprints.remove(entries.get(i).getKey());
            }
        }
    }
    
    /**
     * Get fingerprint statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cachedFingerprints", cachedFingerprints.size());
        stats.put("maxCacheSize", MAX_CACHED_FINGERPRINTS);
        stats.put("cacheDuration", FINGERPRINT_CACHE_DURATION);
        return stats;
    }
}