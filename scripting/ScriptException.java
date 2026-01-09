package engine.scripting;

/**
 * Exception thrown when script operations fail.
 */
public class ScriptException extends Exception {
    
    private final String scriptId;
    private final ScriptLanguage language;
    private final int lineNumber;
    private final int columnNumber;
    
    public ScriptException(String message) {
        super(message);
        this.scriptId = null;
        this.language = null;
        this.lineNumber = -1;
        this.columnNumber = -1;
    }
    
    public ScriptException(String message, Throwable cause) {
        super(message, cause);
        this.scriptId = null;
        this.language = null;
        this.lineNumber = -1;
        this.columnNumber = -1;
    }
    
    public ScriptException(String message, String scriptId, ScriptLanguage language) {
        super(message);
        this.scriptId = scriptId;
        this.language = language;
        this.lineNumber = -1;
        this.columnNumber = -1;
    }
    
    public ScriptException(String message, String scriptId, ScriptLanguage language, 
                          int lineNumber, int columnNumber) {
        super(message);
        this.scriptId = scriptId;
        this.language = language;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }
    
    public ScriptException(String message, Throwable cause, String scriptId, 
                          ScriptLanguage language, int lineNumber, int columnNumber) {
        super(message, cause);
        this.scriptId = scriptId;
        this.language = language;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }
    
    public String getScriptId() {
        return scriptId;
    }
    
    public ScriptLanguage getLanguage() {
        return language;
    }
    
    public int getLineNumber() {
        return lineNumber;
    }
    
    public int getColumnNumber() {
        return columnNumber;
    }
    
    public boolean hasLocation() {
        return lineNumber >= 0 && columnNumber >= 0;
    }
    
    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        
        if (scriptId != null) {
            sb.append(" (Script: ").append(scriptId);
            if (language != null) {
                sb.append(", Language: ").append(language.getDisplayName());
            }
            if (hasLocation()) {
                sb.append(", Line: ").append(lineNumber).append(", Column: ").append(columnNumber);
            }
            sb.append(")");
        }
        
        return sb.toString();
    }
}