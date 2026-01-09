package fps.anticheat.client;

import fps.anticheat.ViolationType;

/**
 * Represents a process-related violation detected by the process monitor.
 * Contains violation details, evidence, and confidence information.
 */
public class ProcessViolation {
    
    private final ViolationType violationType;
    private final String description;
    private final ProcessInfo process;
    private final float confidence;
    private final String detectionMethod;
    private final long timestamp;
    private String evidence;
    private String additionalInfo;
    private int severity;
    
    /**
     * Create a process violation
     */
    public ProcessViolation(ViolationType violationType, String description, 
                           ProcessInfo process, float confidence, String detectionMethod) {
        this.violationType = violationType;
        this.description = description;
        this.process = process;
        this.confidence = Math.max(0.0f, Math.min(1.0f, confidence)); // Clamp to [0,1]
        this.detectionMethod = detectionMethod;
        this.timestamp = System.currentTimeMillis();
        this.severity = calculateSeverity();
        this.evidence = generateEvidence();
    }
    
    /**
     * Create a process violation with additional information
     */
    public ProcessViolation(ViolationType violationType, String description, 
                           ProcessInfo process, float confidence, String detectionMethod,
                           String additionalInfo) {
        this(violationType, description, process, confidence, detectionMethod);
        this.additionalInfo = additionalInfo;
    }
    
    /**
     * Calculate violation severity based on type and confidence
     */
    private int calculateSeverity() {
        int baseSeverity;
        
        switch (violationType) {
            case BANNED_SOFTWARE:
                baseSeverity = 10; // Critical
                break;
            case CLIENT_MODIFICATION:
                baseSeverity = 8; // High
                break;
            case SUSPICIOUS_PATTERNS:
                baseSeverity = 6; // Medium
                break;
            case MEMORY_MANIPULATION:
                baseSeverity = 9; // Very High
                break;
            case PROCESS_INJECTION:
                baseSeverity = 9; // Very High
                break;
            case DEBUGGING_TOOLS:
                baseSeverity = 7; // High
                break;
            default:
                baseSeverity = 5; // Medium
                break;
        }
        
        // Adjust severity based on confidence
        float adjustedSeverity = baseSeverity * confidence;
        
        return Math.round(adjustedSeverity);
    }
    
    /**
     * Generate evidence string for this violation
     */
    private String generateEvidence() {
        StringBuilder evidenceBuilder = new StringBuilder();
        
        evidenceBuilder.append("Process Violation Evidence:\n");
        evidenceBuilder.append("Detection Method: ").append(detectionMethod).append("\n");
        evidenceBuilder.append("Timestamp: ").append(new java.util.Date(timestamp)).append("\n");
        evidenceBuilder.append("Confidence: ").append(String.format("%.2f", confidence)).append("\n");
        evidenceBuilder.append("Severity: ").append(severity).append("/10\n");
        
        if (process != null) {
            evidenceBuilder.append("\nProcess Information:\n");
            evidenceBuilder.append("PID: ").append(process.getPid()).append("\n");
            evidenceBuilder.append("Name: ").append(process.getName()).append("\n");
            evidenceBuilder.append("Command Line: ").append(process.getCommandLine()).append("\n");
            
            if (process.getPath() != null) {
                evidenceBuilder.append("Path: ").append(process.getPath()).append("\n");
            }
            
            evidenceBuilder.append("Memory Usage: ").append(process.getMemoryUsage()).append(" bytes\n");
            evidenceBuilder.append("CPU Usage: ").append(process.getCpuUsage()).append("%\n");
            evidenceBuilder.append("Process Age: ").append(process.getAge()).append(" ms\n");
            evidenceBuilder.append("Is System Process: ").append(process.isSystemProcess()).append("\n");
            evidenceBuilder.append("Is Suspicious: ").append(process.isSuspicious()).append("\n");
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
               violationType == ViolationType.BANNED_SOFTWARE ||
               violationType == ViolationType.MEMORY_MANIPULATION ||
               violationType == ViolationType.PROCESS_INJECTION;
    }
    
    /**
     * Check if this violation is high priority
     */
    public boolean isHighPriority() {
        return severity >= 7 || confidence >= 0.7f;
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
        return Math.round(severity * confidence * 10);
    }
    
    /**
     * Check if this violation should trigger an alert
     */
    public boolean shouldAlert() {
        return isCritical() || (isHighPriority() && confidence >= 0.8f);
    }
    
    /**
     * Get violation category for reporting
     */
    public String getCategory() {
        switch (violationType) {
            case BANNED_SOFTWARE:
            case CLIENT_MODIFICATION:
                return "SOFTWARE_VIOLATION";
            case MEMORY_MANIPULATION:
            case PROCESS_INJECTION:
                return "MEMORY_VIOLATION";
            case DEBUGGING_TOOLS:
                return "DEBUGGING_VIOLATION";
            case SUSPICIOUS_PATTERNS:
                return "BEHAVIORAL_VIOLATION";
            default:
                return "GENERAL_VIOLATION";
        }
    }
    
    /**
     * Create a summary of this violation
     */
    public String getSummary() {
        return String.format("[%s] %s (Confidence: %.2f, Severity: %d/10, Process: %s)",
                           violationType.name(), description, confidence, severity, 
                           process != null ? process.getName() : "unknown");
    }
    
    /**
     * Create a detailed report of this violation
     */
    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== PROCESS VIOLATION REPORT ===\n");
        report.append("Violation Type: ").append(violationType.name()).append("\n");
        report.append("Description: ").append(description).append("\n");
        report.append("Category: ").append(getCategory()).append("\n");
        report.append("Confidence: ").append(String.format("%.2f", confidence)).append("\n");
        report.append("Severity: ").append(severity).append("/10\n");
        report.append("Risk Score: ").append(getRiskScore()).append("/100\n");
        report.append("Detection Method: ").append(detectionMethod).append("\n");
        report.append("Timestamp: ").append(new java.util.Date(timestamp)).append("\n");
        report.append("Recommended Action: ").append(getRecommendedAction()).append("\n");
        report.append("Should Alert: ").append(shouldAlert()).append("\n");
        
        if (process != null) {
            report.append("\n=== PROCESS DETAILS ===\n");
            report.append(process.toDetailedString());
        }
        
        if (evidence != null && !evidence.isEmpty()) {
            report.append("\n=== EVIDENCE ===\n");
            report.append(evidence);
        }
        
        return report.toString();
    }
    
    /**
     * Compare violations by severity and confidence
     */
    public int compareTo(ProcessViolation other) {
        if (other == null) return 1;
        
        // First compare by severity
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
        
        ProcessViolation that = (ProcessViolation) obj;
        return violationType == that.violationType &&
               Float.compare(that.confidence, confidence) == 0 &&
               timestamp == that.timestamp &&
               description.equals(that.description) &&
               detectionMethod.equals(that.detectionMethod) &&
               (process != null ? process.equals(that.process) : that.process == null);
    }
    
    @Override
    public int hashCode() {
        int result = violationType.hashCode();
        result = 31 * result + description.hashCode();
        result = 31 * result + (process != null ? process.hashCode() : 0);
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
    public ProcessInfo getProcess() { return process; }
    public float getConfidence() { return confidence; }
    public String getDetectionMethod() { return detectionMethod; }
    public long getTimestamp() { return timestamp; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getAdditionalInfo() { return additionalInfo; }
    public void setAdditionalInfo(String additionalInfo) { this.additionalInfo = additionalInfo; }
    public int getSeverity() { return severity; }
    public void setSeverity(int severity) { this.severity = Math.max(0, Math.min(10, severity)); }
}