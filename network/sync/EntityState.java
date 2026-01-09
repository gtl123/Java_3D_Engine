package engine.network.sync;

import org.joml.Vector3f;

import java.nio.ByteBuffer;

/**
 * Represents the network-synchronized state of an entity.
 * Contains position, rotation, velocity, and other synchronized properties.
 */
public class EntityState {
    
    private final int entityId;
    private final Vector3f position = new Vector3f();
    private final Vector3f rotation = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    private final Vector3f scale = new Vector3f(1.0f);
    
    // Additional state properties
    private float health = 100.0f;
    private int entityType = 0;
    private boolean visible = true;
    private long lastUpdateTime;
    
    // Network-specific properties
    private int networkId = -1;
    private boolean isDirty = false;
    
    public EntityState(int entityId) {
        this.entityId = entityId;
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Create a copy of this entity state.
     */
    public EntityState copy() {
        EntityState copy = new EntityState(entityId);
        copy.position.set(this.position);
        copy.rotation.set(this.rotation);
        copy.velocity.set(this.velocity);
        copy.scale.set(this.scale);
        copy.health = this.health;
        copy.entityType = this.entityType;
        copy.visible = this.visible;
        copy.networkId = this.networkId;
        copy.lastUpdateTime = this.lastUpdateTime;
        return copy;
    }
    
    /**
     * Serialize entity state to byte array.
     */
    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(getSerializedSize());
        
        // Entity identification
        buffer.putInt(entityId);
        buffer.putInt(networkId);
        buffer.putInt(entityType);
        
        // Position (3 floats)
        buffer.putFloat(position.x);
        buffer.putFloat(position.y);
        buffer.putFloat(position.z);
        
        // Rotation (3 floats)
        buffer.putFloat(rotation.x);
        buffer.putFloat(rotation.y);
        buffer.putFloat(rotation.z);
        
        // Velocity (3 floats)
        buffer.putFloat(velocity.x);
        buffer.putFloat(velocity.y);
        buffer.putFloat(velocity.z);
        
        // Scale (3 floats)
        buffer.putFloat(scale.x);
        buffer.putFloat(scale.y);
        buffer.putFloat(scale.z);
        
        // Additional properties
        buffer.putFloat(health);
        buffer.put((byte) (visible ? 1 : 0));
        buffer.putLong(lastUpdateTime);
        
        return buffer.array();
    }
    
    /**
     * Deserialize entity state from byte array.
     */
    public static EntityState deserialize(int entityId, byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        
        EntityState state = new EntityState(entityId);
        
        // Entity identification
        int deserializedEntityId = buffer.getInt();
        state.networkId = buffer.getInt();
        state.entityType = buffer.getInt();
        
        // Position
        state.position.set(
            buffer.getFloat(),
            buffer.getFloat(),
            buffer.getFloat()
        );
        
        // Rotation
        state.rotation.set(
            buffer.getFloat(),
            buffer.getFloat(),
            buffer.getFloat()
        );
        
        // Velocity
        state.velocity.set(
            buffer.getFloat(),
            buffer.getFloat(),
            buffer.getFloat()
        );
        
        // Scale
        state.scale.set(
            buffer.getFloat(),
            buffer.getFloat(),
            buffer.getFloat()
        );
        
        // Additional properties
        state.health = buffer.getFloat();
        state.visible = buffer.get() == 1;
        state.lastUpdateTime = buffer.getLong();
        
        return state;
    }
    
    /**
     * Get the serialized size in bytes.
     */
    public int getSerializedSize() {
        return 4 + 4 + 4 +           // entityId, networkId, entityType
               4 * 3 +               // position (3 floats)
               4 * 3 +               // rotation (3 floats)
               4 * 3 +               // velocity (3 floats)
               4 * 3 +               // scale (3 floats)
               4 +                   // health
               1 +                   // visible
               8;                    // lastUpdateTime
    }
    
