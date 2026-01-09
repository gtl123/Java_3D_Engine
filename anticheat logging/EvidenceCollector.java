package fps.anticheat.logging;

import fps.anticheat.detection.ViolationData;
import fps.core.player.Player;
import fps.core.game.GameState;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/**
 * Collects and manages evidence for anti-cheat violations.
 */
public class EvidenceCollector {
    private final AntiCheatLoggingManager loggingManager;
    private final Map<String, EvidencePackage> activeEvidenceCollection;
    private final int maxEvidenceSize;
    private final int evidenceRetentionDays;
    
    public EvidenceCollector(AntiCheatLoggingManager loggingManager) {
        this.loggingManager = loggingManager;
        this.activeEvidenceCollection = new ConcurrentHashMap<>();
        this.maxEvidenceSize = 50 * 1024 * 1024; // 50MB per evidence package
        this.evidenceRetentionDays = 30;
    }
    
    /**
     * Start collecting evidence for a player
     */
    public String startEvidenceCollection(String playerId, String reason, ViolationData initialViolation) {
        String evidenceId = generateEvidenceId();
        
        EvidencePackage evidence = new EvidencePackage(evidenceId, playerId, reason);
        evidence.addViolation(initialViolation);
        
        activeEvidenceCollection.put(evidenceId, evidence);
        
        // Log evidence collection start
        LogEntry entry = new LogEntry.Builder()
                .level(LogLevel.INFO)
                .category("EVIDENCE_COLLECTION")
                .message("Evidence collection started")
                .playerId(playerId)
                .metadata("evidenceId", evidenceId)
                .metadata("reason", reason)
                .build();
        
        loggingManager.logEntry(entry);
        
        return evidenceId;
    }
    
    /**
     * Add player state snapshot to evidence
     */
    public void addPlayerStateSnapshot(String evidenceId, Player player, GameState gameState) {
        EvidencePackage evidence = activeEvidenceCollection.get(evidenceId);
        if (evidence != null) {
            PlayerStateSnapshot snapshot = new PlayerStateSnapshot(
                    LocalDateTime.now(),
                    player.getPosition(),
                    player.getRotation(),
                    player.getVelocity(),
                    player.getHealth(),
                    player.getWeapon(),
                    player.getInputState(),
                    gameState.getCurrentMap(),
                    gameState.getGameMode()
            );
            
            evidence.addPlayerSnapshot(snapshot);
        }
    }
    
    /**
     * Add network packet data to evidence
     */
    public void addNetworkPacketData(String evidenceId, String packetType, byte[] packetData, 
                                   LocalDateTime timestamp) {
        EvidencePackage evidence = activeEvidenceCollection.get(evidenceId);
        if (evidence != null) {
            NetworkPacketEvidence packet = new NetworkPacketEvidence(
                    timestamp, packetType, packetData
            );
            evidence.addNetworkPacket(packet);
        }
    }
    
    /**
     * Add system information to evidence
     */
    public void addSystemInformation(String evidenceId, Map<String, Object> systemInfo) {
        EvidencePackage evidence = activeEvidenceCollection.get(evidenceId);
        if (evidence != null) {
            evidence.addSystemInfo(systemInfo);
        }
    }
    
    /**
     * Add screenshot evidence
     */
    public void addScreenshotEvidence(String evidenceId, byte[] screenshotData, String description) {
        EvidencePackage evidence = activeEvidenceCollection.get(evidenceId);
        if (evidence != null) {
            ScreenshotEvidence screenshot = new ScreenshotEvidence(
                    LocalDateTime.now(), screenshotData, description
            );
            evidence.addScreenshot(screenshot);
        }
    }
    
    /**
     * Add additional violation to existing evidence
     */
    public void addViolationToEvidence(String evidenceId, ViolationData violation) {
        EvidencePackage evidence = activeEvidenceCollection.get(evidenceId);
        if (evidence != null) {
            evidence.addViolation(violation);
        }
    }
    
