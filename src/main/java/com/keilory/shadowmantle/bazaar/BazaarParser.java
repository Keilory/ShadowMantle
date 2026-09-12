package com.keilory.shadowmantle.bazaar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BazaarParser {
    private static final String PRICE = "([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmb]?)";
    private static final Pattern BUY = Pattern.compile("(?i).*?\\bbuy(?:\\s+price)?\\s*[:\\-]?\\s*" + PRICE + "\\s*(?:coins?)?.*");
    private static final Pattern SELL = Pattern.compile("(?i).*?\\bsell(?:\\s+price)?\\s*[:\\-]?\\s*" + PRICE + "\\s*(?:coins?)?.*");
    private static final Pattern BUY_INSTANTLY = Pattern.compile("(?i)^buy\\s+instantly$");
    private static final Pattern SELL_INSTANTLY = Pattern.compile("(?i)^sell\\s+instantly$");
    private static final Pattern PRICE_PER_UNIT = Pattern.compile("(?i)^price\\s+per\\s+unit\\s*[:\\-]?\\s*" + PRICE + "\\s*(?:coins?)?\\s*$");
    private static final Pattern PRICE_PER_UNIT_UNAVAILABLE = Pattern.compile("(?i)^price\\s+per\\s+unit\\s*[:\\-]?\\s*(?:n/?a|not\\s+available)\\s*$");
    private static final Pattern PRODUCT = Pattern.compile("^(?:>|➤|►|▶|▸|▹|»)?\\s*(.+?)\\s+" + PRICE + "\\s*\\|\\s*" + PRICE + "(?:\\s+coins?)?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private BazaarParser() {}

    /** Pure worker-thread parser. All Minecraft API access must happen before this method. */
    public static List<BazaarPrice> parseScreen(List<BazaarSlotSnapshot> slots) {
        List<BazaarPrice> prices = new ArrayList<>();
        Map<String, InstantPrice> instantPrices = new LinkedHashMap<>();
        for (BazaarSlotSnapshot slot : slots) {
            if (slot == null || slot.tooltipLines().isEmpty()) continue;
            List<String> lines = slot.tooltipLines();
            prices.addAll(parseRegularSlot(slot.itemName(), lines));
            collectInstantPrice(lines, instantPrices);
        }
        for (InstantPrice instant : instantPrices.values()) {
            if ((instant.buy == null && instant.sell == null) || instant.itemName.isBlank()) continue;
            String itemId = "name:" + BazaarPriceDatabase.normalize(instant.itemName);
            prices.add(new BazaarPrice(itemId, instant.itemName, instant.buy, instant.sell,
                    System.currentTimeMillis(), BazaarPriceSource.BAZAAR));
        }
        return prices;
    }

    private static List<BazaarPrice> parseRegularSlot(String stackItemName, List<String> lines) {
        List<BazaarPrice> prices = new ArrayList<>();
        Double buy = null;
        Double sell = null;
        InstantPriceSection instantSection = InstantPriceSection.NONE;
        boolean hasInstantSection = false;
        for (String line : lines) {
            String normalized = normalizeLine(line);
            if (BUY_INSTANTLY.matcher(normalized).matches()) { instantSection = InstantPriceSection.BUY; hasInstantSection = true; continue; }
            if (SELL_INSTANTLY.matcher(normalized).matches()) { instantSection = InstantPriceSection.SELL; hasInstantSection = true; continue; }
            Matcher pricePerUnitMatcher = PRICE_PER_UNIT.matcher(normalized);
            if (pricePerUnitMatcher.matches()) {
                Double price = parseCoins(pricePerUnitMatcher.group(1));
                if (price != null) {
                    if (instantSection == InstantPriceSection.BUY) buy = price;
                    else if (instantSection == InstantPriceSection.SELL) sell = price;
                }
                continue;
            }
            if (PRICE_PER_UNIT_UNAVAILABLE.matcher(normalized).matches()) continue;
            Matcher buyMatcher = BUY.matcher(normalized);
            if (buyMatcher.matches()) { buy = parseCoins(buyMatcher.group(1)); continue; }
            Matcher sellMatcher = SELL.matcher(normalized);
            if (sellMatcher.matches()) { sell = parseCoins(sellMatcher.group(1)); continue; }
            Matcher productMatcher = PRODUCT.matcher(normalized);
            if (productMatcher.matches()) {
                String name = cleanProductName(productMatcher.group(1));
                Double sellPrice = parseCoins(productMatcher.group(2));
                Double buyPrice = parseCoins(productMatcher.group(3));
                if (!name.isBlank() && sellPrice != null && buyPrice != null) {
                    BazaarPrice price = new BazaarPrice("name:" + BazaarPriceDatabase.normalize(name), name,
                            buyPrice, sellPrice, System.currentTimeMillis(), BazaarPriceSource.BAZAAR);
                    prices.add(price);
                }
            }
        }
        if (!hasInstantSection && (buy != null || sell != null)) {
            String itemName = tooltipItemName(stackItemName, lines);
            prices.add(new BazaarPrice("name:" + BazaarPriceDatabase.normalize(itemName), itemName,
                    buy, sell, System.currentTimeMillis(), BazaarPriceSource.BAZAAR));
        }
        return prices;
    }

    private static void collectInstantPrice(List<String> lines, Map<String, InstantPrice> prices) {
        InstantPriceSection section = InstantPriceSection.NONE;
        String itemName = null;
        for (String line : lines) {
            String normalized = normalizeLine(line);
            if (BUY_INSTANTLY.matcher(normalized).matches()) { section = InstantPriceSection.BUY; itemName = null; continue; }
            if (SELL_INSTANTLY.matcher(normalized).matches()) { section = InstantPriceSection.SELL; itemName = null; continue; }
            if (section == InstantPriceSection.NONE) continue;
            Matcher priceMatcher = PRICE_PER_UNIT.matcher(normalized);
            if (priceMatcher.matches()) {
                Double price = parseCoins(priceMatcher.group(1));
                if (price != null && itemName != null && !itemName.isBlank()) {
                    String key = BazaarPriceDatabase.normalize(itemName);
                    String stableItemName = itemName;
                    InstantPrice instant = prices.computeIfAbsent(key, ignored -> new InstantPrice(stableItemName));
                    if (section == InstantPriceSection.BUY) instant.buy = price; else instant.sell = price;
                }
                continue;
            }
            if (PRICE_PER_UNIT_UNAVAILABLE.matcher(normalized).matches()) {
                if (itemName != null && !itemName.isBlank()) {
                    String key = BazaarPriceDatabase.normalize(itemName);
                    String stableItemName = itemName;
                    prices.computeIfAbsent(key, ignored -> new InstantPrice(stableItemName));
                }
                continue;
            }
            if (itemName == null && !normalized.isBlank()) itemName = normalized;
        }
    }

    private static String cleanProductName(String value) { return value.trim(); }
    private static String tooltipItemName(String stackItemName, List<String> lines) { return !lines.isEmpty() && !lines.get(0).isBlank() ? cleanProductName(lines.get(0)) : stackItemName; }
    private static String normalizeLine(String line) { return WHITESPACE.matcher(line.replace('\u00A0', ' ')).replaceAll(" ").trim(); }

    public static Double parseCoins(String raw) {
        String token = raw.replace(",", "").replace(" ", "").trim();
        if (token.isEmpty() || token.equalsIgnoreCase("n/a")) return null;
        char suffix = Character.toLowerCase(token.charAt(token.length() - 1));
        double multiplier = switch (suffix) { case 'k' -> 1_000.0; case 'm' -> 1_000_000.0; case 'b' -> 1_000_000_000.0; default -> 1.0; };
        if (multiplier != 1.0) token = token.substring(0, token.length() - 1);
        try { return Double.parseDouble(token) * multiplier; } catch (NumberFormatException ignored) { return null; }
    }

    private enum InstantPriceSection { NONE, BUY, SELL }
    private static final class InstantPrice {
        private final String itemName;
        private Double buy;
        private Double sell;
        private InstantPrice(String itemName) { this.itemName = itemName; }
    }
}
