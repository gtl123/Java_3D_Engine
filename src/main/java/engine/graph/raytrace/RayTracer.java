package engine.graph.raytrace;

import engine.graph.Camera;
import engine.graph.ShaderProgram;
import engine.graph.Transformation;
import engine.io.Window;
import engine.utils.Utils;
import org.joml.Matrix4f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.GL_READ_WRITE;
import static org.lwjgl.opengl.GL30.GL_RGBA32F;
import static org.lwjgl.opengl.GL42.glBindImageTexture;
import static org.lwjgl.opengl.GL43.glDispatchCompute;
import static org.lwjgl.opengl.GL44.glMemoryBarrier;
import static org.lwjgl.opengl.GL44.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;

public class RayTracer {

    private final ShaderProgram computeShaderProgram;
    private final Transformation transformation;
    private int textureId;
    private int width, height;

    public RayTracer() throws Exception {
        transformation = new Transformation();
        computeShaderProgram = new ShaderProgram();
        computeShaderProgram.createComputeShader(Utils.loadResource("/shaders/raytracer.glsl"));
        computeShaderProgram.link();

        computeShaderProgram.createUniform("invViewMatrix");
        computeShaderProgram.createUniform("invProjectionMatrix");
    }

    public void init(Window window) {
        this.width = window.getWidth();
        this.height = window.getHeight();

        // Create texture for compute shader output
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, width, height, 0, GL_RGBA, GL_FLOAT, 0);
    }

    public void render(Window window, Camera camera) {
        computeShaderProgram.bind();

        // Bind texture to image unit 0
        glBindImageTexture(0, textureId, 0, false, 0, GL_READ_WRITE, GL_RGBA32F);

        Matrix4f projectionMatrix = transformation.getProjectionMatrix((float) Math.toRadians(60.0), width, height,
                0.01f, 100.0f);
        Matrix4f viewMatrix = transformation.getViewMatrix(camera);

        Matrix4f invProjectionMatrix = new Matrix4f(projectionMatrix).invert();
        Matrix4f invViewMatrix = new Matrix4f(viewMatrix).invert();

        computeShaderProgram.setUniform("invProjectionMatrix", invProjectionMatrix);
        computeShaderProgram.setUniform("invViewMatrix", invViewMatrix);

        glDispatchCompute((int) Math.ceil(width / 16.0), (int) Math.ceil(height / 16.0), 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

        computeShaderProgram.unbind();

        // In a real engine, we would render a fullscreen quad with this texture here.
        // For now, the texture is updated. Accessing it requires drawing it separately.
    }

    public int getOutputTextureId() {
        return textureId;
    }

    public void cleanup() {
        computeShaderProgram.cleanup();
        glDeleteTextures(textureId);
    }
}
