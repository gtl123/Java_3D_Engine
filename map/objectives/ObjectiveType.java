package fps.map.objectives;

/**
 * Enumeration of objective types for different game modes and gameplay mechanics.
 * Each type defines specific behavior and interaction patterns.
 */
public enum ObjectiveType {
    
    // Bomb-related objectives
    BOMB_SITE("Bomb Site", "Site where bombs can be planted and defused", 
              ObjectiveCategory.DESTRUCTION, true, false),
    
    BOMB_SPAWN("Bomb Spawn", "Location where the bomb spawns at round start", 
               ObjectiveCategory.PICKUP, false, false),
    
    // Capture objectives
    CAPTURE_POINT("Capture Point", "Point that can be captured and held by teams", 
                  ObjectiveCategory.CONTROL, true, true),
    
    CONTROL_ZONE("Control Zone", "Large area that provides strategic control", 
                 ObjectiveCategory.CONTROL, true, true),
    
    KING_OF_HILL("King of the Hill", "Central point that must be held for victory", 
                 ObjectiveCategory.CONTROL, true, true),
    
    // Flag objectives
    FLAG("Flag", "Team flag that can be captured and returned", 
         ObjectiveCategory.PICKUP, true, false),
    
    FLAG_BASE("Flag Base", "Home base where flags are returned", 
              ObjectiveCategory.DELIVERY, false, false),
    
    // VIP and escort objectives
    VIP_SPAWN("VIP Spawn", "Starting location for VIP player", 
              ObjectiveCategory.ESCORT, false, false),
    
    VIP_EXTRACTION("VIP Extraction", "Extraction point for VIP player", 
                   ObjectiveCategory.ESCORT, false, false),
    
    ESCORT_CHECKPOINT("Escort Checkpoint", "Waypoint in escort missions", 
                      ObjectiveCategory.ESCORT, true, false),
    
    // Payload objectives
    PAYLOAD_CART("Payload Cart", "Cart that must be pushed to destination", 
                 ObjectiveCategory.ESCORT, true, true),
    
    PAYLOAD_CHECKPOINT("Payload Checkpoint", "Checkpoint along payload route", 
                       ObjectiveCategory.ESCORT, false, false),
    
    // Domination objectives
    DOMINATION_POINT("Domination Point", "Point in domination game mode", 
                     ObjectiveCategory.CONTROL, true, true),
    
    // Headquarters objectives
    HEADQUARTERS("Headquarters", "Temporary control point that moves", 
                 ObjectiveCategory.CONTROL, true, true),
    
    // Search and rescue
    HOSTAGE_SPAWN("Hostage Spawn", "Location where hostages start", 
                  ObjectiveCategory.RESCUE, false, false),
    
    HOSTAGE_RESCUE("Hostage Rescue", "Zone where hostages are rescued", 
                   ObjectiveCategory.RESCUE, false, false),
    
    // Intel and data
    INTEL_PICKUP("Intel Pickup", "Intelligence that can be collected", 
                 ObjectiveCategory.PICKUP, true, false),
    
    DATA_TERMINAL("Data Terminal", "Terminal for uploading/downloading data", 
                  ObjectiveCategory.INTERACTION, true, false),
    
    // Sabotage objectives
    SABOTAGE_TARGET("Sabotage Target", "Target that can be sabotaged", 
                    ObjectiveCategory.DESTRUCTION, true, false),
    
    GENERATOR("Generator", "Power generator that can be destroyed", 
              ObjectiveCategory.DESTRUCTION, true, false),
    
    // Supply objectives
    SUPPLY_CACHE("Supply Cache", "Cache of supplies that can be captured", 
                 ObjectiveCategory.PICKUP, true, false),
    
    AMMO_DEPOT("Ammo Depot", "Depot providing ammunition resupply", 
               ObjectiveCategory.INTERACTION, false, false),
    
