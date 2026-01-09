package engine.profiler;

import engine.logging.LogManager;
import engine.logging.MetricsCollector;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * CPU and GPU performance profiler with detailed metrics collection.
 * Provides method-level profiling, call stack tracking, and GPU timing.
 */
public class PerformanceProfiler implements IProfiler {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final MetricsCollector metricsCollector = logManager.getMetricsCollector();
    
    private final ProfilerConfiguration config;
    private final AtomicBoolean active = new AtomicBoolean(false);
    
    // CPU profiling
    private final ThreadMXBean threadBean;
    private final Map<String, MethodProfile> methodProfiles = new ConcurrentHashMap<>();
    private final ThreadLocal<CallStack> callStacks = new ThreadLocal<CallStack>() {
        @Override
        protected CallStack initialValue() {
            return new CallStack(config.getMaxCallStackDepth());
        }
    };
    
    // GPU profiling
    private final Queue<GPUQuery> availableQueries = new ConcurrentLinkedQueue<>();
    private final Queue<GPUQuery> activeQueries = new ConcurrentLinkedQueue<>();
    private final Map<String, GPUProfile> gpuProfiles = new ConcurrentHashMap<>();
    
    // Frame timing
    private final AtomicLong frameCount = new AtomicLong(0);
    private final AtomicReference<Double> currentFPS = new AtomicReference<>(0.0);
    private final AtomicReference<Double> averageFrameTime = new AtomicReference<>(0.0);
    private final AtomicReference<Double> minFrameTime = new AtomicReference<>(Double.MAX_VALUE);
    private final AtomicReference<Double> maxFrameTime = new AtomicReference<>(0.0);
    
    // Sampling
    private long lastCpuSampleTime = 0;
    private long lastFrameTime = System.nanoTime();
    
    public PerformanceProfiler(ProfilerConfiguration config) {
        this.config = config;
        this.threadBean = ManagementFactory.getThreadMXBean();
        
        // Enable CPU time measurement if supported
        if (threadBean.isCurrentThreadCpuTimeSupported()) {
            threadBean.setThreadCpuTimeEnabled(true);
        }
    }
    
    @Override
    public void initialize() {
        logManager.info("PerformanceProfiler", "Initializing performance profiler",
                       "cpuProfiling", config.isCpuProfilingEnabled(),
                       "gpuProfiling", config.isGpuProfilingEnabled(),
                       "methodLevelProfiling", config.isMethodLevelProfiling());
        
        // Initialize GPU queries if GPU profiling is enabled
        if (config.isGpuProfilingEnabled() && config.isGpuTimingEnabled()) {
            initializeGPUQueries();
        }
        
        logManager.info("PerformanceProfiler", "Performance profiler initialized");
    }
    
    @Override
    public void start() {
        if (active.get()) {
            return;
        }
        
        logManager.info("PerformanceProfiler", "Starting performance profiling");
        
        // Reset all profiles
        methodProfiles.clear();
        gpuProfiles.clear();
        
        // Reset frame timing
        frameCount.set(0);
        currentFPS.set(0.0);
        averageFrameTime.set(0.0);
        minFrameTime.set(Double.MAX_VALUE);
        maxFrameTime.set(0.0);
        
        lastFrameTime = System.nanoTime();
        lastCpuSampleTime = System.currentTimeMillis();
        
        active.set(true);
        
        logManager.info("PerformanceProfiler", "Performance profiling started");
    }
    
    @Override
    public void stop() {
        if (!active.get()) {
            return;
        }
        
        logManager.info("PerformanceProfiler", "Stopping performance profiling");
        
        active.set(false);
        
        // Process any remaining GPU queries
        processGPUQueries();
        
        logManager.info("PerformanceProfiler", "Performance profiling stopped");
    }
    
