package fps.anticheat;

import org.joml.Vector3f;

/**
 * Represents a player action that needs to be validated by the anti-cheat system.
 * Contains all necessary data for server-side validation and cheat detection.
 */
public class PlayerAction {
    
    private final ActionType actionType;
    private final int playerId;
    private final long timestamp;
    private final long clientTimestamp;
    private final int sequenceNumber;
    
    // Position and movement data
    private Vector3f position;
    private Vector3f velocity;
    private Vector3f rotation;
    private Vector3f previousPosition;
    private Vector3f previousRotation;
    
    // Input data
    private InputData inputData;
    
    // Weapon and combat data
    private WeaponActionData weaponData;
    
    // Network data
    private float ping;
    private float packetLoss;
    private String clientVersion;
    
    // Validation metadata
    private boolean validated = false;
    private String validationReason;
    
    public PlayerAction(ActionType actionType, int playerId) {
        this.actionType = actionType;
        this.playerId = playerId;
        this.timestamp = System.currentTimeMillis();
        this.clientTimestamp = timestamp;
        this.sequenceNumber = 0;
    }
    
    public PlayerAction(ActionType actionType, int playerId, long clientTimestamp, int sequenceNumber) {
        this.actionType = actionType;
        this.playerId = playerId;
        this.timestamp = System.currentTimeMillis();
        this.clientTimestamp = clientTimestamp;
        this.sequenceNumber = sequenceNumber;
    }
    
    /**
     * Calculate time difference between server and client
     */
    public long getTimeDelta() {
        return timestamp - clientTimestamp;
    }
    
    /**
     * Check if action is within acceptable time window
     */
    public boolean isWithinTimeWindow(long maxDelta) {
        return Math.abs(getTimeDelta()) <= maxDelta;
    }
    
    /**
     * Calculate movement distance since last action
     */
    public float getMovementDistance() {
        if (position == null || previousPosition == null) {
            return 0.0f;
        }
        return position.distance(previousPosition);
    }
    
    /**
     * Calculate movement speed
     */
    public float getMovementSpeed() {
        if (velocity == null) {
            return 0.0f;
        }
        return velocity.length();
    }
    
    /**
     * Calculate rotation change since last action
     */
    public Vector3f getRotationDelta() {
        if (rotation == null || previousRotation == null) {
            return new Vector3f(0, 0, 0);
        }
        return new Vector3f(rotation).sub(previousRotation);
    }
    
    /**
     * Get aim direction vector
     */
    public Vector3f getAimDirection() {
        if (rotation == null) {
            return new Vector3f(0, 0, -1);
        }
        
        float pitch = (float) Math.toRadians(rotation.x);
        float yaw = (float) Math.toRadians(rotation.y);
        
        return new Vector3f(
            (float) (Math.cos(pitch) * Math.sin(yaw)),
            (float) (-Math.sin(pitch)),
            (float) (Math.cos(pitch) * Math.cos(yaw))
        );
    }
    
    /**
     * Check if action involves weapon usage
     */
    public boolean isWeaponAction() {
        return actionType == ActionType.FIRE_WEAPON || 
               actionType == ActionType.RELOAD_WEAPON || 
               actionType == ActionType.SWITCH_WEAPON;
    }
    
    /**
     * Check if action involves movement
     */
    public boolean isMovementAction() {
        return actionType == ActionType.MOVE || 
               actionType == ActionType.JUMP || 
               actionType == ActionType.CROUCH ||
               actionType == ActionType.SPRINT;
    }
    
    /**
     * Check if action involves aiming
     */
    public boolean isAimAction() {
        return actionType == ActionType.AIM || 
               actionType == ActionType.FIRE_WEAPON;
    }
    
    // Getters and setters
    public ActionType getActionType() { return actionType; }
    public int getPlayerId() { return playerId; }
    public long getTimestamp() { return timestamp; }
    public long getClientTimestamp() { return clientTimestamp; }
    public int getSequenceNumber() { return sequenceNumber; }
    
    public Vector3f getPosition() { return position; }
    public void setPosition(Vector3f position) { this.position = position; }
    
    public Vector3f getVelocity() { return velocity; }
    public void setVelocity(Vector3f velocity) { this.velocity = velocity; }
    
    public Vector3f getRotation() { return rotation; }
    public void setRotation(Vector3f rotation) { this.rotation = rotation; }
    
    public Vector3f getPreviousPosition() { return previousPosition; }
    public void setPreviousPosition(Vector3f previousPosition) { this.previousPosition = previousPosition; }
    
    public Vector3f getPreviousRotation() { return previousRotation; }
    public void setPreviousRotation(Vector3f previousRotation) { this.previousRotation = previousRotation; }
    
    public InputData getInputData() { return inputData; }
    public void setInputData(InputData inputData) { this.inputData = inputData; }
    
    public WeaponActionData getWeaponData() { return weaponData; }
    public void setWeaponData(WeaponActionData weaponData) { this.weaponData = weaponData; }
    
    public float getPing() { return ping; }
    public void setPing(float ping) { this.ping = ping; }
    
    public float getPacketLoss() { return packetLoss; }
    public void setPacketLoss(float packetLoss) { this.packetLoss = packetLoss; }
    
    public String getClientVersion() { return clientVersion; }
    public void setClientVersion(String clientVersion) { this.clientVersion = clientVersion; }
    
