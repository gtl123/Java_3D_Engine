package engine.scripting;

/**
 * Supported scripting languages in the engine.
 */
public enum ScriptLanguage {
    LUA("lua", "Lua", new String[]{".lua"}),
    JAVASCRIPT("javascript", "JavaScript", new String[]{".js", ".mjs"});
    
    private final String id;
    private final String displayName;
    private final String[] fileExtensions;
    
    ScriptLanguage(String id, String displayName, String[] fileExtensions) {
        this.id = id;
        this.displayName = displayName;
        this.fileExtensions = fileExtensions;
    }
    
    public String getId() {
        return id;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String[] getFileExtensions() {
        return fileExtensions;
    }
    
    /**
     * Determine script language from file extension.
     * @param filename The filename to check
     * @return The detected language, or null if not supported
     */
    public static ScriptLanguage fromFilename(String filename) {
        if (filename == null) return null;
        
        String lowerFilename = filename.toLowerCase();
        for (ScriptLanguage language : values()) {
            for (String extension : language.fileExtensions) {
                if (lowerFilename.endsWith(extension)) {
                    return language;
                }
            }
        }
        return null;
    }
}