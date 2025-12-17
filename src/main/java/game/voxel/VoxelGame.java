package game.voxel;

import engine.IGameLogic;
import engine.graph.*;
import engine.io.Input;
import engine.io.MousePicker;
import engine.io.Window;
import engine.physics.AABB;
import engine.voxel.Block;
import engine.voxel.Chunk;
import engine.voxel.ChunkManager;
import engine.world.TimeSystem;
import game.voxel.entity.PlayerController;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.*;

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
    private static double fov = 90;
    private static final float NEAR_PLANE = 0.01f;
    private static final float FAR_PLANE = 1000.0f;

    private static final float PLANET_RADIUS = 6_371_000.0f;

    private boolean mouseLocked = false;

    private PlayerController player;

    public VoxelGame() {
        renderer = new Renderer();
        transformation = new Transformation();
    }

    @Override
    public void init(Window window) throws Exception {

        renderer.init(window);

        player = new PlayerController(new Camera());
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
        hud.setPlayerHealth(player.getHealth());
        hud.setPlayerHunger(100f); // start full hunger
        hud.setPlayerWater(100f); // start full hydration
        hud.setPlayerYaw(player.getCamera().getRotation().y);
        hud.setSelectedSlot(0); // highlight first hotbar slot

        // --- Inventory + mouse picker ---
        inventory = new Inventory();
        mousePicker = new MousePicker(window);

        // Player spawn and view
        player.getCamera().setPosition(0, 80, 0);
        player.getCamera().setRotation(15, 0, 0);

        initSkyTextures();
    }

    // Block interaction / breaking
    private Vector3f selectedBlock = null;
    private float breakProgress = 0.0f;
    private float placeTimer = 0.0f;

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

        // Camera look only
        if (input.isKeyPressed(GLFW_KEY_LEFT)) {
            int current = player.getCameraMode().ordinal();
            int next = (current - 1 + PlayerController.CameraMode.values().length)
                    % PlayerController.CameraMode.values().length;
            player.setCameraMode(PlayerController.CameraMode.values()[next]);
        } else if (input.isKeyPressed(GLFW_KEY_RIGHT)) {
            int current = player.getCameraMode().ordinal();
            int next = (current + 1) % PlayerController.CameraMode.values().length;
            player.setCameraMode(PlayerController.CameraMode.values()[next]);
        }

        if (mouseLocked) {
            float dx = (float) input.getMouseDX();
            float dy = (float) input.getMouseDY();
            player.rotateView(-dy * MOUSE_SENSITIVITY, dx * MOUSE_SENSITIVITY, 0);
        }

        // Inventory selection
        if (input.isKeyPressed(GLFW_KEY_1))
            inventory.selectSlot(0);
        if (input.isKeyPressed(GLFW_KEY_2))
            inventory.selectSlot(1);
        if (input.isKeyPressed(GLFW_KEY_3))
            inventory.selectSlot(2);
        if (input.isKeyPressed(GLFW_KEY_4))
            inventory.selectSlot(3);
        if (input.isKeyPressed(GLFW_KEY_5))
            inventory.selectSlot(4);
        if (input.isKeyPressed(GLFW_KEY_6))
            inventory.selectSlot(5);
        if (input.isKeyPressed(GLFW_KEY_7))
            inventory.selectSlot(6);
        if (input.isKeyPressed(GLFW_KEY_8))
            inventory.selectSlot(7);
        if (input.isKeyPressed(GLFW_KEY_9))
            inventory.selectSlot(8);

        // Mouse picker updates
        mousePicker.update(player.getCamera(), transformation, (float) fov, NEAR_PLANE, FAR_PLANE);

        if (mouseLocked) {
            Vector3f targetedBlock = mousePicker.raycastBlock(player.getCamera(), chunkManager, 5.0f);

            // Handle selection change
            if (targetedBlock == null || (selectedBlock != null && !targetedBlock.equals(selectedBlock))) {
                breakProgress = 0.0f;
            }
            selectedBlock = targetedBlock;

            // --- Block Breaking ---
            if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_1) && selectedBlock != null) {
                Block b = getBlockAt((int) selectedBlock.x, (int) selectedBlock.y, (int) selectedBlock.z);
                if (b != Block.AIR && b.getHardness() >= 0) {
                    float speed = 1.0f;
                    float step = 0.02f * speed;
                    breakProgress += step;

                    if (breakProgress >= b.getHardness()) {
                        setBlock((int) selectedBlock.x, (int) selectedBlock.y, (int) selectedBlock.z, Block.AIR);
                        breakProgress = 0.0f;
                        selectedBlock = null;
                    }
                } else {
                    breakProgress = 0.0f;
                }
            } else {
                breakProgress = 0.0f;
            }

            // --- Block Placing ---
            if (placeTimer <= 0 && input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_2)) {
                Vector3f placePos = mousePicker.raycastPlace(player.getCamera(), chunkManager, 5.0f);
                if (placePos != null) {
                    Block selected = inventory.getSelectedBlock();
                    if (selected != Block.AIR) {
                        // Prevent placing inside the player
                        AABB blockAABB = new AABB(placePos.x, placePos.y, placePos.z, 1, 1, 1);
                        if (!player.getBoundingBox().intersects(blockAABB)) {
                            setBlock((int) placePos.x, (int) placePos.y, (int) placePos.z, selected);
                            placeTimer = 0.25f; // 250ms cooldown
                        }
                    }
                }
            }
        }
    }

    private Block getBlockAt(int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        String key = ChunkManager.getChunkKey(cx, cz);
        Chunk c = chunkManager.getChunks().get(key);
        if (c != null) {
            return c.getBlock(x & 15, y, z & 15);
        }
        return Block.AIR;
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
        int fps = (int) (1.0f / interval);
        hud.setFPS(fps);

        // World stats
        hud.setStats(chunkManager.getChunks().size(), chunkManager.getTotalVertices());

        // Player state
        hud.setPlayerHealth(player.getHealth()); // hearts
        hud.setPlayerHunger(player.getHunger()); // drumsticks
        hud.setPlayerWater(player.getHydration()); // droplets
        hud.setPlayerYaw(player.getCamera().getRotation().y);

        // Hotbar selection (from inventory)
        hud.setSelectedSlot(inventory.getSelectedSlot());
        // Movement inputs (consumed by playerController physics)
        boolean jump = input.isKeyPressed(GLFW_KEY_SPACE);
        boolean moveDown = input.isKeyPressed(GLFW_KEY_Q);
        boolean moveUp = input.isKeyPressed(GLFW_KEY_E);
        boolean fwd = input.isKeyPressed(GLFW_KEY_W);
        boolean back = input.isKeyPressed(GLFW_KEY_S);
        boolean left = input.isKeyPressed(GLFW_KEY_A);
        boolean right = input.isKeyPressed(GLFW_KEY_D);
        fov += (input.isKeyPressed(GLFW_KEY_KP_ADD) ? 1 : 0) - (input.isKeyPressed(GLFW_KEY_KP_SUBTRACT) ? 1 : 0);
        fov = Math.max(30, Math.min(fov, 200));

        if (placeTimer > 0)
            placeTimer -= interval;

        player.update(interval, chunkManager, jump, fwd, back, left, right, moveUp, moveDown);

        // Update HUD
        hud.setPlayerYaw(player.getViewRotation().y);

        // Infinite world loading based on player position
        int px = (int) player.getPosition().x / 16;
        int pz = (int) player.getPosition().z / 16;
        chunkManager.loadChunksAround(px, pz, 6);

        timeSystem.update(interval);
    }

    @Override
    public void render(Window window) {
        renderer.clear();

        Matrix4f projection = transformation.getProjectionMatrix(
                (float) Math.toRadians(fov), window.getWidth(), window.getHeight(), NEAR_PLANE, FAR_PLANE);
        transformation.getViewMatrix(player.getCamera());

        // Rotation-only view (ignore translation)
        Camera cam = player.getCamera();
        Matrix4f rotOnlyView = new Matrix4f()
                .rotateX((float) Math.toRadians(cam.getRotation().x))
                .rotateY((float) Math.toRadians(cam.getRotation().y));

        // MVP = projection * rotation-only view
        Matrix4f mvp = new Matrix4f(projection).mul(rotOnlyView);

        // --- Pass 1: Sky ---
        skyShader.bind();
        skyShader.setUniform("uMVP", mvp);

        skyShader.setUniform("uViewportSize", new Vector2f(window.getWidth(), window.getHeight()));

        skyShader.setUniform("uCameraPos", player.getCamera().getPosition());
        skyShader.setUniform("uCameraHeight", player.getCamera().getPosition().y);
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
        glDisable(GL_CULL_FACE); // sky from inside — avoid culling
        glDepthMask(false); // don’t write depth
        renderer.renderSkyDome();
        glDepthMask(true);
        glEnable(GL_CULL_FACE);

        skyShader.unbind();

        // --- Pass 2: Terrain ---
        renderer.renderChunks(window, player.getCamera(), chunkManager.getChunks().values(), timeSystem);

        // --- Pass 3: player ---
        renderer.renderGameItems(player.getAllParts(), player.getCamera(), transformation);

        // --- Pass 4: Selection / Breaking ---
        if (selectedBlock != null) {
            float hardness = getBlockAt((int) selectedBlock.x, (int) selectedBlock.y, (int) selectedBlock.z)
                    .getHardness();
            renderer.renderSelection(window, player.getCamera(), selectedBlock, breakProgress, hardness);
        }

        // --- Pass 5: HUD ---
        hud.render(window);
    }

    @Override
    public void cleanup() {
        renderer.cleanup();
        if (renderer != null && renderer.getSelectionMesh() != null) {
            renderer.getSelectionMesh().cleanup();
        }
        if (skyShader != null)
            skyShader.cleanup();
        if (player != null) {
            for (GameItem part : player.getAllParts()) {
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
            buffer.put((byte) (r * 255));
            buffer.put((byte) (g * 255));
            buffer.put((byte) (b * 255));
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
        skyViewTextureId = createDummyTexture(64, 64, 0.5f, 0.7f, 1.0f); // bluish sky
        transmittanceTextureId = createDummyTexture(64, 64, 1.0f, 1.0f, 1.0f); // white (no attenuation)
    }

}
