package fps.anticheat;

/**
 * Types of anti-cheat violations that can be detected.
 * Each type has associated severity and detection methods.
 */
public enum ViolationType {
    
    // Server-side validation violations
    SERVER_VALIDATION("Server Validation", Category.SERVER_SIDE, Severity.HIGH),
    IMPOSSIBLE_MOVEMENT("Impossible Movement", Category.SERVER_SIDE, Severity.HIGH),
    IMPOSSIBLE_SHOT("Impossible Shot", Category.SERVER_SIDE, Severity.CRITICAL),
    RATE_LIMIT_EXCEEDED("Rate Limit Exceeded", Category.SERVER_SIDE, Severity.MEDIUM),
    PHYSICS_VIOLATION("Physics Violation", Category.SERVER_SIDE, Severity.HIGH),
    
    // Real-time cheat detection
    AIMBOT("Aimbot", Category.REALTIME, Severity.CRITICAL),
    WALLHACK("Wallhack", Category.REALTIME, Severity.CRITICAL),
    SPEED_HACK("Speed Hack", Category.REALTIME, Severity.CRITICAL),
    TRIGGER_BOT("Trigger Bot", Category.REALTIME, Severity.HIGH),
    NO_RECOIL("No Recoil", Category.REALTIME, Severity.HIGH),
    ESP("ESP/Radar Hack", Category.REALTIME, Severity.CRITICAL),
    AUTO_AIM("Auto Aim", Category.REALTIME, Severity.CRITICAL),
    
    // Behavioral analysis violations
    BEHAVIORAL_ANALYSIS("Behavioral Analysis", Category.BEHAVIORAL, Severity.MEDIUM),
    INHUMAN_REACTIONS("Inhuman Reactions", Category.BEHAVIORAL, Severity.HIGH),
    ROBOTIC_MOVEMENT("Robotic Movement", Category.BEHAVIORAL, Severity.MEDIUM),
    PERFECT_TRACKING("Perfect Tracking", Category.BEHAVIORAL, Severity.HIGH),
    SUSPICIOUS_PATTERNS("Suspicious Patterns", Category.BEHAVIORAL, Severity.MEDIUM),
    
    // Statistical anomalies
    STATISTICAL_ANOMALY("Statistical Anomaly", Category.STATISTICAL, Severity.MEDIUM),
    IMPOSSIBLE_ACCURACY("Impossible Accuracy", Category.STATISTICAL, Severity.HIGH),
    IMPOSSIBLE_HEADSHOT_RATIO("Impossible Headshot Ratio", Category.STATISTICAL, Severity.HIGH),
    SUPERHUMAN_PERFORMANCE("Superhuman Performance", Category.STATISTICAL, Severity.HIGH),
    CONSISTENCY_ANOMALY("Consistency Anomaly", Category.STATISTICAL, Severity.MEDIUM),
    
    // Hardware and system violations
    HARDWARE_VALIDATION("Hardware Validation", Category.HARDWARE, Severity.MEDIUM),
    VIRTUAL_MACHINE_DETECTED("Virtual Machine Detected", Category.HARDWARE, Severity.HIGH),
    DEBUGGER_DETECTED("Debugger Detected", Category.HARDWARE, Severity.CRITICAL),
    MEMORY_TAMPERING("Memory Tampering", Category.HARDWARE, Severity.CRITICAL),
    MEMORY_MANIPULATION("Memory Manipulation", Category.HARDWARE, Severity.CRITICAL),
    PROCESS_INJECTION("Process Injection", Category.HARDWARE, Severity.CRITICAL),
    BANNED_SOFTWARE("Banned Software", Category.HARDWARE, Severity.HIGH),
    DEBUGGING_TOOLS("Debugging Tools", Category.HARDWARE, Severity.HIGH),
    
    // Client-side monitoring violations
    CLIENT_MODIFICATION("Client Modification", Category.CLIENT_SIDE, Severity.HIGH),
    FILE_TAMPERING("File Tampering", Category.CLIENT_SIDE, Severity.HIGH),
    FILE_SYSTEM_MANIPULATION("File System Manipulation", Category.CLIENT_SIDE, Severity.HIGH),
    CHECKSUM_MISMATCH("Checksum Mismatch", Category.CLIENT_SIDE, Severity.MEDIUM),
    UNAUTHORIZED_DLL("Unauthorized DLL", Category.CLIENT_SIDE, Severity.HIGH),
    HOOK_DETECTED("Hook Detected", Category.CLIENT_SIDE, Severity.HIGH),
    
    // Network and protocol violations
    NETWORK_MANIPULATION("Network Manipulation", Category.NETWORK, Severity.HIGH),
    PACKET_TAMPERING("Packet Tampering", Category.NETWORK, Severity.HIGH),
    PROTOCOL_VIOLATION("Protocol Violation", Category.NETWORK, Severity.MEDIUM),
    TIMING_MANIPULATION("Timing Manipulation", Category.NETWORK, Severity.HIGH),
    
