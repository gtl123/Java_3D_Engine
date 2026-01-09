package engine.network;

/**
 * Network configuration settings for the engine.
 * Provides centralized configuration for all networking components.
 */
public class NetworkConfiguration {
    
    // Connection settings
    private int serverPort = 7777;
    private int maxConnections = 64;
    private long connectionTimeoutMs = 30000; // 30 seconds
    private long heartbeatIntervalMs = 5000;  // 5 seconds
    
    // Protocol settings
    private boolean compressionEnabled = true;
    private int compressionThreshold = 512; // bytes
    private boolean encryptionEnabled = true;
    private boolean deltaCompressionEnabled = true;
    
    // Performance settings
    private int serverTickRate = 64; // 64 Hz = ~15.6ms per tick
    private int clientUpdateRate = 20; // 20 Hz for client updates
    private int maxPacketSize = 1400; // MTU safe size
    private int sendBufferSize = 65536;
    private int receiveBufferSize = 65536;
    
    // Reliability settings
    private int maxRetransmissions = 5;
    private long retransmissionTimeoutMs = 100;
    private long ackTimeoutMs = 1000;
    
    // Anti-cheat integration
    private boolean antiCheatEnabled = true;
    private long violationRetentionTime = 86400000; // 24 hours
    
    public NetworkConfiguration() {
        // Default configuration
    }
    
    // Getters
    public int getServerPort() { return serverPort; }
    public int getMaxConnections() { return maxConnections; }
    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
    public boolean isCompressionEnabled() { return compressionEnabled; }
    public int getCompressionThreshold() { return compressionThreshold; }
    public boolean isEncryptionEnabled() { return encryptionEnabled; }
    public boolean isDeltaCompressionEnabled() { return deltaCompressionEnabled; }
    public int getServerTickRate() { return serverTickRate; }
    public int getClientUpdateRate() { return clientUpdateRate; }
    public int getMaxPacketSize() { return maxPacketSize; }
    public int getSendBufferSize() { return sendBufferSize; }
    public int getReceiveBufferSize() { return receiveBufferSize; }
    public int getMaxRetransmissions() { return maxRetransmissions; }
    public long getRetransmissionTimeoutMs() { return retransmissionTimeoutMs; }
    public long getAckTimeoutMs() { return ackTimeoutMs; }
    public boolean isAntiCheatEnabled() { return antiCheatEnabled; }
    public long getViolationRetentionTime() { return violationRetentionTime; }
    
    // Setters
    public void setServerPort(int serverPort) { this.serverPort = serverPort; }
    public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }
    public void setConnectionTimeoutMs(long connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }
    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) { this.heartbeatIntervalMs = heartbeatIntervalMs; }
    public void setCompressionEnabled(boolean compressionEnabled) { this.compressionEnabled = compressionEnabled; }
    public void setCompressionThreshold(int compressionThreshold) { this.compressionThreshold = compressionThreshold; }
    public void setEncryptionEnabled(boolean encryptionEnabled) { this.encryptionEnabled = encryptionEnabled; }
    public void setDeltaCompressionEnabled(boolean deltaCompressionEnabled) { this.deltaCompressionEnabled = deltaCompressionEnabled; }
    public void setServerTickRate(int serverTickRate) { this.serverTickRate = serverTickRate; }
    public void setClientUpdateRate(int clientUpdateRate) { this.clientUpdateRate = clientUpdateRate; }
    public void setMaxPacketSize(int maxPacketSize) { this.maxPacketSize = maxPacketSize; }
    public void setSendBufferSize(int sendBufferSize) { this.sendBufferSize = sendBufferSize; }
    public void setReceiveBufferSize(int receiveBufferSize) { this.receiveBufferSize = receiveBufferSize; }
    public void setMaxRetransmissions(int maxRetransmissions) { this.maxRetransmissions = maxRetransmissions; }
    public void setRetransmissionTimeoutMs(long retransmissionTimeoutMs) { this.retransmissionTimeoutMs = retransmissionTimeoutMs; }
    public void setAckTimeoutMs(long ackTimeoutMs) { this.ackTimeoutMs = ackTimeoutMs; }
    public void setAntiCheatEnabled(boolean antiCheatEnabled) { this.antiCheatEnabled = antiCheatEnabled; }
    public void setViolationRetentionTime(long violationRetentionTime) { this.violationRetentionTime = violationRetentionTime; }
}