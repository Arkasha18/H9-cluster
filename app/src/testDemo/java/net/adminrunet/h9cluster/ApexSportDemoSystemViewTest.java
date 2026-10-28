package net.adminrunet.h9cluster;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.view.View;

import net.adminrunet.h9cluster.skins.SkinRegistry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class ApexSportDemoSystemViewTest {
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 720;

    @Test
    public void apexUsesASeparateUpperLayerWithoutChangingOtherFactories() {
        assertTrue(PreviewSystemIcons.create(RuntimeEnvironment.getApplication(),
                SkinRegistry.APEX_SPORT) instanceof ApexSportDemoSystemView);
        assertTrue(PreviewSystemIcons.create(RuntimeEnvironment.getApplication(),
                SkinRegistry.SCULPTED_TITANIUM) instanceof TitaniumDemoSystemView);
        assertTrue(PreviewSystemIcons.create(RuntimeEnvironment.getApplication(),
                SkinRegistry.ION_AURORA) instanceof DemoSystemIconsView);
        assertNull(PreviewSystemIcons.create(RuntimeEnvironment.getApplication(),
                SkinRegistry.CLASSIC));
        assertNull(PreviewSystemIcons.create(RuntimeEnvironment.getApplication(),
                SkinRegistry.STOCK));
    }

    @Test
    public void factoryPixelsStayInRealSystemFieldsAndStampStaysAtApprovedAnchor()
            throws IOException {
        View view = overlay();
        PreviewSystemIcons.update(view, fixture("D", 6, "NORMAL"));
        Bitmap frame = render(view);
        Bitmap mask = BitmapFactory.decodeFile(repositoryFile(
                "docs/H9_Cluster_Neutral_Design_Template_1920x720/03_system_icons_forbidden_mask.png")
                .getAbsolutePath());
        assertNotNull(mask);
        int[] pixels = pixels(frame), reserved = pixels(mask);
        int selector = 0, mode = 0, stamp = 0, lamps = 0;
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int index = y * WIDTH + x;
            if ((pixels[index] >>> 24) == 0) continue;
            if (x >= 1480 && x < 1880 && y >= 20 && y < 52) {
                stamp++;
                continue;
            }
            assertTrue("Demo factory pixel outside field03 at " + x + "," + y,
                    (reserved[index] >>> 24) != 0);
            if (x >= 923 && x < 1046 && y >= 20 && y < 77) selector++;
            else if (x >= 1428 && x < 1527 && y >= 668 && y < 698) mode++;
            else lamps++;
        }
        assertTrue("Mockup label must be visible", stamp > 300);
        assertTrue("Factory selector is rendered above the skin", selector > 100);
        assertTrue("Factory drive mode is rendered above the skin", mode > 50);
        assertTrue("Reuse the complete existing lamp-test overlay", lamps > 1000);
        assertEquals(0, nonTransparent(frame, 672, 80, 1096, 720));
        File output = new File("build/reports/apexsport/demo-system-layer.png");
        assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
        try (FileOutputStream stream = new FileOutputStream(output)) {
            assertTrue(frame.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        frame.recycle();
        mask.recycle();
    }

    @Test
    public void selectorAndDriveModeUpdateOnlyTheDemoUpperLayer() {
        View view = overlay();
        PreviewSystemIcons.update(view, fixture("D", 1, "ECO"));
        Bitmap driveOne = render(view);
        PreviewSystemIcons.update(view, fixture("D", 8, "ECO"));
        Bitmap driveEight = render(view);
        assertArrayEquals("D ratio remains exclusively in the skin",
                region(driveOne, 923, 20, 123, 57),
                region(driveEight, 923, 20, 123, 57));
        PreviewSystemIcons.update(view, fixture("M", 1, "ECO"));
        Bitmap manualOne = render(view);
        PreviewSystemIcons.update(view, fixture("M", 8, "ECO"));
        Bitmap manualEight = render(view);
        assertFalse("Manual ratio belongs to the factory layer",
                Arrays.equals(region(manualOne, 923, 20, 123, 57),
                        region(manualEight, 923, 20, 123, 57)));
        for (String selector : new String[] {"P", "R", "N", "M", "D"}) {
            PreviewSystemIcons.update(view, fixture(selector, 4, "ECO"));
            Bitmap selected = render(view);
            assertTrue(selector, nonTransparent(selected, 923, 20, 1046, 77) > 100);
            selected.recycle();
        }
        PreviewSystemIcons.update(view, fixture("", 4, "ECO"));
        Bitmap unknown = render(view);
        assertEquals(0, nonTransparent(unknown, 923, 20, 1046, 77));
        PreviewSystemIcons.update(view, fixture("D", 1, "NORMAL"));
        Bitmap normal = render(view);
        assertFalse("Factory mode responds to snapshot updates",
                Arrays.equals(region(driveOne, 1428, 668, 99, 30),
                        region(normal, 1428, 668, 99, 30)));
        driveOne.recycle(); driveEight.recycle(); manualOne.recycle();
        manualEight.recycle(); unknown.recycle(); normal.recycle();
    }

    private static View overlay() {
        View view = PreviewSystemIcons.create(RuntimeEnvironment.getApplication(),
                SkinRegistry.APEX_SPORT);
        assertNotNull(view);
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH, HEIGHT);
        return view;
    }

    private static Bitmap render(View view) {
        Bitmap image = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        image.setDensity(Bitmap.DENSITY_NONE);
        view.draw(new Canvas(image));
        return image;
    }

    private static int[] pixels(Bitmap image) {
        return region(image, 0, 0, image.getWidth(), image.getHeight());
    }

    private static int[] region(Bitmap image, int x, int y, int width, int height) {
        int[] result = new int[width * height];
        image.getPixels(result, 0, width, x, y, width, height);
        return result;
    }

    private static int nonTransparent(Bitmap image, int left, int top, int right, int bottom) {
        int count = 0;
        for (int pixel : region(image, left, top, right - left, bottom - top)) {
            if ((pixel >>> 24) != 0) count++;
        }
        return count;
    }

    private static File repositoryFile(String relative) {
        File file = new File(relative);
        return file.isFile() ? file : new File("../" + relative);
    }

    private static ClusterState fixture(String selector, int gear, String mode) {
        return new ClusterState(86, 2640, gear, selector, 88, 76, 54, 540,
                124680, 42.8f, 186.4f, 2.35f, 2.36f, 2.32f, 2.34f,
                8.4f, 9.7f, 100, 14.2f, 12.5f, 12,
                86, 86.2f, 85.9f, 86.1f, 248,
                60000, 60000, 60000, 60000, 60000, mode);
    }
}
