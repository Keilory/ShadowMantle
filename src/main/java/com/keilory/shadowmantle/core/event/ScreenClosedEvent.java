package com.keilory.shadowmantle.core.event;

import net.minecraft.client.gui.screen.ingame.HandledScreen;

public record ScreenClosedEvent(HandledScreen<?> screen) {
}
