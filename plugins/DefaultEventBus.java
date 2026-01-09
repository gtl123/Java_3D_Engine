package engine.plugins;

import engine.logging.LogManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Default implementation of EventBus for inter-plugin communication.
 */
public class DefaultEventBus implements EventBus {
    
    private static final LogManager logManager = LogManager.getInstance();
    
    private final ConcurrentHashMap<Class<?>, List<EventSubscriptionImpl>> subscriptions = new ConcurrentHashMap<>();
    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "EventBus-Async");
        t.setDaemon(true);
        return t;
    });
    
    private final AtomicLong totalEventsPublished = new AtomicLong(0);
    private final AtomicLong totalEventsHandled = new AtomicLong(0);
    private final AtomicLong subscriptionIdCounter = new AtomicLong(0);
    
    private volatile boolean eventLogging = false;
    private volatile boolean initialized = false;
    
    public void initialize() {
        if (initialized) {
            return;
        }
        
        logManager.info("EventBus", "Initializing event bus");
        initialized = true;
        logManager.info("EventBus", "Event bus initialized");
    }
    
    public void shutdown() {
        if (!initialized) {
            return;
        }
        
        logManager.info("EventBus", "Shutting down event bus");
        subscriptions.clear();
        asyncExecutor.shutdown();
        initialized = false;
        logManager.info("EventBus", "Event bus shutdown complete");
    }
    
    @Override
    public <T> EventSubscription subscribe(Class<T> eventType, Consumer<T> handler) {
        return subscribe(eventType, handler, 0);
    }
    
    @Override
    public <T> EventSubscription subscribe(Class<T> eventType, Consumer<T> handler, int priority) {
        return subscribe(eventType, event -> true, handler, priority);
    }
    
    @Override
    public <T> EventSubscription subscribe(Class<T> eventType, Predicate<T> filter, Consumer<T> handler) {
        return subscribe(eventType, filter, handler, 0);
    }
    
    private <T> EventSubscription subscribe(Class<T> eventType, Predicate<T> filter, Consumer<T> handler, int priority) {
        EventSubscriptionImpl subscription = new EventSubscriptionImpl(
                subscriptionIdCounter.incrementAndGet(),
                eventType,
                "unknown", // Plugin ID would be determined from context
                priority,
                event -> {
                    if (filter.test(eventType.cast(event))) {
                        handler.accept(eventType.cast(event));
                        totalEventsHandled.incrementAndGet();
                    }
                }
        );
        
        subscriptions.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(subscription);
        
        // Sort by priority (higher first)
        subscriptions.get(eventType).sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        
        if (eventLogging) {
            logManager.debug("EventBus", "Event subscription added",
                           "eventType", eventType.getSimpleName(),
                           "priority", priority);
        }
        
        return subscription;
    }
    
    @Override
    public void unsubscribe(EventSubscription subscription) {
        if (subscription instanceof EventSubscriptionImpl) {
            EventSubscriptionImpl impl = (EventSubscriptionImpl) subscription;
            List<EventSubscriptionImpl> subs = subscriptions.get(impl.getEventType());
            if (subs != null) {
                subs.remove(impl);
                impl.cancel();
                
                if (eventLogging) {
                    logManager.debug("EventBus", "Event subscription removed",
                                   "eventType", impl.getEventType().getSimpleName());
                }
            }
        }
    }
    
    @Override
    public void publish(Object event) {
        if (!initialized || event == null) {
            return;
        }
        
        totalEventsPublished.incrementAndGet();
        
        if (eventLogging) {
            logManager.debug("EventBus", "Publishing event",
                           "eventType", event.getClass().getSimpleName());
        }
        
        Class<?> eventType = event.getClass();
        List<EventSubscriptionImpl> subs = subscriptions.get(eventType);
        
        if (subs != null) {
            for (EventSubscriptionImpl subscription : subs) {
                if (subscription.isActive()) {
                    try {
                        subscription.getHandler().accept(event);
                    } catch (Exception e) {
                        logManager.error("EventBus", "Error handling event", e,
                                       "eventType", eventType.getSimpleName(),
                                       "subscriptionId", subscription.getId());
                    }
                }
            }
        }
    }
    
    @Override
    public void publishAsync(Object event) {
        asyncExecutor.submit(() -> publish(event));
    }
    
    @Override
    public void publishToPlugin(String pluginId, Object event) {
        // For now, publish to all - would need plugin context to filter
        publish(event);
    }
    
    @Override
    public boolean hasSubscribers(Class<?> eventType) {
        List<EventSubscriptionImpl> subs = subscriptions.get(eventType);
        return subs != null && !subs.isEmpty();
    }
    
    @Override
    public int getSubscriberCount(Class<?> eventType) {
        List<EventSubscriptionImpl> subs = subscriptions.get(eventType);
        return subs != null ? subs.size() : 0;
    }
    
    @Override
    public void clearPluginSubscriptions(String pluginId) {
        subscriptions.values().forEach(subs -> 
            subs.removeIf(sub -> pluginId.equals(sub.getPluginId())));
    }
    
    @Override
    public void setEventLogging(boolean enabled) {
        this.eventLogging = enabled;
        logManager.info("EventBus", "Event logging " + (enabled ? "enabled" : "disabled"));
    }
    
    @Override
    public EventBusStatistics getStatistics() {
        int totalSubscriptions = subscriptions.values().stream()
                .mapToInt(List::size)
                .sum();
        int activeSubscriptions = subscriptions.values().stream()
                .mapToInt(subs -> (int) subs.stream().filter(EventSubscriptionImpl::isActive).count())
                .sum();
        int uniqueEventTypes = subscriptions.size();
        
        return new EventBusStatistics(totalSubscriptions, activeSubscriptions,
                totalEventsPublished.get(), totalEventsHandled.get(), uniqueEventTypes);
    }
    
    private static class EventSubscriptionImpl implements EventSubscription {
        private final long id;
        private final Class<?> eventType;
        private final String pluginId;
        private final int priority;
        private final Consumer<Object> handler;
        private volatile boolean active = true;
        
        public EventSubscriptionImpl(long id, Class<?> eventType, String pluginId, 
                                   int priority, Consumer<Object> handler) {
            this.id = id;
            this.eventType = eventType;
            this.pluginId = pluginId;
            this.priority = priority;
            this.handler = handler;
        }
        
        @Override
        public Class<?> getEventType() {
            return eventType;
        }
        
        @Override
        public String getPluginId() {
            return pluginId;
        }
        
        @Override
        public int getPriority() {
            return priority;
        }
        
        @Override
        public boolean isActive() {
            return active;
        }
        
        @Override
        public void cancel() {
            active = false;
        }
        
        public long getId() {
            return id;
        }
        
        public Consumer<Object> getHandler() {
            return handler;
        }
    }
}