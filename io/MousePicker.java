package engine.io;

import engine.camera.Camera;
import engine.raster.Transformation;

import game.voxel.Block;
import game.voxel.Chunk;
import game.voxel.ChunkManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class MousePicker {

    private Vector3f currentRay = new Vector3f();
    private Matrix4f projectionMatrix;
    private Matrix4f viewMatrix;
    private Window window;

    public MousePicker(Window window) {
        this.window = window;
        this.projectionMatrix = new Matrix4f();
        this.viewMatrix = new Matrix4f();
    }

    public void update(Camera camera, Transformation transformation, float fov, float zNear, float zFar) {
        viewMatrix = transformation.getViewMatrix(camera);
        projectionMatrix = transformation.getProjectionMatrix(fov, window.getWidth(), window.getHeight(), zNear, zFar);
        currentRay = calculateMouseRay(window.getWidth() / 2f, window.getHeight() / 2f); // Center screen for crosshair
    }

    public Vector3f getCurrentRay() {
        return currentRay;
    }

    private Vector3f calculateMouseRay(float mouseX, float mouseY) {
        // GLFW (0,0) is Top-Left.
        // Window space (0,0) bottom-left.
        // Using center of screen for crosshair

        Vector4f clipCoords = new Vector4f(0, 0, -1.0f, 1.0f); // Center of screen
        Vector4f eyeCoords = toEyeCoords(clipCoords);
        Vector3f worldRay = toWorldCoords(eyeCoords);
        return worldRay;
    }

    private Vector4f toEyeCoords(Vector4f clipCoords) {
        Matrix4f invertedProjection = new Matrix4f(projectionMatrix).invert();
        Vector4f eyeCoords = invertedProjection.transform(clipCoords);
        return new Vector4f(eyeCoords.x, eyeCoords.y, -1f, 0f);
    }

    private Vector3f toWorldCoords(Vector4f eyeCoords) {
        Matrix4f invertedView = new Matrix4f(viewMatrix).invert();
        Vector4f rayWorld = invertedView.transform(eyeCoords);
        Vector3f mouseRay = new Vector3f(rayWorld.x, rayWorld.y, rayWorld.z);
        mouseRay.normalize();
        return mouseRay;
    }

    // Raycast logic
    public Vector3f raycastBlock(Camera camera, ChunkManager cm, float distance) {
        Vector3f pos = new Vector3f(camera.getPosition());
        pos.y += 1.6f; // Eye height

        Vector3f dir = new Vector3f(currentRay);
        float step = 0.05f;

        for (float d = 0; d < distance; d += step) {
            Vector3f p = new Vector3f(pos).add(new Vector3f(dir).mul(d));
            int x = (int) Math.floor(p.x);
            int y = (int) Math.floor(p.y);
            int z = (int) Math.floor(p.z);

            Block b = getBlock(cm, x, y, z);
            if (b != Block.AIR && b != Block.WATER) {
                return new Vector3f(x, y, z);
            }
        }
        return null;
    }

    public Vector3f raycastPlace(Camera camera, ChunkManager cm, float distance) {
        Vector3f pos = new Vector3f(camera.getPosition());
        pos.y += 1.6f;

        Vector3f dir = new Vector3f(currentRay);
        float step = 0.05f;

        int lastX = (int) Math.floor(pos.x);
        int lastY = (int) Math.floor(pos.y);
        int lastZ = (int) Math.floor(pos.z);

        for (float d = 0; d < distance; d += step) {
            Vector3f p = new Vector3f(pos).add(new Vector3f(dir).mul(d));
            int x = (int) Math.floor(p.x);
            int y = (int) Math.floor(p.y);
            int z = (int) Math.floor(p.z);

            Block b = getBlock(cm, x, y, z);
            if (b != Block.AIR && b != Block.WATER) {
                // Return the previous empty block position
                return new Vector3f(lastX, lastY, lastZ);
            }

            lastX = x;
            lastY = y;
            lastZ = z;
        }
        return null;
    }

    private Block getBlock(ChunkManager cm, int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        long key = ChunkManager.getChunkKey(cx, cz);
        Chunk c = cm.getChunks().get(key);
        if (c != null) {
            int lx = x & 15;
            int ly = y;
            int lz = z & 15;
            return c.getBlock(lx, ly, lz);
        }
        return Block.AIR;
    }
}
