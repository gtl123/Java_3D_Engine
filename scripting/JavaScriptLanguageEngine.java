package engine.scripting;

import engine.logging.LogManager;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * JavaScript language engine implementation using GraalJS.
 */
public class JavaScriptLanguageEngine implements DefaultScriptEngine.LanguageEngine {
    
    private static final LogManager logManager = LogManager.getInstance();
    private static final String JS_LANGUAGE = "js";
    
    private Engine polyglotEngine;
    private volatile boolean initialized = false;
    
    @Override
    public void initialize() throws ScriptException {
        if (initialized) {
            return;
        }
        
        try {
            logManager.info("JavaScriptLanguageEngine", "Initializing JavaScript engine");
            
            // Create GraalVM polyglot engine with security restrictions
            polyglotEngine = Engine.newBuilder()
                .option("engine.WarnInterpreterOnly", "false")
                .build();
            
            initialized = true;
            
            logManager.info("JavaScriptLanguageEngine", "JavaScript engine initialized successfully");
            
        } catch (Exception e) {
            throw new ScriptException("Failed to initialize JavaScript engine", e);
        }
    }
    
    @Override
    public void shutdown() throws ScriptException {
        if (!initialized) {
            return;
        }
        
        logManager.info("JavaScriptLanguageEngine", "Shutting down JavaScript engine");
        
        if (polyglotEngine != null) {
            polyglotEngine.close();
            polyglotEngine = null;
        }
        
        initialized = false;
        
        logManager.info("JavaScriptLanguageEngine", "JavaScript engine shutdown complete");
    }
    
    @Override
    public Script createScript(String scriptId, String source, ScriptLanguage language) throws ScriptException {
        if (!initialized) {
            throw new ScriptException("JavaScript engine not initialized");
        }
        
        if (language != ScriptLanguage.JAVASCRIPT) {
            throw new ScriptException("Invalid language for JavaScript engine: " + language);
        }
        
        try {
            return new JavaScriptScript(scriptId, source, polyglotEngine);
        } catch (Exception e) {
            throw new ScriptException("Failed to create JavaScript script", e, scriptId, language, -1, -1);
        }
    }
    
    /**
     * JavaScript script implementation using GraalJS.
     */
    private static class JavaScriptScript implements Script {
        
        private final String id;
        private final String name;
        private final String source;
        private final Instant loadTime;
        private final Instant lastModified;
        private final Map<String, Object> metadata;
        private final Context jsContext;
        
        private volatile boolean disposed = false;
        
        public JavaScriptScript(String scriptId, String source, Engine polyglotEngine) throws ScriptException {
            this.id = scriptId;
            this.name = extractNameFromId(scriptId);
            this.source = source;
            this.loadTime = Instant.now();
            this.lastModified = Instant.now();
            this.metadata = new HashMap<>();
            
            try {
                // Create sandboxed JavaScript context
                this.jsContext = Context.newBuilder(JS_LANGUAGE)
                    .engine(polyglotEngine)
                    .allowAllAccess(false) // Restrict access for security
                    .allowIO(false) // No file I/O
                    .allowNativeAccess(false) // No native code access
                    .allowCreateThread(false) // No thread creation
                    .allowHostClassLookup(className -> false) // No host class access
                    .build();
                
                // Evaluate the script to check for syntax errors
                jsContext.eval(JS_LANGUAGE, source);
                
                logManager.debug("JavaScriptScript", "JavaScript script compiled", "scriptId", scriptId);
                
            } catch (PolyglotException e) {
                throw new ScriptException("Failed to compile JavaScript script: " + e.getMessage(), 
                                        e, scriptId, ScriptLanguage.JAVASCRIPT, -1, -1);
            } catch (Exception e) {
                throw new ScriptException("Failed to create JavaScript context", 
                                        e, scriptId, ScriptLanguage.JAVASCRIPT, -1, -1);
            }
        }
        
