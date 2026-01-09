package fps.anticheat.server;

import fps.anticheat.PlayerAction;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks validation state for a specific player on the server side.
 * Maintains history of actions, positions, and timing for validation purposes.
 */
public class PlayerValidationState {
    
    private final int playerId;
    private final long creationTime;
    
    // Current state
    private Vector3f currentPosition;
    private Vector3f currentVelocity;
    private Vector3f currentRotation;
    private long lastActionTime;
    private long lastUpdateTime;
    
    // Previous state for delta calculations
    private Vector3f previousPosition;
    private Vector3f previousVelocity;
    private Vector3f previousRotation;
    private long previousActionTime;
    
    // Action history for pattern analysis
    private final Deque<PlayerAction> actionHistory = new ArrayDeque<>();
    private final int maxHistorySize = 100;
    
    // Rate limiting tracking
    private final Map<PlayerAction.ActionType, ActionRateTracker> rateLimiters = new ConcurrentHashMap<>();
    
    // Movement validation state
    private float totalDistanceTraveled = 0.0f;
    private float maxSpeedObserved = 0.0f;
    private int jumpCount = 0;
    private long lastJumpTime = 0;
    private boolean isOnGround = true;
    private float airTime = 0.0f;
    
    // Weapon validation state
    private String currentWeapon = "";
    private int currentAmmunition = 0;
    private long lastFireTime = 0;
    private int shotsInBurst = 0;
    private long burstStartTime = 0;
    
    // Network validation state
    private float averagePing = 0.0f;
    private float averagePacketLoss = 0.0f;
    private int networkSamples = 0;
    private long lastNetworkUpdate = 0;
    
    // Violation tracking
    private int violationCount = 0;
    private long lastViolationTime = 0;
    private float suspicionLevel = 0.0f;
    
    public PlayerValidationState(int playerId) {
        this.playerId = playerId;
        this.creationTime = System.currentTimeMillis();
        this.lastActionTime = creationTime;
        this.lastUpdateTime = creationTime;
        this.previousActionTime = creationTime;
        
        // Initialize position vectors
        this.currentPosition = new Vector3f(0, 0, 0);
        this.currentVelocity = new Vector3f(0, 0, 0);
        this.currentRotation = new Vector3f(0, 0, 0);
        this.previousPosition = new Vector3f(0, 0, 0);
        this.previousVelocity = new Vector3f(0, 0, 0);
        this.previousRotation = new Vector3f(0, 0, 0);
        
        // Initialize rate limiters for all action types
        for (PlayerAction.ActionType actionType : PlayerAction.ActionType.values()) {
            rateLimiters.put(actionType, new ActionRateTracker());
        }
    }
    
    /**
     * Update state with a new player action
     */
    public synchronized void updateWithAction(PlayerAction action) {
        // Store previous state
        previousPosition.set(currentPosition);
        previousVelocity.set(currentVelocity);
        previousRotation.set(currentRotation);
        previousActionTime = lastActionTime;
        
        // Update current state
        if (action.getPosition() != null) {
            currentPosition.set(action.getPosition());
        }
        if (action.getVelocity() != null) {
            currentVelocity.set(action.getVelocity());
        }
        if (action.getRotation() != null) {
            currentRotation.set(action.getRotation());
        }
        
        lastActionTime = action.getTimestamp();
        lastUpdateTime = System.currentTimeMillis();
        
        // Update action-specific state
        updateActionSpecificState(action);
        
        // Update rate limiting
        ActionRateTracker rateTracker = rateLimiters.get(action.getActionType());
        if (rateTracker != null) {
            rateTracker.recordAction(action.getTimestamp());
        }
        
        // Add to action history
        actionHistory.addLast(action);
        if (actionHistory.size() > maxHistorySize) {
            actionHistory.removeFirst();
        }
        
        // Update movement statistics
        updateMovementStatistics(action);
        
        // Update network statistics
        updateNetworkStatistics(action);
    }
    
    /**
     * Update action-specific state
     */
    private void updateActionSpecificState(PlayerAction action) {
        switch (action.getActionType()) {
            case JUMP:
                jumpCount++;
                lastJumpTime = action.getTimestamp();
                isOnGround = false;
                break;
                
            case MOVE:
                // Update ground state based on position
                if (action.getPosition() != null && action.getPosition().y <= 0.1f) {
                    if (!isOnGround) {
                        airTime = (action.getTimestamp() - lastJumpTime) / 1000.0f;
                    }
                    isOnGround = true;
                }
                break;
                
            case FIRE_WEAPON:
                lastFireTime = action.getTimestamp();
                
                // Track burst firing
                if (action.getTimestamp() - burstStartTime > 1000) { // New burst if > 1 second gap
                    burstStartTime = action.getTimestamp();
                    shotsInBurst = 1;
                } else {
                    shotsInBurst++;
                }
                
                // Update weapon state
                if (action.getWeaponData() != null) {
                    currentWeapon = action.getWeaponData().getWeaponId();
                    currentAmmunition = action.getWeaponData().getAmmunition();
                }
                break;
                
            case RELOAD_WEAPON:
            case SWITCH_WEAPON:
                if (action.getWeaponData() != null) {
                    currentWeapon = action.getWeaponData().getWeaponId();
                    currentAmmunition = action.getWeaponData().getAmmunition();
                }
                break;
        }
    }
    
    /**
     * Update movement statistics
     */
    private void updateMovementStatistics(PlayerAction action) {
        if (action.getPosition() != null && !previousPosition.equals(0, 0, 0)) {
            float distance = currentPosition.distance(previousPosition);
            totalDistanceTraveled += distance;
            
            // Calculate speed
            float timeDelta = (action.getTimestamp() - previousActionTime) / 1000.0f;
            if (timeDelta > 0) {
                float speed = distance / timeDelta;
                maxSpeedObserved = Math.max(maxSpeedObserved, speed);
            }
        }
    }
    
