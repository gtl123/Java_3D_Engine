package engine.network.messages;

import engine.network.protocol.NetworkProtocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Network message for chat communication.
 */
public class ChatMessage extends NetworkMessage {
    
    public enum ChatType {
        GLOBAL(0),
        TEAM(1),
        PRIVATE(2),
        SYSTEM(3);
        
        private final int value;
        
        ChatType(int value) {
            this.value = value;
        }
        
        public int getValue() { return value; }
        
        public static ChatType fromValue(int value) {
            for (ChatType type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            return GLOBAL;
        }
    }
    
    private final ChatType chatType;
    private final String message;
    private final String senderName;
    private final int targetPlayerId;
    
    public ChatMessage(int senderId, ChatType chatType, String message, String senderName, int targetPlayerId) {
        super(NetworkProtocol.MessageType.CHAT_MESSAGE, senderId);
        this.chatType = chatType;
        this.message = message != null ? message : "";
        this.senderName = senderName != null ? senderName : "Unknown";
        this.targetPlayerId = targetPlayerId;
    }
    
    @Override
    protected void serializeData(ByteBuffer buffer) {
        buffer.putInt(chatType.getValue());
        buffer.putInt(targetPlayerId);
        
        byte[] senderBytes = senderName.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(senderBytes.length);
        buffer.put(senderBytes);
        
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(messageBytes.length);
        buffer.put(messageBytes);
    }
    
    @Override
    protected int getDataSize() {
        return 4 + 4 + // chatType + targetPlayerId
               4 + senderName.getBytes(StandardCharsets.UTF_8).length +
               4 + message.getBytes(StandardCharsets.UTF_8).length;
    }
    
    public static ChatMessage deserializeData(ByteBuffer buffer, int senderId, long timestamp) {
        ChatType chatType = ChatType.fromValue(buffer.getInt());
        int targetPlayerId = buffer.getInt();
        
        int senderLength = buffer.getInt();
        byte[] senderBytes = new byte[senderLength];
        buffer.get(senderBytes);
        String senderName = new String(senderBytes, StandardCharsets.UTF_8);
        
        int messageLength = buffer.getInt();
        byte[] messageBytes = new byte[messageLength];
        buffer.get(messageBytes);
        String message = new String(messageBytes, StandardCharsets.UTF_8);
        
        return new ChatMessage(senderId, chatType, message, senderName, targetPlayerId);
    }
    
    // Getters
    public ChatType getChatType() { return chatType; }
    public String getMessage() { return message; }
    public String getSenderName() { return senderName; }
    public int getTargetPlayerId() { return targetPlayerId; }
}