package com.keilory.shadowmantle.core;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared in-memory state registry for immutable feature snapshots.
 * Values are owned by features; the core only provides namespaced storage.
 */
public final class StateStore {
    private final Map<String, Object> values = new ConcurrentHashMap<>();

    public <T> void put(String key, T value) {
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = values.get(key);
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    public void remove(String key) {
        values.remove(key);
    }

    public void clear() {
        values.clear();
    }
}
