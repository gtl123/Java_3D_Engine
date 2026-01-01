package engine.gfx;

import engine.io.Window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Manages and executes a stack of render passes.
 * This is the core of the engine's rendering pipeline.
 */
public class RenderSystem {
    private final List<RenderPass> passes;
    private boolean needsSort = false;

    public RenderSystem() {
        this.passes = new ArrayList<>();
    }

    /**
     * Adds a render pass to the system
     * 
     * @param pass The pass to add
     */
    public void addPass(RenderPass pass) {
        passes.add(pass);
        pass.initialize();
        needsSort = true;
    }

    /**
     * Removes a render pass from the system
     * 
     * @param pass The pass to remove
     */
    public void removePass(RenderPass pass) {
        if (passes.remove(pass)) {
            pass.cleanup();
        }
    }

    /**
     * Renders all passes in priority order
     * 
     * @param window    The game window
     * @param deltaTime Time since last frame
     */
    public void renderAll(Window window, float deltaTime) {
        if (needsSort) {
            passes.sort(Comparator.comparingInt(RenderPass::getPriority));
            needsSort = false;
        }

        for (RenderPass pass : passes) {
            pass.render(window, deltaTime);
        }
    }

    /**
     * Cleans up all render passes
     */
    public void cleanup() {
        for (RenderPass pass : passes) {
            pass.cleanup();
        }
        passes.clear();
    }
}
