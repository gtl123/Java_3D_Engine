package fps.anticheat.server;

import engine.logging.LogManager;
import fps.anticheat.*;
import fps.ballistics.BallisticsSystem;
import fps.physics.MovementPhysics;
import fps.player.Player;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-side validation system for anti-cheat.
 * Validates all player actions against physics constraints and game rules.
 * This is the authoritative validation layer that prevents impossible actions.
 */
public class ServerSideValidator {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Configuration
    private AntiCheatConfiguration config;
    
    // Validation components
    private MovementValidator movementValidator;
    private WeaponValidator weaponValidator;
    private PhysicsValidator physicsValidator;
    private RateLimitValidator rateLimitValidator;
    private NetworkValidator networkValidator;
    
    // Player state tracking
    private final Map<Integer, PlayerValidationState> playerStates = new ConcurrentHashMap<>();
    
    // Performance metrics
    private final AtomicLong totalValidations = new AtomicLong(0);
    private final AtomicLong totalViolations = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    
    // System state
    private boolean initialized = false;
    
    public ServerSideValidator() {
        logManager.info("ServerSideValidator", "Server-side validator created");
    }
    
    /**
     * Initialize the server-side validator
     */
    public void initialize() throws Exception {
        logManager.info("ServerSideValidator", "Initializing server-side validator");
        
        try {
            // Initialize validation components
            movementValidator = new MovementValidator();
            weaponValidator = new WeaponValidator();
            physicsValidator = new PhysicsValidator();
            rateLimitValidator = new RateLimitValidator();
            networkValidator = new NetworkValidator();
            
            initialized = true;
            
            logManager.info("ServerSideValidator", "Server-side validator initialization complete");
            
        } catch (Exception e) {
            logManager.error("ServerSideValidator", "Failed to initialize server-side validator", e);
            throw e;
        }
    }
    
    /**
     * Set configuration
     */
    public void setConfiguration(AntiCheatConfiguration config) {
        this.config = config;
        
        if (movementValidator != null) movementValidator.setConfiguration(config);
        if (weaponValidator != null) weaponValidator.setConfiguration(config);
        if (physicsValidator != null) physicsValidator.setConfiguration(config);
        if (rateLimitValidator != null) rateLimitValidator.setConfiguration(config);
        if (networkValidator != null) networkValidator.setConfiguration(config);
    }
    
    /**
     * Validate a player action
     */
    public ValidationResult validateAction(int playerId, PlayerAction action) {
        if (!initialized) {
            return ValidationResult.allowed(0.5f, "Validator not initialized");
        }
        
        long startTime = System.nanoTime();
        totalValidations.incrementAndGet();
        
        try {
            // Get or create player validation state
            PlayerValidationState playerState = getPlayerState(playerId);
            
            // Update player state with action
            playerState.updateWithAction(action);
            
            // Perform validation based on action type
            ValidationResult result = performValidation(playerState, action);
            
            // Record processing time
            long processingTime = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds
            totalProcessingTime.addAndGet(processingTime);
            
            // Log violations
            if (!result.isValid()) {
                totalViolations.incrementAndGet();
                
                logManager.warn("ServerSideValidator", "Action validation failed",
                               "playerId", playerId,
                               "actionType", action.getActionType(),
                               "reason", result.getReason(),
                               "confidence", result.getConfidence());
            }
            
            return ValidationResult.create(
                result.isValid(),
                result.getConfidence(),
                result.getReason(),
                result.getViolationType(),
                result.getEvidence(),
                processingTime,
                "ServerSideValidator"
            );
            
        } catch (Exception e) {
            logManager.error("ServerSideValidator", "Error validating action", e);
            return ValidationResult.denied("Validation error: " + e.getMessage(), ViolationType.SERVER_VALIDATION);
        }
    }
    
    /**
     * Perform validation based on action type
     */
    private ValidationResult performValidation(PlayerValidationState playerState, PlayerAction action) {
        ValidationResult result = ValidationResult.allowed();
        
        // Network validation (always performed)
        ValidationResult networkResult = networkValidator.validate(playerState, action);
        result = result.combine(networkResult);
        
        // Rate limiting validation (always performed)
        ValidationResult rateLimitResult = rateLimitValidator.validate(playerState, action);
        result = result.combine(rateLimitResult);
        
        // Action-specific validation
        switch (action.getActionType()) {
            case MOVE:
            case JUMP:
            case CROUCH:
            case SPRINT:
                ValidationResult movementResult = movementValidator.validate(playerState, action);
                result = result.combine(movementResult);
                
                ValidationResult physicsResult = physicsValidator.validate(playerState, action);
                result = result.combine(physicsResult);
                break;
                
            case FIRE_WEAPON:
            case RELOAD_WEAPON:
            case SWITCH_WEAPON:
                ValidationResult weaponResult = weaponValidator.validate(playerState, action);
                result = result.combine(weaponResult);
                break;
                
            case AIM:
                // Aim validation (check for impossible rotation speeds)
                ValidationResult aimResult = validateAimAction(playerState, action);
                result = result.combine(aimResult);
                break;
                
            default:
                // Generic validation for other actions
                break;
        }
        
        return result;
    }
    
