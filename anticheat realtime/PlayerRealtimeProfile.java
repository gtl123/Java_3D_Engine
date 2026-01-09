package fps.anticheat.realtime;

import fps.anticheat.PlayerAction;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Real-time profile for tracking player behavior and actions during gameplay.
 * Maintains recent action history and calculates suspicious behavior metrics.
 */
public class PlayerRealtimeProfile {
    
    private final String playerId;
    private final long creationTime;
    
    // Action tracking
    private final Queue<PlayerAction> recentActions;
    private final Queue<CheatDetection> detectionHistory;
    private long lastActionTime;
    private int actionCount;
    private int violationCount;
    
    // Behavior metrics
    private float suspiciousScore;
    private float aimSmoothness;
    private float movementConsistency;
    private float reactionTimeAverage;
    private float accuracyTrend;
    
    // Movement tracking
    private Vector3 lastPosition;
    private Vector3 lastVelocity;
    private float lastViewAngleX;
    private float lastViewAngleY;
    private long lastMovementTime;
    
    // Shooting tracking
    private long lastShotTime;
    private int consecutiveHeadshots;
    private float averageTimeBetweenShots;
    private boolean isCurrentlyShooting;
    
    // Aim tracking
    private final Queue<AimSample> aimSamples;
    private float aimAcceleration;
    private float aimJerkiness;
    private boolean hasUnhumanAiming;
    
    // Performance tracking
    private final Map<String, Integer> detectionCounts;
    private final Map<String, Float> confidenceScores;
    
    // Configuration
    private static final int MAX_RECENT_ACTIONS = 100;
    private static final int MAX_DETECTION_HISTORY = 50;
    private static final int MAX_AIM_SAMPLES = 20;
    private static final long STALE_PROFILE_TIME = 1800000; // 30 minutes
    
    /**
     * Aim sample for tracking aim behavior
     */
    public static class AimSample {
        public final float angleX;
        public final float angleY;
        public final long timestamp;
        public final float deltaX;
        public final float deltaY;
        public final float speed;
        
        public AimSample(float angleX, float angleY, long timestamp, float deltaX, float deltaY) {
            this.angleX = angleX;
            this.angleY = angleY;
            this.timestamp = timestamp;
            this.deltaX = deltaX;
            this.deltaY = deltaY;
            this.speed = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        }
    }
    
    /**
     * 3D Vector for position/velocity tracking
     */
    public static class Vector3 {
        public final float x, y, z;
        
        public Vector3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        public float distance(Vector3 other) {
            float dx = x - other.x;
            float dy = y - other.y;
            float dz = z - other.z;
            return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        
        public Vector3 subtract(Vector3 other) {
            return new Vector3(x - other.x, y - other.y, z - other.z);
        }
        
        public float magnitude() {
            return (float) Math.sqrt(x * x + y * y + z * z);
        }
    }
    
    public PlayerRealtimeProfile(String playerId) {
        this.playerId = playerId;
        this.creationTime = System.currentTimeMillis();
        
        this.recentActions = new ConcurrentLinkedQueue<>();
        this.detectionHistory = new ConcurrentLinkedQueue<>();
        this.aimSamples = new ConcurrentLinkedQueue<>();
        
        this.lastActionTime = creationTime;
        this.actionCount = 0;
        this.violationCount = 0;
        this.suspiciousScore = 0.0f;
        
        this.aimSmoothness = 0.5f;
        this.movementConsistency = 0.5f;
        this.reactionTimeAverage = 0.25f;
        this.accuracyTrend = 0.0f;
        
        this.consecutiveHeadshots = 0;
        this.averageTimeBetweenShots = 0.0f;
        this.isCurrentlyShooting = false;
        
        this.aimAcceleration = 0.0f;
        this.aimJerkiness = 0.0f;
        this.hasUnhumanAiming = false;
        
        this.detectionCounts = new HashMap<>();
        this.confidenceScores = new HashMap<>();
    }
    
