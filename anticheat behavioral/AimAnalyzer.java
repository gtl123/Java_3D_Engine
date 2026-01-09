package fps.anticheat.behavioral;

import engine.logging.LogManager;
import fps.anticheat.*;
import engine.math.Vector3f;

import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Analyzes player aim behavior to detect aimbots, aim assistance, and inhuman aiming patterns.
 * Focuses on aim smoothness, tracking accuracy, and reaction time analysis.
 */
public class AimAnalyzer {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Configuration
    private AntiCheatConfiguration config;
    
    // Analysis thresholds
    private float perfectTrackingThreshold = 0.98f;
    private float inhumanSmoothnessThreshold = 0.95f;
    private float flickShotAccuracyThreshold = 0.9f;
    private int minSamplesForAnalysis = 10;
    
    // Statistics tracking
    private long totalAnalyses = 0;
    private long totalViolations = 0;
    private boolean initialized = false;
    
    public AimAnalyzer() {
        logManager.debug("AimAnalyzer", "Aim analyzer created");
    }
    
    /**
     * Initialize the aim analyzer
     */
    public void initialize() throws Exception {
        logManager.info("AimAnalyzer", "Initializing aim analyzer");
        
        try {
            initialized = true;
            logManager.info("AimAnalyzer", "Aim analyzer initialization complete");
            
        } catch (Exception e) {
            logManager.error("AimAnalyzer", "Failed to initialize aim analyzer", e);
            throw e;
        }
    }
    
    /**
     * Set configuration
     */
    public void setConfiguration(AntiCheatConfiguration config) {
        this.config = config;
        
        if (config != null) {
            // Update thresholds based on configuration
            // Configuration could include aim analysis settings
        }
    }
    
    /**
     * Analyze aim behavior for a player action
     */
    public AimAnalysisResult analyzeAim(PlayerBehaviorProfile profile, PlayerAction action) {
        if (!initialized) {
            return AimAnalysisResult.error("Aim analyzer not initialized");
        }
        
        totalAnalyses++;
        
        try {
            AimAnalysisResult result = new AimAnalysisResult();
            List<BehavioralViolation> violations = new ArrayList<>();
            
            // Only analyze aim-related actions
            if (!action.isAiming() && !action.isShooting()) {
                return result;
            }
            
            PlayerBehaviorProfile.AimBehaviorMetrics aimMetrics = profile.getAimMetrics();
            
            // Check for perfect tracking
            BehavioralViolation perfectTracking = checkPerfectTracking(profile, action, aimMetrics);
            if (perfectTracking != null) {
                violations.add(perfectTracking);
            }
            
            // Check for inhuman smoothness
            BehavioralViolation inhumanSmoothness = checkInhumanSmoothness(profile, action, aimMetrics);
            if (inhumanSmoothness != null) {
                violations.add(inhumanSmoothness);
            }
            
            // Check for impossible flick shots
            BehavioralViolation impossibleFlicks = checkImpossibleFlickShots(profile, action, aimMetrics);
            if (impossibleFlicks != null) {
                violations.add(impossibleFlicks);
            }
            
            // Check for robotic aim patterns
            BehavioralViolation roboticAim = checkRoboticAimPatterns(profile, action, aimMetrics);
            if (roboticAim != null) {
                violations.add(roboticAim);
            }
            
            // Check for aim lock behavior
            BehavioralViolation aimLock = checkAimLockBehavior(profile, action, aimMetrics);
            if (aimLock != null) {
                violations.add(aimLock);
            }
            
            result.addViolations(violations);
            totalViolations += violations.size();
            
            return result;
            
        } catch (Exception e) {
            logManager.error("AimAnalyzer", "Error analyzing aim behavior", e,
                           "playerId", profile.getPlayerId());
            return AimAnalysisResult.error("Aim analysis error: " + e.getMessage());
        }
    }
    
