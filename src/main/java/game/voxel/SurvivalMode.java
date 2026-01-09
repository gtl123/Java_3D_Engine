package game.voxel;

import org.joml.Vector3f;

/**
 * SurvivalMode: Implements a complete survival game mode with health, hunger, and resource management
 */
public class SurvivalMode {
    
    // Player stats
    private float health = 100.0f;
    private float maxHealth = 100.0f;
    private float hunger = 100.0f;
    private float maxHunger = 100.0f;
    private float thirst = 100.0f;
    private float maxThirst = 100.0f;
    private float stamina = 100.0f;
    private float maxStamina = 100.0f;
    
    // Environmental factors
    private float temperature = 20.0f;  // Celsius
    private float exposure = 0.0f;       // 0-100, affects health
    private boolean isRaining = false;
    private boolean isNight = false;
    
    // Inventory
    private int woodCount = 0;
    private int stoneCount = 0;
    private int foodCount = 5;
    private int waterCount = 5;
    
    // Timers
    private float hungerTimer = 0.0f;
    private float thirstTimer = 0.0f;
    private float staminaRegenTimer = 0.0f;
    
    public SurvivalMode() {
        this.health = maxHealth;
        this.hunger = maxHunger;
        this.thirst = maxThirst;
        this.stamina = maxStamina;
    }
    
    /**
     * Update survival mode each frame
     */
    public void update(float deltaTime, boolean isMoving, boolean isRunning, float timeOfDay) {
        // Update time-based factors
        updateHunger(deltaTime, isMoving, isRunning);
        updateThirst(deltaTime, isMoving, isRunning);
        updateStamina(deltaTime, isMoving, isRunning);
        updateTemperature(timeOfDay);
        updateHealth(deltaTime);
        
        // Clamp values
        clampStats();
    }
    
    /**
     * Update hunger system
     */
    private void updateHunger(float deltaTime, boolean isMoving, boolean isRunning) {
        hungerTimer += deltaTime;
        
        float hungerRate = 0.5f;  // Base hunger per second
        if (isMoving) hungerRate *= 1.5f;
        if (isRunning) hungerRate *= 2.5f;
        
        if (hungerTimer > 1.0f) {
            hunger -= hungerRate;
            hungerTimer = 0.0f;
        }
        
        // Health damage from starvation
        if (hunger < 0.0f) {
            health -= 0.5f;
            hunger = 0.0f;
        }
    }
    
    /**
     * Update thirst system
     */
    private void updateThirst(float deltaTime, boolean isMoving, boolean isRunning) {
        thirstTimer += deltaTime;
        
        float thirstRate = 0.3f;  // Base thirst per second
        if (isMoving) thirstRate *= 1.5f;
        if (isRunning) thirstRate *= 2.5f;
        if (temperature > 25.0f) thirstRate *= 1.5f;  // Hotter = more thirsty
        
        if (thirstTimer > 1.0f) {
            thirst -= thirstRate;
            thirstTimer = 0.0f;
        }
        
        // Health damage from dehydration
        if (thirst < 0.0f) {
            health -= 1.0f;
            thirst = 0.0f;
        }
    }
    
    /**
     * Update stamina system
     */
    private void updateStamina(float deltaTime, boolean isMoving, boolean isRunning) {
        if (isRunning) {
            stamina -= 20.0f * deltaTime;  // Drain stamina while running
        } else {
            staminaRegenTimer += deltaTime;
            if (staminaRegenTimer > 0.5f) {
                stamina += 5.0f;  // Regenerate stamina
                staminaRegenTimer = 0.0f;
            }
        }
    }
    
    /**
     * Update temperature based on time of day
     */
    private void updateTemperature(float timeOfDay) {
        // Temperature varies with time of day
        // 0.0 = midnight (coldest), 0.5 = noon (hottest)
        float baseTemp = 20.0f;
        float variation = 15.0f;
        
        float t = timeOfDay;
        if (t < 0.25f) {
            // Midnight to sunrise: cold
            temperature = baseTemp - variation;
        } else if (t < 0.5f) {
            // Sunrise to noon: warming
            temperature = baseTemp - variation + (t - 0.25f) / 0.25f * variation * 2.0f;
        } else if (t < 0.75f) {
            // Noon to sunset: cooling
            temperature = baseTemp + variation - (t - 0.5f) / 0.25f * variation * 2.0f;
        } else {
            // Sunset to midnight: cold
            temperature = baseTemp - variation;
        }
        
        // Rain affects temperature
        if (isRaining) {
            temperature -= 5.0f;
        }
    }
    
    /**
     * Update health based on various factors
     */
    private void updateHealth(float deltaTime) {
        // Exposure damage
        if (exposure > 50.0f) {
            health -= (exposure - 50.0f) * 0.01f * deltaTime;
        }
        
        // Hunger recovery
        if (hunger > 80.0f && health < maxHealth) {
            health += 0.1f * deltaTime;
        }
    }
    
    /**
     * Clamp all stat values
     */
    private void clampStats() {
        health = Math.max(0, Math.min(maxHealth, health));
        hunger = Math.max(0, Math.min(maxHunger, hunger));
        thirst = Math.max(0, Math.min(maxThirst, thirst));
        stamina = Math.max(0, Math.min(maxStamina, stamina));
        exposure = Math.max(0, Math.min(100, exposure));
    }
    
    /**
     * Eat food to restore hunger
     */
    public void eatFood(int amount) {
        if (foodCount >= amount) {
            hunger = Math.min(maxHunger, hunger + amount * 20.0f);
            foodCount -= amount;
        }
    }
    
    /**
     * Drink water to restore thirst
     */
    public void drinkWater(int amount) {
        if (waterCount >= amount) {
            thirst = Math.min(maxThirst, thirst + amount * 20.0f);
            waterCount -= amount;
        }
    }
    
    /**
     * Take damage
     */
    public void takeDamage(float amount) {
        health -= amount;
    }
    
    /**
     * Heal
     */
    public void heal(float amount) {
        health = Math.min(maxHealth, health + amount);
    }
    
    // Getters
    public float getHealth() { return health; }
    public float getHunger() { return hunger; }
    public float getThirst() { return thirst; }
    public float getStamina() { return stamina; }
    public float getTemperature() { return temperature; }
    public float getExposure() { return exposure; }
    public int getWoodCount() { return woodCount; }
    public int getStoneCount() { return stoneCount; }
    public int getFoodCount() { return foodCount; }
    public int getWaterCount() { return waterCount; }
    public boolean isAlive() { return health > 0; }
    public boolean isStarving() { return hunger < 20.0f; }
    public boolean isDehydrated() { return thirst < 20.0f; }
    public boolean isExhausted() { return stamina < 20.0f; }
    
    // Setters
    public void setRaining(boolean raining) { isRaining = raining; }
    public void setNight(boolean night) { isNight = night; }
    public void addWood(int amount) { woodCount += amount; }
    public void addStone(int amount) { stoneCount += amount; }
    public void addFood(int amount) { foodCount += amount; }
    public void addWater(int amount) { waterCount += amount; }
}
