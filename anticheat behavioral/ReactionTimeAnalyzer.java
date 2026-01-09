package fps.anticheat.behavioral;

import engine.logging.LogManager;
import fps.anticheat.*;

import java.util.List;
import java.util.ArrayList;

/**
 * Analyzes player reaction times to detect inhuman response speeds.
 */
public class ReactionTimeAnalyzer {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private AntiCheatConfiguration config;
    private long inhumanReactionThreshold = 100; // 100ms
    private int minSamplesForAnalysis = 5;
    private long totalAnalyses = 0;
    private long totalViolations = 0;
    private boolean initialized = false;
    
    public ReactionTimeAnalyzer() {
        logManager.debug("ReactionTimeAnalyzer", "Reaction time analyzer created");
    }
    
    public void initialize() throws Exception {
        initialized = true;
        logManager.info("ReactionTimeAnalyzer", "Reaction time analyzer initialized");
    }
    
    public void setConfiguration(AntiCheatConfiguration config) {
        this.config = config;
    }
    
    public ReactionTimeAnalysisResult analyzeReactionTime(PlayerBehaviorProfile profile, PlayerAction action) {
        if (!initialized) {
            return ReactionTimeAnalysisResult.error("Reaction time analyzer not initialized");
        }
        
        totalAnalyses++;
        ReactionTimeAnalysisResult result = new ReactionTimeAnalysisResult();
        List<BehavioralViolation> violations = new ArrayList<>();
        
        PlayerBehaviorProfile.ReactionTimeBehaviorMetrics reactionMetrics = profile.getReactionMetrics();
        
        // Check for inhuman reaction times
        if (reactionMetrics.averageReactionTime > 0 && 
            reactionMetrics.averageReactionTime < inhumanReactionThreshold &&
            reactionMetrics.reactionTimes.size() >= minSamplesForAnalysis) {
            
            float confidence = Math.min(1.0f, (inhumanReactionThreshold - reactionMetrics.averageReactionTime) / (float)inhumanReactionThreshold);
            
            violations.add(new BehavioralViolation(
                ViolationType.INHUMAN_REACTIONS,
                "Inhuman reaction time detected: " + reactionMetrics.averageReactionTime + "ms average",
                profile.getPlayerId(),
                confidence,
                "reaction_time_analysis",
                profile
            ));
        }
        
        result.addViolations(violations);
        totalViolations += violations.size();
        return result;
    }
    
    public Object getStatistics() {
        return new Object(); // Simplified for brevity
    }
    
    public void update(float deltaTime) {}
    public void cleanup() { initialized = false; }
    public boolean isInitialized() { return initialized; }
}

class ReactionTimeAnalysisResult {
    private final List<BehavioralViolation> violations = new ArrayList<>();
    private boolean successful = true;
    private String errorMessage;
    
    public static ReactionTimeAnalysisResult error(String errorMessage) {
        ReactionTimeAnalysisResult result = new ReactionTimeAnalysisResult();
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