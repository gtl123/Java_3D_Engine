package fps.anticheat.client;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * Represents the result of a memory integrity check operation.
 * Contains memory violations found, check statistics, and performance metrics.
 */
public class MemoryIntegrityResult {
    
    private final List<MemoryViolation> violations;
    private final Map<String, Object> checkMetrics;
    private final long checkStartTime;
    private long checkEndTime;
    private long checkTime;
    private int regionsChecked;
    private boolean successful;
    private String errorMessage;
    private String checkId;
    
    /**
     * Create a new memory integrity result
     */
    public MemoryIntegrityResult() {
        this.violations = new ArrayList<>();
        this.checkMetrics = new HashMap<>();
        this.checkStartTime = System.currentTimeMillis();
        this.checkEndTime = 0;
        this.checkTime = 0;
        this.regionsChecked = 0;
        this.successful = true;
        this.checkId = generateCheckId();
    }
    
    /**
     * Create an error result
     */
    public static MemoryIntegrityResult error(String errorMessage) {
        MemoryIntegrityResult result = new MemoryIntegrityResult();
        result.successful = false;
        result.errorMessage = errorMessage;
        result.checkEndTime = System.currentTimeMillis();
        result.checkTime = result.checkEndTime - result.checkStartTime;
        return result;
    }
    
    /**
     * Create a successful result with violations
     */
    public static MemoryIntegrityResult success(List<MemoryViolation> violations, int regionsChecked) {
        MemoryIntegrityResult result = new MemoryIntegrityResult();
        result.violations.addAll(violations);
        result.regionsChecked = regionsChecked;
        result.checkEndTime = System.currentTimeMillis();
        result.checkTime = result.checkEndTime - result.checkStartTime;
        return result;
    }
    
    /**
     * Generate unique check ID
     */
    private String generateCheckId() {
        return "MEM_" + System.currentTimeMillis() + "_" + 
               Integer.toHexString((int)(Math.random() * 0xFFFF));
    }
    
    /**
     * Add a violation to the result
     */
    public void addViolation(MemoryViolation violation) {
        if (violation != null) {
            violations.add(violation);
        }
    }
    
