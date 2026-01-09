package engine;

import engine.io.Input;
import engine.io.Window;
import engine.logging.LogManager;
import engine.logging.PerformanceMonitor;
import engine.plugins.DefaultPluginManager;
import engine.plugins.PluginException;
import engine.plugins.PluginManager;
import engine.profiler.ProfilerManager;
import engine.profiler.ProfilerIntegration;
import engine.utils.Timer;

import java.nio.file.Paths;

public class Engine implements Runnable {

    public static final int TARGET_FPS = 100;
    public static final int TARGET_UPS = 30;

    private final Window window;
    private final Thread gameLoopThread;
    private final Timer timer;
    private final IGameLogic gameLogic;
    private final LogManager logManager;
    private final PerformanceMonitor performanceMonitor;
    private final PluginManager pluginManager;
    private final ProfilerManager profilerManager;
    private ProfilerIntegration profilerIntegration;
    private Input input;

    public Engine(String windowTitle, int width, int height, boolean vSync, IGameLogic gameLogic) {
        gameLoopThread = new Thread(this, "GAME_LOOP_THREAD");
        window = new Window(windowTitle, width, height, vSync);
        this.gameLogic = gameLogic;
        this.timer = new Timer();
        this.logManager = LogManager.getInstance();
        this.performanceMonitor = logManager.getPerformanceMonitor();
        this.pluginManager = new DefaultPluginManager();
        this.profilerManager = ProfilerManager.getInstance();
    }

    public void start() {
        String osName = System.getProperty("os.name");
        if (osName.contains("Mac")) {
            gameLoopThread.run();
        } else {
            gameLoopThread.start();
        }
    }

