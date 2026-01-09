package fps.anticheat.server;

import engine.logging.LogManager;
import fps.anticheat.*;
import fps.weapon.WeaponDefinition;
import fps.weapon.WeaponManager;

/**
 * Validates weapon-related actions including firing rates, ammunition, and reload times.
 * Ensures all weapon usage follows game rules and weapon specifications.
 */
public class WeaponValidator {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Configuration
    private AntiCheatConfiguration config;
    
    // Weapon constraints
    private float fireRateTolerance = 1.1f; // 10% tolerance
    private float reloadTimeTolerance = 0.9f; // Allow 10% faster reloads
    private int ammunitionTolerance = 5; // Allow 5 extra rounds tolerance
    
    // Weapon manager reference for weapon definitions
    private WeaponManager weaponManager;
    
    public WeaponValidator() {
        logManager.debug("WeaponValidator", "Weapon validator created");
    }
    
    /**
     * Set configuration
     */
    public void setConfiguration(AntiCheatConfiguration config) {
        this.config = config;
        
        if (config != null) {
            // Update constraints based on configuration
            this.fireRateTolerance = 1.0f + (config.getMaxFireRate() / 10000.0f); // Dynamic tolerance
        }
    }
    
    /**
     * Set weapon manager reference
     */
    public void setWeaponManager(WeaponManager weaponManager) {
        this.weaponManager = weaponManager;
    }
    
    /**
     * Validate a weapon action
     */
    public ValidationResult validate(PlayerValidationState playerState, PlayerAction action) {
        try {
            // Validate based on action type
            switch (action.getActionType()) {
                case FIRE_WEAPON:
                    return validateWeaponFire(playerState, action);
                case RELOAD_WEAPON:
                    return validateWeaponReload(playerState, action);
                case SWITCH_WEAPON:
                    return validateWeaponSwitch(playerState, action);
                default:
                    return ValidationResult.allowed();
            }
            
        } catch (Exception e) {
            logManager.error("WeaponValidator", "Error validating weapon action", e);
            return ValidationResult.denied("Weapon validation error", ViolationType.SERVER_VALIDATION);
        }
    }
    
