package engine.network.protocol;

import engine.logging.LogManager;
import engine.network.NetworkConfiguration;
import engine.network.NetworkConnection;
import engine.network.NetworkException;
import engine.network.security.NetworkSecurity;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

/**
 * Custom binary network protocol with reliability layer.
 * Handles message types, sequencing, acknowledgments, and retransmission.
 */
public class NetworkProtocol {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Protocol constants
    private static final byte PROTOCOL_VERSION = 1;
    private static final int HEADER_SIZE = 16; // bytes
    private static final int MAX_PAYLOAD_SIZE = 1400 - HEADER_SIZE; // MTU safe
    
    // Message types
    public enum MessageType {
        HEARTBEAT(0x01),
        CONNECT(0x02),
        DISCONNECT(0x03),
        ENTITY_UPDATE(0x10),
        PLAYER_INPUT(0x11),
        GAME_EVENT(0x12),
        CHAT_MESSAGE(0x13),
        WORLD_DATA(0x14),
        ACK(0x20),
        NACK(0x21);
        
        private final byte value;
        
        MessageType(int value) {
            this.value = (byte) value;
        }
        
        public byte getValue() { return value; }
        
        public static MessageType fromByte(byte value) {
            for (MessageType type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown message type: " + value);
        }
    }
    
    // Reliability flags
    public static final byte FLAG_RELIABLE = 0x01;
    public static final byte FLAG_ORDERED = 0x02;
    public static final byte FLAG_COMPRESSED = 0x04;
    public static final byte FLAG_ENCRYPTED = 0x08;
    
    private final NetworkConfiguration config;
    private final NetworkSecurity security;
    private final AtomicInteger sequenceCounter = new AtomicInteger(0);
    
    // Reliability tracking
    private final ConcurrentHashMap<String, ReliabilityManager> reliabilityManagers = new ConcurrentHashMap<>();
    
    private volatile boolean initialized = false;
    
    public NetworkProtocol(NetworkConfiguration config, NetworkSecurity security) {
        this.config = config;
        this.security = security;
    }
    
    /**
     * Initialize the network protocol.
     */
    public void initialize() throws NetworkException {
        if (initialized) {
            return;
        }
        
        logManager.info("NetworkProtocol", "Network protocol initialized",
                       "version", PROTOCOL_VERSION,
                       "headerSize", HEADER_SIZE,
                       "maxPayloadSize", MAX_PAYLOAD_SIZE);
        
        initialized = true;
    }
    
    /**
     * Shutdown the network protocol.
     */
    public void shutdown() {
        if (!initialized) {
            return;
        }
        
        reliabilityManagers.clear();
        initialized = false;
        
        logManager.info("NetworkProtocol", "Network protocol shutdown complete");
    }
    
    /**
     * Process incoming packet from connection.
     */
    public void processIncomingPacket(NetworkConnection connection, byte[] packetData) throws NetworkException {
        if (packetData.length < HEADER_SIZE) {
            throw new NetworkException("Packet too small: " + packetData.length);
        }
        
        try {
            // Parse packet header
            PacketHeader header = parseHeader(packetData);
            
            // Validate protocol version
            if (header.version != PROTOCOL_VERSION) {
                throw new NetworkException("Invalid protocol version: " + header.version);
            }
            
            // Validate checksum
            if (!validateChecksum(packetData, header)) {
                throw new NetworkException("Invalid packet checksum");
            }
            
            // Extract payload
            byte[] payload = new byte[header.payloadSize];
            System.arraycopy(packetData, HEADER_SIZE, payload, 0, header.payloadSize);
            
            // Decrypt if needed
            if ((header.flags & FLAG_ENCRYPTED) != 0) {
                payload = security.decrypt(payload, connection.getAuthToken());
            }
            
            // Decompress if needed
            if ((header.flags & FLAG_COMPRESSED) != 0) {
                payload = decompress(payload);
            }
            
            // Handle reliability
            if ((header.flags & FLAG_RELIABLE) != 0) {
                ReliabilityManager reliabilityManager = getReliabilityManager(connection);
                if (!reliabilityManager.handleIncomingReliablePacket(header.sequenceNumber)) {
                    // Duplicate packet, ignore
                    return;
                }
                
                // Send acknowledgment
                sendAcknowledgment(connection, header.sequenceNumber);
            }
            
            // Process message based on type
            processMessage(connection, header.messageType, payload);
            
        } catch (Exception e) {
            throw new NetworkException("Failed to process incoming packet", e);
        }
    }
    
