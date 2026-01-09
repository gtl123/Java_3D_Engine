package engine.network.messages;

import engine.network.protocol.NetworkProtocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Network message for client disconnection.
 */
public class DisconnectMessage extends NetworkMessage {
    
    private final String reason;
    
    public DisconnectMessage(int senderId, String reason) {
        super(NetworkProtocol.MessageType.DISCONNECT, senderId);
        this.reason = reason != null ? reason : "Unknown";
    }
    
    @Override
    protected void serializeData(ByteBuffer buffer) {
        byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(reasonBytes.length);
        buffer.put(reasonBytes);
    }
    
    @Override
    protected int getDataSize() {
        return 4 + reason.getBytes(StandardCharsets.UTF_8).length;
    }
    
    public static DisconnectMessage deserializeData(ByteBuffer buffer, int senderId, long timestamp) {
        int reasonLength = buffer.getInt();
        byte[] reasonBytes = new byte[reasonLength];
        buffer.get(reasonBytes);
        String reason = new String(reasonBytes, StandardCharsets.UTF_8);
        
        return new DisconnectMessage(senderId, reason);
    }
    
    public String getReason() { return reason; }
}