    /**
     * Check for perfect tracking patterns
     */
    private BehavioralViolation checkPerfectTracking(PlayerBehaviorProfile profile, PlayerAction action, 
                                                    PlayerBehaviorProfile.AimBehaviorMetrics aimMetrics) {
        
        if (aimMetrics.totalAimActions < minSamplesForAnalysis) {
            return null;
        }
        
        // Check if aim smoothness is too perfect
        if (aimMetrics.averageSmoothness >= perfectTrackingThreshold) {
            float confidence = Math.min(1.0f, (aimMetrics.averageSmoothness - perfectTrackingThreshold) / (1.0f - perfectTrackingThreshold));
            
            return new BehavioralViolation(
                ViolationType.PERFECT_TRACKING,
                "Perfect aim tracking detected: smoothness = " + String.format("%.3f", aimMetrics.averageSmoothness),
                profile.getPlayerId(),
                confidence,
                "perfect_tracking_analysis",
                profile
            );
        }
        
        return null;
    }
    
    /**
     * Check for inhuman smoothness in aim
     */
    private BehavioralViolation checkInhumanSmoothness(PlayerBehaviorProfile profile, PlayerAction action,
                                                      PlayerBehaviorProfile.AimBehaviorMetrics aimMetrics) {
        
        if (aimMetrics.rotationHistory.size() < minSamplesForAnalysis) {
            return null;
        }
        
        // Calculate smoothness variance
        Float[] rotations = aimMetrics.rotationHistory.toArray(new Float[0]);
        float variance = calculateRotationVariance(rotations);
        
        // Very low variance indicates inhuman consistency
        if (variance < 0.01f && aimMetrics.averageSmoothness >= inhumanSmoothnessThreshold) {
            float confidence = Math.min(1.0f, (inhumanSmoothnessThreshold - variance) / inhumanSmoothnessThreshold);
            
            return new BehavioralViolation(
                ViolationType.INHUMAN_REACTIONS,
                "Inhuman aim smoothness detected: variance = " + String.format("%.4f", variance),
                profile.getPlayerId(),
                confidence,
                "inhuman_smoothness_analysis",
                profile
            );
        }
        
        return null;
    }
    
    /**
     * Calculate rotation variance for smoothness analysis
     */
    private float calculateRotationVariance(Float[] rotations) {
        if (rotations.length < 2) {
            return 1.0f;
        }
        
        float mean = 0;
        for (float rotation : rotations) {
            mean += rotation;
        }
        mean /= rotations.length;
        
        float variance = 0;
        for (float rotation : rotations) {
            variance += (rotation - mean) * (rotation - mean);
        }
        variance /= rotations.length;
        
        return variance;
    }
    
    /**
     * Check for impossible flick shots
     */
    private BehavioralViolation checkImpossibleFlickShots(PlayerBehaviorProfile profile, PlayerAction action,
                                                         PlayerBehaviorProfile.AimBehaviorMetrics aimMetrics) {
        
        if (!action.isShooting() || aimMetrics.totalShots < 10) {
            return null;
        }
        
        // Check flick shot accuracy ratio
        if (aimMetrics.flickShots > 0) {
            float flickRatio = (float) aimMetrics.flickShots / aimMetrics.totalShots;
            
            if (flickRatio >= flickShotAccuracyThreshold) {
                float confidence = Math.min(1.0f, (flickRatio - flickShotAccuracyThreshold) / (1.0f - flickShotAccuracyThreshold));
                
                return new BehavioralViolation(
                    ViolationType.PERFECT_TRACKING,
                    "Impossible flick shot accuracy: " + String.format("%.1f%%", flickRatio * 100) + " flick shots",
                    profile.getPlayerId(),
                    confidence,
                    "flick_shot_analysis",
                    profile
                );
            }
        }
        
        // Check for instant target acquisition
        Vector3f rotation = action.getRotation();
        float rotationMagnitude = rotation.length();
        
        if (action.isShooting() && rotationMagnitude > 30.0f) {
            // Large rotation followed immediately by accurate shot
            long timeSinceLastAim = action.getTimestamp() - aimMetrics.lastAimTime;
            
            if (timeSinceLastAim < 50) { // Less than 50ms reaction time
                return new BehavioralViolation(
                    ViolationType.INHUMAN_REACTIONS,
                    "Instant target acquisition: " + String.format("%.1f°", rotationMagnitude) + " rotation in " + timeSinceLastAim + "ms",
                    profile.getPlayerId(),
                    0.8f,
                    "instant_acquisition_analysis",
                    profile
                );
            }
        }
        
        return null;
    }
    
