package engine.assets;

import engine.logging.LogManager;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL32.GL_GEOMETRY_SHADER;
import static org.lwjgl.opengl.GL40.GL_TESS_CONTROL_SHADER;
import static org.lwjgl.opengl.GL40.GL_TESS_EVALUATION_SHADER;
import static org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER;

/**
 * Shader asset with support for multiple shader stages, hot reloading, and preprocessing.
 * Supports vertex, fragment, geometry, tessellation, and compute shaders with automatic
 * uniform detection and dependency tracking.
 */
public class ShaderAsset implements Asset {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Shader stage patterns
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("#include\\s+[\"<]([^\"<>]+)[\">]");
    private static final Pattern UNIFORM_PATTERN = Pattern.compile("uniform\\s+(\\w+)\\s+(\\w+)\\s*;");
    private static final Pattern VERSION_PATTERN = Pattern.compile("#version\\s+(\\d+)");
    
    private final String assetId;
    private final String path;
    private final AssetMetadata metadata;
    private final AtomicReference<LoadState> loadState = new AtomicReference<>(LoadState.UNLOADED);
    private final CompletableFuture<Void> loadFuture = new CompletableFuture<>();
    
    // Shader data
    private volatile int programId = -1;
    private volatile Map<ShaderStage, Integer> shaderIds = new HashMap<>();
    private volatile Map<ShaderStage, String> shaderSources = new HashMap<>();
    private volatile Map<String, Integer> uniformLocations = new HashMap<>();
    private volatile Set<String> includeDependencies = new HashSet<>();
    private volatile int glslVersion = 330;
    
    // Configuration
    private boolean enableHotReload = true;
    private boolean enablePreprocessing = true;
    private Map<String, String> preprocessorDefines = new HashMap<>();
    
    /**
     * Shader stages enumeration.
     */
    public enum ShaderStage {
        VERTEX(GL_VERTEX_SHADER, ".vert"),
        FRAGMENT(GL_FRAGMENT_SHADER, ".frag"),
        GEOMETRY(GL_GEOMETRY_SHADER, ".geom"),
        TESS_CONTROL(GL_TESS_CONTROL_SHADER, ".tesc"),
        TESS_EVALUATION(GL_TESS_EVALUATION_SHADER, ".tese"),
        COMPUTE(GL_COMPUTE_SHADER, ".comp");
        
        private final int glType;
        private final String extension;
        
        ShaderStage(int glType, String extension) {
            this.glType = glType;
            this.extension = extension;
        }
        
        public int getGLType() { return glType; }
        public String getExtension() { return extension; }
        
        public static ShaderStage fromExtension(String extension) {
            for (ShaderStage stage : values()) {
                if (stage.extension.equals(extension)) {
                    return stage;
                }
            }
            return null;
        }
    }
    
    /**
     * Shader asset factory for creating shader assets.
     */
    public static class Factory implements AssetLoader.AssetFactory {
        @Override
        public Asset createAsset(String assetId, String path, AssetType type) throws Exception {
            if (type != AssetType.SHADER) {
                throw new IllegalArgumentException("Invalid asset type for ShaderAsset: " + type);
            }
            
            ShaderAsset shaderAsset = new ShaderAsset(assetId, path);
            shaderAsset.load();
            return shaderAsset;
        }
    }
    
    /**
     * Create a new shader asset.
     * @param assetId Asset identifier
     * @param path Shader file path (can be a program file or individual shader)
     */
    public ShaderAsset(String assetId, String path) {
        this.assetId = assetId;
        this.path = path;
        
        // Create metadata
        this.metadata = AssetMetadata.builder(assetId, path, AssetType.SHADER)
            .streamable(false)
            .compressible(true)
            .hotReloadEnabled(true)
            .build();
        
        logManager.debug("ShaderAsset", "Shader asset created", "assetId", assetId, "path", path);
    }
    
    /**
     * Add a preprocessor define.
     * @param name Define name
     * @param value Define value
     */
    public void addDefine(String name, String value) {
        preprocessorDefines.put(name, value);
    }
    
    /**
     * Remove a preprocessor define.
     * @param name Define name
     */
    public void removeDefine(String name) {
        preprocessorDefines.remove(name);
    }
    
