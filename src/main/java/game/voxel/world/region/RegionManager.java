package game.voxel.world.region;

import game.voxel.Block;
import game.voxel.Chunk;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

public class RegionManager {
    private final File worldDir;
    private final Map<Long, RegionFile> regionCache = new HashMap<>();

    public RegionManager(File worldDir) {
        this.worldDir = new File(worldDir, "regions");
        if (!this.worldDir.exists()) {
            this.worldDir.mkdirs();
        }
    }

    public synchronized void saveChunk(Chunk chunk) {
        int cx = chunk.getChunkX();
        int cz = chunk.getChunkZ();
        RegionFile region = getRegionFile(cx, cz);

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (DataOutputStream dos = new DataOutputStream(new DeflaterOutputStream(bos))) {
                // Write simple block data (could be optimized with Run-Length Encoding later)
                Block[][][] blocks = chunk.getBlocks();
                for (int x = 0; x < Chunk.SIZE_X; x++) {
                    for (int y = 0; y < Chunk.SIZE_Y; y++) {
                        for (int z = 0; z < Chunk.SIZE_Z; z++) {
                            dos.writeByte(blocks[x][y][z].getId());
                        }
                    }
                }
            }

            region.writeChunk(cx, cz, bos.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized Chunk loadChunk(int cx, int cz) {
        RegionFile region = getRegionFile(cx, cz);
        try {
            DataInputStream dis = region.getChunkInputStream(cx, cz);
            if (dis == null)
                return null;

            Chunk chunk = new Chunk(cx, cz);
            for (int x = 0; x < Chunk.SIZE_X; x++) {
                for (int y = 0; y < Chunk.SIZE_Y; y++) {
                    for (int z = 0; z < Chunk.SIZE_Z; z++) {
                        int id = dis.readByte() & 0xFF;
                        chunk.setBlock(x, y, z, Block.getById(id));
                    }
                }
            }
            dis.close();
            return chunk;
        } catch (IOException e) {
            // Silently fail if chunk not found or corrupted - will be regenerated
            // e.printStackTrace();
        }
        return null;
    }

    private RegionFile getRegionFile(int cx, int cz) {
        int rx = cx >> 5;
        int rz = cz >> 5;
        long regionKey = (((long) rx) << 32) | (rz & 0xFFFFFFFFL);

        RegionFile region = regionCache.get(regionKey);
        if (region == null) {
            File file = new File(worldDir, "r." + rx + "." + rz + ".reg");
            try {
                region = new RegionFile(file);
                regionCache.put(regionKey, region);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return region;
    }

    public void cleanup() {
        for (RegionFile region : regionCache.values()) {
            try {
                region.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        regionCache.clear();
    }
}
