package fps.anticheat.hardware;

import java.util.*;

/**
 * Represents a comprehensive hardware fingerprint for system identification.
 * Contains both the master fingerprint and individual component fingerprints for detailed analysis.
 */
public class HardwareFingerprint {
    
    private final String masterFingerprint;
    private final String cpuFingerprint;
    private final String motherboardFingerprint;
    private final String memoryFingerprint;
    private final String storageFingerprint;
    private final String gpuFingerprint;
    private final String networkFingerprint;
    
    private final HardwareInfo hardwareInfo;
    private final long creationTime;
    private final long expirationTime;
    
    private float confidence;
    private boolean isVirtualMachine;
    private boolean isSuspicious;
    private String suspiciousReason;
    private Map<String, String> metadata;
    
    // Fingerprint validity duration (1 hour)
    private static final long FINGERPRINT_VALIDITY_DURATION = 3600000;
    
    /**
     * Create a hardware fingerprint
     */
    public HardwareFingerprint(String masterFingerprint, String cpuFingerprint, 
                              String motherboardFingerprint, String memoryFingerprint,
                              String storageFingerprint, String gpuFingerprint, 
                              String networkFingerprint, HardwareInfo hardwareInfo) {
        this.masterFingerprint = masterFingerprint;
        this.cpuFingerprint = cpuFingerprint;
        this.motherboardFingerprint = motherboardFingerprint;
        this.memoryFingerprint = memoryFingerprint;
        this.storageFingerprint = storageFingerprint;
        this.gpuFingerprint = gpuFingerprint;
        this.networkFingerprint = networkFingerprint;
        this.hardwareInfo = hardwareInfo;
        
        this.creationTime = System.currentTimeMillis();
        this.expirationTime = creationTime + FINGERPRINT_VALIDITY_DURATION;
        
        this.confidence = calculateConfidence();
        this.isVirtualMachine = detectVirtualMachine();
        this.isSuspicious = false;
        this.suspiciousReason = null;
        this.metadata = new HashMap<>();
        
        // Perform initial analysis
        analyzeFingerprint();
    }
    
    /**
     * Calculate confidence score based on available hardware information
     */
    private float calculateConfidence() {
        float confidence = 0.0f;
        int componentCount = 0;
        
        // Check each component for validity
        if (isValidComponent(cpuFingerprint)) {
            confidence += 0.25f;
            componentCount++;
        }
        
        if (isValidComponent(motherboardFingerprint)) {
            confidence += 0.20f;
            componentCount++;
        }
        
        if (isValidComponent(memoryFingerprint)) {
            confidence += 0.15f;
            componentCount++;
        }
        
        if (isValidComponent(storageFingerprint)) {
            confidence += 0.15f;
            componentCount++;
        }
        
        if (isValidComponent(gpuFingerprint)) {
            confidence += 0.15f;
            componentCount++;
        }
        
        if (isValidComponent(networkFingerprint)) {
            confidence += 0.10f;
            componentCount++;
        }
        
        // Adjust confidence based on component availability
        if (componentCount < 4) {
            confidence *= 0.8f; // Reduce confidence if missing components
        }
        
        return Math.max(0.0f, Math.min(1.0f, confidence));
    }
    
    /**
     * Check if a component fingerprint is valid
     */
    private boolean isValidComponent(String component) {
        return component != null && 
               !component.isEmpty() && 
               !component.startsWith("UNKNOWN_") &&
               !component.startsWith("FALLBACK_") &&
               component.length() > 10;
    }
    
    /**
     * Detect if the system is running in a virtual machine
     */
    private boolean detectVirtualMachine() {
        if (hardwareInfo == null) {
            return false;
        }
        
        // Check for VM indicators in hardware info
        String cpuModel = hardwareInfo.getCpuModel();
        String motherboardModel = hardwareInfo.getMotherboardModel();
        String biosVersion = hardwareInfo.getBiosVersion();
        
        // Common VM indicators
        String[] vmIndicators = {
            "VMware", "VirtualBox", "QEMU", "Xen", "Hyper-V", 
            "Parallels", "Virtual", "VM", "VBOX", "BOCHS"
        };
        
        for (String indicator : vmIndicators) {
            if (containsIgnoreCase(cpuModel, indicator) ||
                containsIgnoreCase(motherboardModel, indicator) ||
                containsIgnoreCase(biosVersion, indicator)) {
                return true;
            }
        }
        
        // Check for suspicious hardware combinations
        if (hardwareInfo.getCpuCores() <= 2 && 
            hardwareInfo.getTotalMemory() <= 4096 &&
            hardwareInfo.getStorageDevices().size() <= 1) {
            // Might be a VM with limited resources
            metadata.put("vm_suspicion", "limited_resources");
        }
        
        return false;
    }
    