    /**
     * Finalize and store evidence package
     */
    public CompletableFuture<String> finalizeEvidence(String evidenceId, String conclusion) {
        return CompletableFuture.supplyAsync(() -> {
            EvidencePackage evidence = activeEvidenceCollection.remove(evidenceId);
            if (evidence == null) {
                return null;
            }
            
            evidence.finalize(conclusion);
            
            // Compress and store evidence
            try {
                byte[] compressedEvidence = compressEvidence(evidence);
                String storagePath = storeEvidence(evidenceId, compressedEvidence);
                
                // Log evidence finalization
                LogEntry entry = new LogEntry.Builder()
                        .level(LogLevel.INFO)
                        .category("EVIDENCE_FINALIZED")
                        .message("Evidence package finalized and stored")
                        .playerId(evidence.getPlayerId())
                        .metadata("evidenceId", evidenceId)
                        .metadata("storagePath", storagePath)
                        .metadata("evidenceSize", compressedEvidence.length)
                        .metadata("violationCount", evidence.getViolations().size())
                        .metadata("conclusion", conclusion)
                        .build();
                
                loggingManager.logEntry(entry);
                
                return storagePath;
            } catch (IOException e) {
                // Log error
                LogEntry errorEntry = new LogEntry.Builder()
                        .level(LogLevel.ERROR)
                        .category("EVIDENCE_ERROR")
                        .message("Failed to store evidence package")
                        .playerId(evidence.getPlayerId())
                        .metadata("evidenceId", evidenceId)
                        .metadata("error", e.getMessage())
                        .stackTrace(getStackTrace(e))
                        .build();
                
                loggingManager.logEntry(errorEntry);
                return null;
            }
        });
    }
    
    /**
     * Get active evidence collection for a player
     */
    public List<String> getActiveEvidenceForPlayer(String playerId) {
        return activeEvidenceCollection.values().stream()
                .filter(evidence -> evidence.getPlayerId().equals(playerId))
                .map(EvidencePackage::getEvidenceId)
                .toList();
    }
    
    /**
     * Cancel evidence collection
     */
    public void cancelEvidenceCollection(String evidenceId, String reason) {
        EvidencePackage evidence = activeEvidenceCollection.remove(evidenceId);
        if (evidence != null) {
            LogEntry entry = new LogEntry.Builder()
                    .level(LogLevel.WARNING)
                    .category("EVIDENCE_CANCELLED")
                    .message("Evidence collection cancelled")
                    .playerId(evidence.getPlayerId())
                    .metadata("evidenceId", evidenceId)
                    .metadata("reason", reason)
                    .build();
            
            loggingManager.logEntry(entry);
        }
    }
    
    private String generateEvidenceId() {
        return "EVD_" + System.currentTimeMillis() + "_" + 
               UUID.randomUUID().toString().substring(0, 8);
    }
    
