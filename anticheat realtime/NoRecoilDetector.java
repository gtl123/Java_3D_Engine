package fps.anticheat.realtime;

import fps.anticheat.*;
import java.util.*;

/**
 * Detects no-recoil cheats by analyzing weapon recoil patterns.
 * Identifies unnatural recoil compensation and perfect spray control.
 */
public class NoRecoilDetector {
    
    private final AntiCheatConfiguration config;
    private final Map<String, NoRecoilProfile> playerProfiles;
    
    private static final float EXPECTED_RECOIL_VARIANCE = 0.1f;
    private static final float PERFECT_COMPENSATION_THRESHOLD = 0.02f;
    
    private static class NoRecoilProfile {
        Queue<Float> recoilPatterns;
        float compensationAccuracy;
        int perfectCompensationCount;
        long lastUpdateTime;
        
        NoRecoilProfile() {
            this.recoilPatterns = new LinkedList<>();
            this.compensationAccuracy = 0.5f;
            this.perfectCompensationCount = 0;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        boolean isStale() {
            return System.currentTimeMillis() - lastUpdateTime > 1800000;
        }
    }
    
    public NoRecoilDetector(AntiCheatConfiguration config) {
        this.config = config;
        this.playerProfiles = new HashMap<>();
    }
    
    public List<CheatDetection> detectNoRecoil(PlayerAction action, PlayerRealtimeProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        if (action.getType() != PlayerAction.ActionType.SHOOT) {
            return detections;
        }
        
        NoRecoilProfile recoilProfile = playerProfiles.computeIfAbsent(action.getPlayerId(), k -> new NoRecoilProfile());
        
        // Analyze recoil compensation
        float recoilCompensation = analyzeRecoilCompensation(action, profile);
        
        if (recoilCompensation >= 0) {
            recoilProfile.recoilPatterns.offer(recoilCompensation);
            if (recoilProfile.recoilPatterns.size() > 15) {
                recoilProfile.recoilPatterns.poll();
            }
            
            detections.addAll(checkPerfectCompensation(action.getPlayerId(), recoilProfile, recoilCompensation));
        }
        
        recoilProfile.lastUpdateTime = System.currentTimeMillis();
        return detections;
    }
    
    private float analyzeRecoilCompensation(PlayerAction action, PlayerRealtimeProfile profile) {
        // Simplified recoil analysis - would integrate with weapon system
        return (float)(Math.random() * 0.2f); // Simulate compensation accuracy
    }
    
    private List<CheatDetection> checkPerfectCompensation(String playerId, NoRecoilProfile profile, float compensation) {
        List<CheatDetection> detections = new ArrayList<>();
        
        if (compensation < PERFECT_COMPENSATION_THRESHOLD) {
            profile.perfectCompensationCount++;
            
            if (profile.perfectCompensationCount >= 5) {
                float confidence = Math.min(1.0f, profile.perfectCompensationCount / 10.0f);
                
                CheatDetection detection = new CheatDetection(
                    ViolationType.NO_RECOIL,
                    String.format("Perfect recoil compensation detected (%d instances)", profile.perfectCompensationCount),
                    confidence,
                    "NoRecoilDetector"
                );
                
                detection.addEvidence(String.format("Perfect compensations: %d, Accuracy: %.4f", 
                                                   profile.perfectCompensationCount, compensation));
                detections.add(detection);
            }
        }
        
        return detections;
    }
    
    public void cleanup() {
        playerProfiles.entrySet().removeIf(entry -> entry.getValue().isStale());
    }
}