package engine.ai;

import engine.entity.Entity;
import org.joml.Vector3f;

/**
 * Base interface for AI components that can be attached to entities
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public interface AIComponent {
    
    /**
     * Initialize the AI component with the owning entity
     * @param entity The entity this AI component belongs to
     */
    void initialize(Entity entity);
    
    /**
     * Update the AI component logic
     * @param deltaTime Time elapsed since last update in seconds
     */
    void update(float deltaTime);
    
    /**
     * Get the entity this AI component is attached to
     * @return The owning entity
     */
    Entity getEntity();
    
    /**
     * Get the current position of the AI entity
     * @return Current world position
     */
    Vector3f getPosition();
    
    /**
     * Set the target position for this AI entity
     * @param target Target position to move towards
     */
    void setTarget(Vector3f target);
    
    /**
     * Get the current target position
     * @return Current target position, or null if no target
     */
    Vector3f getTarget();
    
    /**
     * Check if this AI component is currently active
     * @return true if active, false otherwise
     */
    boolean isActive();
    
    /**
     * Set the active state of this AI component
     * @param active true to activate, false to deactivate
     */
    void setActive(boolean active);
    
    /**
     * Cleanup resources used by this AI component
     */
    void cleanup();
}