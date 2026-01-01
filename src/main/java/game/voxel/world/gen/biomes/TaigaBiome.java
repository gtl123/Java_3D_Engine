package game.voxel.world.gen.biomes;

import game.voxel.Block;
import game.voxel.Chunk;
import game.voxel.ChunkManager;
import game.voxel.world.gen.VegetationGenerator;

public class TaigaBiome implements Biome {
    @Override
    public Block getSurfaceBlock(int y, int surfaceH) {
        return Block.GRASS; // Darker grass if we had it
    }

    @Override
    public double getTreeDensity() {
        return 0.08; // Dense
    }

    @Override
    public void placeTree(ChunkManager cm, Chunk c, int x, int y, int z) {
        VegetationGenerator.placePine(cm, c, x, y, z);
    }
}