    /**
     * Add a new player action to the profile
     */
    public synchronized void addAction(PlayerAction action) {
        // Add to recent actions
        recentActions.offer(action);
        if (recentActions.size() > MAX_RECENT_ACTIONS) {
            recentActions.poll();
        }
        
        // Update basic metrics
        actionCount++;
        lastActionTime = action.getTimestamp();
        
        // Update specific behavior based on action type
        updateBehaviorMetrics(action);
        
        // Update suspicious score
        updateSuspiciousScore();
    }
    
    /**
     * Update behavior metrics based on action
     */
    private void updateBehaviorMetrics(PlayerAction action) {
        switch (action.getType()) {
            case MOVE:
                updateMovementMetrics(action);
                break;
            case SHOOT:
                updateShootingMetrics(action);
                break;
            case AIM:
                updateAimMetrics(action);
                break;
            case RELOAD:
                updateReloadMetrics(action);
                break;
            default:
                break;
        }
    }
    
    /**
     * Update movement-related metrics
     */
    private void updateMovementMetrics(PlayerAction action) {
        Vector3 currentPosition = new Vector3(action.getPositionX(), action.getPositionY(), action.getPositionZ());
        
        if (lastPosition != null) {
            float distance = currentPosition.distance(lastPosition);
            long timeDelta = action.getTimestamp() - lastMovementTime;
            
            if (timeDelta > 0) {
                float speed = distance / (timeDelta / 1000.0f); // units per second
                Vector3 velocity = currentPosition.subtract(lastPosition);
                
                // Update movement consistency
                if (lastVelocity != null) {
                    float velocityChange = velocity.subtract(lastVelocity).magnitude();
                    movementConsistency = (movementConsistency * 0.9f) + (velocityChange * 0.1f);
                }
                
                lastVelocity = velocity;
            }
        }
        
        lastPosition = currentPosition;
        lastMovementTime = action.getTimestamp();
    }
    
    /**
     * Update shooting-related metrics
     */
    private void updateShootingMetrics(PlayerAction action) {
        long currentTime = action.getTimestamp();
        
        if (lastShotTime > 0) {
            float timeBetweenShots = (currentTime - lastShotTime) / 1000.0f;
            averageTimeBetweenShots = (averageTimeBetweenShots * 0.8f) + (timeBetweenShots * 0.2f);
        }
        
        // Track headshots
        if (action.isHeadshot()) {
            consecutiveHeadshots++;
        } else {
            consecutiveHeadshots = 0;
        }
        
        // Update accuracy trend
        if (action.isHit()) {
            accuracyTrend = (accuracyTrend * 0.9f) + (0.1f);
        } else {
            accuracyTrend = (accuracyTrend * 0.9f);
        }
        
        lastShotTime = currentTime;
        isCurrentlyShooting = true;
    }
    
    /**
     * Update aim-related metrics
     */
    private void updateAimMetrics(PlayerAction action) {
        float currentAngleX = action.getViewAngleX();
        float currentAngleY = action.getViewAngleY();
        long currentTime = action.getTimestamp();
        
        if (lastViewAngleX != 0 || lastViewAngleY != 0) {
            float deltaX = currentAngleX - lastViewAngleX;
            float deltaY = currentAngleY - lastViewAngleY;
            
            // Normalize angle differences
            deltaX = normalizeAngle(deltaX);
            deltaY = normalizeAngle(deltaY);
            
            AimSample sample = new AimSample(currentAngleX, currentAngleY, currentTime, deltaX, deltaY);
            
            // Add to aim samples
            aimSamples.offer(sample);
            if (aimSamples.size() > MAX_AIM_SAMPLES) {
                aimSamples.poll();
            }
            
            // Calculate aim metrics
            updateAimSmoothness(sample);
            updateAimAcceleration(sample);
        }
        
        lastViewAngleX = currentAngleX;
        lastViewAngleY = currentAngleY;
    }
    
