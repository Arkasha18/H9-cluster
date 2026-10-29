package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import net.adminrunet.h9cluster.skins.SkinRegistry;
import net.adminrunet.h9cluster.skins.SkinSettings;
import net.adminrunet.h9cluster.skins.SpeedDisplayMode;

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
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowDisplayManager;

/** Integration coverage for the global gear-number checkbox and draft lifecycle. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class SettingsGearNumberTest {
    @Before
    public void prepareSettingsAndClusterDisplay() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(SkinPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        SkinPreferences.setSelectedSkin(context, SkinRegistry.HORIZON);
        context.getSystemService(Context.DISPLAY_SERVICE);
        ShadowDisplayManager.addDisplay("w1920dp-h720dp");
        ShadowDisplayManager.addDisplay("w1920dp-h720dp");
        assertTrue(ClusterLauncher.isClusterDisplayAvailable(context));
    }

    @Test
    public void existingPreferencesDefaultToVisibleAndSelectionIsGlobal() {
        Context context = RuntimeEnvironment.getApplication();
        SkinPreferences.setFuelTankCapacityLiters(context, 60);
        SkinPreferences.setSpeedDisplayMode(context, SpeedDisplayMode.FACTORY_PLUS_5_KPH);
        assertTrue(SkinPreferences.isGearNumberVisible(context));

        SkinPreferences.setGearNumberVisible(context, false);
        for (SkinRegistry.Definition skin : SkinRegistry.getDefinitions()) {
            SkinPreferences.setSelectedSkin(context, skin.id);
            assertFalse(skin.id, SkinPreferences.isGearNumberVisible(context));
        }
        assertEquals(60, SkinPreferences.getFuelTankCapacityLiters(context));
        assertEquals(SpeedDisplayMode.FACTORY_PLUS_5_KPH,
                SkinPreferences.getSpeedDisplayMode(context));
        SkinPreferences.setGearNumberVisible(context, true);
        assertTrue(SkinPreferences.isGearNumberVisible(context));
    }

    @Test
    public void snapshotIdentityIncludesVisibilityAndSkinSwitchesKeepIt() {
        SkinSettingsSession session = new SkinSettingsSession(
                SkinRegistry.CLASSIC, skinId -> SkinSettings.empty());
        SkinSettingsSession.Snapshot original = session.snapshot();
        assertTrue(original.gearNumberVisible);
        assertTrue(new SkinSettingsSession.Snapshot(SkinRegistry.CLASSIC,
                SkinSettings.empty(), SpeedDisplayMode.REAL).gearNumberVisible);
        assertTrue(new SkinSettingsSession.Snapshot(SkinRegistry.CLASSIC,
                SkinSettings.empty(), SpeedDisplayMode.REAL, 60).gearNumberVisible);

        session.setGearNumberVisible(false);
        assertFalse(original.equals(session.snapshot()));
        for (SkinRegistry.Definition skin : SkinRegistry.getDefinitions()) {
            session.selectSkin(skin.id);
            assertFalse(skin.id, session.snapshot().gearNumberVisible);
        }
        session.selectSkin(SkinRegistry.CLASSIC);
        session.setGearNumberVisible(true);
        assertEquals(original, session.snapshot());
        assertEquals(original.hashCode(), session.snapshot().hashCode());
    }

    @Test
    public void checkboxPreviewsWithoutSavingThenSaveSurvivesReopening()
            throws Exception {
        ActivityController<SettingsActivity> controller =
                Robolectric.buildActivity(SettingsActivity.class).setup();
        SettingsActivity activity = controller.get();
        try {
            SettingsView view = settingsView(activity);
            render(view).recycle();
            assertTrue(snapshot(activity).gearNumberVisible);
            assertTrue(SkinPreferences.isGearNumberVisible(activity));

            tap(view, 220.0f, 392.0f);
            Intent preview = shadowOf(activity).getNextStartedActivity();
            assertNotNull(preview);
            assertTrue(preview.getBooleanExtra(PreviewActivity.EXTRA_HAS_DRAFT, false));
            assertFalse(preview.getBooleanExtra(
                    PreviewActivity.EXTRA_DRAFT_GEAR_NUMBER_VISIBLE, true));
            assertFalse(resolvePreview(preview).gearNumberVisible);
            assertFalse(snapshot(activity).gearNumberVisible);
            assertTrue(SkinPreferences.isGearNumberVisible(activity));
            exportSettings(view);

            tap(view, 600.0f, 392.0f);
            assertFalse(SkinPreferences.isGearNumberVisible(activity));
            Intent saved = shadowOf(activity).getNextStartedActivity();
            assertNotNull(saved);
            assertTrue(saved.getBooleanExtra(PreviewActivity.EXTRA_RELOAD_SKIN, false));
            assertFalse(saved.getBooleanExtra(PreviewActivity.EXTRA_HAS_DRAFT, false));
            assertFalse(resolvePreview(saved).gearNumberVisible);
            activity.onBackPressed();
            assertNull("Saved settings must not trigger another rollback",
                    shadowOf(activity).getNextStartedActivity());
        } finally {
            controller.pause().stop().destroy();
        }

        ActivityController<SettingsActivity> reopened =
                Robolectric.buildActivity(SettingsActivity.class).setup();
        try {
            assertFalse(snapshot(reopened.get()).gearNumberVisible);
        } finally {
            reopened.pause().stop().destroy();
        }
    }

    @Test
    public void backRestoresHiddenGearNumberAfterUnsavedVisiblePreview()
            throws Exception {
        SkinPreferences.setGearNumberVisible(RuntimeEnvironment.getApplication(), false);
        ActivityController<SettingsActivity> controller =
                Robolectric.buildActivity(SettingsActivity.class).setup();
        SettingsActivity activity = controller.get();
        try {
            SettingsView view = settingsView(activity);
            render(view).recycle();
            assertFalse(snapshot(activity).gearNumberVisible);
            tap(view, 220.0f, 392.0f);
            Intent preview = shadowOf(activity).getNextStartedActivity();
            assertNotNull(preview);
            assertTrue(preview.getBooleanExtra(
                    PreviewActivity.EXTRA_DRAFT_GEAR_NUMBER_VISIBLE, false));
            assertTrue(resolvePreview(preview).gearNumberVisible);
            assertFalse(SkinPreferences.isGearNumberVisible(activity));

            activity.onBackPressed();
            Intent restored = shadowOf(activity).getNextStartedActivity();
            assertNotNull("Back must restore the saved Display 2 state", restored);
            assertTrue(restored.getBooleanExtra(PreviewActivity.EXTRA_RELOAD_SKIN, false));
            assertFalse(restored.getBooleanExtra(PreviewActivity.EXTRA_HAS_DRAFT, false));
            assertFalse(resolvePreview(restored).gearNumberVisible);
            assertFalse(SkinPreferences.isGearNumberVisible(activity));
            assertTrue(activity.isFinishing());
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void choosingAnotherSkinKeepsUnsavedGearVisibilityInPreview()
            throws Exception {
        ActivityController<SettingsActivity> controller =
                Robolectric.buildActivity(SettingsActivity.class).setup();
        SettingsActivity activity = controller.get();
        try {
            SettingsView view = settingsView(activity);
            render(view).recycle();
            tap(view, 220.0f, 392.0f);
            assertNotNull(shadowOf(activity).getNextStartedActivity());

            SkinRegistry.Definition[] skins = SkinRegistry.getDefinitions();
            for (int index = 0; index < skins.length; index++) {
                if (!skins[index].hasRenderer()) {
                    continue;
                }
                tap(view, 480.0f, 200.0f);
                AlertDialog picker = ShadowAlertDialog.getLatestAlertDialog();
                assertNotNull(picker);
                shadowOf(picker).clickOnItem(index);
                assertEquals(skins[index].id, snapshot(activity).skinId);
                assertFalse(skins[index].id, snapshot(activity).gearNumberVisible);
                Intent preview = shadowOf(activity).getNextStartedActivity();
                assertNotNull(skins[index].id, preview);
                SkinSettingsSession.Snapshot draft = resolvePreview(preview);
                assertEquals(skins[index].id, draft.skinId);
                assertFalse(skins[index].id, draft.gearNumberVisible);
                assertTrue(SkinPreferences.isGearNumberVisible(activity));
            }
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
        // Attach a context without starting the vehicle readers in onCreate.
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
        File directory = new File("build/reports/gear-settings");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap frame = render(view);
        try (FileOutputStream output = new FileOutputStream(
                new File(directory, "settings-gear-off.png"))) {
            assertTrue(frame.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            frame.recycle();
        }
    }
}
