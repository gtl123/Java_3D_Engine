package fps.anticheat.statistical;

import engine.logging.LogManager;
import fps.anticheat.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Main statistical analysis engine that detects anomalies in player performance statistics.
 * Analyzes accuracy, headshot ratios, kill/death ratios, and other performance metrics
 * to identify statistically impossible or highly improbable performance patterns.
 */
public class StatisticalAnalysisEngine {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Configuration
    private AntiCheatConfiguration config;
    
    // Analysis components
    private AccuracyAnalyzer accuracyAnalyzer;
    private HeadshotAnalyzer headshotAnalyzer;
    private PerformanceAnalyzer performanceAnalyzer;
    private ConsistencyAnalyzer consistencyAnalyzer;
    private OutlierDetector outlierDetector;
    
    // Player statistics tracking
    private final Map<String, PlayerStatisticalProfile> playerProfiles = new ConcurrentHashMap<>();
    private final Queue<StatisticalEvent> recentEvents = new ConcurrentLinkedQueue<>();
    private final List<StatisticalViolation> detectedViolations = new ArrayList<>();
    
    // Analysis parameters
    private int maxEventsToTrack = 50000;
    private long profileRetentionTime = 7 * 24 * 60 * 60 * 1000L; // 7 days
    private float anomalyThreshold = 0.01f; // 1% probability threshold
    private float violationThreshold = 0.001f; // 0.1% probability threshold
    
    // Performance tracking
    private long totalAnalyses = 0;
    private long totalViolations = 0;
    private long lastAnalysisTime = 0;
    private int analysisInterval = 1000; // milliseconds
    
    // System state
    private boolean initialized = false;
    private boolean analysisActive = false;
    
    public StatisticalAnalysisEngine() {
        logManager.debug("StatisticalAnalysisEngine", "Statistical analysis engine created");
    }
    
    /**
     * Initialize the statistical analysis engine
     */
    public void initialize() throws Exception {
        logManager.info("StatisticalAnalysisEngine", "Initializing statistical analysis engine");
        
        try {
            // Initialize analysis components
            accuracyAnalyzer = new AccuracyAnalyzer();
            accuracyAnalyzer.initialize();
            
            headshotAnalyzer = new HeadshotAnalyzer();
            headshotAnalyzer.initialize();
            
            performanceAnalyzer = new PerformanceAnalyzer();
            performanceAnalyzer.initialize();
            
            consistencyAnalyzer = new ConsistencyAnalyzer();
            consistencyAnalyzer.initialize();
            
            outlierDetector = new OutlierDetector();
            outlierDetector.initialize();
            
            // Start analysis
            analysisActive = true;
            initialized = true;
            
            logManager.info("StatisticalAnalysisEngine", "Statistical analysis engine initialization complete");
            
        } catch (Exception e) {
            logManager.error("StatisticalAnalysisEngine", "Failed to initialize statistical analysis engine", e);
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
            // Configuration could include statistical analysis settings
            
            // Pass configuration to analyzers
            if (accuracyAnalyzer != null) accuracyAnalyzer.setConfiguration(config);
            if (headshotAnalyzer != null) headshotAnalyzer.setConfiguration(config);
            if (performanceAnalyzer != null) performanceAnalyzer.setConfiguration(config);
            if (consistencyAnalyzer != null) consistencyAnalyzer.setConfiguration(config);
            if (outlierDetector != null) outlierDetector.setConfiguration(config);
        }
    }
    
