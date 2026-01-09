package fps.core;

import engine.logging.LogManager;

/**
 * Game settings configuration for the FPS game.
 * Handles gameplay, graphics, audio, and control settings.
 */
public class GameSettings {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Gameplay Settings
    private float mouseSensitivity = 1.0f;
    private boolean invertMouseY = false;
    private float fieldOfView = 90.0f;
    private boolean autoReload = true;
    private boolean toggleADS = false; // Aim Down Sights
    private boolean toggleCrouch = false;
    
    // Graphics Settings
    private int targetFPS = 144;
    private boolean vsyncEnabled = false;
    private int renderDistance = 1000;
    private float brightnessLevel = 1.0f;
    private boolean showFPS = true;
    private boolean showNetworkStats = false;
    
    // Audio Settings
    private float masterVolume = 1.0f;
    private float effectsVolume = 1.0f;
    private float musicVolume = 0.7f;
    private float voiceChatVolume = 1.0f;
    private boolean audioEnabled = true;
    private boolean positionalAudio = true;
    
    // Network Settings
    private int maxPing = 100;
    private String preferredRegion = "auto";
    private boolean lagCompensation = true;
    private int clientUpdateRate = 64; // Hz
    private int serverTickRate = 128; // Hz
    
    // Performance Settings
    private boolean performanceMonitoring = true;
    private boolean memoryOptimization = true;
    private int maxConcurrentSounds = 32;
    private boolean asyncAssetLoading = true;
    
    // Accessibility Settings
    private boolean colorBlindSupport = false;
    private float uiScale = 1.0f;
    private boolean subtitlesEnabled = false;
    private boolean reducedMotion = false;
    
    public GameSettings() {
        logManager.info("GameSettings", "Game settings created");
    }
    
    /**
     * Load default settings
     */
    public void loadDefaults() {
        logManager.info("GameSettings", "Loading default settings");
        
        // Settings are already initialized with defaults
        // This method can be extended to load from config files
        
        validateSettings();
    }
    
    /**
     * Validate all settings are within acceptable ranges
     */
    public void validateSettings() {
        // Clamp values to acceptable ranges
        mouseSensitivity = Math.max(0.1f, Math.min(10.0f, mouseSensitivity));
        fieldOfView = Math.max(60.0f, Math.min(120.0f, fieldOfView));
        targetFPS = Math.max(30, Math.min(300, targetFPS));
        renderDistance = Math.max(100, Math.min(5000, renderDistance));
        brightnessLevel = Math.max(0.1f, Math.min(2.0f, brightnessLevel));
        
        // Audio volumes
        masterVolume = Math.max(0.0f, Math.min(1.0f, masterVolume));
        effectsVolume = Math.max(0.0f, Math.min(1.0f, effectsVolume));
        musicVolume = Math.max(0.0f, Math.min(1.0f, musicVolume));
        voiceChatVolume = Math.max(0.0f, Math.min(1.0f, voiceChatVolume));
        
        // Network settings
        maxPing = Math.max(50, Math.min(500, maxPing));
        clientUpdateRate = Math.max(20, Math.min(128, clientUpdateRate));
        serverTickRate = Math.max(20, Math.min(128, serverTickRate));
        
        // Performance settings
        maxConcurrentSounds = Math.max(8, Math.min(128, maxConcurrentSounds));
        uiScale = Math.max(0.5f, Math.min(2.0f, uiScale));
        
        logManager.debug("GameSettings", "Settings validated");
    }
    
    /**
     * Apply settings to relevant systems
     */
    public void applySettings() {
        logManager.info("GameSettings", "Applying settings to systems");
        
        // This method would notify various systems of setting changes
        // For now, it's a placeholder for future implementation
        
        validateSettings();
    }
    
    // Gameplay Settings Getters/Setters
    public float getMouseSensitivity() { return mouseSensitivity; }
    public void setMouseSensitivity(float mouseSensitivity) {
        this.mouseSensitivity = mouseSensitivity;
        validateSettings();
    }
    
    public boolean isInvertMouseY() { return invertMouseY; }
    public void setInvertMouseY(boolean invertMouseY) { this.invertMouseY = invertMouseY; }
    
    public float getFieldOfView() { return fieldOfView; }
    public void setFieldOfView(float fieldOfView) {
        this.fieldOfView = fieldOfView;
        validateSettings();
    }
    
    public boolean isAutoReload() { return autoReload; }
    public void setAutoReload(boolean autoReload) { this.autoReload = autoReload; }
    
    public boolean isToggleADS() { return toggleADS; }
    public void setToggleADS(boolean toggleADS) { this.toggleADS = toggleADS; }
    
    public boolean isToggleCrouch() { return toggleCrouch; }
    public void setToggleCrouch(boolean toggleCrouch) { this.toggleCrouch = toggleCrouch; }
    
