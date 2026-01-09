package fps.map.boundaries;

import fps.core.math.Vector3f;
import fps.core.math.BoundingBox;
import java.util.*;
import java.util.logging.Logger;

/**
 * Defines a map boundary region with specific behavior and properties.
 * Boundaries can be playable areas, out-of-bounds zones, death zones, or special regions.
 */
public class MapBoundary {
    private static final Logger logger = Logger.getLogger(MapBoundary.class.getName());
    
    private final String id;
    private final String name;
    private final BoundaryType type;
    private final BoundingBox bounds;
    private final List<Vector3f> vertices;
    private final float height;
    private final BoundaryAction action;
    private final Map<String, Object> properties;
    private final boolean enabled;
    private final int priority;
    
    // Timing and effects
    private final float warningTime;
    private final float actionDelay;
    private final String warningMessage;
    private final String actionMessage;
    
    // Visual and audio feedback
    private final String visualEffect;
    private final String audioEffect;
    private final boolean showWarningUI;
    
    private MapBoundary(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.type = builder.type;
        this.bounds = builder.bounds;
        this.vertices = new ArrayList<>(builder.vertices);
        this.height = builder.height;
        this.action = builder.action;
        this.properties = new HashMap<>(builder.properties);
        this.enabled = builder.enabled;
        this.priority = builder.priority;
        this.warningTime = builder.warningTime;
        this.actionDelay = builder.actionDelay;
        this.warningMessage = builder.warningMessage;
        this.actionMessage = builder.actionMessage;
        this.visualEffect = builder.visualEffect;
        this.audioEffect = builder.audioEffect;
        this.showWarningUI = builder.showWarningUI;
    }
    
    /**
     * Check if a point is inside this boundary
     */
    public boolean containsPoint(Vector3f point) {
        // First check bounding box for quick rejection
        if (!bounds.contains(point)) {
            return false;
        }
        
        // Check height bounds
        if (point.y < bounds.getMin().y || point.y > bounds.getMin().y + height) {
            return false;
        }
        
        // Perform point-in-polygon test for 2D projection
        return isPointInPolygon(point, vertices);
    }
    
    /**
     * Get the distance from a point to the boundary edge
     */
    public float getDistanceToEdge(Vector3f point) {
        if (vertices.isEmpty()) {
            return bounds.getDistanceToPoint(point);
        }
        
        float minDistance = Float.MAX_VALUE;
        
        // Check distance to each edge of the polygon
        for (int i = 0; i < vertices.size(); i++) {
            Vector3f v1 = vertices.get(i);
            Vector3f v2 = vertices.get((i + 1) % vertices.size());
            
            float distance = distanceToLineSegment(point, v1, v2);
            minDistance = Math.min(minDistance, distance);
        }
        
        return minDistance;
    }
    
    /**
     * Get the closest point on the boundary to the given point
     */
    public Vector3f getClosestPointOnBoundary(Vector3f point) {
        if (vertices.isEmpty()) {
            return bounds.getClosestPoint(point);
        }
        
        Vector3f closestPoint = null;
        float minDistance = Float.MAX_VALUE;
        
        for (int i = 0; i < vertices.size(); i++) {
            Vector3f v1 = vertices.get(i);
            Vector3f v2 = vertices.get((i + 1) % vertices.size());
            
            Vector3f closest = closestPointOnLineSegment(point, v1, v2);
            float distance = point.distance(closest);
            
            if (distance < minDistance) {
                minDistance = distance;
                closestPoint = closest;
            }
        }
        
        return closestPoint != null ? closestPoint : point;
    }
    
    /**
     * Check if this boundary should trigger for the given point
     */
    public boolean shouldTrigger(Vector3f point) {
        if (!enabled) {
            return false;
        }
        
        switch (type) {
            case PLAYABLE_AREA:
                return !containsPoint(point); // Trigger when outside playable area
            case OUT_OF_BOUNDS:
            case DEATH_ZONE:
            case RESTRICTED_AREA:
                return containsPoint(point); // Trigger when inside restricted area
            case WARNING_ZONE:
                return containsPoint(point);
            default:
                return false;
        }
    }
    
