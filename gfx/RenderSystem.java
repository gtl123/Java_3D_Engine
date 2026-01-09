package engine.gfx;

import engine.io.Window;
import engine.logging.LogManager;
import engine.logging.PerformanceMonitor;
import engine.logging.MetricsCollector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Manages and executes a stack of render passes.
 * This is the core of the engine's rendering pipeline.
 */
public class RenderSystem {
    private final List<RenderPass> passes;
    private final LogManager logManager;
    private final PerformanceMonitor performanceMonitor;
    private final MetricsCollector metricsCollector;
    private boolean needsSort = false;

    public RenderSystem() {
        this.passes = new ArrayList<>();
        this.logManager = LogManager.getInstance();
        this.performanceMonitor = logManager.getPerformanceMonitor();
        this.metricsCollector = logManager.getMetricsCollector();
    }

    /**
     * Adds a render pass to the system
     *
     * @param pass The pass to add
     */
    public void addPass(RenderPass pass) {
        passes.add(pass);
        
        try {
            pass.initialize();
            logManager.info("RenderSystem", "Render pass added",
                           "passClass", pass.getClass().getSimpleName(),
                           "priority", pass.getPriority());
        } catch (Exception e) {
            logManager.error("RenderSystem", "Failed to initialize render pass", e,
                           "passClass", pass.getClass().getSimpleName());
            passes.remove(pass);
            throw e;
        }
        
        needsSort = true;
        metricsCollector.incrementCounter("renderSystem.passesAdded");
    }

    /**
     * Removes a render pass from the system
     *
     * @param pass The pass to remove
     */
    public void removePass(RenderPass pass) {
        if (passes.remove(pass)) {
            try {
                pass.cleanup();
                logManager.info("RenderSystem", "Render pass removed",
                               "passClass", pass.getClass().getSimpleName());
            } catch (Exception e) {
                logManager.error("RenderSystem", "Error cleaning up render pass", e,
                               "passClass", pass.getClass().getSimpleName());
            }
            metricsCollector.incrementCounter("renderSystem.passesRemoved");
        }
    }

    /**
     * Renders all passes in priority order
     *
     * @param window    The game window
     * @param deltaTime Time since last frame
     */
    public void renderAll(Window window, float deltaTime) {
        try (var renderTimer = metricsCollector.startTimer("renderSystem.totalRenderTime")) {
            if (needsSort) {
                passes.sort(Comparator.comparingInt(RenderPass::getPriority));
                needsSort = false;
                logManager.debug("RenderSystem", "Render passes sorted",
                               "passCount", passes.size());
            }

            metricsCollector.setGauge("renderSystem.activePasses", passes.size());
            
            for (RenderPass pass : passes) {
                String passName = pass.getClass().getSimpleName();
                
                try (var passTimer = metricsCollector.startTimer("renderSystem.pass." + passName)) {
                    performanceMonitor.recordRenderCall();
                    pass.render(window, deltaTime);
                    metricsCollector.incrementCounter("renderSystem.passExecutions");
                } catch (Exception e) {
                    logManager.error("RenderSystem", "Error in render pass", e,
                                   "passClass", passName,
                                   "deltaTime", deltaTime);
                    metricsCollector.incrementCounter("renderSystem.passErrors");
                    // Continue with other passes even if one fails
                }
            }
        } catch (Exception e) {
            logManager.error("RenderSystem", "Critical error in render system", e,
                           "deltaTime", deltaTime);
            metricsCollector.incrementCounter("renderSystem.criticalErrors");
        }
    }

    /**
     * Cleans up all render passes
     */
    public void cleanup() {
        logManager.info("RenderSystem", "Starting render system cleanup",
                       "passCount", passes.size());
        
        int cleanupErrors = 0;
        for (RenderPass pass : passes) {
            try {
                pass.cleanup();
            } catch (Exception e) {
                cleanupErrors++;
                logManager.error("RenderSystem", "Error cleaning up render pass", e,
                               "passClass", pass.getClass().getSimpleName());
            }
        }
        
        passes.clear();
        
        if (cleanupErrors > 0) {
            logManager.warn("RenderSystem", "Render system cleanup completed with errors",
                           "errorCount", cleanupErrors);
        } else {
            logManager.info("RenderSystem", "Render system cleanup completed successfully");
        }
    }
}