    private byte[] compressEvidence(EvidencePackage evidence) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            // Serialize evidence package (simplified - would use proper serialization)
            String evidenceJson = evidence.toJson();
            gzipOut.write(evidenceJson.getBytes());
        }
        return baos.toByteArray();
    }
    
    private String storeEvidence(String evidenceId, byte[] compressedData) {
        // Store evidence to secure location (implementation would depend on storage system)
        String storagePath = "evidence/" + evidenceId + ".gz";
        // Implementation would write to secure storage
        return storagePath;
    }
    
    private String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * Evidence package containing all collected evidence
     */
    public static class EvidencePackage {
        private final String evidenceId;
        private final String playerId;
        private final String reason;
        private final LocalDateTime startTime;
        private final List<ViolationData> violations;
        private final List<PlayerStateSnapshot> playerSnapshots;
        private final List<NetworkPacketEvidence> networkPackets;
        private final List<ScreenshotEvidence> screenshots;
        private final Map<String, Object> systemInfo;
        private LocalDateTime endTime;
        private String conclusion;
        private boolean finalized;
        
        public EvidencePackage(String evidenceId, String playerId, String reason) {
            this.evidenceId = evidenceId;
            this.playerId = playerId;
            this.reason = reason;
            this.startTime = LocalDateTime.now();
            this.violations = new ArrayList<>();
            this.playerSnapshots = new ArrayList<>();
            this.networkPackets = new ArrayList<>();
            this.screenshots = new ArrayList<>();
            this.systemInfo = new HashMap<>();
            this.finalized = false;
        }
        
        public void addViolation(ViolationData violation) {
            if (!finalized) violations.add(violation);
        }
        
        public void addPlayerSnapshot(PlayerStateSnapshot snapshot) {
            if (!finalized) playerSnapshots.add(snapshot);
        }
        
        public void addNetworkPacket(NetworkPacketEvidence packet) {
            if (!finalized) networkPackets.add(packet);
        }
        
        public void addScreenshot(ScreenshotEvidence screenshot) {
            if (!finalized) screenshots.add(screenshot);
        }
        
        public void addSystemInfo(Map<String, Object> info) {
            if (!finalized) systemInfo.putAll(info);
        }
        
        public void finalize(String conclusion) {
            this.endTime = LocalDateTime.now();
            this.conclusion = conclusion;
            this.finalized = true;
        }
        
        public String toJson() {
            // Simplified JSON serialization - would use proper JSON library
            return String.format(
                    "{\"evidenceId\":\"%s\",\"playerId\":\"%s\",\"reason\":\"%s\"," +
                    "\"startTime\":\"%s\",\"endTime\":\"%s\",\"conclusion\":\"%s\"," +
                    "\"violationCount\":%d,\"snapshotCount\":%d,\"packetCount\":%d," +
                    "\"screenshotCount\":%d}",
                    evidenceId, playerId, reason, startTime, endTime, conclusion,
                    violations.size(), playerSnapshots.size(), networkPackets.size(),
                    screenshots.size()
            );
        }
        
        // Getters
        public String getEvidenceId() { return evidenceId; }
        public String getPlayerId() { return playerId; }
        public String getReason() { return reason; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public String getConclusion() { return conclusion; }
        public List<ViolationData> getViolations() { return new ArrayList<>(violations); }
        public List<PlayerStateSnapshot> getPlayerSnapshots() { return new ArrayList<>(playerSnapshots); }
        public List<NetworkPacketEvidence> getNetworkPackets() { return new ArrayList<>(networkPackets); }
        public List<ScreenshotEvidence> getScreenshots() { return new ArrayList<>(screenshots); }
        public Map<String, Object> getSystemInfo() { return new HashMap<>(systemInfo); }
        public boolean isFinalized() { return finalized; }
    }
    
    /**
     * Player state snapshot for evidence
     */
    public static class PlayerStateSnapshot {
        private final LocalDateTime timestamp;
        private final Object position;
        private final Object rotation;
        private final Object velocity;
        private final float health;
        private final String weapon;
        private final Object inputState;
        private final String mapName;
        private final String gameMode;
        
        public PlayerStateSnapshot(LocalDateTime timestamp, Object position, Object rotation,
                                 Object velocity, float health, String weapon, Object inputState,
                                 String mapName, String gameMode) {
            this.timestamp = timestamp;
            this.position = position;
            this.rotation = rotation;
            this.velocity = velocity;
            this.health = health;
            this.weapon = weapon;
            this.inputState = inputState;
            this.mapName = mapName;
            this.gameMode = gameMode;
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public Object getPosition() { return position; }
        public Object getRotation() { return rotation; }
        public Object getVelocity() { return velocity; }
        public float getHealth() { return health; }
        public String getWeapon() { return weapon; }
        public Object getInputState() { return inputState; }
        public String getMapName() { return mapName; }
        public String getGameMode() { return gameMode; }
    }
    
    /**
     * Network packet evidence
     */
    public static class NetworkPacketEvidence {
        private final LocalDateTime timestamp;
        private final String packetType;
        private final byte[] packetData;
        
        public NetworkPacketEvidence(LocalDateTime timestamp, String packetType, byte[] packetData) {
            this.timestamp = timestamp;
            this.packetType = packetType;
            this.packetData = packetData.clone();
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getPacketType() { return packetType; }
        public byte[] getPacketData() { return packetData.clone(); }
    }
    
    /**
     * Screenshot evidence
     */
    public static class ScreenshotEvidence {
        private final LocalDateTime timestamp;
        private final byte[] imageData;
        private final String description;
        
        public ScreenshotEvidence(LocalDateTime timestamp, byte[] imageData, String description) {
            this.timestamp = timestamp;
            this.imageData = imageData.clone();
            this.description = description;
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public byte[] getImageData() { return imageData.clone(); }
        public String getDescription() { return description; }
    }
}