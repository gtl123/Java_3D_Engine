package engine.rendering.advanced;

import engine.assets.AssetManager;
import engine.config.ConfigurationManager;
import engine.logging.LogManager;
import engine.logging.MetricsCollector;
import org.joml.Vector2f;
import org.joml.Vector2i;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Advanced Texture Atlas Manager for efficient texture management and atlasing.
 * Provides automatic texture packing, atlas generation, and runtime texture streaming.
 * Supports multiple atlas types and dynamic texture loading/unloading.
 */
public class TextureAtlasManager {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = LogManager.getInstance().getMetricsCollector();
    private static final ConfigurationManager configManager = ConfigurationManager.getInstance();
    private static final AssetManager assetManager = AssetManager.getInstance();
    
    // Configuration
    private final int maxAtlasSize;
    private final int defaultAtlasSize;
    private final boolean enableMipmaps;
    private final boolean enableAnisotropicFiltering;
    private final float maxAnisotropy;
    
    // Atlas management
    private final Map<String, TextureAtlas> atlases = new ConcurrentHashMap<>();
    private final Map<String, AtlasEntry> textureEntries = new ConcurrentHashMap<>();
    private final Map<AtlasType, List<String>> atlasByType = new ConcurrentHashMap<>();
    
    // Texture loading
    private final Set<String> loadingTextures = ConcurrentHashMap.newKeySet();
    private final Queue<String> loadQueue = new LinkedList<>();
    
    // Performance tracking
    private int totalTexturesLoaded = 0;
    private long totalMemoryUsed = 0;
    private int atlasesCreated = 0;
    
    /**
     * Atlas types for different texture categories.
     */
    public enum AtlasType {
        DIFFUSE("diffuse", GL_RGBA8, true),
        NORMAL("normal", GL_RGBA8, false),
        METALLIC_ROUGHNESS("metallic_roughness", GL_RG8, false),
        EMISSIVE("emissive", GL_RGB16F, true),
        UI("ui", GL_RGBA8, true),
        TERRAIN("terrain", GL_RGBA8, true);
        
        private final String name;
        private final int internalFormat;
        private final boolean srgb;
        
        AtlasType(String name, int internalFormat, boolean srgb) {
            this.name = name;
            this.internalFormat = internalFormat;
            this.srgb = srgb;
        }
        
        public String getName() { return name; }
        public int getInternalFormat() { return internalFormat; }
        public boolean isSrgb() { return srgb; }
    }
    
    /**
     * Represents a texture atlas containing multiple packed textures.
     */
    public static class TextureAtlas {
        private final String name;
        private final AtlasType type;
        private final int size;
        private final int textureId;
        private final TexturePacker packer;
        
        private boolean finalized = false;
        private int textureCount = 0;
        private long memoryUsage = 0;
        
        public TextureAtlas(String name, AtlasType type, int size) {
            this.name = name;
            this.type = type;
            this.size = size;
            this.packer = new TexturePacker(size, size);
            
            // Create OpenGL texture
            this.textureId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureId);
            
            // Allocate texture storage
            int internalFormat = type.isSrgb() ? GL_SRGB8_ALPHA8 : type.getInternalFormat();
            glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, size, size, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
            
            // Set texture parameters
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            
            glBindTexture(GL_TEXTURE_2D, 0);
            
            this.memoryUsage = calculateMemoryUsage(size, size, internalFormat);
        }
        
        private long calculateMemoryUsage(int width, int height, int internalFormat) {
            int bytesPerPixel = 4; // Default to RGBA
            switch (internalFormat) {
                case GL_R8: bytesPerPixel = 1; break;
                case GL_RG8: bytesPerPixel = 2; break;
                case GL_RGB8: bytesPerPixel = 3; break;
                case GL_RGBA8:
                case GL_SRGB8_ALPHA8: bytesPerPixel = 4; break;
                case GL_RGB16F: bytesPerPixel = 6; break;
                case GL_RGBA16F: bytesPerPixel = 8; break;
            }
            
            // Include mipmaps (adds ~33% more memory)
            long baseSize = (long) width * height * bytesPerPixel;
            return (long) (baseSize * 1.33f);
        }
        
