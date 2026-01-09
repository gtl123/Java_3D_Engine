package fps.map.elements;

import fps.core.math.Vector3f;
import fps.core.math.BoundingBox;
import fps.game.Player;
import java.util.Collection;

/**
 * A door element that can be opened, closed, locked, and destroyed.
 * Supports automatic closing, key requirements, and team restrictions.
 */
public class Door extends DynamicElement {
    
    // Door-specific properties
    private final float openAngle;
    private final float closeDelay;
    private final boolean autoClose;
    private final String requiredKey;
    private final String allowedTeam;
    private final float health;
    
    // Runtime state
    private float currentAngle;
    private float closeTimer;
    private float currentHealth;
    private String lastUser;
    
    private Door(Builder builder) {
        super(builder);
        this.openAngle = builder.openAngle;
        this.closeDelay = builder.closeDelay;
        this.autoClose = builder.autoClose;
        this.requiredKey = builder.requiredKey;
        this.allowedTeam = builder.allowedTeam;
        this.health = builder.health;
        
        this.currentAngle = (getCurrentState() == ElementState.OPEN) ? openAngle : 0.0f;
        this.closeTimer = 0.0f;
        this.currentHealth = health;
        this.lastUser = null;
    }
    
    @Override
    protected void updateElement(float deltaTime, Collection<Player> nearbyPlayers) {
        // Update door angle during transitions
        updateDoorAngle(deltaTime);
        
        // Handle auto-close timer
        if (autoClose && getCurrentState() == ElementState.OPEN) {
            closeTimer += deltaTime;
            if (closeTimer >= closeDelay) {
                // Check if any players are blocking the door
                if (!isPlayerBlocking(nearbyPlayers)) {
                    changeState(ElementState.CLOSING, stateTransitionDuration);
                    closeTimer = 0.0f;
                }
            }
        }
        
        // Update collision based on current state
        updateCollision();
    }
    
    @Override
    protected InteractionResult performInteraction(Player player, String action) {
        // Check if player has required key
        if (requiredKey != null && !playerHasKey(player, requiredKey)) {
            return InteractionResult.locked(getName(), requiredKey);
        }
        
        // Check team restrictions
        if (allowedTeam != null && !player.getTeam().equals(allowedTeam)) {
            return InteractionResult.insufficientPermissions(getName(), "Team: " + allowedTeam);
        }
        
        // Check current state and perform appropriate action
        switch (getCurrentState()) {
            case CLOSED:
                return openDoor(player);
            case OPEN:
                return closeDoor(player);
            case LOCKED:
                return unlockDoor(player);
            case DESTROYED:
                return InteractionResult.destroyed(getName());
            case OPENING:
            case CLOSING:
                return new InteractionResult(false, getName() + " is currently moving");
            default:
                return new InteractionResult(false, "Cannot interact with " + getName() + " in state " + getCurrentState().getDisplayName());
        }
    }
    
    /**
     * Open the door
     */
    private InteractionResult openDoor(Player player) {
        if (changeState(ElementState.OPENING, stateTransitionDuration)) {
            lastUser = player.getName();
            closeTimer = 0.0f;
            return InteractionResult.success("Opened " + getName(), InteractionResult.InteractionType.OPEN);
        }
        return new InteractionResult(false, "Failed to open " + getName());
    }
    
    /**
     * Close the door
     */
    private InteractionResult closeDoor(Player player) {
        if (changeState(ElementState.CLOSING, stateTransitionDuration)) {
            lastUser = player.getName();
            closeTimer = 0.0f;
            return InteractionResult.success("Closed " + getName(), InteractionResult.InteractionType.CLOSE);
        }
        return new InteractionResult(false, "Failed to close " + getName());
    }
    
