package fps.matchmaking;

import fps.matchmaking.MatchmakingTypes.Region;

/**
 * Configuration settings for the matchmaking system
 */
public class MatchmakingConfiguration {
    
    // Core matchmaking settings
    private int matchmakingThreads = 4;
    private long matchmakingIntervalMs = 1000; // 1 second
    private long statisticsIntervalMs = 5000; // 5 seconds
    private long queueHealthIntervalMs = 10000; // 10 seconds
    
    // Queue settings
    private long maxAcceptableWaitTime = 300000; // 5 minutes
    private int maxQueueSize = 10000;
    private float expandingSearchRate = 50.0f; // Rating expansion per second
    private long maxSearchTime = 600000; // 10 minutes max search
    
    // Match quality settings
    private float minAcceptableMatchQuality = 0.3f;
    private float preferredMatchQuality = 0.7f;
    private boolean prioritizeQualityOverSpeed = true;
    
    // Regional settings
    private Region defaultRegion = Region.AUTO;
    private int maxPingThreshold = 150;
    private boolean allowCrossRegionMatching = true;
    
    // Component configurations
    private QueueConfiguration queueConfig;
    private HBRConfiguration hbrConfig;
    private RankingConfiguration rankingConfig;
    private MatchFormationConfiguration matchFormationConfig;
    private StatisticsConfiguration statisticsConfig;
    private AntiCheatConfiguration antiCheatConfig;
    private TournamentConfiguration tournamentConfig;
    
    public MatchmakingConfiguration() {
        initializeDefaultConfigurations();
    }
    
    private void initializeDefaultConfigurations() {
        this.queueConfig = new QueueConfiguration();
        this.hbrConfig = new HBRConfiguration();
        this.rankingConfig = new RankingConfiguration();
        this.matchFormationConfig = new MatchFormationConfiguration();
        this.statisticsConfig = new StatisticsConfiguration();
        this.antiCheatConfig = new AntiCheatConfiguration();
        this.tournamentConfig = new TournamentConfiguration();
    }
    
    // Getters and setters
    public int getMatchmakingThreads() { return matchmakingThreads; }
    public void setMatchmakingThreads(int matchmakingThreads) { this.matchmakingThreads = matchmakingThreads; }
    
    public long getMatchmakingIntervalMs() { return matchmakingIntervalMs; }
    public void setMatchmakingIntervalMs(long matchmakingIntervalMs) { this.matchmakingIntervalMs = matchmakingIntervalMs; }
    
    public long getStatisticsIntervalMs() { return statisticsIntervalMs; }
    public void setStatisticsIntervalMs(long statisticsIntervalMs) { this.statisticsIntervalMs = statisticsIntervalMs; }
    
    public long getQueueHealthIntervalMs() { return queueHealthIntervalMs; }
    public void setQueueHealthIntervalMs(long queueHealthIntervalMs) { this.queueHealthIntervalMs = queueHealthIntervalMs; }
    
    public long getMaxAcceptableWaitTime() { return maxAcceptableWaitTime; }
    public void setMaxAcceptableWaitTime(long maxAcceptableWaitTime) { this.maxAcceptableWaitTime = maxAcceptableWaitTime; }
    
    public int getMaxQueueSize() { return maxQueueSize; }
    public void setMaxQueueSize(int maxQueueSize) { this.maxQueueSize = maxQueueSize; }
    
    public float getExpandingSearchRate() { return expandingSearchRate; }
    public void setExpandingSearchRate(float expandingSearchRate) { this.expandingSearchRate = expandingSearchRate; }
    
    public long getMaxSearchTime() { return maxSearchTime; }
    public void setMaxSearchTime(long maxSearchTime) { this.maxSearchTime = maxSearchTime; }
    
    public float getMinAcceptableMatchQuality() { return minAcceptableMatchQuality; }
    public void setMinAcceptableMatchQuality(float minAcceptableMatchQuality) { this.minAcceptableMatchQuality = minAcceptableMatchQuality; }
    
    public float getPreferredMatchQuality() { return preferredMatchQuality; }
    public void setPreferredMatchQuality(float preferredMatchQuality) { this.preferredMatchQuality = preferredMatchQuality; }
    
    public boolean isPrioritizeQualityOverSpeed() { return prioritizeQualityOverSpeed; }
    public void setPrioritizeQualityOverSpeed(boolean prioritizeQualityOverSpeed) { this.prioritizeQualityOverSpeed = prioritizeQualityOverSpeed; }
    
