package engine.profiler;

import engine.logging.LogManager;
import engine.camera.Camera;
import engine.io.Window;

/**
 * System-specific profiling utilities for easy integration across engine systems.
 * Provides specialized profiling methods for different engine components.
 */
public class SystemProfilers {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final ProfilerAPI profilerAPI = ProfilerAPI.getInstance();
    
    /**
     * Audio System Profiler
     * Specialized profiling for audio operations.
     */
    public static class AudioProfiler {
        
        /**
         * Profile audio buffer processing.
         * @param bufferSize Size of audio buffer
         * @param sampleRate Sample rate
         * @param code Audio processing code
         */
        public static void profileAudioProcessing(int bufferSize, int sampleRate, Runnable code) {
            String profileName = String.format("Audio.Processing[%d@%dHz]", bufferSize, sampleRate);
            profilerAPI.profile(profileName, code);
        }
        
        /**
         * Record audio latency measurement.
         * @param latencyMs Audio latency in milliseconds
         */
        public static void recordAudioLatency(double latencyMs) {
            profilerAPI.startTimer("Audio.Latency");
            // In a real implementation, this would measure actual audio latency
            profilerAPI.stopTimer("Audio.Latency");
        }
        
        /**
         * Profile sound loading operation.
         * @param soundFile Sound file being loaded
         * @param code Loading code
         */
        public static void profileSoundLoading(String soundFile, Runnable code) {
            profilerAPI.profile("Audio.LoadSound[" + soundFile + "]", code);
        }
    }
    
    /**
     * Asset System Profiler
     * Specialized profiling for asset loading and management.
     */
    public static class AssetProfiler {
        
        /**
         * Profile asset loading operation.
         * @param assetType Type of asset (texture, model, etc.)
         * @param assetPath Path to asset
         * @param code Loading code
         * @param <T> Asset type
         * @return Loaded asset
         */
        public static <T> T profileAssetLoading(String assetType, String assetPath, 
                                              java.util.function.Supplier<T> code) {
            String profileName = String.format("Asset.Load[%s:%s]", assetType, assetPath);
            return profilerAPI.profile(profileName, code);
        }
        
        /**
         * Track asset memory usage.
         * @param assetType Type of asset
         * @param memorySize Memory size in bytes
         */
        public static void trackAssetMemory(String assetType, long memorySize) {
            profilerAPI.trackAllocation(assetType, memorySize);
        }
        
        /**
         * Profile asset cache operations.
         * @param operation Cache operation (get, put, evict)
         * @param assetId Asset identifier
         * @param code Cache operation code
         */
        public static void profileCacheOperation(String operation, String assetId, Runnable code) {
            profilerAPI.profile("Asset.Cache." + operation + "[" + assetId + "]", code);
        }
    }
    
    /**
     * Physics System Profiler
     * Specialized profiling for physics simulation.
     */
    public static class PhysicsProfiler {
        
        /**
         * Profile physics simulation step.
         * @param deltaTime Time step
         * @param objectCount Number of physics objects
         * @param code Simulation code
         */
        public static void profileSimulationStep(float deltaTime, int objectCount, Runnable code) {
            String profileName = String.format("Physics.Simulation[dt=%.3f,objects=%d]", deltaTime, objectCount);
            profilerAPI.profile(profileName, code);
        }
        
        /**
         * Profile collision detection.
         * @param collisionPairs Number of collision pairs to check
         * @param code Collision detection code
         */
        public static void profileCollisionDetection(int collisionPairs, Runnable code) {
            profilerAPI.profile("Physics.CollisionDetection[pairs=" + collisionPairs + "]", code);
        }
        
        /**
         * Profile constraint solving.
         * @param constraintCount Number of constraints
         * @param iterations Solver iterations
         * @param code Constraint solving code
         */
        public static void profileConstraintSolving(int constraintCount, int iterations, Runnable code) {
            String profileName = String.format("Physics.ConstraintSolver[constraints=%d,iter=%d]", 
                                             constraintCount, iterations);
            profilerAPI.profile(profileName, code);
        }
    }
    
