package engine.network.messages;

import engine.network.protocol.NetworkProtocol;
import engine.network.sync.EntityState;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Network message for entity state updates.
 * Contains synchronized entity states for multiple entities.
 */
public class EntityUpdateMessage extends NetworkMessage {
    
    private final List<EntityState> entityStates;
    private final boolean isDeltaUpdate;
    
    public EntityUpdateMessage(int senderId, List<EntityState> entityStates, boolean isDeltaUpdate) {
        super(NetworkProtocol.MessageType.ENTITY_UPDATE, senderId);
        this.entityStates = new ArrayList<>(entityStates);
        this.isDeltaUpdate = isDeltaUpdate;
    }
    
    @Override
    protected void serializeData(ByteBuffer buffer) {
        buffer.put((byte) (isDeltaUpdate ? 1 : 0));
        buffer.putInt(entityStates.size());
        
        for (EntityState state : entityStates) {
            byte[] stateData = state.serialize();
            buffer.putInt(stateData.length);
            buffer.put(stateData);
        }
    }
    
    @Override
    protected int getDataSize() {
        int size = 1 + 4; // isDeltaUpdate + entity count
        
        for (EntityState state : entityStates) {
            size += 4; // state data length
            size += state.getSerializedSize();
        }
        
        return size;
    }
    
    public static EntityUpdateMessage deserializeData(ByteBuffer buffer, int senderId, long timestamp) {
        boolean isDeltaUpdate = buffer.get() == 1;
        int entityCount = buffer.getInt();
        
        List<EntityState> entityStates = new ArrayList<>();
        
        for (int i = 0; i < entityCount; i++) {
            int stateDataLength = buffer.getInt();
            byte[] stateData = new byte[stateDataLength];
            buffer.get(stateData);
            
            // Note: EntityState.deserialize needs entity ID, which should be in the data
            // For now, we'll use a placeholder implementation
            EntityState state = EntityState.deserialize(0, stateData);
            entityStates.add(state);
        }
        
        return new EntityUpdateMessage(senderId, entityStates, isDeltaUpdate);
    }
    
    // Getters
    public List<EntityState> getEntityStates() { return new ArrayList<>(entityStates); }
    public boolean isDeltaUpdate() { return isDeltaUpdate; }
    public int getEntityCount() { return entityStates.size(); }
    
    @Override
    public String toString() {
        return "EntityUpdateMessage{" +
               "senderId=" + getSenderId() +
               ", entityCount=" + entityStates.size() +
               ", isDeltaUpdate=" + isDeltaUpdate +
               '}';
    }
}