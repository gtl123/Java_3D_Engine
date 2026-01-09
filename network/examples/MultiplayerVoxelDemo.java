package engine.network.examples;

import engine.config.ConfigurationManager;
import engine.entity.NetworkEntity;
import engine.logging.LogManager;
import engine.network.*;
import engine.network.debug.NetworkDebugger;
import engine.network.messages.*;
import engine.network.sync.EntityState;
import engine.raster.CubeMeshBuilder;
import engine.raster.Mesh;
import org.joml.Vector3f;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive demonstration of the Enterprise Networking Layer.
 * Shows server setup, client connections, entity synchronization, and multiplayer gameplay.
 */
public class MultiplayerVoxelDemo {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Network components
    private NetworkManager networkManager;
    private NetworkDebugger networkDebugger;
    
    // Game state
    private final ConcurrentHashMap<Integer, NetworkEntity> players = new ConcurrentHashMap<>();
    private final AtomicInteger nextPlayerId = new AtomicInteger(1);
    
    // Demo configuration
    private boolean isServer = false;
    private String serverAddress = "localhost";
    private int serverPort = 7777;
    
    public MultiplayerVoxelDemo() {
        // Initialize logging and configuration
        LogManager.getInstance().initialize();
        ConfigurationManager.getInstance().initialize();
    }
    
    /**
     * Run the multiplayer demo as a server.
     */
    public void runAsServer() {
        logManager.info("MultiplayerVoxelDemo", "Starting multiplayer voxel demo as SERVER");
        
        try {
            isServer = true;
            
            // Initialize networking systems
            initializeNetworking();
            
            // Start server
            startServer();
            
            // Run server game loop
            runServerGameLoop();
            
        } catch (Exception e) {
            logManager.error("MultiplayerVoxelDemo", "Server error", e);
        } finally {
            shutdown();
        }
    }
    
    /**
     * Run the multiplayer demo as a client.
     */
    public void runAsClient(String serverAddress) {
        logManager.info("MultiplayerVoxelDemo", "Starting multiplayer voxel demo as CLIENT",
                       "serverAddress", serverAddress);
        
        try {
            isServer = false;
            this.serverAddress = serverAddress;
            
            // Initialize networking systems
            initializeNetworking();
            
            // Connect to server
            connectToServer();
            
            // Run client game loop
            runClientGameLoop();
            
        } catch (Exception e) {
            logManager.error("MultiplayerVoxelDemo", "Client error", e);
        } finally {
            shutdown();
        }
    }
    
    /**
     * Initialize networking systems.
     */
    private void initializeNetworking() throws NetworkException {
        logManager.info("MultiplayerVoxelDemo", "Initializing networking systems");
        
        // Initialize network manager
        networkManager = NetworkManager.getInstance();
        networkManager.initialize();
        networkManager.start();
        
        // Initialize network debugger
        networkDebugger = NetworkDebugger.getInstance();
        networkDebugger.initialize();
        networkDebugger.setDebugEnabled(true);
        networkDebugger.setPacketLoggingEnabled(true);
        networkDebugger.setPerformanceMonitoringEnabled(true);
        networkDebugger.start();
        
        logManager.info("MultiplayerVoxelDemo", "Networking systems initialized successfully");
    }
    
    /**
     * Start the server.
     */
    private void startServer() throws NetworkException {
        logManager.info("MultiplayerVoxelDemo", "Starting server",
                       "port", serverPort,
                       "maxConnections", networkManager.getConfiguration().getMaxConnections());
        
        // Start server
        ClientServerArchitecture clientServer = networkManager.getClientServerArchitecture();
        clientServer.startServer(ClientServerArchitecture.ServerMode.DEDICATED_SERVER);
        
        // Create some demo entities
        createDemoEntities();
        
        logManager.info("MultiplayerVoxelDemo", "Server started successfully");
    }
    
    /**
     * Connect to server as client.
     */
    private void connectToServer() throws NetworkException {
        logManager.info("MultiplayerVoxelDemo", "Connecting to server",
                       "address", serverAddress,
                       "port", serverPort);
        
        // Connect to server
        ClientServerArchitecture clientServer = networkManager.getClientServerArchitecture();
        clientServer.connectToServer(serverAddress, serverPort);
        
        logManager.info("MultiplayerVoxelDemo", "Connected to server successfully");
    }
    
