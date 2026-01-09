package fps.anticheat;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * Represents a detected cheat violation with all associated evidence and metadata.
 * Used for logging, punishment decisions, and appeals processing.
 */
public class CheatViolation {
    
    private final String violationId;
    private final int playerId;
    private final ViolationType violationType;
    private final String reason;
    private final Object evidence;
    private final long timestamp;
    private final float confidence;
    
    // Context information
    private final String detectorName;
    private final String gameMode;
    private final String mapName;
    private final int playerCount;
    
    // Player state at time of violation
    private final PlayerStateSnapshot playerState;
    
    // Additional metadata
    private final Map<String, Object> metadata;
    
    // Processing status
    private ViolationStatus status;
    private String processingNotes;
    private long processedTimestamp;
    private String processedBy;
    
    public CheatViolation(int playerId, ViolationType violationType, String reason, Object evidence) {
        this(playerId, violationType, reason, evidence, 1.0f, "system");
    }
    
    public CheatViolation(int playerId, ViolationType violationType, String reason, 
                         Object evidence, float confidence, String detectorName) {
        this.violationId = UUID.randomUUID().toString();
        this.playerId = playerId;
        this.violationType = violationType;
        this.reason = reason;
        this.evidence = evidence;
        this.confidence = confidence;
        this.detectorName = detectorName;
        this.timestamp = System.currentTimeMillis();
        
        // Initialize context (would be populated from game state)
        this.gameMode = "Unknown";
        this.mapName = "Unknown";
        this.playerCount = 0;
        
        // Initialize player state snapshot
        this.playerState = new PlayerStateSnapshot();
        
        // Initialize metadata
        this.metadata = new HashMap<>();
        
        // Set initial status
        this.status = ViolationStatus.DETECTED;
        this.processingNotes = "";
        this.processedTimestamp = 0;
        this.processedBy = "";
    }
    
    /**
     * Add metadata to the violation
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
     * Check if this violation has expired based on retention policy
     */
    public boolean hasExpired() {
        long retentionTime = violationType.getEvidenceRetentionTime();
        return System.currentTimeMillis() - timestamp > retentionTime;
    }
    
    /**
     * Get the age of this violation in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - timestamp;
    }
    
    /**
     * Check if this violation requires immediate action
     */
    public boolean requiresImmediateAction() {
        return violationType.requiresImmediateAction() && confidence >= violationType.getConfidenceThreshold();
    }
    
    /**
     * Get the severity level of this violation
     */
    public ViolationType.Severity getSeverity() {
        return violationType.getSeverity();
    }
    
    /**
     * Mark violation as processed
     */
    public void markProcessed(String processedBy, String notes) {
        this.status = ViolationStatus.PROCESSED;
        this.processedBy = processedBy;
        this.processingNotes = notes;
        this.processedTimestamp = System.currentTimeMillis();
    }
    
    /**
     * Mark violation as false positive
     */
    public void markFalsePositive(String processedBy, String notes) {
        this.status = ViolationStatus.FALSE_POSITIVE;
        this.processedBy = processedBy;
        this.processingNotes = notes;
        this.processedTimestamp = System.currentTimeMillis();
    }
    
    /**
     * Mark violation as under review
     */
    public void markUnderReview(String processedBy, String notes) {
        this.status = ViolationStatus.UNDER_REVIEW;
        this.processedBy = processedBy;
        this.processingNotes = notes;
        this.processedTimestamp = System.currentTimeMillis();
    }
    
