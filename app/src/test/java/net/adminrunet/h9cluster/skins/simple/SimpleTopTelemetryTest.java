package net.adminrunet.h9cluster.skins.simple;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import java.lang.reflect.Field;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class SimpleTopTelemetryTest {
    @Test
    public void tyreTelemetryAndWifiOccupyOppositeTopCorners() {
        Context context = RuntimeEnvironment.getApplication();
        SimpleClusterView view = new SimpleClusterView(
                context,
                SimpleScaleColor.NONE);
        int width = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY);
        view.measure(width, height);
        view.layout(0, 0, 1920, 720);

        Bitmap frame = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(frame));

        assertTrue("tyre telemetry must draw in the top-left corner",
                opaquePixelCount(frame, 0, 0, 220, 90) > 100);
        assertTrue("Wi-Fi must draw in the top-right corner",
                opaquePixelCount(frame, 1800, 0, 1920, 90) > 40);
    }

    @Test
    public void noScalesViewDoesNotAllocateAStaticBitmap() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        SimpleClusterView view = new SimpleClusterView(
                context,
                SimpleScaleColor.NONE);
        int width = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY);
        view.measure(width, height);
        view.layout(0, 0, 1920, 720);

        Field background = SimpleClusterView.class.getDeclaredField("staticBackground");
        background.setAccessible(true);
        assertNull(background.get(view));
    }

    private static int opaquePixelCount(
            Bitmap frame,
            int left,
            int top,
            int right,
            int bottom) {
        int count = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                if (Color.alpha(frame.getPixel(x, y)) != 0) {
                    count++;
                }
            }
        }
        return count;
    }
}
