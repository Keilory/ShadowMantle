package com.keilory.shadowmantle.bazaar;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DungeonChestStateCapture {
    private DungeonChestStateCapture() {
    }

    static RawScreenData capture(HandledScreen<?> screen, long generation) {
        DungeonChestScreenType type = detectType(screen);
        List<RawSlotData> slots = new ArrayList<>();

        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.inventory instanceof PlayerInventory || !slot.hasStack()) continue;

            ItemStack stack = slot.getStack();
            List<String> tooltip = new ArrayList<>();
            if (type == DungeonChestScreenType.CROESUS || type == DungeonChestScreenType.SELECTION) {
                for (Text line : stack.getTooltip(Item.TooltipContext.DEFAULT, null, TooltipType.BASIC)) {
                    tooltip.add(line.getString());
                }
            }
            slots.add(new RawSlotData(slot.id, stack.getName().getString(), stack.getCount(), List.copyOf(tooltip)));
        }

        return new RawScreenData(type, screen.getTitle().getString(), generation, List.copyOf(slots));
    }

    private static DungeonChestScreenType detectType(HandledScreen<?> screen) {
        String title = normalize(screen.getTitle().getString());
        if (title.equalsIgnoreCase("Croesus")) return DungeonChestScreenType.CROESUS;
        if (title.toLowerCase(Locale.ROOT).startsWith("the catacombs - floor ")) return DungeonChestScreenType.SELECTION;
        if (DungeonChestParser.isChestName(title)) return DungeonChestScreenType.REWARD;
        return DungeonChestScreenType.OTHER;
    }

    private static String normalize(String value) {
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}
