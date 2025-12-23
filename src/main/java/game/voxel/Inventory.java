package game.voxel;

public class Inventory {

    private final Block[] hotbar;
    private int selectedSlot;

    public Inventory() {
        hotbar = new Block[] {
                Block.GRASS,
                Block.DIRT,
                Block.STONE,
                Block.SAND,

                Block.WOOD,
                Block.LEAVES,
                Block.BEDROCK,
                Block.WATER
        };
        selectedSlot = 0;
    }

    public Block getSelectedBlock() {
        if (selectedSlot >= 0 && selectedSlot < hotbar.length) {
            return hotbar[selectedSlot];
        }
        return Block.AIR;
    }

    public void selectSlot(int slot) {
        if (slot >= 0 && slot < hotbar.length) {
            selectedSlot = slot;
            System.out.println("Selected Block: " + hotbar[selectedSlot]);
        }
    }

    public void removeItem(int slot) {
        if (slot >= 0 && slot < hotbar.length) {
            hotbar[slot] = Block.AIR;
        }
    }

    public boolean addItem(Block block) {
        if (block == Block.AIR)
            return false;
        for (int i = 0; i < hotbar.length; i++) {
            if (hotbar[i] == Block.AIR) {
                hotbar[i] = block;
                return true;
            }
        }
        return false;
    }

    public int getSelectedSlot() {
        return selectedSlot;
    }
}
