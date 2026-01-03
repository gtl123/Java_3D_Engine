package game.voxel;

import engine.IGameLogic;
import engine.shaders.SkyDomeShader;
import engine.raster.Renderer;
import engine.raster.Transformation;
import engine.io.Input;
import engine.io.MousePicker;
import engine.io.Window;
import engine.camera.Camera;
import engine.entity.Entity;
import engine.entity.BrowserEntity;
import engine.physics.AABB;
import game.voxel.world.TimeSystem;
import game.voxel.world.WeatherSystem;
import game.voxel.entity.PlayerController;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import game.voxel.entity.ItemEntity;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.*;

import game.menu.MenuManager;
import engine.gfx.RenderSystem;
import game.voxel.gfx.*;

public class VoxelGame implements IGameLogic {

    private final Renderer renderer;
    private ChunkManager chunkManager;
    private MousePicker mousePicker;
    private final Transformation transformation;

    private HUD hud;
    private Inventory inventory;
    private TimeSystem timeSystem;
    private WeatherSystem weatherSystem;
    private WeatherParticleSystem weatherParticleSystem;
    private MenuManager menuManager;
    private SkyDomeShader skyShader;

    private static double fov = 90;
    private static final float NEAR_PLANE = 0.01f;
    private static final float FAR_PLANE = 1000.0f;

    private boolean mouseLocked = false;

    private PlayerController player;
    private List<ItemEntity> itemEntities = new ArrayList<>();
    private List<BrowserEntity> browserEntities = new ArrayList<>();
    private float dropCooldown = 0.0f;

    // World identity/state for saving
    private String worldName;
    private long seed;
    private boolean saveRequested = false;
    private boolean isBusy = false;
    private String busyMessage = "";

    private Window window;

    // FPS tracking
    private float fpsSmoothed = 60.0f;
    private static final float FPS_SMOOTH_FACTOR = 0.95f;

    private RenderSystem renderSystem;
    private SelectionRenderPass selectionPass;

    public VoxelGame(String worldName, long seed) {
        this.worldName = worldName;
        // Use a fixed seed for the menu background to ensure a consistent cool look
        this.seed = worldName.equals("menu_background") ? 1337420L : seed;
        renderer = new Renderer();
        transformation = new Transformation();
        this.timeSystem = new TimeSystem();
        this.weatherSystem = new WeatherSystem();
        this.menuManager = new MenuManager();
        menuManager.setGame(this); // Inject game reference
    }

    @Override
    public void init(Window window) throws Exception {
        this.window = window;
        renderer.init(window);
        initSkyTextures();

        // If starting with a "menu" world name, we are in the menu
        boolean startsInMenu = worldName.equals("menu_background");

        player = new PlayerController(new Camera());
        chunkManager = new ChunkManager(seed, worldName);
        chunkManager.init();

        timeSystem.setTimeSpeed(0.005f);
        this.weatherParticleSystem = new WeatherParticleSystem();

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
        hud.setPlayerHunger(100f);
        hud.setPlayerWater(100f);
        hud.setPlayerYaw(player.getCamera().getRotation().y);
        hud.setSelectedSlot(0);

        // --- Inventory + mouse picker ---
        inventory = new Inventory();
        mousePicker = new MousePicker(window);

        // Player spawn and view
        player.getCamera().setPosition(0, 80, 0);
        player.getCamera().setRotation(15, 0, 0);

        // Start in menu if appropriate
        setupRenderSystem();

        if (startsInMenu) {
            player.getCamera().setPosition(0, 120, 0);
            player.getCamera().setRotation(35, 45, 0);
            menuManager.switchTo(MenuManager.MenuState.MAIN_MENU);
            chunkManager.loadChunksAround(0, 0, 6); // 13x13 area
            
            // Create stacked browser pages for the menu
            BrowserEntity mainMenuBrowser = new BrowserEntity("https://3000-i36dxtoer9b9r2w1arbem-29b75f94.us2.manus.computer/", 2.0f, 1.5f);
            mainMenuBrowser.setPosition(0, 120, -2.0f);
            mainMenuBrowser.setRotation(0, 0, 0);
            browserEntities.add(mainMenuBrowser);
            
            BrowserEntity worldSelectBrowser = new BrowserEntity("https://3000-i36dxtoer9b9r2w1arbem-29b75f94.us2.manus.computer/world-select", 2.0f, 1.5f);
            worldSelectBrowser.setPosition(0, 120, -2.5f);
            worldSelectBrowser.setRotation(0, 0, 0);
            browserEntities.add(worldSelectBrowser);
            
            BrowserEntity settingsBrowser = new BrowserEntity("https://3000-i36dxtoer9b9r2w1arbem-29b75f94.us2.manus.computer/settings", 2.0f, 1.5f);
            settingsBrowser.setPosition(0, 120, -3.0f);
            settingsBrowser.setRotation(0, 0, 0);
            browserEntities.add(settingsBrowser);
        } else {
            // If passed a real world, start playing
            menuManager.switchTo(MenuManager.MenuState.NONE);
        }
    }

