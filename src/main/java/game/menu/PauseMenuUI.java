package game.menu;

import engine.io.Input;
import engine.io.Window;
import org.lwjgl.glfw.GLFW;

/**
 * Pause menu UI - resume, save, settings, quit.
 */
public class PauseMenuUI {
    private final MenuManager menuManager;
    private int selectedButton = 0;

    public PauseMenuUI(MenuManager menuManager) {
        this.menuManager = menuManager;
    }

    public void handleInput(Input input, Window window) {
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_UP) || input.isKeyJustPressed(GLFW.GLFW_KEY_W)
                || (input.isKeyJustPressed(GLFW.GLFW_KEY_TAB) && input.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT))) {
            selectedButton--;
            if (selectedButton < 0) {
                selectedButton = 3;
            }
        }
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_DOWN) || input.isKeyJustPressed(GLFW.GLFW_KEY_S)
                || input.isKeyJustPressed(GLFW.GLFW_KEY_TAB)) {
            selectedButton++;
            if (selectedButton > 3) {
                selectedButton = 0;
            }
        }

        if (input.isKeyJustPressed(GLFW.GLFW_KEY_ENTER) || input.isKeyJustPressed(GLFW.GLFW_KEY_SPACE)) {
            executeButton();
        }

        // Resume on ESC
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_ESCAPE)) {
            menuManager.goBack();
        }

        // Mouse Interaction
        double mx = input.getMouseX();
        double my = input.getMouseY();
        boolean mouseMoved = input.getMouseDX() != 0 || input.getMouseDY() != 0;

        float startY = window.getHeight() / 2f - 50;
        float spacing = 60f;
        float fontHeight = 35f;
        float btnWidth = 300f; // estimation

        String[] labels = { "RESUME", "SAVE GAME", "SETTINGS", "QUIT TO TITLE" };
        for (int i = 0; i < labels.length; i++) {
            float y = startY + i * spacing;
            boolean over = mx >= (window.getWidth() / 2f - btnWidth / 2f)
                    && mx <= (window.getWidth() / 2f + btnWidth / 2f)
                    && my >= y && my <= (y + fontHeight);
            if (over) {
                if (mouseMoved) {
                    selectedButton = i;
                }
                if (input.isMouseButtonJustPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
                    selectedButton = i;
                    executeButton();
                }
            }
        }

    }

    private void executeButton() {
        switch (selectedButton) {
            case 0: // Resume
                menuManager.goBack();
                break;
            case 1: // Save
                System.out.println("Saving Game...");
                menuManager.getGame().saveWorld();
                break;
            case 2: // Settings
                menuManager.switchTo(MenuManager.MenuState.SETTINGS);
                break;
            case 3: // Quit to Title
                System.out.println("Quitting to Title...");
                menuManager.getGame().saveWorld();
                menuManager.switchTo(MenuManager.MenuState.MAIN_MENU);
                break;
        }
    }

    public void render(Window window) {
        game.voxel.HUD hud = menuManager.getGame().getHUD();
        hud.bind();

        // Dark Overlay
        hud.renderRect(window.getWidth() / 2f, window.getHeight() / 2f, window.getWidth(), window.getHeight(),
                new org.joml.Vector4f(0, 0, 0, 0.6f));

        hud.renderTextCentered("PAUSED", window.getWidth() / 2f, 150, 8f);

        String[] labels = { "RESUME", "SAVE GAME", "SETTINGS", "QUIT TO TITLE" };
        float startY = window.getHeight() / 2f - 50;
        float spacing = 60f;

        for (int i = 0; i < labels.length; i++) {
            float y = startY + i * spacing;
            boolean selected = (i == selectedButton);
            org.joml.Vector4f color = selected ? new org.joml.Vector4f(1f, 1f, 0f, 1f)
                    : new org.joml.Vector4f(1f, 1f, 1f, 1f);

            if (selected) {
                hud.renderTextCentered("> " + labels[i] + " <", window.getWidth() / 2f, y, 5f, color);
            } else {
                hud.renderTextCentered(labels[i], window.getWidth() / 2f, y, 5f, color);
            }
        }

        hud.unbind();
    }
}