    /**
     * Set hot reload enabled.
     * @param enabled Hot reload enabled
     */
    public void setHotReloadEnabled(boolean enabled) {
        this.enableHotReload = enabled;
    }
    
    /**
     * Set preprocessing enabled.
     * @param enabled Preprocessing enabled
     */
    public void setPreprocessingEnabled(boolean enabled) {
        this.enablePreprocessing = enabled;
    }
    
    /**
     * Load the shader from file(s).
     */
    public void load() throws Exception {
        if (!loadState.compareAndSet(LoadState.UNLOADED, LoadState.LOADING)) {
            return; // Already loading or loaded
        }
        
        try {
            logManager.info("ShaderAsset", "Loading shader", "assetId", assetId, "path", path);
            
            long startTime = System.currentTimeMillis();
            
            // Discover and load shader stages
            discoverShaderStages();
            
            // Load shader sources
            loadShaderSources();
            
            // Preprocess shaders if enabled
            if (enablePreprocessing) {
                preprocessShaders();
            }
            
            // Compile and link shader program
            compileAndLinkProgram();
            
            // Extract uniforms
            extractUniforms();
            
            long loadTime = System.currentTimeMillis() - startTime;
            metadata.setLoadTime(loadTime);
            
            loadState.set(LoadState.LOADED);
            loadFuture.complete(null);
            
            logManager.info("ShaderAsset", "Shader loaded successfully",
                           "assetId", assetId,
                           "programId", programId,
                           "stages", shaderIds.keySet(),
                           "uniforms", uniformLocations.size(),
                           "loadTime", loadTime);
            
        } catch (Exception e) {
            loadState.set(LoadState.ERROR);
            loadFuture.completeExceptionally(e);
            
            logManager.error("ShaderAsset", "Failed to load shader",
                           "assetId", assetId, "path", path, "error", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Use this shader program for rendering.
     */
    public void use() {
        if (programId != -1) {
            glUseProgram(programId);
        }
    }
    
    /**
     * Stop using any shader program.
     */
    public static void unuse() {
        glUseProgram(0);
    }
    
    /**
     * Get uniform location by name.
     * @param name Uniform name
     * @return Uniform location or -1 if not found
     */
    public int getUniformLocation(String name) {
        return uniformLocations.getOrDefault(name, -1);
    }
    
    /**
     * Set uniform integer value.
     * @param name Uniform name
     * @param value Value
     */
    public void setUniform(String name, int value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            glUniform1i(location, value);
        }
    }
    
    /**
     * Set uniform float value.
     * @param name Uniform name
     * @param value Value
     */
    public void setUniform(String name, float value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            glUniform1f(location, value);
        }
    }
    
    /**
     * Set uniform vector2 value.
     * @param name Uniform name
     * @param x X component
     * @param y Y component
     */
    public void setUniform(String name, float x, float y) {
        int location = getUniformLocation(name);
        if (location != -1) {
            glUniform2f(location, x, y);
        }
    }
    
    /**
     * Set uniform vector3 value.
     * @param name Uniform name
     * @param x X component
     * @param y Y component
     * @param z Z component
     */
    public void setUniform(String name, float x, float y, float z) {
        int location = getUniformLocation(name);
        if (location != -1) {
            glUniform3f(location, x, y, z);
        }
    }
    
    /**
     * Set uniform vector4 value.
     * @param name Uniform name
     * @param x X component
     * @param y Y component
     * @param z Z component
     * @param w W component
     */
    public void setUniform(String name, float x, float y, float z, float w) {
        int location = getUniformLocation(name);
        if (location != -1) {
            glUniform4f(location, x, y, z, w);
        }
    }
    
    /**
     * Set uniform matrix4 value.
     * @param name Uniform name
     * @param matrix Matrix values (16 floats)
     */
    public void setUniformMatrix4(String name, float[] matrix) {
        int location = getUniformLocation(name);
        if (location != -1 && matrix.length >= 16) {
            glUniformMatrix4fv(location, false, matrix);
        }
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
        return AssetType.SHADER;
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
        long size = 0;
        for (String source : shaderSources.values()) {
            size += source.length();
        }
        return size;
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
                throw new RuntimeException("Failed to reload shader: " + assetId, e);
            }
        });
    }
    
    @Override
    public void dispose() {
        // Delete shader objects
        for (int shaderId : shaderIds.values()) {
            glDeleteShader(shaderId);
        }
        shaderIds.clear();
        
        // Delete program
        if (programId != -1) {
            glDeleteProgram(programId);
            programId = -1;
        }
        
        shaderSources.clear();
        uniformLocations.clear();
        includeDependencies.clear();
        
        loadState.set(LoadState.DISPOSED);
        
        logManager.debug("ShaderAsset", "Shader disposed", "assetId", assetId);
    }
    
    @Override
    public long getLastModified() {
        try {
            long lastModified = 0;
            
            // Check main shader file
            java.io.File mainFile = new java.io.File(path);
            if (mainFile.exists()) {
                lastModified = Math.max(lastModified, mainFile.lastModified());
            }
            
            // Check individual stage files
            for (ShaderStage stage : ShaderStage.values()) {
                String stagePath = getStageFilePath(stage);
                java.io.File stageFile = new java.io.File(stagePath);
                if (stageFile.exists()) {
                    lastModified = Math.max(lastModified, stageFile.lastModified());
                }
            }
            
            // Check include dependencies
            for (String includePath : includeDependencies) {
                java.io.File includeFile = new java.io.File(includePath);
                if (includeFile.exists()) {
                    lastModified = Math.max(lastModified, includeFile.lastModified());
                }
            }
            
            return lastModified;
        } catch (Exception e) {
            return 0;
        }
    }
    
    @Override
    public String[] getDependencies() {
        return includeDependencies.toArray(new String[0]);
    }
    
    // Getters
    
    public int getProgramId() { return programId; }
    public Map<ShaderStage, Integer> getShaderIds() { return Collections.unmodifiableMap(shaderIds); }
    public Map<String, Integer> getUniformLocations() { return Collections.unmodifiableMap(uniformLocations); }
    public Set<String> getIncludeDependencies() { return Collections.unmodifiableSet(includeDependencies); }
    public int getGLSLVersion() { return glslVersion; }
    
    private void discoverShaderStages() {
        // Check if path is a program file or individual shader
        String extension = getFileExtension(path);
        
        if (extension.equals(".glsl") || extension.equals(".shader")) {
            // Program file - look for individual stage files
            String basePath = path.substring(0, path.lastIndexOf('.'));
            
            for (ShaderStage stage : ShaderStage.values()) {
                String stagePath = basePath + stage.getExtension();
                if (Files.exists(Paths.get(stagePath))) {
                    shaderSources.put(stage, ""); // Placeholder
                }
            }
        } else {
            // Individual shader file
            ShaderStage stage = ShaderStage.fromExtension(extension);
            if (stage != null) {
                shaderSources.put(stage, ""); // Placeholder
            }
        }
        
        if (shaderSources.isEmpty()) {
            throw new RuntimeException("No shader stages found for: " + path);
        }
    }
    
    private void loadShaderSources() throws IOException {
        for (ShaderStage stage : shaderSources.keySet()) {
            String stagePath = getStageFilePath(stage);
            String source = Files.readString(Paths.get(stagePath));
            shaderSources.put(stage, source);
            
            // Extract GLSL version
            Matcher versionMatcher = VERSION_PATTERN.matcher(source);
            if (versionMatcher.find()) {
                glslVersion = Integer.parseInt(versionMatcher.group(1));
            }
        }
    }
    
    private void preprocessShaders() {
        for (Map.Entry<ShaderStage, String> entry : shaderSources.entrySet()) {
            String source = entry.getValue();
            
            // Add preprocessor defines
            StringBuilder processedSource = new StringBuilder();
            
            // Add version directive first
            Matcher versionMatcher = VERSION_PATTERN.matcher(source);
            if (versionMatcher.find()) {
                processedSource.append(versionMatcher.group()).append("\n");
                source = source.substring(versionMatcher.end());
            }
            
            // Add defines
            for (Map.Entry<String, String> define : preprocessorDefines.entrySet()) {
                processedSource.append("#define ").append(define.getKey()).append(" ").append(define.getValue()).append("\n");
            }
            
            // Process includes
            source = processIncludes(source);
            
            processedSource.append(source);
            entry.setValue(processedSource.toString());
        }
    }
    
    private String processIncludes(String source) {
        Matcher includeMatcher = INCLUDE_PATTERN.matcher(source);
        StringBuffer result = new StringBuffer();
        
        while (includeMatcher.find()) {
            String includePath = includeMatcher.group(1);
            String includeContent = loadIncludeFile(includePath);
            includeDependencies.add(includePath);
            
            // Recursively process includes in the included file
            includeContent = processIncludes(includeContent);
            
            includeMatcher.appendReplacement(result, Matcher.quoteReplacement(includeContent));
        }
        includeMatcher.appendTail(result);
        
        return result.toString();
    }
    
    private String loadIncludeFile(String includePath) {
        try {
            // Try relative to shader directory first
            String shaderDir = Paths.get(path).getParent().toString();
            String fullPath = Paths.get(shaderDir, includePath).toString();
            
            if (Files.exists(Paths.get(fullPath))) {
                return Files.readString(Paths.get(fullPath));
            }
            
            // Try absolute path
            if (Files.exists(Paths.get(includePath))) {
                return Files.readString(Paths.get(includePath));
            }
            
            logManager.warn("ShaderAsset", "Include file not found", "path", includePath);
            return "// Include file not found: " + includePath + "\n";
            
        } catch (IOException e) {
            logManager.error("ShaderAsset", "Failed to load include file", "path", includePath, "error", e.getMessage());
            return "// Failed to load include: " + includePath + "\n";
        }
    }
    
    private void compileAndLinkProgram() {
        // Create program
        programId = glCreateProgram();
        
        // Compile shaders
        for (Map.Entry<ShaderStage, String> entry : shaderSources.entrySet()) {
            ShaderStage stage = entry.getKey();
            String source = entry.getValue();
            
            int shaderId = compileShader(stage, source);
            shaderIds.put(stage, shaderId);
            glAttachShader(programId, shaderId);
        }
        
        // Link program
        glLinkProgram(programId);
        
        // Check link status
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            throw new RuntimeException("Shader program linking failed: " + log);
        }
        
        // Detach and delete shaders (they're now part of the program)
        for (int shaderId : shaderIds.values()) {
            glDetachShader(programId, shaderId);
            glDeleteShader(shaderId);
        }
    }
    
    private int compileShader(ShaderStage stage, String source) {
        int shaderId = glCreateShader(stage.getGLType());
        glShaderSource(shaderId, source);
        glCompileShader(shaderId);
        
        // Check compilation status
        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shaderId);
            glDeleteShader(shaderId);
            throw new RuntimeException("Shader compilation failed (" + stage + "): " + log);
        }
        
        return shaderId;
    }
    
    private void extractUniforms() {
        int uniformCount = glGetProgrami(programId, GL_ACTIVE_UNIFORMS);
        
        for (int i = 0; i < uniformCount; i++) {
            String uniformName = glGetActiveUniform(programId, i, 256);
            int location = glGetUniformLocation(programId, uniformName);
            uniformLocations.put(uniformName, location);
        }
        
        logManager.debug("ShaderAsset", "Uniforms extracted", "assetId", assetId, "count", uniformCount);
    }
    
    private String getStageFilePath(ShaderStage stage) {
        String extension = getFileExtension(path);
        
        if (extension.equals(".glsl") || extension.equals(".shader")) {
            // Program file - construct stage file path
            String basePath = path.substring(0, path.lastIndexOf('.'));
            return basePath + stage.getExtension();
        } else {
            // Individual shader file
            return path;
        }
    }
    
    private String getFileExtension(String filePath) {
        int lastDot = filePath.lastIndexOf('.');
        return lastDot != -1 ? filePath.substring(lastDot) : "";
    }
    
    @Override
    public String toString() {
        return String.format("ShaderAsset{id='%s', path='%s', program=%d, stages=%s, uniforms=%d, state=%s}",
                           assetId, path, programId, shaderIds.keySet(), uniformLocations.size(), loadState.get());
    }
}