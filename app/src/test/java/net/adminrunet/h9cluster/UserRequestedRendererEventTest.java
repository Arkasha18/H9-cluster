package net.adminrunet.h9cluster;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.adminrunet.h9cluster.trip.TripSummaryLayerState;

import org.junit.Test;

public final class UserRequestedRendererEventTest {
    @Test
    public void manualRequestIsConsumedOnlyOnceAcrossActivityRecreation() {
        UserRequestedRendererEvent firstActivity =
                new UserRequestedRendererEvent(false);
        assertTrue(firstActivity.consume(true));

        UserRequestedRendererEvent recreatedActivity =
                new UserRequestedRendererEvent(firstActivity.isConsumed());

        assertFalse(recreatedActivity.consume(true));
    }

    @Test
    public void consumedRequestCannotOverrideANewerEngineStopAfterRecreation() {
        UserRequestedRendererEvent event =
                new UserRequestedRendererEvent(false);
        TripSummaryLayerState layers = new TripSummaryLayerState(true);
        assertTrue(event.consume(true));
        layers.onUserRequestedRenderer();
        assertTrue(layers.isRendererVisible());

        layers.onSummaryShown();
        UserRequestedRendererEvent recreatedEvent =
                new UserRequestedRendererEvent(event.isConsumed());
        TripSummaryLayerState recreatedLayers =
                new TripSummaryLayerState(true);
        if (recreatedEvent.consume(true)) {
            recreatedLayers.onUserRequestedRenderer();
        }

        assertFalse(recreatedLayers.isRendererVisible());
    }

    @Test
    public void aNewManualIntentCanRequestTheRendererAgain() {
        UserRequestedRendererEvent event =
                new UserRequestedRendererEvent(true);

        event.onNewIntent();

        assertTrue(event.consume(true));
    }

    @Test
    public void automaticIntentNeverRequestsTheRenderer() {
        UserRequestedRendererEvent event =
                new UserRequestedRendererEvent(false);

        assertFalse(event.consume(false));
        assertFalse(event.isConsumed());
    }
}
