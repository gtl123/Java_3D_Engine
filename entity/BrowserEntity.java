package engine.entity;

import engine.io.WebBrowser;
import engine.raster.Mesh;
import engine.raster.PlaneMeshBuilder;
import engine.raster.Texture;
import engine.security.InputValidator;

public class BrowserEntity extends Entity {
    private WebBrowser browser;

    public BrowserEntity(String url, float width, float height) {
        super(PlaneMeshBuilder.createPlane(width, height));
        
        // Validate URL for security before creating browser
        String validatedUrl = InputValidator.validateUrl(url);
        this.browser = new WebBrowser(validatedUrl, 1024, 1024);
        
        // Set the browser texture on the mesh
        Texture texture = new Texture(browser.getTextureId());
        this.getMesh().setTexture(texture);
    }

    public void setPosition(float x, float y, float z) {
        this.setLocalPosition(x, y, z);
    }

    public void setRotation(float x, float y, float z) {
        this.setLocalRotation(x, y, z);
    }

    public void cleanup() {
        browser.cleanup();
        getMesh().cleanup();
    }
}