    /**
     * Check for robotic aim patterns
     */
    private BehavioralViolation checkRoboticAimPatterns(PlayerBehaviorProfile profile, PlayerAction action,
                                                       PlayerBehaviorProfile.AimBehaviorMetrics aimMetrics) {
        
        if (aimMetrics.rotationHistory.size() < 20) {
            return null;
        }
        
        // Check for perfectly linear aim movements
        Float[] rotations = aimMetrics.rotationHistory.toArray(new Float[0]);
        boolean hasLinearPattern = checkLinearAimPattern(rotations);
        
        if (hasLinearPattern && aimMetrics.averageSmoothness > 0.9f) {
            return new BehavioralViolation(
                ViolationType.ROBOTIC_MOVEMENT,
                "Robotic aim patterns detected: linear movement with high smoothness",
                profile.getPlayerId(),
                0.75f,
                "robotic_aim_analysis",
                profile
            );
        }
        
        // Check for repetitive aim patterns
        boolean hasRepetitivePattern = checkRepetitiveAimPattern(rotations);
        
        if (hasRepetitivePattern) {
            return new BehavioralViolation(
                ViolationType.SUSPICIOUS_PATTERNS,
                "Repetitive aim patterns detected",
                profile.getPlayerId(),
                0.7f,
                "repetitive_aim_analysis",
                profile
            );
        }
        
        return null;
    }
    
    /**
     * Check for linear aim patterns
     */
    private boolean checkLinearAimPattern(Float[] rotations) {
        if (rotations.length < 10) {
            return false;
        }
        
        int linearSequences = 0;
        int minLinearLength = 5;
        
        for (int i = 0; i <= rotations.length - minLinearLength; i++) {
            boolean isLinear = true;
            float slope = rotations[i + 1] - rotations[i];
            
            for (int j = i + 1; j < i + minLinearLength - 1; j++) {
                float currentSlope = rotations[j + 1] - rotations[j];
                if (Math.abs(currentSlope - slope) > 0.1f) {
                    isLinear = false;
                    break;
                }
            }
            
            if (isLinear) {
                linearSequences++;
            }
        }
        
        // If more than 30% of sequences are linear, it's suspicious
        return linearSequences > (rotations.length - minLinearLength + 1) * 0.3f;
    }
    
    /**
     * Check for repetitive aim patterns
     */
    private boolean checkRepetitiveAimPattern(Float[] rotations) {
        if (rotations.length < 20) {
            return false;
        }
        
        // Look for repeating sequences
        int patternLength = 3;
        int repetitions = 0;
        
        for (int i = 0; i <= rotations.length - patternLength * 2; i++) {
            boolean isRepeating = true;
            
            for (int j = 0; j < patternLength; j++) {
                if (Math.abs(rotations[i + j] - rotations[i + patternLength + j]) > 0.5f) {
                    isRepeating = false;
                    break;
                }
            }
            
            if (isRepeating) {
                repetitions++;
            }
        }
        
        // If more than 20% of patterns repeat, it's suspicious
        return repetitions > (rotations.length - patternLength * 2 + 1) * 0.2f;
    }
    
