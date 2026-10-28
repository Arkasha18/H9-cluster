package net.adminrunet.h9cluster.skins.ionaurora;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class IonAuroraMainValueSpacingTest {
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 720;

    @Test
    public void everyDigitOutlineAndUnavailableValueLeaveAFullPixelAroundTheFrames()
            throws Exception {
        IonAuroraClusterView view = new IonAuroraClusterView(
                RuntimeEnvironment.getApplication(), false);
        Method drawValues = IonAuroraClusterView.class.getDeclaredMethod(
                "drawMainValues", Canvas.class, float.class);
        drawValues.setAccessible(true);
        Method drawLabels = IonAuroraClusterView.class.getDeclaredMethod(
                "drawStaticLabels", Canvas.class);
        drawLabels.setAccessible(true);
        Field speedText = IonAuroraClusterView.class.getDeclaredField("cachedSpeedText");
        speedText.setAccessible(true);
        Field rpmText = IonAuroraClusterView.class.getDeclaredField("cachedRpmText");
        rpmText.setAccessible(true);

        Bitmap chrome = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        IonAuroraChrome.draw(new Canvas(chrome));
        int[] chromePixels = pixels(chrome);
        Bitmap labels = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        drawLabels.invoke(view, new Canvas(labels));
        int[] labelPixels = pixels(labels);
        // Only the two main unit labels occur in these rows; no clipping affects the renderer.
        for (int index = 0; index < labelPixels.length; index++) {
            int x = index % WIDTH;
            int y = index / WIDTH;
            if (y < 350 || y >= 401 || !inMainColumn(x)) labelPixels[index] = 0;
        }
        assertSeparatedFromChrome("units", labelPixels, chromePixels);
        int[][] unitBounds = boundsBySide(labelPixels);

        Bitmap values = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas valueCanvas = new Canvas(values);
        String[][] samples = {
                {"0", "0"}, {"86", "2400"}, {"220", "8000"}, {"--", "--"},
                // Repetition covers every glyph and the widest 3/4-digit combinations,
                // a geometric superset of speed 0..220 and engine speed 0..8000.
                {"000", "0000"}, {"111", "1111"}, {"222", "2222"},
                {"333", "3333"}, {"444", "4444"}, {"555", "5555"},
                {"666", "6666"}, {"777", "7777"}, {"888", "8888"},
                {"999", "9999"}
        };
        for (String[] sample : samples) {
            values.eraseColor(Color.TRANSPARENT);
            speedText.set(view, sample[0]);
            rpmText.set(view, sample[1]);
            drawValues.invoke(view, valueCanvas, 1.0f);
            int[] valuePixels = pixels(values);
            String description = sample[0] + "/" + sample[1];
            int[][] valueBounds = boundsBySide(valuePixels);
            for (int side = 0; side < 2; side++) {
                int[] number = valueBounds[side];
                int[] unit = unitBounds[side];
                System.out.println("MAIN_VALUE_SPACING " + description + " side=" + side
                        + " numeral=[" + number[0] + "," + number[1] + ","
                        + number[2] + "," + number[3] + "] units=["
                        + unit[0] + "," + unit[1] + "," + unit[2] + "," + unit[3] + "]");
                assertTrue(description + " must leave one complete row between numeral and unit",
                        unit[1] - number[3] >= 2);
            }
            assertSeparatedFromChrome(description + " including real glow", valuePixels, chromePixels);
        }
        values.recycle();
        labels.recycle();
        chrome.recycle();
    }

    private static void assertSeparatedFromChrome(String description, int[] foreground, int[] chrome) {
        int visible = 0;
        for (int index = 0; index < foreground.length; index++) {
            if ((foreground[index] >>> 24) == 0) continue;
            visible++;
            int x = index % WIDTH;
            int y = index / WIDTH;
            assertTrue(description + " must stay inside the intended central readout gap at "
                            + x + "," + y,
                    inMainColumn(x) && y >= 280 && y < 395);
            // Touching pixels (distance one) have no free pixel between them. Requiring
            // all eight neighbors to be transparent proves at least one complete pixel.
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int frameX = x + dx;
                    int frameY = y + dy;
                    if ((chrome[frameY * WIDTH + frameX] >>> 24) != 0) {
                        fail(description + " touches a frame near " + x + "," + y
                                + "; neighboring frame pixel=" + frameX + "," + frameY);
                    }
                }
            }
        }
        assertTrue(description + " must actually render visible pixels", visible > 100);
    }

    private static boolean inMainColumn(int x) {
        return (x >= 120 && x < 560) || (x >= 1360 && x < 1830);
    }

    private static int[][] boundsBySide(int[] pixels) {
        int[][] bounds = {{WIDTH, HEIGHT, -1, -1}, {WIDTH, HEIGHT, -1, -1}};
        for (int index = 0; index < pixels.length; index++) {
            if ((pixels[index] >>> 24) == 0) continue;
            int x = index % WIDTH;
            int y = index / WIDTH;
            int[] side = bounds[x < WIDTH / 2 ? 0 : 1];
            side[0] = Math.min(side[0], x);
            side[1] = Math.min(side[1], y);
            side[2] = Math.max(side[2], x);
            side[3] = Math.max(side[3], y);
        }
        assertTrue("Left readout must be present", bounds[0][2] >= bounds[0][0]);
        assertTrue("Right readout must be present", bounds[1][2] >= bounds[1][0]);
        return bounds;
    }

    private static int[] pixels(Bitmap bitmap) {
        int[] result = new int[WIDTH * HEIGHT];
        bitmap.getPixels(result, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
        return result;
    }
}