        public boolean addTexture(String textureName, ByteBuffer textureData, int width, int height) {
            if (finalized) return false;
            
            TexturePacker.PackResult result = packer.pack(width, height);
            if (result == null) return false;
            
            // Upload texture data to atlas
            glBindTexture(GL_TEXTURE_2D, textureId);
            glTexSubImage2D(GL_TEXTURE_2D, 0, result.x, result.y, width, height, 
                           GL_RGBA, GL_UNSIGNED_BYTE, textureData);
            glBindTexture(GL_TEXTURE_2D, 0);
            
            textureCount++;
            return true;
        }
        
        public void finalize() {
            if (finalized) return;
            
            // Generate mipmaps
            glBindTexture(GL_TEXTURE_2D, textureId);
            glGenerateMipmap(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, 0);
            
            finalized = true;
        }
        
        public void cleanup() {
            if (textureId != 0) {
                glDeleteTextures(textureId);
            }
        }
        
        // Getters
        public String getName() { return name; }
        public AtlasType getType() { return type; }
        public int getSize() { return size; }
        public int getTextureId() { return textureId; }
        public boolean isFinalized() { return finalized; }
        public int getTextureCount() { return textureCount; }
        public long getMemoryUsage() { return memoryUsage; }
        public float getUtilization() { return packer.getUtilization(); }
    }
    
    /**
     * Represents an entry in a texture atlas.
     */
    public static class AtlasEntry {
        private final String textureName;
        private final String atlasName;
        private final Vector2f uvMin;
        private final Vector2f uvMax;
        private final Vector2i originalSize;
        
        public AtlasEntry(String textureName, String atlasName, Vector2f uvMin, Vector2f uvMax, Vector2i originalSize) {
            this.textureName = textureName;
            this.atlasName = atlasName;
            this.uvMin = new Vector2f(uvMin);
            this.uvMax = new Vector2f(uvMax);
            this.originalSize = new Vector2i(originalSize);
        }
        
        // Getters
        public String getTextureName() { return textureName; }
        public String getAtlasName() { return atlasName; }
        public Vector2f getUvMin() { return new Vector2f(uvMin); }
        public Vector2f getUvMax() { return new Vector2f(uvMax); }
        public Vector2i getOriginalSize() { return new Vector2i(originalSize); }
        public Vector2f getUvSize() { return new Vector2f(uvMax).sub(uvMin); }
    }
    
    /**
     * Simple texture packer using shelf-based algorithm.
     */
    private static class TexturePacker {
        private final int atlasWidth;
        private final int atlasHeight;
        private final List<Shelf> shelves = new ArrayList<>();
        private int usedArea = 0;
        
        private static class Shelf {
            int y;
            int height;
            int currentX;
            
            Shelf(int y, int height) {
                this.y = y;
                this.height = height;
                this.currentX = 0;
            }
            
            boolean canFit(int width, int height, int atlasWidth) {
                return height <= this.height && currentX + width <= atlasWidth;
            }
            
            PackResult allocate(int width, int height) {
                PackResult result = new PackResult(currentX, y, width, height);
                currentX += width;
                return result;
            }
        }
        
        private static class PackResult {
            final int x, y, width, height;
            
            PackResult(int x, int y, int width, int height) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
            }
        }
        
        public TexturePacker(int width, int height) {
            this.atlasWidth = width;
            this.atlasHeight = height;
        }
        
        public PackResult pack(int width, int height) {
            // Add padding to prevent bleeding
            int paddedWidth = width + 2;
            int paddedHeight = height + 2;
            
            // Try to fit in existing shelves
            for (Shelf shelf : shelves) {
                if (shelf.canFit(paddedWidth, paddedHeight, atlasWidth)) {
                    PackResult result = shelf.allocate(paddedWidth, paddedHeight);
                    usedArea += paddedWidth * paddedHeight;
                    return new PackResult(result.x + 1, result.y + 1, width, height); // Remove padding from result
                }
            }
            
            // Create new shelf
            int shelfY = shelves.isEmpty() ? 0 : 
                       shelves.get(shelves.size() - 1).y + shelves.get(shelves.size() - 1).height;
            
            if (shelfY + paddedHeight > atlasHeight) {
                return null; // No space
            }
            
            Shelf newShelf = new Shelf(shelfY, paddedHeight);
            if (newShelf.canFit(paddedWidth, paddedHeight, atlasWidth)) {
                shelves.add(newShelf);
                PackResult result = newShelf.allocate(paddedWidth, paddedHeight);
                usedArea += paddedWidth * paddedHeight;
                return new PackResult(result.x + 1, result.y + 1, width, height); // Remove padding from result
            }
            
            return null; // No space
        }
        
        public float getUtilization() {
            return (float) usedArea / (atlasWidth * atlasHeight);
        }
    }
    
    /**
     * Initialize the texture atlas manager.
     */
    public TextureAtlasManager() {
        // Load configuration
        this.maxAtlasSize = configManager.getInt("rendering.atlas.maxSize", 4096);
        this.defaultAtlasSize = configManager.getInt("rendering.atlas.defaultSize", 2048);
        this.enableMipmaps = configManager.getBoolean("rendering.atlas.mipmaps", true);
        this.enableAnisotropicFiltering = configManager.getBoolean("rendering.atlas.anisotropic", true);
        this.maxAnisotropy = configManager.getFloat("rendering.atlas.maxAnisotropy", 16.0f);
        
        // Initialize atlas lists for each type
        for (AtlasType type : AtlasType.values()) {
            atlasByType.put(type, new ArrayList<>());
        }
        
        logManager.info("TextureAtlasManager", "Texture atlas manager initialized",
                       "maxAtlasSize", maxAtlasSize,
                       "defaultAtlasSize", defaultAtlasSize,
                       "mipmaps", enableMipmaps);
    }
    
    /**
     * Load a texture into an appropriate atlas.
     */
    public boolean loadTexture(String texturePath, AtlasType type) {
        if (textureEntries.containsKey(texturePath)) {
            return true; // Already loaded
        }
        
        if (loadingTextures.contains(texturePath)) {
            return false; // Currently loading
        }
        
        loadingTextures.add(texturePath);
        
        try {
            // Load texture data using AssetManager
            // This is a simplified version - actual implementation would use AssetManager
            ByteBuffer textureData = loadTextureData(texturePath);
            if (textureData == null) {
                logManager.warn("TextureAtlasManager", "Failed to load texture data", "path", texturePath);
                return false;
            }
            
            // Get texture dimensions (would be provided by actual texture loader)
            Vector2i dimensions = getTextureDimensions(texturePath);
            
            // Find or create suitable atlas
            TextureAtlas atlas = findOrCreateAtlas(type, dimensions.x, dimensions.y);
            if (atlas == null) {
                logManager.error("TextureAtlasManager", "No suitable atlas found for texture", "path", texturePath);
                return false;
            }
            
            // Add texture to atlas
            if (atlas.addTexture(texturePath, textureData, dimensions.x, dimensions.y)) {
                // Calculate UV coordinates
                Vector2f uvMin = new Vector2f(); // Would be calculated from packer result
                Vector2f uvMax = new Vector2f(); // Would be calculated from packer result
                
                AtlasEntry entry = new AtlasEntry(texturePath, atlas.getName(), uvMin, uvMax, dimensions);
                textureEntries.put(texturePath, entry);
                
                totalTexturesLoaded++;
                metricsCollector.incrementCounter("textureAtlas.texturesLoaded");
                
                logManager.debug("TextureAtlasManager", "Texture loaded into atlas",
                               "texture", texturePath, "atlas", atlas.getName());
                
                return true;
            }
            
        } catch (Exception e) {
            logManager.error("TextureAtlasManager", "Error loading texture", e, "path", texturePath);
        } finally {
            loadingTextures.remove(texturePath);
        }
        
        return false;
    }
    
    /**
     * Get atlas entry for a texture.
     */
    public AtlasEntry getAtlasEntry(String texturePath) {
        return textureEntries.get(texturePath);
    }
    
    /**
     * Get atlas by name.
     */
    public TextureAtlas getAtlas(String atlasName) {
        return atlases.get(atlasName);
    }
    
    /**
     * Get all atlases of a specific type.
     */
    public List<TextureAtlas> getAtlasesByType(AtlasType type) {
        List<String> atlasNames = atlasByType.get(type);
        List<TextureAtlas> result = new ArrayList<>();
        
        for (String name : atlasNames) {
            TextureAtlas atlas = atlases.get(name);
            if (atlas != null) {
                result.add(atlas);
            }
        }
        
        return result;
    }
    
    /**
     * Finalize all atlases (generate mipmaps, etc.).
     */
    public void finalizeAtlases() {
        for (TextureAtlas atlas : atlases.values()) {
            if (!atlas.isFinalized()) {
                atlas.finalize();
                logManager.debug("TextureAtlasManager", "Atlas finalized",
                               "name", atlas.getName(),
                               "textures", atlas.getTextureCount(),
                               "utilization", String.format("%.1f%%", atlas.getUtilization() * 100));
            }
        }
        
        logManager.info("TextureAtlasManager", "All atlases finalized", "count", atlases.size());
    }
    
    /**
     * Get texture atlas statistics.
     */
    public TextureAtlasStatistics getStatistics() {
        int totalTextures = textureEntries.size();
        long totalMemory = atlases.values().stream().mapToLong(TextureAtlas::getMemoryUsage).sum();
        float avgUtilization = (float) atlases.values().stream()
                                              .mapToDouble(TextureAtlas::getUtilization)
                                              .average().orElse(0.0);
        
        return new TextureAtlasStatistics(
            atlases.size(),
            totalTextures,
            totalMemory,
            avgUtilization
        );
    }
    
    private TextureAtlas findOrCreateAtlas(AtlasType type, int textureWidth, int textureHeight) {
        // Find existing atlas with space
        List<String> typeAtlases = atlasByType.get(type);
        for (String atlasName : typeAtlases) {
            TextureAtlas atlas = atlases.get(atlasName);
            if (atlas != null && !atlas.isFinalized()) {
                // Check if texture would fit (simplified check)
                if (textureWidth <= atlas.getSize() && textureHeight <= atlas.getSize()) {
                    return atlas;
                }
            }
        }
        
        // Create new atlas
        int atlasSize = Math.max(defaultAtlasSize, Math.max(textureWidth, textureHeight));
        atlasSize = Math.min(atlasSize, maxAtlasSize);
        
        String atlasName = type.getName() + "_atlas_" + atlasesCreated++;
        TextureAtlas newAtlas = new TextureAtlas(atlasName, type, atlasSize);
        
        atlases.put(atlasName, newAtlas);
        typeAtlases.add(atlasName);
        
        totalMemoryUsed += newAtlas.getMemoryUsage();
        metricsCollector.incrementCounter("textureAtlas.atlasesCreated");
        metricsCollector.setGauge("textureAtlas.totalMemoryMB", totalMemoryUsed / (1024 * 1024));
        
        logManager.info("TextureAtlasManager", "New atlas created",
                       "name", atlasName, "type", type.getName(), "size", atlasSize);
        
        return newAtlas;
    }
    
    private ByteBuffer loadTextureData(String texturePath) {
        // Placeholder - actual implementation would use AssetManager
        // to load texture data from various formats (PNG, JPG, DDS, etc.)
        return null;
    }
    
    private Vector2i getTextureDimensions(String texturePath) {
        // Placeholder - actual implementation would extract dimensions from texture file
        return new Vector2i(256, 256);
    }
    
    /**
     * Cleanup all atlases and resources.
     */
    public void cleanup() {
        logManager.info("TextureAtlasManager", "Cleaning up texture atlas manager");
        
        for (TextureAtlas atlas : atlases.values()) {
            atlas.cleanup();
        }
        
        atlases.clear();
        textureEntries.clear();
        atlasByType.clear();
        loadingTextures.clear();
        loadQueue.clear();
        
        logManager.info("TextureAtlasManager", "Texture atlas manager cleanup complete");
    }
    
    /**
     * Texture atlas statistics.
     */
    public static class TextureAtlasStatistics {
        public final int totalAtlases;
        public final int totalTextures;
        public final long totalMemoryBytes;
        public final float averageUtilization;
        
        public TextureAtlasStatistics(int totalAtlases, int totalTextures, 
                                    long totalMemoryBytes, float averageUtilization) {
            this.totalAtlases = totalAtlases;
            this.totalTextures = totalTextures;
            this.totalMemoryBytes = totalMemoryBytes;
            this.averageUtilization = averageUtilization;
        }
        
        @Override
        public String toString() {
            return String.format("AtlasStats{atlases=%d, textures=%d, memoryMB=%.1f, utilization=%.1f%%}",
                               totalAtlases, totalTextures, totalMemoryBytes / (1024.0 * 1024.0), 
                               averageUtilization * 100);
        }
    }
}