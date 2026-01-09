package fps.anticheat.integration;

import fps.core.GameEngine;
import fps.core.GameState;
import fps.core.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors game state for anomalies and suspicious patterns that might indicate cheating.
 */
public class GameStateMonitor {
    
    private final AntiCheatIntegrationManager integrationManager;
    private volatile boolean enabled = false;
    
    // Game state tracking
    private final Map<String, PlayerGameState> playerGameStates;
    private GameState lastGameState;
    private long lastMonitoringTime;
    
    // Anomaly detection thresholds
    private static final double MAX_SCORE_INCREASE_RATE = 100.0; // points per minute
    private static final double MAX_KILL_DEATH_RATIO = 10.0;
    private static final double MAX_ACCURACY_PERCENTAGE = 95.0;
    private static final int MAX_CONSECUTIVE_HEADSHOTS = 10;
    
    public GameStateMonitor(AntiCheatIntegrationManager integrationManager) {
        this.integrationManager = integrationManager;
        this.playerGameStates = new ConcurrentHashMap<>();
        this.lastMonitoringTime = System.currentTimeMillis();
    }
    
    /**
     * Perform periodic game state monitoring
     */
    public void performMonitoring() {
        if (!enabled) return;
        
        try {
            GameEngine gameEngine = integrationManager.getGameEngine();
            GameState currentGameState = gameEngine.getGameState();
            
            if (currentGameState != null) {
                monitorGameState(currentGameState);
                monitorPlayerStates(currentGameState);
                
                lastGameState = currentGameState;
                lastMonitoringTime = System.currentTimeMillis();
            }
            
        } catch (Exception e) {
            System.err.println("Error during game state monitoring: " + e.getMessage());
        }
    }
    
    /**
     * Monitor overall game state for anomalies
     */
    private void monitorGameState(GameState gameState) {
        // Check for unusual game state changes
        if (lastGameState != null) {
            // Monitor score progression
            checkScoreProgression(gameState);
            
            // Monitor player count changes
            checkPlayerCountChanges(gameState);
            
            // Monitor game duration anomalies
            checkGameDurationAnomalies(gameState);
        }
    }
    
    /**
     * Monitor individual player states
     */
    private void monitorPlayerStates(GameState gameState) {
        Collection<Player> players = gameState.getPlayers();
        
        for (Player player : players) {
            String playerId = player.getId();
            
            // Get or create player game state
            PlayerGameState playerState = playerGameStates.computeIfAbsent(
                playerId, k -> new PlayerGameState(playerId));
            
            // Update player state
            playerState.update(player, gameState);
            
            // Check for anomalies
            List<GameStateAnomaly> anomalies = detectPlayerAnomalies(player, playerState);
            
            // Handle detected anomalies
            for (GameStateAnomaly anomaly : anomalies) {
                handleAnomaly(player, anomaly);
            }
        }
    }
    
    /**
     * Check score progression for anomalies
     */
    private void checkScoreProgression(GameState gameState) {
        if (lastGameState == null) return;
        
        long timeDelta = gameState.getTimestamp() - lastGameState.getTimestamp();
        if (timeDelta <= 0) return;
        
        Collection<Player> players = gameState.getPlayers();
        
        for (Player player : players) {
            Player lastPlayer = lastGameState.getPlayer(player.getId());
            if (lastPlayer == null) continue;
            
            int scoreDelta = player.getScore() - lastPlayer.getScore();
            double scoreRate = (scoreDelta / (timeDelta / 60000.0)); // points per minute
            
            if (scoreRate > MAX_SCORE_INCREASE_RATE) {
                GameStateAnomaly anomaly = new GameStateAnomaly(
                    AnomalyType.EXCESSIVE_SCORE_RATE,
                    "Excessive score increase rate: " + String.format("%.2f", scoreRate) + " points/min",
                    0.6f
                );
                handleAnomaly(player, anomaly);
            }
        }
    }
    
