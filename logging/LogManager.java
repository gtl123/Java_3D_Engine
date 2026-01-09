package engine.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Enhanced LogManager with profiler integration support.
 * Provides structured logging with component-based organization and metrics integration.
 */
public class LogManager {
    private static LogManager instance;
    private MetricsCollector metricsCollector;
    
    // Log level configuration
    private LogLevel currentLogLevel = LogLevel.INFO;
    private final Map<String, LogLevel> componentLogLevels = new ConcurrentHashMap<>();
    
    // Formatting
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    public enum LogLevel {
        DEBUG(0), INFO(1), WARN(2), ERROR(3);
        
        private final int level;
        LogLevel(int level) { this.level = level; }
        public int getLevel() { return level; }
    }
    
    private LogManager() {
        this.metricsCollector = new MetricsCollector();
    }
    
    public static LogManager getInstance() {
        if (instance == null) {
            synchronized (LogManager.class) {
                if (instance == null) {
                    instance = new LogManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Set the global log level.
     */
    public void setLogLevel(LogLevel level) {
        this.currentLogLevel = level;
    }
    
    /**
     * Set log level for a specific component.
     */
    public void setComponentLogLevel(String component, LogLevel level) {
        componentLogLevels.put(component, level);
    }
    
    /**
     * Check if a log level is enabled for a component.
     */
    private boolean isLevelEnabled(String component, LogLevel level) {
        LogLevel componentLevel = componentLogLevels.getOrDefault(component, currentLogLevel);
        return level.getLevel() >= componentLevel.getLevel();
    }
    
    /**
     * Format log message with parameters.
     */
    private String formatMessage(String message, Object... params) {
        if (params.length == 0) {
            return message;
        }
        
        StringBuilder formatted = new StringBuilder(message);
        for (int i = 0; i < params.length; i += 2) {
            if (i + 1 < params.length) {
                formatted.append(", ").append(params[i]).append("=").append(params[i + 1]);
            }
        }
        return formatted.toString();
    }
    
    /**
     * Create formatted log entry.
     */
    private String createLogEntry(LogLevel level, String component, String message) {
        return String.format("[%s] [%s] [%s] %s",
                           TIMESTAMP_FORMAT.format(LocalDateTime.now()),
                           level.name(),
                           component != null ? component : "SYSTEM",
                           message);
    }
    
    // Enhanced logging methods with component support
    public void debug(String component, String message, Object... params) {
        if (isLevelEnabled(component, LogLevel.DEBUG)) {
            String formattedMessage = formatMessage(message, params);
            System.out.println(createLogEntry(LogLevel.DEBUG, component, formattedMessage));
            
            // Track debug messages in metrics
            metricsCollector.incrementCounter("log.debug.count");
            metricsCollector.incrementCounter("log.debug." + component + ".count");
        }
    }
    
    public void info(String component, String message, Object... params) {
        if (isLevelEnabled(component, LogLevel.INFO)) {
            String formattedMessage = formatMessage(message, params);
            System.out.println(createLogEntry(LogLevel.INFO, component, formattedMessage));
            
            // Track info messages in metrics
            metricsCollector.incrementCounter("log.info.count");
            metricsCollector.incrementCounter("log.info." + component + ".count");
        }
    }
    
    public void warn(String component, String message, Object... params) {
        if (isLevelEnabled(component, LogLevel.WARN)) {
            String formattedMessage = formatMessage(message, params);
            System.out.println(createLogEntry(LogLevel.WARN, component, formattedMessage));
            
            // Track warnings in metrics
            metricsCollector.incrementCounter("log.warn.count");
            metricsCollector.incrementCounter("log.warn." + component + ".count");
        }
    }
    
    public void error(String component, String message, Object... params) {
        if (isLevelEnabled(component, LogLevel.ERROR)) {
            String formattedMessage = formatMessage(message, params);
            System.err.println(createLogEntry(LogLevel.ERROR, component, formattedMessage));
            
            // Track errors in metrics
            metricsCollector.incrementCounter("log.error.count");
            metricsCollector.incrementCounter("log.error." + component + ".count");
        }
    }
    
    public void error(String component, String message, Throwable throwable, Object... params) {
        if (isLevelEnabled(component, LogLevel.ERROR)) {
            String formattedMessage = formatMessage(message, params);
            System.err.println(createLogEntry(LogLevel.ERROR, component, formattedMessage));
            throwable.printStackTrace();
            
            // Track errors in metrics
            metricsCollector.incrementCounter("log.error.count");
            metricsCollector.incrementCounter("log.error." + component + ".count");
            metricsCollector.incrementCounter("log.exception.count");
        }
    }
    
    // Legacy methods for backward compatibility
    public void info(String message) {
        info("SYSTEM", message);
    }
    
    public void warn(String message) {
        warn("SYSTEM", message);
    }
    
    public void error(String message) {
        error("SYSTEM", message);
    }
    
    public void error(String message, Throwable throwable) {
        error("SYSTEM", message, throwable);
    }
    
    public void debug(String message) {
        debug("SYSTEM", message);
    }
    
    /**
     * Log profiler metrics to the metrics collector.
     */
    public void logProfilerMetrics(String profilerType, Map<String, Object> metrics) {
        for (Map.Entry<String, Object> entry : metrics.entrySet()) {
            String metricName = "profiler." + profilerType + "." + entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Number) {
                long longValue = ((Number) value).longValue();
                metricsCollector.recordMetric(metricName, longValue);
            } else if (value instanceof Boolean) {
                metricsCollector.recordMetric(metricName, ((Boolean) value) ? 1L : 0L);
            }
        }
    }
    
    /**
     * Log profiler timing information.
     */
    public void logProfilerTiming(String profilerType, String operation, long durationMs) {
        String timerName = "profiler." + profilerType + "." + operation + ".time";
        metricsCollector.recordTime(timerName, durationMs);
        
        // Also track operation count
        String counterName = "profiler." + profilerType + "." + operation + ".count";
        metricsCollector.incrementCounter(counterName);
    }
    
    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }
    
    public LogLevel getCurrentLogLevel() {
        return currentLogLevel;
    }
    
    public Map<String, LogLevel> getComponentLogLevels() {
        return new ConcurrentHashMap<>(componentLogLevels);
    }
}