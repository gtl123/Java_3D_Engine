package game.voxel;

import engine.IGameLogic;
import engine.graph.*;
import engine.io.Input;
import engine.io.MousePicker;
import engine.io.Window;
import engine.voxel.Block;
import engine.voxel.Chunk;
import engine.voxel.ChunkManager;
import engine.world.TimeSystem;
import game.voxel.Player.PlayerController;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.*;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;

public class VoxelGame implements IGameLogic {

    private final Renderer renderer;
    private ChunkManager chunkManager;
    private MousePicker mousePicker;
    private final Transformation transformation;


    private HUD hud;
    private Inventory inventory;
    private TimeSystem timeSystem;

    private SkyDomeShader skyShader;


    private static final float MOUSE_SENSITIVITY = 0.1f;
    private static  double fov = 120;
    private static final float NEAR_PLANE = 0.01f;
    private static final float FAR_PLANE = 1000.0f;

    private static final float PLANET_RADIUS = 6_371_000.0f;
    private int frameCount = 0;

    private boolean mouseLocked = false;

    private PlayerController Player;

    public VoxelGame() {
        renderer = new Renderer();
        transformation = new Transformation();
    }

    @Override
    public void init(Window window) throws Exception {

        renderer.init(window);

        Player = new PlayerController(new Camera());
        chunkManager = new ChunkManager();
        chunkManager.init();

        timeSystem = new TimeSystem();
        timeSystem.setTimeSpeed(0.005f);

        skyShader = new SkyDomeShader();

        // --- HUD setup ---
        hud = new HUD();
        hud.init(window);

        // Initial HUD stats
        hud.setStats(chunkManager.getChunks().size(), chunkManager.getTotalVertices());

        // Initial HUD player state
        hud.setPlayerHealth(Player.getHealth());
        hud.setPlayerHunger(100f);   // start full hunger
        hud.setPlayerWater(100f);    // start full hydration
        hud.setPlayerYaw(Player.getCamera().getRotation().y);
        hud.setSelectedSlot(0);      // highlight first hotbar slot

        // --- Inventory + mouse picker ---
        inventory = new Inventory();
        mousePicker = new MousePicker(window);

        // Player spawn and view
        Player.getCamera().setPosition(0, 80, 0);
        Player.getCamera().setRotation(15, 0, 0);

        initSkyTextures();
    }



