package fps.anticheat.client;

import fps.anticheat.ViolationType;

/**
 * Represents a file-related violation detected by the file integrity checker.
 * Contains violation details, file information, and evidence.
 */
public class FileViolation {
    
    private final ViolationType violationType;
    private final String description;
    private final String filePath;
    private final float confidence;
    private final String detectionMethod;
    private final long timestamp;
    
    private String evidence;
    private String additionalInfo;
    private int severity;
    private FileInfo affectedFile;
    private String expectedHash;
    private String actualHash;
    private long expectedSize;
    private long actualSize;
    
    /**
     * Create a file violation
     */
    public FileViolation(ViolationType violationType, String description, 
                        String filePath, float confidence, String detectionMethod) {
        this.violationType = violationType;
        this.description = description;
        this.filePath = filePath;
        this.confidence = Math.max(0.0f, Math.min(1.0f, confidence)); // Clamp to [0,1]
        this.detectionMethod = detectionMethod;
        this.timestamp = System.currentTimeMillis();
        this.severity = calculateSeverity();
        this.evidence = generateEvidence();
        this.expectedSize = -1;
        this.actualSize = -1;
    }
    
    /**
     * Create a file violation with file info
     */
    public FileViolation(ViolationType violationType, String description, 
                        String filePath, float confidence, String detectionMethod,
                        FileInfo affectedFile) {
        this(violationType, description, filePath, confidence, detectionMethod);
        this.affectedFile = affectedFile;
        this.evidence = generateEvidence(); // Regenerate with file info
    }
    
    /**
     * Calculate violation severity based on type and context
     */
    private int calculateSeverity() {
        int baseSeverity;
        
        switch (violationType) {
            case CLIENT_MODIFICATION:
                baseSeverity = 9; // Critical - client tampering
                break;
            case PROCESS_INJECTION:
                baseSeverity = 10; // Critical - DLL injection
                break;
            case BANNED_SOFTWARE:
                baseSeverity = 8; // High - banned files
                break;
            case SUSPICIOUS_PATTERNS:
                baseSeverity = 6; // Medium - suspicious activity
                break;
            case FILE_SYSTEM_MANIPULATION:
                baseSeverity = 7; // High - file system tampering
                break;
            default:
                baseSeverity = 5; // Medium
                break;
        }
        
        // Adjust severity based on confidence
        float adjustedSeverity = baseSeverity * confidence;
        
        // Adjust based on file criticality
        if (affectedFile != null) {
            if (affectedFile.isCritical()) {
                adjustedSeverity *= 1.3f;
            }
            if (affectedFile.isExecutable()) {
                adjustedSeverity *= 1.2f;
            }
        }
        
        return Math.min(10, Math.round(adjustedSeverity));
    }
    
