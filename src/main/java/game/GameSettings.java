package game;

import java.io.*;
import java.util.Properties;

/**
 * Manages persistent game settings stored in settings.properties.
 * Settings include render distance, FOV, vsync, and mouse sensitivity.
 */
public class GameSettings {
    private static final String SETTINGS_FILE = "settings.properties";

    // Default values
    private int renderDistance = 8;
    private float fov = 70.0f;
    private boolean vsync = true;
    private float mouseSensitivity = 1.0f;

    public GameSettings() {
        load();
    }

    /**
     * Load settings from file. If file doesn't exist, use defaults.
     */
    public void load() {
        File file = new File(SETTINGS_FILE);
        if (!file.exists()) {
            System.out.println("Settings file not found, using defaults");
            return;
        }

        try (InputStream input = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(input);

            renderDistance = Integer.parseInt(props.getProperty("renderDistance", "8"));
            fov = Float.parseFloat(props.getProperty("fov", "70.0"));
            vsync = Boolean.parseBoolean(props.getProperty("vsync", "true"));
            mouseSensitivity = Float.parseFloat(props.getProperty("mouseSensitivity", "1.0"));

            System.out.println("Settings loaded successfully");
        } catch (IOException | NumberFormatException e) {
            System.err.println("Error loading settings, using defaults: " + e.getMessage());
        }
    }

    /**
     * Save current settings to file.
     */
    public void save() {
        try (OutputStream output = new FileOutputStream(SETTINGS_FILE)) {
            Properties props = new Properties();
            props.setProperty("renderDistance", String.valueOf(renderDistance));
            props.setProperty("fov", String.valueOf(fov));
            props.setProperty("vsync", String.valueOf(vsync));
            props.setProperty("mouseSensitivity", String.valueOf(mouseSensitivity));

            props.store(output, "Game Settings");
            System.out.println("Settings saved successfully");
        } catch (IOException e) {
            System.err.println("Error saving settings: " + e.getMessage());
        }
    }

    // Getters
    public int getRenderDistance() {
        return renderDistance;
    }

    public float getFov() {
        return fov;
    }

    public boolean isVsync() {
        return vsync;
    }

    public float getMouseSensitivity() {
        return mouseSensitivity;
    }

    // Setters with validation
    public void setRenderDistance(int distance) {
        this.renderDistance = Math.max(4, Math.min(16, distance));
    }

    public void setFov(float fov) {
        this.fov = Math.max(60.0f, Math.min(110.0f, fov));
    }

    public void setVsync(boolean vsync) {
        this.vsync = vsync;
    }

    public void setMouseSensitivity(float sensitivity) {
        this.mouseSensitivity = Math.max(0.1f, Math.min(2.0f, sensitivity));
    }
}