        @Override
        public String getId() {
            return id;
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public String getPath() {
            return id; // For now, ID is the path
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
        public Map<String, Object> getMetadata() {
            return new HashMap<>(metadata);
        }
        
        @Override
        public Instant getLoadTime() {
            return loadTime;
        }
        
        @Override
        public Instant getLastModified() {
            return lastModified;
        }
        
        @Override
        public boolean isCompiled() {
            return true; // GraalJS compiles scripts
        }
        
        @Override
        public boolean supportsHotReload() {
            return true;
        }
        
        @Override
        public CompletableFuture<ScriptExecutionResult> execute(Map<String, Object> parameters) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return executeSync(parameters);
                } catch (ScriptException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        
        @Override
        public ScriptExecutionResult executeSync(Map<String, Object> parameters) throws ScriptException {
            if (disposed) {
                throw new ScriptException("Script has been disposed", id, ScriptLanguage.JAVASCRIPT);
            }
            
            Instant startTime = Instant.now();
            
            try {
                // Set parameters as global variables
                if (parameters != null) {
                    for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                        setGlobal(entry.getKey(), entry.getValue());
                    }
                }
                
                // Execute the script
                Value result = jsContext.eval(JS_LANGUAGE, source);
                
                Instant endTime = Instant.now();
                
                // Convert JavaScript result to Java object
                Object javaResult = valueToJava(result);
                
                return ScriptExecutionResult.builder()
                    .status(ScriptExecutionResult.Status.SUCCESS)
                    .returnValue(javaResult)
                    .startTime(startTime)
                    .endTime(endTime)
                    .scriptId(id)
                    .language(ScriptLanguage.JAVASCRIPT)
                    .build();
                
            } catch (PolyglotException e) {
                Instant endTime = Instant.now();
                
                ScriptException scriptException = new ScriptException(
                    "JavaScript execution error: " + e.getMessage(), e, id, ScriptLanguage.JAVASCRIPT, -1, -1);
                
                return ScriptExecutionResult.builder()
                    .status(ScriptExecutionResult.Status.ERROR)
                    .error(scriptException)
                    .startTime(startTime)
                    .endTime(endTime)
                    .scriptId(id)
                    .language(ScriptLanguage.JAVASCRIPT)
                    .build();
                    
            } catch (Exception e) {
                Instant endTime = Instant.now();
                
                ScriptException scriptException = new ScriptException(
                    "Script execution error: " + e.getMessage(), e, id, ScriptLanguage.JAVASCRIPT, -1, -1);
                
                return ScriptExecutionResult.builder()
                    .status(ScriptExecutionResult.Status.ERROR)
                    .error(scriptException)
                    .startTime(startTime)
                    .endTime(endTime)
                    .scriptId(id)
                    .language(ScriptLanguage.JAVASCRIPT)
                    .build();
            }
        }
        
        @Override
        public CompletableFuture<Object> callFunction(String functionName, Object... arguments) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return callFunctionSync(functionName, arguments);
                } catch (ScriptException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        
        @Override
        public Object callFunctionSync(String functionName, Object... arguments) throws ScriptException {
            if (disposed) {
                throw new ScriptException("Script has been disposed", id, ScriptLanguage.JAVASCRIPT);
            }
            
            try {
                Value function = jsContext.getBindings(JS_LANGUAGE).getMember(functionName);
                if (function == null || !function.canExecute()) {
                    throw new ScriptException("Function not found or not executable: " + functionName, 
                                            id, ScriptLanguage.JAVASCRIPT);
                }
                
                // Execute the function
                Value result = function.execute((Object[]) arguments);
                
                // Convert result back to Java
                return valueToJava(result);
                
            } catch (PolyglotException e) {
                throw new ScriptException("JavaScript function call error: " + e.getMessage(), 
                                        e, id, ScriptLanguage.JAVASCRIPT, -1, -1);
            } catch (Exception e) {
                throw new ScriptException("Function call error: " + e.getMessage(), 
                                        e, id, ScriptLanguage.JAVASCRIPT, -1, -1);
            }
        }
        
        @Override
        public String[] getAvailableFunctions() {
            List<String> functions = new ArrayList<>();
            
            try {
                Value bindings = jsContext.getBindings(JS_LANGUAGE);
                Set<String> keys = bindings.getMemberKeys();
                
                for (String key : keys) {
                    Value member = bindings.getMember(key);
                    if (member != null && member.canExecute()) {
                        functions.add(key);
                    }
                }
                
            } catch (Exception e) {
                logManager.warn("JavaScriptScript", "Error getting available functions", 
                               "scriptId", id, "error", e.getMessage());
            }
            
            return functions.toArray(new String[0]);
        }
        
        @Override
        public void setGlobal(String name, Object value) {
            try {
                jsContext.getBindings(JS_LANGUAGE).putMember(name, value);
            } catch (Exception e) {
                logManager.warn("JavaScriptScript", "Error setting global variable", 
                               "scriptId", id, "variable", name, "error", e.getMessage());
            }
        }
        
        @Override
        public Object getGlobal(String name) {
            try {
                Value value = jsContext.getBindings(JS_LANGUAGE).getMember(name);
                return valueToJava(value);
            } catch (Exception e) {
                logManager.warn("JavaScriptScript", "Error getting global variable", 
                               "scriptId", id, "variable", name, "error", e.getMessage());
                return null;
            }
        }
        
        @Override
        public CompletableFuture<Void> reload() {
            return CompletableFuture.runAsync(() -> {
                // Reload would require re-creating the script
                logManager.debug("JavaScriptScript", "Reload requested", "scriptId", id);
            });
        }
        
        @Override
        public void dispose() {
            if (!disposed) {
                disposed = true;
                
                if (jsContext != null) {
                    try {
                        jsContext.close();
                    } catch (Exception e) {
                        logManager.warn("JavaScriptScript", "Error closing JavaScript context", 
                                       "scriptId", id, "error", e.getMessage());
                    }
                }
                
                logManager.debug("JavaScriptScript", "Script disposed", "scriptId", id);
            }
        }
        
        @Override
        public boolean isDisposed() {
            return disposed;
        }
        
        private String extractNameFromId(String scriptId) {
            int lastSlash = scriptId.lastIndexOf('/');
            int lastBackslash = scriptId.lastIndexOf('\\');
            int lastSeparator = Math.max(lastSlash, lastBackslash);
            
            if (lastSeparator >= 0 && lastSeparator < scriptId.length() - 1) {
                return scriptId.substring(lastSeparator + 1);
            }
            
            return scriptId;
        }
        
        private Object valueToJava(Value value) {
            if (value == null) {
                return null;
            } else if (value.isNull()) {
                return null;
            } else if (value.isBoolean()) {
                return value.asBoolean();
            } else if (value.isNumber()) {
                if (value.fitsInInt()) {
                    return value.asInt();
                } else if (value.fitsInLong()) {
                    return value.asLong();
                } else if (value.fitsInFloat()) {
                    return value.asFloat();
                } else {
                    return value.asDouble();
                }
            } else if (value.isString()) {
                return value.asString();
            } else if (value.hasArrayElements()) {
                List<Object> list = new ArrayList<>();
                long size = value.getArraySize();
                for (long i = 0; i < size; i++) {
                    list.add(valueToJava(value.getArrayElement(i)));
                }
                return list;
            } else if (value.hasMembers()) {
                Map<String, Object> map = new HashMap<>();
                Set<String> keys = value.getMemberKeys();
                for (String key : keys) {
                    map.put(key, valueToJava(value.getMember(key)));
                }
                return map;
            } else {
                return value.toString();
            }
        }
    }
}