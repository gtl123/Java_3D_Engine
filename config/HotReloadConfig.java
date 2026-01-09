package engine.config;

import engine.logging.LogManager;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Live configuration updates without restart.
 * Monitors configuration sources for changes and notifies listeners.
 */
public class HotReloadConfig {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    /**
     * Configuration change event.
     */
    public static class ConfigChangeEvent {
        private final String key;
        private final Object oldValue;
        private final Object newValue;
        private final String source;
        private final long timestamp;
        
        public ConfigChangeEvent(String key, Object oldValue, Object newValue, String source) {
            this.key = key;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.source = source;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getKey() { return key; }
        public Object getOldValue() { return oldValue; }
        public Object getNewValue() { return newValue; }
        public String getSource() { return source; }
        public long getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format("ConfigChangeEvent{key='%s', oldValue=%s, newValue=%s, source='%s'}",
                               key, oldValue, newValue, source);
        }
    }
    
    /**
     * Configuration change listener.
     */
    @FunctionalInterface
    public interface ConfigChangeListener {
        void onConfigChange(ConfigChangeEvent event);
    }
    
    private final List<ConfigSource> watchedSources = new CopyOnWriteArrayList<>();
    private final List<ConfigChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Object> lastKnownValues = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "HotReloadConfig-Monitor");
        t.setDaemon(true);
        return t;
    });
    
    private volatile boolean monitoring = false;
    private volatile long checkIntervalMs = 1000; // Check every second
    private ScheduledFuture<?> monitoringTask;
    
    public HotReloadConfig() {
        logManager.info("HotReloadConfig", "Hot reload configuration system initialized");
    }
    
    /**
     * Start monitoring configuration sources for changes.
     */
    public synchronized void startMonitoring() {
        if (monitoring) {
            return;
        }
        
        monitoring = true;
        monitoringTask = scheduler.scheduleWithFixedDelay(
            this::checkForChanges,
            checkIntervalMs,
            checkIntervalMs,
            TimeUnit.MILLISECONDS
        );
        
        logManager.info("HotReloadConfig", "Started monitoring configuration sources",
                       "sourceCount", watchedSources.size(),
                       "checkIntervalMs", checkIntervalMs);
    }
    
    /**
     * Stop monitoring configuration sources.
     */
    public synchronized void stopMonitoring() {
        if (!monitoring) {
            return;
        }
        
        monitoring = false;
        if (monitoringTask != null) {
            monitoringTask.cancel(false);
            monitoringTask = null;
        }
        
        logManager.info("HotReloadConfig", "Stopped monitoring configuration sources");
    }
    
    /**
     * Add a configuration source to monitor.
     */
    public void addWatchedSource(ConfigSource source) {
        if (source != null && source.supportsHotReload()) {
            watchedSources.add(source);
            
            // Initialize last known values
            Map<String, String> values = source.getAllValues();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = source.getName() + "." + entry.getKey();
                lastKnownValues.put(key, entry.getValue());
            }
            
            logManager.debug("HotReloadConfig", "Added watched configuration source",
                           "sourceName", source.getName(),
                           "valueCount", values.size());
        } else if (source != null) {
            logManager.warn("HotReloadConfig", "Configuration source does not support hot reload",
                           "sourceName", source.getName());
        }
    }
    
    /**
     * Remove a configuration source from monitoring.
     */
    public void removeWatchedSource(ConfigSource source) {
        if (source != null) {
            watchedSources.remove(source);
            
            // Remove last known values for this source
            String prefix = source.getName() + ".";
            lastKnownValues.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
            
            logManager.debug("HotReloadConfig", "Removed watched configuration source",
                           "sourceName", source.getName());
        }
    }
    
    /**
     * Add a configuration change listener.
     */
    public void addChangeListener(ConfigChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
            logManager.debug("HotReloadConfig", "Added configuration change listener");
        }
    }
    
    /**
     * Remove a configuration change listener.
     */
    public void removeChangeListener(ConfigChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
            logManager.debug("HotReloadConfig", "Removed configuration change listener");
        }
    }
    
    /**
     * Set the check interval for monitoring.
     */
    public void setCheckInterval(long intervalMs) {
        this.checkIntervalMs = Math.max(100, intervalMs); // Minimum 100ms
        
        // Restart monitoring with new interval if currently running
        if (monitoring) {
            stopMonitoring();
            startMonitoring();
        }
        
        logManager.info("HotReloadConfig", "Check interval updated",
                       "intervalMs", this.checkIntervalMs);
    }
    
    /**
     * Manually trigger a configuration reload check.
     */
    public void checkForChanges() {
        if (watchedSources.isEmpty()) {
            return;
        }
        
        try {
            for (ConfigSource source : watchedSources) {
                checkSourceForChanges(source);
            }
        } catch (Exception e) {
            logManager.error("HotReloadConfig", "Error during configuration change check", e);
        }
    }
    
    /**
     * Check a specific configuration source for changes.
     */
    private void checkSourceForChanges(ConfigSource source) {
        try {
            if (!source.isAvailable()) {
                return;
            }
            
            // Reload the source to get latest values
            if (!source.reload()) {
                return;
            }
            
            Map<String, String> currentValues = source.getAllValues();
            String sourcePrefix = source.getName() + ".";
            
            // Check for changes and new values
            for (Map.Entry<String, String> entry : currentValues.entrySet()) {
                String configKey = entry.getKey();
                String fullKey = sourcePrefix + configKey;
                String newValue = entry.getValue();
                Object oldValue = lastKnownValues.get(fullKey);
                
                if (!Objects.equals(oldValue, newValue)) {
                    lastKnownValues.put(fullKey, newValue);
                    
                    ConfigChangeEvent event = new ConfigChangeEvent(
                        configKey, oldValue, newValue, source.getName()
                    );
                    
                    notifyListeners(event);
                }
            }
            
            // Check for removed values
            Set<String> currentKeys = new HashSet<>();
            for (String key : currentValues.keySet()) {
                currentKeys.add(sourcePrefix + key);
            }
            
            Iterator<Map.Entry<String, Object>> iterator = lastKnownValues.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Object> entry = iterator.next();
                String fullKey = entry.getKey();
                
                if (fullKey.startsWith(sourcePrefix) && !currentKeys.contains(fullKey)) {
                    String configKey = fullKey.substring(sourcePrefix.length());
                    Object oldValue = entry.getValue();
                    
                    iterator.remove();
                    
                    ConfigChangeEvent event = new ConfigChangeEvent(
                        configKey, oldValue, null, source.getName()
                    );
                    
                    notifyListeners(event);
                }
            }
            
        } catch (Exception e) {
            logManager.error("HotReloadConfig", "Error checking source for changes", e,
                           "sourceName", source.getName());
        }
    }
    
    /**
     * Notify all listeners of a configuration change.
     */
    private void notifyListeners(ConfigChangeEvent event) {
        logManager.debug("HotReloadConfig", "Configuration change detected",
                        "key", event.getKey(),
                        "oldValue", event.getOldValue(),
                        "newValue", event.getNewValue(),
                        "source", event.getSource());
        
        for (ConfigChangeListener listener : listeners) {
            try {
                listener.onConfigChange(event);
            } catch (Exception e) {
                logManager.error("HotReloadConfig", "Error in configuration change listener", e,
                               "eventKey", event.getKey());
            }
        }
    }
    
    /**
     * Get the current monitoring status.
     */
    public boolean isMonitoring() {
        return monitoring;
    }
    
    /**
     * Get the current check interval.
     */
    public long getCheckInterval() {
        return checkIntervalMs;
    }
    
    /**
     * Get the number of watched sources.
     */
    public int getWatchedSourceCount() {
        return watchedSources.size();
    }
    
    /**
     * Get the number of registered listeners.
     */
    public int getListenerCount() {
        return listeners.size();
    }
    
    /**
     * Shutdown the hot reload system.
     */
    public void shutdown() {
        stopMonitoring();
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        watchedSources.clear();
        listeners.clear();
        lastKnownValues.clear();
        
        logManager.info("HotReloadConfig", "Hot reload configuration system shutdown");
    }
}