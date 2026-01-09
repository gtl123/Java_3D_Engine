package fps.anticheat.realtime;

import fps.anticheat.*;

import java.util.*;

/**
 * Detects speed hack cheats in real-time by analyzing player movement patterns.
 * Identifies impossible movement speeds, teleportation, and physics violations.
 */
public class SpeedHackDetector {
    
    private final AntiCheatConfiguration config;
    private final Map<String, SpeedHackProfile> playerProfiles;
    
    // Detection thresholds
    private static final float MAX_HUMAN_SPEED = 12.0f; // units per second
    private static final float MAX_SPRINT_SPEED = 18.0f; // units per second
    private static final float TELEPORT_DISTANCE_THRESHOLD = 50.0f; // units
    private static final float TELEPORT_TIME_THRESHOLD = 100.0f; // milliseconds
    private static final float IMPOSSIBLE_ACCELERATION = 100.0f; // units per second squared
    
    /**
     * Profile for tracking speed hack behavior
     */
    private static class SpeedHackProfile {
        Vector3 lastPosition;
        long lastPositionTime;
        float maxRecordedSpeed;
        int speedViolationCount;
        int teleportCount;
        Queue<MovementSample> movementHistory;
        long lastUpdateTime;
        
        SpeedHackProfile() {
            this.lastPosition = null;
            this.lastPositionTime = 0;
            this.maxRecordedSpeed = 0.0f;
            this.speedViolationCount = 0;
            this.teleportCount = 0;
            this.movementHistory = new LinkedList<>();
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        boolean isStale() {
            return System.currentTimeMillis() - lastUpdateTime > 1800000; // 30 minutes
        }
    }
    
    /**
     * Movement sample for analysis
     */
    private static class MovementSample {
        final Vector3 position;
        final float speed;
        final float acceleration;
        final long timestamp;
        
