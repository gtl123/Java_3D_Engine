package game.voxel;

import engine.entity.BrowserEntity;
import engine.io.Window;
import engine.raster.Mesh;
import engine.raster.Texture;
import org.joml.Vector3f;

/**
 * TestBrowserDisplay: Creates and manages a visible browser display in the world
 * This allows testing of the browser rendering system on a 3D plane mesh
 */
public class TestBrowserDisplay {
    
    private BrowserEntity browserEntity;
    private boolean isInitialized = false;
    
    public TestBrowserDisplay() {
    }
    
    /**
     * Initialize the test browser display in the world
     * Places it at a fixed location visible from spawn
     */
    public void initialize() {
        try {
            // Create a browser entity that displays the menu system
            // URL points to the local menu server
            browserEntity = new BrowserEntity(
                "https://3000-i36dxtoer9b9r2w1arbem-29b75f94.us2.manus.computer/",
                4.0f,  // width in world units
                3.0f   // height in world units
            );
            
            // Position it in the world at a visible location
            // Place it above ground, in front of the player spawn
            browserEntity.setPosition(0, 70, 10);  // x, y, z
            browserEntity.setRotation(0, 0, 0);    // No rotation initially
            
            isInitialized = true;
            System.out.println("Test browser display initialized at position (0, 70, 10)");
        } catch (Exception e) {
            System.err.println("Failed to initialize test browser display: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get the browser entity for rendering
     */
    public BrowserEntity getBrowserEntity() {
        return browserEntity;
    }
    
    /**
     * Check if the browser display is initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * Update the browser display (for animations, interactions, etc.)
     */
    public void update(float deltaTime) {
        if (!isInitialized || browserEntity == null) {
            return;
        }
        
        // Optional: Add gentle rotation for visual interest
        Vector3f currentRot = new Vector3f(0, 0, 0);
        currentRot.y += deltaTime * 0.1f;  // Slow rotation
        // browserEntity.setRotation(currentRot.x, currentRot.y, currentRot.z);
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        if (browserEntity != null) {
            browserEntity.cleanup();
            browserEntity = null;
        }
        isInitialized = false;
    }
    
    /**
     * Create a simple test plane without browser for debugging
     */
    public static Mesh createTestPlane(float width, float height) {
        float halfW = width / 2.0f;
        float halfH = height / 2.0f;
        
        float[] positions = new float[] {
            -halfW, halfH, 0,   // top-left
            -halfW, -halfH, 0,  // bottom-left
            halfW, -halfH, 0,   // bottom-right
            halfW, halfH, 0     // top-right
        };
        
        float[] textCoords = new float[] {
            0, 0,
            0, 1,
            1, 1,
            1, 0
        };
        
        float[] normals = new float[] {
            0, 0, 1,
            0, 0, 1,
            0, 0, 1,
            0, 0, 1
        };
        
        int[] indices = new int[] {
            0, 1, 2,
            2, 3, 0
        };
        
        return new Mesh(positions, textCoords, normals, indices);
    }
}
