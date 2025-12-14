package game.voxel;

import engine.voxel.Block;

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

    public int getSelectedSlot() {
        return selectedSlot;
    }
}