    // Getters
    public String getViolationId() { return violationId; }
    public int getPlayerId() { return playerId; }
    public ViolationType getViolationType() { return violationType; }
    public String getReason() { return reason; }
    public Object getEvidence() { return evidence; }
    public long getTimestamp() { return timestamp; }
    public float getConfidence() { return confidence; }
    public String getDetectorName() { return detectorName; }
    public String getGameMode() { return gameMode; }
    public String getMapName() { return mapName; }
    public int getPlayerCount() { return playerCount; }
    public PlayerStateSnapshot getPlayerState() { return playerState; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
    public ViolationStatus getStatus() { return status; }
    public String getProcessingNotes() { return processingNotes; }
    public long getProcessedTimestamp() { return processedTimestamp; }
    public String getProcessedBy() { return processedBy; }
    
    /**
     * Status of violation processing
     */
    public enum ViolationStatus {
        DETECTED("Detected"),
        UNDER_REVIEW("Under Review"),
        PROCESSED("Processed"),
        FALSE_POSITIVE("False Positive"),
        APPEALED("Appealed"),
        APPEAL_APPROVED("Appeal Approved"),
        APPEAL_DENIED("Appeal Denied");
        
        private final String displayName;
        
        ViolationStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    /**
     * Snapshot of player state at time of violation
     */
    public static class PlayerStateSnapshot {
        private String playerName = "";
        private float positionX = 0.0f;
        private float positionY = 0.0f;
        private float positionZ = 0.0f;
        private float rotationX = 0.0f;
        private float rotationY = 0.0f;
        private float rotationZ = 0.0f;
        private float velocityX = 0.0f;
        private float velocityY = 0.0f;
        private float velocityZ = 0.0f;
        private float health = 100.0f;
        private String activeWeapon = "";
        private int ammunition = 0;
        private boolean isAiming = false;
        private boolean isMoving = false;
        private boolean isJumping = false;
        private boolean isCrouching = false;
        private float ping = 0.0f;
        private float packetLoss = 0.0f;
        
        // Performance statistics at time of violation
        private float accuracy = 0.0f;
        private float headshotRatio = 0.0f;
        private int killCount = 0;
        private int deathCount = 0;
        private float kdRatio = 0.0f;
        
        public PlayerStateSnapshot() {
            // Default constructor
        }
        
        // Getters and setters
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        
        public float getPositionX() { return positionX; }
        public void setPositionX(float positionX) { this.positionX = positionX; }
        
        public float getPositionY() { return positionY; }
        public void setPositionY(float positionY) { this.positionY = positionY; }
        
        public float getPositionZ() { return positionZ; }
        public void setPositionZ(float positionZ) { this.positionZ = positionZ; }
        
        public float getRotationX() { return rotationX; }
        public void setRotationX(float rotationX) { this.rotationX = rotationX; }
        
        public float getRotationY() { return rotationY; }
        public void setRotationY(float rotationY) { this.rotationY = rotationY; }
        
        public float getRotationZ() { return rotationZ; }
        public void setRotationZ(float rotationZ) { this.rotationZ = rotationZ; }
        
        public float getVelocityX() { return velocityX; }
        public void setVelocityX(float velocityX) { this.velocityX = velocityX; }
        
        public float getVelocityY() { return velocityY; }
        public void setVelocityY(float velocityY) { this.velocityY = velocityY; }
        
        public float getVelocityZ() { return velocityZ; }
        public void setVelocityZ(float velocityZ) { this.velocityZ = velocityZ; }
        
        public float getHealth() { return health; }
        public void setHealth(float health) { this.health = health; }
        
        public String getActiveWeapon() { return activeWeapon; }
        public void setActiveWeapon(String activeWeapon) { this.activeWeapon = activeWeapon; }
        
        public int getAmmunition() { return ammunition; }
        public void setAmmunition(int ammunition) { this.ammunition = ammunition; }
        
        public boolean isAiming() { return isAiming; }
        public void setAiming(boolean aiming) { this.isAiming = aiming; }
        
        public boolean isMoving() { return isMoving; }
        public void setMoving(boolean moving) { this.isMoving = moving; }
        
        public boolean isJumping() { return isJumping; }
        public void setJumping(boolean jumping) { this.isJumping = jumping; }
        
        public boolean isCrouching() { return isCrouching; }
        public void setCrouching(boolean crouching) { this.isCrouching = crouching; }
        
        public float getPing() { return ping; }
        public void setPing(float ping) { this.ping = ping; }
        
        public float getPacketLoss() { return packetLoss; }
        public void setPacketLoss(float packetLoss) { this.packetLoss = packetLoss; }
        
        public float getAccuracy() { return accuracy; }
        public void setAccuracy(float accuracy) { this.accuracy = accuracy; }
        
        public float getHeadshotRatio() { return headshotRatio; }
        public void setHeadshotRatio(float headshotRatio) { this.headshotRatio = headshotRatio; }
        
        public int getKillCount() { return killCount; }
        public void setKillCount(int killCount) { this.killCount = killCount; }
        
        public int getDeathCount() { return deathCount; }
        public void setDeathCount(int deathCount) { this.deathCount = deathCount; }
        
        public float getKdRatio() { return kdRatio; }
        public void setKdRatio(float kdRatio) { this.kdRatio = kdRatio; }
    }
    
    @Override
    public String toString() {
        return String.format("CheatViolation{id='%s', playerId=%d, type=%s, confidence=%.2f, status=%s, timestamp=%d}", 
                           violationId, playerId, violationType, confidence, status, timestamp);
    }
}