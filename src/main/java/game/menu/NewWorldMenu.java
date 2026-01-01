package game.menu;

import engine.io.Input;
import engine.io.Window;
import game.voxel.HUD;
import org.lwjgl.glfw.GLFW;

public class NewWorldMenu {

    private final MenuManager menuManager;
    private StringBuilder worldNameInput = new StringBuilder("New World");
    private StringBuilder seedInput = new StringBuilder();
    private int selectedButton = 0; // 0 = Name, 1 = Seed, 2 = Create, 3 = Cancel

    public NewWorldMenu(MenuManager menuManager) {
        this.menuManager = menuManager;
    }

    public void handleInput(Input input, Window window) {
        // Navigation (Up/Down)
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_UP)) {
            selectedButton--;
            if (selectedButton < 0)
                selectedButton = 3;
        }
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_DOWN)) {
            selectedButton++;
            if (selectedButton > 3)
                selectedButton = 0;
        }

        // Action
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_ENTER)) {
            if (selectedButton == 2) { // Create
                createWorld();
            } else if (selectedButton == 3) { // Cancel
                menuManager.goBack();
            }
        }

        // Mouse Interaction
        double mx = input.getMouseX();
        double my = input.getMouseY();
        boolean mouseMoved = input.getMouseDX() != 0 || input.getMouseDY() != 0;

        float startY = 250f;
        float spacing = 60f;
        float fontHeight = 35f; // 5 * 7
        float btnWidth = 400f; // estimation

        // Buttons list
        float[] centersY = { startY, startY + spacing, startY + spacing * 3, startY + spacing * 4 };
        for (int i = 0; i < centersY.length; i++) {
            float y = centersY[i];
            boolean over = mx >= (window.getWidth() / 2f - btnWidth / 2f)
                    && mx <= (window.getWidth() / 2f + btnWidth / 2f)
                    && my >= y && my <= (y + fontHeight);
            if (over) {
                if (mouseMoved) {
                    selectedButton = i;
                }
                if (input.isMouseButtonJustPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
                    selectedButton = i;
                    if (i == 2)
                        createWorld();
                    else if (i == 3)
                        menuManager.goBack();
                }
            }
        }

    }

    private void createWorld() {
        String name = worldNameInput.toString();
        long seed = seedInput.length() > 0 ? Long.parseLong(seedInput.toString()) : System.currentTimeMillis();

        // Unique name generation to avoid collision
        name = "World_" + System.currentTimeMillis();

        System.out.println("Creating world: " + name + " Seed: " + seed);
        try {
            menuManager.getGame().loadWorld(name, seed);
            menuManager.switchTo(MenuManager.MenuState.NONE); // Play
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to create world: " + e.getMessage());
        }
    }

    public void render(Window window) {
        HUD hud = menuManager.getGame().getHUD();
        hud.bind();

        // Background
        hud.renderRect(window.getWidth() / 2f, window.getHeight() / 2f, window.getWidth(), window.getHeight(),
                new org.joml.Vector4f(0, 0, 0, 0.8f));

        hud.renderTextCentered("CREATE NEW WORLD", window.getWidth() / 2f, 100, 8f);

        float spacing = 60f;
        float startY = 250f;

        // Name Field
        String nameStr = "NAME: " + worldNameInput.toString() + (selectedButton == 0 ? "_" : "");
        hud.renderTextCentered(nameStr, window.getWidth() / 2f, startY, 5f,
                selectedButton == 0 ? new org.joml.Vector4f(1, 1, 0, 1) : new org.joml.Vector4f(1, 1, 1, 1));

        // Seed Field
        String seedStr = "SEED: " + (seedInput.length() == 0 ? "(RANDOM)" : seedInput.toString())
                + (selectedButton == 1 ? "_" : "");
        hud.renderTextCentered(seedStr, window.getWidth() / 2f, startY + spacing, 5f,
                selectedButton == 1 ? new org.joml.Vector4f(1, 1, 0, 1) : new org.joml.Vector4f(1, 1, 1, 1));

        // Create Button
        hud.renderTextCentered("CREATE WORLD", window.getWidth() / 2f, startY + spacing * 3, 6f,
                selectedButton == 2 ? new org.joml.Vector4f(0, 1, 0, 1) : new org.joml.Vector4f(1, 1, 1, 1));

        // Cancel Button
        hud.renderTextCentered("CANCEL", window.getWidth() / 2f, startY + spacing * 4, 6f,
                selectedButton == 3 ? new org.joml.Vector4f(1, 0, 0, 1) : new org.joml.Vector4f(1, 1, 1, 1));

        hud.unbind();
    }
}
