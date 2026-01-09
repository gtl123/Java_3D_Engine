package engine.scripting;

import engine.logging.LogManager;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Lua language engine implementation using LuaJ.
 */
public class LuaLanguageEngine implements DefaultScriptEngine.LanguageEngine {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private Globals globals;
    private volatile boolean initialized = false;
    
    @Override
    public void initialize() throws ScriptException {
        if (initialized) {
            return;
        }
        
        try {
            logManager.info("LuaLanguageEngine", "Initializing Lua engine");
            
            // Create Lua globals with standard libraries
            globals = JsePlatform.standardGlobals();
            
            // Setup security restrictions
            setupSecurity();
            
            initialized = true;
            
            logManager.info("LuaLanguageEngine", "Lua engine initialized successfully");
            
        } catch (Exception e) {
            throw new ScriptException("Failed to initialize Lua engine", e);
        }
    }
    
    @Override
    public void shutdown() throws ScriptException {
        if (!initialized) {
            return;
        }
        
        logManager.info("LuaLanguageEngine", "Shutting down Lua engine");
        
        globals = null;
        initialized = false;
        
        logManager.info("LuaLanguageEngine", "Lua engine shutdown complete");
    }
    
    @Override
    public Script createScript(String scriptId, String source, ScriptLanguage language) throws ScriptException {
        if (!initialized) {
            throw new ScriptException("Lua engine not initialized");
        }
        
        if (language != ScriptLanguage.LUA) {
            throw new ScriptException("Invalid language for Lua engine: " + language);
        }
        
        try {
            return new LuaScript(scriptId, source, globals);
        } catch (Exception e) {
            throw new ScriptException("Failed to create Lua script", e, scriptId, language, -1, -1);
        }
    }
    
    private void setupSecurity() {
        // Remove potentially dangerous functions
        globals.set("dofile", LuaValue.NIL);
        globals.set("loadfile", LuaValue.NIL);
        globals.set("require", LuaValue.NIL);
        globals.set("module", LuaValue.NIL);
        
        // Restrict os library
        LuaValue os = globals.get("os");
        if (os.istable()) {
            LuaTable osTable = os.checktable();
            osTable.set("execute", LuaValue.NIL);
            osTable.set("exit", LuaValue.NIL);
            osTable.set("remove", LuaValue.NIL);
            osTable.set("rename", LuaValue.NIL);
            osTable.set("tmpname", LuaValue.NIL);
        }
        
        // Restrict io library
        LuaValue io = globals.get("io");
        if (io.istable()) {
            LuaTable ioTable = io.checktable();
            ioTable.set("open", LuaValue.NIL);
            ioTable.set("popen", LuaValue.NIL);
            ioTable.set("tmpfile", LuaValue.NIL);
        }
        
        logManager.debug("LuaLanguageEngine", "Security restrictions applied");
    }
    
    /**
     * Lua script implementation.
     */
    private static class LuaScript implements Script {
        
        private final String id;
        private final String name;
        private final String source;
        private final Instant loadTime;
        private final Instant lastModified;
        private final Map<String, Object> metadata;
        private final Globals scriptGlobals;
        private final LuaValue compiledChunk;
        
        private volatile boolean disposed = false;
        
        public LuaScript(String scriptId, String source, Globals parentGlobals) throws ScriptException {
            this.id = scriptId;
            this.name = extractNameFromId(scriptId);
            this.source = source;
            this.loadTime = Instant.now();
            this.lastModified = Instant.now();
            this.metadata = new HashMap<>();
            
            // Create isolated globals for this script
            this.scriptGlobals = new Globals();
            scriptGlobals.load(parentGlobals);
            
            try {
                // Compile the script
                this.compiledChunk = scriptGlobals.load(source, scriptId);
                
                logManager.debug("LuaScript", "Lua script compiled", "scriptId", scriptId);
                
            } catch (LuaError e) {
                throw new ScriptException("Failed to compile Lua script: " + e.getMessage(), 
                                        e, scriptId, ScriptLanguage.LUA, -1, -1);
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
            return ScriptLanguage.LUA;
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
            return compiledChunk != null;
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
                throw new ScriptException("Script has been disposed", id, ScriptLanguage.LUA);
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
                LuaValue result = compiledChunk.call();
                
                Instant endTime = Instant.now();
                
                // Convert Lua result to Java object
                Object javaResult = luaToJava(result);
                
                return ScriptExecutionResult.builder()
                    .status(ScriptExecutionResult.Status.SUCCESS)
                    .returnValue(javaResult)
                    .startTime(startTime)
                    .endTime(endTime)
                    .scriptId(id)
                    .language(ScriptLanguage.LUA)
                    .build();
                
            } catch (LuaError e) {
                Instant endTime = Instant.now();
                
                ScriptException scriptException = new ScriptException(
                    "Lua execution error: " + e.getMessage(), e, id, ScriptLanguage.LUA, -1, -1);
                
                return ScriptExecutionResult.builder()
                    .status(ScriptExecutionResult.Status.ERROR)
                    .error(scriptException)
                    .startTime(startTime)
                    .endTime(endTime)
                    .scriptId(id)
                    .language(ScriptLanguage.LUA)
                    .build();
                    
            } catch (Exception e) {
                Instant endTime = Instant.now();
                
                ScriptException scriptException = new ScriptException(
                    "Script execution error: " + e.getMessage(), e, id, ScriptLanguage.LUA, -1, -1);
                
                return ScriptExecutionResult.builder()
                    .status(ScriptExecutionResult.Status.ERROR)
                    .error(scriptException)
                    .startTime(startTime)
                    .endTime(endTime)
                    .scriptId(id)
                    .language(ScriptLanguage.LUA)
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
                throw new ScriptException("Script has been disposed", id, ScriptLanguage.LUA);
            }
            
            try {
                LuaValue function = scriptGlobals.get(functionName);
                if (function.isnil()) {
                    throw new ScriptException("Function not found: " + functionName, id, ScriptLanguage.LUA);
                }
                
                if (!function.isfunction()) {
                    throw new ScriptException("Value is not a function: " + functionName, id, ScriptLanguage.LUA);
                }
                
                // Convert arguments to Lua values
                LuaValue[] luaArgs = new LuaValue[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    luaArgs[i] = javaToLua(arguments[i]);
                }
                
                // Call the function
                LuaValue result = function.invoke(luaArgs).arg1();
                
                // Convert result back to Java
                return luaToJava(result);
                
            } catch (LuaError e) {
                throw new ScriptException("Lua function call error: " + e.getMessage(), 
                                        e, id, ScriptLanguage.LUA, -1, -1);
            } catch (Exception e) {
                throw new ScriptException("Function call error: " + e.getMessage(), 
                                        e, id, ScriptLanguage.LUA, -1, -1);
            }
        }
        
