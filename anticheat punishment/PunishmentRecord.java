package fps.anticheat.punishment;

import fps.anticheat.ValidationResult;
import java.util.List;

/**
 * Records a punishment issued to a player for historical tracking.
 */
public class PunishmentRecord {
    
    private final String playerId;
    private final PunishmentType punishmentType;
    private final String reason;
    private final long timestamp;
    private final long duration; // -1 for permanent
    private final List<ValidationResult> violations;
    
    public PunishmentRecord(String playerId, PunishmentType punishmentType, String reason, 
                          long timestamp, long duration, List<ValidationResult> violations) {
        this.playerId = playerId;
        this.punishmentType = punishmentType;
        this.reason = reason;
        this.timestamp = timestamp;
        this.duration = duration;
        this.violations = violations;
    }
    
    /**
     * Check if this was a ban (not just a warning)
     */
    public boolean isBan() {
        return punishmentType != PunishmentType.WARNING;
    }
    
    /**
     * Get severity score of this punishment
     */
    public float getSeverityScore() {
        switch (punishmentType) {
            case WARNING:
                return 0.2f;
            case TEMPORARY_BAN:
                return 0.5f;
            case PERMANENT_BAN:
                return 0.8f;
            case HARDWARE_BAN:
                return 1.0f;
            default:
                return 0.0f;
        }
    }
    
    /**
     * Get total violation severity from associated violations
     */
    public float getTotalViolationSeverity() {
        if (violations == null || violations.isEmpty()) {
            return 0.0f;
        }
        
        return (float) violations.stream()
                .mapToDouble(ValidationResult::getSeverity)
                .sum();
    }
    
    /**
     * Get violation count
     */
    public int getViolationCount() {
        return violations != null ? violations.size() : 0;
    }
    
    // Getters
    public String getPlayerId() { return playerId; }
    public PunishmentType getPunishmentType() { return punishmentType; }
    public String getReason() { return reason; }
    public long getTimestamp() { return timestamp; }
    public long getDuration() { return duration; }
    public List<ValidationResult> getViolations() { return violations; }
    
    @Override
    public String toString() {
        return String.format("PunishmentRecord{playerId='%s', type=%s, reason='%s', timestamp=%d, duration=%d, violations=%d}", 
                           playerId, punishmentType, reason, timestamp, duration, getViolationCount());
    }
}