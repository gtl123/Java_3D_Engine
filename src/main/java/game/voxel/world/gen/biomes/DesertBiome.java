package game.voxel.world.gen.biomes;

import game.voxel.Block;
import game.voxel.Chunk;
import game.voxel.ChunkManager;

public class DesertBiome implements Biome {
    @Override
    public Block getSurfaceBlock(int y, int surfaceH) {
        return Block.SAND;
    }

    @Override
    public double getTreeDensity() {
        return 0.005; // Cactus rare
    }

    @Override
    public void placeTree(ChunkManager cm, Chunk c, int x, int y, int z) {
        // Improvised Cactus: 3-high wood
        for (int i = 0; i < 3; i++)
            c.setBlock(x, y + i, z, Block.WOOD);
    }
}
