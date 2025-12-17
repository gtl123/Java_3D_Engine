package engine.graph;

import engine.io.Window;
import engine.utils.Utils;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Renderer {

    private static final float FOV = (float) Math.toRadians(120.0f); // align with VoxelGame
    private static final float Z_NEAR = 0.01f;
    private static final float Z_FAR = 1000.f;

    private final Transformation transformation;

    private ShaderProgram shaderProgram;
    private ShaderProgram instancedShaderProgram;

    // Scene framebuffer resources
    private int sceneFBO = 0;
    private int sceneColorTex = 0;
    private int sceneDepthTex = 0; // keep this
    private int sceneWidth = 0;
    private int sceneHeight = 0;

    // Optional screen quad for raytracing
    private ShaderProgram screenQuadShaderProgram;
    private int screenQuadVAO;
    private int screenQuadVBO;

    // Sky dome resources (own VAO/VBO/EBO)
    private int skyDomeVAO = 0;
    private int skyDomeVBO = 0;
    private int skyDomeEBO = 0;
    private int skyDomeIndexCount = 0;

    public Renderer() {
        transformation = new Transformation();
    }

    public void init(Window window) throws Exception {
        setupSceneShader();
        setupInstancedShader();
        initSceneTarget(window.getWidth(), window.getHeight());

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        // Build sky dome mesh once (segments: slices x stacks, radius large enough to
        // cover far plane)
        buildSkyDome(32, 16, 100.0f);
    }

    public int getSceneFBO() {
        return sceneFBO;
    }

    public int getSceneColorTex() {
        return sceneColorTex;
    }

    public int getSceneDepthTex() {
        return sceneDepthTex;
    }

    private void setupSceneShader() throws Exception {
        shaderProgram = new ShaderProgram();
        shaderProgram.createVertexShader(Utils.loadResource("shaders/vertex.vs"));
        shaderProgram.createFragmentShader(Utils.loadResource("shaders/fragment.fs"));
        shaderProgram.link();

        shaderProgram.createUniform("projectionMatrix");
        shaderProgram.createUniform("modelViewMatrix");
        shaderProgram.createUniform("texture_sampler");
        shaderProgram.createUniform("useColour");
        shaderProgram.createUniform("colour");

        // Lighting uniforms
        shaderProgram.createUniform("lightDirection");
        shaderProgram.createUniform("lightColor");
        shaderProgram.createUniform("ambientStrength");
    }

    private void setupInstancedShader() throws Exception {
        instancedShaderProgram = new ShaderProgram();
        instancedShaderProgram.createVertexShader(Utils.loadResource("shaders/vertex_instanced.vs"));
        instancedShaderProgram.createFragmentShader(Utils.loadResource("shaders/fragment.fs"));
        instancedShaderProgram.link();

        instancedShaderProgram.createUniform("projectionMatrix");
        instancedShaderProgram.createUniform("modelViewMatrix");
        instancedShaderProgram.createUniform("texture_sampler");
        instancedShaderProgram.createUniform("colour");
        instancedShaderProgram.createUniform("useColour");
        instancedShaderProgram.createUniform("isInstanced");
    }

    private void initSceneTarget(int width, int height) {
        sceneWidth = width;
        sceneHeight = height;

        // Delete old if exists
        if (sceneFBO != 0) {
            glDeleteFramebuffers(sceneFBO);
            glDeleteTextures(sceneColorTex);
            glDeleteTextures(sceneDepthTex);
            sceneFBO = 0;
            sceneColorTex = 0;
            sceneDepthTex = 0;
        }

        // Create FBO
        sceneFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, sceneFBO);

        // Color texture
        sceneColorTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, sceneColorTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_2D, sceneColorTex, 0);

        // Depth texture (sampled in post)
        sceneDepthTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, sceneDepthTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, width, height, 0,
                GL_DEPTH_COMPONENT, GL_FLOAT, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                GL_TEXTURE_2D, sceneDepthTex, 0);

        // Explicitly set draw buffer(s) for this FBO
        glDrawBuffers(new int[] { GL_COLOR_ATTACHMENT0 });

        // Check completeness
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            throw new IllegalStateException("Scene framebuffer incomplete: " + status);
        }

        // Unbind
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    // Batch render: bind once, set projection once, then draw all items (with
    // hierarchy support)
    public void renderGameItems(List<GameItem> items, Camera camera, Transformation transformation) {
        if (items == null || items.isEmpty())
            return;

        shaderProgram.bind();

        Matrix4f projectionMatrix = transformation.getProjectionMatrix(
                FOV, sceneWidth, sceneHeight, Z_NEAR, Z_FAR);
        shaderProgram.setUniform("projectionMatrix", projectionMatrix);
        shaderProgram.setUniform("texture_sampler", 0);

        Matrix4f viewMatrix = transformation.getViewMatrix(camera);

        // Render each root item and its children
        for (GameItem item : items) {
            renderItemRecursive(item, viewMatrix);
        }

        shaderProgram.unbind();
    }

    // Recursive renderer for parent/child hierarchy
    private void renderItemRecursive(GameItem item, Matrix4f viewMatrix) {
        Mesh mesh = item.getMesh();
        if (mesh != null) {
            // Use hierarchical world transform
            Matrix4f modelMatrix = item.getWorldTransform();
            Matrix4f modelViewMatrix = new Matrix4f(viewMatrix).mul(modelMatrix);

            shaderProgram.setUniform("modelViewMatrix", modelViewMatrix);
            shaderProgram.setUniform("colour", mesh.getColour());
            shaderProgram.setUniform("useColour", mesh.getTexture() != null ? 0 : 1);

            if (mesh.getTexture() != null) {
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, mesh.getTexture().getId());
            }

            mesh.render();
        }

        // Render children
        for (GameItem child : item.getChildren()) {
            renderItemRecursive(child, viewMatrix);
        }
    }

    public void clear() {
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    // Draw chunks into the currently bound framebuffer.
    // Assumes caller has already bound the scene FBO and set viewport.
    public void renderChunksToTexture(Window window,
            Camera camera,
            Collection<engine.voxel.Chunk> chunks,
            engine.world.TimeSystem timeSystem) {

        shaderProgram.bind();

        Matrix4f projectionMatrix = transformation.getProjectionMatrix(
                FOV, sceneWidth, sceneHeight, Z_NEAR, Z_FAR);
        shaderProgram.setUniform("projectionMatrix", projectionMatrix);

        Matrix4f viewMatrix = transformation.getViewMatrix(camera);

        // Lighting
        shaderProgram.setUniform("lightDirection", timeSystem.getSunDirection());
        shaderProgram.setUniform("lightColor", timeSystem.getLightColor());
        shaderProgram.setUniform("ambientStrength", timeSystem.getAmbientStrength());

        // Sampler binding for meshes
        shaderProgram.setUniform("texture_sampler", 0);

        for (engine.voxel.Chunk chunk : chunks) {
            Mesh mesh = chunk.getMesh();
            if (mesh != null) {
                Matrix4f modelMatrix = new Matrix4f()
                        .translate(chunk.getChunkX() * engine.voxel.Chunk.SIZE_X, 0,
                                chunk.getChunkZ() * engine.voxel.Chunk.SIZE_Z);
                Matrix4f modelViewMatrix = new Matrix4f(viewMatrix).mul(modelMatrix);
                shaderProgram.setUniform("modelViewMatrix", modelViewMatrix);

                shaderProgram.setUniform("colour", mesh.getColour());
                shaderProgram.setUniform("useColour", mesh.getTexture() != null ? 0 : 1);

                if (mesh.getTexture() != null) {
                    glActiveTexture(GL_TEXTURE0);
                    glBindTexture(GL_TEXTURE_2D, mesh.getTexture().getId());
                }

                mesh.render();
            }
        }

        shaderProgram.unbind();
    }

    // Direct on-screen chunk rendering (used if no postprocess)
    public void renderChunks(Window window, Camera camera,
            Collection<engine.voxel.Chunk> chunks,
            engine.world.TimeSystem timeSystem) {
        handleResize(window);

        shaderProgram.bind();

        Matrix4f projectionMatrix = transformation.getProjectionMatrix(
                FOV, window.getWidth(), window.getHeight(), Z_NEAR, Z_FAR);
        shaderProgram.setUniform("projectionMatrix", projectionMatrix);
        Matrix4f viewMatrix = transformation.getViewMatrix(camera);

        shaderProgram.setUniform("lightDirection", timeSystem.getSunDirection());
        shaderProgram.setUniform("lightColor", timeSystem.getLightColor());
        shaderProgram.setUniform("ambientStrength", timeSystem.getAmbientStrength());

        // Ensure sampler is set
        shaderProgram.setUniform("texture_sampler", 0);

        for (engine.voxel.Chunk chunk : chunks) {
            Mesh mesh = chunk.getMesh();
            if (mesh != null) {
                Matrix4f modelMatrix = new Matrix4f()
                        .translate(chunk.getChunkX() * engine.voxel.Chunk.SIZE_X, 0,
                                chunk.getChunkZ() * engine.voxel.Chunk.SIZE_Z);
                Matrix4f modelViewMatrix = new Matrix4f(viewMatrix).mul(modelMatrix);
                shaderProgram.setUniform("modelViewMatrix", modelViewMatrix);

                // Mirror the to-texture path
                shaderProgram.setUniform("colour", mesh.getColour());
                shaderProgram.setUniform("useColour", mesh.getTexture() != null ? 0 : 1);

                if (mesh.getTexture() != null) {
                    glActiveTexture(GL_TEXTURE0);
                    glBindTexture(GL_TEXTURE_2D, mesh.getTexture().getId());
                }

                mesh.render();
            }
        }

        shaderProgram.unbind();
    }

    private void handleResize(Window window) {
        if (window.isResized()) {
            glViewport(0, 0, window.getWidth(), window.getHeight());
            initSceneTarget(window.getWidth(), window.getHeight());
            window.setResized(false);
        }
    }

    // --------------------------
    // Sky dome
    // --------------------------

    /**
     * Render the sky dome mesh. Assumes SkyDomeShader is bound and its uniforms are
     * set by the caller.
     */
    public void renderSkyDome() {
        if (skyDomeVAO == 0 || skyDomeIndexCount == 0)
            return;

        glBindVertexArray(skyDomeVAO);
        glDrawElements(GL_TRIANGLES, skyDomeIndexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    /**
     * Builds a sphere (sky dome) VAO/VBO/EBO with inward normals for inside view.
     * 
     * @param slices longitudinal segments (e.g., 32)
     * @param stacks latitudinal segments (e.g., 16)
     * @param radius sphere radius; choose large enough to enclose far plane
     */
    private void buildSkyDome(int slices, int stacks, float radius) {
        List<Float> positions = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        for (int i = 0; i <= stacks; i++) {
            double v = (double) i / stacks;
            double phi = v * Math.PI;
            for (int j = 0; j <= slices; j++) {
                double u = (double) j / slices;
                double theta = u * (Math.PI * 2.0);

                float x = (float) (radius * Math.sin(phi) * Math.cos(theta));
                float y = (float) (radius * Math.cos(phi));
                float z = (float) (radius * Math.sin(phi) * Math.sin(theta));

                positions.add(x);
                positions.add(y);
                positions.add(z);
            }
        }

        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                int first = (i * (slices + 1)) + j;
                int second = first + slices + 1;

                indices.add(first);
                indices.add(second);
                indices.add(first + 1);

                indices.add(second);
                indices.add(second + 1);
                indices.add(first + 1);
            }
        }

        float[] posArr = new float[positions.size()];
        for (int i = 0; i < positions.size(); i++)
            posArr[i] = positions.get(i);

        int[] idxArr = indices.stream().mapToInt(i -> i).toArray();
        skyDomeIndexCount = idxArr.length;

        skyDomeVAO = glGenVertexArrays();
        glBindVertexArray(skyDomeVAO);

        skyDomeVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, skyDomeVBO);
        glBufferData(GL_ARRAY_BUFFER, posArr, GL_STATIC_DRAW);

        glEnableVertexAttribArray(0); // position at location 0
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);

        skyDomeEBO = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, skyDomeEBO);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idxArr, GL_STATIC_DRAW);

        glBindVertexArray(0);
    }

    public void cleanup() {
        if (shaderProgram != null)
            shaderProgram.cleanup();
        if (instancedShaderProgram != null)
            instancedShaderProgram.cleanup();
        if (screenQuadShaderProgram != null)
            screenQuadShaderProgram.cleanup();
        if (screenQuadVAO != 0)
            glDeleteVertexArrays(screenQuadVAO);
        if (screenQuadVBO != 0)
            glDeleteBuffers(screenQuadVBO);

        if (sceneFBO != 0)
            glDeleteFramebuffers(sceneFBO);
        if (sceneColorTex != 0)
            glDeleteTextures(sceneColorTex);
        if (sceneDepthTex != 0)
            glDeleteTextures(sceneDepthTex);

        // Sky dome cleanup
        if (skyDomeVAO != 0)
            glDeleteVertexArrays(skyDomeVAO);
        if (skyDomeVBO != 0)
            glDeleteBuffers(skyDomeVBO);
        if (skyDomeEBO != 0)
            glDeleteBuffers(skyDomeEBO);
    }

    // --------------------------
    // Ray tracing screen quad (optional)
    // --------------------------

    public void initRayTracing() throws Exception {
        screenQuadShaderProgram = new ShaderProgram();
        screenQuadShaderProgram.createVertexShader(Utils.loadResource("shaders/screen_quad.vs"));
        screenQuadShaderProgram.createFragmentShader(Utils.loadResource("shaders/screen_quad.fs"));
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