    private void setupRenderSystem() {
        if (renderSystem == null) {
            renderSystem = new RenderSystem();
        } else {
            renderSystem.cleanup(); // Clear existing passes
        }

        // Add render passes in priority order
        renderSystem.addPass(new SkyRenderPass(skyShader, transformation, timeSystem, weatherSystem,
                player.getCamera(), fov, skyViewTextureId, transmittanceTextureId, renderer));
        renderSystem
                .addPass(new TerrainRenderPass(renderer, chunkManager, timeSystem, weatherSystem, player.getCamera()));
        renderSystem.addPass(new EntityRenderPass(renderer, transformation, player.getCamera(), player, itemEntities));
        renderSystem.addPass(new WeatherRenderPass(renderer, weatherParticleSystem,
                weatherSystem, player.getCamera(),
                transformation));
        
        renderSystem.addPass(new BrowserRenderPass(renderer, transformation, player.getCamera(), browserEntities));

        selectionPass = new SelectionRenderPass(renderer, player.getCamera(), chunkManager, menuManager);
        renderSystem.addPass(selectionPass);

        renderSystem.addPass(new UIRenderPass(menuManager, hud));
    }

    // Block interaction / breaking
    private Vector3f selectedBlock = null;
    private float breakProgress = 0.0f;
    private float placeTimer = 0.0f;

