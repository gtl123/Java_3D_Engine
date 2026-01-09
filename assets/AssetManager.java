package engine.assets;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simplified AssetManager for scripting system integration
 */
public class AssetManager {
    private static AssetManager instance;
    private final Map<String, Object> assets = new ConcurrentHashMap<>();
    
    private AssetManager() {
        // Initialize with some default assets
    }
    
    public static AssetManager getInstance() {
        if (instance == null) {
            instance = new AssetManager();
        }
        return instance;
    }
    
    public Object loadAsset(String path) {
        // Simplified asset loading - just return a placeholder
        Object asset = assets.get(path);
        if (asset == null) {
            asset = "Asset[" + path + "]";
            assets.put(path, asset);
        }
        return asset;
    }
    
    public Object getAsset(String path) {
        return assets.get(path);
    }
    
    public boolean hasAsset(String path) {
        return assets.containsKey(path);
    }
    
    public void unloadAsset(String path) {
        assets.remove(path);
    }
    
    public void preloadAsset(String path) {
        loadAsset(path);
    }
    
    public String[] getLoadedAssets() {
        return assets.keySet().toArray(new String[0]);
    }
    
    public void cleanup() {
        assets.clear();
    }
}