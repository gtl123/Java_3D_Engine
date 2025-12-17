package engine.voxel;

public enum Block {
    AIR(0, 0, 0, true, false),
    GRASS(1, 0, 0, false, true), // solid
    DIRT(2, 1, 0, false, true),
    STONE(3, 2, 0, false, true),
    BEDROCK(4, 3, 0, false, true),
    WOOD(5, 0, 1, false, true),
    LEAVES(6, 1, 1, true, false), // transparent, not solid
    SAND(7, 2, 1, false, true),
    WATER(8, 3, 1, true, false); // transparent, not solid

    private final int id;
    private final int atlasX;
    private final int atlasY;
    private final boolean transparent;
    private final boolean solid;

    Block(int id, int atlasX, int atlasY, boolean transparent, boolean solid) {
        this.id = id;
        this.atlasX = atlasX;
        this.atlasY = atlasY;
        this.transparent = transparent;
        this.solid = solid;
    }

    public int getId() { return id; }
    public int getAtlasX() { return atlasX; }
    public int getAtlasY() { return atlasY; }
    public boolean isTransparent() { return transparent; }
    public boolean isSolid() { return solid; }

    public static Block getById(int id) {
        for (Block b : values()) {
            if (b.id == id) return b;
        }
        return AIR;
    }
}
