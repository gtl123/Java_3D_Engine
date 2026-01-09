package fps.anticheat.server;

import engine.logging.LogManager;
import fps.anticheat.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates network-related aspects of player actions.
 * Detects network manipulation, timing attacks, and packet anomalies.
 */
public class NetworkValidator {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Configuration
    private AntiCheatConfiguration config;
    
    // Network constraints
    private float maxPing = 500.0f; // 500ms maximum ping
    private float maxPacketLoss = 0.1f; // 10% maximum packet loss
    private float maxJitter = 100.0f; // 100ms maximum jitter
    private long maxTimeDelta = 5000; // 5 second maximum time delta
    private long minTimeDelta = 1; // 1ms minimum time delta
    
    // Timing validation
    private float clockSkewTolerance = 1000.0f; // 1 second clock skew tolerance
    private float sequenceNumberTolerance = 100; // Allow 100 out-of-order packets
    
    // Player network state tracking
    private final Map<Integer, NetworkState> playerNetworkStates = new ConcurrentHashMap<>();
    
    public NetworkValidator() {
        logManager.debug("NetworkValidator", "Network validator created");
    }
    
    /**
     * Set configuration
     */
    public void setConfiguration(AntiCheatConfiguration config) {
        this.config = config;
        
        if (config != null) {
            // Update network constraints based on configuration
            // Configuration could include network-specific settings
        }
    }
    
    /**
     * Validate network aspects of an action
     */
    public ValidationResult validate(PlayerValidationState playerState, PlayerAction action) {
        try {
            int playerId = action.getPlayerId();
            
            // Get or create network state for player
            NetworkState networkState = getNetworkState(playerId);
            
            // Update network state with current action
            networkState.updateWithAction(action);
            
            // Validate timing
            ValidationResult timingResult = validateTiming(networkState, action);
            if (!timingResult.isValid()) {
                return timingResult;
            }
            
            // Validate sequence numbers
            ValidationResult sequenceResult = validateSequenceNumber(networkState, action);
            if (!sequenceResult.isValid()) {
                return sequenceResult;
            }
            
            // Validate network metrics
            ValidationResult metricsResult = validateNetworkMetrics(networkState, action);
            if (!metricsResult.isValid()) {
                return metricsResult;
            }
            
            // Validate packet patterns
            ValidationResult patternResult = validatePacketPatterns(networkState, action);
            if (!patternResult.isValid()) {
                return patternResult;
            }
            
            // Validate clock synchronization
            ValidationResult clockResult = validateClockSync(networkState, action);
            if (!clockResult.isValid()) {
                return clockResult;
            }
            
            return ValidationResult.allowed();
            
        } catch (Exception e) {
            logManager.error("NetworkValidator", "Error validating network", e);
            return ValidationResult.denied("Network validation error", ViolationType.SERVER_VALIDATION);
        }
    }
    
