package engine.audio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simplified SoundManager for scripting system integration
 */
public class SoundManager {
    private static SoundManager instance;
    private final Map<String, Object> sounds = new ConcurrentHashMap<>();
    private float masterVolume = 1.0f;
    private boolean muted = false;
    
    private SoundManager() {
        // Initialize sound system
    }
    
    public static SoundManager getInstance() {
        if (instance == null) {
            instance = new SoundManager();
        }
        return instance;
    }
    
    public void loadSound(String name, String path) {
        // Simplified sound loading
        sounds.put(name, "Sound[" + path + "]");
        System.out.println("Loaded sound: " + name + " from " + path);
    }
    
    public void playSound(String name) {
        if (!muted && sounds.containsKey(name)) {
            System.out.println("Playing sound: " + name + " (volume: " + masterVolume + ")");
        }
    }
    
    public void playSound(String name, float volume) {
        if (!muted && sounds.containsKey(name)) {
            float actualVolume = masterVolume * volume;
            System.out.println("Playing sound: " + name + " (volume: " + actualVolume + ")");
        }
    }
    
    public void playSound(String name, float volume, float pitch) {
        if (!muted && sounds.containsKey(name)) {
            float actualVolume = masterVolume * volume;
            System.out.println("Playing sound: " + name + " (volume: " + actualVolume + ", pitch: " + pitch + ")");
        }
    }
    
    public void stopSound(String name) {
        if (sounds.containsKey(name)) {
            System.out.println("Stopping sound: " + name);
        }
    }
    
    public void stopAllSounds() {
        System.out.println("Stopping all sounds");
    }
    
    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0.0f, Math.min(1.0f, volume));
        System.out.println("Master volume set to: " + this.masterVolume);
    }
    
    public float getMasterVolume() {
        return masterVolume;
    }
    
    public void setMuted(boolean muted) {
        this.muted = muted;
        System.out.println("Sound " + (muted ? "muted" : "unmuted"));
    }
    
    public boolean isMuted() {
        return muted;
    }
    
    public boolean hasSound(String name) {
        return sounds.containsKey(name);
    }
    
    public void unloadSound(String name) {
        sounds.remove(name);
        System.out.println("Unloaded sound: " + name);
    }
    
    public String[] getLoadedSounds() {
        return sounds.keySet().toArray(new String[0]);
    }
    
    public void cleanup() {
        sounds.clear();
        System.out.println("SoundManager cleaned up");
    }
}