package fps.anticheat.punishment;

/**
 * Represents a ban appeal submitted by a player.
 */
public class BanAppeal {
    
    private final String playerId;
    private final PlayerBan ban;
    private final String appealReason;
    private final String contactInfo;
    private final long submissionTime;
    
    private AppealStatus status;
    private String adminNotes;
    private long processedTime;
    
    public BanAppeal(String playerId, PlayerBan ban, String appealReason, String contactInfo) {
        this.playerId = playerId;
        this.ban = ban;
        this.appealReason = appealReason;
        this.contactInfo = contactInfo;
        this.submissionTime = System.currentTimeMillis();
        this.status = AppealStatus.PENDING;
    }
    
    /**
     * Process the appeal with admin decision
     */
    public void process(boolean approved, String adminNotes) {
        this.status = approved ? AppealStatus.APPROVED : AppealStatus.DENIED;
        this.adminNotes = adminNotes;
        this.processedTime = System.currentTimeMillis();
    }
    
    /**
     * Check if appeal is still pending
     */
    public boolean isPending() {
        return status == AppealStatus.PENDING;
    }
    
    /**
     * Check if appeal was approved
     */
    public boolean isApproved() {
        return status == AppealStatus.APPROVED;
    }
    
    /**
     * Get processing time in milliseconds
     */
    public long getProcessingTime() {
        if (status == AppealStatus.PENDING) {
            return System.currentTimeMillis() - submissionTime;
        }
        return processedTime - submissionTime;
    }
    
    // Getters
    public String getPlayerId() { return playerId; }
    public PlayerBan getBan() { return ban; }
    public String getAppealReason() { return appealReason; }
    public String getContactInfo() { return contactInfo; }
    public long getSubmissionTime() { return submissionTime; }
    public AppealStatus getStatus() { return status; }
    public String getAdminNotes() { return adminNotes; }
    public long getProcessedTime() { return processedTime; }
    
    @Override
    public String toString() {
        return String.format("BanAppeal{playerId='%s', status=%s, submissionTime=%d, processingTime=%d}", 
                           playerId, status, submissionTime, getProcessingTime());
    }
}