    /**
     * Validate action timing
     */
    private ValidationResult validateTiming(NetworkState networkState, PlayerAction action) {
        long serverTime = System.currentTimeMillis();
        long clientTime = action.getClientTimestamp();
        long actionTime = action.getTimestamp();
        
        // Validate time delta between client and server
        long timeDelta = Math.abs(serverTime - clientTime);
        
        if (timeDelta > maxTimeDelta) {
            return ValidationResult.denied(
                String.format("Time delta too large: %dms (max: %dms)", timeDelta, maxTimeDelta),
                ViolationType.TIMING_MANIPULATION,
                timeDelta
            );
        }
        
        // Validate minimum time between actions
        long lastActionTime = networkState.getLastActionTime();
        if (lastActionTime > 0) {
            long actionInterval = actionTime - lastActionTime;
            
            if (actionInterval < minTimeDelta) {
                return ValidationResult.denied(
                    String.format("Action interval too small: %dms (min: %dms)", 
                                 actionInterval, minTimeDelta),
                    ViolationType.TIMING_MANIPULATION,
                    actionInterval
                );
            }
        }
        
        // Check for time travel (actions from the future)
        if (clientTime > serverTime + clockSkewTolerance) {
            return ValidationResult.denied(
                String.format("Action from future: client=%d, server=%d", clientTime, serverTime),
                ViolationType.TIMING_MANIPULATION,
                clientTime - serverTime
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate sequence numbers for packet ordering
     */
    private ValidationResult validateSequenceNumber(NetworkState networkState, PlayerAction action) {
        int currentSequence = action.getSequenceNumber();
        int lastSequence = networkState.getLastSequenceNumber();
        
        // Skip validation for first packet
        if (lastSequence == 0) {
            return ValidationResult.allowed();
        }
        
        // Check for sequence number rollover (handle wraparound)
        int expectedSequence = lastSequence + 1;
        if (expectedSequence < 0) {
            expectedSequence = 0; // Handle integer overflow
        }
        
        // Allow some out-of-order packets due to network conditions
        int sequenceDelta = Math.abs(currentSequence - expectedSequence);
        
        if (sequenceDelta > sequenceNumberTolerance) {
            return ValidationResult.denied(
                String.format("Sequence number anomaly: current=%d, expected=%d, delta=%d", 
                             currentSequence, expectedSequence, sequenceDelta),
                ViolationType.PACKET_TAMPERING,
                sequenceDelta
            );
        }
        
        // Check for duplicate sequence numbers
        if (networkState.hasRecentSequenceNumber(currentSequence)) {
            return ValidationResult.denied(
                String.format("Duplicate sequence number: %d", currentSequence),
                ViolationType.PACKET_TAMPERING,
                currentSequence
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate network metrics (ping, packet loss, jitter)
     */
    private ValidationResult validateNetworkMetrics(NetworkState networkState, PlayerAction action) {
        float ping = action.getPing();
        float packetLoss = action.getPacketLoss();
        
        // Validate ping
        if (ping > maxPing) {
            return ValidationResult.denied(
                String.format("Ping too high: %.2fms (max: %.2fms)", ping, maxPing),
                ViolationType.NETWORK_MANIPULATION,
                ping
            );
        }
        
        if (ping < 0) {
            return ValidationResult.denied(
                String.format("Invalid ping: %.2fms", ping),
                ViolationType.NETWORK_MANIPULATION,
                ping
            );
        }
        
        // Validate packet loss
        if (packetLoss > maxPacketLoss) {
            return ValidationResult.denied(
                String.format("Packet loss too high: %.2f%% (max: %.2f%%)", 
                             packetLoss * 100, maxPacketLoss * 100),
                ViolationType.NETWORK_MANIPULATION,
                packetLoss
            );
        }
        
        if (packetLoss < 0 || packetLoss > 1.0f) {
            return ValidationResult.denied(
                String.format("Invalid packet loss: %.2f%%", packetLoss * 100),
                ViolationType.NETWORK_MANIPULATION,
                packetLoss
            );
        }
        
        // Validate jitter (ping variation)
        float jitter = networkState.calculateJitter(ping);
        if (jitter > maxJitter) {
            return ValidationResult.denied(
                String.format("Jitter too high: %.2fms (max: %.2fms)", jitter, maxJitter),
                ViolationType.NETWORK_MANIPULATION,
                jitter
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate packet patterns for anomalies
     */
    private ValidationResult validatePacketPatterns(NetworkState networkState, PlayerAction action) {
        // Check for perfectly consistent timing (indicating bot behavior)
        if (networkState.hasPerfectTiming()) {
            return ValidationResult.denied(
                "Perfect packet timing detected (possible bot)",
                ViolationType.BEHAVIORAL_ANALYSIS,
                networkState.getTimingVariance()
            );
        }
        
        // Check for impossible network conditions
        if (networkState.hasImpossibleNetworkConditions()) {
            return ValidationResult.denied(
                "Impossible network conditions detected",
                ViolationType.NETWORK_MANIPULATION,
                networkState.getAveragePing()
            );
        }
        
        // Check for packet burst patterns
        if (networkState.hasSuspiciousBurstPattern()) {
            return ValidationResult.denied(
                "Suspicious packet burst pattern detected",
                ViolationType.PACKET_TAMPERING,
                networkState.getBurstCount()
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate clock synchronization
     */
    private ValidationResult validateClockSync(NetworkState networkState, PlayerAction action) {
        long serverTime = System.currentTimeMillis();
        long clientTime = action.getClientTimestamp();
        
        // Calculate clock skew
        long clockSkew = serverTime - clientTime;
        networkState.updateClockSkew(clockSkew);
        
        // Check for sudden clock skew changes (indicating time manipulation)
        float skewVariance = networkState.getClockSkewVariance();
        if (skewVariance > clockSkewTolerance) {
            return ValidationResult.denied(
                String.format("Clock skew variance too high: %.2fms (max: %.2fms)", 
                             skewVariance, clockSkewTolerance),
                ViolationType.TIMING_MANIPULATION,
                skewVariance
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Get or create network state for player
     */
    private NetworkState getNetworkState(int playerId) {
        return playerNetworkStates.computeIfAbsent(playerId, NetworkState::new);
    }
    
    /**
     * Remove network state for player
     */
    public void removeNetworkState(int playerId) {
        playerNetworkStates.remove(playerId);
        
        logManager.debug("NetworkValidator", "Removed network state",
                        "playerId", playerId);
    }
    
    /**
     * Update validator
     */
    public void update(float deltaTime) {
        // Clean up old network states
        cleanupOldNetworkStates();
    }
    
    /**
     * Clean up old network states
     */
    private void cleanupOldNetworkStates() {
        long cutoffTime = System.currentTimeMillis() - 300000; // 5 minutes
        
        playerNetworkStates.entrySet().removeIf(entry -> {
            NetworkState state = entry.getValue();
            return state.getLastUpdateTime() < cutoffTime;
        });
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        playerNetworkStates.clear();
        logManager.debug("NetworkValidator", "Network validator cleaned up");
    }
    
    // Getters and setters
    public float getMaxPing() { return maxPing; }
    public void setMaxPing(float maxPing) { this.maxPing = maxPing; }
    
    public float getMaxPacketLoss() { return maxPacketLoss; }
    public void setMaxPacketLoss(float maxPacketLoss) { this.maxPacketLoss = maxPacketLoss; }
    
    public float getMaxJitter() { return maxJitter; }
    public void setMaxJitter(float maxJitter) { this.maxJitter = maxJitter; }
    
    public long getMaxTimeDelta() { return maxTimeDelta; }
    public void setMaxTimeDelta(long maxTimeDelta) { this.maxTimeDelta = maxTimeDelta; }
    
    public long getMinTimeDelta() { return minTimeDelta; }
    public void setMinTimeDelta(long minTimeDelta) { this.minTimeDelta = minTimeDelta; }
    
    public float getClockSkewTolerance() { return clockSkewTolerance; }
    public void setClockSkewTolerance(float clockSkewTolerance) { this.clockSkewTolerance = clockSkewTolerance; }
    
    /**
     * Network state tracking for individual players
     */
    private static class NetworkState {
        private final int playerId;
        private long lastActionTime = 0;
        private long lastUpdateTime = 0;
        private int lastSequenceNumber = 0;
        private final java.util.Deque<Integer> recentSequenceNumbers = new java.util.ArrayDeque<>();
        private final java.util.Deque<Float> pingHistory = new java.util.ArrayDeque<>();
        private final java.util.Deque<Long> clockSkewHistory = new java.util.ArrayDeque<>();
        private final java.util.Deque<Long> timingHistory = new java.util.ArrayDeque<>();
        private final int maxHistorySize = 50;
        
        public NetworkState(int playerId) {
            this.playerId = playerId;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        public void updateWithAction(PlayerAction action) {
            lastActionTime = action.getTimestamp();
            lastUpdateTime = System.currentTimeMillis();
            lastSequenceNumber = action.getSequenceNumber();
            
            // Update sequence number history
            recentSequenceNumbers.addLast(action.getSequenceNumber());
            if (recentSequenceNumbers.size() > maxHistorySize) {
                recentSequenceNumbers.removeFirst();
            }
            
            // Update ping history
            if (action.getPing() > 0) {
                pingHistory.addLast(action.getPing());
                if (pingHistory.size() > maxHistorySize) {
                    pingHistory.removeFirst();
                }
            }
            
            // Update timing history
            timingHistory.addLast(action.getTimestamp());
            if (timingHistory.size() > maxHistorySize) {
                timingHistory.removeFirst();
            }
        }
        
        public boolean hasRecentSequenceNumber(int sequenceNumber) {
            return recentSequenceNumbers.contains(sequenceNumber);
        }
        
        public float calculateJitter(float currentPing) {
            if (pingHistory.size() < 2) {
                return 0.0f;
            }
            
            float sum = 0.0f;
            float mean = (float) pingHistory.stream().mapToDouble(Float::doubleValue).average().orElse(0.0);
            
            for (float ping : pingHistory) {
                sum += Math.abs(ping - mean);
            }
            
            return sum / pingHistory.size();
        }
        
        public void updateClockSkew(long clockSkew) {
            clockSkewHistory.addLast(clockSkew);
            if (clockSkewHistory.size() > maxHistorySize) {
                clockSkewHistory.removeFirst();
            }
        }
        
        public float getClockSkewVariance() {
            if (clockSkewHistory.size() < 2) {
                return 0.0f;
            }
            
            double mean = clockSkewHistory.stream().mapToLong(Long::longValue).average().orElse(0.0);
            double variance = clockSkewHistory.stream()
                .mapToDouble(skew -> Math.pow(skew - mean, 2))
                .average().orElse(0.0);
            
            return (float) Math.sqrt(variance);
        }
        
        public boolean hasPerfectTiming() {
            if (timingHistory.size() < 10) {
                return false;
            }
            
            // Check if timing intervals are too consistent
            return getTimingVariance() < 1.0f; // Less than 1ms variance
        }
        
        public float getTimingVariance() {
            if (timingHistory.size() < 2) {
                return Float.MAX_VALUE;
            }
            
            java.util.List<Long> intervals = new java.util.ArrayList<>();
            Long previous = null;
            
            for (Long timestamp : timingHistory) {
                if (previous != null) {
                    intervals.add(timestamp - previous);
                }
                previous = timestamp;
            }
            
            if (intervals.isEmpty()) {
                return Float.MAX_VALUE;
            }
            
            double mean = intervals.stream().mapToLong(Long::longValue).average().orElse(0.0);
            double variance = intervals.stream()
                .mapToDouble(interval -> Math.pow(interval - mean, 2))
                .average().orElse(0.0);
            
            return (float) Math.sqrt(variance);
        }
        
        public boolean hasImpossibleNetworkConditions() {
            // Check for impossible combinations (e.g., 0ms ping with high packet loss)
            if (pingHistory.size() > 5) {
                float avgPing = (float) pingHistory.stream().mapToDouble(Float::doubleValue).average().orElse(0.0);
                return avgPing < 1.0f; // Impossible to have sub-1ms ping consistently
            }
            return false;
        }
        
        public boolean hasSuspiciousBurstPattern() {
            // Check for unusual packet burst patterns
            return getBurstCount() > 20; // More than 20 packets in rapid succession
        }
        
        public int getBurstCount() {
            if (timingHistory.size() < 2) {
                return 0;
            }
            
            int burstCount = 0;
            int currentBurst = 1;
            Long previous = null;
            
            for (Long timestamp : timingHistory) {
                if (previous != null) {
                    if (timestamp - previous < 10) { // Less than 10ms between packets
                        currentBurst++;
                    } else {
                        burstCount = Math.max(burstCount, currentBurst);
                        currentBurst = 1;
                    }
                }
                previous = timestamp;
            }
            
            return Math.max(burstCount, currentBurst);
        }
        
        public float getAveragePing() {
            return (float) pingHistory.stream().mapToDouble(Float::doubleValue).average().orElse(0.0);
        }
        
        // Getters
        public long getLastActionTime() { return lastActionTime; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        public int getLastSequenceNumber() { return lastSequenceNumber; }
    }
}