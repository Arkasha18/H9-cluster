package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;

import net.adminrunet.h9cluster.skins.SkinRegistry;
import net.adminrunet.h9cluster.skins.ionaurora.IonAuroraClusterView;

import java.io.File;
import java.io.FileOutputStream;
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
public final class DemoSystemIconsViewTest {
    @Test
    public void factoryProvidesSystemIconsOnlyForIonAurora() {
        Context context = RuntimeEnvironment.getApplication();
        assertNotNull(PreviewSystemIcons.create(context, SkinRegistry.ION_AURORA));
        assertNull(PreviewSystemIcons.create(context, SkinRegistry.CLASSIC));
    }

    @Test
    public void systemIconsStayInsideMaskAndCompleteTheLiveComposition()
            throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        ShadowSystemClock.advanceBy(Duration.ofMillis(1L));
        long nowMs = SystemClock.elapsedRealtime();
        ClusterState state = new DemoScenario().snapshot(9000L, nowMs);
        View overlay = PreviewSystemIcons.create(context, SkinRegistry.ION_AURORA);
        assertNotNull(overlay);
        PreviewSystemIcons.update(overlay, state);
        layoutScreen(overlay);
        Bitmap overlayFrame = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        overlay.draw(new Canvas(overlayFrame));

        IonAuroraClusterView skin = new IonAuroraClusterView(context);
        skin.setClusterState(state);
        layoutScreen(skin);
        Bitmap composition = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        Canvas compositionCanvas = new Canvas(composition);
        skin.draw(compositionCanvas);
        ShadowSystemClock.advanceBy(Duration.ofMillis(1200L));
        skin.draw(compositionCanvas);
        overlay.draw(compositionCanvas);
        exportPng(composition,
                new File("build/reports/ionaurora/demo_with_system_icons.png"));

        Bitmap mask = BitmapFactory.decodeFile(locateMask().getAbsolutePath());
        assertNotNull(mask);
        assertEquals(1920, mask.getWidth());
        assertEquals(720, mask.getHeight());
        int[] overlayPixels = new int[1920 * 720];
        int[] maskPixels = new int[overlayPixels.length];
        overlayFrame.getPixels(overlayPixels, 0, 1920, 0, 0, 1920, 720);
        mask.getPixels(maskPixels, 0, 1920, 0, 0, 1920, 720);
        int visiblePixels = 0;
        for (int index = 0; index < overlayPixels.length; index++) {
            if ((overlayPixels[index] >>> 24) == 0) continue;
            visiblePixels++;
            if ((maskPixels[index] >>> 24) == 0) {
                fail("Demo system icon outside mask03 at " + (index % 1920)
                        + "," + (index / 1920));
            }
        }
        assertTrue("The overlay must contain visible system lamps", visiblePixels > 2000);
        assertEquals("The imitation layer must not draw any system gear caption",
                0, countVisible(overlayFrame, 921, 18, 1048, 79));
        assertEquals("The imitation layer must not draw driveMode",
                0, countVisible(overlayFrame, 1426, 666, 1529, 700));
        assertTrue("The composed skin must show its own automatic gear numeral",
                countBright(composition, 982, 28, 1018, 66) > 30);
        mask.recycle();
        overlayFrame.recycle();
        composition.recycle();
    }

    private static void layoutScreen(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1920, 720);
    }

    private static int countVisible(Bitmap bitmap, int left, int top, int right, int bottom) {
        int visible = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                if ((bitmap.getPixel(x, y) >>> 24) != 0) visible++;
            }
        }
        return visible;
    }

    private static int countBright(Bitmap bitmap, int left, int top, int right, int bottom) {
        int bright = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int pixel = bitmap.getPixel(x, y);
                if (((pixel >>> 16) & 255) > 180
                        && ((pixel >>> 8) & 255) > 180 && (pixel & 255) > 180) {
                    bright++;
                }
            }
        }
        return bright;
    }

    private static File locateMask() throws IOException {
        String relative = "docs/H9_Cluster_Neutral_Design_Template_1920x720/"
                + "03_system_icons_forbidden_mask.png";
        File file = new File(relative);
        if (file.isFile()) return file;
        file = new File("../" + relative);
        if (file.isFile()) return file;
        throw new IOException("Cannot locate technical control layer mask03");
    }

    private static void exportPng(Bitmap bitmap, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create QA directory: " + parent);
        }
        try (FileOutputStream output = new FileOutputStream(destination)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("Cannot encode combined Demo QA frame");
            }
        }
    }
}
