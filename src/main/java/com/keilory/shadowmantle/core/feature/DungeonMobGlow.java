package com.keilory.shadowmantle.core.feature;

import com.keilory.shadowmantle.mixin.PlayerListHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves Hypixel-like dungeon nameplate armor stands to their real living entity.
 *
 * A marker armor stand is positioned directly above its real entity. Once that
 * relationship is proven, the pair receives a persistent internal Glow_Id.
 * Only starred nameplates are eligible for a glow pair.
 */
public final class DungeonMobGlow {
    private static final double ARMOR_STAND_HORIZONTAL_RADIUS = 32.0;
    private static final double ARMOR_STAND_VERTICAL_RADIUS = 16.0;
    private static final double XZ_MATCH_TOLERANCE = 0.05;
    private static final double MIN_VERTICAL_OFFSET = 0.5;
    private static final double MAX_VERTICAL_OFFSET = 3.0;
    private static final String DUNGEON_AREA_MARKER = "Area: Dungeon";
    private static final String STAR_MARKER = "✮";

    /** ArmorStand UUID -> persistent Glow_Id. */
    private static final Map<UUID, Long> STAND_GLOW_IDS = new HashMap<>();
    /** LivingEntity UUID -> persistent Glow_Id. */
    private static final Map<UUID, Long> MOB_GLOW_IDS = new HashMap<>();
    /** Glow_Id -> resolved living entity UUID. */
    private static final Map<Long, UUID> GLOW_ID_TO_MOB = new HashMap<>();
    /** Glow_Id -> resolved armor stand UUID. */
    private static final Map<Long, UUID> GLOW_ID_TO_STAND = new HashMap<>();

    private static long nextGlowId = 1L;
    private static RegistryKey<World> resolvedWorld;
    private static boolean dungeonAreaActive;

    private DungeonMobGlow() {
    }

    /**
     * Resolves starred marker stands only while the tab contains "Area: Dungeon".
     * Once a stand/entity pair is found, that pair is frozen and is never
     * replaced by a different nearby entity during subsequent client ticks.
     */
    public static void update(MinecraftClient client) {
        if (client == null || client.world == null || client.player == null) {
            clear();
            dungeonAreaActive = false;
            return;
        }

        RegistryKey<World> world = client.world.getRegistryKey();
        if (!world.equals(resolvedWorld)) {
            clear();
            resolvedWorld = world;
        }

        boolean inDungeon = isDungeonArea(client);
        if (!inDungeon) {
            if (dungeonAreaActive) clear();
            dungeonAreaActive = false;
            return;
        }
        dungeonAreaActive = true;

        List<ArmorStandEntity> stands = client.world.getEntitiesByClass(
                ArmorStandEntity.class,
                client.player.getBoundingBox().expand(ARMOR_STAND_HORIZONTAL_RADIUS, ARMOR_STAND_VERTICAL_RADIUS, ARMOR_STAND_HORIZONTAL_RADIUS),
                stand -> !stand.isRemoved()
                        && stand.isMarker()
                        && stand.getCustomName() != null
                        && containsStarMarker(stand.getCustomName())
        );

        List<LivingEntity> entities = client.world.getEntitiesByClass(
                LivingEntity.class,
                client.player.getBoundingBox().expand(ARMOR_STAND_HORIZONTAL_RADIUS, ARMOR_STAND_VERTICAL_RADIUS, ARMOR_STAND_HORIZONTAL_RADIUS),
                entity -> !entity.isRemoved()
                        && !(entity instanceof ArmorStandEntity)
                        && (entity instanceof HostileEntity || entity instanceof PlayerEntity)
        );

        Set<UUID> claimedEntities = new HashSet<>(MOB_GLOW_IDS.keySet());
        Set<UUID> claimedStands = new HashSet<>(STAND_GLOW_IDS.keySet());

        for (ArmorStandEntity stand : stands) {
            UUID standUuid = stand.getUuid();
            if (claimedStands.contains(standUuid)) continue;

            LivingEntity best = null;
            double bestVerticalDistance = Double.MAX_VALUE;

            for (LivingEntity entity : entities) {
                if (claimedEntities.contains(entity.getUuid())) continue;
                if (!isValidMatch(stand, entity)) continue;

                double verticalOffset = stand.getY() - entity.getY();
                if (verticalOffset < bestVerticalDistance) {
                    bestVerticalDistance = verticalOffset;
                    best = entity;
                }
            }

            if (best != null) {
                createGlowPair(stand, best);
                claimedStands.add(standUuid);
                claimedEntities.add(best.getUuid());
            }
        }
    }

