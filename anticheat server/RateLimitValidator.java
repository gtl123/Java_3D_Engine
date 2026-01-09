package fps.anticheat.server;

import engine.logging.LogManager;
import fps.anticheat.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates action rates to prevent spam and rapid-fire exploits.
 * Enforces rate limits for different types of player actions.
 */
public class RateLimitValidator {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Configuration
    private AntiCheatConfiguration config;
    
    // Rate limits (actions per second)
    private final Map<PlayerAction.ActionType, Float> rateLimits = new ConcurrentHashMap<>();
    
    // Default rate limits
    private static final float DEFAULT_MOVE_RATE = 60.0f; // 60 movement updates per second
    private static final float DEFAULT_AIM_RATE = 120.0f; // 120 aim updates per second
    private static final float DEFAULT_FIRE_RATE = 20.0f; // 20 shots per second max
    private static final float DEFAULT_RELOAD_RATE = 2.0f; // 2 reloads per second max
    private static final float DEFAULT_SWITCH_RATE = 5.0f; // 5 weapon switches per second max
    private static final float DEFAULT_JUMP_RATE = 3.0f; // 3 jumps per second max
    private static final float DEFAULT_CROUCH_RATE = 10.0f; // 10 crouch toggles per second max
    private static final float DEFAULT_INTERACT_RATE = 5.0f; // 5 interactions per second max
    private static final float DEFAULT_CHAT_RATE = 1.0f; // 1 chat message per second max
    
    public RateLimitValidator() {
        logManager.debug("RateLimitValidator", "Rate limit validator created");
        initializeDefaultRateLimits();
    }
    
    /**
     * Initialize default rate limits
     */
    private void initializeDefaultRateLimits() {
        rateLimits.put(PlayerAction.ActionType.MOVE, DEFAULT_MOVE_RATE);
        rateLimits.put(PlayerAction.ActionType.AIM, DEFAULT_AIM_RATE);
        rateLimits.put(PlayerAction.ActionType.FIRE_WEAPON, DEFAULT_FIRE_RATE);
        rateLimits.put(PlayerAction.ActionType.RELOAD_WEAPON, DEFAULT_RELOAD_RATE);
        rateLimits.put(PlayerAction.ActionType.SWITCH_WEAPON, DEFAULT_SWITCH_RATE);
        rateLimits.put(PlayerAction.ActionType.JUMP, DEFAULT_JUMP_RATE);
        rateLimits.put(PlayerAction.ActionType.CROUCH, DEFAULT_CROUCH_RATE);
        rateLimits.put(PlayerAction.ActionType.SPRINT, DEFAULT_MOVE_RATE);
        rateLimits.put(PlayerAction.ActionType.INTERACT, DEFAULT_INTERACT_RATE);
        rateLimits.put(PlayerAction.ActionType.CHAT, DEFAULT_CHAT_RATE);
        rateLimits.put(PlayerAction.ActionType.USE_ITEM, DEFAULT_INTERACT_RATE);
    }
    
    /**
     * Set configuration
     */
    public void setConfiguration(AntiCheatConfiguration config) {
        this.config = config;
        
        if (config != null) {
            // Update rate limits based on configuration
            float maxActionsPerSecond = config.getMaxActionsPerSecond();
            
            // Scale rate limits based on configuration
            for (Map.Entry<PlayerAction.ActionType, Float> entry : rateLimits.entrySet()) {
                PlayerAction.ActionType actionType = entry.getKey();
                float baseRate = entry.getValue();
                
                // Apply scaling factor from configuration
                float scaledRate = Math.min(baseRate, maxActionsPerSecond);
                rateLimits.put(actionType, scaledRate);
            }
        }
    }
    
