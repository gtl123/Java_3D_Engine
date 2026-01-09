package engine.plugins;

import java.util.function.Consumer;

/**
 * Event bus for inter-plugin communication and engine event distribution.
 * Provides a decoupled messaging system for plugins to communicate with each other
 * and receive notifications from the engine.
 */
public interface EventBus {
    
    /**
     * Subscribe to events of a specific type.
     * @param eventType Event class type
     * @param handler Event handler function
     * @param <T> Event type
     * @return Subscription handle for unsubscribing
     */
    <T> EventSubscription subscribe(Class<T> eventType, Consumer<T> handler);
    
    /**
     * Subscribe to events of a specific type with priority.
     * Higher priority handlers are called first.
     * @param eventType Event class type
     * @param handler Event handler function
     * @param priority Handler priority (higher = called first)
     * @param <T> Event type
     * @return Subscription handle for unsubscribing
     */
    <T> EventSubscription subscribe(Class<T> eventType, Consumer<T> handler, int priority);
    
    /**
     * Subscribe to events with a filter condition.
     * @param eventType Event class type
     * @param filter Filter predicate (return true to handle event)
     * @param handler Event handler function
     * @param <T> Event type
     * @return Subscription handle for unsubscribing
     */
    <T> EventSubscription subscribe(Class<T> eventType, java.util.function.Predicate<T> filter, Consumer<T> handler);
    
    /**
     * Unsubscribe from events using the subscription handle.
     * @param subscription Subscription to cancel
     */
    void unsubscribe(EventSubscription subscription);
    
    /**
     * Publish an event to all subscribers.
     * @param event Event to publish
     */
    void publish(Object event);
    
    /**
     * Publish an event asynchronously.
     * @param event Event to publish
     */
    void publishAsync(Object event);
    
    /**
     * Publish an event to a specific plugin only.
     * @param pluginId Target plugin ID
     * @param event Event to publish
     */
    void publishToPlugin(String pluginId, Object event);
    
    /**
     * Check if there are any subscribers for an event type.
     * @param eventType Event class type
     * @return true if there are subscribers
     */
    boolean hasSubscribers(Class<?> eventType);
    
    /**
     * Get the number of subscribers for an event type.
     * @param eventType Event class type
     * @return Number of subscribers
     */
    int getSubscriberCount(Class<?> eventType);
    
    /**
     * Clear all subscriptions for a specific plugin.
     * Called automatically when a plugin is unloaded.
     * @param pluginId Plugin ID to clear subscriptions for
     */
    void clearPluginSubscriptions(String pluginId);
    
    /**
     * Enable or disable event logging for debugging.
     * @param enabled Whether to log events
     */
    void setEventLogging(boolean enabled);
    
    /**
     * Get event bus statistics.
     * @return Event bus statistics
     */
    EventBusStatistics getStatistics();
    
    /**
     * Event subscription handle for managing subscriptions.
     */
    interface EventSubscription {
        /**
         * Get the event type this subscription handles.
         * @return Event class type
         */
        Class<?> getEventType();
        
        /**
         * Get the plugin ID that owns this subscription.
         * @return Plugin ID
         */
        String getPluginId();
        
        /**
         * Get the subscription priority.
         * @return Priority value
         */
        int getPriority();
        
        /**
         * Check if this subscription is still active.
         * @return true if active
         */
        boolean isActive();
        
        /**
         * Cancel this subscription.
         */
        void cancel();
    }
    
    /**
     * Event bus statistics for monitoring and debugging.
     */
    class EventBusStatistics {
        private final int totalSubscriptions;
        private final int activeSubscriptions;
        private final long totalEventsPublished;
        private final long totalEventsHandled;
        private final int uniqueEventTypes;
        
        public EventBusStatistics(int totalSubscriptions, int activeSubscriptions, 
                                long totalEventsPublished, long totalEventsHandled, int uniqueEventTypes) {
            this.totalSubscriptions = totalSubscriptions;
            this.activeSubscriptions = activeSubscriptions;
            this.totalEventsPublished = totalEventsPublished;
            this.totalEventsHandled = totalEventsHandled;
            this.uniqueEventTypes = uniqueEventTypes;
        }
        
        public int getTotalSubscriptions() { return totalSubscriptions; }
        public int getActiveSubscriptions() { return activeSubscriptions; }
        public long getTotalEventsPublished() { return totalEventsPublished; }
        public long getTotalEventsHandled() { return totalEventsHandled; }
        public int getUniqueEventTypes() { return uniqueEventTypes; }
        
        @Override
        public String toString() {
            return String.format("EventBusStats{subscriptions=%d/%d, events=%d/%d, types=%d}",
                               activeSubscriptions, totalSubscriptions, totalEventsHandled, 
                               totalEventsPublished, uniqueEventTypes);
        }
    }
}