package engine.rendering.advanced;

import engine.assets.AssetManager;
import engine.config.ConfigurationManager;
import engine.logging.LogManager;
import engine.logging.MetricsCollector;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

/**
 * Advanced Shader Compiler with hot-reloading, preprocessing, and optimization.
 * Supports shader includes, conditional compilation, and runtime recompilation.
 * Provides automatic shader validation and error reporting.
 */
public class ShaderCompiler {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = LogManager.getInstance().getMetricsCollector();
    private static final ConfigurationManager configManager = ConfigurationManager.getInstance();
    private static final AssetManager assetManager = AssetManager.getInstance();
    
    // Configuration
    private final boolean enableHotReload;
    private final boolean enableOptimization;
    private final boolean enableDebugInfo;
    private final String shaderDirectory;
    
    // Shader management
    private final Map<String, ShaderProgram> programs = new ConcurrentHashMap<>();
    private final Map<String, CompiledShader> shaders = new ConcurrentHashMap<>();
    private final Map<String, String> shaderSources = new ConcurrentHashMap<>();
    private final Map<String, Long> lastModified = new ConcurrentHashMap<>();
    
    // Hot-reload support
    private WatchService watchService;
    private Thread watchThread;
    private final Set<String> watchedFiles = ConcurrentHashMap.newKeySet();
    
    // Include system
    private final Map<String, String> includes = new ConcurrentHashMap<>();
    private final Pattern includePattern = Pattern.compile("#include\\s+[\"<]([^\"<>]+)[\">]");
    
    // Performance tracking
    private int shadersCompiled = 0;
    private int programsLinked = 0;
    private long totalCompileTime = 0;
    
    /**
     * Shader types supported by the compiler.
     */
    public enum ShaderType {
        VERTEX(GL_VERTEX_SHADER, ".vert"),
        FRAGMENT(GL_FRAGMENT_SHADER, ".frag"),
        GEOMETRY(GL_GEOMETRY_SHADER, ".geom"),
        COMPUTE(GL_COMPUTE_SHADER, ".comp"),
        TESS_CONTROL(GL_TESS_CONTROL_SHADER, ".tesc"),
        TESS_EVALUATION(GL_TESS_EVALUATION_SHADER, ".tese");
        
        private final int glType;
        private final String extension;
        
        ShaderType(int glType, String extension) {
            this.glType = glType;
            this.extension = extension;
        }
        
        public int getGLType() { return glType; }
        public String getExtension() { return extension; }
        
        public static ShaderType fromExtension(String filename) {
            for (ShaderType type : values()) {
                if (filename.endsWith(type.extension)) {
                    return type;
                }
            }
            return null;
        }
    }
    
    /**
     * Represents a compiled shader.
     */
    public static class CompiledShader {
        private final String name;
        private final ShaderType type;
        private final int shaderId;
        private final String source;
        private final long compileTime;
        
        public CompiledShader(String name, ShaderType type, int shaderId, String source, long compileTime) {
            this.name = name;
            this.type = type;
            this.shaderId = shaderId;
            this.source = source;
            this.compileTime = compileTime;
        }
        
        public String getName() { return name; }
        public ShaderType getType() { return type; }
        public int getShaderId() { return shaderId; }
        public String getSource() { return source; }
        public long getCompileTime() { return compileTime; }
        
        public void cleanup() {
            if (shaderId != 0) {
                glDeleteShader(shaderId);
            }
        }
    }
    
    /**
     * Represents a linked shader program.
     */
    public static class ShaderProgram {
        private final String name;
        private final int programId;
        private final Map<ShaderType, String> shaderFiles;
        private final Map<String, Integer> uniformLocations = new ConcurrentHashMap<>();
        private final long linkTime;
        
        public ShaderProgram(String name, int programId, Map<ShaderType, String> shaderFiles, long linkTime) {
            this.name = name;
            this.programId = programId;
            this.shaderFiles = new HashMap<>(shaderFiles);
            this.linkTime = linkTime;
        }
        
        public String getName() { return name; }
        public int getProgramId() { return programId; }
        public Map<ShaderType, String> getShaderFiles() { return new HashMap<>(shaderFiles); }
        public long getLinkTime() { return linkTime; }
        
        public int getUniformLocation(String uniformName) {
            return uniformLocations.computeIfAbsent(uniformName, 
                name -> glGetUniformLocation(programId, name));
        }
        
        public void use() {
            glUseProgram(programId);
        }
        
