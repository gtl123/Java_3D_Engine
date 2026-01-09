package fps.anticheat;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * Represents a punishment action applied to a player for anti-cheat violations.
 * Contains all details about the punishment including duration, reason, and appeal status.
 */
public class PunishmentAction {
    
    private final String punishmentId;
    private final int playerId;
    private final ViolationType.PunishmentType type;
    private final String reason;
    private final long duration; // in milliseconds, Long.MAX_VALUE for permanent
    private final long issuedTimestamp;
    private final String issuedBy;
    
    // Associated violation
    private final String violationId;
    private final ViolationType violationType;
    
    // Hardware ban information
    private final String hardwareFingerprint;
    private final boolean isHardwareBan;
    
    // Status tracking
    private PunishmentStatus status;
    private long effectiveTimestamp;
    private long expirationTimestamp;
    
    // Appeal information
    private boolean appealable;
    private String appealId;
    private AppealStatus appealStatus;
    private String appealReason;
    private long appealTimestamp;
    private String appealProcessedBy;
    private String appealResponse;
    
    // Additional metadata
    private final Map<String, Object> metadata;
    
    public PunishmentAction(int playerId, ViolationType.PunishmentType type, String reason, 
                           long duration, String violationId, ViolationType violationType) {
        this(playerId, type, reason, duration, violationId, violationType, "system", null, false);
    }
    
    public PunishmentAction(int playerId, ViolationType.PunishmentType type, String reason, 
                           long duration, String violationId, ViolationType violationType,
                           String issuedBy, String hardwareFingerprint, boolean isHardwareBan) {
        this.punishmentId = UUID.randomUUID().toString();
        this.playerId = playerId;
        this.type = type;
        this.reason = reason;
        this.duration = duration;
        this.violationId = violationId;
        this.violationType = violationType;
        this.issuedBy = issuedBy;
        this.hardwareFingerprint = hardwareFingerprint;
        this.isHardwareBan = isHardwareBan;
        
        // Set timestamps
        this.issuedTimestamp = System.currentTimeMillis();
        this.effectiveTimestamp = issuedTimestamp;
        this.expirationTimestamp = isPermanent() ? Long.MAX_VALUE : issuedTimestamp + duration;
        
        // Set initial status
        this.status = PunishmentStatus.ACTIVE;
        
        // Set appealability
        this.appealable = violationType.allowsAppeals() && type != ViolationType.PunishmentType.WARNING;
        
        // Initialize appeal fields
        this.appealStatus = AppealStatus.NOT_APPEALED;
        
        // Initialize metadata
        this.metadata = new HashMap<>();
    }
    
    /**
     * Check if this punishment is currently active
     */
    public boolean isActive() {
        if (status != PunishmentStatus.ACTIVE) {
            return false;
        }
        
        if (isPermanent()) {
            return true;
        }
        
        return System.currentTimeMillis() < expirationTimestamp;
    }
    
    /**
     * Check if this punishment has expired
     */
    public boolean hasExpired() {
        if (isPermanent()) {
            return false;
        }
        
        return System.currentTimeMillis() >= expirationTimestamp;
    }
    
    /**
     * Check if this punishment is permanent
     */
    public boolean isPermanent() {
        return type.isPermanent() || duration == Long.MAX_VALUE;
    }
    
