package engine.scripting;

import engine.assets.AssetManager;
import engine.audio.SoundManager;
import engine.config.ConfigurationManager;
import engine.io.Input;
import engine.logging.LogManager;
import engine.raster.Renderer;
import game.voxel.VoxelGame;

/**
 * Comprehensive engine API bindings for scripts.
 * Provides safe access to engine systems and game functionality.
 */
public class ScriptAPI {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Core engine systems
    private final LoggingAPI logging;
    private final ConfigAPI config;
    private final AssetAPI assets;
    private final AudioAPI audio;
    private final InputAPI input;
    private final RenderAPI render;
    private final GameAPI game;
    private final MathAPI math;
    private final UtilAPI util;
    
    public ScriptAPI() {
        this.logging = new LoggingAPI();
        this.config = new ConfigAPI();
        this.assets = new AssetAPI();
        this.audio = new AudioAPI();
        this.input = new InputAPI();
        this.render = new RenderAPI();
        this.game = new GameAPI();
        this.math = new MathAPI();
        this.util = new UtilAPI();
        
        logManager.debug("ScriptAPI", "Script API initialized");
    }
    
    /**
     * Get logging API for script logging.
     */
    public LoggingAPI getLogging() {
        return logging;
    }
    
    /**
     * Get configuration API for accessing engine settings.
     */
    public ConfigAPI getConfig() {
        return config;
    }
    
    /**
     * Get asset API for loading and managing assets.
     */
    public AssetAPI getAssets() {
        return assets;
    }
    
    /**
     * Get audio API for sound and music playback.
     */
    public AudioAPI getAudio() {
        return audio;
    }
    
    /**
     * Get input API for handling user input.
     */
    public InputAPI getInput() {
        return input;
    }
    
    /**
     * Get render API for graphics and rendering.
     */
    public RenderAPI getRender() {
        return render;
    }
    
    /**
     * Get game API for game-specific functionality.
     */
    public GameAPI getGame() {
        return game;
    }
    
    /**
     * Get math API for mathematical operations.
     */
    public MathAPI getMath() {
        return math;
    }
    
    /**
     * Get utility API for common operations.
     */
    public UtilAPI getUtil() {
        return util;
    }
    
    /**
     * Logging API for scripts.
     */
    public static class LoggingAPI {
        private final LogManager logManager = LogManager.getInstance();
        
        public void trace(String component, String message) {
            logManager.trace("Script." + component, message);
        }
        
        public void debug(String component, String message) {
            logManager.debug("Script." + component, message);
        }
        
        public void info(String component, String message) {
            logManager.info("Script." + component, message);
        }
        
        public void warn(String component, String message) {
            logManager.warn("Script." + component, message);
        }
        
        public void error(String component, String message) {
            logManager.error("Script." + component, message);
        }
        
        public void log(String level, String component, String message) {
            switch (level.toLowerCase()) {
                case "trace" -> trace(component, message);
                case "debug" -> debug(component, message);
                case "info" -> info(component, message);
                case "warn" -> warn(component, message);
                case "error" -> error(component, message);
                default -> info(component, message);
            }
        }
    }
    
    /**
     * Configuration API for scripts.
     */
    public static class ConfigAPI {
        private final ConfigurationManager configManager = ConfigurationManager.getInstance();
        
        public Object getValue(String key) {
            return configManager.getValue(key).orElse(null);
        }
        
        public Object getValue(String key, Object defaultValue) {
            return configManager.getValue(key, defaultValue);
        }
        
        public String getString(String key) {
            return configManager.getValue(key, "");
        }
        
        public String getString(String key, String defaultValue) {
            return configManager.getValue(key, defaultValue);
        }
        
        public int getInt(String key) {
            return configManager.getValue(key, 0);
        }
        
        public int getInt(String key, int defaultValue) {
            return configManager.getValue(key, defaultValue);
        }
        
        public float getFloat(String key) {
            return configManager.getValue(key, 0.0f);
        }
        
        public float getFloat(String key, float defaultValue) {
            return configManager.getValue(key, defaultValue);
        }
        
        public boolean getBoolean(String key) {
            return configManager.getValue(key, false);
        }
        
        public boolean getBoolean(String key, boolean defaultValue) {
            return configManager.getValue(key, defaultValue);
        }
        
