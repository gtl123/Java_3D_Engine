package engine.network;

import engine.logging.LogManager;
import engine.network.protocol.NetworkProtocol;
import engine.network.security.NetworkSecurity;
import engine.profiler.ProfilerManager;
import engine.profiler.NetworkProfiler;
import engine.profiler.PerformanceProfiler;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Connection pooling and management system.
 * Handles UDP connections with reliability layer and connection lifecycle.
 */
public class ConnectionManager {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final NetworkConfiguration config;
    private final NetworkProtocol protocol;
    private final NetworkSecurity security;
    
    // Profiler components
    private ProfilerManager profilerManager;
    private NetworkProfiler networkProfiler;
    private PerformanceProfiler performanceProfiler;
    
    // Connection management
    private final ConcurrentHashMap<String, NetworkConnection> connections = new ConcurrentHashMap<>();
    private final AtomicInteger connectionIdCounter = new AtomicInteger(0);
    
    // Network I/O
    private DatagramChannel serverChannel;
    private Selector selector;
    private final ByteBuffer receiveBuffer;
    private final ByteBuffer sendBuffer;
    
    // Statistics
    private final AtomicLong packetsReceived = new AtomicLong(0);
    private final AtomicLong packetsSent = new AtomicLong(0);
    private final AtomicLong bytesReceived = new AtomicLong(0);
    private final AtomicLong bytesSent = new AtomicLong(0);
    
    private volatile boolean initialized = false;
    private volatile boolean running = false;
    
    public ConnectionManager(NetworkConfiguration config, NetworkProtocol protocol, NetworkSecurity security) {
        this.config = config;
        this.protocol = protocol;
        this.security = security;
        this.receiveBuffer = ByteBuffer.allocateDirect(config.getReceiveBufferSize());
        this.sendBuffer = ByteBuffer.allocateDirect(config.getSendBufferSize());
    }
    
    /**
     * Set the profiler manager for profiling integration.
     */
    public void setProfilerManager(ProfilerManager profilerManager) {
        this.profilerManager = profilerManager;
        if (profilerManager != null && profilerManager.isInitialized()) {
            this.networkProfiler = profilerManager.getNetworkProfiler();
            this.performanceProfiler = profilerManager.getPerformanceProfiler();
            
            logManager.debug("ConnectionManager", "Profiler integration enabled");
        }
    }
    
    /**
     * Initialize the connection manager.
     */
    public void initialize() throws NetworkException {
        if (initialized) {
            return;
        }
        
        // Start profiling initialization if available
        if (performanceProfiler != null) {
            performanceProfiler.startMethodProfiling("ConnectionManager.initialize");
        }
        
        try {
            // Create selector for non-blocking I/O
            selector = Selector.open();
            
            // Create and configure server channel
            serverChannel = DatagramChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().setReceiveBufferSize(config.getReceiveBufferSize());
            serverChannel.socket().setSendBufferSize(config.getSendBufferSize());
            
            // Bind to server port
            InetSocketAddress bindAddress = new InetSocketAddress(config.getServerPort());
            serverChannel.bind(bindAddress);
            
            // Register with selector
            serverChannel.register(selector, SelectionKey.OP_READ);
            
            initialized = true;
            
            logManager.info("ConnectionManager", "Connection manager initialized",
                           "serverPort", config.getServerPort(),
                           "maxConnections", config.getMaxConnections());
            
        } catch (Exception e) {
            throw new NetworkException("Failed to initialize connection manager", e);
        } finally {
            // End profiling initialization
            if (performanceProfiler != null) {
                performanceProfiler.endMethodProfiling("ConnectionManager.initialize");
            }
        }
    }
    
    /**
     * Start the connection manager.
     */
    public void start() throws NetworkException {
        if (!initialized) {
            throw new IllegalStateException("ConnectionManager must be initialized before starting");
        }
        
        if (running) {
            return;
        }
        
        running = true;
        
        logManager.info("ConnectionManager", "Connection manager started");
    }
    
