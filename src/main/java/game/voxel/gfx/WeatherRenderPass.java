package game.voxel.gfx;

import engine.camera.Camera;
import engine.gfx.RenderPass;
import engine.io.Window;
import engine.raster.Renderer;
import engine.raster.Transformation;
import game.voxel.world.WeatherSystem;

public class WeatherRenderPass implements RenderPass {
    private final Renderer renderer;
    private final WeatherParticleSystem particleSystem;
    private final WeatherSystem weatherSystem;
    private final Camera camera;
    private final Transformation transformation;

    public WeatherRenderPass(Renderer renderer, WeatherParticleSystem particleSystem,
            WeatherSystem weatherSystem, Camera camera, Transformation transformation) {
        this.renderer = renderer;
        this.particleSystem = particleSystem;
        this.weatherSystem = weatherSystem;
        this.camera = camera;
        this.transformation = transformation;
    }

    @Override
    public void render(Window window, float deltaTime) {
        particleSystem.update(deltaTime, camera, weatherSystem);
        particleSystem.render(camera, weatherSystem, renderer, transformation);
    }

    @Override
    public int getPriority() {
        return 40; // Render weather after entities
    }

    @Override
    public void cleanup() {
        particleSystem.cleanup();
    }
}
