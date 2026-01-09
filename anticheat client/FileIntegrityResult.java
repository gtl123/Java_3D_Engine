package fps.anticheat.client;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * Represents the result of a file integrity check operation.
 * Contains file violations found, check statistics, and performance metrics.
 */
public class FileIntegrityResult {
    
    private final List<FileViolation> violations;
    private final Map<String, Object> checkMetrics;
    private final long checkStartTime;
    private long checkEndTime;
    private long checkTime;
    private int filesChecked;
    private boolean successful;
    private String errorMessage;
    private String checkId;
    
    /**
     * Create a new file integrity result
     */
    public FileIntegrityResult() {
        this.violations = new ArrayList<>();
        this.checkMetrics = new HashMap<>();
        this.checkStartTime = System.currentTimeMillis();
        this.checkEndTime = 0;
        this.checkTime = 0;
        this.filesChecked = 0;
        this.successful = true;
        this.checkId = generateCheckId();
    }
    
    /**
     * Create an error result
     */
    public static FileIntegrityResult error(String errorMessage) {
        FileIntegrityResult result = new FileIntegrityResult();
        result.successful = false;
        result.errorMessage = errorMessage;
        result.checkEndTime = System.currentTimeMillis();
        result.checkTime = result.checkEndTime - result.checkStartTime;
        return result;
    }
    
    /**
     * Create a successful result with violations
     */
    public static FileIntegrityResult success(List<FileViolation> violations, int filesChecked) {
        FileIntegrityResult result = new FileIntegrityResult();
        result.violations.addAll(violations);
        result.filesChecked = filesChecked;
        result.checkEndTime = System.currentTimeMillis();
        result.checkTime = result.checkEndTime - result.checkStartTime;
        return result;
    }
    
    /**
     * Generate unique check ID
     */
    private String generateCheckId() {
        return "FILE_" + System.currentTimeMillis() + "_" + 
               Integer.toHexString((int)(Math.random() * 0xFFFF));
    }
    
    /**
     * Add a violation to the result
     */
    public void addViolation(FileViolation violation) {
        if (violation != null) {
            violations.add(violation);
        }
    }
    
    /**
     * Add multiple violations to the result
     */
    public void addViolations(List<FileViolation> violations) {
        if (violations != null) {
            this.violations.addAll(violations);
        }
    }
    
    /**
     * Complete the check and calculate final metrics
     */
    public void completeCheck() {
        checkEndTime = System.currentTimeMillis();
        checkTime = checkEndTime - checkStartTime;
        
        // Calculate check metrics
        calculateCheckMetrics();
    }
    
    /**
     * Calculate check performance and detection metrics
     */
    private void calculateCheckMetrics() {
        checkMetrics.clear();
        
        // Basic metrics
        checkMetrics.put("totalViolations", violations.size());
        checkMetrics.put("filesChecked", filesChecked);
        checkMetrics.put("checkTimeMs", checkTime);
        checkMetrics.put("successful", successful);
        
        if (filesChecked > 0) {
            checkMetrics.put("violationRate", (double)violations.size() / filesChecked);
            checkMetrics.put("filesPerSecond", filesChecked / Math.max(1.0, checkTime / 1000.0));
        }
        
        // Violation analysis
        if (!violations.isEmpty()) {
            Map<String, Integer> violationsByType = new HashMap<>();
            Map<String, Integer> violationsByCategory = new HashMap<>();
            Map<String, Integer> violationsByFileType = new HashMap<>();
            int criticalViolations = 0;
            int highPriorityViolations = 0;
            int executableFileViolations = 0;
            int criticalFileViolations = 0;
            float totalConfidence = 0;
            int totalSeverity = 0;
            int totalRiskScore = 0;
            
            for (FileViolation violation : violations) {
                // Count by type
                String type = violation.getViolationType().name();
                violationsByType.put(type, violationsByType.getOrDefault(type, 0) + 1);
                
                // Count by category
                String category = violation.getCategory();
                violationsByCategory.put(category, violationsByCategory.getOrDefault(category, 0) + 1);
                
                // Count by file type
                String fileType = violation.getAffectedFileType();
                violationsByFileType.put(fileType, violationsByFileType.getOrDefault(fileType, 0) + 1);
                
                // Count priority levels
                if (violation.isCritical()) {
                    criticalViolations++;
                }
                if (violation.isHighPriority()) {
                    highPriorityViolations++;
                }
                
                // Count file type violations
                if (violation.affectsExecutableFile()) {
                    executableFileViolations++;
                }
                if (violation.affectsCriticalFile()) {
                    criticalFileViolations++;
                }
                
                // Accumulate metrics
                totalConfidence += violation.getConfidence();
                totalSeverity += violation.getSeverity();
                totalRiskScore += violation.getRiskScore();
            }
            
            checkMetrics.put("violationsByType", violationsByType);
            checkMetrics.put("violationsByCategory", violationsByCategory);
            checkMetrics.put("violationsByFileType", violationsByFileType);
            checkMetrics.put("criticalViolations", criticalViolations);
            checkMetrics.put("highPriorityViolations", highPriorityViolations);
            checkMetrics.put("executableFileViolations", executableFileViolations);
            checkMetrics.put("criticalFileViolations", criticalFileViolations);
            checkMetrics.put("averageConfidence", totalConfidence / violations.size());
            checkMetrics.put("averageSeverity", (double)totalSeverity / violations.size());
            checkMetrics.put("averageRiskScore", (double)totalRiskScore / violations.size());
            
            // Find highest risk violation
            FileViolation highestRisk = violations.stream()
                .max((v1, v2) -> Integer.compare(v1.getRiskScore(), v2.getRiskScore()))
                .orElse(null);
            
            if (highestRisk != null) {
                checkMetrics.put("highestRiskScore", highestRisk.getRiskScore());
                checkMetrics.put("highestRiskType", highestRisk.getViolationType().name());
                checkMetrics.put("highestRiskFile", highestRisk.getFileName());
            }
        }
    }
    
