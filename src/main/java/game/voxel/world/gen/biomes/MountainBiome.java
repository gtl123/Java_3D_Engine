package game.voxel.world.gen.biomes;

import game.voxel.Block;
import game.voxel.Chunk;
import game.voxel.ChunkManager;
import game.voxel.world.gen.VegetationGenerator;

public class MountainBiome implements Biome {
    @Override
    public Block getSurfaceBlock(int y, int surfaceH) {
        return (y > 100) ? Block.STONE : Block.GRASS;
    }

    @Override
    public double getTreeDensity() {
        return 0.01;
    }

    @Override
    public void placeTree(ChunkManager cm, Chunk c, int x, int y, int z) {
        VegetationGenerator.placePine(cm, c, x, y, z);
    }
}
