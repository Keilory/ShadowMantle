package com.keilory.shadowmantle.core;

import com.keilory.shadowmantle.core.event.ClientTickEvent;
import com.keilory.shadowmantle.core.event.ScreenClosedEvent;
import com.keilory.shadowmantle.core.event.ScreenOpenedEvent;
import com.keilory.shadowmantle.core.event.ScreenRenderEvent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

public final class CoreEventBridge {
    private CoreEventBridge() { }

    public static void register(ShadowMantleCore core) {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof HandledScreen<?> handledScreen)) return;
            core.events().post(new ScreenOpenedEvent(handledScreen));
            ScreenEvents.afterRender(screen).register((renderedScreen, context, mouseX, mouseY, tickDelta) ->
                    core.events().post(new ScreenRenderEvent(renderedScreen, context, mouseX, mouseY, tickDelta)));
            ScreenEvents.remove(screen).register(removedScreen -> core.events().post(new ScreenClosedEvent(handledScreen)));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> core.events().post(new ClientTickEvent(client)));
    }
}
