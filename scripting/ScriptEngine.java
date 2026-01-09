package engine.scripting;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Multi-language script execution engine.
 * Coordinates script loading, compilation, execution, and lifecycle management.
 */
public interface ScriptEngine {
    
    /**
     * Initialize the script engine.
     * @throws ScriptException if initialization fails
     */
    void initialize() throws ScriptException;
    
    /**
     * Shutdown the script engine and cleanup resources.
     * @throws ScriptException if shutdown fails
     */
    void shutdown() throws ScriptException;
    
    /**
     * Load a script from file.
     * @param scriptPath Path to the script file
     * @return CompletableFuture with loaded script
     */
    CompletableFuture<Script> loadScript(Path scriptPath);
    
    /**
     * Load a script from source code.
     * @param scriptId Unique script identifier
     * @param source Script source code
     * @param language Script language
     * @return CompletableFuture with loaded script
     */
    CompletableFuture<Script> loadScript(String scriptId, String source, ScriptLanguage language);
    
    /**
     * Load a script synchronously.
     * @param scriptPath Path to the script file
     * @return Loaded script
     * @throws ScriptException if loading fails
     */
    Script loadScriptSync(Path scriptPath) throws ScriptException;
    
    /**
     * Load a script from source synchronously.
     * @param scriptId Unique script identifier
     * @param source Script source code
     * @param language Script language
     * @return Loaded script
     * @throws ScriptException if loading fails
     */
    Script loadScriptSync(String scriptId, String source, ScriptLanguage language) throws ScriptException;
    
    /**
     * Unload a script by ID.
     * @param scriptId Script ID to unload
     * @return true if script was found and unloaded
     */
    boolean unloadScript(String scriptId);
    
    /**
     * Get a loaded script by ID.
     * @param scriptId Script ID
     * @return Script instance or empty if not found
     */
    Optional<Script> getScript(String scriptId);
    
    /**
     * Get all loaded scripts.
     * @return Collection of all loaded scripts
     */
    Collection<Script> getAllScripts();
    
    /**
     * Get scripts by language.
     * @param language Script language to filter by
     * @return Collection of scripts in the specified language
     */
    Collection<Script> getScriptsByLanguage(ScriptLanguage language);
    
    /**
     * Execute a script by ID.
     * @param scriptId Script ID to execute
     * @param parameters Execution parameters
     * @return CompletableFuture with execution result
     */
    CompletableFuture<ScriptExecutionResult> executeScript(String scriptId, Map<String, Object> parameters);
    
    /**
     * Execute a script synchronously.
     * @param scriptId Script ID to execute
     * @param parameters Execution parameters
     * @return Execution result
     * @throws ScriptException if execution fails
     */
    ScriptExecutionResult executeScriptSync(String scriptId, Map<String, Object> parameters) throws ScriptException;
    
    /**
     * Call a function in a script.
     * @param scriptId Script ID
     * @param functionName Function name to call
     * @param arguments Function arguments
     * @return CompletableFuture with function result
     */
    CompletableFuture<Object> callScriptFunction(String scriptId, String functionName, Object... arguments);
    
    /**
     * Call a function synchronously.
     * @param scriptId Script ID
     * @param functionName Function name to call
     * @param arguments Function arguments
     * @return Function result
     * @throws ScriptException if call fails
     */
    Object callScriptFunctionSync(String scriptId, String functionName, Object... arguments) throws ScriptException;
    
    /**
     * Reload a script from its source.
     * @param scriptId Script ID to reload
     * @return CompletableFuture that completes when reloaded
     */
    CompletableFuture<Script> reloadScript(String scriptId);
    
    /**
     * Compile a script for better performance.
     * @param scriptId Script ID to compile
     * @return CompletableFuture that completes when compiled
     */
    CompletableFuture<Void> compileScript(String scriptId);
    
    /**
     * Set a global variable accessible to all scripts.
     * @param name Variable name
     * @param value Variable value
     */
    void setGlobalVariable(String name, Object value);
    
    /**
     * Get a global variable.
     * @param name Variable name
     * @return Variable value, or null if not found
     */
    Object getGlobalVariable(String name);
    
