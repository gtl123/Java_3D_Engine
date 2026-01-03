package engine.entity;

import engine.io.WebBrowser;
import engine.raster.Mesh;
import engine.raster.PlaneMeshBuilder;
import engine.raster.Texture;

public class BrowserEntity extends Entity {
    private WebBrowser browser;
    private Mesh mesh;

    public BrowserEntity(String url, float width, float height) {
        super();
        this.browser = new WebBrowser(url, 1024, 1024);
        this.mesh = PlaneMeshBuilder.createPlane(width, height);
        // We need a way to set the texture ID directly or wrap it in a Texture object
        Texture texture = new Texture(browser.getTextureId());
        this.mesh.setTexture(texture);
    }

    public Mesh getMesh() {
        return mesh;
    }

    public void cleanup() {
        browser.cleanup();
        mesh.cleanup();
    }
}
