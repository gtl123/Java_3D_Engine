package engine.network.messages;

import engine.network.protocol.NetworkProtocol;

import java.nio.ByteBuffer;

/**
 * Network message for world/chunk data synchronization.
 */
public class WorldDataMessage extends NetworkMessage {
    
    public enum DataType {
        CHUNK_DATA(1),
        BLOCK_UPDATE(2),
        WORLD_METADATA(3),
        TERRAIN_GENERATION(4);
        
        private final int value;
        
        DataType(int value) {
            this.value = value;
        }
        
        public int getValue() { return value; }
        
        public static DataType fromValue(int value) {
            for (DataType type : values()) {
                if (type.value == value) {
                    return type;
                }
            }
            return CHUNK_DATA;
        }
    }
    
    private final DataType dataType;
    private final int chunkX;
    private final int chunkZ;
    private final byte[] worldData;
    private final boolean isCompressed;
    
    public WorldDataMessage(int senderId, DataType dataType, int chunkX, int chunkZ, 
                           byte[] worldData, boolean isCompressed) {
        super(NetworkProtocol.MessageType.WORLD_DATA, senderId);
        this.dataType = dataType;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.worldData = worldData != null ? worldData.clone() : new byte[0];
        this.isCompressed = isCompressed;
    }
    
    @Override
    protected void serializeData(ByteBuffer buffer) {
        buffer.putInt(dataType.getValue());
        buffer.putInt(chunkX);
        buffer.putInt(chunkZ);
        buffer.put((byte) (isCompressed ? 1 : 0));
        buffer.putInt(worldData.length);
        buffer.put(worldData);
    }
    
    @Override
    protected int getDataSize() {
        return 4 + 4 + 4 + 1 + 4 + worldData.length;
    }
    
    public static WorldDataMessage deserializeData(ByteBuffer buffer, int senderId, long timestamp) {
        DataType dataType = DataType.fromValue(buffer.getInt());
        int chunkX = buffer.getInt();
        int chunkZ = buffer.getInt();
        boolean isCompressed = buffer.get() == 1;
        
        int dataLength = buffer.getInt();
        byte[] worldData = new byte[dataLength];
        buffer.get(worldData);
        
        return new WorldDataMessage(senderId, dataType, chunkX, chunkZ, worldData, isCompressed);
    }
    
    // Getters
    public DataType getDataType() { return dataType; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public byte[] getWorldData() { return worldData.clone(); }
    public boolean isCompressed() { return isCompressed; }
    public int getDataSize() { return worldData.length; }
    
    @Override
    public String toString() {
        return "WorldDataMessage{" +
               "senderId=" + getSenderId() +
               ", dataType=" + dataType +
               ", chunkX=" + chunkX +
               ", chunkZ=" + chunkZ +
               ", dataSize=" + worldData.length +
               ", compressed=" + isCompressed +
               '}';
    }
}