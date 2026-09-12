package com.keilory.shadowmantle.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

final class Ui {
    static final int BG = 0xF50B0E14;
    static final int BG_PANEL = 0xF5161921;
    static final int BG_CARD = 0xFF1D222C;
    static final int BG_CARD_HOVER = 0xFF252C38;
    static final int BG_TRACK = 0xFF11151C;
    static final int ACCENT = 0xFF7C6CFF;
    static final int ACCENT_DARK = 0xFF302B54;
    static final int TEXT_PRIMARY = 0xFFF4F5FA;
    static final int TEXT_SECONDARY = 0xFFD0D3DC;
    static final int TEXT_MUTED = 0xFF858B9A;
    static final int GOOD = 0xFF5DE2A5;
    static final int BAD = 0xFFFF667A;

    private Ui() {}

    static void background(DrawContext context, int width, int height) {
        context.fill(0, 0, width, height, BG);
        // Subtle framing lines give the flat vanilla surface more depth without
        // using blur, shaders, or per-frame texture allocation.
        context.fill(0, 0, width, 1, 0xFF252A35);
        context.fill(0, height - 1, width, height, 0xFF07090D);
    }

    static void drawRounded(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        c.fill(x + r, y, x + w - r, y + h, color);
        c.fill(x, y + r, x + r, y + h - r, color);
        c.fill(x + w - r, y + r, x + w, y + h - r, color);
        if (r > 1) {
            c.fill(x + 1, y + 1, x + r, y + r, color);
            c.fill(x + w - r, y + 1, x + w - 1, y + r, color);
            c.fill(x + 1, y + h - r, x + r, y + h - 1, color);
            c.fill(x + w - r, y + h - r, x + w - 1, y + h - 1, color);
        }
    }

    static void text(DrawContext c, String value, int x, int y, int color, boolean shadow) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        Text text = Text.literal(value);
        if (shadow) c.drawTextWithShadow(renderer, text, x, y, color);
        else c.drawText(renderer, text, x, y, color, false);
    }

    static void textRight(DrawContext c, String value, int right, int y, int color) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        c.drawTextWithShadow(renderer, Text.literal(value), right - renderer.getWidth(value), y, color);
    }

    static void center(DrawContext c, String value, int centerX, int y, int color, boolean shadow) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int x = centerX - renderer.getWidth(value) / 2;
        if (shadow) c.drawTextWithShadow(renderer, Text.literal(value), x, y, color);
        else c.drawText(renderer, Text.literal(value), x, y, color, false);
    }

    static void button(DrawContext c, int x, int y, int w, int h, String label, int mx, int my, boolean enabled) {
        boolean hover = enabled && mx >= x && mx <= x + w && my >= y && my <= y + h;
        int bg = !enabled ? 0xFF171A21 : hover ? BG_CARD_HOVER : BG_CARD;
        int border = hover ? ACCENT : 0xFF2A303C;
        drawRounded(c, x, y, w, h, 9, bg);
        c.fill(x, y, x + w, y + 1, border);
        c.fill(x, y + h - 1, x + w, y + h, border);
        center(c, label, x + w / 2, y + 14, enabled ? TEXT_SECONDARY : TEXT_MUTED, true);
    }
}
