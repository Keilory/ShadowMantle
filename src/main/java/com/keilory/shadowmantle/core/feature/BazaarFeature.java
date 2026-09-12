package com.keilory.shadowmantle.core.feature;

import com.keilory.shadowmantle.bazaar.BazaarPriceDatabase;
import com.keilory.shadowmantle.bazaar.BazaarScanner;
import com.keilory.shadowmantle.bazaar.BazaarTooltip;
import com.keilory.shadowmantle.core.EventBus;
import com.keilory.shadowmantle.core.ShadowMantleCore;
import com.keilory.shadowmantle.core.Feature;
import com.keilory.shadowmantle.core.FeatureIds;
import com.keilory.shadowmantle.core.event.ClientTickEvent;
import com.keilory.shadowmantle.core.event.ScreenClosedEvent;
import com.keilory.shadowmantle.core.event.ScreenOpenedEvent;
import com.keilory.shadowmantle.core.event.SlotChangedEvent;

import java.util.ArrayList;
import java.util.List;

public final class BazaarFeature implements Feature {
    private final BazaarPriceDatabase database;
    private BazaarScanner scanner;
    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();
    public BazaarFeature(BazaarPriceDatabase database) { this.database = database; }
    @Override public String id() { return FeatureIds.BAZAAR; }
    @Override public void initialize(ShadowMantleCore core) {
        BazaarTooltip.register(database);
        scanner = new BazaarScanner(database, core.scheduler()); scanner.register();
        subscriptions.add(core.events().subscribe(ClientTickEvent.class, event -> scanner.onClientTick(event.client())));
        subscriptions.add(core.events().subscribe(ScreenOpenedEvent.class, event -> scanner.onScreenOpened(event.screen())));
        subscriptions.add(core.events().subscribe(ScreenClosedEvent.class, event -> scanner.onScreenClosed(event.screen())));
        subscriptions.add(core.events().subscribe(SlotChangedEvent.class, event -> BazaarScanner.onSlotChanged(event.handler(), event.slotId())));
    }
    @Override public void shutdown() {
        for (EventBus.Subscription subscription : subscriptions) subscription.unsubscribe();
        subscriptions.clear();
        if (scanner != null) { scanner.close(); scanner = null; }
    }
}
