package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;

import net.adminrunet.h9cluster.skins.classic.ClassicClusterView;
import net.adminrunet.h9cluster.skins.ionaurora.IonAuroraClusterView;
import net.adminrunet.h9cluster.skins.sport.SportClusterView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;

/** Demo-only frontal previews from the actual skin renderers and lamp-test overlay. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class SkinHeaderPreviewTest {
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 720;
    private static final String[] NAMES = {"ion-aurora", "classic", "sport"};

    @Test
    public void exportAllThreeHeadersWithIdenticalD5TelemetryAndSystemLamps()
            throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ShadowSystemClock.advanceBy(Duration.ofMillis(1L));
        View[] skins = {
                new IonAuroraClusterView(context),
                new ClassicClusterView(context),
                new SportClusterView(context)
        };
        // Direct construction is deliberately limited to this Demo test. The
        // production preview factory is not widened to Classic or Sport.
        DemoSystemIconsView systemIcons = new DemoSystemIconsView(context);
        layoutScreen(systemIcons);
        Bitmap lamps = blankFrame();
        Bitmap[] skinFrames = new Bitmap[skins.length];
        try {
            systemIcons.draw(new Canvas(lamps));
            assertTrue("The existing Demo overlay must contain system lamps",
                    countVisible(lamps) > 2000);
            for (int index = 0; index < skins.length; index++) {
                layoutScreen(skins[index]);
                skinFrames[index] = blankFrame();
            }

            // Let launch effects settle using real frame-sized intervals, while
            // refreshing exactly the same readings so ATF and wheel data stay fresh.
            for (int frame = 0; frame <= 40; frame++) {
                ClusterState state = previewState(SystemClock.elapsedRealtime());
                systemIcons.setClusterState(state);
                for (int index = 0; index < skins.length; index++) {
                    ((ClusterRenderer) skins[index]).setClusterState(state);
                    skins[index].draw(new Canvas(skinFrames[index]));
                }
                if (frame < 40) {
                    ShadowSystemClock.advanceBy(Duration.ofMillis(33L));
                }
            }

            for (int index = 0; index < skins.length; index++) {
                assertEquals(NAMES[index] + " must actually render 86 km/h",
                        86, Math.round(displayedValue(skins[index], "displayedSpeed")));
                assertEquals(NAMES[index] + " must actually render 2400 rpm",
                        2400, Math.round(displayedValue(skins[index], "displayedRpm")));
                Bitmap composition = blankFrame();
                try {
                    Canvas canvas = new Canvas(composition);
                    // Composite after the skin's CLEAR pass. Filling its own
                    // Canvas first would let Classic/Sport erase the black matte.
                    canvas.drawColor(Color.BLACK);
                    canvas.drawBitmap(skinFrames[index], 0, 0, null);
                    canvas.drawBitmap(lamps, 0, 0, null);
                    assertEquals("Preview must have an opaque black navigation background",
                            Color.BLACK, composition.getPixel(960, 360));
                    assertTrue(NAMES[index] + " must show its own D5 numeral",
                            countBrightGearPixels(composition) > 30);
                    exportPng(composition, new File(
                            "build/reports/header-v2/" + NAMES[index] + ".png"));
                } finally {
                    composition.recycle();
                }
            }
        } finally {
            lamps.recycle();
            for (Bitmap frame : skinFrames) {
                if (frame != null) frame.recycle();
            }
        }
    }

    private static float displayedValue(View skin, String fieldName) throws Exception {
        Field field = skin.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getFloat(skin);
    }

    private static Bitmap blankFrame() {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        return bitmap;
    }

    private static void layoutScreen(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH, HEIGHT);
    }

    private static int countVisible(Bitmap bitmap) {
        int[] pixels = new int[WIDTH * HEIGHT];
        bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
        int count = 0;
        for (int pixel : pixels) {
            if (Color.alpha(pixel) != 0) count++;
        }
        return count;
    }

    private static int countBrightGearPixels(Bitmap bitmap) {
        int count = 0;
        for (int y = 28; y < 66; y++) {
            for (int x = 982; x < 1018; x++) {
                int pixel = bitmap.getPixel(x, y);
                if (Color.red(pixel) > 180 && Color.green(pixel) > 180
                        && Color.blue(pixel) > 180) {
                    count++;
                }
            }
        }
        return count;
    }

    private static ClusterState previewState(long nowMs) {
        return new ClusterState(86, 2400, 5, GearSelector.DRIVE, 92, 78.0f, 47.0f, 421,
                28642.0, 42.3f, 167.8f, 2.35f, 2.37f, 2.42f, 2.40f,
                12.6f, 14.8f, 14.8f, 13.8f, 18.5f, 12.0f,
                85.8f, 86.2f, 85.9f, 86.1f, 224.0f,
                nowMs, nowMs, nowMs, nowMs, nowMs, "NORMAL");
    }

    private static void exportPng(Bitmap bitmap, File destination) throws IOException {
        File directory = destination.getParentFile();
        if (directory != null && !directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create skin header QA directory: " + directory);
        }
        try (FileOutputStream output = new FileOutputStream(destination)) {
            assertTrue("Cannot encode " + destination.getName(),
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        Bitmap decoded = BitmapFactory.decodeFile(destination.getAbsolutePath());
        assertNotNull("Exported preview must decode successfully", decoded);
        try {
            assertEquals(WIDTH, decoded.getWidth());
            assertEquals(HEIGHT, decoded.getHeight());
        } finally {
            decoded.recycle();
        }
    }
}
