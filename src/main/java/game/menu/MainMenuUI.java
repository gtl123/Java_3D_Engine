package game.menu;

import engine.io.Input;
import engine.io.Window;
import game.voxel.HUD;
import game.GameSettings;
import org.lwjgl.glfw.GLFW;

/**
 * Main menu UI - title screen with play, settings, and quit options.
 */
public class MainMenuUI {

    private enum Button {
        PLAY, SETTINGS, QUIT
    }

    private Button selectedButton = Button.PLAY;
    private MenuManager menuManager;
    private GameSettings settings;

    public MainMenuUI(MenuManager menuManager, GameSettings settings) {
        this.menuManager = menuManager;
        this.settings = settings;
    }

    public void handleInput(Input input, Window window) {
        // Navigate with arrow keys
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_UP) || input.isKeyJustPressed(GLFW.GLFW_KEY_W)
                || (input.isKeyJustPressed(GLFW.GLFW_KEY_TAB) && input.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT))) {
            selectedButton = Button.values()[(selectedButton.ordinal() - 1 + Button.values().length)
                    % Button.values().length];
        }
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_DOWN) || input.isKeyJustPressed(GLFW.GLFW_KEY_S)
                || input.isKeyJustPressed(GLFW.GLFW_KEY_TAB)) {
            selectedButton = Button.values()[(selectedButton.ordinal() + 1) % Button.values().length];
        }

        // Select with Enter or mouse click
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_ENTER) || input.isKeyJustPressed(GLFW.GLFW_KEY_SPACE)) {
            executeButton();
        }

        // Mouse Interaction
        double mx = input.getMouseX();
        double my = input.getMouseY();
        boolean mouseMoved = input.getMouseDX() != 0 || input.getMouseDY() != 0;

        float startY = window.getHeight() / 2f;
        float spacing = 50f;
        float fontHeight = 35f;

        Button[] buttons = Button.values();
        for (int i = 0; i < buttons.length; i++) {
            float y = startY + i * spacing;
            float btnWidth = 300f;
            float btnHeight = fontHeight;

            boolean over = mx >= (window.getWidth() / 2f - btnWidth / 2f)
                    && mx <= (window.getWidth() / 2f + btnWidth / 2f)
                    && my >= y && my <= (y + btnHeight);

            if (over) {
                if (mouseMoved) {
                    selectedButton = buttons[i];
                }
                if (input.isMouseButtonJustPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
                    selectedButton = buttons[i];
                    executeButton();
                }
            }
        }

    }

    private void executeButton() {
        switch (selectedButton) {
            case PLAY:
                menuManager.switchTo(MenuManager.MenuState.WORLD_SELECT);
                break;
            case SETTINGS:
                menuManager.switchTo(MenuManager.MenuState.SETTINGS);
                break;
            case QUIT:
                System.exit(0);
                break;
        }
    }

    public void render(Window window) {
        HUD hud = menuManager.getGame().getHUD();
        hud.bind();

        // 1. Draw semi-transparent background
        hud.renderRect(window.getWidth() / 2f, window.getHeight() / 2f, window.getWidth(), window.getHeight(),
                new org.joml.Vector4f(0f, 0f, 0f, 0.5f));

        // 2. Title
        hud.renderTextCentered("VOXEL GAME", window.getWidth() / 2f, window.getHeight() / 4f, 8f);

        // 3. Menu items
        String[] labels = { "PLAY", "SETTINGS", "QUIT" };
        float startY = window.getHeight() / 2f;
        float spacing = 50f;

        for (int i = 0; i < labels.length; i++) {
            float y = startY + i * spacing;
            boolean selected = (i == selectedButton.ordinal());

            // Draw selection indicator or color
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
