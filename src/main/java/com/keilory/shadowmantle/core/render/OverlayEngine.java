package com.keilory.shadowmantle.core.render;

import com.keilory.shadowmantle.core.event.ScreenRenderEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Central render dispatcher. Overlay code is invoked only from the client render callback. */
public final class OverlayEngine {
    private final Map<String, Overlay> overlays = new ConcurrentHashMap<>();

    public OverlayHandle register(Overlay overlay) {
        if (overlay == null) throw new IllegalArgumentException("overlay");
        Overlay previous = overlays.putIfAbsent(overlay.id(), overlay);
        if (previous != null) throw new IllegalStateException("Duplicate overlay: " + overlay.id());
        return () -> overlays.remove(overlay.id(), overlay);
    }

    public void render(ScreenRenderEvent event) {
        var ordered = new ArrayList<>(overlays.values());
        ordered.sort(Comparator.comparingInt(Overlay::priority).thenComparing(Overlay::id));
        for (Overlay overlay : ordered) {
            try {
                overlay.render(event);
            } catch (RuntimeException error) {
                System.err.println("[ShadowMantle] Overlay '" + overlay.id() + "' failed: " + error.getMessage());
            }
        }
    }

    public void clear() { overlays.clear(); }
}
