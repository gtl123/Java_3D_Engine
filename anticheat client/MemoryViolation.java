package fps.anticheat.client;

import fps.anticheat.ViolationType;

/**
 * Represents a memory-related violation detected by the memory integrity checker.
 * Contains violation details, memory address information, and evidence.
 */
public class MemoryViolation {
    
    private final ViolationType violationType;
    private final String description;
    private final long memoryAddress;
    private final float confidence;
    private final String detectionMethod;
    private final long timestamp;
    
    private String evidence;
    private String additionalInfo;
    private int severity;
    private long memorySize;
    private String memoryContent;
    private String expectedContent;
    private MemoryRegion affectedRegion;
    
    /**
     * Create a memory violation
     */
    public MemoryViolation(ViolationType violationType, String description, 
                          long memoryAddress, float confidence, String detectionMethod) {
        this.violationType = violationType;
        this.description = description;
        this.memoryAddress = memoryAddress;
        this.confidence = Math.max(0.0f, Math.min(1.0f, confidence)); // Clamp to [0,1]
        this.detectionMethod = detectionMethod;
        this.timestamp = System.currentTimeMillis();
        this.severity = calculateSeverity();
        this.evidence = generateEvidence();
        this.memorySize = 0;
    }
    
    /**
     * Create a memory violation with additional information
     */
    public MemoryViolation(ViolationType violationType, String description, 
                          long memoryAddress, float confidence, String detectionMethod,
                          long memorySize, MemoryRegion affectedRegion) {
        this(violationType, description, memoryAddress, confidence, detectionMethod);
        this.memorySize = memorySize;
        this.affectedRegion = affectedRegion;
        this.evidence = generateEvidence(); // Regenerate with additional info
    }
    
    /**
     * Calculate violation severity based on type and context
     */
    private int calculateSeverity() {
        int baseSeverity;
        
        switch (violationType) {
            case PROCESS_INJECTION:
                baseSeverity = 10; // Critical - code injection
                break;
            case MEMORY_MANIPULATION:
                baseSeverity = 8; // High - memory tampering
                break;
            case CLIENT_MODIFICATION:
                baseSeverity = 7; // High - client tampering
                break;
            case DEBUGGING_TOOLS:
                baseSeverity = 6; // Medium-High - debugging detected
                break;
            case SUSPICIOUS_PATTERNS:
                baseSeverity = 5; // Medium - suspicious behavior
                break;
            default:
                baseSeverity = 4; // Medium-Low
                break;
        }
        
        // Adjust severity based on confidence
        float adjustedSeverity = baseSeverity * confidence;
        
        // Adjust based on memory region criticality
        if (affectedRegion != null && affectedRegion.isCritical()) {
            adjustedSeverity *= 1.2f;
        }
        
        return Math.min(10, Math.round(adjustedSeverity));
    }
    
    /**
     * Generate evidence string for this violation
     */
    private String generateEvidence() {
        StringBuilder evidenceBuilder = new StringBuilder();
        
        evidenceBuilder.append("Memory Violation Evidence:\n");
        evidenceBuilder.append("Detection Method: ").append(detectionMethod).append("\n");
        evidenceBuilder.append("Timestamp: ").append(new java.util.Date(timestamp)).append("\n");
        evidenceBuilder.append("Confidence: ").append(String.format("%.2f", confidence)).append("\n");
        evidenceBuilder.append("Severity: ").append(severity).append("/10\n");
        
        evidenceBuilder.append("\nMemory Information:\n");
        evidenceBuilder.append("Address: 0x").append(Long.toHexString(memoryAddress)).append("\n");
        
        if (memorySize > 0) {
            evidenceBuilder.append("Size: ").append(memorySize).append(" bytes\n");
            evidenceBuilder.append("End Address: 0x").append(Long.toHexString(memoryAddress + memorySize)).append("\n");
        }
        
        if (affectedRegion != null) {
            evidenceBuilder.append("\nAffected Region:\n");
            evidenceBuilder.append("Region Name: ").append(affectedRegion.getName()).append("\n");
            evidenceBuilder.append("Region Type: ").append(affectedRegion.getRegionType()).append("\n");
            evidenceBuilder.append("Region Start: 0x").append(Long.toHexString(affectedRegion.getStartAddress())).append("\n");
            evidenceBuilder.append("Region Size: ").append(affectedRegion.getSize()).append(" bytes\n");
            evidenceBuilder.append("Is Critical: ").append(affectedRegion.isCritical()).append("\n");
            evidenceBuilder.append("Is Executable: ").append(affectedRegion.isExecutable()).append("\n");
            evidenceBuilder.append("Protection Level: ").append(affectedRegion.getProtectionLevel()).append("/10\n");
        }
        
        if (memoryContent != null) {
            evidenceBuilder.append("\nMemory Content (hex): ");
            evidenceBuilder.append(memoryContent.substring(0, Math.min(64, memoryContent.length())));
            if (memoryContent.length() > 64) {
                evidenceBuilder.append("...");
            }
            evidenceBuilder.append("\n");
        }
        
        if (expectedContent != null) {
            evidenceBuilder.append("Expected Content (hex): ");
            evidenceBuilder.append(expectedContent.substring(0, Math.min(64, expectedContent.length())));
            if (expectedContent.length() > 64) {
                evidenceBuilder.append("...");
            }
            evidenceBuilder.append("\n");
        }
        
        if (additionalInfo != null && !additionalInfo.isEmpty()) {
            evidenceBuilder.append("\nAdditional Information:\n");
            evidenceBuilder.append(additionalInfo).append("\n");
        }
        
        return evidenceBuilder.toString();
    }
    
