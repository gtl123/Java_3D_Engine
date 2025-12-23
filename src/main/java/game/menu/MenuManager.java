package game.menu;

import engine.io.Input;
import engine.io.Window;

/**
 * Manages menu state and transitions between different menu screens.
 */
public class MenuManager {

    public enum MenuState {
        NONE, // In-game, no menu
        MAIN_MENU, // Title screen
        WORLD_SELECT, // Choose or create world
        NEW_WORLD, // Create new world dialog
        SETTINGS, // Settings menu
        PAUSE_MENU // In-game pause menu
    }

    private MenuState currentState;
    private MenuState previousState; // For back navigation

    // Menu screens (to be implemented)
    // private MainMenuUI mainMenu;
    // private WorldSelectUI worldSelect;
    // private SettingsMenuUI settingsMenu;
    // private PauseMenuUI pauseMenu;

    public MenuManager() {
        this.currentState = MenuState.MAIN_MENU;
        this.previousState = MenuState.NONE;
    }

    /**
     * Switch to a new menu state.
     */
    public void switchTo(MenuState newState) {
        this.previousState = this.currentState;
        this.currentState = newState;
        System.out.println("Menu: " + previousState + " -> " + currentState);
    }

    /**
     * Go back to the previous menu.
     */
    public void goBack() {
        MenuState temp = currentState;
        currentState = previousState;
        previousState = temp;
    }

    /**
     * Handle input for the currently active menu.
     */
    public void handleInput(Input input, Window window) {
        switch (currentState) {
            case MAIN_MENU:
                // mainMenu.handleInput(input, window);
                break;
            case WORLD_SELECT:
                // worldSelect.handleInput(input, window);
                break;
            case SETTINGS:
                // settingsMenu.handleInput(input, window);
                break;
            case PAUSE_MENU:
                // pauseMenu.handleInput(input, window);
                break;
            default:
                break;
        }
    }

    /**
     * Render the currently active menu.
     */
    public void render(Window window) {
        switch (currentState) {
            case MAIN_MENU:
                // mainMenu.render(window);
                break;
            case WORLD_SELECT:
                // worldSelect.render(window);
                break;
            case SETTINGS:
                // settingsMenu.render(window);
                break;
            case PAUSE_MENU:
                // pauseMenu.render(window);
                break;
            default:
                break;
        }
    }

    public MenuState getCurrentState() {
        return currentState;
    }

    public boolean isInMenu() {
        return currentState != MenuState.NONE;
    }
}