        public boolean isFeatureEnabled(String featureName) {
            return configManager.getFeatureFlags().isEnabled(featureName);
        }
    }
    
    /**
     * Asset API for scripts.
     */
    public static class AssetAPI {
        private final AssetManager assetManager = AssetManager.getInstance();
        
        public boolean loadAsset(String path) {
            try {
                assetManager.loadAsset(path);
                return true;
            } catch (Exception e) {
                logManager.error("ScriptAPI.AssetAPI", "Failed to load asset", "path", path, "error", e.getMessage());
                return false;
            }
        }
        
        public boolean isAssetLoaded(String assetId) {
            return assetManager.getAsset(assetId) != null;
        }
        
        public boolean unloadAsset(String assetId) {
            return assetManager.unloadAsset(assetId);
        }
        
        public String getAssetPath(String assetId) {
            var asset = assetManager.getAsset(assetId);
            return asset != null ? asset.getPath() : null;
        }
        
        public long getAssetSize(String assetId) {
            var asset = assetManager.getAsset(assetId);
            return asset != null ? asset.getSize() : 0;
        }
    }
    
    /**
     * Audio API for scripts.
     */
    public static class AudioAPI {
        private SoundManager soundManager;
        
        public AudioAPI() {
            // SoundManager would be injected or retrieved from engine
            // For now, we'll handle it gracefully if not available
        }
        
        public boolean playSound(String soundId) {
            if (soundManager != null) {
                try {
                    // soundManager.playSound(soundId);
                    logManager.debug("ScriptAPI.AudioAPI", "Playing sound", "soundId", soundId);
                    return true;
                } catch (Exception e) {
                    logManager.error("ScriptAPI.AudioAPI", "Failed to play sound", "soundId", soundId, "error", e.getMessage());
                    return false;
                }
            }
            return false;
        }
        
        public boolean stopSound(String soundId) {
            if (soundManager != null) {
                try {
                    // soundManager.stopSound(soundId);
                    logManager.debug("ScriptAPI.AudioAPI", "Stopping sound", "soundId", soundId);
                    return true;
                } catch (Exception e) {
                    logManager.error("ScriptAPI.AudioAPI", "Failed to stop sound", "soundId", soundId, "error", e.getMessage());
                    return false;
                }
            }
            return false;
        }
        
        public void setVolume(float volume) {
            if (soundManager != null) {
                // soundManager.setMasterVolume(volume);
                logManager.debug("ScriptAPI.AudioAPI", "Setting volume", "volume", volume);
            }
        }
    }
    
    /**
     * Input API for scripts.
     */
    public static class InputAPI {
        
        public boolean isKeyPressed(int keyCode) {
            return Input.isKeyPressed(keyCode);
        }
        
        public boolean isMouseButtonPressed(int button) {
            return Input.isMouseButtonPressed(button);
        }
        
        public float getMouseX() {
            return Input.getMouseX();
        }
        
        public float getMouseY() {
            return Input.getMouseY();
        }
        
        public float getMouseDeltaX() {
            return Input.getMouseDeltaX();
        }
        
        public float getMouseDeltaY() {
            return Input.getMouseDeltaY();
        }
        
        public boolean wasKeyJustPressed(int keyCode) {
            // This would need to be implemented in the Input class
            return Input.isKeyPressed(keyCode);
        }
        
        public boolean wasKeyJustReleased(int keyCode) {
            // This would need to be implemented in the Input class
            return !Input.isKeyPressed(keyCode);
        }
    }
    
    /**
     * Render API for scripts.
     */
    public static class RenderAPI {
        
        public int getScreenWidth() {
            // Would get from renderer or window
            return 1920; // Default value
        }
        
        public int getScreenHeight() {
            // Would get from renderer or window
            return 1080; // Default value
        }
        
        public float getFPS() {
            // Would get from performance monitor
            return 60.0f; // Default value
        }
        
        public void setRenderDistance(float distance) {
            logManager.debug("ScriptAPI.RenderAPI", "Setting render distance", "distance", distance);
            // Would set in renderer
        }
        
        public void enableWireframe(boolean enable) {
            logManager.debug("ScriptAPI.RenderAPI", "Wireframe mode", "enabled", enable);
            // Would set in renderer
        }
    }
    
