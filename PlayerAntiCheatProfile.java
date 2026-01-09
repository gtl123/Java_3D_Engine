package fps.anticheat;

import fps.player.Player;
import org.joml.Vector3f;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Comprehensive anti-cheat profile for a player.
 * Tracks behavior patterns, statistics, violations, and risk assessment.
 */
public class PlayerAntiCheatProfile {
    
    private final int playerId;
    private final String playerName;
    private final long creationTime;
    
    // Player reference
    private final Player player;
    
    // Violation tracking
    private final List<CheatViolation> violations = new CopyOnWriteArrayList<>();
    private final Map<ViolationType, Integer> violationCounts = new ConcurrentHashMap<>();
    
    // Action history for behavioral analysis
    private final List<PlayerAction> actionHistory = new ArrayList<>();
    private final int maxActionHistory = 1000;
    
    // Behavioral metrics
    private BehavioralMetrics behavioralMetrics;
    
    // Statistical metrics
    private StatisticalMetrics statisticalMetrics;
    
    // Hardware fingerprint
    private String hardwareFingerprint;
    private long lastHardwareCheck;
    
    // Risk assessment
    private RiskLevel currentRiskLevel;
    private float riskScore;
    private String riskReason;
    private long lastRiskAssessment;
    
    // Performance tracking
    private long totalActionsProcessed;
    private long totalViolationsDetected;
    private float averageConfidenceScore;
    
    // Session tracking
    private long sessionStartTime;
    private long totalPlayTime;
    private int sessionCount;
    
    public PlayerAntiCheatProfile(Player player) {
        this.playerId = player.getPlayerId();
        this.playerName = player.getPlayerName();
        this.player = player;
        this.creationTime = System.currentTimeMillis();
        
        // Initialize metrics
        this.behavioralMetrics = new BehavioralMetrics();
        this.statisticalMetrics = new StatisticalMetrics();
        
        // Initialize risk assessment
        this.currentRiskLevel = RiskLevel.LOW;
        this.riskScore = 0.0f;
        this.riskReason = "New player";
        this.lastRiskAssessment = creationTime;
        
        // Initialize session
        this.sessionStartTime = creationTime;
        this.totalPlayTime = 0;
        this.sessionCount = 1;
        
        // Initialize performance tracking
        this.totalActionsProcessed = 0;
        this.totalViolationsDetected = 0;
        this.averageConfidenceScore = 0.0f;
    }
    
    /**
     * Record a player action for analysis
     */
    public synchronized void recordAction(PlayerAction action) {
        // Add to action history
        actionHistory.add(action);
        
        // Maintain history size limit
        if (actionHistory.size() > maxActionHistory) {
            actionHistory.remove(0);
        }
        
        // Update behavioral metrics
        behavioralMetrics.updateWithAction(action);
        
        // Update statistical metrics
        statisticalMetrics.updateWithAction(action);
        
        // Update performance tracking
        totalActionsProcessed++;
    }
    
    /**
     * Add a violation to the profile
     */
    public synchronized void addViolation(CheatViolation violation) {
        violations.add(violation);
        
        // Update violation counts
        ViolationType type = violation.getViolationType();
        violationCounts.put(type, violationCounts.getOrDefault(type, 0) + 1);
        
        // Update performance tracking
        totalViolationsDetected++;
        
        // Update average confidence score
        float totalConfidence = averageConfidenceScore * (totalViolationsDetected - 1) + violation.getConfidence();
        averageConfidenceScore = totalConfidence / totalViolationsDetected;
        
        // Trigger risk reassessment
        assessRisk();
    }
    
    /**
     * Get violations of a specific type
     */
    public List<CheatViolation> getViolationsByType(ViolationType type) {
        List<CheatViolation> result = new ArrayList<>();
        for (CheatViolation violation : violations) {
            if (violation.getViolationType() == type) {
                result.add(violation);
            }
        }
        return result;
    }
    
    /**
     * Get recent violations within a time window
     */
    public List<CheatViolation> getRecentViolations(long timeWindowMs) {
        long cutoffTime = System.currentTimeMillis() - timeWindowMs;
        List<CheatViolation> result = new ArrayList<>();
        
        for (CheatViolation violation : violations) {
            if (violation.getTimestamp() >= cutoffTime) {
                result.add(violation);
            }
        }
        
        return result;
    }
    
