package fps.anticheat.realtime;

import fps.anticheat.*;
import fps.anticheat.behavioral.BehavioralAnalysisEngine;
import fps.anticheat.statistical.StatisticalAnalysisEngine;

import java.util.*;
import java.util.concurrent.*;

/**
 * Real-time cheat detection system that monitors gameplay in real-time.
 * Coordinates multiple detection algorithms to identify cheating behavior as it happens.
 */
public class RealtimeCheatDetector {
    
    private final AntiCheatConfiguration config;
    private final Map<String, PlayerRealtimeProfile> playerProfiles;
    private final ExecutorService detectionExecutor;
    
    // Detection components
    private final AimbotDetector aimbotDetector;
    private final WallhackDetector wallhackDetector;
    private final SpeedHackDetector speedHackDetector;
    private final TriggerBotDetector triggerBotDetector;
    private final NoRecoilDetector noRecoilDetector;
    private final ESPDetector espDetector;
    
    // Analysis engines
    private final BehavioralAnalysisEngine behavioralEngine;
    private final StatisticalAnalysisEngine statisticalEngine;
    
    // Detection settings
    private static final int MAX_CONCURRENT_DETECTIONS = 10;
    private static final long DETECTION_INTERVAL_MS = 100; // 100ms intervals
    private static final int MAX_PLAYER_PROFILES = 1000;
    
    // Performance monitoring
    private long totalDetections;
    private long totalViolations;
    private final Map<String, Long> detectionCounts;
    
    public RealtimeCheatDetector(AntiCheatConfiguration config, 
                                BehavioralAnalysisEngine behavioralEngine,
                                StatisticalAnalysisEngine statisticalEngine) {
        this.config = config;
        this.behavioralEngine = behavioralEngine;
        this.statisticalEngine = statisticalEngine;
        
        this.playerProfiles = new ConcurrentHashMap<>();
        this.detectionExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_DETECTIONS);
        this.detectionCounts = new ConcurrentHashMap<>();
        
        // Initialize detection components
        this.aimbotDetector = new AimbotDetector(config);
        this.wallhackDetector = new WallhackDetector(config);
        this.speedHackDetector = new SpeedHackDetector(config);
        this.triggerBotDetector = new TriggerBotDetector(config);
        this.noRecoilDetector = new NoRecoilDetector(config);
        this.espDetector = new ESPDetector(config);
        
