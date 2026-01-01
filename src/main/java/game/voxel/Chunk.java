package game.voxel;

import engine.raster.Mesh;
import engine.raster.MeshData;
import engine.raster.Texture;

public class Chunk {

    public static final int SIZE_X = 16;
    public static final int SIZE_Y = 256;
    public static final int SIZE_Z = 16;

    private final int chunkX;
    private final int chunkZ;
    private final Block[][][] blocks;
    private final int[] heightMap; // 16x16 cache of highest solid block per column
    private final Mesh[] lodMeshes;
    private boolean dirty;

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blocks = new Block[SIZE_X][SIZE_Y][SIZE_Z];
        this.heightMap = new int[SIZE_X * SIZE_Z];
        this.lodMeshes = new Mesh[3]; // LOD 0, 1, 2
        this.dirty = true;

        for (int x = 0; x < SIZE_X; x++) {
            for (int y = 0; y < SIZE_Y; y++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    blocks[x][y][z] = Block.AIR;
                }
            }
            // Initialize heightmap to 0
            for (int z = 0; z < SIZE_Z; z++) {
                heightMap[x * SIZE_Z + z] = 0;
            }
        }
    }

    public void setBlock(int x, int y, int z, Block block) {
        if (x >= 0 && x < SIZE_X && y >= 0 && y < SIZE_Y && z >= 0 && z < SIZE_Z) {
            blocks[x][y][z] = block;
            dirty = true;

            // Update heightmap
            int idx = x * SIZE_Z + z;
            if (block.isSolid()) {
                if (y > heightMap[idx]) {
                    heightMap[idx] = y;
                }
            } else if (y >= heightMap[idx]) {
                // Removed solid block at heightmap level - need to find new max
                int newHeight = 0;
                for (int checkY = SIZE_Y - 1; checkY >= 0; checkY--) {
                    if (blocks[x][checkY][z].isSolid()) {
                        newHeight = checkY;
                        break;
                    }
                }
                heightMap[idx] = newHeight;
            }
        }
    }

    public Block getBlock(int x, int y, int z) {
        if (x >= 0 && x < SIZE_X && y >= 0 && y < SIZE_Y && z >= 0 && z < SIZE_Z) {
            return blocks[x][y][z];
        }
        return Block.AIR;
    }

    public void updateMesh(Texture texture, ChunkManager chunkManager) {
        if (!dirty && lodMeshes[0] != null)
            return;

        for (int i = 0; i < lodMeshes.length; i++) {
            if (lodMeshes[i] != null)
                lodMeshes[i].cleanup();
            MeshData data = game.voxel.gfx.GreedyMesher.generateMeshData(this, texture, chunkManager, i);
            lodMeshes[i] = new Mesh(data);
            lodMeshes[i].setTexture(texture);
        }
        dirty = false;
    }

    public void setMeshData(int lod, MeshData data, Texture texture) {
        if (lod < 0 || lod >= lodMeshes.length)
            return;
        if (lodMeshes[lod] != null)
            lodMeshes[lod].cleanup();
        lodMeshes[lod] = new Mesh(data);
        lodMeshes[lod].setTexture(texture);
    }

    public MeshData generateMeshData(int lod, Texture texture, ChunkManager chunkManager) {
        return game.voxel.gfx.GreedyMesher.generateMeshData(this, texture, chunkManager, lod);
    }

    public Mesh getMesh() {
        return lodMeshes[0];
    }

    public Mesh getMesh(int lod) {
        if (lod < 0 || lod >= lodMeshes.length)
            return lodMeshes[0];
        return lodMeshes[lod];
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public boolean isDirty() {
        return dirty;
    }

    public Block[][][] getBlocks() {
        return blocks;
    }

    public int getGroundHeight(int localX, int localZ) {
        if (localX >= 0 && localX < SIZE_X && localZ >= 0 && localZ < SIZE_Z) {
            return heightMap[localX * SIZE_Z + localZ];
        }
        return 0;
    }

    public void cleanup() {
        for (Mesh m : lodMeshes) {
            if (m != null)
                m.cleanup();
        }
    }
}
