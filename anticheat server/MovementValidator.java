package fps.anticheat.server;

import engine.logging.LogManager;
import fps.anticheat.*;
import org.joml.Vector3f;

/**
 * Validates player movement actions for impossible speeds, teleportation, and physics violations.
 * Ensures all movement follows realistic physics constraints and game rules.
 */
public class MovementValidator {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Configuration
    private AntiCheatConfiguration config;
    
    // Movement constraints
    private float maxWalkSpeed = 6.0f;
    private float maxRunSpeed = 12.0f;
    private float maxJumpHeight = 2.5f;
    private float maxFallSpeed = 50.0f;
    private float gravity = 9.81f;
    private float jumpCooldown = 0.5f; // seconds
    
    // Tolerance values
    private float speedTolerance = 1.2f; // 20% tolerance
    private float positionTolerance = 0.5f; // meters
    private float teleportThreshold = 10.0f; // meters
    
    public MovementValidator() {
        logManager.debug("MovementValidator", "Movement validator created");
    }
    
    /**
     * Set configuration
     */
    public void setConfiguration(AntiCheatConfiguration config) {
        this.config = config;
        
        if (config != null) {
            this.maxRunSpeed = config.getMaxMovementSpeed();
            this.maxJumpHeight = config.getMaxJumpHeight();
        }
    }
    
    /**
     * Validate a movement action
     */
    public ValidationResult validate(PlayerValidationState playerState, PlayerAction action) {
        try {
            // Validate based on action type
            switch (action.getActionType()) {
                case MOVE:
                    return validateMovement(playerState, action);
                case JUMP:
                    return validateJump(playerState, action);
                case CROUCH:
                    return validateCrouch(playerState, action);
                case SPRINT:
                    return validateSprint(playerState, action);
                default:
                    return ValidationResult.allowed();
            }
            
        } catch (Exception e) {
            logManager.error("MovementValidator", "Error validating movement", e);
            return ValidationResult.denied("Movement validation error", ViolationType.SERVER_VALIDATION);
        }
    }
    
