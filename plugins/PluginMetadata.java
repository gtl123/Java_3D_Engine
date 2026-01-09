package engine.plugins;

import java.util.*;

/**
 * Contains metadata information about a plugin.
 * This includes identification, versioning, dependencies, and configuration.
 */
public class PluginMetadata {
    
    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final String author;
    private final String website;
    private final String license;
    private final String mainClass;
    private final String engineVersion;
    private final List<PluginDependency> dependencies;
    private final List<String> permissions;
    private final Map<String, Object> properties;
    private final boolean hotReloadable;
    private final boolean sandboxed;
    private final int priority;
    
    /**
     * Plugin dependency information.
     */
    public static class PluginDependency {
        private final String pluginId;
        private final String version;
        private final boolean optional;
        
        public PluginDependency(String pluginId, String version, boolean optional) {
            this.pluginId = pluginId;
            this.version = version;
            this.optional = optional;
        }
        
        public String getPluginId() { return pluginId; }
        public String getVersion() { return version; }
        public boolean isOptional() { return optional; }
        
        @Override
        public String toString() {
            return pluginId + ":" + version + (optional ? " (optional)" : "");
        }
    }
    
    /**
     * Builder for creating PluginMetadata instances.
     */
    public static class Builder {
        private String id;
        private String name;
        private String version;
        private String description = "";
        private String author = "";
        private String website = "";
        private String license = "";
        private String mainClass;
        private String engineVersion = "1.0.0";
        private List<PluginDependency> dependencies = new ArrayList<>();
        private List<String> permissions = new ArrayList<>();
        private Map<String, Object> properties = new HashMap<>();
        private boolean hotReloadable = true;
        private boolean sandboxed = true;
        private int priority = 0;
        
        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder author(String author) { this.author = author; return this; }
        public Builder website(String website) { this.website = website; return this; }
        public Builder license(String license) { this.license = license; return this; }
        public Builder mainClass(String mainClass) { this.mainClass = mainClass; return this; }
        public Builder engineVersion(String engineVersion) { this.engineVersion = engineVersion; return this; }
        public Builder hotReloadable(boolean hotReloadable) { this.hotReloadable = hotReloadable; return this; }
        public Builder sandboxed(boolean sandboxed) { this.sandboxed = sandboxed; return this; }
        public Builder priority(int priority) { this.priority = priority; return this; }
        
        public Builder addDependency(String pluginId, String version) {
            return addDependency(pluginId, version, false);
        }
        
        public Builder addDependency(String pluginId, String version, boolean optional) {
            this.dependencies.add(new PluginDependency(pluginId, version, optional));
            return this;
        }
        
        public Builder addPermission(String permission) {
            this.permissions.add(permission);
            return this;
        }
        
        public Builder setProperty(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }
        
        public PluginMetadata build() {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Plugin ID is required");
            }
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Plugin name is required");
            }
            if (version == null || version.trim().isEmpty()) {
                throw new IllegalArgumentException("Plugin version is required");
            }
            if (mainClass == null || mainClass.trim().isEmpty()) {
                throw new IllegalArgumentException("Plugin main class is required");
            }
            
            return new PluginMetadata(this);
        }
    }
    
    private PluginMetadata(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.version = builder.version;
        this.description = builder.description;
        this.author = builder.author;
        this.website = builder.website;
        this.license = builder.license;
        this.mainClass = builder.mainClass;
        this.engineVersion = builder.engineVersion;
        this.dependencies = Collections.unmodifiableList(new ArrayList<>(builder.dependencies));
        this.permissions = Collections.unmodifiableList(new ArrayList<>(builder.permissions));
        this.properties = Collections.unmodifiableMap(new HashMap<>(builder.properties));
        this.hotReloadable = builder.hotReloadable;
        this.sandboxed = builder.sandboxed;
        this.priority = builder.priority;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
    public String getWebsite() { return website; }
    public String getLicense() { return license; }
    public String getMainClass() { return mainClass; }
    public String getEngineVersion() { return engineVersion; }
    public List<PluginDependency> getDependencies() { return dependencies; }
    public List<String> getPermissions() { return permissions; }
    public Map<String, Object> getProperties() { return properties; }
    public boolean isHotReloadable() { return hotReloadable; }
    public boolean isSandboxed() { return sandboxed; }
    public int getPriority() { return priority; }
    
    /**
     * Get a property value with type casting.
     * @param key Property key
     * @param defaultValue Default value if property not found
     * @return Property value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, T defaultValue) {
        Object value = properties.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return (T) value;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
    
    /**
     * Check if the plugin has a specific permission.
     * @param permission Permission to check
     * @return true if plugin has the permission
     */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
    
    /**
     * Create a new builder instance.
     * @return New builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public String toString() {
        return String.format("PluginMetadata{id='%s', name='%s', version='%s', author='%s'}", 
                           id, name, version, author);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PluginMetadata that = (PluginMetadata) obj;
        return Objects.equals(id, that.id) && Objects.equals(version, that.version);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, version);
    }
}