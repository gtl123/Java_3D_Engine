package fps.anticheat.client;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * Represents the result of a process scan operation.
 * Contains violations found, scan statistics, and performance metrics.
 */
public class ProcessScanResult {
    
    private final List<ProcessViolation> violations;
    private final Map<String, Object> scanMetrics;
    private final long scanStartTime;
    private long scanEndTime;
    private long scanTime;
    private int processesScanned;
    private boolean successful;
    private String errorMessage;
    private String scanId;
    
    /**
     * Create a new process scan result
     */
    public ProcessScanResult() {
        this.violations = new ArrayList<>();
        this.scanMetrics = new HashMap<>();
        this.scanStartTime = System.currentTimeMillis();
        this.scanEndTime = 0;
        this.scanTime = 0;
        this.processesScanned = 0;
        this.successful = true;
        this.scanId = generateScanId();
    }
    
    /**
     * Create an error result
     */
    public static ProcessScanResult error(String errorMessage) {
        ProcessScanResult result = new ProcessScanResult();
        result.successful = false;
        result.errorMessage = errorMessage;
        result.scanEndTime = System.currentTimeMillis();
        result.scanTime = result.scanEndTime - result.scanStartTime;
        return result;
    }
    
    /**
     * Create a successful result with violations
     */
    public static ProcessScanResult success(List<ProcessViolation> violations, int processesScanned) {
        ProcessScanResult result = new ProcessScanResult();
        result.violations.addAll(violations);
        result.processesScanned = processesScanned;
        result.scanEndTime = System.currentTimeMillis();
        result.scanTime = result.scanEndTime - result.scanStartTime;
        return result;
    }
    
    /**
     * Generate unique scan ID
     */
    private String generateScanId() {
        return "SCAN_" + System.currentTimeMillis() + "_" + 
               Integer.toHexString((int)(Math.random() * 0xFFFF));
    }
    
    /**
     * Add a violation to the result
     */
    public void addViolation(ProcessViolation violation) {
        if (violation != null) {
            violations.add(violation);
        }
    }
    
    /**
     * Add multiple violations to the result
     */
    public void addViolations(List<ProcessViolation> violations) {
        if (violations != null) {
            this.violations.addAll(violations);
        }
    }
    
    /**
     * Complete the scan and calculate final metrics
     */
    public void completeScan() {
        scanEndTime = System.currentTimeMillis();
        scanTime = scanEndTime - scanStartTime;
        
        // Calculate scan metrics
        calculateScanMetrics();
    }
    
    /**
     * Calculate scan performance and detection metrics
     */
    private void calculateScanMetrics() {
        scanMetrics.clear();
        
        // Basic metrics
        scanMetrics.put("totalViolations", violations.size());
        scanMetrics.put("processesScanned", processesScanned);
        scanMetrics.put("scanTimeMs", scanTime);
        scanMetrics.put("successful", successful);
        
        if (processesScanned > 0) {
            scanMetrics.put("violationRate", (double)violations.size() / processesScanned);
            scanMetrics.put("processesPerSecond", processesScanned / Math.max(1.0, scanTime / 1000.0));
        }
        
        // Violation analysis
        if (!violations.isEmpty()) {
            Map<String, Integer> violationsByType = new HashMap<>();
            Map<String, Integer> violationsByCategory = new HashMap<>();
            int criticalViolations = 0;
            int highPriorityViolations = 0;
            float totalConfidence = 0;
            int totalSeverity = 0;
            
            for (ProcessViolation violation : violations) {
                // Count by type
                String type = violation.getViolationType().name();
                violationsByType.put(type, violationsByType.getOrDefault(type, 0) + 1);
                
                // Count by category
                String category = violation.getCategory();
                violationsByCategory.put(category, violationsByCategory.getOrDefault(category, 0) + 1);
                
                // Count priority levels
                if (violation.isCritical()) {
                    criticalViolations++;
                }
                if (violation.isHighPriority()) {
                    highPriorityViolations++;
                }
                
                // Accumulate confidence and severity
                totalConfidence += violation.getConfidence();
                totalSeverity += violation.getSeverity();
            }
            
            scanMetrics.put("violationsByType", violationsByType);
            scanMetrics.put("violationsByCategory", violationsByCategory);
            scanMetrics.put("criticalViolations", criticalViolations);
            scanMetrics.put("highPriorityViolations", highPriorityViolations);
            scanMetrics.put("averageConfidence", totalConfidence / violations.size());
            scanMetrics.put("averageSeverity", (double)totalSeverity / violations.size());
            
            // Find highest severity violation
            ProcessViolation highestSeverity = violations.stream()
                .max((v1, v2) -> Integer.compare(v1.getSeverity(), v2.getSeverity()))
                .orElse(null);
            
            if (highestSeverity != null) {
                scanMetrics.put("highestSeverity", highestSeverity.getSeverity());
                scanMetrics.put("highestSeverityType", highestSeverity.getViolationType().name());
            }
        }
    }
    
