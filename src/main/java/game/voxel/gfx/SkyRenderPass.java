package game.voxel.gfx;

import engine.camera.Camera;
import engine.gfx.RenderPass;
import engine.io.Window;
import engine.raster.Transformation;
import engine.shaders.SkyDomeShader;
import game.voxel.world.TimeSystem;
import game.voxel.world.WeatherSystem;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.*;

/**
 * Renders the sky dome with atmospheric scattering.
 */
public class SkyRenderPass implements RenderPass {
    private static final float PLANET_RADIUS = 600_371_000.0f;
    private static final float NEAR_PLANE = 0.01f;
    private static final float FAR_PLANE = 1000.0f;

    private final SkyDomeShader skyShader;
    private final Transformation transformation;
    private final TimeSystem timeSystem;
    private final WeatherSystem weatherSystem;
    private final Camera camera;
    private final double fov;
    private final engine.raster.Renderer renderer;

    private int skyViewTextureId;
    private int transmittanceTextureId;

    public SkyRenderPass(SkyDomeShader skyShader, Transformation transformation,
            TimeSystem timeSystem, WeatherSystem weatherSystem, Camera camera, double fov,
            int skyViewTextureId, int transmittanceTextureId,
            engine.raster.Renderer renderer) {
        this.skyShader = skyShader;
        this.transformation = transformation;
        this.timeSystem = timeSystem;
        this.weatherSystem = weatherSystem;
        this.camera = camera;
        this.fov = fov;
        this.skyViewTextureId = skyViewTextureId;
        this.transmittanceTextureId = transmittanceTextureId;
        this.renderer = renderer;
    }

    @Override
    public void render(Window window, float deltaTime) {
        Matrix4f projection = transformation.getProjectionMatrix(
                (float) Math.toRadians(fov), window.getWidth(), window.getHeight(), NEAR_PLANE, FAR_PLANE);

        // Rotation-only view (ignore translation)
        Matrix4f rotOnlyView = new Matrix4f()
                .rotateX((float) Math.toRadians(camera.getRotation().x))
                .rotateY((float) Math.toRadians(camera.getRotation().y));

        Matrix4f mvp = new Matrix4f(projection).mul(rotOnlyView);

        skyShader.bind();
        skyShader.setUniform("uMVP", mvp);
        skyShader.setUniform("uViewportSize", new Vector2f(window.getWidth(), window.getHeight()));
        skyShader.setUniform("uCameraPos", camera.getPosition());
        skyShader.setUniform("uCameraHeight", camera.getPosition().y);
        skyShader.setUniform("uSunDir", timeSystem.getSunDirection());
        skyShader.setUniform("uSunIntensity", 1.0f - weatherSystem.getSkyDarkness() * 0.8f);
        skyShader.setUniform("uMieG", 0.78f);
        skyShader.setUniform("uTurbidity", 3.0f);
        skyShader.setUniform("uPlanetRadius", PLANET_RADIUS);
        skyShader.setUniform("uAtmosphereTop", 800_000.0f);
        skyShader.setUniform("uUp", new Vector3f(0, 1, 0));

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, skyViewTextureId);
        skyShader.setUniform("uSkyViewLUT", 0);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, transmittanceTextureId);
        skyShader.setUniform("uTransmittanceLUT", 1);

        // Draw sky geometry
        glDisable(GL_CULL_FACE);
        glDepthMask(false);
        renderer.renderSkyDome();
        glDepthMask(true);
        glEnable(GL_CULL_FACE);

        skyShader.unbind();
    }

    @Override
    public int getPriority() {
        return 10; // Render sky first
    }

    @Override
    public void cleanup() {
        // Textures are managed elsewhere
    }
}