    private static boolean isDungeonArea(MinecraftClient client) {
        PlayerListHud playerListHud = client.inGameHud.getPlayerListHud();
        if (!(playerListHud instanceof PlayerListHudAccessor accessor)) return false;

        String header = textString(accessor.fakePixel$getHeader());
        String footer = textString(accessor.fakePixel$getFooter());
        if (containsDungeonMarker(header) || containsDungeonMarker(footer)) return true;

        List<PlayerListEntry> entries = accessor.fakePixel$collectPlayerEntries();
        for (PlayerListEntry entry : entries) {
            String renderedName = textString(playerListHud.getPlayerName(entry));
            String displayName = textString(entry.getDisplayName());
            if (containsDungeonMarker(renderedName) || containsDungeonMarker(displayName)) return true;
        }

        return false;
    }

    private static boolean containsDungeonMarker(String value) {
        return value.replaceAll("§.", "").contains(DUNGEON_AREA_MARKER);
    }

    private static boolean containsStarMarker(Text text) {
        return containsStarMarker(textString(text));
    }

    private static boolean containsStarMarker(String value) {
        return value.replaceAll("§.", "").contains(STAR_MARKER);
    }

    private static String textString(Text text) {
        return text == null ? "" : text.getString();
    }

    private static boolean isValidMatch(ArmorStandEntity stand, LivingEntity entity) {
        double dx = Math.abs(entity.getX() - stand.getX());
        double dz = Math.abs(entity.getZ() - stand.getZ());
        double verticalOffset = stand.getY() - entity.getY();

        return dx <= XZ_MATCH_TOLERANCE
                && dz <= XZ_MATCH_TOLERANCE
                && verticalOffset >= MIN_VERTICAL_OFFSET
                && verticalOffset <= MAX_VERTICAL_OFFSET;
    }

    private static void createGlowPair(ArmorStandEntity stand, LivingEntity entity) {
        long glowId = nextGlowId++;
        UUID standUuid = stand.getUuid();
        UUID entityUuid = entity.getUuid();

        STAND_GLOW_IDS.put(standUuid, glowId);
        MOB_GLOW_IDS.put(entityUuid, glowId);
        GLOW_ID_TO_STAND.put(glowId, standUuid);
        GLOW_ID_TO_MOB.put(glowId, entityUuid);
    }

    private static void clear() {
        STAND_GLOW_IDS.clear();
        MOB_GLOW_IDS.clear();
        GLOW_ID_TO_MOB.clear();
        GLOW_ID_TO_STAND.clear();
    }

    public static boolean shouldGlow(LivingEntity entity) {
        return dungeonAreaActive
                && entity != null
                && !(entity instanceof ArmorStandEntity)
                && MOB_GLOW_IDS.containsKey(entity.getUuid());
    }

    public static long getGlowId(LivingEntity entity) {
        if (entity == null) return -1L;
        return MOB_GLOW_IDS.getOrDefault(entity.getUuid(), -1L);
    }

    public static int getGlowColor(LivingEntity entity) {
        if (entity.hasCustomName() && entity.getCustomName() != null) {
            String name = entity.getCustomName().getString();

            if (name.contains("Shadow Assassin") || name.contains("Lost Adventurer")) {
                return 0xFF5555;
            }

            if (name.contains("Key Guardian") || name.contains(STAR_MARKER)) {
                return 0xFFAA00;
            }
        }

        return 0x55FF55;
    }
}
