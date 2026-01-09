package engine.network.protocol;

import engine.logging.LogManager;
import engine.network.NetworkConfiguration;
import engine.network.NetworkConnection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages reliable packet delivery with acknowledgments and retransmission.
 */
public class ReliabilityManager {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final NetworkConfiguration config;
    private final NetworkConnection connection;
    
    // Reliable packet tracking
    private final ConcurrentHashMap<Integer, PendingPacket> pendingPackets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> receivedPackets = new ConcurrentHashMap<>();
    
    // Sequence tracking
    private final AtomicInteger nextExpectedSequence = new AtomicInteger(1);
    private final AtomicLong lastCleanupTime = new AtomicLong(System.currentTimeMillis());
    
    // Statistics
    private final AtomicInteger packetsRetransmitted = new AtomicInteger(0);
    private final AtomicInteger packetsAcknowledged = new AtomicInteger(0);
    private final AtomicInteger duplicatePacketsReceived = new AtomicInteger(0);
    
    public ReliabilityManager(NetworkConfiguration config, NetworkConnection connection) {
        this.config = config;
        this.connection = connection;
    }
    
    /**
     * Add a packet that requires reliable delivery.
     */
    public void addPendingPacket(int sequenceNumber, byte[] packetData) {
        PendingPacket pendingPacket = new PendingPacket(sequenceNumber, packetData, System.currentTimeMillis());
        pendingPackets.put(sequenceNumber, pendingPacket);
        
        logManager.debug("ReliabilityManager", "Packet added for reliable delivery",
                        "sequenceNumber", sequenceNumber,
                        "connectionId", connection.getConnectionId());
    }
    
    /**
     * Handle acknowledgment for a reliable packet.
     */
    public void handleAcknowledgment(int sequenceNumber) {
        PendingPacket removed = pendingPackets.remove(sequenceNumber);
        
        if (removed != null) {
            packetsAcknowledged.incrementAndGet();
            
            logManager.debug("ReliabilityManager", "Packet acknowledged",
                           "sequenceNumber", sequenceNumber,
                           "rtt", System.currentTimeMillis() - removed.sentTime,
                           "connectionId", connection.getConnectionId());
        }
    }
    
    /**
     * Handle incoming reliable packet and check for duplicates.
     */
    public boolean handleIncomingReliablePacket(int sequenceNumber) {
        long currentTime = System.currentTimeMillis();
        
        // Check if we've already received this packet
        if (receivedPackets.containsKey(sequenceNumber)) {
            duplicatePacketsReceived.incrementAndGet();
            
            logManager.debug("ReliabilityManager", "Duplicate packet received",
                           "sequenceNumber", sequenceNumber,
                           "connectionId", connection.getConnectionId());
            return false; // Duplicate
        }
        
        // Mark packet as received
        receivedPackets.put(sequenceNumber, currentTime);
        
        // Update expected sequence if this is the next one
        if (sequenceNumber == nextExpectedSequence.get()) {
            nextExpectedSequence.incrementAndGet();
        }
        
        // Cleanup old received packets periodically
        if (currentTime - lastCleanupTime.get() > 30000) { // Every 30 seconds
            cleanupOldReceivedPackets(currentTime);
            lastCleanupTime.set(currentTime);
        }
        
        return true; // New packet
    }
    
    /**
     * Update reliability manager - handle retransmissions.
     */
    public void update(long deltaTime) {
        long currentTime = System.currentTimeMillis();
        
        // Check for packets that need retransmission
        for (PendingPacket packet : pendingPackets.values()) {
            if (currentTime - packet.lastSentTime > config.getRetransmissionTimeoutMs()) {
                if (packet.retransmissionCount < config.getMaxRetransmissions()) {
                    retransmitPacket(packet);
                } else {
                    // Max retransmissions reached, remove packet
                    pendingPackets.remove(packet.sequenceNumber);
                    
                    logManager.warn("ReliabilityManager", "Packet dropped after max retransmissions",
                                   "sequenceNumber", packet.sequenceNumber,
                                   "retransmissions", packet.retransmissionCount,
                                   "connectionId", connection.getConnectionId());
                }
            }
        }
    }
    
    /**
     * Retransmit a packet.
     */
    private void retransmitPacket(PendingPacket packet) {
        try {
            connection.sendPacket(packet.data);
            packet.lastSentTime = System.currentTimeMillis();
            packet.retransmissionCount++;
            packetsRetransmitted.incrementAndGet();
            
            logManager.debug("ReliabilityManager", "Packet retransmitted",
                           "sequenceNumber", packet.sequenceNumber,
                           "attempt", packet.retransmissionCount,
                           "connectionId", connection.getConnectionId());
            
        } catch (Exception e) {
            logManager.error("ReliabilityManager", "Failed to retransmit packet", e,
                           "sequenceNumber", packet.sequenceNumber,
                           "connectionId", connection.getConnectionId());
        }
    }
    
    /**
     * Clean up old received packet records to prevent memory leaks.
     */
    private void cleanupOldReceivedPackets(long currentTime) {
        final long maxAge = 60000; // Keep records for 1 minute
        
        receivedPackets.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > maxAge);
        
        logManager.debug("ReliabilityManager", "Cleaned up old received packet records",
                        "remainingRecords", receivedPackets.size(),
                        "connectionId", connection.getConnectionId());
    }
    
    /**
     * Get reliability statistics.
     */
    public ReliabilityStats getStatistics() {
        return new ReliabilityStats(
            pendingPackets.size(),
            packetsAcknowledged.get(),
            packetsRetransmitted.get(),
            duplicatePacketsReceived.get(),
            receivedPackets.size()
        );
    }
    
    /**
     * Clear all pending packets (used when connection closes).
     */
    public void clear() {
        pendingPackets.clear();
        receivedPackets.clear();
        
        logManager.debug("ReliabilityManager", "Reliability manager cleared",
                        "connectionId", connection.getConnectionId());
    }
    
    /**
     * Represents a packet waiting for acknowledgment.
     */
    private static class PendingPacket {
        final int sequenceNumber;
        final byte[] data;
        final long sentTime;
        long lastSentTime;
        int retransmissionCount;
        
        PendingPacket(int sequenceNumber, byte[] data, long sentTime) {
            this.sequenceNumber = sequenceNumber;
            this.data = data.clone(); // Defensive copy
            this.sentTime = sentTime;
            this.lastSentTime = sentTime;
            this.retransmissionCount = 0;
        }
    }
    
    /**
     * Reliability statistics.
     */
    public static class ReliabilityStats {
        public final int pendingPackets;
        public final int acknowledgedPackets;
        public final int retransmittedPackets;
        public final int duplicatePackets;
        public final int receivedPacketRecords;
        
        ReliabilityStats(int pendingPackets, int acknowledgedPackets, 
                        int retransmittedPackets, int duplicatePackets, 
                        int receivedPacketRecords) {
            this.pendingPackets = pendingPackets;
            this.acknowledgedPackets = acknowledgedPackets;
            this.retransmittedPackets = retransmittedPackets;
            this.duplicatePackets = duplicatePackets;
            this.receivedPacketRecords = receivedPacketRecords;
        }
        
        @Override
        public String toString() {
            return "ReliabilityStats{" +
                   "pending=" + pendingPackets +
                   ", acked=" + acknowledgedPackets +
                   ", retransmitted=" + retransmittedPackets +
                   ", duplicates=" + duplicatePackets +
                   '}';
        }
    }
}