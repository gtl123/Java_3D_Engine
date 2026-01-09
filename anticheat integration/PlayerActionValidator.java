package fps.anticheat.integration;

import fps.core.Player;
import fps.gameplay.ActionValidator;
import fps.gameplay.actions.*;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Validates player actions through the anti-cheat system before they are processed by game systems.
 */
public class PlayerActionValidator implements ActionValidator {
    
    private final AntiCheatIntegrationManager integrationManager;
    private volatile boolean enabled = false;
    private final AtomicLong validatedActionCount = new AtomicLong(0);
    
    public PlayerActionValidator(AntiCheatIntegrationManager integrationManager) {
        this.integrationManager = integrationManager;
    }
    
    @Override
    public ActionValidationResult validateMovementAction(Player player, MovementAction action) {
        if (!enabled) {
            return ActionValidationResult.ALLOW;
        }
        
        validatedActionCount.incrementAndGet();
        
        try {
            PlayerAction antiCheatAction = new PlayerAction(
                PlayerActionType.MOVEMENT,
                action.getFromPosition(),
                action.getToPosition(),
                System.currentTimeMillis()
            );
            
            ValidationResult result = integrationManager.processPlayerAction(player, antiCheatAction);
            
            if (!result.isValid()) {
                integrationManager.handleValidationResult(player, result);
                
                // Block action if severity is high
                if (result.getSeverity() >= 0.7f) {
                    return new ActionValidationResult(false, result.getMessage());
                } else if (result.getSeverity() >= 0.4f) {
                    // Modify action to safe values
                    MovementAction safeAction = createSafeMovementAction(player, action);
                    return new ActionValidationResult(true, "Action modified for safety", safeAction);
                }
            }
            
            return ActionValidationResult.ALLOW;
            
        } catch (Exception e) {
            System.err.println("Error validating movement action: " + e.getMessage());
            return ActionValidationResult.ALLOW; // Allow on error to prevent game disruption
        }
    }
    
    @Override
    public ActionValidationResult validateWeaponAction(Player player, WeaponAction action) {
        if (!enabled) {
            return ActionValidationResult.ALLOW;
        }
        
        validatedActionCount.incrementAndGet();
        
        try {
            PlayerActionType actionType;
            switch (action.getType()) {
                case SHOOT:
                    actionType = PlayerActionType.WEAPON_FIRE;
                    break;
                case RELOAD:
                    actionType = PlayerActionType.WEAPON_RELOAD;
                    break;
                case SWITCH:
                    actionType = PlayerActionType.WEAPON_SWITCH;
                    break;
                default:
                    actionType = PlayerActionType.WEAPON_FIRE;
            }
            
            PlayerAction antiCheatAction = new PlayerAction(
                actionType,
                player.getLocation(),
                action.getTargetPosition(),
                System.currentTimeMillis()
            );
            
            ValidationResult result = integrationManager.processPlayerAction(player, antiCheatAction);
            
            if (!result.isValid()) {
                integrationManager.handleValidationResult(player, result);
                
                // Block action if severity is high
                if (result.getSeverity() >= 0.6f) {
                    return new ActionValidationResult(false, result.getMessage());
                } else if (result.getSeverity() >= 0.3f) {
                    // Modify action to safe values
                    WeaponAction safeAction = createSafeWeaponAction(player, action);
                    return new ActionValidationResult(true, "Action modified for safety", safeAction);
                }
            }
            
            return ActionValidationResult.ALLOW;
            
        } catch (Exception e) {
            System.err.println("Error validating weapon action: " + e.getMessage());
            return ActionValidationResult.ALLOW;
        }
    }
    
    @Override
    public ActionValidationResult validateInteractionAction(Player player, InteractionAction action) {
        if (!enabled) {
            return ActionValidationResult.ALLOW;
        }
        
        validatedActionCount.incrementAndGet();
        
        try {
            PlayerAction antiCheatAction = new PlayerAction(
                PlayerActionType.INTERACTION,
                player.getLocation(),
                action.getTargetPosition(),
                System.currentTimeMillis()
            );
            
            ValidationResult result = integrationManager.processPlayerAction(player, antiCheatAction);
            
            if (!result.isValid()) {
                integrationManager.handleValidationResult(player, result);
                
                // Block action if severity is moderate or higher
                if (result.getSeverity() >= 0.5f) {
                    return new ActionValidationResult(false, result.getMessage());
                }
            }
            
            return ActionValidationResult.ALLOW;
            
        } catch (Exception e) {
            System.err.println("Error validating interaction action: " + e.getMessage());
            return ActionValidationResult.ALLOW;
        }
    }
    
    @Override
    public ActionValidationResult validateChatAction(Player player, ChatAction action) {
        if (!enabled) {
            return ActionValidationResult.ALLOW;
        }
        
        validatedActionCount.incrementAndGet();
        
        try {
            PlayerAction antiCheatAction = new PlayerAction(
                PlayerActionType.CHAT,
                player.getLocation(),
                null,
                System.currentTimeMillis()
            );
            
            ValidationResult result = integrationManager.processPlayerAction(player, antiCheatAction);
            
            if (!result.isValid()) {
                integrationManager.handleValidationResult(player, result);
                
                // Block chat if spam detected
                if (result.getViolationType() == ViolationType.SPAM) {
                    return new ActionValidationResult(false, "Chat rate limit exceeded");
                }
            }
            
            // Additional chat content validation
            String message = action.getMessage();
            if (containsInappropriateContent(message)) {
                return new ActionValidationResult(false, "Message contains inappropriate content");
            }
            
            if (message.length() > 500) {
                // Truncate long messages
                ChatAction truncatedAction = new ChatAction(message.substring(0, 500) + "...");
                return new ActionValidationResult(true, "Message truncated", truncatedAction);
            }
            
            return ActionValidationResult.ALLOW;
            
        } catch (Exception e) {
            System.err.println("Error validating chat action: " + e.getMessage());
            return ActionValidationResult.ALLOW;
        }
    }
    