    /**
     * Run server game loop.
     */
    private void runServerGameLoop() {
        logManager.info("MultiplayerVoxelDemo", "Starting server game loop");
        
        long lastUpdate = System.currentTimeMillis();
        final long targetFrameTime = 1000 / 20; // 20 TPS
        
        // Run for demo duration (60 seconds)
        long demoStartTime = System.currentTimeMillis();
        long demoDuration = 60000; // 60 seconds
        
        while (System.currentTimeMillis() - demoStartTime < demoDuration) {
            try {
                long currentTime = System.currentTimeMillis();
                long deltaTime = currentTime - lastUpdate;
                
                if (deltaTime >= targetFrameTime) {
                    // Update game logic
                    updateServerGameLogic(deltaTime);
                    
                    // Update network entities
                    updateNetworkEntities(deltaTime);
                    
                    // Process network messages
                    processNetworkMessages();
                    
                    lastUpdate = currentTime;
                }
                
                // Small sleep to prevent busy waiting
                Thread.sleep(1);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logManager.error("MultiplayerVoxelDemo", "Error in server game loop", e);
            }
        }
        
        logManager.info("MultiplayerVoxelDemo", "Server game loop completed");
    }
    
    /**
     * Run client game loop.
     */
    private void runClientGameLoop() {
        logManager.info("MultiplayerVoxelDemo", "Starting client game loop");
        
        long lastUpdate = System.currentTimeMillis();
        final long targetFrameTime = 1000 / 60; // 60 FPS
        
        // Run for demo duration (60 seconds)
        long demoStartTime = System.currentTimeMillis();
        long demoDuration = 60000; // 60 seconds
        
        while (System.currentTimeMillis() - demoStartTime < demoDuration) {
            try {
                long currentTime = System.currentTimeMillis();
                long deltaTime = currentTime - lastUpdate;
                
                if (deltaTime >= targetFrameTime) {
                    // Update client logic
                    updateClientGameLogic(deltaTime);
                    
                    // Update network entities
                    updateNetworkEntities(deltaTime);
                    
                    // Send input to server
                    sendPlayerInput();
                    
                    lastUpdate = currentTime;
                }
                
                // Small sleep to prevent busy waiting
                Thread.sleep(1);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logManager.error("MultiplayerVoxelDemo", "Error in client game loop", e);
            }
        }
        
        logManager.info("MultiplayerVoxelDemo", "Client game loop completed");
    }
    
    /**
     * Create demo entities for the server.
     */
    private void createDemoEntities() {
        logManager.info("MultiplayerVoxelDemo", "Creating demo entities");
        
        // Create some demo player entities
        for (int i = 0; i < 3; i++) {
            createDemoPlayer(i);
        }
        
        // Create some demo world objects
        createDemoWorldObjects();
        
        logManager.info("MultiplayerVoxelDemo", "Demo entities created",
                       "playerCount", players.size());
    }
    
    /**
     * Create a demo player entity.
     */
    private void createDemoPlayer(int playerIndex) {
        try {
            // Create player mesh (simple cube for demo)
            Mesh playerMesh = CubeMeshBuilder.createCube(0.6f, 1.8f, 0.6f);
            
            // Create network entity
            int playerId = nextPlayerId.getAndIncrement();
            NetworkEntity player = new NetworkEntity(playerMesh, playerId);
            
            // Set initial position
            float x = playerIndex * 5.0f;
            float y = 10.0f;
            float z = 0.0f;
            player.setLocalPosition(x, y, z);
            
            // Enable networking
            player.enableNetworking(
                NetworkReplication.OwnershipType.SERVER,
                NetworkReplication.ReplicationMode.ALWAYS,
                "server"
            );
            
            players.put(playerId, player);
            
            logManager.info("MultiplayerVoxelDemo", "Demo player created",
                           "playerId", playerId,
                           "position", new Vector3f(x, y, z));
            
        } catch (Exception e) {
            logManager.error("MultiplayerVoxelDemo", "Error creating demo player", e);
        }
    }
    
    /**
     * Create demo world objects.
     */
    private void createDemoWorldObjects() {
        // Create some static world objects for demonstration
        logManager.info("MultiplayerVoxelDemo", "Creating demo world objects");
        
        // TODO: Create demo blocks, items, etc.
        // This would integrate with the existing voxel world system
    }
    
    /**
     * Update server game logic.
     */
    private void updateServerGameLogic(long deltaTime) {
        // Simulate some server-side game logic
        
        // Move demo players in a simple pattern
        for (NetworkEntity player : players.values()) {
            if (player.isNetworkEnabled()) {
                // Simple circular movement for demo
                long time = System.currentTimeMillis();
                float angle = (time / 1000.0f) * 0.5f; // Slow rotation
                
                float x = (float) Math.cos(angle) * 3.0f;
                float z = (float) Math.sin(angle) * 3.0f;
                
                player.setLocalPosition(x, 10.0f, z);
            }
        }
    }
    