    /**
     * Validate action rate limits
     */
    public ValidationResult validate(PlayerValidationState playerState, PlayerAction action) {
        try {
            PlayerAction.ActionType actionType = action.getActionType();
            
            // Get rate limit for this action type
            Float rateLimit = rateLimits.get(actionType);
            if (rateLimit == null) {
                // No rate limit defined for this action type
                return ValidationResult.allowed();
            }
            
            // Get current actions per second for this action type
            float currentRate = playerState.getActionsPerSecond(actionType);
            
            // Check if rate limit is exceeded
            if (currentRate > rateLimit) {
                return ValidationResult.denied(
                    String.format("Rate limit exceeded for %s: %.2f/s (max: %.2f/s)", 
                                 actionType, currentRate, rateLimit),
                    ViolationType.RATE_LIMIT_EXCEEDED,
                    currentRate
                );
            }
            
            // Additional validation for specific action types
            ValidationResult specificResult = validateSpecificActionRate(playerState, action);
            if (!specificResult.isValid()) {
                return specificResult;
            }
            
            // Check for burst patterns that might indicate automation
            ValidationResult burstResult = validateBurstPattern(playerState, action);
            if (!burstResult.isValid()) {
                return burstResult;
            }
            
            return ValidationResult.allowed();
            
        } catch (Exception e) {
            logManager.error("RateLimitValidator", "Error validating rate limits", e);
            return ValidationResult.denied("Rate limit validation error", ViolationType.SERVER_VALIDATION);
        }
    }
    
    /**
     * Validate specific action rate patterns
     */
    private ValidationResult validateSpecificActionRate(PlayerValidationState playerState, PlayerAction action) {
        PlayerAction.ActionType actionType = action.getActionType();
        
        switch (actionType) {
            case FIRE_WEAPON:
                return validateFireRate(playerState, action);
            case JUMP:
                return validateJumpRate(playerState, action);
            case CHAT:
                return validateChatRate(playerState, action);
            default:
                return ValidationResult.allowed();
        }
    }
    
