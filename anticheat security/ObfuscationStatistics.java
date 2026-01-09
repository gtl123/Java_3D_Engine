package fps.anticheat.security;

/**
 * Statistics about the obfuscation system performance.
 */
public class ObfuscationStatistics {
    
    private final int obfuscatedStrings;
    private final int encryptionKeys;
    private final int obfuscatedMethods;
    private final int detectionCount;
    private final long timestamp;
    
    public ObfuscationStatistics(int obfuscatedStrings, int encryptionKeys, 
                               int obfuscatedMethods, int detectionCount) {
        this.obfuscatedStrings = obfuscatedStrings;
        this.encryptionKeys = encryptionKeys;
        this.obfuscatedMethods = obfuscatedMethods;
        this.detectionCount = detectionCount;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Get obfuscation coverage score (0.0 to 1.0)
     */
    public float getObfuscationCoverage() {
        // Calculate coverage based on number of obfuscated elements
        float stringCoverage = Math.min(obfuscatedStrings / 100.0f, 1.0f);
        float methodCoverage = Math.min(obfuscatedMethods / 50.0f, 1.0f);
        
        return (stringCoverage + methodCoverage) / 2.0f;
    }
    
    /**
     * Get security effectiveness score
     */
    public float getSecurityEffectiveness() {
        float coverage = getObfuscationCoverage();
        float keyDiversity = Math.min(encryptionKeys / 4.0f, 1.0f); // 4 security levels
        float detectionPenalty = Math.min(detectionCount / 10.0f, 0.5f); // Max 50% penalty
        
        return Math.max(0.0f, (coverage + keyDiversity) / 2.0f - detectionPenalty);
    }
    
    // Getters
    public int getObfuscatedStrings() { return obfuscatedStrings; }
    public int getEncryptionKeys() { return encryptionKeys; }
    public int getObfuscatedMethods() { return obfuscatedMethods; }
    public int getDetectionCount() { return detectionCount; }
    public long getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("ObfuscationStatistics{strings=%d, keys=%d, methods=%d, detections=%d, " +
                           "coverage=%.2f, effectiveness=%.2f}", 
                           obfuscatedStrings, encryptionKeys, obfuscatedMethods, detectionCount,
                           getObfuscationCoverage(), getSecurityEffectiveness());
    }
}