    /**
     * Check if scan has violations
     */
    public boolean hasViolations() {
        return !violations.isEmpty();
    }
    
    /**
     * Check if scan has critical violations
     */
    public boolean hasCriticalViolations() {
        return violations.stream().anyMatch(ProcessViolation::isCritical);
    }
    
    /**
     * Check if scan has high priority violations
     */
    public boolean hasHighPriorityViolations() {
        return violations.stream().anyMatch(ProcessViolation::isHighPriority);
    }
    
    /**
     * Get violations sorted by severity (highest first)
     */
    public List<ProcessViolation> getViolationsBySeverity() {
        List<ProcessViolation> sorted = new ArrayList<>(violations);
        sorted.sort((v1, v2) -> Integer.compare(v2.getSeverity(), v1.getSeverity()));
        return sorted;
    }
    
    /**
     * Get critical violations only
     */
    public List<ProcessViolation> getCriticalViolations() {
        return violations.stream()
            .filter(ProcessViolation::isCritical)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get high priority violations only
     */
    public List<ProcessViolation> getHighPriorityViolations() {
        return violations.stream()
            .filter(ProcessViolation::isHighPriority)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get overall risk score for this scan (0-100)
     */
    public int getOverallRiskScore() {
        if (violations.isEmpty()) {
            return 0;
        }
        
        int totalRisk = violations.stream()
            .mapToInt(ProcessViolation::getRiskScore)
            .sum();
        
        // Average risk, but cap at 100
        return Math.min(100, totalRisk / violations.size());
    }
    
    /**
     * Get scan performance rating (0-100, higher is better)
     */
    public int getPerformanceRating() {
        if (!successful) {
            return 0;
        }
        
        // Base rating
        int rating = 100;
        
        // Penalize slow scans (over 1 second)
        if (scanTime > 1000) {
            rating -= Math.min(50, (int)((scanTime - 1000) / 100));
        }
        
        // Penalize if no processes were scanned
        if (processesScanned == 0) {
            rating -= 30;
        }
        
        return Math.max(0, rating);
    }
    
    /**
     * Create a summary of the scan result
     */
    public String getSummary() {
        if (!successful) {
            return String.format("Scan failed: %s", errorMessage);
        }
        
        return String.format("Scan completed: %d processes scanned, %d violations found (%d critical, %d high priority) in %d ms",
                           processesScanned, violations.size(), 
                           getCriticalViolations().size(), getHighPriorityViolations().size(),
                           scanTime);
    }
    
    /**
     * Create a detailed report of the scan result
     */
    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== PROCESS SCAN REPORT ===\n");
        report.append("Scan ID: ").append(scanId).append("\n");
        report.append("Timestamp: ").append(new java.util.Date(scanStartTime)).append("\n");
        report.append("Status: ").append(successful ? "SUCCESS" : "FAILED").append("\n");
        
        if (!successful) {
            report.append("Error: ").append(errorMessage).append("\n");
            return report.toString();
        }
        
        report.append("Scan Time: ").append(scanTime).append(" ms\n");
        report.append("Processes Scanned: ").append(processesScanned).append("\n");
        report.append("Total Violations: ").append(violations.size()).append("\n");
        report.append("Critical Violations: ").append(getCriticalViolations().size()).append("\n");
        report.append("High Priority Violations: ").append(getHighPriorityViolations().size()).append("\n");
        report.append("Overall Risk Score: ").append(getOverallRiskScore()).append("/100\n");
        report.append("Performance Rating: ").append(getPerformanceRating()).append("/100\n");
        
        if (hasViolations()) {
            report.append("\n=== VIOLATIONS ===\n");
            List<ProcessViolation> sortedViolations = getViolationsBySeverity();
            
            for (int i = 0; i < sortedViolations.size(); i++) {
                ProcessViolation violation = sortedViolations.get(i);
                report.append(String.format("%d. %s\n", i + 1, violation.getSummary()));
            }
        }
        
        // Add scan metrics
        if (!scanMetrics.isEmpty()) {
            report.append("\n=== SCAN METRICS ===\n");
            for (Map.Entry<String, Object> entry : scanMetrics.entrySet()) {
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
    public List<ProcessViolation> getViolations() { return Collections.unmodifiableList(violations); }
    public Map<String, Object> getScanMetrics() { return Collections.unmodifiableMap(scanMetrics); }
    public long getScanStartTime() { return scanStartTime; }
    public long getScanEndTime() { return scanEndTime; }
    public long getScanTime() { return scanTime; }
    public void setScanTime(long scanTime) { this.scanTime = scanTime; }
    public int getProcessesScanned() { return processesScanned; }
    public void setProcessesScanned(int processesScanned) { this.processesScanned = processesScanned; }
    public boolean isSuccessful() { return successful; }
    public void setSuccessful(boolean successful) { this.successful = successful; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getScanId() { return scanId; }
}