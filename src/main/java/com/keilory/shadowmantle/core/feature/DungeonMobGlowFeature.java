package com.keilory.shadowmantle.core.feature;

import com.keilory.shadowmantle.core.EventBus;
import com.keilory.shadowmantle.core.Feature;
import com.keilory.shadowmantle.core.FeatureIds;
import com.keilory.shadowmantle.core.ShadowMantleCore;
import com.keilory.shadowmantle.core.event.ClientTickEvent;

public final class DungeonMobGlowFeature implements Feature {
    private EventBus.Subscription tickSubscription;

    @Override
    public String id() {
        return FeatureIds.DUNGEON_MOB_GLOW;
    }

    @Override
    public void initialize(ShadowMantleCore core) {
        tickSubscription = core.events().subscribe(
                ClientTickEvent.class,
                event -> DungeonMobGlow.update(event.client())
        );
    }

    @Override
    public void shutdown() {
        if (tickSubscription != null) {
            tickSubscription.unsubscribe();
            tickSubscription = null;
        }
        DungeonMobGlow.update(null);
    }
}