    /**
     * Get the severity level based on distance to boundary
     */
    public BoundarySeverity getSeverity(Vector3f point) {
        float distance = getDistanceToEdge(point);
        
        if (distance <= 1.0f) {
            return BoundarySeverity.CRITICAL;
        } else if (distance <= 5.0f) {
            return BoundarySeverity.HIGH;
        } else if (distance <= 10.0f) {
            return BoundarySeverity.MEDIUM;
        } else {
            return BoundarySeverity.LOW;
        }
    }
    
    private boolean isPointInPolygon(Vector3f point, List<Vector3f> polygon) {
        if (polygon.size() < 3) {
            return false;
        }
        
        boolean inside = false;
        int j = polygon.size() - 1;
        
        for (int i = 0; i < polygon.size(); i++) {
            Vector3f vi = polygon.get(i);
            Vector3f vj = polygon.get(j);
            
            if (((vi.z > point.z) != (vj.z > point.z)) &&
                (point.x < (vj.x - vi.x) * (point.z - vi.z) / (vj.z - vi.z) + vi.x)) {
                inside = !inside;
            }
            j = i;
        }
        
        return inside;
    }
    
    private float distanceToLineSegment(Vector3f point, Vector3f a, Vector3f b) {
        Vector3f ab = b.subtract(a);
        Vector3f ap = point.subtract(a);
        
        float abSquared = ab.dot(ab);
        if (abSquared == 0) {
            return point.distance(a);
        }
        
        float t = Math.max(0, Math.min(1, ap.dot(ab) / abSquared));
        Vector3f projection = a.add(ab.multiply(t));
        
        return point.distance(projection);
    }
    