    /**
     * Create a safe movement action by limiting speed and preventing impossible movements
     */
    private MovementAction createSafeMovementAction(Player player, MovementAction originalAction) {
        Vector3 currentPos = player.getLocation();
        Vector3 targetPos = originalAction.getToPosition();
        
        // Calculate safe movement distance based on time elapsed
        long timeDelta = System.currentTimeMillis() - player.getLastMoveTime();
        double maxDistance = calculateMaxMovementDistance(timeDelta);
        
        double actualDistance = currentPos.distanceTo(targetPos);
        
        if (actualDistance > maxDistance) {
            // Limit movement to maximum safe distance
            Vector3 direction = targetPos.subtract(currentPos).normalize();
            Vector3 safePosition = currentPos.add(direction.multiply(maxDistance));
            
            return new MovementAction(originalAction.getFromPosition(), safePosition, 
                                    originalAction.getMovementType());
        }
        
        return originalAction;
    }
    
    /**
     * Create a safe weapon action by adjusting timing and target
     */
    private WeaponAction createSafeWeaponAction(Player player, WeaponAction originalAction) {
        if (originalAction.getType() == WeaponActionType.SHOOT) {
            // Ensure weapon fire rate is respected
            Weapon currentWeapon = player.getCurrentWeapon();
            if (currentWeapon != null) {
                long timeSinceLastShot = System.currentTimeMillis() - player.getLastShotTime();
                long minTimeBetweenShots = 60000 / currentWeapon.getFireRate();
                
                if (timeSinceLastShot < minTimeBetweenShots) {
                    // Delay the shot to respect fire rate
                    return new WeaponAction(WeaponActionType.SHOOT, 
                                          originalAction.getTargetPosition(),
                                          System.currentTimeMillis() + (minTimeBetweenShots - timeSinceLastShot));
                }
            }
            
            // Limit shot range
            Vector3 playerPos = player.getLocation();
            Vector3 targetPos = originalAction.getTargetPosition();
            double distance = playerPos.distanceTo(targetPos);
            
            if (currentWeapon != null && distance > currentWeapon.getMaxRange()) {
                // Limit target to maximum weapon range
                Vector3 direction = targetPos.subtract(playerPos).normalize();
                Vector3 safeTarget = playerPos.add(direction.multiply(currentWeapon.getMaxRange()));
                
                return new WeaponAction(WeaponActionType.SHOOT, safeTarget, originalAction.getTimestamp());
            }
        }
        
        return originalAction;
    }
    
    /**
     * Calculate maximum movement distance based on time elapsed
     */
    private double calculateMaxMovementDistance(long timeDelta) {
        if (timeDelta <= 0) return 0.0;
        
        // Maximum human running speed is about 10 m/s
        double maxSpeed = 12.0; // Allow some tolerance
        return maxSpeed * (timeDelta / 1000.0);
    }
    
    /**
     * Check if chat message contains inappropriate content
     */
    private boolean containsInappropriateContent(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        
        String lowerMessage = message.toLowerCase();
        
        // Simple inappropriate content detection
        String[] inappropriateWords = {
            "cheat", "hack", "aimbot", "wallhack", "speedhack", "exploit"
        };
        
        for (String word : inappropriateWords) {
            if (lowerMessage.contains(word)) {
                return true;
            }
        }
        
        // Check for excessive repetition (spam pattern)
        if (hasExcessiveRepetition(message)) {
            return true;
        }
        
        // Check for excessive caps
        if (hasExcessiveCaps(message)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check for excessive character repetition
     */
    private boolean hasExcessiveRepetition(String message) {
        if (message.length() < 10) return false;
        
        int maxRepeats = 0;
        char lastChar = 0;
        int currentRepeats = 0;
        
        for (char c : message.toCharArray()) {
            if (c == lastChar) {
                currentRepeats++;
                maxRepeats = Math.max(maxRepeats, currentRepeats);
            } else {
                currentRepeats = 1;
                lastChar = c;
            }
        }
        
        return maxRepeats > 5; // More than 5 consecutive identical characters
    }
    
    /**
     * Check for excessive capital letters
     */
    private boolean hasExcessiveCaps(String message) {
        if (message.length() < 10) return false;
        
        int capsCount = 0;
        for (char c : message.toCharArray()) {
            if (Character.isUpperCase(c)) {
                capsCount++;
            }
        }
        
        double capsRatio = (double) capsCount / message.length();
        return capsRatio > 0.7; // More than 70% caps
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public long getValidatedActionCount() {
        return validatedActionCount.get();
    }
    
    public void resetActionCount() {
        validatedActionCount.set(0);
    }
}