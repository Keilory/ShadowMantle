package com.keilory.shadowmantle.bazaar;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.OrderedText;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

record RawSlotData(int slotId, String itemName, int count, List<String> tooltipLines) {}

record RawScreenData(DungeonChestScreenType type, String title, long generation, List<RawSlotData> slots) {}

record Highlight(int background, int border) {}

record CalculationLine(
        String text,
        int color,
        String secondaryText,
        int secondaryColor,
        int yOffset
) {}

record CalculationResult(
        DungeonChestScreenType type,
        int panelHeight,
        String title,
        List<CalculationLine> lines,
        Map<Integer, Highlight> highlights
) {}

record RenderLine(
        OrderedText text,
        int color,
        OrderedText secondaryText,
        int secondaryColor,
        int secondaryXOffset,
        int yOffset
) {}

record RenderSnapshot(
        DungeonChestScreenType type,
        int panelHeight,
        OrderedText title,
        List<RenderLine> lines,
        Map<Integer, Highlight> highlights
) {}

enum DungeonChestScreenType { OTHER, CROESUS, SELECTION, REWARD }

final class ScreenState {
    long generation;
    boolean closed;
    boolean calculating;
    RenderSnapshot snapshot;
}

final class ScreenStateManager {
    private final WeakHashMap<HandledScreen<?>, ScreenState> states = new WeakHashMap<>();

    ScreenState getOrCreate(HandledScreen<?> screen) {
        return states.computeIfAbsent(screen, ignored -> new ScreenState());
    }

    ScreenState get(HandledScreen<?> screen) {
        return states.get(screen);
    }

    void remove(HandledScreen<?> screen) {
        ScreenState state = states.remove(screen);
        if (state != null) {
            state.closed = true;
            state.generation++;
            state.snapshot = null;
        }
    }

    void clear() {
        for (ScreenState state : states.values()) {
            state.closed = true;
            state.generation++;
            state.snapshot = null;
        }
        states.clear();
    }
}