    @Override
    public void run() {
        try {
            init();
            gameLoop();
        } catch (Exception e) {
            logManager.fatal("Engine", "Critical engine error", e,
                           "errorType", e.getClass().getSimpleName());
            e.printStackTrace();
            javax.swing.JOptionPane.showMessageDialog(null, "Engine Error: " + e.getMessage(), "Error",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        } finally {
            cleanup();
        }
    }

    protected void init() throws Exception {
        // Initialize logging system first
        logManager.initialize();
        logManager.info("Engine", "Initializing engine",
                       "targetFPS", TARGET_FPS,
                       "targetUPS", TARGET_UPS);
        
        window.init();
        this.input = new Input(window.getWindowHandle());
        timer.init();
        
        // Initialize profiler system
        try {
            profilerManager.initialize();
            profilerIntegration = profilerManager.getProfilerIntegration();
            
            // Enable profiling if configured
            if (profilerManager.getConfiguration().isEnabled()) {
                profilerManager.enable();
                logManager.info("Engine", "Profiler system enabled");
            }
            
            logManager.info("Engine", "Profiler system initialized");
        } catch (Exception e) {
            logManager.error("Engine", "Failed to initialize profiler system", e);
            // Continue without profiling if initialization fails
        }
        
        // Initialize plugin system
        try {
            pluginManager.initialize();
            logManager.info("Engine", "Plugin system initialized");
            
            // Discover and load plugins from plugins directory
            pluginManager.discoverAndLoadPlugins(Paths.get("plugins"))
                .thenRun(() -> {
                    logManager.info("Engine", "Plugins discovered and loaded");
                    // Start all plugins after game logic is initialized
                    pluginManager.startAllPlugins()
                        .thenRun(() -> logManager.info("Engine", "All plugins started"));
                });
        } catch (PluginException e) {
            logManager.error("Engine", "Failed to initialize plugin system", e);
            // Continue without plugins if initialization fails
        }
        
        // Set profiler manager references in other systems
        if (profilerManager.isInitialized()) {
            // Integrate profiler with render system
            if (renderSystem instanceof AdvancedRenderSystem) {
                ((AdvancedRenderSystem) renderSystem).setProfilerManager(profilerManager);
                logManager.debug("Engine", "Profiler integrated with AdvancedRenderSystem");
            }
            
            // Note: ConnectionManager integration would be done when network system is initialized
        }
        
        gameLogic.init(window);
        
        logManager.info("Engine", "Engine initialization complete");
    }

    protected void gameLoop() {
        float elapsedTime;
        float accumulator = 0f;
        float interval = 1f / TARGET_UPS;

        int frames = 0;
        float fpsTimer = 0f;
        int currentFPS = 0;
        long frameStartTime = System.nanoTime();

        logManager.info("Engine", "Starting game loop",
                       "targetFPS", TARGET_FPS,
                       "targetUPS", TARGET_UPS);

        boolean running = true;
        while (running && !window.windowShouldClose()) {
            // Start frame performance monitoring
            performanceMonitor.startFrame();
            
            elapsedTime = timer.getElapsedTime();
            accumulator += elapsedTime;
            fpsTimer += elapsedTime;

            // FPS counter and performance logging
            frames++;
            if (fpsTimer >= 1.0f) {
                currentFPS = frames;
                window.setTitle(window.getTitle().split(" \\|")[0] + " | FPS: " + currentFPS);
                
                // Log performance metrics every second
                logManager.debug("Engine", "Performance metrics",
                               "fps", performanceMonitor.getCurrentFPS(),
                               "avgFrameTime", performanceMonitor.getAverageFrameTime(),
                               "heapUsedMB", performanceMonitor.getHeapUsedMB(),
                               "heapUsagePercent", performanceMonitor.getHeapUsagePercent());
                
                frames = 0;
                fpsTimer = 0;
            }

            input(); // Process input every frame

            while (accumulator >= interval) {
                update(interval);
                accumulator -= interval;
            }

            render();

            if (!window.isResized()) {
                sync();
            }
            
            // Update profiler system
            if (profilerManager.isEnabled()) {
                profilerManager.update(elapsedTime);
            }
            
            // End frame performance monitoring
            performanceMonitor.endFrame();
        }
        
        logManager.info("Engine", "Game loop ended");
    }

    private void sync() {
        float loopSlot = 1f / TARGET_FPS;
        double endTime = timer.getLastLoopTime() + loopSlot;
        while (timer.getTime() < endTime) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException ie) {
            }
        }
    }

    protected void input() {
        input.update(); // Update mouse deltas
        gameLogic.input(window, input);
    }

    protected void update(float interval) {
        gameLogic.update(interval, input);
    }

    protected void render() {
        gameLogic.render(window);
        window.update();
    }

    protected void cleanup() {
        logManager.info("Engine", "Starting engine cleanup");
        
        // Shutdown profiler system first
        try {
            if (profilerManager.isInitialized()) {
                profilerManager.shutdown();
                logManager.info("Engine", "Profiler system shutdown complete");
            }
        } catch (Exception e) {
            logManager.error("Engine", "Error during profiler system shutdown", e);
        }
        
        // Shutdown plugin system
        try {
            pluginManager.shutdown();
            logManager.info("Engine", "Plugin system shutdown complete");
        } catch (Exception e) {
            logManager.error("Engine", "Error during plugin system shutdown", e);
        }
        
        try {
            gameLogic.cleanup();
        } catch (Exception e) {
            logManager.error("Engine", "Error during game logic cleanup", e);
        }
        
        try {
            window.cleanup();
        } catch (Exception e) {
            logManager.error("Engine", "Error during window cleanup", e);
        }
        
        logManager.info("Engine", "Engine cleanup complete");
        logManager.shutdown();
    }
    
    /**
     * Get the plugin manager instance.
     * @return Plugin manager
     */
    public PluginManager getPluginManager() {
        return pluginManager;
    }
    
    /**
     * Get the profiler manager instance.
     * @return Profiler manager
     */
    public ProfilerManager getProfilerManager() {
        return profilerManager;
    }
    
    /**
     * Get the profiler integration instance.
     * @return Profiler integration
     */
    public ProfilerIntegration getProfilerIntegration() {
        return profilerIntegration;
    }
    
    /**
     * Get the performance monitor instance.
     * @return Performance monitor
     */
    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }
}
