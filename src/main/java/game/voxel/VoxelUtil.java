package game.voxel;

public class VoxelUtil {
    public static long packPos(int x, int y, int z) {
        return (((long) x + 10000000L) << 38) | ((long) (y & 0xFF) << 30) | ((long) z + 10000000L);
    }

    public static int unpackX(long p) {
        return (int) ((p >> 38) - 10000000L);
    }

    public static int unpackY(long p) {
        return (int) ((p >> 30) & 0xFF);
    }

    public static int unpackZ(long p) {
        return (int) ((p & 0x3FFFFFFFL) - 10000000L);
    }

    public static int getRegionX(int chunkX) {
        return chunkX >> 5;
    }

    public static int getRegionZ(int chunkZ) {
        return chunkZ >> 5;
    }
}
