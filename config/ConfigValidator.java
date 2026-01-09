package engine.config;

import engine.logging.LogManager;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Configuration validation and schema enforcement system.
 * Provides type safety, range validation, and custom validation rules.
 */
public class ConfigValidator {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    /**
     * Validation rule for a configuration key.
     */
    public static class ValidationRule {
        private final String key;
        private final Class<?> expectedType;
        private final boolean required;
        private final Object defaultValue;
        private final Predicate<Object> validator;
        private final String description;
        
        private ValidationRule(Builder builder) {
            this.key = builder.key;
            this.expectedType = builder.expectedType;
            this.required = builder.required;
            this.defaultValue = builder.defaultValue;
            this.validator = builder.validator;
            this.description = builder.description;
        }
        
        public String getKey() { return key; }
        public Class<?> getExpectedType() { return expectedType; }
        public boolean isRequired() { return required; }
        public Object getDefaultValue() { return defaultValue; }
        public String getDescription() { return description; }
        
        public boolean validate(Object value) {
            if (value == null) {
                return !required;
            }
            
            // Type check
            if (!expectedType.isInstance(value)) {
                return false;
            }
            
            // Custom validation
            return validator == null || validator.test(value);
        }
        
        public static class Builder {
            private String key;
            private Class<?> expectedType = String.class;
            private boolean required = false;
            private Object defaultValue;
            private Predicate<Object> validator;
            private String description = "";
            
            public Builder key(String key) {
                this.key = key;
                return this;
            }
            
            public Builder type(Class<?> type) {
                this.expectedType = type;
                return this;
            }
            
            public Builder required(boolean required) {
                this.required = required;
                return this;
            }
            
            public Builder defaultValue(Object defaultValue) {
                this.defaultValue = defaultValue;
                return this;
            }
            
            public Builder validator(Predicate<Object> validator) {
                this.validator = validator;
                return this;
            }
            
            public Builder description(String description) {
                this.description = description;
                return this;
            }
            
            public Builder range(Number min, Number max) {
                this.validator = value -> {
                    if (!(value instanceof Number)) return false;
                    double val = ((Number) value).doubleValue();
                    double minVal = min.doubleValue();
                    double maxVal = max.doubleValue();
                    return val >= minVal && val <= maxVal;
                };
                return this;
            }
            
            public Builder pattern(String regex) {
                Pattern pattern = Pattern.compile(regex);
                this.validator = value -> {
                    if (!(value instanceof String)) return false;
                    return pattern.matcher((String) value).matches();
                };
                return this;
            }
            
            public Builder oneOf(Object... allowedValues) {
                Set<Object> allowed = new HashSet<>(Arrays.asList(allowedValues));
                this.validator = allowed::contains;
                return this;
            }
            
            public ValidationRule build() {
                if (key == null) {
                    throw new IllegalArgumentException("Key is required for validation rule");
                }
                return new ValidationRule(this);
            }
        }
    }
    
    /**
     * Validation result containing errors and warnings.
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final Map<String, Object> validatedValues = new HashMap<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public void addValidatedValue(String key, Object value) {
            validatedValues.put(key, value);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
        
        public Map<String, Object> getValidatedValues() {
            return new HashMap<>(validatedValues);
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }
    
    private final Map<String, ValidationRule> rules = new HashMap<>();
    
    public ConfigValidator() {
        initializeDefaultRules();
    }
    
    /**
     * Initialize default validation rules for engine configuration.
     */
    private void initializeDefaultRules() {
        // Engine settings
        addRule(new ValidationRule.Builder()
                .key("engine.render.targetFps")
                .type(Integer.class)
                .range(30, 240)
                .defaultValue(60)
                .description("Target frames per second")
                .build());
        
        addRule(new ValidationRule.Builder()
                .key("engine.render.vsync")
                .type(Boolean.class)
                .defaultValue(true)
                .description("Enable vertical synchronization")
                .build());
        
        addRule(new ValidationRule.Builder()
                .key("engine.render.renderDistance")
                .type(Integer.class)
                .range(4, 32)
                .defaultValue(8)
                .description("Render distance in chunks")
                .build());
        
        addRule(new ValidationRule.Builder()
                .key("engine.render.fov")
                .type(Float.class)
                .range(60.0f, 120.0f)
                .defaultValue(70.0f)
                .description("Field of view in degrees")
                .build());
        
        // Audio settings
        addRule(new ValidationRule.Builder()
                .key("engine.audio.masterVolume")
                .type(Float.class)
                .range(0.0f, 1.0f)
                .defaultValue(1.0f)
                .description("Master audio volume")
                .build());
        
        // Input settings
        addRule(new ValidationRule.Builder()
                .key("engine.input.mouseSensitivity")
                .type(Float.class)
                .range(0.1f, 5.0f)
                .defaultValue(1.0f)
                .description("Mouse sensitivity multiplier")
                .build());
        
        // Logging settings
        addRule(new ValidationRule.Builder()
                .key("engine.logging.level")
                .type(String.class)
                .oneOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL")
                .defaultValue("INFO")
                .description("Minimum logging level")
                .build());
        
        addRule(new ValidationRule.Builder()
                .key("engine.logging.console.enabled")
                .type(Boolean.class)
                .defaultValue(true)
                .description("Enable console logging")
                .build());
        
        addRule(new ValidationRule.Builder()
                .key("engine.logging.file.enabled")
                .type(Boolean.class)
                .defaultValue(true)
                .description("Enable file logging")
                .build());
        
        // Performance settings
        addRule(new ValidationRule.Builder()
                .key("engine.performance.monitoring.enabled")
                .type(Boolean.class)
                .defaultValue(false)
                .description("Enable performance monitoring")
                .build());
        
        // Physics settings
        addRule(new ValidationRule.Builder()
                .key("engine.physics.gravity")
                .type(Float.class)
                .range(-50.0f, 0.0f)
                .defaultValue(-9.81f)
                .description("Gravity acceleration")
                .build());
        
        addRule(new ValidationRule.Builder()
                .key("engine.physics.timeStep")
                .type(Float.class)
                .range(0.001f, 0.1f)
                .defaultValue(0.016f)
                .description("Physics simulation time step")
                .build());
        
        logManager.info("ConfigValidator", "Default validation rules initialized",
                       "ruleCount", rules.size());
    }
    