    /**
     * AI System Profiler
     * Specialized profiling for AI operations.
     */
    public static class AIProfiler {
        
        /**
         * Profile pathfinding operation.
         * @param algorithm Pathfinding algorithm used
         * @param startNode Start node
         * @param endNode End node
         * @param code Pathfinding code
         * @param <T> Path type
         * @return Computed path
         */
        public static <T> T profilePathfinding(String algorithm, String startNode, String endNode,
                                             java.util.function.Supplier<T> code) {
            String profileName = String.format("AI.Pathfinding[%s:%s->%s]", algorithm, startNode, endNode);
            return profilerAPI.profile(profileName, code);
        }
        
        /**
         * Profile AI decision making.
         * @param aiType Type of AI (NPC, enemy, etc.)
         * @param decisionCount Number of decisions to make
         * @param code Decision making code
         */
        public static void profileDecisionMaking(String aiType, int decisionCount, Runnable code) {
            String profileName = String.format("AI.DecisionMaking[%s:decisions=%d]", aiType, decisionCount);
            profilerAPI.profile(profileName, code);
        }
        
        /**
         * Profile behavior tree execution.
         * @param treeId Behavior tree identifier
         * @param nodeCount Number of nodes in tree
         * @param code Behavior tree execution code
         */
        public static void profileBehaviorTree(String treeId, int nodeCount, Runnable code) {
            String profileName = String.format("AI.BehaviorTree[%s:nodes=%d]", treeId, nodeCount);
            profilerAPI.profile(profileName, code);
        }
    }
    
    /**
     * Input System Profiler
     * Specialized profiling for input processing.
     */
    public static class InputProfiler {
        
        /**
         * Profile input event processing.
         * @param eventType Type of input event
         * @param eventCount Number of events to process
         * @param code Event processing code
         */
        public static void profileInputProcessing(String eventType, int eventCount, Runnable code) {
            String profileName = String.format("Input.Processing[%s:events=%d]", eventType, eventCount);
            profilerAPI.profile(profileName, code);
        }
        
        /**
         * Profile input polling.
         * @param deviceType Input device type
         * @param code Polling code
         */
        public static void profileInputPolling(String deviceType, Runnable code) {
            profilerAPI.profile("Input.Polling[" + deviceType + "]", code);
        }
        
