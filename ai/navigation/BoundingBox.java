package engine.ai.navigation;

import org.joml.Vector3f;

/**
 * Axis-aligned bounding box for spatial calculations
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class BoundingBox {
    
    private final Vector3f min;
    private final Vector3f max;
    
    /**
     * Create a bounding box from min and max points
     * @param min Minimum point
     * @param max Maximum point
     */
    public BoundingBox(Vector3f min, Vector3f max) {
        this.min = new Vector3f(
            Math.min(min.x, max.x),
            Math.min(min.y, max.y),
            Math.min(min.z, max.z)
        );
        this.max = new Vector3f(
            Math.max(min.x, max.x),
            Math.max(min.y, max.y),
            Math.max(min.z, max.z)
        );
    }
    
    /**
     * Create a bounding box from coordinates
     * @param minX Minimum X
     * @param minY Minimum Y
     * @param minZ Minimum Z
     * @param maxX Maximum X
     * @param maxY Maximum Y
     * @param maxZ Maximum Z
     */
    public BoundingBox(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this(new Vector3f(minX, minY, minZ), new Vector3f(maxX, maxY, maxZ));
    }
    
    /**
     * Create a bounding box centered at a point with given size
     * @param center Center point
     * @param size Size in each dimension
     */
    public static BoundingBox fromCenterAndSize(Vector3f center, Vector3f size) {
        Vector3f halfSize = new Vector3f(size).mul(0.5f);
        return new BoundingBox(
            new Vector3f(center).sub(halfSize),
            new Vector3f(center).add(halfSize)
        );
    }
    
    /**
     * Create a bounding box from a collection of points
     * @param points Points to include
     * @return Bounding box containing all points
     */
    public static BoundingBox fromPoints(Vector3f... points) {
        if (points.length == 0) {
            return new BoundingBox(new Vector3f(), new Vector3f());
        }
        
        Vector3f min = new Vector3f(points[0]);
        Vector3f max = new Vector3f(points[0]);
        
        for (int i = 1; i < points.length; i++) {
            Vector3f point = points[i];
            min.x = Math.min(min.x, point.x);
            min.y = Math.min(min.y, point.y);
            min.z = Math.min(min.z, point.z);
            max.x = Math.max(max.x, point.x);
            max.y = Math.max(max.y, point.y);
            max.z = Math.max(max.z, point.z);
        }
        
        return new BoundingBox(min, max);
    }
    
    /**
     * Get the minimum point
     * @return Minimum point (copy)
     */
    public Vector3f getMin() {
        return new Vector3f(min);
    }
    
    /**
     * Get the maximum point
     * @return Maximum point (copy)
     */
    public Vector3f getMax() {
        return new Vector3f(max);
    }
    
    /**
     * Get the center point
     * @return Center point
     */
    public Vector3f getCenter() {
        return new Vector3f(min).add(max).mul(0.5f);
    }
    
    /**
     * Get the size of the bounding box
     * @return Size vector
     */
    public Vector3f getSize() {
        return new Vector3f(max).sub(min);
    }
    
    /**
     * Get the half-extents of the bounding box
     * @return Half-extents vector
     */
    public Vector3f getHalfExtents() {
        return getSize().mul(0.5f);
    }
    
    /**
     * Check if this bounding box contains a point
     * @param point Point to check
     * @return true if point is inside
     */
    public boolean contains(Vector3f point) {
        return point.x >= min.x && point.x <= max.x &&
               point.y >= min.y && point.y <= max.y &&
               point.z >= min.z && point.z <= max.z;
    }
    
    /**
     * Check if this bounding box intersects another
     * @param other Other bounding box
     * @return true if they intersect
     */
    public boolean intersects(BoundingBox other) {
        return min.x <= other.max.x && max.x >= other.min.x &&
               min.y <= other.max.y && max.y >= other.min.y &&
               min.z <= other.max.z && max.z >= other.min.z;
    }
    
    /**
     * Expand this bounding box to include a point
     * @param point Point to include
     * @return New expanded bounding box
     */
    public BoundingBox expand(Vector3f point) {
        return new BoundingBox(
            new Vector3f(
                Math.min(min.x, point.x),
                Math.min(min.y, point.y),
                Math.min(min.z, point.z)
            ),
            new Vector3f(
                Math.max(max.x, point.x),
                Math.max(max.y, point.y),
                Math.max(max.z, point.z)
            )
        );
    }
    
    /**
     * Expand this bounding box by a margin
     * @param margin Margin to expand by
     * @return New expanded bounding box
     */
    public BoundingBox expand(float margin) {
        Vector3f marginVec = new Vector3f(margin, margin, margin);
        return new BoundingBox(
            new Vector3f(min).sub(marginVec),
            new Vector3f(max).add(marginVec)
        );
    }
    
    /**
     * Get the closest point on this bounding box to a given point
     * @param point Point to find closest to
     * @return Closest point on the bounding box
     */
    public Vector3f getClosestPoint(Vector3f point) {
        return new Vector3f(
            Math.max(min.x, Math.min(point.x, max.x)),
            Math.max(min.y, Math.min(point.y, max.y)),
            Math.max(min.z, Math.min(point.z, max.z))
        );
    }
    
    /**
     * Get the distance from a point to this bounding box
     * @param point Point to measure distance to
     * @return Distance (0 if point is inside)
     */
    public float getDistance(Vector3f point) {
        Vector3f closestPoint = getClosestPoint(point);
        return point.distance(closestPoint);
    }
    
    /**
     * Get the volume of this bounding box
     * @return Volume
     */
    public float getVolume() {
        Vector3f size = getSize();
        return size.x * size.y * size.z;
    }
    
    /**
     * Get the surface area of this bounding box
     * @return Surface area
     */
    public float getSurfaceArea() {
        Vector3f size = getSize();
        return 2.0f * (size.x * size.y + size.y * size.z + size.z * size.x);
    }
    
    /**
     * Check if this bounding box is valid (min <= max)
     * @return true if valid
     */
    public boolean isValid() {
        return min.x <= max.x && min.y <= max.y && min.z <= max.z;
    }
    
    /**
     * Check if this bounding box is empty (zero volume)
     * @return true if empty
     */
    public boolean isEmpty() {
        return min.x >= max.x || min.y >= max.y || min.z >= max.z;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        BoundingBox that = (BoundingBox) obj;
        return min.equals(that.min) && max.equals(that.max);
    }
    
    @Override
    public int hashCode() {
        return min.hashCode() * 31 + max.hashCode();
    }
    
    @Override
    public String toString() {
        return "BoundingBox{min=" + min + ", max=" + max + "}";
    }
}