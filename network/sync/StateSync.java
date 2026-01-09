package engine.network.sync;

import engine.logging.LogManager;
import engine.network.NetworkConfiguration;
import engine.network.protocol.NetworkProtocol;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Entity state synchronization with delta compression.
 * Efficiently synchronizes entity states across network using delta compression.
 */
public class StateSync {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final NetworkConfiguration config;
    private final NetworkProtocol protocol;
    
    // Entity state tracking
    private final ConcurrentHashMap<Integer, EntityState> entityStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientStateTracker> clientStateTrackers = new ConcurrentHashMap<>();
    
    // Delta compression
    private final DeltaCompressor deltaCompressor = new DeltaCompressor();
    
    // Statistics
    private final AtomicLong stateUpdatesProcessed = new AtomicLong(0);
    private final AtomicLong deltaCompressionSavings = new AtomicLong(0);
    private final AtomicLong bytesTransmitted = new AtomicLong(0);
    
    private volatile boolean initialized = false;
    private volatile boolean running = false;
    
    public StateSync(NetworkConfiguration config, NetworkProtocol protocol) {
        this.config = config;
        this.protocol = protocol;
    }
    
    /**
     * Initialize the state synchronization system.
     */
    public void initialize() throws Exception {
        if (initialized) {
            return;
        }
        
        deltaCompressor.initialize();
        
        initialized = true;
        
        logManager.info("StateSync", "State synchronization system initialized",
                       "deltaCompressionEnabled", config.isDeltaCompressionEnabled());
    }
    
    /**
     * Start the state synchronization system.
     */
    public void start() throws Exception {
        if (!initialized) {
            throw new IllegalStateException("StateSync must be initialized before starting");
        }
        
        if (running) {
            return;
        }
        
        running = true;
        
        logManager.info("StateSync", "State synchronization system started");
    }
    
    /**
     * Stop the state synchronization system.
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        
        logManager.info("StateSync", "State synchronization system stopped");
    }
    
    /**
     * Shutdown the state synchronization system.
     */
    public void shutdown() {
        stop();
        
        if (!initialized) {
            return;
        }
        
        entityStates.clear();
        clientStateTrackers.clear();
        
        initialized = false;
        
        logManager.info("StateSync", "State synchronization system shutdown complete");
    }
    
    /**
     * Update the state synchronization system.
     */
    public void update(long deltaTime) {
        if (!running) {
            return;
        }
        
        try {
            // Update client state trackers
            updateClientStateTrackers(deltaTime);
            
            // Clean up old entity states
            cleanupOldEntityStates();
            
        } catch (Exception e) {
            logManager.error("StateSync", "Error updating state synchronization", e);
        }
    }
    
    /**
     * Register an entity for state synchronization.
     */
    public void registerEntity(int entityId, EntityState initialState) {
        entityStates.put(entityId, initialState);
        
        logManager.debug("StateSync", "Entity registered for synchronization",
                        "entityId", entityId,
                        "stateSize", initialState.getSerializedSize());
    }
    
    /**
     * Unregister an entity from state synchronization.
     */
    public void unregisterEntity(int entityId) {
        EntityState removed = entityStates.remove(entityId);
        
        if (removed != null) {
            logManager.debug("StateSync", "Entity unregistered from synchronization",
                           "entityId", entityId);
        }
    }
    
    /**
     * Update entity state.
     */
    public void updateEntityState(int entityId, EntityState newState) {
        EntityState previousState = entityStates.put(entityId, newState);
        
        if (previousState != null) {
            stateUpdatesProcessed.incrementAndGet();
            
            // Generate delta if compression is enabled
            if (config.isDeltaCompressionEnabled()) {
                byte[] delta = deltaCompressor.generateDelta(previousState, newState);
                if (delta.length < newState.getSerializedSize()) {
                    long savings = newState.getSerializedSize() - delta.length;
                    deltaCompressionSavings.addAndGet(savings);
                }
            }
        }
    }
    
    /**
     * Get current entity state.
     */
    public EntityState getEntityState(int entityId) {
        return entityStates.get(entityId);
    }
    
