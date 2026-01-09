package fps.anticheat;

import java.util.Properties;
import java.io.InputStream;
import java.io.IOException;

/**
 * Configuration settings for the anti-cheat system.
 * Manages all tunable parameters and thresholds.
 */
public class AntiCheatConfiguration {
    
    // Server-side validation settings
    private float maxMovementSpeed = 12.0f;
    private float maxJumpHeight = 2.5f;
    private float maxFireRate = 1200.0f; // rounds per minute
    private float maxAccuracy = 95.0f; // percentage
    private float maxHeadshotRatio = 0.8f;
    private int maxActionsPerSecond = 50;
    
    // Behavioral analysis settings
    private float aimSmoothnessTolerance = 0.1f;
    private float reactionTimeThreshold = 0.05f; // 50ms minimum
    private float crosshairPlacementTolerance = 0.2f;
    private int behavioralSampleSize = 100;
    private float behavioralConfidenceThreshold = 0.85f;
    
    // Statistical analysis settings
    private float statisticalDeviationThreshold = 3.0f; // standard deviations
    private int statisticalSampleSize = 500;
    private float anomalyConfidenceThreshold = 0.9f;
    private long statisticalWindowSize = 300000; // 5 minutes in ms
    
    // Hardware validation settings
    private boolean enableHardwareFingerprinting = true;
    private boolean enableVirtualMachineDetection = true;
    private boolean enableDebuggerDetection = true;
    private int hardwareFingerprintComponents = 8;
    private float hardwareSimilarityThreshold = 0.95f;
    
    // Real-time detection settings
    private boolean enableAimbotDetection = true;
    private boolean enableWallhackDetection = true;
    private boolean enableSpeedHackDetection = true;
    private boolean enableTriggerBotDetection = true;
    private float realtimeDetectionSensitivity = 0.8f;
    
    // Punishment settings
    private boolean enableGradualPunishment = true;
    private int warningThreshold = 3;
    private int temporaryBanThreshold = 5;
    private int permanentBanThreshold = 10;
    private long temporaryBanDuration = 86400000; // 24 hours in ms
    private boolean enableHardwareBans = true;
    
    // Performance settings
    private int maxConcurrentAnalyses = 4;
    private float cpuUsageLimit = 5.0f; // percentage
    private int memoryUsageLimit = 256; // MB
    private long violationRetentionTime = 2592000000L; // 30 days in ms
    
    // Security settings
    private boolean enableCodeObfuscation = true;
    private boolean enableAntiTamper = true;
    private boolean enableAntiDebugging = true;
    private String encryptionKey = "DefaultAntiCheatKey2024";
    private int securityUpdateInterval = 3600; // seconds
    
    // Logging settings
    private boolean enableDetailedLogging = true;
    private boolean enableEvidenceCollection = true;
    private String logLevel = "INFO";
    private long logRetentionTime = 7776000000L; // 90 days in ms
    private int maxLogFileSize = 100; // MB
    
    public AntiCheatConfiguration() {
        // Default constructor
    }
    
    /**
     * Load default configuration values
     */
    public void loadDefaults() {
        // Values are already set as defaults above
    }
    
