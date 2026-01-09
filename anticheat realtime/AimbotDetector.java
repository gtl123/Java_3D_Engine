package fps.anticheat.realtime;

import fps.anticheat.*;

import java.util.*;

/**
 * Detects aimbot cheats in real-time by analyzing aim behavior patterns.
 * Uses multiple detection methods including aim smoothness, snap detection, and tracking analysis.
 */
public class AimbotDetector {
    
    private final AntiCheatConfiguration config;
    private final Map<String, AimbotProfile> playerProfiles;
    
    // Detection thresholds
    private static final float SNAP_ANGLE_THRESHOLD = 45.0f; // Degrees
    private static final float SNAP_TIME_THRESHOLD = 50.0f; // Milliseconds
    private static final float PERFECT_TRACKING_THRESHOLD = 0.95f; // Tracking accuracy
    private static final float INHUMAN_SMOOTHNESS_THRESHOLD = 0.02f; // Too smooth
    private static final float INHUMAN_PRECISION_THRESHOLD = 0.001f; // Too precise
    private static final int MIN_SAMPLES_FOR_DETECTION = 10;
    
    // Cache settings
    private static final long PROFILE_CACHE_DURATION = 1800000; // 30 minutes
    
    /**
     * Profile for tracking aimbot-specific behavior
     */
    private static class AimbotProfile {
        float averageAimSpeed;
        float aimSmoothness;
        float trackingAccuracy;
        int snapCount;
        int perfectTrackingCount;
        long lastUpdateTime;
        Queue<AimSnapshot> aimHistory;
        
        AimbotProfile() {
            this.averageAimSpeed = 0.0f;
            this.aimSmoothness = 0.5f;
            this.trackingAccuracy = 0.0f;
            this.snapCount = 0;
            this.perfectTrackingCount = 0;
            this.lastUpdateTime = System.currentTimeMillis();
            this.aimHistory = new LinkedList<>();
        }
        
        boolean isStale() {
            return System.currentTimeMillis() - lastUpdateTime > PROFILE_CACHE_DURATION;
        }
    }
    
    /**
     * Snapshot of aim data at a specific moment
     */
    private static class AimSnapshot {
        final float angleX, angleY;
        final float deltaX, deltaY;
        final float speed;
        final long timestamp;
        final boolean isShooting;
        final boolean isTargeting;
        
        AimSnapshot(float angleX, float angleY, float deltaX, float deltaY, 
                   long timestamp, boolean isShooting, boolean isTargeting) {
            this.angleX = angleX;
            this.angleY = angleY;
            this.deltaX = deltaX;
            this.deltaY = deltaY;
            this.speed = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            this.timestamp = timestamp;
            this.isShooting = isShooting;
            this.isTargeting = isTargeting;
        }
    }
    
    public AimbotDetector(AntiCheatConfiguration config) {
        this.config = config;
        this.playerProfiles = new HashMap<>();
    }
    
    /**
     * Detect aimbot behavior in player action
     */
    public List<CheatDetection> detectAimbot(PlayerAction action, PlayerRealtimeProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        // Only analyze aim-related actions
        if (action.getType() != PlayerAction.ActionType.AIM && 
            action.getType() != PlayerAction.ActionType.SHOOT) {
            return detections;
        }
        
        // Get or create aimbot profile
        AimbotProfile aimbotProfile = getOrCreateAimbotProfile(action.getPlayerId());
        
        // Update profile with new aim data
        updateAimbotProfile(aimbotProfile, action, profile);
        
        // Perform aimbot detections
        detections.addAll(detectAimSnapping(action, aimbotProfile));
        detections.addAll(detectInhumanSmoothness(action, aimbotProfile));
        detections.addAll(detectPerfectTracking(action, aimbotProfile));
        detections.addAll(detectInhumanPrecision(action, aimbotProfile));
        detections.addAll(detectImpossibleAimSpeed(action, aimbotProfile));
        
        return detections;
    }
    
    /**
     * Get or create aimbot profile for player
     */
    private AimbotProfile getOrCreateAimbotProfile(String playerId) {
        return playerProfiles.computeIfAbsent(playerId, k -> new AimbotProfile());
    }
    
