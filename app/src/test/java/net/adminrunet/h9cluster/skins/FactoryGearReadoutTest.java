package net.adminrunet.h9cluster.skins;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import net.adminrunet.h9cluster.ClusterRenderer;
import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.GearSelector;
import net.adminrunet.h9cluster.skins.classic.ClassicClusterView;
import net.adminrunet.h9cluster.skins.ionaurora.IonAuroraClusterView;
import net.adminrunet.h9cluster.skins.sport.SportClusterView;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;

/** Pixel-level contract for the small, system-adjacent automatic gear numeral. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class FactoryGearReadoutTest {
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 720;
    private static final int GEAR_LEFT = 982;
    private static final int GEAR_TOP = 28;
    private static final int GEAR_RIGHT = 1018;
    private static final int GEAR_BOTTOM = 66;

    @Test
    public void everyAutomaticRatioMatchesIonAuroraAndExactTypography() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        View[] factoryViews = factoryViews(context);
        IonAuroraClusterView ionAurora = new IonAuroraClusterView(context);
        Field ionLabel = IonAuroraClusterView.class.getDeclaredField("currentGearText");
        ionLabel.setAccessible(true);
        Method ionDraw = IonAuroraClusterView.class.getDeclaredMethod(
                "drawCurrentGear", Canvas.class, float.class);
        ionDraw.setAccessible(true);

        for (int ratio = 1; ratio <= 8; ratio++) {
            Bitmap reference = typographyReference(context, ratio);
            Bitmap ion = blankFrame();
            ionLabel.set(ionAurora, Integer.toString(ratio));
            ionDraw.invoke(ionAurora, new Canvas(ion), 1.0f);
            assertArrayEquals("ION AURORA reference for D" + ratio,
                    pixels(reference), pixels(ion));
            for (View view : factoryViews) {
                Bitmap actual = drawGear(view, state(GearSelector.DRIVE, ratio));
                String label = view.getClass().getSimpleName() + " D" + ratio;
                assertArrayEquals(label + " must have exactly the same numeral as ION AURORA",
                        pixels(ion), pixels(actual));
                assertTinyNumeralOnly(actual, label);
                assertEmptyRectangle(actual, 914, 76, 1006, 142,
                        label + " must not draw the old gear card");
                actual.recycle();
            }
            reference.recycle();
            ion.recycle();
        }
    }

    @Test
    public void manualAndNonDrivePositionsDrawNeitherNumberNorSelector() throws Exception {
        for (View view : factoryViews(RuntimeEnvironment.getApplication())) {
            for (int ratio : new int[] {1, 2, 8}) {
                assertEmptyGear(view, GearSelector.MANUAL, ratio);
            }
            for (String selector : new String[] {GearSelector.PARK, GearSelector.NEUTRAL,
                    GearSelector.REVERSE, GearSelector.UNKNOWN, "M1", "M2", "M8", "D1",
                    "unsupported", null}) {
                assertEmptyGear(view, selector, 5);
            }
        }
    }

    @Test
    public void invalidAutomaticRatiosDrawNothing() throws Exception {
        for (View view : factoryViews(RuntimeEnvironment.getApplication())) {
            for (int ratio : new int[] {Integer.MIN_VALUE, -1, 0, 9, Integer.MAX_VALUE}) {
                assertEmptyGear(view, GearSelector.DRIVE, ratio);
            }
        }
    }

    @Test
    public void fullFramesClearTheAutomaticNumeralWhenLeavingDrive() {
        ShadowSystemClock.advanceBy(Duration.ofMillis(1L));
        for (View view : factoryViews(RuntimeEnvironment.getApplication())) {
            measureAndLayout(view);
            Bitmap frame = blankFrame();
            Canvas canvas = new Canvas(frame);
            ClusterRenderer renderer = (ClusterRenderer) view;
            renderer.setClusterState(state(GearSelector.MANUAL, 1));
            view.draw(canvas);
            int[] blankReservation = rectanglePixels(
                    frame, GEAR_LEFT, GEAR_TOP, GEAR_RIGHT, GEAR_BOTTOM);
            int[] oldCardReservation = rectanglePixels(frame, 914, 76, 1006, 142);

            String[] selectors = {GearSelector.MANUAL, GearSelector.MANUAL, GearSelector.MANUAL,
                    GearSelector.PARK, GearSelector.NEUTRAL, GearSelector.REVERSE,
                    GearSelector.UNKNOWN, null, GearSelector.DRIVE};
            int[] ratios = {1, 2, 8, 5, 5, 5, 5, 5, 0};
            for (int index = 0; index < selectors.length; index++) {
                renderer.setClusterState(state(GearSelector.DRIVE, 8));
                view.draw(canvas);
                int[] automaticReservation = rectanglePixels(
                        frame, GEAR_LEFT, GEAR_TOP, GEAR_RIGHT, GEAR_BOTTOM);
                assertTrue(view.getClass().getSimpleName() + " D8 must appear in the full frame",
                        differentPixels(blankReservation, automaticReservation) > 30);
                assertArrayEquals("D8 must not restore a card below the system indicator",
                        oldCardReservation, rectanglePixels(frame, 914, 76, 1006, 142));

                renderer.setClusterState(state(selectors[index], ratios[index]));
                view.draw(canvas);
                assertArrayEquals(view.getClass().getSimpleName() + " must clear D8 for "
                                + selectors[index] + ":" + ratios[index],
                        blankReservation, rectanglePixels(
                                frame, GEAR_LEFT, GEAR_TOP, GEAR_RIGHT, GEAR_BOTTOM));
                assertArrayEquals("Non-drive state must not restore the old gear card",
                        oldCardReservation, rectanglePixels(frame, 914, 76, 1006, 142));
            }
            frame.recycle();
        }
    }

    @Test
    public void exportClassicAndSportD5FramesForVisualReview() throws Exception {
        ShadowSystemClock.advanceBy(Duration.ofMillis(1L));
        for (View view : factoryViews(RuntimeEnvironment.getApplication())) {
            measureAndLayout(view);
            ((ClusterRenderer) view).setClusterState(state(GearSelector.DRIVE, 5));
            Bitmap frame = blankFrame();
            Canvas canvas = new Canvas(frame);
            view.draw(canvas);
            ShadowSystemClock.advanceBy(Duration.ofMillis(1200L));
            view.draw(canvas);
            File directory = new File("build/reports/gear-readout");
            assertTrue("Cannot create gear readout report directory",
                    directory.isDirectory() || directory.mkdirs());
            String filename = view instanceof ClassicClusterView ? "classic.png" : "sport.png";
            try (FileOutputStream output = new FileOutputStream(new File(directory, filename))) {
                assertTrue("Cannot export " + filename,
                        frame.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
            frame.recycle();
        }
    }

    private static View[] factoryViews(Context context) {
        return new View[] {new ClassicClusterView(context), new SportClusterView(context)};
    }

    private static Bitmap drawGear(View view, ClusterState state) throws Exception {
        Method draw = view.getClass().getDeclaredMethod(
                "drawCurrentGear", Canvas.class, ClusterState.class);
        draw.setAccessible(true);
        Bitmap frame = blankFrame();
        draw.invoke(view, new Canvas(frame), state);
        return frame;
    }

    private static Bitmap typographyReference(Context context, int ratio) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTypeface(Typeface.createFromAsset(context.getAssets(), "fonts/Rajdhani-Medium.ttf"));
        paint.setTextSize(44.0f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(0xFFFAFDFF);
        paint.setFakeBoldText(true);
        paint.setTextSkewX(0.0f);
        Bitmap frame = blankFrame();
        new Canvas(frame).drawText(Integer.toString(ratio), 1000.0f, 63.0f, paint);
        return frame;
    }

    private static void assertTinyNumeralOnly(Bitmap frame, String label) {
        int visiblePixels = 0;
        int[] actual = pixels(frame);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int alpha = actual[y * WIDTH + x] >>> 24;
                if (x >= GEAR_LEFT && x < GEAR_RIGHT && y >= GEAR_TOP && y < GEAR_BOTTOM) {
                    if (alpha != 0) visiblePixels++;
                } else if (alpha != 0) {
                    assertEquals(label + " escaped the tiny gear reservation at " + x + "," + y,
                            0, alpha);
                }
            }
        }
        assertTrue(label + " must contain a readable numeral", visiblePixels > 30);
    }

    private static void assertEmptyGear(View view, String selector, int ratio) throws Exception {
        Bitmap frame = drawGear(view, state(selector, ratio));
        String label = view.getClass().getSimpleName() + " " + selector + ":" + ratio;
        for (int color : pixels(frame)) {
            if (color != 0) assertEquals(label + " must draw absolutely nothing", 0, color);
        }
        frame.recycle();
    }

    private static void assertEmptyRectangle(
            Bitmap frame, int left, int top, int right, int bottom, String label) {
        for (int color : rectanglePixels(frame, left, top, right, bottom)) {
            assertEquals(label, 0, color);
        }
    }

    private static int[] pixels(Bitmap frame) {
        return rectanglePixels(frame, 0, 0, WIDTH, HEIGHT);
    }

    private static int[] rectanglePixels(Bitmap frame, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        int[] pixels = new int[width * height];
        frame.getPixels(pixels, 0, width, left, top, width, height);
        return pixels;
    }

    private static int differentPixels(int[] before, int[] after) {
        int count = 0;
        for (int index = 0; index < before.length; index++) {
            if (before[index] != after[index]) count++;
        }
        return count;
    }

    private static Bitmap blankFrame() {
        return Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
    }

    private static void measureAndLayout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH, HEIGHT);
    }

    private static ClusterState state(String selector, int ratio) {
        long nowMs = SystemClock.elapsedRealtime();
        return new ClusterState(86, 2400, ratio, selector, 92, 78.0f, 47.0f, 421,
                28642.0, 42.3f, 167.8f, 2.35f, 2.37f, 2.42f, 2.40f,
                12.6f, 14.8f, 14.8f, 13.8f, 18.5f, 12.0f,
                85.8f, 86.2f, 85.9f, 86.1f, 224.0f,
                nowMs, nowMs, nowMs, nowMs, nowMs, "NORMAL");
    }
}
