package fps.benchmarking;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the result of a performance benchmark test.
 * Contains all metrics, scores, and metadata from benchmark execution.
 */
public class BenchmarkResult {
    
    private String benchmarkName;
    private String description;
    private double score;
    private double targetScore;
    private boolean passed;
    private long timestamp;
    private long durationMs;
    private Map<String, Object> metrics = new ConcurrentHashMap<>();
    private String errorMessage;
    
    public BenchmarkResult() {
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Get performance efficiency as a percentage.
     */
    public double getEfficiencyPercent() {
        if (targetScore == 0) return 0.0;
        return Math.min((score / targetScore) * 100.0, 100.0);
    }
    
    /**
     * Get score relative to target (1.0 = meets target exactly).
     */
    public double getRelativeScore() {
        if (targetScore == 0) return 0.0;
        return score / targetScore;
    }
    
    /**
     * Check if the benchmark significantly exceeds the target.
     */
    public boolean exceedsTarget() {
        return score > targetScore * 1.1; // 10% above target
    }
    
    /**
     * Get a human-readable performance grade.
     */
    public String getPerformanceGrade() {
        double efficiency = getEfficiencyPercent();
        
        if (efficiency >= 95) return "A+";
        if (efficiency >= 90) return "A";
        if (efficiency >= 85) return "B+";
        if (efficiency >= 80) return "B";
        if (efficiency >= 75) return "C+";
        if (efficiency >= 70) return "C";
        if (efficiency >= 60) return "D";
        return "F";
    }
    
    /**
     * Add a metric to the result.
     */
    public void addMetric(String key, Object value) {
        metrics.put(key, value);
    }
    
    /**
     * Get a typed metric value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetric(String key, Class<T> type) {
        Object value = metrics.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Get a metric as a double, with default value if not found or not convertible.
     */
    public double getMetricAsDouble(String key, double defaultValue) {
        Object value = metrics.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
    
    /**
     * Get a metric as a long, with default value if not found or not convertible.
     */
    public long getMetricAsLong(String key, long defaultValue) {
        Object value = metrics.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }
    
    /**
     * Check if the benchmark has an error.
     */
    public boolean hasError() {
        return errorMessage != null && !errorMessage.isEmpty();
    }
    
    @Override
    public String toString() {
        return String.format("BenchmarkResult{name='%s', score=%.2f, target=%.2f, passed=%s, grade=%s}",
            benchmarkName, score, targetScore, passed, getPerformanceGrade());
    }
    
    // Getters and setters
    public String getBenchmarkName() { return benchmarkName; }
    public void setBenchmarkName(String benchmarkName) { this.benchmarkName = benchmarkName; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    
    public double getTargetScore() { return targetScore; }
    public void setTargetScore(double targetScore) { this.targetScore = targetScore; }
    
    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    
    public Map<String, Object> getMetrics() { return new ConcurrentHashMap<>(metrics); }
    public void setMetrics(Map<String, Object> metrics) { 
        this.metrics = new ConcurrentHashMap<>(metrics); 
    }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}