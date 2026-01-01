package game.menu;

import engine.io.Input;
import engine.io.Window;
import game.save.WorldStorage;
import game.save.WorldStorage.WorldMetadata;
import game.voxel.HUD;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * World selection menu - shows list of worlds, allows creating new or deleting
 * existing.
 */
public class WorldSelectUI {

    private MenuManager menuManager;
    private List<WorldMetadata> availableWorlds;
    private int selectedIndex = 0;
    private boolean confirmDelete = false; // Track if we are in delete confirmation mode

    // private long lastInputTime = 0; // Removed unused
    // private static final long INPUT_DELAY = 200_000_000L; // Removed unused

    public WorldSelectUI(MenuManager menuManager) {
        this.menuManager = menuManager;
        this.availableWorlds = new ArrayList<>();
    }

    public void refreshWorldList() {
        this.availableWorlds = WorldStorage.listWorlds();
        // Reset selection if out of bounds
        if (selectedIndex >= getOptionCount()) {
            selectedIndex = 0;
        }
    }

    private int getOptionCount() {
        // Worlds + Create New + Back
        return availableWorlds.size() + 2;
    }

    public void handleInput(Input input, Window window) {
        // If in delete confirmation mode
        if (confirmDelete) {
            if (input.isKeyJustPressed(GLFW.GLFW_KEY_Y) || input.isKeyJustPressed(GLFW.GLFW_KEY_ENTER)) {
                // Confirm delete
                if (selectedIndex < availableWorlds.size()) {
                    String worldName = availableWorlds.get(selectedIndex).name;
                    System.out.println("Deleting " + worldName);
                    WorldStorage.deleteWorld(worldName);
                    refreshWorldList();
                }
                confirmDelete = false;
            } else if (input.isKeyJustPressed(GLFW.GLFW_KEY_N) || input.isKeyJustPressed(GLFW.GLFW_KEY_ESCAPE)) {
                // Cancel delete
                confirmDelete = false;
            }
            return;
        }

        // Navigation
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_UP) || input.isKeyJustPressed(GLFW.GLFW_KEY_W)
                || (input.isKeyJustPressed(GLFW.GLFW_KEY_TAB) && input.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT))) {
            selectedIndex--;
            if (selectedIndex < 0)
                selectedIndex = getOptionCount() - 1;
        }
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_DOWN) || input.isKeyJustPressed(GLFW.GLFW_KEY_S)
                || input.isKeyJustPressed(GLFW.GLFW_KEY_TAB)) {
            selectedIndex++;
            if (selectedIndex >= getOptionCount())
                selectedIndex = 0;
        }

        // Selection
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_ENTER) || input.isKeyJustPressed(GLFW.GLFW_KEY_SPACE)) {
            executeSelection();
        }

        // Delete trigger
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_DELETE)) {
            if (selectedIndex < availableWorlds.size()) {
                confirmDelete = true;
            }
        }

        // Back
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_ESCAPE)) {
            menuManager.goBack();
        }

        // Mouse Interaction
        if (!confirmDelete) {
            double mx = input.getMouseX();
            double my = input.getMouseY();
            boolean mouseMoved = input.getMouseDX() != 0 || input.getMouseDY() != 0;

            float startY = 200f;
            float spacing = 50f;
            float fontHeight = 35f;
            float btnWidth = 400f; // estimation

            // 1. Check Worlds
            for (int i = 0; i < availableWorlds.size(); i++) {
                float y = startY + i * spacing;
                boolean over = mx >= (window.getWidth() / 2f - btnWidth / 2f)
                        && mx <= (window.getWidth() / 2f + btnWidth / 2f)
                        && my >= y && my <= (y + fontHeight);
                if (over) {
                    if (mouseMoved) {
                        selectedIndex = i;
                    }
                    if (input.isMouseButtonJustPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
                        selectedIndex = i;
                        executeSelection();
                    }
                }
            }

            // 2. Check Create New Button
            float btnY = startY + availableWorlds.size() * spacing + 20;
            boolean overCreate = mx >= (window.getWidth() / 2f - btnWidth / 2f)
                    && mx <= (window.getWidth() / 2f + btnWidth / 2f)
                    && my >= btnY && my <= (btnY + fontHeight);
            if (overCreate) {
                if (mouseMoved) {
                    selectedIndex = availableWorlds.size();
                }
                if (input.isMouseButtonJustPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
                    selectedIndex = availableWorlds.size();
                    executeSelection();
                }
            }

            // 3. Check Back Button
            float backBtnY = btnY + 50;
            boolean overBack = mx >= (window.getWidth() / 2f - btnWidth / 2f)
                    && mx <= (window.getWidth() / 2f + btnWidth / 2f)
                    && my >= backBtnY && my <= (backBtnY + fontHeight);
            if (overBack) {
                if (mouseMoved) {
                    selectedIndex = availableWorlds.size() + 1;
                }
                if (input.isMouseButtonJustPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
                    selectedIndex = availableWorlds.size() + 1;
                    executeSelection();
                }
            }
        }

    }

    private void executeSelection() {
        int listSize = availableWorlds.size();

        if (selectedIndex < listSize) {
            // Play existing world
            playWorld(availableWorlds.get(selectedIndex).name);
        } else if (selectedIndex == listSize) {
            // Create New World
            menuManager.switchTo(MenuManager.MenuState.NEW_WORLD);
        } else {
            // Back
            menuManager.goBack();
        }
    }

    private void playWorld(String worldName) {
        System.out.println("Play World: " + worldName);
        try {
            // Load the world. For existing worlds, seed is loaded from metadata/save.
            // We pass 0 as forcedSeed to indicate "use existing or random".
            menuManager.getGame().loadWorld(worldName, 0);
            menuManager.switchTo(MenuManager.MenuState.NONE);
        } catch (Exception e) {
            e.printStackTrace();
            // Optional: Show error to user via HUD or dialog
            System.err.println("Failed to load world: " + e.getMessage());
        }
    }

    public void render(Window window) {
        HUD hud = menuManager.getGame().getHUD();
        hud.bind();

        // Darken background
        hud.renderRect(window.getWidth() / 2f, window.getHeight() / 2f, window.getWidth(), window.getHeight(),
                new Vector4f(0, 0, 0, 0.8f));

        hud.renderTextCentered("SELECT WORLD", window.getWidth() / 2f, 100, 8f);

        if (confirmDelete) {
            // Render Delete Confirmation Overlay
            hud.renderRect(window.getWidth() / 2f, window.getHeight() / 2f, 600, 300,
                    new Vector4f(0.2f, 0, 0, 0.95f));
            hud.renderTextCentered("DELETE WORLD?", window.getWidth() / 2f, window.getHeight() / 2f - 40, 6f,
                    new Vector4f(1, 0, 0, 1));
            String name = (selectedIndex < availableWorlds.size()) ? availableWorlds.get(selectedIndex).name : "???";
            hud.renderTextCentered(name, window.getWidth() / 2f, window.getHeight() / 2f + 20, 5f);
            hud.renderTextCentered("Press Y to Confirm, N to Cancel", window.getWidth() / 2f,
                    window.getHeight() / 2f + 80, 4f);
        } else {
            // Render List
            float startY = 200f;
            float spacing = 50f;

            // List Worlds
            for (int i = 0; i < availableWorlds.size(); i++) {
                float y = startY + i * spacing;
                String name = availableWorlds.get(i).name;
                boolean selected = (i == selectedIndex);

                Vector4f color = selected ? new Vector4f(1f, 1f, 0f, 1f) : new Vector4f(1f, 1f, 1f, 1f);

                if (selected) {
                    hud.renderTextCentered("> " + name + " <", window.getWidth() / 2f, y, 5f, color);
                } else {
                    hud.renderTextCentered(name, window.getWidth() / 2f, y, 5f, color);
                }
            }

            // Buttons at bottom
            float btnY = startY + availableWorlds.size() * spacing + 20;

            // Create New
            boolean createSelected = (selectedIndex == availableWorlds.size());
            hud.renderTextCentered("CREATE NEW WORLD", window.getWidth() / 2f, btnY, 5f,
                    createSelected ? new Vector4f(0, 1, 0, 1) : new Vector4f(0.7f, 0.7f, 0.7f, 1));

            // Back
            boolean backSelected = (selectedIndex == availableWorlds.size() + 1);
            hud.renderTextCentered("BACK", window.getWidth() / 2f, btnY + 50, 5f,
                    backSelected ? new Vector4f(1, 0, 0, 1) : new Vector4f(0.7f, 0.7f, 0.7f, 1));

            // Instructions
            hud.renderTextCentered("DEL to Delete | ENTER to Select", window.getWidth() / 2f, window.getHeight() - 50,
                    4f);
        }

        hud.unbind();
    }
}
