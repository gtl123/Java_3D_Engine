package fps.anticheat.security;

/**
 * Comprehensive security status information.
 */
public class SecurityStatus {
    
    private final SecurityThreatLevel threatLevel;
    private final boolean securityCompromised;
    private final boolean debuggerDetected;
    private final boolean antiDebugTamperingDetected;
    private final boolean fileTamperingDetected;
    private final float obfuscationEffectiveness;
    private final int debugDetectionCount;
    private final int tamperAttempts;
    private final long timestamp;
    
    public SecurityStatus(SecurityThreatLevel threatLevel, boolean securityCompromised,
                         boolean debuggerDetected, boolean antiDebugTamperingDetected,
                         boolean fileTamperingDetected, float obfuscationEffectiveness,
                         int debugDetectionCount, int tamperAttempts) {
        this.threatLevel = threatLevel;
        this.securityCompromised = securityCompromised;
        this.debuggerDetected = debuggerDetected;
        this.antiDebugTamperingDetected = antiDebugTamperingDetected;
        this.fileTamperingDetected = fileTamperingDetected;
        this.obfuscationEffectiveness = obfuscationEffectiveness;
        this.debugDetectionCount = debugDetectionCount;
        this.tamperAttempts = tamperAttempts;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Get overall security health score (0.0 to 1.0)
     */
    public float getSecurityHealthScore() {
        float score = 1.0f;
        
        // Reduce score based on threat level
        switch (threatLevel) {
            case CRITICAL:
                score -= 0.8f;
                break;
            case HIGH:
                score -= 0.6f;
                break;
            case MEDIUM:
                score -= 0.4f;
                break;
            case ELEVATED:
                score -= 0.2f;
                break;
            case LOW:
                // No reduction
                break;
        }
        
        // Reduce score for active threats
        if (debuggerDetected) score -= 0.3f;
        if (antiDebugTamperingDetected) score -= 0.2f;
        if (fileTamperingDetected) score -= 0.4f;
        if (securityCompromised) score -= 0.5f;
        
        // Reduce score based on detection counts
        score -= Math.min(debugDetectionCount * 0.02f, 0.2f);
        score -= Math.min(tamperAttempts * 0.05f, 0.3f);
        
        // Factor in obfuscation effectiveness
        score = score * (0.5f + obfuscationEffectiveness * 0.5f);
        
        return Math.max(0.0f, Math.min(1.0f, score));
    }
    
    /**
     * Check if immediate action is required
     */
    public boolean requiresImmediateAction() {
        return securityCompromised || 
               threatLevel == SecurityThreatLevel.CRITICAL ||
               (debuggerDetected && fileTamperingDetected) ||
               tamperAttempts >= 10;
    }
    
    /**
     * Get security status summary
     */
    public String getStatusSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Security Status: ").append(threatLevel.name()).append("\n");
        summary.append("Health Score: ").append(String.format("%.1f%%", getSecurityHealthScore() * 100)).append("\n");
        
        if (securityCompromised) {
            summary.append("⚠️ SECURITY COMPROMISED\n");
        }
        
        if (debuggerDetected) {
            summary.append("🐛 Debugger detected\n");
        }
        
        if (antiDebugTamperingDetected) {
            summary.append("🔧 Anti-debug tampering detected\n");
        }
        
        if (fileTamperingDetected) {
            summary.append("📁 File tampering detected\n");
        }
        
        summary.append("Obfuscation Effectiveness: ").append(String.format("%.1f%%", obfuscationEffectiveness * 100)).append("\n");
        summary.append("Debug Detections: ").append(debugDetectionCount).append("\n");
        summary.append("Tamper Attempts: ").append(tamperAttempts).append("\n");
        
        if (requiresImmediateAction()) {
            summary.append("\n🚨 IMMEDIATE ACTION REQUIRED");
        }
        
        return summary.toString();
    }
    
    // Getters
    public SecurityThreatLevel getThreatLevel() { return threatLevel; }
    public boolean isSecurityCompromised() { return securityCompromised; }
    public boolean isDebuggerDetected() { return debuggerDetected; }
    public boolean isAntiDebugTamperingDetected() { return antiDebugTamperingDetected; }
    public boolean isFileTamperingDetected() { return fileTamperingDetected; }
    public float getObfuscationEffectiveness() { return obfuscationEffectiveness; }
    public int getDebugDetectionCount() { return debugDetectionCount; }
    public int getTamperAttempts() { return tamperAttempts; }
    public long getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("SecurityStatus{threat=%s, compromised=%s, debugger=%s, tamper=%s, " +
                           "effectiveness=%.2f, health=%.2f}", 
                           threatLevel, securityCompromised, debuggerDetected, 
                           fileTamperingDetected, obfuscationEffectiveness, getSecurityHealthScore());
    }
}