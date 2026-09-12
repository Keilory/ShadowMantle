package com.keilory.shadowmantle.core.event;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/** Fired once for each handled-screen render after the screen itself has rendered. */
public record ScreenRenderEvent(Screen screen, DrawContext context, int mouseX, int mouseY, float tickDelta) {
}
