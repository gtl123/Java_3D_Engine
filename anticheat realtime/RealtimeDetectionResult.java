package fps.anticheat.realtime;

import java.util.*;

/**
 * Result of real-time cheat detection for a player action.
 * Contains all detected violations and overall assessment.
 */
public class RealtimeDetectionResult {
    
    private final String playerId;
    private final long timestamp;
    private final List<CheatDetection> detections;
    private final float overallSuspiciousScore;
    
    private boolean actionRequired;
    private String recommendedAction;
    private Map<String, Object> metadata;
    
    public RealtimeDetectionResult(String playerId, long timestamp, 
                                  List<CheatDetection> detections, float overallSuspiciousScore) {
        this.playerId = playerId;
        this.timestamp = timestamp;
        this.detections = new ArrayList<>(detections);
        this.overallSuspiciousScore = Math.max(0.0f, Math.min(1.0f, overallSuspiciousScore));
        this.metadata = new HashMap<>();
        
        // Determine if action is required
        this.actionRequired = determineActionRequired();
        this.recommendedAction = determineRecommendedAction();
    }
    
    /**
     * Determine if action is required based on detections
     */
    private boolean determineActionRequired() {
        if (detections.isEmpty()) {
            return false;
        }
        
        // Action required if any critical detections or multiple actionable detections
        int criticalCount = 0;
        int actionableCount = 0;
        
        for (CheatDetection detection : detections) {
            if (detection.isCritical()) {
                criticalCount++;
            }
            if (detection.isActionable()) {
                actionableCount++;
            }
        }
        
        return criticalCount > 0 || actionableCount >= 2 || overallSuspiciousScore >= 0.8f;
    }
    
    /**
     * Determine recommended action based on detections
     */
    private String determineRecommendedAction() {
        if (!actionRequired) {
            return "MONITOR";
        }
        
        float maxRisk = detections.stream()
                .map(CheatDetection::getRiskScore)
                .max(Float::compare)
                .orElse(0.0f);
        
        if (maxRisk >= 0.9f) {
            return "IMMEDIATE_BAN";
        } else if (maxRisk >= 0.8f) {
            return "TEMPORARY_BAN";
        } else if (maxRisk >= 0.6f) {
            return "WARNING";
        } else {
            return "INCREASED_MONITORING";
        }
    }
    
    /**
     * Get detections by priority (highest first)
     */
    public List<CheatDetection> getDetectionsByPriority() {
        List<CheatDetection> sorted = new ArrayList<>(detections);
        sorted.sort((d1, d2) -> Integer.compare(d2.getPriority(), d1.getPriority()));
        return sorted;
    }
    
    /**
     * Get critical detections only
     */
    public List<CheatDetection> getCriticalDetections() {
        return detections.stream()
                .filter(CheatDetection::isCritical)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get actionable detections only
     */
    public List<CheatDetection> getActionableDetections() {
        return detections.stream()
                .filter(CheatDetection::isActionable)
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
     * Check if any violations were detected
     */
    public boolean hasViolations() {
        return !detections.isEmpty();
    }
    
    /**
     * Check if result indicates suspicious behavior
     */
    public boolean isSuspicious() {
        return hasViolations() || overallSuspiciousScore > 0.5f;
    }
    
    /**
     * Get highest confidence detection
     */
    public CheatDetection getHighestConfidenceDetection() {
        return detections.stream()
                .max(Comparator.comparing(CheatDetection::getConfidence))
                .orElse(null);
    }
    
    /**
     * Get highest risk detection
     */
    public CheatDetection getHighestRiskDetection() {
        return detections.stream()
                .max(Comparator.comparing(CheatDetection::getRiskScore))
                .orElse(null);
    }
    
    /**
     * Add metadata to this result
     */
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    /**
     * Get metadata value
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    /**
     * Create a summary of this result
     */
    public String getSummary() {
        return String.format(
            "RealtimeDetectionResult{player=%s, detections=%d, suspicious=%.2f, action=%s, age=%dms}",
            playerId, detections.size(), overallSuspiciousScore, recommendedAction, getAge()
        );
    }
    
    /**
     * Create a detailed summary of this result
     */
    public String getDetailedSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("=== Real-time Detection Result ===\n");
        summary.append("Player: ").append(playerId).append("\n");
        summary.append("Timestamp: ").append(new Date(timestamp)).append("\n");
        summary.append("Detections: ").append(detections.size()).append("\n");
        summary.append("Suspicious Score: ").append(String.format("%.2f", overallSuspiciousScore)).append("\n");
        summary.append("Action Required: ").append(actionRequired).append("\n");
        summary.append("Recommended Action: ").append(recommendedAction).append("\n");
        summary.append("Age: ").append(getAge()).append("ms\n");
        
        if (!detections.isEmpty()) {
            summary.append("\nDetections:\n");
            List<CheatDetection> sortedDetections = getDetectionsByPriority();
            for (int i = 0; i < sortedDetections.size(); i++) {
                CheatDetection detection = sortedDetections.get(i);
                summary.append(String.format("  %d. %s\n", i + 1, detection.getSummary()));
            }
        }
        
        if (!metadata.isEmpty()) {
            summary.append("\nMetadata:\n");
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                summary.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        
        return summary.toString();
    }
    
    /**
     * Convert to map for serialization
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("playerId", playerId);
        map.put("timestamp", timestamp);
        map.put("overallSuspiciousScore", overallSuspiciousScore);
        map.put("actionRequired", actionRequired);
        map.put("recommendedAction", recommendedAction);
        map.put("age", getAge());
        
        List<Map<String, Object>> detectionMaps = new ArrayList<>();
        for (CheatDetection detection : detections) {
            detectionMaps.add(detection.toMap());
        }
        map.put("detections", detectionMaps);
        
        if (!metadata.isEmpty()) {
            map.put("metadata", new HashMap<>(metadata));
        }
        
        return map;
    }
    
    @Override
    public String toString() {
        return getSummary();
    }
    
    // Getters and setters
    public String getPlayerId() { return playerId; }
    public long getTimestamp() { return timestamp; }
    public List<CheatDetection> getDetections() { return new ArrayList<>(detections); }
    public float getOverallSuspiciousScore() { return overallSuspiciousScore; }
    public boolean isActionRequired() { return actionRequired; }
    public void setActionRequired(boolean actionRequired) { this.actionRequired = actionRequired; }
    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = new HashMap<>(metadata); }
}