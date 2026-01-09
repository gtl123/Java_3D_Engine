package engine.config;

import engine.logging.LogManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration source that reads from Java properties files.
 * Supports hot reloading by monitoring file modification times.
 */
public class PropertiesConfigSource implements ConfigSource {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final String name;
    private final String filePath;
    private final int priority;
    private final Map<String, String> properties = new ConcurrentHashMap<>();
    private volatile long lastModified = -1;
    private volatile boolean available = false;
    
    public PropertiesConfigSource(String name, String filePath, int priority) {
        this.name = name;
        this.filePath = filePath;
        this.priority = priority;
        reload();
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public int getPriority() {
        return priority;
    }
    
    @Override
    public Optional<String> getValue(String key) {
        return Optional.ofNullable(properties.get(key));
    }
    
    @Override
    public Map<String, String> getAllValues() {
        return new HashMap<>(properties);
    }
    
    @Override
    public boolean isAvailable() {
        return available;
    }
    
    @Override
    public boolean reload() {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                available = false;
                logManager.debug("PropertiesConfigSource", "Properties file not found",
                               "name", name, "filePath", filePath);
                return false;
            }
            
            long currentModified = Files.getLastModifiedTime(path).toMillis();
            if (currentModified == lastModified && available) {
                return true; // No changes
            }
            
            Properties props = new Properties();
            try (InputStream input = Files.newInputStream(path)) {
                props.load(input);
            }
            
            // Convert Properties to Map<String, String>
            properties.clear();
            for (String key : props.stringPropertyNames()) {
                properties.put(key, props.getProperty(key));
            }
            
            lastModified = currentModified;
            available = true;
            
            logManager.info("PropertiesConfigSource", "Properties loaded successfully",
                           "name", name, "filePath", filePath, "propertyCount", properties.size());
            
            return true;
            
        } catch (IOException e) {
            available = false;
            logManager.error("PropertiesConfigSource", "Failed to load properties file", e,
                           "name", name, "filePath", filePath);
            return false;
        }
    }
    
    @Override
    public boolean supportsHotReload() {
        return true;
    }
    
    @Override
    public long getLastModified() {
        return lastModified;
    }
    
    /**
     * Check if the file has been modified since last load.
     * @return true if file has been modified
     */
    public boolean hasFileChanged() {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return false;
            }
            
            long currentModified = Files.getLastModifiedTime(path).toMillis();
            return currentModified != lastModified;
        } catch (IOException e) {
            logManager.debug("PropertiesConfigSource", "Error checking file modification time", e,
                           "name", name, "filePath", filePath);
            return false;
        }
    }
}