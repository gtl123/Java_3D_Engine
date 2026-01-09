package engine.network.sync;

import engine.logging.LogManager;

import java.nio.ByteBuffer;
import java.util.BitSet;

/**
 * Delta compression system for efficient state synchronization.
 * Compresses entity state updates by only sending changed fields.
 */
public class DeltaCompressor {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Field indices for delta compression
    private static final int FIELD_POSITION_X = 0;
    private static final int FIELD_POSITION_Y = 1;
    private static final int FIELD_POSITION_Z = 2;
    private static final int FIELD_ROTATION_X = 3;
    private static final int FIELD_ROTATION_Y = 4;
    private static final int FIELD_ROTATION_Z = 5;
    private static final int FIELD_VELOCITY_X = 6;
    private static final int FIELD_VELOCITY_Y = 7;
    private static final int FIELD_VELOCITY_Z = 8;
    private static final int FIELD_SCALE_X = 9;
    private static final int FIELD_SCALE_Y = 10;
    private static final int FIELD_SCALE_Z = 11;
    private static final int FIELD_HEALTH = 12;
    private static final int FIELD_ENTITY_TYPE = 13;
    private static final int FIELD_VISIBLE = 14;
    private static final int FIELD_NETWORK_ID = 15;
    
    private static final int TOTAL_FIELDS = 16;
    
    // Compression thresholds
    private static final float POSITION_THRESHOLD = 0.01f;
    private static final float ROTATION_THRESHOLD = 0.1f;
    private static final float VELOCITY_THRESHOLD = 0.01f;
    private static final float SCALE_THRESHOLD = 0.001f;
    private static final float HEALTH_THRESHOLD = 0.1f;
    
    private volatile boolean initialized = false;
    
    /**
     * Initialize the delta compressor.
     */
    public void initialize() {
        initialized = true;
        logManager.info("DeltaCompressor", "Delta compressor initialized",
                       "totalFields", TOTAL_FIELDS);
    }
    
    /**
     * Generate delta between two entity states.
     */
    public byte[] generateDelta(EntityState oldState, EntityState newState) {
        if (!initialized) {
            throw new IllegalStateException("DeltaCompressor not initialized");
        }
        
        if (oldState == null) {
            // No previous state, return full serialization
            return newState.serialize();
        }
        
        // Determine which fields have changed
        BitSet changedFields = new BitSet(TOTAL_FIELDS);
        
        // Check position changes
        if (Math.abs(oldState.getPosition().x - newState.getPosition().x) > POSITION_THRESHOLD) {
            changedFields.set(FIELD_POSITION_X);
        }
        if (Math.abs(oldState.getPosition().y - newState.getPosition().y) > POSITION_THRESHOLD) {
            changedFields.set(FIELD_POSITION_Y);
        }
        if (Math.abs(oldState.getPosition().z - newState.getPosition().z) > POSITION_THRESHOLD) {
            changedFields.set(FIELD_POSITION_Z);
        }
        
        // Check rotation changes
        if (Math.abs(oldState.getRotation().x - newState.getRotation().x) > ROTATION_THRESHOLD) {
            changedFields.set(FIELD_ROTATION_X);
        }
        if (Math.abs(oldState.getRotation().y - newState.getRotation().y) > ROTATION_THRESHOLD) {
            changedFields.set(FIELD_ROTATION_Y);
        }
        if (Math.abs(oldState.getRotation().z - newState.getRotation().z) > ROTATION_THRESHOLD) {
            changedFields.set(FIELD_ROTATION_Z);
        }
        
        // Check velocity changes
        if (Math.abs(oldState.getVelocity().x - newState.getVelocity().x) > VELOCITY_THRESHOLD) {
            changedFields.set(FIELD_VELOCITY_X);
        }
        if (Math.abs(oldState.getVelocity().y - newState.getVelocity().y) > VELOCITY_THRESHOLD) {
            changedFields.set(FIELD_VELOCITY_Y);
        }
        if (Math.abs(oldState.getVelocity().z - newState.getVelocity().z) > VELOCITY_THRESHOLD) {
            changedFields.set(FIELD_VELOCITY_Z);
        }
        
        // Check scale changes
        if (Math.abs(oldState.getScale().x - newState.getScale().x) > SCALE_THRESHOLD) {
            changedFields.set(FIELD_SCALE_X);
        }
        if (Math.abs(oldState.getScale().y - newState.getScale().y) > SCALE_THRESHOLD) {
            changedFields.set(FIELD_SCALE_Y);
        }
        if (Math.abs(oldState.getScale().z - newState.getScale().z) > SCALE_THRESHOLD) {
            changedFields.set(FIELD_SCALE_Z);
        }
        
        // Check other property changes
        if (Math.abs(oldState.getHealth() - newState.getHealth()) > HEALTH_THRESHOLD) {
            changedFields.set(FIELD_HEALTH);
        }
        if (oldState.getEntityType() != newState.getEntityType()) {
            changedFields.set(FIELD_ENTITY_TYPE);
        }
        if (oldState.isVisible() != newState.isVisible()) {
            changedFields.set(FIELD_VISIBLE);
        }
        if (oldState.getNetworkId() != newState.getNetworkId()) {
            changedFields.set(FIELD_NETWORK_ID);
        }
        
        // If no fields changed, return empty delta
        if (changedFields.isEmpty()) {
            return new byte[0];
        }
        
        // Calculate delta size
        int deltaSize = calculateDeltaSize(changedFields);
        ByteBuffer buffer = ByteBuffer.allocate(deltaSize);
        
        // Write entity ID
        buffer.putInt(newState.getEntityId());
        
        // Write changed fields bitmask (2 bytes for 16 fields)
        byte[] bitmask = changedFields.toByteArray();
        buffer.putShort((short) bitmask.length);
        buffer.put(bitmask);
        
        // Write changed field values
        writeChangedFields(buffer, newState, changedFields);
        
        return buffer.array();
    }
    
