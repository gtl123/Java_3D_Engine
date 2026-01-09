package fps.anticheat.server;

import engine.logging.LogManager;
import fps.anticheat.*;
import org.joml.Vector3f;

/**
 * Validates physics-related constraints for player actions.
 * Ensures all actions follow realistic physics laws and game physics rules.
 */
public class PhysicsValidator {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Configuration
    private AntiCheatConfiguration config;
    
    // Physics constants
    private float gravity = 9.81f; // m/s²
    private float airResistance = 0.1f;
    private float groundFriction = 0.8f;
    private float maxAcceleration = 20.0f; // m/s²
    private float maxDeceleration = 30.0f; // m/s²
    
    // Validation tolerances
    private float physicsTolerance = 0.2f; // 20% tolerance for physics calculations
    private float positionTolerance = 0.1f; // 10cm tolerance for position calculations
    private float velocityTolerance = 0.5f; // 0.5 m/s tolerance for velocity calculations
    
    public PhysicsValidator() {
        logManager.debug("PhysicsValidator", "Physics validator created");
    }
    
    /**
     * Set configuration
     */
    public void setConfiguration(AntiCheatConfiguration config) {
        this.config = config;
        
        if (config != null) {
            // Update physics parameters based on configuration
            // Configuration could include custom physics settings
        }
    }
    
    /**
     * Validate physics constraints for an action
     */
    public ValidationResult validate(PlayerValidationState playerState, PlayerAction action) {
        try {
            // Only validate movement-related actions
            if (!action.isMovementAction()) {
                return ValidationResult.allowed();
            }
            
            // Validate position consistency
            ValidationResult positionResult = validatePositionPhysics(playerState, action);
            if (!positionResult.isValid()) {
                return positionResult;
            }
            
            // Validate velocity constraints
            ValidationResult velocityResult = validateVelocityPhysics(playerState, action);
            if (!velocityResult.isValid()) {
                return velocityResult;
            }
            
            // Validate acceleration limits
            ValidationResult accelerationResult = validateAccelerationPhysics(playerState, action);
            if (!accelerationResult.isValid()) {
                return accelerationResult;
            }
            
            // Validate gravity effects
            ValidationResult gravityResult = validateGravityPhysics(playerState, action);
            if (!gravityResult.isValid()) {
                return gravityResult;
            }
            
            // Validate collision constraints
            ValidationResult collisionResult = validateCollisionPhysics(playerState, action);
            if (!collisionResult.isValid()) {
                return collisionResult;
            }
            
            return ValidationResult.allowed();
            
        } catch (Exception e) {
            logManager.error("PhysicsValidator", "Error validating physics", e);
            return ValidationResult.denied("Physics validation error", ViolationType.SERVER_VALIDATION);
        }
    }
    
