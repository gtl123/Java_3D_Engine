package engine.network;

import engine.logging.LogManager;
import engine.network.protocol.NetworkProtocol;
import engine.network.sync.EntityState;
import engine.network.sync.StateSync;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Network replication system for entity ownership and replication.
 * Manages which entities are replicated to which clients and handles ownership.
 */
public class NetworkReplication {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    public enum ReplicationMode {
        ALWAYS,           // Always replicate to all clients
        OWNER_ONLY,       // Only replicate to owner
        PROXIMITY_BASED,  // Replicate based on distance
        INTEREST_BASED    // Replicate based on client interest
    }
    
    public enum OwnershipType {
        SERVER,           // Server owns the entity
        CLIENT,           // Client owns the entity
        SHARED            // Shared ownership
    }
    
    private final NetworkConfiguration config;
    private final StateSync stateSync;
    private final NetworkProtocol protocol;
    
    // Entity replication tracking
    private final ConcurrentHashMap<Integer, ReplicatedEntity> replicatedEntities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientReplicationState> clientStates = new ConcurrentHashMap<>();
    
    // Network ID management
    private final AtomicInteger nextNetworkId = new AtomicInteger(1);
    
    // Statistics
    private final AtomicLong entitiesReplicated = new AtomicLong(0);
    private final AtomicLong replicationUpdates = new AtomicLong(0);
    private final AtomicLong ownershipChanges = new AtomicLong(0);
    
    private volatile boolean initialized = false;
    private volatile boolean running = false;
    
    public NetworkReplication(NetworkConfiguration config, StateSync stateSync, NetworkProtocol protocol) {
        this.config = config;
        this.stateSync = stateSync;
        this.protocol = protocol;
    }
    
    /**
     * Initialize the network replication system.
     */
    public void initialize() throws NetworkException {
        if (initialized) {
            return;
        }
        
        initialized = true;
        
        logManager.info("NetworkReplication", "Network replication system initialized");
    }
    
    /**
     * Start the network replication system.
     */
    public void start() throws NetworkException {
        if (!initialized) {
            throw new IllegalStateException("NetworkReplication must be initialized before starting");
        }
        
        if (running) {
            return;
        }
        
        running = true;
        
        logManager.info("NetworkReplication", "Network replication system started");
    }
    