    /**
     * Apply delta to existing entity state.
     */
    public EntityState applyDelta(EntityState baseState, byte[] deltaData) {
        if (!initialized) {
            throw new IllegalStateException("DeltaCompressor not initialized");
        }
        
        if (deltaData.length == 0) {
            // Empty delta, no changes
            return baseState.copy();
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(deltaData);
        
        // Read entity ID
        int entityId = buffer.getInt();
        
        if (entityId != baseState.getEntityId()) {
            throw new IllegalArgumentException("Delta entity ID mismatch");
        }
        
        // Read changed fields bitmask
        short bitmaskLength = buffer.getShort();
        byte[] bitmaskBytes = new byte[bitmaskLength];
        buffer.get(bitmaskBytes);
        BitSet changedFields = BitSet.valueOf(bitmaskBytes);
        
        // Create new state based on old state
        EntityState newState = baseState.copy();
        
        // Apply changed fields
        applyChangedFields(buffer, newState, changedFields);
        
        return newState;
    }
    
    /**
     * Calculate the size needed for delta data.
     */
    private int calculateDeltaSize(BitSet changedFields) {
        int size = 4; // Entity ID
        size += 2; // Bitmask length
        size += 2; // Bitmask data (assuming max 2 bytes for 16 fields)
        
        // Add size for each changed field
        for (int i = 0; i < TOTAL_FIELDS; i++) {
            if (changedFields.get(i)) {
                switch (i) {
                    case FIELD_POSITION_X:
                    case FIELD_POSITION_Y:
                    case FIELD_POSITION_Z:
                    case FIELD_ROTATION_X:
                    case FIELD_ROTATION_Y:
                    case FIELD_ROTATION_Z:
                    case FIELD_VELOCITY_X:
                    case FIELD_VELOCITY_Y:
                    case FIELD_VELOCITY_Z:
                    case FIELD_SCALE_X:
                    case FIELD_SCALE_Y:
                    case FIELD_SCALE_Z:
                    case FIELD_HEALTH:
                        size += 4; // float
                        break;
                    case FIELD_ENTITY_TYPE:
                    case FIELD_NETWORK_ID:
                        size += 4; // int
                        break;
                    case FIELD_VISIBLE:
                        size += 1; // boolean
                        break;
                }
            }
        }
        
        return size;
    }
    
    /**
     * Write changed field values to buffer.
     */
    private void writeChangedFields(ByteBuffer buffer, EntityState state, BitSet changedFields) {
        for (int i = 0; i < TOTAL_FIELDS; i++) {
            if (changedFields.get(i)) {
                switch (i) {
                    case FIELD_POSITION_X:
                        buffer.putFloat(state.getPosition().x);
                        break;
                    case FIELD_POSITION_Y:
                        buffer.putFloat(state.getPosition().y);
                        break;
                    case FIELD_POSITION_Z:
                        buffer.putFloat(state.getPosition().z);
                        break;
                    case FIELD_ROTATION_X:
                        buffer.putFloat(state.getRotation().x);
                        break;
                    case FIELD_ROTATION_Y:
                        buffer.putFloat(state.getRotation().y);
                        break;
                    case FIELD_ROTATION_Z:
                        buffer.putFloat(state.getRotation().z);
                        break;
                    case FIELD_VELOCITY_X:
                        buffer.putFloat(state.getVelocity().x);
                        break;
                    case FIELD_VELOCITY_Y:
                        buffer.putFloat(state.getVelocity().y);
                        break;
                    case FIELD_VELOCITY_Z:
                        buffer.putFloat(state.getVelocity().z);
                        break;
                    case FIELD_SCALE_X:
                        buffer.putFloat(state.getScale().x);
                        break;
                    case FIELD_SCALE_Y:
                        buffer.putFloat(state.getScale().y);
                        break;
                    case FIELD_SCALE_Z:
                        buffer.putFloat(state.getScale().z);
                        break;
                    case FIELD_HEALTH:
                        buffer.putFloat(state.getHealth());
                        break;
                    case FIELD_ENTITY_TYPE:
                        buffer.putInt(state.getEntityType());
                        break;
                    case FIELD_NETWORK_ID:
                        buffer.putInt(state.getNetworkId());
                        break;
                    case FIELD_VISIBLE:
                        buffer.put((byte) (state.isVisible() ? 1 : 0));
                        break;
                }
            }
        }
    }
    
    /**
     * Apply changed fields from buffer to state.
     */
    private void applyChangedFields(ByteBuffer buffer, EntityState state, BitSet changedFields) {
        for (int i = 0; i < TOTAL_FIELDS; i++) {
            if (changedFields.get(i)) {
                switch (i) {
                    case FIELD_POSITION_X:
                        state.getPosition().x = buffer.getFloat();
                        break;
                    case FIELD_POSITION_Y:
                        state.getPosition().y = buffer.getFloat();
                        break;
                    case FIELD_POSITION_Z:
                        state.getPosition().z = buffer.getFloat();
                        break;
                    case FIELD_ROTATION_X:
                        state.getRotation().x = buffer.getFloat();
                        break;
                    case FIELD_ROTATION_Y:
                        state.getRotation().y = buffer.getFloat();
                        break;
                    case FIELD_ROTATION_Z:
                        state.getRotation().z = buffer.getFloat();
                        break;
                    case FIELD_VELOCITY_X:
                        state.getVelocity().x = buffer.getFloat();
                        break;
                    case FIELD_VELOCITY_Y:
                        state.getVelocity().y = buffer.getFloat();
                        break;
                    case FIELD_VELOCITY_Z:
                        state.getVelocity().z = buffer.getFloat();
                        break;
                    case FIELD_SCALE_X:
                        state.getScale().x = buffer.getFloat();
                        break;
                    case FIELD_SCALE_Y:
                        state.getScale().y = buffer.getFloat();
                        break;
                    case FIELD_SCALE_Z:
                        state.getScale().z = buffer.getFloat();
                        break;
                    case FIELD_HEALTH:
                        state.setHealth(buffer.getFloat());
                        break;
                    case FIELD_ENTITY_TYPE:
                        state.setEntityType(buffer.getInt());
                        break;
                    case FIELD_NETWORK_ID:
                        state.setNetworkId(buffer.getInt());
                        break;
                    case FIELD_VISIBLE:
                        state.setVisible(buffer.get() == 1);
                        break;
                }
            }
        }
        
        // Mark state as updated
        state.markUpdated();
    }
    
    /**
     * Calculate compression ratio for given states.
     */
    public float calculateCompressionRatio(EntityState oldState, EntityState newState) {
        if (oldState == null) {
            return 1.0f; // No compression possible
        }
        
        byte[] delta = generateDelta(oldState, newState);
        int fullSize = newState.getSerializedSize();
        
        if (delta.length == 0) {
            return Float.MAX_VALUE; // Perfect compression (no changes)
        }
        
        return (float) fullSize / delta.length;
    }
}