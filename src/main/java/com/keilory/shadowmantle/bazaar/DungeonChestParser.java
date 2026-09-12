package com.keilory.shadowmantle.bazaar;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipType;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DungeonChestParser {
    private static final Pattern QUANTITY_SUFFIX = Pattern.compile("^(.*?)(?:\\s+[x×](\\d+))$");
    private static final List<String> CHEST_NAMES = List.of(
            "Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock"
    );
    private static final Map<String, ChestOffer> LAST_SELECTION = new HashMap<>();

    private DungeonChestParser() {
    }

    public static boolean isCroesus(HandledScreen<?> screen) {
        return normalize(screen.getTitle().getString()).equalsIgnoreCase("Croesus");
    }

    public static boolean isChestSelection(HandledScreen<?> screen) {
        String title = normalize(screen.getTitle().getString());
        return title.toLowerCase(Locale.ROOT).startsWith("the catacombs - floor ");
    }

    public static boolean isRewardChest(HandledScreen<?> screen) {
        return isChestName(screen.getTitle().getString());
    }

    public static boolean isChestName(String name) {
        String normalized = normalize(name);
        return CHEST_NAMES.stream().anyMatch(chest -> normalized.equalsIgnoreCase(chest) ||
                normalized.equalsIgnoreCase(chest + " Chest"));
    }

    private static String chestTierName(String name) {
        String normalized = normalize(name);
        for (String chest : CHEST_NAMES) {
            if (normalized.equalsIgnoreCase(chest) || normalized.equalsIgnoreCase(chest + " Chest")) {
                return chest;
            }
        }
        return normalized;
    }

    public static List<ChestOffer> parseSelection(HandledScreen<?> screen, BazaarPriceDatabase database)
            throws SQLException {
        List<ChestOffer> offers = new ArrayList<>();
        int openedCount = 0;

        for (Slot slot : screen.getScreenHandler().slots) {
            if (!slot.hasStack() || slot.inventory instanceof PlayerInventory) {
                continue;
            }
            ItemStack stack = slot.getStack();
            String chestName = normalize(stack.getName().getString());
            if (!CHEST_NAMES.contains(chestName)) {
                continue;
            }

            List<String> tooltip = tooltipLines(stack);
            boolean opened = tooltip.stream().anyMatch(line -> normalize(line).equalsIgnoreCase("already opened!"));
            if (opened) {
                openedCount++;
            }

            int contentsIndex = indexOf(tooltip, "contents");
            if (contentsIndex < 0) {
                continue;
            }

            List<Reward> rewards = new ArrayList<>();
            for (int i = contentsIndex + 1; i < tooltip.size(); i++) {
                String line = normalize(tooltip.get(i));
                if (line.isEmpty() || line.equalsIgnoreCase("cost")) {
                    break;
                }
                if (line.equalsIgnoreCase("click to open!") || line.equalsIgnoreCase("already opened!") ||
                        line.equalsIgnoreCase("you can't open more chests!") ||
                        line.equalsIgnoreCase("no more chests to open!") ||
                        line.equalsIgnoreCase("dungeon chest key") || line.equalsIgnoreCase("sell | buy")) {
                    continue;
                }
                if (line.matches("(?i)^(?:n/?a|free|.* coins)$")) {
                    continue;
                }
                if (line.contains("|") && line.matches(".*\\|.*")) {
                    continue;
                }
                rewards.add(parseQuantity(line));
            }

            double chestCost = parseChestCost(tooltip);
            boolean requiresKey = tooltip.stream().map(DungeonChestParser::normalize)
                    .anyMatch(line -> line.equalsIgnoreCase("dungeon chest key"));
            boolean serverBlocked = tooltip.stream().map(DungeonChestParser::normalize)
                    .anyMatch(line -> line.equalsIgnoreCase("you can't open more chests!") ||
                            line.equalsIgnoreCase("no more chests to open!"));
            BazaarPrice keyPrice = requiresKey ? database.findByName("Dungeon Chest Key") : null;

            ChestOffer offer = buildOffer(
                    chestName, opened, rewards, chestCost, requiresKey, keyPrice, !serverBlocked, database);
            offers.add(offer);
        }

        // Croesus allows at most two chest openings. If two chests are already
        // opened, every remaining chest is visibly unavailable.
        if (openedCount >= 2) {
            for (int i = 0; i < offers.size(); i++) {
                ChestOffer offer = offers.get(i);
                if (!offer.opened()) {
                    offers.set(i, offer.withAvailable(false));
                }
            }
        }

        // Keep all parsed offers cached so the opened reward screen can still
        // recover the chest cost/key requirement after the selection screen closes.
        for (ChestOffer offer : offers) {
            LAST_SELECTION.put(offer.chestName().toLowerCase(Locale.ROOT), offer);
        }

        offers.removeIf(ChestOffer::opened);
        offers.sort(Comparator.comparingInt(DungeonChestParser::chestOrder));
        return offers;
    }

    public static ChestOffer parseOpenedChest(HandledScreen<?> screen, BazaarPriceDatabase database)
            throws SQLException {
        String chestName = chestTierName(screen.getTitle().getString());
        List<Reward> rewards = new ArrayList<>();
        boolean started = false;
        for (Slot slot : screen.getScreenHandler().slots) {
            if (!slot.hasStack()) {
                continue;
            }
            ItemStack stack = slot.getStack();
            String itemName = normalize(stack.getName().getString());
            if (itemName.equalsIgnoreCase("Open Reward Chest")) {
                started = true;
                continue;
            }
            if (started || itemName.equalsIgnoreCase("Go Back") || itemName.equalsIgnoreCase("Close")) {
                continue;
            }
            if (itemName.isEmpty()) {
                continue;
            }
            rewards.add(parseQuantity(itemName));
        }

        ChestOffer selected = LAST_SELECTION.get(chestName.toLowerCase(Locale.ROOT));
        if (selected != null) {
            return buildOffer(
                    chestName,
                    true,
                    rewards,
                    selected.chestCost(),
                    selected.requiresKey(),
                    selected.requiresKey() ? database.findByName("Dungeon Chest Key") : null,
                    false,
                    database
            );
        }

        return buildOffer(chestName, true, rewards, 0.0, false, null, false, database);
    }

    private static ChestOffer buildOffer(
            String chestName,
            boolean opened,
            List<Reward> rewards,
            double chestCost,
            boolean requiresKey,
            BazaarPrice keyPrice,
            boolean available,
            BazaarPriceDatabase database
    ) throws SQLException {
        double rewardValue = 0.0;
        List<String> missing = new ArrayList<>();
        List<ValuedReward> valuedRewards = new ArrayList<>();

        for (Reward reward : rewards) {
            BazaarPrice price = database.findByName(reward.itemName);
            boolean knownInBazaar = price != null;
            Double unitSell = knownInBazaar ? positive(price.sellPrice()) : null;
            double total = unitSell == null ? 0.0 : unitSell * reward.quantity;
            if (unitSell != null) {
                rewardValue += total;
            } else if (!knownInBazaar) {
                missing.add(reward.itemName + " x" + reward.quantity);
            }
            valuedRewards.add(new ValuedReward(
                    reward.itemName,
                    reward.quantity,
                    unitSell,
                    total,
                    knownInBazaar
            ));
        }

        double keyCost = 0.0;
        boolean keyPriceMissing = false;
        if (requiresKey) {
            Double keyBuy = keyPrice == null ? null : positive(keyPrice.buyPrice());
            if (keyBuy == null) {
                keyPriceMissing = true;
            } else {
                keyCost = keyBuy;
            }
        }

        double netProfit = rewardValue - chestCost - keyCost;
        return new ChestOffer(
                chestName,
                opened,
                valuedRewards,
                rewardValue,
                chestCost,
                requiresKey,
                keyCost,
                keyPriceMissing,
                netProfit,
                missing,
                available
        );
    }

    private static double parseChestCost(List<String> tooltip) {
        for (int i = 0; i + 1 < tooltip.size(); i++) {
            if (!normalize(tooltip.get(i)).equalsIgnoreCase("cost")) {
                continue;
            }
            String value = normalize(tooltip.get(i + 1));
            if (value.equalsIgnoreCase("free")) {
                return 0.0;
            }
            if (value.toLowerCase(Locale.ROOT).endsWith(" coins")) {
                Double parsed = BazaarParser.parseCoins(value.substring(0, value.length() - 6));
                return parsed == null ? 0.0 : parsed;
            }
        }
        return 0.0;
    }

    private static int indexOf(List<String> lines, String expected) {
        for (int i = 0; i < lines.size(); i++) {
            if (normalize(lines.get(i)).equalsIgnoreCase(expected)) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> tooltipLines(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        for (Text line : stack.getTooltip(Item.TooltipContext.DEFAULT, null, TooltipType.BASIC)) {
            lines.add(line.getString());
        }
        return lines;
    }

    public static Reward parseQuantity(String value) {
        Matcher matcher = QUANTITY_SUFFIX.matcher(value);
        if (matcher.matches()) {
            return new Reward(matcher.group(1).trim(), Integer.parseInt(matcher.group(2)));
        }
        return new Reward(value.trim(), 1);
    }

    private static Double positive(Double value) {
        return value != null && Double.isFinite(value) && value > 0.0 ? value : null;
    }

    private static int chestOrder(ChestOffer offer) {
        return switch (offer.chestName.toLowerCase(Locale.ROOT)) {
            case "wood" -> 0;
            case "gold" -> 1;
            case "diamond" -> 2;
            case "emerald" -> 3;
            case "obsidian" -> 4;
            case "bedrock" -> 5;
            default -> 99;
        };
    }

    private static String normalize(String value) {
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    public record Reward(String itemName, int quantity) {
    }

    public record ValuedReward(
            String itemName,
            int quantity,
            Double unitSellPrice,
            double totalValue,
            boolean knownInBazaar
    ) {
    }

    public record ChestOffer(
            String chestName,
            boolean opened,
            List<ValuedReward> rewards,
            double rewardValue,
            double chestCost,
            boolean requiresKey,
            double keyCost,
            boolean keyPriceMissing,
            double netProfit,
            List<String> missingPrices,
            boolean available
    ) {
        private ChestOffer withAvailable(boolean value) {
            return new ChestOffer(
                    chestName, opened, rewards, rewardValue, chestCost, requiresKey,
                    keyCost, keyPriceMissing, netProfit, missingPrices, value);
        }
    }
}
