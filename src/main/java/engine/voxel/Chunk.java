package engine.voxel;

import engine.graph.Mesh;
import engine.graph.Texture;

import java.util.ArrayList;
import java.util.List;

public class Chunk {

    public static final int SIZE_X = 16;
    public static final int SIZE_Y = 256;
    public static final int SIZE_Z = 16;

    private final int chunkX;
    private final int chunkZ;
    private final Block[][][] blocks;
    private Mesh mesh;
    private boolean dirty;

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blocks = new Block[SIZE_X][SIZE_Y][SIZE_Z];
        this.dirty = true;

        for (int x = 0; x < SIZE_X; x++) {
            for (int y = 0; y < SIZE_Y; y++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    blocks[x][y][z] = Block.AIR;
                }
            }
        }
    }

    public void setBlock(int x, int y, int z, Block block) {
        if (x >= 0 && x < SIZE_X && y >= 0 && y < SIZE_Y && z >= 0 && z < SIZE_Z) {
            blocks[x][y][z] = block;
            dirty = true;
        }
    }

    public Block getBlock(int x, int y, int z) {
        if (x >= 0 && x < SIZE_X && y >= 0 && y < SIZE_Y && z >= 0 && z < SIZE_Z) {
            return blocks[x][y][z];
        }
        return Block.AIR;
    }

    public void updateMesh(Texture texture) {
        if (!dirty && mesh != null)
            return;

        List<Float> positions = new ArrayList<>();
        List<Float> textCoords = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        int indexOffset = 0;
        float textureStep = 1.0f / 4.0f;

        // Greedy Meshing: Process each axis
        for (int axis = 0; axis < 3; axis++) {
            int u = (axis + 1) % 3;
            int v = (axis + 2) % 3;

            int[] x = new int[3];
            int[] q = new int[3];
            int[] dims = new int[] { SIZE_X, SIZE_Y, SIZE_Z };

            q[axis] = 1;
            Block[] mask = new Block[dims[u] * dims[v]];

            // Iterate through slices
            for (x[axis] = -1; x[axis] < dims[axis];) {
                int n = 0;

                // Build mask
                for (x[v] = 0; x[v] < dims[v]; x[v]++) {
                    for (x[u] = 0; x[u] < dims[u]; x[u]++) {
                        Block current = (x[axis] >= 0) ? blocks[x[0]][x[1]][x[2]] : Block.AIR;
                        Block next = (x[axis] < dims[axis] - 1) ? blocks[x[0] + q[0]][x[1] + q[1]][x[2] + q[2]]
                                : Block.AIR;

                        boolean currentSolid = current != Block.AIR && !current.isTransparent();
                        boolean nextSolid = next != Block.AIR && !next.isTransparent();

                        if (currentSolid == nextSolid) {
                            mask[n++] = null;
                        } else if (currentSolid) {
                            mask[n++] = current;
                        } else {
                            mask[n++] = next;
                        }
                    }
                }

                x[axis]++;
                n = 0;

                // Generate mesh from mask
                for (int j = 0; j < dims[v]; j++) {
                    for (int i = 0; i < dims[u];) {
                        if (mask[n] != null) {
                            Block block = mask[n];

                            // Compute width
                            int w;
                            for (w = 1; i + w < dims[u] && mask[n + w] == block; w++)
                                ;

                            // Compute height
                            boolean done = false;
                            int h;
                            for (h = 1; j + h < dims[v]; h++) {
                                for (int k = 0; k < w; k++) {
                                    if (mask[n + k + h * dims[u]] != block) {
                                        done = true;
                                        break;
                                    }
                                }
                                if (done)
                                    break;
                            }

                            // Add quad
                            x[u] = i;
                            x[v] = j;

                            int[] du = new int[3];
                            du[u] = w;
                            int[] dv = new int[3];
                            dv[v] = h;

                            // Determine face direction
                            Block checkCurrent = (x[axis] - 1 >= 0) ? blocks[x[0] - q[0]][x[1] - q[1]][x[2] - q[2]]
                                    : Block.AIR;
                            boolean backFace = (checkCurrent != Block.AIR && !checkCurrent.isTransparent());

                            // Texture coordinates - DO NOT scale by w/h!
                            float u0 = block.getAtlasX() * textureStep;
                            float v0 = block.getAtlasY() * textureStep;

                            // Quad vertices
                            float[] v1Pos = new float[] { x[0], x[1], x[2] };
                            float[] v2Pos = new float[] { x[0] + du[0], x[1] + du[1], x[2] + du[2] };
                            float[] v3Pos = new float[] { x[0] + du[0] + dv[0], x[1] + du[1] + dv[1],
                                    x[2] + du[2] + dv[2] };
                            float[] v4Pos = new float[] { x[0] + dv[0], x[1] + dv[1], x[2] + dv[2] };

                            float nx = 0, ny = 0, nz = 0;
                            if (backFace) {
                                nx = q[0];
                                ny = q[1];
                                nz = q[2];
                                addQuad(positions, textCoords, normals, indices, indexOffset,
                                        v1Pos, v2Pos, v3Pos, v4Pos,
                                        u0, v0, u0, v0, nx, ny, nz);
                            } else {
                                nx = -q[0];
                                ny = -q[1];
                                nz = -q[2];
                                addQuad(positions, textCoords, normals, indices, indexOffset,
                                        v1Pos, v4Pos, v3Pos, v2Pos,
                                        u0, v0, u0, v0, nx, ny, nz);
                            }
                            indexOffset += 4;

                            // Clear mask
                            for (int l = 0; l < h; l++) {
                                for (int k = 0; k < w; k++) {
                                    mask[n + k + l * dims[u]] = null;
                                }
                            }

                            i += w;
                            n += w;
                        } else {
                            i++;
                            n++;
                        }
                    }
                }
            }
        }

        float[] posArr = new float[positions.size()];
        for (int i = 0; i < positions.size(); i++)
            posArr[i] = positions.get(i);
        float[] texArr = new float[textCoords.size()];
        for (int i = 0; i < textCoords.size(); i++)
            texArr[i] = textCoords.get(i);
        float[] normArr = new float[normals.size()];
        for (int i = 0; i < normals.size(); i++)
            normArr[i] = normals.get(i);
        int[] indArr = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++)
            indArr[i] = indices.get(i);

        if (mesh != null)
            mesh.cleanup();
        mesh = new Mesh(posArr, texArr, normArr, indArr);
        mesh.setTexture(texture);
        dirty = false;
    }

    private void addQuad(List<Float> p, List<Float> t, List<Float> n, List<Integer> i, int offset,
            float[] v1, float[] v2, float[] v3, float[] v4,
            float u0, float tv0, float u1, float tv1,
            float nx, float ny, float nz) {
        // v1
        p.add(v1[0]);
        p.add(v1[1]);
        p.add(v1[2]);
        t.add(u0);
        t.add(tv1);
        n.add(nx);
        n.add(ny);
        n.add(nz);

        // v2
        p.add(v2[0]);
        p.add(v2[1]);
        p.add(v2[2]);
        t.add(u1);
        t.add(tv1);
        n.add(nx);
        n.add(ny);
        n.add(nz);

        // v3
        p.add(v3[0]);
        p.add(v3[1]);
        p.add(v3[2]);
        t.add(u1);
        t.add(tv0);
        n.add(nx);
        n.add(ny);
        n.add(nz);

        // v4
        p.add(v4[0]);
        p.add(v4[1]);
        p.add(v4[2]);
        t.add(u0);
        t.add(tv0);
        n.add(nx);
        n.add(ny);
        n.add(nz);

        i.add(offset + 0);
        i.add(offset + 1);
        i.add(offset + 2);
        i.add(offset + 2);
        i.add(offset + 3);
        i.add(offset + 0);
    }

    public Mesh getMesh() {
        return mesh;
    }

    public void cleanup() {
        if (mesh != null)
            mesh.cleanup();
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public Block[][][] getBlocks() {
        return blocks;
    }
}
