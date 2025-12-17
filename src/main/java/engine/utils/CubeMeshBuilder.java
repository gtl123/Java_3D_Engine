package engine.utils;


import engine.graph.Mesh;


public class CubeMeshBuilder {
    public static Mesh createCube(float width, float height, float depth) {
        float hw = width / 2f;
        float hh = height / 2f;
        float hd = depth / 2f;

        // 24 vertices (4 per face × 6 faces)
        float[] positions = {
                // Front
                -hw, -hh,  hd,   hw, -hh,  hd,   hw,  hh,  hd,   -hw,  hh,  hd,
                // Back
                -hw, -hh, -hd,   -hw,  hh, -hd,   hw,  hh, -hd,   hw, -hh, -hd,
                // Left
                -hw, -hh, -hd,   -hw, -hh,  hd,   -hw,  hh,  hd,   -hw,  hh, -hd,
                // Right
                hw, -hh, -hd,    hw,  hh, -hd,    hw,  hh,  hd,    hw, -hh,  hd,
                // Top
                -hw,  hh, -hd,   -hw,  hh,  hd,    hw,  hh,  hd,    hw,  hh, -hd,
                // Bottom
                -hw, -hh, -hd,    hw, -hh, -hd,    hw, -hh,  hd,   -hw, -hh,  hd
        };

        // Simple UVs (each face gets full texture)
        float[] texCoords = {
                // Front
                0,0, 1,0, 1,1, 0,1,
                // Back
                0,0, 1,0, 1,1, 0,1,
                // Left
                0,0, 1,0, 1,1, 0,1,
                // Right
                0,0, 1,0, 1,1, 0,1,
                // Top
                0,0, 1,0, 1,1, 0,1,
                // Bottom
                0,0, 1,0, 1,1, 0,1
        };

        // Normals per face
        float[] normals = {
                // Front
                0,0,1, 0,0,1, 0,0,1, 0,0,1,
                // Back
                0,0,-1, 0,0,-1, 0,0,-1, 0,0,-1,
                // Left
                -1,0,0, -1,0,0, -1,0,0, -1,0,0,
                // Right
                1,0,0, 1,0,0, 1,0,0, 1,0,0,
                // Top
                0,1,0, 0,1,0, 0,1,0, 0,1,0,
                // Bottom
                0,-1,0, 0,-1,0, 0,-1,0, 0,-1,0
        };

        int[] indices = {
                0,1,2, 2,3,0,      // Front
                4,5,6, 6,7,4,      // Back
                8,9,10, 10,11,8,   // Left
                12,13,14, 14,15,12,// Right
                16,17,18, 18,19,16,// Top
                20,21,22, 22,23,20 // Bottom
        };

        return new Mesh(positions, texCoords, normals, indices);
    }
}