    // Graphics Settings Getters/Setters
    public int getTargetFPS() { return targetFPS; }
    public void setTargetFPS(int targetFPS) {
        this.targetFPS = targetFPS;
        validateSettings();
    }
    
    public boolean isVsyncEnabled() { return vsyncEnabled; }
    public void setVsyncEnabled(boolean vsyncEnabled) { this.vsyncEnabled = vsyncEnabled; }
    
    public int getRenderDistance() { return renderDistance; }
    public void setRenderDistance(int renderDistance) {
        this.renderDistance = renderDistance;
        validateSettings();
    }
    
    public float getBrightnessLevel() { return brightnessLevel; }
    public void setBrightnessLevel(float brightnessLevel) {
        this.brightnessLevel = brightnessLevel;
        validateSettings();
    }
    
    public boolean isShowFPS() { return showFPS; }
    public void setShowFPS(boolean showFPS) { this.showFPS = showFPS; }
    
    public boolean isShowNetworkStats() { return showNetworkStats; }
    public void setShowNetworkStats(boolean showNetworkStats) { this.showNetworkStats = showNetworkStats; }
    
    // Audio Settings Getters/Setters
    public float getMasterVolume() { return masterVolume; }
    public void setMasterVolume(float masterVolume) {
        this.masterVolume = masterVolume;
        validateSettings();
    }
    
    public float getEffectsVolume() { return effectsVolume; }
    public void setEffectsVolume(float effectsVolume) {
        this.effectsVolume = effectsVolume;
        validateSettings();
    }
    
    public float getMusicVolume() { return musicVolume; }
    public void setMusicVolume(float musicVolume) {
        this.musicVolume = musicVolume;
        validateSettings();
    }
    
    public float getVoiceChatVolume() { return voiceChatVolume; }
    public void setVoiceChatVolume(float voiceChatVolume) {
        this.voiceChatVolume = voiceChatVolume;
        validateSettings();
    }
    
    public boolean isAudioEnabled() { return audioEnabled; }
    public void setAudioEnabled(boolean audioEnabled) { this.audioEnabled = audioEnabled; }
    
    public boolean isPositionalAudio() { return positionalAudio; }
    public void setPositionalAudio(boolean positionalAudio) { this.positionalAudio = positionalAudio; }
    
    // Network Settings Getters/Setters
    public int getMaxPing() { return maxPing; }
    public void setMaxPing(int maxPing) {
        this.maxPing = maxPing;
        validateSettings();
    }
    
    public String getPreferredRegion() { return preferredRegion; }
    public void setPreferredRegion(String preferredRegion) { this.preferredRegion = preferredRegion; }
    
    public boolean isLagCompensation() { return lagCompensation; }
    public void setLagCompensation(boolean lagCompensation) { this.lagCompensation = lagCompensation; }
    
    public int getClientUpdateRate() { return clientUpdateRate; }
    public void setClientUpdateRate(int clientUpdateRate) {
        this.clientUpdateRate = clientUpdateRate;
        validateSettings();
    }
    
    public int getServerTickRate() { return serverTickRate; }
    public void setServerTickRate(int serverTickRate) {
        this.serverTickRate = serverTickRate;
        validateSettings();
    }
    
    // Performance Settings Getters/Setters
    public boolean isPerformanceMonitoring() { return performanceMonitoring; }
    public void setPerformanceMonitoring(boolean performanceMonitoring) { this.performanceMonitoring = performanceMonitoring; }
    
    public boolean isMemoryOptimization() { return memoryOptimization; }
    public void setMemoryOptimization(boolean memoryOptimization) { this.memoryOptimization = memoryOptimization; }
    
    public int getMaxConcurrentSounds() { return maxConcurrentSounds; }
    public void setMaxConcurrentSounds(int maxConcurrentSounds) {
        this.maxConcurrentSounds = maxConcurrentSounds;
        validateSettings();
    }
    
    public boolean isAsyncAssetLoading() { return asyncAssetLoading; }
    public void setAsyncAssetLoading(boolean asyncAssetLoading) { this.asyncAssetLoading = asyncAssetLoading; }
    
    // Accessibility Settings Getters/Setters
    public boolean isColorBlindSupport() { return colorBlindSupport; }
    public void setColorBlindSupport(boolean colorBlindSupport) { this.colorBlindSupport = colorBlindSupport; }
    
    public float getUiScale() { return uiScale; }
    public void setUiScale(float uiScale) {
        this.uiScale = uiScale;
        validateSettings();
    }
    
    public boolean isSubtitlesEnabled() { return subtitlesEnabled; }
    public void setSubtitlesEnabled(boolean subtitlesEnabled) { this.subtitlesEnabled = subtitlesEnabled; }
    
    public boolean isReducedMotion() { return reducedMotion; }
    public void setReducedMotion(boolean reducedMotion) { this.reducedMotion = reducedMotion; }
}