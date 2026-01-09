package fps.anticheat.behavioral;

import engine.logging.LogManager;
import fps.anticheat.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Main behavioral analysis engine that coordinates all behavioral detection systems.
 * Analyzes player behavior patterns to detect inhuman or robotic gameplay characteristics.
 */
public class BehavioralAnalysisEngine {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Configuration
    private AntiCheatConfiguration config;
    
    // Analysis components
    private AimAnalyzer aimAnalyzer;
    private MovementAnalyzer movementAnalyzer;
    private ReactionTimeAnalyzer reactionTimeAnalyzer;
    private InputPatternAnalyzer inputPatternAnalyzer;
    private TimingAnalyzer timingAnalyzer;
    
    // Player behavior tracking
    private final Map<String, PlayerBehaviorProfile> playerProfiles = new ConcurrentHashMap<>();
    private final Queue<BehaviorEvent> recentEvents = new ConcurrentLinkedQueue<>();
    private final List<BehavioralViolation> detectedViolations = new ArrayList<>();
    
    // Analysis parameters
    private int maxEventsToTrack = 10000;
    private long profileRetentionTime = 24 * 60 * 60 * 1000L; // 24 hours
    private float suspicionThreshold = 0.7f;
    private float violationThreshold = 0.8f;
    
    // Performance tracking
    private long totalAnalyses = 0;
    private long totalViolations = 0;
    private long lastAnalysisTime = 0;
    private int analysisInterval = 100; // milliseconds
    
    // System state
    private boolean initialized = false;
    private boolean analysisActive = false;
    
    public BehavioralAnalysisEngine() {
        logManager.debug("BehavioralAnalysisEngine", "Behavioral analysis engine created");
    }
    
    /**
     * Initialize the behavioral analysis engine
     */
    public void initialize() throws Exception {
        logManager.info("BehavioralAnalysisEngine", "Initializing behavioral analysis engine");
        
        try {
            // Initialize analysis components
            aimAnalyzer = new AimAnalyzer();
            aimAnalyzer.initialize();
            
            movementAnalyzer = new MovementAnalyzer();
            movementAnalyzer.initialize();
            
            reactionTimeAnalyzer = new ReactionTimeAnalyzer();
            reactionTimeAnalyzer.initialize();
            
            inputPatternAnalyzer = new InputPatternAnalyzer();
            inputPatternAnalyzer.initialize();
            
            timingAnalyzer = new TimingAnalyzer();
            timingAnalyzer.initialize();
            
            // Start analysis
            analysisActive = true;
            initialized = true;
            
            logManager.info("BehavioralAnalysisEngine", "Behavioral analysis engine initialization complete");
            
        } catch (Exception e) {
            logManager.error("BehavioralAnalysisEngine", "Failed to initialize behavioral analysis engine", e);
            throw e;
        }
    }
    
    /**
     * Set configuration
     */
    public void setConfiguration(AntiCheatConfiguration config) {
        this.config = config;
        
        if (config != null) {
            // Update analysis parameters based on configuration
            // Configuration could include behavioral analysis settings
            
            // Pass configuration to analyzers
            if (aimAnalyzer != null) aimAnalyzer.setConfiguration(config);
            if (movementAnalyzer != null) movementAnalyzer.setConfiguration(config);
            if (reactionTimeAnalyzer != null) reactionTimeAnalyzer.setConfiguration(config);
            if (inputPatternAnalyzer != null) inputPatternAnalyzer.setConfiguration(config);
            if (timingAnalyzer != null) timingAnalyzer.setConfiguration(config);
        }
    }
    
