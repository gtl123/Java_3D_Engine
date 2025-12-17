package engine.voxel;

import engine.graph.Renderer;
import engine.graph.Texture;
import engine.io.Window;

import engine.world.SimplexNoise;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ChunkManager {

    private final ConcurrentHashMap<String, Chunk> chunks;
    private final ExecutorService executorService;
    private final Texture texture;

    public ChunkManager() throws Exception {
        this.chunks = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.texture = new Texture("textures/terrain.png");
    }

    public void init() {
        loadChunksAround(0, 0, 8);
    }

    public void update(int playerChunkX, int playerChunkZ) {
    }

    public void render(Renderer renderer, Window window) {
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

    public void loadChunksAround(int centerX, int centerZ, int radius) {
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                String key = getChunkKey(x, z);
                if (chunks.containsKey(key))
                    continue;

                final int cx = x;
                final int cz = z;
                tasks.add(() -> {
                    Chunk chunk = new Chunk(cx, cz);
                    generateTerrain(chunk);
                    chunks.put(key, chunk);
                    return null;
                });
            }
        }

        try {
            executorService.invokeAll(tasks);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void generateTerrain(Chunk chunk) {
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                double globalX = chunk.getChunkX() * Chunk.SIZE_X + x;
                double globalZ = chunk.getChunkZ() * Chunk.SIZE_Z + z;

                double noiseVal = SimplexNoise.noise(globalX / 50.0, globalZ / 50.0);
                int height = (int) (40 + noiseVal * 15);

                if (height < 1)
                    height = 1;
                if (height >= Chunk.SIZE_Y)
                    height = Chunk.SIZE_Y - 1;

                for (int y = 0; y <= height; y++) {
                    if (y == 0)
                        chunk.setBlock(x, y, z, Block.BEDROCK);
                    else if (y < height - 3)
                        chunk.setBlock(x, y, z, Block.STONE);
                    else if (y < height)
                        chunk.setBlock(x, y, z, Block.DIRT);
                    else
                        chunk.setBlock(x, y, z, Block.GRASS);
                }

                int waterLevel = 30;
                if (height < waterLevel) {
                    for (int y = height + 1; y <= waterLevel; y++) {
                        chunk.setBlock(x, y, z, Block.WATER);
                    }
                }
            }
        }
    }

    public void updateMeshes() {
        for (Chunk chunk : chunks.values()) {
            chunk.updateMesh(texture);
        }
    }

    public ConcurrentHashMap<String, Chunk> getChunks() {
        return chunks;
    }

    public static String getChunkKey(int x, int z) {
        return x + "," + z;
    }

    public void cleanup() {
        executorService.shutdown();
        texture.cleanup();
        for (Chunk c : chunks.values()) {
            c.cleanup();
        }
    }
    public boolean isSolidBlock(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= Chunk.SIZE_Y) return false;

        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        String key = getChunkKey(chunkX, chunkZ);
        Chunk chunk = chunks.get(key);
        if (chunk == null) return false;

        int localX = worldX & 15;
        int localZ = worldZ & 15;

        Block block = chunk.getBlock(localX, worldY, localZ);
        return block != null && block.isSolid();
    }

    public int getGroundHeight(int worldX, int worldZ) {
        int chunkX = worldX >> 4; // divide by 16
        int chunkZ = worldZ >> 4;
        String key = getChunkKey(chunkX, chunkZ);
        Chunk chunk = chunks.get(key);
        if (chunk == null) {
            return 0; // chunk not loaded, default to 0
        }

        int localX = worldX & 15; // modulo 16
        int localZ = worldZ & 15;

        // Scan from top of chunk downwards
        for (int y = Chunk.SIZE_Y - 1; y >= 0; y--) {
            Block block = chunk.getBlock(localX, y, localZ);
            if (block != null && block.isSolid()) {
                return y + 1; // return the surface height (just above the solid block)
            }
        }

        return 0; // no solid block found, treat as void
    }



}
