package net.adminrunet.h9cluster;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import net.adminrunet.h9cluster.skins.SkinRegistry;

import java.time.Duration;
import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;

/** The shared toggle changes the added numeral in real Canvas output for every skin. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class GearNumberVisibilityTest {
    @Test
    public void everySkinCanHideAndRestoreItsNumberWithoutAnotherTelemetryUpdate() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1));
        int checked = 0;
        for (SkinRegistry.Definition definition : SkinRegistry.getDefinitions()) {
            if (!definition.hasRenderer()) continue;
            checked++;
            boolean horizon = SkinRegistry.HORIZON.equals(definition.id);
            View view = view(definition.id);
            ClusterRenderer renderer = (ClusterRenderer) view;
            ClusterState telemetry = state(GearSelector.DRIVE);
            renderer.setClusterState(telemetry);
            render(view, horizon);
            ShadowSystemClock.advanceBy(Duration.ofMillis(1_200));

            Frame visible = render(view, horizon);
            assertEquals(definition.id + " is visible by default", horizon ? "D8" : "8",
                    visible.gearText);
            renderer.setGearNumberVisible(false);
            Frame hidden = render(view, horizon);
            assertEquals(definition.id + " hides only the ratio", horizon ? "D" : "",
                    hidden.gearText);
            assertTrue(definition.id + " must remove visible pixels of the number",
                    !Arrays.equals(visible.gearPixels, hidden.gearPixels));

            renderer.setGearNumberVisible(true);
            Frame restored = render(view, horizon);
            assertEquals(visible.gearText, restored.gearText);
            assertArrayEquals(definition.id + " restores the same number without a bus update",
                    visible.gearPixels, restored.gearPixels);
            assertEquals(8, telemetry.currentGear);
            assertEquals(GearSelector.DRIVE, telemetry.gearSelector);
        }
        assertEquals(7, checked);
    }

    @Test
    public void manualSelectorRemainsAndSkinsWithoutManualNumeralsStayUnchanged() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1));
        for (SkinRegistry.Definition definition : SkinRegistry.getDefinitions()) {
            if (!definition.hasRenderer()) continue;
            boolean horizon = SkinRegistry.HORIZON.equals(definition.id);
            View view = view(definition.id);
            ClusterRenderer renderer = (ClusterRenderer) view;
            renderer.setClusterState(state(GearSelector.MANUAL));
            render(view, horizon);
            ShadowSystemClock.advanceBy(Duration.ofMillis(1_200));
            Frame visible = render(view, horizon);
            assertEquals(definition.id + " keeps its existing manual presentation",
                    horizon ? "M8" : "", visible.gearText);
            renderer.setGearNumberVisible(false);
            Frame hidden = render(view, horizon);
            assertEquals(definition.id + " keeps the manual selector",
                    horizon ? "M" : "", hidden.gearText);
            if (!horizon) {
                assertArrayEquals(definition.id + " has no added manual numeral",
                        visible.gearPixels, hidden.gearPixels);
            }
        }
    }

    private static View view(String skinId) {
        View view = SkinRegistry.createRenderer(RuntimeEnvironment.getApplication(), skinId);
        view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1920, 720);
        return view;
    }

    private static Frame render(View view, boolean horizon) {
        Bitmap bitmap = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        try {
            GearCanvas canvas = new GearCanvas(bitmap, horizon);
            view.draw(canvas);
            int left = horizon ? 916 : 976;
            int top = horizon ? 100 : 24;
            int width = horizon ? 88 : 48;
            int height = horizon ? 40 : 44;
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, left, top, width, height);
            return new Frame(canvas.gearText, pixels);
        } finally {
            bitmap.recycle();
        }
    }

    private static ClusterState state(String selector) {
        return new ClusterState(
                86, 2_400, 8, selector, 92, 78.0f, 47.0f, 421,
                28_642.25, 42.3f, 167.8f, 2.35f, 2.37f, 2.42f, 2.40f,
                12.6f, 14.8f, 15.3f, 13.8f, -12.5f, 0.0f,
                86.0f, 86.0f, 86.0f, 86.0f, 220.0f,
                0L, 0L, 0L, 0L, 0L, "ECO");
    }

    private static final class Frame {
        final String gearText;
        final int[] gearPixels;

        Frame(String gearText, int[] gearPixels) {
            this.gearText = gearText;
            this.gearPixels = gearPixels;
        }
    }

    private static final class GearCanvas extends Canvas {
        private final boolean horizon;
        String gearText = "";

        GearCanvas(Bitmap bitmap, boolean horizon) {
            super(bitmap);
            this.horizon = horizon;
        }

        @Override
        public void drawText(String text, float x, float y, Paint paint) {
            float expectedX = horizon ? 960 : 1000;
            float expectedY = horizon ? 130 : 63;
            // APEX centers by glyph bounds and uses a left-aligned draw origin.
            if (Math.abs(x - expectedX) < 24.0f && Math.abs(y - expectedY) < 0.01f
                    && paint.getAlpha() > 0) {
                gearText = text;
            }
            super.drawText(text, x, y, paint);
        }
    }
}
