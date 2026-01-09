package fps.anticheat.security;

import fps.anticheat.AntiCheatConfiguration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main coordinator for all security systems including obfuscation, anti-debugging, and tamper protection.
 * Provides a unified interface for security operations and threat response.
 */
public class SecurityCoordinator {
    
    private final AntiCheatConfiguration config;
    private final SecurityObfuscator obfuscator;
    private final AntiDebuggingManager antiDebuggingManager;
    private final TamperProtectionManager tamperProtectionManager;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    
    // Security state
    private volatile SecurityThreatLevel currentThreatLevel;
    private volatile boolean securityCompromised;
    private SecurityEventListener eventListener;
    
    public SecurityCoordinator(AntiCheatConfiguration config) {
        this.config = config;
        this.obfuscator = new SecurityObfuscator();
        this.antiDebuggingManager = new AntiDebuggingManager();
        this.tamperProtectionManager = new TamperProtectionManager();
        this.executorService = Executors.newFixedThreadPool(3);
        this.scheduledExecutor = Executors.newScheduledThreadPool(2);
        
        this.currentThreatLevel = SecurityThreatLevel.LOW;
        this.securityCompromised = false;
        
        // Start periodic security assessment
        startPeriodicSecurityAssessment();
    }
    
    /**
     * Start periodic security assessment
     */
    private void startPeriodicSecurityAssessment() {
        scheduledExecutor.scheduleAtFixedRate(this::assessSecurityThreatLevel, 
                                            30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Assess current security threat level
     */
    private void assessSecurityThreatLevel() {
        try {
            SecurityThreatLevel newThreatLevel = calculateThreatLevel();
            
            if (newThreatLevel != currentThreatLevel) {
                SecurityThreatLevel oldLevel = currentThreatLevel;
                currentThreatLevel = newThreatLevel;
                
                if (eventListener != null) {
                    eventListener.onThreatLevelChanged(oldLevel, newThreatLevel);
                }
                
                // Adjust security measures based on threat level
                adjustSecurityMeasures(newThreatLevel);
            }
            
            // Check for security compromise
            boolean wasCompromised = securityCompromised;
            securityCompromised = isSecurityCompromised();
            
            if (securityCompromised && !wasCompromised) {
                handleSecurityCompromise();
            }
            
        } catch (Exception e) {
            // Log error but don't fail
            System.err.println("Error during security assessment: " + e.getMessage());
        }
    }
    
    /**
     * Calculate current threat level
     */
    private SecurityThreatLevel calculateThreatLevel() {
        int threatScore = 0;
        
        // Anti-debugging detections
        if (antiDebuggingManager.isDebuggerDetected()) {
            threatScore += 40;
        }
        if (antiDebuggingManager.isTamperingDetected()) {
            threatScore += 30;
        }
        threatScore += Math.min(antiDebuggingManager.getDetectionCount() * 2, 20);
        
        // Tamper protection detections
        if (tamperProtectionManager.isTamperingDetected()) {
            threatScore += 50;
        }
        threatScore += Math.min(tamperProtectionManager.getTamperAttempts() * 5, 25);
        
        // Obfuscation effectiveness
        ObfuscationStatistics obfStats = obfuscator.getStatistics();
        float effectiveness = obfStats.getSecurityEffectiveness();
        if (effectiveness < 0.5f) {
            threatScore += 15;
        } else if (effectiveness < 0.7f) {
            threatScore += 5;
        }
        
        // Determine threat level based on score
        if (threatScore >= 80) {
            return SecurityThreatLevel.CRITICAL;
        } else if (threatScore >= 60) {
            return SecurityThreatLevel.HIGH;
        } else if (threatScore >= 30) {
            return SecurityThreatLevel.MEDIUM;
        } else if (threatScore >= 10) {
            return SecurityThreatLevel.ELEVATED;
        } else {
            return SecurityThreatLevel.LOW;
        }
    }
    
    /**
     * Check if security is compromised
     */
    private boolean isSecurityCompromised() {
        return antiDebuggingManager.isSecurityThreatDetected() || 
               tamperProtectionManager.isTamperingDetected() ||
               currentThreatLevel == SecurityThreatLevel.CRITICAL;
    }
    
    /**
     * Adjust security measures based on threat level
     */
    private void adjustSecurityMeasures(SecurityThreatLevel threatLevel) {
        switch (threatLevel) {
            case LOW:
                // Normal security measures
                break;
                
            case ELEVATED:
                // Increase monitoring frequency
                // Enable additional obfuscation
                break;
                
            case MEDIUM:
                // Enable enhanced protection
                // Increase detection sensitivity
                break;
                
            case HIGH:
                // Enable maximum protection
                // Prepare for potential shutdown
                break;
                
            case CRITICAL:
                // Emergency measures
                handleCriticalThreat();
                break;
        }
    }
    
    /**
     * Handle critical security threat
     */
    private void handleCriticalThreat() {
        if (eventListener != null) {
            eventListener.onCriticalThreatDetected(currentThreatLevel);
        }
        
        // In a real implementation, this could:
        // 1. Immediately terminate the application
        // 2. Corrupt sensitive data to prevent extraction
        // 3. Send emergency alert to server
        // 4. Lock out the user
        
        System.err.println("CRITICAL SECURITY THREAT DETECTED - Emergency measures activated");
    }
    
    /**
     * Handle security compromise
     */
    private void handleSecurityCompromise() {
        if (eventListener != null) {
            eventListener.onSecurityCompromised();
        }
        
        System.err.println("SECURITY COMPROMISED - Anti-cheat integrity violated");
    }
    
    /**
     * Obfuscate string with automatic security level selection
     */
    public String obfuscateString(String plaintext) {
        SecurityLevel level = selectSecurityLevel();
        return obfuscator.obfuscateString(plaintext, level);
    }
    
    /**
     * Obfuscate string with specified security level
     */
    public String obfuscateString(String plaintext, SecurityLevel securityLevel) {
        return obfuscator.obfuscateString(plaintext, securityLevel);
    }
    
    /**
     * Deobfuscate string
     */
    public String deobfuscateString(String obfuscatedText, SecurityLevel securityLevel) {
        return obfuscator.deobfuscateString(obfuscatedText, securityLevel);
    }
    
    /**
     * Execute code with obfuscated control flow
     */
    public <T> T executeSecure(String methodName, ControlFlowObfuscator.ObfuscatedExecutor<T> executor) {
        return obfuscator.getControlFlowObfuscator().executeObfuscated(methodName, executor);
    }
    
    /**
     * Execute void method with obfuscated control flow
     */
    public void executeSecureVoid(String methodName, ControlFlowObfuscator.ObfuscatedVoidExecutor executor) {
        obfuscator.getControlFlowObfuscator().executeObfuscatedVoid(methodName, executor);
    }
    
    /**
     * Select appropriate security level based on current threat level
     */
    private SecurityLevel selectSecurityLevel() {
        switch (currentThreatLevel) {
            case LOW:
                return SecurityLevel.LOW;
            case ELEVATED:
                return SecurityLevel.MEDIUM;
            case MEDIUM:
                return SecurityLevel.HIGH;
            case HIGH:
            case CRITICAL:
                return SecurityLevel.CRITICAL;
            default:
                return SecurityLevel.MEDIUM;
        }
    }
    
    /**
     * Add file to tamper protection monitoring
     */
    public void protectFile(String filePath) {
        tamperProtectionManager.addFileToMonitoring(filePath);
    }
    
    /**
     * Add class to tamper protection monitoring
     */
    public void protectClass(String className) {
        tamperProtectionManager.addClassToMonitoring(className);
    }
    
    /**
     * Perform security validation asynchronously
     */
    public CompletableFuture<SecurityValidationResult> validateSecurityAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return performSecurityValidation();
            } catch (Exception e) {
                return new SecurityValidationResult(false, "Security validation failed: " + e.getMessage());
            }
        }, executorService);
    }
    
    /**
     * Perform comprehensive security validation
     */
    private SecurityValidationResult performSecurityValidation() {
        StringBuilder issues = new StringBuilder();
        boolean isValid = true;
        
        // Check anti-debugging status
        if (antiDebuggingManager.isDebuggerDetected()) {
            issues.append("Debugger detected; ");
            isValid = false;
        }
        
        // Check tamper protection status
        if (tamperProtectionManager.isTamperingDetected()) {
            issues.append("Tampering detected; ");
            isValid = false;
        }
        
        // Check obfuscation effectiveness
        ObfuscationStatistics obfStats = obfuscator.getStatistics();
        if (obfStats.getSecurityEffectiveness() < 0.5f) {
            issues.append("Low obfuscation effectiveness; ");
            isValid = false;
        }
        
        // Check threat level
        if (currentThreatLevel.ordinal() >= SecurityThreatLevel.HIGH.ordinal()) {
            issues.append("High threat level detected; ");
            isValid = false;
        }
        
        String message = isValid ? "Security validation passed" : 
                        "Security issues detected: " + issues.toString();
        
        return new SecurityValidationResult(isValid, message);
    }
    
    /**
     * Get comprehensive security status
     */
    public SecurityStatus getSecurityStatus() {
        ObfuscationStatistics obfStats = obfuscator.getStatistics();
        TamperProtectionManager.TamperProtectionStatistics tamperStats = tamperProtectionManager.getStatistics();
        
        return new SecurityStatus(
            currentThreatLevel,
            securityCompromised,
            antiDebuggingManager.isDebuggerDetected(),
            antiDebuggingManager.isTamperingDetected(),
            tamperProtectionManager.isTamperingDetected(),
            obfStats.getSecurityEffectiveness(),
            antiDebuggingManager.getDetectionCount(),
            tamperProtectionManager.getTamperAttempts()
        );
    }
    
    /**
     * Set security event listener
     */
    public void setEventListener(SecurityEventListener listener) {
        this.eventListener = listener;
    }
    
    /**
     * Shutdown security coordinator
     */
    public void shutdown() {
        try {
            antiDebuggingManager.shutdown();
            tamperProtectionManager.shutdown();
            
            executorService.shutdown();
            scheduledExecutor.shutdown();
            
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // Getters for direct access (if needed)
    public SecurityObfuscator getObfuscator() { return obfuscator; }
    public AntiDebuggingManager getAntiDebuggingManager() { return antiDebuggingManager; }
    public TamperProtectionManager getTamperProtectionManager() { return tamperProtectionManager; }
    public SecurityThreatLevel getCurrentThreatLevel() { return currentThreatLevel; }
    public boolean isSecurityCompromised() { return securityCompromised; }
}