    /**
     * Generate state update for client.
     */
    public byte[] generateStateUpdateForClient(String clientId) {
        ClientStateTracker tracker = getOrCreateClientStateTracker(clientId);
        
        try {
            ByteBuffer buffer = ByteBuffer.allocate(65536); // 64KB buffer
            int entityCount = 0;
            
            // Write header
            buffer.putInt(0); // Placeholder for entity count
            buffer.putLong(System.currentTimeMillis()); // Timestamp
            
            // Process each entity
            for (EntityState currentState : entityStates.values()) {
                EntityState lastSentState = tracker.getLastSentState(currentState.getEntityId());
                
                byte[] stateData;
                boolean isDelta = false;
                
                if (lastSentState != null && config.isDeltaCompressionEnabled()) {
                    // Generate delta
                    byte[] delta = deltaCompressor.generateDelta(lastSentState, currentState);
                    if (delta.length < currentState.getSerializedSize()) {
                        stateData = delta;
                        isDelta = true;
                    } else {
                        stateData = currentState.serialize();
                    }
                } else {
                    // Send full state
                    stateData = currentState.serialize();
                }
                
                // Check if we have space in buffer
                if (buffer.remaining() < 12 + stateData.length) {
                    break; // Buffer full
                }
                
                // Write entity update
                buffer.putInt(currentState.getEntityId());
                buffer.put((byte) (isDelta ? 1 : 0));
                buffer.putInt(stateData.length);
                buffer.put(stateData);
                
                // Update tracker
                tracker.updateLastSentState(currentState.getEntityId(), currentState);
                
                entityCount++;
            }
            
            // Update entity count in header
            buffer.putInt(0, entityCount);
            
            // Prepare final data
            byte[] result = new byte[buffer.position()];
            buffer.rewind();
            buffer.get(result);
            
            bytesTransmitted.addAndGet(result.length);
            
            return result;
            
        } catch (Exception e) {
            logManager.error("StateSync", "Error generating state update for client", e,
                           "clientId", clientId);
            return new byte[0];
        }
    }
    
    /**
     * Process state update from server.
     */
    public void processStateUpdateFromServer(byte[] updateData) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(updateData);
            
            // Read header
            int entityCount = buffer.getInt();
            long timestamp = buffer.getLong();
            
            // Process each entity update
            for (int i = 0; i < entityCount; i++) {
                int entityId = buffer.getInt();
                boolean isDelta = buffer.get() == 1;
                int dataLength = buffer.getInt();
                
                byte[] stateData = new byte[dataLength];
                buffer.get(stateData);
                
                EntityState newState;
                
                if (isDelta) {
                    // Apply delta to existing state
                    EntityState currentState = entityStates.get(entityId);
                    if (currentState != null) {
                        newState = deltaCompressor.applyDelta(currentState, stateData);
                    } else {
                        logManager.warn("StateSync", "Received delta for unknown entity",
                                       "entityId", entityId);
                        continue;
                    }
                } else {
                    // Deserialize full state
                    newState = EntityState.deserialize(entityId, stateData);
                }
                
                // Update entity state
                entityStates.put(entityId, newState);
                stateUpdatesProcessed.incrementAndGet();
            }
            
        } catch (Exception e) {
            logManager.error("StateSync", "Error processing state update from server", e);
        }
    }
    
    /**
     * Get or create client state tracker.
     */
    private ClientStateTracker getOrCreateClientStateTracker(String clientId) {
        return clientStateTrackers.computeIfAbsent(clientId, 
            k -> new ClientStateTracker(clientId));
    }
    
    /**
     * Update all client state trackers.
     */
    private void updateClientStateTrackers(long deltaTime) {
        for (ClientStateTracker tracker : clientStateTrackers.values()) {
            tracker.update(deltaTime);
        }
    }
    
    /**
     * Clean up old entity states.
     */
    private void cleanupOldEntityStates() {
        // Remove entities that haven't been updated in a while
        long currentTime = System.currentTimeMillis();
        final long maxAge = 60000; // 1 minute
        
        entityStates.entrySet().removeIf(entry -> {
            EntityState state = entry.getValue();
            return currentTime - state.getLastUpdateTime() > maxAge;
        });
    }
    
    /**
     * Collect statistics for monitoring.
     */
    public void collectStatistics(ConcurrentHashMap<String, Object> stats) {
        stats.put("stateSync.entitiesTracked", entityStates.size());
        stats.put("stateSync.clientTrackers", clientStateTrackers.size());
        stats.put("stateSync.updatesProcessed", stateUpdatesProcessed.get());
        stats.put("stateSync.compressionSavings", deltaCompressionSavings.get());
        stats.put("stateSync.bytesTransmitted", bytesTransmitted.get());
    }
    
    /**
     * Tracks the last sent state for each entity per client.
     */
    private static class ClientStateTracker {
        private final String clientId;
        private final ConcurrentHashMap<Integer, EntityState> lastSentStates = new ConcurrentHashMap<>();
        private volatile long lastUpdateTime;
        
        ClientStateTracker(String clientId) {
            this.clientId = clientId;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        EntityState getLastSentState(int entityId) {
            return lastSentStates.get(entityId);
        }
        
        void updateLastSentState(int entityId, EntityState state) {
            lastSentStates.put(entityId, state.copy());
            lastUpdateTime = System.currentTimeMillis();
        }
        
        void update(long deltaTime) {
            // Clean up old states
            long currentTime = System.currentTimeMillis();
            final long maxAge = 30000; // 30 seconds
            
            lastSentStates.entrySet().removeIf(entry -> 
                currentTime - entry.getValue().getLastUpdateTime() > maxAge);
        }
        
        String getClientId() { return clientId; }
        long getLastUpdateTime() { return lastUpdateTime; }
    }
}