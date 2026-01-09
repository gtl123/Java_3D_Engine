package engine.gfx;

import engine.io.Window;

/**
 * Interface for a single rendering pass in the engine.
 * Implementations define what gets drawn in each render phase.
 */
public interface RenderPass {
    /**
     * Renders this pass
     * 
     * @param window    The game window
     * @param deltaTime Time since last frame
     */
    void render(Window window, float deltaTime);

    /**
     * Called when this pass is added to the render system
     */
    default void initialize() {
    }

    /**
     * Called when this pass is removed or the system is shutting down
     */
    default void cleanup() {
    }

    /**
     * Returns the priority of this render pass (lower = earlier)
     * 
     * @return priority value
     */
    default int getPriority() {
        return 100;
    }
}