    /**
     * Check for unusual player count changes
     */
    private void checkPlayerCountChanges(GameState gameState) {
        if (lastGameState == null) return;
        
        int currentPlayerCount = gameState.getPlayerCount();
        int lastPlayerCount = lastGameState.getPlayerCount();
        int playerCountDelta = Math.abs(currentPlayerCount - lastPlayerCount);
        
        // Check for mass disconnections (possible server attack or cheat detection)
        if (playerCountDelta > 5 && currentPlayerCount < lastPlayerCount) {
            // Mass disconnection detected - might indicate widespread cheating detection
            System.out.println("Mass disconnection detected: " + playerCountDelta + " players left");
        }
    }
    
    /**
     * Check for game duration anomalies
     */
    private void checkGameDurationAnomalies(GameState gameState) {
        long gameDuration = gameState.getGameDuration();
        
        // Check if game is ending too quickly (possible exploitation)
        if (gameState.isGameEnded() && gameDuration < 60000) { // Less than 1 minute
            System.out.println("Game ended suspiciously quickly: " + gameDuration + "ms");
        }
    }
    
    /**
     * Detect anomalies in individual player performance
     */
    private List<GameStateAnomaly> detectPlayerAnomalies(Player player, PlayerGameState playerState) {
        List<GameStateAnomaly> anomalies = new ArrayList<>();
        
        // Check kill/death ratio
        double kdRatio = playerState.getKillDeathRatio();
        if (kdRatio > MAX_KILL_DEATH_RATIO && playerState.getKills() > 10) {
            anomalies.add(new GameStateAnomaly(
                AnomalyType.EXCESSIVE_KD_RATIO,
                "Excessive K/D ratio: " + String.format("%.2f", kdRatio),
                0.5f
            ));
        }
        
        // Check accuracy percentage
        double accuracy = playerState.getAccuracyPercentage();
        if (accuracy > MAX_ACCURACY_PERCENTAGE && playerState.getShotsFired() > 50) {
            anomalies.add(new GameStateAnomaly(
                AnomalyType.EXCESSIVE_ACCURACY,
                "Excessive accuracy: " + String.format("%.1f%%", accuracy),
                0.7f
            ));
        }
        
        // Check consecutive headshots
        int consecutiveHeadshots = playerState.getConsecutiveHeadshots();
        if (consecutiveHeadshots > MAX_CONSECUTIVE_HEADSHOTS) {
            anomalies.add(new GameStateAnomaly(
                AnomalyType.EXCESSIVE_HEADSHOTS,
                "Excessive consecutive headshots: " + consecutiveHeadshots,
                0.8f
            ));
        }
        
        // Check for impossible performance improvements
        if (playerState.hasImpossibleImprovement()) {
            anomalies.add(new GameStateAnomaly(
                AnomalyType.IMPOSSIBLE_IMPROVEMENT,
                "Impossible performance improvement detected",
                0.6f
            ));
        }
        
        // Check for bot-like behavior patterns
        if (playerState.hasBotLikeBehavior()) {
            anomalies.add(new GameStateAnomaly(
                AnomalyType.BOT_LIKE_BEHAVIOR,
                "Bot-like behavior patterns detected",
                0.5f
            ));
        }
        
        return anomalies;
    }
    
    /**
     * Handle detected anomaly
     */
    private void handleAnomaly(Player player, GameStateAnomaly anomaly) {
        // Convert game state anomaly to validation result
        ViolationType violationType = mapAnomalyToViolationType(anomaly.getType());
        
        ValidationResult validationResult = new ValidationResult(
            false,
            violationType,
            anomaly.getSeverity(),
            anomaly.getDescription()
        );
        
        // Process through integration manager
        integrationManager.handleValidationResult(player, validationResult);
    }
    
