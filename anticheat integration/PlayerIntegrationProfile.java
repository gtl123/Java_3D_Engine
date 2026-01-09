package fps.anticheat.integration;

import fps.core.Player;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Integration profile for individual players, tracking their actions and integration state.
 */
public class PlayerIntegrationProfile {
    
    private final String playerId;
    private final Player player;
    private final Queue<PlayerAction> recentActions;
    private final Map<PlayerActionType, ActionStatistics> actionStatistics;
    
    private long creationTime;
    private long lastActivity;
    private int totalActions;
    private boolean highRiskPlayer;
    
    // Action tracking limits
    private static final int MAX_RECENT_ACTIONS = 100;
    private static final long ACTION_RETENTION_TIME = 300000; // 5 minutes
    
    public PlayerIntegrationProfile(String playerId, Player player) {
        this.playerId = playerId;
        this.player = player;
        this.recentActions = new ConcurrentLinkedQueue<>();
        this.actionStatistics = new HashMap<>();
        this.creationTime = System.currentTimeMillis();
        this.lastActivity = creationTime;
        this.totalActions = 0;
        this.highRiskPlayer = false;
        
        initializeActionStatistics();
    }
    
    /**
     * Initialize action statistics for all action types
     */
    private void initializeActionStatistics() {
        for (PlayerActionType actionType : PlayerActionType.values()) {
            actionStatistics.put(actionType, new ActionStatistics(actionType));
        }
    }
    
    /**
     * Record a player action
     */
    public synchronized void recordAction(PlayerAction action) {
        lastActivity = System.currentTimeMillis();
        totalActions++;
        
        // Add to recent actions
        recentActions.offer(action);
        
        // Maintain size limit
        while (recentActions.size() > MAX_RECENT_ACTIONS) {
            recentActions.poll();
        }
        
        // Update statistics
        ActionStatistics stats = actionStatistics.get(action.getActionType());
        if (stats != null) {
            stats.recordAction(action);
        }
        
        // Clean up old actions
        cleanupOldActions();
        
        // Update risk assessment
        updateRiskAssessment();
    }
    
    /**
     * Clean up old actions beyond retention time
     */
    private void cleanupOldActions() {
        long cutoffTime = System.currentTimeMillis() - ACTION_RETENTION_TIME;
        
        recentActions.removeIf(action -> action.getTimestamp() < cutoffTime);
        
        // Clean up statistics
        for (ActionStatistics stats : actionStatistics.values()) {
            stats.cleanup(cutoffTime);
        }
    }
    
    /**
     * Update risk assessment based on recent activity
     */
    private void updateRiskAssessment() {
        // Calculate risk factors
        double actionFrequency = calculateActionFrequency();
        double suspiciousPatternScore = calculateSuspiciousPatternScore();
        double violationHistory = calculateViolationHistoryScore();
        
        // Combine risk factors
        double riskScore = (actionFrequency * 0.3) + (suspiciousPatternScore * 0.5) + (violationHistory * 0.2);
        
        // Update high risk status
        highRiskPlayer = riskScore > 0.7;
    }
    
    /**
     * Calculate action frequency risk factor
     */
    private double calculateActionFrequency() {
        if (recentActions.isEmpty()) return 0.0;
        
        long timeWindow = 60000; // 1 minute
        long cutoffTime = System.currentTimeMillis() - timeWindow;
        
        long recentActionCount = recentActions.stream()
                .filter(action -> action.getTimestamp() >= cutoffTime)
                .count();
        
        // High frequency indicates potential automation
        double frequency = recentActionCount / 60.0; // actions per second
        return Math.min(frequency / 10.0, 1.0); // Normalize to 0-1 (10 actions/sec = max)
    }
    
    /**
     * Calculate suspicious pattern score
     */
    private double calculateSuspiciousPatternScore() {
        double score = 0.0;
        
        // Check for regular intervals (bot-like behavior)
        if (hasRegularIntervals()) {
            score += 0.4;
        }
        
        // Check for impossible sequences
        if (hasImpossibleSequences()) {
            score += 0.3;
        }
        
        // Check for repetitive patterns
        if (hasRepetitivePatterns()) {
            score += 0.2;
        }
        
        // Check for unusual action combinations
        if (hasUnusualActionCombinations()) {
            score += 0.1;
        }
        
        return Math.min(score, 1.0);
    }
    
