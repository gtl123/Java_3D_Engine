package game.voxel;

public enum Block {
    AIR(0, 0, 0, true, false, 0, false, false, 0f, 0, false),
    GRASS(1, 0, 0, false, true, 0.6f, false, false, 1f, 0, false),
    DIRT(2, 1, 0, false, true, 0.5f, false, false, 1f, 0, false),
    STONE(3, 2, 0, false, true, 1.5f, false, false, 1f, 0, false),
    BEDROCK(4, 3, 0, false, true, -1.0f, false, false, 1f, 0, false),
    WOOD(5, 0, 1, false, true, 2.0f, false, false, 1f, 0, false),
    LEAVES(6, 1, 1, true, false, 0.2f, false, false, 1f, 0, false),
    SAND(7, 2, 1, false, true, 0.5f, true, false, 1f, 0, false),
    WATER(8, 3, 1, true, false, 0, false, true, 0.6f, 16, true),
    WATER_F16(16, 3, 1, true, false, 0, false, true, 0.6f, 16, false),
    WATER_15(17, 3, 1, true, false, 0, false, true, 0.6f, 15, false),
    WATER_14(18, 3, 1, true, false, 0, false, true, 0.6f, 14, false),
    WATER_13(19, 3, 1, true, false, 0, false, true, 0.6f, 13, false),
    WATER_12(20, 3, 1, true, false, 0, false, true, 0.6f, 12, false),
    WATER_11(21, 3, 1, true, false, 0, false, true, 0.6f, 11, false),
    WATER_10(22, 3, 1, true, false, 0, false, true, 0.6f, 10, false),
    WATER_9(23, 3, 1, true, false, 0, false, true, 0.6f, 9, false),
    WATER_8(24, 3, 1, true, false, 0, false, true, 0.6f, 8, false),
    WATER_7(9, 3, 1, true, false, 0, false, true, 0.6f, 7, false),
    WATER_6(10, 3, 1, true, false, 0, false, true, 0.6f, 6, false),
    WATER_5(11, 3, 1, true, false, 0, false, true, 0.6f, 5, false),
    WATER_4(12, 3, 1, true, false, 0, false, true, 0.6f, 4, false),
    WATER_3(13, 3, 1, true, false, 0, false, true, 0.6f, 3, false),
    WATER_2(14, 3, 1, true, false, 0, false, true, 0.6f, 2, false),
    WATER_1(15, 3, 1, true, false, 0, false, true, 0.6f, 1, false);

    private final int id;
    private final int atlasX;
    private final int atlasY;
    private final boolean transparent;
    private final boolean solid;
    private final float hardness;
    private final boolean falls;
    private final boolean liquid;
    private final float alpha;
    private final int level;
    private final boolean source;

    Block(int id, int atlasX, int atlasY, boolean transparent, boolean solid, float hardness, boolean falls,
            boolean liquid, float alpha, int level, boolean source) {
        this.id = id;
        this.atlasX = atlasX;
        this.atlasY = atlasY;
        this.transparent = transparent;
        this.solid = solid;
        this.hardness = hardness;
        this.falls = falls;
        this.liquid = liquid;
        this.alpha = alpha;
        this.level = level;
        this.source = source;
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

    public boolean falls() {
        return falls;
    }

    public boolean isLiquid() {
        return liquid;
    }

    public float getAlpha() {
        return alpha;
    }

    public int getLevel() {
        return level;
    }

    public boolean isSource() {
        return source;
    }

    public static Block getWaterByLevel(int level) {
        if (level >= 16)
            return WATER_F16;
        if (level <= 0)
            return AIR;
        for (Block b : values()) {
            if (b.isLiquid() && !b.source && b.level == level)
                return b;
        }
        return WATER_1;
    }

    public static Block getById(int id) {
        for (Block b : values()) {
            if (b.id == id)
                return b;
        }
        return AIR;
    }
}
