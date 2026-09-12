package com.keilory.shadowmantle.bazaar;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

final class DungeonChestCalculator {
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int UNAVAILABLE_SLOT_BG = 0x60FF3333;
    private static final int UNAVAILABLE_SLOT_BORDER = 0xFFFF5555;
    private static final Pattern QUANTITY_SUFFIX = Pattern.compile("^(.*?)(?:\\s+[x×](\\d+))$");
    private static final List<String> CHEST_NAMES = List.of("Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock");
    private static final Map<String, DungeonChestParser.ChestOffer> LAST_SELECTION = new ConcurrentHashMap<>();

    private DungeonChestCalculator() {}

    static CalculationResult calculate(RawScreenData raw, BazaarPriceDatabase database) throws SQLException {
        return switch (raw.type()) {
            case CROESUS -> calculateCroesus(raw);
            case SELECTION -> calculateSelection(raw, database);
            case REWARD -> calculateReward(raw, database);
            case OTHER -> new CalculationResult(DungeonChestScreenType.OTHER, 0, "", List.of(), Map.of());
        };
    }

    private static CalculationResult calculateCroesus(RawScreenData raw) {
        java.util.HashMap<Integer, Highlight> highlights = new java.util.HashMap<>();
        for (RawSlotData slot : raw.slots()) {
            if (!slot.itemName().trim().equalsIgnoreCase("The Catacombs")) continue;
            boolean unavailable = slot.tooltipLines().stream().map(DungeonChestCalculator::normalize)
                    .anyMatch(line -> line.equalsIgnoreCase("no more chests to open!") || line.equalsIgnoreCase("you can't open more chests!"));
            highlights.put(slot.slotId(), new Highlight(unavailable ? UNAVAILABLE_SLOT_BG : withAlpha(GREEN, 0x70), unavailable ? UNAVAILABLE_SLOT_BORDER : GREEN));
        }
        return new CalculationResult(DungeonChestScreenType.CROESUS, 0, "", List.of(), Map.copyOf(highlights));
    }