    private Vector3f closestPointOnLineSegment(Vector3f point, Vector3f a, Vector3f b) {
        Vector3f ab = b.subtract(a);
        Vector3f ap = point.subtract(a);
        
        float abSquared = ab.dot(ab);
        if (abSquared == 0) {
            return a;
        }
        
        float t = Math.max(0, Math.min(1, ap.dot(ab) / abSquared));
        return a.add(ab.multiply(t));
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public BoundaryType getType() { return type; }
    public BoundingBox getBounds() { return bounds; }
    public List<Vector3f> getVertices() { return new ArrayList<>(vertices); }
    public float getHeight() { return height; }
    public BoundaryAction getAction() { return action; }
    public Map<String, Object> getProperties() { return new HashMap<>(properties); }
    public boolean isEnabled() { return enabled; }
    public int getPriority() { return priority; }
    public float getWarningTime() { return warningTime; }
    public float getActionDelay() { return actionDelay; }
    public String getWarningMessage() { return warningMessage; }
    public String getActionMessage() { return actionMessage; }
    public String getVisualEffect() { return visualEffect; }
    public String getAudioEffect() { return audioEffect; }
    public boolean shouldShowWarningUI() { return showWarningUI; }
    
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, Class<T> type) {
        Object value = properties.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    public static class Builder {
        private String id;
        private String name;
        private BoundaryType type = BoundaryType.PLAYABLE_AREA;
        private BoundingBox bounds;
        private List<Vector3f> vertices = new ArrayList<>();
        private float height = 100.0f;
        private BoundaryAction action = BoundaryAction.TELEPORT_BACK;
        private Map<String, Object> properties = new HashMap<>();
        private boolean enabled = true;
        private int priority = 0;
        private float warningTime = 3.0f;
        private float actionDelay = 1.0f;
        private String warningMessage = "Warning: Leaving play area";
        private String actionMessage = "Returned to play area";
        private String visualEffect = "boundary_warning";
        private String audioEffect = "boundary_warning";
        private boolean showWarningUI = true;
        
        public Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }
        
        public Builder type(BoundaryType type) {
            this.type = type;
            return this;
        }
        
        public Builder bounds(BoundingBox bounds) {
            this.bounds = bounds;
            return this;
        }
        
        public Builder vertices(List<Vector3f> vertices) {
            this.vertices = new ArrayList<>(vertices);
            // Auto-calculate bounding box if not set
            if (this.bounds == null && !vertices.isEmpty()) {
                calculateBounds();
            }
            return this;
        }
        
        public Builder addVertex(Vector3f vertex) {
            this.vertices.add(vertex);
            return this;
        }
        
        public Builder height(float height) {
            this.height = height;
            return this;
        }
        
        public Builder action(BoundaryAction action) {
            this.action = action;
            return this;
        }
        
        public Builder property(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }
        
        public Builder warningTime(float warningTime) {
            this.warningTime = warningTime;
            return this;
        }
        
        public Builder actionDelay(float actionDelay) {
            this.actionDelay = actionDelay;
            return this;
        }
        
        public Builder warningMessage(String message) {
            this.warningMessage = message;
            return this;
        }
        
        public Builder actionMessage(String message) {
            this.actionMessage = message;
            return this;
        }
        
        public Builder visualEffect(String effect) {
            this.visualEffect = effect;
            return this;
        }
        
        public Builder audioEffect(String effect) {
            this.audioEffect = effect;
            return this;
        }
        
        public Builder showWarningUI(boolean show) {
            this.showWarningUI = show;
            return this;
        }
        
        private void calculateBounds() {
            if (vertices.isEmpty()) {
                return;
            }
            
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE, maxZ = Float.MIN_VALUE;
            
            for (Vector3f vertex : vertices) {
                minX = Math.min(minX, vertex.x);
                minY = Math.min(minY, vertex.y);
                minZ = Math.min(minZ, vertex.z);
                maxX = Math.max(maxX, vertex.x);
                maxY = Math.max(maxY, vertex.y);
                maxZ = Math.max(maxZ, vertex.z);
            }
            
            this.bounds = new BoundingBox(
                new Vector3f(minX, minY, minZ),
                new Vector3f(maxX, maxY, maxZ)
            );
        }
        
        public MapBoundary build() {
            if (id == null || name == null) {
                throw new IllegalStateException("ID and name are required");
            }
            
            if (bounds == null) {
                calculateBounds();
            }
            
            if (bounds == null) {
                throw new IllegalStateException("Bounds must be specified or calculable from vertices");
            }
            
            return new MapBoundary(this);
        }
    }
    
    // Factory methods for common boundary types
    public static MapBoundary createPlayableArea(String id, String name, List<Vector3f> vertices) {
        return new Builder(id, name)
            .type(BoundaryType.PLAYABLE_AREA)
            .vertices(vertices)
            .action(BoundaryAction.TELEPORT_BACK)
            .warningMessage("Warning: Leaving play area")
            .actionMessage("Returned to play area")
            .build();
    }
    
    public static MapBoundary createDeathZone(String id, String name, List<Vector3f> vertices) {
        return new Builder(id, name)
            .type(BoundaryType.DEATH_ZONE)
            .vertices(vertices)
            .action(BoundaryAction.KILL_PLAYER)
            .warningMessage("Warning: Entering death zone")
            .actionMessage("Eliminated by death zone")
            .warningTime(1.0f)
            .actionDelay(0.5f)
            .build();
    }
    
    public static MapBoundary createOutOfBounds(String id, String name, BoundingBox bounds) {
        return new Builder(id, name)
            .type(BoundaryType.OUT_OF_BOUNDS)
            .bounds(bounds)
            .action(BoundaryAction.TELEPORT_BACK)
            .warningMessage("Warning: Out of bounds")
            .actionMessage("Returned to play area")
            .build();
    }
    
    public static MapBoundary createWarningZone(String id, String name, List<Vector3f> vertices) {
        return new Builder(id, name)
            .type(BoundaryType.WARNING_ZONE)
            .vertices(vertices)
            .action(BoundaryAction.WARNING_ONLY)
            .warningMessage("Caution: Dangerous area")
            .build();
    }
}