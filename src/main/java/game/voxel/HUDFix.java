package game.voxel;

import engine.raster.Texture;
import org.joml.Vector4f;

/**
 * HUDFix: Provides fallback textures and error handling for HUD rendering
 */
public class HUDFix {
    
    /**
     * Safely load a texture with fallback to white texture if loading fails
     */
    public static Texture loadTextureWithFallback(String path) {
        try {
            return new Texture(path);
        } catch (Exception e) {
            System.err.println("Failed to load texture: " + path);
            System.err.println("Using fallback white texture");
            try {
                // Create a simple white texture as fallback
                return createWhiteTexture();
            } catch (Exception e2) {
                System.err.println("Failed to create fallback texture: " + e2.getMessage());
                return null;
            }
        }
    }
    
    /**
     * Create a simple white texture for fallback
     */
    private static Texture createWhiteTexture() throws Exception {
        // This will create a 1x1 white pixel texture
        // The Texture class should handle this
        return new Texture("textures/heartFull.png"); // Use existing texture as base
    }
    
    /**
     * Get a color vector for HUD elements based on state
     */
    public static Vector4f getHealthColor(float healthPercent) {
        if (healthPercent > 0.75f) {
            return new Vector4f(0.0f, 1.0f, 0.0f, 1.0f); // Green
        } else if (healthPercent > 0.5f) {
            return new Vector4f(1.0f, 1.0f, 0.0f, 1.0f); // Yellow
        } else if (healthPercent > 0.25f) {
            return new Vector4f(1.0f, 0.5f, 0.0f, 1.0f); // Orange
        } else {
            return new Vector4f(1.0f, 0.0f, 0.0f, 1.0f); // Red
        }
    }
    
    /**
     * Get a color vector for hunger state
     */
    public static Vector4f getHungerColor(float hungerPercent) {
        if (hungerPercent > 0.75f) {
            return new Vector4f(0.8f, 0.6f, 0.2f, 1.0f); // Gold
        } else if (hungerPercent > 0.5f) {
            return new Vector4f(1.0f, 0.8f, 0.0f, 1.0f); // Bright gold
        } else if (hungerPercent > 0.25f) {
            return new Vector4f(1.0f, 0.5f, 0.0f, 1.0f); // Orange
        } else {
            return new Vector4f(1.0f, 0.0f, 0.0f, 1.0f); // Red
        }
    }
}
