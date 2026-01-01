package game.voxel.world.gen;

import game.voxel.world.gen.biomes.*;

public class BiomeSystem {

    public static Biome getBiome(double temp, double humid, double cont, double erosion) {
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
}
