package engine.scripting;

import engine.logging.LogManager;
import java.util.*;

/**
 * Simplified JavaScript language engine for demonstration purposes
 * This is a mock implementation that doesn't require GraalJS dependency
 */
public class SimplifiedJavaScriptLanguageEngine implements LanguageEngine {
    private static final LogManager logManager = LogManager.getInstance();
    
    @Override
    public ScriptLanguage getLanguage() {
        return ScriptLanguage.JAVASCRIPT;
    }
    
    @Override
    public Script createScript(String scriptId, String source) throws ScriptException {
        return new SimplifiedJavaScriptScript(scriptId, source);
    }
    
    @Override
    public void initialize() throws ScriptException {
        logManager.info("Initialized simplified JavaScript engine");
    }
    
    @Override
    public void cleanup() {
        logManager.info("Cleaned up simplified JavaScript engine");
    }
    
    @Override
    public boolean isInitialized() {
        return true;
    }
    
    @Override
    public Map<String, Object> getEngineInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("engine", "SimplifiedJavaScript");
        info.put("version", "1.0.0");
        info.put("language", "JavaScript");
        return info;
    }
    
    private static class SimplifiedJavaScriptScript implements Script {
        private final String scriptId;
        private final String source;
        private final Map<String, Object> globals = new HashMap<>();
        private final Map<String, Object> metadata = new HashMap<>();
        private boolean loaded = false;
        
        public SimplifiedJavaScriptScript(String scriptId, String source) {
            this.scriptId = scriptId;
            this.source = source;
            this.metadata.put("language", "JavaScript");
            this.metadata.put("created", System.currentTimeMillis());
        }
        
        @Override
        public String getId() {
            return scriptId;
        }
        
        @Override
        public ScriptLanguage getLanguage() {
            return ScriptLanguage.JAVASCRIPT;
        }
        
        @Override
        public String getSource() {
            return source;
        }
        
        @Override
        public ScriptExecutionResult execute() throws ScriptException {
            logManager.info("Executing simplified JavaScript script: " + scriptId);
            loaded = true;
            
            // Simulate script execution
            long startTime = System.currentTimeMillis();
            
            // Mock execution - just return success
            try {
                Thread.sleep(15); // Simulate some work
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            return new ScriptExecutionResult(
                ScriptExecutionResult.Status.SUCCESS,
                "Script executed successfully (simplified)",
                executionTime,
                0
            );
        }
        
        @Override
        public ScriptExecutionResult callFunction(String functionName, Object... arguments) throws ScriptException {
            logManager.info("Calling simplified JavaScript function: " + functionName + " in script: " + scriptId);
            
            long startTime = System.currentTimeMillis();
            
            // Mock function call with some JavaScript-like behavior
            String result;
            switch (functionName) {
                case "init":
                    result = "JavaScript initialization complete";
                    break;
                case "update":
                    result = "JavaScript update called with deltaTime: " + (arguments.length > 0 ? arguments[0] : "0");
                    break;
                case "render":
                    result = "JavaScript render called";
                    break;
                default:
                    result = "Function " + functionName + " called with " + arguments.length + " arguments";
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            return new ScriptExecutionResult(
                ScriptExecutionResult.Status.SUCCESS,
                result,
                executionTime,
                0
            );
        }
        
        @Override
        public void setGlobalVariable(String name, Object value) {
            globals.put(name, value);
        }
        
        @Override
        public Object getGlobalVariable(String name) {
            return globals.get(name);
        }
        
        @Override
        public Map<String, Object> getAllGlobalVariables() {
            return new HashMap<>(globals);
        }
        
        @Override
        public boolean hasFunction(String functionName) {
            // For simplified version, assume common functions exist
            return Set.of("init", "update", "render", "cleanup", "onInput", "onEvent").contains(functionName);
        }
        
        @Override
        public Set<String> getFunctionNames() {
            // Return some mock function names
            return Set.of("init", "update", "render", "cleanup", "onInput", "onEvent");
        }
        
        @Override
        public void reload() throws ScriptException {
            logManager.info("Reloading simplified JavaScript script: " + scriptId);
            loaded = false;
            globals.clear();
        }
        
        @Override
        public boolean isLoaded() {
            return loaded;
        }
        
        @Override
        public Map<String, Object> getMetadata() {
            return new HashMap<>(metadata);
        }
        
        @Override
        public void cleanup() {
            globals.clear();
            loaded = false;
            logManager.info("Cleaned up simplified JavaScript script: " + scriptId);
        }
    }
}