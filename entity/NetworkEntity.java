package engine.entity;

import engine.logging.LogManager;
import engine.network.NetworkManager;
import engine.network.NetworkReplication;
import engine.network.sync.EntityState;
import engine.raster.Mesh;
import org.joml.Vector3f;

/**
 * Network-enabled entity that extends the base Entity class.
 * Provides automatic network synchronization and replication capabilities.
 */
public class NetworkEntity extends Entity {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Network properties
    private int networkId = -1;
    private EntityState networkState;
    private NetworkReplication.OwnershipType ownershipType = NetworkReplication.OwnershipType.SERVER;
    private NetworkReplication.ReplicationMode replicationMode = NetworkReplication.ReplicationMode.ALWAYS;
    private String ownerId = "server";
    
    // Network synchronization
    private boolean networkEnabled = false;
    private boolean isDirty = false;
    private long lastNetworkUpdate = 0;
    private final float networkUpdateThreshold = 0.01f; // Minimum change to trigger network update
    
    // Interpolation for smooth network updates
    private final Vector3f targetPosition = new Vector3f();
    private final Vector3f targetRotation = new Vector3f();
    private final Vector3f targetVelocity = new Vector3f();
    private boolean hasNetworkTarget = false;
    private float interpolationSpeed = 10.0f;
    
    // Network statistics
    private long networkUpdatesReceived = 0;
    private long networkUpdatesSent = 0;
    
    public NetworkEntity(Mesh mesh) {
        super(mesh);
        this.networkState = new EntityState(hashCode()); // Use hashCode as temporary entity ID
    }
    
    public NetworkEntity(Mesh mesh, int entityId) {
        super(mesh);
        this.networkState = new EntityState(entityId);
    }
    
    /**
     * Enable network synchronization for this entity.
     */
    public void enableNetworking(NetworkReplication.OwnershipType ownershipType, 
                                NetworkReplication.ReplicationMode replicationMode, 
                                String ownerId) {
        if (networkEnabled) {
            logManager.warn("NetworkEntity", "Network already enabled for entity", 
                           "entityId", getEntityId());
            return;
        }
        
        this.ownershipType = ownershipType;
        this.replicationMode = replicationMode;
        this.ownerId = ownerId;
        
        // Register with network replication system
        NetworkManager networkManager = NetworkManager.getInstance();
        if (networkManager.isInitialized()) {
            NetworkReplication replication = networkManager.getNetworkReplication();
            this.networkId = replication.registerEntity(getEntityId(), ownershipType, replicationMode, ownerId);
            this.networkState.setNetworkId(networkId);
            
            // Initialize network state with current entity state
            updateNetworkStateFromEntity();
            
            networkEnabled = true;
            
            logManager.info("NetworkEntity", "Network enabled for entity",
                           "entityId", getEntityId(),
                           "networkId", networkId,
                           "ownershipType", ownershipType,
                           "replicationMode", replicationMode);
        }
    }
    
    /**
     * Disable network synchronization for this entity.
     */
    public void disableNetworking() {
        if (!networkEnabled) {
            return;
        }
        
        // Unregister from network replication system
        NetworkManager networkManager = NetworkManager.getInstance();
        if (networkManager.isInitialized()) {
            NetworkReplication replication = networkManager.getNetworkReplication();
            replication.unregisterEntity(getEntityId());
        }
        
        networkEnabled = false;
        networkId = -1;
        
        logManager.info("NetworkEntity", "Network disabled for entity",
                       "entityId", getEntityId());
    }
    
    /**
     * Update entity with network interpolation.
     */
    public void updateNetwork(float deltaTime) {
        if (!networkEnabled) {
            return;
        }
        
        // Interpolate to network target if we have one
        if (hasNetworkTarget) {
            interpolateToNetworkTarget(deltaTime);
        }
        
        // Check if we need to send network updates (for owned entities)
        if (isOwnedByLocalClient() && isDirty) {
            sendNetworkUpdate();
        }
        
        // Reset dirty flag
        isDirty = false;
    }
    
    /**
     * Apply network state update from server.
     */
    public void applyNetworkUpdate(EntityState newState) {
        if (!networkEnabled) {
            return;
        }
        
        networkUpdatesReceived++;
        
        // Set network targets for interpolation
        targetPosition.set(newState.getPosition());
        targetRotation.set(newState.getRotation());
        targetVelocity.set(newState.getVelocity());
        hasNetworkTarget = true;
        
        // Update network state
        this.networkState = newState.copy();
        
        // If we're not the owner, apply the update immediately for non-transform properties
        if (!isOwnedByLocalClient()) {
            // Apply non-interpolated properties immediately
            // Transform properties will be interpolated in updateNetwork()
        }
        
        logManager.debug("NetworkEntity", "Network update applied",
                        "entityId", getEntityId(),
                        "networkId", networkId,
                        "position", newState.getPosition());
    }
    
