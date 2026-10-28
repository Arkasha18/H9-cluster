package net.adminrunet.h9cluster.skins.sculptedtitanium;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.GearSelector;
import net.adminrunet.h9cluster.skins.SkinRegistry;
import net.adminrunet.h9cluster.skins.SkinSettings;
import net.adminrunet.h9cluster.skins.SpeedDisplayMode;
import java.io.File;
import java.io.FileOutputStream;
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
public final class SculptedTitaniumClusterViewTest {
    @Test
    public void liveStatesRespectLowerLeftReservationsAndTheWideMapCorridor() throws Exception {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(60));
        long now = SystemClock.elapsedRealtime();
        Bitmap mask = BitmapFactory.decodeFile(maskFile().getAbsolutePath());
        assertNotNull(mask);
        int[] maskPixels = new int[1920 * 720];
        mask.getPixels(maskPixels, 0, 1920, 0, 0, 1920, 720);
        int protectedPixels = 0;
        for (int y = 0; y < 720; y++) for (int x = 0; x < 1920; x++) {
            if (lowerLeft(x, y) && (maskPixels[y * 1920 + x] >>> 24) != 0) protectedPixels++;
        }
        assertEquals(40651, protectedPixels);
        for (int scenario = 0; scenario < 3; scenario++) {
            SculptedTitaniumClusterView view = new SculptedTitaniumClusterView(RuntimeEnvironment.getApplication(), false);
            layout(view);
            view.setClusterState(fixture(now, scenario));
            Bitmap frame = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(frame));
            int[] pixels = new int[1920 * 720];
            frame.getPixels(pixels, 0, 1920, 0, 0, 1920, 720);
            int chromeUnderFactoryLamps = 0;
            for (int y = 0; y < 720; y++) for (int x = 0; x < 1920; x++) {
                int index = y * 1920 + x;
                if ((pixels[index] >>> 24) == 0) continue;
                if (x >= 560 && x < 1360 && y >= 80) fail("Map corridor painted at " + x + "," + y);
                if (lowerLeft(x, y) && (maskPixels[index] >>> 24) != 0) fail("Lower-left mask painted at " + x + "," + y);
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= 1920 || ny < 0 || ny >= 720) continue;
                    if (lowerLeft(nx, ny) && (maskPixels[ny * 1920 + nx] >>> 24) != 0) {
                        fail("Lower-left clearance violated at " + x + "," + y);
                    }
                }
                if ((maskPixels[index] >>> 24) != 0 && !lowerLeft(x, y)) chromeUnderFactoryLamps++;
            }
            assertTrue("Approved artwork must pass below the factory layer", chromeUnderFactoryLamps > 100000);
            if (scenario == 1) {
                File report = new File("build/reports/sculptedtitanium/live-render.png");
                assertTrue(report.getParentFile().isDirectory() || report.getParentFile().mkdirs());
                try (FileOutputStream out = new FileOutputStream(report)) { assertTrue(frame.compress(Bitmap.CompressFormat.PNG, 100, out)); }
            }
            frame.recycle();
        }
        mask.recycle();
    }

    @Test
    public void registryPropagatesTheNewSpeedSettingToBothDigitsAndGauge() throws Exception {
        java.lang.reflect.Field text = SculptedTitaniumClusterView.class.getDeclaredField("speed");
        java.lang.reflect.Field gauge = SculptedTitaniumClusterView.class.getDeclaredField("indicatedSpeed");
        text.setAccessible(true);
        gauge.setAccessible(true);
        int[] expected = {86, 90, 91};
        int index = 0;
        for (SpeedDisplayMode mode : SpeedDisplayMode.values()) {
            SculptedTitaniumClusterView view = (SculptedTitaniumClusterView) SkinRegistry.createRenderer(
                    RuntimeEnvironment.getApplication(), SkinRegistry.SCULPTED_TITANIUM,
                    SkinSettings.empty(), mode);
            ClusterState measured = fixture(60000, 1);
            view.setClusterState(measured);
            assertEquals(Integer.toString(expected[index]), text.get(view));
            assertEquals(expected[index], gauge.getFloat(view), 0);
            assertEquals(86, measured.speedKph);
            view.setClusterState(fixture(60000, 0));
            assertEquals("0", text.get(view));
            assertEquals(0, gauge.getFloat(view), 0);
            index++;
        }
    }

    @Test
    public void unavailableValuesAndIdleFuelUnitsRemainHonest() {
        assertEquals("—", SculptedTitaniumClusterView.decimal(Float.NaN, 1));
        assertEquals("—", SculptedTitaniumClusterView.decimal(Float.POSITIVE_INFINITY, 1));
        assertEquals("—", SculptedTitaniumClusterView.decimal(-1, 2));
        assertEquals("2.40", SculptedTitaniumClusterView.decimal(2.4f, 2));
        assertEquals("л/ч", SculptedTitaniumClusterView.instantUnit(0));
        assertEquals("л/ч", SculptedTitaniumClusterView.instantUnit(1));
        assertEquals("л/100 км", SculptedTitaniumClusterView.instantUnit(2));
        assertEquals("78 °C", SculptedTitaniumClusterView.formatAtf(fixture(60000, 1), 61000));
        assertEquals("— °C", SculptedTitaniumClusterView.formatAtf(fixture(60000, 1), 76000));
        assertEquals("— °C", SculptedTitaniumClusterView.formatAtf(fixture(60000, 1), 59000));
    }

    @Test
    public void onlyAutomaticRatiosSupplementTheFactorySelector() {
        for (int ratio = 1; ratio <= 8; ratio++) assertEquals(Integer.toString(ratio), SculptedTitaniumClusterView.currentGearLabel("D", ratio));
        for (String selector : new String[]{"P", "N", "R", "M", "", null}) {
            assertEquals("", SculptedTitaniumClusterView.currentGearLabel(selector, 4));
        }
        assertEquals("", SculptedTitaniumClusterView.currentGearLabel("D", 0));
        assertEquals("", SculptedTitaniumClusterView.currentGearLabel("D", 9));
    }

    private static ClusterState fixture(long now, int scenario) {
        int speed = scenario == 0 ? 0 : scenario == 1 ? 86 : 220;
        return new ClusterState(speed, scenario == 0 ? 800 : scenario == 1 ? 2700 : 8000,
                4, GearSelector.DRIVE, 88, 78, 47, 486, scenario == 2 ? 9999999 : 28644,
                scenario == 2 ? 9999.9f : 44, scenario == 2 ? 9999.9f : 169.5f,
                scenario == 2 ? 1.2f : 2.40f, 2.36f, 2.37f, 2.37f,
                scenario == 2 ? Float.NaN : 15.8f, 14.8f, 12.3f, 13.8f,
                scenario == 2 ? -39.5f : 18.8f, scenario == 2 ? -1080 : 12,
                speed, speed + .2f, speed + .1f, speed + .3f, scenario == 2 ? -999 : 197,
                now, now, now, now, scenario == 2 ? now - 16000 : now, "ECO");
    }

    private static boolean lowerLeft(int x, int y) {
        return (x >= 387 && x <= 497 && y >= 373 && y <= 441)
                || (x >= 230 && x <= 547 && y >= 494 && y <= 641);
    }
    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1920, 720);
    }
    private static File maskFile() {
        String relative = "docs/H9_Cluster_Neutral_Design_Template_1920x720/03_system_icons_forbidden_mask.png";
        File file = new File(relative);
        return file.isFile() ? file : new File("../" + relative);
    }
}