    /**
     * Process outgoing packet for connection.
     */
    public byte[] processOutgoingPacket(NetworkConnection connection, byte[] messageData) throws NetworkException {
        return processOutgoingPacket(connection, MessageType.GAME_EVENT, messageData, FLAG_RELIABLE);
    }
    
    /**
     * Process outgoing packet with specific message type and flags.
     */
    public byte[] processOutgoingPacket(NetworkConnection connection, MessageType messageType, 
                                      byte[] messageData, byte flags) throws NetworkException {
        try {
            byte[] payload = messageData;
            
            // Compress if needed and enabled
            if (config.isCompressionEnabled() && payload.length > config.getCompressionThreshold()) {
                payload = compress(payload);
                flags |= FLAG_COMPRESSED;
            }
            
            // Encrypt if needed
            if (config.isEncryptionEnabled() && connection.isAuthenticated()) {
                payload = security.encrypt(payload, connection.getAuthToken());
                flags |= FLAG_ENCRYPTED;
            }
            
            // Get sequence number
            int sequenceNumber = sequenceCounter.incrementAndGet();
            
            // Handle reliability
            if ((flags & FLAG_RELIABLE) != 0) {
                ReliabilityManager reliabilityManager = getReliabilityManager(connection);
                reliabilityManager.addPendingPacket(sequenceNumber, payload);
            }
            
            // Create packet header
            PacketHeader header = new PacketHeader(
                PROTOCOL_VERSION,
                messageType,
                flags,
                sequenceNumber,
                payload.length
            );
            
            // Build complete packet
            return buildPacket(header, payload);
            
        } catch (Exception e) {
            throw new NetworkException("Failed to process outgoing packet", e);
        }
    }
    
    /**
     * Create heartbeat packet.
     */
    public byte[] createHeartbeatPacket() throws NetworkException {
        return processOutgoingPacket(null, MessageType.HEARTBEAT, new byte[0], (byte) 0);
    }
    
    /**
     * Parse packet header from raw data.
     */
    private PacketHeader parseHeader(byte[] packetData) {
        ByteBuffer buffer = ByteBuffer.wrap(packetData);
        
        byte version = buffer.get();
        MessageType messageType = MessageType.fromByte(buffer.get());
        byte flags = buffer.get();
        buffer.get(); // Reserved byte
        int sequenceNumber = buffer.getInt();
        int payloadSize = buffer.getInt();
        int checksum = buffer.getInt();
        
        return new PacketHeader(version, messageType, flags, sequenceNumber, payloadSize, checksum);
    }
    
    /**
     * Build packet from header and payload.
     */
    private byte[] buildPacket(PacketHeader header, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + payload.length);
        
        // Write header
        buffer.put(header.version);
        buffer.put(header.messageType.getValue());
        buffer.put(header.flags);
        buffer.put((byte) 0); // Reserved
        buffer.putInt(header.sequenceNumber);
        buffer.putInt(payload.length);
        
        // Calculate checksum (placeholder for now)
        int checksum = calculateChecksum(payload);
        buffer.putInt(checksum);
        
        // Write payload
        buffer.put(payload);
        
        return buffer.array();
    }
    
    /**
     * Calculate packet checksum.
     */
    private int calculateChecksum(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return (int) crc.getValue();
    }
    
    /**
     * Validate packet checksum.
     */
    private boolean validateChecksum(byte[] packetData, PacketHeader header) {
        if (header.payloadSize == 0) {
            return true; // No payload to validate
        }
        
        byte[] payload = new byte[header.payloadSize];
        System.arraycopy(packetData, HEADER_SIZE, payload, 0, header.payloadSize);
        
        int calculatedChecksum = calculateChecksum(payload);
        return calculatedChecksum == header.checksum;
    }
    