    /**
     * Validate position physics consistency
     */
    private ValidationResult validatePositionPhysics(PlayerValidationState playerState, PlayerAction action) {
        Vector3f currentPos = action.getPosition();
        Vector3f previousPos = playerState.getPreviousPosition();
        Vector3f velocity = action.getVelocity();
        
        if (currentPos == null || previousPos == null || velocity == null) {
            return ValidationResult.allowed(); // Can't validate without complete data
        }
        
        // Skip validation for initial position
        if (previousPos.equals(0, 0, 0)) {
            return ValidationResult.allowed();
        }
        
        float timeDelta = (action.getTimestamp() - playerState.getPreviousActionTime()) / 1000.0f;
        if (timeDelta <= 0) {
            return ValidationResult.denied("Invalid time delta for physics validation", ViolationType.PHYSICS_VIOLATION);
        }
        
        // Calculate expected position based on velocity and time
        Vector3f expectedDelta = new Vector3f(velocity).mul(timeDelta);
        Vector3f actualDelta = new Vector3f(currentPos).sub(previousPos);
        
        // Calculate position error
        float positionError = expectedDelta.distance(actualDelta);
        
        if (positionError > positionTolerance) {
            return ValidationResult.denied(
                String.format("Position physics violation: %.3fm error (tolerance: %.3fm)", 
                             positionError, positionTolerance),
                ViolationType.PHYSICS_VIOLATION,
                positionError
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate velocity physics constraints
     */
    private ValidationResult validateVelocityPhysics(PlayerValidationState playerState, PlayerAction action) {
        Vector3f currentVel = action.getVelocity();
        Vector3f previousVel = playerState.getPreviousVelocity();
        
        if (currentVel == null || previousVel == null) {
            return ValidationResult.allowed();
        }
        
        // Skip validation for initial velocity
        if (previousVel.equals(0, 0, 0)) {
            return ValidationResult.allowed();
        }
        
        float timeDelta = (action.getTimestamp() - playerState.getPreviousActionTime()) / 1000.0f;
        if (timeDelta <= 0) {
            return ValidationResult.allowed();
        }
        
        // Check for impossible velocity changes
        Vector3f velocityDelta = new Vector3f(currentVel).sub(previousVel);
        float velocityChange = velocityDelta.length();
        
        // Calculate maximum possible velocity change based on acceleration limits
        float maxVelocityChange = maxAcceleration * timeDelta * (1.0f + physicsTolerance);
        
        if (velocityChange > maxVelocityChange) {
            return ValidationResult.denied(
                String.format("Velocity change too large: %.2fm/s (max: %.2fm/s)", 
                             velocityChange, maxVelocityChange),
                ViolationType.PHYSICS_VIOLATION,
                velocityChange
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate acceleration physics
     */
    private ValidationResult validateAccelerationPhysics(PlayerValidationState playerState, PlayerAction action) {
        Vector3f currentVel = action.getVelocity();
        Vector3f previousVel = playerState.getPreviousVelocity();
        
        if (currentVel == null || previousVel == null) {
            return ValidationResult.allowed();
        }
        
        float timeDelta = (action.getTimestamp() - playerState.getPreviousActionTime()) / 1000.0f;
        if (timeDelta <= 0) {
            return ValidationResult.allowed();
        }
        
        // Calculate acceleration
        Vector3f velocityDelta = new Vector3f(currentVel).sub(previousVel);
        Vector3f acceleration = new Vector3f(velocityDelta).div(timeDelta);
        
        // Check horizontal acceleration (excluding gravity)
        Vector3f horizontalAccel = new Vector3f(acceleration.x, 0, acceleration.z);
        float horizontalAccelMagnitude = horizontalAccel.length();
        
        if (horizontalAccelMagnitude > maxAcceleration * (1.0f + physicsTolerance)) {
            return ValidationResult.denied(
                String.format("Horizontal acceleration too high: %.2fm/s² (max: %.2fm/s²)", 
                             horizontalAccelMagnitude, maxAcceleration),
                ViolationType.PHYSICS_VIOLATION,
                horizontalAccelMagnitude
            );
        }
        
        // Check for impossible deceleration
        if (horizontalAccelMagnitude > maxDeceleration * (1.0f + physicsTolerance)) {
            return ValidationResult.denied(
                String.format("Deceleration too high: %.2fm/s² (max: %.2fm/s²)", 
                             horizontalAccelMagnitude, maxDeceleration),
                ViolationType.PHYSICS_VIOLATION,
                horizontalAccelMagnitude
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate gravity physics effects
     */
    private ValidationResult validateGravityPhysics(PlayerValidationState playerState, PlayerAction action) {
        Vector3f currentVel = action.getVelocity();
        Vector3f previousVel = playerState.getPreviousVelocity();
        Vector3f currentPos = action.getPosition();
        
        if (currentVel == null || previousVel == null || currentPos == null) {
            return ValidationResult.allowed();
        }
        
        float timeDelta = (action.getTimestamp() - playerState.getPreviousActionTime()) / 1000.0f;
        if (timeDelta <= 0) {
            return ValidationResult.allowed();
        }
        
        // Only validate gravity when player is in air
        if (playerState.isOnGround() || currentPos.y <= 0.1f) {
            return ValidationResult.allowed();
        }
        
        // Calculate expected vertical velocity change due to gravity
        float expectedGravityEffect = -gravity * timeDelta;
        float actualVerticalVelChange = currentVel.y - previousVel.y;
        
        // Allow tolerance for air resistance and other factors
        float gravityTolerance = Math.abs(expectedGravityEffect) * physicsTolerance + 1.0f;
        
        // Check if vertical velocity change is consistent with gravity
        if (actualVerticalVelChange > expectedGravityEffect + gravityTolerance) {
            return ValidationResult.denied(
                String.format("Gravity violation: vertical velocity change %.2fm/s (expected: %.2fm/s)", 
                             actualVerticalVelChange, expectedGravityEffect),
                ViolationType.PHYSICS_VIOLATION,
                actualVerticalVelChange
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate collision physics
     */
    private ValidationResult validateCollisionPhysics(PlayerValidationState playerState, PlayerAction action) {
        Vector3f currentPos = action.getPosition();
        Vector3f previousPos = playerState.getPreviousPosition();
        
        if (currentPos == null || previousPos == null) {
            return ValidationResult.allowed();
        }
        
        // Check for clipping through ground
        if (currentPos.y < 0 && previousPos.y >= 0) {
            return ValidationResult.denied(
                String.format("Ground clipping detected: y=%.3f", currentPos.y),
                ViolationType.PHYSICS_VIOLATION,
                currentPos.y
            );
        }
        
        // Check for impossible vertical movement while on ground
        if (playerState.isOnGround() && currentPos.y > previousPos.y + 0.1f) {
            // Player moved up while on ground without jumping
            if (action.getActionType() != PlayerAction.ActionType.JUMP) {
                return ValidationResult.denied(
                    String.format("Upward movement without jump: %.3fm", currentPos.y - previousPos.y),
                    ViolationType.PHYSICS_VIOLATION,
                    currentPos.y - previousPos.y
                );
            }
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate momentum conservation
     */
    private ValidationResult validateMomentumConservation(PlayerValidationState playerState, PlayerAction action) {
        Vector3f currentVel = action.getVelocity();
        Vector3f previousVel = playerState.getPreviousVelocity();
        
        if (currentVel == null || previousVel == null) {
            return ValidationResult.allowed();
        }
        
        float timeDelta = (action.getTimestamp() - playerState.getPreviousActionTime()) / 1000.0f;
        if (timeDelta <= 0) {
            return ValidationResult.allowed();
        }
        
        // Calculate momentum change
        float currentMomentum = currentVel.length();
        float previousMomentum = previousVel.length();
        float momentumChange = Math.abs(currentMomentum - previousMomentum);
        
        // Calculate maximum possible momentum change
        float maxMomentumChange = maxAcceleration * timeDelta * (1.0f + physicsTolerance);
        
        if (momentumChange > maxMomentumChange) {
            return ValidationResult.denied(
                String.format("Momentum conservation violation: %.2f change (max: %.2f)", 
                             momentumChange, maxMomentumChange),
                ViolationType.PHYSICS_VIOLATION,
                momentumChange
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Update validator
     */
    public void update(float deltaTime) {
        // Update any time-based physics parameters
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        logManager.debug("PhysicsValidator", "Physics validator cleaned up");
    }
    
    // Getters and setters
    public float getGravity() { return gravity; }
    public void setGravity(float gravity) { this.gravity = gravity; }
    
    public float getAirResistance() { return airResistance; }
    public void setAirResistance(float airResistance) { this.airResistance = airResistance; }
    
    public float getGroundFriction() { return groundFriction; }
    public void setGroundFriction(float groundFriction) { this.groundFriction = groundFriction; }
    
    public float getMaxAcceleration() { return maxAcceleration; }
    public void setMaxAcceleration(float maxAcceleration) { this.maxAcceleration = maxAcceleration; }
    
    public float getMaxDeceleration() { return maxDeceleration; }
    public void setMaxDeceleration(float maxDeceleration) { this.maxDeceleration = maxDeceleration; }
    
    public float getPhysicsTolerance() { return physicsTolerance; }
    public void setPhysicsTolerance(float physicsTolerance) { this.physicsTolerance = physicsTolerance; }
    
    public float getPositionTolerance() { return positionTolerance; }
    public void setPositionTolerance(float positionTolerance) { this.positionTolerance = positionTolerance; }
    
    public float getVelocityTolerance() { return velocityTolerance; }
    public void setVelocityTolerance(float velocityTolerance) { this.velocityTolerance = velocityTolerance; }
}