    /**
     * Analyze player action for behavioral patterns
     */
    public BehavioralAnalysisResult analyzePlayerAction(String playerId, PlayerAction action) {
        if (!initialized || !analysisActive) {
            return BehavioralAnalysisResult.error("Behavioral analysis engine not initialized or not active");
        }
        
        long startTime = System.nanoTime();
        totalAnalyses++;
        
        try {
            BehavioralAnalysisResult result = new BehavioralAnalysisResult();
            
            // Get or create player profile
            PlayerBehaviorProfile profile = getOrCreatePlayerProfile(playerId);
            
            // Create behavior event
            BehaviorEvent event = new BehaviorEvent(playerId, action, System.currentTimeMillis());
            
            // Add to recent events
            addBehaviorEvent(event);
            
            // Update player profile with new action
            profile.addAction(action);
            
            // Perform individual analyses
            List<BehavioralViolation> violations = new ArrayList<>();
            
            // Aim analysis
            AimAnalysisResult aimResult = aimAnalyzer.analyzeAim(profile, action);
            if (aimResult.hasViolations()) {
                violations.addAll(aimResult.getViolations());
            }
            
            // Movement analysis
            MovementAnalysisResult movementResult = movementAnalyzer.analyzeMovement(profile, action);
            if (movementResult.hasViolations()) {
                violations.addAll(movementResult.getViolations());
            }
            
            // Reaction time analysis
            ReactionTimeAnalysisResult reactionResult = reactionTimeAnalyzer.analyzeReactionTime(profile, action);
            if (reactionResult.hasViolations()) {
                violations.addAll(reactionResult.getViolations());
            }
            
            // Input pattern analysis
            InputPatternAnalysisResult inputResult = inputPatternAnalyzer.analyzeInputPattern(profile, action);
            if (inputResult.hasViolations()) {
                violations.addAll(inputResult.getViolations());
            }
            
            // Timing analysis
            TimingAnalysisResult timingResult = timingAnalyzer.analyzeTiming(profile, action);
            if (timingResult.hasViolations()) {
                violations.addAll(timingResult.getViolations());
            }
            
            // Add violations to result
            result.addViolations(violations);
            
            // Update profile suspicion level
            updateProfileSuspicion(profile, violations);
            
            // Check for overall behavioral violations
            List<BehavioralViolation> overallViolations = checkOverallBehavior(profile);
            result.addViolations(overallViolations);
            
            // Update statistics
            totalViolations += result.getViolations().size();
            
            // Calculate analysis time
            long analysisTime = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds
            result.setAnalysisTime(analysisTime);
            
            lastAnalysisTime = System.currentTimeMillis();
            
            return result;
            
        } catch (Exception e) {
            logManager.error("BehavioralAnalysisEngine", "Error analyzing player action", e,
                           "playerId", playerId);
            return BehavioralAnalysisResult.error("Behavioral analysis error: " + e.getMessage());
        }
    }
    
    /**
     * Get or create player behavior profile
     */
    private PlayerBehaviorProfile getOrCreatePlayerProfile(String playerId) {
        PlayerBehaviorProfile profile = playerProfiles.get(playerId);
        
        if (profile == null) {
            profile = new PlayerBehaviorProfile(playerId);
            playerProfiles.put(playerId, profile);
            
            logManager.debug("BehavioralAnalysisEngine", "Created new player behavior profile",
                           "playerId", playerId);
        }
        
        return profile;
    }
    
    /**
     * Add behavior event to tracking
     */
    private void addBehaviorEvent(BehaviorEvent event) {
        recentEvents.offer(event);
        
        // Limit queue size
        while (recentEvents.size() > maxEventsToTrack) {
            recentEvents.poll();
        }
    }
    
    /**
     * Update player profile suspicion level based on violations
     */
    private void updateProfileSuspicion(PlayerBehaviorProfile profile, List<BehavioralViolation> violations) {
        if (violations.isEmpty()) {
            // Decrease suspicion over time for clean behavior
            profile.decreaseSuspicion(0.01f);
            return;
        }
        
        // Calculate suspicion increase based on violations
        float suspicionIncrease = 0;
        
        for (BehavioralViolation violation : violations) {
            float violationWeight = violation.getConfidence() * violation.getSeverity() / 10.0f;
            suspicionIncrease += violationWeight * 0.1f;
        }
        
        profile.increaseSuspicion(suspicionIncrease);
        
        logManager.debug("BehavioralAnalysisEngine", "Updated player suspicion",
                        "playerId", profile.getPlayerId(),
                        "suspicionLevel", profile.getSuspicionLevel(),
                        "violations", violations.size());
    }
    
