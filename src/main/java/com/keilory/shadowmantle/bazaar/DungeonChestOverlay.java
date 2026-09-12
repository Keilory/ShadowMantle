package com.keilory.shadowmantle.bazaar;

import com.keilory.shadowmantle.core.ShadowMantleScheduler;
import com.keilory.shadowmantle.core.event.ScreenRenderEvent;
import com.keilory.shadowmantle.core.render.Overlay;
import com.keilory.shadowmantle.mixin.HandledScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DungeonChestOverlay implements Overlay {
    private static final int PANEL_WIDTH = 190;
    private static final int PANEL_PADDING = 8;
    private static final int BG = 0xE8101018;
    private static final int BORDER = 0xFF555555;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFFF5555;
    private static DungeonChestOverlay INSTANCE;
    private final BazaarPriceDatabase database;
    private final ShadowMantleScheduler scheduler;
    private final ScreenStateManager stateManager = new ScreenStateManager();

    public DungeonChestOverlay(BazaarPriceDatabase database, ShadowMantleScheduler scheduler) { this.database = database; this.scheduler = scheduler; }
    public void register() { INSTANCE = this; }
    @Override public String id() { return "dungeon-chest"; }
    @Override public int priority() { return 100; }

    public static void renderSlotHighlight(HandledScreen<?> screen, DrawContext context, Slot slot) {
        DungeonChestOverlay overlay = INSTANCE;
        if (overlay == null || slot.inventory instanceof PlayerInventory || !slot.hasStack()) return;
        ScreenState state = overlay.stateManager.get(screen);
        if (state == null || state.closed || state.snapshot == null) return;
        Highlight highlight = state.snapshot.highlights().get(slot.id);
        if (highlight != null) drawSlotIndicator(context, screen, slot, highlight.background(), highlight.border());
    }
    public static void onSlotChanged(ScreenHandler handler, int slotId) {
        DungeonChestOverlay overlay = INSTANCE; if (overlay == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof HandledScreen<?> screen) || screen.getScreenHandler() != handler) return;
        overlay.invalidateAndSchedule(screen);
    }
    public void onScreenOpened(HandledScreen<?> screen) {
        ScreenState state = stateManager.getOrCreate(screen); state.closed = false; state.generation++; state.snapshot = null; scheduleCalculation(screen, state);
    }
    public void onScreenClosed(HandledScreen<?> screen) { stateManager.remove(screen); }
    private void invalidateAndSchedule(HandledScreen<?> screen) { ScreenState state = stateManager.getOrCreate(screen); if (state.closed) return; state.generation++; if (!state.calculating) scheduleCalculation(screen, state); }
    private void scheduleCalculation(HandledScreen<?> screen, ScreenState state) {
        if (state.closed || state.calculating) return;
        long generation = state.generation; RawScreenData raw = DungeonChestStateCapture.capture(screen, generation);
        if (raw.type() == DungeonChestScreenType.OTHER) { state.snapshot = null; return; }
        state.calculating = true;
        scheduler.execute("DungeonCalc", () -> {
            CalculationResult result;
            try { result = DungeonChestCalculator.calculate(raw, database); }
            catch (SQLException | RuntimeException error) { result = new CalculationResult(raw.type(), 45, "Dungeon Chest", List.of(new CalculationLine("Price DB error", RED, null, 0, 25)), Map.of()); }
            clientPublish(screen, state, raw, result);
        });
    }
    private void clientPublish(HandledScreen<?> screen, ScreenState state, RawScreenData raw, CalculationResult result) {
        MinecraftClient.getInstance().execute(() -> {
            state.calculating = false;
            if (state.closed || stateManager.get(screen) != state) return;
            if (state.generation != raw.generation()) { scheduleCalculation(screen, state); return; }
            state.snapshot = buildRenderSnapshot(result);
        });
    }
    private RenderSnapshot buildRenderSnapshot(CalculationResult result) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer; List<RenderLine> lines = new ArrayList<>(result.lines().size());
        for (CalculationLine line : result.lines()) {
            String primary = trimToPanel(line.text(), renderer); OrderedText primaryText = Text.literal(primary).asOrderedText(); OrderedText secondaryText = null; int secondaryXOffset = 0;
            if (line.secondaryText() != null) { secondaryText = Text.literal(line.secondaryText()).asOrderedText(); secondaryXOffset = renderer.getWidth(primaryText) + 4; }
            lines.add(new RenderLine(primaryText, line.color(), secondaryText, line.secondaryColor(), secondaryXOffset, line.yOffset()));
        }
        return new RenderSnapshot(result.type(), result.panelHeight(), Text.literal(result.title()).formatted(Formatting.WHITE).asOrderedText(), List.copyOf(lines), result.highlights());
    }
    @Override public void render(ScreenRenderEvent event) { renderAfterScreen(event.screen(), event.context()); }
    private void renderAfterScreen(Screen screen, DrawContext context) {
        if (!(screen instanceof HandledScreen<?> handledScreen)) return; ScreenState state = stateManager.get(handledScreen); if (state == null || state.closed || state.snapshot == null) return;
        RenderSnapshot snapshot = state.snapshot; if (snapshot.type() != DungeonChestScreenType.SELECTION && snapshot.type() != DungeonChestScreenType.REWARD) return;
        int x = panelX(handledScreen.width, PANEL_WIDTH); int y = Math.max(4, handledScreen.height / 2 - snapshot.panelHeight() / 2); drawPanel(context, x, y, PANEL_WIDTH, snapshot.panelHeight(), snapshot.title());
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        for (RenderLine line : snapshot.lines()) { context.drawTextWithShadow(renderer, line.text(), x + PANEL_PADDING, y + line.yOffset(), line.color()); if (line.secondaryText() != null) context.drawTextWithShadow(renderer, line.secondaryText(), x + PANEL_PADDING + line.secondaryXOffset(), y + line.yOffset(), line.secondaryColor()); }
    }
    private static String trimToPanel(String value, TextRenderer renderer) { return renderer.trimToWidth(value, PANEL_WIDTH - PANEL_PADDING * 2); }
    private static void drawPanel(DrawContext context, int x, int y, int width, int height, OrderedText title) { context.fill(x, y, x + width, y + height, BG); context.fill(x, y, x + width, y + 1, BORDER); context.fill(x, y + height - 1, x + width, y + height, BORDER); context.fill(x, y, x + 1, y + height, BORDER); context.fill(x + width - 1, y, x + width, y + height, BORDER); context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, title, x + width / 2, y + 6, WHITE); }
    private static void drawSlotIndicator(DrawContext context, HandledScreen<?> screen, Slot slot, int background, int border) { int x = ((HandledScreenAccessor) screen).fakePixel$getX() + slot.x; int y = ((HandledScreenAccessor) screen).fakePixel$getY() + slot.y; context.fill(x - 1, y - 1, x + 17, y + 17, background); context.fill(x - 1, y - 1, x + 17, y, border); context.fill(x - 1, y + 16, x + 17, y + 17, border); context.fill(x - 1, y - 1, x, y + 17, border); context.fill(x + 16, y - 1, x + 17, y + 17, border); }
    private int panelX(int screenWidth, int width) { int right = screenWidth / 2 + 100; if (right + width <= screenWidth - 4) return right; return Math.max(4, screenWidth / 2 - 100 - width - 8); }
    public void close() { if (INSTANCE == this) INSTANCE = null; stateManager.clear(); }
}
