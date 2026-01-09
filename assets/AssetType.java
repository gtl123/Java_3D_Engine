package engine.assets;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enumeration of supported asset types in the engine.
 * Each type defines file extensions, loading characteristics, and processing requirements.
 */
public enum AssetType {
    
    TEXTURE("texture", new String[]{".png", ".jpg", ".jpeg", ".dds", ".ktx", ".tga"}, true, false),
    MODEL("model", new String[]{".obj", ".fbx", ".gltf", ".glb", ".dae"}, false, true),
    AUDIO("audio", new String[]{".wav", ".ogg", ".mp3", ".flac"}, true, true),
    SHADER("shader", new String[]{".vs", ".fs", ".gs", ".tcs", ".tes", ".comp", ".glsl"}, false, false),
    CONFIG("config", new String[]{".properties", ".json", ".yaml", ".yml", ".xml"}, false, false),
    SCRIPT("script", new String[]{".js", ".lua", ".py"}, false, false),
    FONT("font", new String[]{".ttf", ".otf", ".woff", ".woff2"}, false, false),
    ANIMATION("animation", new String[]{".anim", ".fbx", ".dae"}, false, true),
    MATERIAL("material", new String[]{".mat", ".json"}, false, false),
    SCENE("scene", new String[]{".scene", ".json", ".xml"}, false, true),
    BINARY("binary", new String[]{".bin", ".dat", ".cache"}, true, true),
    UNKNOWN("unknown", new String[0], false, false);
    
    private final String typeName;
    private final Set<String> extensions;
    private final boolean supportsCompression;
    private final boolean supportsStreaming;
    
    AssetType(String typeName, String[] extensions, boolean supportsCompression, boolean supportsStreaming) {
        this.typeName = typeName;
        this.extensions = Arrays.stream(extensions)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        this.supportsCompression = supportsCompression;
        this.supportsStreaming = supportsStreaming;
    }
    
    /**
     * Get the type name.
     * @return Type name
     */
    public String getTypeName() {
        return typeName;
    }
    
    /**
     * Get supported file extensions for this asset type.
     * @return Set of file extensions (including the dot)
     */
    public Set<String> getExtensions() {
        return extensions;
    }
    
    /**
     * Check if this asset type supports compression.
     * @return true if compression is supported
     */
    public boolean supportsCompression() {
        return supportsCompression;
    }
    
    /**
     * Check if this asset type supports streaming.
     * @return true if streaming is supported
     */
    public boolean supportsStreaming() {
        return supportsStreaming;
    }
    
    /**
     * Check if the given file extension is supported by this asset type.
     * @param extension File extension (with or without dot)
     * @return true if supported
     */
    public boolean supportsExtension(String extension) {
        if (extension == null) return false;
        
        String normalizedExt = extension.toLowerCase();
        if (!normalizedExt.startsWith(".")) {
            normalizedExt = "." + normalizedExt;
        }
        
        return extensions.contains(normalizedExt);
    }
    
    /**
     * Determine asset type from file path/name.
     * @param filePath File path or name
     * @return Detected asset type, or UNKNOWN if not recognized
     */
    public static AssetType fromFilePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return UNKNOWN;
        }
        
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filePath.length() - 1) {
            return UNKNOWN;
        }
        
        String extension = filePath.substring(lastDot).toLowerCase();
        
        for (AssetType type : values()) {
            if (type != UNKNOWN && type.supportsExtension(extension)) {
                return type;
            }
        }
        
        return UNKNOWN;
    }
    
    /**
     * Get asset type by name.
     * @param typeName Type name
     * @return Asset type, or UNKNOWN if not found
     */
    public static AssetType fromTypeName(String typeName) {
        if (typeName == null) return UNKNOWN;
        
        for (AssetType type : values()) {
            if (type.typeName.equalsIgnoreCase(typeName)) {
                return type;
            }
        }
        
        return UNKNOWN;
    }
    
    /**
     * Get the default priority for loading this asset type.
     * Higher values indicate higher priority.
     * @return Loading priority (0-100)
     */
    public int getLoadingPriority() {
        switch (this) {
            case SHADER:
                return 90; // Shaders needed for rendering
            case TEXTURE:
                return 80; // Textures needed for most rendering
            case CONFIG:
                return 70; // Configuration affects other loading
            case MATERIAL:
                return 60; // Materials depend on shaders/textures
            case MODEL:
                return 50; // Models are content
            case AUDIO:
                return 40; // Audio can be loaded later
            case FONT:
                return 30; // Fonts for UI
            case ANIMATION:
                return 20; // Animations are secondary
            case SCRIPT:
                return 10; // Scripts can be loaded on-demand
            case SCENE:
                return 5;  // Scenes are composite
            default:
                return 1;  // Unknown/binary assets lowest priority
        }
    }
    
    /**
     * Get the estimated memory multiplier for this asset type.
     * Used for cache size estimation.
     * @return Memory multiplier (1.0 = file size, >1.0 = expanded in memory)
     */
    public float getMemoryMultiplier() {
        switch (this) {
            case TEXTURE:
                return 4.0f; // Uncompressed RGBA textures
            case MODEL:
                return 2.0f; // Vertex data expansion
            case AUDIO:
                return 1.5f; // Decompressed audio
            case SHADER:
                return 1.2f; // Compiled shader overhead
            case FONT:
                return 3.0f; // Font atlas generation
            default:
                return 1.0f; // Assume file size
        }
    }
}