    /**
     * Check for overall behavioral violations based on profile
     */
    private List<BehavioralViolation> checkOverallBehavior(PlayerBehaviorProfile profile) {
        List<BehavioralViolation> violations = new ArrayList<>();
        
        float suspicionLevel = profile.getSuspicionLevel();
        
        // Check if suspicion level exceeds thresholds
        if (suspicionLevel >= violationThreshold) {
            violations.add(new BehavioralViolation(
                ViolationType.BEHAVIORAL_ANALYSIS,
                "High suspicion level detected: " + String.format("%.2f", suspicionLevel),
                profile.getPlayerId(),
                suspicionLevel,
                "overall_behavior_analysis"
            ));
        } else if (suspicionLevel >= suspicionThreshold) {
            violations.add(new BehavioralViolation(
                ViolationType.SUSPICIOUS_PATTERNS,
                "Elevated suspicion level detected: " + String.format("%.2f", suspicionLevel),
                profile.getPlayerId(),
                suspicionLevel * 0.8f,
                "overall_behavior_analysis"
            ));
        }
        
        // Check for consistent inhuman performance
        if (profile.hasConsistentInhumanPerformance()) {
            violations.add(new BehavioralViolation(
                ViolationType.INHUMAN_REACTIONS,
                "Consistent inhuman performance detected",
                profile.getPlayerId(),
                0.9f,
                "inhuman_performance_check"
            ));
        }
        
        // Check for robotic movement patterns
        if (profile.hasRoboticMovementPatterns()) {
            violations.add(new BehavioralViolation(
                ViolationType.ROBOTIC_MOVEMENT,
                "Robotic movement patterns detected",
                profile.getPlayerId(),
                0.8f,
                "robotic_movement_check"
            ));
        }
        
        // Check for perfect tracking
        if (profile.hasPerfectTrackingPatterns()) {
            violations.add(new BehavioralViolation(
                ViolationType.PERFECT_TRACKING,
                "Perfect tracking patterns detected",
                profile.getPlayerId(),
                0.85f,
                "perfect_tracking_check"
            ));
        }
        
        return violations;
    }
    
    /**
     * Perform periodic cleanup of old data
     */
    public void performCleanup() {
        long currentTime = System.currentTimeMillis();
        
        // Clean up old player profiles
        playerProfiles.entrySet().removeIf(entry -> {
            PlayerBehaviorProfile profile = entry.getValue();
            return (currentTime - profile.getLastActivityTime()) > profileRetentionTime;
        });
        
        // Clean up old events
        recentEvents.removeIf(event -> 
            (currentTime - event.getTimestamp()) > profileRetentionTime);
        
        logManager.debug("BehavioralAnalysisEngine", "Performed cleanup",
                        "activeProfiles", playerProfiles.size(),
                        "recentEvents", recentEvents.size());
    }
    
    /**
     * Get player behavior profile
     */
    public PlayerBehaviorProfile getPlayerProfile(String playerId) {
        return playerProfiles.get(playerId);
    }
    
    /**
     * Get behavioral analysis statistics
     */
    public BehavioralAnalysisStatistics getStatistics() {
        BehavioralAnalysisStatistics stats = new BehavioralAnalysisStatistics();
        stats.totalAnalyses = totalAnalyses;
        stats.totalViolations = totalViolations;
        stats.activeProfiles = playerProfiles.size();
        stats.recentEvents = recentEvents.size();
        stats.lastAnalysisTime = lastAnalysisTime;
        stats.analysisActive = analysisActive;
        
        // Get component statistics
        if (aimAnalyzer != null) {
            stats.aimAnalysisStats = aimAnalyzer.getStatistics();
        }
        if (movementAnalyzer != null) {
            stats.movementAnalysisStats = movementAnalyzer.getStatistics();
        }
        if (reactionTimeAnalyzer != null) {
            stats.reactionTimeAnalysisStats = reactionTimeAnalyzer.getStatistics();
        }
        if (inputPatternAnalyzer != null) {
            stats.inputPatternAnalysisStats = inputPatternAnalyzer.getStatistics();
        }
        if (timingAnalyzer != null) {
            stats.timingAnalysisStats = timingAnalyzer.getStatistics();
        }
        
        return stats;
    }
    
