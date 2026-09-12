package com.keilory.shadowmantle.core;

import com.keilory.shadowmantle.core.event.ScreenRenderEvent;
import com.keilory.shadowmantle.core.render.OverlayEngine;
import net.minecraft.client.MinecraftClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared foundation for all ShadowMantle features.
 * This class deliberately contains no feature-specific logic.
 */
public final class ShadowMantleCore {
    private static ShadowMantleCore instance;

    private final MinecraftClient client;
    private final EventBus eventBus = new EventBus();
    private final StateStore stateStore = new StateStore();
    private final ShadowMantleScheduler scheduler = new ShadowMantleScheduler();
    private final OverlayEngine overlayEngine = new OverlayEngine();
    private final Map<String, Feature> features = new ConcurrentHashMap<>();

    private ShadowMantleCore(MinecraftClient client) {
        this.client = client;
        eventBus.subscribe(ScreenRenderEvent.class, overlayEngine::render);
    }

    public static ShadowMantleCore initialize(MinecraftClient client) {
        if (instance != null) return instance;
        instance = new ShadowMantleCore(client);
        CoreEventBridge.register(instance);
        return instance;
    }

    public static ShadowMantleCore get() {
        if (instance == null) throw new IllegalStateException("ShadowMantleCore has not been initialized");
        return instance;
    }

    public MinecraftClient client() { return client; }
    public EventBus events() { return eventBus; }
    public StateStore state() { return stateStore; }
    public ShadowMantleScheduler scheduler() { return scheduler; }
    public OverlayEngine overlays() { return overlayEngine; }

    public Feature feature(String id) {
        return features.get(id);
    }

    public void registerFeature(Feature feature) {
        if (feature == null) throw new IllegalArgumentException("feature");
        Feature previous = features.putIfAbsent(feature.id(), feature);
        if (previous != null) throw new IllegalStateException("Duplicate ShadowMantle feature: " + feature.id());
        try {
            feature.initialize(this);
        } catch (RuntimeException error) {
            features.remove(feature.id(), feature);
            throw error;
        }
    }

    public void shutdown() {
        for (Feature feature : features.values()) {
            try {
                feature.shutdown();
            } catch (RuntimeException error) {
                System.err.println("[ShadowMantle] Failed to shut down feature " + feature.id() + ": " + error.getMessage());
            }
        }
        features.clear();
        overlayEngine.clear();
        eventBus.clear();
        stateStore.clear();
        scheduler.shutdown();
        instance = null;
    }
}
