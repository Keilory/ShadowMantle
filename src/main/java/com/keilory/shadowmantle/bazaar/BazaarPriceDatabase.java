package com.keilory.shadowmantle.bazaar;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class BazaarPriceDatabase implements AutoCloseable {
    private static final int SCHEMA_VERSION = 3;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern STACK_COUNT_SUFFIX = Pattern.compile("(?i)\\s+x\\d+$");
    private final Connection connection;
    private final Map<String, BazaarPrice> pricesByName = new HashMap<>();
    private final Map<String, BazaarPrice> pricesById = new HashMap<>();
    private boolean priceCacheLoaded;
    private BazaarPriceDatabase(Connection connection) { this.connection = connection; }
    public static BazaarPriceDatabase open(MinecraftClient client) throws SQLException {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("shadowmantle");
        try { Files.createDirectories(configDir); } catch (IOException e) { throw new SQLException("Failed to create ShadowMantle config directory: " + configDir, e); }
        Path legacyPath = FabricLoader.getInstance().getConfigDir().resolve("bazaar_prices.db");
        Path databasePath = configDir.resolve("bazaar_prices.db").toAbsolutePath();
        migrateLegacyDatabase(legacyPath, databasePath);
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        BazaarPriceDatabase database = new BazaarPriceDatabase(connection); database.initializeSchema(); return database;
    }
    private static void migrateLegacyDatabase(Path legacyPath, Path databasePath) throws SQLException {
        if (Files.exists(databasePath) || !Files.exists(legacyPath)) return;
        try {
            Files.move(legacyPath, databasePath);
        } catch (IOException e) {
            throw new SQLException("Failed to migrate Bazaar database to: " + databasePath, e);
        }
    }
    private void initializeSchema() throws SQLException { if (!isCurrentSchema()) resetDatabase(); createSchema(); }
    private boolean isCurrentSchema() throws SQLException {
        if (!tableExists("bazaar_prices")) return false;
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("PRAGMA user_version")) { if (!rs.next() || rs.getInt(1) != SCHEMA_VERSION) return false; }
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("PRAGMA table_info(bazaar_prices)")) {
            boolean itemId = false, itemName = false, buyPrice = false, sellPrice = false, updatedAt = false, source = false;
            while (rs.next()) switch (rs.getString("name")) { case "item_id" -> itemId = true; case "item_name" -> itemName = true; case "buy_price" -> buyPrice = true; case "sell_price" -> sellPrice = true; case "updated_at" -> updatedAt = true; case "source" -> source = true; default -> { } }
            return itemId && itemName && buyPrice && sellPrice && updatedAt && source;
        }
    }
    private boolean tableExists(String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1")) { statement.setString(1, tableName); try (ResultSet rs = statement.executeQuery()) { return rs.next(); } }
    }
    private void resetDatabase() throws SQLException {
        try (Statement statement = connection.createStatement()) { statement.executeUpdate("DROP TABLE IF EXISTS bazaar_prices"); statement.executeUpdate("PRAGMA user_version = " + SCHEMA_VERSION); }
        priceCacheLoaded = false; pricesByName.clear(); pricesById.clear();
    }
    private void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS bazaar_prices (item_id TEXT PRIMARY KEY, item_name TEXT, buy_price REAL, sell_price REAL, updated_at INTEGER, source TEXT NOT NULL DEFAULT 'UNKNOWN')");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bazaar_prices_name ON bazaar_prices(item_name)");
            statement.executeUpdate("PRAGMA user_version = " + SCHEMA_VERSION);
        }
    }
    public synchronized void save(String itemId, String itemName, double buyPrice, double sellPrice) throws SQLException { saveBatch(List.of(new BazaarPrice(itemId, itemName, buyPrice, sellPrice, System.currentTimeMillis(), BazaarPriceSource.UNKNOWN))); }
    public synchronized void saveBatch(List<BazaarPrice> prices) throws SQLException {
        if (prices.isEmpty()) return;
        boolean previousAutoCommit = connection.getAutoCommit(); connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO bazaar_prices(item_id, item_name, buy_price, sell_price, updated_at, source) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(item_id) DO UPDATE SET item_name = excluded.item_name, buy_price = excluded.buy_price, sell_price = excluded.sell_price, updated_at = excluded.updated_at, source = excluded.source")) {
            long now = System.currentTimeMillis();
            for (BazaarPrice price : prices) {
                statement.setString(1, price.itemId()); statement.setString(2, price.itemName());
                if (price.buyPrice() == null) statement.setNull(3, java.sql.Types.REAL); else statement.setDouble(3, price.buyPrice());
                if (price.sellPrice() == null) statement.setNull(4, java.sql.Types.REAL); else statement.setDouble(4, price.sellPrice());
                statement.setLong(5, now); statement.setString(6, price.source().name()); statement.addBatch();
            }
            statement.executeBatch(); connection.commit(); priceCacheLoaded = false; pricesByName.clear(); pricesById.clear();
        } catch (SQLException e) { connection.rollback(); throw e; } finally { connection.setAutoCommit(previousAutoCommit); }
    }
    public synchronized BazaarPrice find(ItemStack stack) throws SQLException {
        ensurePriceCacheLoaded(); BazaarPrice byName = pricesByName.get(normalize(stack.getName().getString())); if (byName != null) return byName;
        String itemId = Registries.ITEM.getId(stack.getItem()).toString(); return pricesById.get(itemId);
    }
    public synchronized BazaarPrice findByName(String itemName) throws SQLException { String normalizedName = normalize(itemName); if (normalizedName.isEmpty()) return null; ensurePriceCacheLoaded(); return pricesByName.get(normalizedName); }
    public synchronized BazaarPrice findDirectByName(String itemName) throws SQLException { BazaarPrice price = findByName(itemName); return price != null && price.isDirectBazaarPrice() ? price : null; }
    private void ensurePriceCacheLoaded() throws SQLException {
        if (priceCacheLoaded) return; pricesByName.clear(); pricesById.clear();
        try (PreparedStatement statement = connection.prepareStatement("SELECT item_id, item_name, buy_price, sell_price, updated_at, source FROM bazaar_prices"); ResultSet rs = statement.executeQuery()) {
            while (rs.next()) { BazaarPrice price = read(rs); pricesById.put(price.itemId(), price); String normalizedName = normalize(price.itemName()); if (!normalizedName.isEmpty()) pricesByName.put(normalizedName, price); }
        }
        priceCacheLoaded = true;
    }
    private BazaarPrice read(ResultSet rs) throws SQLException {
        double buy = rs.getDouble("buy_price"); Double buyPrice = rs.wasNull() ? null : buy; double sell = rs.getDouble("sell_price"); Double sellPrice = rs.wasNull() ? null : sell;
        BazaarPriceSource source; try { source = BazaarPriceSource.valueOf(rs.getString("source")); } catch (IllegalArgumentException | NullPointerException ignored) { source = BazaarPriceSource.UNKNOWN; }
        return new BazaarPrice(rs.getString("item_id"), rs.getString("item_name"), buyPrice, sellPrice, rs.getLong("updated_at"), source);
    }
    public static String normalize(String value) { String normalized = WHITESPACE.matcher(value.replace('\u00A0', ' ')).replaceAll(" ").trim(); return STACK_COUNT_SUFFIX.matcher(normalized).replaceFirst("").trim().toLowerCase(Locale.ROOT); }
    @Override public synchronized void close() throws SQLException { connection.close(); }
}
