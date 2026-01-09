package fps.anticheat.behavioral;

import fps.anticheat.ViolationType;

/**
 * Represents a behavioral violation detected by the behavioral analysis engine.
 * Contains violation details, player information, and behavioral evidence.
 */
public class BehavioralViolation {
    
    private final ViolationType violationType;
    private final String description;
    private final String playerId;
    private final float confidence;
    private final String detectionMethod;
    private final long timestamp;
    
    private String evidence;
    private String additionalInfo;
    private int severity;
    private PlayerBehaviorProfile playerProfile;
    private BehaviorEvent triggeringEvent;
    
    /**
     * Create a behavioral violation
     */
    public BehavioralViolation(ViolationType violationType, String description, 
                              String playerId, float confidence, String detectionMethod) {
        this.violationType = violationType;
        this.description = description;
        this.playerId = playerId;
        this.confidence = Math.max(0.0f, Math.min(1.0f, confidence)); // Clamp to [0,1]
        this.detectionMethod = detectionMethod;
        this.timestamp = System.currentTimeMillis();
        this.severity = calculateSeverity();
        this.evidence = generateEvidence();
    }
    
    /**
     * Create a behavioral violation with player profile
     */
    public BehavioralViolation(ViolationType violationType, String description, 
                              String playerId, float confidence, String detectionMethod,
                              PlayerBehaviorProfile playerProfile) {
        this(violationType, description, playerId, confidence, detectionMethod);
        this.playerProfile = playerProfile;
        this.evidence = generateEvidence(); // Regenerate with profile info
    }
    
    /**
     * Calculate violation severity based on type and context
     */
    private int calculateSeverity() {
        int baseSeverity;
        
        switch (violationType) {
            case INHUMAN_REACTIONS:
                baseSeverity = 9; // Critical - inhuman performance
                break;
            case PERFECT_TRACKING:
                baseSeverity = 8; // High - perfect aim tracking
                break;
            case ROBOTIC_MOVEMENT:
                baseSeverity = 7; // High - robotic patterns
                break;
            case BEHAVIORAL_ANALYSIS:
                baseSeverity = 6; // Medium-High - general behavioral issues
                break;
            case SUSPICIOUS_PATTERNS:
                baseSeverity = 5; // Medium - suspicious behavior
                break;
            default:
                baseSeverity = 4; // Medium-Low
                break;
        }
        
        // Adjust severity based on confidence
        float adjustedSeverity = baseSeverity * confidence;
        
        // Adjust based on player profile if available
        if (playerProfile != null) {
            float suspicionLevel = playerProfile.getSuspicionLevel();
            if (suspicionLevel > 0.8f) {
                adjustedSeverity *= 1.2f; // Increase severity for highly suspicious players
            }
            
            int violationCount = playerProfile.getViolationCount();
            if (violationCount > 5) {
                adjustedSeverity *= 1.1f; // Increase severity for repeat offenders
            }
        }
        
        return Math.min(10, Math.round(adjustedSeverity));
    }
    
    /**
     * Generate evidence string for this violation
     */
    private String generateEvidence() {
        StringBuilder evidenceBuilder = new StringBuilder();
        
        evidenceBuilder.append("Behavioral Violation Evidence:\n");
        evidenceBuilder.append("Detection Method: ").append(detectionMethod).append("\n");
        evidenceBuilder.append("Timestamp: ").append(new java.util.Date(timestamp)).append("\n");
        evidenceBuilder.append("Confidence: ").append(String.format("%.2f", confidence)).append("\n");
        evidenceBuilder.append("Severity: ").append(severity).append("/10\n");
        
        evidenceBuilder.append("\nPlayer Information:\n");
        evidenceBuilder.append("Player ID: ").append(playerId).append("\n");
        
        if (playerProfile != null) {
            evidenceBuilder.append("Profile Age: ").append(playerProfile.getAge()).append(" ms\n");
            evidenceBuilder.append("Total Actions: ").append(playerProfile.getTotalActions()).append("\n");
            evidenceBuilder.append("Suspicion Level: ").append(String.format("%.2f", playerProfile.getSuspicionLevel())).append("\n");
            evidenceBuilder.append("Violation Count: ").append(playerProfile.getViolationCount()).append("\n");
            
            // Add behavioral metrics
            evidenceBuilder.append("\nBehavioral Metrics:\n");
            PlayerBehaviorProfile.AimBehaviorMetrics aimMetrics = playerProfile.getAimMetrics();
            evidenceBuilder.append("Aim Smoothness: ").append(String.format("%.3f", aimMetrics.averageSmoothness)).append("\n");
            evidenceBuilder.append("Total Shots: ").append(aimMetrics.totalShots).append("\n");
            evidenceBuilder.append("Flick Shots: ").append(aimMetrics.flickShots).append("\n");
            
            PlayerBehaviorProfile.MovementBehaviorMetrics movementMetrics = playerProfile.getMovementMetrics();
            evidenceBuilder.append("Movement Consistency: ").append(String.format("%.3f", movementMetrics.averageConsistency)).append("\n");
            
            PlayerBehaviorProfile.ReactionTimeBehaviorMetrics reactionMetrics = playerProfile.getReactionMetrics();
            evidenceBuilder.append("Average Reaction Time: ").append(reactionMetrics.averageReactionTime).append(" ms\n");
            
            // Add statistical metrics
            evidenceBuilder.append("\nStatistical Metrics:\n");
            for (java.util.Map.Entry<String, Double> entry : playerProfile.getStatisticalMetrics().entrySet()) {
                evidenceBuilder.append(entry.getKey()).append(": ").append(String.format("%.3f", entry.getValue())).append("\n");
            }
        }
        
        if (triggeringEvent != null) {
            evidenceBuilder.append("\nTriggering Event:\n");
            evidenceBuilder.append("Event Type: ").append(triggeringEvent.getEventType()).append("\n");
            evidenceBuilder.append("Event Significance: ").append(String.format("%.2f", triggeringEvent.getSignificance())).append("\n");
            evidenceBuilder.append("Event Age: ").append(triggeringEvent.getAge()).append(" ms\n");
        }
        
        if (additionalInfo != null && !additionalInfo.isEmpty()) {
            evidenceBuilder.append("\nAdditional Information:\n");
            evidenceBuilder.append(additionalInfo).append("\n");
        }
        
        return evidenceBuilder.toString();
    }
    
