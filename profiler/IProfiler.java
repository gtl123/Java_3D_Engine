package engine.profiler;

/**
 * Base interface for all profiler components.
 * Defines the common lifecycle and data collection methods.
 */
public interface IProfiler {
    
    /**
     * Initialize the profiler.
     * Called once during profiler setup.
     */
    void initialize();
    
    /**
     * Start profiling.
     * Begin collecting performance data.
     */
    void start();
    
    /**
     * Stop profiling.
     * Stop collecting performance data.
     */
    void stop();
    
    /**
     * Update the profiler.
     * Called each frame to update profiling data.
     * 
     * @param deltaTime Time since last update in seconds
     */
    void update(float deltaTime);
    
    /**
     * Collect current profiling data.
     * 
     * @return ProfilerData containing current metrics
     */
    ProfilerData collectData();
    
    /**
     * Reset profiler state and clear collected data.
     */
    void reset();
    
    /**
     * Cleanup profiler resources.
     * Called during shutdown.
     */
    void cleanup();
    
    /**
     * Check if the profiler is currently active.
     * 
     * @return true if profiler is active, false otherwise
     */
    boolean isActive();
    
    /**
     * Get the profiler type identifier.
     * 
     * @return String identifying the profiler type
     */
    String getProfilerType();
}