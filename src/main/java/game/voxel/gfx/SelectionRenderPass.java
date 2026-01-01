package game.voxel.gfx;

import engine.camera.Camera;
import engine.gfx.RenderPass;
import engine.io.Window;
import engine.raster.Renderer;
import game.voxel.Block;
import game.voxel.ChunkManager;
import game.menu.MenuManager;
import org.joml.Vector3f;

/**
 * Renders the block selection/breaking outline.
 */
public class SelectionRenderPass implements RenderPass {
    private final Renderer renderer;
    private final Camera camera;
    private final ChunkManager chunkManager;
    private final MenuManager menuManager;
    private Vector3f selectedBlock;
    private float breakProgress;

    public SelectionRenderPass(Renderer renderer, Camera camera,
            ChunkManager chunkManager, MenuManager menuManager) {
        this.renderer = renderer;
        this.camera = camera;
        this.chunkManager = chunkManager;
        this.menuManager = menuManager;
    }

    public void setSelection(Vector3f block, float progress) {
        this.selectedBlock = block;
        this.breakProgress = progress;
    }

    @Override
    public void render(Window window, float deltaTime) {
        if (selectedBlock != null && !menuManager.isInMenu()) {
            Block block = chunkManager.getBlockAt(
                    (int) selectedBlock.x,
                    (int) selectedBlock.y,
                    (int) selectedBlock.z);
            float hardness = block.getHardness();
            renderer.renderSelection(window, camera, selectedBlock, breakProgress, hardness);
        }
    }

    @Override
    public int getPriority() {
        return 35; // Render selection after entities
    }

    @Override
    public void cleanup() {
        // Nothing to clean up
    }
}
