package com.keilory.shadowmantle.bazaar;

import java.util.List;

/**
 * Immutable Bazaar GUI data captured on the client thread.
 * Contains no Minecraft objects so it can safely cross to the parser worker.
 */
public record BazaarSlotSnapshot(String itemName, List<String> tooltipLines) {
    public BazaarSlotSnapshot {
        itemName = itemName == null ? "" : itemName;
        tooltipLines = List.copyOf(tooltipLines);
    }
}