    /**
     * Game API for scripts.
     */
    public static class GameAPI {
        private VoxelGame voxelGame;
        
        public void setVoxelGame(VoxelGame game) {
            this.voxelGame = game;
        }
        
        public boolean isGameRunning() {
            return voxelGame != null;
        }
        
        public void pauseGame() {
            if (voxelGame != null) {
                logManager.debug("ScriptAPI.GameAPI", "Pausing game");
                // voxelGame.pause();
            }
        }
        
        public void resumeGame() {
            if (voxelGame != null) {
                logManager.debug("ScriptAPI.GameAPI", "Resuming game");
                // voxelGame.resume();
            }
        }
        
        public void saveGame(String saveName) {
            if (voxelGame != null) {
                logManager.debug("ScriptAPI.GameAPI", "Saving game", "saveName", saveName);
                // voxelGame.save(saveName);
            }
        }
        
        public void loadGame(String saveName) {
            if (voxelGame != null) {
                logManager.debug("ScriptAPI.GameAPI", "Loading game", "saveName", saveName);
                // voxelGame.load(saveName);
            }
        }
        
        public void setBlock(int x, int y, int z, int blockType) {
            if (voxelGame != null) {
                logManager.debug("ScriptAPI.GameAPI", "Setting block", 
                               "x", x, "y", y, "z", z, "type", blockType);
                // voxelGame.setBlock(x, y, z, blockType);
            }
        }
        
        public int getBlock(int x, int y, int z) {
            if (voxelGame != null) {
                // return voxelGame.getBlock(x, y, z);
                return 0; // Default air block
            }
            return 0;
        }
    }
    
    /**
     * Math API for scripts.
     */
    public static class MathAPI {
        
        public float sin(float angle) {
            return (float) Math.sin(angle);
        }
        
        public float cos(float angle) {
            return (float) Math.cos(angle);
        }
        
        public float tan(float angle) {
            return (float) Math.tan(angle);
        }
        
        public float sqrt(float value) {
            return (float) Math.sqrt(value);
        }
        
        public float pow(float base, float exponent) {
            return (float) Math.pow(base, exponent);
        }
        
        public float abs(float value) {
            return Math.abs(value);
        }
        
        public float min(float a, float b) {
            return Math.min(a, b);
        }
        
        public float max(float a, float b) {
            return Math.max(a, b);
        }
        
        public float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }
        
        public float lerp(float a, float b, float t) {
            return a + t * (b - a);
        }
        
        public float random() {
            return (float) Math.random();
        }
        
        public int randomInt(int min, int max) {
            return min + (int) (Math.random() * (max - min + 1));
        }
        
        public float distance(float x1, float y1, float x2, float y2) {
            float dx = x2 - x1;
            float dy = y2 - y1;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }
        
        public float distance3D(float x1, float y1, float z1, float x2, float y2, float z2) {
            float dx = x2 - x1;
            float dy = y2 - y1;
            float dz = z2 - z1;
            return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        
        public float toDegrees(float radians) {
            return (float) Math.toDegrees(radians);
        }
        
        public float toRadians(float degrees) {
            return (float) Math.toRadians(degrees);
        }
    }
    
    /**
     * Utility API for scripts.
     */
    public static class UtilAPI {
        
        public long getCurrentTime() {
            return System.currentTimeMillis();
        }
        
        public long getNanoTime() {
            return System.nanoTime();
        }
        
        public void sleep(long milliseconds) {
            try {
                Thread.sleep(milliseconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        public String formatString(String format, Object... args) {
            return String.format(format, args);
        }
        
        public String[] splitString(String str, String delimiter) {
            return str.split(delimiter);
        }
        
        public String joinStrings(String[] strings, String delimiter) {
            return String.join(delimiter, strings);
        }
        
        public boolean isEmpty(String str) {
            return str == null || str.trim().isEmpty();
        }
        
        public String trim(String str) {
            return str != null ? str.trim() : "";
        }
        
        public String toLowerCase(String str) {
            return str != null ? str.toLowerCase() : "";
        }
        
        public String toUpperCase(String str) {
            return str != null ? str.toUpperCase() : "";
        }
    }
}