        @Override
        public String[] getAvailableFunctions() {
            List<String> functions = new ArrayList<>();
            
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs next = scriptGlobals.next(key);
                if ((key = next.arg1()).isnil()) {
                    break;
                }
                
                LuaValue value = next.arg(2);
                if (value.isfunction() && key.isstring()) {
                    functions.add(key.tojstring());
                }
            }
            
            return functions.toArray(new String[0]);
        }
        
        @Override
        public void setGlobal(String name, Object value) {
            scriptGlobals.set(name, javaToLua(value));
        }
        
        @Override
        public Object getGlobal(String name) {
            LuaValue value = scriptGlobals.get(name);
            return luaToJava(value);
        }
        
        @Override
        public CompletableFuture<Void> reload() {
            return CompletableFuture.runAsync(() -> {
                // Reload would require re-creating the script
                logManager.debug("LuaScript", "Reload requested", "scriptId", id);
            });
        }
        
        @Override
        public void dispose() {
            if (!disposed) {
                disposed = true;
                logManager.debug("LuaScript", "Script disposed", "scriptId", id);
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
        
        private LuaValue javaToLua(Object obj) {
            if (obj == null) {
                return LuaValue.NIL;
            } else if (obj instanceof Boolean) {
                return LuaValue.valueOf((Boolean) obj);
            } else if (obj instanceof Integer) {
                return LuaValue.valueOf((Integer) obj);
            } else if (obj instanceof Long) {
                return LuaValue.valueOf((Long) obj);
            } else if (obj instanceof Float) {
                return LuaValue.valueOf((Float) obj);
            } else if (obj instanceof Double) {
                return LuaValue.valueOf((Double) obj);
            } else if (obj instanceof String) {
                return LuaValue.valueOf((String) obj);
            } else if (obj instanceof Map) {
                LuaTable table = new LuaTable();
                Map<?, ?> map = (Map<?, ?>) obj;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    table.set(javaToLua(entry.getKey()), javaToLua(entry.getValue()));
                }
                return table;
            } else if (obj instanceof List) {
                LuaTable table = new LuaTable();
                List<?> list = (List<?>) obj;
                for (int i = 0; i < list.size(); i++) {
                    table.set(i + 1, javaToLua(list.get(i))); // Lua arrays are 1-indexed
                }
                return table;
            } else {
                // For other objects, convert to string
                return LuaValue.valueOf(obj.toString());
            }
        }
        
        private Object luaToJava(LuaValue value) {
            if (value.isnil()) {
                return null;
            } else if (value.isboolean()) {
                return value.toboolean();
            } else if (value.isint()) {
                return value.toint();
            } else if (value.isnumber()) {
                return value.todouble();
            } else if (value.isstring()) {
                return value.tojstring();
            } else if (value.istable()) {
                LuaTable table = value.checktable();
                Map<Object, Object> map = new HashMap<>();
                
                LuaValue key = LuaValue.NIL;
                while (true) {
                    Varargs next = table.next(key);
                    if ((key = next.arg1()).isnil()) {
                        break;
                    }
                    
                    Object javaKey = luaToJava(key);
                    Object javaValue = luaToJava(next.arg(2));
                    map.put(javaKey, javaValue);
                }
                
                return map;
            } else {
                return value.toString();
            }
        }
    }
}