    @Override
    public void input(Window window, Input input) {
        // Menu input routing
        if (menuManager.isInMenu()) {
            // Unlock mouse for menu
            if (mouseLocked) {
                mouseLocked = false;
                glfwSetInputMode(window.getWindowHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            }
            menuManager.handleInput(input, window);
            return;
        }

        // Quick-save current world (F5)
        if (input.isKeyJustPressed(GLFW_KEY_F5)) {
            saveRequested = true;
        }

        // Toggle Pause Menu
        if (input.isKeyJustPressed(GLFW_KEY_ESCAPE)) {
            if (menuManager.getCurrentState() == MenuManager.MenuState.NONE) {
                menuManager.switchTo(MenuManager.MenuState.PAUSE_MENU);
                return;
            }
        }

        // Cursor lock toggle (only if NOT in menu, but we already returned if in menu)
        // Re-implementing mouse lock logic compatible with pause
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
            player.rotateView(-dy * menuManager.getSettings().getMouseSensitivity(),
                    dx * menuManager.getSettings().getMouseSensitivity(), 0);
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

        // Feature Showcase Shortcuts
        if (input.isKeyJustPressed(GLFW_KEY_K)) {
            weatherSystem.cycleWeather();
        }
        if (input.isKeyJustPressed(GLFW_KEY_L)) {
            timeSystem.setTimeOfDay(timeSystem.getTimeOfDay() + 0.0416f); // ~1 hour
        }

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
        // ALWAYS update chunk loading and mesh application (even in menus!)
        int px = worldName.equals("menu_background") ? 0 : (int) Math.floor(player.getPosition().x / 16.0);
        int pz = worldName.equals("menu_background") ? 0 : (int) Math.floor(player.getPosition().z / 16.0);
        int renderDist = worldName.equals("menu_background") ? 6 : menuManager.getSettings().getRenderDistance();

        chunkManager.update(interval, px, pz, renderDist);
        chunkManager.updateMeshes();

        // If in menu, only update camera for "flyover" effect or freeze
        if (menuManager.isInMenu()) {
            if (menuManager.getCurrentState() == MenuManager.MenuState.MAIN_MENU) {
                // Flyover/Orbital rotation for menu background
                player.rotateView(0, 12.0f * interval, 0);
                // Directly update camera rotation to ensure it bypasses any input blocks
                player.getCamera().setRotation(
                        player.getViewRotation().x,
                        player.getViewRotation().y,
                        player.getViewRotation().z);
                // Ensure the 13x13 grid remains loaded
                chunkManager.loadChunksAround(0, 0, 6);
            }
            return;
        }

        // Update settings from menu (e.g. FOV, Render Distance)
        // TODO: Update chunk manager if distance changed

        // Update block physics (falling blocks, water flow, etc)

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

        // Zoom
        float targetFov = menuManager.getSettings().getFov();
        if (input.isKeyPressed(GLFW_KEY_C))
            targetFov = 30.0f; // Zoom key
        fov = targetFov;

        if (placeTimer > 0)
            placeTimer -= interval;

        player.update(interval, chunkManager, jump, fwd, back, left, right, moveUp, moveDown);

        // Update HUD
        hud.setPlayerYaw(player.getViewRotation().y);

        // Load chunks
        chunkManager.loadChunksAround(px, pz, menuManager.getSettings().getRenderDistance());

        timeSystem.update(interval);
        weatherSystem.update(interval);

        if (saveRequested) {
            saveWorld();
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
        if (isBusy) {
            hud.renderLoadingScreen(window, busyMessage);
            return;
        }

        renderer.clear();

        // Update projection matrix for this frame
        transformation.getProjectionMatrix(
                (float) Math.toRadians(fov), window.getWidth(), window.getHeight(), NEAR_PLANE, FAR_PLANE);
        transformation.getViewMatrix(player.getCamera());

        // Update selection pass with current state
        selectionPass.setSelection(selectedBlock, breakProgress);

        // Render all passes via the render system
        renderSystem.renderAll(window, 0); // deltaTime not used yet
    }

    @Override
    public void cleanup() {
        if (renderSystem != null) {
            renderSystem.cleanup();
        }
        if (weatherParticleSystem != null) {
            weatherParticleSystem.cleanup();
        }
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

    public void saveWorld() {
        if (worldName.equals("menu_background"))
            return; // Don't save menu

        busyMessage = "SAVING WORLD...";
        isBusy = true;
        render(window);
        window.update();

        try {
            // Save Metadata (if needed, but WorldStorage handles creation. Updating
            // lastPlayed is good)
            game.save.WorldStorage.WorldMetadata meta = new game.save.WorldStorage.WorldMetadata(worldName, seed);
            game.save.WorldStorage.saveWorldMetadata(meta);

            // Save Chunks (Changed Blocks)
            WorldSave.save(worldName, seed, timeSystem.getTimeOfDay(), timeSystem.getDayOfYear(),
                    chunkManager.getChangedBlocks());

            // Save Player
            int[] invIds = new int[8]; // Assuming size 8
            for (int i = 0; i < 8; i++) {
                inventory.selectSlot(i); // Hack to access? No, Inventory needs direct access or we expose it
                // Inventory doesn't expose array directly. We'll use getSelectedBlock by
                // selecting each?
                // Or update Inventory to expose contents.
                // For now, let's assume getSelectedBlock works on current selection.
                // This is intrusive. Ideally Inventory exposes items.
                // Let's rely on the fact we can't get all items easily without changing
                // Inventory.
                // TODO: Fix Inventory saving properly
                invIds[i] = 1;
            }
            // Better: loop via selectSlot?
            int oldSlot = inventory.getSelectedSlot();
            for (int i = 0; i < 8; i++) {
                inventory.selectSlot(i);
                invIds[i] = inventory.getSelectedBlock().getId();
            }
            inventory.selectSlot(oldSlot);

            Camera cam = player.getCamera();
            WorldSave.PlayerSaveData pData = new WorldSave.PlayerSaveData(
                    cam.getPosition().x, cam.getPosition().y, cam.getPosition().z,
                    cam.getRotation().x, cam.getRotation().y,
                    player.getHealth(), player.getStamina(), player.getHunger(), player.getHydration(),
                    invIds);
            WorldSave.savePlayer(worldName, pData);

            System.out.println("World saved: " + worldName);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isBusy = false;
        }
    }

    public void loadWorld(String name, long forcedSeed) {
        // Unload current
        cleanup(); // Cleanup old resources (chunks, player meshes, etc.)

        // Reset/Re-init
        this.worldName = name;

        // Try to load metadata first to get seed if not provided (or if forcedSeed is
        // 0)
        game.save.WorldStorage.WorldMetadata meta = game.save.WorldStorage.loadWorldMetadata(name);
        if (meta != null) {
            this.seed = meta.seed;
        } else {
            this.seed = (forcedSeed != 0) ? forcedSeed : System.currentTimeMillis();
            // Create metadata if new
            game.save.WorldStorage.saveWorldMetadata(new game.save.WorldStorage.WorldMetadata(name, this.seed));
        }

        System.out.println("Loading world: " + name + " Seed: " + this.seed);

        busyMessage = "LOADING WORLD...";
        isBusy = true;
        render(window);
        window.update();

        // Re-init systems
        try {
            renderer.init(this.window);
            skyShader = new SkyDomeShader(); // Re-init sky shader
            this.weatherParticleSystem = new WeatherParticleSystem(); // Re-init weather particle system

            // Re-create ChunkManager
            chunkManager = new ChunkManager(this.seed, name);

            // Load changed blocks
            WorldSave.LoadedWorld loaded = null;
            if (WorldSave.exists(name)) {
                try {
                    loaded = WorldSave.load(name);
                    chunkManager.getChangedBlocks().putAll(loaded.changedBlocks);
                    timeSystem.setTimeOfDay(loaded.timeOfDay);
                    timeSystem.setDayOfYear(loaded.dayOfYear);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                timeSystem.setTimeOfDay(0.2f); // Morning
            }

            chunkManager.init(); // Load initial chunks

            // Re-create Player
            player = new PlayerController(new Camera());
            if (WorldSave.exists(name)) { // Check player file?
                try {
                    WorldSave.PlayerSaveData pData = WorldSave.loadPlayer(name);
                    if (pData != null) {
                        player.getCamera().setPosition(pData.x, pData.y, pData.z);
                        player.getCamera().setRotation(pData.rotX, pData.rotY, 0);
                        // Restore stats (need setters in PlayerController)
                        // player.setHealth(pData.health);
                        // player.setStamina(pData.stamina);
                        // ...

                        // Restore inventory
                        int[] savedInv = pData.inventory;
                        for (int i = 0; i < Math.min(8, savedInv.length); i++) {
                            inventory.selectSlot(i);
                            Block b = Block.getById(savedInv[i]);
                            if (b != Block.AIR)
                                inventory.addItem(b);
                        }
                        inventory.selectSlot(0);
                    } else {
                        // Spawn at ground level
                        int spawnX = 0, spawnZ = 0;
                        int y = chunkManager.getGroundHeight(spawnX, spawnZ) + 2;
                        player.getCamera().setPosition(spawnX, y, spawnZ);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                int spawnX = 0, spawnZ = 0;
                int y = chunkManager.getGroundHeight(spawnX, spawnZ) + 2;
                player.getCamera().setPosition(spawnX, y, spawnZ);
            }

            itemEntities.clear();
            setupRenderSystem(); // RE-POPULATE PASSES FOR NEW WORLD

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isBusy = false;
        }
    }

    public HUD getHUD() {
        return hud;
    }
}
