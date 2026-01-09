package engine.profiler;

import engine.logging.LogManager;
import engine.logging.MetricsCollector;
import engine.network.ConnectionManager;
import engine.network.NetworkConnection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Network profiler for performance analysis and monitoring.
 * Tracks bandwidth usage, latency, packet loss, and connection statistics.
 */
public class NetworkProfiler implements IProfiler {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = logManager.getMetricsCollector();
    
    private final ProfilerConfiguration config;
    private final AtomicBoolean active = new AtomicBoolean(false);
    
    // Network statistics
    private final AtomicLong totalPacketsSent = new AtomicLong(0);
    private final AtomicLong totalPacketsReceived = new AtomicLong(0);
    private final AtomicLong totalBytesSent = new AtomicLong(0);
    private final AtomicLong totalBytesReceived = new AtomicLong(0);
    private final AtomicLong totalPacketsLost = new AtomicLong(0);
    
    // Connection tracking
    private final Map<String, ConnectionProfile> connectionProfiles = new ConcurrentHashMap<>();
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong totalConnections = new AtomicLong(0);
    
    // Bandwidth tracking
    private final Queue<BandwidthSample> bandwidthSamples = new ConcurrentLinkedQueue<>();
    private final AtomicLong peakBandwidthIn = new AtomicLong(0);
    private final AtomicLong peakBandwidthOut = new AtomicLong(0);
    
    // Latency tracking
    private final Queue<LatencySample> latencySamples = new ConcurrentLinkedQueue<>();
    private final Map<String, PingTracker> pingTrackers = new ConcurrentHashMap<>();
    
    // Packet inspection
    private final Map<String, PacketTypeProfile> packetProfiles = new ConcurrentHashMap<>();
    
    // Sampling
    private long lastSampleTime = 0;
    private long lastBandwidthBytes = 0;
    
    public NetworkProfiler(ProfilerConfiguration config) {
        this.config = config;
    }
    
    @Override
    public void initialize() {
        logManager.info("NetworkProfiler", "Initializing network profiler",
                       "bandwidthTracking", config.isBandwidthTracking(),
                       "latencyMeasurement", config.isLatencyMeasurement(),
                       "packetInspection", config.isPacketInspection());
        
        logManager.info("NetworkProfiler", "Network profiler initialized");
    }
    
    @Override
    public void start() {
        if (active.get()) {
            return;
        }
        
        logManager.info("NetworkProfiler", "Starting network profiling");
        
        // Reset all tracking data
        totalPacketsSent.set(0);
        totalPacketsReceived.set(0);
        totalBytesSent.set(0);
        totalBytesReceived.set(0);
        totalPacketsLost.set(0);
        
        connectionProfiles.clear();
        bandwidthSamples.clear();
        latencySamples.clear();
        pingTrackers.clear();
        packetProfiles.clear();
        
        activeConnections.set(0);
        totalConnections.set(0);
        peakBandwidthIn.set(0);
        peakBandwidthOut.set(0);
        
        lastSampleTime = System.currentTimeMillis();
        lastBandwidthBytes = 0;
        
        active.set(true);
        
        logManager.info("NetworkProfiler", "Network profiling started");
    }
    
    @Override
    public void stop() {
        if (!active.get()) {
            return;
        }
        
        logManager.info("NetworkProfiler", "Stopping network profiling");
        
        active.set(false);
        
        logManager.info("NetworkProfiler", "Network profiling stopped");
    }
    
    @Override
    public void update(float deltaTime) {
        if (!active.get()) {
            return;
        }
        
        // Sample network statistics periodically
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSampleTime >= config.getNetworkSamplingIntervalMs()) {
            sampleNetworkStatistics();
            lastSampleTime = currentTime;
        }
        
        // Update bandwidth tracking
        if (config.isBandwidthTracking()) {
            updateBandwidthTracking();
        }
        
        // Update latency measurements
        if (config.isLatencyMeasurement()) {
            updateLatencyMeasurements();
        }
        
