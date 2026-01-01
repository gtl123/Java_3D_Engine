package game.menu;

import engine.io.Input;
import engine.io.Window;
import game.GameSettings;
import game.voxel.HUD;
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

    // private long lastInputTime = 0; // Removed unused
    // private static final long INPUT_DELAY = 150_000_000L; // Removed unused

    public SettingsMenuUI(MenuManager menuManager, GameSettings settings) {
        this.menuManager = menuManager;
        this.settings = settings;
    }

    public void handleInput(Input input, Window window) {
        // Navigate settings
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_UP) || input.isKeyJustPressed(GLFW.GLFW_KEY_W)
                || (input.isKeyJustPressed(GLFW.GLFW_KEY_TAB) && input.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT))) {
            selectedSetting = Setting.values()[(selectedSetting.ordinal() - 1 + Setting.values().length)
                    % Setting.values().length];
        }
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_DOWN) || input.isKeyJustPressed(GLFW.GLFW_KEY_S)
                || input.isKeyJustPressed(GLFW.GLFW_KEY_TAB)) {
            selectedSetting = Setting.values()[(selectedSetting.ordinal() + 1) % Setting.values().length];
        }

        // Adjust selected setting
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_LEFT) || input.isKeyJustPressed(GLFW.GLFW_KEY_A)) {
            adjustSetting(-1);
        }
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_RIGHT) || input.isKeyJustPressed(GLFW.GLFW_KEY_D)) {
            adjustSetting(1);
        }

        // Save and exit
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_ENTER)) {
            settings.save();
            menuManager.goBack();
        }

        // Cancel
        if (input.isKeyJustPressed(GLFW.GLFW_KEY_ESCAPE)) {
            settings.load(); // Revert changes
            menuManager.goBack();
        }

        // Mouse Interaction
        double mx = input.getMouseX();
        double my = input.getMouseY();
        boolean mouseMoved = input.getMouseDX() != 0 || input.getMouseDY() != 0;

        float startY = 200;
        float spacing = 60f;
        float fontHeight = 35f; // 5 * 7
        float btnWidth = 500f; // estimation

        // Settings list
        Setting[] settingsList = Setting.values();
        for (int i = 0; i < settingsList.length; i++) {
            float y = startY + i * spacing;

            boolean over = mx >= (window.getWidth() / 2f - btnWidth / 2f)
                    && mx <= (window.getWidth() / 2f + btnWidth / 2f)
                    && my >= y && my <= (y + fontHeight);

            if (over) {
                if (mouseMoved) {
                    selectedSetting = settingsList[i];
                }
                // Left click -> Increase
                if (input.isMouseButtonJustPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
                    selectedSetting = settingsList[i];
                    adjustSetting(1);
                }
                // Right click -> Decrease
                if (input.isMouseButtonJustPressed(GLFW.GLFW_MOUSE_BUTTON_2)) {
                    selectedSetting = settingsList[i];
                    adjustSetting(-1);
                }
            }
        }

        // Save Button
        float saveY = window.getHeight() - 100;
        float saveHeight = 28f;
        boolean overSave = mx >= (window.getWidth() / 2f - btnWidth / 2f)
                && mx <= (window.getWidth() / 2f + btnWidth / 2f)
                && my >= saveY && my <= (saveY + saveHeight);
        if (overSave && input.isMouseButtonJustPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
            settings.save();
            menuManager.goBack();
        }

        // Cancel Button
        float cancelY = window.getHeight() - 60;
        boolean overCancel = mx >= (window.getWidth() / 2f - btnWidth / 2f)
                && mx <= (window.getWidth() / 2f + btnWidth / 2f)
                && my >= cancelY && my <= (cancelY + saveHeight);
        if (overCancel && input.isMouseButtonJustPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
            settings.load();
            menuManager.goBack();
        }

    }

    private void adjustSetting(int delta) {
        switch (selectedSetting) {
            case RENDER_DISTANCE:
                int rd = Math.max(Setting.RENDER_DISTANCE.min,
                        Math.min(Setting.RENDER_DISTANCE.max, settings.getRenderDistance() + delta));
                settings.setRenderDistance(rd);
                break;
            case FOV:
                float fov = Math.max(Setting.FOV.min, Math.min(Setting.FOV.max, settings.getFov() + delta * 5)); // 5
                                                                                                                 // degree
                                                                                                                 // steps
                settings.setFov(fov);
                break;
            case VSYNC:
                settings.setVsync(!settings.isVsync());
                break;
            case MOUSE_SENSITIVITY:
                // Sens is stored as float, min/max for sens in Setting are 1 and 20 (*0.1)
                float currentSens = settings.getMouseSensitivity();
                float newSens = Math.max(Setting.MOUSE_SENSITIVITY.min * 0.1f,
                        Math.min(Setting.MOUSE_SENSITIVITY.max * 0.1f, currentSens + (delta * 0.1f)));
                settings.setMouseSensitivity(newSens);
                break;
        }
    }

    public void render(Window window) {
        HUD hud = menuManager.getGame().getHUD();
        hud.bind();

        // Background
        hud.renderRect(window.getWidth() / 2f, window.getHeight() / 2f, window.getWidth(), window.getHeight(),
                new org.joml.Vector4f(0, 0, 0, 0.8f));

        // Title
        hud.renderTextCentered("SETTINGS", window.getWidth() / 2f, 100, 8f);

        // Options
        float startY = 200;
        float spacing = 60f;
        int i = 0;

        for (Setting s : Setting.values()) {
            float y = startY + i * spacing;
            boolean selected = (s == selectedSetting);
            org.joml.Vector4f color = selected ? new org.joml.Vector4f(1f, 1f, 0f, 1f)
                    : new org.joml.Vector4f(1f, 1f, 1f, 1f);

            String text = s.display + ": " + getValue(s);
            if (selected) {
                hud.renderTextCentered("> " + text + " <", window.getWidth() / 2f, y, 5f, color);
            } else {
                hud.renderTextCentered(text, window.getWidth() / 2f, y, 5f, color);
            }
            i++;
        }

        hud.renderTextCentered("PRESS ENTER TO SAVE", window.getWidth() / 2f, window.getHeight() - 100, 4f);
        hud.renderTextCentered("PRESS ESC TO CANCEL", window.getWidth() / 2f, window.getHeight() - 60, 4f);

        hud.unbind();
    }

    private String getValue(Setting s) {
        switch (s) {
            case RENDER_DISTANCE:
                return String.valueOf(settings.getRenderDistance()) + " chunks";
            case FOV:
                return String.format("%.0f deg", settings.getFov());
            case VSYNC:
                return settings.isVsync() ? "On" : "Off";
            case MOUSE_SENSITIVITY:
                return String.format("%.1f", settings.getMouseSensitivity());
            default:
                return "";
        }
    }
}
