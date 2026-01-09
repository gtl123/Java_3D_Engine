package fps.anticheat;

import engine.logging.LogManager;
import fps.anticheat.server.ServerSideValidator;
import fps.anticheat.client.ClientSideMonitor;
import fps.anticheat.behavioral.BehavioralAnalysisEngine;
import fps.anticheat.statistical.StatisticalAnomalyDetector;
import fps.anticheat.hardware.HardwareValidator;
import fps.anticheat.detection.RealTimeCheatDetector;
import fps.anticheat.punishment.BanManager;
import fps.anticheat.security.SecurityObfuscator;
import fps.anticheat.logging.AntiCheatLogger;
import fps.player.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;

/**
 * Central manager for the enterprise-grade anti-cheat system.
 * Coordinates all anti-cheat components and provides unified interface.
 */
public class AntiCheatManager {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static AntiCheatManager instance;
    
    // Core anti-cheat components
    private ServerSideValidator serverValidator;
    private ClientSideMonitor clientMonitor;
    private BehavioralAnalysisEngine behavioralEngine;
    private StatisticalAnomalyDetector statisticalDetector;
    private HardwareValidator hardwareValidator;
    private RealTimeCheatDetector realtimeDetector;
    private BanManager banManager;
    private SecurityObfuscator securityObfuscator;
    private AntiCheatLogger antiCheatLogger;
    
    // Player monitoring
    private final Map<Integer, PlayerAntiCheatProfile> playerProfiles = new ConcurrentHashMap<>();
    
    // System state
    private boolean initialized = false;
    private boolean enabled = true;
    private AntiCheatConfiguration config;
    
    // Background processing
    private ScheduledExecutorService executorService;
    
    // Performance metrics
    private long totalChecksPerformed = 0;
    private long totalViolationsDetected = 0;
    private long totalPlayersMonitored = 0;
    