    /**
     * Check if check has violations
     */
    public boolean hasViolations() {
        return !violations.isEmpty();
    }
    
    /**
     * Check if check has critical violations
     */
    public boolean hasCriticalViolations() {
        return violations.stream().anyMatch(FileViolation::isCritical);
    }
    
    /**
     * Check if check has high priority violations
     */
    public boolean hasHighPriorityViolations() {
        return violations.stream().anyMatch(FileViolation::isHighPriority);
    }
    
    /**
     * Check if check has executable file violations
     */
    public boolean hasExecutableFileViolations() {
        return violations.stream().anyMatch(FileViolation::affectsExecutableFile);
    }
    
    /**
     * Check if check has critical file violations
     */
    public boolean hasCriticalFileViolations() {
        return violations.stream().anyMatch(FileViolation::affectsCriticalFile);
    }
    
    /**
     * Get violations sorted by risk score (highest first)
     */
    public List<FileViolation> getViolationsByRisk() {
        List<FileViolation> sorted = new ArrayList<>(violations);
        sorted.sort((v1, v2) -> Integer.compare(v2.getRiskScore(), v1.getRiskScore()));
        return sorted;
    }
    
    /**
     * Get critical violations only
     */
    public List<FileViolation> getCriticalViolations() {
        return violations.stream()
            .filter(FileViolation::isCritical)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get high priority violations only
     */
    public List<FileViolation> getHighPriorityViolations() {
        return violations.stream()
            .filter(FileViolation::isHighPriority)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get executable file violations only
     */
    public List<FileViolation> getExecutableFileViolations() {
        return violations.stream()
            .filter(FileViolation::affectsExecutableFile)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get critical file violations only
     */
    public List<FileViolation> getCriticalFileViolations() {
        return violations.stream()
            .filter(FileViolation::affectsCriticalFile)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get violations by file type
     */
    public List<FileViolation> getViolationsByFileType(String fileType) {
        return violations.stream()
            .filter(v -> fileType.equals(v.getAffectedFileType()))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get overall risk score for this check (0-100)
     */
    public int getOverallRiskScore() {
        if (violations.isEmpty()) {
            return 0;
        }
        
        // Calculate weighted risk score
        int totalRisk = 0;
        int totalWeight = 0;
        
        for (FileViolation violation : violations) {
            int weight = violation.isCritical() ? 3 : (violation.isHighPriority() ? 2 : 1);
            totalRisk += violation.getRiskScore() * weight;
            totalWeight += weight;
        }
        
        return totalWeight > 0 ? Math.min(100, totalRisk / totalWeight) : 0;
    }
    
    /**
     * Get check performance rating (0-100, higher is better)
     */
    public int getPerformanceRating() {
        if (!successful) {
            return 0;
        }
        
        // Base rating
        int rating = 100;
        
        // Penalize slow checks (over 1 second)
        if (checkTime > 1000) {
            rating -= Math.min(40, (int)((checkTime - 1000) / 100));
        }
        
        // Penalize if no files were checked
        if (filesChecked == 0) {
            rating -= 30;
        }
        
        return Math.max(0, rating);
    }
    
    /**
     * Get file integrity status
     */
    public String getIntegrityStatus() {
        if (!successful) {
            return "CHECK_FAILED";
        } else if (hasCriticalViolations()) {
            return "CRITICAL_VIOLATIONS";
        } else if (hasHighPriorityViolations()) {
            return "HIGH_PRIORITY_VIOLATIONS";
        } else if (hasViolations()) {
            return "VIOLATIONS_DETECTED";
        } else {
            return "INTEGRITY_OK";
        }
    }
    
    /**
     * Get threat level based on violations
     */
    public String getThreatLevel() {
        if (!successful) {
            return "UNKNOWN";
        }
        
        int overallRisk = getOverallRiskScore();
        
        if (overallRisk >= 80) {
            return "CRITICAL";
        } else if (overallRisk >= 60) {
            return "HIGH";
        } else if (overallRisk >= 40) {
            return "MEDIUM";
        } else if (overallRisk >= 20) {
            return "LOW";
        } else {
            return "MINIMAL";
        }
    }
    
    /**
     * Create a summary of the check result
     */
    public String getSummary() {
        if (!successful) {
            return String.format("File integrity check failed: %s", errorMessage);
        }
        
        return String.format("File check completed: %d files checked, %d violations found (%d critical, %d high priority, %d executable files, %d critical files) in %d ms",
                           filesChecked, violations.size(), 
                           getCriticalViolations().size(), getHighPriorityViolations().size(),
                           getExecutableFileViolations().size(), getCriticalFileViolations().size(),
                           checkTime);
    }
    
    /**
     * Create a detailed report of the check result
     */
    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== FILE INTEGRITY CHECK REPORT ===\n");
        report.append("Check ID: ").append(checkId).append("\n");
        report.append("Timestamp: ").append(new java.util.Date(checkStartTime)).append("\n");
        report.append("Status: ").append(getIntegrityStatus()).append("\n");
        report.append("Threat Level: ").append(getThreatLevel()).append("\n");
        
        if (!successful) {
            report.append("Error: ").append(errorMessage).append("\n");
            return report.toString();
        }
        
        report.append("Check Time: ").append(checkTime).append(" ms\n");
        report.append("Files Checked: ").append(filesChecked).append("\n");
        report.append("Total Violations: ").append(violations.size()).append("\n");
        report.append("Critical Violations: ").append(getCriticalViolations().size()).append("\n");
        report.append("High Priority Violations: ").append(getHighPriorityViolations().size()).append("\n");
        report.append("Executable File Violations: ").append(getExecutableFileViolations().size()).append("\n");
        report.append("Critical File Violations: ").append(getCriticalFileViolations().size()).append("\n");
        report.append("Overall Risk Score: ").append(getOverallRiskScore()).append("/100\n");
        report.append("Performance Rating: ").append(getPerformanceRating()).append("/100\n");
        
        if (hasViolations()) {
            report.append("\n=== VIOLATIONS (by Risk Score) ===\n");
            List<FileViolation> sortedViolations = getViolationsByRisk();
            
            for (int i = 0; i < sortedViolations.size(); i++) {
                FileViolation violation = sortedViolations.get(i);
                report.append(String.format("%d. %s (Risk: %d/100)\n", 
                            i + 1, violation.getSummary(), violation.getRiskScore()));
            }
            
            // Group violations by file type
            report.append("\n=== VIOLATIONS BY FILE TYPE ===\n");
            Map<String, List<FileViolation>> violationsByType = new HashMap<>();
            
            for (FileViolation violation : violations) {
                String fileType = violation.getAffectedFileType();
                violationsByType.computeIfAbsent(fileType, k -> new ArrayList<>()).add(violation);
            }
            
            for (Map.Entry<String, List<FileViolation>> entry : violationsByType.entrySet()) {
                report.append(entry.getKey()).append(": ").append(entry.getValue().size()).append(" violations\n");
            }
        }
        
        // Add check metrics
        if (!checkMetrics.isEmpty()) {
            report.append("\n=== CHECK METRICS ===\n");
            for (Map.Entry<String, Object> entry : checkMetrics.entrySet()) {
                report.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        
        return report.toString();
    }
    
    @Override
    public String toString() {
        return getSummary();
    }
    
    // Getters and setters
    public List<FileViolation> getViolations() { return Collections.unmodifiableList(violations); }
    public Map<String, Object> getCheckMetrics() { return Collections.unmodifiableMap(checkMetrics); }
    public long getCheckStartTime() { return checkStartTime; }
    public long getCheckEndTime() { return checkEndTime; }
    public long getCheckTime() { return checkTime; }
    public void setCheckTime(long checkTime) { this.checkTime = checkTime; }
    public int getFilesChecked() { return filesChecked; }
    public void setFilesChecked(int filesChecked) { this.filesChecked = filesChecked; }
    public boolean isSuccessful() { return successful; }
    public void setSuccessful(boolean successful) { this.successful = successful; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getCheckId() { return checkId; }
}