    /**
     * Process message based on type.
     */
    private void processMessage(NetworkConnection connection, MessageType messageType, byte[] payload) {
        switch (messageType) {
            case HEARTBEAT:
                connection.onHeartbeatReceived();
                break;
                
            case CONNECT:
                handleConnectMessage(connection, payload);
                break;
                
            case DISCONNECT:
                handleDisconnectMessage(connection, payload);
                break;
                
            case ACK:
                handleAcknowledgment(connection, payload);
                break;
                
            case ENTITY_UPDATE:
            case PLAYER_INPUT:
            case GAME_EVENT:
            case CHAT_MESSAGE:
            case WORLD_DATA:
                // Forward to appropriate handlers (will be implemented in higher layers)
                logManager.debug("NetworkProtocol", "Message received",
                               "type", messageType,
                               "size", payload.length,
                               "connectionId", connection.getConnectionId());
                break;
                
            default:
                logManager.warn("NetworkProtocol", "Unknown message type received",
                               "type", messageType,
                               "connectionId", connection.getConnectionId());
        }
    }
    
    /**
     * Handle connection message.
     */
    private void handleConnectMessage(NetworkConnection connection, byte[] payload) {
        // Extract authentication token from payload
        if (payload.length >= 32) {
            String authToken = new String(payload, 0, 32);
            connection.authenticate(authToken);
        }
    }
    
    /**
     * Handle disconnect message.
     */
    private void handleDisconnectMessage(NetworkConnection connection, byte[] payload) {
        connection.close();
    }
    
    /**
     * Send acknowledgment for reliable packet.
     */
    private void sendAcknowledgment(NetworkConnection connection, int sequenceNumber) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(4);
            buffer.putInt(sequenceNumber);
            byte[] ackData = buffer.array();
            
            byte[] packet = processOutgoingPacket(connection, MessageType.ACK, ackData, (byte) 0);
            connection.sendPacket(packet);
            
        } catch (Exception e) {
            logManager.error("NetworkProtocol", "Failed to send acknowledgment", e,
                           "sequenceNumber", sequenceNumber,
                           "connectionId", connection.getConnectionId());
        }
    }
    
    /**
     * Handle acknowledgment received.
     */
    private void handleAcknowledgment(NetworkConnection connection, byte[] payload) {
        if (payload.length >= 4) {
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            int sequenceNumber = buffer.getInt();
            
            ReliabilityManager reliabilityManager = getReliabilityManager(connection);
            reliabilityManager.handleAcknowledgment(sequenceNumber);
        }
    }
    
    /**
     * Get reliability manager for connection.
     */
    private ReliabilityManager getReliabilityManager(NetworkConnection connection) {
        return reliabilityManagers.computeIfAbsent(connection.getConnectionId(),
            k -> new ReliabilityManager(config, connection));
    }
    
    /**
     * Compress data using simple compression.
     */
    private byte[] compress(byte[] data) {
        // Simple compression implementation (could be improved with actual compression algorithms)
        return data; // Placeholder - implement actual compression
    }
    
    /**
     * Decompress data.
     */
    private byte[] decompress(byte[] data) {
        // Simple decompression implementation
        return data; // Placeholder - implement actual decompression
    }
    
    /**
     * Packet header structure.
     */
    private static class PacketHeader {
        final byte version;
        final MessageType messageType;
        final byte flags;
        final int sequenceNumber;
        final int payloadSize;
        final int checksum;
        
        PacketHeader(byte version, MessageType messageType, byte flags, 
                    int sequenceNumber, int payloadSize) {
            this(version, messageType, flags, sequenceNumber, payloadSize, 0);
        }
        
        PacketHeader(byte version, MessageType messageType, byte flags, 
                    int sequenceNumber, int payloadSize, int checksum) {
            this.version = version;
            this.messageType = messageType;
            this.flags = flags;
            this.sequenceNumber = sequenceNumber;
            this.payloadSize = payloadSize;
            this.checksum = checksum;
        }
    }
}