    /**
     * Remove a global variable.
     * @param name Variable name
     * @return true if variable was removed
     */
    boolean removeGlobalVariable(String name);
    
    /**
     * Get all global variables.
     * @return Map of all global variables
     */
    Map<String, Object> getAllGlobalVariables();
    
    /**
     * Check if a language is supported.
     * @param language Script language to check
     * @return true if language is supported
     */
    boolean isLanguageSupported(ScriptLanguage language);
    
    /**
     * Get supported languages.
     * @return Array of supported languages
     */
    ScriptLanguage[] getSupportedLanguages();
    
    /**
     * Enable or disable hot reloading.
     * @param enabled Hot reload enabled
     */
    void setHotReloadEnabled(boolean enabled);
    
    /**
     * Check if hot reloading is enabled.
     * @return true if hot reload is enabled
     */
    boolean isHotReloadEnabled();
    
    /**
     * Get script engine statistics.
     * @return Engine statistics
     */
    ScriptEngineStatistics getStatistics();
    
    /**
     * Add a script engine listener.
     * @param listener Listener to add
     */
    void addScriptEngineListener(ScriptEngineListener listener);
    
    /**
     * Remove a script engine listener.
     * @param listener Listener to remove
     */
    void removeScriptEngineListener(ScriptEngineListener listener);
    
    /**
     * Script engine listener for lifecycle events.
     */
    interface ScriptEngineListener {
        /**
         * Called when a script is loaded.
         * @param script Loaded script
         */
        default void onScriptLoaded(Script script) {}
        
        /**
         * Called when a script is unloaded.
         * @param scriptId Unloaded script ID
         */
        default void onScriptUnloaded(String scriptId) {}
        
        /**
         * Called when a script is reloaded.
         * @param script Reloaded script
         */
        default void onScriptReloaded(Script script) {}
        
        /**
         * Called when script execution starts.
         * @param scriptId Script ID
         */
        default void onScriptExecutionStarted(String scriptId) {}
        
        /**
         * Called when script execution completes.
         * @param result Execution result
         */
        default void onScriptExecutionCompleted(ScriptExecutionResult result) {}
        
        /**
         * Called when script execution fails.
         * @param scriptId Script ID
         * @param error Error that occurred
         */
        default void onScriptExecutionFailed(String scriptId, ScriptException error) {}
    }
    
    /**
     * Script engine statistics for monitoring.
     */
    class ScriptEngineStatistics {
        private final int totalScripts;
        private final int compiledScripts;
        private final long totalExecutions;
        private final long totalExecutionTime;
        private final long averageExecutionTime;
        private final int hotReloads;
        private final Map<ScriptLanguage, Integer> scriptsByLanguage;
        
        public ScriptEngineStatistics(int totalScripts, int compiledScripts, long totalExecutions,
                                    long totalExecutionTime, long averageExecutionTime, int hotReloads,
                                    Map<ScriptLanguage, Integer> scriptsByLanguage) {
            this.totalScripts = totalScripts;
            this.compiledScripts = compiledScripts;
            this.totalExecutions = totalExecutions;
            this.totalExecutionTime = totalExecutionTime;
            this.averageExecutionTime = averageExecutionTime;
            this.hotReloads = hotReloads;
            this.scriptsByLanguage = scriptsByLanguage;
        }
        
        public int getTotalScripts() { return totalScripts; }
        public int getCompiledScripts() { return compiledScripts; }
        public long getTotalExecutions() { return totalExecutions; }
        public long getTotalExecutionTime() { return totalExecutionTime; }
        public long getAverageExecutionTime() { return averageExecutionTime; }
        public int getHotReloads() { return hotReloads; }
        public Map<ScriptLanguage, Integer> getScriptsByLanguage() { return scriptsByLanguage; }
        
        @Override
        public String toString() {
            return String.format("ScriptEngineStats{scripts=%d, compiled=%d, executions=%d, avgTime=%dms, hotReloads=%d}",
                               totalScripts, compiledScripts, totalExecutions, averageExecutionTime, hotReloads);
        }
    }
}