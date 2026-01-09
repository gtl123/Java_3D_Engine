package engine.logging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Enhanced MetricsCollector with profiler integration support.
 * Provides comprehensive metrics collection including counters, timers, gauges, and histograms.
 */
public class MetricsCollector {
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> timers = new ConcurrentHashMap<>();
    private final Map<String, DoubleAdder> gauges = new ConcurrentHashMap<>();
    private final Map<String, MetricHistogram> histograms = new ConcurrentHashMap<>();
    private final Map<String, Object> customMetrics = new ConcurrentHashMap<>();
    
    // Profiler-specific metrics tracking
    private final Map<String, ProfilerMetrics> profilerMetrics = new ConcurrentHashMap<>();
    private final ReadWriteLock metricsLock = new ReentrantReadWriteLock();
    
    // Counter operations
    public void incrementCounter(String name) {
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    public void incrementCounter(String name, long value) {
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).addAndGet(value);
    }
    
    public void setCounter(String name, long value) {
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).set(value);
    }
    
    public long getCounter(String name) {
        AtomicLong counter = counters.get(name);
        return counter != null ? counter.get() : 0;
    }
    
    // Timer operations
    public void recordTime(String name, long timeMs) {
        timers.computeIfAbsent(name, k -> new AtomicLong(0)).addAndGet(timeMs);
        
        // Also record in histogram for statistical analysis
        recordHistogram(name + ".histogram", timeMs);
    }
    
    public long getTimer(String name) {
        AtomicLong timer = timers.get(name);
        return timer != null ? timer.get() : 0;
    }
    
    // Gauge operations (for current values like memory usage, active connections)
    public void recordGauge(String name, double value) {
        gauges.computeIfAbsent(name, k -> new DoubleAdder()).reset();
        gauges.get(name).add(value);
    }
    
    public void updateGauge(String name, double delta) {
        gauges.computeIfAbsent(name, k -> new DoubleAdder()).add(delta);
    }
    
    public double getGauge(String name) {
        DoubleAdder gauge = gauges.get(name);
        return gauge != null ? gauge.sum() : 0.0;
    }
    
    // Histogram operations (for statistical analysis)
    public void recordHistogram(String name, long value) {
        histograms.computeIfAbsent(name, k -> new MetricHistogram()).record(value);
    }
    
    public MetricHistogram getHistogram(String name) {
        return histograms.get(name);
    }
    
    // Generic metric recording
    public void recordMetric(String name, Object value) {
        if (value instanceof Number) {
            long longValue = ((Number) value).longValue();
            if (name.contains("time") || name.contains("duration")) {
                recordTime(name, longValue);
            } else if (name.contains("count") || name.contains("total")) {
                setCounter(name, longValue);
            } else {
                recordGauge(name, longValue);
            }
        } else {
            customMetrics.put(name, value);
        }
    }
    
    // Profiler-specific methods
    public void recordProfilerMetrics(String profilerType, Map<String, Object> metrics) {
        metricsLock.writeLock().lock();
        try {
            ProfilerMetrics profilerMetric = profilerMetrics.computeIfAbsent(profilerType,
                k -> new ProfilerMetrics(profilerType));
            
            profilerMetric.updateMetrics(metrics);
            
            // Also record in general metrics
            for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                String metricName = "profiler." + profilerType + "." + entry.getKey();
                recordMetric(metricName, entry.getValue());
            }
        } finally {
            metricsLock.writeLock().unlock();
        }
    }
    
    public ProfilerMetrics getProfilerMetrics(String profilerType) {
        metricsLock.readLock().lock();
        try {
            return profilerMetrics.get(profilerType);
        } finally {
            metricsLock.readLock().unlock();
        }
    }
    
    public Map<String, ProfilerMetrics> getAllProfilerMetrics() {
        metricsLock.readLock().lock();
        try {
            return new ConcurrentHashMap<>(profilerMetrics);
        } finally {
            metricsLock.readLock().unlock();
        }
    }
    
    // Batch operations for profiler data
    public void recordProfilerBatch(String profilerType, Map<String, Object> batch) {
        metricsLock.writeLock().lock();
        try {
            for (Map.Entry<String, Object> entry : batch.entrySet()) {
                String metricName = "profiler." + profilerType + "." + entry.getKey();
                recordMetric(metricName, entry.getValue());
            }
            
            // Update profiler-specific metrics
            recordProfilerMetrics(profilerType, batch);
        } finally {
            metricsLock.writeLock().unlock();
        }
    }
    
    // Aggregation methods
    public Map<String, Long> getAllCounters() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        counters.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }
    
    public Map<String, Long> getAllTimers() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        timers.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }
    
    public Map<String, Double> getAllGauges() {
        Map<String, Double> result = new ConcurrentHashMap<>();
        gauges.forEach((k, v) -> result.put(k, v.sum()));
        return result;
    }
    
    public Map<String, MetricHistogram> getAllHistograms() {
        return new ConcurrentHashMap<>(histograms);
    }
    
    public Map<String, Object> getAllCustomMetrics() {
        return new ConcurrentHashMap<>(customMetrics);
    }
    
    public Map<String, Object> getAllMetrics() {
        Map<String, Object> allMetrics = new ConcurrentHashMap<>();
        
        // Add all metric types
        getAllCounters().forEach((k, v) -> allMetrics.put(k, v));
        getAllTimers().forEach((k, v) -> allMetrics.put(k, v));
        getAllGauges().forEach((k, v) -> allMetrics.put(k, v));
        getAllCustomMetrics().forEach((k, v) -> allMetrics.put(k, v));
        
        return allMetrics;
    }
    
    // Filtering methods for profiler metrics
    public Map<String, Object> getProfilerMetricsByType(String profilerType) {
        Map<String, Object> result = new ConcurrentHashMap<>();
        String prefix = "profiler." + profilerType + ".";
        
        getAllMetrics().entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(prefix))
            .forEach(entry -> result.put(entry.getKey().substring(prefix.length()), entry.getValue()));
        
        return result;
    }
    
    public void reset() {
        metricsLock.writeLock().lock();
        try {
            counters.clear();
            timers.clear();
            gauges.clear();
            histograms.clear();
            customMetrics.clear();
            profilerMetrics.clear();
        } finally {
            metricsLock.writeLock().unlock();
        }
    }
    
    public void resetProfilerMetrics(String profilerType) {
        metricsLock.writeLock().lock();
        try {
            profilerMetrics.remove(profilerType);
            
            // Remove profiler-specific metrics from general collections
            String prefix = "profiler." + profilerType + ".";
            counters.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
            timers.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
            gauges.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
            histograms.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
            customMetrics.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
        } finally {
            metricsLock.writeLock().unlock();
        }
    }
    
    /**
     * Profiler-specific metrics container.
     */
    public static class ProfilerMetrics {
        private final String profilerType;
        private final Map<String, Object> metrics = new ConcurrentHashMap<>();
        private volatile long lastUpdateTime;
        private final AtomicLong updateCount = new AtomicLong(0);
        
        public ProfilerMetrics(String profilerType) {
            this.profilerType = profilerType;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        public void updateMetrics(Map<String, Object> newMetrics) {
            metrics.putAll(newMetrics);
            lastUpdateTime = System.currentTimeMillis();
            updateCount.incrementAndGet();
        }
        
        public String getProfilerType() { return profilerType; }
        public Map<String, Object> getMetrics() { return new ConcurrentHashMap<>(metrics); }
        public long getLastUpdateTime() { return lastUpdateTime; }
        public long getUpdateCount() { return updateCount.get(); }
        
        @SuppressWarnings("unchecked")
        public <T> T getMetric(String key, Class<T> type) {
            Object value = metrics.get(key);
            if (value != null && type.isInstance(value)) {
                return (T) value;
            }
            return null;
        }
    }
    
    /**
     * Simple histogram implementation for statistical analysis.
     */
    public static class MetricHistogram {
        private final List<Long> values = new ArrayList<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();
        private volatile long count = 0;
        private volatile long sum = 0;
        private volatile long min = Long.MAX_VALUE;
        private volatile long max = Long.MIN_VALUE;
        
        public void record(long value) {
            lock.writeLock().lock();
            try {
                values.add(value);
                count++;
                sum += value;
                min = Math.min(min, value);
                max = Math.max(max, value);
                
                // Keep only last 1000 values for memory efficiency
                if (values.size() > 1000) {
                    values.remove(0);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
        
        public long getCount() { return count; }
        public long getSum() { return sum; }
        public long getMin() { return min == Long.MAX_VALUE ? 0 : min; }
        public long getMax() { return max == Long.MIN_VALUE ? 0 : max; }
        
        public double getAverage() {
            return count > 0 ? (double) sum / count : 0.0;
        }
        
        public long getPercentile(double percentile) {
            lock.readLock().lock();
            try {
                if (values.isEmpty()) return 0;
                
                List<Long> sorted = new ArrayList<>(values);
                sorted.sort(Long::compareTo);
                
                int index = (int) Math.ceil(percentile * sorted.size()) - 1;
                index = Math.max(0, Math.min(index, sorted.size() - 1));
                
                return sorted.get(index);
            } finally {
                lock.readLock().unlock();
            }
        }
    }
}