    /**
     * Analyze player statistics for anomalies
     */
    public StatisticalAnalysisResult analyzePlayerStatistics(String playerId, PlayerStatistics statistics) {
        if (!initialized || !analysisActive) {
            return StatisticalAnalysisResult.error("Statistical analysis engine not initialized or not active");
        }
        
        long startTime = System.nanoTime();
        totalAnalyses++;
        
        try {
            StatisticalAnalysisResult result = new StatisticalAnalysisResult();
            
            // Get or create player profile
            PlayerStatisticalProfile profile = getOrCreatePlayerProfile(playerId);
            
            // Create statistical event
            StatisticalEvent event = new StatisticalEvent(playerId, statistics, System.currentTimeMillis());
            
            // Add to recent events
            addStatisticalEvent(event);
            
            // Update player profile with new statistics
            profile.updateStatistics(statistics);
            
            // Perform individual analyses
            List<StatisticalViolation> violations = new ArrayList<>();
            
            // Accuracy analysis
            AccuracyAnalysisResult accuracyResult = accuracyAnalyzer.analyzeAccuracy(profile, statistics);
            if (accuracyResult.hasViolations()) {
                violations.addAll(accuracyResult.getViolations());
            }
            
            // Headshot analysis
            HeadshotAnalysisResult headshotResult = headshotAnalyzer.analyzeHeadshots(profile, statistics);
            if (headshotResult.hasViolations()) {
                violations.addAll(headshotResult.getViolations());
            }
            
            // Performance analysis
            PerformanceAnalysisResult performanceResult = performanceAnalyzer.analyzePerformance(profile, statistics);
            if (performanceResult.hasViolations()) {
                violations.addAll(performanceResult.getViolations());
            }
            
            // Consistency analysis
            ConsistencyAnalysisResult consistencyResult = consistencyAnalyzer.analyzeConsistency(profile, statistics);
            if (consistencyResult.hasViolations()) {
                violations.addAll(consistencyResult.getViolations());
            }
            
            // Outlier detection
            OutlierDetectionResult outlierResult = outlierDetector.detectOutliers(profile, statistics);
            if (outlierResult.hasViolations()) {
                violations.addAll(outlierResult.getViolations());
            }
            
            // Add violations to result
            result.addViolations(violations);
            
            // Update profile anomaly level
            updateProfileAnomalyLevel(profile, violations);
            
            // Check for overall statistical violations
            List<StatisticalViolation> overallViolations = checkOverallStatistics(profile);
            result.addViolations(overallViolations);
            
            // Update statistics
            totalViolations += result.getViolations().size();
            
            // Calculate analysis time
            long analysisTime = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds
            result.setAnalysisTime(analysisTime);
            
            lastAnalysisTime = System.currentTimeMillis();
            
            return result;
            
        } catch (Exception e) {
            logManager.error("StatisticalAnalysisEngine", "Error analyzing player statistics", e,
                           "playerId", playerId);
            return StatisticalAnalysisResult.error("Statistical analysis error: " + e.getMessage());
        }
    }
    
    /**
     * Get or create player statistical profile
     */
    private PlayerStatisticalProfile getOrCreatePlayerProfile(String playerId) {
        PlayerStatisticalProfile profile = playerProfiles.get(playerId);
        
        if (profile == null) {
            profile = new PlayerStatisticalProfile(playerId);
            playerProfiles.put(playerId, profile);
            
            logManager.debug("StatisticalAnalysisEngine", "Created new player statistical profile",
                           "playerId", playerId);
        }
        
        return profile;
    }
    
    /**
     * Add statistical event to tracking
     */
    private void addStatisticalEvent(StatisticalEvent event) {
        recentEvents.offer(event);
        
        // Limit queue size
        while (recentEvents.size() > maxEventsToTrack) {
            recentEvents.poll();
        }
    }
    
    /**
     * Update player profile anomaly level based on violations
     */
    private void updateProfileAnomalyLevel(PlayerStatisticalProfile profile, List<StatisticalViolation> violations) {
        if (violations.isEmpty()) {
            // Decrease anomaly level over time for normal statistics
            profile.decreaseAnomalyLevel(0.01f);
            return;
        }
        
        // Calculate anomaly increase based on violations
        float anomalyIncrease = 0;
        
        for (StatisticalViolation violation : violations) {
            float violationWeight = violation.getConfidence() * violation.getSeverity() / 10.0f;
            anomalyIncrease += violationWeight * 0.1f;
        }
        
        profile.increaseAnomalyLevel(anomalyIncrease);
        
        logManager.debug("StatisticalAnalysisEngine", "Updated player anomaly level",
                        "playerId", profile.getPlayerId(),
                        "anomalyLevel", profile.getAnomalyLevel(),
                        "violations", violations.size());
    }
    
