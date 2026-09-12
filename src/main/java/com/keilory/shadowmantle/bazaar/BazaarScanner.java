package com.keilory.shadowmantle.bazaar;

import com.keilory.shadowmantle.core.ShadowMantleScheduler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class BazaarScanner {
    private static BazaarScanner INSTANCE;

    private final BazaarPriceDatabase database;
    private final ShadowMantleScheduler scheduler;
    private final Set<HandledScreen<?>> bazaarSessionScreens = Collections.newSetFromMap(new WeakHashMap<>());
    private boolean inBazaarNavigation;
    private ScreenHandler dirtyHandler;
    private boolean dirty;
    private boolean scanRunning;

    private ScreenHandler snapshotHandler;
    private final Map<Integer, BazaarSlotSnapshot> slotSnapshots = new LinkedHashMap<>();
    private final Set<Integer> dirtySlotIds = new HashSet<>();
    private boolean fullSnapshotDirty;

    public BazaarScanner(BazaarPriceDatabase database, ShadowMantleScheduler scheduler) {
        this.database = database;
        this.scheduler = scheduler;
    }

    public void register() { INSTANCE = this; }

    public static void onSlotChanged(ScreenHandler handler, int slotId) {
        BazaarScanner scanner = INSTANCE;
        if (scanner == null) return;
        if (slotId >= 0 && slotId < handler.slots.size() && handler.slots.get(slotId).inventory instanceof PlayerInventory) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) return;
        if (screen.getScreenHandler() != handler || !scanner.isBazaarScreen(screen)) return;
        scanner.markDirty(handler, slotId);
    }

    public void onClientTick(MinecraftClient client) {
        flushIfDirty(client);
        validateBazaarSession(client);
    }

    public void onScreenOpened(HandledScreen<?> handledScreen) {
        if (isBazaarRoot(handledScreen)) beginBazaarSession(handledScreen);
        else if (inBazaarNavigation && hasContainerSlots(handledScreen)) bazaarSessionScreens.add(handledScreen);
        else return;
        resetSnapshotCache(handledScreen.getScreenHandler());
        markDirty(handledScreen.getScreenHandler(), -1);
    }

    public void onScreenClosed(HandledScreen<?> screen) {
        if (screen.getScreenHandler() == dirtyHandler) { dirtyHandler = null; dirty = false; }
        if (screen.getScreenHandler() == snapshotHandler) {
            snapshotHandler = null;
            slotSnapshots.clear();
            dirtySlotIds.clear();
            fullSnapshotDirty = false;
        }
        bazaarSessionScreens.remove(screen);
    }

    private void flushIfDirty(MinecraftClient client) {
        if (!dirty || scanRunning) return;
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) { resetNavigation(); return; }
        ScreenHandler handler = screen.getScreenHandler();
        if (handler != dirtyHandler || !isBazaarScreen(screen)) { dirty = false; dirtyHandler = null; return; }
        List<BazaarSlotSnapshot> snapshot = captureIncrementalSnapshot(handler, client);
        dirty = false; dirtyHandler = null; scanRunning = true;
        scheduler.execute("BazaarScan", () -> {
            try { List<BazaarPrice> prices = BazaarParser.parseScreen(snapshot); if (!prices.isEmpty()) database.saveBatch(prices); }
            catch (SQLException | RuntimeException error) { System.err.println("[ShadowMantle] Failed to update Bazaar prices: " + error.getMessage()); }
            finally { client.execute(() -> scanRunning = false); }
        });
    }

    private List<BazaarSlotSnapshot> captureIncrementalSnapshot(ScreenHandler handler, MinecraftClient client) {
        if (snapshotHandler != handler) resetSnapshotCache(handler);
        if (fullSnapshotDirty) {
            slotSnapshots.clear();
            for (Slot slot : handler.slots) captureSlotSnapshot(slot, client);
            fullSnapshotDirty = false; dirtySlotIds.clear();
        } else if (!dirtySlotIds.isEmpty()) {
            for (Integer slotId : List.copyOf(dirtySlotIds)) {
                if (slotId == null || slotId < 0 || slotId >= handler.slots.size()) continue;
                captureSlotSnapshot(handler.slots.get(slotId), client);
            }
            dirtySlotIds.clear();
        }
        return List.copyOf(slotSnapshots.values());
    }

    private void captureSlotSnapshot(Slot slot, MinecraftClient client) {
        int slotId = slot.id;
        if (slot.inventory instanceof PlayerInventory || !slot.hasStack()) { slotSnapshots.remove(slotId); return; }
        ItemStack stack = slot.getStack();
        List<String> tooltipLines = new ArrayList<>();
        for (Text line : stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC)) tooltipLines.add(line.getString());
        slotSnapshots.put(slotId, new BazaarSlotSnapshot(stack.getName().getString(), tooltipLines));
    }

    private void resetSnapshotCache(ScreenHandler handler) { snapshotHandler = handler; slotSnapshots.clear(); dirtySlotIds.clear(); fullSnapshotDirty = true; }
    private void beginBazaarSession(HandledScreen<?> screen) { inBazaarNavigation = true; bazaarSessionScreens.clear(); bazaarSessionScreens.add(screen); }
    private boolean hasContainerSlots(HandledScreen<?> screen) { for (Slot slot : screen.getScreenHandler().slots) if (!(slot.inventory instanceof PlayerInventory)) return true; return false; }

    private void markDirty(ScreenHandler handler, int slotId) {
        dirtyHandler = handler; dirty = true;
        if (snapshotHandler != handler) resetSnapshotCache(handler);
        if (slotId < 0) { fullSnapshotDirty = true; dirtySlotIds.clear(); }
        else if (!fullSnapshotDirty) dirtySlotIds.add(slotId);
    }

    private void validateBazaarSession(MinecraftClient client) {
        if (!inBazaarNavigation) return;
        if (!(client.currentScreen instanceof HandledScreen<?> screen) || !isBazaarScreen(screen)) resetNavigation();
    }
    private boolean isBazaarRoot(HandledScreen<?> screen) { String title = screen.getTitle().getString().toLowerCase(Locale.ROOT); return title.contains("bazaar") || title.contains("базар"); }
    private boolean isBazaarScreen(HandledScreen<?> screen) { return bazaarSessionScreens.contains(screen); }
    private void resetNavigation() { dirtyHandler = null; dirty = false; snapshotHandler = null; slotSnapshots.clear(); dirtySlotIds.clear(); fullSnapshotDirty = false; inBazaarNavigation = false; bazaarSessionScreens.clear(); }

    public void close() {
        INSTANCE = null; inBazaarNavigation = false; bazaarSessionScreens.clear(); dirtyHandler = null; dirty = false;
        snapshotHandler = null; slotSnapshots.clear(); dirtySlotIds.clear(); fullSnapshotDirty = false;
    }
}
