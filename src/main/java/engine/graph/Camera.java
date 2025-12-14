package engine.graph;

import engine.physics.AABB;
import engine.voxel.Block;
import engine.voxel.Chunk;
import engine.voxel.ChunkManager;
import org.joml.Vector3f;

public class Camera {

    private final Vector3f position;
    private final Vector3f rotation;
    private final Vector3f velocity;
    private final AABB boundingBox;

    // Physics constants
    private static final float GRAVITY = -20.0f;
    private static final float JUMP_FORCE = 8.0f;
    private static final float MOVEMENT_SPEED = 5.0f;
    private static final float PLAYER_WIDTH = 0.6f;
    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float EYE_HEIGHT = 1.62f; // Eye height from feet

    private boolean onGround = false;

    public Camera() {
        position = new Vector3f(0, 50, 0);
        rotation = new Vector3f(0, 0, 0);
        velocity = new Vector3f(0, 0, 0);

        boundingBox = new AABB(
                position.x - PLAYER_WIDTH / 2, position.y, position.z - PLAYER_WIDTH / 2,
                PLAYER_WIDTH, PLAYER_HEIGHT, PLAYER_WIDTH);
    }

    public Vector3f getPosition() {
        return position;
    }

    public Vector3f getEyePosition() {
        return new Vector3f(position.x, position.y + EYE_HEIGHT, position.z);
    }

    public void setPosition(float x, float y, float z) {
        position.x = x;
        position.y = y;
        position.z = z;
        // Update AABB
        boundingBox.min.set(x - PLAYER_WIDTH / 2, y, z - PLAYER_WIDTH / 2);
        boundingBox.max.set(x + PLAYER_WIDTH / 2, y + PLAYER_HEIGHT, z + PLAYER_WIDTH / 2);
    }

    public void movePosition(float offsetX, float offsetY, float offsetZ) {
        if (offsetZ != 0) {
            position.x += (float) Math.sin(Math.toRadians(rotation.y)) * -1.0f * offsetZ;
            position.z += (float) Math.cos(Math.toRadians(rotation.y)) * offsetZ;
        }
        if (offsetX != 0) {
            position.x += (float) Math.sin(Math.toRadians(rotation.y - 90)) * -1.0f * offsetX;
            position.z += (float) Math.cos(Math.toRadians(rotation.y - 90)) * offsetX;
        }
        position.y += offsetY;

        // Update AABB
        boundingBox.min.set(position.x - PLAYER_WIDTH / 2, position.y, position.z - PLAYER_WIDTH / 2);
        boundingBox.max.set(position.x + PLAYER_WIDTH / 2, position.y + PLAYER_HEIGHT, position.z + PLAYER_WIDTH / 2);
    }

    public Vector3f getRotation() {
        return rotation;
    }

    public void setRotation(float x, float y, float z) {
        rotation.x = x;
        rotation.y = y;
        rotation.z = z;
    }

    public void moveRotation(float offsetX, float offsetY, float offsetZ) {
        rotation.x += offsetX;
        rotation.y += offsetY;
        rotation.z += offsetZ;

        // Clamp pitch to prevent looking all the way around (FPS style)
        if (rotation.x > 89.0f) {
            rotation.x = 89.0f;
        } else if (rotation.x < -89.0f) {
            rotation.x = -89.0f;
        }
    }

    public void update(float interval, ChunkManager chunkManager, boolean jump, boolean moveForward, boolean moveBack,
            boolean moveLeft, boolean moveRight, boolean moveUp, boolean moveDown) {

        // Input Movement (camera-relative)
        float yaw = (float) Math.toRadians(rotation.y);
        float dx = 0;
        float dy = 0;
        float dz = 0;

        if (moveForward) {
            dx -= Math.sin(yaw);
            dz -= Math.cos(yaw);
        }
        if (moveBack) {
            dx += Math.sin(yaw);
            dz += Math.cos(yaw);
        }
        if (moveLeft) {
            dx -= Math.cos(yaw);
            dz += Math.sin(yaw);
        }
        if (moveRight) {
            dx += Math.cos(yaw);
            dz -= Math.sin(yaw);
        }
        if (moveUp) {
            dy += 1.0f;
        }
        if (moveDown) {
            dy -= 1.0f;
        }

        // Normalize horizontal speed
        if (dx != 0 || dz != 0) {
            Vector3f moveDir = new Vector3f(dx, 0, dz).normalize().mul(MOVEMENT_SPEED);
            velocity.x = moveDir.x;
            velocity.z = moveDir.z;
        } else {
            velocity.x = 0;
            velocity.z = 0;
        }

        // Vertical movement (no gravity for now)
        velocity.y = dy * MOVEMENT_SPEED;

        // Gravity DISABLED
        // velocity.y += GRAVITY * interval;

        // Jump
        if (onGround && jump) {
            velocity.y = JUMP_FORCE;
            onGround = false;
        }

        // Apply velocity with collision (X axis)
        boundingBox.move(velocity.x * interval, 0, 0);
        if (checkCollision(chunkManager)) {
            boundingBox.move(-velocity.x * interval, 0, 0);
            velocity.x = 0;
        }
        position.x = boundingBox.min.x + PLAYER_WIDTH / 2;

        // Apply velocity with collision (Z axis)
        boundingBox.move(0, 0, velocity.z * interval);
        if (checkCollision(chunkManager)) {
            boundingBox.move(0, 0, -velocity.z * interval);
            velocity.z = 0;
        }
        position.z = boundingBox.min.z + PLAYER_WIDTH / 2;

        // Apply velocity with collision (Y axis)
        boundingBox.move(0, velocity.y * interval, 0);
        onGround = false;
        if (checkCollision(chunkManager)) {
            boundingBox.move(0, -velocity.y * interval, 0);
            if (velocity.y < 0)
                onGround = true;
            velocity.y = 0;
        }
        position.y = boundingBox.min.y;
    }

    private boolean checkCollision(ChunkManager chunkManager) {
        int minX = (int) Math.floor(boundingBox.min.x);
        int maxX = (int) Math.floor(boundingBox.max.x);
        int minY = (int) Math.floor(boundingBox.min.y);
        int maxY = (int) Math.floor(boundingBox.max.y);
        int minZ = (int) Math.floor(boundingBox.min.z);
        int maxZ = (int) Math.floor(boundingBox.max.z);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = getBlock(chunkManager, x, y, z);
                    if (block != Block.AIR && block != Block.WATER) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Block getBlock(ChunkManager cm, int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        String key = ChunkManager.getChunkKey(cx, cz);
        Chunk c = cm.getChunks().get(key);
        if (c != null) {
            int lx = x & 15;
            int ly = y;
            int lz = z & 15;
            return c.getBlock(lx, ly, lz);
        }
        return Block.AIR;
    }

    public Vector3f getVelocity() {
        return velocity;
    }
}