    private static CalculationResult calculateSelection(RawScreenData raw, BazaarPriceDatabase database) throws SQLException {
        LAST_SELECTION.clear();
        List<ParsedSelectionSlot> parsed = new ArrayList<>();
        int openedCount = 0;
        for (RawSlotData slot : raw.slots()) {
            String chestName = normalize(slot.itemName());
            if (!CHEST_NAMES.contains(chestName)) continue;
            List<String> tooltip = slot.tooltipLines();
            boolean opened = tooltip.stream().map(DungeonChestCalculator::normalize).anyMatch(line -> line.equalsIgnoreCase("already opened!"));
            if (opened) openedCount++;
            int contentsIndex = indexOf(tooltip, "contents");
            if (contentsIndex < 0) continue;
            List<DungeonChestParser.Reward> rewards = new ArrayList<>();
            for (int i = contentsIndex + 1; i < tooltip.size(); i++) {
                String line = normalize(tooltip.get(i));
                if (line.isEmpty() || line.equalsIgnoreCase("cost")) break;
                if (line.equalsIgnoreCase("click to open!") || line.equalsIgnoreCase("already opened!") || line.equalsIgnoreCase("you can't open more chests!") || line.equalsIgnoreCase("no more chests to open!") || line.equalsIgnoreCase("dungeon chest key") || line.equalsIgnoreCase("sell | buy")) continue;
                if (line.matches("(?i)^(?:n/?a|free|.* coins)$")) continue;
                if (line.contains("|") && line.matches(".*\\|.*")) continue;
                rewards.add(parseQuantity(line));
            }
            double chestCost = parseChestCost(tooltip);
            boolean requiresKey = tooltip.stream().map(DungeonChestCalculator::normalize).anyMatch(line -> line.equalsIgnoreCase("dungeon chest key"));
            boolean serverBlocked = tooltip.stream().map(DungeonChestCalculator::normalize).anyMatch(line -> line.equalsIgnoreCase("you can't open more chests!") || line.equalsIgnoreCase("no more chests to open!"));
            BazaarPrice keyPrice = requiresKey ? database.findByName("Dungeon Chest Key") : null;
            DungeonChestParser.ChestOffer offer = buildOffer(chestName, opened, rewards, chestCost, requiresKey, keyPrice, !serverBlocked, database);
            parsed.add(new ParsedSelectionSlot(slot.slotId(), offer));
        }
        List<DungeonChestParser.ChestOffer> offers = new ArrayList<>();
        for (ParsedSelectionSlot parsedSlot : parsed) {
            DungeonChestParser.ChestOffer offer = parsedSlot.offer();
            if (openedCount >= 2 && !offer.opened()) offer = withAvailable(offer, false);
            LAST_SELECTION.put(offer.chestName().toLowerCase(Locale.ROOT), offer);
            if (!offer.opened()) offers.add(offer);
        }
        offers.sort(Comparator.comparingInt(DungeonChestCalculator::chestOrder));
        List<DungeonChestParser.ChestOffer> availableOffers = new ArrayList<>();
        int availableCount = 0;
        for (DungeonChestParser.ChestOffer offer : offers) {
            if (offer.available()) availableCount++;
            if (offer.available() && Double.isFinite(offer.netProfit())) availableOffers.add(offer);
        }
        availableOffers.sort(Comparator.comparingDouble(DungeonChestParser.ChestOffer::netProfit).reversed());
        java.util.HashMap<Integer, Highlight> highlights = new java.util.HashMap<>();
        if (availableCount > 0) {
            for (ParsedSelectionSlot parsedSlot : parsed) {
                if (parsedSlot.offer().opened()) continue;
                DungeonChestParser.ChestOffer offer = findOffer(offers, parsedSlot.offer().chestName());
                if (offer == null) continue;
                if (!offer.available()) { highlights.put(parsedSlot.slotId(), new Highlight(UNAVAILABLE_SLOT_BG, UNAVAILABLE_SLOT_BORDER)); continue; }
                int rank = availableOffers.indexOf(offer);
                if (rank < 0) continue;
                double rankPercent = availableOffers.size() <= 1 ? 1.0 : (double) (availableOffers.size() - 1 - rank) / (availableOffers.size() - 1);
                int color = profitRankColor(rankPercent);
                highlights.put(parsedSlot.slotId(), new Highlight(withAlpha(color, 0x70), color));
            }
        }
        if (availableCount == 0 || offers.isEmpty()) {
            return new CalculationResult(DungeonChestScreenType.SELECTION, 48, "Dungeon Chest Profit", List.of(new CalculationLine("NO MORE AVAILABLE CHESTS", RED, null, 0, 25)), Map.of());
        }
        List<CalculationLine> lines = new ArrayList<>();
        int y = 20;
        for (DungeonChestParser.ChestOffer offer : offers) {
            String chest = capitalize(offer.chestName());
            String status = offer.available() ? "AVAILABLE" : "UNAVAILABLE";
            int statusColor = offer.available() ? GREEN : RED;
            lines.add(new CalculationLine(chest, chestNameColor(chest), status, statusColor, y));
            y += 11;
            String costs = "-" + BazaarTooltip.formatCoins(offer.chestCost()) + " [c]";
            if (offer.requiresKey()) costs += offer.keyPriceMissing() ? " -N/A [k]" : " -" + BazaarTooltip.formatCoins(offer.keyCost()) + " [k]";
            lines.add(new CalculationLine(trimWithoutFont("Value " + BazaarTooltip.formatCoins(offer.rewardValue()) + "  " + costs), GRAY, null, 0, y));
            y += 11;
            String profit = "Profit " + signedCoins(offer.netProfit());
            if (!offer.missingPrices().isEmpty()) profit += "  Missing: " + offer.missingPrices().size();
            lines.add(new CalculationLine(trimWithoutFont(profit), offer.netProfit() >= 0 ? GREEN : RED, null, 0, y));
            y += 22;
        }
        return new CalculationResult(DungeonChestScreenType.SELECTION, 24 + offers.size() * 44, "Dungeon Chest Profit", List.copyOf(lines), Map.copyOf(highlights));
    }

