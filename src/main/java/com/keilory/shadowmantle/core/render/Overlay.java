package com.keilory.shadowmantle.core.render;

import com.keilory.shadowmantle.core.event.ScreenRenderEvent;

/** Small render-layer contract used by the shared overlay engine. */
public interface Overlay {
    String id();

    default int priority() {
        return 0;
    }

    void render(ScreenRenderEvent event);
}
