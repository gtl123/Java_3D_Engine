package fps.anticheat.punishment;

/**
 * Status of a ban appeal.
 */
public enum AppealStatus {
    /**
     * Appeal is pending review
     */
    PENDING,
    
    /**
     * Appeal was approved and ban lifted
     */
    APPROVED,
    
    /**
     * Appeal was denied
     */
    DENIED
}