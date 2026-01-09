package fps.anticheat.realtime;

import fps.anticheat.*;
import java.util.*;

/**
 * Detects ESP (Extra Sensory Perception) cheats by analyzing player awareness patterns.
 * Identifies impossible information gathering and suspicious enemy detection.
 */
public class ESPDetector {
    
    private final AntiCheatConfiguration config;
    private final Map<String, ESPProfile> playerProfiles;
    
    private static final float IMPOSSIBLE_AWARENESS_THRESHOLD = 0.9f;
    private static final float SUSPICIOUS_DETECTION_RATE = 0.8f;
    
    private static class ESPProfile {
        int impossibleAwarenessCount;
        float enemyDetectionRate;
        Queue<AwarenessEvent> awarenessHistory;
        long lastUpdateTime;
        
        ESPProfile() {
            this.impossibleAwarenessCount = 0;
            this.enemyDetectionRate = 0.3f;
            this.awarenessHistory = new LinkedList<>();
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        boolean isStale() {
            return System.currentTimeMillis() - lastUpdateTime > 1800000;
        }
    }
    
    private static class AwarenessEvent {
        final boolean hadLineOfSight;
        final float confidence;
        final long timestamp;
        
        AwarenessEvent(boolean hadLineOfSight, float confidence) {
            this.hadLineOfSight = hadLineOfSight;
            this.confidence = confidence;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    public ESPDetector(AntiCheatConfiguration config) {
        this.config = config;
        this.playerProfiles = new HashMap<>();
    }
    
    public List<CheatDetection> detectESP(PlayerAction action, PlayerRealtimeProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        ESPProfile espProfile = playerProfiles.computeIfAbsent(action.getPlayerId(), k -> new ESPProfile());
        
        // Analyze awareness patterns
        if (action.getType() == PlayerAction.ActionType.AIM || action.getType() == PlayerAction.ActionType.SHOOT) {
            boolean hasLineOfSight = checkLineOfSight(action);
            float awarenessConfidence = calculateAwarenessConfidence(action, hasLineOfSight);
            
            AwarenessEvent event = new AwarenessEvent(hasLineOfSight, awarenessConfidence);
            espProfile.awarenessHistory.offer(event);
            
            if (espProfile.awarenessHistory.size() > 20) {
                espProfile.awarenessHistory.poll();
            }
            
            detections.addAll(checkImpossibleAwareness(action.getPlayerId(), espProfile, event));
        }
        
        espProfile.lastUpdateTime = System.currentTimeMillis();
        return detections;
    }
    
    private boolean checkLineOfSight(PlayerAction action) {
        // Simplified line of sight check - would integrate with game's visibility system
        return Math.random() > 0.3; // 70% chance of having line of sight
    }
    
    private float calculateAwarenessConfidence(PlayerAction action, boolean hasLineOfSight) {
        // Calculate how confident we are that the player should know about the target
        if (hasLineOfSight) {
            return 0.9f;
        } else {
            // Check for sound cues, teammate callouts, etc.
            return 0.2f; // Low confidence without line of sight
        }
    }
    
    private List<CheatDetection> checkImpossibleAwareness(String playerId, ESPProfile profile, AwarenessEvent event) {
        List<CheatDetection> detections = new ArrayList<>();
        
        if (!event.hadLineOfSight && event.confidence < 0.3f) {
            profile.impossibleAwarenessCount++;
            
            if (profile.impossibleAwarenessCount >= 3) {
                float confidence = Math.min(1.0f, profile.impossibleAwarenessCount / 8.0f);
                
                CheatDetection detection = new CheatDetection(
                    ViolationType.ESP,
                    String.format("Impossible enemy awareness detected (%d instances)", profile.impossibleAwarenessCount),
                    confidence,
                    "ESPDetector"
                );
                
                detection.addEvidence(String.format("Impossible awareness count: %d, No line of sight", 
                                                   profile.impossibleAwarenessCount));
                detection.addMetadata("impossibleAwarenessCount", profile.impossibleAwarenessCount);
                
                detections.add(detection);
            }
        }
        
        return detections;
    }
    
    public void cleanup() {
        playerProfiles.entrySet().removeIf(entry -> entry.getValue().isStale());
    }
}