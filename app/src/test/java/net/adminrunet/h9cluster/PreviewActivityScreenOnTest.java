package net.adminrunet.h9cluster;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowManager;
import java.time.Duration;
import net.adminrunet.h9cluster.trip.TripSummary;
import net.adminrunet.h9cluster.trip.TripSummaryCoordinator;
import net.adminrunet.h9cluster.trip.TripSummaryLayerStore;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDisplayManager;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class PreviewActivityScreenOnTest {
    private ActivityController<PreviewActivity> controller;

    @After public void destroy() {
        if (controller != null) {
            controller.get().closeClusterWindow();
            controller.pause().stop().destroy();
        }
    }

    @Test public void summaryTimeoutReleasesScreenAndNextEngineStartRestoresIt() {
        PreviewActivity activity = create(false);
        assertTrue(keepsScreenOn(activity));
        ReflectionHelpers.callInstanceMethod(activity, "showTripSummary",
                ClassParameter.from(TripSummary.class,
                        new TripSummary(1.0, true, 9.0, true, 10_000, true)));
        assertNotNull(ReflectionHelpers.getField(activity, "tripSummaryView"));
        assertTrue(keepsScreenOn(activity));

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11));
        assertNull(ReflectionHelpers.getField(activity, "tripSummaryView"));
        assertFalse("A hidden panel and expired summary must allow Android screen timeout",
                keepsScreenOn(activity));

        TripSummaryCoordinator coordinator = ReflectionHelpers.getField(activity, "tripCoordinator");
        coordinator.onClusterState(VehicleSpeedTripIntegrationTest.state(
                800, 0, SystemClock.elapsedRealtime()));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        coordinator.onClusterState(VehicleSpeedTripIntegrationTest.state(
                800, 0, SystemClock.elapsedRealtime()));
        assertTrue("A confirmed new engine run must keep the visible dashboard on",
                keepsScreenOn(activity));
    }

    @Test public void recreationOfSuppressedPanelDoesNotKeepScreenOn() {
        assertFalse(keepsScreenOn(create(true)));
    }

    @Test public void explicitExitReleasesScreenImmediately() {
        PreviewActivity activity = create(false);
        assertTrue(keepsScreenOn(activity));
        activity.closeClusterWindow();
        assertFalse(keepsScreenOn(activity));
    }

    private PreviewActivity create(boolean suppressed) {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(60));
        Context app = RuntimeEnvironment.getApplication();
        new TripSummaryLayerStore(app).setRendererSuppressed(suppressed);
        SkinPreferences.setSelectedSkin(app, net.adminrunet.h9cluster.skins.SkinRegistry.CLASSIC);
        ShadowDisplayManager.addDisplay("w1920dp-h720dp");
        int clusterDisplay = ShadowDisplayManager.addDisplay("w1920dp-h720dp");
        assertEquals(ClusterDisplayPolicy.CLUSTER_DISPLAY_ID, clusterDisplay);
        DisplayManager displays = (DisplayManager) app.getSystemService(Context.DISPLAY_SERVICE);
        Context displayContext = app.createDisplayContext(displays.getDisplay(clusterDisplay));
        controller = Robolectric.buildActivity(PreviewActivity.class);
        PreviewActivity activity = controller.get();
        ReflectionHelpers.setField(activity, "mWindowManager",
                displayContext.getSystemService(Context.WINDOW_SERVICE));
        controller.create().start().resume().visible();
        assertFalse("The test must exercise the actual dashboard activity", activity.isFinishing());
        ClusterDataSource source = ReflectionHelpers.getField(activity, "dataSource");
        assertNotNull(source);
        source.stop();
        return activity;
    }

    private static boolean keepsScreenOn(PreviewActivity activity) {
        return (activity.getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
    }
}
