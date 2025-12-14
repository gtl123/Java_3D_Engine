package engine.graph;

import engine.utils.Utils;
import engine.world.TimeSystem;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL11.*;

public class SkyRenderer {

    private Mesh skyMesh;
    private ShaderProgram shaderProgram;

    public void init() throws Exception {
        // Create sky dome mesh (large cube/sphere)
        createSkyMesh();

        // Create shader program
        shaderProgram = new ShaderProgram();
        shaderProgram.createVertexShader(Utils.loadResource("/shaders/sky_vertex.vs"));
        shaderProgram.createFragmentShader(Utils.loadResource("/shaders/sky_fragment.fs"));
        shaderProgram.link();

        // Create uniforms
        shaderProgram.createUniform("projectionMatrix");
        shaderProgram.createUniform("viewMatrix");
        shaderProgram.createUniform("timeOfDay");
        shaderProgram.createUniform("sunDirection");
        shaderProgram.createUniform("moonDirection");
        shaderProgram.createUniform("cameraPos");
    }

    private void createSkyMesh() {
        // Create large cube for sky
        float size = 500.0f;
        float[] positions = new float[] {
                // Front
                -size, -size, size,
                size, -size, size,
                size, size, size,
                -size, size, size,
                // Back
                size, -size, -size,
                -size, -size, -size,
                -size, size, -size,
                size, size, -size,
                // Left
                -size, -size, -size,
                -size, -size, size,
                -size, size, size,
                -size, size, -size,
                // Right
                size, -size, size,
                size, -size, -size,
                size, size, -size,
                size, size, size,
                // Top
                -size, size, size,
                size, size, size,
                size, size, -size,
                -size, size, -size,
                // Bottom
                -size, -size, -size,
                size, -size, -size,
                size, -size, size,
                -size, -size, size
        };

        int[] indices = new int[] {
                0, 1, 2, 2, 3, 0, // Front
                4, 5, 6, 6, 7, 4, // Back
                8, 9, 10, 10, 11, 8, // Left
                12, 13, 14, 14, 15, 12, // Right
                16, 17, 18, 18, 19, 16, // Top
                20, 21, 22, 22, 23, 20 // Bottom
        };

        // Dummy texture coords and normals
        float[] texCoords = new float[positions.length / 3 * 2];
        float[] normals = new float[positions.length];

        skyMesh = new Mesh(positions, texCoords, normals, indices);
    }

    public void render(Matrix4f projectionMatrix, Matrix4f viewMatrix, TimeSystem timeSystem) {
        glDepthMask(false); // Don't write to depth buffer

        shaderProgram.bind();

        shaderProgram.setUniform("projectionMatrix", projectionMatrix);
        shaderProgram.setUniform("viewMatrix", viewMatrix);
        shaderProgram.setUniform("timeOfDay", timeSystem.getTimeOfDay());
        shaderProgram.setUniform("sunDirection", timeSystem.getSunDirection());
        shaderProgram.setUniform("moonDirection", timeSystem.getMoonDirection());

        // Extract camera position from view matrix (inverse translation)
        Matrix4f invView = new Matrix4f(viewMatrix).invert();
        shaderProgram.setUniform("cameraPos", new Vector3f(invView.m30(), invView.m31(), invView.m32()));

        skyMesh.render();

        shaderProgram.unbind();

        glDepthMask(true); // Re-enable depth writing
    }

    public void cleanup() {
        if (shaderProgram != null) {
            shaderProgram.cleanup();
        }
        if (skyMesh != null) {
            skyMesh.cleanup();
        }
    }
}