    public Region getDefaultRegion() { return defaultRegion; }
    public void setDefaultRegion(Region defaultRegion) { this.defaultRegion = defaultRegion; }
    
    public int getMaxPingThreshold() { return maxPingThreshold; }
    public void setMaxPingThreshold(int maxPingThreshold) { this.maxPingThreshold = maxPingThreshold; }
    
    public boolean isAllowCrossRegionMatching() { return allowCrossRegionMatching; }
    public void setAllowCrossRegionMatching(boolean allowCrossRegionMatching) { this.allowCrossRegionMatching = allowCrossRegionMatching; }
    
    // Component configuration getters
    public QueueConfiguration getQueueConfig() { return queueConfig; }
    public HBRConfiguration getHbrConfig() { return hbrConfig; }
    public RankingConfiguration getRankingConfig() { return rankingConfig; }
    public MatchFormationConfiguration getMatchFormationConfig() { return matchFormationConfig; }
    public StatisticsConfiguration getStatisticsConfig() { return statisticsConfig; }
    public AntiCheatConfiguration getAntiCheatConfig() { return antiCheatConfig; }
    public TournamentConfiguration getTournamentConfig() { return tournamentConfig; }
    
    /**
     * Queue system configuration
     */
    public static class QueueConfiguration {
        private int maxPlayersPerQueue = 10000;
        private long queueTimeoutMs = 1800000; // 30 minutes
        private boolean enablePriorityQueue = true;
        private float premiumPlayerPriorityMultiplier = 1.5f;
        private int maxPartySizeForSoloQueue = 1;
        private int maxPartySizeForTeamQueue = 4;
        
        // Getters and setters
        public int getMaxPlayersPerQueue() { return maxPlayersPerQueue; }
        public void setMaxPlayersPerQueue(int maxPlayersPerQueue) { this.maxPlayersPerQueue = maxPlayersPerQueue; }
        
        public long getQueueTimeoutMs() { return queueTimeoutMs; }
        public void setQueueTimeoutMs(long queueTimeoutMs) { this.queueTimeoutMs = queueTimeoutMs; }
        
        public boolean isEnablePriorityQueue() { return enablePriorityQueue; }
        public void setEnablePriorityQueue(boolean enablePriorityQueue) { this.enablePriorityQueue = enablePriorityQueue; }
        
        public float getPremiumPlayerPriorityMultiplier() { return premiumPlayerPriorityMultiplier; }
        public void setPremiumPlayerPriorityMultiplier(float premiumPlayerPriorityMultiplier) { this.premiumPlayerPriorityMultiplier = premiumPlayerPriorityMultiplier; }
        
        public int getMaxPartySizeForSoloQueue() { return maxPartySizeForSoloQueue; }
        public void setMaxPartySizeForSoloQueue(int maxPartySizeForSoloQueue) { this.maxPartySizeForSoloQueue = maxPartySizeForSoloQueue; }
        
        public int getMaxPartySizeForTeamQueue() { return maxPartySizeForTeamQueue; }
        public void setMaxPartySizeForTeamQueue(int maxPartySizeForTeamQueue) { this.maxPartySizeForTeamQueue = maxPartySizeForTeamQueue; }
    }
    
    /**
     * Hidden Battle Rating system configuration
     */
    public static class HBRConfiguration {
        private float initialRating = 1500.0f;
        private float kFactor = 32.0f;
        private float initialUncertainty = 350.0f;
        private float minUncertainty = 50.0f;
        private float uncertaintyDecayRate = 0.95f;
        private int placementMatchCount = 10;
        private float maxRatingChange = 100.0f;
        private boolean enableSeasonalDecay = true;
        private float seasonalDecayRate = 0.98f;
        
        // Getters and setters
        public float getInitialRating() { return initialRating; }
        public void setInitialRating(float initialRating) { this.initialRating = initialRating; }
        
        public float getKFactor() { return kFactor; }
        public void setKFactor(float kFactor) { this.kFactor = kFactor; }
        
        public float getInitialUncertainty() { return initialUncertainty; }
        public void setInitialUncertainty(float initialUncertainty) { this.initialUncertainty = initialUncertainty; }
        
        public float getMinUncertainty() { return minUncertainty; }
        public void setMinUncertainty(float minUncertainty) { this.minUncertainty = minUncertainty; }
        
        public float getUncertaintyDecayRate() { return uncertaintyDecayRate; }
        public void setUncertaintyDecayRate(float uncertaintyDecayRate) { this.uncertaintyDecayRate = uncertaintyDecayRate; }
        
        public int getPlacementMatchCount() { return placementMatchCount; }
        public void setPlacementMatchCount(int placementMatchCount) { this.placementMatchCount = placementMatchCount; }
        
