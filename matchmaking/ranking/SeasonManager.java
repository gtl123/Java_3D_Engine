package fps.matchmaking.ranking;

import engine.logging.LogManager;
import fps.matchmaking.MatchmakingConfiguration.RankingConfiguration;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages competitive seasons and season transitions
 */
public class SeasonManager {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final RankingConfiguration config;
    private final AtomicInteger currentSeason = new AtomicInteger(1);
    private final AtomicLong seasonStartTime = new AtomicLong(0);
    private final AtomicLong seasonEndTime = new AtomicLong(0);
    
    // Season duration (default: 3 months)
    private static final long DEFAULT_SEASON_DURATION_MS = 90L * 24 * 60 * 60 * 1000; // 90 days
    
    private volatile boolean initialized = false;
    
    public SeasonManager(RankingConfiguration config) {
        this.config = config;
    }
    
    public void initialize() {
        // Initialize with current season
        long currentTime = System.currentTimeMillis();
        seasonStartTime.set(currentTime);
        seasonEndTime.set(currentTime + DEFAULT_SEASON_DURATION_MS);
        
        initialized = true;
        
        logManager.info("SeasonManager", "Season manager initialized",
                       "currentSeason", currentSeason.get(),
                       "seasonStartTime", seasonStartTime.get(),
                       "seasonEndTime", seasonEndTime.get());
    }
    
    /**
     * Start a new competitive season
     */
    public void startNewSeason() {
        int newSeason = currentSeason.incrementAndGet();
        long currentTime = System.currentTimeMillis();
        
        seasonStartTime.set(currentTime);
        seasonEndTime.set(currentTime + DEFAULT_SEASON_DURATION_MS);
        
        logManager.info("SeasonManager", "New season started",
                       "season", newSeason,
                       "startTime", seasonStartTime.get(),
                       "endTime", seasonEndTime.get());
    }
    
    /**
     * Check if current season has ended
     */
    public boolean hasSeasonEnded() {
        return System.currentTimeMillis() >= seasonEndTime.get();
    }
    
    /**
     * Get time remaining in current season
     */
    public long getTimeRemainingMs() {
        long remaining = seasonEndTime.get() - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
    
    /**
     * Get season progress (0.0 to 1.0)
     */
    public float getSeasonProgress() {
        long currentTime = System.currentTimeMillis();
        long startTime = seasonStartTime.get();
        long endTime = seasonEndTime.get();
        
        if (currentTime <= startTime) {
            return 0.0f;
        }
        
        if (currentTime >= endTime) {
            return 1.0f;
        }
        
        return (float) (currentTime - startTime) / (endTime - startTime);
    }
    
    /**
     * Get days remaining in season
     */
    public int getDaysRemaining() {
        long remainingMs = getTimeRemainingMs();
        return (int) (remainingMs / (24 * 60 * 60 * 1000));
    }
    
    /**
     * Get season information
     */
    public SeasonInfo getSeasonInfo() {
        return new SeasonInfo(
            currentSeason.get(),
            seasonStartTime.get(),
            seasonEndTime.get(),
            getSeasonProgress(),
            getDaysRemaining()
        );
    }
    
    /**
     * Set season duration
     */
    public void setSeasonDuration(long durationMs) {
        long currentTime = System.currentTimeMillis();
        long startTime = seasonStartTime.get();
        
        seasonEndTime.set(startTime + durationMs);
        
        logManager.info("SeasonManager", "Season duration updated",
                       "durationMs", durationMs,
                       "newEndTime", seasonEndTime.get());
    }
    
    /**
     * Extend current season
     */
    public void extendSeason(long extensionMs) {
        long newEndTime = seasonEndTime.addAndGet(extensionMs);
        
        logManager.info("SeasonManager", "Season extended",
                       "extensionMs", extensionMs,
                       "newEndTime", newEndTime);
    }
    
    /**
     * Force end current season
     */
    public void endCurrentSeason() {
        seasonEndTime.set(System.currentTimeMillis());
        
        logManager.info("SeasonManager", "Season force ended",
                       "season", currentSeason.get());
    }
    
    /**
     * Check if we're in preseason (before official start)
     */
    public boolean isPreseason() {
        return System.currentTimeMillis() < seasonStartTime.get();
    }
    
    /**
     * Check if we're in season end period
     */
    public boolean isSeasonEndPeriod() {
        long timeRemaining = getTimeRemainingMs();
        return timeRemaining <= (7 * 24 * 60 * 60 * 1000); // Last 7 days
    }
    
    public void cleanup() {
        initialized = false;
        logManager.info("SeasonManager", "Season manager cleaned up");
    }
    
    // Getters
    public int getCurrentSeason() { return currentSeason.get(); }
    public long getSeasonStartTime() { return seasonStartTime.get(); }
    public long getSeasonEndTime() { return seasonEndTime.get(); }
    public boolean isInitialized() { return initialized; }
    
    /**
     * Season information data class
     */
    public static class SeasonInfo {
        private final int seasonNumber;
        private final long startTime;
        private final long endTime;
        private final float progress;
        private final int daysRemaining;
        
        public SeasonInfo(int seasonNumber, long startTime, long endTime, 
                         float progress, int daysRemaining) {
            this.seasonNumber = seasonNumber;
            this.startTime = startTime;
            this.endTime = endTime;
            this.progress = progress;
            this.daysRemaining = daysRemaining;
        }
        
        // Getters
        public int getSeasonNumber() { return seasonNumber; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public float getProgress() { return progress; }
        public int getDaysRemaining() { return daysRemaining; }
        
        public boolean isActive() {
            long currentTime = System.currentTimeMillis();
            return currentTime >= startTime && currentTime < endTime;
        }
        
        public boolean hasEnded() {
            return System.currentTimeMillis() >= endTime;
        }
        
        @Override
        public String toString() {
            return String.format("Season %d (%.1f%% complete, %d days remaining)",
                               seasonNumber, progress * 100, daysRemaining);
        }
    }
}