    /**
     * Update reload-related metrics
     */
    private void updateReloadMetrics(PlayerAction action) {
        isCurrentlyShooting = false;
        // Reset consecutive headshots on reload
        consecutiveHeadshots = 0;
    }
    
    /**
     * Update aim smoothness metric
     */
    private void updateAimSmoothness(AimSample sample) {
        if (aimSamples.size() >= 3) {
            // Calculate smoothness based on acceleration changes
            List<AimSample> samples = new ArrayList<>(aimSamples);
            float totalJerkiness = 0.0f;
            
            for (int i = 2; i < samples.size(); i++) {
                AimSample s1 = samples.get(i - 2);
                AimSample s2 = samples.get(i - 1);
                AimSample s3 = samples.get(i);
                
                // Calculate acceleration change (jerk)
                float accel1 = (s2.speed - s1.speed) / Math.max(1, s2.timestamp - s1.timestamp);
                float accel2 = (s3.speed - s2.speed) / Math.max(1, s3.timestamp - s2.timestamp);
                float jerk = Math.abs(accel2 - accel1);
                
                totalJerkiness += jerk;
            }
            
            aimJerkiness = totalJerkiness / (samples.size() - 2);
            aimSmoothness = 1.0f / (1.0f + aimJerkiness);
            
            // Check for unhuman aiming patterns
            hasUnhumanAiming = aimJerkiness < 0.001f && sample.speed > 10.0f; // Too smooth for high speed
        }
    }
    
    /**
     * Update aim acceleration metric
     */
    private void updateAimAcceleration(AimSample sample) {
        if (aimSamples.size() >= 2) {
            List<AimSample> samples = new ArrayList<>(aimSamples);
            AimSample prev = samples.get(samples.size() - 2);
            
            long timeDelta = sample.timestamp - prev.timestamp;
            if (timeDelta > 0) {
                aimAcceleration = (sample.speed - prev.speed) / timeDelta;
            }
        }
    }
    
    /**
     * Normalize angle difference to handle wraparound
     */
    private float normalizeAngle(float angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }
    
    /**
     * Add a cheat detection to history
     */
    public synchronized void addDetection(CheatDetection detection) {
        detectionHistory.offer(detection);
        if (detectionHistory.size() > MAX_DETECTION_HISTORY) {
            detectionHistory.poll();
        }
        
        // Update detection counts
        String type = detection.getViolationType().name();
        detectionCounts.merge(type, 1, Integer::sum);
        
        // Update confidence scores
        confidenceScores.put(type, Math.max(
            confidenceScores.getOrDefault(type, 0.0f),
            detection.getConfidence()
        ));
    }
    
    /**
     * Increment violation count
     */
    public synchronized void incrementViolationCount() {
        violationCount++;
    }
    
    /**
     * Update overall suspicious score
     */
    private void updateSuspiciousScore() {
        float score = 0.0f;
        
        // Factor in aim metrics
        if (hasUnhumanAiming) score += 0.3f;
        if (aimSmoothness < 0.1f) score += 0.2f;
        if (aimSmoothness > 0.95f) score += 0.15f;
        
        // Factor in shooting metrics
        if (consecutiveHeadshots > 10) score += 0.25f;
        if (averageTimeBetweenShots > 0 && averageTimeBetweenShots < 0.05f) score += 0.2f; // Too fast
        
        // Factor in accuracy
        if (accuracyTrend > 0.9f) score += 0.2f;
        
        // Factor in detection history
        float recentDetectionScore = calculateRecentDetectionScore();
        score += recentDetectionScore * 0.3f;
        
        // Factor in violation rate
        if (actionCount > 0) {
            float violationRate = (float) violationCount / actionCount;
            score += violationRate * 0.2f;
        }
        
        // Smooth the score change
        suspiciousScore = (suspiciousScore * 0.8f) + (score * 0.2f);
        suspiciousScore = Math.max(0.0f, Math.min(1.0f, suspiciousScore));
    }
    