        public void cleanup() {
            if (programId != 0) {
                glDeleteProgram(programId);
            }
        }
    }
    
    /**
     * Shader compilation result.
     */
    public static class CompilationResult {
        private final boolean success;
        private final String errorLog;
        private final CompiledShader shader;
        
        public CompilationResult(boolean success, String errorLog, CompiledShader shader) {
            this.success = success;
            this.errorLog = errorLog;
            this.shader = shader;
        }
        
        public boolean isSuccess() { return success; }
        public String getErrorLog() { return errorLog; }
        public CompiledShader getShader() { return shader; }
    }
    
    /**
     * Initialize the shader compiler.
     */
    public ShaderCompiler() {
        // Load configuration
        this.enableHotReload = configManager.getBoolean("rendering.shaders.hotReload", true);
        this.enableOptimization = configManager.getBoolean("rendering.shaders.optimization", true);
        this.enableDebugInfo = configManager.getBoolean("rendering.shaders.debugInfo", false);
        this.shaderDirectory = configManager.getString("rendering.shaders.directory", "assets/shaders");
        
        // Initialize hot-reload if enabled
        if (enableHotReload) {
            initializeHotReload();
        }
        
        // Load common includes
        loadCommonIncludes();
        
        logManager.info("ShaderCompiler", "Shader compiler initialized",
                       "hotReload", enableHotReload,
                       "optimization", enableOptimization,
                       "shaderDir", shaderDirectory);
    }
    
    /**
     * Compile a shader from file.
     */
    public CompilationResult compileShader(String shaderPath) {
        long startTime = System.nanoTime();
        
        try {
            // Determine shader type from file extension
            ShaderType type = ShaderType.fromExtension(shaderPath);
            if (type == null) {
                return new CompilationResult(false, "Unknown shader type for file: " + shaderPath, null);
            }
            
            // Load and preprocess shader source
            String source = loadShaderSource(shaderPath);
            if (source == null) {
                return new CompilationResult(false, "Failed to load shader source: " + shaderPath, null);
            }
            
            String processedSource = preprocessShader(source, shaderPath);
            
            // Create and compile shader
            int shaderId = glCreateShader(type.getGLType());
            glShaderSource(shaderId, processedSource);
            glCompileShader(shaderId);
            
            // Check compilation status
            int compileStatus = glGetShaderi(shaderId, GL_COMPILE_STATUS);
            if (compileStatus == GL_FALSE) {
                String errorLog = glGetShaderInfoLog(shaderId);
                glDeleteShader(shaderId);
                
                logManager.error("ShaderCompiler", "Shader compilation failed", 
                               "shader", shaderPath, "error", errorLog);
                
                return new CompilationResult(false, errorLog, null);
            }
            
            long endTime = System.nanoTime();
            long compileTime = endTime - startTime;
            
            CompiledShader shader = new CompiledShader(shaderPath, type, shaderId, processedSource, compileTime);
            shaders.put(shaderPath, shader);
            shaderSources.put(shaderPath, source);
            
            // Track file for hot-reload
            if (enableHotReload) {
                trackFileForHotReload(shaderPath);
            }
            
            shadersCompiled++;
            totalCompileTime += compileTime;
            
            metricsCollector.incrementCounter("shaderCompiler.shadersCompiled");
            metricsCollector.recordHistogram("shaderCompiler.compileTime", compileTime / 1_000_000.0);
            
            logManager.debug("ShaderCompiler", "Shader compiled successfully",
                           "shader", shaderPath, "type", type.name(), 
                           "timeMs", compileTime / 1_000_000.0);
            
            return new CompilationResult(true, null, shader);
            
        } catch (Exception e) {
            logManager.error("ShaderCompiler", "Error compiling shader", e, "shader", shaderPath);
            return new CompilationResult(false, "Exception: " + e.getMessage(), null);
        }
    }
    