    /**
     * Add multiple violations to the result
     */
    public void addViolations(List<MemoryViolation> violations) {
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
        checkMetrics.put("regionsChecked", regionsChecked);
        checkMetrics.put("checkTimeMs", checkTime);
        checkMetrics.put("successful", successful);
        
        if (regionsChecked > 0) {
            checkMetrics.put("violationRate", (double)violations.size() / regionsChecked);
            checkMetrics.put("regionsPerSecond", regionsChecked / Math.max(1.0, checkTime / 1000.0));
        }
        
        // Violation analysis
        if (!violations.isEmpty()) {
            Map<String, Integer> violationsByType = new HashMap<>();
            Map<String, Integer> violationsByCategory = new HashMap<>();
            Map<String, Integer> violationsByRegionType = new HashMap<>();
            int criticalViolations = 0;
            int highPriorityViolations = 0;
            int executableMemoryViolations = 0;
            int criticalMemoryViolations = 0;
            float totalConfidence = 0;
            int totalSeverity = 0;
            int totalRiskScore = 0;
            
            for (MemoryViolation violation : violations) {
                // Count by type
                String type = violation.getViolationType().name();
                violationsByType.put(type, violationsByType.getOrDefault(type, 0) + 1);
                
                // Count by category
                String category = violation.getCategory();
                violationsByCategory.put(category, violationsByCategory.getOrDefault(category, 0) + 1);
                
                // Count by affected region type
                String regionType = violation.getAffectedRegionType();
                violationsByRegionType.put(regionType, violationsByRegionType.getOrDefault(regionType, 0) + 1);
                
                // Count priority levels
                if (violation.isCritical()) {
                    criticalViolations++;
                }
                if (violation.isHighPriority()) {
                    highPriorityViolations++;
                }
                
                // Count memory type violations
                if (violation.affectsExecutableMemory()) {
                    executableMemoryViolations++;
                }
                if (violation.affectsCriticalMemory()) {
                    criticalMemoryViolations++;
                }
                
                // Accumulate metrics
                totalConfidence += violation.getConfidence();
                totalSeverity += violation.getSeverity();
                totalRiskScore += violation.getRiskScore();
            }
            
            checkMetrics.put("violationsByType", violationsByType);
            checkMetrics.put("violationsByCategory", violationsByCategory);
            checkMetrics.put("violationsByRegionType", violationsByRegionType);
            checkMetrics.put("criticalViolations", criticalViolations);
            checkMetrics.put("highPriorityViolations", highPriorityViolations);
            checkMetrics.put("executableMemoryViolations", executableMemoryViolations);
            checkMetrics.put("criticalMemoryViolations", criticalMemoryViolations);
            checkMetrics.put("averageConfidence", totalConfidence / violations.size());
            checkMetrics.put("averageSeverity", (double)totalSeverity / violations.size());
            checkMetrics.put("averageRiskScore", (double)totalRiskScore / violations.size());
            
            // Find highest risk violation
            MemoryViolation highestRisk = violations.stream()
                .max((v1, v2) -> Integer.compare(v1.getRiskScore(), v2.getRiskScore()))
                .orElse(null);
            
            if (highestRisk != null) {
                checkMetrics.put("highestRiskScore", highestRisk.getRiskScore());
                checkMetrics.put("highestRiskType", highestRisk.getViolationType().name());
                checkMetrics.put("highestRiskAddress", "0x" + Long.toHexString(highestRisk.getMemoryAddress()));
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
        return violations.stream().anyMatch(MemoryViolation::isCritical);
    }
    
    /**
     * Check if check has high priority violations
     */
    public boolean hasHighPriorityViolations() {
        return violations.stream().anyMatch(MemoryViolation::isHighPriority);
    }
    
    /**
     * Check if check has executable memory violations
     */
    public boolean hasExecutableMemoryViolations() {
        return violations.stream().anyMatch(MemoryViolation::affectsExecutableMemory);
    }
    
    /**
     * Check if check has critical memory violations
     */
    public boolean hasCriticalMemoryViolations() {
        return violations.stream().anyMatch(MemoryViolation::affectsCriticalMemory);
    }
    
    /**
     * Get violations sorted by risk score (highest first)
     */
    public List<MemoryViolation> getViolationsByRisk() {
        List<MemoryViolation> sorted = new ArrayList<>(violations);
        sorted.sort((v1, v2) -> Integer.compare(v2.getRiskScore(), v1.getRiskScore()));
        return sorted;
    }
    
    /**
     * Get critical violations only
     */
    public List<MemoryViolation> getCriticalViolations() {
        return violations.stream()
            .filter(MemoryViolation::isCritical)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get high priority violations only
     */
    public List<MemoryViolation> getHighPriorityViolations() {
        return violations.stream()
            .filter(MemoryViolation::isHighPriority)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get executable memory violations only
     */
    public List<MemoryViolation> getExecutableMemoryViolations() {
        return violations.stream()
            .filter(MemoryViolation::affectsExecutableMemory)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get critical memory violations only
     */
    public List<MemoryViolation> getCriticalMemoryViolations() {
        return violations.stream()
            .filter(MemoryViolation::affectsCriticalMemory)
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
        
        for (MemoryViolation violation : violations) {
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
        
        // Penalize slow checks (over 500ms)
        if (checkTime > 500) {
            rating -= Math.min(40, (int)((checkTime - 500) / 50));
        }
        
        // Penalize if no regions were checked
        if (regionsChecked == 0) {
            rating -= 30;
        }
        
        return Math.max(0, rating);
    }
    
    /**
     * Get memory integrity status
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
     * Create a summary of the check result
     */
    public String getSummary() {
        if (!successful) {
            return String.format("Memory integrity check failed: %s", errorMessage);
        }
        
        return String.format("Memory check completed: %d regions checked, %d violations found (%d critical, %d high priority, %d executable memory) in %d ms",
                           regionsChecked, violations.size(), 
                           getCriticalViolations().size(), getHighPriorityViolations().size(),
                           getExecutableMemoryViolations().size(), checkTime);
    }
    
    /**
     * Create a detailed report of the check result
     */
    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== MEMORY INTEGRITY CHECK REPORT ===\n");
        report.append("Check ID: ").append(checkId).append("\n");
        report.append("Timestamp: ").append(new java.util.Date(checkStartTime)).append("\n");
        report.append("Status: ").append(getIntegrityStatus()).append("\n");
        
        if (!successful) {
            report.append("Error: ").append(errorMessage).append("\n");
            return report.toString();
        }
        
        report.append("Check Time: ").append(checkTime).append(" ms\n");
        report.append("Regions Checked: ").append(regionsChecked).append("\n");
        report.append("Total Violations: ").append(violations.size()).append("\n");
        report.append("Critical Violations: ").append(getCriticalViolations().size()).append("\n");
        report.append("High Priority Violations: ").append(getHighPriorityViolations().size()).append("\n");
        report.append("Executable Memory Violations: ").append(getExecutableMemoryViolations().size()).append("\n");
        report.append("Critical Memory Violations: ").append(getCriticalMemoryViolations().size()).append("\n");
        report.append("Overall Risk Score: ").append(getOverallRiskScore()).append("/100\n");
        report.append("Performance Rating: ").append(getPerformanceRating()).append("/100\n");
        
        if (hasViolations()) {
            report.append("\n=== VIOLATIONS (by Risk Score) ===\n");
            List<MemoryViolation> sortedViolations = getViolationsByRisk();
            
            for (int i = 0; i < sortedViolations.size(); i++) {
                MemoryViolation violation = sortedViolations.get(i);
                report.append(String.format("%d. %s (Risk: %d/100)\n", 
                            i + 1, violation.getSummary(), violation.getRiskScore()));
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
    public List<MemoryViolation> getViolations() { return Collections.unmodifiableList(violations); }
    public Map<String, Object> getCheckMetrics() { return Collections.unmodifiableMap(checkMetrics); }
    public long getCheckStartTime() { return checkStartTime; }
    public long getCheckEndTime() { return checkEndTime; }
    public long getCheckTime() { return checkTime; }
    public void setCheckTime(long checkTime) { this.checkTime = checkTime; }
    public int getRegionsChecked() { return regionsChecked; }
    public void setRegionsChecked(int regionsChecked) { this.regionsChecked = regionsChecked; }
    public boolean isSuccessful() { return successful; }
    public void setSuccessful(boolean successful) { this.successful = successful; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getCheckId() { return checkId; }
}