    /**
     * Add a validation rule.
     */
    public void addRule(ValidationRule rule) {
        rules.put(rule.getKey(), rule);
        logManager.debug("ConfigValidator", "Validation rule added",
                        "key", rule.getKey(), "type", rule.getExpectedType().getSimpleName(),
                        "required", rule.isRequired());
    }
    
    /**
     * Remove a validation rule.
     */
    public void removeRule(String key) {
        ValidationRule removed = rules.remove(key);
        if (removed != null) {
            logManager.debug("ConfigValidator", "Validation rule removed", "key", key);
        }
    }
    
    /**
     * Validate a configuration map.
     */
    public ValidationResult validate(Map<String, Object> configuration) {
        ValidationResult result = new ValidationResult();
        
        // Check all rules
        for (ValidationRule rule : rules.values()) {
            String key = rule.getKey();
            Object value = configuration.get(key);
            
            if (value == null) {
                if (rule.isRequired()) {
                    result.addError("Required configuration key missing: " + key);
                } else if (rule.getDefaultValue() != null) {
                    result.addValidatedValue(key, rule.getDefaultValue());
                    result.addWarning("Using default value for " + key + ": " + rule.getDefaultValue());
                }
                continue;
            }
            
            // Convert string values to appropriate types
            Object convertedValue = convertValue(value, rule.getExpectedType());
            if (convertedValue == null) {
                result.addError("Invalid type for " + key + ": expected " + 
                              rule.getExpectedType().getSimpleName() + ", got " + 
                              value.getClass().getSimpleName());
                continue;
            }
            
            // Validate the converted value
            if (!rule.validate(convertedValue)) {
                result.addError("Validation failed for " + key + ": " + convertedValue);
                continue;
            }
            
            result.addValidatedValue(key, convertedValue);
        }
        
        // Check for unknown keys
        for (String key : configuration.keySet()) {
            if (!rules.containsKey(key)) {
                result.addWarning("Unknown configuration key: " + key);
            }
        }
        
        logManager.debug("ConfigValidator", "Configuration validation completed",
                        "valid", result.isValid(),
                        "errorCount", result.getErrors().size(),
                        "warningCount", result.getWarnings().size());
        
        return result;
    }
    
    /**
     * Convert a value to the expected type.
     */
    private Object convertValue(Object value, Class<?> expectedType) {
        if (value == null) return null;
        if (expectedType.isInstance(value)) return value;
        
        try {
            String stringValue = value.toString();
            
            if (expectedType == Integer.class || expectedType == int.class) {
                return Integer.valueOf(stringValue);
            } else if (expectedType == Float.class || expectedType == float.class) {
                return Float.valueOf(stringValue);
            } else if (expectedType == Double.class || expectedType == double.class) {
                return Double.valueOf(stringValue);
            } else if (expectedType == Boolean.class || expectedType == boolean.class) {
                return Boolean.valueOf(stringValue);
            } else if (expectedType == Long.class || expectedType == long.class) {
                return Long.valueOf(stringValue);
            } else if (expectedType == String.class) {
                return stringValue;
            }
        } catch (NumberFormatException e) {
            logManager.debug("ConfigValidator", "Type conversion failed",
                           "value", value, "expectedType", expectedType.getSimpleName());
        }
        
        return null;
    }
    
    /**
     * Get all validation rules.
     */
    public Map<String, ValidationRule> getAllRules() {
        return new HashMap<>(rules);
    }
    
    /**
     * Get a validation rule by key.
     */
    public Optional<ValidationRule> getRule(String key) {
        return Optional.ofNullable(rules.get(key));
    }
}