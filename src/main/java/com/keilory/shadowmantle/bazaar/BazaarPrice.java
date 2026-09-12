package com.keilory.shadowmantle.bazaar;

public record BazaarPrice(
        String itemId,
        String itemName,
        Double buyPrice,
        Double sellPrice,
        long updatedAt,
        BazaarPriceSource source
) {
    public BazaarPrice(String itemId, String itemName, Double buyPrice, Double sellPrice, long updatedAt) {
        this(itemId, itemName, buyPrice, sellPrice, updatedAt, BazaarPriceSource.UNKNOWN);
    }

    public boolean isDirectBazaarPrice() {
        return source == BazaarPriceSource.BAZAAR;
    }
}
