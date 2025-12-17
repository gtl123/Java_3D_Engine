package engine.voxel;

public enum Block {
    AIR(0, 0, 0, true, false, 0),
    GRASS(1, 0, 0, false, true, 0.6f),
    DIRT(2, 1, 0, false, true, 0.5f),
    STONE(3, 2, 0, false, true, 1.5f),
    BEDROCK(4, 3, 0, false, true, -1.0f),
    WOOD(5, 0, 1, false, true, 2.0f),
    LEAVES(6, 1, 1, true, false, 0.2f),
    SAND(7, 2, 1, false, true, 0.5f),
    WATER(8, 3, 1, true, false, 0);

    private final int id;
    private final int atlasX;
    private final int atlasY;
    private final boolean transparent;
    private final boolean solid;
    private final float hardness;

    Block(int id, int atlasX, int atlasY, boolean transparent, boolean solid, float hardness) {
        this.id = id;
        this.atlasX = atlasX;
        this.atlasY = atlasY;
        this.transparent = transparent;
        this.solid = solid;
        this.hardness = hardness;
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

    public boolean isSolid() {
        return solid;
    }

    public float getHardness() {
        return hardness;
    }

    public static Block getById(int id) {
        for (Block b : values()) {
            if (b.id == id)
                return b;
        }
        return AIR;
    }
}