    // Elimination objectives
    ELIMINATION_ZONE("Elimination Zone", "Zone where eliminations count for victory", 
                     ObjectiveCategory.ELIMINATION, false, false),
    
    // Custom objectives
    CUSTOM("Custom", "Custom objective type for mod support", 
           ObjectiveCategory.CUSTOM, true, true);
    
    private final String displayName;
    private final String description;
    private final ObjectiveCategory category;
    private final boolean canBeContested;
    private final boolean canBeNeutral;
    
    ObjectiveType(String displayName, String description, ObjectiveCategory category, 
                  boolean canBeContested, boolean canBeNeutral) {
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.canBeContested = canBeContested;
        this.canBeNeutral = canBeNeutral;
    }
    
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public ObjectiveCategory getCategory() { return category; }
    public boolean canBeContested() { return canBeContested; }
    public boolean canBeNeutral() { return canBeNeutral; }
    
    /**
     * Get default interaction type for this objective type
     */
    public ObjectiveInteractionType getDefaultInteractionType() {
        switch (this) {
            case BOMB_SITE:
                return ObjectiveInteractionType.PLANT_DEFUSE;
            case CAPTURE_POINT:
            case CONTROL_ZONE:
            case DOMINATION_POINT:
            case KING_OF_HILL:
            case HEADQUARTERS:
                return ObjectiveInteractionType.CAPTURE_HOLD;
            case FLAG:
            case INTEL_PICKUP:
            case SUPPLY_CACHE:
                return ObjectiveInteractionType.PICKUP_CARRY;
            case DATA_TERMINAL:
            case AMMO_DEPOT:
                return ObjectiveInteractionType.INTERACT_CHANNEL;
            case SABOTAGE_TARGET:
            case GENERATOR:
                return ObjectiveInteractionType.SABOTAGE;
            case VIP_EXTRACTION:
            case HOSTAGE_RESCUE:
            case FLAG_BASE:
                return ObjectiveInteractionType.DELIVERY;
            default:
                return ObjectiveInteractionType.INSTANT;
        }
    }
    
    /**
     * Get default interaction time for this objective type
     */
    public float getDefaultInteractionTime() {
        switch (this) {
            case BOMB_SITE:
                return 5.0f; // 5 seconds to plant/defuse
            case CAPTURE_POINT:
            case DOMINATION_POINT:
                return 10.0f; // 10 seconds to capture
            case CONTROL_ZONE:
            case KING_OF_HILL:
                return 15.0f; // 15 seconds for large zones
            case DATA_TERMINAL:
                return 8.0f; // 8 seconds to hack
            case SABOTAGE_TARGET:
            case GENERATOR:
                return 12.0f; // 12 seconds to sabotage
            case FLAG:
            case INTEL_PICKUP:
                return 1.0f; // 1 second to pickup
            case SUPPLY_CACHE:
                return 3.0f; // 3 seconds to collect
            default:
                return 0.0f; // Instant
        }
    }
    
    /**
     * Get default interaction radius for this objective type
     */
    public float getDefaultInteractionRadius() {
        switch (this) {
            case CAPTURE_POINT:
            case CONTROL_ZONE:
            case DOMINATION_POINT:
            case KING_OF_HILL:
            case HEADQUARTERS:
                return 8.0f; // Large radius for area control
            case BOMB_SITE:
                return 3.0f; // Medium radius for bomb sites
            case FLAG:
            case INTEL_PICKUP:
            case DATA_TERMINAL:
                return 2.0f; // Small radius for pickups
            case SUPPLY_CACHE:
            case AMMO_DEPOT:
                return 4.0f; // Medium radius for supplies
            case VIP_EXTRACTION:
            case HOSTAGE_RESCUE:
                return 5.0f; // Large radius for rescue zones
            default:
                return 3.0f; // Default medium radius
        }
    }
    
