package engine.voxel;

public enum Block {
    AIR(0, 0, 0, true),
    GRASS(1, 0, 0, false), // Mapping needed for multi-face (top/side/bottom)
    DIRT(2, 1, 0, false),
    STONE(3, 2, 0, false),
    BEDROCK(4, 3, 0, false),
    WOOD(5, 0, 1, false),
    LEAVES(6, 1, 1, true), // Transparent
    SAND(7, 2, 1, false),
    WATER(8, 3, 1, true);

    private final int id;
    private final int atlasX;
    private final int atlasY;
    private final boolean transparent;

    Block(int id, int atlasX, int atlasY, boolean transparent) {
        this.id = id;
        this.atlasX = atlasX;
        this.atlasY = atlasY;
        this.transparent = transparent;
    }

    public int getId() {
        return id;
    }

    public int getAtlasX() {
        return atlasX;
    }

    public int getAtlasY() {
        return atlasY;
    }

    public boolean isTransparent() {
        return transparent;
    }

    public static Block getById(int id) {
        for (Block b : values()) {
            if (b.id == id)
                return b;
        }
        return AIR;
    }
}
