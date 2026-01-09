package fps.anticheat.realtime;

import fps.anticheat.ViolationType;

import java.util.*;

/**
 * Represents a detected cheat or suspicious behavior in real-time.
 * Contains detailed information about the detection including confidence, evidence, and context.
 */
public class CheatDetection {
    
    private final ViolationType violationType;
    private final String description;
    private final float confidence;
    private final String detectorName;
    private final long timestamp;
    
    private String evidence;
    private Map<String, Object> metadata;
    private float severity;
    private boolean confirmed;
    private String playerId;
    
    public CheatDetection(ViolationType violationType, String description, float confidence, String detectorName) {
        this.violationType = violationType;
        this.description = description;
        this.confidence = Math.max(0.0f, Math.min(1.0f, confidence));
        this.detectorName = detectorName;
        this.timestamp = System.currentTimeMillis();
        
        this.metadata = new HashMap<>();
        this.severity = calculateSeverity(violationType, confidence);
        this.confirmed = false;
    }
    
    public CheatDetection(ViolationType violationType, String description, float confidence, 
                         String detectorName, String playerId) {
        this(violationType, description, confidence, detectorName);
        this.playerId = playerId;
    }
    
    /**
     * Calculate severity based on violation type and confidence
     */
    private float calculateSeverity(ViolationType violationType, float confidence) {
        float baseSeverity;
        
        switch (violationType) {
            case AIMBOT:
            case WALLHACK:
            case SPEED_HACK:
                baseSeverity = 0.9f;
                break;
            case TRIGGER_BOT:
            case NO_RECOIL:
            case ESP:
                baseSeverity = 0.8f;
                break;
            case IMPOSSIBLE_ACCURACY:
            case IMPOSSIBLE_HEADSHOT_RATE:
                baseSeverity = 0.85f;
                break;
            case STATISTICAL_ANOMALY:
            case OUTLIER_BEHAVIOR:
                baseSeverity = 0.6f;
                break;
            case SUSPICIOUS_MOVEMENT:
            case SUSPICIOUS_AIM:
                baseSeverity = 0.5f;
                break;
            default:
                baseSeverity = 0.4f;
                break;
        }
        
        // Adjust severity based on confidence
        return baseSeverity * confidence;
    }
    
    /**
     * Get detection age in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - timestamp;
    }
    
    /**
     * Check if detection is recent (within specified time)
     */
    public boolean isRecent(long maxAge) {
        return getAge() <= maxAge;
    }
    
    /**
     * Check if detection is critical (high severity and confidence)
     */
    public boolean isCritical() {
        return severity >= 0.8f && confidence >= 0.8f;
    }
    
    /**
     * Check if detection is actionable (sufficient confidence for action)
     */
    public boolean isActionable() {
        return confidence >= 0.7f;
    }
    
    /**
     * Add evidence to this detection
     */
    public void addEvidence(String newEvidence) {
        if (this.evidence == null || this.evidence.isEmpty()) {
            this.evidence = newEvidence;
        } else {
            this.evidence += "; " + newEvidence;
        }
    }
    
    /**
     * Add metadata to this detection
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
     * Confirm this detection as valid
     */
    public void confirm() {
        this.confirmed = true;
    }
    
    /**
     * Get risk score combining severity and confidence
     */
    public float getRiskScore() {
        return (severity * 0.6f) + (confidence * 0.4f);
    }
    
    /**
     * Get priority for processing (higher is more urgent)
     */
    public int getPriority() {
        float riskScore = getRiskScore();
        
        if (riskScore >= 0.9f) return 5; // Critical
        if (riskScore >= 0.8f) return 4; // High
        if (riskScore >= 0.6f) return 3; // Medium
        if (riskScore >= 0.4f) return 2; // Low
        return 1; // Minimal
    }
    
