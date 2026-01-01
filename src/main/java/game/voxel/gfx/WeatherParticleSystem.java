package game.voxel.gfx;

import engine.camera.Camera;
import engine.raster.Mesh;
import engine.raster.MeshData;
import game.voxel.world.WeatherSystem;
import org.joml.Vector3f;

import java.util.Random;

import static org.lwjgl.opengl.GL11C.*;
import engine.raster.Transformation;
import org.joml.Matrix4f;

public class WeatherParticleSystem {
    private static final int MAX_PARTICLES = 10000;
    private final Particle[] particles = new Particle[MAX_PARTICLES];
    private final Random random = new Random();

    private Mesh rainMesh;
    private Mesh snowMesh;

    private static class Particle {
        Vector3f position = new Vector3f();
        Vector3f velocity = new Vector3f();
        boolean active = false;
    }

    public WeatherParticleSystem() {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            particles[i] = new Particle();
        }
        initMeshes();
    }

    private void initMeshes() {
        // Simple vertical quads for rain
        float[] rainPos = { -0.02f, 0.5f, 0, 0.02f, 0.5f, 0, 0.02f, -0.5f, 0, -0.02f, -0.5f, 0 };
        float[] rainTex = { 0, 0, 1, 0, 1, 1, 0, 1 };
        float[] rainNorm = { 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1 };
        int[] rainInd = { 0, 1, 2, 2, 3, 0 };
        rainMesh = new Mesh(new MeshData(rainPos, rainTex, rainNorm, rainInd));

        // Hexagonal/Square sprite for snow
        float[] snowPos = { -0.1f, 0.1f, 0, 0.1f, 0.1f, 0, 0.1f, -0.1f, 0, -0.1f, -0.1f, 0 };
        snowMesh = new Mesh(new MeshData(snowPos, rainTex, rainNorm, rainInd));
    }

    public void update(float deltaTime, Camera camera, WeatherSystem weather) {
        WeatherSystem.WeatherType type = weather.getCurrentWeather();
        if (type == WeatherSystem.WeatherType.CLEAR)
            return;

        Vector3f playerPos = camera.getPosition();
        float spawnRate = (type == WeatherSystem.WeatherType.STORM) ? 500 : 200;
        int toSpawn = (int) (spawnRate * deltaTime);

        for (int i = 0; i < MAX_PARTICLES && toSpawn > 0; i++) {
            if (!particles[i].active) {
                particles[i].active = true;
                particles[i].position.set(
                        playerPos.x + (random.nextFloat() - 0.5f) * 40,
                        playerPos.y + 20 + random.nextFloat() * 10,
                        playerPos.z + (random.nextFloat() - 0.5f) * 40);
                float speed = (type == WeatherSystem.WeatherType.SNOW) ? 5f : 20f;
                particles[i].velocity.set(0, -speed, 0);
                toSpawn--;
            }
        }

        for (Particle p : particles) {
            if (p.active) {
                p.position.add(p.velocity.x * deltaTime, p.velocity.y * deltaTime, p.velocity.z * deltaTime);
                if (p.position.y < playerPos.y - 10) {
                    p.active = false;
                }
            }
        }
    }

    public void render(Camera camera, WeatherSystem weather, engine.raster.Renderer renderer,
            Transformation transformation) {
        WeatherSystem.WeatherType type = weather.getCurrentWeather();
        if (type == WeatherSystem.WeatherType.CLEAR)
            return;

        Mesh mesh = (type == WeatherSystem.WeatherType.SNOW) ? snowMesh : rainMesh;

        renderer.bindShader();
        renderer.setupSceneUniforms(camera, transformation);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);

        Matrix4f viewMatrix = transformation.getViewMatrix(camera);

        for (Particle p : particles) {
            if (p.active) {
                Matrix4f modelMatrix = new Matrix4f().translate(p.position);
                // Make particle always face camera (billboarding)
                modelMatrix.m00(viewMatrix.m00());
                modelMatrix.m01(viewMatrix.m10());
                modelMatrix.m02(viewMatrix.m20());
                modelMatrix.m10(viewMatrix.m01());
                modelMatrix.m11(viewMatrix.m11());
                modelMatrix.m12(viewMatrix.m21());
                modelMatrix.m20(viewMatrix.m02());
                modelMatrix.m21(viewMatrix.m12());
                modelMatrix.m22(viewMatrix.m22());

                Matrix4f modelViewMatrix = new Matrix4f(viewMatrix).mul(modelMatrix);
                renderer.renderGameItemQuick(mesh, modelViewMatrix);
            }
        }

        glDepthMask(true);
        glDisable(GL_BLEND);
        renderer.unbindShader();
    }

    public void cleanup() {
        rainMesh.cleanup();
        snowMesh.cleanup();
    }
}