    /**
     * Map anomaly type to violation type
     */
    private ViolationType mapAnomalyToViolationType(AnomalyType anomalyType) {
        switch (anomalyType) {
            case EXCESSIVE_ACCURACY:
                return ViolationType.AIMBOT;
            case EXCESSIVE_HEADSHOTS:
                return ViolationType.AIMBOT;
            case EXCESSIVE_KD_RATIO:
                return ViolationType.STATISTICAL_ANOMALY;
            case EXCESSIVE_SCORE_RATE:
                return ViolationType.STATISTICAL_ANOMALY;
            case IMPOSSIBLE_IMPROVEMENT:
                return ViolationType.STATISTICAL_ANOMALY;
            case BOT_LIKE_BEHAVIOR:
                return ViolationType.BEHAVIORAL_ANOMALY;
            default:
                return ViolationType.SUSPICIOUS_ACTIVITY;
        }
    }
    
    /**
     * Get monitoring statistics
     */
    public GameStateMonitoringStatistics getStatistics() {
        int totalAnomalies = 0;
        int totalPlayersMonitored = playerGameStates.size();
        
        Map<AnomalyType, Integer> anomalyCounts = new HashMap<>();
        
        for (PlayerGameState playerState : playerGameStates.values()) {
            totalAnomalies += playerState.getTotalAnomalies();
            
            for (Map.Entry<AnomalyType, Integer> entry : playerState.getAnomalyCounts().entrySet()) {
                anomalyCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        
        return new GameStateMonitoringStatistics(
            totalPlayersMonitored,
            totalAnomalies,
            anomalyCounts,
            System.currentTimeMillis() - lastMonitoringTime
        );
    }
    
    /**
     * Clean up old player states
     */
    public void cleanup() {
        long cutoffTime = System.currentTimeMillis() - (30 * 60 * 1000); // 30 minutes
        
        playerGameStates.entrySet().removeIf(entry -> 
            entry.getValue().getLastUpdate() < cutoffTime);
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Game state anomaly types
     */
    public enum AnomalyType {
        EXCESSIVE_ACCURACY,
        EXCESSIVE_HEADSHOTS,
        EXCESSIVE_KD_RATIO,
        EXCESSIVE_SCORE_RATE,
        IMPOSSIBLE_IMPROVEMENT,
        BOT_LIKE_BEHAVIOR
    }
    
    /**
     * Represents a detected game state anomaly
     */
    public static class GameStateAnomaly {
        private final AnomalyType type;
        private final String description;
        private final float severity;
        private final long timestamp;
        
        public GameStateAnomaly(AnomalyType type, String description, float severity) {
            this.type = type;
            this.description = description;
            this.severity = severity;
            this.timestamp = System.currentTimeMillis();
        }
        
        public AnomalyType getType() { return type; }
        public String getDescription() { return description; }
        public float getSeverity() { return severity; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Game state monitoring statistics
     */
    public static class GameStateMonitoringStatistics {
        private final int playersMonitored;
        private final int totalAnomalies;
        private final Map<AnomalyType, Integer> anomalyCounts;
        private final long lastMonitoringInterval;
        
        public GameStateMonitoringStatistics(int playersMonitored, int totalAnomalies,
                                           Map<AnomalyType, Integer> anomalyCounts,
                                           long lastMonitoringInterval) {
            this.playersMonitored = playersMonitored;
            this.totalAnomalies = totalAnomalies;
            this.anomalyCounts = new HashMap<>(anomalyCounts);
            this.lastMonitoringInterval = lastMonitoringInterval;
        }
        
        public int getPlayersMonitored() { return playersMonitored; }
        public int getTotalAnomalies() { return totalAnomalies; }
        public Map<AnomalyType, Integer> getAnomalyCounts() { return anomalyCounts; }
        public long getLastMonitoringInterval() { return lastMonitoringInterval; }
        
        @Override
        public String toString() {
            return String.format("GameStateMonitoringStatistics{players=%d, anomalies=%d, interval=%dms}", 
                               playersMonitored, totalAnomalies, lastMonitoringInterval);
        }
    }
}