    /**
     * Update aimbot profile with new aim data
     */
    private void updateAimbotProfile(AimbotProfile aimbotProfile, PlayerAction action, PlayerRealtimeProfile profile) {
        float currentAngleX = action.getViewAngleX();
        float currentAngleY = action.getViewAngleY();
        
        // Get previous angles from profile
        float prevAngleX = profile.getLastViewAngleX();
        float prevAngleY = profile.getLastViewAngleY();
        
        // Calculate deltas
        float deltaX = normalizeAngle(currentAngleX - prevAngleX);
        float deltaY = normalizeAngle(currentAngleY - prevAngleY);
        
        // Create aim snapshot
        AimSnapshot snapshot = new AimSnapshot(
            currentAngleX, currentAngleY, deltaX, deltaY,
            action.getTimestamp(), 
            action.getType() == PlayerAction.ActionType.SHOOT,
            isTargetingEnemy(action)
        );
        
        // Add to history
        aimbotProfile.aimHistory.offer(snapshot);
        if (aimbotProfile.aimHistory.size() > 50) { // Keep last 50 samples
            aimbotProfile.aimHistory.poll();
        }
        
        // Update profile metrics
        updateProfileMetrics(aimbotProfile);
        aimbotProfile.lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Update profile metrics based on aim history
     */
    private void updateProfileMetrics(AimbotProfile profile) {
        if (profile.aimHistory.size() < 2) return;
        
        List<AimSnapshot> history = new ArrayList<>(profile.aimHistory);
        
        // Calculate average aim speed
        float totalSpeed = 0.0f;
        int speedSamples = 0;
        
        for (AimSnapshot snapshot : history) {
            if (snapshot.speed > 0) {
                totalSpeed += snapshot.speed;
                speedSamples++;
            }
        }
        
        if (speedSamples > 0) {
            profile.averageAimSpeed = totalSpeed / speedSamples;
        }
        
        // Calculate aim smoothness (variance in acceleration)
        if (history.size() >= 3) {
            profile.aimSmoothness = calculateAimSmoothness(history);
        }
        
        // Calculate tracking accuracy
        profile.trackingAccuracy = calculateTrackingAccuracy(history);
    }
    
    /**
     * Calculate aim smoothness based on acceleration variance
     */
    private float calculateAimSmoothness(List<AimSnapshot> history) {
        if (history.size() < 3) return 0.5f;
        
        List<Float> accelerations = new ArrayList<>();
        
        for (int i = 2; i < history.size(); i++) {
            AimSnapshot s1 = history.get(i - 2);
            AimSnapshot s2 = history.get(i - 1);
            AimSnapshot s3 = history.get(i);
            
            long dt1 = s2.timestamp - s1.timestamp;
            long dt2 = s3.timestamp - s2.timestamp;
            
            if (dt1 > 0 && dt2 > 0) {
                float accel1 = (s2.speed - s1.speed) / dt1;
                float accel2 = (s3.speed - s2.speed) / dt2;
                float jerk = Math.abs(accel2 - accel1);
                accelerations.add(jerk);
            }
        }
        
        if (accelerations.isEmpty()) return 0.5f;
        
        // Calculate variance in acceleration (jerk)
        float mean = accelerations.stream().reduce(0.0f, Float::sum) / accelerations.size();
        float variance = accelerations.stream()
                .map(a -> (a - mean) * (a - mean))
                .reduce(0.0f, Float::sum) / accelerations.size();
        
        // Convert to smoothness score (lower variance = higher smoothness)
        return 1.0f / (1.0f + variance);
    }
    
    /**
     * Calculate tracking accuracy when targeting enemies
     */
    private float calculateTrackingAccuracy(List<AimSnapshot> history) {
        int targetingSamples = 0;
        int accurateSamples = 0;
        
        for (AimSnapshot snapshot : history) {
            if (snapshot.isTargeting) {
                targetingSamples++;
                // Consider accurate if aim change is small and consistent
                if (snapshot.speed < 5.0f && snapshot.speed > 0.1f) {
                    accurateSamples++;
                }
            }
        }
        
        return targetingSamples > 0 ? (float) accurateSamples / targetingSamples : 0.0f;
    }
    
    /**
     * Detect aim snapping (sudden large aim movements)
     */
    private List<CheatDetection> detectAimSnapping(PlayerAction action, AimbotProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        if (profile.aimHistory.size() < 2) return detections;
        
        AimSnapshot latest = ((LinkedList<AimSnapshot>) profile.aimHistory).peekLast();
        if (latest == null) return detections;
        
        // Check for snap to target
        if (latest.speed > SNAP_ANGLE_THRESHOLD) {
            // Check if this was followed by immediate shooting
            boolean immediateShoot = latest.isShooting || 
                (action.getType() == PlayerAction.ActionType.SHOOT && 
                 action.getTimestamp() - latest.timestamp < SNAP_TIME_THRESHOLD);
            
            if (immediateShoot) {
                float confidence = Math.min(1.0f, latest.speed / (SNAP_ANGLE_THRESHOLD * 2));
                
                CheatDetection detection = new CheatDetection(
                    ViolationType.AIMBOT,
                    String.format("Aim snap detected: %.1f° in %dms", 
                                latest.speed, action.getTimestamp() - latest.timestamp),
                    confidence,
                    "AimbotDetector"
                );
                
                detection.addEvidence(String.format("Snap speed: %.2f°, Threshold: %.2f°", 
                                                   latest.speed, SNAP_ANGLE_THRESHOLD));
                detection.addMetadata("snapSpeed", latest.speed);
                detection.addMetadata("snapTime", action.getTimestamp() - latest.timestamp);
                
                detections.add(detection);
                profile.snapCount++;
            }
        }
        
        return detections;
    }
    
    /**
     * Detect inhuman smoothness (too perfect aim movement)
     */
    private List<CheatDetection> detectInhumanSmoothness(PlayerAction action, AimbotProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        if (profile.aimHistory.size() < MIN_SAMPLES_FOR_DETECTION) return detections;
        
        // Check if aim is unnaturally smooth
        if (profile.aimSmoothness > (1.0f - INHUMAN_SMOOTHNESS_THRESHOLD) && 
            profile.averageAimSpeed > 5.0f) {
            
            float confidence = (profile.aimSmoothness - (1.0f - INHUMAN_SMOOTHNESS_THRESHOLD)) / INHUMAN_SMOOTHNESS_THRESHOLD;
            
            CheatDetection detection = new CheatDetection(
                ViolationType.AIMBOT,
                String.format("Inhuman aim smoothness detected: %.3f", profile.aimSmoothness),
                confidence,
                "AimbotDetector"
            );
            
            detection.addEvidence(String.format("Smoothness: %.4f, Threshold: %.4f", 
                                               profile.aimSmoothness, 1.0f - INHUMAN_SMOOTHNESS_THRESHOLD));
            detection.addMetadata("aimSmoothness", profile.aimSmoothness);
            detection.addMetadata("averageAimSpeed", profile.averageAimSpeed);
            
            detections.add(detection);
        }
        
        return detections;
    }
    
    /**
     * Detect perfect tracking (too accurate target following)
     */
    private List<CheatDetection> detectPerfectTracking(PlayerAction action, AimbotProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        if (profile.trackingAccuracy >= PERFECT_TRACKING_THRESHOLD) {
            profile.perfectTrackingCount++;
            
            // Require multiple instances of perfect tracking
            if (profile.perfectTrackingCount >= 3) {
                float confidence = (profile.trackingAccuracy - PERFECT_TRACKING_THRESHOLD) / 
                                 (1.0f - PERFECT_TRACKING_THRESHOLD);
                
                CheatDetection detection = new CheatDetection(
                    ViolationType.AIMBOT,
                    String.format("Perfect target tracking detected: %.3f accuracy", profile.trackingAccuracy),
                    confidence,
                    "AimbotDetector"
                );
                
                detection.addEvidence(String.format("Tracking accuracy: %.4f, Threshold: %.4f", 
                                                   profile.trackingAccuracy, PERFECT_TRACKING_THRESHOLD));
                detection.addMetadata("trackingAccuracy", profile.trackingAccuracy);
                detection.addMetadata("perfectTrackingCount", profile.perfectTrackingCount);
                
                detections.add(detection);
            }
        } else {
            // Reset counter if tracking isn't perfect
            profile.perfectTrackingCount = Math.max(0, profile.perfectTrackingCount - 1);
        }
        
        return detections;
    }
    
    /**
     * Detect inhuman precision (movements too precise for human input)
     */
    private List<CheatDetection> detectInhumanPrecision(PlayerAction action, AimbotProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        if (profile.aimHistory.size() < 5) return detections;
        
        // Check for repeated identical movements (sign of bot)
        List<AimSnapshot> recent = new ArrayList<>(profile.aimHistory).subList(
            Math.max(0, profile.aimHistory.size() - 5), profile.aimHistory.size());
        
        int identicalMovements = 0;
        for (int i = 1; i < recent.size(); i++) {
            AimSnapshot prev = recent.get(i - 1);
            AimSnapshot curr = recent.get(i);
            
            if (Math.abs(curr.deltaX - prev.deltaX) < INHUMAN_PRECISION_THRESHOLD &&
                Math.abs(curr.deltaY - prev.deltaY) < INHUMAN_PRECISION_THRESHOLD &&
                curr.speed > 1.0f) {
                identicalMovements++;
            }
        }
        
        if (identicalMovements >= 3) {
            float confidence = Math.min(1.0f, identicalMovements / 4.0f);
            
            CheatDetection detection = new CheatDetection(
                ViolationType.AIMBOT,
                String.format("Inhuman aim precision detected: %d identical movements", identicalMovements),
                confidence,
                "AimbotDetector"
            );
            
            detection.addEvidence(String.format("Identical movements: %d, Precision threshold: %.6f", 
                                               identicalMovements, INHUMAN_PRECISION_THRESHOLD));
            detection.addMetadata("identicalMovements", identicalMovements);
            
            detections.add(detection);
        }
        
        return detections;
    }
    
    /**
     * Detect impossible aim speeds
     */
    private List<CheatDetection> detectImpossibleAimSpeed(PlayerAction action, AimbotProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        if (profile.aimHistory.isEmpty()) return detections;
        
        AimSnapshot latest = ((LinkedList<AimSnapshot>) profile.aimHistory).peekLast();
        if (latest == null) return detections;
        
        // Check for impossible aim speed (too fast for human)
        float maxHumanAimSpeed = 180.0f; // degrees per frame at 60fps
        if (latest.speed > maxHumanAimSpeed) {
            float confidence = Math.min(1.0f, latest.speed / (maxHumanAimSpeed * 2));
            
            CheatDetection detection = new CheatDetection(
                ViolationType.AIMBOT,
                String.format("Impossible aim speed detected: %.1f°/frame", latest.speed),
                confidence,
                "AimbotDetector"
            );
            
            detection.addEvidence(String.format("Aim speed: %.2f°, Max human: %.2f°", 
                                               latest.speed, maxHumanAimSpeed));
            detection.addMetadata("aimSpeed", latest.speed);
            detection.addMetadata("maxHumanSpeed", maxHumanAimSpeed);
            
            detections.add(detection);
        }
        
        return detections;
    }
    
    /**
     * Normalize angle to handle wraparound
     */
    private float normalizeAngle(float angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }
    
    /**
     * Check if player is currently targeting an enemy (simplified)
     */
    private boolean isTargetingEnemy(PlayerAction action) {
        // This would integrate with the game's target detection system
        // For now, assume targeting if shooting or recent aim movement toward center
        return action.getType() == PlayerAction.ActionType.SHOOT ||
               (Math.abs(action.getViewAngleX()) < 45 && Math.abs(action.getViewAngleY()) < 45);
    }
    
    /**
     * Clean up old profiles
     */
    public void cleanup() {
        playerProfiles.entrySet().removeIf(entry -> entry.getValue().isStale());
    }
    
    /**
     * Get aimbot statistics for a player
     */
    public Map<String, Object> getPlayerAimbotStats(String playerId) {
        AimbotProfile profile = playerProfiles.get(playerId);
        if (profile == null) return null;
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("averageAimSpeed", profile.averageAimSpeed);
        stats.put("aimSmoothness", profile.aimSmoothness);
        stats.put("trackingAccuracy", profile.trackingAccuracy);
        stats.put("snapCount", profile.snapCount);
        stats.put("perfectTrackingCount", profile.perfectTrackingCount);
        stats.put("sampleCount", profile.aimHistory.size());
        stats.put("lastUpdate", profile.lastUpdateTime);
        
        return stats;
    }
}