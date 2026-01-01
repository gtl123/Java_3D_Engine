package game.voxel.world.gen;

import game.voxel.Block;
import game.voxel.Chunk;
import game.voxel.ChunkManager;
import game.voxel.world.SimplexNoise;
import game.voxel.world.gen.biomes.Biome;

public class TerrainGenerator {

    private static final int SEA_LEVEL = 32;

    public void generateTerrain(ChunkManager cm, Chunk chunk, long seed) {
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
                Biome biome = BiomeSystem.getBiome(temperature, humidity, continentalness, erosion);

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
                    boolean isCave = false;
                    if (y > 0 && y < height) {
                        double caveNoise1 = SimplexNoise.noise(globalX * 0.02, y * 0.02, globalZ * 0.02);
                        // Ridged noise for tunnels
                        double tunnel = Math.abs(caveNoise1);

                        // Surface fade: blend caves out near the surface to prevent swiss-cheese
                        // mountains
                        double surfaceFade = 1.0;
                        int distFromSurface = height - y;
                        if (distFromSurface < 10) {
                            // Linear fade from surface down to 10 blocks
                            surfaceFade = distFromSurface / 10.0;
                        }

                        // Adjusted threshold: higher near surface (less caves), lower deep (more caves)
                        double threshold = 0.55 + (1.0 - surfaceFade) * 0.15; // 0.55 to 0.70

                        if (tunnel > threshold) {
                            isCave = true;
                        }
                    }

                    if (y <= height) {
                        if (isCave) {
                            if (y < SEA_LEVEL) {
                                chunk.setBlock(x, y, z, Block.WATER); // Flooded caves
                            } else {
                                chunk.setBlock(x, y, z, Block.AIR);
                            }
                        } else {
                            if (y < height - 3) {
                                chunk.setBlock(x, y, z, Block.STONE);
                            } else {
                                chunk.setBlock(x, y, z, biome.getSurfaceBlock(y, height));
                            }
                        }
                    } else {
                        // Above ground
                        if (y <= SEA_LEVEL) {
                            chunk.setBlock(x, y, z, Block.WATER);
                            // We need to trigger updates for water if we want it to flow potentially?
                            // But usually generation is static.
                        } else {
                            chunk.setBlock(x, y, z, Block.AIR);
                        }
                    }
                }

                // --- 3. Vegetation / Decoration (Surface only) ---
                if (!isSolidBlockInChunk(chunk, x, height + 1, z) && height >= SEA_LEVEL) {
                    double treeProb = biome.getTreeDensity();
                    if (treeProb > 0) {
                        double densityNoise = SimplexNoise.noise(globalX / 50.0, globalZ / 50.0);
                        if (densityNoise > 0.0) {
                            double rnd = fract(Math.abs(SimplexNoise.noise(globalX * 13.0, globalZ * 37.0) * 100.0));
                            if (rnd < treeProb) {
                                biome.placeTree(cm, chunk, x, height + 1, z);
                            }
                        }
                    }
                }
            }
        }
    }

    private double fbm(double x, double z, double scale, int octaves, double persistence) {
        double total = 0;
        double frequency = 1.0 / scale;
        double amplitude = 1.0;
        double maxValue = 0;
        for (int i = 0; i < octaves; i++) {
            total += SimplexNoise.noise(x * frequency, z * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= 2;
        }
        return total / maxValue;
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
        double baseH = SEA_LEVEL;
        if (c < -0.2) {
            baseH -= 20.0 + (-c * 40.0);
        } else if (c < 0.1) {
            baseH += c * 10.0;
        } else {
            baseH += 10.0 + (c * 50.0);
        }

        double terrainFactor = 1.0;
        if (e > 0.3) {
            terrainFactor = 0.2;
        } else if (e < -0.3) {
            terrainFactor = 2.5;
        }

        double height = baseH + (pv * 20.0 * terrainFactor);
        return (int) Math.max(1, Math.min(Chunk.SIZE_Y - 1, height));
    }
}
