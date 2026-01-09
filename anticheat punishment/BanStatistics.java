package fps.anticheat.punishment;

/**
 * Statistics about bans and punishments in the system.
 */
public class BanStatistics {
    
    private final int activeBans;
    private final int temporaryBans;
    private final int permanentBans;
    private final int hardwareBans;
    private final int totalPunishments;
    private final int activeAppeals;
    private final long timestamp;
    
    public BanStatistics(int activeBans, int temporaryBans, int permanentBans, 
                        int hardwareBans, int totalPunishments, int activeAppeals) {
        this.activeBans = activeBans;
        this.temporaryBans = temporaryBans;
        this.permanentBans = permanentBans;
        this.hardwareBans = hardwareBans;
        this.totalPunishments = totalPunishments;
        this.activeAppeals = activeAppeals;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Get ban rate (percentage of permanent/hardware bans vs total active bans)
     */
    public float getBanRate() {
        if (activeBans == 0) return 0.0f;
        return (float) (permanentBans + hardwareBans) / activeBans * 100.0f;
    }
    
    /**
     * Get temporary ban rate
     */
    public float getTemporaryBanRate() {
        if (activeBans == 0) return 0.0f;
        return (float) temporaryBans / activeBans * 100.0f;
    }
    
    /**
     * Get appeal rate (percentage of active appeals vs active bans)
     */
    public float getAppealRate() {
        if (activeBans == 0) return 0.0f;
        return (float) activeAppeals / activeBans * 100.0f;
    }
    
    // Getters
    public int getActiveBans() { return activeBans; }
    public int getTemporaryBans() { return temporaryBans; }
    public int getPermanentBans() { return permanentBans; }
    public int getHardwareBans() { return hardwareBans; }
    public int getTotalPunishments() { return totalPunishments; }
    public int getActiveAppeals() { return activeAppeals; }
    public long getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("BanStatistics{activeBans=%d, temp=%d, perm=%d, hw=%d, total=%d, appeals=%d, banRate=%.1f%%}", 
                           activeBans, temporaryBans, permanentBans, hardwareBans, 
                           totalPunishments, activeAppeals, getBanRate());
    }
}