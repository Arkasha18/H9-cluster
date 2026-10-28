package net.adminrunet.h9cluster.skins.apexsport;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.skins.SpeedDisplayMode;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;

/** Native compositor regression for factory text visible through the bottom shelf. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class ApexSportBackdropOpacityTest {
    private static final int W = 1920;
    private static final int H = 720;
    private static final long WALL_TIME = Instant.parse("2026-10-28T14:32:00Z").toEpochMilli();
    private static final String BASE = "docs/H9_Cluster_Neutral_Design_Template_1920x720/"
            + "04_yandex_map_black_gradient_rgba.png";
    private TimeZone originalZone;

    @Before
    public void freezeClocks() {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        ShadowSystemClock.advanceBy(Duration.ofSeconds(60));
    }

    @After
    public void restoreTimeZone() {
        TimeZone.setDefault(originalZone);
    }

    @Test
    public void everyUnpaintedPixelKeepsTheOriginalCalibratedBackdrop() {
        Bitmap original = decodeBase();
        for (float distance : new float[]{0f, 9999.9f}) {
            Bitmap complete = render(true, distance);
            Bitmap foreground = render(false, distance);
            int unpainted = 0;
            int bottomUnpainted = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                if (Color.alpha(foreground.getPixel(x, y)) != 0) continue;
                int expected = original.getPixel(x, y);
                int actual = complete.getPixel(x, y);
                if (expected != actual) {
                    fail("Calibrated backdrop changed at " + x + "," + y
                            + " for distance " + distance + ": expected "
                            + Integer.toHexString(expected) + " but was " + Integer.toHexString(actual));
                }
                unpainted++;
                if (y >= 647) bottomUnpainted++;
            }
            assertTrue("The comparison must cover the transparent map and unpainted fields",
                    unpainted > W * H / 2);
            assertTrue("The bottom calibration is explicitly exercised", bottomUnpainted > W);
            complete.recycle();
            foreground.recycle();
        }
        original.recycle();
    }

    @Test
    public void factoryTextBetweenDayAndTripCannotBleedThroughTheOriginalShelf() throws Exception {
        Bitmap plain = lowerLayer(false);
        Bitmap stress = lowerLayer(true);
        int injectedPixels = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            if (plain.getPixel(x, y) == stress.getPixel(x, y)) continue;
            assertTrue("Only the known factory-text area is altered by this stress fixture",
                    x >= 440 && x < 488 && y >= 674 && y < 696);
            injectedPixels++;
        }
        assertTrue("The lower layers must actually differ before compositing", injectedPixels > 500);

        for (float distance : new float[]{0f, 9999.9f}) {
            Bitmap complete = render(true, distance);
            Bitmap foreground = render(false, distance);
            Bitmap plainComposite = composite(plain, complete);
            Bitmap stressComposite = composite(stress, complete);
            // Row 647 and the gradient from x=489 remain calibrated antialiasing;
            // this check deliberately covers only the source's opaque interior.
            for (int y = 648; y < H; y++) for (int x = 400; x < 489; x++) {
                assertEquals("Opaque original bottom shelf at " + x + "," + y,
                        255, Color.alpha(complete.getPixel(x, y)));
                assertEquals("Synthetic factory text must be occluded at " + x + "," + y,
                        plainComposite.getPixel(x, y), stressComposite.getPixel(x, y));
            }
            // Occlusion belongs to the calibrated backdrop, not to an extra
            // rectangle painted between the newly spaced DAY/TRIP rows.
            for (int y = 677; y < 685; y++) for (int x = 440; x < 460; x++) {
                assertEquals("No foreground patch between DAY and TRIP at " + x + "," + y,
                        0, Color.alpha(foreground.getPixel(x, y)));
            }
            if (distance == 9999.9f) writeStressReview(stress, stressComposite);
            complete.recycle();
            foreground.recycle();
            plainComposite.recycle();
            stressComposite.recycle();
        }
        plain.recycle();
        stress.recycle();
    }

    private static Bitmap render(boolean backdrop, float distance) {
        ApexSportClusterView view = new ApexSportClusterView(RuntimeEnvironment.getApplication(),
                SpeedDisplayMode.REAL, backdrop, true, () -> WALL_TIME);
        view.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, W, H);
        view.setClusterState(snapshot(distance));
        Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static ClusterState snapshot(float distance) {
        long now = SystemClock.elapsedRealtime();
        return new ClusterState(0, 750, 0, "P", 88, 76, 54, 540,
                9999999, distance, distance, 2.35f, 2.36f, 2.32f, 2.34f,
                .8f, 9.7f, 9.7f, 14.2f, 12.5f, 0,
                0, 0, 0, 0, 0, now, now, now, now, now, "ECO");
    }

    private static Bitmap decodeBase() {
        File file = new File(BASE);
        if (!file.isFile()) file = new File("../" + BASE);
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        assertNotNull("Original calibrated backdrop", bitmap);
        assertEquals(W, bitmap.getWidth());
        assertEquals(H, bitmap.getHeight());
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        return bitmap;
    }

    private static Bitmap lowerLayer(boolean injectedText) {
        Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFF123047);
        if (injectedText) {
            int saved = canvas.save();
            canvas.clipRect(440, 674, 488, 696);
            Paint paint = new Paint();
            for (int x = 440; x < 488; x += 4) {
                paint.setColor((x - 440) % 8 == 0 ? Color.MAGENTA : Color.YELLOW);
                canvas.drawRect(x, 674, x + 4, 696, paint);
            }
            paint.setAntiAlias(true);
            paint.setColor(Color.WHITE);
            paint.setTextSize(24);
            canvas.drawText("2 km", 440, 694, paint);
            canvas.restoreToCount(saved);
        }
        return bitmap;
    }

    private static Bitmap composite(Bitmap lower, Bitmap viewLayer) {
        Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(lower, 0, 0, null);
        canvas.drawBitmap(viewLayer, 0, 0, null);
        return bitmap;
    }

    /** A labelled synthetic compositor stress fixture, never a car screenshot. */
    private static void writeStressReview(Bitmap lower, Bitmap composite) throws Exception {
        Bitmap review = Bitmap.createBitmap(1120, 116, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(review);
        canvas.drawColor(Color.BLACK);
        Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
        caption.setColor(Color.WHITE);
        caption.setTextSize(16);
        canvas.drawText("STRESS FIXTURE: synthetic lower layer", 8, 20, caption);
        canvas.drawText("Native View composite: injected text hidden", 568, 20, caption);
        Rect source = new Rect(0, 642, 560, 720);
        canvas.drawBitmap(lower, source, new Rect(0, 30, 560, 108), null);
        canvas.drawBitmap(composite, source, new Rect(560, 30, 1120, 108), null);
        File file = new File("build/reports/apexsport/native-bottom-occlusion.png");
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(review.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        review.recycle();
    }
}
