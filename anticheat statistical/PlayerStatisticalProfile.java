package fps.anticheat.statistical;

import fps.anticheat.PlayerStatistics;

import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tracks statistical patterns and historical data for an individual player.
 * Maintains statistical history for anomaly detection and trend analysis.
 */
public class PlayerStatisticalProfile {
    
    private final String playerId;
    private final long creationTime;
    private long lastUpdateTime;
    
    // Statistical history
    private final Queue<PlayerStatistics> statisticsHistory = new ConcurrentLinkedQueue<>();
    private final List<PlayerStatistics> significantStatistics = new ArrayList<>();
    private int maxHistorySize = 100;
    private int maxSignificantSize = 50;
    
    // Anomaly tracking
    private float anomalyLevel = 0.0f;
    private int violationCount = 0;
    private long totalUpdates = 0;
    
    // Current statistics
    private PlayerStatistics currentStatistics;
    private PlayerStatistics baselineStatistics;
    
    // Statistical trends
    private final StatisticalTrends trends = new StatisticalTrends();
    
    public PlayerStatisticalProfile(String playerId) {
        this.playerId = playerId;
        this.creationTime = System.currentTimeMillis();
        this.lastUpdateTime = creationTime;
    }
    
    /**
     * Update profile with new statistics
     */
    public synchronized void updateStatistics(PlayerStatistics statistics) {
        lastUpdateTime = System.currentTimeMillis();
        totalUpdates++;
        
        // Store previous statistics
        if (currentStatistics != null) {
            statisticsHistory.offer(currentStatistics.copy());
            
            // Limit history size
            while (statisticsHistory.size() > maxHistorySize) {
                statisticsHistory.poll();
            }
            
            // Check if statistics are significant
            if (isSignificantUpdate(statistics)) {
                significantStatistics.add(statistics.copy());
                
                // Limit significant statistics size
                while (significantStatistics.size() > maxSignificantSize) {
                    significantStatistics.remove(0);
                }
            }
        }
        
        // Update current statistics
        currentStatistics = statistics.copy();
        
        // Set baseline if not set
        if (baselineStatistics == null) {
            baselineStatistics = statistics.copy();
        }
        
        // Update trends
        updateTrends(statistics);
    }
    
    /**
     * Check if statistics update is significant
     */
    private boolean isSignificantUpdate(PlayerStatistics statistics) {
        if (currentStatistics == null) {
            return true;
        }
        
        // Check for significant changes in key metrics
        float accuracyChange = Math.abs(statistics.getAverageAccuracy() - currentStatistics.getAverageAccuracy());
        float kdChange = Math.abs(statistics.getKillDeathRatio() - currentStatistics.getKillDeathRatio());
        float headshotChange = Math.abs(statistics.getHeadshotPercentage() - currentStatistics.getHeadshotPercentage());
        
        return accuracyChange > 0.1f || kdChange > 1.0f || headshotChange > 0.1f;
    }
    
    /**
     * Update statistical trends
     */
    private void updateTrends(PlayerStatistics statistics) {
        trends.addDataPoint("accuracy", statistics.getAverageAccuracy());
        trends.addDataPoint("killDeathRatio", statistics.getKillDeathRatio());
        trends.addDataPoint("headshotPercentage", statistics.getHeadshotPercentage());
        trends.addDataPoint("winRate", statistics.getWinRate());
        trends.addDataPoint("killsPerRound", statistics.getKillsPerRound());
    }
    
    /**
     * Increase anomaly level
     */
    public synchronized void increaseAnomalyLevel(float amount) {
        anomalyLevel = Math.min(1.0f, anomalyLevel + amount);
    }
    
    /**
     * Decrease anomaly level
     */
    public synchronized void decreaseAnomalyLevel(float amount) {
        anomalyLevel = Math.max(0.0f, anomalyLevel - amount);
    }
    
    /**
     * Increment violation count
     */
    public synchronized void incrementViolationCount() {
        violationCount++;
    }
    
    /**
     * Check if player has impossible accuracy
     */
    public boolean hasImpossibleAccuracy() {
        if (currentStatistics == null) {
            return false;
        }
        
        // Check for impossibly high accuracy with significant sample size
        return currentStatistics.getAverageAccuracy() > 0.95f && 
               currentStatistics.getTotalShots() > 500 &&
               currentStatistics.getStatisticalSignificance() > 0.7f;
    }
    
    /**
     * Check if player has impossible headshot ratio
     */
    public boolean hasImpossibleHeadshotRatio() {
        if (currentStatistics == null) {
            return false;
        }
        
        // Check for impossibly high headshot ratio with significant sample size
        return currentStatistics.getHeadshotPercentage() > 0.8f && 
               currentStatistics.getShotsHit() > 200 &&
               currentStatistics.getStatisticalSignificance() > 0.7f;
    }
    
    /**
     * Check if player has superhuman performance
     */
    public boolean hasSuperhumanPerformance() {
        if (currentStatistics == null) {
            return false;
        }
        
        // Check for combination of impossible metrics
        boolean highAccuracy = currentStatistics.getAverageAccuracy() > 0.9f;
        boolean highHeadshots = currentStatistics.getHeadshotPercentage() > 0.7f;
        boolean highKD = currentStatistics.getKillDeathRatio() > 15.0f;
        boolean highWinRate = currentStatistics.getWinRate() > 0.9f;
        
        int impossibleMetrics = 0;
        if (highAccuracy) impossibleMetrics++;
        if (highHeadshots) impossibleMetrics++;
        if (highKD) impossibleMetrics++;
        if (highWinRate) impossibleMetrics++;
        
        // If 3 or more metrics are at impossible levels
        return impossibleMetrics >= 3 && currentStatistics.getStatisticalSignificance() > 0.5f;
    }
    