    /**
     * Load configuration from properties file
     */
    public void loadFromProperties(String propertiesFile) throws IOException {
        Properties props = new Properties();
        
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(propertiesFile)) {
            if (input != null) {
                props.load(input);
                applyProperties(props);
            }
        }
    }
    
    /**
     * Apply properties to configuration
     */
    private void applyProperties(Properties props) {
        // Server-side validation
        maxMovementSpeed = Float.parseFloat(props.getProperty("anticheat.server.maxMovementSpeed", String.valueOf(maxMovementSpeed)));
        maxJumpHeight = Float.parseFloat(props.getProperty("anticheat.server.maxJumpHeight", String.valueOf(maxJumpHeight)));
        maxFireRate = Float.parseFloat(props.getProperty("anticheat.server.maxFireRate", String.valueOf(maxFireRate)));
        maxAccuracy = Float.parseFloat(props.getProperty("anticheat.server.maxAccuracy", String.valueOf(maxAccuracy)));
        maxHeadshotRatio = Float.parseFloat(props.getProperty("anticheat.server.maxHeadshotRatio", String.valueOf(maxHeadshotRatio)));
        maxActionsPerSecond = Integer.parseInt(props.getProperty("anticheat.server.maxActionsPerSecond", String.valueOf(maxActionsPerSecond)));
        
        // Behavioral analysis
        aimSmoothnessTolerance = Float.parseFloat(props.getProperty("anticheat.behavioral.aimSmoothnessTolerance", String.valueOf(aimSmoothnessTolerance)));
        reactionTimeThreshold = Float.parseFloat(props.getProperty("anticheat.behavioral.reactionTimeThreshold", String.valueOf(reactionTimeThreshold)));
        crosshairPlacementTolerance = Float.parseFloat(props.getProperty("anticheat.behavioral.crosshairPlacementTolerance", String.valueOf(crosshairPlacementTolerance)));
        behavioralSampleSize = Integer.parseInt(props.getProperty("anticheat.behavioral.sampleSize", String.valueOf(behavioralSampleSize)));
        behavioralConfidenceThreshold = Float.parseFloat(props.getProperty("anticheat.behavioral.confidenceThreshold", String.valueOf(behavioralConfidenceThreshold)));
        
        // Statistical analysis
        statisticalDeviationThreshold = Float.parseFloat(props.getProperty("anticheat.statistical.deviationThreshold", String.valueOf(statisticalDeviationThreshold)));
        statisticalSampleSize = Integer.parseInt(props.getProperty("anticheat.statistical.sampleSize", String.valueOf(statisticalSampleSize)));
        anomalyConfidenceThreshold = Float.parseFloat(props.getProperty("anticheat.statistical.anomalyConfidenceThreshold", String.valueOf(anomalyConfidenceThreshold)));
        statisticalWindowSize = Long.parseLong(props.getProperty("anticheat.statistical.windowSize", String.valueOf(statisticalWindowSize)));
        
        // Hardware validation
        enableHardwareFingerprinting = Boolean.parseBoolean(props.getProperty("anticheat.hardware.enableFingerprinting", String.valueOf(enableHardwareFingerprinting)));
        enableVirtualMachineDetection = Boolean.parseBoolean(props.getProperty("anticheat.hardware.enableVMDetection", String.valueOf(enableVirtualMachineDetection)));
        enableDebuggerDetection = Boolean.parseBoolean(props.getProperty("anticheat.hardware.enableDebuggerDetection", String.valueOf(enableDebuggerDetection)));
        hardwareFingerprintComponents = Integer.parseInt(props.getProperty("anticheat.hardware.fingerprintComponents", String.valueOf(hardwareFingerprintComponents)));
        hardwareSimilarityThreshold = Float.parseFloat(props.getProperty("anticheat.hardware.similarityThreshold", String.valueOf(hardwareSimilarityThreshold)));
        
        // Real-time detection
        enableAimbotDetection = Boolean.parseBoolean(props.getProperty("anticheat.realtime.enableAimbotDetection", String.valueOf(enableAimbotDetection)));
        enableWallhackDetection = Boolean.parseBoolean(props.getProperty("anticheat.realtime.enableWallhackDetection", String.valueOf(enableWallhackDetection)));
        enableSpeedHackDetection = Boolean.parseBoolean(props.getProperty("anticheat.realtime.enableSpeedHackDetection", String.valueOf(enableSpeedHackDetection)));
        enableTriggerBotDetection = Boolean.parseBoolean(props.getProperty("anticheat.realtime.enableTriggerBotDetection", String.valueOf(enableTriggerBotDetection)));
        realtimeDetectionSensitivity = Float.parseFloat(props.getProperty("anticheat.realtime.detectionSensitivity", String.valueOf(realtimeDetectionSensitivity)));
        
        // Punishment settings
        enableGradualPunishment = Boolean.parseBoolean(props.getProperty("anticheat.punishment.enableGradual", String.valueOf(enableGradualPunishment)));
        warningThreshold = Integer.parseInt(props.getProperty("anticheat.punishment.warningThreshold", String.valueOf(warningThreshold)));
        temporaryBanThreshold = Integer.parseInt(props.getProperty("anticheat.punishment.temporaryBanThreshold", String.valueOf(temporaryBanThreshold)));
        permanentBanThreshold = Integer.parseInt(props.getProperty("anticheat.punishment.permanentBanThreshold", String.valueOf(permanentBanThreshold)));
        temporaryBanDuration = Long.parseLong(props.getProperty("anticheat.punishment.temporaryBanDuration", String.valueOf(temporaryBanDuration)));
        enableHardwareBans = Boolean.parseBoolean(props.getProperty("anticheat.punishment.enableHardwareBans", String.valueOf(enableHardwareBans)));
        
        // Performance settings
        maxConcurrentAnalyses = Integer.parseInt(props.getProperty("anticheat.performance.maxConcurrentAnalyses", String.valueOf(maxConcurrentAnalyses)));
        cpuUsageLimit = Float.parseFloat(props.getProperty("anticheat.performance.cpuUsageLimit", String.valueOf(cpuUsageLimit)));
        memoryUsageLimit = Integer.parseInt(props.getProperty("anticheat.performance.memoryUsageLimit", String.valueOf(memoryUsageLimit)));
        violationRetentionTime = Long.parseLong(props.getProperty("anticheat.performance.violationRetentionTime", String.valueOf(violationRetentionTime)));
        
        // Security settings
        enableCodeObfuscation = Boolean.parseBoolean(props.getProperty("anticheat.security.enableObfuscation", String.valueOf(enableCodeObfuscation)));
        enableAntiTamper = Boolean.parseBoolean(props.getProperty("anticheat.security.enableAntiTamper", String.valueOf(enableAntiTamper)));
        enableAntiDebugging = Boolean.parseBoolean(props.getProperty("anticheat.security.enableAntiDebugging", String.valueOf(enableAntiDebugging)));
        encryptionKey = props.getProperty("anticheat.security.encryptionKey", encryptionKey);
        securityUpdateInterval = Integer.parseInt(props.getProperty("anticheat.security.updateInterval", String.valueOf(securityUpdateInterval)));
        
        // Logging settings
        enableDetailedLogging = Boolean.parseBoolean(props.getProperty("anticheat.logging.enableDetailed", String.valueOf(enableDetailedLogging)));
        enableEvidenceCollection = Boolean.parseBoolean(props.getProperty("anticheat.logging.enableEvidence", String.valueOf(enableEvidenceCollection)));
        logLevel = props.getProperty("anticheat.logging.level", logLevel);
        logRetentionTime = Long.parseLong(props.getProperty("anticheat.logging.retentionTime", String.valueOf(logRetentionTime)));
        maxLogFileSize = Integer.parseInt(props.getProperty("anticheat.logging.maxFileSize", String.valueOf(maxLogFileSize)));
    }
    
    /**
     * Validate configuration values
     */
    public boolean validate() {
        // Validate ranges and dependencies
        if (maxMovementSpeed <= 0 || maxMovementSpeed > 100) return false;
        if (maxJumpHeight <= 0 || maxJumpHeight > 10) return false;
        if (maxFireRate <= 0 || maxFireRate > 10000) return false;
        if (maxAccuracy < 0 || maxAccuracy > 100) return false;
        if (maxHeadshotRatio < 0 || maxHeadshotRatio > 1) return false;
        if (maxActionsPerSecond <= 0 || maxActionsPerSecond > 1000) return false;
        
        if (aimSmoothnessTolerance < 0 || aimSmoothnessTolerance > 1) return false;
        if (reactionTimeThreshold < 0 || reactionTimeThreshold > 1) return false;
        if (crosshairPlacementTolerance < 0 || crosshairPlacementTolerance > 1) return false;
        if (behavioralSampleSize <= 0 || behavioralSampleSize > 10000) return false;
        if (behavioralConfidenceThreshold < 0 || behavioralConfidenceThreshold > 1) return false;
        
        if (statisticalDeviationThreshold <= 0 || statisticalDeviationThreshold > 10) return false;
        if (statisticalSampleSize <= 0 || statisticalSampleSize > 100000) return false;
        if (anomalyConfidenceThreshold < 0 || anomalyConfidenceThreshold > 1) return false;
        if (statisticalWindowSize <= 0) return false;
        
        if (hardwareFingerprintComponents <= 0 || hardwareFingerprintComponents > 32) return false;
        if (hardwareSimilarityThreshold < 0 || hardwareSimilarityThreshold > 1) return false;
        
        if (realtimeDetectionSensitivity < 0 || realtimeDetectionSensitivity > 1) return false;
        
        if (warningThreshold < 0 || warningThreshold > 100) return false;
        if (temporaryBanThreshold < 0 || temporaryBanThreshold > 100) return false;
        if (permanentBanThreshold < 0 || permanentBanThreshold > 100) return false;
        if (temporaryBanDuration <= 0) return false;
        
        if (maxConcurrentAnalyses <= 0 || maxConcurrentAnalyses > 64) return false;
        if (cpuUsageLimit < 0 || cpuUsageLimit > 100) return false;
        if (memoryUsageLimit <= 0 || memoryUsageLimit > 8192) return false;
        if (violationRetentionTime <= 0) return false;
        
        if (securityUpdateInterval <= 0) return false;
        if (logRetentionTime <= 0) return false;
        if (maxLogFileSize <= 0 || maxLogFileSize > 1024) return false;
        
        return true;
    }
    
    // Getters for all configuration values
    public float getMaxMovementSpeed() { return maxMovementSpeed; }
    public float getMaxJumpHeight() { return maxJumpHeight; }
    public float getMaxFireRate() { return maxFireRate; }
    public float getMaxAccuracy() { return maxAccuracy; }
    public float getMaxHeadshotRatio() { return maxHeadshotRatio; }
    public int getMaxActionsPerSecond() { return maxActionsPerSecond; }
    
    public float getAimSmoothnessTolerance() { return aimSmoothnessTolerance; }
    public float getReactionTimeThreshold() { return reactionTimeThreshold; }
    public float getCrosshairPlacementTolerance() { return crosshairPlacementTolerance; }
    public int getBehavioralSampleSize() { return behavioralSampleSize; }
    public float getBehavioralConfidenceThreshold() { return behavioralConfidenceThreshold; }
    
    public float getStatisticalDeviationThreshold() { return statisticalDeviationThreshold; }
    public int getStatisticalSampleSize() { return statisticalSampleSize; }
    public float getAnomalyConfidenceThreshold() { return anomalyConfidenceThreshold; }
    public long getStatisticalWindowSize() { return statisticalWindowSize; }
    
    public boolean isHardwareFingerprintingEnabled() { return enableHardwareFingerprinting; }
    public boolean isVirtualMachineDetectionEnabled() { return enableVirtualMachineDetection; }
    public boolean isDebuggerDetectionEnabled() { return enableDebuggerDetection; }
    public int getHardwareFingerprintComponents() { return hardwareFingerprintComponents; }
    public float getHardwareSimilarityThreshold() { return hardwareSimilarityThreshold; }
    
    public boolean isAimbotDetectionEnabled() { return enableAimbotDetection; }
    public boolean isWallhackDetectionEnabled() { return enableWallhackDetection; }
    public boolean isSpeedHackDetectionEnabled() { return enableSpeedHackDetection; }
    public boolean isTriggerBotDetectionEnabled() { return enableTriggerBotDetection; }
    public float getRealtimeDetectionSensitivity() { return realtimeDetectionSensitivity; }
    
    public boolean isGradualPunishmentEnabled() { return enableGradualPunishment; }
    public int getWarningThreshold() { return warningThreshold; }
    public int getTemporaryBanThreshold() { return temporaryBanThreshold; }
    public int getPermanentBanThreshold() { return permanentBanThreshold; }
    public long getTemporaryBanDuration() { return temporaryBanDuration; }
    public boolean isHardwareBansEnabled() { return enableHardwareBans; }
    
    public int getMaxConcurrentAnalyses() { return maxConcurrentAnalyses; }
    public float getCpuUsageLimit() { return cpuUsageLimit; }
    public int getMemoryUsageLimit() { return memoryUsageLimit; }
    public long getViolationRetentionTime() { return violationRetentionTime; }
    
    public boolean isCodeObfuscationEnabled() { return enableCodeObfuscation; }
    public boolean isAntiTamperEnabled() { return enableAntiTamper; }
    public boolean isAntiDebuggingEnabled() { return enableAntiDebugging; }
    public String getEncryptionKey() { return encryptionKey; }
    public int getSecurityUpdateInterval() { return securityUpdateInterval; }
    
    public boolean isDetailedLoggingEnabled() { return enableDetailedLogging; }
    public boolean isEvidenceCollectionEnabled() { return enableEvidenceCollection; }
    public String getLogLevel() { return logLevel; }
    public long getLogRetentionTime() { return logRetentionTime; }
    public int getMaxLogFileSize() { return maxLogFileSize; }
    
    // Setters for runtime configuration changes
    public void setMaxMovementSpeed(float maxMovementSpeed) { this.maxMovementSpeed = maxMovementSpeed; }
    public void setMaxJumpHeight(float maxJumpHeight) { this.maxJumpHeight = maxJumpHeight; }
    public void setMaxFireRate(float maxFireRate) { this.maxFireRate = maxFireRate; }
    public void setMaxAccuracy(float maxAccuracy) { this.maxAccuracy = maxAccuracy; }
    public void setMaxHeadshotRatio(float maxHeadshotRatio) { this.maxHeadshotRatio = maxHeadshotRatio; }
    public void setMaxActionsPerSecond(int maxActionsPerSecond) { this.maxActionsPerSecond = maxActionsPerSecond; }
    
    public void setRealtimeDetectionSensitivity(float sensitivity) { this.realtimeDetectionSensitivity = sensitivity; }
    public void setBehavioralConfidenceThreshold(float threshold) { this.behavioralConfidenceThreshold = threshold; }
    public void setAnomalyConfidenceThreshold(float threshold) { this.anomalyConfidenceThreshold = threshold; }
    
    public void setEnabled(String component, boolean enabled) {
        switch (component.toLowerCase()) {
            case "aimbot":
                this.enableAimbotDetection = enabled;
                break;
            case "wallhack":
                this.enableWallhackDetection = enabled;
                break;
            case "speedhack":
                this.enableSpeedHackDetection = enabled;
                break;
            case "triggerbot":
                this.enableTriggerBotDetection = enabled;
                break;
            case "hardware":
                this.enableHardwareFingerprinting = enabled;
                break;
            case "vm":
                this.enableVirtualMachineDetection = enabled;
                break;
            case "debugger":
                this.enableDebuggerDetection = enabled;
                break;
        }
    }
}