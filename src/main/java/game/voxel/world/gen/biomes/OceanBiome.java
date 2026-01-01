package game.voxel.world.gen.biomes;

import game.voxel.Block;
import game.voxel.Chunk;
import game.voxel.ChunkManager;

public class OceanBiome implements Biome {
    @Override
    public Block getSurfaceBlock(int y, int surfaceH) {
        return Block.SAND;
    }

    @Override
    public double getTreeDensity() {
        return 0;
    }

    @Override
    public void placeTree(ChunkManager cm, Chunk c, int x, int y, int z) {
        // No trees
    }
}
