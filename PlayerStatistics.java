package fps.anticheat;

/**
 * Represents player performance statistics for analysis.
 * Contains accuracy, kill/death ratios, headshot percentages, and other performance metrics.
 */
public class PlayerStatistics {
    
    private final String playerId;
    private final long timestamp;
    
    // Combat statistics
    private int totalShots = 0;
    private int shotsHit = 0;
    private int totalKills = 0;
    private int totalDeaths = 0;
    private int headshots = 0;
    private int bodyshots = 0;
    private int limbshots = 0;
    
    // Weapon statistics
    private int primaryWeaponKills = 0;
    private int secondaryWeaponKills = 0;
    private int meleeKills = 0;
    private int grenadeKills = 0;
    
    // Match statistics
    private int matchesPlayed = 0;
    private int matchesWon = 0;
    private long totalPlayTime = 0; // milliseconds
    private int totalScore = 0;
    
    // Performance metrics
    private float averageReactionTime = 0.0f;
    private float averageAccuracy = 0.0f;
    private float killDeathRatio = 0.0f;
    private float headshotPercentage = 0.0f;
    private float winRate = 0.0f;
    
    // Advanced statistics
    private int multiKills = 0;
    private int killStreaks = 0;
    private int longestKillStreak = 0;
    private float damagePerRound = 0.0f;
    private float killsPerRound = 0.0f;
    
