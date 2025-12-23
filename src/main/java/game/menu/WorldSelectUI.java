package game.menu;

import engine.io.Input;
import engine.io.Window;
import game.save.WorldStorage;
import game.save.WorldStorage.WorldMetadata;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * World selection menu - shows list of worlds, allows creating new or deleting
 * existing.
 */
public class WorldSelectUI {

    private MenuManager menuManager;
    private List<WorldMetadata> worlds;
    private int selectedIndex = 0;
    private boolean showConfirmDelete = false;

    public WorldSelectUI(MenuManager menuManager) {
        this.menuManager = menuManager;
        refreshWorldList();
    }

    public void refreshWorldList() {
        this.worlds = WorldStorage.listWorlds();
        if (selectedIndex >= worlds.size()) {
            selectedIndex = Math.max(0, worlds.size() - 1);
        }
    }

    public void handleInput(Input input, Window window) {
        if (showConfirmDelete) {
            handleDeleteConfirmation(input);
            return;
        }

        // Navigate list
        if (input.isKeyPressed(GLFW.GLFW_KEY_UP) || input.isKeyPressed(GLFW.GLFW_KEY_W)) {
            if (!worlds.isEmpty()) {
                selectedIndex = (selectedIndex - 1 + worlds.size()) % worlds.size();
            }
        }
        if (input.isKeyPressed(GLFW.GLFW_KEY_DOWN) || input.isKeyPressed(GLFW.GLFW_KEY_S)) {
            if (!worlds.isEmpty()) {
                selectedIndex = (selectedIndex + 1) % worlds.size();
            }
        }

        // Actions
        if (input.isKeyPressed(GLFW.GLFW_KEY_ENTER)) {
            if (!worlds.isEmpty()) {
                playWorld(worlds.get(selectedIndex));
            }
        }

        if (input.isKeyPressed(GLFW.GLFW_KEY_N)) {
            menuManager.switchTo(MenuManager.MenuState.NEW_WORLD);
        }

        if (input.isKeyPressed(GLFW.GLFW_KEY_DELETE) && !worlds.isEmpty()) {
            showConfirmDelete = true;
        }

        if (input.isKeyPressed(GLFW.GLFW_KEY_ESCAPE)) {
            menuManager.goBack();
        }
    }

    private void handleDeleteConfirmation(Input input) {
        if (input.isKeyPressed(GLFW.GLFW_KEY_Y)) {
            if (!worlds.isEmpty()) {
                WorldMetadata toDelete = worlds.get(selectedIndex);
                WorldStorage.deleteWorld(toDelete.name);
                refreshWorldList();
            }
            showConfirmDelete = false;
        }
        if (input.isKeyPressed(GLFW.GLFW_KEY_N) || input.isKeyPressed(GLFW.GLFW_KEY_ESCAPE)) {
            showConfirmDelete = false;
        }
    }

    private void playWorld(WorldMetadata world) {
        System.out.println("Loading world: " + world.name + " (seed: " + world.seed + ")");
        // TODO: Actually load the world and start the game
        menuManager.switchTo(MenuManager.MenuState.NONE);
    }

    public void render(Window window) {
        // TODO: Implement 2D rendering
        System.out.println("World Select Menu:");
        for (int i = 0; i < worlds.size(); i++) {
            String marker = (i == selectedIndex) ? "> " : "  ";
            System.out.println(marker + worlds.get(i).name);
        }
        if (showConfirmDelete) {
            System.out.println("Delete world? (Y/N)");
        }
    }
}
