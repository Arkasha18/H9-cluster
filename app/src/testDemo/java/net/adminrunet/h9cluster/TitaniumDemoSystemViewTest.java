package net.adminrunet.h9cluster;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import java.io.File;
import java.time.Duration;
import net.adminrunet.h9cluster.skins.SkinRegistry;
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
public final class TitaniumDemoSystemViewTest {
    @Test
    public void factoryCaptionsAndLampsStayInsideTheirOwnSystemFields() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(60));
        View overlay = PreviewSystemIcons.create(RuntimeEnvironment.getApplication(), SkinRegistry.SCULPTED_TITANIUM);
        assertNotNull(overlay);
        PreviewSystemIcons.update(overlay, new DemoScenario().snapshot(9000, SystemClock.elapsedRealtime()));
        overlay.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        overlay.layout(0, 0, 1920, 720);
        Bitmap frame = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        overlay.draw(new Canvas(frame));
        String relative = "docs/H9_Cluster_Neutral_Design_Template_1920x720/03_system_icons_forbidden_mask.png";
        File file = new File(relative);
        if (!file.isFile()) file = new File("../" + relative);
        Bitmap mask = BitmapFactory.decodeFile(file.getAbsolutePath());
        assertNotNull(mask);
        int[] pixels = new int[1920 * 720], maskPixels = new int[pixels.length];
        frame.getPixels(pixels, 0, 1920, 0, 0, 1920, 720);
        mask.getPixels(maskPixels, 0, 1920, 0, 0, 1920, 720);
        int selectorPixels = 0, modePixels = 0;
        for (int i = 0; i < pixels.length; i++) {
            if ((pixels[i] >>> 24) == 0) continue;
            int x = i % 1920, y = i / 1920;
            assertTrue("System overlay left its field at " + x + "," + y, (maskPixels[i] >>> 24) != 0);
            if (x >= 923 && x <= 1045 && y >= 20 && y <= 76) selectorPixels++;
            if (x >= 1428 && x <= 1526 && y >= 668 && y <= 697) modePixels++;
        }
        assertTrue("Demo must show the factory selector above the skin", selectorPixels > 100);
        assertTrue("Demo must show the factory drive mode above the lower-right saddle", modePixels > 50);
        frame.recycle(); mask.recycle();
    }
}