        // Clean up old samples
        cleanupOldSamples();
    }
    
    @Override
    public ProfilerData collectData() {
        ProfilerData data = new ProfilerData("network");
        
        // Basic network statistics
        data.addMetric("totalPacketsSent", totalPacketsSent.get());
        data.addMetric("totalPacketsReceived", totalPacketsReceived.get());
        data.addMetric("totalBytesSent", totalBytesSent.get());
        data.addMetric("totalBytesReceived", totalBytesReceived.get());
        data.addMetric("totalPacketsLost", totalPacketsLost.get());
        
        // Connection statistics
        data.addMetric("activeConnections", activeConnections.get());
        data.addMetric("totalConnections", totalConnections.get());
        data.addMetric("connectionProfiles", connectionProfiles.size());
        
        // Bandwidth metrics
        if (config.isBandwidthTracking()) {
            collectBandwidthMetrics(data);
        }
        
        // Latency metrics
        if (config.isLatencyMeasurement()) {
            collectLatencyMetrics(data);
        }
        
        // Packet inspection metrics
        if (config.isPacketInspection()) {
            collectPacketInspectionMetrics(data);
        }
        
        // Connection quality metrics
        collectConnectionQualityMetrics(data);
        
        // Add metadata
        data.addMetadata("bandwidthTracking", config.isBandwidthTracking());
        data.addMetadata("latencyMeasurement", config.isLatencyMeasurement());
        data.addMetadata("packetInspection", config.isPacketInspection());
        data.addMetadata("samplingInterval", config.getNetworkSamplingIntervalMs());
        
        return data;
    }
    
    @Override
    public void reset() {
        logManager.info("NetworkProfiler", "Resetting network profiler");
        
        totalPacketsSent.set(0);
        totalPacketsReceived.set(0);
        totalBytesSent.set(0);
        totalBytesReceived.set(0);
        totalPacketsLost.set(0);
        
        connectionProfiles.clear();
        bandwidthSamples.clear();
        latencySamples.clear();
        pingTrackers.clear();
        packetProfiles.clear();
        
        activeConnections.set(0);
        totalConnections.set(0);
        peakBandwidthIn.set(0);
        peakBandwidthOut.set(0);
    }
    
    @Override
    public void cleanup() {
        logManager.info("NetworkProfiler", "Cleaning up network profiler");
        
        stop();
        
        connectionProfiles.clear();
        bandwidthSamples.clear();
        latencySamples.clear();
        pingTrackers.clear();
        packetProfiles.clear();
        
        logManager.info("NetworkProfiler", "Network profiler cleanup complete");
    }
    
    @Override
    public boolean isActive() {
        return active.get();
    }
    
    @Override
    public String getProfilerType() {
        return "network";
    }
    
    /**
     * Record a packet being sent.
     */
    public void recordPacketSent(String connectionId, String packetType, int size) {
        if (!active.get()) {
            return;
        }
        
        totalPacketsSent.incrementAndGet();
        totalBytesSent.addAndGet(size);
        
        // Update connection profile
        ConnectionProfile profile = connectionProfiles.computeIfAbsent(connectionId, 
                                                                      k -> new ConnectionProfile(k));
        profile.recordPacketSent(size);
        
        // Update packet type profile
        if (config.isPacketInspection()) {
            PacketTypeProfile packetProfile = packetProfiles.computeIfAbsent(packetType, 
                                                                            k -> new PacketTypeProfile(k));
            packetProfile.recordSentPacket(size);
        }
        
        // Record metrics
        metricsCollector.incrementCounter("network.packets.sent");
        metricsCollector.incrementCounter("network.bytes.sent", size);
    }
    
    /**
     * Record a packet being received.
     */
    public void recordPacketReceived(String connectionId, String packetType, int size) {
        if (!active.get()) {
            return;
        }
        
        totalPacketsReceived.incrementAndGet();
        totalBytesReceived.addAndGet(size);
        
        // Update connection profile
        ConnectionProfile profile = connectionProfiles.computeIfAbsent(connectionId, 
                                                                      k -> new ConnectionProfile(k));
        profile.recordPacketReceived(size);
        
        // Update packet type profile
        if (config.isPacketInspection()) {
            PacketTypeProfile packetProfile = packetProfiles.computeIfAbsent(packetType, 
                                                                            k -> new PacketTypeProfile(k));
            packetProfile.recordReceivedPacket(size);
        }
        
        // Record metrics
        metricsCollector.incrementCounter("network.packets.received");
        metricsCollector.incrementCounter("network.bytes.received", size);
    }
    
    /**
     * Record a packet loss event.
     */
    public void recordPacketLoss(String connectionId, int lostPackets) {
        if (!active.get()) {
            return;
        }
        
        totalPacketsLost.addAndGet(lostPackets);
        
        // Update connection profile
        ConnectionProfile profile = connectionProfiles.get(connectionId);
        if (profile != null) {
            profile.recordPacketLoss(lostPackets);
        }
        
        // Record metrics
        metricsCollector.incrementCounter("network.packets.lost", lostPackets);
    }
    
    /**
     * Record connection establishment.
     */
    public void recordConnectionEstablished(String connectionId) {
        if (!active.get()) {
            return;
        }
        
        activeConnections.incrementAndGet();
        totalConnections.incrementAndGet();
        
        // Create connection profile
        ConnectionProfile profile = new ConnectionProfile(connectionId);
        profile.connectionTime = System.currentTimeMillis();
        connectionProfiles.put(connectionId, profile);
        
        // Create ping tracker
        if (config.isLatencyMeasurement()) {
            pingTrackers.put(connectionId, new PingTracker(connectionId));
        }
        
        logManager.debug("NetworkProfiler", "Connection established", 
                        "connectionId", connectionId,
                        "activeConnections", activeConnections.get());
    }
    
    /**
     * Record connection termination.
     */
    public void recordConnectionTerminated(String connectionId) {
        if (!active.get()) {
            return;
        }
        
        activeConnections.decrementAndGet();
        
        // Update connection profile
        ConnectionProfile profile = connectionProfiles.get(connectionId);
        if (profile != null) {
            profile.disconnectionTime = System.currentTimeMillis();
            profile.connectionDuration = profile.disconnectionTime - profile.connectionTime;
        }
        
        // Remove ping tracker
        pingTrackers.remove(connectionId);
        
        logManager.debug("NetworkProfiler", "Connection terminated", 
                        "connectionId", connectionId,
                        "activeConnections", activeConnections.get());
    }
    
    /**
     * Record latency measurement.
     */
    public void recordLatency(String connectionId, double latencyMs) {
        if (!active.get() || !config.isLatencyMeasurement()) {
            return;
        }
        
        // Update connection profile
        ConnectionProfile profile = connectionProfiles.get(connectionId);
        if (profile != null) {
            profile.recordLatency(latencyMs);
        }
        
        // Add latency sample
        LatencySample sample = new LatencySample();
        sample.connectionId = connectionId;
        sample.latency = latencyMs;
        sample.timestamp = System.currentTimeMillis();
        latencySamples.offer(sample);
        
        // Update ping tracker
        PingTracker tracker = pingTrackers.get(connectionId);
        if (tracker != null) {
            tracker.recordPing(latencyMs);
        }
        
        // Record metrics
        metricsCollector.recordTime("network.latency." + connectionId, (long)latencyMs);
    }
    
    private void sampleNetworkStatistics() {
        // Sample current network state
        long currentBytes = totalBytesSent.get() + totalBytesReceived.get();
        long bytesDelta = currentBytes - lastBandwidthBytes;
        lastBandwidthBytes = currentBytes;
        
        // Calculate bandwidth
        long timeDelta = config.getNetworkSamplingIntervalMs();
        if (timeDelta > 0) {
            long bandwidth = (bytesDelta * 1000) / timeDelta; // bytes per second
            
            // Add bandwidth sample
            BandwidthSample sample = new BandwidthSample();
            sample.timestamp = System.currentTimeMillis();
            sample.bandwidth = bandwidth;
            sample.bytesSent = totalBytesSent.get();
            sample.bytesReceived = totalBytesReceived.get();
            bandwidthSamples.offer(sample);
            
            // Update peak bandwidth
            peakBandwidthIn.updateAndGet(current -> Math.max(current, bandwidth / 2)); // Rough estimate
            peakBandwidthOut.updateAndGet(current -> Math.max(current, bandwidth / 2));
        }
    }
    
    private void updateBandwidthTracking() {
        // Calculate current bandwidth utilization
        if (!bandwidthSamples.isEmpty()) {
            BandwidthSample latest = ((ConcurrentLinkedQueue<BandwidthSample>) bandwidthSamples).peek();
            if (latest != null) {
                metricsCollector.recordTime("network.bandwidth.current", latest.bandwidth);
            }
        }
    }
    
    private void updateLatencyMeasurements() {
        // Update ping trackers
        for (PingTracker tracker : pingTrackers.values()) {
            tracker.update();
        }
    }
    
    private void cleanupOldSamples() {
        long cutoffTime = System.currentTimeMillis() - 60000; // Keep 1 minute of samples
        
        // Clean bandwidth samples
        while (!bandwidthSamples.isEmpty()) {
            BandwidthSample sample = bandwidthSamples.peek();
            if (sample != null && sample.timestamp < cutoffTime) {
                bandwidthSamples.poll();
            } else {
                break;
            }
        }
        
        // Clean latency samples
        while (!latencySamples.isEmpty()) {
            LatencySample sample = latencySamples.peek();
            if (sample != null && sample.timestamp < cutoffTime) {
                latencySamples.poll();
            } else {
                break;
            }
        }
    }
    
    private void collectBandwidthMetrics(ProfilerData data) {
        data.addMetric("peakBandwidthIn", peakBandwidthIn.get());
        data.addMetric("peakBandwidthOut", peakBandwidthOut.get());
        
        // Calculate average bandwidth
        if (!bandwidthSamples.isEmpty()) {
            long totalBandwidth = 0;
            int sampleCount = 0;
            
            for (BandwidthSample sample : bandwidthSamples) {
                totalBandwidth += sample.bandwidth;
                sampleCount++;
            }
            
            if (sampleCount > 0) {
                data.addMetric("averageBandwidth", (double)totalBandwidth / sampleCount);
            }
            
            // Current bandwidth
            BandwidthSample latest = ((ConcurrentLinkedQueue<BandwidthSample>) bandwidthSamples).peek();
            if (latest != null) {
                data.addMetric("currentBandwidth", latest.bandwidth);
            }
        }
    }
    
    private void collectLatencyMetrics(ProfilerData data) {
        if (latencySamples.isEmpty()) {
            return;
        }
        
        double totalLatency = 0.0;
        double minLatency = Double.MAX_VALUE;
        double maxLatency = 0.0;
        int sampleCount = 0;
        
        for (LatencySample sample : latencySamples) {
            totalLatency += sample.latency;
            minLatency = Math.min(minLatency, sample.latency);
            maxLatency = Math.max(maxLatency, sample.latency);
            sampleCount++;
        }
        
        if (sampleCount > 0) {
            data.addMetric("averageLatency", totalLatency / sampleCount);
            data.addMetric("minLatency", minLatency);
            data.addMetric("maxLatency", maxLatency);
        }
        
        // Per-connection latency
        for (Map.Entry<String, PingTracker> entry : pingTrackers.entrySet()) {
            String connectionId = entry.getKey();
            PingTracker tracker = entry.getValue();
            
            data.addMetric("latency." + connectionId + ".avg", tracker.getAverageLatency());
            data.addMetric("latency." + connectionId + ".min", tracker.getMinLatency());
            data.addMetric("latency." + connectionId + ".max", tracker.getMaxLatency());
        }
    }
    
    private void collectPacketInspectionMetrics(ProfilerData data) {
        int totalPacketTypes = packetProfiles.size();
        data.addMetric("packetTypeCount", totalPacketTypes);
        
        if (totalPacketTypes > 0) {
            // Most frequent packet type
            PacketTypeProfile mostFrequent = packetProfiles.values().stream()
                .max((a, b) -> Long.compare(a.totalPackets, b.totalPackets))
                .orElse(null);
            
            if (mostFrequent != null) {
                data.addMetric("mostFrequentPacketType", mostFrequent.packetType);
                data.addMetric("mostFrequentPacketCount", mostFrequent.totalPackets);
            }
            
            // Largest packet type by bytes
            PacketTypeProfile largest = packetProfiles.values().stream()
                .max((a, b) -> Long.compare(a.totalBytes, b.totalBytes))
                .orElse(null);
            
            if (largest != null) {
                data.addMetric("largestPacketType", largest.packetType);
                data.addMetric("largestPacketBytes", largest.totalBytes);
            }
        }
    }
    
    private void collectConnectionQualityMetrics(ProfilerData data) {
        if (connectionProfiles.isEmpty()) {
            return;
        }
        
        double totalPacketLossRate = 0.0;
        double totalLatency = 0.0;
        int connectionCount = 0;
        
        for (ConnectionProfile profile : connectionProfiles.values()) {
            if (profile.packetsSent > 0) {
                double lossRate = (double)profile.packetsLost / profile.packetsSent * 100.0;
                totalPacketLossRate += lossRate;
            }
            
            if (profile.latencySamples > 0) {
                totalLatency += profile.averageLatency;
            }
            
            connectionCount++;
        }
        
        if (connectionCount > 0) {
            data.addMetric("averagePacketLossRate", totalPacketLossRate / connectionCount);
            data.addMetric("averageConnectionLatency", totalLatency / connectionCount);
        }
        
        // Connection with worst quality
        ConnectionProfile worstConnection = connectionProfiles.values().stream()
            .max((a, b) -> {
                double lossRateA = a.packetsSent > 0 ? (double)a.packetsLost / a.packetsSent : 0.0;
                double lossRateB = b.packetsSent > 0 ? (double)b.packetsLost / b.packetsSent : 0.0;
                return Double.compare(lossRateA, lossRateB);
            })
            .orElse(null);
        
        if (worstConnection != null && worstConnection.packetsSent > 0) {
            double worstLossRate = (double)worstConnection.packetsLost / worstConnection.packetsSent * 100.0;
            data.addMetric("worstConnectionId", worstConnection.connectionId);
            data.addMetric("worstConnectionLossRate", worstLossRate);
        }
    }
    
    // Helper classes
    private static class ConnectionProfile {
        final String connectionId;
        long connectionTime = 0;
        long disconnectionTime = 0;
        long connectionDuration = 0;
        
        long packetsSent = 0;
        long packetsReceived = 0;
        long bytesSent = 0;
        long bytesReceived = 0;
        long packetsLost = 0;
        
        double totalLatency = 0.0;
        double minLatency = Double.MAX_VALUE;
        double maxLatency = 0.0;
        double averageLatency = 0.0;
        int latencySamples = 0;
        
        ConnectionProfile(String connectionId) {
            this.connectionId = connectionId;
        }
        
        void recordPacketSent(int size) {
            packetsSent++;
            bytesSent += size;
        }
        
        void recordPacketReceived(int size) {
            packetsReceived++;
            bytesReceived += size;
        }
        
        void recordPacketLoss(int lostPackets) {
            packetsLost += lostPackets;
        }
        
        void recordLatency(double latency) {
            totalLatency += latency;
            minLatency = Math.min(minLatency, latency);
            maxLatency = Math.max(maxLatency, latency);
            latencySamples++;
            averageLatency = totalLatency / latencySamples;
        }
    }
    
    private static class BandwidthSample {
        long timestamp;
        long bandwidth; // bytes per second
        long bytesSent;
        long bytesReceived;
    }
    
    private static class LatencySample {
        long timestamp;
        String connectionId;
        double latency; // milliseconds
    }
    
    private static class PacketTypeProfile {
        final String packetType;
        long totalPackets = 0;
        long totalBytes = 0;
        long sentPackets = 0;
        long receivedPackets = 0;
        long sentBytes = 0;
        long receivedBytes = 0;
        
        PacketTypeProfile(String packetType) {
            this.packetType = packetType;
        }
        
        void recordSentPacket(int size) {
            totalPackets++;
            totalBytes += size;
            sentPackets++;
            sentBytes += size;
        }
        
        void recordReceivedPacket(int size) {
            totalPackets++;
            totalBytes += size;
            receivedPackets++;
            receivedBytes += size;
        }
    }
    
    private static class PingTracker {
        final String connectionId;
        final Queue<Double> recentPings = new ConcurrentLinkedQueue<>();
        double totalLatency = 0.0;
        double minLatency = Double.MAX_VALUE;
        double maxLatency = 0.0;
        int sampleCount = 0;
        
        PingTracker(String connectionId) {
            this.connectionId = connectionId;
        }
        
        void recordPing(double latency) {
            recentPings.offer(latency);
            totalLatency += latency;
            minLatency = Math.min(minLatency, latency);
            maxLatency = Math.max(maxLatency, latency);
            sampleCount++;
            
            // Keep only recent pings
            if (recentPings.size() > 100) {
                recentPings.poll();
            }
        }
        
        void update() {
            // Clean old pings (older than 30 seconds)
            long cutoffTime = System.currentTimeMillis() - 30000;
            // Note: This is a simplified cleanup - in a real implementation,
            // we'd need to track timestamps for each ping
        }
        
        double getAverageLatency() {
            return sampleCount > 0 ? totalLatency / sampleCount : 0.0;
        }
        
        double getMinLatency() {
            return minLatency != Double.MAX_VALUE ? minLatency : 0.0;
        }
        
        double getMaxLatency() {
            return maxLatency;
        }
    }
}