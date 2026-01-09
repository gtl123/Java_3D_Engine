package fps.anticheat.punishment;

import fps.anticheat.*;
import fps.anticheat.hardware.HardwareFingerprint;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player bans and punishments with graduated enforcement and appeals system.
 * Handles temporary bans, permanent bans, hardware bans, and ban evasion detection.
 */
public class BanManager {
    
    private final AntiCheatConfiguration config;
    private final Map<String, PlayerBan> activeBans;
    private final Map<String, List<PunishmentRecord>> punishmentHistory;
    private final Map<String, Set<String>> hardwareBans; // Hardware fingerprint -> banned player IDs
    private final Map<String, BanAppeal> activeAppeals;
    
    // Ban escalation thresholds
    private static final int WARNING_THRESHOLD = 3;
    private static final int TEMP_BAN_THRESHOLD = 5;
    private static final int PERMANENT_BAN_THRESHOLD = 10;
    
    // Ban durations (in milliseconds)
    private static final long FIRST_TEMP_BAN_DURATION = 3600000; // 1 hour
    private static final long SECOND_TEMP_BAN_DURATION = 86400000; // 24 hours
    private static final long THIRD_TEMP_BAN_DURATION = 604800000; // 7 days
    
    public BanManager(AntiCheatConfiguration config) {
        this.config = config;
        this.activeBans = new ConcurrentHashMap<>();
        this.punishmentHistory = new ConcurrentHashMap<>();
        this.hardwareBans = new ConcurrentHashMap<>();
        this.activeAppeals = new ConcurrentHashMap<>();
    }
    
    /**
     * Process violations and determine appropriate punishment
     */
    public PunishmentDecision processViolations(String playerId, List<ValidationResult> violations, 
                                              HardwareFingerprint hardwareFingerprint) {
        // Check if player is already banned
        PlayerBan existingBan = getActiveBan(playerId);
        if (existingBan != null && !existingBan.isExpired()) {
            return new PunishmentDecision(PunishmentType.ALREADY_BANNED, existingBan, 
                "Player is already banned");
        }
        
        // Check for hardware ban evasion
        if (hardwareFingerprint != null) {
            String hardwareBanReason = checkHardwareBanEvasion(playerId, hardwareFingerprint);
            if (hardwareBanReason != null) {
                return issueHardwareBan(playerId, hardwareFingerprint, hardwareBanReason);
            }
        }
        
        // Calculate violation severity
        float totalSeverity = calculateTotalSeverity(violations);
        int violationCount = violations.size();
        
        // Get punishment history
        List<PunishmentRecord> history = getPunishmentHistory(playerId);
        
        // Determine punishment based on severity and history
        PunishmentType punishmentType = determinePunishmentType(totalSeverity, violationCount, history);
        
        // Issue punishment
        return issuePunishment(playerId, punishmentType, violations, hardwareFingerprint);
    }
    
    /**
     * Calculate total severity of violations
     */
    private float calculateTotalSeverity(List<ValidationResult> violations) {
        float totalSeverity = 0.0f;
        float maxSeverity = 0.0f;
        
        for (ValidationResult violation : violations) {
            float severity = violation.getSeverity();
            totalSeverity += severity;
            maxSeverity = Math.max(maxSeverity, severity);
        }
        
        // Combine total and max severity (weighted toward max for critical violations)
        return (maxSeverity * 0.7f) + (totalSeverity / violations.size() * 0.3f);
    }
    
    /**
     * Determine appropriate punishment type
     */
    private PunishmentType determinePunishmentType(float severity, int violationCount, List<PunishmentRecord> history) {
        // Count previous punishments
        int warningCount = 0;
        int tempBanCount = 0;
        int permanentBanCount = 0;
        
        for (PunishmentRecord record : history) {
            switch (record.getPunishmentType()) {
                case WARNING:
                    warningCount++;
                    break;
                case TEMPORARY_BAN:
                    tempBanCount++;
                    break;
                case PERMANENT_BAN:
                case HARDWARE_BAN:
                    permanentBanCount++;
                    break;
            }
        }
        
        // Immediate permanent ban for critical violations
        if (severity >= 0.9f || violationCount >= 5) {
            return PunishmentType.PERMANENT_BAN;
        }
        
        // Escalation based on history
        if (permanentBanCount > 0) {
            return PunishmentType.HARDWARE_BAN; // Hardware ban for ban evasion
        } else if (tempBanCount >= 3) {
            return PunishmentType.PERMANENT_BAN;
        } else if (warningCount >= WARNING_THRESHOLD || tempBanCount > 0) {
            return PunishmentType.TEMPORARY_BAN;
        } else if (severity >= 0.6f) {
            return PunishmentType.TEMPORARY_BAN;
        } else {
            return PunishmentType.WARNING;
        }
    }
    