    /**
     * Case-insensitive contains check
     */
    private boolean containsIgnoreCase(String text, String search) {
        if (text == null || search == null) {
            return false;
        }
        return text.toLowerCase().contains(search.toLowerCase());
    }
    
    /**
     * Analyze fingerprint for suspicious characteristics
     */
    private void analyzeFingerprint() {
        List<String> suspiciousReasons = new ArrayList<>();
        
        // Check for VM detection
        if (isVirtualMachine) {
            suspiciousReasons.add("Virtual machine detected");
        }
        
        // Check for missing or invalid components
        int invalidComponents = 0;
        if (!isValidComponent(cpuFingerprint)) invalidComponents++;
        if (!isValidComponent(motherboardFingerprint)) invalidComponents++;
        if (!isValidComponent(storageFingerprint)) invalidComponents++;
        
        if (invalidComponents >= 2) {
            suspiciousReasons.add("Multiple hardware components undetectable");
        }
        
        // Check for suspicious hardware combinations
        if (hardwareInfo != null) {
            analyzeSuspiciousHardware(suspiciousReasons);
        }
        
        // Check fingerprint entropy
        if (hasLowEntropy()) {
            suspiciousReasons.add("Low fingerprint entropy");
        }
        
        // Set suspicious status
        if (!suspiciousReasons.isEmpty()) {
            this.isSuspicious = true;
            this.suspiciousReason = String.join("; ", suspiciousReasons);
        }
    }
    
    /**
     * Analyze hardware for suspicious characteristics
     */
    private void analyzeSuspiciousHardware(List<String> suspiciousReasons) {
        // Check for unrealistic hardware specs
        if (hardwareInfo.getCpuCores() > 64) {
            suspiciousReasons.add("Unrealistic CPU core count");
        }
        
        if (hardwareInfo.getTotalMemory() > 1024 * 1024) { // > 1TB RAM
            suspiciousReasons.add("Unrealistic memory amount");
        }
        
        // Check for missing essential components
        if (hardwareInfo.getGpuModel() == null || hardwareInfo.getGpuModel().isEmpty()) {
            suspiciousReasons.add("No GPU detected");
        }
        
        if (hardwareInfo.getNetworkAdapters().isEmpty()) {
            suspiciousReasons.add("No network adapters detected");
        }
        
        // Check for sandbox indicators
        if (hardwareInfo.getHostname().toLowerCase().contains("sandbox") ||
            hardwareInfo.getHostname().toLowerCase().contains("analysis")) {
            suspiciousReasons.add("Sandbox environment detected");
        }
    }
    
    /**
     * Check if fingerprint has low entropy (indicating possible spoofing)
     */
    private boolean hasLowEntropy() {
        String combined = masterFingerprint + cpuFingerprint + motherboardFingerprint;
        
        // Simple entropy check - count unique characters
        Set<Character> uniqueChars = new HashSet<>();
        for (char c : combined.toCharArray()) {
            uniqueChars.add(c);
        }
        
        // Low entropy if less than 50% unique characters
        return uniqueChars.size() < (combined.length() * 0.5);
    }
    
    /**
     * Check if fingerprint has expired
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }
    
    /**
     * Get age of fingerprint in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - creationTime;
    }
    
    /**
     * Check if fingerprint is valid for use
     */
    public boolean isValid() {
        return !isExpired() && confidence >= 0.5f && masterFingerprint != null;
    }
    
    /**
     * Get a short identifier for this fingerprint
     */
    public String getShortId() {
        if (masterFingerprint == null || masterFingerprint.length() < 16) {
            return "INVALID";
        }
        return masterFingerprint.substring(0, 16);
    }
    
    /**
     * Get fingerprint summary for logging
     */
    public String getSummary() {
        return String.format(
            "HardwareFingerprint{id=%s, confidence=%.2f, vm=%s, suspicious=%s, age=%dms}",
            getShortId(), confidence, isVirtualMachine, isSuspicious, getAge()
        );
    }
    
