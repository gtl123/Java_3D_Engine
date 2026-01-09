package engine.network;

import engine.logging.LogManager;
import engine.network.protocol.NetworkProtocol;
import engine.network.security.NetworkSecurity;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a single network connection with reliability and security features.
 */
public class NetworkConnection {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    public enum ConnectionState {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED,
        TIMEOUT,
        ERROR
    }
    
    private final int connectionNumber;
    private final String connectionId;
    private final SocketAddress remoteAddress;
    private final NetworkConfiguration config;
    private final NetworkProtocol protocol;
    private final NetworkSecurity security;
    private final ConnectionManager connectionManager;
    
    // Connection state
    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.CONNECTING);
    private volatile long lastActivityTime;
    private volatile long connectionStartTime;
    private volatile String closeReason = "";
    
    // Security
    private volatile String authToken;
    private volatile boolean authenticated = false;
    
    // Performance metrics
    private final AtomicLong packetsReceived = new AtomicLong(0);
    private final AtomicLong packetsSent = new AtomicLong(0);
    private final AtomicLong bytesReceived = new AtomicLong(0);
    private final AtomicLong bytesSent = new AtomicLong(0);
    private volatile double latency = 0.0;
    
    // Heartbeat
    private volatile long lastHeartbeatSent;
    private volatile long lastHeartbeatReceived;
    
    public NetworkConnection(int connectionNumber, String connectionId, SocketAddress remoteAddress,
                           NetworkConfiguration config, NetworkProtocol protocol, 
                           NetworkSecurity security, ConnectionManager connectionManager) {
        this.connectionNumber = connectionNumber;
        this.connectionId = connectionId;
        this.remoteAddress = remoteAddress;
        this.config = config;
        this.protocol = protocol;
        this.security = security;
        this.connectionManager = connectionManager;
        
        this.connectionStartTime = System.currentTimeMillis();
        this.lastActivityTime = connectionStartTime;
        this.lastHeartbeatReceived = connectionStartTime;
        
        logManager.debug("NetworkConnection", "Connection created",
                        "connectionId", connectionId,
                        "remoteAddress", remoteAddress);
    }
    
    /**
     * Update connection state and handle timeouts.
     */
    public void update(long deltaTime) {
        long currentTime = System.currentTimeMillis();
        
        // Check for connection timeout
        if (currentTime - lastActivityTime > config.getConnectionTimeoutMs()) {
            if (state.get() != ConnectionState.TIMEOUT) {
                state.set(ConnectionState.TIMEOUT);
                closeReason = "Connection timeout";
                logManager.warn("NetworkConnection", "Connection timed out",
                               "connectionId", connectionId,
                               "lastActivity", currentTime - lastActivityTime);
            }
            return;
        }
        
        // Send heartbeat if needed
        if (state.get() == ConnectionState.CONNECTED && 
            currentTime - lastHeartbeatSent > config.getHeartbeatIntervalMs()) {
            sendHeartbeat();
        }
        
        // Check heartbeat timeout
        if (state.get() == ConnectionState.CONNECTED &&
            currentTime - lastHeartbeatReceived > config.getHeartbeatIntervalMs() * 3) {
            state.set(ConnectionState.TIMEOUT);
            closeReason = "Heartbeat timeout";
            logManager.warn("NetworkConnection", "Heartbeat timeout",
                           "connectionId", connectionId);
        }
    }
    
    /**
     * Handle incoming packet from this connection.
     */
    public void handleIncomingPacket(byte[] packetData) {
        lastActivityTime = System.currentTimeMillis();
        packetsReceived.incrementAndGet();
        bytesReceived.addAndGet(packetData.length);
        
        try {
            // Process packet through protocol layer
            protocol.processIncomingPacket(this, packetData);
            
        } catch (Exception e) {
            logManager.error("NetworkConnection", "Error processing incoming packet", e,
                           "connectionId", connectionId);
            state.set(ConnectionState.ERROR);
            closeReason = "Packet processing error: " + e.getMessage();
        }
    }
    
    /**
     * Send packet through this connection.
     */
    public void sendPacket(byte[] packetData) {
        if (state.get() != ConnectionState.CONNECTED && state.get() != ConnectionState.CONNECTING) {
            return;
        }
        
        try {
            // Process packet through protocol layer
            byte[] processedData = protocol.processOutgoingPacket(this, packetData);
            
            // Send through connection manager
            connectionManager.sendPacket(remoteAddress, processedData);
            
            packetsSent.incrementAndGet();
            bytesSent.addAndGet(processedData.length);
            
        } catch (Exception e) {
            logManager.error("NetworkConnection", "Error sending packet", e,
                           "connectionId", connectionId);
            state.set(ConnectionState.ERROR);
            closeReason = "Packet sending error: " + e.getMessage();
        }
    }
    
    /**
     * Send heartbeat packet.
     */
    private void sendHeartbeat() {
        try {
            byte[] heartbeatData = protocol.createHeartbeatPacket();
            sendPacket(heartbeatData);
            lastHeartbeatSent = System.currentTimeMillis();
            
        } catch (Exception e) {
            logManager.error("NetworkConnection", "Error sending heartbeat", e,
                           "connectionId", connectionId);
        }
    }
    
    /**
     * Handle heartbeat received.
     */
    public void onHeartbeatReceived() {
        lastHeartbeatReceived = System.currentTimeMillis();
        
        // Calculate latency (simple RTT/2 approximation)
        if (lastHeartbeatSent > 0) {
            latency = (lastHeartbeatReceived - lastHeartbeatSent) / 2.0;
        }
    }
    
    /**
     * Authenticate this connection.
     */
    public void authenticate(String token) {
        if (security.validateAuthToken(token)) {
            this.authToken = token;
            this.authenticated = true;
            state.set(ConnectionState.CONNECTED);
            
            logManager.info("NetworkConnection", "Connection authenticated",
                           "connectionId", connectionId);
        } else {
            state.set(ConnectionState.ERROR);
            closeReason = "Authentication failed";
            
            logManager.warn("NetworkConnection", "Authentication failed",
                           "connectionId", connectionId);
        }
    }
    
    /**
     * Close this connection.
     */
    public void close() {
        if (state.get() == ConnectionState.DISCONNECTED) {
            return;
        }
        
        ConnectionState previousState = state.getAndSet(ConnectionState.DISCONNECTED);
        
        if (closeReason.isEmpty()) {
            closeReason = "Connection closed normally";
        }
        
        logManager.info("NetworkConnection", "Connection closed",
                       "connectionId", connectionId,
                       "previousState", previousState,
                       "reason", closeReason,
                       "duration", System.currentTimeMillis() - connectionStartTime,
                       "packetsReceived", packetsReceived.get(),
                       "packetsSent", packetsSent.get());
    }
    
    /**
     * Check if connection should be closed.
     */
    public boolean shouldClose() {
        ConnectionState currentState = state.get();
        return currentState == ConnectionState.DISCONNECTED ||
               currentState == ConnectionState.TIMEOUT ||
               currentState == ConnectionState.ERROR;
    }
    
    // Getters
    public int getConnectionNumber() { return connectionNumber; }
    public String getConnectionId() { return connectionId; }
    public SocketAddress getRemoteAddress() { return remoteAddress; }
    public ConnectionState getState() { return state.get(); }
    public boolean isConnected() { return state.get() == ConnectionState.CONNECTED; }
    public boolean isAuthenticated() { return authenticated; }
    public String getAuthToken() { return authToken; }
    public String getCloseReason() { return closeReason; }
    public double getLatency() { return latency; }
    
    // Statistics
    public long getPacketsReceived() { return packetsReceived.get(); }
    public long getPacketsSent() { return packetsSent.get(); }
    public long getBytesReceived() { return bytesReceived.get(); }
    public long getBytesSent() { return bytesSent.get(); }
    public long getConnectionDuration() { return System.currentTimeMillis() - connectionStartTime; }
    
    @Override
    public String toString() {
        return "NetworkConnection{" +
               "id='" + connectionId + '\'' +
               ", state=" + state.get() +
               ", authenticated=" + authenticated +
               ", latency=" + latency +
               '}';
    }
}