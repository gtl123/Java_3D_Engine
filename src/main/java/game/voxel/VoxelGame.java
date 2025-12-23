package game.voxel;

import engine.IGameLogic;
import engine.entity.Entity;
import engine.camera.*;
import engine.shaders.SkyDomeShader;
import engine.raster.Renderer;
import engine.raster.Transformation;
import engine.io.Input;
import engine.io.MousePicker;
import engine.io.Window;
import engine.physics.AABB;
import game.voxel.world.TimeSystem;
import game.voxel.entity.PlayerController;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import game.voxel.entity.ItemEntity;

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

    private static final float PLANET_RADIUS = 600_371_000.0f;

    private boolean mouseLocked = false;

    private PlayerController player;
    private List<ItemEntity> itemEntities = new ArrayList<>();
    private float dropCooldown = 0.0f;

    // World identity/state for saving
    private final String worldName;
    private final long seed;
    private boolean saveRequested = false;

    // FPS tracking
    private float fpsSmoothed = 60.0f;
    private static final float FPS_SMOOTH_FACTOR = 0.95f;

    public VoxelGame(String worldName, long seed) {
        this.worldName = worldName;
        this.seed = seed;
        renderer = new Renderer();
        transformation = new Transformation();
    }

    @Override
    public void init(Window window) throws Exception {

        renderer.init(window);

        player = new PlayerController(new Camera());
        chunkManager = new ChunkManager(seed);
        chunkManager.init();

        timeSystem = new TimeSystem();
        timeSystem.setTimeSpeed(0.005f);

        skyShader = new SkyDomeShader();

        // --- HUD setup ---
        hud = new HUD();
        hud.init(window);

        // Initial HUD stats
        int initialRendered = (int) chunkManager.getChunks().values().stream()
                .filter(c -> c.getMesh() != null)
                .count();
        hud.setStats(initialRendered, chunkManager.getChunks().size(), chunkManager.getTotalVertices());

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
        // Quick-save current world (F5)
        if (input.isKeyPressed(GLFW_KEY_F5)) {
            saveRequested = true;
        }

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

        // Drop item
        if (dropCooldown <= 0 && input.isKeyPressed(GLFW_KEY_G)) {
            Block selected = inventory.getSelectedBlock();
            if (selected != Block.AIR) {
                Vector3f pos = player.getCamera().getEyePosition();
                float yaw = (float) Math.toRadians(player.getCamera().getRotation().y);
                float pitch = (float) Math.toRadians(player.getCamera().getRotation().x);
                Vector3f dir = new Vector3f(
                        (float) (Math.sin(yaw) * Math.cos(pitch)),
                        (float) (-Math.sin(pitch)),
                        (float) (-Math.cos(yaw) * Math.cos(pitch)));
                Vector3f vel = new Vector3f(dir).mul(5.0f).add(0, 2.0f, 0);

                ItemEntity dropped = new ItemEntity(selected, pos, vel);
                // Reuse the same terrain atlas as the world blocks for a consistent look
                if (dropped.getGameItem().getMesh() != null && chunkManager.getBlockTexture() != null) {
                    dropped.getGameItem().getMesh().setTexture(chunkManager.getBlockTexture());
                }
                itemEntities.add(dropped);
                inventory.removeItem(inventory.getSelectedSlot());
                dropCooldown = 0.3f;
            }
        }
    }

    private Block getBlockAt(int x, int y, int z) {
        return chunkManager.getBlockAt(x, y, z);
    }

    private void setBlock(int x, int y, int z, Block block) {
        chunkManager.setBlockAt(x, y, z, block);
    }

    @Override
    public void update(float interval, Input input) {

        int px = (int) Math.floor(player.getPosition().x / 16.0);
        int pz = (int) Math.floor(player.getPosition().z / 16.0);

        // Update block physics (falling blocks, water flow, etc)
        chunkManager.update(interval, px, pz);

        // Update chunk meshes
        chunkManager.updateMeshes();

        // FPS - smooth using exponential moving average
        float instantFps = 1.0f / interval;
        fpsSmoothed = fpsSmoothed * FPS_SMOOTH_FACTOR + instantFps * (1.0f - FPS_SMOOTH_FACTOR);
        hud.setFPS((int) fpsSmoothed);

        // World stats - count rendered chunks (those with meshes)
        int renderedChunks = (int) chunkManager.getChunks().values().stream()
                .filter(c -> c.getMesh() != null)
                .count();
        hud.setStats(renderedChunks, chunkManager.getChunks().size(), chunkManager.getTotalVertices());

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

        chunkManager.loadChunksAround(px, pz, 6);

        timeSystem.update(interval);

        // Persist if requested
        if (saveRequested) {
            try {
                game.voxel.WorldSave.save(
                        worldName,
                        seed,
                        timeSystem.getTimeOfDay(),
                        timeSystem.getDayOfYear(),
                        chunkManager.getChangedBlocks());
                System.out.println("World saved: " + worldName);
            } catch (Exception e) {
                e.printStackTrace();
            }
            saveRequested = false;
        }

        if (dropCooldown > 0)
            dropCooldown -= interval;

        // Update item entities and pickups
        Iterator<ItemEntity> it = itemEntities.iterator();
        while (it.hasNext()) {
            ItemEntity item = it.next();
            item.update(interval, chunkManager);

            // Pickup logic
            float dist = item.getPosition().distance(player.getCamera().getPosition());
            if (dist < 1.5f) {
                if (inventory.addItem(item.getBlock())) {
                    item.getGameItem().getMesh().cleanup();
                    it.remove();
                }
            }
        }
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
        skyShader.setUniform("uSunIntensity", 1.0f);
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

        // --- Pass 4.5: Items ---
        List<Entity> itemsToRender = new ArrayList<>();
        for (ItemEntity ie : itemEntities) {
            itemsToRender.add(ie.getGameItem());
        }
        renderer.renderGameItems(itemsToRender, player.getCamera(), transformation);

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
            for (Entity part : player.getAllParts()) {
                if (part != null && part.getMesh() != null) {
                    part.getMesh().cleanup();
                }
            }
        }
        if (chunkManager != null) {
            chunkManager.cleanup();
        }
        for (ItemEntity ie : itemEntities) {
            if (ie.getGameItem().getMesh() != null) {
                ie.getGameItem().getMesh().cleanup();
            }
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
