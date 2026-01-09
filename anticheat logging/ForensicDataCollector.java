package fps.anticheat.logging;

import fps.anticheat.detection.ViolationData;
import fps.core.player.Player;
import fps.core.game.GameState;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects detailed forensic data for investigation and analysis.
 */
public class ForensicDataCollector {
    private final AntiCheatLoggingManager loggingManager;
    private final Map<String, ForensicSession> activeSessions;
    private final AtomicLong sessionCounter;
    private final int maxForensicDataSize;
    
    public ForensicDataCollector(AntiCheatLoggingManager loggingManager) {
        this.loggingManager = loggingManager;
        this.activeSessions = new ConcurrentHashMap<>();
        this.sessionCounter = new AtomicLong(0);
        this.maxForensicDataSize = 100 * 1024 * 1024; // 100MB per session
    }
    
    /**
     * Start forensic data collection session
     */
    public String startForensicSession(String playerId, String trigger, ViolationData initialViolation) {
        String sessionId = generateSessionId();
        
        ForensicSession session = new ForensicSession(sessionId, playerId, trigger);
        if (initialViolation != null) {
            session.addViolation(initialViolation);
        }
        
        activeSessions.put(sessionId, session);
        
        // Log session start
        LogEntry entry = new LogEntry.Builder()
                .level(LogLevel.INFO)
                .category("FORENSIC_SESSION")
                .message("Forensic data collection started")
                .playerId(playerId)
                .metadata("forensicSessionId", sessionId)
                .metadata("trigger", trigger)
                .build();
        
        loggingManager.logEntry(entry);
        
        return sessionId;
    }
    
    /**
     * Collect player movement data
     */
    public void collectMovementData(String sessionId, Player player, LocalDateTime timestamp) {
        ForensicSession session = activeSessions.get(sessionId);
        if (session != null) {
            MovementData movement = new MovementData(
                    timestamp,
                    player.getPosition(),
                    player.getVelocity(),
                    player.getRotation(),
                    player.getInputState(),
                    player.isOnGround(),
                    player.getMovementSpeed()
            );
            session.addMovementData(movement);
        }
    }
    
    /**
     * Collect weapon usage data
     */
    public void collectWeaponData(String sessionId, Player player, String weaponAction, 
                                Map<String, Object> weaponState) {
        ForensicSession session = activeSessions.get(sessionId);
        if (session != null) {
            WeaponData weapon = new WeaponData(
                    LocalDateTime.now(),
                    player.getWeapon(),
                    weaponAction,
                    weaponState,
                    player.getAimDirection(),
                    player.getRecoilPattern()
            );
            session.addWeaponData(weapon);
        }
    }
    
    /**
     * Collect network timing data
     */
    public void collectNetworkTiming(String sessionId, String packetType, long sendTime, 
                                   long receiveTime, int packetSize) {
        ForensicSession session = activeSessions.get(sessionId);
        if (session != null) {
            NetworkTiming timing = new NetworkTiming(
                    LocalDateTime.now(),
                    packetType,
                    sendTime,
                    receiveTime,
                    receiveTime - sendTime,
                    packetSize
            );
            session.addNetworkTiming(timing);
        }
    }
    
    /**
     * Collect input pattern data
     */
    public void collectInputPattern(String sessionId, String inputType, Object inputData, 
                                  long inputTimestamp) {
        ForensicSession session = activeSessions.get(sessionId);
        if (session != null) {
            InputPattern input = new InputPattern(
                    LocalDateTime.now(),
                    inputType,
                    inputData,
                    inputTimestamp,
                    calculateInputTiming(sessionId, inputTimestamp)
            );
            session.addInputPattern(input);
        }
    }
    
    /**
     * Collect system performance data
     */
    public void collectPerformanceData(String sessionId, Map<String, Object> performanceMetrics) {
        ForensicSession session = activeSessions.get(sessionId);
        if (session != null) {
            PerformanceData performance = new PerformanceData(
                    LocalDateTime.now(),
                    performanceMetrics
            );
            session.addPerformanceData(performance);
        }
    }
    
