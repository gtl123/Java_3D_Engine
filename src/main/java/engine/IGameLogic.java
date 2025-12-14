package engine;

import engine.io.Input;
import engine.io.Window;

public interface IGameLogic {

    void init(Window window) throws Exception;

    void input(Window window, Input input);

    void update(float interval, Input input);

    void render(Window window);

    void cleanup();
}
