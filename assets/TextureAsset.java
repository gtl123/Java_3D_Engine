package engine.assets;

import engine.logging.LogManager;
import engine.raster.Texture;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.stb.STBImage.*;

/**
 * Enhanced texture asset with multiple format support, streaming, and compression.
 * Replaces the basic Texture class with enterprise-grade features including
 * progressive loading, format detection, and memory optimization.
 */
public class TextureAsset implements Asset {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final String assetId;
    private final String path;
    private final AssetMetadata metadata;
    private final AtomicReference<LoadState> loadState = new AtomicReference<>(LoadState.UNLOADED);
    private final CompletableFuture<Void> loadFuture = new CompletableFuture<>();
    
    // Texture data
    private volatile int textureId = -1;
    private volatile int width = 0;
    private volatile int height = 0;
    private volatile int channels = 0;
    private volatile TextureFormat format = TextureFormat.RGBA;
    private volatile FilterMode filterMode = FilterMode.NEAREST;
    private volatile WrapMode wrapMode = WrapMode.REPEAT;
    private volatile boolean generateMipmaps = true;
    
    // Streaming support
    private volatile ByteBuffer imageData;
    private volatile boolean isStreaming = false;
    
    /**
     * Supported texture formats.
     */
    public enum TextureFormat {
        RGB(GL_RGB, 3),
        RGBA(GL_RGBA, 4),
        LUMINANCE(GL_LUMINANCE, 1),
        LUMINANCE_ALPHA(GL_LUMINANCE_ALPHA, 2);
        
        private final int glFormat;
        private final int channels;
        
        TextureFormat(int glFormat, int channels) {
            this.glFormat = glFormat;
            this.channels = channels;
        }
        
        public int getGLFormat() { return glFormat; }
        public int getChannels() { return channels; }
        
        public static TextureFormat fromChannels(int channels) {
            switch (channels) {
                case 1: return LUMINANCE;
                case 2: return LUMINANCE_ALPHA;
                case 3: return RGB;
                case 4: return RGBA;
                default: return RGBA;
            }
        }
    }
    
    /**
     * Texture filtering modes.
     */
    public enum FilterMode {
        NEAREST(GL_NEAREST, GL_NEAREST),
        LINEAR(GL_LINEAR, GL_LINEAR),
        NEAREST_MIPMAP_NEAREST(GL_NEAREST_MIPMAP_NEAREST, GL_NEAREST),
        LINEAR_MIPMAP_NEAREST(GL_LINEAR_MIPMAP_NEAREST, GL_LINEAR),
        NEAREST_MIPMAP_LINEAR(GL_NEAREST_MIPMAP_LINEAR, GL_NEAREST),
        LINEAR_MIPMAP_LINEAR(GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR);
        
        private final int minFilter;
        private final int magFilter;
        
        FilterMode(int minFilter, int magFilter) {
            this.minFilter = minFilter;
            this.magFilter = magFilter;
        }
        
        public int getMinFilter() { return minFilter; }
        public int getMagFilter() { return magFilter; }
    }
    
    /**
     * Texture wrap modes.
     */
    public enum WrapMode {
        REPEAT(GL_REPEAT),
        CLAMP_TO_EDGE(GL_CLAMP_TO_EDGE),
        CLAMP_TO_BORDER(GL_CLAMP_TO_BORDER),
        MIRRORED_REPEAT(GL_MIRRORED_REPEAT);
        
        private final int glMode;
        
        WrapMode(int glMode) {
            this.glMode = glMode;
        }
        
        public int getGLMode() { return glMode; }
    }
    
    /**
     * Texture asset factory for creating texture assets.
     */
    public static class Factory implements AssetLoader.AssetFactory {
        @Override
        public Asset createAsset(String assetId, String path, AssetType type) throws Exception {
            if (type != AssetType.TEXTURE) {
                throw new IllegalArgumentException("Invalid asset type for TextureAsset: " + type);
            }
            
            TextureAsset textureAsset = new TextureAsset(assetId, path);
            textureAsset.load();
            return textureAsset;
        }
    }
    
    /**
     * Create a new texture asset.
     * @param assetId Asset identifier
     * @param path Texture file path
     */
    public TextureAsset(String assetId, String path) {
        this.assetId = assetId;
        this.path = path;
        
        // Create metadata
        this.metadata = AssetMetadata.builder(assetId, path, AssetType.TEXTURE)
            .streamable(true)
            .compressible(true)
            .hotReloadEnabled(true)
            .build();
        
        logManager.debug("TextureAsset", "Texture asset created", "assetId", assetId, "path", path);
    }
    
