package game.voxel.gfx;

import engine.gfx.RenderPass;
import engine.io.Window;
import game.menu.MenuManager;
import game.voxel.HUD;

/**
 * Renders UI elements (HUD and menus).
 */
public class UIRenderPass implements RenderPass {
    private final MenuManager menuManager;
    private final HUD hud;

    public UIRenderPass(MenuManager menuManager, HUD hud) {
        this.menuManager = menuManager;
        this.hud = hud;
    }

    @Override
    public void render(Window window, float deltaTime) {
        if (menuManager.isInMenu()) {
            menuManager.render(window);
        } else {
            hud.render(window);
        }
    }

    @Override
    public int getPriority() {
        return 100; // Render UI last
    }

    @Override
    public void cleanup() {
        // HUD and menus are managed elsewhere
    }
}
