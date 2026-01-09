package fps.anticheat.punishment;

/**
 * Event listener interface for punishment system events.
 */
public interface PunishmentEventListener {
    
    /**
     * Called when a punishment is issued to a player
     */
    void onPunishmentIssued(String playerId, PunishmentDecision punishmentDecision, 
                           EscalationDecision escalationDecision);
    
    /**
     * Called when a ban appeal is processed
     */
    void onAppealProcessed(String playerId, boolean approved, String adminNotes);
    
    /**
     * Called when a ban expires
     */
    default void onBanExpired(String playerId, PlayerBan expiredBan) {
        // Default implementation - can be overridden
    }
    
    /**
     * Called when escalation level changes for a player
     */
    default void onEscalationLevelChanged(String playerId, EscalationLevel oldLevel, 
                                        EscalationLevel newLevel) {
        // Default implementation - can be overridden
    }
    
    /**
     * Called when suspicious patterns are detected
     */
    default void onSuspiciousPatternsDetected(String playerId, PlayerEscalationProfile profile) {
        // Default implementation - can be overridden
    }
    
    /**
     * Called when hardware ban evasion is detected
     */
    default void onBanEvasionDetected(String playerId, String originalBannedPlayerId, 
                                    String hardwareFingerprint) {
        // Default implementation - can be overridden
    }
}