    /**
     * Check for overall statistical violations based on profile
     */
    private List<StatisticalViolation> checkOverallStatistics(PlayerStatisticalProfile profile) {
        List<StatisticalViolation> violations = new ArrayList<>();
        
        float anomalyLevel = profile.getAnomalyLevel();
        
        // Check if anomaly level exceeds thresholds
        if (anomalyLevel >= violationThreshold) {
            violations.add(new StatisticalViolation(
                ViolationType.STATISTICAL_ANOMALY,
                "High statistical anomaly level detected: " + String.format("%.4f", anomalyLevel),
                profile.getPlayerId(),
                Math.min(1.0f, anomalyLevel / violationThreshold),
                "overall_statistical_analysis"
            ));
        } else if (anomalyLevel >= anomalyThreshold) {
            violations.add(new StatisticalViolation(
                ViolationType.SUSPICIOUS_PATTERNS,
                "Elevated statistical anomaly level detected: " + String.format("%.4f", anomalyLevel),
                profile.getPlayerId(),
                Math.min(1.0f, anomalyLevel / anomalyThreshold * 0.8f),
                "overall_statistical_analysis"
            ));
        }
        
        // Check for impossible accuracy
        if (profile.hasImpossibleAccuracy()) {
            violations.add(new StatisticalViolation(
                ViolationType.IMPOSSIBLE_ACCURACY,
                "Impossible accuracy detected",
                profile.getPlayerId(),
                0.95f,
                "impossible_accuracy_check"
            ));
        }
        
        // Check for impossible headshot ratio
        if (profile.hasImpossibleHeadshotRatio()) {
            violations.add(new StatisticalViolation(
                ViolationType.IMPOSSIBLE_HEADSHOT_RATIO,
                "Impossible headshot ratio detected",
                profile.getPlayerId(),
                0.9f,
                "impossible_headshot_check"
            ));
        }
        
        // Check for superhuman performance
        if (profile.hasSuperhumanPerformance()) {
            violations.add(new StatisticalViolation(
                ViolationType.SUPERHUMAN_PERFORMANCE,
                "Superhuman performance detected",
                profile.getPlayerId(),
                0.85f,
                "superhuman_performance_check"
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
            PlayerStatisticalProfile profile = entry.getValue();
            return (currentTime - profile.getLastUpdateTime()) > profileRetentionTime;
        });
        
        // Clean up old events
        recentEvents.removeIf(event -> 
            (currentTime - event.getTimestamp()) > profileRetentionTime);
        
        logManager.debug("StatisticalAnalysisEngine", "Performed cleanup",
                        "activeProfiles", playerProfiles.size(),
                        "recentEvents", recentEvents.size());
    }
    
    /**
     * Get player statistical profile
     */
    public PlayerStatisticalProfile getPlayerProfile(String playerId) {
        return playerProfiles.get(playerId);
    }
    
    /**
     * Get statistical analysis statistics
     */
    public StatisticalAnalysisStatistics getStatistics() {
        StatisticalAnalysisStatistics stats = new StatisticalAnalysisStatistics();
        stats.totalAnalyses = totalAnalyses;
        stats.totalViolations = totalViolations;
        stats.activeProfiles = playerProfiles.size();
        stats.recentEvents = recentEvents.size();
        stats.lastAnalysisTime = lastAnalysisTime;
        stats.analysisActive = analysisActive;
        
        // Get component statistics
        if (accuracyAnalyzer != null) {
            stats.accuracyAnalysisStats = accuracyAnalyzer.getStatistics();
        }
        if (headshotAnalyzer != null) {
            stats.headshotAnalysisStats = headshotAnalyzer.getStatistics();
        }
        if (performanceAnalyzer != null) {
            stats.performanceAnalysisStats = performanceAnalyzer.getStatistics();
        }
        if (consistencyAnalyzer != null) {
            stats.consistencyAnalysisStats = consistencyAnalyzer.getStatistics();
        }
        if (outlierDetector != null) {
            stats.outlierDetectionStats = outlierDetector.getStatistics();
        }
        
        return stats;
    }
    
    /**
     * Update statistical analysis engine
     */
    public void update(float deltaTime) {
        if (!initialized || !analysisActive) {
            return;
        }
        
        // Update analysis components
        if (accuracyAnalyzer != null) accuracyAnalyzer.update(deltaTime);
        if (headshotAnalyzer != null) headshotAnalyzer.update(deltaTime);
        if (performanceAnalyzer != null) performanceAnalyzer.update(deltaTime);
        if (consistencyAnalyzer != null) consistencyAnalyzer.update(deltaTime);
        if (outlierDetector != null) outlierDetector.update(deltaTime);
        
        // Perform periodic cleanup
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAnalysisTime > 300000) { // Every 5 minutes
            performCleanup();
        }
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        analysisActive = false;
        
        if (accuracyAnalyzer != null) accuracyAnalyzer.cleanup();
        if (headshotAnalyzer != null) headshotAnalyzer.cleanup();
        if (performanceAnalyzer != null) performanceAnalyzer.cleanup();
        if (consistencyAnalyzer != null) consistencyAnalyzer.cleanup();
        if (outlierDetector != null) outlierDetector.cleanup();
        
        playerProfiles.clear();
        recentEvents.clear();
        detectedViolations.clear();
        initialized = false;
        
        logManager.debug("StatisticalAnalysisEngine", "Statistical analysis engine cleaned up");
    }
    
