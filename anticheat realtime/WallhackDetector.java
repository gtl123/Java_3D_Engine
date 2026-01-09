package fps.anticheat.realtime;

import fps.anticheat.*;

import java.util.*;

/**
 * Detects wallhack cheats in real-time by analyzing player behavior and game state.
 * Identifies impossible information gathering and suspicious pre-aiming through walls.
 */
public class WallhackDetector {
    
    private final AntiCheatConfiguration config;
    private final Map<String, WallhackProfile> playerProfiles;
    
    // Detection thresholds
    private static final float IMPOSSIBLE_KNOWLEDGE_THRESHOLD = 0.8f;
    private static final float PREFIRE_TIME_THRESHOLD = 200.0f; // ms
    private static final float WALL_TRACKING_THRESHOLD = 0.7f;
    private static final int MIN_SAMPLES_FOR_DETECTION = 15;
    
    /**
     * Profile for tracking wallhack-specific behavior
     */
    private static class WallhackProfile {
        int impossibleKnowledgeCount;
        int prefireCount;
        int wallTrackingCount;
        float suspiciousLookRate;
        long lastUpdateTime;
        Queue<SuspiciousEvent> eventHistory;
        
        WallhackProfile() {
            this.impossibleKnowledgeCount = 0;
            this.prefireCount = 0;
            this.wallTrackingCount = 0;
            this.suspiciousLookRate = 0.0f;
            this.lastUpdateTime = System.currentTimeMillis();
            this.eventHistory = new LinkedList<>();
        }
        
        boolean isStale() {
            return System.currentTimeMillis() - lastUpdateTime > 1800000; // 30 minutes
        }
    }
    
    /**
     * Suspicious event for wallhack detection
     */
    private static class SuspiciousEvent {
        final String eventType;
        final float confidence;
        final long timestamp;
        final String details;
        
        SuspiciousEvent(String eventType, float confidence, String details) {
            this.eventType = eventType;
            this.confidence = confidence;
            this.timestamp = System.currentTimeMillis();
            this.details = details;
        }
    }
    
    public WallhackDetector(AntiCheatConfiguration config) {
        this.config = config;
        this.playerProfiles = new HashMap<>();
    }
    
    /**
     * Detect wallhack behavior in player action
     */
    public List<CheatDetection> detectWallhack(PlayerAction action, PlayerRealtimeProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        // Get or create wallhack profile
        WallhackProfile wallhackProfile = getOrCreateWallhackProfile(action.getPlayerId());
        
        // Perform wallhack detections
        detections.addAll(detectImpossibleKnowledge(action, wallhackProfile));
        detections.addAll(detectPrefire(action, wallhackProfile));
        detections.addAll(detectWallTracking(action, wallhackProfile));
        detections.addAll(detectSuspiciousLooking(action, wallhackProfile));
        
        wallhackProfile.lastUpdateTime = System.currentTimeMillis();
        return detections;
    }
    
    /**
     * Get or create wallhack profile for player
     */
    private WallhackProfile getOrCreateWallhackProfile(String playerId) {
        return playerProfiles.computeIfAbsent(playerId, k -> new WallhackProfile());
    }
    
    /**
     * Detect impossible knowledge of enemy positions
     */
    private List<CheatDetection> detectImpossibleKnowledge(PlayerAction action, WallhackProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        // Check if player is looking at enemies through walls
        if (isLookingThroughWalls(action)) {
            profile.impossibleKnowledgeCount++;
            
            if (profile.impossibleKnowledgeCount >= 3) {
                float confidence = Math.min(1.0f, profile.impossibleKnowledgeCount / 10.0f);
                
                CheatDetection detection = new CheatDetection(
                    ViolationType.WALLHACK,
                    String.format("Impossible enemy knowledge detected (%d instances)", profile.impossibleKnowledgeCount),
                    confidence,
                    "WallhackDetector"
                );
                
                detection.addEvidence(String.format("Impossible knowledge count: %d", profile.impossibleKnowledgeCount));
                detection.addMetadata("impossibleKnowledgeCount", profile.impossibleKnowledgeCount);
                
                detections.add(detection);
                
                // Add to event history
                profile.eventHistory.offer(new SuspiciousEvent("IMPOSSIBLE_KNOWLEDGE", confidence, 
                    "Looking at enemies through walls"));
            }
        }
        
        return detections;
    }
    
    /**
     * Detect prefiring (shooting before enemy is visible)
     */
    private List<CheatDetection> detectPrefire(PlayerAction action, WallhackProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        if (action.getType() == PlayerAction.ActionType.SHOOT) {
            // Check if shooting before enemy becomes visible
            if (isPrefiring(action)) {
                profile.prefireCount++;
                
                if (profile.prefireCount >= 2) {
                    float confidence = Math.min(1.0f, profile.prefireCount / 5.0f);
                    
                    CheatDetection detection = new CheatDetection(
                        ViolationType.WALLHACK,
                        String.format("Prefiring detected (%d instances)", profile.prefireCount),
                        confidence,
                        "WallhackDetector"
                    );
                    
                    detection.addEvidence(String.format("Prefire count: %d", profile.prefireCount));
                    detection.addMetadata("prefireCount", profile.prefireCount);
                    
                    detections.add(detection);
                    
                    // Add to event history
                    profile.eventHistory.offer(new SuspiciousEvent("PREFIRE", confidence, 
                        "Shooting before enemy visible"));
                }
            }
        }
        
        return detections;
    }
    