    /**
     * Unlock the door
     */
    private InteractionResult unlockDoor(Player player) {
        if (requiredKey != null && !playerHasKey(player, requiredKey)) {
            return InteractionResult.locked(getName(), requiredKey);
        }
        
        setState(ElementState.CLOSED);
        return InteractionResult.success("Unlocked " + getName(), InteractionResult.InteractionType.UNLOCK);
    }
    
    /**
     * Check if player has the required key
     */
    private boolean playerHasKey(Player player, String keyName) {
        // This would check the player's inventory for the key
        // For now, return true as a placeholder
        return player.getProperty("keys", String.class) != null && 
               player.getProperty("keys", String.class).contains(keyName);
    }
    
    /**
     * Check if any player is blocking the door from closing
     */
    private boolean isPlayerBlocking(Collection<Player> nearbyPlayers) {
        BoundingBox doorPath = getDoorPath();
        
        for (Player player : nearbyPlayers) {
            if (doorPath.contains(player.getPosition())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get the path the door travels when opening/closing
     */
    private BoundingBox getDoorPath() {
        // Calculate the area the door sweeps through when opening
        Vector3f doorSize = bounds.getSize();
        float sweepRadius = Math.max(doorSize.x, doorSize.z);
        
        Vector3f min = position.subtract(new Vector3f(sweepRadius, 0, sweepRadius));
        Vector3f max = position.add(new Vector3f(sweepRadius, doorSize.y, sweepRadius));
        
        return new BoundingBox(min, max);
    }
    
    /**
     * Update door angle during transitions
     */
    private void updateDoorAngle(float deltaTime) {
        float targetAngle = 0.0f;
        
        switch (getCurrentState()) {
            case OPEN:
                targetAngle = openAngle;
                break;
            case CLOSED:
                targetAngle = 0.0f;
                break;
            case OPENING:
                targetAngle = openAngle * getTransitionProgress();
                break;
            case CLOSING:
                targetAngle = openAngle * (1.0f - getTransitionProgress());
                break;
        }
        
        currentAngle = targetAngle;
    }
    
    /**
     * Update collision based on door state
     */
    private void updateCollision() {
        // Door blocks movement when closed or closing, allows movement when open
        boolean shouldBlock = (getCurrentState() == ElementState.CLOSED || 
                              getCurrentState() == ElementState.CLOSING ||
                              getCurrentState() == ElementState.LOCKED);
        
        // This would update the physics collision shape
        // physicsSystem.setCollisionEnabled(this, shouldBlock);
    }
    
    /**
     * Take damage and potentially destroy the door
     */
    public InteractionResult takeDamage(float damage, String damageSource) {
        if (getCurrentState() == ElementState.DESTROYED) {
            return InteractionResult.destroyed(getName());
        }
        
        currentHealth -= damage;
        
        if (currentHealth <= 0) {
            setState(ElementState.DESTROYED);
            currentHealth = 0;
            
            // Create destruction effects
            createDestructionEffects();
            
            return InteractionResult.success("Destroyed " + getName(), InteractionResult.InteractionType.DESTROY)
                .addData("damageSource", damageSource)
                .addData("finalDamage", damage);
        } else {
            // Door is damaged but still functional
            if (currentHealth < health * 0.5f && getCurrentState() != ElementState.DAMAGED) {
                setState(ElementState.DAMAGED);
            }
            
            return new InteractionResult(true, String.format("%s took %.1f damage", getName(), damage))
                .addData("remainingHealth", currentHealth)
                .addData("damageSource", damageSource);
        }
    }
    
    /**
     * Create visual and audio effects for door destruction
     */
    private void createDestructionEffects() {
        // This would trigger particle effects, sound effects, and debris
        // effectsSystem.createExplosion(position, "door_destruction");
        // audioSystem.playSound("door_break", position);
    }
    
    @Override
    protected void onStateChanged(ElementState oldState, ElementState newState) {
        super.onStateChanged(oldState, newState);
        
        // Play appropriate sound effects
        String soundEffect = newState.getAudioEffect();
        if (soundEffect != null) {
            // audioSystem.playSound(soundEffect, position);
        }
        
        // Reset timers on state change
        if (newState == ElementState.OPEN) {
            closeTimer = 0.0f;
        }
    }
    
    @Override
    protected void onTransitionCompleted() {
        super.onTransitionCompleted();
        
        // Set final state after transition
        switch (getCurrentState()) {
            case OPENING:
                setState(ElementState.OPEN);
                break;
            case CLOSING:
                setState(ElementState.CLOSED);
                break;
        }
    }
    
    @Override
    public Vector3f getInterpolatedRotation() {
        // Return current door rotation based on angle
        return new Vector3f(rotation.x, rotation.y + currentAngle, rotation.z);
    }
    
    // Getters for door-specific properties
    public float getOpenAngle() { return openAngle; }
    public float getCloseDelay() { return closeDelay; }
    public boolean isAutoClose() { return autoClose; }
    public String getRequiredKey() { return requiredKey; }
    public String getAllowedTeam() { return allowedTeam; }
    public float getHealth() { return health; }
    public float getCurrentHealth() { return currentHealth; }
    public float getCurrentAngle() { return currentAngle; }
    public String getLastUser() { return lastUser; }
    
    /**
     * Builder for creating Door instances
     */
    public static class Builder extends DynamicElement.Builder<Builder> {
        private float openAngle = 90.0f;
        private float closeDelay = 5.0f;
        private boolean autoClose = true;
        private String requiredKey = null;
        private String allowedTeam = null;
        private float health = 100.0f;
        
        public Builder(String id, String name) {
            super(id, name, ElementType.DOOR);
            // Set door-specific defaults
            interactionPrompt("Press E to open/close door");
            transitionDuration(1.0f);
            networkPriority(0); // High priority for doors
        }
        
        public Builder openAngle(float angle) {
            this.openAngle = angle;
            return this;
        }
        
        public Builder closeDelay(float delay) {
            this.closeDelay = delay;
            return this;
        }
        
        public Builder autoClose(boolean autoClose) {
            this.autoClose = autoClose;
            return this;
        }
        
        public Builder requiredKey(String key) {
            this.requiredKey = key;
            return this;
        }
        
        public Builder allowedTeam(String team) {
            this.allowedTeam = team;
            return this;
        }
        
        public Builder health(float health) {
            this.health = health;
            return this;
        }
        
        @Override
        public Door build() {
            if (bounds == null) {
                // Create default door bounds if not specified
                bounds = new BoundingBox(
                    position.subtract(new Vector3f(0.5f, 0, 0.1f)),
                    position.add(new Vector3f(0.5f, 2.0f, 0.1f))
                );
            }
            
            return new Door(this);
        }
    }
    
    /**
     * Factory methods for common door types
     */
    public static Door createStandardDoor(String id, String name, Vector3f position) {
        return new Builder(id, name)
            .position(position)
            .openAngle(90.0f)
            .autoClose(true)
            .closeDelay(5.0f)
            .build();
    }
    
    public static Door createSecurityDoor(String id, String name, Vector3f position, String requiredKey) {
        return new Builder(id, name)
            .position(position)
            .requiredKey(requiredKey)
            .health(200.0f)
            .autoClose(true)
            .closeDelay(3.0f)
            .interactionPrompt("Press E to use security door")
            .build();
    }
    
    public static Door createTeamDoor(String id, String name, Vector3f position, String team) {
        return new Builder(id, name)
            .position(position)
            .allowedTeam(team)
            .autoClose(true)
            .closeDelay(2.0f)
            .interactionPrompt("Press E to use team door")
            .build();
    }
    
    public static Door createBlastDoor(String id, String name, Vector3f position) {
        return new Builder(id, name)
            .position(position)
            .health(500.0f)
            .openAngle(180.0f)
            .transitionDuration(3.0f)
            .autoClose(false)
            .interactionPrompt("Press E to operate blast door")
            .build();
    }
}