    // Getters and setters
    public Map<String, PlayerStatisticalProfile> getPlayerProfiles() { return new ConcurrentHashMap<>(playerProfiles); }
    public Queue<StatisticalEvent> getRecentEvents() { return new ConcurrentLinkedQueue<>(recentEvents); }
    public int getMaxEventsToTrack() { return maxEventsToTrack; }
    public void setMaxEventsToTrack(int maxEventsToTrack) { this.maxEventsToTrack = maxEventsToTrack; }
    public long getProfileRetentionTime() { return profileRetentionTime; }
    public void setProfileRetentionTime(long profileRetentionTime) { this.profileRetentionTime = profileRetentionTime; }
    public float getAnomalyThreshold() { return anomalyThreshold; }
    public void setAnomalyThreshold(float anomalyThreshold) { this.anomalyThreshold = anomalyThreshold; }
    public float getViolationThreshold() { return violationThreshold; }
    public void setViolationThreshold(float violationThreshold) { this.violationThreshold = violationThreshold; }
    public int getAnalysisInterval() { return analysisInterval; }
    public void setAnalysisInterval(int analysisInterval) { this.analysisInterval = analysisInterval; }
    public boolean isInitialized() { return initialized; }
    public boolean isAnalysisActive() { return analysisActive; }
    public void setAnalysisActive(boolean analysisActive) { this.analysisActive = analysisActive; }
    
    /**
     * Statistical analysis statistics
     */
    public static class StatisticalAnalysisStatistics {
        public long totalAnalyses = 0;
        public long totalViolations = 0;
        public int activeProfiles = 0;
        public int recentEvents = 0;
        public long lastAnalysisTime = 0;
        public boolean analysisActive = false;
        
        // Component statistics
        public Object accuracyAnalysisStats;
        public Object headshotAnalysisStats;
        public Object performanceAnalysisStats;
        public Object consistencyAnalysisStats;
        public Object outlierDetectionStats;
        
        @Override
        public String toString() {
            return String.format("StatisticalAnalysisStatistics{analyses=%d, violations=%d, profiles=%d, events=%d, active=%s}",
                               totalAnalyses, totalViolations, activeProfiles, recentEvents, analysisActive);
        }
    }
}