    /**
     * Check if this state differs significantly from another state.
     */
    public boolean isDifferentFrom(EntityState other, float threshold) {
        if (other == null) return true;
        
        // Check position difference
        if (position.distance(other.position) > threshold) return true;
        
        // Check rotation difference
        if (rotation.distance(other.rotation) > threshold) return true;
        
        // Check velocity difference
        if (velocity.distance(other.velocity) > threshold) return true;
        
        // Check other properties
        if (Math.abs(health - other.health) > 0.1f) return true;
        if (visible != other.visible) return true;
        if (entityType != other.entityType) return true;
        
        return false;
    }
    
    /**
     * Interpolate between this state and another state.
     */
    public EntityState interpolate(EntityState target, float alpha) {
        EntityState result = this.copy();
        
        // Interpolate position
        result.position.lerp(target.position, alpha);
        
        // Interpolate rotation (simple linear interpolation)
        result.rotation.lerp(target.rotation, alpha);
        
        // Interpolate velocity
        result.velocity.lerp(target.velocity, alpha);
        
        // Interpolate scale
        result.scale.lerp(target.scale, alpha);
        
        // Interpolate health
        result.health = this.health + (target.health - this.health) * alpha;
        
        // Non-interpolated properties
        result.visible = target.visible;
        result.entityType = target.entityType;
        result.lastUpdateTime = target.lastUpdateTime;
        
        return result;
    }
    
    /**
     * Update the last update time to current time.
     */
    public void markUpdated() {
        this.lastUpdateTime = System.currentTimeMillis();
        this.isDirty = true;
    }
    
    // Getters and setters
    public int getEntityId() { return entityId; }
    public int getNetworkId() { return networkId; }
    public void setNetworkId(int networkId) { this.networkId = networkId; }
    
    public Vector3f getPosition() { return position; }
    public void setPosition(float x, float y, float z) { 
        position.set(x, y, z); 
        markUpdated();
    }
    public void setPosition(Vector3f pos) { 
        position.set(pos); 
        markUpdated();
    }
    
    public Vector3f getRotation() { return rotation; }
    public void setRotation(float x, float y, float z) { 
        rotation.set(x, y, z); 
        markUpdated();
    }
    public void setRotation(Vector3f rot) { 
        rotation.set(rot); 
        markUpdated();
    }
    
    public Vector3f getVelocity() { return velocity; }
    public void setVelocity(float x, float y, float z) { 
        velocity.set(x, y, z); 
        markUpdated();
    }
    public void setVelocity(Vector3f vel) { 
        velocity.set(vel); 
        markUpdated();
    }
    
    public Vector3f getScale() { return scale; }
    public void setScale(float x, float y, float z) { 
        scale.set(x, y, z); 
        markUpdated();
    }
    public void setScale(Vector3f scl) { 
        scale.set(scl); 
        markUpdated();
    }
    
    public float getHealth() { return health; }
    public void setHealth(float health) { 
        this.health = health; 
        markUpdated();
    }
    
    public int getEntityType() { return entityType; }
    public void setEntityType(int entityType) { 
        this.entityType = entityType; 
        markUpdated();
    }
    
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { 
        this.visible = visible; 
        markUpdated();
    }
    
    public long getLastUpdateTime() { return lastUpdateTime; }
    public boolean isDirty() { return isDirty; }
    public void clearDirty() { isDirty = false; }
    
    @Override
    public String toString() {
        return "EntityState{" +
               "entityId=" + entityId +
               ", networkId=" + networkId +
               ", position=" + position +
               ", rotation=" + rotation +
               ", velocity=" + velocity +
               ", health=" + health +
               ", visible=" + visible +
               '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        EntityState that = (EntityState) obj;
        return entityId == that.entityId &&
               networkId == that.networkId &&
               Float.compare(that.health, health) == 0 &&
               entityType == that.entityType &&
               visible == that.visible &&
               position.equals(that.position) &&
               rotation.equals(that.rotation) &&
               velocity.equals(that.velocity) &&
               scale.equals(that.scale);
    }
    
    @Override
    public int hashCode() {
        return entityId;
    }
}