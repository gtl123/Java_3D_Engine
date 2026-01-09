package fps.anticheat.statistical;

import java.util.*;

/**
 * Represents the result of statistical analysis performed on player data.
 * Contains violations, confidence metrics, and analysis metadata.
 */
public class StatisticalAnalysisResult {
    
    private final String playerId;
    private final long timestamp;
    private final String analysisType;
    private final boolean suspicious;
    
    private final List<StatisticalViolation> violations;
    private final Map<String, Float> metrics;
    private final Map<String, String> metadata;
    
    private float overallConfidence;
    private float riskScore;
    private String summary;
    private boolean actionRequired;
    
    /**
     * Create a statistical analysis result
     */
    public StatisticalAnalysisResult(String playerId, String analysisType, boolean suspicious) {
        this.playerId = playerId;
        this.analysisType = analysisType;
        this.suspicious = suspicious;
        this.timestamp = System.currentTimeMillis();
        
        this.violations = new ArrayList<>();
        this.metrics = new HashMap<>();
        this.metadata = new HashMap<>();
        
        this.overallConfidence = 0.0f;
        this.riskScore = 0.0f;
        this.actionRequired = false;
    }
    
    /**
     * Add a violation to this result
     */
    public void addViolation(StatisticalViolation violation) {
        violations.add(violation);
        recalculateMetrics();
    }
    
    /**
     * Add multiple violations to this result
     */
    public void addViolations(List<StatisticalViolation> newViolations) {
        violations.addAll(newViolations);
        recalculateMetrics();
    }
    
    /**
     * Add a metric to this result
     */
    public void addMetric(String name, float value) {
        metrics.put(name, value);
    }
    
    /**
     * Add metadata to this result
     */
    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }
    
    /**
     * Recalculate overall metrics based on violations
     */
    private void recalculateMetrics() {
        if (violations.isEmpty()) {
            overallConfidence = 0.0f;
            riskScore = 0.0f;
            actionRequired = false;
            return;
        }
        
        // Calculate overall confidence as weighted average
        float totalConfidence = 0.0f;
        float totalWeight = 0.0f;
        
        for (StatisticalViolation violation : violations) {
            float weight = violation.getSeverity();
            totalConfidence += violation.getConfidence() * weight;
            totalWeight += weight;
        }
        
        overallConfidence = totalWeight > 0 ? totalConfidence / totalWeight : 0.0f;
        
        // Calculate risk score
        riskScore = calculateRiskScore();
        
        // Determine if action is required
        actionRequired = determineActionRequired();
        
        // Update summary
        updateSummary();
    }
    
    /**
     * Calculate overall risk score
     */
    private float calculateRiskScore() {
        if (violations.isEmpty()) {
            return 0.0f;
        }
        
        float maxRisk = 0.0f;
        float avgRisk = 0.0f;
        
        for (StatisticalViolation violation : violations) {
            float risk = violation.getRiskScore();
            maxRisk = Math.max(maxRisk, risk);
            avgRisk += risk;
        }
        
        avgRisk /= violations.size();
        
        // Combine max and average risk (weighted toward max for safety)
        return (maxRisk * 0.7f) + (avgRisk * 0.3f);
    }
    
    /**
     * Determine if action is required based on violations
     */
    private boolean determineActionRequired() {
        if (violations.isEmpty()) {
            return false;
        }
        
        // Action required if any critical violations or multiple actionable violations
        int criticalCount = 0;
        int actionableCount = 0;
        
        for (StatisticalViolation violation : violations) {
            if (violation.isCritical()) {
                criticalCount++;
            }
            if (violation.isActionable()) {
                actionableCount++;
            }
        }
        
        return criticalCount > 0 || actionableCount >= 2 || riskScore >= 0.8f;
    }
    
    /**
     * Update summary based on current state
     */
    private void updateSummary() {
        if (violations.isEmpty()) {
            summary = "No statistical violations detected";
            return;
        }
        
        int criticalCount = (int) violations.stream().mapToLong(v -> v.isCritical() ? 1 : 0).sum();
        int actionableCount = (int) violations.stream().mapToLong(v -> v.isActionable() ? 1 : 0).sum();
        
        summary = String.format(
            "Statistical analysis detected %d violations (%d critical, %d actionable). " +
            "Risk score: %.2f, Confidence: %.2f, Action required: %s",
            violations.size(), criticalCount, actionableCount, 
            riskScore, overallConfidence, actionRequired
        );
    }
    
    /**
     * Get violations by priority (highest first)
     */
    public List<StatisticalViolation> getViolationsByPriority() {
        List<StatisticalViolation> sorted = new ArrayList<>(violations);
        sorted.sort((v1, v2) -> Integer.compare(v2.getPriority(), v1.getPriority()));
        return sorted;
    }
    
    /**
     * Get critical violations only
     */
    public List<StatisticalViolation> getCriticalViolations() {
        return violations.stream()
                .filter(StatisticalViolation::isCritical)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get actionable violations only
     */
    public List<StatisticalViolation> getActionableViolations() {
        return violations.stream()
                .filter(StatisticalViolation::isActionable)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get result age in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - timestamp;
    }
    
    /**
     * Check if result is recent (within specified time)
     */
    public boolean isRecent(long maxAge) {
        return getAge() <= maxAge;
    }
    
    /**
     * Get a metric value
     */
    public Float getMetric(String name) {
        return metrics.get(name);
    }
    
    /**
     * Get metadata value
     */
    public String getMetadata(String key) {
        return metadata.get(key);
    }
    
    /**
     * Create a detailed summary of this result
     */
    public String getDetailedSummary() {
        return String.format(
            "StatisticalAnalysisResult{player=%s, type=%s, suspicious=%s, violations=%d, " +
            "confidence=%.2f, risk=%.2f, actionRequired=%s, age=%dms}",
            playerId, analysisType, suspicious, violations.size(), 
            overallConfidence, riskScore, actionRequired, getAge()
        );
    }
    
    @Override
    public String toString() {
        return summary != null ? summary : getDetailedSummary();
    }
    
    // Getters
    public String getPlayerId() { return playerId; }
    public long getTimestamp() { return timestamp; }
    public String getAnalysisType() { return analysisType; }
    public boolean isSuspicious() { return suspicious; }
    public List<StatisticalViolation> getViolations() { return new ArrayList<>(violations); }
    public Map<String, Float> getMetrics() { return new HashMap<>(metrics); }
    public Map<String, String> getMetadata() { return new HashMap<>(metadata); }
    public float getOverallConfidence() { return overallConfidence; }
    public float getRiskScore() { return riskScore; }
    public String getSummary() { return summary; }
    public boolean isActionRequired() { return actionRequired; }
}