    /**
     * Stop the connection manager.
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        
        // Close all connections
        for (NetworkConnection connection : connections.values()) {
            connection.close();
        }
        connections.clear();
        
        logManager.info("ConnectionManager", "Connection manager stopped");
    }
    
    /**
     * Shutdown the connection manager.
     */
    public void shutdown() {
        // Profile shutdown process
        if (performanceProfiler != null) {
            performanceProfiler.startMethodProfiling("ConnectionManager.shutdown");
        }
        
        try {
            stop();
            
            if (!initialized) {
                return;
            }
            
            try {
                if (serverChannel != null && serverChannel.isOpen()) {
                    serverChannel.close();
                }
                
                if (selector != null && selector.isOpen()) {
                    selector.close();
                }
                
                // Clear profiler references
                networkProfiler = null;
                performanceProfiler = null;
                profilerManager = null;
                
                initialized = false;
                
                logManager.info("ConnectionManager", "Connection manager shutdown complete");
                
            } catch (Exception e) {
                logManager.error("ConnectionManager", "Error during connection manager shutdown", e);
            }
        } finally {
            if (performanceProfiler != null) {
                performanceProfiler.endMethodProfiling("ConnectionManager.shutdown");
            }
        }
    }
    
    /**
     * Update connection manager - process I/O and maintain connections.
     */
    public void update(long deltaTime) {
        if (!running) {
            return;
        }
        
        // Start profiling update cycle
        if (performanceProfiler != null) {
            performanceProfiler.startMethodProfiling("ConnectionManager.update");
        }
        
        try {
            // Process network I/O
            processNetworkIO();
            
            // Update all connections
            updateConnections(deltaTime);
            
            // Clean up dead connections
            cleanupConnections();
            
        } catch (Exception e) {
            logManager.error("ConnectionManager", "Error updating connection manager", e);
        } finally {
            // End profiling update cycle
            if (performanceProfiler != null) {
                performanceProfiler.endMethodProfiling("ConnectionManager.update");
            }
        }
    }
    
