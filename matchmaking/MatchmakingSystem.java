package fps.matchmaking;

import engine.logging.LogManager;
import fps.matchmaking.formation.MatchFormationEngine;
import fps.matchmaking.queue.QueueManager;
import fps.matchmaking.rating.HBRSystem;
import fps.matchmaking.ranking.RankingSystem;
import fps.matchmaking.statistics.MatchmakingStatistics;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central matchmaking system that coordinates all matchmaking components.
 * Provides queue management, match formation, and rating systems.
 */
public class MatchmakingSystem {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // Core components
    private QueueManager queueManager;
    private MatchFormationEngine matchFormationEngine;
    private HBRSystem hbrSystem;
    private RankingSystem rankingSystem;
    private MatchmakingStatistics statistics;
    
    // Configuration
    private MatchmakingConfiguration config;
    
    // System state
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // Threading
    private ScheduledExecutorService executorService;
    
    public MatchmakingSystem() {
        logManager.info("MatchmakingSystem", "Matchmaking system created");
    }
    
    /**
     * Initialize the matchmaking system
     */
    public void initialize(MatchmakingConfiguration config) throws Exception {
        if (initialized.get()) {
            return;
        }
        
        logManager.info("MatchmakingSystem", "Initializing matchmaking system");
        
        try {
            this.config = config;
            
            // Initialize components
            queueManager = new QueueManager();
            queueManager.initialize(config.getQueueConfig());
            
            matchFormationEngine = new MatchFormationEngine();
            matchFormationEngine.initialize(config.getMatchFormationConfig());
            
            hbrSystem = new HBRSystem();
            hbrSystem.initialize(config.getHbrConfig());
            
            rankingSystem = new RankingSystem();
            rankingSystem.initialize(config.getRankingConfig());
            
            statistics = new MatchmakingStatistics();
            statistics.initialize(config.getStatisticsConfig());
            
            // Initialize executor service
            executorService = Executors.newScheduledThreadPool(config.getMatchmakingThreads());
            
            initialized.set(true);
            
            logManager.info("MatchmakingSystem", "Matchmaking system initialized successfully");
            
        } catch (Exception e) {
            logManager.error("MatchmakingSystem", "Failed to initialize matchmaking system", e);
            throw e;
        }
    }
    
    /**
     * Start the matchmaking system
     */
    public void start() throws Exception {
        if (!initialized.get()) {
            throw new IllegalStateException("MatchmakingSystem must be initialized before starting");
        }
        
        if (running.get()) {
            return;
        }
        
        logManager.info("MatchmakingSystem", "Starting matchmaking system");
        
        try {
            // Start components
            queueManager.start();
            matchFormationEngine.start();
            hbrSystem.start();
            rankingSystem.start();
            statistics.start();
            
            // Start background tasks
            startBackgroundTasks();
            
            running.set(true);
            
            logManager.info("MatchmakingSystem", "Matchmaking system started successfully");
            
        } catch (Exception e) {
            logManager.error("MatchmakingSystem", "Failed to start matchmaking system", e);
            throw e;
        }
    }
    
    /**
     * Start background matchmaking tasks
     */
    private void startBackgroundTasks() {
        // Matchmaking processing task
        executorService.scheduleAtFixedRate(() -> {
            try {
                processMatchmaking();
            } catch (Exception e) {
                logManager.error("MatchmakingSystem", "Error in matchmaking processing", e);
            }
        }, 0, config.getMatchmakingIntervalMs(), TimeUnit.MILLISECONDS);
        
        // Statistics collection task
        executorService.scheduleAtFixedRate(() -> {
            try {
                statistics.collectStatistics();
            } catch (Exception e) {
                logManager.error("MatchmakingSystem", "Error collecting statistics", e);
            }
        }, config.getStatisticsIntervalMs(), config.getStatisticsIntervalMs(), TimeUnit.MILLISECONDS);
        
        // Queue health monitoring task
        executorService.scheduleAtFixedRate(() -> {
            try {
                queueManager.monitorQueueHealth();
            } catch (Exception e) {
                logManager.error("MatchmakingSystem", "Error monitoring queue health", e);
            }
        }, config.getQueueHealthIntervalMs(), config.getQueueHealthIntervalMs(), TimeUnit.MILLISECONDS);
    }
    
    /**
     * Process matchmaking for all queues
     */
    private void processMatchmaking() {
        if (!running.get()) {
            return;
        }
        
        try {
            // Process each queue
            queueManager.processQueues(matchFormationEngine);
            
        } catch (Exception e) {
            logManager.error("MatchmakingSystem", "Error processing matchmaking", e);
        }
    }
    
    /**
     * Shutdown the matchmaking system
     */
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        
        logManager.info("MatchmakingSystem", "Shutting down matchmaking system");
        
        try {
            // Stop executor service
            if (executorService != null) {
                executorService.shutdown();
                executorService.awaitTermination(5, TimeUnit.SECONDS);
            }
            
            // Stop components
            if (statistics != null) statistics.shutdown();
            if (rankingSystem != null) rankingSystem.shutdown();
            if (hbrSystem != null) hbrSystem.shutdown();
            if (matchFormationEngine != null) matchFormationEngine.shutdown();
            if (queueManager != null) queueManager.shutdown();
            
            initialized.set(false);
            
            logManager.info("MatchmakingSystem", "Matchmaking system shutdown complete");
            
        } catch (Exception e) {
            logManager.error("MatchmakingSystem", "Error during matchmaking system shutdown", e);
        }
    }
    
    // Getters
    public boolean isInitialized() { return initialized.get(); }
    public boolean isRunning() { return running.get(); }
    public QueueManager getQueueManager() { return queueManager; }
    public MatchFormationEngine getMatchFormationEngine() { return matchFormationEngine; }
    public HBRSystem getHBRSystem() { return hbrSystem; }
    public RankingSystem getRankingSystem() { return rankingSystem; }
    public MatchmakingStatistics getStatistics() { return statistics; }
    public MatchmakingConfiguration getConfiguration() { return config; }
}