    public PlayerStatistics(String playerId) {
        this.playerId = playerId;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Create player statistics with basic combat data
     */
    public PlayerStatistics(String playerId, int totalShots, int shotsHit, int totalKills, 
                           int totalDeaths, int headshots) {
        this.playerId = playerId;
        this.timestamp = System.currentTimeMillis();
        this.totalShots = totalShots;
        this.shotsHit = shotsHit;
        this.totalKills = totalKills;
        this.totalDeaths = totalDeaths;
        this.headshots = headshots;
        
        // Calculate derived metrics
        calculateDerivedMetrics();
    }
    
    /**
     * Calculate derived performance metrics
     */
    public void calculateDerivedMetrics() {
        // Calculate accuracy
        if (totalShots > 0) {
            averageAccuracy = (float) shotsHit / totalShots;
        }
        
        // Calculate K/D ratio
        if (totalDeaths > 0) {
            killDeathRatio = (float) totalKills / totalDeaths;
        } else if (totalKills > 0) {
            killDeathRatio = totalKills; // No deaths, use kill count
        }
        
        // Calculate headshot percentage
        if (shotsHit > 0) {
            headshotPercentage = (float) headshots / shotsHit;
        }
        
        // Calculate win rate
        if (matchesPlayed > 0) {
            winRate = (float) matchesWon / matchesPlayed;
        }
        
        // Calculate damage per round (estimated)
        if (matchesPlayed > 0) {
            damagePerRound = (float) (shotsHit * 35) / matchesPlayed; // Assume 35 damage per hit
        }
        
        // Calculate kills per round
        if (matchesPlayed > 0) {
            killsPerRound = (float) totalKills / matchesPlayed;
        }
    }
    
    /**
     * Add shot statistics
     */
    public void addShot(boolean hit, boolean headshot, boolean bodyshot, boolean limbshot) {
        totalShots++;
        
        if (hit) {
            shotsHit++;
            
            if (headshot) {
                headshots++;
            } else if (bodyshot) {
                bodyshots++;
            } else if (limbshot) {
                limbshots++;
            }
        }
        
        calculateDerivedMetrics();
    }
    
    /**
     * Add kill statistics
     */
    public void addKill(boolean isHeadshot, String weaponType) {
        totalKills++;
        
        if (isHeadshot) {
            headshots++;
        }
        
        // Track weapon-specific kills
        switch (weaponType.toLowerCase()) {
            case "primary":
                primaryWeaponKills++;
                break;
            case "secondary":
                secondaryWeaponKills++;
                break;
            case "melee":
                meleeKills++;
                break;
            case "grenade":
                grenadeKills++;
                break;
        }
        
        calculateDerivedMetrics();
    }
    
    /**
     * Add death
     */
    public void addDeath() {
        totalDeaths++;
        calculateDerivedMetrics();
    }
    
    /**
     * Add match result
     */
    public void addMatch(boolean won, long playTime, int score) {
        matchesPlayed++;
        if (won) {
            matchesWon++;
        }
        totalPlayTime += playTime;
        totalScore += score;
        
        calculateDerivedMetrics();
    }
    
    /**
     * Check if statistics are suspicious
     */
    public boolean isSuspicious() {
        // Check for impossible accuracy
        if (averageAccuracy > 0.95f && totalShots > 100) {
            return true;
        }
        
        // Check for impossible headshot ratio
        if (headshotPercentage > 0.8f && shotsHit > 50) {
            return true;
        }
        
        // Check for impossible K/D ratio
        if (killDeathRatio > 20.0f && totalKills > 50) {
            return true;
        }
        
        // Check for impossible win rate
        if (winRate > 0.95f && matchesPlayed > 20) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get statistical significance level
     */
    public float getStatisticalSignificance() {
        float significance = 0.0f;
        
        // Higher sample sizes increase significance
        if (totalShots > 1000) significance += 0.3f;
        else if (totalShots > 500) significance += 0.2f;
        else if (totalShots > 100) significance += 0.1f;
        
        if (totalKills > 500) significance += 0.3f;
        else if (totalKills > 100) significance += 0.2f;
        else if (totalKills > 50) significance += 0.1f;
        
        if (matchesPlayed > 100) significance += 0.2f;
        else if (matchesPlayed > 50) significance += 0.1f;
        
        return Math.min(1.0f, significance);
    }
    
    /**
     * Create a copy of these statistics
     */
    public PlayerStatistics copy() {
        PlayerStatistics copy = new PlayerStatistics(playerId);
        
        copy.totalShots = this.totalShots;
        copy.shotsHit = this.shotsHit;
        copy.totalKills = this.totalKills;
        copy.totalDeaths = this.totalDeaths;
        copy.headshots = this.headshots;
        copy.bodyshots = this.bodyshots;
        copy.limbshots = this.limbshots;
        
        copy.primaryWeaponKills = this.primaryWeaponKills;
        copy.secondaryWeaponKills = this.secondaryWeaponKills;
        copy.meleeKills = this.meleeKills;
        copy.grenadeKills = this.grenadeKills;
        
        copy.matchesPlayed = this.matchesPlayed;
        copy.matchesWon = this.matchesWon;
        copy.totalPlayTime = this.totalPlayTime;
        copy.totalScore = this.totalScore;
        
        copy.averageReactionTime = this.averageReactionTime;
        copy.multiKills = this.multiKills;
        copy.killStreaks = this.killStreaks;
        copy.longestKillStreak = this.longestKillStreak;
        copy.damagePerRound = this.damagePerRound;
        
        copy.calculateDerivedMetrics();
        
        return copy;
    }
    
    @Override
    public String toString() {
        return String.format("PlayerStatistics{player=%s, accuracy=%.2f%%, K/D=%.2f, headshot=%.1f%%, matches=%d, suspicious=%s}",
                           playerId, averageAccuracy * 100, killDeathRatio, headshotPercentage * 100, 
                           matchesPlayed, isSuspicious());
    }
    
    // Getters and setters
    public String getPlayerId() { return playerId; }
    public long getTimestamp() { return timestamp; }
    public int getTotalShots() { return totalShots; }
    public void setTotalShots(int totalShots) { this.totalShots = totalShots; }
    public int getShotsHit() { return shotsHit; }
    public void setShotsHit(int shotsHit) { this.shotsHit = shotsHit; }
    public int getTotalKills() { return totalKills; }
    public void setTotalKills(int totalKills) { this.totalKills = totalKills; }
    public int getTotalDeaths() { return totalDeaths; }
    public void setTotalDeaths(int totalDeaths) { this.totalDeaths = totalDeaths; }
    public int getHeadshots() { return headshots; }
    public void setHeadshots(int headshots) { this.headshots = headshots; }
    public int getBodyshots() { return bodyshots; }
    public void setBodyshots(int bodyshots) { this.bodyshots = bodyshots; }
    public int getLimbshots() { return limbshots; }
    public void setLimbshots(int limbshots) { this.limbshots = limbshots; }
    public int getPrimaryWeaponKills() { return primaryWeaponKills; }
    public void setPrimaryWeaponKills(int primaryWeaponKills) { this.primaryWeaponKills = primaryWeaponKills; }
    public int getSecondaryWeaponKills() { return secondaryWeaponKills; }
    public void setSecondaryWeaponKills(int secondaryWeaponKills) { this.secondaryWeaponKills = secondaryWeaponKills; }
    public int getMeleeKills() { return meleeKills; }
    public void setMeleeKills(int meleeKills) { this.meleeKills = meleeKills; }
    public int getGrenadeKills() { return grenadeKills; }
    public void setGrenadeKills(int grenadeKills) { this.grenadeKills = grenadeKills; }
    public int getMatchesPlayed() { return matchesPlayed; }
    public void setMatchesPlayed(int matchesPlayed) { this.matchesPlayed = matchesPlayed; }
    public int getMatchesWon() { return matchesWon; }
    public void setMatchesWon(int matchesWon) { this.matchesWon = matchesWon; }
    public long getTotalPlayTime() { return totalPlayTime; }
    public void setTotalPlayTime(long totalPlayTime) { this.totalPlayTime = totalPlayTime; }
    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }
    public float getAverageReactionTime() { return averageReactionTime; }
    public void setAverageReactionTime(float averageReactionTime) { this.averageReactionTime = averageReactionTime; }
    public float getAverageAccuracy() { return averageAccuracy; }
    public float getKillDeathRatio() { return killDeathRatio; }
    public float getHeadshotPercentage() { return headshotPercentage; }
    public float getWinRate() { return winRate; }
    public int getMultiKills() { return multiKills; }
    public void setMultiKills(int multiKills) { this.multiKills = multiKills; }
    public int getKillStreaks() { return killStreaks; }
    public void setKillStreaks(int killStreaks) { this.killStreaks = killStreaks; }
    public int getLongestKillStreak() { return longestKillStreak; }
    public void setLongestKillStreak(int longestKillStreak) { this.longestKillStreak = longestKillStreak; }
    public float getDamagePerRound() { return damagePerRound; }
    public void setDamagePerRound(float damagePerRound) { this.damagePerRound = damagePerRound; }
    public float getKillsPerRound() { return killsPerRound; }
    public void setKillsPerRound(float killsPerRound) { this.killsPerRound = killsPerRound; }
}