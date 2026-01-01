package engine;

import engine.io.Input;
import engine.io.Window;
import engine.utils.Timer;

public class Engine implements Runnable {

    public static final int TARGET_FPS = 100;
    public static final int TARGET_UPS = 30;

    private final Window window;
    private final Thread gameLoopThread;
    private final Timer timer;
    private final IGameLogic gameLogic;
    private Input input;

    public Engine(String windowTitle, int width, int height, boolean vSync, IGameLogic gameLogic) {
        gameLoopThread = new Thread(this, "GAME_LOOP_THREAD");
        window = new Window(windowTitle, width, height, vSync);
        this.gameLogic = gameLogic;
        this.timer = new Timer();
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
            e.printStackTrace();
            javax.swing.JOptionPane.showMessageDialog(null, "Engine Error: " + e.getMessage(), "Error",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        } finally {
            cleanup();
        }
    }

    protected void init() throws Exception {
        window.init();
        this.input = new Input(window.getWindowHandle());
        timer.init();
        gameLogic.init(window);
    }

    protected void gameLoop() {
        float elapsedTime;
        float accumulator = 0f;
        float interval = 1f / TARGET_UPS;

        int frames = 0;
        float fpsTimer = 0f;
        int currentFPS = 0;

        boolean running = true;
        while (running && !window.windowShouldClose()) {
            elapsedTime = timer.getElapsedTime();
            accumulator += elapsedTime;
            fpsTimer += elapsedTime;

            // FPS counter
            frames++;
            if (fpsTimer >= 1.0f) {
                currentFPS = frames;
                window.setTitle(window.getTitle().split(" \\|")[0] + " | FPS: " + currentFPS);
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
        }
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
        gameLogic.cleanup();
        window.cleanup();
    }
}
