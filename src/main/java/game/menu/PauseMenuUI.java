package game.menu;

import engine.io.Input;
import engine.io.Window;
import game.GameSettings;
import org.lwjgl.glfw.GLFW;

/**
 * Pause menu - shown when player presses ESC during gameplay.
 */
public class PauseMenuUI {

    private enum Button {
        RESUME, SAVE, SETTINGS, SAVE_AND_QUIT
    }

    private MenuManager menuManager;
    private GameSettings settings;
    private Button selectedButton = Button.RESUME;
    private Runnable onSaveWorld;

    public PauseMenuUI(MenuManager menuManager, GameSettings settings, Runnable onSaveWorld) {
        this.menuManager = menuManager;
        this.settings = settings;
        this.onSaveWorld = onSaveWorld;
    }

    public void handleInput(Input input, Window window) {
        // Navigate buttons
        if (input.isKeyPressed(GLFW.GLFW_KEY_UP) || input.isKeyPressed(GLFW.GLFW_KEY_W)) {
            selectedButton = Button.values()[(selectedButton.ordinal() - 1 + Button.values().length)
                    % Button.values().length];
        }
        if (input.isKeyPressed(GLFW.GLFW_KEY_DOWN) || input.isKeyPressed(GLFW.GLFW_KEY_S)) {
            selectedButton = Button.values()[(selectedButton.ordinal() + 1) % Button.values().length];
        }

        // Execute button
        if (input.isKeyPressed(GLFW.GLFW_KEY_ENTER)) {
            executeButton();
        }

        // ESC to resume
        if (input.isKeyPressed(GLFW.GLFW_KEY_ESCAPE)) {
            menuManager.switchTo(MenuManager.MenuState.NONE);
        }
    }

    private void executeButton() {
        switch (selectedButton) {
            case RESUME:
                menuManager.switchTo(MenuManager.MenuState.NONE);
                break;
            case SAVE:
                if (onSaveWorld != null) {
                    onSaveWorld.run();
                }
                break;
            case SETTINGS:
                menuManager.switchTo(MenuManager.MenuState.SETTINGS);
                break;
            case SAVE_AND_QUIT:
                if (onSaveWorld != null) {
                    onSaveWorld.run();
                }
                menuManager.switchTo(MenuManager.MenuState.MAIN_MENU);
                break;
        }
    }

    public void render(Window window) {
        // TODO: Implement 2D rendering with semi-transparent overlay
        System.out.println("Pause Menu: " + selectedButton + " selected");
    }
}
