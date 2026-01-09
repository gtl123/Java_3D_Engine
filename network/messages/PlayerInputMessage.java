package engine.network.messages;

import engine.network.protocol.NetworkProtocol;
import org.joml.Vector3f;

import java.nio.ByteBuffer;

/**
 * Network message for player input data.
 * Contains movement, rotation, and action inputs from clients.
 */
public class PlayerInputMessage extends NetworkMessage {
    
    // Input flags
    public static final int INPUT_MOVE_FORWARD = 1 << 0;
    public static final int INPUT_MOVE_BACKWARD = 1 << 1;
    public static final int INPUT_MOVE_LEFT = 1 << 2;
    public static final int INPUT_MOVE_RIGHT = 1 << 3;
    public static final int INPUT_MOVE_UP = 1 << 4;
    public static final int INPUT_MOVE_DOWN = 1 << 5;
    public static final int INPUT_JUMP = 1 << 6;
    public static final int INPUT_CROUCH = 1 << 7;
    public static final int INPUT_PRIMARY_ACTION = 1 << 8;
    public static final int INPUT_SECONDARY_ACTION = 1 << 9;
    
    private final int inputFlags;
    private final Vector3f mouseMovement;
    private final Vector3f playerPosition;
    private final Vector3f playerRotation;
    private final int sequenceNumber;
    
    public PlayerInputMessage(int senderId, int inputFlags, Vector3f mouseMovement, 
                             Vector3f playerPosition, Vector3f playerRotation, int sequenceNumber) {
        super(NetworkProtocol.MessageType.PLAYER_INPUT, senderId);
        this.inputFlags = inputFlags;
        this.mouseMovement = new Vector3f(mouseMovement);
        this.playerPosition = new Vector3f(playerPosition);
        this.playerRotation = new Vector3f(playerRotation);
        this.sequenceNumber = sequenceNumber;
    }
    
    @Override
    protected void serializeData(ByteBuffer buffer) {
        buffer.putInt(inputFlags);
        buffer.putInt(sequenceNumber);
        
        // Mouse movement
        buffer.putFloat(mouseMovement.x);
        buffer.putFloat(mouseMovement.y);
        buffer.putFloat(mouseMovement.z);
        
        // Player position
        buffer.putFloat(playerPosition.x);
        buffer.putFloat(playerPosition.y);
        buffer.putFloat(playerPosition.z);
        
        // Player rotation
        buffer.putFloat(playerRotation.x);
        buffer.putFloat(playerRotation.y);
        buffer.putFloat(playerRotation.z);
    }
    
    @Override
    protected int getDataSize() {
        return 4 + 4 + // inputFlags + sequenceNumber
               4 * 3 + // mouseMovement (3 floats)
               4 * 3 + // playerPosition (3 floats)
               4 * 3;  // playerRotation (3 floats)
    }
    
    public static PlayerInputMessage deserializeData(ByteBuffer buffer, int senderId, long timestamp) {
        int inputFlags = buffer.getInt();
        int sequenceNumber = buffer.getInt();
        
        Vector3f mouseMovement = new Vector3f(
            buffer.getFloat(),
            buffer.getFloat(),
            buffer.getFloat()
        );
        
        Vector3f playerPosition = new Vector3f(
            buffer.getFloat(),
            buffer.getFloat(),
            buffer.getFloat()
        );
        
        Vector3f playerRotation = new Vector3f(
            buffer.getFloat(),
            buffer.getFloat(),
            buffer.getFloat()
        );
        
        return new PlayerInputMessage(senderId, inputFlags, mouseMovement, 
                                    playerPosition, playerRotation, sequenceNumber);
    }
    
    // Input flag helpers
    public boolean isMoveForward() { return (inputFlags & INPUT_MOVE_FORWARD) != 0; }
    public boolean isMoveBackward() { return (inputFlags & INPUT_MOVE_BACKWARD) != 0; }
    public boolean isMoveLeft() { return (inputFlags & INPUT_MOVE_LEFT) != 0; }
    public boolean isMoveRight() { return (inputFlags & INPUT_MOVE_RIGHT) != 0; }
    public boolean isMoveUp() { return (inputFlags & INPUT_MOVE_UP) != 0; }
    public boolean isMoveDown() { return (inputFlags & INPUT_MOVE_DOWN) != 0; }
    public boolean isJump() { return (inputFlags & INPUT_JUMP) != 0; }
    public boolean isCrouch() { return (inputFlags & INPUT_CROUCH) != 0; }
    public boolean isPrimaryAction() { return (inputFlags & INPUT_PRIMARY_ACTION) != 0; }
    public boolean isSecondaryAction() { return (inputFlags & INPUT_SECONDARY_ACTION) != 0; }
    
    // Getters
    public int getInputFlags() { return inputFlags; }
    public Vector3f getMouseMovement() { return new Vector3f(mouseMovement); }
    public Vector3f getPlayerPosition() { return new Vector3f(playerPosition); }
    public Vector3f getPlayerRotation() { return new Vector3f(playerRotation); }
    public int getSequenceNumber() { return sequenceNumber; }
    
    @Override
    public String toString() {
        return "PlayerInputMessage{" +
               "senderId=" + getSenderId() +
               ", inputFlags=" + Integer.toBinaryString(inputFlags) +
               ", sequenceNumber=" + sequenceNumber +
               ", position=" + playerPosition +
               ", rotation=" + playerRotation +
               '}';
    }
}