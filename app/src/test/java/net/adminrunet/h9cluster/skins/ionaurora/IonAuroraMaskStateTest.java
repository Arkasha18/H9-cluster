package net.adminrunet.h9cluster.skins.ionaurora;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;

import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.GearSelector;

import java.io.File;
import java.io.IOException;
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
public final class IonAuroraMaskStateTest {
    @Test
    public void idleReadoutsNeverPaintUnderSystemIcons() throws IOException {
        long nowMs = freshClock();
        assertForegroundClear("idle 0 km/h, 800 rpm",
                fixture(nowMs, 0, 800, 18.5f, 0.0f, false));
    }

    @Test
    public void maximumReadoutsNeverPaintUnderSystemIcons() throws IOException {
        long nowMs = freshClock();
        assertForegroundClear("maximum 220 km/h, 8000 rpm",
                fixture(nowMs, 220, 8000, 18.5f, 0.0f, false));
    }

    @Test
    public void staleAtfWarningsAndSignedSteeringExtremesStayOutsideSystemIcons()
            throws IOException {
        long nowMs = freshClock();
        assertForegroundClear("stale ATF, low fuel/pressure/voltage, -780 steering",
                fixture(nowMs, 86, 2400, -39.5f, -780.0f, true));
        nowMs = freshClock();
        assertForegroundClear("stale ATF, low fuel/pressure/voltage, +780 steering",
                fixture(nowMs, 86, 2400, -39.5f, 780.0f, true));
    }

    private static void assertForegroundClear(String scenario, ClusterState state)
            throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        IonAuroraClusterView view = new IonAuroraClusterView(context, false);
        view.setClusterState(state);
        view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1920, 720);

        Bitmap foreground = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(foreground);
        view.draw(canvas);
        ShadowSystemClock.advanceBy(Duration.ofMillis(1200L));
        view.draw(canvas);

        Bitmap forbidden = BitmapFactory.decodeFile(locateMask().getAbsolutePath());
        assertNotNull(forbidden);
        assertEquals(1920, forbidden.getWidth());
        assertEquals(720, forbidden.getHeight());
        int[] actualPixels = new int[1920 * 720];
        int[] maskPixels = new int[actualPixels.length];
        foreground.getPixels(actualPixels, 0, 1920, 0, 0, 1920, 720);
        forbidden.getPixels(maskPixels, 0, 1920, 0, 0, 1920, 720);
        int protectedPixels = 0;
        int visibleForegroundPixels = 0;
        for (int index = 0; index < actualPixels.length; index++) {
            int foregroundAlpha = actualPixels[index] >>> 24;
            if ((maskPixels[index] >>> 24) != 0) {
                protectedPixels++;
                int x = index % 1920;
                int y = index / 1920;
                boolean allowedGearDigit = x >= 982 && x < 1018
                        && y >= 28 && y < 66;
                if (foregroundAlpha != 0 && !allowedGearDigit) {
                    fail(scenario + ": foreground alpha=" + foregroundAlpha
                            + " under mask03 at " + (index % 1920) + ","
                            + (index / 1920));
                }
            } else if (foregroundAlpha != 0) {
                visibleForegroundPixels++;
            }
        }
        assertTrue("The complete mask03 must be loaded", protectedPixels > 1000);
        assertTrue("The check must render visible gauges, chrome and readouts",
                visibleForegroundPixels > 10000);
        foreground.recycle();
        forbidden.recycle();
    }

    private static long freshClock() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(60));
        return SystemClock.elapsedRealtime();
    }

    private static ClusterState fixture(long nowMs, int speed, int rpm,
            float outsideTemperature, float steeringAngle, boolean warningsAndStaleAtf) {
        return new ClusterState(
                speed,
                rpm,
                5,
                GearSelector.DRIVE,
                warningsAndStaleAtf ? 122 : 92,
                warningsAndStaleAtf ? 138.0f : 78.0f,
                warningsAndStaleAtf ? 4.0f : 47.0f,
                warningsAndStaleAtf ? 19 : 421,
                28_642.0,
                42.3f,
                167.8f,
                warningsAndStaleAtf ? 1.35f : 2.35f,
                2.37f,
                2.42f,
                2.40f,
                speed <= 1 ? 1.3f : 12.6f,
                14.8f,
                14.8f,
                warningsAndStaleAtf ? 11.2f : 13.8f,
                outsideTemperature,
                steeringAngle,
                speed,
                speed,
                speed,
                speed,
                warningsAndStaleAtf ? -124.0f : 224.0f,
                nowMs,
                nowMs,
                nowMs,
                nowMs,
                warningsAndStaleAtf ? nowMs - 16000L : nowMs,
                "NORMAL");
    }

    private static File locateMask() throws IOException {
        String relative = "docs/H9_Cluster_Neutral_Design_Template_1920x720/"
                + "03_system_icons_forbidden_mask.png";
        File fromRoot = new File(relative);
        if (fromRoot.isFile()) {
            return fromRoot;
        }
        File fromModule = new File("../" + relative);
        if (fromModule.isFile()) {
            return fromModule;
        }
        throw new IOException("Cannot locate technical control layer mask03");
    }
}