    /**
     * Get recent actions within a time window
     */
    public List<PlayerAction> getRecentActions(long timeWindowMs) {
        long cutoffTime = System.currentTimeMillis() - timeWindowMs;
        List<PlayerAction> result = new ArrayList<>();
        
        for (int i = actionHistory.size() - 1; i >= 0; i--) {
            PlayerAction action = actionHistory.get(i);
            if (action.getTimestamp() >= cutoffTime) {
                result.add(0, action); // Add to beginning to maintain chronological order
            } else {
                break; // Actions are chronologically ordered
            }
        }
        
        return result;
    }
    
    /**
     * Clean up old violations based on retention policy
     */
    public synchronized void cleanupOldViolations(long cutoffTime) {
        violations.removeIf(violation -> violation.getTimestamp() < cutoffTime);
        
        // Recalculate violation counts
        violationCounts.clear();
        for (CheatViolation violation : violations) {
            ViolationType type = violation.getViolationType();
            violationCounts.put(type, violationCounts.getOrDefault(type, 0) + 1);
        }
    }
    
    /**
     * Assess current risk level based on violations and behavior
     */
    private void assessRisk() {
        float newRiskScore = calculateRiskScore();
        RiskLevel newRiskLevel = determineRiskLevel(newRiskScore);
        
        if (newRiskLevel != currentRiskLevel || Math.abs(newRiskScore - riskScore) > 0.1f) {
            currentRiskLevel = newRiskLevel;
            riskScore = newRiskScore;
            riskReason = generateRiskReason();
            lastRiskAssessment = System.currentTimeMillis();
        }
    }
    
    /**
     * Calculate risk score based on various factors
     */
    private float calculateRiskScore() {
        float score = 0.0f;
        
        // Violation-based risk
        for (Map.Entry<ViolationType, Integer> entry : violationCounts.entrySet()) {
            ViolationType type = entry.getKey();
            int count = entry.getValue();
            
            // Weight by severity
            float severityWeight = type.getSeverity().getLevel() / 4.0f;
            score += count * severityWeight * 0.2f;
        }
        
        // Recent violation frequency
        List<CheatViolation> recentViolations = getRecentViolations(3600000); // Last hour
        score += recentViolations.size() * 0.1f;
        
        // Behavioral risk factors
        if (behavioralMetrics.hasInhumanReactions()) {
            score += 0.3f;
        }
        if (behavioralMetrics.hasRoboticMovement()) {
            score += 0.2f;
        }
        if (behavioralMetrics.hasPerfectTracking()) {
            score += 0.4f;
        }
        
        // Statistical anomalies
        if (statisticalMetrics.hasImpossibleAccuracy()) {
            score += 0.5f;
        }
        if (statisticalMetrics.hasImpossibleHeadshotRatio()) {
            score += 0.4f;
        }
        
        // Cap at 1.0
        return Math.min(score, 1.0f);
    }
    
    /**
     * Determine risk level from score
     */
    private RiskLevel determineRiskLevel(float score) {
        if (score >= 0.8f) {
            return RiskLevel.CRITICAL;
        } else if (score >= 0.6f) {
            return RiskLevel.HIGH;
        } else if (score >= 0.3f) {
            return RiskLevel.MEDIUM;
        } else {
            return RiskLevel.LOW;
        }
    }
    
    /**
     * Generate human-readable risk reason
     */
    private String generateRiskReason() {
        if (violations.isEmpty()) {
            return "No violations detected";
        }
        
        StringBuilder reason = new StringBuilder();
        
        // Most common violation type
        ViolationType mostCommon = null;
        int maxCount = 0;
        for (Map.Entry<ViolationType, Integer> entry : violationCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                mostCommon = entry.getKey();
            }
        }
        
        if (mostCommon != null) {
            reason.append(maxCount).append(" ").append(mostCommon.getDisplayName()).append(" violations");
        }
        
        // Recent activity
        List<CheatViolation> recent = getRecentViolations(3600000);
        if (!recent.isEmpty()) {
            reason.append(", ").append(recent.size()).append(" recent violations");
        }
        