    /**
     * Check for aim lock behavior
     */
    private BehavioralViolation checkAimLockBehavior(PlayerBehaviorProfile profile, PlayerAction action,
                                                    PlayerBehaviorProfile.AimBehaviorMetrics aimMetrics) {
        
        if (aimMetrics.rotationHistory.size() < 10) {
            return null;
        }
        
        // Check for sudden stops in aim movement (aim lock)
        Float[] rotations = aimMetrics.rotationHistory.toArray(new Float[0]);
        
        // Look for patterns where rotation suddenly stops to zero
        int suddenStops = 0;
        for (int i = 1; i < rotations.length - 1; i++) {
            if (rotations[i - 1] > 5.0f && rotations[i] < 0.1f && rotations[i + 1] < 0.1f) {
                suddenStops++;
            }
        }
        
        if (suddenStops > rotations.length * 0.1f) { // More than 10% sudden stops
            return new BehavioralViolation(
                ViolationType.PERFECT_TRACKING,
                "Aim lock behavior detected: " + suddenStops + " sudden stops in " + rotations.length + " samples",
                profile.getPlayerId(),
                0.8f,
                "aim_lock_analysis",
                profile
            );
        }
        
        return null;
    }
    
    /**
     * Get aim analyzer statistics
     */
    public AimAnalyzerStatistics getStatistics() {
        AimAnalyzerStatistics stats = new AimAnalyzerStatistics();
        stats.totalAnalyses = totalAnalyses;
        stats.totalViolations = totalViolations;
        stats.initialized = initialized;
        
        return stats;
    }
    
    /**
     * Update aim analyzer
     */
    public void update(float deltaTime) {
        // Update any time-based parameters
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        initialized = false;
        logManager.debug("AimAnalyzer", "Aim analyzer cleaned up");
    }
    
    // Getters and setters
    public float getPerfectTrackingThreshold() { return perfectTrackingThreshold; }
    public void setPerfectTrackingThreshold(float perfectTrackingThreshold) { this.perfectTrackingThreshold = perfectTrackingThreshold; }
    public float getInhumanSmoothnessThreshold() { return inhumanSmoothnessThreshold; }
    public void setInhumanSmoothnessThreshold(float inhumanSmoothnessThreshold) { this.inhumanSmoothnessThreshold = inhumanSmoothnessThreshold; }
    public float getFlickShotAccuracyThreshold() { return flickShotAccuracyThreshold; }
    public void setFlickShotAccuracyThreshold(float flickShotAccuracyThreshold) { this.flickShotAccuracyThreshold = flickShotAccuracyThreshold; }
    public int getMinSamplesForAnalysis() { return minSamplesForAnalysis; }
    public void setMinSamplesForAnalysis(int minSamplesForAnalysis) { this.minSamplesForAnalysis = minSamplesForAnalysis; }
    public boolean isInitialized() { return initialized; }
    
    /**
     * Aim analyzer statistics
     */
    public static class AimAnalyzerStatistics {
        public long totalAnalyses = 0;
        public long totalViolations = 0;
        public boolean initialized = false;
        
        @Override
        public String toString() {
            return String.format("AimAnalyzerStatistics{analyses=%d, violations=%d, initialized=%s}",
                               totalAnalyses, totalViolations, initialized);
        }
    }
}

/**
 * Result of aim analysis
 */
class AimAnalysisResult {
    private final List<BehavioralViolation> violations = new ArrayList<>();
    private boolean successful = true;
    private String errorMessage;
    
    public static AimAnalysisResult error(String errorMessage) {
        AimAnalysisResult result = new AimAnalysisResult();
        result.successful = false;
        result.errorMessage = errorMessage;
        return result;
    }
    
    public void addViolation(BehavioralViolation violation) {
        if (violation != null) {
            violations.add(violation);
        }
    }
    
    public void addViolations(List<BehavioralViolation> violations) {
        if (violations != null) {
            this.violations.addAll(violations);
        }
    }
    
    public boolean hasViolations() {
        return !violations.isEmpty();
    }
    
    public List<BehavioralViolation> getViolations() {
        return new ArrayList<>(violations);
    }
    
    public boolean isSuccessful() { return successful; }
    public String getErrorMessage() { return errorMessage; }
}