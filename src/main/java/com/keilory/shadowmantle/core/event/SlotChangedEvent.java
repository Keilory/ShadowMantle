package com.keilory.shadowmantle.core.event;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

public record SlotChangedEvent(ScreenHandler handler, int slotId, ItemStack stack) {
}
