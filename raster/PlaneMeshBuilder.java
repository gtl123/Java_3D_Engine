package engine.raster;

public class PlaneMeshBuilder {
    public static Mesh createPlane(float width, float height) {
        float halfWidth = width / 2.0f;
        float halfHeight = height / 2.0f;

        float[] positions = new float[]{
            -halfWidth,  halfHeight, 0.0f,
            -halfWidth, -halfHeight, 0.0f,
             halfWidth, -halfHeight, 0.0f,
             halfWidth,  halfHeight, 0.0f,
        };
        float[] textCoords = new float[]{
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f,
        };
        float[] normals = new float[]{
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f,
        };
        int[] indices = new int[]{
            0, 1, 3, 3, 1, 2
        };

        return new Mesh(positions, textCoords, normals, indices);
    }
}
