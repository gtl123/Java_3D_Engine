package engine.physics;

import org.joml.Vector3f;

public class AABB {
    public Vector3f min;
    public Vector3f max;

    public AABB(Vector3f min, Vector3f max) {
        this.min = min;
        this.max = max;
    }

    public AABB(float x, float y, float z, float width, float height, float depth) {
        this.min = new Vector3f(x, y, z);
        this.max = new Vector3f(x + width, y + height, z + depth);
    }

    public boolean intersects(AABB other) {
        return (min.x < other.max.x && max.x > other.min.x) &&
                (min.y < other.max.y && max.y > other.min.y) &&
                (min.z < other.max.z && max.z > other.min.z);
    }

    public void move(float x, float y, float z) {
        min.add(x, y, z);
        max.add(x, y, z);
    }
    public Vector3f getCenter() {
        return new Vector3f(
                (min.x + max.x) * 0.5f,
                (min.y + max.y) * 0.5f,
                (min.z + max.z) * 0.5f
        );
    }

    public Vector3f getHalfExtents() {
        return new Vector3f(
                (max.x - min.x) * 0.5f,
                (max.y - min.y) * 0.5f,
                (max.z - min.z) * 0.5f
        );
    }

    public void setPosition(Vector3f pos) {
        Vector3f half = getHalfExtents();
        min.set(pos.x - half.x, pos.y - half.y, pos.z - half.z);
        max.set(pos.x + half.x, pos.y + half.y, pos.z + half.z);
    }

}