    /**
     * Check if this violation is critical (requires immediate action)
     */
    public boolean isCritical() {
        return severity >= 9 || confidence >= 0.9f || 
               violationType == ViolationType.INHUMAN_REACTIONS ||
               (playerProfile != null && playerProfile.getSuspicionLevel() >= 0.9f);
    }
    
    /**
     * Check if this violation is high priority
     */
    public boolean isHighPriority() {
        return severity >= 7 || confidence >= 0.8f ||
               violationType == ViolationType.PERFECT_TRACKING ||
               violationType == ViolationType.ROBOTIC_MOVEMENT;
    }
    
    /**
     * Get recommended action for this violation
     */
    public String getRecommendedAction() {
        if (isCritical()) {
            return "IMMEDIATE_BAN";
        } else if (isHighPriority()) {
            return "TEMPORARY_BAN";
        } else if (severity >= 5) {
            return "WARNING";
        } else {
            return "MONITOR";
        }
    }
    
    /**
     * Get violation risk score (0-100)
     */
    public int getRiskScore() {
        int baseScore = Math.round(severity * confidence * 10);
        
        // Adjust based on player profile
        if (playerProfile != null) {
            float suspicionLevel = playerProfile.getSuspicionLevel();
            baseScore += Math.round(suspicionLevel * 20); // Add up to 20 points for suspicion
            
            int violationCount = playerProfile.getViolationCount();
            baseScore += Math.min(15, violationCount * 3); // Add up to 15 points for repeat violations
            
            // Check for inhuman performance indicators
            if (playerProfile.hasConsistentInhumanPerformance()) {
                baseScore += 25;
            }
            if (playerProfile.hasRoboticMovementPatterns()) {
                baseScore += 15;
            }
            if (playerProfile.hasPerfectTrackingPatterns()) {
                baseScore += 20;
            }
        }
        
        return Math.min(100, baseScore);
    }
    
    /**
     * Check if this violation should trigger an alert
     */
    public boolean shouldAlert() {
        return isCritical() || (isHighPriority() && confidence >= 0.8f) ||
               (playerProfile != null && playerProfile.getSuspicionLevel() >= 0.8f);
    }
    
    /**
     * Get violation category for reporting
     */
    public String getCategory() {
        switch (violationType) {
            case INHUMAN_REACTIONS:
                return "INHUMAN_PERFORMANCE";
            case PERFECT_TRACKING:
                return "AIM_VIOLATION";
            case ROBOTIC_MOVEMENT:
                return "MOVEMENT_VIOLATION";
            case BEHAVIORAL_ANALYSIS:
                return "BEHAVIORAL_VIOLATION";
            case SUSPICIOUS_PATTERNS:
                return "PATTERN_VIOLATION";
            default:
                return "BEHAVIORAL_VIOLATION";
        }
    }
    
    /**
     * Get behavioral pattern type
     */
    public String getBehavioralPatternType() {
        if (violationType == ViolationType.INHUMAN_REACTIONS) {
            return "REACTION_TIME_ANOMALY";
        } else if (violationType == ViolationType.PERFECT_TRACKING) {
            return "AIM_TRACKING_ANOMALY";
        } else if (violationType == ViolationType.ROBOTIC_MOVEMENT) {
            return "MOVEMENT_PATTERN_ANOMALY";
        } else {
            return "GENERAL_BEHAVIORAL_ANOMALY";
        }
    }
    