        public float getMaxRatingChange() { return maxRatingChange; }
        public void setMaxRatingChange(float maxRatingChange) { this.maxRatingChange = maxRatingChange; }
        
        public boolean isEnableSeasonalDecay() { return enableSeasonalDecay; }
        public void setEnableSeasonalDecay(boolean enableSeasonalDecay) { this.enableSeasonalDecay = enableSeasonalDecay; }
        
        public float getSeasonalDecayRate() { return seasonalDecayRate; }
        public void setSeasonalDecayRate(float seasonalDecayRate) { this.seasonalDecayRate = seasonalDecayRate; }
    }
    
    /**
     * Ranking system configuration
     */
    public static class RankingConfiguration {
        private boolean enableRankDecay = true;
        private long rankDecayPeriodMs = 2592000000L; // 30 days
        private float rankDecayAmount = 0.05f;
        private int promotionWinStreak = 3;
        private int demotionLossStreak = 5;
        private boolean enableRankProtection = true;
        private int rankProtectionGames = 3;
        
        // Getters and setters
        public boolean isEnableRankDecay() { return enableRankDecay; }
        public void setEnableRankDecay(boolean enableRankDecay) { this.enableRankDecay = enableRankDecay; }
        
        public long getRankDecayPeriodMs() { return rankDecayPeriodMs; }
        public void setRankDecayPeriodMs(long rankDecayPeriodMs) { this.rankDecayPeriodMs = rankDecayPeriodMs; }
        
        public float getRankDecayAmount() { return rankDecayAmount; }
        public void setRankDecayAmount(float rankDecayAmount) { this.rankDecayAmount = rankDecayAmount; }
        
        public int getPromotionWinStreak() { return promotionWinStreak; }
        public void setPromotionWinStreak(int promotionWinStreak) { this.promotionWinStreak = promotionWinStreak; }
        
        public int getDemotionLossStreak() { return demotionLossStreak; }
        public void setDemotionLossStreak(int demotionLossStreak) { this.demotionLossStreak = demotionLossStreak; }
        
        public boolean isEnableRankProtection() { return enableRankProtection; }
        public void setEnableRankProtection(boolean enableRankProtection) { this.enableRankProtection = enableRankProtection; }
        
        public int getRankProtectionGames() { return rankProtectionGames; }
        public void setRankProtectionGames(int rankProtectionGames) { this.rankProtectionGames = rankProtectionGames; }
    }
    
    /**
     * Match formation configuration
     */
    public static class MatchFormationConfiguration {
        private float maxRatingDifference = 200.0f;
        private float ratingExpansionRate = 10.0f; // Per second
        private float teamBalanceWeight = 0.4f;
        private float pingWeight = 0.3f;
        private float waitTimeWeight = 0.3f;
        private int maxIterationsPerSearch = 1000;
        private boolean enableAdvancedBalancing = true;
        
        // Getters and setters
        public float getMaxRatingDifference() { return maxRatingDifference; }
        public void setMaxRatingDifference(float maxRatingDifference) { this.maxRatingDifference = maxRatingDifference; }
        
        public float getRatingExpansionRate() { return ratingExpansionRate; }
        public void setRatingExpansionRate(float ratingExpansionRate) { this.ratingExpansionRate = ratingExpansionRate; }
        
        public float getTeamBalanceWeight() { return teamBalanceWeight; }
        public void setTeamBalanceWeight(float teamBalanceWeight) { this.teamBalanceWeight = teamBalanceWeight; }
        
        public float getPingWeight() { return pingWeight; }
        public void setPingWeight(float pingWeight) { this.pingWeight = pingWeight; }
        
        public float getWaitTimeWeight() { return waitTimeWeight; }
        public void setWaitTimeWeight(float waitTimeWeight) { this.waitTimeWeight = waitTimeWeight; }
        
        public int getMaxIterationsPerSearch() { return maxIterationsPerSearch; }
        public void setMaxIterationsPerSearch(int maxIterationsPerSearch) { this.maxIterationsPerSearch = maxIterationsPerSearch; }
        
        public boolean isEnableAdvancedBalancing() { return enableAdvancedBalancing; }
        public void setEnableAdvancedBalancing(boolean enableAdvancedBalancing) { this.enableAdvancedBalancing = enableAdvancedBalancing; }
    }
    
    /**
     * Statistics configuration
     */
    public static class StatisticsConfiguration {
        private boolean enableDetailedTracking = true;
        private long statisticsRetentionPeriodMs = 31536000000L; // 1 year
        private int maxStatisticsPerPlayer = 10000;
        private boolean enableRealTimeUpdates = true;
        