    /**
     * Calculate score based on recent detections
     */
    private float calculateRecentDetectionScore() {
        if (detectionHistory.isEmpty()) {
            return 0.0f;
        }
        
        long currentTime = System.currentTimeMillis();
        long recentWindow = 60000; // 1 minute
        
        float totalConfidence = 0.0f;
        int recentCount = 0;
        
        for (CheatDetection detection : detectionHistory) {
            if (currentTime - detection.getTimestamp() <= recentWindow) {
                totalConfidence += detection.getConfidence();
                recentCount++;
            }
        }
        
        return recentCount > 0 ? totalConfidence / recentCount : 0.0f;
    }
    
    /**
     * Check if profile is stale and should be cleaned up
     */
    public boolean isStale() {
        return System.currentTimeMillis() - lastActionTime > STALE_PROFILE_TIME;
    }
    
    /**
     * Get recent actions within time window
     */
    public List<PlayerAction> getRecentActions(long timeWindowMs) {
        long cutoffTime = System.currentTimeMillis() - timeWindowMs;
        return recentActions.stream()
                .filter(action -> action.getTimestamp() >= cutoffTime)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get recent detections within time window
     */
    public List<CheatDetection> getRecentDetections(long timeWindowMs) {
        long cutoffTime = System.currentTimeMillis() - timeWindowMs;
        return detectionHistory.stream()
                .filter(detection -> detection.getTimestamp() >= cutoffTime)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get profile summary for logging
     */
    public String getProfileSummary() {
        return String.format(
            "PlayerRealtimeProfile{id=%s, actions=%d, violations=%d, suspicious=%.2f, " +
            "aimSmoothness=%.2f, accuracy=%.2f, headshots=%d}",
            playerId, actionCount, violationCount, suspiciousScore,
            aimSmoothness, accuracyTrend, consecutiveHeadshots
        );
    }
    
    // Getters
    public String getPlayerId() { return playerId; }
    public long getCreationTime() { return creationTime; }
    public long getLastActionTime() { return lastActionTime; }
    public int getActionCount() { return actionCount; }
    public int getViolationCount() { return violationCount; }
    public float getSuspiciousScore() { return suspiciousScore; }
    public float getAimSmoothness() { return aimSmoothness; }
    public float getMovementConsistency() { return movementConsistency; }
    public float getReactionTimeAverage() { return reactionTimeAverage; }
    public float getAccuracyTrend() { return accuracyTrend; }
    public Vector3 getLastPosition() { return lastPosition; }
    public Vector3 getLastVelocity() { return lastVelocity; }
    public float getLastViewAngleX() { return lastViewAngleX; }
    public float getLastViewAngleY() { return lastViewAngleY; }
    public long getLastShotTime() { return lastShotTime; }
    public int getConsecutiveHeadshots() { return consecutiveHeadshots; }
    public float getAverageTimeBetweenShots() { return averageTimeBetweenShots; }
    public boolean isCurrentlyShooting() { return isCurrentlyShooting; }
    public float getAimAcceleration() { return aimAcceleration; }
    public float getAimJerkiness() { return aimJerkiness; }
    public boolean hasUnhumanAiming() { return hasUnhumanAiming; }
    public Queue<PlayerAction> getRecentActions() { return new ConcurrentLinkedQueue<>(recentActions); }
    public Queue<CheatDetection> getDetectionHistory() { return new ConcurrentLinkedQueue<>(detectionHistory); }
    public Queue<AimSample> getAimSamples() { return new ConcurrentLinkedQueue<>(aimSamples); }
    public Map<String, Integer> getDetectionCounts() { return new HashMap<>(detectionCounts); }
    public Map<String, Float> getConfidenceScores() { return new HashMap<>(confidenceScores); }
}