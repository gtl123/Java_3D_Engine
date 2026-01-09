package fps.map.boundaries;

/**
 * Defines the severity level of a boundary violation or proximity.
 * Used to determine appropriate warnings, effects, and actions.
 */
public enum BoundarySeverity {
    /**
     * No immediate concern - player is well within safe boundaries.
     * Distance > 10 units from boundary edge.
     */
    LOW(0, "Safe", "rgba(0, 255, 0, 0.3)", false, false),
    
    /**
     * Minor concern - player is approaching boundary.
     * Distance 5-10 units from boundary edge.
     */
    MEDIUM(1, "Caution", "rgba(255, 255, 0, 0.5)", true, false),
    
    /**
     * Significant concern - player is very close to boundary.
     * Distance 1-5 units from boundary edge.
     */
    HIGH(2, "Warning", "rgba(255, 165, 0, 0.7)", true, true),
    
    /**
     * Critical concern - player is at or beyond boundary.
     * Distance <= 1 unit from boundary edge or inside restricted area.
     */
    CRITICAL(3, "Critical", "rgba(255, 0, 0, 0.9)", true, true);
    
    private final int level;
    private final String displayName;
    private final String colorCode;
    private final boolean showWarning;
    private final boolean playSound;
    
    BoundarySeverity(int level, String displayName, String colorCode, boolean showWarning, boolean playSound) {
        this.level = level;
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.showWarning = showWarning;
        this.playSound = playSound;
    }
    
    /**
     * Get the numeric severity level (0-3)
     */
    public int getLevel() {
        return level;
    }
    
    /**
     * Get the human-readable display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Get the RGBA color code for UI display
     */
    public String getColorCode() {
        return colorCode;
    }
    
    /**
     * Check if warnings should be shown at this severity level
     */
    public boolean shouldShowWarning() {
        return showWarning;
    }
    
    /**
     * Check if sound effects should be played at this severity level
     */
    public boolean shouldPlaySound() {
        return playSound;
    }
    
    /**
     * Check if this severity is higher than another
     */
    public boolean isHigherThan(BoundarySeverity other) {
        return this.level > other.level;
    }
    
    /**
     * Check if this severity is lower than another
     */
    public boolean isLowerThan(BoundarySeverity other) {
        return this.level < other.level;
    }
    
    /**
     * Get the warning frequency in seconds for this severity level
     */
    public float getWarningFrequency() {
        switch (this) {
            case LOW:
                return 0.0f; // No warnings
            case MEDIUM:
                return 5.0f; // Every 5 seconds
            case HIGH:
                return 2.0f; // Every 2 seconds
            case CRITICAL:
                return 0.5f; // Every 0.5 seconds
            default:
                return 3.0f;
        }
    }
    
    /**
     * Get the UI opacity for boundary visualization
     */
    public float getUIOpacity() {
        switch (this) {
            case LOW:
                return 0.1f;
            case MEDIUM:
                return 0.3f;
            case HIGH:
                return 0.6f;
            case CRITICAL:
                return 0.9f;
            default:
                return 0.5f;
        }
    }
    
    /**
     * Get the screen shake intensity for this severity level
     */
    public float getShakeIntensity() {
        switch (this) {
            case LOW:
                return 0.0f;
            case MEDIUM:
                return 0.1f;
            case HIGH:
                return 0.3f;
            case CRITICAL:
                return 0.7f;
            default:
                return 0.0f;
        }
    }
    
    /**
     * Get the audio volume multiplier for this severity level
     */
    public float getAudioVolume() {
        switch (this) {
            case LOW:
                return 0.0f;
            case MEDIUM:
                return 0.3f;
            case HIGH:
                return 0.6f;
            case CRITICAL:
                return 1.0f;
            default:
                return 0.5f;
        }
    }
    
    /**
     * Get the particle effect intensity for this severity level
     */
    public float getParticleIntensity() {
        switch (this) {
            case LOW:
                return 0.0f;
            case MEDIUM:
                return 0.2f;
            case HIGH:
                return 0.5f;
            case CRITICAL:
                return 1.0f;
            default:
                return 0.3f;
        }
    }
    
    /**
     * Check if immediate action should be taken at this severity level
     */
    public boolean requiresImmediateAction() {
        return this == CRITICAL;
    }
    
    /**
     * Get the recommended action delay for this severity level
     */
    public float getActionDelay() {
        switch (this) {
            case LOW:
                return Float.MAX_VALUE; // No action needed
            case MEDIUM:
                return 3.0f; // Give player time to react
            case HIGH:
                return 1.5f; // Shorter time to react
            case CRITICAL:
                return 0.5f; // Immediate action
            default:
                return 2.0f;
        }
    }
    
    /**
     * Get severity level based on distance to boundary
     */
    public static BoundarySeverity fromDistance(float distance) {
        if (distance <= 1.0f) {
            return CRITICAL;
        } else if (distance <= 5.0f) {
            return HIGH;
        } else if (distance <= 10.0f) {
            return MEDIUM;
        } else {
            return LOW;
        }
    }
    
    /**
     * Get the next higher severity level
     */
    public BoundarySeverity escalate() {
        switch (this) {
            case LOW:
                return MEDIUM;
            case MEDIUM:
                return HIGH;
            case HIGH:
            case CRITICAL:
                return CRITICAL;
            default:
                return this;
        }
    }
    
    /**
     * Get the next lower severity level
     */
    public BoundarySeverity deescalate() {
        switch (this) {
            case CRITICAL:
                return HIGH;
            case HIGH:
                return MEDIUM;
            case MEDIUM:
            case LOW:
                return LOW;
            default:
                return this;
        }
    }
}