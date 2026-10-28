package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import net.adminrunet.h9cluster.skins.SkinRegistry;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDisplayManager;

/** Exercises the real settings touch targets and preview/save/cancel transport. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class SettingsFuelCapacityTest {
    @Before
    public void prepareSettingsAndClusterDisplay() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(SkinPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        // Horizon also exercises the skin-settings button beside the tank selector.
        SkinPreferences.setSelectedSkin(context, SkinRegistry.HORIZON);
        context.getSystemService(Context.DISPLAY_SERVICE);
        ShadowDisplayManager.addDisplay("w1920dp-h720dp");
        ShadowDisplayManager.addDisplay("w1920dp-h720dp");
        assertTrue(ClusterLauncher.isClusterDisplayAvailable(context));
    }

    @Test
    public void selectingSixtyPreviewsWithoutSavingThenSaveSurvivesReopening()
            throws Exception {
        ActivityController<SettingsActivity> controller =
                Robolectric.buildActivity(SettingsActivity.class).setup();
        SettingsActivity activity = controller.get();
        try {
            SettingsView view = settingsView(activity);
            render(view).recycle();
            assertEquals(80, snapshot(activity).fuelTankCapacityLiters);
            assertEquals(80, SkinPreferences.getFuelTankCapacityLiters(activity));

            tap(view, SettingsFuelControl.segmentCenterX(1), 339.0f);
            Intent preview = shadowOf(activity).getNextStartedActivity();
            assertNotNull(preview);
            assertTrue(preview.getBooleanExtra(PreviewActivity.EXTRA_HAS_DRAFT, false));
            assertEquals(60, preview.getIntExtra(
                    PreviewActivity.EXTRA_DRAFT_FUEL_TANK_CAPACITY, -1));
            assertEquals(60, resolvePreview(preview).fuelTankCapacityLiters);
            assertEquals(80, SkinPreferences.getFuelTankCapacityLiters(activity));
            exportSettings(view);

            tap(view, 480.0f, 392.0f);
            assertEquals(60, SkinPreferences.getFuelTankCapacityLiters(activity));
            Intent saved = shadowOf(activity).getNextStartedActivity();
            assertNotNull(saved);
            assertTrue(saved.getBooleanExtra(PreviewActivity.EXTRA_RELOAD_SKIN, false));
            assertFalse(saved.getBooleanExtra(PreviewActivity.EXTRA_HAS_DRAFT, false));
            assertEquals(60, resolvePreview(saved).fuelTankCapacityLiters);
            activity.onBackPressed();
            assertNull("Saved settings must not trigger another rollback",
                    shadowOf(activity).getNextStartedActivity());
        } finally {
            controller.pause().stop().destroy();
        }

        ActivityController<SettingsActivity> reopened =
                Robolectric.buildActivity(SettingsActivity.class).setup();
        try {
            assertEquals(60, snapshot(reopened.get()).fuelTankCapacityLiters);
        } finally {
            reopened.pause().stop().destroy();
        }
    }

    @Test
    public void backRestoresPersistedCapacityInsteadOfKeepingUnsavedPreview()
            throws Exception {
        SkinPreferences.setFuelTankCapacityLiters(RuntimeEnvironment.getApplication(), 60);
        ActivityController<SettingsActivity> controller =
                Robolectric.buildActivity(SettingsActivity.class).setup();
        SettingsActivity activity = controller.get();
        try {
            SettingsView view = settingsView(activity);
            render(view).recycle();
            assertEquals(60, snapshot(activity).fuelTankCapacityLiters);
            tap(view, SettingsFuelControl.segmentCenterX(0), 339.0f);
            Intent preview = shadowOf(activity).getNextStartedActivity();
            assertNotNull(preview);
            assertEquals(80, resolvePreview(preview).fuelTankCapacityLiters);
            assertEquals(60, SkinPreferences.getFuelTankCapacityLiters(activity));

            activity.onBackPressed();
            Intent restored = shadowOf(activity).getNextStartedActivity();
            assertNotNull("Back must restore the saved Display 2 state", restored);
            assertTrue(restored.getBooleanExtra(PreviewActivity.EXTRA_RELOAD_SKIN, false));
            assertFalse(restored.getBooleanExtra(PreviewActivity.EXTRA_HAS_DRAFT, false));
            assertEquals(60, resolvePreview(restored).fuelTankCapacityLiters);
            assertEquals(60, SkinPreferences.getFuelTankCapacityLiters(activity));
            assertTrue(activity.isFinishing());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    private static SettingsView settingsView(SettingsActivity activity) throws Exception {
        Field field = SettingsActivity.class.getDeclaredField("settingsView");
        field.setAccessible(true);
        return (SettingsView) field.get(activity);
    }

    private static SkinSettingsSession.Snapshot snapshot(SettingsActivity activity)
            throws Exception {
        Field field = SettingsActivity.class.getDeclaredField("session");
        field.setAccessible(true);
        return ((SkinSettingsSession) field.get(activity)).snapshot();
    }

    private static SkinSettingsSession.Snapshot resolvePreview(Intent intent)
            throws Exception {
        // Attach an Activity context without starting vehicle data sources in onCreate.
        PreviewActivity preview = Robolectric.buildActivity(PreviewActivity.class).get();
        Method resolve = PreviewActivity.class.getDeclaredMethod("resolveSnapshot", Intent.class);
        resolve.setAccessible(true);
        return (SkinSettingsSession.Snapshot) resolve.invoke(preview, intent);
    }

    private static void tap(View view, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0);
        try {
            assertTrue(view.onTouchEvent(event));
        } finally {
            event.recycle();
        }
    }

    private static Bitmap render(SettingsView view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 960, 540);
        Bitmap frame = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(frame));
        return frame;
    }

    private static void exportSettings(SettingsView view) throws Exception {
        File directory = new File("build/reports/fuel-settings");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap frame = render(view);
        try (FileOutputStream output = new FileOutputStream(
                new File(directory, "settings-60.png"))) {
            assertTrue(frame.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            frame.recycle();
        }
    }
}
