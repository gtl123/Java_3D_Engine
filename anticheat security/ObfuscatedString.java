package fps.anticheat.security;

/**
 * Represents an obfuscated string with metadata.
 */
public class ObfuscatedString {
    
    private final String originalText;
    private final String obfuscatedText;
    private final SecurityLevel securityLevel;
    private final long timestamp;
    private int accessCount;
    private long lastAccessed;
    
    public ObfuscatedString(String originalText, String obfuscatedText, 
                          SecurityLevel securityLevel, long timestamp) {
        this.originalText = originalText;
        this.obfuscatedText = obfuscatedText;
        this.securityLevel = securityLevel;
        this.timestamp = timestamp;
        this.accessCount = 0;
        this.lastAccessed = timestamp;
    }
    
    /**
     * Record access to this obfuscated string
     */
    public void recordAccess() {
        this.accessCount++;
        this.lastAccessed = System.currentTimeMillis();
    }
    
    /**
     * Check if string should be re-obfuscated based on access patterns
     */
    public boolean shouldReobfuscate() {
        long currentTime = System.currentTimeMillis();
        long age = currentTime - timestamp;
        
        // Re-obfuscate if:
        // 1. String is older than 1 hour and accessed more than 100 times
        // 2. String is older than 6 hours regardless of access count
        // 3. Critical security level strings older than 30 minutes
        
        if (securityLevel == SecurityLevel.CRITICAL && age > 1800000) { // 30 minutes
            return true;
        }
        
        if (age > 21600000) { // 6 hours
            return true;
        }
        
        if (age > 3600000 && accessCount > 100) { // 1 hour and 100+ accesses
            return true;
        }
        
        return false;
    }
    
    /**
     * Get security risk score based on usage patterns
     */
    public float getSecurityRiskScore() {
        long currentTime = System.currentTimeMillis();
        long age = currentTime - timestamp;
        long timeSinceLastAccess = currentTime - lastAccessed;
        
        float riskScore = 0.0f;
        
        // Age factor (older = higher risk)
        riskScore += Math.min(age / 3600000.0f, 1.0f) * 0.3f; // Max 0.3 for age
        
        // Access frequency factor (more accesses = higher risk)
        float accessRate = accessCount / Math.max(age / 3600000.0f, 0.1f); // accesses per hour
        riskScore += Math.min(accessRate / 50.0f, 1.0f) * 0.4f; // Max 0.4 for frequency
        
        // Recent access factor (recent access = higher risk)
        if (timeSinceLastAccess < 300000) { // 5 minutes
            riskScore += 0.2f;
        } else if (timeSinceLastAccess < 1800000) { // 30 minutes
            riskScore += 0.1f;
        }
        
        // Security level factor (lower security = higher risk)
        switch (securityLevel) {
            case LOW:
                riskScore += 0.1f;
                break;
            case MEDIUM:
                riskScore += 0.05f;
                break;
            case HIGH:
                // No additional risk
                break;
            case CRITICAL:
                riskScore -= 0.1f; // Lower risk due to stronger obfuscation
                break;
        }
        
        return Math.max(0.0f, Math.min(1.0f, riskScore));
    }
    
    // Getters
    public String getOriginalText() { return originalText; }
    public String getObfuscatedText() { return obfuscatedText; }
    public SecurityLevel getSecurityLevel() { return securityLevel; }
    public long getTimestamp() { return timestamp; }
    public int getAccessCount() { return accessCount; }
    public long getLastAccessed() { return lastAccessed; }
    
    @Override
    public String toString() {
        return String.format("ObfuscatedString{level=%s, accessCount=%d, age=%dms, riskScore=%.2f}", 
                           securityLevel, accessCount, System.currentTimeMillis() - timestamp, 
                           getSecurityRiskScore());
    }
}