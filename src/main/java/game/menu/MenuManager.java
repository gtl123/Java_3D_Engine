package game.menu;

import engine.io.Input;
import engine.io.Window;
import game.GameSettings;

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

    // Menu screens
    private MainMenuUI mainMenu;
    private WorldSelectUI worldSelect;
    private NewWorldMenu newWorldMenu;
    private SettingsMenuUI settingsMenu;
    private PauseMenuUI pauseMenu;

    // References
    private GameSettings settings;
    private game.voxel.VoxelGame game;

    public void setGame(game.voxel.VoxelGame game) {
        this.game = game;
    }

    public game.voxel.VoxelGame getGame() {
        return game;
    }

    public MenuManager() {
        this.currentState = MenuState.MAIN_MENU;
        this.previousState = MenuState.NONE;

        // Settings are shared
        this.settings = new GameSettings();

        // Initialize menus
        this.mainMenu = new MainMenuUI(this, settings);
        this.worldSelect = new WorldSelectUI(this);
        this.newWorldMenu = new NewWorldMenu(this);
        this.settingsMenu = new SettingsMenuUI(this, settings);
        this.pauseMenu = new PauseMenuUI(this);
    }

    /**
     * Switch to a new menu state.
     */
    public void switchTo(MenuState newState) {
        if (currentState == newState)
            return;

        this.previousState = this.currentState;
        this.currentState = newState;

        // Refresh specific menus if needed
        if (newState == MenuState.WORLD_SELECT) {
            worldSelect.refreshWorldList();
        }

        System.out.println("Menu: " + previousState + " -> " + currentState);
    }

    /**
     * Go back to the previous menu.
     */
    public void goBack() {
        if (previousState != MenuState.NONE) {
            switchTo(previousState);
        } else {
            // Default fallback logic
            if (currentState == MenuState.SETTINGS || currentState == MenuState.WORLD_SELECT) {
                switchTo(MenuState.MAIN_MENU);
            } else if (currentState == MenuState.PAUSE_MENU) {
                switchTo(MenuState.NONE); // Resume game
            } else if (currentState == MenuState.NEW_WORLD) {
                switchTo(MenuState.WORLD_SELECT);
            }
        }
    }

    /**
     * Handle input for the currently active menu.
     */
    public void handleInput(Input input, Window window) {
        switch (currentState) {
            case MAIN_MENU:
                mainMenu.handleInput(input, window);
                break;
            case WORLD_SELECT:
                worldSelect.handleInput(input, window);
                break;
            case NEW_WORLD:
                newWorldMenu.handleInput(input, window);
                break;
            case SETTINGS:
                settingsMenu.handleInput(input, window);
                break;
            case PAUSE_MENU:
                pauseMenu.handleInput(input, window);
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
                mainMenu.render(window);
                break;
            case WORLD_SELECT:
                worldSelect.render(window);
                break;
            case NEW_WORLD:
                newWorldMenu.render(window);
                break;
            case SETTINGS:
                settingsMenu.render(window);
                break;
            case PAUSE_MENU:
                pauseMenu.render(window);
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

    public GameSettings getSettings() {
        return settings;
    }
}