    private static CalculationResult calculateReward(RawScreenData raw, BazaarPriceDatabase database) throws SQLException {
        String chestName = chestTierName(raw.title());
        List<RawSlotData> rewardSlots = new ArrayList<>();
        boolean started = false;
        for (RawSlotData slot : raw.slots()) {
            String itemName = normalize(slot.itemName());
            if (itemName.equalsIgnoreCase("Open Reward Chest")) { started = true; continue; }
            if (started || itemName.equalsIgnoreCase("Go Back") || itemName.equalsIgnoreCase("Close")) continue;
            if (itemName.isEmpty()) continue;
            rewardSlots.add(slot);
        }
        List<DungeonChestParser.Reward> rewards = new ArrayList<>(rewardSlots.size());
        for (RawSlotData slot : rewardSlots) rewards.add(rewardFromSlot(slot));
        DungeonChestParser.ChestOffer selected = LAST_SELECTION.get(chestName.toLowerCase(Locale.ROOT));
        DungeonChestParser.ChestOffer offer;
        if (selected != null) {
            BazaarPrice keyPrice = selected.requiresKey() ? database.findByName("Dungeon Chest Key") : null;
            offer = buildOffer(chestName, true, rewards, selected.chestCost(), selected.requiresKey(), keyPrice, false, database);
        } else offer = buildOffer(chestName, true, rewards, 0.0, false, null, false, database);
        double maxRewardValue = Double.NEGATIVE_INFINITY;
        for (DungeonChestParser.ValuedReward reward : offer.rewards()) if (isPricedReward(reward)) maxRewardValue = Math.max(maxRewardValue, reward.totalValue());
        java.util.HashMap<Integer, Highlight> highlights = new java.util.HashMap<>();
        for (int i = 0; i < rewardSlots.size() && i < offer.rewards().size(); i++) {
            DungeonChestParser.ValuedReward reward = offer.rewards().get(i);
            if (Double.isFinite(maxRewardValue) && isPricedReward(reward) && Double.compare(reward.totalValue(), maxRewardValue) == 0) highlights.put(rewardSlots.get(i).slotId(), new Highlight(withAlpha(GREEN, 0x70), GREEN));
        }
        int rows = Math.min(offer.rewards().size(), 7);
        int panelHeight = 54 + rows * 18 + (offer.missingPrices().isEmpty() ? 0 : 20);
        List<CalculationLine> lines = new ArrayList<>();
        lines.add(new CalculationLine("Rewards: " + BazaarTooltip.formatCoins(offer.rewardValue()), WHITE, null, 0, 20));
        lines.add(new CalculationLine("Net: " + signedCoins(offer.netProfit()), offer.netProfit() >= 0 ? GREEN : RED, null, 0, 33));
        int y = 49;
        for (int i = 0; i < rows; i++) {
            DungeonChestParser.ValuedReward reward = offer.rewards().get(i);
            String value = reward.knownInBazaar() ? BazaarTooltip.formatCoins(reward.totalValue()) : "N/A";
            lines.add(new CalculationLine(trimWithoutFont(reward.itemName() + " x" + reward.quantity() + ": " + value), reward.knownInBazaar() ? GRAY : RED, null, 0, y));
            y += 18;
        }
        if (offer.rewards().size() > rows) { lines.add(new CalculationLine("+" + (offer.rewards().size() - rows) + " more", GRAY, null, 0, y)); y += 13; }
        if (!offer.missingPrices().isEmpty()) lines.add(new CalculationLine(trimWithoutFont("No price: " + String.join(", ", offer.missingPrices())), YELLOW, null, 0, y));
        return new CalculationResult(DungeonChestScreenType.REWARD, panelHeight, capitalize(chestName) + " Value", List.copyOf(lines), Map.copyOf(highlights));
    }

    private record ParsedSelectionSlot(int slotId, DungeonChestParser.ChestOffer offer) {}

    private static DungeonChestParser.Reward rewardFromSlot(RawSlotData slot) {
        DungeonChestParser.Reward parsed = parseQuantity(slot.itemName());
        if (parsed.quantity() != 1 || slot.count() == 1) return parsed;
        return new DungeonChestParser.Reward(parsed.itemName(), slot.count());
    }

