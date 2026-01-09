package fps.anticheat.behavioral;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * Represents the result of a behavioral analysis operation.
 * Contains behavioral violations found, analysis statistics, and performance metrics.
 */
public class BehavioralAnalysisResult {
    
    private final List<BehavioralViolation> violations;
    private final Map<String, Object> analysisMetrics;
    private final long analysisStartTime;
    private long analysisEndTime;
    private long analysisTime;
    private boolean successful;
    private String errorMessage;
    private String analysisId;
    
    /**
     * Create a new behavioral analysis result
     */
    public BehavioralAnalysisResult() {
        this.violations = new ArrayList<>();
        this.analysisMetrics = new HashMap<>();
        this.analysisStartTime = System.currentTimeMillis();
        this.analysisEndTime = 0;
        this.analysisTime = 0;
        this.successful = true;
        this.analysisId = generateAnalysisId();
    }
    
    /**
     * Create an error result
     */
    public static BehavioralAnalysisResult error(String errorMessage) {
        BehavioralAnalysisResult result = new BehavioralAnalysisResult();
        result.successful = false;
        result.errorMessage = errorMessage;
        result.analysisEndTime = System.currentTimeMillis();
        result.analysisTime = result.analysisEndTime - result.analysisStartTime;
        return result;
    }
    
    /**
     * Create a successful result with violations
     */
    public static BehavioralAnalysisResult success(List<BehavioralViolation> violations) {
        BehavioralAnalysisResult result = new BehavioralAnalysisResult();
        result.violations.addAll(violations);
        result.analysisEndTime = System.currentTimeMillis();
        result.analysisTime = result.analysisEndTime - result.analysisStartTime;
        return result;
    }
    
    /**
     * Generate unique analysis ID
     */
    private String generateAnalysisId() {
        return "BEHAV_" + System.currentTimeMillis() + "_" + 
               Integer.toHexString((int)(Math.random() * 0xFFFF));
    }
    
    /**
     * Add a violation to the result
     */
    public void addViolation(BehavioralViolation violation) {
        if (violation != null) {
            violations.add(violation);
        }
    }
    
    /**
     * Add multiple violations to the result
     */
    public void addViolations(List<BehavioralViolation> violations) {
        if (violations != null) {
            this.violations.addAll(violations);
        }
    }
    
    /**
     * Complete the analysis and calculate final metrics
     */
    public void completeAnalysis() {
        analysisEndTime = System.currentTimeMillis();
        analysisTime = analysisEndTime - analysisStartTime;
        
        // Calculate analysis metrics
        calculateAnalysisMetrics();
    }
    
    /**
     * Calculate analysis performance and detection metrics
     */
    private void calculateAnalysisMetrics() {
        analysisMetrics.clear();
        
        // Basic metrics
        analysisMetrics.put("totalViolations", violations.size());
        analysisMetrics.put("analysisTimeMs", analysisTime);
        analysisMetrics.put("successful", successful);
        
        // Violation analysis
        if (!violations.isEmpty()) {
            Map<String, Integer> violationsByType = new HashMap<>();
            Map<String, Integer> violationsByCategory = new HashMap<>();
            Map<String, Integer> violationsByPatternType = new HashMap<>();
            int criticalViolations = 0;
            int highPriorityViolations = 0;
            float totalConfidence = 0;
            int totalSeverity = 0;
            int totalRiskScore = 0;
            
            for (BehavioralViolation violation : violations) {
                // Count by type
                String type = violation.getViolationType().name();
                violationsByType.put(type, violationsByType.getOrDefault(type, 0) + 1);
                
                // Count by category
                String category = violation.getCategory();
                violationsByCategory.put(category, violationsByCategory.getOrDefault(category, 0) + 1);
                
                // Count by pattern type
                String patternType = violation.getBehavioralPatternType();
                violationsByPatternType.put(patternType, violationsByPatternType.getOrDefault(patternType, 0) + 1);
                
                // Count priority levels
                if (violation.isCritical()) {
                    criticalViolations++;
                }
                if (violation.isHighPriority()) {
                    highPriorityViolations++;
                }
                
                // Accumulate metrics
                totalConfidence += violation.getConfidence();
                totalSeverity += violation.getSeverity();
                totalRiskScore += violation.getRiskScore();
            }
            
            analysisMetrics.put("violationsByType", violationsByType);
            analysisMetrics.put("violationsByCategory", violationsByCategory);
            analysisMetrics.put("violationsByPatternType", violationsByPatternType);
            analysisMetrics.put("criticalViolations", criticalViolations);
            analysisMetrics.put("highPriorityViolations", highPriorityViolations);
            analysisMetrics.put("averageConfidence", totalConfidence / violations.size());
            analysisMetrics.put("averageSeverity", (double)totalSeverity / violations.size());
            analysisMetrics.put("averageRiskScore", (double)totalRiskScore / violations.size());
            
            // Find highest risk violation
            BehavioralViolation highestRisk = violations.stream()
                .max((v1, v2) -> Integer.compare(v1.getRiskScore(), v2.getRiskScore()))
                .orElse(null);
            
            if (highestRisk != null) {
                analysisMetrics.put("highestRiskScore", highestRisk.getRiskScore());
                analysisMetrics.put("highestRiskType", highestRisk.getViolationType().name());
                analysisMetrics.put("highestRiskPlayer", highestRisk.getPlayerId());
            }
        }
    }
    
