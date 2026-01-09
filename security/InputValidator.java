package engine.security;

import engine.logging.LogManager;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Centralized input validation and sanitization utilities for security.
 * Provides methods to validate and sanitize user inputs to prevent security vulnerabilities.
 */
public class InputValidator {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    // World name validation - alphanumeric, underscore, hyphen only, max 50 chars
    private static final Pattern WORLD_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,50}$");
    
    // URL validation - only allow specific whitelisted domains
    private static final Pattern SAFE_URL_PATTERN = Pattern.compile("^https://localhost:[0-9]{1,5}/.*$");
    
    // File path validation - prevent directory traversal
    private static final Pattern UNSAFE_PATH_PATTERN = Pattern.compile(".*[./\\\\].*");
    
    /**
     * Validates and sanitizes a world name to prevent path traversal attacks.
     * 
     * @param worldName The world name to validate
     * @return The sanitized world name
     * @throws SecurityException if the world name is invalid or unsafe
     */
    public static String validateWorldName(String worldName) {
        if (worldName == null || worldName.trim().isEmpty()) {
            logManager.warn("InputValidator", "World name validation failed: null or empty",
                           "inputValue", worldName);
            throw new SecurityException("World name cannot be null or empty");
        }
        
        String trimmed = worldName.trim();
        
        // Check length
        if (trimmed.length() > 50) {
            logManager.warn("InputValidator", "World name validation failed: too long",
                           "inputValue", trimmed,
                           "length", trimmed.length(),
                           "maxLength", 50);
            throw new SecurityException("World name too long");
        }
        
        // Check for path traversal attempts
        if (UNSAFE_PATH_PATTERN.matcher(trimmed).matches()) {
            logManager.error("InputValidator", "Security violation: path traversal attempt in world name",
                            "inputValue", trimmed,
                            "violationType", "PATH_TRAVERSAL");
            throw new SecurityException("Invalid characters in world name");
        }
        
        // Validate against allowed pattern
        if (!WORLD_NAME_PATTERN.matcher(trimmed).matches()) {
            logManager.warn("InputValidator", "World name validation failed: invalid characters",
                           "inputValue", trimmed,
                           "allowedPattern", WORLD_NAME_PATTERN.pattern());
            throw new SecurityException("World name contains invalid characters");
        }
        
        logManager.debug("InputValidator", "World name validated successfully",
                        "validatedName", trimmed);
        return trimmed;
    }
    
    /**
     * Validates a URL against a whitelist of safe domains.
     * 
     * @param url The URL to validate
     * @return The validated URL
     * @throws SecurityException if the URL is not whitelisted
     */
    public static String validateUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            logManager.warn("InputValidator", "URL validation failed: null or empty",
                           "inputValue", url);
            throw new SecurityException("URL cannot be null or empty");
        }
        
        String trimmed = url.trim();
        
        // Only allow localhost URLs for security
        if (!SAFE_URL_PATTERN.matcher(trimmed).matches()) {
            logManager.error("InputValidator", "Security violation: URL not in whitelist",
                            "inputValue", trimmed,
                            "violationType", "URL_WHITELIST_VIOLATION",
                            "allowedPattern", SAFE_URL_PATTERN.pattern());
            throw new SecurityException("URL not in whitelist");
        }
        
        logManager.debug("InputValidator", "URL validated successfully",
                        "validatedUrl", trimmed);
        return trimmed;
    }
    
    /**
     * Creates a safe file path by validating the input and preventing directory traversal.
     * 
     * @param baseDir The base directory (must be safe)
     * @param userInput The user-provided path component
     * @return A safe Path object
     * @throws SecurityException if the path would escape the base directory
     */
    public static Path createSafePath(String baseDir, String userInput) {
        String validatedInput = validateWorldName(userInput);
        
        try {
            Path basePath = Paths.get(baseDir).toAbsolutePath().normalize();
            Path requestedPath = basePath.resolve(validatedInput).normalize();
            
            // Ensure the resolved path is still within the base directory
            if (!requestedPath.startsWith(basePath)) {
                logManager.error("InputValidator", "Security violation: path traversal attempt detected",
                                "baseDir", baseDir,
                                "userInput", userInput,
                                "resolvedPath", requestedPath.toString(),
                                "basePath", basePath.toString(),
                                "violationType", "PATH_TRAVERSAL");
                throw new SecurityException("Path traversal attempt detected");
            }
            
            logManager.debug("InputValidator", "Safe path created successfully",
                            "baseDir", baseDir,
                            "userInput", userInput,
                            "safePath", requestedPath.toString());
            return requestedPath;
        } catch (SecurityException e) {
            // Re-throw security exceptions without wrapping
            throw e;
        } catch (Exception e) {
            logManager.error("InputValidator", "Path construction failed", e,
                            "baseDir", baseDir,
                            "userInput", userInput);
            throw new SecurityException("Invalid path construction: " + e.getMessage());
        }
    }
    
    /**
     * Sanitizes error messages to prevent information disclosure.
     * 
     * @param originalMessage The original error message
     * @return A sanitized error message safe for user display
     */
    public static String sanitizeErrorMessage(String originalMessage) {
        if (originalMessage == null) {
            logManager.debug("InputValidator", "Error message sanitization: null input");
            return "An error occurred";
        }
        
        // Remove file paths, stack traces, and other sensitive information
        String sanitized = originalMessage
            .replaceAll("(?i)at\\s+[a-zA-Z0-9_.]+\\([^)]*\\)", "") // Remove stack trace lines
            .replaceAll("(?i)[a-zA-Z]:[/\\\\][^\\s]*", "[PATH]") // Remove Windows paths
            .replaceAll("(?i)/[^\\s]*", "[PATH]") // Remove Unix paths
            .replaceAll("(?i)\\\\[^\\s]*", "[PATH]") // Remove Windows backslash paths
            .replaceAll("(?i)caused by:.*", "") // Remove cause chains
            .trim();
        
        // If message is now empty or too generic, provide a safe default
        if (sanitized.isEmpty() || sanitized.length() < 5) {
            logManager.debug("InputValidator", "Error message sanitization: message too short after sanitization",
                            "originalLength", originalMessage.length(),
                            "sanitizedLength", sanitized.length());
            return "Operation failed";
        }
        
        logManager.debug("InputValidator", "Error message sanitized",
                        "originalLength", originalMessage.length(),
                        "sanitizedLength", sanitized.length());
        return sanitized;
    }
}