    private static DungeonChestParser.ChestOffer buildOffer(String chestName, boolean opened, List<DungeonChestParser.Reward> rewards, double chestCost, boolean requiresKey, BazaarPrice keyPrice, boolean available, BazaarPriceDatabase database) throws SQLException {
        double rewardValue = 0.0;
        List<String> missing = new ArrayList<>();
        List<DungeonChestParser.ValuedReward> valuedRewards = new ArrayList<>();
        for (DungeonChestParser.Reward reward : rewards) {
            BazaarPrice price = database.findByName(reward.itemName());
            boolean known = price != null;
            Double unitSell = known ? positive(price.sellPrice()) : null;
            double total = unitSell == null ? 0.0 : unitSell * reward.quantity();
            if (unitSell != null) rewardValue += total; else if (!known) missing.add(reward.itemName() + " x" + reward.quantity());
            valuedRewards.add(new DungeonChestParser.ValuedReward(reward.itemName(), reward.quantity(), unitSell, total, known));
        }
        double keyCost = 0.0;
        boolean keyMissing = false;
        if (requiresKey) {
            Double keyBuy = keyPrice == null ? null : positive(keyPrice.buyPrice());
            if (keyBuy == null) keyMissing = true; else keyCost = keyBuy;
        }
        return new DungeonChestParser.ChestOffer(chestName, opened, valuedRewards, rewardValue, chestCost, requiresKey, keyCost, keyMissing, rewardValue - chestCost - keyCost, missing, available);
    }

    private static DungeonChestParser.ChestOffer withAvailable(DungeonChestParser.ChestOffer offer, boolean available) {
        return new DungeonChestParser.ChestOffer(offer.chestName(), offer.opened(), offer.rewards(), offer.rewardValue(), offer.chestCost(), offer.requiresKey(), offer.keyCost(), offer.keyPriceMissing(), offer.netProfit(), offer.missingPrices(), available);
    }

    private static DungeonChestParser.ChestOffer findOffer(List<DungeonChestParser.ChestOffer> offers, String name) {
        for (DungeonChestParser.ChestOffer offer : offers) if (offer.chestName().equalsIgnoreCase(name)) return offer;
        return null;
    }

    private static DungeonChestParser.Reward parseQuantity(String value) {
        var matcher = QUANTITY_SUFFIX.matcher(value);
        if (matcher.matches()) return new DungeonChestParser.Reward(matcher.group(1).trim(), Integer.parseInt(matcher.group(2)));
        return new DungeonChestParser.Reward(value.trim(), 1);
    }

    private static int indexOf(List<String> lines, String expected) {
        for (int i = 0; i < lines.size(); i++) if (normalize(lines.get(i)).equalsIgnoreCase(expected)) return i;
        return -1;
    }

    private static double parseChestCost(List<String> tooltip) {
        for (int i = 0; i + 1 < tooltip.size(); i++) {
            if (!normalize(tooltip.get(i)).equalsIgnoreCase("cost")) continue;
            String value = normalize(tooltip.get(i + 1));
            if (value.equalsIgnoreCase("free")) return 0.0;
            if (value.toLowerCase(Locale.ROOT).endsWith(" coins")) {
                Double parsed = BazaarParser.parseCoins(value.substring(0, value.length() - 6));
                return parsed == null ? 0.0 : parsed;
            }
        }
        return 0.0;
    }

    private static boolean isPricedReward(DungeonChestParser.ValuedReward reward) {
        return reward.knownInBazaar() && Double.isFinite(reward.totalValue());
    }

    private static int chestOrder(DungeonChestParser.ChestOffer offer) { return chestOrder(offer.chestName()); }

    private static int chestOrder(String name) {
        int index = CHEST_NAMES.indexOf(capitalize(name));
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private static String chestTierName(String name) {
        String normalized = normalize(name).toLowerCase(Locale.ROOT);
        for (String chestName : CHEST_NAMES) if (normalized.contains(chestName.toLowerCase(Locale.ROOT))) return chestName;
        return "Wood";
    }

    private static String signedCoins(double value) {
        if (!Double.isFinite(value)) return "N/A";
        return (value >= 0 ? "+" : "") + BazaarTooltip.formatCoins(value);
    }

    private static int chestNameColor(String chestName) {
        return switch (chestName.toLowerCase(Locale.ROOT)) {
            case "wood" -> 0xFFB8865B;
            case "gold" -> 0xFFFFD83D;
            case "diamond" -> 0xFF55FFFF;
            case "emerald" -> 0xFF55FF55;
            case "obsidian" -> 0xFFAA55FF;
            case "bedrock" -> 0xFF777777;
            default -> WHITE;
        };
    }

    private static int profitRankColor(double percent) { return percent >= 0.5 ? GREEN : RED; }

    private static int withAlpha(int color, int alpha) { return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24); }

    private static Double positive(Double value) { return value != null && value > 0 ? value : null; }

    private static String normalize(String value) { return value == null ? "" : value.replaceAll("\\s+", " ").trim(); }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) return "";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String trimWithoutFont(String value) { return value == null ? "" : value.trim(); }
}
