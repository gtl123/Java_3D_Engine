package fps.anticheat.integration;

import java.util.List;
import java.util.ArrayList;

/**
 * Result of compatibility checking between anti-cheat and game systems.
 */
public class IntegrationCompatibilityResult {
    
    private final boolean compatible;
    private final List<String> issues;
    private final long timestamp;
    
    public IntegrationCompatibilityResult(boolean compatible, List<String> issues) {
        this.compatible = compatible;
        this.issues = new ArrayList<>(issues);
        this.timestamp = System.currentTimeMillis();
    }
    
    public boolean isCompatible() {
        return compatible;
    }
    
    public List<String> getIssues() {
        return new ArrayList<>(issues);
    }
    
    public String getIssuesAsString() {
        return String.join("; ", issues);
    }
    
    public int getIssueCount() {
        return issues.size();
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return String.format("IntegrationCompatibilityResult{compatible=%s, issues=%d, timestamp=%d}", 
                           compatible, issues.size(), timestamp);
    }
}