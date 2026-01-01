package game.voxel.gfx;

import engine.camera.Camera;
import engine.gfx.RenderPass;
import engine.io.Window;
import engine.raster.Renderer;
import game.voxel.ChunkManager;
import game.voxel.world.TimeSystem;
import game.voxel.world.WeatherSystem;

/**
 * Renders the voxel terrain (chunks).
 */
public class TerrainRenderPass implements RenderPass {
    private final Renderer renderer;
    private final ChunkManager chunkManager;
    private final TimeSystem timeSystem;
    private final WeatherSystem weatherSystem;
    private final Camera camera;

    public TerrainRenderPass(Renderer renderer, ChunkManager chunkManager,
            TimeSystem timeSystem, WeatherSystem weatherSystem, Camera camera) {
        this.renderer = renderer;
        this.chunkManager = chunkManager;
        this.timeSystem = timeSystem;
        this.weatherSystem = weatherSystem;
        this.camera = camera;
    }

    @Override
    public void render(Window window, float deltaTime) {
        renderer.renderChunks(window, camera, chunkManager.getChunks().values(), timeSystem, weatherSystem);
    }

    @Override
    public int getPriority() {
        return 20; // Render terrain after sky
    }

    @Override
    public void cleanup() {
        // Chunks are managed by ChunkManager
    }
}