    /**
     * Issue punishment to player
     */
    private PunishmentDecision issuePunishment(String playerId, PunishmentType punishmentType, 
                                             List<ValidationResult> violations, HardwareFingerprint hardwareFingerprint) {
        String reason = buildPunishmentReason(violations);
        long duration = calculatePunishmentDuration(playerId, punishmentType);
        
        PlayerBan ban = null;
        
        switch (punishmentType) {
            case WARNING:
                // Issue warning (no ban)
                recordPunishment(playerId, punishmentType, reason, 0, violations);
                break;
                
            case TEMPORARY_BAN:
                ban = new PlayerBan(playerId, BanType.TEMPORARY, reason, duration, 
                                  System.currentTimeMillis(), null);
                activeBans.put(playerId, ban);
                recordPunishment(playerId, punishmentType, reason, duration, violations);
                break;
                
            case PERMANENT_BAN:
                ban = new PlayerBan(playerId, BanType.PERMANENT, reason, -1, 
                                  System.currentTimeMillis(), null);
                activeBans.put(playerId, ban);
                recordPunishment(playerId, punishmentType, reason, -1, violations);
                break;
                
            case HARDWARE_BAN:
                return issueHardwareBan(playerId, hardwareFingerprint, reason);
        }
        
        return new PunishmentDecision(punishmentType, ban, reason);
    }
    
    /**
     * Issue hardware ban
     */
    private PunishmentDecision issueHardwareBan(String playerId, HardwareFingerprint hardwareFingerprint, String reason) {
        PlayerBan ban = new PlayerBan(playerId, BanType.HARDWARE, reason, -1, 
                                    System.currentTimeMillis(), hardwareFingerprint);
        
        activeBans.put(playerId, ban);
        
        // Add to hardware ban list
        if (hardwareFingerprint != null) {
            String hwId = hardwareFingerprint.getMasterFingerprint();
            hardwareBans.computeIfAbsent(hwId, k -> new HashSet<>()).add(playerId);
        }
        
        recordPunishment(playerId, PunishmentType.HARDWARE_BAN, reason, -1, Collections.emptyList());
        
        return new PunishmentDecision(PunishmentType.HARDWARE_BAN, ban, reason);
    }
    
    /**
     * Calculate punishment duration based on history
     */
    private long calculatePunishmentDuration(String playerId, PunishmentType punishmentType) {
        if (punishmentType != PunishmentType.TEMPORARY_BAN) {
            return -1; // Permanent or warning
        }
        
        List<PunishmentRecord> history = getPunishmentHistory(playerId);
        int tempBanCount = (int) history.stream()
                .filter(record -> record.getPunishmentType() == PunishmentType.TEMPORARY_BAN)
                .count();
        
        switch (tempBanCount) {
            case 0:
                return FIRST_TEMP_BAN_DURATION;
            case 1:
                return SECOND_TEMP_BAN_DURATION;
            default:
                return THIRD_TEMP_BAN_DURATION;
        }
    }
    
    /**
     * Build punishment reason from violations
     */
    private String buildPunishmentReason(List<ValidationResult> violations) {
        if (violations.isEmpty()) {
            return "Suspicious activity detected";
        }
        
        Map<ViolationType, Integer> violationCounts = new HashMap<>();
        for (ValidationResult violation : violations) {
            violationCounts.merge(violation.getViolationType(), 1, Integer::sum);
        }
        
        StringBuilder reason = new StringBuilder("Detected violations: ");
        violationCounts.entrySet().stream()
                .sorted(Map.Entry.<ViolationType, Integer>comparingByValue().reversed())
                .forEach(entry -> reason.append(entry.getKey().name())
                        .append(" (").append(entry.getValue()).append("), "));
        
        // Remove trailing comma and space
        if (reason.length() > 2) {
            reason.setLength(reason.length() - 2);
        }
        
        return reason.toString();
    }
    
    /**
     * Record punishment in history
     */
    private void recordPunishment(String playerId, PunishmentType punishmentType, String reason, 
                                long duration, List<ValidationResult> violations) {
        PunishmentRecord record = new PunishmentRecord(playerId, punishmentType, reason, 
                                                     System.currentTimeMillis(), duration, violations);
        
        punishmentHistory.computeIfAbsent(playerId, k -> new ArrayList<>()).add(record);
    }
    