    /**
     * Validate general movement
     */
    private ValidationResult validateMovement(PlayerValidationState playerState, PlayerAction action) {
        if (action.getPosition() == null) {
            return ValidationResult.allowed();
        }
        
        Vector3f currentPos = action.getPosition();
        Vector3f previousPos = playerState.getPreviousPosition();
        
        // Skip validation if no previous position
        if (previousPos.equals(0, 0, 0)) {
            return ValidationResult.allowed();
        }
        
        // Calculate movement delta
        float distance = currentPos.distance(previousPos);
        float timeDelta = (action.getTimestamp() - playerState.getPreviousActionTime()) / 1000.0f;
        
        if (timeDelta <= 0) {
            return ValidationResult.denied("Invalid time delta for movement", ViolationType.IMPOSSIBLE_MOVEMENT);
        }
        
        // Check for teleportation
        if (distance > teleportThreshold) {
            return ValidationResult.denied(
                String.format("Teleportation detected: %.2fm in %.3fs", distance, timeDelta),
                ViolationType.IMPOSSIBLE_MOVEMENT,
                distance
            );
        }
        
        // Calculate speed
        float speed = distance / timeDelta;
        float maxAllowedSpeed = getMaxAllowedSpeed(playerState, action) * speedTolerance;
        
        // Check speed limits
        if (speed > maxAllowedSpeed) {
            return ValidationResult.denied(
                String.format("Speed limit exceeded: %.2fm/s (max: %.2fm/s)", speed, maxAllowedSpeed),
                ViolationType.SPEED_HACK,
                speed
            );
        }
        
        // Validate vertical movement (gravity check)
        ValidationResult gravityResult = validateGravity(playerState, action, timeDelta);
        if (!gravityResult.isValid()) {
            return gravityResult;
        }
        
        // Check for impossible position changes
        ValidationResult positionResult = validatePositionChange(playerState, action);
        if (!positionResult.isValid()) {
            return positionResult;
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate jump action
     */
    private ValidationResult validateJump(PlayerValidationState playerState, PlayerAction action) {
        // Check if player is on ground
        if (!playerState.isOnGround()) {
            return ValidationResult.denied("Jump while not on ground", ViolationType.PHYSICS_VIOLATION);
        }
        
        // Check jump cooldown
        long timeSinceLastJump = action.getTimestamp() - playerState.getLastJumpTime();
        if (timeSinceLastJump < jumpCooldown * 1000) {
            return ValidationResult.denied(
                String.format("Jump cooldown violation: %.3fs (min: %.3fs)", 
                             timeSinceLastJump / 1000.0f, jumpCooldown),
                ViolationType.PHYSICS_VIOLATION
            );
        }
        
        // Validate jump velocity
        if (action.getVelocity() != null) {
            float jumpVelocity = action.getVelocity().y;
            float maxJumpVelocity = (float) Math.sqrt(2 * gravity * maxJumpHeight) * speedTolerance;
            
            if (jumpVelocity > maxJumpVelocity) {
                return ValidationResult.denied(
                    String.format("Jump velocity too high: %.2fm/s (max: %.2fm/s)", 
                                 jumpVelocity, maxJumpVelocity),
                    ViolationType.IMPOSSIBLE_MOVEMENT,
                    jumpVelocity
                );
            }
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate crouch action
     */
    private ValidationResult validateCrouch(PlayerValidationState playerState, PlayerAction action) {
        // Crouching should reduce movement speed
        if (action.getVelocity() != null) {
            float speed = action.getVelocity().length();
            float maxCrouchSpeed = maxWalkSpeed * 0.5f * speedTolerance; // 50% of walk speed
            
            if (speed > maxCrouchSpeed) {
                return ValidationResult.denied(
                    String.format("Crouch speed too high: %.2fm/s (max: %.2fm/s)", 
                                 speed, maxCrouchSpeed),
                    ViolationType.SPEED_HACK,
                    speed
                );
            }
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate sprint action
     */
    private ValidationResult validateSprint(PlayerValidationState playerState, PlayerAction action) {
        // Check if player has stamina (would integrate with stamina system)
        // For now, just validate speed
        
        if (action.getVelocity() != null) {
            float speed = action.getVelocity().length();
            float maxSprintSpeed = maxRunSpeed * speedTolerance;
            
            if (speed > maxSprintSpeed) {
                return ValidationResult.denied(
                    String.format("Sprint speed too high: %.2fm/s (max: %.2fm/s)", 
                                 speed, maxSprintSpeed),
                    ViolationType.SPEED_HACK,
                    speed
                );
            }
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate gravity effects on vertical movement
     */
    private ValidationResult validateGravity(PlayerValidationState playerState, PlayerAction action, float timeDelta) {
        Vector3f currentPos = action.getPosition();
        Vector3f previousPos = playerState.getPreviousPosition();
        Vector3f currentVel = action.getVelocity();
        Vector3f previousVel = playerState.getPreviousVelocity();
        
        if (currentVel == null || previousVel == null) {
            return ValidationResult.allowed(); // Can't validate without velocity data
        }
        
        // Check if player is in air
        if (!playerState.isOnGround() && currentPos.y > 0.1f) {
            // Calculate expected velocity change due to gravity
            float expectedVelChange = -gravity * timeDelta;
            float actualVelChange = currentVel.y - previousVel.y;
            
            // Allow some tolerance for air resistance and other factors
            float gravityTolerance = 2.0f; // m/s tolerance
            
            if (actualVelChange > expectedVelChange + gravityTolerance) {
                return ValidationResult.denied(
                    String.format("Gravity violation: velocity change %.2fm/s (expected: %.2fm/s)", 
                                 actualVelChange, expectedVelChange),
                    ViolationType.PHYSICS_VIOLATION,
                    actualVelChange
                );
            }
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate position change for consistency
     */
    private ValidationResult validatePositionChange(PlayerValidationState playerState, PlayerAction action) {
        Vector3f currentPos = action.getPosition();
        Vector3f previousPos = playerState.getPreviousPosition();
        Vector3f velocity = action.getVelocity();
        
        if (velocity == null) {
            return ValidationResult.allowed(); // Can't validate without velocity
        }
        
        float timeDelta = (action.getTimestamp() - playerState.getPreviousActionTime()) / 1000.0f;
        
        // Calculate expected position based on velocity
        Vector3f expectedDelta = new Vector3f(velocity).mul(timeDelta);
        Vector3f actualDelta = new Vector3f(currentPos).sub(previousPos);
        
        // Calculate difference between expected and actual movement
        float deltaError = expectedDelta.distance(actualDelta);
        
        if (deltaError > positionTolerance) {
            return ValidationResult.denied(
                String.format("Position inconsistency: %.2fm error (tolerance: %.2fm)", 
                             deltaError, positionTolerance),
                ViolationType.IMPOSSIBLE_MOVEMENT,
                deltaError
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Get maximum allowed speed based on player state
     */
    private float getMaxAllowedSpeed(PlayerValidationState playerState, PlayerAction action) {
        switch (action.getActionType()) {
            case SPRINT:
                return maxRunSpeed;
            case CROUCH:
                return maxWalkSpeed * 0.5f;
            case MOVE:
            default:
                return maxWalkSpeed;
        }
    }
    
    /**
     * Update validator
     */
    public void update(float deltaTime) {
        // Update any time-based validation parameters
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        logManager.debug("MovementValidator", "Movement validator cleaned up");
    }
    
    // Getters and setters for configuration
    public float getMaxWalkSpeed() { return maxWalkSpeed; }
    public void setMaxWalkSpeed(float maxWalkSpeed) { this.maxWalkSpeed = maxWalkSpeed; }
    
    public float getMaxRunSpeed() { return maxRunSpeed; }
    public void setMaxRunSpeed(float maxRunSpeed) { this.maxRunSpeed = maxRunSpeed; }
    
    public float getMaxJumpHeight() { return maxJumpHeight; }
    public void setMaxJumpHeight(float maxJumpHeight) { this.maxJumpHeight = maxJumpHeight; }
    
    public float getMaxFallSpeed() { return maxFallSpeed; }
    public void setMaxFallSpeed(float maxFallSpeed) { this.maxFallSpeed = maxFallSpeed; }
    
    public float getGravity() { return gravity; }
    public void setGravity(float gravity) { this.gravity = gravity; }
    
    public float getJumpCooldown() { return jumpCooldown; }
    public void setJumpCooldown(float jumpCooldown) { this.jumpCooldown = jumpCooldown; }
    
    public float getSpeedTolerance() { return speedTolerance; }
    public void setSpeedTolerance(float speedTolerance) { this.speedTolerance = speedTolerance; }
    
    public float getPositionTolerance() { return positionTolerance; }
    public void setPositionTolerance(float positionTolerance) { this.positionTolerance = positionTolerance; }
    
    public float getTeleportThreshold() { return teleportThreshold; }
    public void setTeleportThreshold(float teleportThreshold) { this.teleportThreshold = teleportThreshold; }
}