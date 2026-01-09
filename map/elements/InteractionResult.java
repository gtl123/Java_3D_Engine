package fps.map.elements;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the result of an interaction attempt with a dynamic element.
 * Contains success status, messages, and any additional data from the interaction.
 */
public class InteractionResult {
    
    private final boolean success;
    private final String message;
    private final InteractionType type;
    private final Map<String, Object> data;
    private final long timestamp;
    
    // Result codes for different interaction outcomes
    public enum ResultCode {
        SUCCESS,
        FAILED_OUT_OF_RANGE,
        FAILED_NO_LINE_OF_SIGHT,
        FAILED_WRONG_STATE,
        FAILED_INSUFFICIENT_PERMISSIONS,
        FAILED_COOLDOWN,
        FAILED_RESOURCE_REQUIRED,
        FAILED_ALREADY_IN_USE,
        FAILED_LOCKED,
        FAILED_DESTROYED,
        FAILED_NO_POWER,
        FAILED_CUSTOM_CONDITION
    }
    
    public enum InteractionType {
        ACTIVATE,
        DEACTIVATE,
        OPEN,
        CLOSE,
        LOCK,
        UNLOCK,
        USE,
        REPAIR,
        DESTROY,
        TOGGLE,
        CUSTOM
    }
    
    private final ResultCode resultCode;
    
    /**
     * Create a successful interaction result
     */
    public InteractionResult(boolean success, String message) {
        this(success, message, InteractionType.ACTIVATE, success ? ResultCode.SUCCESS : ResultCode.FAILED_CUSTOM_CONDITION);
    }
    
    /**
     * Create an interaction result with specific type
     */
    public InteractionResult(boolean success, String message, InteractionType type) {
        this(success, message, type, success ? ResultCode.SUCCESS : ResultCode.FAILED_CUSTOM_CONDITION);
    }
    