    /**
     * Check for hardware ban evasion
     */
    private String checkHardwareBanEvasion(String playerId, HardwareFingerprint hardwareFingerprint) {
        String hwId = hardwareFingerprint.getMasterFingerprint();
        Set<String> bannedPlayers = hardwareBans.get(hwId);
        
        if (bannedPlayers != null && !bannedPlayers.isEmpty()) {
            // Check if any of the banned players are different from current player
            for (String bannedPlayerId : bannedPlayers) {
                if (!bannedPlayerId.equals(playerId)) {
                    PlayerBan existingBan = activeBans.get(bannedPlayerId);
                    if (existingBan != null && existingBan.getBanType() == BanType.HARDWARE) {
                        return "Hardware ban evasion detected (previously banned player: " + bannedPlayerId + ")";
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Get active ban for player
     */
    public PlayerBan getActiveBan(String playerId) {
        PlayerBan ban = activeBans.get(playerId);
        if (ban != null && ban.isExpired()) {
            activeBans.remove(playerId);
            return null;
        }
        return ban;
    }
    
    /**
     * Check if player is banned
     */
    public boolean isPlayerBanned(String playerId) {
        return getActiveBan(playerId) != null;
    }
    
    /**
     * Check if hardware is banned
     */
    public boolean isHardwareBanned(HardwareFingerprint hardwareFingerprint) {
        if (hardwareFingerprint == null) return false;
        
        String hwId = hardwareFingerprint.getMasterFingerprint();
        Set<String> bannedPlayers = hardwareBans.get(hwId);
        
        if (bannedPlayers != null) {
            for (String playerId : bannedPlayers) {
                PlayerBan ban = activeBans.get(playerId);
                if (ban != null && ban.getBanType() == BanType.HARDWARE && !ban.isExpired()) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Get punishment history for player
     */
    public List<PunishmentRecord> getPunishmentHistory(String playerId) {
        return punishmentHistory.getOrDefault(playerId, new ArrayList<>());
    }
    
    /**
     * Submit ban appeal
     */
    public BanAppeal submitAppeal(String playerId, String appealReason, String contactInfo) {
        PlayerBan ban = getActiveBan(playerId);
        if (ban == null) {
            throw new IllegalStateException("No active ban found for player: " + playerId);
        }
        
        if (activeAppeals.containsKey(playerId)) {
            throw new IllegalStateException("Appeal already submitted for player: " + playerId);
        }
        
        BanAppeal appeal = new BanAppeal(playerId, ban, appealReason, contactInfo);
        activeAppeals.put(playerId, appeal);
        
        return appeal;
    }
    
    /**
     * Process ban appeal
     */
    public void processAppeal(String playerId, boolean approved, String adminNotes) {
        BanAppeal appeal = activeAppeals.get(playerId);
        if (appeal == null) {
            throw new IllegalStateException("No appeal found for player: " + playerId);
        }
        
        appeal.process(approved, adminNotes);
        
        if (approved) {
            // Remove ban
            PlayerBan ban = activeBans.remove(playerId);
            if (ban != null && ban.getBanType() == BanType.HARDWARE) {
                // Remove from hardware ban list
                String hwId = ban.getHardwareFingerprint().getMasterFingerprint();
                Set<String> bannedPlayers = hardwareBans.get(hwId);
                if (bannedPlayers != null) {
                    bannedPlayers.remove(playerId);
                    if (bannedPlayers.isEmpty()) {
                        hardwareBans.remove(hwId);
                    }
                }
            }
        }
        
        activeAppeals.remove(playerId);
    }
    
    /**
     * Get ban statistics
     */
    public BanStatistics getBanStatistics() {
        int activeBanCount = 0;
        int temporaryBanCount = 0;
        int permanentBanCount = 0;
        int hardwareBanCount = 0;
        
        for (PlayerBan ban : activeBans.values()) {
            if (!ban.isExpired()) {
                activeBanCount++;
                switch (ban.getBanType()) {
                    case TEMPORARY:
                        temporaryBanCount++;
                        break;
                    case PERMANENT:
                        permanentBanCount++;
                        break;
                    case HARDWARE:
                        hardwareBanCount++;
                        break;
                }
            }
        }
        
        int totalPunishments = punishmentHistory.values().stream()
                .mapToInt(List::size)
                .sum();
        
        int activeAppealsCount = activeAppeals.size();
        
        return new BanStatistics(activeBanCount, temporaryBanCount, permanentBanCount, 
                               hardwareBanCount, totalPunishments, activeAppealsCount);
    }
    
    /**
     * Clean up expired bans and old records
     */
    public void cleanup() {
        // Remove expired bans
        activeBans.entrySet().removeIf(entry -> entry.getValue().isExpired());
        
        // Clean up old punishment history (keep last 100 records per player)
        for (List<PunishmentRecord> records : punishmentHistory.values()) {
            if (records.size() > 100) {
                records.sort(Comparator.comparing(PunishmentRecord::getTimestamp).reversed());
                records.subList(100, records.size()).clear();
            }
        }
        
        // Clean up hardware bans for expired bans
        hardwareBans.entrySet().removeIf(entry -> {
            Set<String> players = entry.getValue();
            players.removeIf(playerId -> {
                PlayerBan ban = activeBans.get(playerId);
                return ban == null || ban.isExpired() || ban.getBanType() != BanType.HARDWARE;
            });
            return players.isEmpty();
        });
    }
}