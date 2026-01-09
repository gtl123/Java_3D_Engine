package fps.anticheat.integration;

import fps.networking.packets.PacketType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks packet frequency for individual players to detect flooding and suspicious patterns.
 */
public class PacketFrequencyTracker {
    
    private final String playerId;
    private final Map<PacketType, PacketTypeTracker> packetTrackers;
    private long lastActivity;
    
    // Time windows for frequency calculation
    private static final long SHORT_WINDOW = 1000; // 1 second
    private static final long MEDIUM_WINDOW = 5000; // 5 seconds
    private static final long LONG_WINDOW = 30000; // 30 seconds
    
    public PacketFrequencyTracker(String playerId) {
        this.playerId = playerId;
        this.packetTrackers = new ConcurrentHashMap<>();
        this.lastActivity = System.currentTimeMillis();
    }
    
    /**
     * Record a packet of the specified type
     */
    public void recordPacket(PacketType packetType) {
        lastActivity = System.currentTimeMillis();
        
        PacketTypeTracker tracker = packetTrackers.computeIfAbsent(
            packetType, k -> new PacketTypeTracker());
        
        tracker.recordPacket(lastActivity);
    }
    
    /**
     * Get packet rate for specific packet type (packets per second)
     */
    public int getPacketRate(PacketType packetType) {
        PacketTypeTracker tracker = packetTrackers.get(packetType);
        if (tracker == null) return 0;
        
        return tracker.getPacketRate(SHORT_WINDOW);
    }
    
    /**
     * Get packet rate for specific packet type over custom time window
     */
    public int getPacketRate(PacketType packetType, long timeWindow) {
        PacketTypeTracker tracker = packetTrackers.get(packetType);
        if (tracker == null) return 0;
        
        return tracker.getPacketRate(timeWindow);
    }
    
    /**
     * Get total packet rate across all packet types
     */
    public int getTotalPacketRate() {
        return packetTrackers.values().stream()
                .mapToInt(tracker -> tracker.getPacketRate(SHORT_WINDOW))
                .sum();
    }
    
    /**
     * Check if player is packet flooding
     */
    public boolean isPacketFlooding() {
        int totalRate = getTotalPacketRate();
        
        // Different thresholds for different time windows
        int shortWindowRate = getTotalPacketRate(SHORT_WINDOW);
        int mediumWindowRate = getTotalPacketRate(MEDIUM_WINDOW);
        int longWindowRate = getTotalPacketRate(LONG_WINDOW);
        
        // Immediate flooding (very high rate in short window)
        if (shortWindowRate > 200) return true;
        
        // Sustained flooding (high rate over medium window)
        if (mediumWindowRate > 150) return true;
        
        // Long-term flooding (moderate but sustained rate)
        if (longWindowRate > 100) return true;
        
        return false;
    }
    
    /**
     * Get total packet rate over custom time window
     */
    private int getTotalPacketRate(long timeWindow) {
        return packetTrackers.values().stream()
                .mapToInt(tracker -> tracker.getPacketRate(timeWindow))
                .sum();
    }
    
    /**
     * Check for suspicious packet patterns
     */
    public List<String> getSuspiciousPatterns() {
        List<String> patterns = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<PacketType, PacketTypeTracker> entry : packetTrackers.entrySet()) {
            PacketType type = entry.getKey();
            PacketTypeTracker tracker = entry.getValue();
            
            // Check for burst patterns (many packets in very short time)
            if (tracker.hasBurstPattern()) {
                patterns.add("Burst pattern detected for " + type.name());
            }
            
            // Check for regular intervals (bot-like behavior)
            if (tracker.hasRegularIntervals()) {
                patterns.add("Regular interval pattern detected for " + type.name());
            }
            
            // Check for unusual frequency spikes
            int shortRate = tracker.getPacketRate(SHORT_WINDOW);
            int mediumRate = tracker.getPacketRate(MEDIUM_WINDOW);
            
            if (shortRate > mediumRate * 3 && shortRate > 10) {
                patterns.add("Frequency spike detected for " + type.name() + 
                           " (short: " + shortRate + ", medium: " + mediumRate + ")");
            }
        }
        