    private AntiCheatManager() {
        logManager.info("AntiCheatManager", "Anti-cheat manager created");
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized AntiCheatManager getInstance() {
        if (instance == null) {
            instance = new AntiCheatManager();
        }
        return instance;
    }
    
    /**
     * Initialize the anti-cheat system
     */
    public void initialize() throws Exception {
        if (initialized) {
            return;
        }
        
        logManager.info("AntiCheatManager", "Initializing Anti-Cheat System");
        
        try {
            // Load configuration
            config = new AntiCheatConfiguration();
            config.loadDefaults();
            
            // Initialize logging first
            antiCheatLogger = new AntiCheatLogger();
            antiCheatLogger.initialize();
            
            // Initialize security obfuscation
            securityObfuscator = new SecurityObfuscator();
            securityObfuscator.initialize();
            
            // Initialize core components
            serverValidator = new ServerSideValidator();
            serverValidator.initialize();
            
            clientMonitor = new ClientSideMonitor();
            clientMonitor.initialize();
            
            behavioralEngine = new BehavioralAnalysisEngine();
            behavioralEngine.initialize();
            
            statisticalDetector = new StatisticalAnomalyDetector();
            statisticalDetector.initialize();
            
            hardwareValidator = new HardwareValidator();
            hardwareValidator.initialize();
            
            realtimeDetector = new RealTimeCheatDetector();
            realtimeDetector.initialize();
            
            banManager = new BanManager();
            banManager.initialize();
            
            // Initialize background processing
            executorService = Executors.newScheduledThreadPool(4);
            
            // Start background tasks
            startBackgroundTasks();
            
            initialized = true;
            
            logManager.info("AntiCheatManager", "Anti-Cheat System initialization complete");
            antiCheatLogger.logSystemEvent("SYSTEM_INITIALIZED", "Anti-cheat system started successfully");
            
        } catch (Exception e) {
            logManager.error("AntiCheatManager", "Failed to initialize Anti-Cheat System", e);
            antiCheatLogger.logSystemEvent("SYSTEM_INIT_FAILED", "Anti-cheat initialization failed: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Start background monitoring tasks
     */
    private void startBackgroundTasks() {
        // Behavioral analysis task (every 5 seconds)
        executorService.scheduleAtFixedRate(() -> {
            try {
                performBehavioralAnalysis();
            } catch (Exception e) {
                logManager.error("AntiCheatManager", "Error in behavioral analysis task", e);
            }
        }, 5, 5, TimeUnit.SECONDS);
        
        // Statistical analysis task (every 30 seconds)
        executorService.scheduleAtFixedRate(() -> {
            try {
                performStatisticalAnalysis();
            } catch (Exception e) {
                logManager.error("AntiCheatManager", "Error in statistical analysis task", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
        
        // Hardware validation task (every 60 seconds)
        executorService.scheduleAtFixedRate(() -> {
            try {
                performHardwareValidation();
            } catch (Exception e) {
                logManager.error("AntiCheatManager", "Error in hardware validation task", e);
            }
        }, 60, 60, TimeUnit.SECONDS);
        
        // Cleanup task (every 5 minutes)
        executorService.scheduleAtFixedRate(() -> {
            try {
                performCleanup();
            } catch (Exception e) {
                logManager.error("AntiCheatManager", "Error in cleanup task", e);
            }
        }, 300, 300, TimeUnit.SECONDS);
    }
    
    /**
     * Register a player for anti-cheat monitoring
     */
    public void registerPlayer(Player player) {
        if (!initialized || !enabled) {
            return;
        }
        
        int playerId = player.getPlayerId();
        
        // Create anti-cheat profile for player
        PlayerAntiCheatProfile profile = new PlayerAntiCheatProfile(player);
        playerProfiles.put(playerId, profile);
        
        // Initialize hardware fingerprint
        hardwareValidator.generateFingerprint(playerId);
        
        // Start client-side monitoring
        clientMonitor.startMonitoring(playerId);
        
        totalPlayersMonitored++;
        
        logManager.info("AntiCheatManager", "Player registered for anti-cheat monitoring",
                       "playerId", playerId,
                       "playerName", player.getPlayerName());
        
        antiCheatLogger.logPlayerEvent(playerId, "PLAYER_REGISTERED", 
                                     "Player registered for anti-cheat monitoring");
    }
    
    /**
     * Unregister a player from anti-cheat monitoring
     */
    public void unregisterPlayer(int playerId) {
        PlayerAntiCheatProfile profile = playerProfiles.remove(playerId);
        
        if (profile != null) {
            // Stop client-side monitoring
            clientMonitor.stopMonitoring(playerId);
            
            // Generate final report
            generatePlayerReport(profile);
            
            logManager.info("AntiCheatManager", "Player unregistered from anti-cheat monitoring",
                           "playerId", playerId);
            
            antiCheatLogger.logPlayerEvent(playerId, "PLAYER_UNREGISTERED", 
                                         "Player unregistered from anti-cheat monitoring");
        }
    }
    
    /**
     * Validate player action on server side
     */
    public ValidationResult validatePlayerAction(int playerId, PlayerAction action) {
        if (!initialized || !enabled) {
            return ValidationResult.allowed();
        }
        
        totalChecksPerformed++;
        
        // Server-side validation
        ValidationResult serverResult = serverValidator.validateAction(playerId, action);
        
        if (!serverResult.isValid()) {
            handleViolation(playerId, ViolationType.SERVER_VALIDATION, 
                          serverResult.getReason(), action);
            return serverResult;
        }
        
        // Real-time cheat detection
        ValidationResult realtimeResult = realtimeDetector.checkAction(playerId, action);
        
        if (!realtimeResult.isValid()) {
            handleViolation(playerId, ViolationType.REALTIME_DETECTION, 
                          realtimeResult.getReason(), action);
            return realtimeResult;
        }
        
        // Update player profile
        PlayerAntiCheatProfile profile = playerProfiles.get(playerId);
        if (profile != null) {
            profile.recordAction(action);
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * Handle detected violation
     */
    private void handleViolation(int playerId, ViolationType type, String reason, Object evidence) {
        totalViolationsDetected++;
        
        PlayerAntiCheatProfile profile = playerProfiles.get(playerId);
        if (profile == null) {
            return;
        }
        
        // Record violation
        CheatViolation violation = new CheatViolation(playerId, type, reason, evidence);
        profile.addViolation(violation);
        
        // Log violation
        antiCheatLogger.logViolation(violation);
        
        // Determine punishment
        PunishmentAction punishment = banManager.determinePunishment(playerId, violation);
        
        if (punishment != null) {
            // Apply punishment
            banManager.applyPunishment(playerId, punishment);
            
            logManager.warn("AntiCheatManager", "Punishment applied",
                           "playerId", playerId,
                           "violationType", type,
                           "punishment", punishment.getType(),
                           "reason", reason);
            
            antiCheatLogger.logPunishment(playerId, punishment, violation);
        }
        
        logManager.warn("AntiCheatManager", "Cheat violation detected",
                       "playerId", playerId,
                       "violationType", type,
                       "reason", reason);
    }
    
    /**
     * Perform behavioral analysis on all players
     */
    private void performBehavioralAnalysis() {
        for (PlayerAntiCheatProfile profile : playerProfiles.values()) {
            BehavioralAnalysisResult result = behavioralEngine.analyzePlayer(profile);
            
            if (result.isSuspicious()) {
                handleViolation(profile.getPlayerId(), ViolationType.BEHAVIORAL_ANALYSIS, 
                              result.getReason(), result);
            }
        }
    }
    
    /**
     * Perform statistical analysis on all players
     */
    private void performStatisticalAnalysis() {
        for (PlayerAntiCheatProfile profile : playerProfiles.values()) {
            StatisticalAnalysisResult result = statisticalDetector.analyzePlayer(profile);
            
            if (result.isAnomalous()) {
                handleViolation(profile.getPlayerId(), ViolationType.STATISTICAL_ANOMALY, 
                              result.getReason(), result);
            }
        }
    }
    
    /**
     * Perform hardware validation on all players
     */
    private void performHardwareValidation() {
        for (PlayerAntiCheatProfile profile : playerProfiles.values()) {
            HardwareValidationResult result = hardwareValidator.validatePlayer(profile.getPlayerId());
            
            if (!result.isValid()) {
                handleViolation(profile.getPlayerId(), ViolationType.HARDWARE_VALIDATION, 
                              result.getReason(), result);
            }
        }
    }
    
    /**
     * Perform cleanup of old data
     */
    private void performCleanup() {
        // Clean up old violation records
        long cutoffTime = System.currentTimeMillis() - config.getViolationRetentionTime();
        
        for (PlayerAntiCheatProfile profile : playerProfiles.values()) {
            profile.cleanupOldViolations(cutoffTime);
        }
        
        // Clean up ban manager
        banManager.cleanup();
        
        // Clean up logger
        antiCheatLogger.cleanup();
    }
    
    /**
     * Generate comprehensive report for a player
     */
    private void generatePlayerReport(PlayerAntiCheatProfile profile) {
        AntiCheatReport report = new AntiCheatReport(profile);
        antiCheatLogger.logReport(report);
    }
    
    /**
     * Update anti-cheat system
     */
    public void update(float deltaTime) {
        if (!initialized || !enabled) {
            return;
        }
        
        try {
            // Update components
            serverValidator.update(deltaTime);
            clientMonitor.update(deltaTime);
            realtimeDetector.update(deltaTime);
            
            // Update player profiles
            for (PlayerAntiCheatProfile profile : playerProfiles.values()) {
                profile.update(deltaTime);
            }
            
        } catch (Exception e) {
            logManager.error("AntiCheatManager", "Error updating anti-cheat system", e);
        }
    }
    
    /**
     * Get anti-cheat statistics
     */
    public AntiCheatStatistics getStatistics() {
        AntiCheatStatistics stats = new AntiCheatStatistics();
        stats.totalChecksPerformed = totalChecksPerformed;
        stats.totalViolationsDetected = totalViolationsDetected;
        stats.totalPlayersMonitored = totalPlayersMonitored;
        stats.activePlayersMonitored = playerProfiles.size();
        stats.systemEnabled = enabled;
        stats.systemInitialized = initialized;
        
        return stats;
    }
    
    /**
     * Enable or disable anti-cheat system
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        
        logManager.info("AntiCheatManager", "Anti-cheat system " + (enabled ? "enabled" : "disabled"));
        antiCheatLogger.logSystemEvent("SYSTEM_" + (enabled ? "ENABLED" : "DISABLED"), 
                                      "Anti-cheat system " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        logManager.info("AntiCheatManager", "Cleaning up Anti-Cheat System");
        
        try {
            // Stop background tasks
            if (executorService != null) {
                executorService.shutdown();
                executorService.awaitTermination(5, TimeUnit.SECONDS);
            }
            
            // Cleanup components
            if (serverValidator != null) serverValidator.cleanup();
            if (clientMonitor != null) clientMonitor.cleanup();
            if (behavioralEngine != null) behavioralEngine.cleanup();
            if (statisticalDetector != null) statisticalDetector.cleanup();
            if (hardwareValidator != null) hardwareValidator.cleanup();
            if (realtimeDetector != null) realtimeDetector.cleanup();
            if (banManager != null) banManager.cleanup();
            if (securityObfuscator != null) securityObfuscator.cleanup();
            if (antiCheatLogger != null) antiCheatLogger.cleanup();
            
            // Clear player profiles
            playerProfiles.clear();
            
            initialized = false;
            
            logManager.info("AntiCheatManager", "Anti-Cheat System cleanup complete");
            
        } catch (Exception e) {
            logManager.error("AntiCheatManager", "Error during cleanup", e);
        }
    }
    
    // Getters
    public boolean isInitialized() { return initialized; }
    public boolean isEnabled() { return enabled; }
    public AntiCheatConfiguration getConfiguration() { return config; }
    public ServerSideValidator getServerValidator() { return serverValidator; }
    public ClientSideMonitor getClientMonitor() { return clientMonitor; }
    public BehavioralAnalysisEngine getBehavioralEngine() { return behavioralEngine; }
    public StatisticalAnomalyDetector getStatisticalDetector() { return statisticalDetector; }
    public HardwareValidator getHardwareValidator() { return hardwareValidator; }
    public RealTimeCheatDetector getRealtimeDetector() { return realtimeDetector; }
    public BanManager getBanManager() { return banManager; }
    public AntiCheatLogger getAntiCheatLogger() { return antiCheatLogger; }
    public Map<Integer, PlayerAntiCheatProfile> getPlayerProfiles() { return new ConcurrentHashMap<>(playerProfiles); }
}