    /**
     * Get detailed fingerprint information
     */
    public String getDetailedInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Hardware Fingerprint Details:\n");
        info.append("Master: ").append(getShortId()).append("...\n");
        info.append("CPU: ").append(cpuFingerprint != null ? cpuFingerprint.substring(0, Math.min(16, cpuFingerprint.length())) : "NULL").append("...\n");
        info.append("Motherboard: ").append(motherboardFingerprint != null ? motherboardFingerprint.substring(0, Math.min(16, motherboardFingerprint.length())) : "NULL").append("...\n");
        info.append("Storage: ").append(storageFingerprint != null ? storageFingerprint.substring(0, Math.min(16, storageFingerprint.length())) : "NULL").append("...\n");
        info.append("Confidence: ").append(String.format("%.2f", confidence)).append("\n");
        info.append("Virtual Machine: ").append(isVirtualMachine).append("\n");
        info.append("Suspicious: ").append(isSuspicious);
        if (suspiciousReason != null) {
            info.append(" (").append(suspiciousReason).append(")");
        }
        info.append("\n");
        info.append("Age: ").append(getAge()).append("ms\n");
        
        return info.toString();
    }
    
    /**
     * Compare this fingerprint with another for similarity
     */
    public float calculateSimilarity(HardwareFingerprint other) {
        if (other == null) {
            return 0.0f;
        }
        
        float similarity = 0.0f;
        int comparisons = 0;
        
        // Compare each component
        if (cpuFingerprint != null && other.cpuFingerprint != null) {
            similarity += cpuFingerprint.equals(other.cpuFingerprint) ? 0.25f : 0.0f;
            comparisons++;
        }
        
        if (motherboardFingerprint != null && other.motherboardFingerprint != null) {
            similarity += motherboardFingerprint.equals(other.motherboardFingerprint) ? 0.20f : 0.0f;
            comparisons++;
        }
        
        if (storageFingerprint != null && other.storageFingerprint != null) {
            similarity += storageFingerprint.equals(other.storageFingerprint) ? 0.15f : 0.0f;
            comparisons++;
        }
        
        if (memoryFingerprint != null && other.memoryFingerprint != null) {
            similarity += memoryFingerprint.equals(other.memoryFingerprint) ? 0.15f : 0.0f;
            comparisons++;
        }
        
        if (gpuFingerprint != null && other.gpuFingerprint != null) {
            similarity += gpuFingerprint.equals(other.gpuFingerprint) ? 0.15f : 0.0f;
            comparisons++;
        }
        
        if (networkFingerprint != null && other.networkFingerprint != null) {
            similarity += networkFingerprint.equals(other.networkFingerprint) ? 0.10f : 0.0f;
            comparisons++;
        }
        
        // Adjust for missing components
        if (comparisons < 4) {
            similarity *= 0.8f;
        }
        
        return similarity;
    }
    
    /**
     * Add metadata to the fingerprint
     */
    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }
    
    /**
     * Get metadata value
     */
    public String getMetadata(String key) {
        return metadata.get(key);
    }
    
    @Override
    public String toString() {
        return getSummary();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        HardwareFingerprint that = (HardwareFingerprint) obj;
        return Objects.equals(masterFingerprint, that.masterFingerprint);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(masterFingerprint);
    }
    
    // Getters
    public String getMasterFingerprint() { return masterFingerprint; }
    public String getCpuFingerprint() { return cpuFingerprint; }
    public String getMotherboardFingerprint() { return motherboardFingerprint; }
    public String getMemoryFingerprint() { return memoryFingerprint; }
    public String getStorageFingerprint() { return storageFingerprint; }
    public String getGpuFingerprint() { return gpuFingerprint; }
    public String getNetworkFingerprint() { return networkFingerprint; }
    public HardwareInfo getHardwareInfo() { return hardwareInfo; }
    public long getCreationTime() { return creationTime; }
    public long getExpirationTime() { return expirationTime; }
    public float getConfidence() { return confidence; }
    public boolean isVirtualMachine() { return isVirtualMachine; }
    public boolean isSuspicious() { return isSuspicious; }
    public String getSuspiciousReason() { return suspiciousReason; }
    public Map<String, String> getMetadata() { return new HashMap<>(metadata); }
}