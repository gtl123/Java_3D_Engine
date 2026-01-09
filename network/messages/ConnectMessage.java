package engine.network.messages;

import engine.network.protocol.NetworkProtocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Network message for client connection requests.
 */
public class ConnectMessage extends NetworkMessage {
    
    private final String clientName;
    private final String authToken;
    private final int protocolVersion;
    
    public ConnectMessage(int senderId, String clientName, String authToken, int protocolVersion) {
        super(NetworkProtocol.MessageType.CONNECT, senderId);
        this.clientName = clientName;
        this.authToken = authToken;
        this.protocolVersion = protocolVersion;
    }
    
    @Override
    protected void serializeData(ByteBuffer buffer) {
        buffer.putInt(protocolVersion);
        
        byte[] nameBytes = clientName.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(nameBytes.length);
        buffer.put(nameBytes);
        
        byte[] tokenBytes = authToken.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(tokenBytes.length);
        buffer.put(tokenBytes);
    }
    
    @Override
    protected int getDataSize() {
        return 4 + // protocolVersion
               4 + clientName.getBytes(StandardCharsets.UTF_8).length + // clientName
               4 + authToken.getBytes(StandardCharsets.UTF_8).length;   // authToken
    }
    
    public static ConnectMessage deserializeData(ByteBuffer buffer, int senderId, long timestamp) {
        int protocolVersion = buffer.getInt();
        
        int nameLength = buffer.getInt();
        byte[] nameBytes = new byte[nameLength];
        buffer.get(nameBytes);
        String clientName = new String(nameBytes, StandardCharsets.UTF_8);
        
        int tokenLength = buffer.getInt();
        byte[] tokenBytes = new byte[tokenLength];
        buffer.get(tokenBytes);
        String authToken = new String(tokenBytes, StandardCharsets.UTF_8);
        
        return new ConnectMessage(senderId, clientName, authToken, protocolVersion);
    }
    
    // Getters
    public String getClientName() { return clientName; }
    public String getAuthToken() { return authToken; }
    public int getProtocolVersion() { return protocolVersion; }
}