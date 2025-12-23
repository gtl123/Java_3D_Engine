package game.menu;

import engine.io.Input;
import engine.io.Window;
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
        if (input.isKeyPressed(GLFW.GLFW_KEY_UP) || input.isKeyPressed(GLFW.GLFW_KEY_W)) {
            selectedButton = Button.values()[(selectedButton.ordinal() - 1 + Button.values().length)
                    % Button.values().length];
        }
        if (input.isKeyPressed(GLFW.GLFW_KEY_DOWN) || input.isKeyPressed(GLFW.GLFW_KEY_S)) {
            selectedButton = Button.values()[(selectedButton.ordinal() + 1) % Button.values().length];
        }

        // Select with Enter or mouse click
        if (input.isKeyPressed(GLFW.GLFW_KEY_ENTER)) {
            executeButton();
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
        // TODO: Implement 2D rendering
        // For now, this is a placeholder
        System.out.println("Main Menu: " + selectedButton + " selected");
    }
}
