package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import net.adminrunet.h9cluster.skins.SkinRegistry;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;

/** Actual Canvas output: any supported tank size preserves the fuel fraction. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class FuelTankSkinRenderingTest {
    @Test
    public void everySkinShowsArbitraryTankLitersWithTheSameGaugePosition()
            throws Exception {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1));
        int checkedSkins = 0;
        for (SkinRegistry.Definition definition : SkinRegistry.getDefinitions()) {
            if (!definition.hasRenderer()) continue;
            checkedSkins++;
            for (float baselineLiters : new float[] {40.0f, 80.0f}) {
                ClusterState source = state(baselineLiters);
                Frame standard = render(definition.id, source);
                try {
                    assertFuelText(definition.id, standard, Math.round(baselineLiters));
                    assertFuelFraction(definition.id, standard, baselineLiters / 80.0f);
                    for (int capacity : new int[] {20, 55, 60, 80, 100, 200}) {
                        Frame configured = render(
                                definition.id,
                                source.withFuelTankCapacity(capacity));
                        try {
                            assertFuelText(
                                    definition.id,
                                    configured,
                                    Math.round(baselineLiters / 80.0f * capacity));
                            assertFuelFraction(
                                    definition.id,
                                    configured,
                                    baselineLiters / 80.0f);
                            assertSameFuelGauge(definition.id, standard, configured);
                        } finally {
                            configured.bitmap.recycle();
                        }
                    }
                } finally {
                    standard.bitmap.recycle();
                }
            }
        }
        assertEquals(7, checkedSkins);
    }

    private static void assertFuelFraction(String skinId, Frame frame, float fraction) {
        if (!SkinRegistry.CLASSIC.equals(skinId) && !SkinRegistry.SPORT.equals(skinId)) {
            return;
        }
        String expected = String.format(java.util.Locale.US, "%.1f", fraction);
        boolean found = false;
        for (TextCall call : frame.canvas.textCalls) {
            if (expected.equals(call.text)) {
                found = true;
                break;
            }
        }
        assertTrue(skinId + " must draw fuel fraction " + expected, found);
    }

    private static void assertFuelText(String skinId, Frame frame, int liters) {
        String expected;
        float expectedY;
        float minimumX;
        float maximumX;
        boolean verticallyCentered = false;
        switch (skinId) {
            case SkinRegistry.CLASSIC:
                expected = liters + " L";
                expectedY = 298;
                minimumX = 590; maximumX = 602;
                verticallyCentered = true;
                break;
            case SkinRegistry.SPORT:
                expected = liters + " L";
                expectedY = 385;
                minimumX = 590; maximumX = 602;
                verticallyCentered = true;
                break;
            case SkinRegistry.SIMPLE:
                expected = liters + " L";
                expectedY = 668;
                minimumX = 162; maximumX = 174;
                break;
            case SkinRegistry.SCULPTED_TITANIUM:
                expected = liters + " л";
                expectedY = 624;
                minimumX = 151; maximumX = 163;
                break;
            case SkinRegistry.ION_AURORA:
                expected = Integer.toString(liters);
                expectedY = 600;
                minimumX = 135; maximumX = 147;
                verticallyCentered = true;
                break;
            case SkinRegistry.HORIZON:
                expected = Integer.toString(liters);
                expectedY = 236;
                minimumX = 660; maximumX = 672;
                break;
            case SkinRegistry.APEX_SPORT:
                expected = Integer.toString(liters);
                expectedY = 250;
                minimumX = 410; maximumX = 510;
                break;
            default:
                throw new AssertionError("Fuel readout needs coverage for " + skinId);
        }
        boolean found = false;
        for (TextCall call : frame.canvas.textCalls) {
            float actualY = verticallyCentered ? call.centerY : call.y;
            if (call.text.equals(expected) && call.x >= minimumX && call.x <= maximumX
                    && Math.abs(actualY - expectedY) < 0.01f) {
                found = true;
                break;
            }
        }
        assertTrue(skinId + " must draw " + expected + " in its fuel readout; calls="
                + frame.canvas.textCalls, found);
    }

    private static void assertSameFuelGauge(String skinId, Frame standard, Frame small)
            throws Exception {
        switch (skinId) {
            case SkinRegistry.CLASSIC:
            case SkinRegistry.SPORT:
                Bitmap standardNeedles = needles(standard.view);
                Bitmap smallNeedles = needles(small.view);
                try {
                    int fuelCenterY = SkinRegistry.CLASSIC.equals(skinId) ? 305 : 378;
                    assertSameRegion(skinId, standardNeedles, smallNeedles,
                            520, fuelCenterY - 150, 740, fuelCenterY + 150);
                } finally {
                    standardNeedles.recycle();
                    smallNeedles.recycle();
                }
                break;
            case SkinRegistry.HORIZON:
                // Compare the rail itself, without the numeric liters readout whose
                // left edge can reach the pixels immediately to its right.
                assertSameRegion(skinId, standard.bitmap, small.bitmap, 615, 174, 631, 349);
                break;
            case SkinRegistry.ION_AURORA:
                assertSameRegion(skinId, standard.bitmap, small.bitmap, 90, 624, 215, 634);
                break;
            case SkinRegistry.APEX_SPORT:
                // Compare the pointer's outer end. The inner warning arc intentionally
                // represents an absolute 10-liter reserve and changes with tank size.
                for (int y = 173; y <= 303; y++) {
                    for (int x = 401; x <= 531; x++) {
                        int radiusSquared = (x - 466) * (x - 466) + (y - 238) * (y - 238);
                        if (radiusSquared >= 57 * 57 && radiusSquared <= 61 * 61) {
                            assertEquals(skinId + " fuel arc at " + x + "," + y,
                                    standard.bitmap.getPixel(x, y), small.bitmap.getPixel(x, y));
                        }
                    }
                }
                break;
            default:
                // Simple and Sculpted Titanium expose fuel only as a numeric value.
                break;
        }
    }

    private static Bitmap needles(View view) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        Method drawNeedles = view.getClass().getDeclaredMethod("drawNeedleLayer", Canvas.class);
        drawNeedles.setAccessible(true);
        drawNeedles.invoke(view, new Canvas(bitmap));
        return bitmap;
    }

    private static void assertSameRegion(String skinId, Bitmap standard, Bitmap small,
            int left, int top, int right, int bottom) {
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                assertEquals(skinId + " fuel level at " + x + "," + y,
                        standard.getPixel(x, y), small.getPixel(x, y));
            }
        }
    }

    private static Frame render(String skinId, ClusterState state) {
        View view = SkinRegistry.createRenderer(RuntimeEnvironment.getApplication(), skinId);
        ((ClusterRenderer) view).setClusterState(state);
        view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1920, 720);
        Bitmap bitmap = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        TextRecordingCanvas canvas = new TextRecordingCanvas(bitmap);
        view.draw(canvas);
        ShadowSystemClock.advanceBy(Duration.ofMillis(1_200));
        canvas.textCalls.clear();
        view.draw(canvas);
        return new Frame(view, bitmap, canvas);
    }

    private static ClusterState state(float fuelLiters) {
        return new ClusterState(
                86, 2_400, 8, GearSelector.DRIVE, 92, 78.0f, fuelLiters, 421,
                28_642.25, 42.3f, 167.8f, 2.35f, 2.37f, 2.42f, 2.40f,
                12.6f, 14.8f, 15.3f, 13.8f, -12.5f, 0.0f,
                86.0f, 86.0f, 86.0f, 86.0f, 220.0f,
                0L, 0L, 0L, 0L, 0L, "ECO");
    }

    private static final class Frame {
        final View view;
        final Bitmap bitmap;
        final TextRecordingCanvas canvas;

        Frame(View view, Bitmap bitmap, TextRecordingCanvas canvas) {
            this.view = view;
            this.bitmap = bitmap;
            this.canvas = canvas;
        }
    }

    private static final class TextCall {
        final String text;
        final float x;
        final float y;
        final float centerY;

        TextCall(String text, float x, float y, Paint paint) {
            this.text = text;
            this.x = x;
            this.y = y;
            Paint.FontMetrics metrics = paint.getFontMetrics();
            centerY = y + (metrics.ascent + metrics.descent) * 0.5f;
        }

        @Override
        public String toString() {
            return text + "@(x=" + x + ", baseline=" + y + ", centerY=" + centerY + ")";
        }
    }

    private static final class TextRecordingCanvas extends Canvas {
        final List<TextCall> textCalls = new ArrayList<>();

        TextRecordingCanvas(Bitmap bitmap) {
            super(bitmap);
        }

        @Override
        public void drawText(String text, float x, float y, Paint paint) {
            if (paint.getAlpha() > 0) textCalls.add(new TextCall(text, x, y, paint));
            super.drawText(text, x, y, paint);
        }
    }
}
