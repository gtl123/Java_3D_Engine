package game.voxel.advanced;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * CraftingSystem: Advanced crafting and inventory management
 */
public class CraftingSystem {
    
    public enum ItemType {
        WOOD, STONE, DIRT, GRASS, SAND,
        WOOD_PLANKS, WOODEN_STICK, STONE_PICKAXE, WOODEN_PICKAXE,
        APPLE, BREAD, COOKED_MEAT, RAW_MEAT,
        WATER_BUCKET, EMPTY_BUCKET,
        TORCH, CHEST, CRAFTING_TABLE
    }
    
    /**
     * Item class
     */
    public static class Item {
        public ItemType type;
        public int quantity;
        public int maxStackSize;
        
        public Item(ItemType type, int quantity) {
            this.type = type;
            this.quantity = quantity;
            this.maxStackSize = getMaxStackSize(type);
        }
        
        private static int getMaxStackSize(ItemType type) {
            switch (type) {
                case WOOD:
                case STONE:
                case DIRT:
                case GRASS:
                case SAND:
                case WOOD_PLANKS:
                case WOODEN_STICK:
                case APPLE:
                case BREAD:
                case COOKED_MEAT:
                case RAW_MEAT:
                    return 64;
                case WOODEN_PICKAXE:
                case STONE_PICKAXE:
                case WATER_BUCKET:
                case EMPTY_BUCKET:
                case TORCH:
                case CHEST:
                case CRAFTING_TABLE:
                    return 1;
                default:
                    return 64;
            }
        }
    }
    
    /**
     * Recipe class
     */
    public static class Recipe {
        public String name;
        public Map<ItemType, Integer> inputs;
        public ItemType output;
        public int outputQuantity;
        
        public Recipe(String name, ItemType output, int outputQuantity) {
            this.name = name;
            this.output = output;
            this.outputQuantity = outputQuantity;
            this.inputs = new HashMap<>();
        }
        
        public void addInput(ItemType type, int quantity) {
            inputs.put(type, quantity);
        }
        
        public boolean canCraft(Inventory inventory) {
            for (Map.Entry<ItemType, Integer> entry : inputs.entrySet()) {
                if (inventory.getItemCount(entry.getKey()) < entry.getValue()) {
                    return false;
                }
            }
            return true;
        }
    }
    
    /**
     * Inventory class
     */
    public static class Inventory {
        private List<Item> items;
        private int maxSlots;
        
        public Inventory(int maxSlots) {
            this.maxSlots = maxSlots;
            this.items = new ArrayList<>();
        }
        
        public boolean addItem(ItemType type, int quantity) {
            // Try to add to existing stacks first
            for (Item item : items) {
                if (item.type == type && item.quantity < item.maxStackSize) {
                    int space = item.maxStackSize - item.quantity;
                    int toAdd = Math.min(space, quantity);
                    item.quantity += toAdd;
                    quantity -= toAdd;
                    
                    if (quantity == 0) {
                        return true;
                    }
                }
            }
            
            // Create new stacks if needed
            while (quantity > 0 && items.size() < maxSlots) {
                int stackSize = Math.min(quantity, Item.getMaxStackSize(type));
                items.add(new Item(type, stackSize));
                quantity -= stackSize;
            }
            
            return quantity == 0;
        }
        
        public boolean removeItem(ItemType type, int quantity) {
            int removed = 0;
            
            for (Item item : items) {
                if (item.type == type) {
                    int toRemove = Math.min(item.quantity, quantity - removed);
                    item.quantity -= toRemove;
                    removed += toRemove;
                    
                    if (removed == quantity) {
                        items.removeIf(i -> i.quantity <= 0);
                        return true;
                    }
                }
            }
            
            return false;
        }
        
        public int getItemCount(ItemType type) {
            int count = 0;
            for (Item item : items) {
                if (item.type == type) {
                    count += item.quantity;
                }
            }
            return count;
        }
        
        public List<Item> getItems() {
            return items;
        }
        
        public int getUsedSlots() {
            return items.size();
        }
        
        public int getMaxSlots() {
            return maxSlots;
        }
    }
    
    private Map<String, Recipe> recipes;
    private Inventory inventory;
    
    public CraftingSystem(int inventorySlots) {
        this.recipes = new HashMap<>();
        this.inventory = new Inventory(inventorySlots);
        initializeRecipes();
    }
    
    /**
     * Initialize default recipes
     */
    private void initializeRecipes() {
        // Wood to planks
        Recipe woodPlanks = new Recipe("Wood Planks", ItemType.WOOD_PLANKS, 4);
        woodPlanks.addInput(ItemType.WOOD, 1);
        recipes.put("wood_planks", woodPlanks);
        
        // Planks to sticks
        Recipe sticks = new Recipe("Wooden Sticks", ItemType.WOODEN_STICK, 4);
        sticks.addInput(ItemType.WOOD_PLANKS, 1);
        recipes.put("wooden_sticks", sticks);
        
        // Wooden pickaxe
        Recipe woodPickaxe = new Recipe("Wooden Pickaxe", ItemType.WOODEN_PICKAXE, 1);
        woodPickaxe.addInput(ItemType.WOOD_PLANKS, 3);
        woodPickaxe.addInput(ItemType.WOODEN_STICK, 2);
        recipes.put("wooden_pickaxe", woodPickaxe);
        
        // Stone pickaxe
        Recipe stonePickaxe = new Recipe("Stone Pickaxe", ItemType.STONE_PICKAXE, 1);
        stonePickaxe.addInput(ItemType.STONE, 3);
        stonePickaxe.addInput(ItemType.WOODEN_STICK, 2);
        recipes.put("stone_pickaxe", stonePickaxe);
        
        // Torch
        Recipe torch = new Recipe("Torch", ItemType.TORCH, 4);
        torch.addInput(ItemType.WOODEN_STICK, 1);
        torch.addInput(ItemType.WOOD, 1);
        recipes.put("torch", torch);
    }
    
    /**
     * Craft an item
     */
    public boolean craft(String recipeName) {
        Recipe recipe = recipes.get(recipeName);
        if (recipe == null) {
            return false;
        }
        
        if (!recipe.canCraft(inventory)) {
            return false;
        }
        
        // Remove inputs
        for (Map.Entry<ItemType, Integer> entry : recipe.inputs.entrySet()) {
            inventory.removeItem(entry.getKey(), entry.getValue());
        }
        
        // Add output
        inventory.addItem(recipe.output, recipe.outputQuantity);
        
        return true;
    }
    
    /**
     * Get available recipes
     */
    public List<Recipe> getAvailableRecipes() {
        List<Recipe> available = new ArrayList<>();
        for (Recipe recipe : recipes.values()) {
            if (recipe.canCraft(inventory)) {
                available.add(recipe);
            }
        }
        return available;
    }
    
    public Inventory getInventory() {
        return inventory;
    }
}
