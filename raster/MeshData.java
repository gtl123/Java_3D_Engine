package engine.raster;

public class MeshData {
    public final float[] positions;
    public final float[] textCoords;
    public final float[] normals;
    public final int[] indices;

    public MeshData(float[] positions, float[] textCoords, float[] normals, int[] indices) {
        this.positions = positions;
        this.textCoords = textCoords;
        this.normals = normals;
        this.indices = indices;
    }
}
