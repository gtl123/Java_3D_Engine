package engine.network.messages;

import engine.network.protocol.NetworkProtocol;

import java.nio.ByteBuffer;

/**
 * Base class for all network messages.
 * Provides serialization/deserialization framework for network communication.
 */
public abstract class NetworkMessage {
    
    protected final NetworkProtocol.MessageType messageType;
    protected final long timestamp;
    protected final int senderId;
    
    public NetworkMessage(NetworkProtocol.MessageType messageType, int senderId) {
        this.messageType = messageType;
        this.senderId = senderId;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Serialize message to byte array.
     */
    public final byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(getSerializedSize());
        
        // Write common header
        buffer.put(messageType.getValue());
        buffer.putLong(timestamp);
        buffer.putInt(senderId);
        
        // Write message-specific data
        serializeData(buffer);
        
        return buffer.array();
    }
    
    /**
     * Deserialize message from byte array.
     */
    public static NetworkMessage deserialize(byte[] data) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        
        // Read common header
        byte messageTypeByte = buffer.get();
        NetworkProtocol.MessageType messageType = NetworkProtocol.MessageType.fromByte(messageTypeByte);
        long timestamp = buffer.getLong();
        int senderId = buffer.getInt();
        
        // Create appropriate message type
        switch (messageType) {
            case CONNECT:
                return ConnectMessage.deserializeData(buffer, senderId, timestamp);
            case DISCONNECT:
                return DisconnectMessage.deserializeData(buffer, senderId, timestamp);
            case ENTITY_UPDATE:
                return EntityUpdateMessage.deserializeData(buffer, senderId, timestamp);
            case PLAYER_INPUT:
                return PlayerInputMessage.deserializeData(buffer, senderId, timestamp);
            case GAME_EVENT:
                return GameEventMessage.deserializeData(buffer, senderId, timestamp);
            case CHAT_MESSAGE:
                return ChatMessage.deserializeData(buffer, senderId, timestamp);
            case WORLD_DATA:
                return WorldDataMessage.deserializeData(buffer, senderId, timestamp);
            default:
                throw new IllegalArgumentException("Unknown message type: " + messageType);
        }
    }
    
    /**
     * Get the total serialized size of this message.
     */
    public final int getSerializedSize() {
        return getHeaderSize() + getDataSize();
    }
    
    /**
     * Get the size of the common header.
     */
    protected final int getHeaderSize() {
        return 1 + 8 + 4; // messageType + timestamp + senderId
    }
    
    /**
     * Serialize message-specific data to buffer.
     */
    protected abstract void serializeData(ByteBuffer buffer);
    
    /**
     * Get the size of message-specific data.
     */
    protected abstract int getDataSize();
    
    // Getters
    public NetworkProtocol.MessageType getMessageType() { return messageType; }
    public long getTimestamp() { return timestamp; }
    public int getSenderId() { return senderId; }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
               "messageType=" + messageType +
               ", timestamp=" + timestamp +
               ", senderId=" + senderId +
               '}';
    }
}