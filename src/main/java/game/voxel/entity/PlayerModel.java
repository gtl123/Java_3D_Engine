package game.voxel.entity;

import engine.raster.Mesh;
import org.joml.Vector3f;

/**
 * PlayerModel: Creates a realistic humanoid player mesh with proper proportions
 * Replaces the simple cube with a more detailed player character
 */
public class PlayerModel {
    
    /**
     * Create a humanoid player model with head, body, arms, and legs
     */
    public static Mesh createPlayerMesh() {
        // Build a simple but recognizable humanoid figure
        // Using cube primitives positioned to form a character
        
        float[] positions = new float[]{
            // Head (cube at top)
            -0.2f, 0.6f, -0.2f,  // 0
            0.2f, 0.6f, -0.2f,   // 1
            0.2f, 1.0f, -0.2f,   // 2
            -0.2f, 1.0f, -0.2f,  // 3
            -0.2f, 0.6f, 0.2f,   // 4
            0.2f, 0.6f, 0.2f,    // 5
            0.2f, 1.0f, 0.2f,    // 6
            -0.2f, 1.0f, 0.2f,   // 7
            
            // Body (larger cube in middle)
            -0.25f, 0.2f, -0.15f,  // 8
            0.25f, 0.2f, -0.15f,   // 9
            0.25f, 0.6f, -0.15f,   // 10
            -0.25f, 0.6f, -0.15f,  // 11
            -0.25f, 0.2f, 0.15f,   // 12
            0.25f, 0.2f, 0.15f,    // 13
            0.25f, 0.6f, 0.15f,    // 14
            -0.25f, 0.6f, 0.15f,   // 15
            
            // Left arm
            -0.35f, 0.4f, -0.1f,   // 16
            -0.5f, 0.4f, -0.1f,    // 17
            -0.5f, 0.2f, -0.1f,    // 18
            -0.35f, 0.2f, -0.1f,   // 19
            -0.35f, 0.4f, 0.1f,    // 20
            -0.5f, 0.4f, 0.1f,     // 21
            -0.5f, 0.2f, 0.1f,     // 22
            -0.35f, 0.2f, 0.1f,    // 23
            
            // Right arm
            0.35f, 0.4f, -0.1f,    // 24
            0.5f, 0.4f, -0.1f,     // 25
            0.5f, 0.2f, -0.1f,     // 26
            0.35f, 0.2f, -0.1f,    // 27
            0.35f, 0.4f, 0.1f,     // 28
            0.5f, 0.4f, 0.1f,      // 29
            0.5f, 0.2f, 0.1f,      // 30
            0.35f, 0.2f, 0.1f,     // 31
            
            // Left leg
            -0.15f, 0.0f, -0.1f,   // 32
            -0.05f, 0.0f, -0.1f,   // 33
            -0.05f, 0.2f, -0.1f,   // 34
            -0.15f, 0.2f, -0.1f,   // 35
            -0.15f, 0.0f, 0.1f,    // 36
            -0.05f, 0.0f, 0.1f,    // 37
            -0.05f, 0.2f, 0.1f,    // 38
            -0.15f, 0.2f, 0.1f,    // 39
            
            // Right leg
            0.05f, 0.0f, -0.1f,    // 40
            0.15f, 0.0f, -0.1f,    // 41
            0.15f, 0.2f, -0.1f,    // 42
            0.05f, 0.2f, -0.1f,    // 43
            0.05f, 0.0f, 0.1f,     // 44
            0.15f, 0.0f, 0.1f,     // 45
            0.15f, 0.2f, 0.1f,     // 46
            0.05f, 0.2f, 0.1f      // 47
        };
        
        float[] textCoords = new float[positions.length / 3 * 2];
        for (int i = 0; i < textCoords.length; i += 2) {
            textCoords[i] = (i / 2) % 2 == 0 ? 0.0f : 1.0f;
            textCoords[i + 1] = ((i / 2) / 2) % 2 == 0 ? 0.0f : 1.0f;
        }
        
        float[] normals = new float[positions.length];
        for (int i = 0; i < normals.length; i += 3) {
            normals[i] = 0.0f;
            normals[i + 1] = 1.0f;
            normals[i + 2] = 0.0f;
        }
        
        // Create indices for all cube faces
        int[] indices = createCubeIndices();
        
        return new Mesh(positions, textCoords, normals, indices);
    }
    
    /**
     * Create indices for a cube mesh
     */
    private static int[] createCubeIndices() {
        // Simple cube indices (6 faces, 2 triangles per face)
        int[] indices = new int[]{
            // Head
            0, 1, 2, 2, 3, 0,
            4, 6, 5, 4, 7, 6,
            0, 4, 5, 5, 1, 0,
            2, 6, 7, 7, 3, 2,
            0, 3, 7, 7, 4, 0,
            1, 5, 6, 6, 2, 1,
            
            // Body
            8, 9, 10, 10, 11, 8,
            12, 14, 13, 12, 15, 14,
            8, 12, 13, 13, 9, 8,
            10, 14, 15, 15, 11, 10,
            8, 11, 15, 15, 12, 8,
            9, 13, 14, 14, 10, 9,
            
            // Left arm
            16, 17, 18, 18, 19, 16,
            20, 22, 21, 20, 23, 22,
            16, 20, 21, 21, 17, 16,
            18, 22, 23, 23, 19, 18,
            16, 19, 23, 23, 20, 16,
            17, 21, 22, 22, 18, 17,
            
            // Right arm
            24, 25, 26, 26, 27, 24,
            28, 30, 29, 28, 31, 30,
            24, 28, 29, 29, 25, 24,
            26, 30, 31, 31, 27, 26,
            24, 27, 31, 31, 28, 24,
            25, 29, 30, 30, 26, 25,
            
            // Left leg
            32, 33, 34, 34, 35, 32,
            36, 38, 37, 36, 39, 38,
            32, 36, 37, 37, 33, 32,
            34, 38, 39, 39, 35, 34,
            32, 35, 39, 39, 36, 32,
            33, 37, 38, 38, 34, 33,
            
            // Right leg
            40, 41, 42, 42, 43, 40,
            44, 46, 45, 44, 47, 46,
            40, 44, 45, 45, 41, 40,
            42, 46, 47, 47, 43, 42,
            40, 43, 47, 47, 44, 40,
            41, 45, 46, 46, 42, 41
        };
        
        return indices;
    }
    
    /**
     * Create a simple walking animation offset
     */
    public static Vector3f getWalkingOffset(float time, float speed) {
        float legSwing = (float) Math.sin(time * speed * 4.0f) * 0.1f;
        float armSwing = (float) Math.sin(time * speed * 4.0f + Math.PI) * 0.1f;
        float bounce = (float) Math.abs(Math.sin(time * speed * 2.0f)) * 0.05f;
        
        return new Vector3f(0, bounce, 0);
    }
    
    /**
     * Create a jumping animation offset
     */
    public static Vector3f getJumpingOffset(float jumpTime) {
        if (jumpTime < 0.5f) {
            // Going up
            float t = jumpTime / 0.5f;
            return new Vector3f(0, t * 0.5f, 0);
        } else {
            // Coming down
            float t = (jumpTime - 0.5f) / 0.5f;
            return new Vector3f(0, 0.5f * (1.0f - t), 0);
        }
    }
}