    @Override
    public void update(float deltaTime) {
        if (!active.get()) {
            return;
        }
        
        // Update frame timing
        updateFrameTiming();
        
        // Process GPU queries
        if (config.isGpuProfilingEnabled()) {
            processGPUQueries();
        }
        
        // Sample CPU usage periodically
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCpuSampleTime >= config.getCpuSamplingIntervalMs()) {
            sampleCPUUsage();
            lastCpuSampleTime = currentTime;
        }
    }
    
    @Override
    public ProfilerData collectData() {
        ProfilerData data = new ProfilerData("performance");
        
        // Frame timing metrics
        data.addMetric("frameCount", frameCount.get());
        data.addMetric("currentFPS", currentFPS.get());
        data.addMetric("averageFrameTime", averageFrameTime.get());
        data.addMetric("minFrameTime", minFrameTime.get());
        data.addMetric("maxFrameTime", maxFrameTime.get());
        
        // CPU profiling metrics
        if (config.isCpuProfilingEnabled()) {
            collectCPUMetrics(data);
        }
        
        // GPU profiling metrics
        if (config.isGpuProfilingEnabled()) {
            collectGPUMetrics(data);
        }
        
        // Add metadata
        data.addMetadata("cpuProfilingEnabled", config.isCpuProfilingEnabled());
        data.addMetadata("gpuProfilingEnabled", config.isGpuProfilingEnabled());
        data.addMetadata("methodLevelProfiling", config.isMethodLevelProfiling());
        data.addMetadata("callStackTracking", config.isCallStackTracking());
        
        return data;
    }
    
    @Override
    public void reset() {
        logManager.info("PerformanceProfiler", "Resetting performance profiler");
        
        methodProfiles.clear();
        gpuProfiles.clear();
        
        frameCount.set(0);
        currentFPS.set(0.0);
        averageFrameTime.set(0.0);
        minFrameTime.set(Double.MAX_VALUE);
        maxFrameTime.set(0.0);
        
        lastFrameTime = System.nanoTime();
    }
    
    @Override
    public void cleanup() {
        logManager.info("PerformanceProfiler", "Cleaning up performance profiler");
        
        stop();
        
        // Cleanup GPU queries
        if (config.isGpuProfilingEnabled()) {
            cleanupGPUQueries();
        }
        
        methodProfiles.clear();
        gpuProfiles.clear();
        
        logManager.info("PerformanceProfiler", "Performance profiler cleanup complete");
    }
    
    @Override
    public boolean isActive() {
        return active.get();
    }
    
    @Override
    public String getProfilerType() {
        return "performance";
    }
    
    /**
     * Start profiling a method call.
     */
    public void startMethod(String methodName) {
        if (!active.get() || !config.isMethodLevelProfiling()) {
            return;
        }
        
        long startTime = System.nanoTime();
        long cpuTime = threadBean.isCurrentThreadCpuTimeSupported() ? 
                      threadBean.getCurrentThreadCpuTime() : 0;
        
        CallStack callStack = callStacks.get();
        callStack.push(methodName, startTime, cpuTime);
        
        // Update method profile
        MethodProfile profile = methodProfiles.computeIfAbsent(methodName, k -> new MethodProfile(k));
        profile.startCall(startTime);
    }
    
    /**
     * End profiling a method call.
     */
    public void endMethod(String methodName) {
        if (!active.get() || !config.isMethodLevelProfiling()) {
            return;
        }
        
        long endTime = System.nanoTime();
        long cpuTime = threadBean.isCurrentThreadCpuTimeSupported() ? 
                      threadBean.getCurrentThreadCpuTime() : 0;
        
        CallStack callStack = callStacks.get();
        CallStack.MethodCall call = callStack.pop();
        
        if (call != null && call.methodName.equals(methodName)) {
            long duration = endTime - call.startTime;
            long cpuDuration = cpuTime - call.cpuStartTime;
            
            // Update method profile
            MethodProfile profile = methodProfiles.get(methodName);
            if (profile != null) {
                profile.endCall(endTime, duration, cpuDuration);
            }
            
            // Record metrics
            metricsCollector.recordTime("method." + methodName, duration / 1_000_000); // Convert to ms
        }
    }
    
    /**
     * Start GPU timing for a specific operation.
     */
    public void startGPUTiming(String operationName) {
        if (!active.get() || !config.isGpuProfilingEnabled() || !config.isGpuTimingEnabled()) {
            return;
        }
        
        GPUQuery query = availableQueries.poll();
        if (query == null) {
            // Create new query if none available
            query = new GPUQuery(operationName);
        } else {
            query.operationName = operationName;
        }
        
        // Start GPU timer query
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query.queryId);
        query.startTime = System.nanoTime();
        query.active = true;
        
        activeQueries.offer(query);
    }
    
    /**
     * End GPU timing for a specific operation.
     */
    public void endGPUTiming(String operationName) {
        if (!active.get() || !config.isGpuProfilingEnabled() || !config.isGpuTimingEnabled()) {
            return;
        }
        
        // End the most recent query for this operation
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
    }
    
    private void initializeGPUQueries() {
        // Pre-allocate GPU query objects
        for (int i = 0; i < config.getMaxGpuQueries(); i++) {
            GPUQuery query = new GPUQuery("");
            availableQueries.offer(query);
        }
        
        logManager.debug("PerformanceProfiler", "GPU queries initialized", 
                        "count", config.getMaxGpuQueries());
    }
    
    private void processGPUQueries() {
        GPUQuery query;
        while ((query = activeQueries.peek()) != null) {
            if (!query.active) {
                activeQueries.poll();
                continue;
            }
            
            // Check if query result is available
            int available = GL15.glGetQueryObjecti(query.queryId, GL15.GL_QUERY_RESULT_AVAILABLE);
            if (available == GL15.GL_TRUE) {
                // Get the result
                long gpuTime = GL15.glGetQueryObjecti64(query.queryId, GL15.GL_QUERY_RESULT);
                long endTime = System.nanoTime();
                
                // Update GPU profile
                GPUProfile profile = gpuProfiles.computeIfAbsent(query.operationName, 
                                                                k -> new GPUProfile(k));
                profile.addSample(gpuTime, endTime - query.startTime);
                
                // Record metrics
                metricsCollector.recordTime("gpu." + query.operationName, gpuTime / 1_000_000); // Convert to ms
                
                // Return query to available pool
                query.active = false;
                availableQueries.offer(activeQueries.poll());
            } else {
                // Query not ready yet, stop processing
                break;
            }
        }
    }
    
    private void cleanupGPUQueries() {
        // Delete all GPU query objects
        while (!availableQueries.isEmpty()) {
            GPUQuery query = availableQueries.poll();
            GL15.glDeleteQueries(query.queryId);
        }
        
        while (!activeQueries.isEmpty()) {
            GPUQuery query = activeQueries.poll();
            GL15.glDeleteQueries(query.queryId);
        }
    }
    
    private void updateFrameTiming() {
        long currentTime = System.nanoTime();
        double frameTime = (currentTime - lastFrameTime) / 1_000_000.0; // Convert to milliseconds
        lastFrameTime = currentTime;
        
        frameCount.incrementAndGet();
        
        // Update frame time statistics
        minFrameTime.updateAndGet(current -> Math.min(current, frameTime));
        maxFrameTime.updateAndGet(current -> Math.max(current, frameTime));
        
        // Calculate rolling average (simple exponential moving average)
        double alpha = 0.1; // Smoothing factor
        averageFrameTime.updateAndGet(current -> current * (1 - alpha) + frameTime * alpha);
        
        // Update FPS (calculate every 60 frames for stability)
        if (frameCount.get() % 60 == 0) {
            double fps = 1000.0 / averageFrameTime.get();
            currentFPS.set(fps);
        }
    }
    
    private void sampleCPUUsage() {
        // Sample overall CPU usage and thread-specific metrics
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        metricsCollector.incrementCounter("cpu.samples");
        metricsCollector.recordTime("memory.used", usedMemory / (1024 * 1024)); // MB
    }
    
    private void collectCPUMetrics(ProfilerData data) {
        // Method profiling metrics
        int totalMethods = methodProfiles.size();
        long totalCalls = 0;
        double totalTime = 0.0;
        
        for (MethodProfile profile : methodProfiles.values()) {
            totalCalls += profile.callCount;
            totalTime += profile.totalTime;
        }
        
        data.addMetric("methodCount", totalMethods);
        data.addMetric("totalMethodCalls", totalCalls);
        data.addMetric("totalMethodTime", totalTime);
        
        if (totalCalls > 0) {
            data.addMetric("averageMethodTime", totalTime / totalCalls);
        }
        
        // Find hottest methods
        MethodProfile hottestMethod = methodProfiles.values().stream()
            .max((a, b) -> Double.compare(a.totalTime, b.totalTime))
            .orElse(null);
        
        if (hottestMethod != null) {
            data.addMetric("hottestMethod", hottestMethod.methodName);
            data.addMetric("hottestMethodTime", hottestMethod.totalTime);
        }
    }
    
    private void collectGPUMetrics(ProfilerData data) {
        // GPU profiling metrics
        int totalOperations = gpuProfiles.size();
        long totalGpuTime = 0;
        
        for (GPUProfile profile : gpuProfiles.values()) {
            totalGpuTime += profile.totalGpuTime;
        }
        
        data.addMetric("gpuOperationCount", totalOperations);
        data.addMetric("totalGpuTime", totalGpuTime / 1_000_000.0); // Convert to ms
        
        // Find most expensive GPU operation
        GPUProfile expensiveOperation = gpuProfiles.values().stream()
            .max((a, b) -> Long.compare(a.totalGpuTime, b.totalGpuTime))
            .orElse(null);
        
        if (expensiveOperation != null) {
            data.addMetric("expensiveGpuOperation", expensiveOperation.operationName);
            data.addMetric("expensiveGpuOperationTime", expensiveOperation.totalGpuTime / 1_000_000.0);
        }
    }
    
    // Helper classes
    private static class MethodProfile {
        final String methodName;
        long callCount = 0;
        double totalTime = 0.0; // in nanoseconds
        double totalCpuTime = 0.0; // in nanoseconds
        double minTime = Double.MAX_VALUE;
        double maxTime = 0.0;
        long lastStartTime = 0;
        
        MethodProfile(String methodName) {
            this.methodName = methodName;
        }
        
        void startCall(long startTime) {
            lastStartTime = startTime;
        }
        
        void endCall(long endTime, long duration, long cpuDuration) {
            callCount++;
            totalTime += duration;
            totalCpuTime += cpuDuration;
            
            double durationMs = duration / 1_000_000.0;
            minTime = Math.min(minTime, durationMs);
            maxTime = Math.max(maxTime, durationMs);
        }
    }
    
    private static class GPUProfile {
        final String operationName;
        long sampleCount = 0;
        long totalGpuTime = 0; // in nanoseconds
        long totalCpuTime = 0; // in nanoseconds
        long minGpuTime = Long.MAX_VALUE;
        long maxGpuTime = 0;
        
        GPUProfile(String operationName) {
            this.operationName = operationName;
        }
        
        void addSample(long gpuTime, long cpuTime) {
            sampleCount++;
            totalGpuTime += gpuTime;
            totalCpuTime += cpuTime;
            minGpuTime = Math.min(minGpuTime, gpuTime);
            maxGpuTime = Math.max(maxGpuTime, gpuTime);
        }
    }
    
    private static class GPUQuery {
        final int queryId;
        String operationName;
        long startTime;
        boolean active = false;
        
        GPUQuery(String operationName) {
            this.operationName = operationName;
            this.queryId = GL15.glGenQueries();
        }
    }
    
    private static class CallStack {
        private final MethodCall[] stack;
        private int top = -1;
        private final int maxDepth;
        
        CallStack(int maxDepth) {
            this.maxDepth = maxDepth;
            this.stack = new MethodCall[maxDepth];
            for (int i = 0; i < maxDepth; i++) {
                stack[i] = new MethodCall();
            }
        }
        
        void push(String methodName, long startTime, long cpuStartTime) {
            if (top < maxDepth - 1) {
                top++;
                MethodCall call = stack[top];
                call.methodName = methodName;
                call.startTime = startTime;
                call.cpuStartTime = cpuStartTime;
            }
        }
        
        MethodCall pop() {
            if (top >= 0) {
                return stack[top--];
            }
            return null;
        }
        
        static class MethodCall {
            String methodName;
            long startTime;
            long cpuStartTime;
        }
    }
}