    /**
     * Update network statistics
     */
    private void updateNetworkStatistics(PlayerAction action) {
        if (action.getPing() > 0) {
            averagePing = (averagePing * networkSamples + action.getPing()) / (networkSamples + 1);
        }
        
        if (action.getPacketLoss() >= 0) {
            averagePacketLoss = (averagePacketLoss * networkSamples + action.getPacketLoss()) / (networkSamples + 1);
        }
        
        networkSamples++;
        lastNetworkUpdate = action.getTimestamp();
    }
    
    /**
     * Record a validation violation
     */
    public synchronized void recordViolation() {
        violationCount++;
        lastViolationTime = System.currentTimeMillis();
        
        // Increase suspicion level
        suspicionLevel = Math.min(1.0f, suspicionLevel + 0.1f);
    }
    
    /**
     * Get time since last action
     */
    public long getTimeSinceLastAction() {
        return System.currentTimeMillis() - lastActionTime;
    }
    
    /**
     * Get movement speed between last two positions
     */
    public float getCurrentMovementSpeed() {
        if (previousPosition.equals(0, 0, 0)) {
            return 0.0f;
        }
        
        float distance = currentPosition.distance(previousPosition);
        float timeDelta = (lastActionTime - previousActionTime) / 1000.0f;
        
        return timeDelta > 0 ? distance / timeDelta : 0.0f;
    }
    
    /**
     * Get rotation speed between last two rotations
     */
    public float getCurrentRotationSpeed() {
        Vector3f rotationDelta = new Vector3f(currentRotation).sub(previousRotation);
        float rotationDistance = rotationDelta.length();
        float timeDelta = (lastActionTime - previousActionTime) / 1000.0f;
        
        return timeDelta > 0 ? rotationDistance / timeDelta : 0.0f;
    }
    
    /**
     * Get actions per second for a specific action type
     */
    public float getActionsPerSecond(PlayerAction.ActionType actionType) {
        ActionRateTracker tracker = rateLimiters.get(actionType);
        return tracker != null ? tracker.getActionsPerSecond() : 0.0f;
    }
    
    /**
     * Get recent actions within time window
     */
    public synchronized int getRecentActionCount(PlayerAction.ActionType actionType, long timeWindowMs) {
        long cutoffTime = System.currentTimeMillis() - timeWindowMs;
        int count = 0;
        
        for (PlayerAction action : actionHistory) {
            if (action.getTimestamp() >= cutoffTime && action.getActionType() == actionType) {
                count++;
            }
        }
        
        return count;
    }
    
    /**
     * Check if player is exhibiting suspicious behavior
     */
    public boolean isSuspicious() {
        return suspicionLevel > 0.5f || violationCount > 5;
    }
    
    /**
     * Decay suspicion level over time
     */
    public synchronized void decaySuspicion(float deltaTime) {
        suspicionLevel = Math.max(0.0f, suspicionLevel - deltaTime * 0.01f); // Decay 1% per second
    }
    
    // Getters
    public int getPlayerId() { return playerId; }
    public long getCreationTime() { return creationTime; }
    public Vector3f getCurrentPosition() { return new Vector3f(currentPosition); }
    public Vector3f getCurrentVelocity() { return new Vector3f(currentVelocity); }
    public Vector3f getCurrentRotation() { return new Vector3f(currentRotation); }
    public Vector3f getPreviousPosition() { return new Vector3f(previousPosition); }
    public Vector3f getPreviousVelocity() { return new Vector3f(previousVelocity); }
    public Vector3f getPreviousRotation() { return new Vector3f(previousRotation); }
    public long getLastActionTime() { return lastActionTime; }
    public long getLastUpdateTime() { return lastUpdateTime; }
    public long getPreviousActionTime() { return previousActionTime; }
    public float getTotalDistanceTraveled() { return totalDistanceTraveled; }
    public float getMaxSpeedObserved() { return maxSpeedObserved; }
    public int getJumpCount() { return jumpCount; }
    public long getLastJumpTime() { return lastJumpTime; }
    public boolean isOnGround() { return isOnGround; }
    public float getAirTime() { return airTime; }
    public String getCurrentWeapon() { return currentWeapon; }
    public int getCurrentAmmunition() { return currentAmmunition; }
    public long getLastFireTime() { return lastFireTime; }
    public int getShotsInBurst() { return shotsInBurst; }
    public long getBurstStartTime() { return burstStartTime; }
    public float getAveragePing() { return averagePing; }
    public float getAveragePacketLoss() { return averagePacketLoss; }
    public int getNetworkSamples() { return networkSamples; }
    public long getLastNetworkUpdate() { return lastNetworkUpdate; }
    public int getViolationCount() { return violationCount; }
    public long getLastViolationTime() { return lastViolationTime; }
    public float getSuspicionLevel() { return suspicionLevel; }
    
    /**
     * Action rate tracker for rate limiting validation
     */
    private static class ActionRateTracker {
        private final Deque<Long> actionTimestamps = new ArrayDeque<>();
        private final long timeWindow = 1000; // 1 second window
        
        public void recordAction(long timestamp) {
            actionTimestamps.addLast(timestamp);
            
            // Remove old timestamps outside the window
            while (!actionTimestamps.isEmpty() && 
                   actionTimestamps.peekFirst() < timestamp - timeWindow) {
                actionTimestamps.removeFirst();
            }
        }
        
        public float getActionsPerSecond() {
            return actionTimestamps.size();
        }
        
        public int getActionCount() {
            return actionTimestamps.size();
        }
    }
}