    /**
     * Create a detailed summary of this detection
     */
    public String getDetailedSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("CheatDetection{");
        summary.append("type=").append(violationType);
        summary.append(", detector=").append(detectorName);
        summary.append(", confidence=").append(String.format("%.2f", confidence));
        summary.append(", severity=").append(String.format("%.2f", severity));
        summary.append(", risk=").append(String.format("%.2f", getRiskScore()));
        summary.append(", priority=").append(getPriority());
        summary.append(", confirmed=").append(confirmed);
        summary.append(", age=").append(getAge()).append("ms");
        
        if (playerId != null) {
            summary.append(", player=").append(playerId);
        }
        
        summary.append(", description='").append(description).append("'");
        
        if (evidence != null && !evidence.isEmpty()) {
            summary.append(", evidence='").append(evidence).append("'");
        }
        
        if (!metadata.isEmpty()) {
            summary.append(", metadata=").append(metadata);
        }
        
        summary.append("}");
        return summary.toString();
    }
    
    /**
     * Create a brief summary of this detection
     */
    public String getSummary() {
        return String.format("CheatDetection{type=%s, confidence=%.2f, risk=%.2f}",
                           violationType, confidence, getRiskScore());
    }
    
    /**
     * Check if this detection matches another detection (for deduplication)
     */
    public boolean matches(CheatDetection other) {
        if (other == null) return false;
        
        return violationType == other.violationType &&
               detectorName.equals(other.detectorName) &&
               Objects.equals(playerId, other.playerId) &&
               Math.abs(confidence - other.confidence) < 0.1f &&
               Math.abs(timestamp - other.timestamp) < 5000; // Within 5 seconds
    }
    
    /**
     * Merge this detection with another similar detection
     */
    public CheatDetection mergeWith(CheatDetection other) {
        if (!matches(other)) {
            return this; // Cannot merge dissimilar detections
        }
        
        // Create merged detection with higher confidence
        float mergedConfidence = Math.max(confidence, other.confidence);
        CheatDetection merged = new CheatDetection(violationType, description, mergedConfidence, detectorName, playerId);
        
        // Merge evidence
        if (evidence != null) merged.addEvidence(evidence);
        if (other.evidence != null) merged.addEvidence(other.evidence);
        
        // Merge metadata
        merged.metadata.putAll(metadata);
        merged.metadata.putAll(other.metadata);
        
        // Use higher severity
        merged.severity = Math.max(severity, other.severity);
        
        // Confirmed if either is confirmed
        merged.confirmed = confirmed || other.confirmed;
        
        return merged;
    }
    
    /**
     * Convert to map for serialization
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("violationType", violationType.name());
        map.put("description", description);
        map.put("confidence", confidence);
        map.put("detectorName", detectorName);
        map.put("timestamp", timestamp);
        map.put("severity", severity);
        map.put("confirmed", confirmed);
        map.put("riskScore", getRiskScore());
        map.put("priority", getPriority());
        
        if (playerId != null) {
            map.put("playerId", playerId);
        }
        
        if (evidence != null) {
            map.put("evidence", evidence);
        }
        
        if (!metadata.isEmpty()) {
            map.put("metadata", new HashMap<>(metadata));
        }
        
        return map;
    }
    
    @Override
    public String toString() {
        return getSummary();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        CheatDetection that = (CheatDetection) obj;
        return Float.compare(that.confidence, confidence) == 0 &&
               timestamp == that.timestamp &&
               violationType == that.violationType &&
               Objects.equals(description, that.description) &&
               Objects.equals(detectorName, that.detectorName) &&
               Objects.equals(playerId, that.playerId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(violationType, description, confidence, detectorName, timestamp, playerId);
    }
    
    // Getters and setters
    public ViolationType getViolationType() { return violationType; }
    public String getDescription() { return description; }
    public float getConfidence() { return confidence; }
    public String getDetectorName() { return detectorName; }
    public long getTimestamp() { return timestamp; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = new HashMap<>(metadata); }
    public float getSeverity() { return severity; }
    public void setSeverity(float severity) { this.severity = Math.max(0.0f, Math.min(1.0f, severity)); }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
}