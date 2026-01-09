package engine.scripting;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Result of script execution containing return value, performance metrics, and error information.
 */
public class ScriptExecutionResult {
    
    public enum Status {
        SUCCESS,
        ERROR,
        TIMEOUT,
        CANCELLED
    }
    
    private final Status status;
    private final Object returnValue;
    private final ScriptException error;
    private final Instant startTime;
    private final Instant endTime;
    private final Duration executionTime;
    private final Map<String, Object> metrics;
    private final String scriptId;
    private final ScriptLanguage language;
    
    private ScriptExecutionResult(Builder builder) {
        this.status = builder.status;
        this.returnValue = builder.returnValue;
        this.error = builder.error;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.executionTime = builder.executionTime;
        this.metrics = builder.metrics;
        this.scriptId = builder.scriptId;
        this.language = builder.language;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public Optional<Object> getReturnValue() {
        return Optional.ofNullable(returnValue);
    }
    
    public Optional<ScriptException> getError() {
        return Optional.ofNullable(error);
    }
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public Instant getEndTime() {
        return endTime;
    }
    
    public Duration getExecutionTime() {
        return executionTime;
    }
    
    public Map<String, Object> getMetrics() {
        return metrics;
    }
    
    public String getScriptId() {
        return scriptId;
    }
    
    public ScriptLanguage getLanguage() {
        return language;
    }
    
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    public boolean hasError() {
        return status == Status.ERROR;
    }
    
    public boolean wasTimeout() {
        return status == Status.TIMEOUT;
    }
    
    public boolean wasCancelled() {
        return status == Status.CANCELLED;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ScriptExecutionResult{");
        sb.append("status=").append(status);
        sb.append(", scriptId='").append(scriptId).append('\'');
        sb.append(", language=").append(language);
        sb.append(", executionTime=").append(executionTime);
        
        if (returnValue != null) {
            sb.append(", returnValue=").append(returnValue);
        }
        
        if (error != null) {
            sb.append(", error='").append(error.getMessage()).append('\'');
        }
        
        sb.append('}');
        return sb.toString();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Status status;
        private Object returnValue;
        private ScriptException error;
        private Instant startTime;
        private Instant endTime;
        private Duration executionTime;
        private Map<String, Object> metrics;
        private String scriptId;
        private ScriptLanguage language;
        
        public Builder status(Status status) {
            this.status = status;
            return this;
        }
        
        public Builder returnValue(Object returnValue) {
            this.returnValue = returnValue;
            return this;
        }
        
        public Builder error(ScriptException error) {
            this.error = error;
            this.status = Status.ERROR;
            return this;
        }
        
        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }
        
        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            if (startTime != null && endTime != null) {
                this.executionTime = Duration.between(startTime, endTime);
            }
            return this;
        }
        
        public Builder executionTime(Duration executionTime) {
            this.executionTime = executionTime;
            return this;
        }
        
        public Builder metrics(Map<String, Object> metrics) {
            this.metrics = metrics;
            return this;
        }
        
        public Builder scriptId(String scriptId) {
            this.scriptId = scriptId;
            return this;
        }
        
        public Builder language(ScriptLanguage language) {
            this.language = language;
            return this;
        }
        
        public ScriptExecutionResult build() {
            if (status == null) {
                status = Status.SUCCESS;
            }
            
            if (startTime != null && endTime != null && executionTime == null) {
                executionTime = Duration.between(startTime, endTime);
            }
            
            return new ScriptExecutionResult(this);
        }
    }
}