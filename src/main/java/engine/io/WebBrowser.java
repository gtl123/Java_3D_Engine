package engine.io;

import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefRenderHandler;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandlerAdapter;

import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.opengl.GL11.*;

public class WebBrowser {
    private CefApp cefApp;
    private CefClient client;
    private CefBrowser browser;
    private int textureId;
    private int width, height;
    private ByteBuffer buffer;

    public WebBrowser(String url, int width, int height) {
        this.width = width;
        this.height = height;
        this.buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());

        try {
            CefAppBuilder builder = new CefAppBuilder();
            builder.setInstallDir(new java.io.File("jcef-bundle"));
            builder.setAppHandler(new MavenCefAppHandlerAdapter() {});
            
            cefApp = builder.build();
            client = cefApp.createClient();
            
            CefRenderHandler renderHandler = new CefRenderHandlerAdapter() {
                @Override
                public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
                    WebBrowser.this.buffer.clear();
                    WebBrowser.this.buffer.put(buffer);
                    WebBrowser.this.buffer.flip();
                    updateTexture();
                }

                @Override
                public boolean getViewRect(CefBrowser browser, Rectangle rect) {
                    rect.setBounds(0, 0, WebBrowser.this.width, WebBrowser.this.height);
                    return true;
                }

                @Override
                public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
                    return viewPoint;
                }
            };

            browser = client.createBrowser(url, true, false);
            // browser.setRenderHandler(renderHandler); // This might need a specific OSR browser creation
            
            initTexture();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initTexture() {
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_BGRA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private void updateTexture() {
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_BGRA, GL_UNSIGNED_BYTE, buffer);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public int getTextureId() {
        return textureId;
    }

    public void cleanup() {
        if (browser != null) browser.close(true);
        if (client != null) client.dispose();
        if (cefApp != null) cefApp.dispose();
        glDeleteTextures(textureId);
    }
}
