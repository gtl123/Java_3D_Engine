package engine.ai.pathfinding;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a path as a series of waypoints
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class Path {
    
    private final List<Vector3f> waypoints;
    private int currentWaypointIndex;
    private final float totalLength;
    
    /**
     * Create a new path from a list of waypoints
     * @param waypoints List of waypoints defining the path
     */
    public Path(List<Vector3f> waypoints) {
        this.waypoints = new ArrayList<>(waypoints);
        this.currentWaypointIndex = 0;
        this.totalLength = calculateTotalLength();
    }
    
    /**
     * Get all waypoints in the path
     * @return List of waypoints
     */
    public List<Vector3f> getWaypoints() {
        return new ArrayList<>(waypoints);
    }
    
    /**
     * Get the current waypoint
     * @return Current waypoint, or null if path is complete
     */
    public Vector3f getCurrentWaypoint() {
        if (currentWaypointIndex < waypoints.size()) {
            return new Vector3f(waypoints.get(currentWaypointIndex));
        }
        return null;
    }
    
    /**
     * Get the next waypoint without advancing
     * @return Next waypoint, or null if no more waypoints
     */
    public Vector3f getNextWaypoint() {
        if (currentWaypointIndex + 1 < waypoints.size()) {
            return new Vector3f(waypoints.get(currentWaypointIndex + 1));
        }
        return null;
    }
    
    /**
     * Advance to the next waypoint
     * @return true if advanced, false if already at the end
     */
    public boolean advanceWaypoint() {
        if (currentWaypointIndex < waypoints.size() - 1) {
            currentWaypointIndex++;
            return true;
        }
        return false;
    }
    
    /**
     * Check if the path is complete (reached the end)
     * @return true if complete
     */
    public boolean isComplete() {
        return currentWaypointIndex >= waypoints.size();
    }
    
    /**
     * Check if the path is empty
     * @return true if no waypoints
     */
    public boolean isEmpty() {
        return waypoints.isEmpty();
    }
    
    /**
     * Get the number of waypoints in the path
     * @return Number of waypoints
     */
    public int getWaypointCount() {
        return waypoints.size();
    }
    
    /**
     * Get the current waypoint index
     * @return Current index
     */
    public int getCurrentWaypointIndex() {
        return currentWaypointIndex;
    }
    
    /**
     * Set the current waypoint index
     * @param index New index
     */
    public void setCurrentWaypointIndex(int index) {
        this.currentWaypointIndex = Math.max(0, Math.min(index, waypoints.size()));
    }
    
    /**
     * Get the total length of the path
     * @return Total path length
     */
    public float getTotalLength() {
        return totalLength;
    }
    
    /**
     * Get the remaining distance from current position to path end
     * @param currentPosition Current position
     * @return Remaining distance
     */
    public float getRemainingDistance(Vector3f currentPosition) {
        if (isComplete() || waypoints.isEmpty()) {
            return 0.0f;
        }
        
        float distance = 0.0f;
        
        // Distance to current waypoint
        Vector3f currentWaypoint = getCurrentWaypoint();
        if (currentWaypoint != null) {
            distance += currentPosition.distance(currentWaypoint);
            
            // Distance between remaining waypoints
            for (int i = currentWaypointIndex; i < waypoints.size() - 1; i++) {
                distance += waypoints.get(i).distance(waypoints.get(i + 1));
            }
        }
        
        return distance;
    }
    
    /**
     * Get the closest waypoint to a given position
     * @param position Position to check
     * @return Index of closest waypoint
     */
    public int getClosestWaypointIndex(Vector3f position) {
        if (waypoints.isEmpty()) {
            return -1;
        }
        
        int closestIndex = 0;
        float closestDistance = position.distance(waypoints.get(0));
        
        for (int i = 1; i < waypoints.size(); i++) {
            float distance = position.distance(waypoints.get(i));
            if (distance < closestDistance) {
                closestDistance = distance;
                closestIndex = i;
            }
        }
        
        return closestIndex;
    }
    
    /**
     * Reset the path to the beginning
     */
    public void reset() {
        currentWaypointIndex = 0;
    }
    
    /**
     * Create a reversed copy of this path
     * @return New path with waypoints in reverse order
     */
    public Path reverse() {
        List<Vector3f> reversedWaypoints = new ArrayList<>(waypoints);
        java.util.Collections.reverse(reversedWaypoints);
        return new Path(reversedWaypoints);
    }
    
    /**
     * Create a sub-path from the current waypoint to the end
     * @return New path starting from current waypoint
     */
    public Path getSubPath() {
        if (currentWaypointIndex >= waypoints.size()) {
            return new Path(new ArrayList<>());
        }
        
        List<Vector3f> subWaypoints = waypoints.subList(currentWaypointIndex, waypoints.size());
        return new Path(subWaypoints);
    }
    
    /**
     * Calculate the total length of the path
     */
    private float calculateTotalLength() {
        if (waypoints.size() < 2) {
            return 0.0f;
        }
        
        float length = 0.0f;
        for (int i = 0; i < waypoints.size() - 1; i++) {
            length += waypoints.get(i).distance(waypoints.get(i + 1));
        }
        
        return length;
    }
    
    @Override
    public String toString() {
        return "Path{" +
                "waypoints=" + waypoints.size() +
                ", currentIndex=" + currentWaypointIndex +
                ", totalLength=" + totalLength +
                ", complete=" + isComplete() +
                '}';
    }
}