        this.totalDetections = 0;
        this.totalViolations = 0;
    }
    
    /**
     * Process a player action in real-time
     */
    public CompletableFuture<RealtimeDetectionResult> processPlayerAction(PlayerAction action) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                totalDetections++;
                
                // Get or create player profile
                PlayerRealtimeProfile profile = getOrCreatePlayerProfile(action.getPlayerId());
                
                // Update profile with new action
                profile.addAction(action);
                
                // Perform real-time detections
                List<CheatDetection> detections = performRealtimeDetections(action, profile);
                
                // Create detection result
                RealtimeDetectionResult result = new RealtimeDetectionResult(
                    action.getPlayerId(),
                    action.getTimestamp(),
                    detections,
                    profile.getSuspiciousScore()
                );
                
                // Update violation count if violations detected
                if (!detections.isEmpty()) {
                    totalViolations++;
                    profile.incrementViolationCount();
                }
                
                // Update detection counts
                updateDetectionCounts(detections);
                
                return result;
                
            } catch (Exception e) {
                // Return error result
                return new RealtimeDetectionResult(
                    action.getPlayerId(),
                    action.getTimestamp(),
                    Collections.singletonList(new CheatDetection(
                        ViolationType.SYSTEM_ERROR,
                        "Real-time detection error: " + e.getMessage(),
                        0.1f,
                        "RealtimeCheatDetector"
                    )),
                    0.1f
                );
            }
        }, detectionExecutor);
    }
    
    /**
     * Get or create player profile for real-time tracking
     */
    private PlayerRealtimeProfile getOrCreatePlayerProfile(String playerId) {
        return playerProfiles.computeIfAbsent(playerId, id -> {
            // Limit number of profiles to prevent memory issues
            if (playerProfiles.size() >= MAX_PLAYER_PROFILES) {
                cleanupOldProfiles();
            }
            return new PlayerRealtimeProfile(id);
        });
    }
    
    /**
     * Perform all real-time cheat detections
     */
    private List<CheatDetection> performRealtimeDetections(PlayerAction action, PlayerRealtimeProfile profile) {
        List<CheatDetection> detections = new ArrayList<>();
        
        // Aimbot detection
        List<CheatDetection> aimbotResults = aimbotDetector.detectAimbot(action, profile);
        detections.addAll(aimbotResults);
        
        // Wallhack detection
        List<CheatDetection> wallhackResults = wallhackDetector.detectWallhack(action, profile);
        detections.addAll(wallhackResults);
        
        // Speed hack detection
        List<CheatDetection> speedResults = speedHackDetector.detectSpeedHack(action, profile);
        detections.addAll(speedResults);
        
        // Trigger bot detection
        List<CheatDetection> triggerResults = triggerBotDetector.detectTriggerBot(action, profile);
        detections.addAll(triggerResults);
        
        // No recoil detection
        List<CheatDetection> recoilResults = noRecoilDetector.detectNoRecoil(action, profile);
        detections.addAll(recoilResults);
        
        // ESP detection
        List<CheatDetection> espResults = espDetector.detectESP(action, profile);
        detections.addAll(espResults);
        
        // Filter and prioritize detections
        return filterAndPrioritizeDetections(detections);
    }
    
    /**
     * Filter and prioritize detections based on confidence and severity
     */
    private List<CheatDetection> filterAndPrioritizeDetections(List<CheatDetection> detections) {
        // Filter out low-confidence detections
        List<CheatDetection> filtered = detections.stream()
                .filter(detection -> detection.getConfidence() >= config.getMinDetectionConfidence())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        // Sort by confidence (highest first)
        filtered.sort((d1, d2) -> Float.compare(d2.getConfidence(), d1.getConfidence()));
        
        // Limit number of detections to prevent spam
        int maxDetections = config.getMaxDetectionsPerAction();
        if (filtered.size() > maxDetections) {
            filtered = filtered.subList(0, maxDetections);
        }
        
        return filtered;
    }
    
    /**
     * Update detection counts for monitoring
     */
    private void updateDetectionCounts(List<CheatDetection> detections) {
        for (CheatDetection detection : detections) {
            String type = detection.getViolationType().name();
            detectionCounts.merge(type, 1L, Long::sum);
        }
    }
    
    /**
     * Process multiple actions in batch for efficiency
     */
    public CompletableFuture<List<RealtimeDetectionResult>> processBatchActions(List<PlayerAction> actions) {
        List<CompletableFuture<RealtimeDetectionResult>> futures = actions.stream()
                .map(this::processPlayerAction)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll));
    }
    
    /**
     * Get real-time statistics for a player
     */
    public PlayerRealtimeStats getPlayerStats(String playerId) {
        PlayerRealtimeProfile profile = playerProfiles.get(playerId);
        if (profile == null) {
            return null;
        }
        
        return new PlayerRealtimeStats(
            playerId,
            profile.getActionCount(),
            profile.getViolationCount(),
            profile.getSuspiciousScore(),
            profile.getLastActionTime(),
            profile.getDetectionHistory()
        );
    }
    
    /**
     * Get overall detection statistics
     */
    public RealtimeDetectionStats getDetectionStats() {
        return new RealtimeDetectionStats(
            totalDetections,
            totalViolations,
            playerProfiles.size(),
            new HashMap<>(detectionCounts),
            calculateDetectionRate(),
            getAverageProcessingTime()
        );
    }
    
    /**
     * Calculate detection rate (violations per detection)
     */
    private float calculateDetectionRate() {
        return totalDetections > 0 ? (float) totalViolations / totalDetections : 0.0f;
    }
    
    /**
     * Get average processing time (placeholder - would need actual timing)
     */
    private float getAverageProcessingTime() {
        // This would be calculated from actual timing measurements
        return 5.0f; // 5ms average
    }
    
    /**
     * Clean up old player profiles to prevent memory leaks
     */
    private void cleanupOldProfiles() {
        long cutoffTime = System.currentTimeMillis() - (30 * 60 * 1000); // 30 minutes
        
        playerProfiles.entrySet().removeIf(entry -> {
            PlayerRealtimeProfile profile = entry.getValue();
            return profile.getLastActionTime() < cutoffTime;
        });
    }
    
    /**
     * Perform periodic maintenance
     */
    public void performMaintenance() {
        // Clean up old profiles
        cleanupOldProfiles();
        
        // Clean up detector caches
        aimbotDetector.cleanup();
        wallhackDetector.cleanup();
        speedHackDetector.cleanup();
        triggerBotDetector.cleanup();
        noRecoilDetector.cleanup();
        espDetector.cleanup();
    }
    
    /**
     * Check if a player is currently flagged as suspicious
     */
    public boolean isPlayerSuspicious(String playerId) {
        PlayerRealtimeProfile profile = playerProfiles.get(playerId);
        return profile != null && profile.getSuspiciousScore() > config.getSuspiciousThreshold();
    }
    
    /**
     * Get suspicious players list
     */
    public List<String> getSuspiciousPlayers() {
        return playerProfiles.entrySet().stream()
                .filter(entry -> entry.getValue().getSuspiciousScore() > config.getSuspiciousThreshold())
                .map(Map.Entry::getKey)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Reset detection statistics
     */
    public void resetStatistics() {
        totalDetections = 0;
        totalViolations = 0;
        detectionCounts.clear();
    }
    
    /**
     * Shutdown the detector
     */
    public void shutdown() {
        detectionExecutor.shutdown();
        try {
            if (!detectionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                detectionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            detectionExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Check if detector is healthy and functioning
     */
    public boolean isHealthy() {
        return !detectionExecutor.isShutdown() && 
               !detectionExecutor.isTerminated() &&
               playerProfiles.size() < MAX_PLAYER_PROFILES;
    }
    
    /**
     * Get detector configuration
     */
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("maxConcurrentDetections", MAX_CONCURRENT_DETECTIONS);
        config.put("detectionIntervalMs", DETECTION_INTERVAL_MS);
        config.put("maxPlayerProfiles", MAX_PLAYER_PROFILES);
        config.put("minDetectionConfidence", this.config.getMinDetectionConfidence());
        config.put("maxDetectionsPerAction", this.config.getMaxDetectionsPerAction());
        config.put("suspiciousThreshold", this.config.getSuspiciousThreshold());
        return config;
    }
}