package fps.map.data;

import java.time.LocalDateTime;

/**
 * Version information for maps supporting semantic versioning and update tracking.
 */
public class MapVersion implements Comparable<MapVersion> {
    
    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final String buildMetadata;
    private final LocalDateTime versionDate;
    
    public MapVersion(int major, int minor, int patch) {
        this(major, minor, patch, null, null, LocalDateTime.now());
    }
    
    public MapVersion(int major, int minor, int patch, String preRelease) {
        this(major, minor, patch, preRelease, null, LocalDateTime.now());
    }
    
    public MapVersion(int major, int minor, int patch, String preRelease, String buildMetadata, LocalDateTime versionDate) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version numbers cannot be negative");
        }
        
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = preRelease;
        this.buildMetadata = buildMetadata;
        this.versionDate = versionDate != null ? versionDate : LocalDateTime.now();
    }
    
    // Getters
    public int getMajor() { return major; }
    public int getMinor() { return minor; }
    public int getPatch() { return patch; }
    public String getPreRelease() { return preRelease; }
    public String getBuildMetadata() { return buildMetadata; }
    public LocalDateTime getVersionDate() { return versionDate; }
    
    /**
     * Check if this is a pre-release version
     */
    public boolean isPreRelease() {
        return preRelease != null && !preRelease.isEmpty();
    }
    
    /**
     * Check if this version is compatible with another version
     */
    public boolean isCompatibleWith(MapVersion other) {
        // Same major version is considered compatible
        return this.major == other.major;
    }
    
    /**
     * Check if this version is newer than another version
     */
    public boolean isNewerThan(MapVersion other) {
        return this.compareTo(other) > 0;
    }
    
    /**
     * Check if this version is older than another version
     */
    public boolean isOlderThan(MapVersion other) {
        return this.compareTo(other) < 0;
    }
    
    /**
     * Get the next patch version
     */
    public MapVersion nextPatch() {
        return new MapVersion(major, minor, patch + 1);
    }
    
    /**
     * Get the next minor version
     */
    public MapVersion nextMinor() {
        return new MapVersion(major, minor + 1, 0);
    }
    
    /**
     * Get the next major version
     */
    public MapVersion nextMajor() {
        return new MapVersion(major + 1, 0, 0);
    }
    
    /**
     * Parse version string in format "major.minor.patch[-prerelease][+build]"
     */
    public static MapVersion parse(String versionString) {
        if (versionString == null || versionString.isEmpty()) {
            throw new IllegalArgumentException("Version string cannot be null or empty");
        }
        
        try {
            // Split by + to separate build metadata
            String[] buildParts = versionString.split("\\+", 2);
            String versionPart = buildParts[0];
            String buildMetadata = buildParts.length > 1 ? buildParts[1] : null;
            
            // Split by - to separate pre-release
            String[] preReleaseParts = versionPart.split("-", 2);
            String corePart = preReleaseParts[0];
            String preRelease = preReleaseParts.length > 1 ? preReleaseParts[1] : null;
            
            // Split core version numbers
            String[] versionNumbers = corePart.split("\\.");
            if (versionNumbers.length != 3) {
                throw new IllegalArgumentException("Version must have exactly 3 numbers: major.minor.patch");
            }
            
            int major = Integer.parseInt(versionNumbers[0]);
            int minor = Integer.parseInt(versionNumbers[1]);
            int patch = Integer.parseInt(versionNumbers[2]);
            
            return new MapVersion(major, minor, patch, preRelease, buildMetadata, LocalDateTime.now());
            
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid version format: " + versionString, e);
        }
    }
    
    @Override
    public int compareTo(MapVersion other) {
        if (other == null) {
            return 1;
        }
        
        // Compare major version
        int result = Integer.compare(this.major, other.major);
        if (result != 0) {
            return result;
        }
        
        // Compare minor version
        result = Integer.compare(this.minor, other.minor);
        if (result != 0) {
            return result;
        }
        
        // Compare patch version
        result = Integer.compare(this.patch, other.patch);
        if (result != 0) {
            return result;
        }
        
        // Compare pre-release versions
        if (this.preRelease == null && other.preRelease == null) {
            return 0;
        }
        if (this.preRelease == null) {
            return 1; // Release version is higher than pre-release
        }
        if (other.preRelease == null) {
            return -1; // Pre-release is lower than release
        }
        
        return this.preRelease.compareTo(other.preRelease);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        MapVersion that = (MapVersion) obj;
        return major == that.major &&
               minor == that.minor &&
               patch == that.patch &&
               java.util.Objects.equals(preRelease, that.preRelease);
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(major, minor, patch, preRelease);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(major).append('.').append(minor).append('.').append(patch);
        
        if (preRelease != null && !preRelease.isEmpty()) {
            sb.append('-').append(preRelease);
        }
        
        if (buildMetadata != null && !buildMetadata.isEmpty()) {
            sb.append('+').append(buildMetadata);
        }
        
        return sb.toString();
    }
    
    /**
     * Get a detailed string representation including date
     */
    public String toDetailedString() {
        return String.format("%s (created: %s)", toString(), versionDate.toString());
    }
}