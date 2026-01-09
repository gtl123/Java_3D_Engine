package fps.anticheat;

/**
 * Result of validating a player action.
 * Contains validation status, confidence level, and detailed reasoning.
 */
public class ValidationResult {
    
    private final boolean valid;
    private final float confidence;
    private final String reason;
    private final ViolationType violationType;
    private final Object evidence;
    private final long timestamp;
    
    // Performance metrics
    private final long validationTimeMs;
    private final String validatorName;
    
    private ValidationResult(boolean valid, float confidence, String reason, 
                           ViolationType violationType, Object evidence, 
                           long validationTimeMs, String validatorName) {
        this.valid = valid;
        this.confidence = confidence;
        this.reason = reason;
        this.violationType = violationType;
        this.evidence = evidence;
        this.timestamp = System.currentTimeMillis();
        this.validationTimeMs = validationTimeMs;
        this.validatorName = validatorName;
    }
    
    /**
     * Create a result indicating the action is allowed
     */
    public static ValidationResult allowed() {
        return new ValidationResult(true, 1.0f, "Action allowed", null, null, 0, "system");
    }
    
    /**
     * Create a result indicating the action is allowed with specific confidence
     */
    public static ValidationResult allowed(float confidence, String reason) {
        return new ValidationResult(true, confidence, reason, null, null, 0, "system");
    }
    
    /**
     * Create a result indicating the action is denied
     */
    public static ValidationResult denied(String reason) {
        return new ValidationResult(false, 1.0f, reason, ViolationType.GENERAL, null, 0, "system");
    }
    
    /**
     * Create a result indicating the action is denied with specific violation type
     */
    public static ValidationResult denied(String reason, ViolationType violationType) {
        return new ValidationResult(false, 1.0f, reason, violationType, null, 0, "system");
    }
    
    /**
     * Create a result indicating the action is denied with evidence
     */
    public static ValidationResult denied(String reason, ViolationType violationType, Object evidence) {
        return new ValidationResult(false, 1.0f, reason, violationType, evidence, 0, "system");
    }
    
    /**
     * Create a result with full details
     */
    public static ValidationResult create(boolean valid, float confidence, String reason, 
                                        ViolationType violationType, Object evidence, 
                                        long validationTimeMs, String validatorName) {
        return new ValidationResult(valid, confidence, reason, violationType, evidence, 
                                  validationTimeMs, validatorName);
    }
    
    /**
     * Check if the action should be blocked based on confidence threshold
     */
    public boolean shouldBlock(float confidenceThreshold) {
        return !valid && confidence >= confidenceThreshold;
    }
    
    /**
     * Check if this is a suspicious result that warrants investigation
     */
    public boolean isSuspicious() {
        return !valid || confidence < 0.8f;
    }
    
    /**
     * Get severity level based on violation type and confidence
     */
    public SeverityLevel getSeverityLevel() {
        if (valid) {
            return SeverityLevel.NONE;
        }
        
        if (violationType == null) {
            return SeverityLevel.LOW;
        }
        
        switch (violationType) {
            case AIMBOT:
            case WALLHACK:
            case SPEED_HACK:
                return confidence > 0.9f ? SeverityLevel.CRITICAL : SeverityLevel.HIGH;
            
            case TRIGGER_BOT:
            case NO_RECOIL:
            case ESP:
                return confidence > 0.8f ? SeverityLevel.HIGH : SeverityLevel.MEDIUM;
            
            case BEHAVIORAL_ANALYSIS:
            case STATISTICAL_ANOMALY:
                return confidence > 0.9f ? SeverityLevel.MEDIUM : SeverityLevel.LOW;
            
            case SERVER_VALIDATION:
            case HARDWARE_VALIDATION:
                return confidence > 0.95f ? SeverityLevel.HIGH : SeverityLevel.MEDIUM;
            
            default:
                return SeverityLevel.LOW;
        }
    }
    
    /**
     * Combine this result with another validation result
     */
    public ValidationResult combine(ValidationResult other) {
        if (other == null) {
            return this;
        }
        
        // If either result is invalid, the combined result is invalid
        boolean combinedValid = this.valid && other.valid;
        
        // Use the lower confidence of the two
        float combinedConfidence = Math.min(this.confidence, other.confidence);
        
        // Combine reasons
        String combinedReason = this.reason;
        if (!other.valid) {
            combinedReason = combinedReason + "; " + other.reason;
        }
        
        // Use the more severe violation type
        ViolationType combinedViolationType = this.violationType;
        if (other.violationType != null && 
            (combinedViolationType == null || other.getSeverityLevel().ordinal() > this.getSeverityLevel().ordinal())) {
            combinedViolationType = other.violationType;
        }
        
        // Combine validation times
        long combinedValidationTime = this.validationTimeMs + other.validationTimeMs;
        
        // Combine validator names
        String combinedValidatorName = this.validatorName + "+" + other.validatorName;
        
        return new ValidationResult(combinedValid, combinedConfidence, combinedReason, 
                                  combinedViolationType, this.evidence, 
                                  combinedValidationTime, combinedValidatorName);
    }
    
    // Getters
    public boolean isValid() { return valid; }
    public float getConfidence() { return confidence; }
    public String getReason() { return reason; }
    public ViolationType getViolationType() { return violationType; }
    public Object getEvidence() { return evidence; }
    public long getTimestamp() { return timestamp; }
    public long getValidationTimeMs() { return validationTimeMs; }
    public String getValidatorName() { return validatorName; }
    
    /**
     * Severity levels for violations
     */
    public enum SeverityLevel {
        NONE,       // No violation
        LOW,        // Minor suspicious activity
        MEDIUM,     // Moderate suspicious activity
        HIGH,       // Highly suspicious activity
        CRITICAL    // Definitive cheating detected
    }
    
    @Override
    public String toString() {
        return String.format("ValidationResult{valid=%s, confidence=%.2f, reason='%s', type=%s, severity=%s, validator='%s', time=%dms}", 
                           valid, confidence, reason, violationType, getSeverityLevel(), validatorName, validationTimeMs);
    }
}