    // General violations
    GENERAL("General Violation", Category.GENERAL, Severity.LOW),
    MULTIPLE_VIOLATIONS("Multiple Violations", Category.GENERAL, Severity.HIGH),
    REPEATED_OFFENSE("Repeated Offense", Category.GENERAL, Severity.HIGH);
    
    private final String displayName;
    private final Category category;
    private final Severity severity;
    
    ViolationType(String displayName, Category category, Severity severity) {
        this.displayName = displayName;
        this.category = category;
        this.severity = severity;
    }
    
    /**
     * Get the base punishment recommendation for this violation type
     */
    public PunishmentType getRecommendedPunishment() {
        switch (severity) {
            case LOW:
                return PunishmentType.WARNING;
            case MEDIUM:
                return PunishmentType.TEMPORARY_BAN;
            case HIGH:
                return PunishmentType.TEMPORARY_BAN;
            case CRITICAL:
                return PunishmentType.PERMANENT_BAN;
            default:
                return PunishmentType.WARNING;
        }
    }
    
    /**
     * Get the detection confidence threshold for this violation type
     */
    public float getConfidenceThreshold() {
        switch (severity) {
            case LOW:
                return 0.6f;
            case MEDIUM:
                return 0.7f;
            case HIGH:
                return 0.8f;
            case CRITICAL:
                return 0.9f;
            default:
                return 0.8f;
        }
    }
    
    /**
     * Check if this violation type should trigger immediate action
     */
    public boolean requiresImmediateAction() {
        return severity == Severity.CRITICAL || 
               this == DEBUGGER_DETECTED || 
               this == MEMORY_TAMPERING || 
               this == PROCESS_INJECTION;
    }
    
    /**
     * Check if this violation type allows for appeals
     */
    public boolean allowsAppeals() {
        return severity != Severity.CRITICAL || 
               category == Category.STATISTICAL || 
               category == Category.BEHAVIORAL;
    }
    
    /**
     * Get the evidence retention time for this violation type
     */
    public long getEvidenceRetentionTime() {
        switch (severity) {
            case LOW:
                return 7 * 24 * 60 * 60 * 1000L; // 7 days
            case MEDIUM:
                return 30 * 24 * 60 * 60 * 1000L; // 30 days
            case HIGH:
                return 90 * 24 * 60 * 60 * 1000L; // 90 days
            case CRITICAL:
                return 365 * 24 * 60 * 60 * 1000L; // 1 year
            default:
                return 30 * 24 * 60 * 60 * 1000L; // 30 days
        }
    }
    
    // Getters
    public String getDisplayName() { return displayName; }
    public Category getCategory() { return category; }
    public Severity getSeverity() { return severity; }
    
    /**
     * Categories of violations based on detection method
     */
    public enum Category {
        SERVER_SIDE("Server-Side Validation"),
        REALTIME("Real-Time Detection"),
        BEHAVIORAL("Behavioral Analysis"),
        STATISTICAL("Statistical Analysis"),
        HARDWARE("Hardware Validation"),
        CLIENT_SIDE("Client-Side Monitoring"),
        NETWORK("Network Monitoring"),
        GENERAL("General");
        
        private final String displayName;
        
        Category(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() { return displayName; }
    }
    
    /**
     * Severity levels for violations
     */
    public enum Severity {
        LOW(1, "Low"),
        MEDIUM(2, "Medium"),
        HIGH(3, "High"),
        CRITICAL(4, "Critical");
        
        private final int level;
        private final String displayName;
        
        Severity(int level, String displayName) {
            this.level = level;
            this.displayName = displayName;
        }
        
        public int getLevel() { return level; }
        public String getDisplayName() { return displayName; }
        
        public boolean isMoreSevereThan(Severity other) {
            return this.level > other.level;
        }
    }
    
    /**
     * Types of punishments that can be applied
     */
    public enum PunishmentType {
        WARNING("Warning", 0),
        KICK("Kick", 0),
        TEMPORARY_BAN("Temporary Ban", 24 * 60 * 60 * 1000L), // 24 hours default
        PERMANENT_BAN("Permanent Ban", Long.MAX_VALUE),
        HARDWARE_BAN("Hardware Ban", Long.MAX_VALUE),
        ACCOUNT_SUSPENSION("Account Suspension", 7 * 24 * 60 * 60 * 1000L); // 7 days default
        
        private final String displayName;
        private final long defaultDuration; // in milliseconds
        
        PunishmentType(String displayName, long defaultDuration) {
            this.displayName = displayName;
            this.defaultDuration = defaultDuration;
        }
        
        public String getDisplayName() { return displayName; }
        public long getDefaultDuration() { return defaultDuration; }
        
        public boolean isPermanent() {
            return this == PERMANENT_BAN || this == HARDWARE_BAN;
        }
        
        public boolean isTemporary() {
            return this == TEMPORARY_BAN || this == ACCOUNT_SUSPENSION;
        }
    }
}