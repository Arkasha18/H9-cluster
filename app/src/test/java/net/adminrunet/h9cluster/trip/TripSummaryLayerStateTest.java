package net.adminrunet.h9cluster.trip;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TripSummaryLayerStateTest {
    @Test
    public void keepsRendererHiddenAfterSummaryDismissesUntilEngineStarts() {
        TripSummaryLayerState state = new TripSummaryLayerState();
        assertTrue(state.isRendererVisible());
        assertFalse(state.isSummaryVisible());

        state.onSummaryShown();
        assertFalse(state.isRendererVisible());
        assertTrue(state.isSummaryVisible());

        state.onSummaryDismissed();
        assertFalse(state.isRendererVisible());
        assertFalse(state.isSummaryVisible());

        state.onEngineStarted();
        assertTrue(state.isRendererVisible());
        assertFalse(state.isSummaryVisible());
    }

    @Test
    public void restoresHiddenRendererAfterActivityRecreation() {
        TripSummaryLayerState state = new TripSummaryLayerState(true);

        assertFalse(state.isRendererVisible());
        assertFalse(state.isSummaryVisible());

        state.onEngineStarted();
        assertTrue(state.isRendererVisible());
    }

    @Test
    public void userRequestShowsRendererHiddenSinceTheEngineStopped() {
        TripSummaryLayerState state = new TripSummaryLayerState(true);

        state.onUserRequestedRenderer();

        assertTrue(state.isRendererVisible());
    }

    @Test
    public void userRequestShowsRendererAfterAStopWithoutDistance() {
        TripSummaryLayerState state = new TripSummaryLayerState();
        state.onSummaryShown();
        state.onSummaryDismissed();
        assertFalse(state.isRendererVisible());

        state.onUserRequestedRenderer();

        assertTrue(state.isRendererVisible());
        assertFalse(state.isSummaryVisible());
    }

    @Test
    public void userRequestReplacesASummaryStillOnScreen() {
        TripSummaryLayerState state = new TripSummaryLayerState();
        state.onSummaryShown();

        state.onUserRequestedRenderer();

        assertTrue(state.isRendererVisible());
        assertFalse(state.isSummaryVisible());
    }

    @Test
    public void engineStartStillShowsRendererAfterAUserRequest() {
        TripSummaryLayerState state = new TripSummaryLayerState(true);
        state.onUserRequestedRenderer();

        state.onSummaryShown();
        assertFalse(state.isRendererVisible());

        state.onEngineStarted();
        assertTrue(state.isRendererVisible());
    }
}
