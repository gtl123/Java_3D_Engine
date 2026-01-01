package game.voxel.gfx;

import engine.camera.Camera;
import engine.entity.Entity;
import engine.gfx.RenderPass;
import engine.io.Window;
import engine.raster.Renderer;
import engine.raster.Transformation;
import game.voxel.entity.ItemEntity;
import game.voxel.entity.PlayerController;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders game entities (player, items, etc).
 */
public class EntityRenderPass implements RenderPass {
    private final Renderer renderer;
    private final Transformation transformation;
    private final Camera camera;
    private final PlayerController player;
    private final List<ItemEntity> itemEntities;

    public EntityRenderPass(Renderer renderer, Transformation transformation,
            Camera camera, PlayerController player,
            List<ItemEntity> itemEntities) {
        this.renderer = renderer;
        this.transformation = transformation;
        this.camera = camera;
        this.player = player;
        this.itemEntities = itemEntities;
    }

    @Override
    public void render(Window window, float deltaTime) {
        // Render player parts
        renderer.renderGameItems(player.getAllParts(), camera, transformation);

        // Render item entities
        List<Entity> itemsToRender = new ArrayList<>();
        for (ItemEntity ie : itemEntities) {
            itemsToRender.add(ie.getGameItem());
        }
        renderer.renderGameItems(itemsToRender, camera, transformation);
    }

    @Override
    public int getPriority() {
        return 30; // Render entities after terrain
    }

    @Override
    public void cleanup() {
        // Entities are managed elsewhere
    }
}