    /**
     * Validate aim action for impossible rotation speeds
     */
    private ValidationResult validateAimAction(PlayerValidationState playerState, PlayerAction action) {
        if (action.getRotation() == null || action.getPreviousRotation() == null) {
            return ValidationResult.allowed();
        }
        
        Vector3f rotationDelta = action.getRotationDelta();
        float rotationSpeed = rotationDelta.length();
        
        // Calculate time delta
        float timeDelta = (action.getTimestamp() - playerState.getLastActionTime()) / 1000.0f;
        if (timeDelta <= 0) {
            return ValidationResult.denied("Invalid time delta for aim action", ViolationType.IMPOSSIBLE_MOVEMENT);
        }
        
        // Calculate rotation speed in degrees per second
        float rotationSpeedDegPerSec = rotationSpeed / timeDelta;
        
        // Check for impossible rotation speeds (human limit is around 1000 deg/sec)
        float maxRotationSpeed = 1200.0f; // Allow some tolerance
        
        if (rotationSpeedDegPerSec > maxRotationSpeed) {
            return ValidationResult.denied(
                String.format("Impossible rotation speed: %.2f deg/sec (max: %.2f)", 
                             rotationSpeedDegPerSec, maxRotationSpeed),
                ViolationType.IMPOSSIBLE_MOVEMENT,
                rotationSpeedDegPerSec
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Get or create player validation state
     */
    private PlayerValidationState getPlayerState(int playerId) {
        return playerStates.computeIfAbsent(playerId, id -> new PlayerValidationState(id));
    }
    
    /**
     * Remove player validation state
     */
    public void removePlayerState(int playerId) {
        playerStates.remove(playerId);
        
        logManager.debug("ServerSideValidator", "Removed player validation state",
                        "playerId", playerId);
    }
    
    /**
     * Update validator
     */
    public void update(float deltaTime) {
        if (!initialized) return;
        
        try {
            // Update validation components
            if (movementValidator != null) movementValidator.update(deltaTime);
            if (weaponValidator != null) weaponValidator.update(deltaTime);
            if (physicsValidator != null) physicsValidator.update(deltaTime);
            if (rateLimitValidator != null) rateLimitValidator.update(deltaTime);
            if (networkValidator != null) networkValidator.update(deltaTime);
            
            // Clean up old player states
            cleanupOldPlayerStates();
            
        } catch (Exception e) {
            logManager.error("ServerSideValidator", "Error updating server-side validator", e);
        }
    }
    
    /**
     * Clean up old player states
     */
    private void cleanupOldPlayerStates() {
        long cutoffTime = System.currentTimeMillis() - 300000; // 5 minutes
        
        playerStates.entrySet().removeIf(entry -> {
            PlayerValidationState state = entry.getValue();
            return state.getLastActionTime() < cutoffTime;
        });
    }
    
    /**
     * Get validation statistics
     */
    public ValidationStatistics getStatistics() {
        ValidationStatistics stats = new ValidationStatistics();
        stats.totalValidations = totalValidations.get();
        stats.totalViolations = totalViolations.get();
        stats.averageProcessingTime = totalValidations.get() > 0 ? 
            (float) totalProcessingTime.get() / totalValidations.get() : 0.0f;
        stats.violationRate = totalValidations.get() > 0 ? 
            (float) totalViolations.get() / totalValidations.get() : 0.0f;
        stats.activePlayerStates = playerStates.size();
        
        return stats;
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        logManager.info("ServerSideValidator", "Cleaning up server-side validator");
        
        try {
            playerStates.clear();
            
            if (movementValidator != null) movementValidator.cleanup();
            if (weaponValidator != null) weaponValidator.cleanup();
            if (physicsValidator != null) physicsValidator.cleanup();
            if (rateLimitValidator != null) rateLimitValidator.cleanup();
            if (networkValidator != null) networkValidator.cleanup();
            
            initialized = false;
            
            logManager.info("ServerSideValidator", "Server-side validator cleanup complete");
            
        } catch (Exception e) {
            logManager.error("ServerSideValidator", "Error during cleanup", e);
        }
    }
    
    // Getters
    public boolean isInitialized() { return initialized; }
    public MovementValidator getMovementValidator() { return movementValidator; }
    public WeaponValidator getWeaponValidator() { return weaponValidator; }
    public PhysicsValidator getPhysicsValidator() { return physicsValidator; }
    public RateLimitValidator getRateLimitValidator() { return rateLimitValidator; }
    public NetworkValidator getNetworkValidator() { return networkValidator; }
    
    /**
     * Validation statistics
     */
    public static class ValidationStatistics {
        public long totalValidations = 0;
        public long totalViolations = 0;
        public float averageProcessingTime = 0.0f;
        public float violationRate = 0.0f;
        public int activePlayerStates = 0;
        
        @Override
        public String toString() {
            return String.format("ValidationStatistics{validations=%d, violations=%d, avgTime=%.2fms, violationRate=%.2f%%, activeStates=%d}",
                               totalValidations, totalViolations, averageProcessingTime, violationRate * 100, activePlayerStates);
        }
    }
}