package engine.graph;

import engine.io.Window;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

public class HUD {

    private final List<GameItem> gameItems;
    private final GUIShaderProgram shaderProgram;
    private Mesh mesh;

    // Stats
    private String fpsText = "FPS: 0";
    private String statsText = "Chunks: 0";

    public HUD() throws Exception {
        gameItems = new ArrayList<>();
        shaderProgram = new GUIShaderProgram();
        setupMesh();
    }

    public Mesh getMesh() {
        return mesh;
    }

    private void setupMesh() {
        // Standard Quad Mesh for GUI elements
        float[] positions = new float[] {
                -1.0f, 1.0f, 0.0f,
                -1.0f, -1.0f, 0.0f,
                1.0f, -1.0f, 0.0f,
                1.0f, 1.0f, 0.0f,
        };
        float[] textCoords = new float[] {
                0.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 1.0f,
                1.0f, 0.0f
        };
        int[] indices = new int[] { 0, 1, 3, 3, 1, 2 };
        this.mesh = new Mesh(positions, textCoords, new float[positions.length], indices); // Dummy normals
    }

    public void init(Window window) {
        // Init logic if needed
    }

    public void render(Window window) {
        shaderProgram.bind();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);

        // Render crosshair and other game items
        for (GameItem item : gameItems) {
            Matrix4f modelMatrix = new Matrix4f();
            modelMatrix.translate(item.getPosition());
            modelMatrix.scale(item.getScale());

            shaderProgram.setUniform("projModelMatrix", modelMatrix);
            shaderProgram.setUniform("colour",
                    item.getMesh().getColour() != null ? new Vector4f(item.getMesh().getColour(), 1.0f)
                            : new Vector4f(1, 1, 1, 1));
            shaderProgram.setUniform("hasTexture", item.getMesh().getTexture() != null ? 1 : 0);

            if (item.getMesh().getTexture() != null) {
                shaderProgram.setUniform("texture_sampler", 0);
                glActiveTexture(GL_TEXTURE0);
                item.getMesh().getTexture().bind();
            }

            item.getMesh().render();
        }

        // Render stats overlay (simple colored background for text)
        renderStatsOverlay(window);

        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        shaderProgram.unbind();

        // Simple text rendering (print to title for now)
        // TODO: Proper text rendering with bitmap fonts
    }

    public void addItem(GameItem item) {
        // Reuse the quad mesh but allow item to have its own texture/color
        // Wait, standard Mesh inside GameItem? Yes.
        // But we want to share the geometry (Quad) and only change Texture/Scale/Pos.
        // For simplicity now, let's just assume GameItem has the Quad mesh.
        item.setMesh(this.mesh);
        gameItems.add(item);
    }

    public void setFPS(int fps) {
        this.fpsText = "FPS: " + fps;
    }

    public void setStats(int chunks, int vertices) {
        this.statsText = "Chunks: " + chunks + " | Vertices: " + vertices;
    }

    public String getFPSText() {
        return fpsText;
    }

    public String getStatsText() {
        return statsText;
    }

    private void renderStatsOverlay(Window window) {
        // Render a semi-transparent background box for stats in top-left
        Matrix4f modelMatrix = new Matrix4f();
        // Position: top-left corner, scale to small rectangle
        modelMatrix.translate(-0.7f, 0.85f, 0);
        modelMatrix.scale(0.25f, 0.12f, 1);

        shaderProgram.setUniform("projModelMatrix", modelMatrix);
        shaderProgram.setUniform("colour", new Vector4f(0, 0, 0, 0.5f)); // Semi-transparent black
        shaderProgram.setUniform("hasTexture", 0);

        mesh.render();

        // Note: Actual text rendering would require a font texture atlas
        // For now, stats are shown in window title
    }

    public void cleanup() {
        shaderProgram.cleanup();
        mesh.cleanup();
    }
}
