package engine.entity;

import engine.raster.Mesh;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class Entity {

    private Mesh mesh;

    // Local transform (relative to parent)
    private final Vector3f localPosition;
    private final Vector3f localRotation;
    private float localScale;

    // Parent/children
    private Entity parent;
    private final List<Entity> children;

    public Entity(Mesh mesh) {
        this.mesh = mesh;
        this.localPosition = new Vector3f(0, 0, 0);
        this.localRotation = new Vector3f(0, 0, 0);
        this.localScale = 1f;
        this.children = new ArrayList<>();
    }

    // --- Parent/child management ---
    public void addChild(Entity child, Vector3f offset) {
        child.parent = this;
        child.localPosition.set(offset);
        children.add(child);
    }

    public void removeChild(Entity child) {
        if (children.remove(child)) {
            child.parent = null;
        }
    }

    public List<Entity> getChildren() {
        return children;
    }

    public Entity getParent() {
        return parent;
    }

    // --- Local transform setters ---
    public void setLocalPosition(float x, float y, float z) {
        this.localPosition.set(x, y, z);
    }

    public void setLocalRotation(float x, float y, float z) {
        this.localRotation.set(x, y, z);
    }

    public void setLocalScale(float scale) {
        this.localScale = scale;
    }

    // --- World transform calculation ---
    public Matrix4f getWorldTransform() {
        Matrix4f transform = new Matrix4f()
                .translate(localPosition)
                .rotateXYZ(localRotation.x, localRotation.y, localRotation.z)
                .scale(localScale);

        if (parent != null) {
            return new Matrix4f(parent.getWorldTransform()).mul(transform);
        }
        return transform;
    }

    // --- Mesh access ---
    public Mesh getMesh() {
        return mesh;
    }

    public void setMesh(Mesh mesh) {
        this.mesh = mesh;
    }
}
