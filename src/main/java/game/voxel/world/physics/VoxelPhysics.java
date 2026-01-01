package game.voxel.world.physics;

import game.voxel.Block;
import game.voxel.ChunkManager;
import game.voxel.VoxelUtil;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class VoxelPhysics {

    private final ChunkManager chunkManager;
    private Set<Long> activeBlocks = ConcurrentHashMap.newKeySet();

    public VoxelPhysics(ChunkManager chunkManager) {
        this.chunkManager = chunkManager;
    }

    public void addActiveBlock(int x, int y, int z) {
        activeBlocks.add(VoxelUtil.packPos(x, y, z));
    }

    public void removeBlocksInChunk(int cx, int cz) {
        activeBlocks.removeIf(pos -> {
            int x = VoxelUtil.unpackX(pos);
            int z = VoxelUtil.unpackZ(pos);
            return (x >> 4) == cx && (z >> 4) == cz;
        });
    }

    public void tick(int playerChunkX, int playerChunkZ) {
        if (activeBlocks.isEmpty())
            return;

        Set<Long> toProcess = activeBlocks;
        activeBlocks = ConcurrentHashMap.newKeySet();

        int physicsRadius = 5; // Chunk radius

        for (long pos : toProcess) {
            int x = VoxelUtil.unpackX(pos);
            int y = VoxelUtil.unpackY(pos);
            int z = VoxelUtil.unpackZ(pos);

            // Radius check
            int cx = x >> 4;
            int cz = z >> 4;
            if (Math.abs(cx - playerChunkX) > physicsRadius || Math.abs(cz - playerChunkZ) > physicsRadius) {
                // Keep it active but don't process this tick
                activeBlocks.add(pos);
                continue;
            }

            Block block = chunkManager.getBlockAt(x, y, z);
            if (block == Block.AIR)
                continue;

            if (block.falls()) {
                handleFalling(x, y, z, block);
            } else if (block.isLiquid()) {
                updateLiquid(x, y, z, block);
            }
        }
    }

    private void handleFalling(int x, int y, int z, Block block) {
        if (y <= 0)
            return;
        Block below = chunkManager.getBlockAt(x, y - 1, z);

        if (below == Block.AIR || below.isLiquid()) {
            chunkManager.setBlockAt(x, y, z, (below.isLiquid() ? below : Block.AIR));
            chunkManager.setBlockAt(x, y - 1, z, block);
        }
    }

    private void updateLiquid(int x, int y, int z, Block block) {
        int newLevel = 0;

        if (block.isSource()) {
            newLevel = 16;
        } else {
            int maxNeighbor = 0;
            // From Above
            Block above = chunkManager.getBlockAt(x, y + 1, z);
            if (above.isLiquid()) {
                newLevel = 16;
            } else {
                // From Sides
                int[][] neighbors = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
                for (int[] offset : neighbors) {
                    Block n = chunkManager.getBlockAt(x + offset[0], y, z + offset[1]);
                    if (n.isLiquid()) {
                        int nLevel = n.getLevel();
                        if (nLevel > maxNeighbor) {
                            maxNeighbor = nLevel;
                        }
                    }
                }
                newLevel = maxNeighbor - 2;
            }
        }

        if (newLevel < 0)
            newLevel = 0;

        if (newLevel != block.getLevel() && !block.isSource()) {
            Block newBlock = Block.getWaterByLevel(newLevel);
            chunkManager.setBlockAt(x, y, z, newBlock);
            return;
        }

        if (newLevel <= 0)
            return;

        // Flow Down
        if (y > 0) {
            Block below = chunkManager.getBlockAt(x, y - 1, z);
            if (canFlowInto(below)) {
                chunkManager.setBlockAt(x, y - 1, z, Block.WATER_F16);
                return;
            }
        }

        // Flow Horizontally
        if (newLevel > 2) {
            int spreadLevel = newLevel - 2;
            Block spreadBlock = Block.getWaterByLevel(spreadLevel);

            int[][] neighbors = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
            for (int[] offset : neighbors) {
                int nx = x + offset[0];
                int nz = z + offset[1];
                Block target = chunkManager.getBlockAt(nx, y, nz);

                if (canFlowInto(target)) {
                    if (target.isLiquid() && target.getLevel() >= spreadLevel)
                        continue;
                    chunkManager.setBlockAt(nx, y, nz, spreadBlock);
                }
            }
        }
    }

    private boolean canFlowInto(Block b) {
        return b == Block.AIR || (b.isLiquid() && !b.isSource()) || (!b.isSolid() && b != Block.BEDROCK);
    }
}
