package game.voxel.gfx;

import engine.camera.Camera;
import engine.entity.BrowserEntity;
import engine.gfx.RenderPass;
import engine.io.Window;
import engine.raster.Renderer;
import engine.raster.Transformation;
import org.joml.Matrix4f;

import java.util.List;

public class BrowserRenderPass implements RenderPass {
    private final Renderer renderer;
    private final Transformation transformation;
    private final Camera camera;
    private final List<BrowserEntity> browserEntities;

    public BrowserRenderPass(Renderer renderer, Transformation transformation, Camera camera, List<BrowserEntity> browserEntities) {
        this.renderer = renderer;
        this.transformation = transformation;
        this.camera = camera;
        this.browserEntities = browserEntities;
    }

    @Override
    public void render(Window window, float deltaTime) {
        if (browserEntities.isEmpty()) return;

        renderer.bindShader();
        renderer.setupSceneUniforms(camera, transformation);

        for (BrowserEntity entity : browserEntities) {
            Matrix4f viewMatrix = transformation.getViewMatrix(camera);
            Matrix4f modelViewMatrix = transformation.getModelViewMatrix(entity, viewMatrix);
            renderer.renderGameItemQuick(entity.getMesh(), modelViewMatrix);
        }

        renderer.unbindShader();
    }

    @Override
    public int getPriority() {
        return 30; // Render after terrain
    }
}
