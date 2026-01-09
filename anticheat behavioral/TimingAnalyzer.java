package fps.anticheat.behavioral;

import engine.logging.LogManager;
import fps.anticheat.*;

import java.util.List;
import java.util.ArrayList;

/**
 * Analyzes player timing patterns to detect automated timing and macro usage.
 */
public class TimingAnalyzer {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private AntiCheatConfiguration config;
    private float perfectTimingThreshold = 0.98f;
    private int minSamplesForAnalysis = 10;
    private long totalAnalyses = 0;
    private long totalViolations = 0;
    private boolean initialized = false;
    
    public TimingAnalyzer() {
        logManager.debug("TimingAnalyzer", "Timing analyzer created");
    }
    
    public void initialize() throws Exception {
        initialized = true;
        logManager.info("TimingAnalyzer", "Timing analyzer initialized");
    }
    
    public void setConfiguration(AntiCheatConfiguration config) {
        this.config = config;
    }
    
    public TimingAnalysisResult analyzeTiming(PlayerBehaviorProfile profile, PlayerAction action) {
        if (!initialized) {
            return TimingAnalysisResult.error("Timing analyzer not initialized");
        }
        
        totalAnalyses++;
        TimingAnalysisResult result = new TimingAnalysisResult();
        List<BehavioralViolation> violations = new ArrayList<>();
        
        PlayerBehaviorProfile.TimingBehaviorMetrics timingMetrics = profile.getTimingMetrics();
        
        // Check for perfect timing patterns (indicating macros)
        if (timingMetrics.shotIntervals.size() >= minSamplesForAnalysis) {
            float timingConsistency = calculateTimingConsistency(timingMetrics);
            
            if (timingConsistency >= perfectTimingThreshold) {
                violations.add(new BehavioralViolation(
                    ViolationType.SUSPICIOUS_PATTERNS,
                    "Perfect timing patterns detected: consistency = " + String.format("%.3f", timingConsistency),
                    profile.getPlayerId(),
                    timingConsistency,
                    "timing_analysis",
                    profile
                ));
            }
        }
        
        result.addViolations(violations);
        totalViolations += violations.size();
        return result;
    }
    
    private float calculateTimingConsistency(PlayerBehaviorProfile.TimingBehaviorMetrics timingMetrics) {
        if (timingMetrics.shotIntervals.size() < 2) {
            return 0.5f;
        }
        
        Long[] intervals = timingMetrics.shotIntervals.toArray(new Long[0]);
        double mean = 0;
        for (long interval : intervals) {
            mean += interval;
        }
        mean /= intervals.length;
        
        double variance = 0;
        for (long interval : intervals) {
            variance += (interval - mean) * (interval - mean);
        }
        variance /= intervals.length;
        
        double standardDeviation = Math.sqrt(variance);
        
        // Lower standard deviation = higher consistency
        return (float) Math.max(0, 1.0 - (standardDeviation / mean));
    }
    
    public Object getStatistics() {
        return new Object(); // Simplified for brevity
    }
    
    public void update(float deltaTime) {}
    public void cleanup() { initialized = false; }
    public boolean isInitialized() { return initialized; }
}

class TimingAnalysisResult {
    private final List<BehavioralViolation> violations = new ArrayList<>();
    private boolean successful = true;
    private String errorMessage;
    
    public static TimingAnalysisResult error(String errorMessage) {
        TimingAnalysisResult result = new TimingAnalysisResult();
        result.successful = false;
        result.errorMessage = errorMessage;
        return result;
    }
    
    public void addViolations(List<BehavioralViolation> violations) {
        if (violations != null) this.violations.addAll(violations);
    }
    
    public boolean hasViolations() { return !violations.isEmpty(); }
    public List<BehavioralViolation> getViolations() { return new ArrayList<>(violations); }
}