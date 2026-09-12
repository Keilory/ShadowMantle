package com.keilory.shadowmantle.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Lightweight internal event bus used by ShadowMantle features.
 * Events are plain Java objects; subscribers decide which thread they require.
 * Listener failures are isolated so one feature cannot break event delivery to others.
 */
public final class EventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger("shadowmantle/EventBus");
    private final Map<Class<?>, CopyOnWriteArrayList<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    public <E> Subscription subscribe(Class<E> eventType, Consumer<E> listener) {
        if (eventType == null) throw new IllegalArgumentException("eventType");
        if (listener == null) throw new IllegalArgumentException("listener");
        listeners.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>()).add(listener);
        return new SubscriptionImpl<>(this, eventType, listener);
    }

    public <E> void unsubscribe(Class<E> eventType, Consumer<E> listener) {
        List<Consumer<?>> registered = listeners.get(eventType);
        if (registered != null) {
            registered.remove(listener);
            if (registered.isEmpty()) {
                listeners.remove(eventType, registered);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <E> void post(E event) {
        if (event == null) return;
        List<Consumer<?>> registered = listeners.get(event.getClass());
        if (registered == null) return;
        for (Consumer<?> listener : registered) {
            try {
                ((Consumer<E>) listener).accept(event);
            } catch (RuntimeException error) {
                LOGGER.error("Event listener failed for {}", event.getClass().getSimpleName(), error);
            }
        }
    }

    public void clear() {
        listeners.clear();
    }

    @FunctionalInterface
    public interface Subscription {
        void unsubscribe();
    }

    private static final class SubscriptionImpl<E> implements Subscription {
        private final EventBus bus;
        private final Class<E> eventType;
        private final Consumer<E> listener;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private SubscriptionImpl(EventBus bus, Class<E> eventType, Consumer<E> listener) {
            this.bus = bus;
            this.eventType = eventType;
            this.listener = listener;
        }

        @Override
        public void unsubscribe() {
            if (active.compareAndSet(true, false)) {
                bus.unsubscribe(eventType, listener);
            }
        }
    }
}