    /**
     * Stop the network replication system.
     */
    public void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        
        logManager.info("NetworkReplication", "Network replication system stopped");
    }
    
    /**
     * Shutdown the network replication system.
     */
    public void shutdown() {
        stop();
        
        if (!initialized) {
            return;
        }
        
        replicatedEntities.clear();
        clientStates.clear();
        
        initialized = false;
        
        logManager.info("NetworkReplication", "Network replication system shutdown complete");
    }
    
    /**
     * Update the network replication system.
     */
    public void update(long deltaTime) {
        if (!running) {
            return;
        }
        
        try {
            // Update replication for all entities
            updateEntityReplication(deltaTime);
            
            // Update client replication states
            updateClientReplicationStates(deltaTime);
            
            // Clean up destroyed entities
            cleanupDestroyedEntities();
            
        } catch (Exception e) {
            logManager.error("NetworkReplication", "Error updating network replication", e);
        }
    }
    
    /**
     * Register an entity for network replication.
     */
    public int registerEntity(int entityId, OwnershipType ownershipType, ReplicationMode replicationMode, String ownerId) {
        int networkId = nextNetworkId.getAndIncrement();
        
        ReplicatedEntity replicatedEntity = new ReplicatedEntity(
            entityId, networkId, ownershipType, replicationMode, ownerId);
        
        replicatedEntities.put(entityId, replicatedEntity);
        entitiesReplicated.incrementAndGet();
        
        // Register with state sync
        EntityState initialState = new EntityState(entityId);
        initialState.setNetworkId(networkId);
        stateSync.registerEntity(entityId, initialState);
        
        logManager.info("NetworkReplication", "Entity registered for replication",
                       "entityId", entityId,
                       "networkId", networkId,
                       "ownershipType", ownershipType,
                       "replicationMode", replicationMode,
                       "ownerId", ownerId);
        
        return networkId;
    }
    
    /**
     * Unregister an entity from network replication.
     */
    public void unregisterEntity(int entityId) {
        ReplicatedEntity removed = replicatedEntities.remove(entityId);
        
        if (removed != null) {
            // Unregister from state sync
            stateSync.unregisterEntity(entityId);
            
            // Notify all clients about entity destruction
            notifyEntityDestroyed(removed);
            
            logManager.info("NetworkReplication", "Entity unregistered from replication",
                           "entityId", entityId,
                           "networkId", removed.networkId);
        }
    }
    
    /**
     * Change entity ownership.
     */
    public void changeOwnership(int entityId, String newOwnerId) {
        ReplicatedEntity entity = replicatedEntities.get(entityId);
        
        if (entity != null) {
            String oldOwnerId = entity.ownerId;
            entity.ownerId = newOwnerId;
            ownershipChanges.incrementAndGet();
            
            logManager.info("NetworkReplication", "Entity ownership changed",
                           "entityId", entityId,
                           "oldOwner", oldOwnerId,
                           "newOwner", newOwnerId);
            
            // Notify clients about ownership change
            notifyOwnershipChanged(entity, oldOwnerId, newOwnerId);
        }
    }
    
    /**
     * Update entity state for replication.
     */
    public void updateEntityState(int entityId, EntityState newState) {
        ReplicatedEntity entity = replicatedEntities.get(entityId);
        
        if (entity != null) {
            // Update state sync
            stateSync.updateEntityState(entityId, newState);
            replicationUpdates.incrementAndGet();
            
            // Determine which clients should receive this update
            for (ClientReplicationState clientState : clientStates.values()) {
                if (shouldReplicateToClient(entity, clientState)) {
                    clientState.markEntityForUpdate(entityId);
                }
            }
        }
    }
    
    /**
     * Handle client connected event.
     */
    public void onClientConnected(ClientServerArchitecture.ClientInfo clientInfo) {
        String clientId = clientInfo.connection.getConnectionId();
        ClientReplicationState clientState = new ClientReplicationState(clientId, clientInfo);
        clientStates.put(clientId, clientState);
        
        // Send initial entity states to new client
        sendInitialStatesToClient(clientState);
        
        logManager.info("NetworkReplication", "Client connected to replication system",
                       "clientId", clientId);
    }
    
    /**
     * Handle client disconnected event.
     */
    public void onClientDisconnected(ClientServerArchitecture.ClientInfo clientInfo) {
        String clientId = clientInfo.connection.getConnectionId();
        ClientReplicationState removed = clientStates.remove(clientId);
        
        if (removed != null) {
            // Transfer ownership of client-owned entities to server
            transferClientOwnedEntities(clientId);
            
            logManager.info("NetworkReplication", "Client disconnected from replication system",
                           "clientId", clientId);
        }
    }
    
    /**
     * Update entity replication for all entities.
     */
    private void updateEntityReplication(long deltaTime) {
        for (ReplicatedEntity entity : replicatedEntities.values()) {
            try {
                updateSingleEntityReplication(entity, deltaTime);
            } catch (Exception e) {
                logManager.error("NetworkReplication", "Error updating entity replication", e,
                               "entityId", entity.entityId);
            }
        }
    }
    
    /**
     * Update replication for a single entity.
     */
    private void updateSingleEntityReplication(ReplicatedEntity entity, long deltaTime) {
        // Update entity's replication state
        entity.update(deltaTime);
        
        // Check if entity needs replication updates
        if (entity.needsUpdate()) {
            for (ClientReplicationState clientState : clientStates.values()) {
                if (shouldReplicateToClient(entity, clientState)) {
                    clientState.markEntityForUpdate(entity.entityId);
                }
            }
            entity.markUpdated();
        }
    }
    
    /**
     * Update client replication states.
     */
    private void updateClientReplicationStates(long deltaTime) {
        for (ClientReplicationState clientState : clientStates.values()) {
            try {
                clientState.update(deltaTime);
                
                // Send pending updates to client
                sendUpdatesToClient(clientState);
                
            } catch (Exception e) {
                logManager.error("NetworkReplication", "Error updating client replication state", e,
                               "clientId", clientState.clientId);
            }
        }
    }
    
    /**
     * Determine if entity should be replicated to client.
     */
    private boolean shouldReplicateToClient(ReplicatedEntity entity, ClientReplicationState clientState) {
        switch (entity.replicationMode) {
            case ALWAYS:
                return true;
                
            case OWNER_ONLY:
                return entity.ownerId.equals(clientState.clientId);
                
            case PROXIMITY_BASED:
                return isWithinProximity(entity, clientState);
                
            case INTEREST_BASED:
                return clientState.isInterestedInEntity(entity.entityId);
                
            default:
                return false;
        }
    }
    
    /**
     * Check if entity is within proximity of client.
     */
    private boolean isWithinProximity(ReplicatedEntity entity, ClientReplicationState clientState) {
        // TODO: Implement proximity-based replication
        // This would require client position tracking and distance calculations
        return true; // Placeholder - replicate to all for now
    }
    
    /**
     * Send initial entity states to newly connected client.
     */
    private void sendInitialStatesToClient(ClientReplicationState clientState) {
        for (ReplicatedEntity entity : replicatedEntities.values()) {
            if (shouldReplicateToClient(entity, clientState)) {
                clientState.markEntityForUpdate(entity.entityId);
            }
        }
    }
    
    /**
     * Send pending updates to client.
     */
    private void sendUpdatesToClient(ClientReplicationState clientState) {
        if (clientState.hasPendingUpdates()) {
            try {
                byte[] updateData = stateSync.generateStateUpdateForClient(clientState.clientId);
                
                if (updateData.length > 0) {
                    // Send through protocol
                    clientState.clientInfo.connection.sendPacket(updateData);
                    clientState.clearPendingUpdates();
                }
                
            } catch (Exception e) {
                logManager.error("NetworkReplication", "Error sending updates to client", e,
                               "clientId", clientState.clientId);
            }
        }
    }
    
    /**
     * Notify clients about entity destruction.
     */
    private void notifyEntityDestroyed(ReplicatedEntity entity) {
        // TODO: Implement entity destruction notification
        logManager.debug("NetworkReplication", "Entity destruction notification",
                        "entityId", entity.entityId);
    }
    
    /**
     * Notify clients about ownership change.
     */
    private void notifyOwnershipChanged(ReplicatedEntity entity, String oldOwnerId, String newOwnerId) {
        // TODO: Implement ownership change notification
        logManager.debug("NetworkReplication", "Ownership change notification",
                        "entityId", entity.entityId,
                        "oldOwner", oldOwnerId,
                        "newOwner", newOwnerId);
    }
    
    /**
     * Transfer client-owned entities to server when client disconnects.
     */
    private void transferClientOwnedEntities(String clientId) {
        for (ReplicatedEntity entity : replicatedEntities.values()) {
            if (clientId.equals(entity.ownerId) && entity.ownershipType == OwnershipType.CLIENT) {
                entity.ownerId = "server";
                entity.ownershipType = OwnershipType.SERVER;
                ownershipChanges.incrementAndGet();
                
                logManager.info("NetworkReplication", "Entity ownership transferred to server",
                               "entityId", entity.entityId,
                               "previousOwner", clientId);
            }
        }
    }
    
    /**
     * Clean up destroyed entities.
     */
    private void cleanupDestroyedEntities() {
        // Remove entities marked for destruction
        replicatedEntities.entrySet().removeIf(entry -> {
            ReplicatedEntity entity = entry.getValue();
            return entity.isDestroyed();
        });
    }
    
    /**
     * Collect statistics for monitoring.
     */
    public void collectStatistics(ConcurrentHashMap<String, Object> stats) {
        stats.put("replication.entitiesReplicated", replicatedEntities.size());
        stats.put("replication.clientStates", clientStates.size());
        stats.put("replication.totalEntitiesReplicated", entitiesReplicated.get());
        stats.put("replication.replicationUpdates", replicationUpdates.get());
        stats.put("replication.ownershipChanges", ownershipChanges.get());
    }
    
    // Getters
    public int getReplicatedEntityCount() { return replicatedEntities.size(); }
    public int getClientStateCount() { return clientStates.size(); }
    
    /**
     * Represents a replicated entity with ownership and replication settings.
     */
    private static class ReplicatedEntity {
        final int entityId;
        final int networkId;
        OwnershipType ownershipType;
        final ReplicationMode replicationMode;
        String ownerId;
        
        private volatile long lastUpdateTime;
        private volatile boolean needsUpdate = false;
        private volatile boolean destroyed = false;
        
        ReplicatedEntity(int entityId, int networkId, OwnershipType ownershipType, 
                        ReplicationMode replicationMode, String ownerId) {
            this.entityId = entityId;
            this.networkId = networkId;
            this.ownershipType = ownershipType;
            this.replicationMode = replicationMode;
            this.ownerId = ownerId;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        void update(long deltaTime) {
            // Update logic for replicated entity
        }
        
        boolean needsUpdate() { return needsUpdate; }
        void markUpdated() { 
            needsUpdate = false; 
            lastUpdateTime = System.currentTimeMillis();
        }
        void markForUpdate() { needsUpdate = true; }
        
        boolean isDestroyed() { return destroyed; }
        void destroy() { destroyed = true; }
    }
    
    /**
     * Tracks replication state for a specific client.
     */
    private static class ClientReplicationState {
        final String clientId;
        final ClientServerArchitecture.ClientInfo clientInfo;
        
        private final ConcurrentHashMap<Integer, Long> pendingUpdates = new ConcurrentHashMap<>();
        private volatile long lastUpdateTime;
        
        ClientReplicationState(String clientId, ClientServerArchitecture.ClientInfo clientInfo) {
            this.clientId = clientId;
            this.clientInfo = clientInfo;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        void markEntityForUpdate(int entityId) {
            pendingUpdates.put(entityId, System.currentTimeMillis());
        }
        
        boolean hasPendingUpdates() {
            return !pendingUpdates.isEmpty();
        }
        
        void clearPendingUpdates() {
            pendingUpdates.clear();
            lastUpdateTime = System.currentTimeMillis();
        }
        
        boolean isInterestedInEntity(int entityId) {
            // TODO: Implement interest management
            return true; // Placeholder - interested in all entities
        }
        
        void update(long deltaTime) {
            // Update client replication state
        }
    }
}