    /**
     * Create a detailed interaction result
     */
    public InteractionResult(boolean success, String message, InteractionType type, ResultCode resultCode) {
        this.success = success;
        this.message = message;
        this.type = type;
        this.resultCode = resultCode;
        this.data = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Create a successful result with data
     */
    public static InteractionResult success(String message, InteractionType type) {
        return new InteractionResult(true, message, type, ResultCode.SUCCESS);
    }
    
    /**
     * Create a failure result with specific reason
     */
    public static InteractionResult failure(String message, ResultCode reason) {
        return new InteractionResult(false, message, InteractionType.ACTIVATE, reason);
    }
    
    /**
     * Create a failure result for out of range
     */
    public static InteractionResult outOfRange(String elementName, float distance, float maxRange) {
        String message = String.format("Too far from %s (%.1fm, max %.1fm)", elementName, distance, maxRange);
        InteractionResult result = new InteractionResult(false, message, InteractionType.ACTIVATE, ResultCode.FAILED_OUT_OF_RANGE);
        result.addData("distance", distance);
        result.addData("maxRange", maxRange);
        return result;
    }
    
    /**
     * Create a failure result for no line of sight
     */
    public static InteractionResult noLineOfSight(String elementName) {
        String message = String.format("No clear line of sight to %s", elementName);
        return new InteractionResult(false, message, InteractionType.ACTIVATE, ResultCode.FAILED_NO_LINE_OF_SIGHT);
    }
    
    /**
     * Create a failure result for wrong state
     */
    public static InteractionResult wrongState(String elementName, ElementState currentState, ElementState requiredState) {
        String message = String.format("%s is %s, requires %s", elementName, currentState.getDisplayName(), requiredState.getDisplayName());
        InteractionResult result = new InteractionResult(false, message, InteractionType.ACTIVATE, ResultCode.FAILED_WRONG_STATE);
        result.addData("currentState", currentState);
        result.addData("requiredState", requiredState);
        return result;
    }
    
    /**
     * Create a failure result for insufficient permissions
     */
    public static InteractionResult insufficientPermissions(String elementName, String requiredPermission) {
        String message = String.format("Insufficient permissions to use %s (requires: %s)", elementName, requiredPermission);
        InteractionResult result = new InteractionResult(false, message, InteractionType.ACTIVATE, ResultCode.FAILED_INSUFFICIENT_PERMISSIONS);
        result.addData("requiredPermission", requiredPermission);
        return result;
    }
    
    /**
     * Create a failure result for cooldown
     */
    public static InteractionResult onCooldown(String elementName, float remainingTime) {
        String message = String.format("%s is on cooldown (%.1fs remaining)", elementName, remainingTime);
        InteractionResult result = new InteractionResult(false, message, InteractionType.ACTIVATE, ResultCode.FAILED_COOLDOWN);
        result.addData("remainingCooldown", remainingTime);
        return result;
    }
    
    /**
     * Create a failure result for missing resource
     */
    public static InteractionResult missingResource(String elementName, String resourceType, int required, int available) {
        String message = String.format("%s requires %d %s (have %d)", elementName, required, resourceType, available);
        InteractionResult result = new InteractionResult(false, message, InteractionType.ACTIVATE, ResultCode.FAILED_RESOURCE_REQUIRED);
        result.addData("resourceType", resourceType);
        result.addData("required", required);
        result.addData("available", available);
        return result;
    }
    
    /**
     * Create a failure result for element already in use
     */
    public static InteractionResult alreadyInUse(String elementName, String currentUser) {
        String message = String.format("%s is already being used by %s", elementName, currentUser);
        InteractionResult result = new InteractionResult(false, message, InteractionType.ACTIVATE, ResultCode.FAILED_ALREADY_IN_USE);
        result.addData("currentUser", currentUser);
        return result;
    }
    
    /**
     * Create a failure result for locked element
     */
    public static InteractionResult locked(String elementName, String keyRequired) {
        String message = String.format("%s is locked%s", elementName, 
                                      keyRequired != null ? " (requires " + keyRequired + ")" : "");
        InteractionResult result = new InteractionResult(false, message, InteractionType.ACTIVATE, ResultCode.FAILED_LOCKED);
        if (keyRequired != null) {
            result.addData("keyRequired", keyRequired);
        }
        return result;
    }
    
    /**
     * Create a failure result for destroyed element
     */
    public static InteractionResult destroyed(String elementName) {
        String message = String.format("%s is destroyed and cannot be used", elementName);
        return new InteractionResult(false, message, InteractionType.ACTIVATE, ResultCode.FAILED_DESTROYED);
    }
    
    /**
     * Create a failure result for unpowered element
     */
    public static InteractionResult noPower(String elementName) {
        String message = String.format("%s has no power", elementName);
        return new InteractionResult(false, message, InteractionType.ACTIVATE, ResultCode.FAILED_NO_POWER);
    }
    
    /**
     * Add data to the interaction result
     */
    public InteractionResult addData(String key, Object value) {
        data.put(key, value);
        return this;
    }
    
    /**
     * Get data from the interaction result
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(String key, Class<T> type) {
        Object value = data.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Check if the interaction was successful
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * Check if the interaction failed
     */
    public boolean isFailure() {
        return !success;
    }
    
    /**
     * Get the result message
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * Get the interaction type
     */
    public InteractionType getType() {
        return type;
    }
    
    /**
     * Get the result code
     */
    public ResultCode getResultCode() {
        return resultCode;
    }
    
    /**
     * Get all data
     */
    public Map<String, Object> getAllData() {
        return new HashMap<>(data);
    }
    
    /**
     * Get the timestamp of the interaction
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Check if this result indicates a temporary failure (can retry)
     */
    public boolean isTemporaryFailure() {
        switch (resultCode) {
            case FAILED_OUT_OF_RANGE:
            case FAILED_NO_LINE_OF_SIGHT:
            case FAILED_COOLDOWN:
            case FAILED_ALREADY_IN_USE:
            case FAILED_NO_POWER:
                return true;
            case FAILED_DESTROYED:
            case FAILED_INSUFFICIENT_PERMISSIONS:
            case FAILED_LOCKED:
                return false;
            default:
                return false;
        }
    }
    
    /**
     * Check if this result indicates a permanent failure (cannot retry without changes)
     */
    public boolean isPermanentFailure() {
        return isFailure() && !isTemporaryFailure();
    }
    
    /**
     * Get a user-friendly description of the result
     */
    public String getDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append(success ? "Success" : "Failed");
        desc.append(": ").append(message);
        
        if (!data.isEmpty()) {
            desc.append(" (");
            boolean first = true;
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (!first) desc.append(", ");
                desc.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
            desc.append(")");
        }
        
        return desc.toString();
    }
    
    /**
     * Get suggested action for failed interactions
     */
    public String getSuggestedAction() {
        switch (resultCode) {
            case FAILED_OUT_OF_RANGE:
                return "Move closer to the element";
            case FAILED_NO_LINE_OF_SIGHT:
                return "Clear the line of sight to the element";
            case FAILED_COOLDOWN:
                Float cooldown = getData("remainingCooldown", Float.class);
                return cooldown != null ? 
                    String.format("Wait %.1f seconds", cooldown) : 
                    "Wait for cooldown to finish";
            case FAILED_ALREADY_IN_USE:
                return "Wait for other player to finish";
            case FAILED_LOCKED:
                String key = getData("keyRequired", String.class);
                return key != null ? 
                    "Find " + key + " to unlock" : 
                    "Find a way to unlock this element";
            case FAILED_NO_POWER:
                return "Restore power to this element";
            case FAILED_DESTROYED:
                return "This element cannot be repaired";
            case FAILED_INSUFFICIENT_PERMISSIONS:
                return "You don't have permission to use this";
            default:
                return "Check element requirements";
        }
    }
    
    @Override
    public String toString() {
        return String.format("InteractionResult{success=%s, type=%s, code=%s, message='%s'}", 
                           success, type, resultCode, message);
    }
}