        return reason.toString();
    }
    
    /**
     * Update profile with delta time
     */
    public void update(float deltaTime) {
        // Update play time
        totalPlayTime += (long) (deltaTime * 1000);
        
        // Update metrics
        behavioralMetrics.update(deltaTime);
        statisticalMetrics.update(deltaTime);
        
        // Periodic risk assessment (every 5 minutes)
        if (System.currentTimeMillis() - lastRiskAssessment > 300000) {
            assessRisk();
        }
    }
    
    /**
     * Start a new session
     */
    public void startNewSession() {
        sessionStartTime = System.currentTimeMillis();
        sessionCount++;
    }
    
    /**
     * End current session
     */
    public void endSession() {
        long sessionDuration = System.currentTimeMillis() - sessionStartTime;
        totalPlayTime += sessionDuration;
    }
    
    // Getters
    public int getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public long getCreationTime() { return creationTime; }
    public Player getPlayer() { return player; }
    public List<CheatViolation> getViolations() { return new ArrayList<>(violations); }
    public Map<ViolationType, Integer> getViolationCounts() { return new ConcurrentHashMap<>(violationCounts); }
    public List<PlayerAction> getActionHistory() { return new ArrayList<>(actionHistory); }
    public BehavioralMetrics getBehavioralMetrics() { return behavioralMetrics; }
    public StatisticalMetrics getStatisticalMetrics() { return statisticalMetrics; }
    public String getHardwareFingerprint() { return hardwareFingerprint; }
    public void setHardwareFingerprint(String fingerprint) { this.hardwareFingerprint = fingerprint; }
    public long getLastHardwareCheck() { return lastHardwareCheck; }
    public void setLastHardwareCheck(long timestamp) { this.lastHardwareCheck = timestamp; }
    public RiskLevel getCurrentRiskLevel() { return currentRiskLevel; }
    public float getRiskScore() { return riskScore; }
    public String getRiskReason() { return riskReason; }
    public long getLastRiskAssessment() { return lastRiskAssessment; }
    public long getTotalActionsProcessed() { return totalActionsProcessed; }
    public long getTotalViolationsDetected() { return totalViolationsDetected; }
    public float getAverageConfidenceScore() { return averageConfidenceScore; }
    public long getSessionStartTime() { return sessionStartTime; }
    public long getTotalPlayTime() { return totalPlayTime; }
    public int getSessionCount() { return sessionCount; }
    
    /**
     * Risk levels for players
     */
    public enum RiskLevel {
        LOW("Low Risk", 0),
        MEDIUM("Medium Risk", 1),
        HIGH("High Risk", 2),
        CRITICAL("Critical Risk", 3);
        
        private final String displayName;
        private final int level;
        
        RiskLevel(String displayName, int level) {
            this.displayName = displayName;
            this.level = level;
        }
        
        public String getDisplayName() { return displayName; }
        public int getLevel() { return level; }
    }
    
    /**
     * Placeholder for behavioral metrics
     */
    public static class BehavioralMetrics {
        private boolean inhumanReactions = false;
        private boolean roboticMovement = false;
        private boolean perfectTracking = false;
        
        public void updateWithAction(PlayerAction action) {
            // Implementation would analyze action for behavioral patterns
        }
        
        public void update(float deltaTime) {
            // Update behavioral analysis
        }
        
        public boolean hasInhumanReactions() { return inhumanReactions; }
        public boolean hasRoboticMovement() { return roboticMovement; }
        public boolean hasPerfectTracking() { return perfectTracking; }
    }
    
    /**
     * Placeholder for statistical metrics
     */
    public static class StatisticalMetrics {
        private boolean impossibleAccuracy = false;
        private boolean impossibleHeadshotRatio = false;
        
        public void updateWithAction(PlayerAction action) {
            // Implementation would analyze action for statistical anomalies
        }
        
        public void update(float deltaTime) {
            // Update statistical analysis
        }
        
        public boolean hasImpossibleAccuracy() { return impossibleAccuracy; }
        public boolean hasImpossibleHeadshotRatio() { return impossibleHeadshotRatio; }
    }
}