    public boolean isValidated() { return validated; }
    public void setValidated(boolean validated) { this.validated = validated; }
    
    public String getValidationReason() { return validationReason; }
    public void setValidationReason(String validationReason) { this.validationReason = validationReason; }
    
    /**
     * Types of player actions that can be validated
     */
    public enum ActionType {
        MOVE,           // Player movement
        JUMP,           // Player jumping
        CROUCH,         // Player crouching
        SPRINT,         // Player sprinting
        AIM,            // Player aiming/looking
        FIRE_WEAPON,    // Firing a weapon
        RELOAD_WEAPON,  // Reloading a weapon
        SWITCH_WEAPON,  // Switching weapons
        USE_ITEM,       // Using an item
        INTERACT,       // Interacting with environment
        CHAT,           // Chat message
        DISCONNECT,     // Player disconnecting
        CONNECT,        // Player connecting
        RESPAWN,        // Player respawning
        SPECTATE        // Player spectating
    }
    
    /**
     * Input data associated with an action
     */
    public static class InputData {
        private boolean[] keysPressed = new boolean[256];
        private boolean[] mouseButtons = new boolean[8];
        private float mouseDeltaX;
        private float mouseDeltaY;
        private float mouseWheelDelta;
        private long inputTimestamp;
        
        public InputData() {
            this.inputTimestamp = System.currentTimeMillis();
        }
        
        public boolean isKeyPressed(int keyCode) {
            return keyCode >= 0 && keyCode < keysPressed.length && keysPressed[keyCode];
        }
        
        public void setKeyPressed(int keyCode, boolean pressed) {
            if (keyCode >= 0 && keyCode < keysPressed.length) {
                keysPressed[keyCode] = pressed;
            }
        }
        
        public boolean isMouseButtonPressed(int button) {
            return button >= 0 && button < mouseButtons.length && mouseButtons[button];
        }
        
        public void setMouseButtonPressed(int button, boolean pressed) {
            if (button >= 0 && button < mouseButtons.length) {
                mouseButtons[button] = pressed;
            }
        }
        
        public float getMouseSensitivity() {
            return (float) Math.sqrt(mouseDeltaX * mouseDeltaX + mouseDeltaY * mouseDeltaY);
        }
        
        // Getters and setters
        public boolean[] getKeysPressed() { return keysPressed; }
        public void setKeysPressed(boolean[] keysPressed) { this.keysPressed = keysPressed; }
        
        public boolean[] getMouseButtons() { return mouseButtons; }
        public void setMouseButtons(boolean[] mouseButtons) { this.mouseButtons = mouseButtons; }
        
        public float getMouseDeltaX() { return mouseDeltaX; }
        public void setMouseDeltaX(float mouseDeltaX) { this.mouseDeltaX = mouseDeltaX; }
        
        public float getMouseDeltaY() { return mouseDeltaY; }
        public void setMouseDeltaY(float mouseDeltaY) { this.mouseDeltaY = mouseDeltaY; }
        
        public float getMouseWheelDelta() { return mouseWheelDelta; }
        public void setMouseWheelDelta(float mouseWheelDelta) { this.mouseWheelDelta = mouseWheelDelta; }
        
        public long getInputTimestamp() { return inputTimestamp; }
        public void setInputTimestamp(long inputTimestamp) { this.inputTimestamp = inputTimestamp; }
    }
    
    /**
     * Weapon-specific action data
     */
    public static class WeaponActionData {
        private String weaponId;
        private int weaponInstanceId;
        private Vector3f fireOrigin;
        private Vector3f fireDirection;
        private int ammunition;
        private float recoilPattern;
        private float accuracy;
        private boolean isAiming;
        private long lastFireTime;
        
        public WeaponActionData() {
            // Default constructor
        }
        
        public WeaponActionData(String weaponId, int weaponInstanceId) {
            this.weaponId = weaponId;
            this.weaponInstanceId = weaponInstanceId;
        }
        
        public float getTimeSinceLastFire() {
            return (System.currentTimeMillis() - lastFireTime) / 1000.0f;
        }
        
        // Getters and setters
        public String getWeaponId() { return weaponId; }
        public void setWeaponId(String weaponId) { this.weaponId = weaponId; }
        
        public int getWeaponInstanceId() { return weaponInstanceId; }
        public void setWeaponInstanceId(int weaponInstanceId) { this.weaponInstanceId = weaponInstanceId; }
        
        public Vector3f getFireOrigin() { return fireOrigin; }
        public void setFireOrigin(Vector3f fireOrigin) { this.fireOrigin = fireOrigin; }
        
        public Vector3f getFireDirection() { return fireDirection; }
        public void setFireDirection(Vector3f fireDirection) { this.fireDirection = fireDirection; }
        
        public int getAmmunition() { return ammunition; }
        public void setAmmunition(int ammunition) { this.ammunition = ammunition; }
        
        public float getRecoilPattern() { return recoilPattern; }
        public void setRecoilPattern(float recoilPattern) { this.recoilPattern = recoilPattern; }
        
        public float getAccuracy() { return accuracy; }
        public void setAccuracy(float accuracy) { this.accuracy = accuracy; }
        
        public boolean isAiming() { return isAiming; }
        public void setAiming(boolean aiming) { this.isAiming = aiming; }
        
        public long getLastFireTime() { return lastFireTime; }
        public void setLastFireTime(long lastFireTime) { this.lastFireTime = lastFireTime; }
    }
}