    /**
     * Check if this violation is critical (requires immediate action)
     */
    public boolean isCritical() {
        return severity >= 9 || confidence >= 0.9f || 
               violationType == ViolationType.PROCESS_INJECTION ||
               (affectedRegion != null && affectedRegion.isCritical() && affectedRegion.isExecutable());
    }
    
    /**
     * Check if this violation is high priority
     */
    public boolean isHighPriority() {
        return severity >= 7 || confidence >= 0.7f ||
               violationType == ViolationType.MEMORY_MANIPULATION;
    }
    
    /**
     * Get recommended action for this violation
     */
    public String getRecommendedAction() {
        if (isCritical()) {
            return "IMMEDIATE_BAN";
        } else if (isHighPriority()) {
            return "TEMPORARY_BAN";
        } else if (severity >= 5) {
            return "WARNING";
        } else {
            return "LOG_ONLY";
        }
    }
    
    /**
     * Get violation risk score (0-100)
     */
    public int getRiskScore() {
        int baseScore = Math.round(severity * confidence * 10);
        
        // Adjust based on memory characteristics
        if (affectedRegion != null) {
            if (affectedRegion.isExecutable()) {
                baseScore += 10; // Executable memory is more dangerous
            }
            if (affectedRegion.isCritical()) {
                baseScore += 15; // Critical regions are high risk
            }
        }
        
        return Math.min(100, baseScore);
    }
    
    /**
     * Check if this violation should trigger an alert
     */
    public boolean shouldAlert() {
        return isCritical() || (isHighPriority() && confidence >= 0.8f) ||
               (affectedRegion != null && affectedRegion.isCritical());
    }
    
    /**
     * Get violation category for reporting
     */
    public String getCategory() {
        switch (violationType) {
            case PROCESS_INJECTION:
                return "CODE_INJECTION";
            case MEMORY_MANIPULATION:
                return "MEMORY_TAMPERING";
            case CLIENT_MODIFICATION:
                return "CLIENT_TAMPERING";
            case DEBUGGING_TOOLS:
                return "DEBUGGING_VIOLATION";
            case SUSPICIOUS_PATTERNS:
                return "BEHAVIORAL_VIOLATION";
            default:
                return "MEMORY_VIOLATION";
        }
    }
    
    /**
     * Get memory region type affected
     */
    public String getAffectedRegionType() {
        if (affectedRegion != null) {
            return affectedRegion.getRegionType();
        }
        return "UNKNOWN";
    }
    
    /**
     * Check if violation affects executable memory
     */
    public boolean affectsExecutableMemory() {
        return affectedRegion != null && affectedRegion.isExecutable();
    }
    
    /**
     * Check if violation affects critical system memory
     */
    public boolean affectsCriticalMemory() {
        return affectedRegion != null && affectedRegion.isCritical();
    }
    
    /**
     * Create a summary of this violation
     */
    public String getSummary() {
        return String.format("[%s] %s at 0x%x (Confidence: %.2f, Severity: %d/10)",
                           violationType.name(), description, memoryAddress, confidence, severity);
    }
    