    /**
     * Detect tracking enemies through walls
     */
    private List<CheatDetection> detectWallTracking(PlayerAction action, WallhackProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        if (action.getType() == PlayerAction.ActionType.AIM) {
            // Check if tracking enemy movement through walls
            if (isTrackingThroughWalls(action)) {
                profile.wallTrackingCount++;
                
                if (profile.wallTrackingCount >= 5) {
                    float confidence = Math.min(1.0f, profile.wallTrackingCount / 10.0f);
                    
                    CheatDetection detection = new CheatDetection(
                        ViolationType.WALLHACK,
                        String.format("Wall tracking detected (%d instances)", profile.wallTrackingCount),
                        confidence,
                        "WallhackDetector"
                    );
                    
                    detection.addEvidence(String.format("Wall tracking count: %d", profile.wallTrackingCount));
                    detection.addMetadata("wallTrackingCount", profile.wallTrackingCount);
                    
                    detections.add(detection);
                    
                    // Add to event history
                    profile.eventHistory.offer(new SuspiciousEvent("WALL_TRACKING", confidence, 
                        "Tracking enemies through walls"));
                }
            }
        }
        
        return detections;
    }
    
    /**
     * Detect suspicious looking patterns
     */
    private List<CheatDetection> detectSuspiciousLooking(PlayerAction action, WallhackProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        // Calculate suspicious look rate
        updateSuspiciousLookRate(profile);
        
        if (profile.suspiciousLookRate > IMPOSSIBLE_KNOWLEDGE_THRESHOLD) {
            float confidence = (profile.suspiciousLookRate - IMPOSSIBLE_KNOWLEDGE_THRESHOLD) / 
                             (1.0f - IMPOSSIBLE_KNOWLEDGE_THRESHOLD);
            
            CheatDetection detection = new CheatDetection(
                ViolationType.WALLHACK,
                String.format("Suspicious looking pattern detected (rate: %.2f)", profile.suspiciousLookRate),
                confidence,
                "WallhackDetector"
            );
            
            detection.addEvidence(String.format("Suspicious look rate: %.3f, Threshold: %.3f", 
                                               profile.suspiciousLookRate, IMPOSSIBLE_KNOWLEDGE_THRESHOLD));
            detection.addMetadata("suspiciousLookRate", profile.suspiciousLookRate);
            
            detections.add(detection);
        }
        
        return detections;
    }
    
    /**
     * Check if player is looking at enemies through walls (simplified)
     */
    private boolean isLookingThroughWalls(PlayerAction action) {
        // This would integrate with the game's visibility system
        // For demonstration, we'll use simplified logic
        
        // Check if aiming at specific angles that might indicate wall hacking
        float angleX = action.getViewAngleX();
        float angleY = action.getViewAngleY();
        
        // Simplified check: if looking at common enemy positions through walls
        return Math.abs(angleX) > 90 && Math.abs(angleY) < 30 && 
               action.getType() == PlayerAction.ActionType.AIM;
    }
    
    /**
     * Check if player is prefiring (simplified)
     */
    private boolean isPrefiring(PlayerAction action) {
        // This would check if shooting before enemy becomes visible
        // For demonstration, we'll use simplified timing logic
        
        // Check if shooting immediately after aiming (potential prefire)
        return action.getType() == PlayerAction.ActionType.SHOOT &&
               System.currentTimeMillis() - action.getTimestamp() < PREFIRE_TIME_THRESHOLD;
    }
    
    /**
     * Check if player is tracking enemies through walls (simplified)
     */
    private boolean isTrackingThroughWalls(PlayerAction action) {
        // This would analyze aim movement patterns relative to enemy positions
        // For demonstration, we'll use simplified logic
        
        // Check for smooth tracking movements that might indicate wallhack
        float deltaX = Math.abs(action.getViewAngleX());
        float deltaY = Math.abs(action.getViewAngleY());
        
        return deltaX > 5 && deltaX < 45 && deltaY < 10; // Horizontal tracking
    }
    
    /**
     * Update suspicious look rate based on recent events
     */
    private void updateSuspiciousLookRate(WallhackProfile profile) {
        // Clean old events
        long cutoffTime = System.currentTimeMillis() - 300000; // 5 minutes
        profile.eventHistory.removeIf(event -> event.timestamp < cutoffTime);
        
        // Calculate rate
        if (profile.eventHistory.size() > 0) {
            float totalConfidence = 0.0f;
            for (SuspiciousEvent event : profile.eventHistory) {
                totalConfidence += event.confidence;
            }
            profile.suspiciousLookRate = totalConfidence / profile.eventHistory.size();
        } else {
            profile.suspiciousLookRate *= 0.95f; // Decay rate
        }
    }
    
    /**
     * Clean up old profiles
     */
    public void cleanup() {
        playerProfiles.entrySet().removeIf(entry -> entry.getValue().isStale());
    }
    
    /**
     * Get wallhack statistics for a player
     */
    public Map<String, Object> getPlayerWallhackStats(String playerId) {
        WallhackProfile profile = playerProfiles.get(playerId);
        if (profile == null) return null;
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("impossibleKnowledgeCount", profile.impossibleKnowledgeCount);
        stats.put("prefireCount", profile.prefireCount);
        stats.put("wallTrackingCount", profile.wallTrackingCount);
        stats.put("suspiciousLookRate", profile.suspiciousLookRate);
        stats.put("eventCount", profile.eventHistory.size());
        stats.put("lastUpdate", profile.lastUpdateTime);
        
        return stats;
    }
}