    /**
     * Get remaining time in milliseconds
     */
    public long getRemainingTime() {
        if (isPermanent()) {
            return Long.MAX_VALUE;
        }
        
        long remaining = expirationTimestamp - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
    
    /**
     * Get time since punishment was issued
     */
    public long getTimeSinceIssued() {
        return System.currentTimeMillis() - issuedTimestamp;
    }
    
    /**
     * Activate the punishment
     */
    public void activate() {
        this.status = PunishmentStatus.ACTIVE;
        this.effectiveTimestamp = System.currentTimeMillis();
        
        if (!isPermanent()) {
            this.expirationTimestamp = effectiveTimestamp + duration;
        }
    }
    
    /**
     * Revoke the punishment
     */
    public void revoke(String reason) {
        this.status = PunishmentStatus.REVOKED;
        addMetadata("revoke_reason", reason);
        addMetadata("revoke_timestamp", System.currentTimeMillis());
    }
    
    /**
     * Mark punishment as expired
     */
    public void expire() {
        this.status = PunishmentStatus.EXPIRED;
    }
    
    /**
     * Submit an appeal for this punishment
     */
    public void submitAppeal(String appealReason) {
        if (!appealable) {
            throw new IllegalStateException("This punishment is not appealable");
        }
        
        if (appealStatus != AppealStatus.NOT_APPEALED) {
            throw new IllegalStateException("Appeal already submitted");
        }
        
        this.appealId = UUID.randomUUID().toString();
        this.appealReason = appealReason;
        this.appealTimestamp = System.currentTimeMillis();
        this.appealStatus = AppealStatus.PENDING;
    }
    
    /**
     * Process an appeal
     */
    public void processAppeal(boolean approved, String response, String processedBy) {
        if (appealStatus != AppealStatus.PENDING) {
            throw new IllegalStateException("No pending appeal to process");
        }
        
        this.appealStatus = approved ? AppealStatus.APPROVED : AppealStatus.DENIED;
        this.appealResponse = response;
        this.appealProcessedBy = processedBy;
        
        if (approved) {
            revoke("Appeal approved: " + response);
        }
        
        addMetadata("appeal_processed_timestamp", System.currentTimeMillis());
    }
    
    /**
     * Add metadata to the punishment
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
     * Get severity level of this punishment
     */
    public SeverityLevel getSeverityLevel() {
        switch (type) {
            case WARNING:
                return SeverityLevel.LOW;
            case KICK:
                return SeverityLevel.LOW;
            case TEMPORARY_BAN:
                return SeverityLevel.MEDIUM;
            case ACCOUNT_SUSPENSION:
                return SeverityLevel.MEDIUM;
            case PERMANENT_BAN:
                return SeverityLevel.HIGH;
            case HARDWARE_BAN:
                return SeverityLevel.CRITICAL;
            default:
                return SeverityLevel.LOW;
        }
    }
    
    /**
     * Create a formatted description of the punishment
     */
    public String getDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append(type.getDisplayName());
        
        if (!isPermanent()) {
            long remainingMs = getRemainingTime();
            if (remainingMs > 0) {
                long hours = remainingMs / (1000 * 60 * 60);
                long minutes = (remainingMs % (1000 * 60 * 60)) / (1000 * 60);
                desc.append(" (").append(hours).append("h ").append(minutes).append("m remaining)");
            }
        }
        
        desc.append(" - ").append(reason);
        
        if (isHardwareBan) {
            desc.append(" [Hardware Ban]");
        }
        
        return desc.toString();
    }
    
    // Getters
    public String getPunishmentId() { return punishmentId; }
    public int getPlayerId() { return playerId; }
    public ViolationType.PunishmentType getType() { return type; }
    public String getReason() { return reason; }
    public long getDuration() { return duration; }
    public long getIssuedTimestamp() { return issuedTimestamp; }
    public String getIssuedBy() { return issuedBy; }
    public String getViolationId() { return violationId; }
    public ViolationType getViolationType() { return violationType; }
    public String getHardwareFingerprint() { return hardwareFingerprint; }
    public boolean isHardwareBan() { return isHardwareBan; }
    public PunishmentStatus getStatus() { return status; }
    public long getEffectiveTimestamp() { return effectiveTimestamp; }
    public long getExpirationTimestamp() { return expirationTimestamp; }
    public boolean isAppealable() { return appealable; }
    public String getAppealId() { return appealId; }
    public AppealStatus getAppealStatus() { return appealStatus; }
    public String getAppealReason() { return appealReason; }
    public long getAppealTimestamp() { return appealTimestamp; }
    public String getAppealProcessedBy() { return appealProcessedBy; }
    public String getAppealResponse() { return appealResponse; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
    
    /**
     * Status of a punishment
     */
    public enum PunishmentStatus {
        PENDING("Pending"),
        ACTIVE("Active"),
        EXPIRED("Expired"),
        REVOKED("Revoked"),
        APPEALED("Appealed");
        
        private final String displayName;
        
        PunishmentStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    /**
     * Status of an appeal
     */
    public enum AppealStatus {
        NOT_APPEALED("Not Appealed"),
        PENDING("Pending Review"),
        APPROVED("Approved"),
        DENIED("Denied");
        
        private final String displayName;
        
        AppealStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    /**
     * Severity levels for punishments
     */
    public enum SeverityLevel {
        LOW("Low"),
        MEDIUM("Medium"),
        HIGH("High"),
        CRITICAL("Critical");
        
        private final String displayName;
        
        SeverityLevel(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    @Override
    public String toString() {
        return String.format("PunishmentAction{id='%s', playerId=%d, type=%s, status=%s, active=%s, permanent=%s}", 
                           punishmentId, playerId, type, status, isActive(), isPermanent());
    }
}