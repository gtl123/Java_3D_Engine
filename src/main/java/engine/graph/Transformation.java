package engine.graph;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Transformation {

    private final Matrix4f projectionMatrix;
    private final Matrix4f viewMatrix;
    private final Matrix4f modelViewMatrix;

    public Transformation() {
        projectionMatrix = new Matrix4f();
        viewMatrix = new Matrix4f();
        modelViewMatrix = new Matrix4f();
    }


    /**
     * Build a view matrix from the camera's position and rotation.
     */
    public Matrix4f getViewMatrix(Camera camera) {
        Vector3f cameraPos = camera.getEyePosition(); // eye height
        Vector3f rotation = camera.getRotation();

        viewMatrix.identity();
        // Apply rotations first
        viewMatrix.rotate((float) Math.toRadians(rotation.x), new Vector3f(1, 0, 0))
                .rotate((float) Math.toRadians(rotation.y), new Vector3f(0, 1, 0));
        // Then translate
        viewMatrix.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        return viewMatrix;
    }
    public Matrix4f getProjectionMatrix(float fov, int width, int height, float zNear, float zFar) {
        float aspectRatio = (float) width / (float) height;
        return new Matrix4f().perspective(fov, aspectRatio, zNear, zFar);
    }


    public Matrix4f buildModelMatrix(Vector3f position, Vector3f rotation, float scale) {
        return new Matrix4f()
                .identity()
                .rotateX((float) Math.toRadians(rotation.x))
                .rotateY((float) Math.toRadians(rotation.y))
                .rotateZ((float) Math.toRadians(rotation.z))
                .translate(position)
                .scale(scale);
    }

    public Matrix4f getModelViewMatrix(GameItem gameItem, Matrix4f viewMatrix) {
        // Use hierarchical world transform from GameItem
        Matrix4f worldTransform = gameItem.getWorldTransform();

        // Combine with view matrix
        return new Matrix4f(viewMatrix).mul(worldTransform);
    }


}
