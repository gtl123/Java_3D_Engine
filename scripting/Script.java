package engine.scripting;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a loaded script with metadata and execution capabilities.
 */
public interface Script {
    
    /**
     * Get the unique script identifier.
     * @return Script ID
     */
    String getId();
    
    /**
     * Get the script name.
     * @return Script name
     */
    String getName();
    
    /**
     * Get the script file path.
     * @return File path
     */
    String getPath();
    
    /**
     * Get the script language.
     * @return Script language
     */
    ScriptLanguage getLanguage();
    
    /**
     * Get the script source code.
     * @return Source code
     */
    String getSource();
    
    /**
     * Get script metadata.
     * @return Metadata map
     */
    Map<String, Object> getMetadata();
    
    /**
     * Get when the script was loaded.
     * @return Load timestamp
     */
    Instant getLoadTime();
    
    /**
     * Get when the script was last modified.
     * @return Last modified timestamp
     */
    Instant getLastModified();
    
    /**
     * Check if the script is compiled.
     * @return true if compiled
     */
    boolean isCompiled();
    
    /**
     * Check if the script supports hot reloading.
     * @return true if hot reload is supported
     */
    boolean supportsHotReload();
    
    /**
     * Execute the script with given parameters.
     * @param parameters Execution parameters
     * @return CompletableFuture with execution result
     */
    CompletableFuture<ScriptExecutionResult> execute(Map<String, Object> parameters);
    
    /**
     * Execute the script synchronously.
     * @param parameters Execution parameters
     * @return Execution result
     * @throws ScriptException if execution fails
     */
    ScriptExecutionResult executeSync(Map<String, Object> parameters) throws ScriptException;
    
    /**
     * Call a specific function in the script.
     * @param functionName Function name to call
     * @param arguments Function arguments
     * @return CompletableFuture with function result
     */
    CompletableFuture<Object> callFunction(String functionName, Object... arguments);
    
    /**
     * Call a function synchronously.
     * @param functionName Function name to call
     * @param arguments Function arguments
     * @return Function result
     * @throws ScriptException if call fails
     */
    Object callFunctionSync(String functionName, Object... arguments) throws ScriptException;
    
    /**
     * Get available functions in the script.
     * @return Array of function names
     */
    String[] getAvailableFunctions();
    
    /**
     * Set a global variable in the script context.
     * @param name Variable name
     * @param value Variable value
     */
    void setGlobal(String name, Object value);
    
    /**
     * Get a global variable from the script context.
     * @param name Variable name
     * @return Variable value, or null if not found
     */
    Object getGlobal(String name);
    
    /**
     * Reload the script from its source.
     * @return CompletableFuture that completes when reloaded
     */
    CompletableFuture<Void> reload();
    
    /**
     * Dispose of the script and free resources.
     */
    void dispose();
    
    /**
     * Check if the script has been disposed.
     * @return true if disposed
     */
    boolean isDisposed();
}