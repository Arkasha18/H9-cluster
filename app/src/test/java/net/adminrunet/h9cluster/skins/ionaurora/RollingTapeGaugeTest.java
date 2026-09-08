package net.adminrunet.h9cluster.skins.ionaurora;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class RollingTapeGaugeTest {
    @Test
    public void wholeSpeedAndRawRpmShareTheExactFixedIndex() {
        assertEquals(365.0f, RollingTapeGauge.projectMarkY(86.0f, 86.0f, 3.6f), 0.0f);
        assertEquals(365.0f,
                RollingTapeGauge.projectMarkY(2400.0f, 2400.0f, 0.10f), 0.0f);
        assertTrue(RollingTapeGauge.projectMarkY(2000.0f, 2400.0f, 0.10f) > 402.0f);
        assertTrue(RollingTapeGauge.projectMarkY(3000.0f, 2400.0f, 0.10f) < 328.0f);
    }

    @Test
    public void increasingCurrentValueMovesAStationaryMarkDown() {
        float before = RollingTapeGauge.projectMarkY(80.0f, 70.0f, 3.6f);
        float after = RollingTapeGauge.projectMarkY(80.0f, 75.0f, 3.6f);
        assertTrue(after > before);
    }

    @Test
    public void decreasingCurrentValueMovesAStationaryRpmMarkUp() {
        float before = RollingTapeGauge.projectMarkY(3000.0f, 3500.0f, 0.10f);
        float after = RollingTapeGauge.projectMarkY(3000.0f, 3400.0f, 0.10f);
        assertTrue(after < before);
    }

    @Test
    public void mechanicalRibsUseTheSameProjectionAndTravelAsTheScale() {
        float ribMark = 14.0f * 22.0f / 3.6f;
        assertEquals(RollingTapeGauge.projectMarkY(ribMark, 86.0f, 3.6f),
                RollingTapeGauge.projectRibY(14, 86.0f, 3.6f), 0.0f);
        assertTrue(RollingTapeGauge.projectRibY(14, 90.0f, 3.6f)
                > RollingTapeGauge.projectRibY(14, 86.0f, 3.6f));
        assertTrue(RollingTapeGauge.projectRibY(14, 80.0f, 3.6f)
                < RollingTapeGauge.projectRibY(14, 86.0f, 3.6f));
    }

    @Test
    public void higherMarksAlwaysAppearAboveLowerMarks() {
        float previousY = Float.POSITIVE_INFINITY;
        for (int mark = 0; mark <= 220; mark += 2) {
            float y = RollingTapeGauge.projectMarkY(mark, 86.0f, 3.6f);
            assertTrue(y < previousY);
            previousY = y;
        }
    }

    @Test
    public void equalMarkIntervalsCompressAsTheTapeCurvesAway() {
        float nearSpacing = RollingTapeGauge.projectMarkY(86.0f, 86.0f, 3.6f)
                - RollingTapeGauge.projectMarkY(106.0f, 86.0f, 3.6f);
        float distantSpacing = RollingTapeGauge.projectMarkY(166.0f, 86.0f, 3.6f)
                - RollingTapeGauge.projectMarkY(186.0f, 86.0f, 3.6f);
        assertTrue(distantSpacing > 0.0f);
        assertTrue(distantSpacing < nearSpacing * 0.3f);
    }

    @Test
    public void invalidAndOutOfRangeValuesClampWithoutWrapping() {
        assertEquals(0.0f,
                RollingTapeGauge.sanitizeValue(Float.NaN, 0.0f, 220.0f), 0.0f);
        assertEquals(0.0f, RollingTapeGauge.sanitizeValue(-5.0f, 0.0f, 220.0f), 0.0f);
        assertEquals(220.0f,
                RollingTapeGauge.sanitizeValue(300.0f, 0.0f, 220.0f), 0.0f);
        assertEquals(8000.0f,
                RollingTapeGauge.sanitizeValue(9000.0f, 0.0f, 8000.0f), 0.0f);
    }

    @Test
    public void integerReadoutsDoNotIntroduceTenthsOrScaleRpmToThousands() {
        assertEquals("0", RollingTapeGauge.formatFocusValue(0, 0));
        assertEquals("86", RollingTapeGauge.formatFocusValue(86, 0));
        assertEquals("2400", RollingTapeGauge.formatFocusValue(2400, 0));
        assertEquals("8000", RollingTapeGauge.formatFocusValue(8000, 0));
        assertEquals("3000", RollingTapeGauge.formatLabel(3000.0f, true));
    }

    @Test
    public void fixedIndexLeavesDarkSpaceBesideTheFocusedGlyph() {
        RollingTapeGauge gauge = new RollingTapeGauge(100.0f, 174.0f, 608.0f,
                0.0f, 220.0f, 3.6f, 2.0f, 10.0f, 20.0f, 0,
                Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        Bitmap bitmap = Bitmap.createBitmap(200, 720, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        gauge.draw(canvas, 0.0f, 0.0f, 1.0f);
        gauge.drawFixedIndex(canvas, 1.4f, 1.0f);

        // A former uninterrupted white index crossed these otherwise empty glyph margins.
        assertTrue(Color.red(bitmap.getPixel(72, 365)) < 30);
        assertTrue(Color.green(bitmap.getPixel(72, 365)) < 50);
        assertTrue(Color.red(bitmap.getPixel(128, 365)) < 30);
        assertTrue(Color.green(bitmap.getPixel(128, 365)) < 50);
        assertTrue(Color.green(bitmap.getPixel(45, 365)) > 150);
        assertTrue(Color.green(bitmap.getPixel(155, 365)) > 150);

        int brightGlyphPixels = 0;
        for (int y = 345; y < 386; y++) {
            for (int x = 87; x < 113; x++) {
                int pixel = bitmap.getPixel(x, y);
                if (Color.red(pixel) > 200 && Color.green(pixel) > 200) {
                    brightGlyphPixels++;
                }
            }
        }
        assertTrue(brightGlyphPixels > 40);
        bitmap.recycle();
    }

    @Test
    public void allLightingAndMovingPartsStayInsideTheMaskSafeColumn() {
        for (boolean rpm : new boolean[] {false, true}) {
            RollingTapeGauge gauge = createGauge(rpm);
            Bitmap bitmap = Bitmap.createBitmap(200, 720, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            float[] values = rpm ? new float[] {0.0f, 800.0f, 2400.0f, 8000.0f}
                    : new float[] {0.0f, 86.0f, 140.0f, 220.0f};
            for (int frame = 0; frame < values.length; frame++) {
                bitmap.eraseColor(Color.TRANSPARENT);
                gauge.draw(canvas, values[frame], rpm ? 2200.0f : 30.0f,
                        1.0f, frame * 2.1f);
                gauge.drawFixedIndex(canvas, 1.4f, 1.0f);
                int[] pixels = new int[200 * 720];
                bitmap.getPixels(pixels, 0, 200, 0, 0, 200, 720);
                for (int y = 0; y < 720; y++) {
                    for (int x = 0; x < 200; x++) {
                        if (x < 16 || x >= 184 || y < 101 || y >= 612) {
                            assertEquals("Outside gauge column at " + x + "," + y,
                                    0, Color.alpha(pixels[y * 200 + x]));
                        }
                    }
                }
            }
            bitmap.recycle();
        }
    }

    @Test
    public void ambientEnergyMovesWithoutChangingTheFixedReadoutOrTape() {
        RollingTapeGauge gauge = createGauge(true);
        Bitmap first = Bitmap.createBitmap(200, 720, Bitmap.Config.ARGB_8888);
        Bitmap second = Bitmap.createBitmap(200, 720, Bitmap.Config.ARGB_8888);
        Canvas firstCanvas = new Canvas(first);
        Canvas secondCanvas = new Canvas(second);
        gauge.draw(firstCanvas, 2400.0f, 0.0f, 1.0f, 0.0f);
        gauge.drawFixedIndex(firstCanvas, 1.0f, 1.0f);
        gauge.draw(secondCanvas, 2400.0f, 0.0f, 1.0f, 1.8f);
        gauge.drawFixedIndex(secondCanvas, 1.0f, 1.0f);
        int changedEnergyPixels = 0;
        for (int y = 101; y < 612; y++) {
            for (int x = 16; x < 184; x++) {
                int before = first.getPixel(x, y);
                int after = second.getPixel(x, y);
                if (x >= 51 && x <= 149) {
                    assertEquals("Ambient energy must not move the tape or digits", before, after);
                } else if (before != after) {
                    changedEnergyPixels++;
                }
            }
        }
        assertTrue("Energy channels should visibly flow", changedEnergyPixels > 100);
        first.recycle();
        second.recycle();
    }

    @Test
    public void zeroRevealProducesNoVisibleGeometryIncludingEnergyChannels() {
        RollingTapeGauge gauge = createGauge(false);
        Bitmap bitmap = Bitmap.createBitmap(200, 720, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        gauge.draw(canvas, 86.0f, 40.0f, 0.0f, 2.5f);
        gauge.drawFixedIndex(canvas, 1.4f, 0.0f);
        int[] pixels = new int[200 * 720];
        bitmap.getPixels(pixels, 0, 200, 0, 0, 200, 720);
        for (int pixel : pixels) {
            assertEquals(0, Color.alpha(pixel));
        }
        bitmap.recycle();
    }

    private static RollingTapeGauge createGauge(boolean rpm) {
        return new RollingTapeGauge(100.0f, 115.0f, 598.0f,
                0.0f, rpm ? 8000.0f : 220.0f,
                rpm ? 0.10f : 3.6f, rpm ? 100.0f : 2.0f,
                rpm ? 500.0f : 10.0f, rpm ? 1000.0f : 20.0f, 0,
                Typeface.create("sans-serif-condensed", Typeface.NORMAL));
    }
}