    /**
     * Update client game logic.
     */
    private void updateClientGameLogic(long deltaTime) {
        // Simulate client-side prediction and interpolation
        
        // TODO: Implement client-side prediction
        // TODO: Handle server corrections
        // TODO: Interpolate remote entities
    }
    
    /**
     * Update network entities.
     */
    private void updateNetworkEntities(long deltaTime) {
        for (NetworkEntity entity : players.values()) {
            if (entity.isNetworkEnabled()) {
                entity.updateNetwork(deltaTime / 1000.0f);
            }
        }
    }
    
    /**
     * Process network messages.
     */
    private void processNetworkMessages() {
        // TODO: Process incoming network messages
        // This would handle player input, chat messages, game events, etc.
    }
    
    /**
     * Send player input to server (client-side).
     */
    private void sendPlayerInput() {
        if (isServer) {
            return; // Server doesn't send input
        }
        
        // Simulate player input
        int inputFlags = 0;
        if (Math.random() > 0.7) inputFlags |= PlayerInputMessage.INPUT_MOVE_FORWARD;
        if (Math.random() > 0.9) inputFlags |= PlayerInputMessage.INPUT_JUMP;
        
        Vector3f mouseMovement = new Vector3f((float) (Math.random() - 0.5) * 2.0f, 0, 0);
        Vector3f playerPosition = new Vector3f(0, 0, 0); // Would be actual player position
        Vector3f playerRotation = new Vector3f(0, 0, 0); // Would be actual player rotation
        
        PlayerInputMessage inputMessage = new PlayerInputMessage(
            1, // Client ID
            inputFlags,
            mouseMovement,
            playerPosition,
            playerRotation,
            (int) (System.currentTimeMillis() % Integer.MAX_VALUE)
        );
        
        // TODO: Send message through network system
        networkDebugger.logPacket("SENT", "client-1", inputMessage.getSerializedSize(), "PLAYER_INPUT");
    }
    
    /**
     * Shutdown networking systems.
     */
    private void shutdown() {
        logManager.info("MultiplayerVoxelDemo", "Shutting down multiplayer demo");
        
        try {
            // Generate final debug report
            if (networkDebugger != null) {
                String debugReport = networkDebugger.generateDebugReport();
                logManager.info("MultiplayerVoxelDemo", "Final network debug report:\n" + debugReport);
                
                networkDebugger.shutdown();
            }
            
            // Shutdown network manager
            if (networkManager != null) {
                networkManager.shutdown();
            }
            
            // Clear entities
            players.clear();
            
            logManager.info("MultiplayerVoxelDemo", "Multiplayer demo shutdown complete");
            
        } catch (Exception e) {
            logManager.error("MultiplayerVoxelDemo", "Error during shutdown", e);
        }
    }
    
    /**
     * Main method for running the demo.
     */
    public static void main(String[] args) {
        MultiplayerVoxelDemo demo = new MultiplayerVoxelDemo();
        
        if (args.length > 0 && "client".equals(args[0])) {
            String serverAddress = args.length > 1 ? args[1] : "localhost";
            demo.runAsClient(serverAddress);
        } else {
            demo.runAsServer();
        }
    }
    
    /**
     * Demonstrate network statistics and monitoring.
     */
    public void demonstrateNetworkMonitoring() {
        if (networkDebugger == null) {
            return;
        }
        
        // Get current statistics
        NetworkDebugger.NetworkStatistics stats = networkDebugger.getNetworkStatistics();
        
        logManager.info("MultiplayerVoxelDemo", "Network monitoring demonstration",
                       "activeConnections", stats.activeConnections,
                       "totalPacketsSent", stats.totalPacketsSent,
                       "totalPacketsReceived", stats.totalPacketsReceived,
                       "averageLatency", String.format("%.2f ms", stats.averageLatency),
                       "throughput", String.format("%.2f KB/s", stats.throughputBytesPerSecond / 1024.0));
        
        // Demonstrate performance metrics
        var performanceMetrics = networkDebugger.getPerformanceMetrics();
        for (var metric : performanceMetrics.values()) {
            logManager.info("MultiplayerVoxelDemo", "Performance metric",
                           "name", metric.getName(),
                           "average", String.format("%.2f", metric.getAverage()),
                           "count", metric.getCount());
        }
        
        // Demonstrate connection debug info
        var connectionInfo = networkDebugger.getConnectionDebugInfo();
        for (var info : connectionInfo.values()) {
            logManager.info("MultiplayerVoxelDemo", "Connection debug info",
                           "connectionId", info.connectionId,
                           "duration", info.getConnectionDuration() + "ms",
                           "packetsSent", info.packetsSent,
                           "packetsReceived", info.packetsReceived);
        }
    }
}