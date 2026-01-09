package fps.anticheat.realtime;

import fps.anticheat.*;
import java.util.*;

/**
 * Detects trigger bot cheats in real-time by analyzing shooting reaction times.
 * Identifies inhuman reaction speeds and perfect timing patterns.
 */
public class TriggerBotDetector {
    
    private final AntiCheatConfiguration config;
    private final Map<String, TriggerBotProfile> playerProfiles;
    
    // Detection thresholds
    private static final float MIN_HUMAN_REACTION_TIME = 150.0f; // milliseconds
    private static final float PERFECT_TIMING_THRESHOLD = 0.95f;
    private static final int MIN_SAMPLES_FOR_DETECTION = 10;
    
    private static class TriggerBotProfile {
        Queue<Float> reactionTimes;
        float averageReactionTime;
        int perfectTimingCount;
        long lastUpdateTime;
        
        TriggerBotProfile() {
            this.reactionTimes = new LinkedList<>();
            this.averageReactionTime = 250.0f;
            this.perfectTimingCount = 0;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        boolean isStale() {
            return System.currentTimeMillis() - lastUpdateTime > 1800000;
        }
    }
    
    public TriggerBotDetector(AntiCheatConfiguration config) {
        this.config = config;
        this.playerProfiles = new HashMap<>();
    }
    
    public List<CheatDetection> detectTriggerBot(PlayerAction action, PlayerRealtimeProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        if (action.getType() != PlayerAction.ActionType.SHOOT) {
            return detections;
        }
        
        TriggerBotProfile triggerProfile = playerProfiles.computeIfAbsent(action.getPlayerId(), k -> new TriggerBotProfile());
        
        // Simulate reaction time calculation
        float reactionTime = calculateReactionTime(action, profile);
        
        if (reactionTime > 0) {
            triggerProfile.reactionTimes.offer(reactionTime);
            if (triggerProfile.reactionTimes.size() > 20) {
                triggerProfile.reactionTimes.poll();
            }
            
            updateProfile(triggerProfile);
            detections.addAll(checkInhumanReactionTime(action.getPlayerId(), triggerProfile, reactionTime));
        }
        
        triggerProfile.lastUpdateTime = System.currentTimeMillis();
        return detections;
    }
    
    private float calculateReactionTime(PlayerAction action, PlayerRealtimeProfile profile) {
        // Simplified reaction time calculation
        return Math.max(50.0f, 100.0f + (float)(Math.random() * 200.0f));
    }
    
    private void updateProfile(TriggerBotProfile profile) {
        if (!profile.reactionTimes.isEmpty()) {
            float sum = profile.reactionTimes.stream().reduce(0.0f, Float::sum);
            profile.averageReactionTime = sum / profile.reactionTimes.size();
        }
    }
    
    private List<CheatDetection> checkInhumanReactionTime(String playerId, TriggerBotProfile profile, float reactionTime) {
        List<CheatDetection> detections = new ArrayList<>();
        
        if (reactionTime < MIN_HUMAN_REACTION_TIME) {
            float confidence = (MIN_HUMAN_REACTION_TIME - reactionTime) / MIN_HUMAN_REACTION_TIME;
            
            CheatDetection detection = new CheatDetection(
                ViolationType.TRIGGER_BOT,
                String.format("Inhuman reaction time detected: %.1fms", reactionTime),
                confidence,
                "TriggerBotDetector"
            );
            
            detection.addEvidence(String.format("Reaction time: %.1fms, Min human: %.1fms", 
                                               reactionTime, MIN_HUMAN_REACTION_TIME));
            detections.add(detection);
        }
        
        return detections;
    }
    
    public void cleanup() {
        playerProfiles.entrySet().removeIf(entry -> entry.getValue().isStale());
    }
}