    /**
     * Link a shader program from multiple shaders.
     */
    public ShaderProgram linkProgram(String programName, Map<ShaderType, String> shaderFiles) {
        long startTime = System.nanoTime();
        
        try {
            // Compile all shaders
            Map<ShaderType, CompiledShader> compiledShaders = new HashMap<>();
            
            for (Map.Entry<ShaderType, String> entry : shaderFiles.entrySet()) {
                CompilationResult result = compileShader(entry.getValue());
                if (!result.isSuccess()) {
                    logManager.error("ShaderCompiler", "Failed to compile shader for program",
                                   "program", programName, "shader", entry.getValue(), 
                                   "error", result.getErrorLog());
                    return null;
                }
                compiledShaders.put(entry.getKey(), result.getShader());
            }
            
            // Create and link program
            int programId = glCreateProgram();
            
            // Attach shaders
            for (CompiledShader shader : compiledShaders.values()) {
                glAttachShader(programId, shader.getShaderId());
            }
            
            // Link program
            glLinkProgram(programId);
            
            // Check link status
            int linkStatus = glGetProgrami(programId, GL_LINK_STATUS);
            if (linkStatus == GL_FALSE) {
                String errorLog = glGetProgramInfoLog(programId);
                
                // Cleanup
                for (CompiledShader shader : compiledShaders.values()) {
                    glDetachShader(programId, shader.getShaderId());
                }
                glDeleteProgram(programId);
                
                logManager.error("ShaderCompiler", "Program linking failed",
                               "program", programName, "error", errorLog);
                return null;
            }
            
            // Detach shaders (they're no longer needed attached)
            for (CompiledShader shader : compiledShaders.values()) {
                glDetachShader(programId, shader.getShaderId());
            }
            
            long endTime = System.nanoTime();
            long linkTime = endTime - startTime;
            
            ShaderProgram program = new ShaderProgram(programName, programId, shaderFiles, linkTime);
            programs.put(programName, program);
            
            programsLinked++;
            
            metricsCollector.incrementCounter("shaderCompiler.programsLinked");
            metricsCollector.recordHistogram("shaderCompiler.linkTime", linkTime / 1_000_000.0);
            
            logManager.info("ShaderCompiler", "Shader program linked successfully",
                          "program", programName, "shaders", shaderFiles.size(),
                          "timeMs", linkTime / 1_000_000.0);
            
            return program;
            
        } catch (Exception e) {
            logManager.error("ShaderCompiler", "Error linking program", e, "program", programName);
            return null;
        }
    }
    
    /**
     * Get a compiled shader program.
     */
    public ShaderProgram getProgram(String programName) {
        return programs.get(programName);
    }
    
    /**
     * Reload a shader program (for hot-reload).
     */
    public boolean reloadProgram(String programName) {
        ShaderProgram existingProgram = programs.get(programName);
        if (existingProgram == null) {
            return false;
        }
        
        logManager.info("ShaderCompiler", "Reloading shader program", "program", programName);
        
        // Clear cached shaders for this program
        for (String shaderFile : existingProgram.getShaderFiles().values()) {
            shaders.remove(shaderFile);
        }
        
        // Relink program
        ShaderProgram newProgram = linkProgram(programName, existingProgram.getShaderFiles());
        if (newProgram != null) {
            // Cleanup old program
            existingProgram.cleanup();
            
            logManager.info("ShaderCompiler", "Shader program reloaded successfully", "program", programName);
            return true;
        } else {
            logManager.error("ShaderCompiler", "Failed to reload shader program", "program", programName);
            return false;
        }
    }
    
    private String loadShaderSource(String shaderPath) {
        try {
            Path fullPath = Paths.get(shaderDirectory, shaderPath);
            if (!Files.exists(fullPath)) {
                // Try loading from assets
                return assetManager.loadTextAsset(shaderPath);
            }
            
            String source = new String(Files.readAllBytes(fullPath));
            lastModified.put(shaderPath, Files.getLastModifiedTime(fullPath).toMillis());
            return source;
            
        } catch (Exception e) {
            logManager.error("ShaderCompiler", "Error loading shader source", e, "shader", shaderPath);
            return null;
        }
    }
    
    private String preprocessShader(String source, String shaderPath) {
        StringBuilder result = new StringBuilder();
        String[] lines = source.split("\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // Process includes
            Matcher matcher = includePattern.matcher(line);
            if (matcher.find()) {
                String includePath = matcher.group(1);
                String includeContent = includes.get(includePath);
                
                if (includeContent == null) {
                    // Try to load include file
                    includeContent = loadShaderSource("includes/" + includePath);
                    if (includeContent != null) {
                        includes.put(includePath, includeContent);
                    }
                }
                
                if (includeContent != null) {
                    result.append("// Begin include: ").append(includePath).append("\n");
                    result.append(includeContent).append("\n");
                    result.append("// End include: ").append(includePath).append("\n");
                } else {
                    logManager.warn("ShaderCompiler", "Include file not found", 
                                  "include", includePath, "shader", shaderPath);
                    result.append(line).append("\n");
                }
            } else {
                result.append(line).append("\n");
            }
        }
        
        return result.toString();
    }
    
