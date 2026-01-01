package game.voxel;

import engine.raster.Texture;

import game.voxel.gfx.AsyncMeshRebuilder;
import game.voxel.world.SimplexNoise;
import game.voxel.world.gen.TerrainGenerator;
import game.voxel.world.physics.VoxelPhysics;
import game.voxel.world.region.RegionManager;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/**
 * ChunkManager coordinates chunk lifecycle, generation, and physics simulation.
 */
public class ChunkManager {

    private final ConcurrentHashMap<Long, Chunk> chunks;
    private final ExecutorService executorService;
    private final Texture texture;
    private final long seed;
    private final ConcurrentHashMap<Long, Byte> changedBlocks = new ConcurrentHashMap<>();
    private final Set<Long> pendingChunks = ConcurrentHashMap.newKeySet();
    private final Set<Long> dirtyChunks = ConcurrentHashMap.newKeySet();

    // Delegated systems
    private final TerrainGenerator terrainGenerator;
    private final VoxelPhysics physics;
    private final RegionManager regionManager;
    private final AsyncMeshRebuilder meshRebuilder; // Field already existed, ensuring it's here.

    private float tickTimer = 0;
    private static final float TICK_RATE = 0.1f; // 10 ticks per second

    public ChunkManager(long seed, String worldName) throws Exception {
        this.seed = seed;
        this.chunks = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.texture = new Texture("textures/terrain.png");

        // Initialize systems
        SimplexNoise.init(seed);
        this.terrainGenerator = new TerrainGenerator();
        this.physics = new VoxelPhysics(this);

        File worldDir = new File("worlds", worldName); // Existing line
        this.regionManager = new RegionManager(worldDir); // Existing line
        this.meshRebuilder = new AsyncMeshRebuilder(this, texture);
    }

    public void init() {
        loadChunksAround(0, 0, 1);
    }

    public void update(float interval, int playerChunkX, int playerChunkZ, int renderDistance) {
        tickTimer += interval;
        if (tickTimer >= TICK_RATE) {
            physics.tick(playerChunkX, playerChunkZ);
            tickTimer = 0;
        }

        // Request rebuilds for dirty chunks
        Iterator<Long> dirtyIt = dirtyChunks.iterator();
        while (dirtyIt.hasNext()) {
            Long key = dirtyIt.next();
            Chunk chunk = chunks.get(key);
            if (chunk != null) {
                meshRebuilder.requestRebuild(chunk);
            }
            dirtyIt.remove();
        }

        meshRebuilder.applyUpdates(chunks, 2); // Upload up to 2 chunks per frame

        // Cleanup distant chunks to prevent memory leaks
        // Must be significantly larger than render distance to prevent load cycles
        cleanupChunks(playerChunkX, playerChunkZ, renderDistance + 5);
    }