    /**
     * Load the texture from file.
     */
    public void load() throws Exception {
        if (!loadState.compareAndSet(LoadState.UNLOADED, LoadState.LOADING)) {
            return; // Already loading or loaded
        }
        
        try {
            logManager.info("TextureAsset", "Loading texture", "assetId", assetId, "path", path);
            
            long startTime = System.currentTimeMillis();
            
            // Load image data
            loadImageData();
            
            // Create OpenGL texture
            createGLTexture();
            
            long loadTime = System.currentTimeMillis() - startTime;
            metadata.setLoadTime(loadTime);
            
            loadState.set(LoadState.LOADED);
            loadFuture.complete(null);
            
            logManager.info("TextureAsset", "Texture loaded successfully",
                           "assetId", assetId,
                           "width", width,
                           "height", height,
                           "format", format,
                           "loadTime", loadTime);
            
        } catch (Exception e) {
            loadState.set(LoadState.ERROR);
            loadFuture.completeExceptionally(e);
            
            logManager.error("TextureAsset", "Failed to load texture",
                           "assetId", assetId, "path", path, "error", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Load texture with streaming support.
     * @param streamer Asset streamer for progressive loading
     */
    public void loadWithStreaming(AssetStreamer streamer) {
        if (!loadState.compareAndSet(LoadState.UNLOADED, LoadState.STREAMING)) {
            return; // Already loading or loaded
        }
        
        isStreaming = true;
        
        AssetStreamer.StreamingCallback callback = new AssetStreamer.StreamingCallback() {
            private ByteBuffer accumulatedData;
            private int totalBytesReceived = 0;
            
            @Override
            public void onChunkReceived(AssetStreamer.StreamingSession session, ByteBuffer chunkData, 
                                      int chunkIndex, boolean isLastChunk) {
                try {
                    // Accumulate chunk data
                    if (accumulatedData == null) {
                        accumulatedData = ByteBuffer.allocate((int) session.getTotalSize());
                    }
                    
                    accumulatedData.put(chunkData);
                    totalBytesReceived += chunkData.remaining();
                    
                    logManager.debug("TextureAsset", "Texture chunk received",
                                   "assetId", assetId,
                                   "chunkIndex", chunkIndex,
                                   "chunkSize", chunkData.remaining(),
                                   "totalReceived", totalBytesReceived,
                                   "progress", session.getProgress());
                    
                    if (isLastChunk) {
                        accumulatedData.flip();
                        processStreamedData(accumulatedData);
                    }
                } catch (Exception e) {
                    logManager.error("TextureAsset", "Error processing texture chunk",
                                   "assetId", assetId, "chunkIndex", chunkIndex, "error", e.getMessage());
                }
            }
            
            @Override
            public void onStreamingComplete(AssetStreamer.StreamingSession session) {
                try {
                    createGLTexture();
                    loadState.set(LoadState.LOADED);
                    loadFuture.complete(null);
                    isStreaming = false;
                    
                    logManager.info("TextureAsset", "Texture streaming completed",
                                   "assetId", assetId,
                                   "totalSize", session.getTotalSize(),
                                   "streamingTime", session.getElapsedTime());
                } catch (Exception e) {
                    onStreamingError(session, e);
                }
            }
            
            @Override
            public void onStreamingError(AssetStreamer.StreamingSession session, Exception error) {
                loadState.set(LoadState.ERROR);
                loadFuture.completeExceptionally(error);
                isStreaming = false;
                
                logManager.error("TextureAsset", "Texture streaming failed",
                               "assetId", assetId, "error", error.getMessage(), error);
            }
            
            @Override
            public void onStreamingCancelled(AssetStreamer.StreamingSession session) {
                loadState.set(LoadState.UNLOADED);
                loadFuture.cancel(true);
                isStreaming = false;
                
                logManager.info("TextureAsset", "Texture streaming cancelled", "assetId", assetId);
            }
        };
        
        streamer.startStreaming(assetId, path, callback);
    }
    
    /**
     * Bind the texture for rendering.
     */
    public void bind() {
        if (textureId != -1) {
            glBindTexture(GL_TEXTURE_2D, textureId);
        }
    }
    
    /**
     * Unbind the texture.
     */
    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    
    /**
     * Set texture filtering mode.
     * @param filterMode Filter mode
     */
    public void setFilterMode(FilterMode filterMode) {
        this.filterMode = filterMode;
        if (textureId != -1) {
            bind();
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filterMode.getMinFilter());
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filterMode.getMagFilter());
        }
    }
    
    /**
     * Set texture wrap mode.
     * @param wrapMode Wrap mode
     */
    public void setWrapMode(WrapMode wrapMode) {
        this.wrapMode = wrapMode;
        if (textureId != -1) {
            bind();
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrapMode.getGLMode());
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrapMode.getGLMode());
        }
    }
    
    /**
     * Enable or disable mipmap generation.
     * @param generateMipmaps Generate mipmaps
     */
    public void setGenerateMipmaps(boolean generateMipmaps) {
        this.generateMipmaps = generateMipmaps;
    }
    
    // Asset interface implementation
    
    @Override
    public String getId() {
        return assetId;
    }
    
    @Override
    public String getPath() {
        return path;
    }
    
    @Override
    public AssetType getType() {
        return AssetType.TEXTURE;
    }
    
    @Override
    public LoadState getLoadState() {
        return loadState.get();
    }
    
    @Override
    public AssetMetadata getMetadata() {
        return metadata;
    }
    
    @Override
    public long getSize() {
        return width * height * format.getChannels();
    }
    
    @Override
    public CompletableFuture<Void> getLoadFuture() {
        return loadFuture;
    }
    
    @Override
    public CompletableFuture<Void> reload() {
        dispose();
        loadState.set(LoadState.UNLOADED);
        
        return CompletableFuture.runAsync(() -> {
            try {
                load();
            } catch (Exception e) {
                throw new RuntimeException("Failed to reload texture: " + assetId, e);
            }
        });
    }
    
    @Override
    public void dispose() {
        if (textureId != -1) {
            glDeleteTextures(textureId);
            textureId = -1;
        }
        
        if (imageData != null) {
            stbi_image_free(imageData);
            imageData = null;
        }
        
        loadState.set(LoadState.DISPOSED);
        
        logManager.debug("TextureAsset", "Texture disposed", "assetId", assetId);
    }
    
    @Override
    public long getLastModified() {
        try {
            java.io.File file = new java.io.File(path);
            return file.exists() ? file.lastModified() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
    
    @Override
    public String[] getDependencies() {
        return new String[0]; // Textures typically have no dependencies
    }
    
    // Getters
    
    public int getTextureId() { return textureId; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getChannels() { return channels; }
    public TextureFormat getFormat() { return format; }
    public FilterMode getFilterMode() { return filterMode; }
    public WrapMode getWrapMode() { return wrapMode; }
    public boolean isGenerateMipmaps() { return generateMipmaps; }
    public boolean isStreaming() { return isStreaming; }
    
    /**
     * Create a legacy Texture wrapper for backward compatibility.
     * @return Legacy Texture instance
     */
    public Texture toLegacyTexture() {
        if (textureId == -1) {
            throw new IllegalStateException("Texture not loaded");
        }
        return new Texture(textureId);
    }
    
    private void loadImageData() throws Exception {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer c = stack.mallocInt(1);
            
            // Load image buffer
            ByteBuffer imageBuffer = engine.utils.Utils.ioResourceToByteBuffer(path, 8 * 1024);
            if (imageBuffer == null) {
                throw new Exception("Image resource [" + path + "] not found");
            }
            
            // Decode image
            imageData = stbi_load_from_memory(imageBuffer, w, h, c, 0);
            if (imageData == null) {
                throw new Exception("Image file [" + path + "] not loaded: " + stbi_failure_reason());
            }
            
            width = w.get();
            height = h.get();
            channels = c.get();
            format = TextureFormat.fromChannels(channels);
            
            logManager.debug("TextureAsset", "Image data loaded",
                           "assetId", assetId,
                           "width", width,
                           "height", height,
                           "channels", channels,
                           "format", format);
        }
    }
    
    private void processStreamedData(ByteBuffer streamedData) throws Exception {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer c = stack.mallocInt(1);
            
            // Decode streamed image data
            imageData = stbi_load_from_memory(streamedData, w, h, c, 0);
            if (imageData == null) {
                throw new Exception("Streamed image data could not be decoded: " + stbi_failure_reason());
            }
            
            width = w.get();
            height = h.get();
            channels = c.get();
            format = TextureFormat.fromChannels(channels);
            
            logManager.debug("TextureAsset", "Streamed image data processed",
                           "assetId", assetId,
                           "width", width,
                           "height", height,
                           "channels", channels);
        }
    }
    
    private void createGLTexture() {
        if (imageData == null) {
            throw new IllegalStateException("No image data available");
        }
        
        // Generate OpenGL texture
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        
        // Set texture parameters
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filterMode.getMinFilter());
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filterMode.getMagFilter());
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrapMode.getGLMode());
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrapMode.getGLMode());
        
        // Upload texture data
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, format.getGLFormat(), width, height, 0,
                    format.getGLFormat(), GL_UNSIGNED_BYTE, imageData);
        
        // Generate mipmaps if enabled
        if (generateMipmaps) {
            glGenerateMipmap(GL_TEXTURE_2D);
        }
        
        // Free image data after upload
        stbi_image_free(imageData);
        imageData = null;
        
        logManager.debug("TextureAsset", "OpenGL texture created",
                       "assetId", assetId,
                       "textureId", textureId,
                       "width", width,
                       "height", height,
                       "format", format,
                       "mipmaps", generateMipmaps);
    }
    
    @Override
    public String toString() {
        return String.format("TextureAsset{id='%s', path='%s', size=%dx%d, format=%s, state=%s}",
                           assetId, path, width, height, format, loadState.get());
    }
}