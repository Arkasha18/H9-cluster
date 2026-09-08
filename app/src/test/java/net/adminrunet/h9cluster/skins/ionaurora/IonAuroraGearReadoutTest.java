package net.adminrunet.h9cluster.skins.ionaurora;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;

import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.GearSelector;

import java.time.Duration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class IonAuroraGearReadoutTest {
    @Test
    public void automaticForwardRatiosAreDigitsWithoutSelectorLetters() {
        for (int ratio = 1; ratio <= 8; ratio++) {
            assertEquals(Integer.toString(ratio),
                    IonAuroraClusterView.currentGearLabel(GearSelector.DRIVE, ratio));
        }
    }

    @Test
    public void manualAndOtherSelectorPositionsNeverProduceAGearLabel() {
        for (int ratio : new int[] {1, 2, 8}) {
            assertEquals("", IonAuroraClusterView.currentGearLabel(GearSelector.MANUAL, ratio));
        }
        for (String selector : new String[] {GearSelector.PARK, GearSelector.NEUTRAL,
                GearSelector.REVERSE, GearSelector.UNKNOWN, "M1", "M2", "M8", "D1",
                "unsupported", null}) {
            assertEquals("Unexpected label for selector " + selector, "",
                    IonAuroraClusterView.currentGearLabel(selector, 5));
        }
    }

    @Test
    public void invalidAutomaticRatiosRemainEmpty() {
        for (int ratio : new int[] {Integer.MIN_VALUE, -1, 0, 9, Integer.MAX_VALUE}) {
            assertEquals("", IonAuroraClusterView.currentGearLabel(GearSelector.DRIVE, ratio));
        }
    }

    @Test
    public void numeralStaysInsideTinyExceptionAndClearsWhenLeavingDrive() {
        Context context = RuntimeEnvironment.getApplication();
        ShadowSystemClock.advanceBy(Duration.ofMillis(1L));
        IonAuroraClusterView view = new IonAuroraClusterView(context, false);
        view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1920, 720);
        Bitmap frame = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(frame);
        view.setClusterState(state(GearSelector.DRIVE, 8));
        view.draw(canvas);
        ShadowSystemClock.advanceBy(Duration.ofMillis(1200L));
        view.draw(canvas);
        assertGearReservation(frame, true, "D8");

        String[] selectors = {GearSelector.MANUAL, GearSelector.MANUAL, GearSelector.MANUAL,
                GearSelector.PARK, GearSelector.NEUTRAL, GearSelector.REVERSE,
                GearSelector.UNKNOWN, "unsupported", null, GearSelector.DRIVE};
        int[] ratios = {1, 2, 8, 5, 5, 5, 5, 5, 5, 0};
        for (int index = 0; index < selectors.length; index++) {
            view.setClusterState(state(selectors[index], ratios[index]));
            ShadowSystemClock.advanceBy(Duration.ofMillis(40L));
            view.draw(canvas);
            assertGearReservation(frame, false, selectors[index] + ":" + ratios[index]);
        }
        frame.recycle();
    }

    private static void assertGearReservation(Bitmap frame, boolean expectDigit, String state) {
        int visibleNumeralPixels = 0;
        for (int y = 18; y < 79; y++) {
            for (int x = 921; x < 1048; x++) {
                int alpha = frame.getPixel(x, y) >>> 24;
                boolean tinyDigit = x >= 982 && x < 1018 && y >= 28 && y < 66;
                if (expectDigit && tinyDigit) {
                    if (alpha != 0) visibleNumeralPixels++;
                } else {
                    assertEquals(state + " must leave gear pixel empty at " + x + "," + y,
                            0, alpha);
                }
            }
        }
        if (expectDigit) assertTrue("Automatic gear numeral must be visible", visibleNumeralPixels > 30);
        for (int y = 666; y < 700; y++) {
            for (int x = 1426; x < 1529; x++) {
                assertEquals("The skin must never draw driveMode", 0, frame.getPixel(x, y) >>> 24);
            }
        }
    }

    private static ClusterState state(String selector, int gear) {
        long nowMs = SystemClock.elapsedRealtime();
        return new ClusterState(86, 2400, gear, selector, 92, 78.0f, 47.0f, 421,
                28642.0, 42.3f, 167.8f, 2.35f, 2.37f, 2.42f, 2.40f,
                12.6f, 14.8f, 14.8f, 13.8f, 18.5f, 12.0f,
                85.8f, 86.2f, 85.9f, 86.1f, 224.0f,
                nowMs, nowMs, nowMs, nowMs, nowMs, "NORMAL");
    }
}