    @Override
    public void input(Window window, Input input) {
        // Cursor lock toggle
        if (input.isKeyPressed(GLFW_KEY_ESCAPE)) {
            mouseLocked = false;
            glfwSetInputMode(window.getWindowHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            input.resetMouse();
        }
        if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_1) && !mouseLocked) {
            mouseLocked = true;
            glfwSetInputMode(window.getWindowHandle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
            input.resetMouse();
        }

        // Camera look only (rotation); no direct position changes here.
        if (input.isKeyPressed(GLFW_KEY_LEFT)) {
            Player.getCamera().moveRotation(0, -1.5f, 0);
        } else if (input.isKeyPressed(GLFW_KEY_RIGHT)) {
            Player.getCamera().moveRotation(0, 1.5f, 0);
        }
        if (input.isKeyPressed(GLFW_KEY_UP)) {
            Player.getCamera().moveRotation(-1.5f, 0, 0);
        } else if (input.isKeyPressed(GLFW_KEY_DOWN)) {
            Player.getCamera().moveRotation(1.5f, 0, 0);
        }

        if (mouseLocked) {
            float dx = (float) input.getMouseDX();
            float dy = (float) input.getMouseDY();
            Player.getCamera().moveRotation(-dy * MOUSE_SENSITIVITY, dx * MOUSE_SENSITIVITY, 0);
        }

        // Inventory selection
        if (input.isKeyPressed(GLFW_KEY_1)) inventory.selectSlot(0);
        if (input.isKeyPressed(GLFW_KEY_2)) inventory.selectSlot(1);
        if (input.isKeyPressed(GLFW_KEY_3)) inventory.selectSlot(2);
        if (input.isKeyPressed(GLFW_KEY_4)) inventory.selectSlot(3);
        if (input.isKeyPressed(GLFW_KEY_5)) inventory.selectSlot(4);
        if (input.isKeyPressed(GLFW_KEY_6)) inventory.selectSlot(5);
        if (input.isKeyPressed(GLFW_KEY_7)) inventory.selectSlot(6);
        if (input.isKeyPressed(GLFW_KEY_8)) inventory.selectSlot(7);
        if (input.isKeyPressed(GLFW_KEY_9)) inventory.selectSlot(8);

        // Mouse picker uses camera for view rays
        mousePicker.update(
                Player.getCamera(),
                transformation,
                (float) fov,
                NEAR_PLANE,
                FAR_PLANE
        );

        // Block interaction
        if (mouseLocked) {
            if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_1)) {
                Vector3f target = mousePicker.raycastBlock(Player.getCamera(), chunkManager, 5.0f);
                if (target != null) setBlock((int) target.x, (int) target.y, (int) target.z, Block.AIR);
            }
            if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_2)) {
                Vector3f target = mousePicker.raycastPlace(Player.getCamera(), chunkManager, 5.0f);
                if (target != null) {
                    Block selected = inventory.getSelectedBlock();
                    setBlock((int) target.x, (int) target.y, (int) target.z, selected);
                }
            }
        }

        // Note: Removed direct camera.movePosition nudges.
        // Movement is handled in Player.update via physics.
    }

    private void setBlock(int x, int y, int z, Block block) {
        int cx = x >> 4;
        int cz = z >> 4;
        String key = ChunkManager.getChunkKey(cx, cz);
        Chunk c = chunkManager.getChunks().get(key);
        if (c != null) {
            int lx = x & 15;
            int lz = z & 15;
            c.setBlock(lx, y, lz, block);
        }
    }

    @Override
    public void update(float interval, Input input) {


        chunkManager.updateMeshes();

        // FPS
        int fps = (int)(1.0f / interval);
        hud.setFPS(fps);

        // World stats
        hud.setStats(chunkManager.getChunks().size(), chunkManager.getTotalVertices());

        // Player state
        hud.setPlayerHealth(Player.getHealth());          // hearts
        hud.setPlayerHunger(Player.getHunger());          // drumsticks
        hud.setPlayerWater(Player.getHydration());        // droplets
        hud.setPlayerYaw(Player.getCamera().getRotation().y);

        // Hotbar selection (from inventory)
        hud.setSelectedSlot(inventory.getSelectedSlot());
        // Movement inputs (consumed by PlayerController physics)
        boolean jump = input.isKeyPressed(GLFW_KEY_SPACE);
        boolean moveDown = input.isKeyPressed(GLFW_KEY_Q);
        boolean moveUp = input.isKeyPressed(GLFW_KEY_E);
        boolean fwd = input.isKeyPressed(GLFW_KEY_W);
        boolean back = input.isKeyPressed(GLFW_KEY_S);
        boolean left = input.isKeyPressed(GLFW_KEY_A);
        boolean right = input.isKeyPressed(GLFW_KEY_D);
        fov += (input.isKeyPressed(GLFW_KEY_KP_ADD) ? 1 : 0) - (input.isKeyPressed(GLFW_KEY_KP_SUBTRACT) ? 1 : 0);
        fov = Math.max(30, Math.min(fov, 200));


        Player.update(interval, chunkManager, jump, fwd, back, left, right, moveUp, moveDown);

        // Infinite world loading based on player position (camera follows the player)
        int px = (int) Player.getCamera().getPosition().x / 16;
        int pz = (int) Player.getCamera().getPosition().z / 16;
        chunkManager.loadChunksAround(px, pz, 6);

        timeSystem.update(interval);
    }

    @Override
    public void render(Window window) {
        renderer.clear();

        // Build projection + view matrices
        Matrix4f projection = transformation.getProjectionMatrix(
                (float) Math.toRadians(fov), window.getWidth(), window.getHeight(), NEAR_PLANE, FAR_PLANE);
        Matrix4f view = transformation.getViewMatrix(Player.getCamera());

        // Rotation-only view (ignore translation)
        Camera cam = Player.getCamera();
        Matrix4f rotOnlyView = new Matrix4f()
                .rotateX((float)Math.toRadians(cam.getRotation().x))
                .rotateY((float)Math.toRadians(cam.getRotation().y));

        // MVP = projection * rotation-only view
        Matrix4f mvp = new Matrix4f(projection).mul(rotOnlyView);

        // --- Pass 1: Sky ---
        skyShader.bind();
        skyShader.setUniform("uMVP", mvp);

        skyShader.setUniform("uViewportSize", new Vector2f(window.getWidth(), window.getHeight()));

        skyShader.setUniform("uCameraPos", Player.getCamera().getPosition());
        skyShader.setUniform("uCameraHeight", Player.getCamera().getPosition().y);
        skyShader.setUniform("uSunDir", timeSystem.getSunDirection());
        skyShader.setUniform("uSunIntensity", 30.0f);
        skyShader.setUniform("uMieG", 0.78f);
        skyShader.setUniform("uTurbidity", 3.0f);
        skyShader.setUniform("uPlanetRadius", PLANET_RADIUS);
        skyShader.setUniform("uAtmosphereTop", 80_000.0f);
        skyShader.setUniform("uUp", new Vector3f(0, 1, 0));

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, skyViewTextureId);
        skyShader.setUniform("uSkyViewLUT", 0);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, transmittanceTextureId);
        skyShader.setUniform("uTransmittanceLUT", 1);

        // Actually draw sky geometry (fullscreen quad or dome)
        glDisable(GL_CULL_FACE);     // sky from inside — avoid culling
        glDepthMask(false);          // don’t write depth
        renderer.renderSkyDome();
        glDepthMask(true);
        glEnable(GL_CULL_FACE);

        skyShader.unbind();

        // --- Pass 2: Terrain ---
        renderer.renderChunks(window, Player.getCamera(), chunkManager.getChunks().values(), timeSystem);

        // --- Pass 3: Player ---
        renderer.renderGameItems(Player.getAllParts(), Player.getCamera(), transformation);

        // --- Pass 4: HUD ---
        hud.render(window);

        frameCount++;
    }


    @Override
    public void cleanup() {
        renderer.cleanup();
        if (skyShader != null) skyShader.cleanup();
        if (Player != null) {
            for (GameItem part : Player.getAllParts()) {
                part.getMesh().cleanup();
            }
        }
        if (chunkManager != null) {
            chunkManager.cleanup();
        }
    }

    private int skyViewTextureId;
    private int transmittanceTextureId;

    private int createDummyTexture(int width, int height, float r, float g, float b) {
        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);

        // Fill with solid color
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 3);
        for (int i = 0; i < width * height; i++) {
            buffer.put((byte)(r * 255));
            buffer.put((byte)(g * 255));
            buffer.put((byte)(b * 255));
        }
        buffer.flip();

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB8, width, height, 0,
                GL_RGB, GL_UNSIGNED_BYTE, buffer);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        return texId;
    }

    public void initSkyTextures() {
        // Simple gradients or solid colors for testing
        skyViewTextureId = createDummyTexture(64, 64, 0.5f, 0.7f, 1.0f);       // bluish sky
        transmittanceTextureId = createDummyTexture(64, 64, 1.0f, 1.0f, 1.0f); // white (no attenuation)
    }

}