        /**
         * Record input latency.
         * @param inputType Type of input
         * @param latencyMs Input latency in milliseconds
         */
        public static void recordInputLatency(String inputType, double latencyMs) {
            // In a real implementation, this would track input-to-response latency
            profilerAPI.startTimer("Input.Latency[" + inputType + "]");
            try {
                Thread.sleep((long) latencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            profilerAPI.stopTimer("Input.Latency[" + inputType + "]");
        }
    }
    
    /**
     * Scripting System Profiler
     * Specialized profiling for scripting operations.
     */
    public static class ScriptProfiler {
        
        /**
         * Profile script execution.
         * @param scriptLanguage Scripting language (JavaScript, Lua, etc.)
         * @param scriptName Script name
         * @param code Script execution code
         * @param <T> Return type
         * @return Script result
         */
        public static <T> T profileScriptExecution(String scriptLanguage, String scriptName,
                                                 java.util.function.Supplier<T> code) {
            String profileName = String.format("Script.Execute[%s:%s]", scriptLanguage, scriptName);
            return profilerAPI.profile(profileName, code);
        }
        
        /**
         * Profile script compilation.
         * @param scriptLanguage Scripting language
         * @param scriptSize Script size in bytes
         * @param code Compilation code
         */
        public static void profileScriptCompilation(String scriptLanguage, long scriptSize, Runnable code) {
            String profileName = String.format("Script.Compile[%s:size=%d]", scriptLanguage, scriptSize);
            profilerAPI.profile(profileName, code);
        }
        
        /**
         * Track script memory usage.
         * @param scriptName Script name
         * @param memoryUsage Memory usage in bytes
         */
        public static void trackScriptMemory(String scriptName, long memoryUsage) {
            profilerAPI.trackAllocation("Script[" + scriptName + "]", memoryUsage);
        }
    }
    
    /**
     * Game Logic Profiler
     * Specialized profiling for game logic operations.
     */
    public static class GameLogicProfiler {
        
        /**
         * Profile game state update.
         * @param deltaTime Time since last update
         * @param entityCount Number of entities to update
         * @param code Update code
         */
        public static void profileGameUpdate(float deltaTime, int entityCount, Runnable code) {
            String profileName = String.format("Game.Update[dt=%.3f,entities=%d]", deltaTime, entityCount);
            profilerAPI.profile(profileName, code);
        }
        
        /**
         * Profile entity system processing.
         * @param systemName Name of entity system
         * @param entityCount Number of entities processed
         * @param code System processing code
         */
        public static void profileEntitySystem(String systemName, int entityCount, Runnable code) {
            String profileName = String.format("Game.EntitySystem[%s:entities=%d]", systemName, entityCount);
            profilerAPI.profile(profileName, code);
        }
        
        /**
         * Profile game event processing.
         * @param eventType Type of game event
         * @param eventCount Number of events
         * @param code Event processing code
         */
        public static void profileEventProcessing(String eventType, int eventCount, Runnable code) {
            String profileName = String.format("Game.Events[%s:count=%d]", eventType, eventCount);
            profilerAPI.profile(profileName, code);
        }
    }
    
    /**
     * UI System Profiler
     * Specialized profiling for user interface operations.
     */
    public static class UIProfiler {
        
        /**
         * Profile UI rendering.
         * @param uiElementCount Number of UI elements
         * @param code UI rendering code
         */
        public static void profileUIRendering(int uiElementCount, Runnable code) {
            String profileName = String.format("UI.Render[elements=%d]", uiElementCount);
            profilerAPI.profile(profileName, code);
        }
        
        /**
         * Profile UI layout calculation.
         * @param layoutType Type of layout (flex, grid, etc.)
         * @param elementCount Number of elements in layout
         * @param code Layout calculation code
         */
        public static void profileLayoutCalculation(String layoutType, int elementCount, Runnable code) {
            String profileName = String.format("UI.Layout[%s:elements=%d]", layoutType, elementCount);
            profilerAPI.profile(profileName, code);
        }
        
        /**
         * Profile UI event handling.
         * @param eventType Type of UI event
         * @param code Event handling code
         */
        public static void profileUIEventHandling(String eventType, Runnable code) {
            profilerAPI.profile("UI.EventHandling[" + eventType + "]", code);
        }
    }
    
    /**
     * Utility methods for common profiling patterns.
     */
    public static class Utils {
        
        /**
         * Profile a complete frame with automatic begin/end.
         * @param frameNumber Frame number
         * @param code Frame processing code
         */
        public static void profileFrame(long frameNumber, Runnable code) {
            profilerAPI.beginRenderFrame();
            try {
                profilerAPI.profile("Frame[" + frameNumber + "]", code);
            } finally {
                profilerAPI.endRenderFrame();
            }
        }
        
        /**
         * Profile a render pass with automatic begin/end.
         * @param passName Name of render pass
         * @param code Render pass code
         */
        public static void profileRenderPass(String passName, Runnable code) {
            profilerAPI.beginRenderPass(passName);
            try {
                code.run();
            } finally {
                profilerAPI.endRenderPass(passName);
            }
        }
        
        /**
         * Profile memory allocation with automatic tracking.
         * @param objectType Type of object being allocated
         * @param allocationCode Allocation code
         * @param <T> Object type
         * @return Allocated object
         */
        public static <T> T profileAllocation(String objectType, java.util.function.Supplier<T> allocationCode) {
            long beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            T result = allocationCode.get();
            long afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            
            long allocatedMemory = afterMemory - beforeMemory;
            if (allocatedMemory > 0) {
                profilerAPI.trackAllocation(objectType, allocatedMemory);
            }
            
            return result;
        }
        
        /**
         * Create a profiling scope that automatically starts and stops timing.
         * @param scopeName Name of the profiling scope
         * @return AutoCloseable profiling scope
         */
        public static AutoCloseable createProfilingScope(String scopeName) {
            profilerAPI.startTimer(scopeName);
            return () -> profilerAPI.stopTimer(scopeName);
        }
        
        /**
         * Profile a method with exception handling.
         * @param methodName Method name
         * @param code Method code
         * @param <T> Return type
         * @return Method result
         * @throws Exception if method throws exception
         */
        public static <T> T profileMethodWithExceptions(String methodName, 
                                                       java.util.function.Supplier<T> code) throws Exception {
            profilerAPI.startTimer(methodName);
            try {
                return code.get();
            } catch (Exception e) {
                logManager.error("SystemProfilers", "Exception in profiled method", e, "method", methodName);
                throw e;
            } finally {
                profilerAPI.stopTimer(methodName);
            }
        }
        
        /**
         * Batch profile multiple operations.
         * @param batchName Name for the batch
         * @param operations Array of operations to profile
         */
        public static void profileBatch(String batchName, Runnable... operations) {
            profilerAPI.startTimer(batchName);
            try {
                for (int i = 0; i < operations.length; i++) {
                    profilerAPI.profile(batchName + ".Operation[" + i + "]", operations[i]);
                }
            } finally {
                profilerAPI.stopTimer(batchName);
            }
        }
    }
    
    /**
     * Integration helpers for easy profiler setup in different systems.
     */
    public static class Integration {
        
        /**
         * Initialize profiling for a specific system.
         * @param systemName Name of the system
         * @return true if profiling was successfully initialized
         */
        public static boolean initializeSystemProfiling(String systemName) {
            try {
                if (!profilerAPI.isProfilingActive()) {
                    profilerAPI.initialize();
                    profilerAPI.enableProfiling();
                }
                
                logManager.info("SystemProfilers", "Profiling initialized for system", "system", systemName);
                return true;
            } catch (Exception e) {
                logManager.error("SystemProfilers", "Failed to initialize profiling for system", e, 
                               "system", systemName);
                return false;
            }
        }
        
        /**
         * Create a system-specific profiler configuration.
         * @param systemName System name
         * @param enablePerformance Enable performance profiling
         * @param enableMemory Enable memory profiling
         * @param enableNetwork Enable network profiling
         * @param enableRender Enable render profiling
         * @return Configured ProfilerConfiguration
         */
        public static ProfilerConfiguration createSystemConfiguration(String systemName,
                                                                    boolean enablePerformance,
                                                                    boolean enableMemory,
                                                                    boolean enableNetwork,
                                                                    boolean enableRender) {
            ProfilerConfiguration config = new ProfilerConfiguration();
            config.setEnabled(true);
            config.setPerformanceProfilingEnabled(enablePerformance);
            config.setMemoryProfilingEnabled(enableMemory);
            config.setNetworkProfilingEnabled(enableNetwork);
            config.setRenderProfilingEnabled(enableRender);
            
            logManager.info("SystemProfilers", "Created profiler configuration for system", 
                           "system", systemName,
                           "performance", enablePerformance,
                           "memory", enableMemory,
                           "network", enableNetwork,
                           "render", enableRender);
            
            return config;
        }
        
        /**
         * Generate a system-specific profiling report.
         * @param systemName System name
         * @param format Report format ("json" or "csv")
         * @return Path to generated report
         */
        public static String generateSystemReport(String systemName, String format) {
            String filename = String.format("%s_profiling_report_%d.%s", 
                                           systemName.toLowerCase(), 
                                           System.currentTimeMillis(), 
                                           format.toLowerCase());
            
            if ("json".equalsIgnoreCase(format)) {
                return profilerAPI.generateJSONReport(filename);
            } else if ("csv".equalsIgnoreCase(format)) {
                return profilerAPI.generateCSVReport(filename);
            } else {
                logManager.error("SystemProfilers", "Unsupported report format", "format", format);
                return null;
            }
        }
    }
}