    /**
     * Check if analysis has violations
     */
    public boolean hasViolations() {
        return !violations.isEmpty();
    }
    
    /**
     * Check if analysis has critical violations
     */
    public boolean hasCriticalViolations() {
        return violations.stream().anyMatch(BehavioralViolation::isCritical);
    }
    
    /**
     * Check if analysis has high priority violations
     */
    public boolean hasHighPriorityViolations() {
        return violations.stream().anyMatch(BehavioralViolation::isHighPriority);
    }
    
    /**
     * Get violations sorted by risk score (highest first)
     */
    public List<BehavioralViolation> getViolationsByRisk() {
        List<BehavioralViolation> sorted = new ArrayList<>(violations);
        sorted.sort((v1, v2) -> Integer.compare(v2.getRiskScore(), v1.getRiskScore()));
        return sorted;
    }
    
    /**
     * Get critical violations only
     */
    public List<BehavioralViolation> getCriticalViolations() {
        return violations.stream()
            .filter(BehavioralViolation::isCritical)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get high priority violations only
     */
    public List<BehavioralViolation> getHighPriorityViolations() {
        return violations.stream()
            .filter(BehavioralViolation::isHighPriority)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get violations by category
     */
    public List<BehavioralViolation> getViolationsByCategory(String category) {
        return violations.stream()
            .filter(v -> category.equals(v.getCategory()))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get violations by pattern type
     */
    public List<BehavioralViolation> getViolationsByPatternType(String patternType) {
        return violations.stream()
            .filter(v -> patternType.equals(v.getBehavioralPatternType()))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get violations for specific player
     */
    public List<BehavioralViolation> getViolationsForPlayer(String playerId) {
        return violations.stream()
            .filter(v -> playerId.equals(v.getPlayerId()))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get overall risk score for this analysis (0-100)
     */
    public int getOverallRiskScore() {
        if (violations.isEmpty()) {
            return 0;
        }
        
        // Calculate weighted risk score
        int totalRisk = 0;
        int totalWeight = 0;
        
        for (BehavioralViolation violation : violations) {
            int weight = violation.isCritical() ? 3 : (violation.isHighPriority() ? 2 : 1);
            totalRisk += violation.getRiskScore() * weight;
            totalWeight += weight;
        }
        
        return totalWeight > 0 ? Math.min(100, totalRisk / totalWeight) : 0;
    }
    
    /**
     * Get analysis performance rating (0-100, higher is better)
     */
    public int getPerformanceRating() {
        if (!successful) {
            return 0;
        }
        
        // Base rating
        int rating = 100;
        
        // Penalize slow analysis (over 100ms)
        if (analysisTime > 100) {
            rating -= Math.min(30, (int)((analysisTime - 100) / 10));
        }
        
        return Math.max(0, rating);
    }
    
    /**
     * Get behavioral threat level
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
     * Get behavioral analysis status
     */
    public String getAnalysisStatus() {
        if (!successful) {
            return "ANALYSIS_FAILED";
        } else if (hasCriticalViolations()) {
            return "CRITICAL_VIOLATIONS";
        } else if (hasHighPriorityViolations()) {
            return "HIGH_PRIORITY_VIOLATIONS";
        } else if (hasViolations()) {
            return "VIOLATIONS_DETECTED";
        } else {
            return "BEHAVIOR_NORMAL";
        }
    }
    
    /**
     * Create a summary of the analysis result
     */
    public String getSummary() {
        if (!successful) {
            return String.format("Behavioral analysis failed: %s", errorMessage);
        }
        
        return String.format("Behavioral analysis completed: %d violations found (%d critical, %d high priority) in %d ms - Threat Level: %s",
                           violations.size(), getCriticalViolations().size(), 
                           getHighPriorityViolations().size(), analysisTime, getThreatLevel());
    }
    
    /**
     * Create a detailed report of the analysis result
     */
    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== BEHAVIORAL ANALYSIS REPORT ===\n");
        report.append("Analysis ID: ").append(analysisId).append("\n");
        report.append("Timestamp: ").append(new java.util.Date(analysisStartTime)).append("\n");
        report.append("Status: ").append(getAnalysisStatus()).append("\n");
        report.append("Threat Level: ").append(getThreatLevel()).append("\n");
        
        if (!successful) {
            report.append("Error: ").append(errorMessage).append("\n");
            return report.toString();
        }
        
        report.append("Analysis Time: ").append(analysisTime).append(" ms\n");
        report.append("Total Violations: ").append(violations.size()).append("\n");
        report.append("Critical Violations: ").append(getCriticalViolations().size()).append("\n");
        report.append("High Priority Violations: ").append(getHighPriorityViolations().size()).append("\n");
        report.append("Overall Risk Score: ").append(getOverallRiskScore()).append("/100\n");
        report.append("Performance Rating: ").append(getPerformanceRating()).append("/100\n");
        
        if (hasViolations()) {
            report.append("\n=== VIOLATIONS (by Risk Score) ===\n");
            List<BehavioralViolation> sortedViolations = getViolationsByRisk();
            
            for (int i = 0; i < sortedViolations.size(); i++) {
                BehavioralViolation violation = sortedViolations.get(i);
                report.append(String.format("%d. %s (Risk: %d/100)\n", 
                            i + 1, violation.getSummary(), violation.getRiskScore()));
            }
            
            // Group violations by category
            report.append("\n=== VIOLATIONS BY CATEGORY ===\n");
            Map<String, List<BehavioralViolation>> violationsByCategory = new HashMap<>();
            
            for (BehavioralViolation violation : violations) {
                String category = violation.getCategory();
                violationsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(violation);
            }
            
            for (Map.Entry<String, List<BehavioralViolation>> entry : violationsByCategory.entrySet()) {
                report.append(entry.getKey()).append(": ").append(entry.getValue().size()).append(" violations\n");
            }
            
            // Group violations by pattern type
            report.append("\n=== VIOLATIONS BY PATTERN TYPE ===\n");
            Map<String, List<BehavioralViolation>> violationsByPattern = new HashMap<>();
            
            for (BehavioralViolation violation : violations) {
                String patternType = violation.getBehavioralPatternType();
                violationsByPattern.computeIfAbsent(patternType, k -> new ArrayList<>()).add(violation);
            }
            
            for (Map.Entry<String, List<BehavioralViolation>> entry : violationsByPattern.entrySet()) {
                report.append(entry.getKey()).append(": ").append(entry.getValue().size()).append(" violations\n");
            }
        }
        
        // Add analysis metrics
        if (!analysisMetrics.isEmpty()) {
            report.append("\n=== ANALYSIS METRICS ===\n");
            for (Map.Entry<String, Object> entry : analysisMetrics.entrySet()) {
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
    public List<BehavioralViolation> getViolations() { return Collections.unmodifiableList(violations); }
    public Map<String, Object> getAnalysisMetrics() { return Collections.unmodifiableMap(analysisMetrics); }
    public long getAnalysisStartTime() { return analysisStartTime; }
    public long getAnalysisEndTime() { return analysisEndTime; }
    public long getAnalysisTime() { return analysisTime; }
    public void setAnalysisTime(long analysisTime) { this.analysisTime = analysisTime; }
    public boolean isSuccessful() { return successful; }
    public void setSuccessful(boolean successful) { this.successful = successful; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getAnalysisId() { return analysisId; }
}