package engine.network.debug;

import engine.logging.LogManager;
import engine.network.NetworkManager;
import engine.network.NetworkConnection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Network debugging and monitoring tools for the Enterprise Networking Layer.
 * Provides real-time network statistics, connection monitoring, and performance analysis.
 */
public class NetworkDebugger {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static volatile NetworkDebugger instance;
    
    private final NetworkManager networkManager;
    private final ScheduledExecutorService debugExecutor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "NetworkDebugger-" + System.currentTimeMillis());
        t.setDaemon(true);
        return t;
    });
    
    // Debug configuration
    private volatile boolean debugEnabled = false;
    private volatile boolean packetLoggingEnabled = false;
    private volatile boolean performanceMonitoringEnabled = true;
    private volatile int statisticsReportInterval = 10; // seconds
    
    // Statistics tracking
    private final AtomicLong totalPacketsSent = new AtomicLong(0);
    private final AtomicLong totalPacketsReceived = new AtomicLong(0);
    private final AtomicLong totalBytesSent = new AtomicLong(0);
    private final AtomicLong totalBytesReceived = new AtomicLong(0);
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong totalDisconnections = new AtomicLong(0);
    
    // Performance metrics
    private final ConcurrentHashMap<String, PerformanceMetric> performanceMetrics = new ConcurrentHashMap<>();
    
    // Connection monitoring
    private final ConcurrentHashMap<String, ConnectionDebugInfo> connectionDebugInfo = new ConcurrentHashMap<>();
    
    private volatile boolean initialized = false;
    private volatile boolean running = false;
    
    private NetworkDebugger() {
        this.networkManager = NetworkManager.getInstance();
    }
    
    /**
     * Get the singleton instance of NetworkDebugger.
     */
    public static NetworkDebugger getInstance() {
        if (instance == null) {
            synchronized (NetworkDebugger.class) {
                if (instance == null) {
                    instance = new NetworkDebugger();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize the network debugger.
     */
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        
        // Initialize performance metrics
        initializePerformanceMetrics();
        
        initialized = true;
        
        logManager.info("NetworkDebugger", "Network debugger initialized");
    }
    
    /**
     * Start the network debugger.
     */
    public synchronized void start() {
        if (!initialized) {
            throw new IllegalStateException("NetworkDebugger must be initialized before starting");
        }
        
        if (running) {
            return;
        }
        
        running = true;
        
        // Start periodic statistics reporting
        if (performanceMonitoringEnabled) {
            startPerformanceMonitoring();
        }
        
        logManager.info("NetworkDebugger", "Network debugger started",
                       "debugEnabled", debugEnabled,
                       "packetLogging", packetLoggingEnabled,
                       "performanceMonitoring", performanceMonitoringEnabled);
    }
    
    /**
     * Stop the network debugger.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        
        running = false;
        
        logManager.info("NetworkDebugger", "Network debugger stopped");
    }
    
    /**
     * Shutdown the network debugger.
     */
    public synchronized void shutdown() {
        stop();
        
        if (!initialized) {
            return;
        }
        
        try {
            debugExecutor.shutdown();
            if (!debugExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                debugExecutor.shutdownNow();
            }
            
            initialized = false;
            
            logManager.info("NetworkDebugger", "Network debugger shutdown complete");
            
        } catch (Exception e) {
            logManager.error("NetworkDebugger", "Error during network debugger shutdown", e);
        }
    }
    
    /**
     * Log packet information for debugging.
     */
    public void logPacket(String direction, String connectionId, int packetSize, String packetType) {
        if (!packetLoggingEnabled || !running) {
            return;
        }
        
        logManager.debug("NetworkDebugger", "Packet logged",
                        "direction", direction,
                        "connectionId", connectionId,
                        "size", packetSize,
                        "type", packetType,
                        "timestamp", System.currentTimeMillis());
        
        // Update statistics
        if ("SENT".equals(direction)) {
            totalPacketsSent.incrementAndGet();
            totalBytesSent.addAndGet(packetSize);
        } else if ("RECEIVED".equals(direction)) {
            totalPacketsReceived.incrementAndGet();
            totalBytesReceived.addAndGet(packetSize);
        }
        
        // Update connection debug info
        updateConnectionDebugInfo(connectionId, direction, packetSize);
    }
    
    /**
     * Log connection event.
     */
    public void logConnectionEvent(String event, String connectionId, String details) {
        if (!debugEnabled || !running) {
            return;
        }
        
        logManager.info("NetworkDebugger", "Connection event",
                       "event", event,
                       "connectionId", connectionId,
                       "details", details);
        
        if ("CONNECTED".equals(event)) {
            totalConnections.incrementAndGet();
            connectionDebugInfo.put(connectionId, new ConnectionDebugInfo(connectionId));
        } else if ("DISCONNECTED".equals(event)) {
            totalDisconnections.incrementAndGet();
            connectionDebugInfo.remove(connectionId);
        }
    }
    
    /**
     * Record performance metric.
     */
    public void recordPerformanceMetric(String metricName, long value) {
        if (!performanceMonitoringEnabled || !running) {
            return;
        }
        
        PerformanceMetric metric = performanceMetrics.computeIfAbsent(metricName, 
            k -> new PerformanceMetric(metricName));
        
        metric.recordValue(value);
    }
    
    /**
     * Get current network statistics.
     */
    public NetworkStatistics getNetworkStatistics() {
        ConcurrentHashMap<String, Object> networkStats = networkManager.getNetworkStatistics();
        
        return new NetworkStatistics(
            totalPacketsSent.get(),
            totalPacketsReceived.get(),
            totalBytesSent.get(),
            totalBytesReceived.get(),
            totalConnections.get(),
            totalDisconnections.get(),
            (Integer) networkStats.getOrDefault("connections.active", 0),
            (Double) networkStats.getOrDefault("latency.average", 0.0),
            calculatePacketLossRate(),
            calculateThroughput()
        );
    }
    
    /**
     * Get performance metrics summary.
     */
    public ConcurrentHashMap<String, PerformanceMetric> getPerformanceMetrics() {
        return new ConcurrentHashMap<>(performanceMetrics);
    }
    
    /**
     * Get connection debug information.
     */
    public ConcurrentHashMap<String, ConnectionDebugInfo> getConnectionDebugInfo() {
        return new ConcurrentHashMap<>(connectionDebugInfo);
    }
    
    /**
     * Generate comprehensive debug report.
     */
    public String generateDebugReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Network Debug Report ===\n");
        report.append("Generated at: ").append(new java.util.Date()).append("\n\n");
        
        // Network statistics
        NetworkStatistics stats = getNetworkStatistics();
        report.append("Network Statistics:\n");
        report.append("  Packets Sent: ").append(stats.totalPacketsSent).append("\n");
        report.append("  Packets Received: ").append(stats.totalPacketsReceived).append("\n");
        report.append("  Bytes Sent: ").append(formatBytes(stats.totalBytesSent)).append("\n");
        report.append("  Bytes Received: ").append(formatBytes(stats.totalBytesReceived)).append("\n");
        report.append("  Active Connections: ").append(stats.activeConnections).append("\n");
        report.append("  Average Latency: ").append(String.format("%.2f ms", stats.averageLatency)).append("\n");
        report.append("  Packet Loss Rate: ").append(String.format("%.2f%%", stats.packetLossRate)).append("\n");
        report.append("  Throughput: ").append(formatBytes((long) stats.throughputBytesPerSecond)).append("/s\n\n");
        
        // Performance metrics
        report.append("Performance Metrics:\n");
        for (PerformanceMetric metric : performanceMetrics.values()) {
            report.append("  ").append(metric.getName()).append(": ");
            report.append("avg=").append(String.format("%.2f", metric.getAverage()));
            report.append(", min=").append(metric.getMinimum());
            report.append(", max=").append(metric.getMaximum());
            report.append(", count=").append(metric.getCount()).append("\n");
        }
        
        // Connection information
        report.append("\nActive Connections:\n");
        for (ConnectionDebugInfo connInfo : connectionDebugInfo.values()) {
            report.append("  ").append(connInfo.connectionId).append(": ");
            report.append("sent=").append(connInfo.packetsSent);
            report.append(", received=").append(connInfo.packetsReceived);
            report.append(", duration=").append(connInfo.getConnectionDuration()).append("ms\n");
        }
        
        return report.toString();
    }
    
    /**
     * Initialize performance metrics.
     */
    private void initializePerformanceMetrics() {
        performanceMetrics.put("network.latency", new PerformanceMetric("network.latency"));
        performanceMetrics.put("network.bandwidth", new PerformanceMetric("network.bandwidth"));
        performanceMetrics.put("network.packetProcessingTime", new PerformanceMetric("network.packetProcessingTime"));
        performanceMetrics.put("network.connectionSetupTime", new PerformanceMetric("network.connectionSetupTime"));
    }
    
    /**
     * Start performance monitoring.
     */
    private void startPerformanceMonitoring() {
        debugExecutor.scheduleAtFixedRate(() -> {
            try {
                if (running) {
                    generatePerformanceReport();
                }
            } catch (Exception e) {
                logManager.error("NetworkDebugger", "Error in performance monitoring", e);
            }
        }, statisticsReportInterval, statisticsReportInterval, TimeUnit.SECONDS);
    }
    
    /**
     * Generate periodic performance report.
     */
    private void generatePerformanceReport() {
        NetworkStatistics stats = getNetworkStatistics();
        
        logManager.info("NetworkDebugger", "Network performance report",
                       "activeConnections", stats.activeConnections,
                       "packetsPerSecond", (stats.totalPacketsSent + stats.totalPacketsReceived) / statisticsReportInterval,
                       "bytesPerSecond", formatBytes((long) stats.throughputBytesPerSecond),
                       "averageLatency", String.format("%.2f ms", stats.averageLatency),
                       "packetLossRate", String.format("%.2f%%", stats.packetLossRate));
    }
    
    /**
     * Update connection debug information.
     */
    private void updateConnectionDebugInfo(String connectionId, String direction, int packetSize) {
        ConnectionDebugInfo info = connectionDebugInfo.get(connectionId);
        if (info != null) {
            if ("SENT".equals(direction)) {
                info.packetsSent++;
                info.bytesSent += packetSize;
            } else if ("RECEIVED".equals(direction)) {
                info.packetsReceived++;
                info.bytesReceived += packetSize;
            }
            info.lastActivity = System.currentTimeMillis();
        }
    }
    
    /**
     * Calculate packet loss rate.
     */
    private double calculatePacketLossRate() {
        // Simplified calculation - in a real implementation, this would track
        // actual packet acknowledgments and retransmissions
        long totalPackets = totalPacketsSent.get() + totalPacketsReceived.get();
        if (totalPackets == 0) return 0.0;
        
        // Placeholder calculation
        return 0.1; // 0.1% packet loss
    }
    
    /**
     * Calculate network throughput.
     */
    private double calculateThroughput() {
        long totalBytes = totalBytesSent.get() + totalBytesReceived.get();
        long uptimeSeconds = System.currentTimeMillis() / 1000; // Simplified
        
        if (uptimeSeconds == 0) return 0.0;
        return (double) totalBytes / uptimeSeconds;
    }
    
    /**
     * Format bytes for human-readable output.
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    // Configuration setters
    public void setDebugEnabled(boolean enabled) { this.debugEnabled = enabled; }
    public void setPacketLoggingEnabled(boolean enabled) { this.packetLoggingEnabled = enabled; }
    public void setPerformanceMonitoringEnabled(boolean enabled) { this.performanceMonitoringEnabled = enabled; }
    public void setStatisticsReportInterval(int seconds) { this.statisticsReportInterval = seconds; }
    
    // Configuration getters
    public boolean isDebugEnabled() { return debugEnabled; }
    public boolean isPacketLoggingEnabled() { return packetLoggingEnabled; }
    public boolean isPerformanceMonitoringEnabled() { return performanceMonitoringEnabled; }
    
    /**
     * Network statistics data class.
     */
    public static class NetworkStatistics {
        public final long totalPacketsSent;
        public final long totalPacketsReceived;
        public final long totalBytesSent;
        public final long totalBytesReceived;
        public final long totalConnections;
        public final long totalDisconnections;
        public final int activeConnections;
        public final double averageLatency;
        public final double packetLossRate;
        public final double throughputBytesPerSecond;
        
        NetworkStatistics(long totalPacketsSent, long totalPacketsReceived,
                         long totalBytesSent, long totalBytesReceived,
                         long totalConnections, long totalDisconnections,
                         int activeConnections, double averageLatency,
                         double packetLossRate, double throughputBytesPerSecond) {
            this.totalPacketsSent = totalPacketsSent;
            this.totalPacketsReceived = totalPacketsReceived;
            this.totalBytesSent = totalBytesSent;
            this.totalBytesReceived = totalBytesReceived;
            this.totalConnections = totalConnections;
            this.totalDisconnections = totalDisconnections;
            this.activeConnections = activeConnections;
            this.averageLatency = averageLatency;
            this.packetLossRate = packetLossRate;
            this.throughputBytesPerSecond = throughputBytesPerSecond;
        }
    }
    
    /**
     * Performance metric tracking.
     */
    public static class PerformanceMetric {
        private final String name;
        private volatile long count = 0;
        private volatile long sum = 0;
        private volatile long minimum = Long.MAX_VALUE;
        private volatile long maximum = Long.MIN_VALUE;
        
        PerformanceMetric(String name) {
            this.name = name;
        }
        
        synchronized void recordValue(long value) {
            count++;
            sum += value;
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        
        public String getName() { return name; }
        public long getCount() { return count; }
        public double getAverage() { return count > 0 ? (double) sum / count : 0.0; }
        public long getMinimum() { return minimum == Long.MAX_VALUE ? 0 : minimum; }
        public long getMaximum() { return maximum == Long.MIN_VALUE ? 0 : maximum; }
    }
    
    /**
     * Connection debug information.
     */
    public static class ConnectionDebugInfo {
        public final String connectionId;
        public final long connectionStartTime;
        public volatile long packetsSent = 0;
        public volatile long packetsReceived = 0;
        public volatile long bytesSent = 0;
        public volatile long bytesReceived = 0;
        public volatile long lastActivity;
        
        ConnectionDebugInfo(String connectionId) {
            this.connectionId = connectionId;
            this.connectionStartTime = System.currentTimeMillis();
            this.lastActivity = connectionStartTime;
        }
        
        public long getConnectionDuration() {
            return System.currentTimeMillis() - connectionStartTime;
        }
        
        public long getTimeSinceLastActivity() {
            return System.currentTimeMillis() - lastActivity;
        }
    }
}