    /**
     * Check if this objective type supports multiple simultaneous users
     */
    public boolean supportsMultipleUsers() {
        switch (this) {
            case CAPTURE_POINT:
            case CONTROL_ZONE:
            case DOMINATION_POINT:
            case KING_OF_HILL:
            case HEADQUARTERS:
            case PAYLOAD_CART:
            case VIP_EXTRACTION:
            case HOSTAGE_RESCUE:
            case ELIMINATION_ZONE:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Get maximum simultaneous users for this objective type
     */
    public int getMaxSimultaneousUsers() {
        if (!supportsMultipleUsers()) {
            return 1;
        }
        
        switch (this) {
            case CAPTURE_POINT:
            case DOMINATION_POINT:
                return 6; // Up to 6 players can capture together
            case CONTROL_ZONE:
            case KING_OF_HILL:
            case HEADQUARTERS:
                return 8; // Large zones support more players
            case PAYLOAD_CART:
                return 4; // Up to 4 players can push payload
            case VIP_EXTRACTION:
            case HOSTAGE_RESCUE:
                return 8; // Rescue zones can handle multiple players
            case ELIMINATION_ZONE:
                return 32; // No limit for elimination zones
            default:
                return 4; // Default for multi-user objectives
        }
    }
    
    /**
     * Check if this objective type requires line of sight
     */
    public boolean requiresLineOfSight() {
        switch (this) {
            case BOMB_SITE:
            case FLAG:
            case INTEL_PICKUP:
            case DATA_TERMINAL:
            case SABOTAGE_TARGET:
            case GENERATOR:
            case SUPPLY_CACHE:
                return true; // These require direct interaction
            case CAPTURE_POINT:
            case CONTROL_ZONE:
            case DOMINATION_POINT:
            case KING_OF_HILL:
            case HEADQUARTERS:
                return false; // Area control doesn't require LOS
            default:
                return false;
        }
    }
}

/**
 * Categories for grouping objective types
 */
enum ObjectiveCategory {
    CONTROL("Control", "Objectives focused on area control"),
    DESTRUCTION("Destruction", "Objectives involving destruction or sabotage"),
    PICKUP("Pickup", "Objectives involving collecting items"),
    DELIVERY("Delivery", "Objectives involving delivering items or players"),
    ESCORT("Escort", "Objectives involving escorting players or objects"),
    RESCUE("Rescue", "Objectives involving rescuing hostages or players"),
    INTERACTION("Interaction", "Objectives requiring interaction with objects"),
    ELIMINATION("Elimination", "Objectives based on player elimination"),
    CUSTOM("Custom", "Custom objective categories");
    
    private final String displayName;
    private final String description;
    
    ObjectiveCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}

/**
 * Types of interactions with objectives
 */
enum ObjectiveInteractionType {
    INSTANT("Instant", "Immediate interaction with no delay"),
    CHANNEL("Channel", "Channeled interaction requiring continuous input"),
    CAPTURE_HOLD("Capture Hold", "Capture by standing in area over time"),
    PLANT_DEFUSE("Plant/Defuse", "Plant or defuse bombs with timer"),
    PICKUP_CARRY("Pickup Carry", "Pick up and carry objects"),
    DELIVERY("Delivery", "Deliver objects or players to location"),
    INTERACT_CHANNEL("Interact Channel", "Interact with channeling time"),
    SABOTAGE("Sabotage", "Sabotage or destroy targets"),
    ESCORT("Escort", "Escort players or objects"),
    RESCUE("Rescue", "Rescue hostages or players");
    
    private final String displayName;
    private final String description;
    
    ObjectiveInteractionType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    
    /**
     * Check if this interaction type requires continuous presence
     */
    public boolean requiresContinuousPresence() {
        switch (this) {
            case CAPTURE_HOLD:
            case CHANNEL:
            case INTERACT_CHANNEL:
            case ESCORT:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Check if this interaction type can be interrupted
     */
    public boolean canBeInterrupted() {
        switch (this) {
            case INSTANT:
            case PICKUP_CARRY:
                return false;
            default:
                return true;
        }
    }
}