    public void loadChunksAround(int centerX, int centerZ, int radius) {
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                long key = getChunkKey(x, z);
                if (chunks.containsKey(key))
                    continue;

                if (pendingChunks.contains(key)) // Original check
                    continue;

                pendingChunks.add(key);
                final int cx = x;
                final int cz = z;
                executorService.submit(() -> {
                    try {
                        // 1. Try to load from region file
                        Chunk chunk = regionManager.loadChunk(cx, cz);

                        // 2. If not found, generate new
                        if (chunk == null) {
                            chunk = new Chunk(cx, cz);
                            terrainGenerator.generateTerrain(this, chunk, seed);
                        }

                        // Async mesh generation
                        meshRebuilder.requestRebuild(chunk);
                        chunks.put(key, chunk);

                        // Mark neighbors as dirty so they re-mesh and see this new neighbor
                        dirtyChunks.add(getChunkKey(cx + 1, cz));
                        dirtyChunks.add(getChunkKey(cx - 1, cz));
                        dirtyChunks.add(getChunkKey(cx, cz + 1));
                        dirtyChunks.add(getChunkKey(cx, cz - 1));

                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        pendingChunks.remove(key);
                    }
                });
            }
        }
    }

    public boolean isChunkLoaded(int cx, int cz) {
        return chunks.containsKey(getChunkKey(cx, cz));
    }

    public synchronized void saveWorld() { // Added saveWorld method
        System.out.println("Saving world...");
        for (Chunk chunk : chunks.values()) {
            regionManager.saveChunk(chunk);
        }
        System.out.println("World saved!");
    }

    private void cleanupChunks(int playerChunkX, int playerChunkZ, int maxRadius) {
        List<Long> toRemove = new ArrayList<>();

        for (Map.Entry<Long, Chunk> entry : chunks.entrySet()) {
            Chunk chunk = entry.getValue();
            int dx = Math.abs(chunk.getChunkX() - playerChunkX);
            int dz = Math.abs(chunk.getChunkZ() - playerChunkZ);

            if (Math.max(dx, dz) > maxRadius) {
                toRemove.add(entry.getKey());
            }
        }

        for (long key : toRemove) {
            Chunk chunk = chunks.remove(key);
            if (chunk != null) {
                int cx = chunk.getChunkX();
                int cz = chunk.getChunkZ();

                // Mark neighbors to re-mesh (important for cave/boundary visibility)
                dirtyChunks.add(getChunkKey(cx + 1, cz));
                dirtyChunks.add(getChunkKey(cx - 1, cz));
                dirtyChunks.add(getChunkKey(cx, cz + 1));
                dirtyChunks.add(getChunkKey(cx, cz - 1));

                physics.removeBlocksInChunk(cx, cz);
                chunk.cleanup();
            }
        }
    }

    public void updateMeshes() {
        int updates = 0;
        int MAX_UPDATES_PER_FRAME = 5;

        for (Chunk chunk : chunks.values()) {
            if (chunk.isDirty()) {
                chunk.updateMesh(texture, this);
                updates++;
                if (updates >= MAX_UPDATES_PER_FRAME) {
                    break;
                }
            }
        }
    }

    public Block getBlockAt(int x, int y, int z) {
        if (y < 0 || y >= Chunk.SIZE_Y)
            return Block.AIR;
        int cx = x >> 4;
        int cz = z >> 4;
        Chunk chunk = chunks.get(getChunkKey(cx, cz));
        if (chunk != null) {
            return chunk.getBlock(x & 15, y, z & 15);
        }
        return Block.AIR;
    }

    public void setBlockAt(int x, int y, int z, Block block) {
        setBlockAt(x, y, z, block, true);
    }

    public void setBlockAt(int x, int y, int z, Block block, boolean triggerPhysics) {
        if (y < 0 || y >= Chunk.SIZE_Y)
            return;
        int cx = x >> 4;
        int cz = z >> 4;
        long key = getChunkKey(cx, cz);
        Chunk chunk = chunks.get(key);
        if (chunk != null) {
            int lx = x & 15;
            int lz = z & 15;

            Block prev = chunk.getBlock(lx, y, lz);
            if (prev != block) {
                chunk.setBlock(lx, y, lz, block);
                changedBlocks.put(VoxelUtil.packPos(x, y, z), (byte) block.getId());

                if (triggerPhysics) {
                    triggerNeighbors(x, y, z);

                    // Notify physics system
                    physics.addActiveBlock(x, y, z);
                    physics.addActiveBlock(x + 1, y, z);
                    physics.addActiveBlock(x - 1, y, z);
                    physics.addActiveBlock(x, y + 1, z);
                    physics.addActiveBlock(x, y - 1, z);
                    physics.addActiveBlock(x, y, z + 1);
                    physics.addActiveBlock(x, y, z - 1);
                }
            }
        }
    }

    /**
     * Use during world generation to avoid physics/dirty checks
     */
    public void setBlockAtSilently(int x, int y, int z, Block block) {
        if (y < 0 || y >= Chunk.SIZE_Y)
            return;
        int cx = x >> 4;
        int cz = z >> 4;
        long key = getChunkKey(cx, cz);
        Chunk chunk = chunks.get(key);
        if (chunk != null) {
            chunk.setBlock(x & 15, y, z & 15, block);
        }
    }

    private void triggerNeighbors(int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        long key = getChunkKey(cx, cz);
        dirtyChunks.add(key);

        // Check if block is at chunk edge
        int lx = x & 15;
        int lz = z & 15;

        if (lx == 0)
            dirtyChunks.add(getChunkKey(cx - 1, cz));
        if (lx == 15)
            dirtyChunks.add(getChunkKey(cx + 1, cz));
        if (lz == 0)
            dirtyChunks.add(getChunkKey(cx, cz - 1));
        if (lz == 15)
            dirtyChunks.add(getChunkKey(cx, cz + 1));
    }

    public ConcurrentHashMap<Long, Byte> getChangedBlocks() {
        return changedBlocks;
    }

    public int getTotalVertices() {
        int total = 0;
        for (Chunk c : chunks.values()) {
            if (c.getMesh() != null) {
                total += c.getMesh().getVertexCount();
            }
        }
        return total;
    }

    public boolean isSolidBlock(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= Chunk.SIZE_Y)
            return false;

        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        long key = getChunkKey(chunkX, chunkZ);
        Chunk chunk = chunks.get(key);
        if (chunk == null)
            return false;

        int localX = worldX & 15;
        int localZ = worldZ & 15;

        Block block = chunk.getBlock(localX, worldY, localZ);
        return block != null && block.isSolid();
    }

    public int getGroundHeight(int worldX, int worldZ) {
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        long key = getChunkKey(chunkX, chunkZ);
        Chunk chunk = chunks.get(key);
        if (chunk == null) {
            return 0;
        }

        int localX = worldX & 15;
        int localZ = worldZ & 15;

        return chunk.getGroundHeight(localX, localZ) + 1; // +1 to spawn above ground
    }

    public Texture getBlockTexture() {
        return texture;
    }

    public ConcurrentHashMap<Long, Chunk> getChunks() {
        return chunks;
    }

    public static long getChunkKey(int x, int z) {
        return (((long) x) << 32) | (z & 0xffffffffL);
    }

    public void cleanup() {
        saveWorld();
        regionManager.cleanup();
        meshRebuilder.cleanup();
        executorService.shutdown();
        for (Chunk c : chunks.values()) {
            c.cleanup();
        }
    }
}
