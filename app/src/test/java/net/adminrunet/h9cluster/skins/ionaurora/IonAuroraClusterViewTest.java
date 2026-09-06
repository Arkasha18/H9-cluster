package net.adminrunet.h9cluster.skins.ionaurora;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.BuildConfig;
import net.adminrunet.h9cluster.GearSelector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.SystemClock;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
public final class IonAuroraClusterViewTest {
    @Test
    public void focusFrameKeepsEverySystemIconClearExceptAtmosphereAndTinyDriveDigit()
            throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        long nowMs = Math.max(1L, SystemClock.elapsedRealtime());
        IonAuroraClusterView view = new IonAuroraClusterView(context);
        view.setClusterState(focusState(nowMs));
        int exactWidth = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY);
        int exactHeight = View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY);
        view.measure(exactWidth, exactHeight);
        view.layout(0, 0, 1920, 720);

        Bitmap rendered = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(rendered);
        view.draw(canvas);
        ShadowSystemClock.advanceBy(Duration.ofMillis(1_200L));
        view.draw(canvas);

        Bitmap base = loadAsset(
                context,
                "dashboard/skins/ionaurora/map_black_gradient.png");
        Bitmap atmosphere = loadAsset(context, "dashboard/skins/ionaurora/cosmic_sides.png");
        Bitmap backdrop = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        Canvas backdropCanvas = new Canvas(backdrop);
        if (BuildConfig.DEMO_MODE) backdropCanvas.drawColor(Color.BLACK);
        backdropCanvas.drawBitmap(base, 0, 0, null);
        backdropCanvas.drawBitmap(atmosphere, 0, 0, null);
        Bitmap forbidden = BitmapFactory.decodeFile(
                locateControlFile("03_system_icons_forbidden_mask.png").getAbsolutePath());
        assertNotNull(forbidden);
        assertEquals(1920, forbidden.getWidth());
        assertEquals(720, forbidden.getHeight());

        IonAuroraClusterView foregroundView = new IonAuroraClusterView(context, false);
        foregroundView.setClusterState(focusState(nowMs));
        foregroundView.measure(exactWidth, exactHeight);
        foregroundView.layout(0, 0, 1920, 720);
        Bitmap foreground = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        Canvas foregroundCanvas = new Canvas(foreground);
        foregroundView.draw(foregroundCanvas);
        ShadowSystemClock.advanceBy(Duration.ofMillis(1_200L));
        foregroundView.draw(foregroundCanvas);
        String variant = BuildConfig.DEMO_MODE ? "demo" : "transparent";
        exportPng(rendered, new File("build/reports/ionaurora/focus_mask_safe_" + variant + ".png"));
        exportPng(foreground, new File("build/reports/ionaurora/foreground_mask_safe_" + variant + ".png"));
        // Temporary top control layer: it is never an asset or a runtime cutout.
        Bitmap control = rendered.copy(Bitmap.Config.ARGB_8888, true);
        Paint controlPaint = new Paint();
        controlPaint.setColorFilter(new PorterDuffColorFilter(0x99FF4A20,
                PorterDuff.Mode.SRC_IN));
        new Canvas(control).drawBitmap(forbidden, 0, 0, controlPaint);
        exportPng(control, new File("build/reports/ionaurora/mask03_control_" + variant + ".png"));
        control.recycle();

        int protectedPixelCount = 0;
        int foregroundOverlap = 0;
        int changedProtected = 0;
        int authorizedGearPixels = 0;
        String firstOverlap = "";
        int changedOutsideForbidden = 0;
        int insufficientGap = 0;
        String firstGap = "";
        for (int y = 0; y < 720; y++) {
            for (int x = 0; x < 1920; x++) {
                boolean protectedPixel = (forbidden.getPixel(x, y) >>> 24) != 0;
                int actual = rendered.getPixel(x, y);
                int expectedBase = backdrop.getPixel(x, y);
                if (protectedPixel) {
                    protectedPixelCount++;
                    if (isAllowedGearDigitPixel(x, y)) {
                        if (Color.alpha(foreground.getPixel(x, y)) != 0) {
                            authorizedGearPixels++;
                        }
                    } else if (Color.alpha(foreground.getPixel(x, y)) != 0) {
                        foregroundOverlap++;
                        if (firstOverlap.isEmpty()) firstOverlap = x + "," + y;
                    }
                    if (!isAllowedGearDigitPixel(x, y) && actual != expectedBase) {
                        changedProtected++;
                    }
                } else if (actual != expectedBase) {
                    changedOutsideForbidden++;
                }
                if (!isAllowedGearDigitPixel(x, y)
                        && Color.alpha(foreground.getPixel(x, y)) != 0) {
                    boolean adjacent = false;
                    for (int yy = Math.max(0, y - 1); yy <= Math.min(719, y + 1); yy++) {
                        for (int xx = Math.max(0, x - 1); xx <= Math.min(1919, x + 1); xx++) {
                            adjacent |= Color.alpha(forbidden.getPixel(xx, yy)) != 0;
                        }
                    }
                    if (adjacent) {
                        insufficientGap++;
                        if (firstGap.isEmpty()) firstGap = x + "," + y;
                    }
                }
            }
        }

        System.out.println("MASK03 protected=" + protectedPixelCount
                + " foregroundOverlap=" + foregroundOverlap
                + " changedProtected=" + changedProtected + " first=" + firstOverlap);
        assertTrue(protectedPixelCount > 100_000);
        assertEquals("Foreground overlaps mask03; first=" + firstOverlap, 0, foregroundOverlap);
        assertEquals("At least one clear pixel must separate mask03 and artwork; first="
                + firstGap, 0, insufficientGap);
        assertEquals("Protected pixels outside the tiny gear digit must match the backdrop",
                0, changedProtected);
        assertTrue("The automatic gear digit must be visible in its tiny exception",
                authorizedGearPixels > 30);
        assertTrue(changedOutsideForbidden > 100_000);
        assertTrue(rendered.hasAlpha());
        // The requested wider navigation aperture is free of any chrome or labels.
        assertRegionUnchanged(rendered, backdrop, 800, 180, 1120, 620);
        // Only the tiny automatic gear numeral may occupy the original gear reservation.
        assertGearRegionUnchangedOutsideDigit(foreground, rendered, backdrop);
        assertRegionUnchanged(rendered, backdrop, 1426, 666, 1529, 700);
        // The relocated drums frame, but never fill, the central navigation corridor.
        assertPanelRecessClear(foreground, rendered, backdrop, 738, 110, 1182, 630);
        // No empty glass remains below the left system warning block.
        assertPanelRecessClear(foreground, rendered, backdrop, 400, 444, 552, 493);
    }

    private static boolean isAllowedGearDigitPixel(int x, int y) {
        return x >= 982 && x < 1018 && y >= 28 && y < 66;
    }

    private static void assertGearRegionUnchangedOutsideDigit(
            Bitmap foreground, Bitmap actual, Bitmap backdrop) {
        for (int y = 18; y < 79; y++) {
            for (int x = 921; x < 1048; x++) {
                if (isAllowedGearDigitPixel(x, y)) continue;
                assertEquals("Gear reservation outside numeral at " + x + "," + y,
                        backdrop.getPixel(x, y), actual.getPixel(x, y));
                assertEquals("Gear foreground outside numeral at " + x + "," + y,
                        0, Color.alpha(foreground.getPixel(x, y)));
            }
        }
    }

    private static void assertPanelRecessClear(Bitmap foreground, Bitmap actual,
            Bitmap backdrop, int left, int top, int right, int bottom) {
        assertRegionUnchanged(actual, backdrop, left, top, right, bottom);
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                assertEquals("Panel recess foreground at " + x + "," + y,
                        0, Color.alpha(foreground.getPixel(x, y)));
            }
        }
    }

    private static void assertRegionUnchanged(Bitmap actual, Bitmap backdrop,
            int left, int top, int right, int bottom) {
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                assertEquals("Reserved aperture at " + x + "," + y,
                        backdrop.getPixel(x, y), actual.getPixel(x, y));
            }
        }
    }

    private static ClusterState focusState(long nowMs) {
        return new ClusterState(
                86,
                2_400,
                5,
                GearSelector.DRIVE,
                92,
                78.0f,
                47.0f,
                421,
                28_642.0,
                42.3f,
                167.8f,
                2.35f,
                2.37f,
                2.42f,
                2.40f,
                12.6f,
                14.8f,
                14.8f,
                13.8f,
                18.5f,
                12.0f,
                85.8f,
                86.2f,
                85.9f,
                86.1f,
                224.0f,
                nowMs,
                nowMs,
                nowMs,
                nowMs,
                nowMs,
                "NORMAL");
    }

    private static Bitmap loadAsset(Context context, String path) throws IOException {
        InputStream input = null;
        try {
            input = context.getAssets().open(path);
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                throw new IOException("Cannot decode asset: " + path);
            }
            return bitmap;
        } finally {
            if (input != null) {
                input.close();
            }
        }
    }

    private static File locateControlFile(String name) throws IOException {
        String relative = "docs/H9_Cluster_Neutral_Design_Template_1920x720/" + name;
        File fromRoot = new File(relative);
        if (fromRoot.isFile()) {
            return fromRoot;
        }
        File fromModule = new File("../" + relative);
        if (fromModule.isFile()) {
            return fromModule;
        }
        throw new IOException("Cannot locate technical control layer: " + name);
    }

    private static void exportPng(Bitmap bitmap, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create QA directory: " + parent);
        }
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(destination);
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("Cannot encode QA frame");
            }
        } finally {
            if (output != null) {
                output.close();
            }
        }
    }
}