    /**
     * Update behavioral analysis engine
     */
    public void update(float deltaTime) {
        if (!initialized || !analysisActive) {
            return;
        }
        
        // Update analysis components
        if (aimAnalyzer != null) aimAnalyzer.update(deltaTime);
        if (movementAnalyzer != null) movementAnalyzer.update(deltaTime);
        if (reactionTimeAnalyzer != null) reactionTimeAnalyzer.update(deltaTime);
        if (inputPatternAnalyzer != null) inputPatternAnalyzer.update(deltaTime);
        if (timingAnalyzer != null) timingAnalyzer.update(deltaTime);
        
        // Perform periodic cleanup
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAnalysisTime > 60000) { // Every minute
            performCleanup();
        }
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        analysisActive = false;
        
        if (aimAnalyzer != null) aimAnalyzer.cleanup();
        if (movementAnalyzer != null) movementAnalyzer.cleanup();
        if (reactionTimeAnalyzer != null) reactionTimeAnalyzer.cleanup();
        if (inputPatternAnalyzer != null) inputPatternAnalyzer.cleanup();
        if (timingAnalyzer != null) timingAnalyzer.cleanup();
        
        playerProfiles.clear();
        recentEvents.clear();
        detectedViolations.clear();
        initialized = false;
        
        logManager.debug("BehavioralAnalysisEngine", "Behavioral analysis engine cleaned up");
    }
    
    // Getters and setters
    public Map<String, PlayerBehaviorProfile> getPlayerProfiles() { return new ConcurrentHashMap<>(playerProfiles); }
    public Queue<BehaviorEvent> getRecentEvents() { return new ConcurrentLinkedQueue<>(recentEvents); }
    public int getMaxEventsToTrack() { return maxEventsToTrack; }
    public void setMaxEventsToTrack(int maxEventsToTrack) { this.maxEventsToTrack = maxEventsToTrack; }
    public long getProfileRetentionTime() { return profileRetentionTime; }
    public void setProfileRetentionTime(long profileRetentionTime) { this.profileRetentionTime = profileRetentionTime; }
    public float getSuspicionThreshold() { return suspicionThreshold; }
    public void setSuspicionThreshold(float suspicionThreshold) { this.suspicionThreshold = suspicionThreshold; }
    public float getViolationThreshold() { return violationThreshold; }
    public void setViolationThreshold(float violationThreshold) { this.violationThreshold = violationThreshold; }
    public int getAnalysisInterval() { return analysisInterval; }
    public void setAnalysisInterval(int analysisInterval) { this.analysisInterval = analysisInterval; }
    public boolean isInitialized() { return initialized; }
    public boolean isAnalysisActive() { return analysisActive; }
    public void setAnalysisActive(boolean analysisActive) { this.analysisActive = analysisActive; }
    
    /**
     * Behavioral analysis statistics
     */
    public static class BehavioralAnalysisStatistics {
        public long totalAnalyses = 0;
        public long totalViolations = 0;
        public int activeProfiles = 0;
        public int recentEvents = 0;
        public long lastAnalysisTime = 0;
        public boolean analysisActive = false;
        
        // Component statistics
        public Object aimAnalysisStats;
        public Object movementAnalysisStats;
        public Object reactionTimeAnalysisStats;
        public Object inputPatternAnalysisStats;
        public Object timingAnalysisStats;
        
        @Override
        public String toString() {
            return String.format("BehavioralAnalysisStatistics{analyses=%d, violations=%d, profiles=%d, events=%d, active=%s}",
                               totalAnalyses, totalViolations, activeProfiles, recentEvents, analysisActive);
        }
    }
}