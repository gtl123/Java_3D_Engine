package engine.plugins.types;

import engine.gfx.RenderSystem;
import engine.io.Window;
import engine.plugins.Plugin;
import engine.plugins.PluginException;

/**
 * Interface for plugins that extend the rendering system.
 * Allows plugins to add custom rendering passes, shaders, and visual effects.
 */
public interface RenderPlugin extends Plugin {
    
    /**
     * Initialize rendering resources.
     * Called after the plugin is initialized but before rendering starts.
     * @param renderSystem The engine's render system
     * @throws PluginException if initialization fails
     */
    void initializeRendering(RenderSystem renderSystem) throws PluginException;
    
    /**
     * Called before the main rendering loop starts each frame.
     * @param window Game window
     * @param deltaTime Time since last frame
     */
    void preRender(Window window, float deltaTime);
    
    /**
     * Called during the main rendering phase.
     * @param window Game window
     * @param deltaTime Time since last frame
     */
    void render(Window window, float deltaTime);
    
    /**
     * Called after the main rendering loop completes each frame.
     * @param window Game window
     * @param deltaTime Time since last frame
     */
    void postRender(Window window, float deltaTime);
    
    /**
     * Cleanup rendering resources.
     * Called when the plugin is being unloaded.
     */
    void cleanupRendering();
    
    /**
     * Get the rendering priority for this plugin.
     * Higher priority plugins render first.
     * @return Rendering priority
     */
    int getRenderPriority();
    
    /**
     * Check if this plugin supports a specific rendering feature.
     * @param feature Feature name to check
     * @return true if feature is supported
     */
    boolean supportsFeature(String feature);
    
    /**
     * Get the shader programs provided by this plugin.
     * @return Array of shader program names
     */
    String[] getProvidedShaders();
    
    /**
     * Get a custom shader program by name.
     * @param shaderName Name of the shader program
     * @return Shader program or null if not found
     */
    Object getShaderProgram(String shaderName);
    
    /**
     * Called when the window is resized.
     * @param window Game window
     * @param newWidth New window width
     * @param newHeight New window height
     */
    void onWindowResize(Window window, int newWidth, int newHeight);
    
    /**
     * Check if this plugin requires specific OpenGL extensions.
     * @return Array of required OpenGL extension names
     */
    String[] getRequiredOpenGLExtensions();
    
    /**
     * Get the minimum OpenGL version required by this plugin.
     * @return OpenGL version string (e.g., "3.3")
     */
    String getMinimumOpenGLVersion();
}