        MovementSample(Vector3 position, float speed, float acceleration, long timestamp) {
            this.position = position;
            this.speed = speed;
            this.acceleration = acceleration;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * 3D Vector for position calculations
     */
    private static class Vector3 {
        final float x, y, z;
        
        Vector3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        float distance(Vector3 other) {
            float dx = x - other.x;
            float dy = y - other.y;
            float dz = z - other.z;
            return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        
        Vector3 subtract(Vector3 other) {
            return new Vector3(x - other.x, y - other.y, z - other.z);
        }
        
        float magnitude() {
            return (float) Math.sqrt(x * x + y * y + z * z);
        }
    }
    
    public SpeedHackDetector(AntiCheatConfiguration config) {
        this.config = config;
        this.playerProfiles = new HashMap<>();
    }
    
    /**
     * Detect speed hack behavior in player action
     */
    public List<CheatDetection> detectSpeedHack(PlayerAction action, PlayerRealtimeProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        // Only analyze movement actions
        if (action.getType() != PlayerAction.ActionType.MOVE) {
            return detections;
        }
        
        // Get or create speed hack profile
        SpeedHackProfile speedProfile = getOrCreateSpeedProfile(action.getPlayerId());
        
        // Update profile with new movement data
        updateSpeedProfile(speedProfile, action);
        
        // Perform speed hack detections
        detections.addAll(detectImpossibleSpeed(action, speedProfile));
        detections.addAll(detectTeleportation(action, speedProfile));
        detections.addAll(detectImpossibleAcceleration(action, speedProfile));
        detections.addAll(detectConsistentSpeedHack(action, speedProfile));
        
        speedProfile.lastUpdateTime = System.currentTimeMillis();
        return detections;
    }
    
    /**
     * Get or create speed profile for player
     */
    private SpeedHackProfile getOrCreateSpeedProfile(String playerId) {
        return playerProfiles.computeIfAbsent(playerId, k -> new SpeedHackProfile());
    }
    
    /**
     * Update speed profile with new movement data
     */
    private void updateSpeedProfile(SpeedHackProfile profile, PlayerAction action) {
        Vector3 currentPosition = new Vector3(action.getPositionX(), action.getPositionY(), action.getPositionZ());
        long currentTime = action.getTimestamp();
        
        if (profile.lastPosition != null && profile.lastPositionTime > 0) {
            // Calculate movement metrics
            float distance = currentPosition.distance(profile.lastPosition);
            long timeDelta = currentTime - profile.lastPositionTime;
            
            if (timeDelta > 0) {
                float speed = distance / (timeDelta / 1000.0f); // units per second
                
                // Calculate acceleration
                float acceleration = 0.0f;
                if (!profile.movementHistory.isEmpty()) {
                    MovementSample lastSample = ((LinkedList<MovementSample>) profile.movementHistory).peekLast();
                    if (lastSample != null) {
                        long accelTimeDelta = currentTime - lastSample.timestamp;
                        if (accelTimeDelta > 0) {
                            acceleration = (speed - lastSample.speed) / (accelTimeDelta / 1000.0f);
                        }
                    }
                }
                
                // Update max recorded speed
                profile.maxRecordedSpeed = Math.max(profile.maxRecordedSpeed, speed);
                
                // Add movement sample
                MovementSample sample = new MovementSample(currentPosition, speed, acceleration, currentTime);
                profile.movementHistory.offer(sample);
                
                // Keep only recent samples
                if (profile.movementHistory.size() > 20) {
                    profile.movementHistory.poll();
                }
            }
        }
        
        profile.lastPosition = currentPosition;
        profile.lastPositionTime = currentTime;
    }
    
    /**
     * Detect impossible movement speeds
     */
    private List<CheatDetection> detectImpossibleSpeed(PlayerAction action, SpeedHackProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        if (profile.movementHistory.isEmpty()) return detections;
        
        MovementSample latest = ((LinkedList<MovementSample>) profile.movementHistory).peekLast();
        if (latest == null) return detections;
        
        float speed = latest.speed;
        float maxAllowedSpeed = isPlayerSprinting(action) ? MAX_SPRINT_SPEED : MAX_HUMAN_SPEED;
        
        if (speed > maxAllowedSpeed) {
            profile.speedViolationCount++;
            
            float severity = Math.min(1.0f, speed / (maxAllowedSpeed * 2));
            
            CheatDetection detection = new CheatDetection(
                ViolationType.SPEED_HACK,
                String.format("Impossible movement speed detected: %.1f units/sec", speed),
                severity,
                "SpeedHackDetector"
            );
            
            detection.addEvidence(String.format("Speed: %.2f, Max allowed: %.2f, Violations: %d", 
                                               speed, maxAllowedSpeed, profile.speedViolationCount));
            detection.addMetadata("speed", speed);
            detection.addMetadata("maxAllowedSpeed", maxAllowedSpeed);
            detection.addMetadata("speedViolationCount", profile.speedViolationCount);
            
            detections.add(detection);
        }
        
        return detections;
    }
    
    /**
     * Detect teleportation (instant movement over large distances)
     */
    private List<CheatDetection> detectTeleportation(PlayerAction action, SpeedHackProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        if (profile.lastPosition == null) return detections;
        
        Vector3 currentPosition = new Vector3(action.getPositionX(), action.getPositionY(), action.getPositionZ());
        float distance = currentPosition.distance(profile.lastPosition);
        long timeDelta = action.getTimestamp() - profile.lastPositionTime;
        
        // Check for teleportation (large distance in short time)
        if (distance > TELEPORT_DISTANCE_THRESHOLD && timeDelta < TELEPORT_TIME_THRESHOLD) {
            profile.teleportCount++;
            
            float severity = Math.min(1.0f, distance / (TELEPORT_DISTANCE_THRESHOLD * 2));
            
            CheatDetection detection = new CheatDetection(
                ViolationType.SPEED_HACK,
                String.format("Teleportation detected: %.1f units in %dms", distance, timeDelta),
                severity,
                "SpeedHackDetector"
            );
            
            detection.addEvidence(String.format("Distance: %.2f, Time: %dms, Teleports: %d", 
                                               distance, timeDelta, profile.teleportCount));
            detection.addMetadata("teleportDistance", distance);
            detection.addMetadata("teleportTime", timeDelta);
            detection.addMetadata("teleportCount", profile.teleportCount);
            
            detections.add(detection);
        }
        
        return detections;
    }
    
    /**
     * Detect impossible acceleration
     */
    private List<CheatDetection> detectImpossibleAcceleration(PlayerAction action, SpeedHackProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        if (profile.movementHistory.isEmpty()) return detections;
        
        MovementSample latest = ((LinkedList<MovementSample>) profile.movementHistory).peekLast();
        if (latest == null) return detections;
        
        float acceleration = Math.abs(latest.acceleration);
        
        if (acceleration > IMPOSSIBLE_ACCELERATION) {
            float severity = Math.min(1.0f, acceleration / (IMPOSSIBLE_ACCELERATION * 2));
            
            CheatDetection detection = new CheatDetection(
                ViolationType.SPEED_HACK,
                String.format("Impossible acceleration detected: %.1f units/sec²", acceleration),
                severity,
                "SpeedHackDetector"
            );
            
            detection.addEvidence(String.format("Acceleration: %.2f, Max allowed: %.2f", 
                                               acceleration, IMPOSSIBLE_ACCELERATION));
            detection.addMetadata("acceleration", acceleration);
            detection.addMetadata("maxAllowedAcceleration", IMPOSSIBLE_ACCELERATION);
            
            detections.add(detection);
        }
        
        return detections;
    }
    
    /**
     * Detect consistent speed hacking patterns
     */
    private List<CheatDetection> detectConsistentSpeedHack(PlayerAction action, SpeedHackProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        if (profile.movementHistory.size() < 10) return detections;
        
        // Check for consistently high speeds
        int highSpeedSamples = 0;
        float totalSpeed = 0.0f;
        
        for (MovementSample sample : profile.movementHistory) {
            totalSpeed += sample.speed;
            if (sample.speed > MAX_HUMAN_SPEED) {
                highSpeedSamples++;
            }
        }
        
        float averageSpeed = totalSpeed / profile.movementHistory.size();
        float highSpeedRatio = (float) highSpeedSamples / profile.movementHistory.size();
        
        // If more than 50% of recent movements are too fast
        if (highSpeedRatio > 0.5f && averageSpeed > MAX_HUMAN_SPEED) {
            float severity = Math.min(1.0f, highSpeedRatio);
            
            CheatDetection detection = new CheatDetection(
                ViolationType.SPEED_HACK,
                String.format("Consistent speed hacking detected (%.1f%% violations)", highSpeedRatio * 100),
                severity,
                "SpeedHackDetector"
            );
            
            detection.addEvidence(String.format("High speed ratio: %.2f, Average speed: %.2f", 
                                               highSpeedRatio, averageSpeed));
            detection.addMetadata("highSpeedRatio", highSpeedRatio);
            detection.addMetadata("averageSpeed", averageSpeed);
            detection.addMetadata("sampleCount", profile.movementHistory.size());
            
            detections.add(detection);
        }
        
        return detections;
    }
    
    /**
     * Check if player is currently sprinting (simplified)
     */
    private boolean isPlayerSprinting(PlayerAction action) {
        // This would check the player's current state
        // For demonstration, we'll use a simple heuristic
        return action.getMetadata("sprinting") != null && 
               (Boolean) action.getMetadata("sprinting");
    }
    
    /**
     * Clean up old profiles
     */
    public void cleanup() {
        playerProfiles.entrySet().removeIf(entry -> entry.getValue().isStale());
    }
    
    /**
     * Get speed hack statistics for a player
     */
    public Map<String, Object> getPlayerSpeedStats(String playerId) {
        SpeedHackProfile profile = playerProfiles.get(playerId);
        if (profile == null) return null;
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("maxRecordedSpeed", profile.maxRecordedSpeed);
        stats.put("speedViolationCount", profile.speedViolationCount);
        stats.put("teleportCount", profile.teleportCount);
        stats.put("movementSamples", profile.movementHistory.size());
        stats.put("lastUpdate", profile.lastUpdateTime);
        
        // Calculate average speed from recent samples
        if (!profile.movementHistory.isEmpty()) {
            float totalSpeed = 0.0f;
            for (MovementSample sample : profile.movementHistory) {
                totalSpeed += sample.speed;
            }
            stats.put("averageRecentSpeed", totalSpeed / profile.movementHistory.size());
        }
        
        return stats;
    }
}