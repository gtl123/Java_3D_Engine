package engine.ai.pathfinding;

import org.joml.Vector3f;

/**
 * Interface for checking if a position is blocked by obstacles
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
@FunctionalInterface
public interface ObstacleChecker {
    
    /**
     * Check if a position is blocked by an obstacle
     * @param position The world position to check
     * @return true if the position is blocked, false if passable
     */
    boolean isBlocked(Vector3f position);
}