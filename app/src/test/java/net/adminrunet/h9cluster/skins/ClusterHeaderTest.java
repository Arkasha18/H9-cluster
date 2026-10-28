package net.adminrunet.h9cluster.skins;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import net.adminrunet.h9cluster.skins.classic.ClassicClusterView;
import net.adminrunet.h9cluster.skins.sport.SportClusterView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Native raster contract for the header, with no gear-digit mask exception. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class ClusterHeaderTest {
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 720;
    private static final long ELAPSED_MS = 1000L;
    private static final ClusterHeader.Style[] HEADER_STYLES = {
            ClusterHeader.Style.ION_AURORA,
            ClusterHeader.Style.FACTORY,
            ClusterHeader.Style.FACTORY
    };
    private static final String[] STYLES = {"ion-aurora", "classic", "sport"};

    @Test
    public void everyStyleAndWifiStateLeaveFullMaskAndOnePixelNeighborGapClear()
            throws Exception {
        Bitmap mask = BitmapFactory.decodeFile(locateMask().getAbsolutePath());
        assertNotNull("The actual alpha03 control layer must be loaded", mask);
        try {
            assertEquals(WIDTH, mask.getWidth());
            assertEquals(HEIGHT, mask.getHeight());
            int[] maskPixels = pixels(mask);
            assertCentralMaskBounds(maskPixels);
            boolean[] protectedWithGap = maskWithOnePixelNeighbors(maskPixels);
            long[] times = {
                    localTime(2026, 9, 7, 8, 8),
                    localTime(2026, 12, 31, 23, 59),
                    localTime(2027, 1, 1, 0, 0)
            };
            for (int style = 0; style < HEADER_STYLES.length; style++) {
                for (boolean connected : new boolean[] {false, true}) {
                    ClusterHeader header = newHeader(HEADER_STYLES[style], style == 0, connected);
                    for (int sample = 0; sample < times.length; sample++) {
                        String scenario = STYLES[style] + "/"
                                + (connected ? "connected" : "disconnected")
                                + "/time-" + sample;
                        Bitmap frame = render(header, times[sample]);
                        try {
                            if (sample == 1) {
                                exportFrame(frame, STYLES[style] + "-"
                                        + (connected ? "connected" : "disconnected") + ".png");
                            }
                            int[] foreground = pixels(frame);
                            assertMaskAndGapClear(foreground, maskPixels,
                                    protectedWithGap, scenario);
                            assertClearRectangle(foreground, 921, 18, 1048, 79,
                                    scenario + " must leave the entire factory gear box empty");
                            assertWifiPixelGap(foreground, scenario);
                            assertClearRectangle(foreground, 1690, 15, 1731, 76,
                                    scenario + " must not retain the old top-right Wi-Fi");
                            assertTrue(scenario + " must have a visible enclosing outline",
                                    alpha(foreground, 970, 14) > 0);
                            assertTrue(scenario + " must have readable clock text",
                                    brightTextPixels(foreground, 893, 92, 1045, 146) > 100);
                            if (style == 0) {
                                assertTrue(scenario + " must have readable date text",
                                        brightTextPixels(foreground, 89, 23, 293, 72) > 150);
                            } else {
                                assertClearRectangle(foreground, 80, 14, 302, 80,
                                        scenario + " must not draw a date");
                            }
                        } finally {
                            frame.recycle();
                        }
                    }
                }
            }
        } finally {
            mask.recycle();
        }
    }

    @Test
    public void dateFlagChangesOnlyTheDateCardAndAlwaysKeepsTheClock() throws Exception {
        long wallTime = localTime(2026, 9, 7, 8, 8);
        for (ClusterHeader.Style style : ClusterHeader.Style.values()) {
            Bitmap withDate = render(newHeader(style, true, true), wallTime);
            Bitmap withoutDate = render(newHeader(style, false, true), wallTime);
            try {
                int[] withPixels = pixels(withDate);
                int[] withoutPixels = pixels(withoutDate);
                int dateDifferences = 0;
                for (int y = 0; y < HEIGHT; y++) {
                    for (int x = 0; x < WIDTH; x++) {
                        int index = y * WIDTH + x;
                        if (withPixels[index] == withoutPixels[index]) {
                            continue;
                        }
                        if (x < 80 || x >= 302 || y < 14 || y >= 80) {
                            fail("showDate changed a non-date pixel at " + x + "," + y);
                        }
                        dateDifferences++;
                    }
                }
                assertTrue("showDate must actually paint a date card", dateDifferences > 1000);
                assertClearRectangle(withoutPixels, 80, 14, 302, 80,
                        "showDate=false must leave the date area transparent");
                assertTrue("The no-date variant must still paint actual clock text",
                        brightTextPixels(withoutPixels, 893, 92, 1045, 146) > 100);
            } finally {
                withDate.recycle();
                withoutDate.recycle();
            }
        }
    }

    @Test
    public void clockAndDateTextActuallyRepaintAcrossMidnightAndYearBoundary()
            throws Exception {
        ClusterHeader header = newHeader(ClusterHeader.Style.ION_AURORA, true, true);
        long beforeMidnight = localTime(2026, 12, 31, 23, 59);
        Bitmap before = render(header, beforeMidnight);
        Bitmap sameMinute = render(header, beforeMidnight + 30_000L);
        Bitmap after = render(header, beforeMidnight + 60_000L);
        try {
            assertArrayEquals("Unchanged HH:mm and date must produce identical pixels",
                    pixels(before), pixels(sameMinute));
            assertTrue("23:59 to 00:00 must repaint clock glyphs inside the unchanged frame",
                    differentPixels(before, after, 893, 92, 1045, 146) > 100);
            assertTrue("31.12.2026 to 01.01.2027 must repaint date glyphs",
                    differentPixels(before, after, 89, 23, 293, 72) > 100);
            assertArrayEquals("A wall-clock change must not modify the Wi-Fi/gear outline",
                    rectanglePixels(before, 884, 13, 1054, 84),
                    rectanglePixels(after, 884, 13, 1054, 84));
        } finally {
            before.recycle();
            sameMinute.recycle();
            after.recycle();
        }
    }

    @Test
    public void stackedFramesShareWidthHeightCenterAndRoundedCorners() throws Exception {
        for (ClusterHeader.Style style : ClusterHeader.Style.values()) {
            ClusterHeader header = newHeader(style, false, true);
            RectF gear = bounds(header, "gearOutline");
            RectF clock = bounds(header, "clockBounds");
            assertEquals(style + " frame left edges must align", gear.left, clock.left, 0.0f);
            assertEquals(style + " frame right edges must align", gear.right, clock.right, 0.0f);
            assertEquals(style + " frame centers must align",
                    gear.centerX(), clock.centerX(), 0.0f);
            assertEquals(style + " frame heights must match", gear.height(), clock.height(), 0.0f);
            float strokeWidth = style == ClusterHeader.Style.FACTORY ? 2.0f : 1.0f;
            assertEquals(style + " must keep one clear raster row between outlines",
                    strokeWidth + 1.0f, clock.top - gear.bottom, 0.0f);
            Bitmap frame = render(header, localTime(2026, 9, 7, 8, 8));
            try {
                int[] foreground = pixels(frame);
                for (RectF card : new RectF[] {gear, clock}) {
                    int top = (int) Math.floor(card.top - strokeWidth * 0.5f);
                    int left = (int) Math.floor(card.left - strokeWidth * 0.5f);
                    assertClearRectangle(foreground, left, top, left + 2, top + 2,
                            style + " rounded frame must leave its outer corner empty");
                    assertTrue(style + " rounded frame top edge must be visible",
                            alpha(foreground, (int) card.centerX(), top) != 0);
                    assertTrue(style + " rounded frame left edge must be visible below its arc",
                            alpha(foreground, left, top + 26) != 0);
                }
                int gapY = (int) Math.ceil(gear.bottom + strokeWidth * 0.5f);
                assertClearRectangle(foreground, (int) Math.ceil(gear.left), gapY,
                        (int) Math.floor(gear.right), gapY + 1,
                        style + " must preserve the actual clear row between stacked frames");
            } finally {
                frame.recycle();
            }
        }
    }

    @Test
    public void factoryClockAndDateCardsExactlyMatchClassicAndSportNativeCardStyle()
            throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ClusterHeader header = newHeader(ClusterHeader.Style.FACTORY, true, true);
        Field textField = ClusterHeader.class.getDeclaredField("textPaint");
        textField.setAccessible(true);
        ((Paint) textField.get(header)).setAlpha(0);
        Bitmap actual = render(header, localTime(2026, 9, 7, 8, 8));
        try {
            for (View skin : new View[] {new ClassicClusterView(context), new SportClusterView(context)}) {
                Field borderColor = skin.getClass().getDeclaredField("COLOR_CARD_BORDER");
                borderColor.setAccessible(true);
                Method drawTopCard = skin.getClass().getDeclaredMethod("drawTopCard",
                        Canvas.class, float.class, float.class, float.class, float.class, int.class);
                drawTopCard.setAccessible(true);
                for (String fieldName : new String[] {"clockBounds", "dateBounds"}) {
                    RectF card = bounds(header, fieldName);
                    Bitmap reference = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
                    try {
                        drawTopCard.invoke(skin, new Canvas(reference),
                                card.left, card.top, card.right, card.bottom, borderColor.getInt(null));
                        int left = (int) Math.floor(card.left) - 1;
                        int top = (int) Math.floor(card.top) - 1;
                        int right = (int) Math.ceil(card.right) + 2;
                        int bottom = (int) Math.ceil(card.bottom) + 2;
                        assertArrayEquals(skin.getClass().getSimpleName() + " " + fieldName
                                        + " must match its existing card's fill, border, stroke and radius",
                                rectanglePixels(reference, left, top, right, bottom),
                                rectanglePixels(actual, left, top, right, bottom));
                    } finally {
                        reference.recycle();
                    }
                }
            }
        } finally {
            actual.recycle();
        }
    }

    private static RectF bounds(ClusterHeader header, String fieldName) throws Exception {
        Field field = ClusterHeader.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return new RectF((RectF) field.get(header));
    }

    private static ClusterHeader newHeader(ClusterHeader.Style style,
            boolean showDate, boolean connected)
            throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ClusterHeader header = new ClusterHeader(context, style, showDate);
        Field indicatorField = ClusterHeader.class.getDeclaredField("wifiIndicator");
        indicatorField.setAccessible(true);
        WifiIndicator indicator = (WifiIndicator) indicatorField.get(header);
        Field connectedField = WifiIndicator.class.getDeclaredField("connected");
        connectedField.setAccessible(true);
        connectedField.setBoolean(indicator, connected);
        Field checkedField = WifiIndicator.class.getDeclaredField("lastCheckedAtMs");
        checkedField.setAccessible(true);
        checkedField.setLong(indicator, ELAPSED_MS);
        return header;
    }

    private static Bitmap render(ClusterHeader header, long wallTimeMillis) {
        Bitmap frame = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        header.draw(new Canvas(frame), ELAPSED_MS, wallTimeMillis);
        return frame;
    }

    private static void assertCentralMaskBounds(int[] mask) {
        int left = WIDTH;
        int top = HEIGHT;
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < 110; y++) {
            for (int x = 800; x < 1150; x++) {
                if (alpha(mask, x, y) != 0) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        assertEquals("Factory reservation left edge, including faint alpha", 921, left);
        assertEquals("Factory reservation top edge, including faint alpha", 18, top);
        assertEquals("Factory reservation right edge, including faint alpha", 1047, right);
        assertEquals("Factory reservation bottom edge, including faint alpha", 78, bottom);
    }

    private static boolean[] maskWithOnePixelNeighbors(int[] mask) {
        boolean[] protectedWithGap = new boolean[mask.length];
        int protectedCount = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (alpha(mask, x, y) == 0) {
                    continue;
                }
                protectedCount++;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int neighborX = x + dx;
                        int neighborY = y + dy;
                        if (neighborX >= 0 && neighborX < WIDTH
                                && neighborY >= 0 && neighborY < HEIGHT) {
                            protectedWithGap[neighborY * WIDTH + neighborX] = true;
                        }
                    }
                }
            }
        }
        assertTrue("The full alpha03 layer must be checked", protectedCount > 170_000);
        return protectedWithGap;
    }

    private static void assertMaskAndGapClear(int[] foreground, int[] mask,
            boolean[] protectedWithGap, String scenario) {
        for (int index = 0; index < foreground.length; index++) {
            if ((foreground[index] >>> 24) == 0) {
                continue;
            }
            if ((mask[index] >>> 24) != 0) {
                fail(scenario + " paints under alpha03 at "
                        + (index % WIDTH) + "," + (index / WIDTH));
            }
            if (protectedWithGap[index]) {
                fail(scenario + " consumes the one-pixel clear gap at "
                        + (index % WIDTH) + "," + (index / WIDTH));
            }
        }
    }

    private static void assertWifiPixelGap(int[] foreground, String scenario) {
        int minX = WIDTH;
        int maxX = -1;
        int visible = 0;
        for (int y = 30; y < 69; y++) {
            for (int x = 892; x < 923; x++) {
                if (alpha(foreground, x, y) != 0) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    visible++;
                }
            }
        }
        assertTrue(scenario + " must draw a real Wi-Fi glyph", visible > 40);
        assertEquals(scenario + " scaled Wi-Fi must start at its native raster bound", 895, minX);
        int borderRight = -1;
        for (int y = 30; y < 69; y++) {
            for (int x = 880; x < 892; x++) {
                if (alpha(foreground, x, y) != 0) {
                    borderRight = Math.max(borderRight, x);
                }
            }
        }
        assertTrue(scenario + " must have a visible left frame border", borderRight >= 885);
        assertTrue(scenario + " must leave at least seven fully clear pixels after the AA border"
                        + "; border ends at " + borderRight + ", Wi-Fi starts at " + minX,
                minX - borderRight - 1 >= 7);
        assertClearRectangle(foreground, borderRight + 1, 30, minX, 69,
                scenario + " must keep the measured Wi-Fi-to-border gap completely transparent");
        assertEquals(scenario + " scaled Wi-Fi must preserve the factory-side gap", 918, maxX);
        assertClearRectangle(foreground, 920, 18, 921, 79,
                scenario + " column920 must remain fully transparent");
    }

    private static void assertClearRectangle(int[] foreground,
            int left, int top, int right, int bottom, String label) {
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                if (alpha(foreground, x, y) != 0) {
                    fail(label + ": nonzero alpha at " + x + "," + y);
                }
            }
        }
    }

    private static int brightTextPixels(int[] foreground,
            int left, int top, int right, int bottom) {
        int count = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int color = foreground[y * WIDTH + x];
                if ((color >>> 24) > 200 && ((color >>> 16) & 255) > 200
                        && ((color >>> 8) & 255) > 200 && (color & 255) > 200) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int differentPixels(Bitmap first, Bitmap second,
            int left, int top, int right, int bottom) {
        int[] firstPixels = rectanglePixels(first, left, top, right, bottom);
        int[] secondPixels = rectanglePixels(second, left, top, right, bottom);
        int different = 0;
        for (int index = 0; index < firstPixels.length; index++) {
            if (firstPixels[index] != secondPixels[index]) {
                different++;
            }
        }
        return different;
    }

    private static int alpha(int[] colors, int x, int y) {
        return colors[y * WIDTH + x] >>> 24;
    }

    private static int[] pixels(Bitmap bitmap) {
        return rectanglePixels(bitmap, 0, 0, WIDTH, HEIGHT);
    }

    private static int[] rectanglePixels(Bitmap bitmap,
            int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        int[] colors = new int[width * height];
        bitmap.getPixels(colors, 0, width, left, top, width, height);
        return colors;
    }

    private static long localTime(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.US);
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }

    private static void exportFrame(Bitmap frame, String filename) throws IOException {
        File directory = new File("build/reports/header");
        assertTrue("Cannot create header visual-QA report directory",
                directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, filename))) {
            assertTrue("Cannot export header " + filename,
                    frame.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
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
        throw new IOException("Cannot locate technical control layer alpha03");
    }
}
