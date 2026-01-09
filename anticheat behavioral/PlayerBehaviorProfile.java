package fps.anticheat.behavioral;

import fps.anticheat.PlayerAction;
import engine.math.Vector3f;

import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks behavioral patterns and statistics for an individual player.
 * Maintains historical data for analysis and anomaly detection.
 */
public class PlayerBehaviorProfile {
    
    private final String playerId;
    private final long creationTime;
    private long lastActivityTime;
    
    // Action history
    private final Queue<PlayerAction> recentActions = new ConcurrentLinkedQueue<>();
    private final List<PlayerAction> significantActions = new ArrayList<>();
    private int maxRecentActions = 1000;
    private int maxSignificantActions = 500;
    
    // Behavioral metrics
    private float suspicionLevel = 0.0f;
    private int violationCount = 0;
    private long totalActions = 0;
    private long totalPlayTime = 0;
    
    // Aim behavior tracking
    private final AimBehaviorMetrics aimMetrics = new AimBehaviorMetrics();
    
    // Movement behavior tracking
    private final MovementBehaviorMetrics movementMetrics = new MovementBehaviorMetrics();
    
    // Reaction time tracking
    private final ReactionTimeBehaviorMetrics reactionMetrics = new ReactionTimeBehaviorMetrics();
    
    // Input pattern tracking
    private final InputPatternBehaviorMetrics inputMetrics = new InputPatternBehaviorMetrics();
    
    // Timing behavior tracking
    private final TimingBehaviorMetrics timingMetrics = new TimingBehaviorMetrics();
    
    // Statistical tracking
    private final Map<String, Double> statisticalMetrics = new ConcurrentHashMap<>();
    
    public PlayerBehaviorProfile(String playerId) {
        this.playerId = playerId;
        this.creationTime = System.currentTimeMillis();
        this.lastActivityTime = creationTime;
        
        // Initialize statistical metrics
        initializeStatisticalMetrics();
    }
    
    /**
     * Initialize statistical metrics tracking
     */
    private void initializeStatisticalMetrics() {
        statisticalMetrics.put("averageAccuracy", 0.0);
        statisticalMetrics.put("headshotRatio", 0.0);
        statisticalMetrics.put("averageReactionTime", 0.0);
        statisticalMetrics.put("movementConsistency", 0.0);
        statisticalMetrics.put("aimSmoothness", 0.0);
        statisticalMetrics.put("inputConsistency", 0.0);
        statisticalMetrics.put("timingConsistency", 0.0);
    }
    
    /**
     * Add a new player action to the profile
     */
    public synchronized void addAction(PlayerAction action) {
        // Update activity time
        lastActivityTime = System.currentTimeMillis();
        totalActions++;
        
        // Add to recent actions
        recentActions.offer(action);
        while (recentActions.size() > maxRecentActions) {
            recentActions.poll();
        }
        
        // Check if action is significant (combat actions, etc.)
        if (isSignificantAction(action)) {
            significantActions.add(action);
            while (significantActions.size() > maxSignificantActions) {
                significantActions.remove(0);
            }
        }
        
        // Update behavioral metrics
        updateAimMetrics(action);
        updateMovementMetrics(action);
        updateReactionMetrics(action);
        updateInputMetrics(action);
        updateTimingMetrics(action);
        
        // Update statistical metrics
        updateStatisticalMetrics();
    }
    