    /**
     * Calculate violation history score
     */
    private double calculateViolationHistoryScore() {
        // This would integrate with the punishment system to get violation history
        // For now, return a placeholder value
        return 0.0;
    }
    
    /**
     * Check for regular intervals between actions
     */
    private boolean hasRegularIntervals() {
        if (recentActions.size() < 10) return false;
        
        List<PlayerAction> actionList = new ArrayList<>(recentActions);
        List<Long> intervals = new ArrayList<>();
        
        for (int i = 1; i < actionList.size(); i++) {
            long interval = actionList.get(i).getTimestamp() - actionList.get(i-1).getTimestamp();
            intervals.add(interval);
        }
        
        if (intervals.size() < 5) return false;
        
        // Calculate variance in intervals
        double avgInterval = intervals.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double variance = intervals.stream()
                .mapToDouble(interval -> Math.pow(interval - avgInterval, 2))
                .average().orElse(0.0);
        
        // Low variance indicates regular intervals (bot-like)
        return variance < 1000.0 && avgInterval > 50.0; // Less than 1 second variance, intervals > 50ms
    }
    
    /**
     * Check for impossible action sequences
     */
    private boolean hasImpossibleSequences() {
        if (recentActions.size() < 3) return false;
        
        List<PlayerAction> actionList = new ArrayList<>(recentActions);
        
        for (int i = 2; i < actionList.size(); i++) {
            PlayerAction action1 = actionList.get(i-2);
            PlayerAction action2 = actionList.get(i-1);
            PlayerAction action3 = actionList.get(i);
            
            // Check for impossible timing
            long interval1 = action2.getTimestamp() - action1.getTimestamp();
            long interval2 = action3.getTimestamp() - action2.getTimestamp();
            
            if (interval1 < 10 && interval2 < 10) { // Actions less than 10ms apart
                return true;
            }
            
            // Check for impossible action combinations
            if (isImpossibleCombination(action1, action2, action3)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if action combination is impossible
     */
    private boolean isImpossibleCombination(PlayerAction action1, PlayerAction action2, PlayerAction action3) {
        // Example: Shooting while reloading is impossible
        if (action1.getActionType() == PlayerActionType.WEAPON_RELOAD &&
            action2.getActionType() == PlayerActionType.WEAPON_FIRE &&
            action3.getTimestamp() - action1.getTimestamp() < 1000) { // Reload takes at least 1 second
            return true;
        }
        
        // Add more impossible combinations as needed
        return false;
    }
    
    /**
     * Check for repetitive patterns
     */
    private boolean hasRepetitivePatterns() {
        if (recentActions.size() < 20) return false;
        
        List<PlayerAction> actionList = new ArrayList<>(recentActions);
        
        // Check for repeating sequences of actions
        for (int patternLength = 3; patternLength <= 10; patternLength++) {
            if (hasRepeatingPattern(actionList, patternLength)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check for repeating pattern of specific length
     */
    private boolean hasRepeatingPattern(List<PlayerAction> actions, int patternLength) {
        if (actions.size() < patternLength * 3) return false; // Need at least 3 repetitions
        
        for (int start = 0; start <= actions.size() - patternLength * 3; start++) {
            boolean isPattern = true;
            
            // Check if pattern repeats
            for (int rep = 1; rep < 3; rep++) {
                for (int i = 0; i < patternLength; i++) {
                    PlayerAction original = actions.get(start + i);
                    PlayerAction repeated = actions.get(start + rep * patternLength + i);
                    
                    if (original.getActionType() != repeated.getActionType()) {
                        isPattern = false;
                        break;
                    }
                }
                if (!isPattern) break;
            }
            
            if (isPattern) return true;
        }
        
        return false;
    }
    
    /**
     * Check for unusual action combinations
     */
    private boolean hasUnusualActionCombinations() {
        // Check for statistically unusual combinations of actions
        // This would be based on learned patterns from legitimate players
        return false; // Placeholder
    }
    
    /**
     * Get action statistics for specific action type
     */
    public ActionStatistics getActionStatistics(PlayerActionType actionType) {
        return actionStatistics.get(actionType);
    }
    
    /**
     * Get all action statistics
     */
    public Map<PlayerActionType, ActionStatistics> getAllActionStatistics() {
        return new HashMap<>(actionStatistics);
    }
    
    /**
     * Get recent actions
     */
    public List<PlayerAction> getRecentActions() {
        return new ArrayList<>(recentActions);
    }
    
    /**
     * Get recent actions of specific type
     */
    public List<PlayerAction> getRecentActions(PlayerActionType actionType) {
        return recentActions.stream()
                .filter(action -> action.getActionType() == actionType)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get profile summary
     */
    public PlayerProfileSummary getProfileSummary() {
        return new PlayerProfileSummary(
            playerId,
            creationTime,
            lastActivity,
            totalActions,
            recentActions.size(),
            highRiskPlayer,
            calculateActionFrequency(),
            calculateSuspiciousPatternScore()
        );
    }
    
    // Getters
    public String getPlayerId() { return playerId; }
    public Player getPlayer() { return player; }
    public long getCreationTime() { return creationTime; }
    public long getLastActivity() { return lastActivity; }
    public int getTotalActions() { return totalActions; }
    public boolean isHighRiskPlayer() { return highRiskPlayer; }
    
    /**
     * Action statistics for a specific action type
     */
    public static class ActionStatistics {
        private final PlayerActionType actionType;
        private final Queue<PlayerAction> actions;
        private int totalCount;
        private long lastActionTime;
        
        public ActionStatistics(PlayerActionType actionType) {
            this.actionType = actionType;
            this.actions = new ConcurrentLinkedQueue<>();
            this.totalCount = 0;
            this.lastActionTime = 0;
        }
        
        public void recordAction(PlayerAction action) {
            if (action.getActionType() == actionType) {
                actions.offer(action);
                totalCount++;
                lastActionTime = action.getTimestamp();
                
                // Maintain size limit
                while (actions.size() > 50) {
                    actions.poll();
                }
            }
        }
        
        public void cleanup(long cutoffTime) {
            actions.removeIf(action -> action.getTimestamp() < cutoffTime);
        }
        
        public int getRecentCount(long timeWindow) {
            long cutoffTime = System.currentTimeMillis() - timeWindow;
            return (int) actions.stream()
                    .filter(action -> action.getTimestamp() >= cutoffTime)
                    .count();
        }
        
        public double getFrequency(long timeWindow) {
            int count = getRecentCount(timeWindow);
            return count / (timeWindow / 1000.0); // actions per second
        }
        
        public PlayerActionType getActionType() { return actionType; }
        public int getTotalCount() { return totalCount; }
        public long getLastActionTime() { return lastActionTime; }
    }
    
    /**
     * Player profile summary
     */
    public static class PlayerProfileSummary {
        private final String playerId;
        private final long creationTime;
        private final long lastActivity;
        private final int totalActions;
        private final int recentActions;
        private final boolean highRisk;
        private final double actionFrequency;
        private final double suspiciousScore;
        
        public PlayerProfileSummary(String playerId, long creationTime, long lastActivity,
                                  int totalActions, int recentActions, boolean highRisk,
                                  double actionFrequency, double suspiciousScore) {
            this.playerId = playerId;
            this.creationTime = creationTime;
            this.lastActivity = lastActivity;
            this.totalActions = totalActions;
            this.recentActions = recentActions;
            this.highRisk = highRisk;
            this.actionFrequency = actionFrequency;
            this.suspiciousScore = suspiciousScore;
        }
        
        public String getPlayerId() { return playerId; }
        public long getCreationTime() { return creationTime; }
        public long getLastActivity() { return lastActivity; }
        public int getTotalActions() { return totalActions; }
        public int getRecentActions() { return recentActions; }
        public boolean isHighRisk() { return highRisk; }
        public double getActionFrequency() { return actionFrequency; }
        public double getSuspiciousScore() { return suspiciousScore; }
        
        @Override
        public String toString() {
            return String.format("PlayerProfileSummary{playerId='%s', totalActions=%d, highRisk=%s, frequency=%.2f, suspicious=%.2f}", 
                               playerId, totalActions, highRisk, actionFrequency, suspiciousScore);
        }
    }
}