        return patterns;
    }
    
    /**
     * Get packet statistics
     */
    public PacketStatistics getStatistics() {
        Map<PacketType, Integer> packetCounts = new HashMap<>();
        Map<PacketType, Integer> packetRates = new HashMap<>();
        
        for (Map.Entry<PacketType, PacketTypeTracker> entry : packetTrackers.entrySet()) {
            PacketType type = entry.getKey();
            PacketTypeTracker tracker = entry.getValue();
            
            packetCounts.put(type, tracker.getTotalPackets());
            packetRates.put(type, tracker.getPacketRate(SHORT_WINDOW));
        }
        
        return new PacketStatistics(playerId, packetCounts, packetRates, 
                                  getTotalPacketRate(), getSuspiciousPatterns());
    }
    
    /**
     * Clean up old packet records
     */
    public void cleanup() {
        long cutoffTime = System.currentTimeMillis() - LONG_WINDOW;
        
        for (PacketTypeTracker tracker : packetTrackers.values()) {
            tracker.cleanup(cutoffTime);
        }
        
        // Remove trackers with no recent packets
        packetTrackers.entrySet().removeIf(entry -> 
            entry.getValue().getTotalPackets() == 0);
    }
    
    public String getPlayerId() {
        return playerId;
    }
    
    public long getLastActivity() {
        return lastActivity;
    }
    
    /**
     * Tracker for individual packet types
     */
    private static class PacketTypeTracker {
        private final List<Long> packetTimestamps;
        private int totalPackets;
        
        public PacketTypeTracker() {
            this.packetTimestamps = new ArrayList<>();
            this.totalPackets = 0;
        }
        
        public synchronized void recordPacket(long timestamp) {
            packetTimestamps.add(timestamp);
            totalPackets++;
            
            // Keep only recent timestamps to prevent memory issues
            if (packetTimestamps.size() > 1000) {
                packetTimestamps.subList(0, packetTimestamps.size() - 500).clear();
            }
        }
        
        public synchronized int getPacketRate(long timeWindow) {
            long cutoffTime = System.currentTimeMillis() - timeWindow;
            
            return (int) packetTimestamps.stream()
                    .filter(timestamp -> timestamp >= cutoffTime)
                    .count();
        }
        
        public synchronized boolean hasBurstPattern() {
            if (packetTimestamps.size() < 10) return false;
            
            long currentTime = System.currentTimeMillis();
            long burstWindow = 100; // 100ms window
            
            // Check for 10+ packets in 100ms window
            long burstStart = currentTime - burstWindow;
            long burstCount = packetTimestamps.stream()
                    .filter(timestamp -> timestamp >= burstStart)
                    .count();
            
            return burstCount >= 10;
        }
        
        public synchronized boolean hasRegularIntervals() {
            if (packetTimestamps.size() < 20) return false;
            
            // Check last 20 packets for regular intervals
            List<Long> recentTimestamps = packetTimestamps.subList(
                Math.max(0, packetTimestamps.size() - 20), packetTimestamps.size());
            
            if (recentTimestamps.size() < 20) return false;
            
            // Calculate intervals between consecutive packets
            List<Long> intervals = new ArrayList<>();
            for (int i = 1; i < recentTimestamps.size(); i++) {
                intervals.add(recentTimestamps.get(i) - recentTimestamps.get(i - 1));
            }
            
            // Check if intervals are suspiciously regular (variance < 10ms)
            double avgInterval = intervals.stream().mapToLong(Long::longValue).average().orElse(0.0);
            double variance = intervals.stream()
                    .mapToDouble(interval -> Math.pow(interval - avgInterval, 2))
                    .average().orElse(0.0);
            
            return variance < 100.0 && avgInterval > 10.0; // Regular intervals with low variance
        }
        
        public synchronized void cleanup(long cutoffTime) {
            packetTimestamps.removeIf(timestamp -> timestamp < cutoffTime);
        }
        
        public int getTotalPackets() {
            return totalPackets;
        }
    }
    
    /**
     * Packet statistics for a player
     */
    public static class PacketStatistics {
        private final String playerId;
        private final Map<PacketType, Integer> packetCounts;
        private final Map<PacketType, Integer> packetRates;
        private final int totalRate;
        private final List<String> suspiciousPatterns;
        
        public PacketStatistics(String playerId, Map<PacketType, Integer> packetCounts,
                              Map<PacketType, Integer> packetRates, int totalRate,
                              List<String> suspiciousPatterns) {
            this.playerId = playerId;
            this.packetCounts = new HashMap<>(packetCounts);
            this.packetRates = new HashMap<>(packetRates);
            this.totalRate = totalRate;
            this.suspiciousPatterns = new ArrayList<>(suspiciousPatterns);
        }
        
        public String getPlayerId() { return playerId; }
        public Map<PacketType, Integer> getPacketCounts() { return packetCounts; }
        public Map<PacketType, Integer> getPacketRates() { return packetRates; }
        public int getTotalRate() { return totalRate; }
        public List<String> getSuspiciousPatterns() { return suspiciousPatterns; }
        
        public boolean hasSuspiciousActivity() {
            return !suspiciousPatterns.isEmpty() || totalRate > 100;
        }
        
        @Override
        public String toString() {
            return String.format("PacketStatistics{playerId='%s', totalRate=%d, patterns=%d}", 
                               playerId, totalRate, suspiciousPatterns.size());
        }
    }
}