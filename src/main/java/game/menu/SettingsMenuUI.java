package game.menu;

import engine.io.Input;
import engine.io.Window;
import game.GameSettings;
import org.lwjgl.glfw.GLFW;

/**
 * Settings menu - allows adjusting render distance, FOV, vsync, and mouse
 * sensitivity.
 */
public class SettingsMenuUI {

    private enum Setting {
        RENDER_DISTANCE("Render Distance", 4, 16),
        FOV("Field of View", 60, 110),
        VSYNC("VSync", 0, 1),
        MOUSE_SENSITIVITY("Mouse Sensitivity", 1, 20); // Store as int (0.1 to 2.0, *10)

        final String display;
        final int min, max;

        Setting(String display, int min, int max) {
            this.display = display;
            this.min = min;
            this.max = max;
        }
    }

    private MenuManager menuManager;
    private GameSettings settings;
    private Setting selectedSetting = Setting.RENDER_DISTANCE;

    public SettingsMenuUI(MenuManager menuManager, GameSettings settings) {
        this.menuManager = menuManager;
        this.settings = settings;
    }

    public void handleInput(Input input, Window window) {
        // Navigate settings
        if (input.isKeyPressed(GLFW.GLFW_KEY_UP) || input.isKeyPressed(GLFW.GLFW_KEY_W)) {
            selectedSetting = Setting.values()[(selectedSetting.ordinal() - 1 + Setting.values().length)
                    % Setting.values().length];
        }
        if (input.isKeyPressed(GLFW.GLFW_KEY_DOWN) || input.isKeyPressed(GLFW.GLFW_KEY_S)) {
            selectedSetting = Setting.values()[(selectedSetting.ordinal() + 1) % Setting.values().length];
        }

        // Adjust selected setting
        if (input.isKeyPressed(GLFW.GLFW_KEY_LEFT) || input.isKeyPressed(GLFW.GLFW_KEY_A)) {
            adjustSetting(-1);
        }
        if (input.isKeyPressed(GLFW.GLFW_KEY_RIGHT) || input.isKeyPressed(GLFW.GLFW_KEY_D)) {
            adjustSetting(1);
        }

        // Save and exit
        if (input.isKeyPressed(GLFW.GLFW_KEY_ENTER)) {
            settings.save();
            menuManager.goBack();
        }

        // Cancel
        if (input.isKeyPressed(GLFW.GLFW_KEY_ESCAPE)) {
            settings.load(); // Revert changes
            menuManager.goBack();
        }
    }

    private void adjustSetting(int delta) {
        switch (selectedSetting) {
            case RENDER_DISTANCE:
                settings.setRenderDistance(settings.getRenderDistance() + delta);
                break;
            case FOV:
                settings.setFov(settings.getFov() + delta);
                break;
            case VSYNC:
                settings.setVsync(!settings.isVsync());
                break;
            case MOUSE_SENSITIVITY:
                float newSens = settings.getMouseSensitivity() + (delta * 0.1f);
                settings.setMouseSensitivity(newSens);
                break;
        }
    }

    public void render(Window window) {
        // TODO: Implement 2D rendering
        System.out.println("Settings Menu:");
        for (Setting s : Setting.values()) {
            String marker = (s == selectedSetting) ? "> " : "  ";
            String value = getValue(s);
            System.out.println(marker + s.display + ": " + value);
        }
        System.out.println("\nPress Enter to Save, ESC to Cancel");
    }

    private String getValue(Setting s) {
        switch (s) {
            case RENDER_DISTANCE:
                return String.valueOf(settings.getRenderDistance()) + " chunks";
            case FOV:
                return String.format("%.0f°", settings.getFov());
            case VSYNC:
                return settings.isVsync() ? "On" : "Off";
            case MOUSE_SENSITIVITY:
                return String.format("%.1f", settings.getMouseSensitivity());
            default:
                return "";
        }
    }
}