    /**
     * Validate weapon fire rate
     */
    private ValidationResult validateFireRate(PlayerValidationState playerState, PlayerAction action) {
        // Check for impossibly fast firing
        long timeSinceLastFire = action.getTimestamp() - playerState.getLastFireTime();
        
        if (playerState.getLastFireTime() > 0 && timeSinceLastFire < 10) { // Less than 10ms between shots
            return ValidationResult.denied(
                String.format("Impossibly fast firing: %dms between shots", timeSinceLastFire),
                ViolationType.IMPOSSIBLE_SHOT,
                timeSinceLastFire
            );
        }
        
        // Check burst fire patterns
        int shotsInBurst = playerState.getShotsInBurst();
        long burstDuration = action.getTimestamp() - playerState.getBurstStartTime();
        
        if (shotsInBurst > 10 && burstDuration < 500) { // More than 10 shots in 500ms
            return ValidationResult.denied(
                String.format("Suspicious burst pattern: %d shots in %dms", shotsInBurst, burstDuration),
                ViolationType.TRIGGER_BOT,
                shotsInBurst
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate jump rate
     */
    private ValidationResult validateJumpRate(PlayerValidationState playerState, PlayerAction action) {
        // Check for bunny hopping (rapid consecutive jumps)
        long timeSinceLastJump = action.getTimestamp() - playerState.getLastJumpTime();
        
        if (playerState.getLastJumpTime() > 0 && timeSinceLastJump < 100) { // Less than 100ms between jumps
            return ValidationResult.denied(
                String.format("Jump spam detected: %dms between jumps", timeSinceLastJump),
                ViolationType.RATE_LIMIT_EXCEEDED,
                timeSinceLastJump
            );
        }
        
        // Check for excessive jumping in short time period
        int recentJumps = playerState.getRecentActionCount(PlayerAction.ActionType.JUMP, 5000); // Last 5 seconds
        if (recentJumps > 15) { // More than 15 jumps in 5 seconds
            return ValidationResult.denied(
                String.format("Excessive jumping: %d jumps in 5 seconds", recentJumps),
                ViolationType.RATE_LIMIT_EXCEEDED,
                recentJumps
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate chat rate
     */
    private ValidationResult validateChatRate(PlayerValidationState playerState, PlayerAction action) {
        // Check for chat spam
        int recentChatMessages = playerState.getRecentActionCount(PlayerAction.ActionType.CHAT, 10000); // Last 10 seconds
        
        if (recentChatMessages > 5) { // More than 5 messages in 10 seconds
            return ValidationResult.denied(
                String.format("Chat spam detected: %d messages in 10 seconds", recentChatMessages),
                ViolationType.RATE_LIMIT_EXCEEDED,
                recentChatMessages
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate burst patterns that might indicate automation
     */
    private ValidationResult validateBurstPattern(PlayerValidationState playerState, PlayerAction action) {
        PlayerAction.ActionType actionType = action.getActionType();
        
        // Check for perfectly timed actions (indicating bot behavior)
        if (actionType == PlayerAction.ActionType.FIRE_WEAPON || 
            actionType == PlayerAction.ActionType.AIM) {
            
            // Get recent actions of the same type
            int recentActions = playerState.getRecentActionCount(actionType, 1000); // Last 1 second
            
            if (recentActions > 10) {
                // Check for perfectly consistent timing
                float averageInterval = 1000.0f / recentActions; // Average interval in ms
                
                // If actions are too perfectly timed, it might be a bot
                if (averageInterval > 50 && averageInterval < 200) { // Between 50-200ms intervals
                    // This could indicate automated behavior
                    return ValidationResult.denied(
                        String.format("Suspicious timing pattern: %.2fms average interval", averageInterval),
                        ViolationType.BEHAVIORAL_ANALYSIS,
                        averageInterval
                    );
                }
            }
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Set rate limit for specific action type
     */
    public void setRateLimit(PlayerAction.ActionType actionType, float actionsPerSecond) {
        rateLimits.put(actionType, actionsPerSecond);
        
        logManager.debug("RateLimitValidator", "Rate limit updated",
                        "actionType", actionType,
                        "rateLimit", actionsPerSecond);
    }
    
    /**
     * Get rate limit for specific action type
     */
    public float getRateLimit(PlayerAction.ActionType actionType) {
        return rateLimits.getOrDefault(actionType, 0.0f);
    }
    
    /**
     * Check if action type has rate limiting enabled
     */
    public boolean hasRateLimit(PlayerAction.ActionType actionType) {
        return rateLimits.containsKey(actionType);
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
        rateLimits.clear();
        logManager.debug("RateLimitValidator", "Rate limit validator cleaned up");
    }
    
    /**
     * Get all rate limits
     */
    public Map<PlayerAction.ActionType, Float> getAllRateLimits() {
        return new ConcurrentHashMap<>(rateLimits);
    }
    
    /**
     * Reset rate limits to defaults
     */
    public void resetToDefaults() {
        rateLimits.clear();
        initializeDefaultRateLimits();
        
        logManager.info("RateLimitValidator", "Rate limits reset to defaults");
    }
    
    /**
     * Enable or disable rate limiting for specific action type
     */
    public void setRateLimitEnabled(PlayerAction.ActionType actionType, boolean enabled) {
        if (enabled) {
            // Restore default rate limit if not already set
            if (!rateLimits.containsKey(actionType)) {
                float defaultRate = getDefaultRateLimit(actionType);
                rateLimits.put(actionType, defaultRate);
            }
        } else {
            // Remove rate limit
            rateLimits.remove(actionType);
        }
        
        logManager.debug("RateLimitValidator", "Rate limiting toggled",
                        "actionType", actionType,
                        "enabled", enabled);
    }
    
    /**
     * Get default rate limit for action type
     */
    private float getDefaultRateLimit(PlayerAction.ActionType actionType) {
        switch (actionType) {
            case MOVE: return DEFAULT_MOVE_RATE;
            case AIM: return DEFAULT_AIM_RATE;
            case FIRE_WEAPON: return DEFAULT_FIRE_RATE;
            case RELOAD_WEAPON: return DEFAULT_RELOAD_RATE;
            case SWITCH_WEAPON: return DEFAULT_SWITCH_RATE;
            case JUMP: return DEFAULT_JUMP_RATE;
            case CROUCH: return DEFAULT_CROUCH_RATE;
            case SPRINT: return DEFAULT_MOVE_RATE;
            case INTERACT: return DEFAULT_INTERACT_RATE;
            case CHAT: return DEFAULT_CHAT_RATE;
            case USE_ITEM: return DEFAULT_INTERACT_RATE;
            default: return 10.0f; // Default fallback
        }
    }
}