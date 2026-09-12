package com.keilory.shadowmantle;

import com.keilory.shadowmantle.bazaar.BazaarPriceDatabase;
import com.keilory.shadowmantle.core.ShadowMantleCore;
import com.keilory.shadowmantle.core.feature.BazaarFeature;
import com.keilory.shadowmantle.core.feature.DungeonFeature;
import com.keilory.shadowmantle.core.feature.DungeonMobGlowFeature;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

public final class ShadowMantleClient implements ClientModInitializer {
    public static final String MOD_ID = "shadowmantle";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static ShadowMantleCore core;
    private static BazaarPriceDatabase database;

    public static ShadowMantleCore core() {
        if (core == null) throw new IllegalStateException();
        return core;
    }

    @Override
    public void onInitializeClient() {
        MinecraftClient client = MinecraftClient.getInstance();
        core = ShadowMantleCore.initialize(client);

        try {
            database = BazaarPriceDatabase.open(client);
            core.registerFeature(new BazaarFeature(database));
            core.registerFeature(new DungeonFeature(database));
            core.registerFeature(new DungeonMobGlowFeature());
            LOGGER.info("ShadowMantle initialized");
        } catch (SQLException error) {
            LOGGER.error("Failed to initialize database", error);
        }
    }
}