    /**
     * Create a detailed report of this violation
     */
    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== MEMORY VIOLATION REPORT ===\n");
        report.append("Violation Type: ").append(violationType.name()).append("\n");
        report.append("Description: ").append(description).append("\n");
        report.append("Category: ").append(getCategory()).append("\n");
        report.append("Memory Address: 0x").append(Long.toHexString(memoryAddress)).append("\n");
        
        if (memorySize > 0) {
            report.append("Memory Size: ").append(memorySize).append(" bytes\n");
            report.append("Address Range: 0x").append(Long.toHexString(memoryAddress))
                  .append(" - 0x").append(Long.toHexString(memoryAddress + memorySize)).append("\n");
        }
        
        report.append("Confidence: ").append(String.format("%.2f", confidence)).append("\n");
        report.append("Severity: ").append(severity).append("/10\n");
        report.append("Risk Score: ").append(getRiskScore()).append("/100\n");
        report.append("Detection Method: ").append(detectionMethod).append("\n");
        report.append("Timestamp: ").append(new java.util.Date(timestamp)).append("\n");
        report.append("Recommended Action: ").append(getRecommendedAction()).append("\n");
        report.append("Should Alert: ").append(shouldAlert()).append("\n");
        report.append("Affects Executable Memory: ").append(affectsExecutableMemory()).append("\n");
        report.append("Affects Critical Memory: ").append(affectsCriticalMemory()).append("\n");
        
        if (affectedRegion != null) {
            report.append("\n=== AFFECTED MEMORY REGION ===\n");
            report.append(affectedRegion.toDetailedString());
        }
        
        if (evidence != null && !evidence.isEmpty()) {
            report.append("\n=== EVIDENCE ===\n");
            report.append(evidence);
        }
        
        return report.toString();
    }
    
    /**
     * Compare violations by severity and risk
     */
    public int compareTo(MemoryViolation other) {
        if (other == null) return 1;
        
        // First compare by risk score
        int riskComparison = Integer.compare(other.getRiskScore(), this.getRiskScore());
        if (riskComparison != 0) {
            return riskComparison;
        }
        
        // Then by severity
        int severityComparison = Integer.compare(other.severity, this.severity);
        if (severityComparison != 0) {
            return severityComparison;
        }
        
        // Then by confidence
        int confidenceComparison = Float.compare(other.confidence, this.confidence);
        if (confidenceComparison != 0) {
            return confidenceComparison;
        }
        
        // Finally by timestamp (newer first)
        return Long.compare(other.timestamp, this.timestamp);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        MemoryViolation that = (MemoryViolation) obj;
        return violationType == that.violationType &&
               memoryAddress == that.memoryAddress &&
               Float.compare(that.confidence, confidence) == 0 &&
               timestamp == that.timestamp &&
               description.equals(that.description) &&
               detectionMethod.equals(that.detectionMethod);
    }
    
    @Override
    public int hashCode() {
        int result = violationType.hashCode();
        result = 31 * result + description.hashCode();
        result = 31 * result + Long.hashCode(memoryAddress);
        result = 31 * result + Float.hashCode(confidence);
        result = 31 * result + detectionMethod.hashCode();
        result = 31 * result + Long.hashCode(timestamp);
        return result;
    }
    
    @Override
    public String toString() {
        return getSummary();
    }
    
    // Getters and setters
    public ViolationType getViolationType() { return violationType; }
    public String getDescription() { return description; }
    public long getMemoryAddress() { return memoryAddress; }
    public float getConfidence() { return confidence; }
    public String getDetectionMethod() { return detectionMethod; }
    public long getTimestamp() { return timestamp; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getAdditionalInfo() { return additionalInfo; }
    public void setAdditionalInfo(String additionalInfo) { this.additionalInfo = additionalInfo; }
    public int getSeverity() { return severity; }
    public void setSeverity(int severity) { this.severity = Math.max(0, Math.min(10, severity)); }
    public long getMemorySize() { return memorySize; }
    public void setMemorySize(long memorySize) { this.memorySize = memorySize; }
    public String getMemoryContent() { return memoryContent; }
    public void setMemoryContent(String memoryContent) { this.memoryContent = memoryContent; }
    public String getExpectedContent() { return expectedContent; }
    public void setExpectedContent(String expectedContent) { this.expectedContent = expectedContent; }
    public MemoryRegion getAffectedRegion() { return affectedRegion; }
    public void setAffectedRegion(MemoryRegion affectedRegion) { this.affectedRegion = affectedRegion; }
}