    private void loadCommonIncludes() {
        // Load common shader includes
        String[] commonIncludes = {
            "common.glsl",
            "lighting.glsl",
            "pbr.glsl",
            "shadows.glsl",
            "noise.glsl"
        };
        
        for (String include : commonIncludes) {
            String content = loadShaderSource("includes/" + include);
            if (content != null) {
                includes.put(include, content);
                logManager.debug("ShaderCompiler", "Loaded common include", "include", include);
            }
        }
    }
    
    private void initializeHotReload() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            
            // Watch shader directory
            Path shaderDir = Paths.get(shaderDirectory);
            if (Files.exists(shaderDir)) {
                shaderDir.register(watchService, 
                                 StandardWatchEventKinds.ENTRY_MODIFY,
                                 StandardWatchEventKinds.ENTRY_CREATE);
            }
            
            // Start watch thread
            watchThread = new Thread(this::watchForChanges, "ShaderHotReload");
            watchThread.setDaemon(true);
            watchThread.start();
            
            logManager.info("ShaderCompiler", "Hot-reload initialized", "directory", shaderDirectory);
            
        } catch (Exception e) {
            logManager.error("ShaderCompiler", "Failed to initialize hot-reload", e);
        }
    }
    
    private void watchForChanges() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.take();
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                        Path changed = (Path) event.context();
                        String filename = changed.toString();
                        
                        if (watchedFiles.contains(filename)) {
                            logManager.info("ShaderCompiler", "Shader file changed, reloading", "file", filename);
                            
                            // Find programs that use this shader
                            for (ShaderProgram program : programs.values()) {
                                if (program.getShaderFiles().values().contains(filename)) {
                                    reloadProgram(program.getName());
                                }
                            }
                        }
                    }
                }
                
                key.reset();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logManager.error("ShaderCompiler", "Error in hot-reload watch thread", e);
            }
        }
    }
    
    private void trackFileForHotReload(String shaderPath) {
        watchedFiles.add(shaderPath);
    }
    
    /**
     * Get shader compiler statistics.
     */
    public ShaderCompilerStatistics getStatistics() {
        return new ShaderCompilerStatistics(
            shadersCompiled,
            programsLinked,
            totalCompileTime / 1_000_000.0, // Convert to milliseconds
            programs.size(),
            shaders.size()
        );
    }
    
    /**
     * Cleanup all shaders and programs.
     */
    public void cleanup() {
        logManager.info("ShaderCompiler", "Cleaning up shader compiler");
        
        // Stop hot-reload
        if (watchThread != null) {
            watchThread.interrupt();
        }
        
        if (watchService != null) {
            try {
                watchService.close();
            } catch (Exception e) {
                logManager.warn("ShaderCompiler", "Error closing watch service", e);
            }
        }
        
        // Cleanup programs
        for (ShaderProgram program : programs.values()) {
            program.cleanup();
        }
        programs.clear();
        
        // Cleanup shaders
        for (CompiledShader shader : shaders.values()) {
            shader.cleanup();
        }
        shaders.clear();
        
        // Clear caches
        shaderSources.clear();
        lastModified.clear();
        includes.clear();
        watchedFiles.clear();
        
        logManager.info("ShaderCompiler", "Shader compiler cleanup complete");
    }
    
    /**
     * Shader compiler statistics.
     */
    public static class ShaderCompilerStatistics {
        public final int shadersCompiled;
        public final int programsLinked;
        public final double totalCompileTimeMs;
        public final int activePrograms;
        public final int activeShaders;
        
        public ShaderCompilerStatistics(int shadersCompiled, int programsLinked, 
                                      double totalCompileTimeMs, int activePrograms, int activeShaders) {
            this.shadersCompiled = shadersCompiled;
            this.programsLinked = programsLinked;
            this.totalCompileTimeMs = totalCompileTimeMs;
            this.activePrograms = activePrograms;
            this.activeShaders = activeShaders;
        }
        
        @Override
        public String toString() {
            return String.format("ShaderStats{compiled=%d, linked=%d, timeMs=%.1f, active=%d/%d}",
                               shadersCompiled, programsLinked, totalCompileTimeMs, 
                               activePrograms, activeShaders);
        }
    }
}