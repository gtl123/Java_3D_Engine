package engine.network.messages;

import engine.network.protocol.NetworkProtocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Network message for game events.
 */
public class GameEventMessage extends NetworkMessage {
    
    public enum EventType {
        BLOCK_PLACED(1),
        BLOCK_DESTROYED(2),
        ITEM_PICKED_UP(3),
        ITEM_DROPPED(4),
        PLAYER_DAMAGED(5),
        PLAYER_HEALED(6),
        WORLD_CHANGED(7),
        CUSTOM_EVENT(99);
        
        private final int value;
        
        EventType(int value) {
            this.value = value;
        }
        
        public int getValue() { return value; }
        
        public static EventType fromValue(int value) {
            for (EventType type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            return CUSTOM_EVENT;
        }
    }
    
    private final EventType eventType;
    private final String eventData;
    private final int targetEntityId;
    
    public GameEventMessage(int senderId, EventType eventType, String eventData, int targetEntityId) {
        super(NetworkProtocol.MessageType.GAME_EVENT, senderId);
        this.eventType = eventType;
        this.eventData = eventData != null ? eventData : "";
        this.targetEntityId = targetEntityId;
    }
    
    @Override
    protected void serializeData(ByteBuffer buffer) {
        buffer.putInt(eventType.getValue());
        buffer.putInt(targetEntityId);
        
        byte[] dataBytes = eventData.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(dataBytes.length);
        buffer.put(dataBytes);
    }
    
    @Override
    protected int getDataSize() {
        return 4 + 4 + 4 + eventData.getBytes(StandardCharsets.UTF_8).length;
    }
    
    public static GameEventMessage deserializeData(ByteBuffer buffer, int senderId, long timestamp) {
        EventType eventType = EventType.fromValue(buffer.getInt());
        int targetEntityId = buffer.getInt();
        
        int dataLength = buffer.getInt();
        byte[] dataBytes = new byte[dataLength];
        buffer.get(dataBytes);
        String eventData = new String(dataBytes, StandardCharsets.UTF_8);
        
        return new GameEventMessage(senderId, eventType, eventData, targetEntityId);
    }
    
    // Getters
    public EventType getEventType() { return eventType; }
    public String getEventData() { return eventData; }
    public int getTargetEntityId() { return targetEntityId; }
}