    /**
     * Validate weapon firing
     */
    private ValidationResult validateWeaponFire(PlayerValidationState playerState, PlayerAction action) {
        PlayerAction.WeaponActionData weaponData = action.getWeaponData();
        if (weaponData == null) {
            return ValidationResult.denied("Missing weapon data for fire action", ViolationType.SERVER_VALIDATION);
        }
        
        String weaponId = weaponData.getWeaponId();
        if (weaponId == null || weaponId.isEmpty()) {
            return ValidationResult.denied("Invalid weapon ID", ViolationType.SERVER_VALIDATION);
        }
        
        // Get weapon definition
        WeaponDefinition weaponDef = getWeaponDefinition(weaponId);
        if (weaponDef == null) {
            return ValidationResult.denied("Unknown weapon: " + weaponId, ViolationType.SERVER_VALIDATION);
        }
        
        // Validate fire rate
        ValidationResult fireRateResult = validateFireRate(playerState, action, weaponDef);
        if (!fireRateResult.isValid()) {
            return fireRateResult;
        }
        
        // Validate ammunition
        ValidationResult ammoResult = validateAmmunition(playerState, action, weaponDef);
        if (!ammoResult.isValid()) {
            return ammoResult;
        }
        
        // Validate weapon state consistency
        ValidationResult stateResult = validateWeaponState(playerState, action, weaponDef);
        if (!stateResult.isValid()) {
            return stateResult;
        }
        
        // Validate shot accuracy (basic check for impossible shots)
        ValidationResult accuracyResult = validateShotAccuracy(playerState, action, weaponDef);
        if (!accuracyResult.isValid()) {
            return accuracyResult;
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate weapon reload
     */
    private ValidationResult validateWeaponReload(PlayerValidationState playerState, PlayerAction action) {
        PlayerAction.WeaponActionData weaponData = action.getWeaponData();
        if (weaponData == null) {
            return ValidationResult.denied("Missing weapon data for reload action", ViolationType.SERVER_VALIDATION);
        }
        
        String weaponId = weaponData.getWeaponId();
        WeaponDefinition weaponDef = getWeaponDefinition(weaponId);
        if (weaponDef == null) {
            return ValidationResult.denied("Unknown weapon for reload: " + weaponId, ViolationType.SERVER_VALIDATION);
        }
        
        // Check if weapon needs reloading
        if (playerState.getCurrentAmmunition() >= weaponDef.getMagazineSize()) {
            return ValidationResult.denied("Reload with full magazine", ViolationType.SERVER_VALIDATION);
        }
        
        // Validate reload timing
        long timeSinceLastFire = action.getTimestamp() - playerState.getLastFireTime();
        float minReloadTime = weaponDef.getReloadTime() * 1000 * reloadTimeTolerance; // Convert to ms
        
        if (timeSinceLastFire < minReloadTime) {
            return ValidationResult.denied(
                String.format("Reload too fast: %.3fs (min: %.3fs)", 
                             timeSinceLastFire / 1000.0f, minReloadTime / 1000.0f),
                ViolationType.IMPOSSIBLE_SHOT,
                timeSinceLastFire
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate weapon switching
     */
    private ValidationResult validateWeaponSwitch(PlayerValidationState playerState, PlayerAction action) {
        PlayerAction.WeaponActionData weaponData = action.getWeaponData();
        if (weaponData == null) {
            return ValidationResult.denied("Missing weapon data for switch action", ViolationType.SERVER_VALIDATION);
        }
        
        String newWeaponId = weaponData.getWeaponId();
        String currentWeaponId = playerState.getCurrentWeapon();
        
        // Check if actually switching to a different weapon
        if (newWeaponId.equals(currentWeaponId)) {
            return ValidationResult.allowed(); // Not actually switching
        }
        
        // Validate weapon switch timing (prevent rapid switching)
        long timeSinceLastFire = action.getTimestamp() - playerState.getLastFireTime();
        float minSwitchTime = 200; // 200ms minimum switch time
        
        if (timeSinceLastFire < minSwitchTime) {
            return ValidationResult.denied(
                String.format("Weapon switch too fast: %.3fs (min: %.3fs)", 
                             timeSinceLastFire / 1000.0f, minSwitchTime / 1000.0f),
                ViolationType.RATE_LIMIT_EXCEEDED,
                timeSinceLastFire
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate fire rate against weapon specifications
     */
    private ValidationResult validateFireRate(PlayerValidationState playerState, PlayerAction action, WeaponDefinition weaponDef) {
        long timeSinceLastFire = action.getTimestamp() - playerState.getLastFireTime();
        
        // Skip validation for first shot
        if (playerState.getLastFireTime() == 0) {
            return ValidationResult.allowed();
        }
        
        // Calculate minimum time between shots based on weapon fire rate
        float fireRateRPM = weaponDef.getFireRate();
        float minTimeBetweenShots = (60.0f / fireRateRPM) * 1000.0f / fireRateTolerance; // Convert to ms with tolerance
        
        if (timeSinceLastFire < minTimeBetweenShots) {
            return ValidationResult.denied(
                String.format("Fire rate exceeded: %.3fs between shots (min: %.3fs)", 
                             timeSinceLastFire / 1000.0f, minTimeBetweenShots / 1000.0f),
                ViolationType.IMPOSSIBLE_SHOT,
                timeSinceLastFire
            );
        }
        
        // Check for burst fire limits
        if (playerState.getShotsInBurst() > getMaxBurstSize(weaponDef)) {
            return ValidationResult.denied(
                String.format("Burst size exceeded: %d shots (max: %d)", 
                             playerState.getShotsInBurst(), getMaxBurstSize(weaponDef)),
                ViolationType.IMPOSSIBLE_SHOT,
                playerState.getShotsInBurst()
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate ammunition consistency
     */
    private ValidationResult validateAmmunition(PlayerValidationState playerState, PlayerAction action, WeaponDefinition weaponDef) {
        PlayerAction.WeaponActionData weaponData = action.getWeaponData();
        int currentAmmo = weaponData.getAmmunition();
        int previousAmmo = playerState.getCurrentAmmunition();
        
        // Check if ammunition decreased by exactly 1 (or within tolerance)
        int expectedAmmo = previousAmmo - 1;
        int ammoTolerance = this.ammunitionTolerance;
        
        if (Math.abs(currentAmmo - expectedAmmo) > ammoTolerance) {
            return ValidationResult.denied(
                String.format("Ammunition inconsistency: %d -> %d (expected: %d)", 
                             previousAmmo, currentAmmo, expectedAmmo),
                ViolationType.SERVER_VALIDATION,
                currentAmmo
            );
        }
        
        // Check for negative ammunition
        if (currentAmmo < 0) {
            return ValidationResult.denied(
                "Negative ammunition: " + currentAmmo,
                ViolationType.SERVER_VALIDATION,
                currentAmmo
            );
        }
        
        // Check for ammunition exceeding magazine size
        if (currentAmmo > weaponDef.getMagazineSize() + ammunitionTolerance) {
            return ValidationResult.denied(
                String.format("Ammunition exceeds magazine size: %d (max: %d)", 
                             currentAmmo, weaponDef.getMagazineSize()),
                ViolationType.SERVER_VALIDATION,
                currentAmmo
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate weapon state consistency
     */
    private ValidationResult validateWeaponState(PlayerValidationState playerState, PlayerAction action, WeaponDefinition weaponDef) {
        PlayerAction.WeaponActionData weaponData = action.getWeaponData();
        
        // Validate weapon ID consistency
        if (!weaponData.getWeaponId().equals(playerState.getCurrentWeapon())) {
            // Allow weapon switch, but validate it was properly switched
            return ValidationResult.allowed(); // This would be caught by weapon switch validation
        }
        
        // Validate fire origin (should be near player position)
        if (weaponData.getFireOrigin() != null && action.getPosition() != null) {
            float distance = weaponData.getFireOrigin().distance(action.getPosition());
            float maxDistance = 2.0f; // 2 meter tolerance for weapon position offset
            
            if (distance > maxDistance) {
                return ValidationResult.denied(
                    String.format("Fire origin too far from player: %.2fm (max: %.2fm)", 
                                 distance, maxDistance),
                    ViolationType.IMPOSSIBLE_SHOT,
                    distance
                );
            }
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Validate shot accuracy for impossible shots
     */
    private ValidationResult validateShotAccuracy(PlayerValidationState playerState, PlayerAction action, WeaponDefinition weaponDef) {
        PlayerAction.WeaponActionData weaponData = action.getWeaponData();
        
        // Check for impossible accuracy values
        if (weaponData.getAccuracy() > 1.0f) {
            return ValidationResult.denied(
                String.format("Impossible accuracy: %.2f%% (max: 100%%)", 
                             weaponData.getAccuracy() * 100),
                ViolationType.IMPOSSIBLE_ACCURACY,
                weaponData.getAccuracy()
            );
        }
        
        // Check for perfect accuracy with inaccurate weapons
        if (weaponData.getAccuracy() > weaponDef.getBaseAccuracy() * 1.2f) { // 20% tolerance
            return ValidationResult.denied(
                String.format("Accuracy too high for weapon: %.2f%% (max: %.2f%%)", 
                             weaponData.getAccuracy() * 100, 
                             weaponDef.getBaseAccuracy() * 120),
                ViolationType.IMPOSSIBLE_ACCURACY,
                weaponData.getAccuracy()
            );
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Get weapon definition from weapon manager
     */
    private WeaponDefinition getWeaponDefinition(String weaponId) {
        if (weaponManager != null) {
            return weaponManager.getWeaponDefinition(weaponId);
        }
        
        // Fallback: create basic weapon definition
        logManager.warn("WeaponValidator", "No weapon manager available, using fallback definition");
        return createFallbackWeaponDefinition(weaponId);
    }
    
    /**
     * Create fallback weapon definition for validation
     */
    private WeaponDefinition createFallbackWeaponDefinition(String weaponId) {
        // Create basic weapon definition with conservative values
        return new WeaponDefinition.Builder(weaponId, weaponId, WeaponDefinition.WeaponType.ASSAULT_RIFLE)
            .fireRate(600.0f)
            .magazineSize(30)
            .reloadTime(2.5f)
            .baseAccuracy(0.8f)
            .build();
    }
    
    /**
     * Get maximum burst size for weapon
     */
    private int getMaxBurstSize(WeaponDefinition weaponDef) {
        switch (weaponDef.getFireMode()) {
            case SEMI_AUTO:
                return 1;
            case BURST:
                return 3;
            case FULL_AUTO:
                return weaponDef.getMagazineSize();
            case BOLT_ACTION:
                return 1;
            default:
                return 1;
        }
    }
    
    /**
     * Update validator
     */
    public void update(float deltaTime) {
        // Update any time-based validation parameters
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        logManager.debug("WeaponValidator", "Weapon validator cleaned up");
    }
    
    // Getters and setters
    public float getFireRateTolerance() { return fireRateTolerance; }
    public void setFireRateTolerance(float fireRateTolerance) { this.fireRateTolerance = fireRateTolerance; }
    
    public float getReloadTimeTolerance() { return reloadTimeTolerance; }
    public void setReloadTimeTolerance(float reloadTimeTolerance) { this.reloadTimeTolerance = reloadTimeTolerance; }
    
    public int getAmmunitionTolerance() { return ammunitionTolerance; }
    public void setAmmunitionTolerance(int ammunitionTolerance) { this.ammunitionTolerance = ammunitionTolerance; }
}