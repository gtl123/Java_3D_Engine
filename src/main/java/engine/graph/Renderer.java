package engine.graph;

import engine.io.Window;
import engine.utils.Utils;
import org.joml.Matrix4f;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Renderer {

    private static final float FOV = (float) Math.toRadians(60.0f);
    private static final float Z_NEAR = 0.01f;
    private static final float Z_FAR = 1000.f;

    private final Transformation transformation;

    private ShaderProgram shaderProgram;
    private ShaderProgram instancedShaderProgram;

    public Renderer() {
        transformation = new Transformation();
    }

    public void init(Window window) throws Exception {
        setupSceneShader();
        setupInstancedShader();
    }

    private void setupSceneShader() throws Exception {
        shaderProgram = new ShaderProgram();
        shaderProgram.createVertexShader(Utils.loadResource("/shaders/vertex.vs"));
        shaderProgram.createFragmentShader(Utils.loadResource("/shaders/fragment.fs"));
        shaderProgram.link();

        shaderProgram.createUniform("projectionMatrix");
        shaderProgram.createUniform("modelViewMatrix");
        shaderProgram.createUniform("texture_sampler");
        shaderProgram.createUniform("useColour");

        // Lighting uniforms
        shaderProgram.createUniform("lightDirection");
        shaderProgram.createUniform("lightColor");
        shaderProgram.createUniform("ambientStrength");
        shaderProgram.createUniform("useColour");
    }

    private void setupInstancedShader() throws Exception {
        instancedShaderProgram = new ShaderProgram();
        instancedShaderProgram.createVertexShader(Utils.loadResource("/shaders/vertex_instanced.vs"));
        instancedShaderProgram.createFragmentShader(Utils.loadResource("/shaders/fragment.fs")); // Reuse fragment
        instancedShaderProgram.link();

        instancedShaderProgram.createUniform("projectionMatrix");
        instancedShaderProgram.createUniform("modelViewMatrix"); // For non-instanced fallback or base
        instancedShaderProgram.createUniform("texture_sampler");
        instancedShaderProgram.createUniform("colour");
        instancedShaderProgram.createUniform("useColour");
        instancedShaderProgram.createUniform("isInstanced");
    }

    public void render(Window window, Camera camera, GameItem[] gameItems,
            Map<InstancedMesh, List<GameItem>> instancedItems) {
        clear();

        if (window.isResized()) {
            glViewport(0, 0, window.getWidth(), window.getHeight());
            window.setResized(false);
        }

        // Render generic items
        shaderProgram.bind();

        Matrix4f projectionMatrix = transformation.getProjectionMatrix(FOV, window.getWidth(), window.getHeight(),
                Z_NEAR, Z_FAR);
        shaderProgram.setUniform("projectionMatrix", projectionMatrix);

        Matrix4f viewMatrix = transformation.getViewMatrix(camera);

        for (GameItem gameItem : gameItems) {
            Mesh mesh = gameItem.getMesh();
            Matrix4f modelViewMatrix = transformation.getModelViewMatrix(gameItem, viewMatrix);
            shaderProgram.setUniform("modelViewMatrix", modelViewMatrix);
            shaderProgram.setUniform("colour", mesh.getColour());
            shaderProgram.setUniform("useColour", mesh.getTexture() != null ? 0 : 1);

            mesh.render();
        }
        shaderProgram.unbind();

        // Render instanced items
        if (instancedItems != null && !instancedItems.isEmpty()) {
            instancedShaderProgram.bind();
            instancedShaderProgram.setUniform("projectionMatrix", projectionMatrix);
            instancedShaderProgram.setUniform("isInstanced", 1);

            // Texture uniform is usually 0 if we bound to GL_TEXTURE0
            instancedShaderProgram.setUniform("texture_sampler", 0);

            for (Map.Entry<InstancedMesh, List<GameItem>> entry : instancedItems.entrySet()) {
                InstancedMesh mesh = entry.getKey();
                List<GameItem> list = entry.getValue();

                instancedShaderProgram.setUniform("colour", mesh.getColour());
                instancedShaderProgram.setUniform("useColour", mesh.getTexture() != null ? 0 : 1);

                mesh.renderListInstanced(list, transformation, viewMatrix);
            }
            instancedShaderProgram.unbind();
        }
    }

    public void clear() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    public void cleanup() {
        if (shaderProgram != null) {
            shaderProgram.cleanup();
        }
        if (instancedShaderProgram != null) {
            instancedShaderProgram.cleanup();
        }
        if (screenQuadShaderProgram != null) {
            screenQuadShaderProgram.cleanup();
        }
        glDeleteVertexArrays(screenQuadVAO);
        glDeleteBuffers(screenQuadVBO);
    }

    private ShaderProgram screenQuadShaderProgram;
    private int screenQuadVAO;
    private int screenQuadVBO;

    public void initRayTracing() throws Exception {
        screenQuadShaderProgram = new ShaderProgram();
        screenQuadShaderProgram.createVertexShader(Utils.loadResource("/shaders/screen_quad.vs"));
        screenQuadShaderProgram.createFragmentShader(Utils.loadResource("/shaders/screen_quad.fs"));
        screenQuadShaderProgram.link();
        screenQuadShaderProgram.createUniform("screenTexture");

        // Create Quad VAO in NDC
        float[] quadVertices = {
                // positions // texCoords
                -1.0f, 1.0f, 0.0f, 1.0f,
                -1.0f, -1.0f, 0.0f, 0.0f,
                1.0f, -1.0f, 1.0f, 0.0f,

                -1.0f, 1.0f, 0.0f, 1.0f,
                1.0f, -1.0f, 1.0f, 0.0f,
                1.0f, 1.0f, 1.0f, 1.0f
        };

        screenQuadVAO = glGenVertexArrays();
        screenQuadVBO = glGenBuffers();
        glBindVertexArray(screenQuadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, screenQuadVBO);
        glBufferData(GL_ARRAY_BUFFER, quadVertices, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * 4, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * 4, 2 * 4);
        glBindVertexArray(0);
    }

    public void renderChunks(Window window, Camera camera, Collection<engine.voxel.Chunk> chunks,
            engine.world.TimeSystem timeSystem) {
        // Don't clear here - sky is rendered first
        if (window.isResized()) {
            glViewport(0, 0, window.getWidth(), window.getHeight());
            window.setResized(false);
        }

        shaderProgram.bind();

        Matrix4f projectionMatrix = transformation.getProjectionMatrix(FOV, window.getWidth(), window.getHeight(),
                Z_NEAR, Z_FAR);
        shaderProgram.setUniform("projectionMatrix", projectionMatrix);
        Matrix4f viewMatrix = transformation.getViewMatrix(camera);

        // Set lighting uniforms from TimeSystem
        shaderProgram.setUniform("lightDirection", timeSystem.getSunDirection());
        shaderProgram.setUniform("lightColor", timeSystem.getLightColor());
        shaderProgram.setUniform("ambientStrength", timeSystem.getAmbientStrength());

        for (engine.voxel.Chunk chunk : chunks) {
            Mesh mesh = chunk.getMesh();
            if (mesh != null) {
                // Calculate model matrix for this chunk
                Matrix4f modelMatrix = new Matrix4f();
                modelMatrix.translate(chunk.getChunkX() * engine.voxel.Chunk.SIZE_X, 0,
                        chunk.getChunkZ() * engine.voxel.Chunk.SIZE_Z);

                // Combine with view matrix
                Matrix4f modelViewMatrix = new Matrix4f(viewMatrix);
                modelViewMatrix.mul(modelMatrix);

                shaderProgram.setUniform("modelViewMatrix", modelViewMatrix);
                mesh.render();
            }
        }

        shaderProgram.unbind();
    }

    public void renderRayTrace(engine.graph.raytrace.RayTracer rayTracer) {
        clear();
        screenQuadShaderProgram.bind();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, rayTracer.getOutputTextureId());
        screenQuadShaderProgram.setUniform("screenTexture", 0);

        glBindVertexArray(screenQuadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        screenQuadShaderProgram.unbind();
    }
}