    /**
     * Create a summary of this violation
     */
    public String getSummary() {
        return String.format("[%s] %s - Player: %s (Confidence: %.2f, Severity: %d/10, Risk: %d/100)",
                           violationType.name(), description, playerId, confidence, severity, getRiskScore());
    }
    
    /**
     * Create a detailed report of this violation
     */
    public String getDetailedReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== BEHAVIORAL VIOLATION REPORT ===\n");
        report.append("Violation Type: ").append(violationType.name()).append("\n");
        report.append("Description: ").append(description).append("\n");
        report.append("Category: ").append(getCategory()).append("\n");
        report.append("Pattern Type: ").append(getBehavioralPatternType()).append("\n");
        report.append("Player ID: ").append(playerId).append("\n");
        report.append("Confidence: ").append(String.format("%.2f", confidence)).append("\n");
        report.append("Severity: ").append(severity).append("/10\n");
        report.append("Risk Score: ").append(getRiskScore()).append("/100\n");
        report.append("Detection Method: ").append(detectionMethod).append("\n");
        report.append("Timestamp: ").append(new java.util.Date(timestamp)).append("\n");
        report.append("Recommended Action: ").append(getRecommendedAction()).append("\n");
        report.append("Should Alert: ").append(shouldAlert()).append("\n");
        
        if (playerProfile != null) {
            report.append("\n=== PLAYER BEHAVIORAL PROFILE ===\n");
            report.append("Profile Age: ").append(playerProfile.getAge()).append(" ms\n");
            report.append("Total Actions: ").append(playerProfile.getTotalActions()).append("\n");
            report.append("Suspicion Level: ").append(String.format("%.2f", playerProfile.getSuspicionLevel())).append("\n");
            report.append("Violation Count: ").append(playerProfile.getViolationCount()).append("\n");
            report.append("Has Inhuman Performance: ").append(playerProfile.hasConsistentInhumanPerformance()).append("\n");
            report.append("Has Robotic Movement: ").append(playerProfile.hasRoboticMovementPatterns()).append("\n");
            report.append("Has Perfect Tracking: ").append(playerProfile.hasPerfectTrackingPatterns()).append("\n");
        }
        
        if (evidence != null && !evidence.isEmpty()) {
            report.append("\n=== EVIDENCE ===\n");
            report.append(evidence);
        }
        
        return report.toString();
    }
    
    /**
     * Compare violations by risk score and severity
     */
    public int compareTo(BehavioralViolation other) {
        if (other == null) return 1;
        
        // First compare by risk score
        int riskComparison = Integer.compare(other.getRiskScore(), this.getRiskScore());
        if (riskComparison != 0) {
            return riskComparison;
        }
        
        // Then by severity
        int severityComparison = Integer.compare(other.severity, this.severity);
        if (severityComparison != 0) {
            return severityComparison;
        }
        
        // Then by confidence
        int confidenceComparison = Float.compare(other.confidence, this.confidence);
        if (confidenceComparison != 0) {
            return confidenceComparison;
        }
        
        // Finally by timestamp (newer first)
        return Long.compare(other.timestamp, this.timestamp);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        BehavioralViolation that = (BehavioralViolation) obj;
        return violationType == that.violationType &&
               Float.compare(that.confidence, confidence) == 0 &&
               timestamp == that.timestamp &&
               description.equals(that.description) &&
               playerId.equals(that.playerId) &&
               detectionMethod.equals(that.detectionMethod);
    }
    
    @Override
    public int hashCode() {
        int result = violationType.hashCode();
        result = 31 * result + description.hashCode();
        result = 31 * result + playerId.hashCode();
        result = 31 * result + Float.hashCode(confidence);
        result = 31 * result + detectionMethod.hashCode();
        result = 31 * result + Long.hashCode(timestamp);
        return result;
    }
    
    @Override
    public String toString() {
        return getSummary();
    }
    
    // Getters and setters
    public ViolationType getViolationType() { return violationType; }
    public String getDescription() { return description; }
    public String getPlayerId() { return playerId; }
    public float getConfidence() { return confidence; }
    public String getDetectionMethod() { return detectionMethod; }
    public long getTimestamp() { return timestamp; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getAdditionalInfo() { return additionalInfo; }
    public void setAdditionalInfo(String additionalInfo) { this.additionalInfo = additionalInfo; }
    public int getSeverity() { return severity; }
    public void setSeverity(int severity) { this.severity = Math.max(0, Math.min(10, severity)); }
    public PlayerBehaviorProfile getPlayerProfile() { return playerProfile; }
    public void setPlayerProfile(PlayerBehaviorProfile playerProfile) { this.playerProfile = playerProfile; }
    public BehaviorEvent getTriggeringEvent() { return triggeringEvent; }
    public void setTriggeringEvent(BehaviorEvent triggeringEvent) { this.triggeringEvent = triggeringEvent; }
}