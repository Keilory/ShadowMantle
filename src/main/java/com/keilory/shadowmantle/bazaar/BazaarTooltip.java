package com.keilory.shadowmantle.bazaar;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BazaarTooltip {
    private static final Pattern QUANTITY_SUFFIX = Pattern.compile("^(.*?)(?:\\s+[x×](\\d+))$");
    private BazaarTooltip() {}

    public static void register(BazaarPriceDatabase database) {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> addPriceLines(database, stack, lines));
    }

    private static void addPriceLines(BazaarPriceDatabase database, ItemStack stack, List<Text> lines) {
        if (stack.isEmpty() || isDungeonChestSelectionItem(stack)) return;
        try {
            BazaarPrice price = database.find(stack);
            if (price == null) return;
            double sellOne = nonNegative(price.sellPrice());
            double buyOne = nonNegative(price.buyPrice());
            int quantity = displayQuantity(stack);
            lines.add(Text.empty());
            lines.add(Text.literal("Sell | Buy").formatted(Formatting.GRAY));

            MutableText priceLine = Text.literal(formatCoins(sellOne)).formatted(Formatting.GREEN);
            priceLine.append(Text.literal(" | ").formatted(Formatting.GRAY));
            priceLine.append(Text.literal(formatCoins(buyOne)).formatted(Formatting.GREEN));
            lines.add(priceLine);

            if (sellOne > 0.0 && quantity > 1) {
                lines.add(Text.literal(formatCoins(sellOne * quantity) + " [" + quantity + "]").formatted(Formatting.YELLOW));
            }
        } catch (SQLException e) {
            System.err.println("[ShadowMantle] Failed to read Bazaar price: " + e.getMessage());
        }
    }

    private static boolean isDungeonChestSelectionItem(ItemStack stack) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) return false;
        return DungeonChestParser.isChestSelection(screen) && DungeonChestParser.isChestName(stack.getName().getString());
    }

    private static int displayQuantity(ItemStack stack) {
        Matcher matcher = QUANTITY_SUFFIX.matcher(stack.getName().getString());
        if (matcher.matches()) {
            try {
                return Math.max(1, Integer.parseInt(matcher.group(2)));
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(1, stack.getCount());
    }

    private static double nonNegative(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0 ? value : 0.0;
    }

    public static String formatCoins(double value) {
        if (value < 1_000) return Long.toString(Math.max(0L, Math.round(value)));
        if (value < 1_000_000) return formatDecimal(value / 1_000) + "k";
        if (value < 1_000_000_000) return formatDecimal(value / 1_000_000) + "m";
        if (value < 1_000_000_000_000L) return formatDecimal(value / 1_000_000_000) + "b";
        return formatDecimal(value / 1_000_000_000_000L) + "t";
    }

    private static String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value).replaceAll("\\.0$", "");
    }
}
