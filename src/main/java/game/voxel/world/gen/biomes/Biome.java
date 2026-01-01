package game.voxel.world.gen.biomes;

import game.voxel.Block;
import game.voxel.Chunk;
import game.voxel.ChunkManager;

public interface Biome {
    Block getSurfaceBlock(int y, int surfaceH);

    double getTreeDensity();

    void placeTree(ChunkManager cm, Chunk c, int x, int y, int z);
}
