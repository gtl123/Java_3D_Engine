package game.voxel;

import engine.IGameLogic;
import engine.graph.Camera;
import engine.graph.Renderer;
import engine.graph.Transformation;
import engine.io.Input;
import engine.io.MousePicker;
import engine.io.Window;
import engine.voxel.Block;
import engine.voxel.Chunk;
import engine.voxel.ChunkManager;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

import engine.graph.HUD;
import engine.graph.GameItem;
import engine.graph.SkyRenderer;
import engine.world.TimeSystem;

public class VoxelGame implements IGameLogic {

    private final Renderer renderer;
    private final Camera camera;
    private ChunkManager chunkManager;
    private MousePicker mousePicker;
    private final Transformation transformation;

    private HUD hud;
    private Inventory inventory;
    private TimeSystem timeSystem;
    private SkyRenderer skyRenderer;

    private static final float MOUSE_SENSITIVITY = 0.2f;

    public VoxelGame() {
        renderer = new Renderer();
        camera = new Camera();
        transformation = new Transformation();
    }

    @Override
    public void init(Window window) throws Exception {
        renderer.init(window);
        chunkManager = new ChunkManager();
        chunkManager.init();

        timeSystem = new TimeSystem();
        timeSystem.setTimeSpeed(0.005f); // Adjust for desired day/night cycle speed

        skyRenderer = new SkyRenderer();
        skyRenderer.init();

        hud = new HUD();
        hud.init(window);
        GameItem crosshair = new GameItem(hud.getMesh());
        crosshair.setScale(0.04f);
        crosshair.setPosition(0, 0, 0);
        hud.addItem(crosshair);

        inventory = new Inventory();

        mousePicker = new MousePicker(window);

        // Start high up
        camera.setPosition(0, 80, 0);
        camera.setRotation(15, 0, 0);

        // glfwSetInputMode(window.getWindowHandle(), GLFW_CURSOR,
        // GLFW_CURSOR_DISABLED);
    }

    private boolean mouseLocked = false;

    @Override
    public void input(Window window, Input input) {
        // Toggle cursor
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

        // Camera rotation
        if (input.isKeyPressed(GLFW_KEY_LEFT)) {
            camera.moveRotation(0, -1.5f, 0);
        } else if (input.isKeyPressed(GLFW_KEY_RIGHT)) {
            camera.moveRotation(0, 1.5f, 0);
        }
        if (input.isKeyPressed(GLFW_KEY_UP)) {
            camera.moveRotation(-1.5f, 0, 0);
        } else if (input.isKeyPressed(GLFW_KEY_DOWN)) {
            camera.moveRotation(1.5f, 0, 0);
        }

        // Mouse look (when mouse is locked)
        if (mouseLocked) {
            float dx = (float) input.getMouseDX();
            float dy = (float) input.getMouseDY();
            camera.moveRotation(-dy * MOUSE_SENSITIVITY, dx * MOUSE_SENSITIVITY, 0);
        }

        // Inventory Selection
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

        // Update MousePicker
        mousePicker.update(camera, transformation, (float) Math.toRadians(60.0f), 0.01f, 1000.f);

        // Block Interaction
        if (mouseLocked) {
            // Breaking (Left Click)
            if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_1)) {
                Vector3f target = mousePicker.raycastBlock(camera, chunkManager, 5.0f);
                if (target != null) {
                    setBlock((int) target.x, (int) target.y, (int) target.z, Block.AIR);
                }
            }
            // Placing (Right Click)
            if (input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_2)) {
                // Use raycastPlace to find the block "against" the hit face
                Vector3f target = mousePicker.raycastPlace(camera, chunkManager, 5.0f);
                if (target != null) {
                    Block selected = inventory.getSelectedBlock();
                    // Avoid placing block inside the player? Simple distance check could be added.
                    setBlock((int) target.x, (int) target.y, (int) target.z, selected);
                }
            }
        }

        // Debug Fly Up/Down
        if (input.isKeyPressed(GLFW_KEY_Q)) {
            camera.movePosition(0, 0.5f, 0);
        }
    }

    private void setBlock(int x, int y, int z, Block block) {
        int cx = x >> 4;
        int cz = z >> 4;
        String key = ChunkManager.getChunkKey(cx, cz);
        Chunk c = chunkManager.getChunks().get(key);
        if (c != null) {
            int lx = x & 15;
            int ly = y;
            int lz = z & 15;
            c.setBlock(lx, ly, lz, block);
            // System.out.println("Set block at " + x + ", " + y + ", " + z + " to " +
            // block);
        }
    }

    @Override
    public void update(float interval, Input input) {
        chunkManager.updateMeshes();

        // Update Physics
        boolean moveUp = input.isKeyPressed(GLFW_KEY_SPACE);
        boolean moveDown = input.isKeyPressed(GLFW_KEY_LEFT_SHIFT);
        boolean fwd = input.isKeyPressed(GLFW_KEY_W);
        boolean back = input.isKeyPressed(GLFW_KEY_S);
        boolean left = input.isKeyPressed(GLFW_KEY_A);
        boolean right = input.isKeyPressed(GLFW_KEY_D);

        camera.update(interval, chunkManager, false, fwd, back, left, right, moveUp, moveDown);

        // Infinite world loading based on player position
        int px = (int) camera.getPosition().x / 16;
        int pz = (int) camera.getPosition().z / 16;
        chunkManager.loadChunksAround(px, pz, 6);

        // Update time system
        timeSystem.update(interval);
    }

    @Override
    public void render(Window window) {
        // Clear screen first
        renderer.clear();

        // Render sky first (background, no depth write)
        skyRenderer.render(
                transformation.getProjectionMatrix(70.0f, window.getWidth(), window.getHeight(), 0.01f, 1000.0f),
                transformation.getViewMatrix(camera), timeSystem);

        // Render chunks with lighting
        renderer.renderChunks(window, camera, chunkManager.getChunks().values(), timeSystem);
        hud.render(window);
    }

    @Override
    public void cleanup() {
        renderer.cleanup();
        if (chunkManager != null) {
            chunkManager.cleanup();
        }
        if (hud != null) {
            hud.cleanup();
        }
        if (skyRenderer != null) {
            skyRenderer.cleanup();
        }
    }
}