    /**
     * Process network I/O using selector.
     */
    private void processNetworkIO() throws Exception {
        // Non-blocking select
        int readyChannels = selector.selectNow();
        
        if (readyChannels > 0) {
            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
            
            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove();
                
                if (key.isReadable()) {
                    handleIncomingPacket();
                }
            }
        }
    }
    
    /**
     * Handle incoming packet from network.
     */
    private void handleIncomingPacket() throws Exception {
        receiveBuffer.clear();
        SocketAddress senderAddress = serverChannel.receive(receiveBuffer);
        
        if (senderAddress == null) {
            return; // No packet received
        }
        
        receiveBuffer.flip();
        int packetSize = receiveBuffer.remaining();
        
        if (packetSize == 0) {
            return;
        }
        
        // Update statistics
        packetsReceived.incrementAndGet();
        bytesReceived.addAndGet(packetSize);
        
        // Record network profiling data
        if (networkProfiler != null) {
            networkProfiler.recordPacketReceived(packetSize);
            networkProfiler.recordBandwidthUsage(packetSize, 0); // Incoming bandwidth
        }
        
        // Create packet data array
        byte[] packetData = new byte[packetSize];
        receiveBuffer.get(packetData);
        
        // Find or create connection
        String connectionId = senderAddress.toString();
        NetworkConnection connection = connections.get(connectionId);
        
        if (connection == null) {
            // New connection attempt
            connection = createNewConnection(senderAddress);
            if (connection == null) {
                return; // Connection rejected
            }
        }
        
        // Process packet through connection
        connection.handleIncomingPacket(packetData);
        
        if (config.isPacketLoggingEnabled()) {
            logManager.debug("ConnectionManager", "Packet received",
                           "from", senderAddress,
                           "size", packetSize,
                           "connectionId", connectionId);
        }
    }
    
    /**
     * Create a new connection for incoming client.
     */
    private NetworkConnection createNewConnection(SocketAddress clientAddress) {
        // Check connection limit
        if (connections.size() >= config.getMaxConnections()) {
            logManager.warn("ConnectionManager", "Connection limit reached, rejecting new connection",
                           "clientAddress", clientAddress,
                           "currentConnections", connections.size(),
                           "maxConnections", config.getMaxConnections());
            return null;
        }
        
        try {
            String connectionId = clientAddress.toString();
            int connectionNumber = connectionIdCounter.incrementAndGet();
            
            NetworkConnection connection = new NetworkConnection(
                connectionNumber, connectionId, clientAddress, config, protocol, security, this);
            
            connections.put(connectionId, connection);
            
            logManager.info("ConnectionManager", "New connection created",
                           "connectionId", connectionId,
                           "connectionNumber", connectionNumber,
                           "clientAddress", clientAddress,
                           "totalConnections", connections.size());
            
            return connection;
            
        } catch (Exception e) {
            logManager.error("ConnectionManager", "Failed to create new connection", e,
                           "clientAddress", clientAddress);
            return null;
        }
    }
    
    /**
     * Update all active connections.
     */
    private void updateConnections(long deltaTime) {
        for (NetworkConnection connection : connections.values()) {
            try {
                connection.update(deltaTime);
            } catch (Exception e) {
                logManager.error("ConnectionManager", "Error updating connection", e,
                               "connectionId", connection.getConnectionId());
            }
        }
    }
    
    /**
     * Clean up dead or timed-out connections.
     */
    private void cleanupConnections() {
        Iterator<NetworkConnection> iterator = connections.values().iterator();
        
        while (iterator.hasNext()) {
            NetworkConnection connection = iterator.next();
            
            if (connection.shouldClose()) {
                iterator.remove();
                connection.close();
                
                logManager.info("ConnectionManager", "Connection cleaned up",
                               "connectionId", connection.getConnectionId(),
                               "reason", connection.getCloseReason());
            }
        }
    }
    
    /**
     * Send packet to specific address.
     */
    public void sendPacket(SocketAddress destination, byte[] data) throws Exception {
        if (!running) {
            return;
        }
        
        // Start profiling packet send
        if (performanceProfiler != null) {
            performanceProfiler.startMethodProfiling("ConnectionManager.sendPacket");
        }
        
        try {
            sendBuffer.clear();
            sendBuffer.put(data);
            sendBuffer.flip();
            
            int bytesSentNow = serverChannel.send(sendBuffer, destination);
            
            if (bytesSentNow > 0) {
                packetsSent.incrementAndGet();
                bytesSent.addAndGet(bytesSentNow);
                
                // Record network profiling data
                if (networkProfiler != null) {
                    networkProfiler.recordPacketSent(bytesSentNow);
                    networkProfiler.recordBandwidthUsage(0, bytesSentNow); // Outgoing bandwidth
                }
                
                if (config.isPacketLoggingEnabled()) {
                    logManager.debug("ConnectionManager", "Packet sent",
                                   "to", destination,
                                   "size", bytesSentNow);
                }
            }
        } finally {
            // End profiling packet send
            if (performanceProfiler != null) {
                performanceProfiler.endMethodProfiling("ConnectionManager.sendPacket");
            }
        }
    }
    
    /**
     * Get connection by ID.
     */
    public NetworkConnection getConnection(String connectionId) {
        return connections.get(connectionId);
    }
    
    /**
     * Remove connection.
     */
    public void removeConnection(String connectionId) {
        NetworkConnection connection = connections.remove(connectionId);
        if (connection != null) {
            connection.close();
            logManager.info("ConnectionManager", "Connection removed",
                           "connectionId", connectionId);
        }
    }
    
    /**
     * Get active connection count.
     */
    public int getActiveConnectionCount() {
        return connections.size();
    }
    
    /**
     * Collect statistics for monitoring.
     */
    public void collectStatistics(ConcurrentHashMap<String, Object> stats) {
        stats.put("connections.active", connections.size());
        stats.put("packets.received", packetsReceived.get());
        stats.put("packets.sent", packetsSent.get());
        stats.put("bandwidth.in", bytesReceived.get());
        stats.put("bandwidth.out", bytesSent.get());
        
        // Calculate average latency from all connections
        double totalLatency = 0.0;
        int connectionCount = 0;
        
        for (NetworkConnection connection : connections.values()) {
            if (connection.isConnected()) {
                totalLatency += connection.getLatency();
                connectionCount++;
            }
        }
        
        double averageLatency = connectionCount > 0 ? totalLatency / connectionCount : 0.0;
        stats.put("latency.average", averageLatency);
        
        // Update network profiler with current statistics
        if (networkProfiler != null) {
            networkProfiler.recordConnectionCount(connections.size());
            networkProfiler.recordLatency(averageLatency);
        }
    }
    
    // Getters
    public boolean isInitialized() { return initialized; }
    public boolean isRunning() { return running; }
    public NetworkConfiguration getConfig() { return config; }
}