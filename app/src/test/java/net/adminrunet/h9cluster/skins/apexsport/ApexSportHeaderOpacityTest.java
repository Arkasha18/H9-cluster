package net.adminrunet.h9cluster.skins.apexsport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import net.adminrunet.h9cluster.skins.SpeedDisplayMode;

import java.time.Instant;
import java.util.TimeZone;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Regression for lower-layer fragments observed next to the clock on the car. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class ApexSportHeaderOpacityTest {
    @Test
    public void calibratedHeaderHidesUnderlyingTextWithoutAddingForegroundPatches() {
        TimeZone originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            for (String time : new String[]{"00:00", "11:11", "16:16", "23:59"}) {
                long millis = Instant.parse("2026-10-28T" + time + ":00Z").toEpochMilli();
                Bitmap full = render(true, millis);
                Bitmap foreground = render(false, millis);
                Bitmap plain = lowerLayer(false);
                Bitmap marked = lowerLayer(true);
                assertNotEquals("The lower test layer must actually contain bright marks",
                        plain.getPixel(1065, 65), marked.getPixel(1065, 65));
                new Canvas(plain).drawBitmap(full, 0, 0, null);
                new Canvas(marked).drawBitmap(full, 0, 0, null);
                for (int y = 0; y < 80; y++) for (int x = 1049; x < 1220; x++) {
                    assertEquals("Underlying text leaks through header at " + x + "," + y,
                            plain.getPixel(x, y), marked.getPixel(x, y));
                    // Row79 is the source image's antialiased edge (alpha128).
                    if (y < 79) assertEquals("Calibrated header must stay opaque", 255,
                            Color.alpha(full.getPixel(x, y)));
                }
                // This area is left of even the widest clock string. Its opacity
                // belongs to the calibrated backdrop, never a fake foreground mask.
                for (int y = 18; y < 80; y++) for (int x = 1049; x < 1100; x++) {
                    assertEquals("No foreground patch next to the clock", 0,
                            Color.alpha(foreground.getPixel(x, y)));
                }
                full.recycle();
                foreground.recycle();
                plain.recycle();
                marked.recycle();
            }
        } finally {
            TimeZone.setDefault(originalZone);
        }
    }

    private static Bitmap render(boolean backdrop, long millis) {
        ApexSportClusterView view = new ApexSportClusterView(
                RuntimeEnvironment.getApplication(), SpeedDisplayMode.REAL,
                backdrop, true, () -> millis);
        view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1920, 720);
        Bitmap bitmap = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static Bitmap lowerLayer(boolean withMarks) {
        Bitmap bitmap = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFF263849);
        if (withMarks) {
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            canvas.drawRect(1060, 35, 1069, 75, paint);
            paint.setColor(Color.GREEN);
            canvas.drawRect(1090, 35, 1100, 75, paint);
            canvas.drawRect(1110, 35, 1120, 75, paint);
        }
        return bitmap;
    }
}