    /**
     * Check if action is significant for analysis
     */
    private boolean isSignificantAction(PlayerAction action) {
        // Combat actions are always significant
        if (action.isShooting() || action.isReloading() || action.isAiming()) {
            return true;
        }
        
        // Rapid movement changes are significant
        if (action.getVelocity().length() > 5.0f) {
            return true;
        }
        
        // Large rotation changes are significant
        Vector3f rotation = action.getRotation();
        if (Math.abs(rotation.x) > 10.0f || Math.abs(rotation.y) > 10.0f) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Update aim behavior metrics
     */
    private void updateAimMetrics(PlayerAction action) {
        if (!action.isAiming() && !action.isShooting()) {
            return;
        }
        
        aimMetrics.totalAimActions++;
        
        // Track aim smoothness
        Vector3f rotation = action.getRotation();
        float rotationMagnitude = rotation.length();
        aimMetrics.rotationHistory.offer(rotationMagnitude);
        
        if (aimMetrics.rotationHistory.size() > 100) {
            aimMetrics.rotationHistory.poll();
        }
        
        // Calculate aim smoothness
        if (aimMetrics.rotationHistory.size() >= 2) {
            float smoothness = calculateAimSmoothness();
            aimMetrics.averageSmoothness = (aimMetrics.averageSmoothness * 0.9f) + (smoothness * 0.1f);
        }
        
        // Track target acquisition time
        if (action.isShooting()) {
            aimMetrics.totalShots++;
            
            // Estimate if this was a quick scope or flick shot
            if (rotationMagnitude > 20.0f) {
                aimMetrics.flickShots++;
            }
        }
        
        // Update last aim time
        aimMetrics.lastAimTime = action.getTimestamp();
    }
    
    /**
     * Calculate aim smoothness based on rotation history
     */
    private float calculateAimSmoothness() {
        if (aimMetrics.rotationHistory.size() < 2) {
            return 1.0f;
        }
        
        Float[] rotations = aimMetrics.rotationHistory.toArray(new Float[0]);
        float totalVariation = 0;
        
        for (int i = 1; i < rotations.length; i++) {
            totalVariation += Math.abs(rotations[i] - rotations[i-1]);
        }
        
        // Lower variation = higher smoothness
        float averageVariation = totalVariation / (rotations.length - 1);
        return Math.max(0, 1.0f - (averageVariation / 50.0f)); // Normalize to 0-1
    }
    
    /**
     * Update movement behavior metrics
     */
    private void updateMovementMetrics(PlayerAction action) {
        movementMetrics.totalMovementActions++;
        
        Vector3f velocity = action.getVelocity();
        float speed = velocity.length();
        
        // Track speed patterns
        movementMetrics.speedHistory.offer(speed);
        if (movementMetrics.speedHistory.size() > 100) {
            movementMetrics.speedHistory.poll();
        }
        
        // Calculate movement consistency
        if (movementMetrics.speedHistory.size() >= 10) {
            float consistency = calculateMovementConsistency();
            movementMetrics.averageConsistency = (movementMetrics.averageConsistency * 0.9f) + (consistency * 0.1f);
        }
        
        // Track direction changes
        Vector3f position = action.getPosition();
        if (movementMetrics.lastPosition != null) {
            Vector3f direction = position.subtract(movementMetrics.lastPosition).normalize();
            
            if (movementMetrics.lastDirection != null) {
                float directionChange = direction.dot(movementMetrics.lastDirection);
                movementMetrics.directionChangeHistory.offer(directionChange);
                
                if (movementMetrics.directionChangeHistory.size() > 50) {
                    movementMetrics.directionChangeHistory.poll();
                }
            }
            
            movementMetrics.lastDirection = direction;
        }
        
        movementMetrics.lastPosition = position;
        movementMetrics.lastMovementTime = action.getTimestamp();
    }
    
    /**
     * Calculate movement consistency
     */
    private float calculateMovementConsistency() {
        if (movementMetrics.speedHistory.size() < 10) {
            return 0.5f;
        }
        
        Float[] speeds = movementMetrics.speedHistory.toArray(new Float[0]);
        float mean = 0;
        for (float speed : speeds) {
            mean += speed;
        }
        mean /= speeds.length;
        
        float variance = 0;
        for (float speed : speeds) {
            variance += (speed - mean) * (speed - mean);
        }
        variance /= speeds.length;
        
        float standardDeviation = (float) Math.sqrt(variance);
        
        // Lower standard deviation = higher consistency
        return Math.max(0, 1.0f - (standardDeviation / mean));
    }
    
    /**
     * Update reaction time metrics
     */
    private void updateReactionMetrics(PlayerAction action) {
        // Track reaction times for combat actions
        if (action.isShooting() || action.isAiming()) {
            long currentTime = action.getTimestamp();
            
            // Look for recent enemy detection or damage events
            // This would typically be provided by the game engine
            // For now, we'll estimate based on action patterns
            
            if (reactionMetrics.lastEnemyDetectionTime > 0) {
                long reactionTime = currentTime - reactionMetrics.lastEnemyDetectionTime;
                
                if (reactionTime > 0 && reactionTime < 2000) { // Valid reaction time range
                    reactionMetrics.reactionTimes.offer(reactionTime);
                    
                    if (reactionMetrics.reactionTimes.size() > 100) {
                        reactionMetrics.reactionTimes.poll();
                    }
                    
                    // Calculate average reaction time
                    if (reactionMetrics.reactionTimes.size() >= 5) {
                        long totalReactionTime = 0;
                        for (long rt : reactionMetrics.reactionTimes) {
                            totalReactionTime += rt;
                        }
                        reactionMetrics.averageReactionTime = totalReactionTime / reactionMetrics.reactionTimes.size();
                    }
                }
            }
            
            reactionMetrics.lastActionTime = currentTime;
        }
        
        reactionMetrics.totalReactionEvents++;
    }
    
    /**
     * Update input pattern metrics
     */
    private void updateInputMetrics(PlayerAction action) {
        inputMetrics.totalInputActions++;
        
        // Track input timing patterns
        long currentTime = action.getTimestamp();
        if (inputMetrics.lastInputTime > 0) {
            long inputInterval = currentTime - inputMetrics.lastInputTime;
            inputMetrics.inputIntervals.offer(inputInterval);
            
            if (inputMetrics.inputIntervals.size() > 100) {
                inputMetrics.inputIntervals.poll();
            }
            
            // Calculate input consistency
            if (inputMetrics.inputIntervals.size() >= 10) {
                float consistency = calculateInputConsistency();
                inputMetrics.averageConsistency = (inputMetrics.averageConsistency * 0.9f) + (consistency * 0.1f);
            }
        }
        
        inputMetrics.lastInputTime = currentTime;
        
        // Track input combinations
        String inputPattern = generateInputPattern(action);
        inputMetrics.inputPatterns.put(inputPattern, 
            inputMetrics.inputPatterns.getOrDefault(inputPattern, 0) + 1);
    }
    
    /**
     * Generate input pattern string for analysis
     */
    private String generateInputPattern(PlayerAction action) {
        StringBuilder pattern = new StringBuilder();
        
        if (action.isShooting()) pattern.append("S");
        if (action.isAiming()) pattern.append("A");
        if (action.isReloading()) pattern.append("R");
        if (action.getVelocity().length() > 0.1f) pattern.append("M");
        
        return pattern.toString();
    }
    
    /**
     * Calculate input consistency
     */
    private float calculateInputConsistency() {
        if (inputMetrics.inputIntervals.size() < 10) {
            return 0.5f;
        }
        
        Long[] intervals = inputMetrics.inputIntervals.toArray(new Long[0]);
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
    
    /**
     * Update timing behavior metrics
     */
    private void updateTimingMetrics(PlayerAction action) {
        timingMetrics.totalTimingEvents++;
        
        long currentTime = action.getTimestamp();
        
        // Track action timing patterns
        if (timingMetrics.lastActionTime > 0) {
            long timingInterval = currentTime - timingMetrics.lastActionTime;
            timingMetrics.timingIntervals.offer(timingInterval);
            
            if (timingMetrics.timingIntervals.size() > 100) {
                timingMetrics.timingIntervals.poll();
            }
        }
        
        timingMetrics.lastActionTime = currentTime;
        
        // Track specific timing patterns for combat actions
        if (action.isShooting()) {
            if (timingMetrics.lastShotTime > 0) {
                long shotInterval = currentTime - timingMetrics.lastShotTime;
                timingMetrics.shotIntervals.offer(shotInterval);
                
                if (timingMetrics.shotIntervals.size() > 50) {
                    timingMetrics.shotIntervals.poll();
                }
            }
            timingMetrics.lastShotTime = currentTime;
        }
    }
    
    /**
     * Update statistical metrics based on current behavior
     */
    private void updateStatisticalMetrics() {
        // Update aim smoothness
        statisticalMetrics.put("aimSmoothness", (double) aimMetrics.averageSmoothness);
        
        // Update movement consistency
        statisticalMetrics.put("movementConsistency", (double) movementMetrics.averageConsistency);
        
        // Update average reaction time
        if (reactionMetrics.averageReactionTime > 0) {
            statisticalMetrics.put("averageReactionTime", (double) reactionMetrics.averageReactionTime);
        }
        
        // Update input consistency
        statisticalMetrics.put("inputConsistency", (double) inputMetrics.averageConsistency);
        
        // Calculate timing consistency
        if (timingMetrics.timingIntervals.size() >= 10) {
            float timingConsistency = calculateTimingConsistency();
            statisticalMetrics.put("timingConsistency", (double) timingConsistency);
        }
    }
    
    /**
     * Calculate timing consistency
     */
    private float calculateTimingConsistency() {
        if (timingMetrics.timingIntervals.size() < 10) {
            return 0.5f;
        }
        
        Long[] intervals = timingMetrics.timingIntervals.toArray(new Long[0]);
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
        
        return (float) Math.max(0, 1.0 - (standardDeviation / mean));
    }
    
    /**
     * Increase suspicion level
     */
    public synchronized void increaseSuspicion(float amount) {
        suspicionLevel = Math.min(1.0f, suspicionLevel + amount);
    }
    
    /**
     * Decrease suspicion level
     */
    public synchronized void decreaseSuspicion(float amount) {
        suspicionLevel = Math.max(0.0f, suspicionLevel - amount);
    }
    
    /**
     * Increment violation count
     */
    public synchronized void incrementViolationCount() {
        violationCount++;
    }
    
    /**
     * Check if player has consistent inhuman performance
     */
    public boolean hasConsistentInhumanPerformance() {
        // Check for impossibly fast reaction times
        if (reactionMetrics.averageReactionTime > 0 && reactionMetrics.averageReactionTime < 100) {
            return true;
        }
        
        // Check for perfect aim consistency
        if (aimMetrics.averageSmoothness > 0.95f && aimMetrics.totalAimActions > 100) {
            return true;
        }
        
        // Check for perfect movement consistency
        if (movementMetrics.averageConsistency > 0.98f && movementMetrics.totalMovementActions > 100) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if player has robotic movement patterns
     */
    public boolean hasRoboticMovementPatterns() {
        // Check for overly consistent movement
        if (movementMetrics.averageConsistency > 0.95f) {
            return true;
        }
        
        // Check for lack of natural variation in direction changes
        if (movementMetrics.directionChangeHistory.size() >= 20) {
            float totalVariation = 0;
            for (float change : movementMetrics.directionChangeHistory) {
                totalVariation += Math.abs(change);
            }
            float averageVariation = totalVariation / movementMetrics.directionChangeHistory.size();
            
            if (averageVariation < 0.1f) { // Too little variation
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if player has perfect tracking patterns
     */
    public boolean hasPerfectTrackingPatterns() {
        // Check for impossibly smooth aim tracking
        if (aimMetrics.averageSmoothness > 0.98f && aimMetrics.totalAimActions > 50) {
            return true;
        }
        
        // Check for perfect flick shot accuracy
        if (aimMetrics.totalShots > 20 && aimMetrics.flickShots > 0) {
            float flickRatio = (float) aimMetrics.flickShots / aimMetrics.totalShots;
            if (flickRatio > 0.8f) { // Too many perfect flick shots
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get profile age in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - creationTime;
    }
    
    /**
     * Get time since last activity in milliseconds
     */
    public long getTimeSinceLastActivity() {
        return System.currentTimeMillis() - lastActivityTime;
    }
    
    // Getters and setters
    public String getPlayerId() { return playerId; }
    public long getCreationTime() { return creationTime; }
    public long getLastActivityTime() { return lastActivityTime; }
    public Queue<PlayerAction> getRecentActions() { return new ConcurrentLinkedQueue<>(recentActions); }
    public List<PlayerAction> getSignificantActions() { return new ArrayList<>(significantActions); }
    public float getSuspicionLevel() { return suspicionLevel; }
    public void setSuspicionLevel(float suspicionLevel) { this.suspicionLevel = Math.max(0.0f, Math.min(1.0f, suspicionLevel)); }
    public int getViolationCount() { return violationCount; }
    public void setViolationCount(int violationCount) { this.violationCount = violationCount; }
    public long getTotalActions() { return totalActions; }
    public long getTotalPlayTime() { return totalPlayTime; }
    public void setTotalPlayTime(long totalPlayTime) { this.totalPlayTime = totalPlayTime; }
    public AimBehaviorMetrics getAimMetrics() { return aimMetrics; }
    public MovementBehaviorMetrics getMovementMetrics() { return movementMetrics; }
    public ReactionTimeBehaviorMetrics getReactionMetrics() { return reactionMetrics; }
    public InputPatternBehaviorMetrics getInputMetrics() { return inputMetrics; }
    public TimingBehaviorMetrics getTimingMetrics() { return timingMetrics; }
    public Map<String, Double> getStatisticalMetrics() { return new ConcurrentHashMap<>(statisticalMetrics); }
    public int getMaxRecentActions() { return maxRecentActions; }
    public void setMaxRecentActions(int maxRecentActions) { this.maxRecentActions = maxRecentActions; }
    public int getMaxSignificantActions() { return maxSignificantActions; }
    public void setMaxSignificantActions(int maxSignificantActions) { this.maxSignificantActions = maxSignificantActions; }
    
    /**
     * Aim behavior metrics
     */
    public static class AimBehaviorMetrics {
        public long totalAimActions = 0;
        public long totalShots = 0;
        public long flickShots = 0;
        public float averageSmoothness = 0.5f;
        public long lastAimTime = 0;
        public final Queue<Float> rotationHistory = new ConcurrentLinkedQueue<>();
    }
    
    /**
     * Movement behavior metrics
     */
    public static class MovementBehaviorMetrics {
        public long totalMovementActions = 0;
        public float averageConsistency = 0.5f;
        public long lastMovementTime = 0;
        public Vector3f lastPosition;
        public Vector3f lastDirection;
        public final Queue<Float> speedHistory = new ConcurrentLinkedQueue<>();
        public final Queue<Float> directionChangeHistory = new ConcurrentLinkedQueue<>();
    }
    
    /**
     * Reaction time behavior metrics
     */
    public static class ReactionTimeBehaviorMetrics {
        public long totalReactionEvents = 0;
        public long averageReactionTime = 0;
        public long lastEnemyDetectionTime = 0;
        public long lastActionTime = 0;
        public final Queue<Long> reactionTimes = new ConcurrentLinkedQueue<>();
    }
    
    /**
     * Input pattern behavior metrics
     */
    public static class InputPatternBehaviorMetrics {
        public long totalInputActions = 0;
        public float averageConsistency = 0.5f;
        public long lastInputTime = 0;
        public final Queue<Long> inputIntervals = new ConcurrentLinkedQueue<>();
        public final Map<String, Integer> inputPatterns = new ConcurrentHashMap<>();
    }
    
    /**
     * Timing behavior metrics
     */
    public static class TimingBehaviorMetrics {
        public long totalTimingEvents = 0;
        public long lastActionTime = 0;
        public long lastShotTime = 0;
        public final Queue<Long> timingIntervals = new ConcurrentLinkedQueue<>();
        public final Queue<Long> shotIntervals = new ConcurrentLinkedQueue<>();
    }
}