        // Getters and setters
        public boolean isEnableDetailedTracking() { return enableDetailedTracking; }
        public void setEnableDetailedTracking(boolean enableDetailedTracking) { this.enableDetailedTracking = enableDetailedTracking; }
        
        public long getStatisticsRetentionPeriodMs() { return statisticsRetentionPeriodMs; }
        public void setStatisticsRetentionPeriodMs(long statisticsRetentionPeriodMs) { this.statisticsRetentionPeriodMs = statisticsRetentionPeriodMs; }
        
        public int getMaxStatisticsPerPlayer() { return maxStatisticsPerPlayer; }
        public void setMaxStatisticsPerPlayer(int maxStatisticsPerPlayer) { this.maxStatisticsPerPlayer = maxStatisticsPerPlayer; }
        
        public boolean isEnableRealTimeUpdates() { return enableRealTimeUpdates; }
        public void setEnableRealTimeUpdates(boolean enableRealTimeUpdates) { this.enableRealTimeUpdates = enableRealTimeUpdates; }
    }
    
    /**
     * Anti-cheat configuration
     */
    public static class AntiCheatConfiguration {
        private boolean enableSmurfDetection = true;
        private boolean enableBoostingDetection = true;
        private float smurfDetectionThreshold = 0.8f;
        private float boostingDetectionThreshold = 0.7f;
        private long penaltyDuration = 86400000L; // 24 hours
        private int maxWarningsBeforePenalty = 3;
        
        // Getters and setters
        public boolean isEnableSmurfDetection() { return enableSmurfDetection; }
        public void setEnableSmurfDetection(boolean enableSmurfDetection) { this.enableSmurfDetection = enableSmurfDetection; }
        
        public boolean isEnableBoostingDetection() { return enableBoostingDetection; }
        public void setEnableBoostingDetection(boolean enableBoostingDetection) { this.enableBoostingDetection = enableBoostingDetection; }
        
        public float getSmurfDetectionThreshold() { return smurfDetectionThreshold; }
        public void setSmurfDetectionThreshold(float smurfDetectionThreshold) { this.smurfDetectionThreshold = smurfDetectionThreshold; }
        
        public float getBoostingDetectionThreshold() { return boostingDetectionThreshold; }
        public void setBoostingDetectionThreshold(float boostingDetectionThreshold) { this.boostingDetectionThreshold = boostingDetectionThreshold; }
        
        public long getPenaltyDuration() { return penaltyDuration; }
        public void setPenaltyDuration(long penaltyDuration) { this.penaltyDuration = penaltyDuration; }
        
        public int getMaxWarningsBeforePenalty() { return maxWarningsBeforePenalty; }
        public void setMaxWarningsBeforePenalty(int maxWarningsBeforePenalty) { this.maxWarningsBeforePenalty = maxWarningsBeforePenalty; }
    }
    
    /**
     * Tournament configuration
     */
    public static class TournamentConfiguration {
        private boolean enableAutomatedTournaments = true;
        private int minPlayersForTournament = 16;
        private int maxPlayersForTournament = 256;
        private long tournamentRegistrationPeriodMs = 3600000L; // 1 hour
        private boolean enablePrizeDistribution = true;
        
        // Getters and setters
        public boolean isEnableAutomatedTournaments() { return enableAutomatedTournaments; }
        public void setEnableAutomatedTournaments(boolean enableAutomatedTournaments) { this.enableAutomatedTournaments = enableAutomatedTournaments; }
        
        public int getMinPlayersForTournament() { return minPlayersForTournament; }
        public void setMinPlayersForTournament(int minPlayersForTournament) { this.minPlayersForTournament = minPlayersForTournament; }
        
        public int getMaxPlayersForTournament() { return maxPlayersForTournament; }
        public void setMaxPlayersForTournament(int maxPlayersForTournament) { this.maxPlayersForTournament = maxPlayersForTournament; }
        
        public long getTournamentRegistrationPeriodMs() { return tournamentRegistrationPeriodMs; }
        public void setTournamentRegistrationPeriodMs(long tournamentRegistrationPeriodMs) { this.tournamentRegistrationPeriodMs = tournamentRegistrationPeriodMs; }
        
        public boolean isEnablePrizeDistribution() { return enablePrizeDistribution; }
        public void setEnablePrizeDistribution(boolean enablePrizeDistribution) { this.enablePrizeDistribution = enablePrizeDistribution; }
    }
}