    /**
     * Generate evidence string for this violation
     */
    private String generateEvidence() {
        StringBuilder evidenceBuilder = new StringBuilder();
        
        evidenceBuilder.append("File Violation Evidence:\n");
        evidenceBuilder.append("Detection Method: ").append(detectionMethod).append("\n");
        evidenceBuilder.append("Timestamp: ").append(new java.util.Date(timestamp)).append("\n");
        evidenceBuilder.append("Confidence: ").append(String.format("%.2f", confidence)).append("\n");
        evidenceBuilder.append("Severity: ").append(severity).append("/10\n");
        
        evidenceBuilder.append("\nFile Information:\n");
        evidenceBuilder.append("File Path: ").append(filePath).append("\n");
        
        if (affectedFile != null) {
            evidenceBuilder.append("File Name: ").append(affectedFile.getName()).append("\n");
            evidenceBuilder.append("File Type: ").append(affectedFile.getFileType()).append("\n");
            evidenceBuilder.append("File Size: ").append(affectedFile.getFormattedSize()).append("\n");
            evidenceBuilder.append("Is Critical: ").append(affectedFile.isCritical()).append("\n");
            evidenceBuilder.append("Is Executable: ").append(affectedFile.isExecutable()).append("\n");
            evidenceBuilder.append("Is Suspicious: ").append(affectedFile.isSuspicious()).append("\n");
            evidenceBuilder.append("Risk Level: ").append(affectedFile.getRiskLevel()).append("/10\n");
            evidenceBuilder.append("Violation Count: ").append(affectedFile.getViolationCount()).append("\n");
            evidenceBuilder.append("Last Modified: ").append(new java.util.Date(affectedFile.getLastModified())).append("\n");
        }
        
        if (expectedHash != null && actualHash != null) {
            evidenceBuilder.append("\nHash Comparison:\n");
            evidenceBuilder.append("Expected Hash: ").append(expectedHash.substring(0, Math.min(32, expectedHash.length()))).append("...\n");
            evidenceBuilder.append("Actual Hash: ").append(actualHash.substring(0, Math.min(32, actualHash.length()))).append("...\n");
        }
        
        if (expectedSize >= 0 && actualSize >= 0) {
            evidenceBuilder.append("\nSize Comparison:\n");
            evidenceBuilder.append("Expected Size: ").append(expectedSize).append(" bytes\n");
            evidenceBuilder.append("Actual Size: ").append(actualSize).append(" bytes\n");
            evidenceBuilder.append("Size Difference: ").append(actualSize - expectedSize).append(" bytes\n");
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
               violationType == ViolationType.CLIENT_MODIFICATION ||
               violationType == ViolationType.PROCESS_INJECTION ||
               (affectedFile != null && affectedFile.isCritical());
    }
    
    /**
     * Check if this violation is high priority
     */
    public boolean isHighPriority() {
        return severity >= 7 || confidence >= 0.7f ||
               violationType == ViolationType.BANNED_SOFTWARE ||
               violationType == ViolationType.FILE_SYSTEM_MANIPULATION;
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
        
        // Adjust based on file characteristics
        if (affectedFile != null) {
            if (affectedFile.isCritical()) {
                baseScore += 15; // Critical files are high risk
            }
            if (affectedFile.isExecutable()) {
                baseScore += 10; // Executable files are more dangerous
            }
            if (affectedFile.isSuspicious()) {
                baseScore += 5; // Suspicious files add risk
            }
        }
        
        return Math.min(100, baseScore);
    }
    
    /**
     * Check if this violation should trigger an alert
     */
    public boolean shouldAlert() {
        return isCritical() || (isHighPriority() && confidence >= 0.8f) ||
               (affectedFile != null && affectedFile.isCritical());
    }
    
    /**
     * Get violation category for reporting
     */
    public String getCategory() {
        switch (violationType) {
            case CLIENT_MODIFICATION:
                return "CLIENT_TAMPERING";
            case PROCESS_INJECTION:
                return "DLL_INJECTION";
            case BANNED_SOFTWARE:
                return "BANNED_FILES";
            case FILE_SYSTEM_MANIPULATION:
                return "FILE_TAMPERING";
            case SUSPICIOUS_PATTERNS:
                return "SUSPICIOUS_FILES";
            default:
                return "FILE_VIOLATION";
        }
    }
    
    /**
     * Get file type affected
     */
    public String getAffectedFileType() {
        if (affectedFile != null) {
            return affectedFile.getFileType();
        }
        
        // Determine from file path
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".exe")) {
            return "EXECUTABLE";
        } else if (lowerPath.endsWith(".dll")) {
            return "LIBRARY";
        } else if (lowerPath.endsWith(".cfg") || lowerPath.endsWith(".ini")) {
            return "CONFIGURATION";
        } else {
            return "UNKNOWN";
        }
    }
    
    /**
     * Check if violation affects executable file
     */
    public boolean affectsExecutableFile() {
        return (affectedFile != null && affectedFile.isExecutable()) ||
               filePath.toLowerCase().endsWith(".exe") ||
               filePath.toLowerCase().endsWith(".dll");
    }
    
    /**
     * Check if violation affects critical file
     */
    public boolean affectsCriticalFile() {
        return affectedFile != null && affectedFile.isCritical();
    }
    
    /**
     * Get file name from path
     */
    public String getFileName() {
        if (affectedFile != null) {
            return affectedFile.getName();
        }
        
        // Extract from path
        int lastSeparator = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        if (lastSeparator >= 0 && lastSeparator < filePath.length() - 1) {
            return filePath.substring(lastSeparator + 1);
        }
        
        return filePath;
    }
    
    /**
     * Create a summary of this violation
     */
    public String getSummary() {
        return String.format("[%s] %s - %s (Confidence: %.2f, Severity: %d/10)",
                           violationType.name(), getFileName(), description, confidence, severity);
    }
    
    /**
     * Create a detailed report of this violation
     */
    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== FILE VIOLATION REPORT ===\n");
        report.append("Violation Type: ").append(violationType.name()).append("\n");
        report.append("Description: ").append(description).append("\n");
        report.append("Category: ").append(getCategory()).append("\n");
        report.append("File Path: ").append(filePath).append("\n");
        report.append("File Name: ").append(getFileName()).append("\n");
        report.append("File Type: ").append(getAffectedFileType()).append("\n");
        report.append("Confidence: ").append(String.format("%.2f", confidence)).append("\n");
        report.append("Severity: ").append(severity).append("/10\n");
        report.append("Risk Score: ").append(getRiskScore()).append("/100\n");
        report.append("Detection Method: ").append(detectionMethod).append("\n");
        report.append("Timestamp: ").append(new java.util.Date(timestamp)).append("\n");
        report.append("Recommended Action: ").append(getRecommendedAction()).append("\n");
        report.append("Should Alert: ").append(shouldAlert()).append("\n");
        report.append("Affects Executable File: ").append(affectsExecutableFile()).append("\n");
        report.append("Affects Critical File: ").append(affectsCriticalFile()).append("\n");
        
        if (affectedFile != null) {
            report.append("\n=== AFFECTED FILE DETAILS ===\n");
            report.append(affectedFile.toDetailedString());
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
    public int compareTo(FileViolation other) {
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
        
        FileViolation that = (FileViolation) obj;
        return violationType == that.violationType &&
               Float.compare(that.confidence, confidence) == 0 &&
               timestamp == that.timestamp &&
               description.equals(that.description) &&
               filePath.equals(that.filePath) &&
               detectionMethod.equals(that.detectionMethod);
    }
    
    @Override
    public int hashCode() {
        int result = violationType.hashCode();
        result = 31 * result + description.hashCode();
        result = 31 * result + filePath.hashCode();
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
    public String getFilePath() { return filePath; }
    public float getConfidence() { return confidence; }
    public String getDetectionMethod() { return detectionMethod; }
    public long getTimestamp() { return timestamp; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getAdditionalInfo() { return additionalInfo; }
    public void setAdditionalInfo(String additionalInfo) { this.additionalInfo = additionalInfo; }
    public int getSeverity() { return severity; }
    public void setSeverity(int severity) { this.severity = Math.max(0, Math.min(10, severity)); }
    public FileInfo getAffectedFile() { return affectedFile; }
    public void setAffectedFile(FileInfo affectedFile) { this.affectedFile = affectedFile; }
    public String getExpectedHash() { return expectedHash; }
    public void setExpectedHash(String expectedHash) { this.expectedHash = expectedHash; }
    public String getActualHash() { return actualHash; }
    public void setActualHash(String actualHash) { this.actualHash = actualHash; }
    public long getExpectedSize() { return expectedSize; }
    public void setExpectedSize(long expectedSize) { this.expectedSize = expectedSize; }
    public long getActualSize() { return actualSize; }
    public void setActualSize(long actualSize) { this.actualSize = actualSize; }
}