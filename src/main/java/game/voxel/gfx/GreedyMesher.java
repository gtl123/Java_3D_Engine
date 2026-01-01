package game.voxel.gfx;

import engine.raster.MeshData;
import engine.raster.Texture;
import game.voxel.Block;
import game.voxel.Chunk;
import game.voxel.ChunkManager;

import java.util.ArrayList;
import java.util.List;

public class GreedyMesher {

    public static MeshData generateMeshData(Chunk chunk, Texture texture, ChunkManager chunkManager) {
        return generateMeshData(chunk, texture, chunkManager, 0);
    }

    public static MeshData generateMeshData(Chunk chunk, Texture texture, ChunkManager chunkManager, int lod) {
        int step = 1 << lod;
        List<Float> positions = new ArrayList<>();
        List<Float> textCoords = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        int indexOffset = 0;
        float textureStep = 1.0f / 4.0f;

        Block[][][] blocks = chunk.getBlocks();

        // Greedy Meshing: Process each axis
        for (int axis = 0; axis < 3; axis++) {
            int u = (axis + 1) % 3;
            int v = (axis + 2) % 3;

            int[] x = new int[3];
            int[] q = new int[3];
            int[] dims = new int[] { Chunk.SIZE_X, Chunk.SIZE_Y, Chunk.SIZE_Z };

            q[axis] = step;
            Block[] mask = new Block[(dims[u] / step) * (dims[v] / step)];

            // Iterate through slices
            for (x[axis] = -step; x[axis] < dims[axis];) {
                int n = 0;

                // Build mask
                for (x[v] = 0; x[v] < dims[v]; x[v] += step) {
                    for (x[u] = 0; x[u] < dims[u]; x[u] += step) {

                        // Get current block (behind the plane at x[axis])
                        Block current = Block.AIR;
                        if (x[axis] >= 0) {
                            current = blocks[x[0]][x[1]][x[2]];
                        } else {
                            // Query neighbor chunk
                            int gx = chunk.getChunkX() * Chunk.SIZE_X + x[0];
                            int gy = x[1];
                            int gz = chunk.getChunkZ() * Chunk.SIZE_Z + x[2];
                            current = chunkManager.getBlockAt(gx, gy, gz);
                        }

                        // Get next block (in front of the plane at x[axis])
                        Block next = Block.AIR;
                        int nx = x[0] + q[0];
                        int ny = x[1] + q[1];
                        int nz = x[2] + q[2];

                        if (nx >= 0 && nx < Chunk.SIZE_X && ny >= 0 && ny < Chunk.SIZE_Y && nz >= 0
                                && nz < Chunk.SIZE_Z) {
                            next = blocks[nx][ny][nz];
                        } else {
                            // Query neighbor chunk
                            int gx = chunk.getChunkX() * Chunk.SIZE_X + nx;
                            int gy = ny;
                            int gz = chunk.getChunkZ() * Chunk.SIZE_Z + nz;
                            next = chunkManager.getBlockAt(gx, gy, gz);
                        }

                        // Determine if we should draw a face
                        if (current == next) {
                            mask[n++] = null;
                        } else if (current != Block.AIR
                                && (next == Block.AIR || (next.isTransparent() && !current.isTransparent()))) {
                            mask[n++] = current;
                        } else if (next != Block.AIR
                                && (current == Block.AIR || (current.isTransparent() && !next.isTransparent()))) {
                            mask[n++] = next;
                        } else {
                            mask[n++] = null;
                        }
                    }
                }

                x[axis] += step;
                n = 0;

                // Generate mesh from mask
                for (int j = 0; j < dims[v]; j += step) {
                    for (int i = 0; i < dims[u];) {
                        int maskIdx = (i / step) + (j / step) * (dims[u] / step);
                        if (mask[maskIdx] != null) {
                            Block block = mask[maskIdx];

                            // Compute width
                            int w;
                            for (w = step; i + w < dims[u]
                                    && mask[((i + w) / step) + (j / step) * (dims[u] / step)] == block; w += step)
                                ;

                            // Compute height
                            boolean done = false;
                            int h;
                            for (h = step; j + h < dims[v]; h += step) {
                                for (int k = 0; k < w; k += step) {
                                    if (mask[((i + k) / step) + ((j + h) / step) * (dims[u] / step)] != block) {
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

                            // Determine backface
                            int cx = x[0] - q[0];
                            int cy = x[1] - q[1];
                            int cz = x[2] - q[2];

                            Block checkCurrent = Block.AIR;
                            if (cx >= 0 && cy >= 0 && cz >= 0 && cx < Chunk.SIZE_X && cy < Chunk.SIZE_Y
                                    && cz < Chunk.SIZE_Z) {
                                checkCurrent = blocks[cx][cy][cz];
                            } else {
                                int gx = chunk.getChunkX() * Chunk.SIZE_X + cx;
                                int gy = cy;
                                int gz = chunk.getChunkZ() * Chunk.SIZE_Z + cz;
                                checkCurrent = chunkManager.getBlockAt(gx, gy, gz);
                            }

                            boolean backFace = (checkCurrent == block);

                            // Texture coordinates
                            float u0 = block.getAtlasX() * textureStep;
                            float v0 = block.getAtlasY() * textureStep;

                            // Quad vertices
                            float[] v1Pos = new float[] { x[0], x[1], x[2] };
                            float[] v2Pos = new float[] { x[0] + du[0], x[1] + du[1], x[2] + du[2] };
                            float[] v3Pos = new float[] { x[0] + du[0] + dv[0], x[1] + du[1] + dv[1],
                                    x[2] + du[2] + dv[2] };
                            float[] v4Pos = new float[] { x[0] + dv[0], x[1] + dv[1], x[2] + dv[2] };

                            // Liquid Height Adjustment
                            if (block.isLiquid()) {
                                float hScale = block.getLevel() / 16.0f;
                                if (axis == 1) { // Horizontal faces
                                    if (backFace) { // Top face
                                        float offset = 1.0f - hScale;
                                        v1Pos[1] -= offset;
                                        v2Pos[1] -= offset;
                                        v3Pos[1] -= offset;
                                        v4Pos[1] -= offset;
                                    }
                                } else { // Side faces
                                    float offset = 1.0f - hScale;
                                    if (axis == 0) {
                                        v2Pos[1] -= offset;
                                        v3Pos[1] -= offset;
                                    } else if (axis == 2) {
                                        v3Pos[1] -= offset;
                                        v4Pos[1] -= offset;
                                    }
                                }
                            }

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
                            for (int l = 0; l < h; l += step) {
                                for (int k = 0; k < w; k += step) {
                                    int clearIdx = ((i + k) / step) + ((j + l) / step) * (dims[u] / step);
                                    mask[clearIdx] = null;
                                }
                            }

                            i += w;
                        } else {
                            i += step;
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

        return new MeshData(posArr, texArr, normArr, indArr);
    }

    private static void addQuad(List<Float> p, List<Float> t, List<Float> n, List<Integer> i, int offset,
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
}
