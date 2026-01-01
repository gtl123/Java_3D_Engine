package game.voxel.world.gen;

import game.voxel.Block;
import game.voxel.Chunk;
import game.voxel.ChunkManager;

public class VegetationGenerator {

    public static void placeOak(ChunkManager cm, Chunk chunk, int x, int startY, int z) {
        if (startY + 6 >= Chunk.SIZE_Y)
            return;

        int worldX = chunk.getChunkX() * Chunk.SIZE_X + x;
        int worldZ = chunk.getChunkZ() * Chunk.SIZE_Z + z;

        // Trunk
        for (int i = 0; i < 5; i++)
            cm.setBlockAtSilently(worldX, startY + i, worldZ, Block.WOOD);

        // Leaves
        for (int ly = startY + 3; ly <= startY + 5; ly++) {
            int r = (ly == startY + 5) ? 1 : 2;
            for (int lx = worldX - r; lx <= worldX + r; lx++) {
                for (int lz = worldZ - r; lz <= worldZ + r; lz++) {
                    if (Math.abs(lx - worldX) == r && Math.abs(lz - worldZ) == r && ly > startY + 3)
                        continue; // rounded corners

                    // Don't replace wood
                    if (cm.getBlockAt(lx, ly, lz) == Block.AIR) {
                        cm.setBlockAtSilently(lx, ly, lz, Block.LEAVES);
                    }
                }
            }
        }
    }

    public static void placePine(ChunkManager cm, Chunk chunk, int x, int startY, int z) {
        if (startY + 9 >= Chunk.SIZE_Y)
            return;

        int worldX = chunk.getChunkX() * Chunk.SIZE_X + x;
        int worldZ = chunk.getChunkZ() * Chunk.SIZE_Z + z;

        // Tall trunk
        for (int i = 0; i < 7; i++)
            cm.setBlockAtSilently(worldX, startY + i, worldZ, Block.WOOD);

        // Cone leaves
        int leafStart = startY + 3;
        int leafTop = startY + 9;
        int radius = 2;
        for (int ly = leafStart; ly <= leafTop; ly++) {
            // Radius shrinkage
            if (ly > startY + 5)
                radius = 1;
            if (ly > startY + 8)
                radius = 0;

            for (int lx = worldX - radius; lx <= worldX + radius; lx++) {
                for (int lz = worldZ - radius; lz <= worldZ + radius; lz++) {
                    if (Math.abs(lx - worldX) == radius && Math.abs(lz - worldZ) == radius && radius > 0)
                        continue; // corners

                    // Don't replace wood
                    if (cm.getBlockAt(lx, ly, lz) == Block.AIR) {
                        cm.setBlockAtSilently(lx, ly, lz, Block.LEAVES);
                    }
                }
            }
        }
    }
}