    /**
     * Collect memory state data
     */
    public void collectMemoryState(String sessionId, Map<String, Object> memoryInfo, 
                                 List<String> suspiciousProcesses) {
        ForensicSession session = activeSessions.get(sessionId);
        if (session != null) {
            MemoryState memory = new MemoryState(
                    LocalDateTime.now(),
                    memoryInfo,
                    suspiciousProcesses
            );
            session.addMemoryState(memory);
        }
    }
    
    /**
     * Add statistical analysis to session
     */
    public void addStatisticalAnalysis(String sessionId, String analysisType, 
                                     Map<String, Object> analysisResults) {
        ForensicSession session = activeSessions.get(sessionId);
        if (session != null) {
            StatisticalAnalysis analysis = new StatisticalAnalysis(
                    LocalDateTime.now(),
                    analysisType,
                    analysisResults
            );
            session.addStatisticalAnalysis(analysis);
        }
    }
    
    /**
     * Finalize forensic session
     */
    public CompletableFuture<ForensicReport> finalizeSession(String sessionId, String conclusion, 
                                                           String investigatorNotes) {
        return CompletableFuture.supplyAsync(() -> {
            ForensicSession session = activeSessions.remove(sessionId);
            if (session == null) {
                return null;
            }
            
            session.finalize(conclusion, investigatorNotes);
            
            // Generate comprehensive report
            ForensicReport report = generateForensicReport(session);
            
            // Log session completion
            LogEntry entry = new LogEntry.Builder()
                    .level(LogLevel.INFO)
                    .category("FORENSIC_COMPLETE")
                    .message("Forensic data collection completed")
                    .playerId(session.getPlayerId())
                    .metadata("forensicSessionId", sessionId)
                    .metadata("conclusion", conclusion)
                    .metadata("dataPoints", session.getTotalDataPoints())
                    .build();
            
            loggingManager.logEntry(entry);
            
            return report;
        });
    }
    
    /**
     * Get active forensic sessions for a player
     */
    public List<String> getActiveSessionsForPlayer(String playerId) {
        return activeSessions.values().stream()
                .filter(session -> session.getPlayerId().equals(playerId))
                .map(ForensicSession::getSessionId)
                .toList();
    }
    
    private String generateSessionId() {
        return "FORENSIC_" + sessionCounter.incrementAndGet() + "_" + System.currentTimeMillis();
    }
    
    private long calculateInputTiming(String sessionId, long inputTimestamp) {
        ForensicSession session = activeSessions.get(sessionId);
        if (session != null && !session.getInputPatterns().isEmpty()) {
            InputPattern lastInput = session.getInputPatterns().get(session.getInputPatterns().size() - 1);
            return inputTimestamp - lastInput.getInputTimestamp();
        }
        return 0;
    }
    
    private ForensicReport generateForensicReport(ForensicSession session) {
        return new ForensicReport(
                session.getSessionId(),
                session.getPlayerId(),
                session.getTrigger(),
                session.getStartTime(),
                session.getEndTime(),
                session.getConclusion(),
                session.getInvestigatorNotes(),
                analyzeMovementPatterns(session.getMovementData()),
                analyzeWeaponUsage(session.getWeaponData()),
                analyzeNetworkBehavior(session.getNetworkTimings()),
                analyzeInputPatterns(session.getInputPatterns()),
                session.getPerformanceData(),
                session.getMemoryStates(),
                session.getStatisticalAnalyses()
        );
    }
    
    private Map<String, Object> analyzeMovementPatterns(List<MovementData> movementData) {
        Map<String, Object> analysis = new HashMap<>();
        
        if (!movementData.isEmpty()) {
            // Calculate movement statistics
            double avgSpeed = movementData.stream()
                    .mapToDouble(MovementData::getMovementSpeed)
                    .average().orElse(0.0);
            
            long totalMovements = movementData.size();
            
            analysis.put("averageSpeed", avgSpeed);
            analysis.put("totalMovements", totalMovements);
            analysis.put("movementConsistency", calculateMovementConsistency(movementData));
        }
        
        return analysis;
    }
    
    private Map<String, Object> analyzeWeaponUsage(List<WeaponData> weaponData) {
        Map<String, Object> analysis = new HashMap<>();
        
        if (!weaponData.isEmpty()) {
            Map<String, Long> actionCounts = weaponData.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            WeaponData::getWeaponAction,
                            java.util.stream.Collectors.counting()
                    ));
            