    /**
     * Get statistical consistency score (0-1, higher is more consistent)
     */
    public float getStatisticalConsistency() {
        if (statisticsHistory.size() < 5) {
            return 0.5f; // Not enough data
        }
        
        // Calculate variance in key metrics
        float accuracyVariance = trends.getVariance("accuracy");
        float kdVariance = trends.getVariance("killDeathRatio");
        float headshotVariance = trends.getVariance("headshotPercentage");
        
        // Lower variance = higher consistency
        float avgVariance = (accuracyVariance + kdVariance + headshotVariance) / 3.0f;
        
        return Math.max(0.0f, 1.0f - avgVariance);
    }
    
    /**
     * Get improvement rate (positive = improving, negative = declining)
     */
    public float getImprovementRate() {
        if (statisticsHistory.size() < 10) {
            return 0.0f; // Not enough data
        }
        
        // Calculate trend slopes for key metrics
        float accuracyTrend = trends.getTrendSlope("accuracy");
        float kdTrend = trends.getTrendSlope("killDeathRatio");
        float headshotTrend = trends.getTrendSlope("headshotPercentage");
        
        return (accuracyTrend + kdTrend + headshotTrend) / 3.0f;
    }
    
    /**
     * Get statistical anomaly probability (0-1, higher is more anomalous)
     */
    public float getAnomalyProbability() {
        if (currentStatistics == null) {
            return 0.0f;
        }
        
        float probability = 0.0f;
        
        // Check accuracy anomaly
        if (currentStatistics.getAverageAccuracy() > 0.8f) {
            probability += (currentStatistics.getAverageAccuracy() - 0.8f) * 2.0f;
        }
        
        // Check headshot anomaly
        if (currentStatistics.getHeadshotPercentage() > 0.6f) {
            probability += (currentStatistics.getHeadshotPercentage() - 0.6f) * 1.5f;
        }
        
        // Check K/D anomaly
        if (currentStatistics.getKillDeathRatio() > 10.0f) {
            probability += Math.min(0.5f, (currentStatistics.getKillDeathRatio() - 10.0f) / 20.0f);
        }
        
        // Adjust by statistical significance
        probability *= currentStatistics.getStatisticalSignificance();
        
        return Math.min(1.0f, probability);
    }
    
    /**
     * Get profile age in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - creationTime;
    }
    
    /**
     * Get time since last update in milliseconds
     */
    public long getTimeSinceLastUpdate() {
        return System.currentTimeMillis() - lastUpdateTime;
    }
    
    // Getters and setters
    public String getPlayerId() { return playerId; }
    public long getCreationTime() { return creationTime; }
    public long getLastUpdateTime() { return lastUpdateTime; }
    public Queue<PlayerStatistics> getStatisticsHistory() { return new ConcurrentLinkedQueue<>(statisticsHistory); }
    public List<PlayerStatistics> getSignificantStatistics() { return new ArrayList<>(significantStatistics); }
    public float getAnomalyLevel() { return anomalyLevel; }
    public void setAnomalyLevel(float anomalyLevel) { this.anomalyLevel = Math.max(0.0f, Math.min(1.0f, anomalyLevel)); }
    public int getViolationCount() { return violationCount; }
    public void setViolationCount(int violationCount) { this.violationCount = violationCount; }
    public long getTotalUpdates() { return totalUpdates; }
    public PlayerStatistics getCurrentStatistics() { return currentStatistics != null ? currentStatistics.copy() : null; }
    public PlayerStatistics getBaselineStatistics() { return baselineStatistics != null ? baselineStatistics.copy() : null; }
    public StatisticalTrends getTrends() { return trends; }
    public int getMaxHistorySize() { return maxHistorySize; }
    public void setMaxHistorySize(int maxHistorySize) { this.maxHistorySize = maxHistorySize; }
    public int getMaxSignificantSize() { return maxSignificantSize; }
    public void setMaxSignificantSize(int maxSignificantSize) { this.maxSignificantSize = maxSignificantSize; }
    
    /**
     * Statistical trends tracking
     */
    public static class StatisticalTrends {
        private final java.util.Map<String, Queue<Float>> trendData = new java.util.concurrent.ConcurrentHashMap<>();
        private final int maxTrendSize = 50;
        
        public void addDataPoint(String metric, float value) {
            Queue<Float> data = trendData.computeIfAbsent(metric, k -> new ConcurrentLinkedQueue<>());
            data.offer(value);
            
            // Limit size
            while (data.size() > maxTrendSize) {
                data.poll();
            }
        }
        
        public float getVariance(String metric) {
            Queue<Float> data = trendData.get(metric);
            if (data == null || data.size() < 2) {
                return 0.5f;
            }
            
            Float[] values = data.toArray(new Float[0]);
            float mean = 0;
            for (float value : values) {
                mean += value;
            }
            mean /= values.length;
            
            float variance = 0;
            for (float value : values) {
                variance += (value - mean) * (value - mean);
            }
            variance /= values.length;
            
            return variance;
        }
        
        public float getTrendSlope(String metric) {
            Queue<Float> data = trendData.get(metric);
            if (data == null || data.size() < 5) {
                return 0.0f;
            }
            
            Float[] values = data.toArray(new Float[0]);
            
            // Simple linear regression slope calculation
            float n = values.length;
            float sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            
            for (int i = 0; i < values.length; i++) {
                float x = i;
                float y = values[i];
                sumX += x;
                sumY += y;
                sumXY += x * y;
                sumX2 += x * x;
            }
            
            float slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
            return slope;
        }
        
        public Queue<Float> getTrendData(String metric) {
            return trendData.get(metric);
        }
    }
}