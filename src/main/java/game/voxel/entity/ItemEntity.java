package game.voxel.entity;

import engine.entity.Entity;
import engine.raster.CubeMeshBuilder;
import game.voxel.Block;
import game.voxel.ChunkManager;
import org.joml.Vector3f;

public class ItemEntity {
    private final Block block;
    private final Entity entity;
    private final Vector3f position;
    private final Vector3f velocity;
    private float rotation;

    /**
     * Creates a dropped block entity. The caller is responsible for assigning a
     * texture to the underlying mesh (e.g. using the shared terrain atlas) to keep
     * visuals consistent with world blocks.
     */
    public ItemEntity(Block block, Vector3f position, Vector3f velocity) {
        this.block = block;
        this.position = new Vector3f(position);
        this.velocity = new Vector3f(velocity);
        // Small cube for the item
        this.entity = new Entity(CubeMeshBuilder.createCube(0.3f, 0.3f, 0.3f, block));
        this.entity.setLocalPosition(position.x, position.y, position.z);
    }

    public void update(float interval, ChunkManager chunkManager) {
        // Gravity
        velocity.y -= 25.0f * interval;

        // Apply velocity
        position.x += velocity.x * interval;
        position.y += velocity.y * interval;
        position.z += velocity.z * interval;

        // Simple ground collision
        if (chunkManager.isSolidBlock((int) Math.floor(position.x), (int) Math.floor(position.y - 0.15f),
                (int) Math.floor(position.z))) {
            position.y = (float) Math.floor(position.y - 0.15f) + 1.15f;
            velocity.y = 0;
            velocity.x *= 0.8f;
            velocity.z *= 0.8f;
        }

        // Air friction
        velocity.x *= 0.98f;
        velocity.z *= 0.98f;

        // Rotation
        rotation += 2.0f * interval;
        entity.setLocalPosition(position.x, position.y, position.z);
        entity.setLocalRotation(0, rotation, 0);
    }

    public Entity getGameItem() {
        return entity;
    }

    public Block getBlock() {
        return block;
    }

    public Vector3f getPosition() {
        return position;
    }
}
    