            analysis.put("actionCounts", actionCounts);
            analysis.put("totalActions", weaponData.size());
        }
        
        return analysis;
    }
    
    private Map<String, Object> analyzeNetworkBehavior(List<NetworkTiming> networkTimings) {
        Map<String, Object> analysis = new HashMap<>();
        
        if (!networkTimings.isEmpty()) {
            double avgLatency = networkTimings.stream()
                    .mapToLong(NetworkTiming::getLatency)
                    .average().orElse(0.0);
            
            analysis.put("averageLatency", avgLatency);
            analysis.put("totalPackets", networkTimings.size());
        }
        
        return analysis;
    }
    
    private Map<String, Object> analyzeInputPatterns(List<InputPattern> inputPatterns) {
        Map<String, Object> analysis = new HashMap<>();
        
        if (!inputPatterns.isEmpty()) {
            double avgTimingDelta = inputPatterns.stream()
                    .mapToLong(InputPattern::getTimingDelta)
                    .average().orElse(0.0);
            
            analysis.put("averageTimingDelta", avgTimingDelta);
            analysis.put("totalInputs", inputPatterns.size());
        }
        
        return analysis;
    }
    
    private double calculateMovementConsistency(List<MovementData> movementData) {
        if (movementData.size() < 2) return 1.0;
        
        double[] speeds = movementData.stream()
                .mapToDouble(MovementData::getMovementSpeed)
                .toArray();
        
        double mean = Arrays.stream(speeds).average().orElse(0.0);
        double variance = Arrays.stream(speeds)
                .map(speed -> Math.pow(speed - mean, 2))
                .average().orElse(0.0);
        
        return 1.0 / (1.0 + Math.sqrt(variance));
    }
    
    // Data classes for forensic collection
    public static class ForensicSession {
        private final String sessionId;
        private final String playerId;
        private final String trigger;
        private final LocalDateTime startTime;
        private final List<ViolationData> violations;
        private final List<MovementData> movementData;
        private final List<WeaponData> weaponData;
        private final List<NetworkTiming> networkTimings;
        private final List<InputPattern> inputPatterns;
        private final List<PerformanceData> performanceData;
        private final List<MemoryState> memoryStates;
        private final List<StatisticalAnalysis> statisticalAnalyses;
        private LocalDateTime endTime;
        private String conclusion;
        private String investigatorNotes;
        
        public ForensicSession(String sessionId, String playerId, String trigger) {
            this.sessionId = sessionId;
            this.playerId = playerId;
            this.trigger = trigger;
            this.startTime = LocalDateTime.now();
            this.violations = new ArrayList<>();
            this.movementData = new ArrayList<>();
            this.weaponData = new ArrayList<>();
            this.networkTimings = new ArrayList<>();
            this.inputPatterns = new ArrayList<>();
            this.performanceData = new ArrayList<>();
            this.memoryStates = new ArrayList<>();
            this.statisticalAnalyses = new ArrayList<>();
        }
        
        public void addViolation(ViolationData violation) { violations.add(violation); }
        public void addMovementData(MovementData movement) { movementData.add(movement); }
        public void addWeaponData(WeaponData weapon) { weaponData.add(weapon); }
        public void addNetworkTiming(NetworkTiming timing) { networkTimings.add(timing); }
        public void addInputPattern(InputPattern input) { inputPatterns.add(input); }
        public void addPerformanceData(PerformanceData performance) { performanceData.add(performance); }
        public void addMemoryState(MemoryState memory) { memoryStates.add(memory); }
        public void addStatisticalAnalysis(StatisticalAnalysis analysis) { statisticalAnalyses.add(analysis); }
        
        public void finalize(String conclusion, String investigatorNotes) {
            this.endTime = LocalDateTime.now();
            this.conclusion = conclusion;
            this.investigatorNotes = investigatorNotes;
        }
        
        public int getTotalDataPoints() {
            return violations.size() + movementData.size() + weaponData.size() + 
                   networkTimings.size() + inputPatterns.size() + performanceData.size() + 
                   memoryStates.size() + statisticalAnalyses.size();
        }
        
        // Getters
        public String getSessionId() { return sessionId; }
        public String getPlayerId() { return playerId; }
        public String getTrigger() { return trigger; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public String getConclusion() { return conclusion; }
        public String getInvestigatorNotes() { return investigatorNotes; }
        public List<ViolationData> getViolations() { return new ArrayList<>(violations); }
        public List<MovementData> getMovementData() { return new ArrayList<>(movementData); }
        public List<WeaponData> getWeaponData() { return new ArrayList<>(weaponData); }
        public List<NetworkTiming> getNetworkTimings() { return new ArrayList<>(networkTimings); }
        public List<InputPattern> getInputPatterns() { return new ArrayList<>(inputPatterns); }
        public List<PerformanceData> getPerformanceData() { return new ArrayList<>(performanceData); }
        public List<MemoryState> getMemoryStates() { return new ArrayList<>(memoryStates); }
        public List<StatisticalAnalysis> getStatisticalAnalyses() { return new ArrayList<>(statisticalAnalyses); }
    }
    
    // Data structure classes
    public static class MovementData {
        private final LocalDateTime timestamp;
        private final Object position;
        private final Object velocity;
        private final Object rotation;
        private final Object inputState;
        private final boolean onGround;
        private final double movementSpeed;
        
        public MovementData(LocalDateTime timestamp, Object position, Object velocity, 
                          Object rotation, Object inputState, boolean onGround, double movementSpeed) {
            this.timestamp = timestamp;
            this.position = position;
            this.velocity = velocity;
            this.rotation = rotation;
            this.inputState = inputState;
            this.onGround = onGround;
            this.movementSpeed = movementSpeed;
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public Object getPosition() { return position; }
        public Object getVelocity() { return velocity; }
        public Object getRotation() { return rotation; }
        public Object getInputState() { return inputState; }
        public boolean isOnGround() { return onGround; }
        public double getMovementSpeed() { return movementSpeed; }
    }
    
    public static class WeaponData {
        private final LocalDateTime timestamp;
        private final String weapon;
        private final String weaponAction;
        private final Map<String, Object> weaponState;
        private final Object aimDirection;
        private final Object recoilPattern;
        
        public WeaponData(LocalDateTime timestamp, String weapon, String weaponAction,
                         Map<String, Object> weaponState, Object aimDirection, Object recoilPattern) {
            this.timestamp = timestamp;
            this.weapon = weapon;
            this.weaponAction = weaponAction;
            this.weaponState = new HashMap<>(weaponState);
            this.aimDirection = aimDirection;
            this.recoilPattern = recoilPattern;
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getWeapon() { return weapon; }
        public String getWeaponAction() { return weaponAction; }
        public Map<String, Object> getWeaponState() { return new HashMap<>(weaponState); }
        public Object getAimDirection() { return aimDirection; }
        public Object getRecoilPattern() { return recoilPattern; }
    }
    
    public static class NetworkTiming {
        private final LocalDateTime timestamp;
        private final String packetType;
        private final long sendTime;
        private final long receiveTime;
        private final long latency;
        private final int packetSize;
        
        public NetworkTiming(LocalDateTime timestamp, String packetType, long sendTime,
                           long receiveTime, long latency, int packetSize) {
            this.timestamp = timestamp;
            this.packetType = packetType;
            this.sendTime = sendTime;
            this.receiveTime = receiveTime;
            this.latency = latency;
            this.packetSize = packetSize;
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getPacketType() { return packetType; }
        public long getSendTime() { return sendTime; }
        public long getReceiveTime() { return receiveTime; }
        public long getLatency() { return latency; }
        public int getPacketSize() { return packetSize; }
    }
    
    public static class InputPattern {
        private final LocalDateTime timestamp;
        private final String inputType;
        private final Object inputData;
        private final long inputTimestamp;
        private final long timingDelta;
        
        public InputPattern(LocalDateTime timestamp, String inputType, Object inputData,
                          long inputTimestamp, long timingDelta) {
            this.timestamp = timestamp;
            this.inputType = inputType;
            this.inputData = inputData;
            this.inputTimestamp = inputTimestamp;
            this.timingDelta = timingDelta;
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getInputType() { return inputType; }
        public Object getInputData() { return inputData; }
        public long getInputTimestamp() { return inputTimestamp; }
        public long getTimingDelta() { return timingDelta; }
    }
    
    public static class PerformanceData {
        private final LocalDateTime timestamp;
        private final Map<String, Object> performanceMetrics;
        
        public PerformanceData(LocalDateTime timestamp, Map<String, Object> performanceMetrics) {
            this.timestamp = timestamp;
            this.performanceMetrics = new HashMap<>(performanceMetrics);
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public Map<String, Object> getPerformanceMetrics() { return new HashMap<>(performanceMetrics); }
    }
    
    public static class MemoryState {
        private final LocalDateTime timestamp;
        private final Map<String, Object> memoryInfo;
        private final List<String> suspiciousProcesses;
        
        public MemoryState(LocalDateTime timestamp, Map<String, Object> memoryInfo,
                          List<String> suspiciousProcesses) {
            this.timestamp = timestamp;
            this.memoryInfo = new HashMap<>(memoryInfo);
            this.suspiciousProcesses = new ArrayList<>(suspiciousProcesses);
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public Map<String, Object> getMemoryInfo() { return new HashMap<>(memoryInfo); }
        public List<String> getSuspiciousProcesses() { return new ArrayList<>(suspiciousProcesses); }
    }
    
    public static class StatisticalAnalysis {
        private final LocalDateTime timestamp;
        private final String analysisType;
        private final Map<String, Object> analysisResults;
        
        public StatisticalAnalysis(LocalDateTime timestamp, String analysisType,
                                 Map<String, Object> analysisResults) {
            this.timestamp = timestamp;
            this.analysisType = analysisType;
            this.analysisResults = new HashMap<>(analysisResults);
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getAnalysisType() { return analysisType; }
        public Map<String, Object> getAnalysisResults() { return new HashMap<>(analysisResults); }
    }
    
    public static class ForensicReport {
        private final String sessionId;
        private final String playerId;
        private final String trigger;
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;
        private final String conclusion;
        private final String investigatorNotes;
        private final Map<String, Object> movementAnalysis;
        private final Map<String, Object> weaponAnalysis;
        private final Map<String, Object> networkAnalysis;
        private final Map<String, Object> inputAnalysis;
        private final List<PerformanceData> performanceData;
        private final List<MemoryState> memoryStates;
        private final List<StatisticalAnalysis> statisticalAnalyses;
        
        public ForensicReport(String sessionId, String playerId, String trigger,
                            LocalDateTime startTime, LocalDateTime endTime, String conclusion,
                            String investigatorNotes, Map<String, Object> movementAnalysis,
                            Map<String, Object> weaponAnalysis, Map<String, Object> networkAnalysis,
                            Map<String, Object> inputAnalysis, List<PerformanceData> performanceData,
                            List<MemoryState> memoryStates, List<StatisticalAnalysis> statisticalAnalyses) {
            this.sessionId = sessionId;
            this.playerId = playerId;
            this.trigger = trigger;
            this.startTime = startTime;
            this.endTime = endTime;
            this.conclusion = conclusion;
            this.investigatorNotes = investigatorNotes;
            this.movementAnalysis = new HashMap<>(movementAnalysis);
            this.weaponAnalysis = new HashMap<>(weaponAnalysis);
            this.networkAnalysis = new HashMap<>(networkAnalysis);
            this.inputAnalysis = new HashMap<>(inputAnalysis);
            this.performanceData = new ArrayList<>(performanceData);
            this.memoryStates = new ArrayList<>(memoryStates);
            this.statisticalAnalyses = new ArrayList<>(statisticalAnalyses);
        }
        
        // Getters
        public String getSessionId() { return sessionId; }
        public String getPlayerId() { return playerId; }
        public String getTrigger() { return trigger; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public String getConclusion() { return conclusion; }
        public String getInvestigatorNotes() { return investigatorNotes; }
        public Map<String, Object> getMovementAnalysis() { return new HashMap<>(movementAnalysis); }
        public Map<String, Object> getWeaponAnalysis() { return new HashMap<>(weaponAnalysis); }
        public Map<String, Object> getNetworkAnalysis() { return new HashMap<>(networkAnalysis); }
        public Map<String, Object> getInputAnalysis() { return new HashMap<>(inputAnalysis); }
        public List<PerformanceData> getPerformanceData() { return new ArrayList<>(performanceData); }
        public List<MemoryState> getMemoryStates() { return new ArrayList<>(memoryStates); }
        public List<StatisticalAnalysis> getStatisticalAnalyses() { return new ArrayList<>(statisticalAnalyses); }
    }
}