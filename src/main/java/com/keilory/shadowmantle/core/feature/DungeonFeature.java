package com.keilory.shadowmantle.core.feature;

import com.keilory.shadowmantle.bazaar.BazaarPriceDatabase;
import com.keilory.shadowmantle.bazaar.DungeonChestOverlay;
import com.keilory.shadowmantle.core.EventBus;
import com.keilory.shadowmantle.core.ShadowMantleCore;
import com.keilory.shadowmantle.core.Feature;
import com.keilory.shadowmantle.core.FeatureIds;
import com.keilory.shadowmantle.core.event.ScreenClosedEvent;
import com.keilory.shadowmantle.core.event.ScreenOpenedEvent;
import com.keilory.shadowmantle.core.event.SlotChangedEvent;
import com.keilory.shadowmantle.core.render.OverlayHandle;

import java.util.ArrayList;
import java.util.List;

public final class DungeonFeature implements Feature {
    private final BazaarPriceDatabase database;
    private DungeonChestOverlay overlay;
    private OverlayHandle overlayHandle;
    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();

    public DungeonFeature(BazaarPriceDatabase database) {
        this.database = database;
    }

    @Override
    public String id() {
        return FeatureIds.DUNGEON_CALCULATOR;
    }

    @Override
    public void initialize(ShadowMantleCore core) {
        overlay = new DungeonChestOverlay(database, core.scheduler());
        overlay.register();
        overlayHandle = core.overlays().register(overlay);
        subscriptions.add(core.events().subscribe(ScreenOpenedEvent.class, event -> overlay.onScreenOpened(event.screen())));
        subscriptions.add(core.events().subscribe(ScreenClosedEvent.class, event -> overlay.onScreenClosed(event.screen())));
        subscriptions.add(core.events().subscribe(SlotChangedEvent.class, event -> DungeonChestOverlay.onSlotChanged(event.handler(), event.slotId())));
    }

    @Override
    public void shutdown() {
        for (EventBus.Subscription subscription : subscriptions) {
            subscription.unsubscribe();
        }
        subscriptions.clear();
        if (overlayHandle != null) {
            overlayHandle.unregister();
            overlayHandle = null;
        }
        if (overlay != null) {
            overlay.close();
            overlay = null;
        }
    }
}