    /**
     * Interpolate entity position/rotation to network target.
     */
    private void interpolateToNetworkTarget(float deltaTime) {
        if (!hasNetworkTarget) {
            return;
        }
        
        Vector3f currentPos = getWorldPosition();
        Vector3f currentRot = getWorldRotation();
        
        // Calculate interpolation factor
        float lerpFactor = Math.min(1.0f, interpolationSpeed * deltaTime);
        
        // Interpolate position
        Vector3f newPos = new Vector3f(currentPos).lerp(targetPosition, lerpFactor);
        setWorldPosition(newPos);
        
        // Interpolate rotation
        Vector3f newRot = new Vector3f(currentRot).lerp(targetRotation, lerpFactor);
        setWorldRotation(newRot);
        
        // Check if we're close enough to target
        float positionDistance = currentPos.distance(targetPosition);
        float rotationDistance = currentRot.distance(targetRotation);
        
        if (positionDistance < 0.01f && rotationDistance < 0.01f) {
            hasNetworkTarget = false;
        }
    }
    
    /**
     * Send network update for this entity.
     */
    private void sendNetworkUpdate() {
        if (!networkEnabled || !isOwnedByLocalClient()) {
            return;
        }
        
        // Update network state from current entity state
        updateNetworkStateFromEntity();
        
        // Send update through network replication system
        NetworkManager networkManager = NetworkManager.getInstance();
        if (networkManager.isInitialized()) {
            NetworkReplication replication = networkManager.getNetworkReplication();
            replication.updateEntityState(getEntityId(), networkState);
            
            networkUpdatesSent++;
            lastNetworkUpdate = System.currentTimeMillis();
        }
    }
    
    /**
     * Update network state from current entity transform.
     */
    private void updateNetworkStateFromEntity() {
        Vector3f worldPos = getWorldPosition();
        Vector3f worldRot = getWorldRotation();
        
        networkState.setPosition(worldPos);
        networkState.setRotation(worldRot);
        // Note: Velocity would need to be calculated or tracked separately
        networkState.markUpdated();
    }
    
    /**
     * Get world position (accounting for parent transforms).
     */
    private Vector3f getWorldPosition() {
        // Extract position from world transform matrix
        Vector3f position = new Vector3f();
        getWorldTransform().getTranslation(position);
        return position;
    }
    
    /**
     * Get world rotation (accounting for parent transforms).
     */
    private Vector3f getWorldRotation() {
        // For simplicity, return local rotation
        // In a full implementation, this would extract rotation from world transform
        return new Vector3f(0, 0, 0); // Placeholder
    }
    
    /**
     * Set world position.
     */
    private void setWorldPosition(Vector3f position) {
        // For simplicity, set local position
        // In a full implementation, this would account for parent transforms
        setLocalPosition(position.x, position.y, position.z);
        markDirty();
    }
    
    /**
     * Set world rotation.
     */
    private void setWorldRotation(Vector3f rotation) {
        // For simplicity, set local rotation
        setLocalRotation(rotation.x, rotation.y, rotation.z);
        markDirty();
    }
    
    /**
     * Mark entity as dirty for network updates.
     */
    private void markDirty() {
        if (networkEnabled) {
            isDirty = true;
        }
    }
    
    /**
     * Check if this entity is owned by the local client.
     */
    private boolean isOwnedByLocalClient() {
        // TODO: Implement proper client ID checking
        // For now, assume server ownership means local if we're the server
        return ownershipType == NetworkReplication.OwnershipType.SERVER;
    }
    
    // Override Entity methods to trigger network updates
    @Override
    public void setLocalPosition(float x, float y, float z) {
        super.setLocalPosition(x, y, z);
        markDirty();
    }
    
    @Override
    public void setLocalRotation(float x, float y, float z) {
        super.setLocalRotation(x, y, z);
        markDirty();
    }
    
    @Override
    public void setLocalScale(float scale) {
        super.setLocalScale(scale);
        markDirty();
    }
    
    // Network property getters and setters
    public int getNetworkId() { return networkId; }
    public EntityState getNetworkState() { return networkState; }
    public boolean isNetworkEnabled() { return networkEnabled; }
    public NetworkReplication.OwnershipType getOwnershipType() { return ownershipType; }
    public NetworkReplication.ReplicationMode getReplicationMode() { return replicationMode; }
    public String getOwnerId() { return ownerId; }
    
    public void setOwnership(String newOwnerId) {
        if (!networkEnabled) {
            return;
        }
        
        String oldOwnerId = this.ownerId;
        this.ownerId = newOwnerId;
        
        // Notify network replication system
        NetworkManager networkManager = NetworkManager.getInstance();
        if (networkManager.isInitialized()) {
            NetworkReplication replication = networkManager.getNetworkReplication();
            replication.changeOwnership(getEntityId(), newOwnerId);
        }
        
        logManager.info("NetworkEntity", "Entity ownership changed",
                       "entityId", getEntityId(),
                       "oldOwner", oldOwnerId,
                       "newOwner", newOwnerId);
    }
    
    public void setInterpolationSpeed(float speed) {
        this.interpolationSpeed = speed;
    }
    
    // Network statistics
    public long getNetworkUpdatesReceived() { return networkUpdatesReceived; }
    public long getNetworkUpdatesSent() { return networkUpdatesSent; }
    public long getLastNetworkUpdate() { return lastNetworkUpdate; }
    
    /**
     * Get entity ID (using hashCode as fallback).
     */
    private int getEntityId() {
        return networkState != null ? networkState.getEntityId() : hashCode();
    }
    
    @Override
    public String toString() {
        return "NetworkEntity{" +
               "entityId=" + getEntityId() +
               ", networkId=" + networkId +
               ", networkEnabled=" + networkEnabled +
               ", ownershipType=" + ownershipType +
               ", ownerId='" + ownerId + '\'' +
               '}';
    }
}