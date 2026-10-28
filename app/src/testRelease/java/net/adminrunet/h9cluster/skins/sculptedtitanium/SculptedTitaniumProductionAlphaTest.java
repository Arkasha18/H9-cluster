package net.adminrunet.h9cluster.skins.sculptedtitanium;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import net.adminrunet.h9cluster.BuildConfig;
import net.adminrunet.h9cluster.ClusterState;
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
public final class SculptedTitaniumProductionAlphaTest {
    @Test
    public void factorySelectorAndDriveModeNeverBecomeSkinPixels() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(60));
        long now = SystemClock.elapsedRealtime();
        SculptedTitaniumClusterView view = new SculptedTitaniumClusterView(RuntimeEnvironment.getApplication());
        view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1920, 720);

        int[] baseline = render(view, state("P", 0, "ECO", now));
        for (String selector : new String[]{"P", "R", "N", "M", ""}) {
            assertArrayEquals("Factory selector and drive mode must not be drawn by the skin",
                    baseline, render(view, state(selector, 4, "SPORT", now)));
        }

        int[] drive = render(view, state("D", 8, "NORMAL", now));
        int changed = 0;
        for (int y = 0; y < 720; y++) for (int x = 0; x < 1920; x++) {
            if (baseline[y * 1920 + x] == drive[y * 1920 + x]) continue;
            assertTrue("Only the authorized ratio may change at " + x + "," + y,
                    x >= 982 && x < 1018 && y >= 28 && y < 66);
            changed++;
        }
        assertTrue("The real automatic gear ratio must remain visible", changed > 0);
    }

    @Test
    public void fullProductionCompositionPreservesMapAlphaAndLowerLeftBase() throws Exception {
        assertFalse(BuildConfig.DEMO_MODE);
        SculptedTitaniumClusterView view = new SculptedTitaniumClusterView(RuntimeEnvironment.getApplication());
        view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1920, 720);
        Bitmap actual = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(actual));
        for (int y = 80; y < 720; y++) for (int x = 672; x < 1096; x++) {
            assertEquals("Map alpha at " + x + "," + y, 0, actual.getPixel(x, y) >>> 24);
        }
        int above = actual.getPixel(800, 78) >>> 24;
        int below = actual.getPixel(800, 80) >>> 24;
        assertTrue("Map header must feather before the clear aperture", above < 32);
        assertEquals(0, below);
        Bitmap base;
        try (java.io.InputStream stream = RuntimeEnvironment.getApplication().getAssets().open(
                "dashboard/skins/sculptedtitanium/map_black_gradient.png")) {
            base = BitmapFactory.decodeStream(stream);
        }
        assertNotNull(base);
        for (int y = 375; y <= 439; y++) for (int x = 389; x <= 495; x++) {
            assertEquals("Reserved field must keep base alpha", base.getPixel(x, y) >>> 24, actual.getPixel(x, y) >>> 24);
        }
        actual.recycle(); base.recycle();
    }

    private static int[] render(SculptedTitaniumClusterView view, ClusterState snapshot) {
        view.setClusterState(snapshot);
        Bitmap frame = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(frame));
        int[] pixels = new int[1920 * 720];
        frame.getPixels(pixels, 0, 1920, 0, 0, 1920, 720);
        frame.recycle();
        return pixels;
    }

    private static ClusterState state(String selector, int ratio, String driveMode, long now) {
        return new ClusterState(86, 2700, ratio, selector, 88, 78, 47, 486,
                28644, 44, 169.5f, 2.40f, 2.36f, 2.37f, 2.37f,
                15.8f, 14.8f, 12.3f, 13.8f, 18.8f, 12,
                86, 86.2f, 86.1f, 86.3f, 197, now, now, now, now, now, driveMode);
    }
}
