package fps.anticheat.punishment;

import fps.anticheat.*;
import fps.anticheat.hardware.HardwareFingerprint;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main coordinator for the punishment system, integrating ban management and escalation.
 */
public class PunishmentCoordinator {
    
    private final AntiCheatConfiguration config;
    private final BanManager banManager;
    private final PunishmentEscalationManager escalationManager;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    
    // Event listeners
    private PunishmentEventListener eventListener;
    
    public PunishmentCoordinator(AntiCheatConfiguration config) {
        this.config = config;
        this.banManager = new BanManager(config);
        this.escalationManager = new PunishmentEscalationManager(config);
        this.executorService = Executors.newFixedThreadPool(4);
        this.scheduledExecutor = Executors.newScheduledThreadPool(2);
        
        // Schedule periodic cleanup
        scheduledExecutor.scheduleAtFixedRate(this::performCleanup, 1, 1, TimeUnit.HOURS);
    }
    
    /**
     * Process violations and determine punishment asynchronously
     */
    public CompletableFuture<PunishmentResult> processViolationsAsync(String playerId, 
                                                                     List<ValidationResult> violations, 
                                                                     HardwareFingerprint hardwareFingerprint) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return processViolations(playerId, violations, hardwareFingerprint);
            } catch (Exception e) {
                return new PunishmentResult(false, null, null, 
                    "Error processing violations: " + e.getMessage());
            }
        }, executorService);
    }
    
    /**
     * Process violations and determine punishment synchronously
     */
    public PunishmentResult processViolations(String playerId, List<ValidationResult> violations, 
                                            HardwareFingerprint hardwareFingerprint) {
        try {
            // Check if player is already banned
            PlayerBan existingBan = banManager.getActiveBan(playerId);
            if (existingBan != null && !existingBan.isExpired()) {
                return new PunishmentResult(false, null, existingBan, 
                    "Player is already banned: " + existingBan.getReason());
            }
            
            // Check for hardware ban
            if (hardwareFingerprint != null && banManager.isHardwareBanned(hardwareFingerprint)) {
                return new PunishmentResult(false, null, null, 
                    "Hardware is banned - connection rejected");
            }
            
            // Get punishment history
            List<PunishmentRecord> punishmentHistory = banManager.getPunishmentHistory(playerId);
            
            // Evaluate escalation
            EscalationDecision escalationDecision = escalationManager.evaluateEscalation(
                playerId, violations, punishmentHistory);
            
            // Process punishment with escalation
            PunishmentDecision punishmentDecision = banManager.processViolations(
                playerId, violations, hardwareFingerprint);
            
            // Apply escalation multipliers
            PunishmentDecision escalatedDecision = applyEscalation(punishmentDecision, escalationDecision);
            
            // Fire event
            if (eventListener != null) {
                eventListener.onPunishmentIssued(playerId, escalatedDecision, escalationDecision);
            }
            
            return new PunishmentResult(true, escalatedDecision, escalatedDecision.getBan(), 
                escalatedDecision.getPunishmentMessage());
            
        } catch (Exception e) {
            return new PunishmentResult(false, null, null, 
                "Error processing punishment: " + e.getMessage());
        }
    }
    
    /**
     * Apply escalation to punishment decision
     */
    private PunishmentDecision applyEscalation(PunishmentDecision originalDecision, 
                                             EscalationDecision escalationDecision) {
        // If escalation suggests hardware ban, override decision
        if (escalationDecision.suggestsHardwareBan() && 
            originalDecision.getPunishmentType() != PunishmentType.HARDWARE_BAN) {
            
            // Create escalated punishment decision (this would need to be implemented in BanManager)
            return new PunishmentDecision(PunishmentType.HARDWARE_BAN, null, 
                "Escalated to hardware ban due to repeat violations and risk factors");
        }
        
        // For other cases, the original decision stands but with escalation context
        return originalDecision;
    }
    
    /**
     * Check if player can connect (not banned)
     */
    public ConnectionCheckResult checkPlayerConnection(String playerId, HardwareFingerprint hardwareFingerprint) {
        try {
            // Check player ban
            PlayerBan playerBan = banManager.getActiveBan(playerId);
            if (playerBan != null && !playerBan.isExpired()) {
                return new ConnectionCheckResult(false, playerBan.getReason(), playerBan);
            }
            
            // Check hardware ban
            if (hardwareFingerprint != null && banManager.isHardwareBanned(hardwareFingerprint)) {
                return new ConnectionCheckResult(false, "Hardware banned", null);
            }
            
            return new ConnectionCheckResult(true, "Connection allowed", null);
            
        } catch (Exception e) {
            // On error, deny connection for safety
            return new ConnectionCheckResult(false, "Connection check failed", null);
        }
    }
    
    /**
     * Submit ban appeal
     */
    public CompletableFuture<BanAppeal> submitAppealAsync(String playerId, String appealReason, String contactInfo) {
        return CompletableFuture.supplyAsync(() -> {
            return banManager.submitAppeal(playerId, appealReason, contactInfo);
        }, executorService);
    }
    
    /**
     * Process ban appeal (admin function)
     */
    public CompletableFuture<Void> processAppealAsync(String playerId, boolean approved, String adminNotes) {
        return CompletableFuture.runAsync(() -> {
            banManager.processAppeal(playerId, approved, adminNotes);
            
            if (eventListener != null) {
                eventListener.onAppealProcessed(playerId, approved, adminNotes);
            }
        }, executorService);
    }
    
    /**
     * Get comprehensive punishment statistics
     */
    public PunishmentSystemStatistics getSystemStatistics() {
        BanStatistics banStats = banManager.getBanStatistics();
        EscalationStatistics escalationStats = escalationManager.getEscalationStatistics();
        
        return new PunishmentSystemStatistics(banStats, escalationStats);
    }
    
    /**
     * Get player punishment summary
     */
    public PlayerPunishmentSummary getPlayerSummary(String playerId) {
        PlayerBan activeBan = banManager.getActiveBan(playerId);
        List<PunishmentRecord> history = banManager.getPunishmentHistory(playerId);
        PlayerEscalationProfile escalationProfile = escalationManager.getEscalationProfile(playerId);
        
        return new PlayerPunishmentSummary(playerId, activeBan, history, escalationProfile);
    }
    
    /**
     * Perform periodic cleanup
     */
    private void performCleanup() {
        try {
            banManager.cleanup();
            escalationManager.cleanup();
        } catch (Exception e) {
            // Log error but don't throw
            System.err.println("Error during punishment system cleanup: " + e.getMessage());
        }
    }
    
    /**
     * Set event listener for punishment events
     */
    public void setEventListener(PunishmentEventListener listener) {
        this.eventListener = listener;
    }
    
    /**
     * Shutdown the punishment coordinator
     */
    public void shutdown() {
        try {
            executorService.shutdown();
            scheduledExecutor.shutdown();
            
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // Getters for direct access (if needed)
    public BanManager getBanManager() { return banManager; }
    public PunishmentEscalationManager getEscalationManager() { return escalationManager; }
}