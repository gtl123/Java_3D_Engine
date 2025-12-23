package game.voxel;

import engine.raster.Renderer;
import engine.raster.Texture;
import engine.io.Window;

import game.voxel.world.SimplexNoise;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class ChunkManager {

    private final ConcurrentHashMap<String, Chunk> chunks;
    private final ExecutorService executorService;
    private final Texture texture;
    // Seed for procedural generation; kept so worlds are deterministic and can
    // be reconstructed from minimal save data.
    private final long seed;
    /**
     * Global sea level used for water filling. Kept as a named constant instead of
     * a
     * magic number so it is easy to tweak later.
     */
    private static final int SEA_LEVEL = 32;

    // --- Height/biome noise scales (in meters) ---
    // Very low frequency "continental" scale for big landmass shapes
    private static final double CONTINENT_SCALE = 20000.0;
    // Medium scale for mountain ranges and larger hills
    private static final double MOUNTAIN_SCALE = 4000.0;
    // Higher-frequency detail noise for small hills and bumps
    private static final double DETAIL_SCALE = 100.0;
    // Large-scale biome control for temperature and humidity (ensures km‑wide
    // deserts)
    private static final double BIOME_SCALE = 1500.0;
    // Tree placement noise scale (controls spacing)
    private static final double TREE_SCALE = 200.0;
    private float tickTimer = 0;
    private static final float TICK_RATE = 0.1f; // 10 ticks per second

    private Set<Long> activeBlocks = ConcurrentHashMap.newKeySet();
    /**
     * Tracks player/world edits in a sparse map of world-position → blockId. This
     * is
     * what gets saved to disk; procedural terrain is always regenerated from the
     * seed, so only changes need to be stored.
     */
    private final ConcurrentHashMap<Long, Byte> changedBlocks = new ConcurrentHashMap<>();

    public ChunkManager(long seed) throws Exception {
        this.seed = seed;
        this.chunks = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.texture = new Texture("textures/terrain.png");
        // Initialize global noise with this world's seed
        game.voxel.world.SimplexNoise.init(seed);
    }

    private long packPos(int x, int y, int z) {
        return (((long) x + 10000000L) << 38) | ((long) (y & 0xFF) << 30) | ((long) z + 10000000L);
    }

    private int unpackX(long p) {
        return (int) ((p >> 38) - 10000000L);
    }

    private int unpackY(long p) {
        return (int) ((p >> 30) & 0xFF);
    }

    private int unpackZ(long p) {
        return (int) ((p & 0x3FFFFFFFL) - 10000000L);
    }

    private void triggerBlock(int x, int y, int z) {
        if (y < 0 || y >= Chunk.SIZE_Y)
            return;
        activeBlocks.add(packPos(x, y, z));
    }

    private void triggerNeighbors(int x, int y, int z) {
        triggerBlock(x, y, z);
        triggerBlock(x + 1, y, z);
        triggerBlock(x - 1, y, z);
        triggerBlock(x, y + 1, z);
        triggerBlock(x, y - 1, z);
        triggerBlock(x, y, z + 1);
        triggerBlock(x, y, z - 1);
    }

    private static class BlockUpdate {
        int x, y, z;
        Block block;

        BlockUpdate(int x, int y, int z, Block block) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
        }
    }

    public void init() {
        loadChunksAround(0, 0, 1);
    }

    public void update(float interval, int playerChunkX, int playerChunkZ) {
        tickTimer += interval;
        if (tickTimer >= TICK_RATE) {
            tick(playerChunkX, playerChunkZ);
            tickTimer = 0;
        }

        // Cleanup distant chunks to prevent memory leaks
        cleanupChunks(playerChunkX, playerChunkZ, 10);
    }

    private void tick(int playerChunkX, int playerChunkZ) {
        if (activeBlocks.isEmpty())
            return;

        Set<Long> toProcess = activeBlocks;
        activeBlocks = ConcurrentHashMap.newKeySet();

        // Limit updates per tick to prevent lags if huge cascade
        // But for now, process all.

        int physicsRadius = 5; // Chunk radius

        for (long pos : toProcess) {
            int x = unpackX(pos);
            int y = unpackY(pos);
            int z = unpackZ(pos);

            // Radius check
            int cx = x >> 4;
            int cz = z >> 4;
            if (Math.abs(cx - playerChunkX) > physicsRadius || Math.abs(cz - playerChunkZ) > physicsRadius) {
                // Too far, don't simulate water, but maybe keep active for later?
                // activeBlocks.add(pos); // Optional: keep vivid
                continue;
            }

            Block block = getBlockAt(x, y, z);
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
        Block below = getBlockAt(x, y - 1, z);

        if (below == Block.AIR || below.isLiquid()) {
            // Swap / Fall
            setBlockAt(x, y, z, (below.isLiquid() ? below : Block.AIR)); // If water below, it displaces up? Or just
                                                                         // vanishes.
            // Simple swap with air primarily
            setBlockAt(x, y - 1, z, block);
        }
    }

    private void updateLiquid(int x, int y, int z, Block block) {
        // 1. Validation (Pull Model) - "Should I exist / what is my level?"
        // Source blocks (Level 16, source=true) don't decay on their own.
        // Flowing blocks must be derived from neighbors.

        int newLevel = 0;

        if (block.isSource()) {
            newLevel = 16;
        } else {
            // Calculate max input level from neighbors
            int maxNeighbor = 0;

            // From Above: Falling water restores full strength (or close to it)
            Block above = getBlockAt(x, y + 1, z);
            if (above.isLiquid()) {
                newLevel = 16; // Falling water is always full strength
            } else {
                // From Sides: Decay
                int[][] neighbors = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
                for (int[] offset : neighbors) {
                    Block n = getBlockAt(x + offset[0], y, z + offset[1]);
                    if (n.isLiquid()) {
                        int nLevel = n.getLevel();
                        if (nLevel > maxNeighbor) {
                            maxNeighbor = nLevel;
                        }
                    }
                }
                // Decay step
                newLevel = maxNeighbor - 2; // Decay by 2 (Limit spread to ~8 blocks)
            }
        }

        if (newLevel < 0)
            newLevel = 0;

        // Apply state change if needed
        if (newLevel != block.getLevel() && !block.isSource()) {
            Block newBlock = Block.getWaterByLevel(newLevel); // 0 returns AIR
            setBlockAt(x, y, z, newBlock);
            return; // State changed, stop spread this tick (next tick will handle spread from new
                    // state)
        }

        // If we turned into Air, stop.
        if (newLevel <= 0)
            return;

        // 2. Spread (Push Model) - "Where should I go?"
        // Only if we are valid liquid

        // A. Flow Down
        if (y > 0) {
            Block below = getBlockAt(x, y - 1, z);
            if (canFlowInto(below)) {
                // Falling water becomes Max Level
                setBlockAt(x, y - 1, z, Block.WATER_F16);
                return; // Prioritize down flow? Maybe.
            }
        }

        // B. Flow Horizontally
        // Only if block below is solid (or we are full source/falling and can spread
        // out?)
        // If block below is Liquid, we generally don't spread sideways unless we are
        // full?
        // MC Logic: If block below is solid OR block below is full liquid?
        // Let's keep it simple: Always try to spread sideways if we have enough level.

        if (newLevel > 2) { // Need at least 2 to spread (since it costs 2)
            int spreadLevel = newLevel - 2;
            Block spreadBlock = Block.getWaterByLevel(spreadLevel);

            int[][] neighbors = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
            for (int[] offset : neighbors) {
                int nx = x + offset[0];
                int nz = z + offset[1];
                Block target = getBlockAt(nx, y, nz);

                if (canFlowInto(target)) {
                    // Don't overwrite higher or equal water
                    if (target.isLiquid() && target.getLevel() >= spreadLevel)
                        continue;

                    setBlockAt(nx, y, nz, spreadBlock);
                }
            }
        }
    }

    private boolean canFlowInto(Block b) {
        return b == Block.AIR || (b.isLiquid() && !b.isSource()) || (!b.isSolid() && b != Block.BEDROCK);
        // e.g. flow into grass/flowers if we had them (non-solid blocks replacement)
        // For now: AIR or Lower Water.
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
        if (y < 0 || y >= Chunk.SIZE_Y)
            return;
        int cx = x >> 4;
        int cz = z >> 4;
        Chunk chunk = chunks.get(getChunkKey(cx, cz));
        if (chunk != null) {
            Block prev = chunk.getBlock(x & 15, y, z & 15);
            if (prev != block) {
                chunk.setBlock(x & 15, y, z & 15, block);
                // Record this as a world edit so it can be saved efficiently.
                changedBlocks.put(packPos(x, y, z), (byte) block.getId());
                triggerNeighbors(x, y, z);
                // Add neighbors to active blocks set for next physics tick
                activeBlocks.add(packPos(x + 1, y, z));
                activeBlocks.add(packPos(x - 1, y, z));
                activeBlocks.add(packPos(x, y + 1, z));
                activeBlocks.add(packPos(x, y - 1, z));
                activeBlocks.add(packPos(x, y, z + 1));
                activeBlocks.add(packPos(x, y, z - 1));
            }
        }
    }

    /**
     * Returns a snapshot of all edited blocks for saving. Callers should treat this
     * as read-only.
     */
    public ConcurrentHashMap<Long, Byte> getChangedBlocks() {
        return changedBlocks;
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

    /**
     * Unloads chunks that are beyond the specified radius from the player.
     * This prevents memory leaks as the player explores.
     */
    private void cleanupChunks(int playerChunkX, int playerChunkZ, int maxRadius) {
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, Chunk> entry : chunks.entrySet()) {
            Chunk chunk = entry.getValue();
            int dx = Math.abs(chunk.getChunkX() - playerChunkX);
            int dz = Math.abs(chunk.getChunkZ() - playerChunkZ);

            // Use chebyshev distance (max of dx, dz) for square unload area
            if (Math.max(dx, dz) > maxRadius) {
                toRemove.add(entry.getKey());
            }
        }

        // Clean up the chunks
        for (String key : toRemove) {
            Chunk chunk = chunks.remove(key);
            if (chunk != null && chunk.getMesh() != null) {
                chunk.cleanup();
            }
        }
    }

    private void generateTerrain(Chunk chunk) {
        int chunkX = chunk.getChunkX();
        int chunkZ = chunk.getChunkZ();

        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                double globalX = chunkX * Chunk.SIZE_X + x;
                double globalZ = chunkZ * Chunk.SIZE_Z + z;

                // --- 1. Base Terrain Shape (Multi-Octave) ---
                // Continentalness: Large scale oceans vs land
                double continentalness = fbm(globalX, globalZ, 2000.0, 3, 0.5);
                // Erosion: large scale mountains vs flat
                double erosion = fbm(globalX + 5000, globalZ + 5000, 1000.0, 3, 0.5);
                // PV (PeaksValleys): Detail scale
                double pv = fbm(globalX, globalZ, 120.0, 4, 0.6);

                // Climate
                double temperature = (fbm(globalX, globalZ, 1000.0, 2, 0.5) + 1.0) * 0.5; // 0..1
                double humidity = (fbm(globalX + 1234, globalZ + 2341, 1000.0, 2, 0.5) + 1.0) * 0.5; // 0..1

                // Biome Determination
                Biome biome = getBiome(temperature, humidity, continentalness, erosion);

                // Calculate Surface Height
                int height = calculateHeight(continentalness, erosion, pv, biome);

                // --- 2. Block Filling ---
                for (int y = 0; y < Chunk.SIZE_Y; y++) {
                    // Bedrock at bottom
                    if (y == 0) {
                        chunk.setBlock(x, y, z, Block.BEDROCK);
                        continue;
                    }

                    // Caves (3D Noise)
                    // Don't carve bedrock or sea (optional: underwater caves?)
                    boolean isCave = false;
                    if (y > 0 && y < height) {
                        // 3D noise spaghetti caves
                        // Frequency 0.02 (scale ~50), Threshold 0.6
                        double caveNoise1 = SimplexNoise.noise(globalX * 0.02, y * 0.02, globalZ * 0.02);
                        double caveNoise2 = SimplexNoise.noise(globalX * 0.02 + 1000, y * 0.02, globalZ * 0.02);
                        // Ridged noise for tunnels
                        double tunnel = Math.abs(caveNoise1);
                        // Mask at surface to prevent Swiss and cheese
                        double surfaceFade = Math.min(1.0, (height - y) / 10.0);

                        // Cheese caves
                        if (tunnel > 0.55) {
                            isCave = true;
                        }
                    }

                    if (y <= height) {
                        if (isCave) {
                            // If underwater, fill cave with water? For now, AIR to represent void, or WATER
                            // if below sea.
                            if (y < SEA_LEVEL) {
                                chunk.setBlock(x, y, z, Block.WATER); // Flooded caves
                            } else {
                                chunk.setBlock(x, y, z, Block.AIR);
                            }
                        } else {
                            // Terrain blocks
                            if (y < height - 3) {
                                chunk.setBlock(x, y, z, Block.STONE);
                            } else {
                                // Surface layers
                                chunk.setBlock(x, y, z, biome.getSurfaceBlock(y, height));
                            }
                        }
                    } else {
                        // Above ground
                        if (y <= SEA_LEVEL) {
                            chunk.setBlock(x, y, z, Block.WATER);
                            if (y == SEA_LEVEL) {
                                triggerBlock(chunkX * Chunk.SIZE_X + x, y, chunkZ * Chunk.SIZE_Z + z);
                            }
                        } else {
                            chunk.setBlock(x, y, z, Block.AIR);
                        }
                    }
                }

                // --- 3. Vegetation / Decoration (Surface only) ---
                if (!isSolidBlockInChunk(chunk, x, height + 1, z) && height >= SEA_LEVEL) {
                    // Flora placement
                    double treeProb = biome.getTreeDensity();
                    if (treeProb > 0) {
                        // High freq noise for clumps
                        double densityNoise = SimplexNoise.noise(globalX / 50.0, globalZ / 50.0);
                        if (densityNoise > 0.0) { // Clumped
                            // Random roll (using noise as 'random' for stability)
                            double rnd = fract(Math.abs(SimplexNoise.noise(globalX * 13.0, globalZ * 37.0) * 100.0));
                            if (rnd < treeProb) {
                                biome.placeTree(this, chunk, x, height + 1, z);
                            }
                        }
                    }
                }
            }
        }
    }

    // Fractal Brownian Motion
    private double fbm(double x, double z, double scale, int octaves, double persistence) {
        double total = 0;
        double frequency = 1.0 / scale;
        double amplitude = 1.0;
        double maxValue = 0; // Used for normalizing result to 0.0 - 1.0
        for (int i = 0; i < octaves; i++) {
            total += SimplexNoise.noise(x * frequency, z * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= 2;
        }
        return total / maxValue; // Normalize to -1..1 generally
    }

    private double fract(double d) {
        return d - Math.floor(d);
    }

    private boolean isSolidBlockInChunk(Chunk c, int x, int y, int z) {
        if (y >= Chunk.SIZE_Y)
            return false;
        Block b = c.getBlock(x, y, z);
        return b != Block.AIR && b.isSolid();
    }

    private int calculateHeight(double c, double e, double pv, Biome biome) {
        // Base height from Continentalness
        // c < 0: Ocean. c > 0: Land.
        double baseH = SEA_LEVEL;
        if (c < -0.2) {
            // Deep Ocean
            baseH -= 20.0 + (-c * 40.0);
        } else if (c < 0.1) {
            // Beach / Coast
            baseH += c * 10.0;
        } else {
            // Land
            baseH += 10.0 + (c * 50.0);
        }

        // Erosion: Determines flatness. e > 0.5 -> Flat. e < -0.5 -> Mountainous.
        double terrainFactor = 1.0;
        if (e > 0.3) {
            // Flat plains
            terrainFactor = 0.2;
        } else if (e < -0.3) {
            // Mountains
            terrainFactor = 2.5;
        }

        double height = baseH + (pv * 20.0 * terrainFactor);

        // Clamp
        return (int) Math.max(1, Math.min(Chunk.SIZE_Y - 1, height));
    }

    // --- Biome System ---

    private enum BiomeType {
        DESERT, FOREST, TAIGA, PLAINS, MOUNTAINS, OCEAN
    }

    private interface Biome {
        Block getSurfaceBlock(int y, int surfaceH);

        double getTreeDensity();

        void placeTree(ChunkManager cm, Chunk c, int x, int y, int z);
    }

    private Biome getBiome(double temp, double humid, double cont, double erosion) {
        if (cont < 0)
            return new OceanBiome();
        if (erosion < -0.5 && cont > 0.5)
            return new MountainBiome();

        if (temp > 0.6 && humid < 0.4)
            return new DesertBiome();
        if (temp < 0.4 && humid > 0.5)
            return new TaigaBiome();
        if (temp > 0.4 && humid > 0.4)
            return new ForestBiome();

        return new PlainsBiome();
    }

    // --- Concrete Biomes ---

    class DesertBiome implements Biome {
        public Block getSurfaceBlock(int y, int surfaceH) {
            return Block.SAND;
        }

        public double getTreeDensity() {
            return 0.005;
        } // Cactus rare

        public void placeTree(ChunkManager cm, Chunk c, int x, int y, int z) {
            // Cactus: simple column of 3 green wool? We don't have cactus. Use leaves logs?
            // Or just Wood column.
            // Improvised Cactus: 3-high wood
            for (int i = 0; i < 3; i++)
                c.setBlock(x, y + i, z, Block.WOOD);
        }
    }

    class ForestBiome implements Biome {
        public Block getSurfaceBlock(int y, int surfaceH) {
            return Block.GRASS;
        }

        public double getTreeDensity() {
            return 0.02;
        }

        public void placeTree(ChunkManager cm, Chunk c, int x, int y, int z) {
            placeOak(c, x, y, z);
        }
    }

    class TaigaBiome implements Biome {
        public Block getSurfaceBlock(int y, int surfaceH) {
            return Block.GRASS;
        } // Darker grass if we had it

        public double getTreeDensity() {
            return 0.08;
        } // Dense

        public void placeTree(ChunkManager cm, Chunk c, int x, int y, int z) {
            placePine(c, x, y, z);
        }
    }

    class PlainsBiome implements Biome {
        public Block getSurfaceBlock(int y, int surfaceH) {
            return Block.GRASS;
        }

        public double getTreeDensity() {
            return 0.001;
        } // Very spare

        public void placeTree(ChunkManager cm, Chunk c, int x, int y, int z) {
            placeOak(c, x, y, z);
        }
    }

    class MountainBiome implements Biome {
        public Block getSurfaceBlock(int y, int surfaceH) {
            return (y > 100) ? Block.STONE : Block.GRASS;
        }

        public double getTreeDensity() {
            return 0.01;
        }

        public void placeTree(ChunkManager cm, Chunk c, int x, int y, int z) {
            placePine(c, x, y, z);
        }
    }

    class OceanBiome implements Biome {
        public Block getSurfaceBlock(int y, int surfaceH) {
            return Block.SAND;
        }

        public double getTreeDensity() {
            return 0;
        }

        public void placeTree(ChunkManager cm, Chunk c, int x, int y, int z) {
        }
    }

    // --- Tree Placers ---

    private void placeOak(Chunk chunk, int x, int startY, int z) {
        if (startY + 6 >= Chunk.SIZE_Y)
            return;
        // Trunk
        for (int i = 0; i < 5; i++)
            chunk.setBlock(x, startY + i, z, Block.WOOD);
        // Leaves
        for (int ly = startY + 3; ly <= startY + 5; ly++) {
            int r = (ly == startY + 5) ? 1 : 2;
            for (int lx = x - r; lx <= x + r; lx++) {
                for (int lz = z - r; lz <= z + r; lz++) {
                    if (Math.abs(lx - x) == r && Math.abs(lz - z) == r && ly > startY + 3)
                        continue; // rounded corners
                    if (isSafeSet(lx, ly, lz))
                        chunk.setBlock(lx, ly, lz, Block.LEAVES);
                }
            }
        }
    }

    private void placePine(Chunk chunk, int x, int startY, int z) {
        if (startY + 9 >= Chunk.SIZE_Y)
            return;
        // Tall trunk
        for (int i = 0; i < 7; i++)
            chunk.setBlock(x, startY + i, z, Block.WOOD);
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

            for (int lx = x - radius; lx <= x + radius; lx++) {
                for (int lz = z - radius; lz <= z + radius; lz++) {
                    if (Math.abs(lx - x) == radius && Math.abs(lz - z) == radius && radius > 0)
                        continue; // corners
                    if (isSafeSet(lx, ly, lz)) {
                        // Don't replace trunk
                        if (lx == x && lz == z && ly < startY + 7)
                            continue;
                        chunk.setBlock(lx, ly, lz, Block.LEAVES);
                    }
                }
            }
        }
    }

    private boolean isSafeSet(int x, int y, int z) {
        return x >= 0 && x < Chunk.SIZE_X && z >= 0 && z < Chunk.SIZE_Z && y >= 0 && y < Chunk.SIZE_Y;
    }

    public void updateMeshes() {
        for (Chunk chunk : chunks.values()) {
            if (chunk.isDirty()) {
                chunk.updateMesh(texture, this);
            }
        }
    }

    /**
     * Exposes the terrain texture used by chunks so that other systems (e.g. item
     * entities) can reuse the same atlas and stay visually consistent.
     */
    public Texture getBlockTexture() {
        return texture;
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
        if (worldY < 0 || worldY >= Chunk.SIZE_Y)
            return false;

        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        String key = getChunkKey(chunkX, chunkZ);
        Chunk chunk = chunks.get(key);
        if (chunk == null)
            return false;

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
