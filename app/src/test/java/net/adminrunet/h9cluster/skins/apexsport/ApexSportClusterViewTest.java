package net.adminrunet.h9cluster.skins.apexsport;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;

import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.RpmReadoutStabilizer;
import net.adminrunet.h9cluster.skins.SkinRegistry;
import net.adminrunet.h9cluster.skins.SkinSettings;
import net.adminrunet.h9cluster.skins.SpeedDisplayMode;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;

/** Native Android Canvas evidence for the approved 1920x720 design. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class ApexSportClusterViewTest {
    private static final int W = 1920;
    private static final int H = 720;
    private static final String DESIGN = "tools/assets/apex_sport/";
    private static final String TEMPLATE = "docs/H9_Cluster_Neutral_Design_Template_1920x720/";
    private static final long SAMPLE_WALL_TIME = Instant.parse("2026-10-28T14:32:00Z").toEpochMilli();
    private static final String[] TELEMETRY = {
        "speedKph", "rpm", "fuelLiters", "rangeKm", "coolantC",
        "transmissionTemperatureC", "outsideTemperatureC", "engineFlywheelTorque",
        "instantFuelConsumption", "consumptionLitersPer100Km", "voltage",
        "odometerKm", "dayKm", "tripKm", "steeringAngleDeg",
        "tyreFrontLeftBar", "tyreFrontRightBar", "tyreRearLeftBar", "tyreRearRightBar",
        "wheelFrontLeftKph", "wheelFrontRightKph", "wheelRearLeftKph", "wheelRearRightKph"
    };
    private TimeZone originalZone;

    @Before
    public void setDeterministicClocks() {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        ShadowSystemClock.advanceBy(Duration.ofSeconds(60));
    }

    @After
    public void restoreTimeZone() {
        TimeZone.setDefault(originalZone);
    }

    @Test
    public void nativeScenariosPreserveStrictFieldsClearanceAndTransparentMap() throws Exception {
        int[] mask = pixels(decode(TEMPLATE + "03_system_icons_forbidden_mask.png"));
        int[] originalBase = pixels(decode(TEMPLATE + "04_yandex_map_black_gradient_rgba.png"));
        boolean[] reserved = new boolean[W * H];
        boolean[] expanded = new boolean[W * H];
        int count = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            if (lowerLeft(x, y) && alpha(mask[y * W + x]) != 0) {
                reserved[y * W + x] = true;
                count++;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    if (x + dx >= 0 && x + dx < W && y + dy >= 0 && y + dy < H) {
                        expanded[(y + dy) * W + x + dx] = true;
                    }
                }
            }
        }
        assertEquals("All fractional-alpha pixels of the real lower-left mask", 40651, count);
        String[] names = {"zero", "motion", "maximum", "long-distances", "negative-temperature", "missing", "stale-atf", "future-atf"};
        for (String name : names) {
            Fixture fixture = scenario(name);
            ApexSportClusterView full = view(true, true);
            ApexSportClusterView foreground = view(false, true);
            ApexSportClusterView critical = view(false, false);
            full.setClusterState(fixture.build());
            foreground.setClusterState(fixture.build());
            critical.setClusterState(fixture.build());
            Bitmap fullFrame = render(full);
            Bitmap foregroundFrame = render(foreground);
            Bitmap criticalFrame = render(critical);
            int[] complete = pixels(fullFrame);
            int[] fg = pixels(foregroundFrame);
            int[] important = pixels(criticalFrame);
            int approvedDecorativeUnderlay = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (expanded[i]) assertPixel(name + " foreground/06+1px", 0, alpha(fg[i]), x, y);
                if (reserved[i]) assertPixel(name + " original base alpha under06", alpha(originalBase[i]), alpha(complete[i]), x, y);
                if (x >= 560 && x < 1360 && y >= 80) assertPixel(name + " 800px corridor", 0, alpha(fg[i]), x, y);
                if (x >= 672 && x < 1096 && y >= 80) assertPixel(name + " clear core", 0, alpha(complete[i]), x, y);
                if (alpha(mask[i]) != 0 && !gearException(x, y)) {
                    assertPixel(name + " critical/03", 0, alpha(important[i]), x, y);
                    if (alpha(fg[i]) != 0 && !reserved[i]) approvedDecorativeUnderlay++;
                }
            }
            assertTrue("The approved metal bezel may pass under the factory arc", approvedDecorativeUnderlay > 0);
            write(fullFrame, "native-" + name + ".png");
            if ("motion".equals(name)) {
                write(foregroundFrame, "native-foreground.png");
                write(criticalFrame, "native-critical.png");
            }
            fullFrame.recycle();
            foregroundFrame.recycle();
            criticalFrame.recycle();
        }
    }

    @Test
    public void approvedSampleUsesAllTwentySevenPassportSources() throws Exception {
        ApexSportClusterView view = view(false, false);
        view.setClusterState(new Fixture().build());
        Bitmap frame = render(view);
        JSONArray readouts = passport().getJSONArray("readouts");
        assertEquals(27, readouts.length());
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < readouts.length(); i++) {
            JSONObject readout = readouts.getJSONObject(i);
            String source = readout.getString("source");
            assertTrue("Duplicate source " + source, seen.add(source));
            if (source.equals("WifiIndicator")) continue;
            if (source.startsWith("DashboardClock.")) continue;
            assertEquals(source, readout.getString("sample"), view.displayedValue(source));
        }
        for (String source : TELEMETRY) assertTrue(source, seen.contains(source));
        assertEquals("14:32", view.displayedValue("DashboardClock.time"));
        assertEquals("28.10.2026", view.displayedValue("DashboardClock.date"));
        assertTrue(seen.contains("WifiIndicator"));
        assertTrue(seen.contains("currentGear"));
        assertTrue(seen.contains("DashboardClock.time"));
        assertTrue(seen.contains("DashboardClock.date"));
        // The sample watermark belongs to review/Demo upper layers, never the renderer.
        assertBlank(frame, 1400, 0, 1920, 70);
        frame.recycle();
    }

    @Test
    public void noSnapshotAndInvalidDataShowDashesWithoutFabricatedZeros() throws Exception {
        ApexSportClusterView view = view(false, false);
        Bitmap initial = render(view);
        for (String source : TELEMETRY) assertEquals("No snapshot: " + source, "—", view.displayedValue(source));
        assertEquals("", view.displayedValue("currentGear"));
        assertTrue(Float.isNaN(view.indicatedSpeedKph()));
        assertTrue(Float.isNaN(view.displayedRpmValue()));
        assertEquals(-1, view.rpmDigitValue());
        write(initial, "native-no-snapshot.png");
        initial.recycle();

        view.setClusterState(scenario("missing").build());
        Bitmap invalid = render(view);
        for (String source : TELEMETRY) assertEquals("Invalid: " + source, "—", view.displayedValue(source));
        assertEquals("", view.displayedValue("currentGear"));
        assertEquals("—", view.displayedValue("instantUnit"));
        invalid.recycle();

        // A delivered valid zero is genuinely zero, not the missing-data state.
        view.setClusterState(scenario("zero").build());
        Bitmap zero = render(view);
        assertEquals("0", view.displayedValue("speedKph"));
        assertEquals("0", view.displayedValue("rpm"));
        assertEquals("0", view.displayedValue("fuelLiters"));
        zero.recycle();
    }

    @Test
    public void atfFreshnessIsReevaluatedWhenOnlyTheClockAdvances() {
        Fixture fixture = new Fixture();
        ApexSportClusterView view = view(false, false);
        view.setClusterState(fixture.build());
        render(view).recycle();
        assertEquals("76", view.displayedValue("transmissionTemperatureC"));
        ShadowSystemClock.advanceBy(Duration.ofMillis(15000));
        render(view).recycle();
        assertEquals("Exactly fifteen seconds remains valid", "76", view.displayedValue("transmissionTemperatureC"));
        ShadowSystemClock.advanceBy(Duration.ofMillis(1));
        render(view).recycle();
        assertEquals("—", view.displayedValue("transmissionTemperatureC"));

        fixture.atfTimestamp = SystemClock.elapsedRealtime() + 1;
        assertFalse(ApexSportClusterView.isAtfFresh(fixture.build(), SystemClock.elapsedRealtime()));
        fixture.atfTimestamp = 0;
        assertFalse(ApexSportClusterView.isAtfFresh(fixture.build(), SystemClock.elapsedRealtime()));
        fixture.atfTimestamp = SystemClock.elapsedRealtime();
        fixture.atf = Float.POSITIVE_INFINITY;
        assertFalse(ApexSportClusterView.isAtfFresh(fixture.build(), SystemClock.elapsedRealtime()));
    }

    @Test
    public void sharedClockRefreshesTimeAndDateWithoutChangingTelemetryFreshness() {
        AtomicLong wallTime = new AtomicLong(SAMPLE_WALL_TIME);
        ApexSportClusterView view = new ApexSportClusterView(RuntimeEnvironment.getApplication(),
                SpeedDisplayMode.REAL, false, false, wallTime::get);
        layout(view);
        view.setClusterState(new Fixture().build());
        render(view).recycle();
        assertEquals("14:32", view.displayedValue("DashboardClock.time"));
        assertEquals("28.10.2026", view.displayedValue("DashboardClock.date"));
        wallTime.set(Instant.parse("2026-10-29T00:01:00Z").toEpochMilli());
        render(view).recycle();
        assertEquals("00:01", view.displayedValue("DashboardClock.time"));
        assertEquals("29.10.2026", view.displayedValue("DashboardClock.date"));
        assertEquals("Wall-clock jumps must not expire monotonic telemetry", "76",
                view.displayedValue("transmissionTemperatureC"));
        wallTime.set(Instant.parse("2026-10-28T23:59:00Z").toEpochMilli());
        render(view).recycle();
        assertEquals("23:59", view.displayedValue("DashboardClock.time"));
        assertEquals("28.10.2026", view.displayedValue("DashboardClock.date"));
    }

    @Test
    public void globalSpeedModesAffectDigitsAndPointerOnceButNotConsumptionUnits() {
        int[] motionExpected = {86, 90, 91};
        int index = 0;
        for (SpeedDisplayMode mode : SpeedDisplayMode.values()) {
            ApexSportClusterView view = (ApexSportClusterView) SkinRegistry.createRenderer(
                    RuntimeEnvironment.getApplication(), "apexsport", SkinSettings.empty(), mode);
            layout(view);
            Fixture fixture = new Fixture();
            ClusterState raw = fixture.build();
            view.setClusterState(raw);
            render(view).recycle();
            assertEquals(Integer.toString(motionExpected[index]), view.displayedValue("speedKph"));
            assertEquals(motionExpected[index], view.indicatedSpeedKph(), 0);
            assertEquals(86, raw.speedKph);
            for (int speed : new int[]{0, 1, 2}) {
                fixture.speed = speed;
                view.setClusterState(fixture.build());
                render(view).recycle();
                assertEquals(speed <= 1 ? "л/ч" : "л/100 км", view.displayedValue("instantUnit"));
                assertEquals(Math.round(mode.indicatedKph(speed)), view.indicatedSpeedKph(), 0);
                assertEquals("9.7", view.displayedValue("consumptionLitersPer100Km"));
                if (speed == 0) assertEquals("0", view.displayedValue("speedKph"));
            }
            index++;
        }
        assertEquals("—", ApexSportClusterView.instantUnit(-1));
    }

    @Test
    public void selectorOnlyAddsAutomaticDigitsInsideTheFactoryException() {
        ApexSportClusterView view = view(false, false);
        Fixture fixture = new Fixture();
        for (String selector : new String[]{"P", "R", "N", "M", "UNKNOWN", "", null}) {
            fixture.selector = selector;
            for (int ratio : new int[]{0, 1, 4, 8, 9}) {
                fixture.gear = ratio;
                view.setClusterState(fixture.build());
                Bitmap frame = render(view);
                assertEquals("", view.displayedValue("currentGear"));
                assertBlank(frame, 922, 19, 1048, 79);
                // Factory drive-mode area: even ECO/NORMAL must not be duplicated.
                assertBlank(frame, 1428, 668, 1528, 699);
                frame.recycle();
            }
        }
        fixture.selector = "D";
        for (int ratio = -1; ratio <= 10; ratio++) {
            fixture.gear = ratio;
            view.setClusterState(fixture.build());
            Bitmap frame = render(view);
            boolean valid = ratio >= 1 && ratio <= 8;
            assertEquals(valid ? Integer.toString(ratio) : "", view.displayedValue("currentGear"));
            int pixelsInException = 0;
            for (int y = 19; y < 79; y++) for (int x = 922; x < 1048; x++) {
                if (Color.alpha(frame.getPixel(x, y)) == 0) continue;
                assertTrue("Gear pixel outside narrow exception", gearException(x, y));
                pixelsInException++;
            }
            assertEquals(valid, pixelsInException > 0);
            frame.recycle();
        }
    }

    @Test
    public void scaleControlPointsAndSweptPointersMatchTheIndependentPassport() throws Exception {
        JSONArray scales = passport().getJSONArray("scales");
        int[] mask = pixels(decode(TEMPLATE + "03_system_icons_forbidden_mask.png"));
        assertEquals(4, scales.length());
        for (int i = 0; i < scales.length(); i++) {
            JSONObject scale = scales.getJSONObject(i);
            float cx = (float) scale.getJSONArray("center").getDouble(0);
            float cy = (float) scale.getJSONArray("center").getDouble(1);
            float radius = (float) scale.getDouble("radius");
            float min = (float) scale.getDouble("min"), max = (float) scale.getDouble("max");
            float start = (float) scale.getDouble("startDeg"), sweep = (float) scale.getDouble("sweepDeg");
            JSONArray calibration = scale.getJSONArray("calibration");
            for (int j = 0; j < calibration.length(); j++) {
                JSONObject anchor = calibration.getJSONObject(j);
                float[] point = ApexSportClusterView.scalePoint(cx, cy, radius,
                        (float) anchor.getDouble("value"), min, max, start, sweep);
                assertEquals(scale.getString("source") + " calibration X", anchor.getJSONArray("xy").getDouble(0), point[0], .002);
                assertEquals(scale.getString("source") + " calibration Y", anchor.getJSONArray("xy").getDouble(1), point[1], .002);
            }
            assertEquals(start, ApexSportClusterView.scaleAngle(min - 100, min, max, start, sweep), .001);
            assertEquals(start + sweep, ApexSportClusterView.scaleAngle(max + 100, min, max, start, sweep), .001);
            assertTrue(Float.isNaN(ApexSportClusterView.scaleAngle(Float.NaN, min, max, start, sweep)));
            // Probe the whole swept path, including pointer stroke, rather than just labelled ticks.
            float outerRadius = i < 2 ? 122 : 63;
            for (int step = 0; step <= 540; step++) {
                float value = min + (max - min) * step / 540;
                float angle = ApexSportClusterView.scaleAngle(value, min, max, start, sweep);
                float[] point = ApexSportClusterView.scalePoint(cx, cy, outerRadius, angle);
                int x = Math.round(point[0]), y = Math.round(point[1]);
                assertEquals(scale.getString("source") + " swept pointer enters field03", 0, alpha(mask[y * W + x]));
            }
        }
    }

    @Test
    public void longAndSignedReadoutsStayWithinTheirPassportBounds() throws Exception {
        Object[][] changes = {
            {"odometerKm", 9999999.0}, {"dayKm", 9999.9f}, {"tripKm", 9999.9f},
            {"rangeKm", 9999}, {"outsideTemperatureC", -50f}, {"transmissionTemperatureC", -40f},
            {"engineFlywheelTorque", -2000f}, {"instantFuelConsumption", 199.9f},
            {"consumptionLitersPer100Km", 199.9f}, {"voltage", 99.9f},
            {"tyreFrontLeftBar", 10f}, {"tyreFrontRightBar", 10f}, {"tyreRearLeftBar", 10f}, {"tyreRearRightBar", 10f},
            {"wheelFrontLeftKph", 350f}, {"wheelFrontRightKph", 350f}, {"wheelRearLeftKph", 350f}, {"wheelRearRightKph", 350f}
        };
        for (Object[] change : changes) {
            String source = (String) change[0];
            ApexSportClusterView view = view(false, false);
            Fixture fixture = new Fixture();
            view.setClusterState(fixture.build());
            Bitmap before = render(view);
            fixture.set(source, (Number) change[1]);
            view.setClusterState(fixture.build());
            Bitmap after = render(view);
            JSONArray bounds = readout(source).getJSONArray("bounds");
            int changed = 0;
            int[] oldPixels = pixels(before), newPixels = pixels(after);
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                if (oldPixels[y * W + x] == newPixels[y * W + x]) continue;
                assertTrue(source + " altered pixels outside passport at " + x + "," + y,
                        x >= Math.floor(bounds.getDouble(0)) && x < Math.ceil(bounds.getDouble(2))
                                && y >= Math.floor(bounds.getDouble(1)) && y < Math.ceil(bounds.getDouble(3)));
                changed++;
            }
            assertTrue(source + " must actually change the rendered numeric value", changed > 20);
            before.recycle();
            after.recycle();
        }
    }

    @Test
    public void enlargedTypographyIsRecordedAndSmallCaptionsRemainReadableInNativePixels() throws Exception {
        Object[][] expectedSizes = {
            {"speedKph", 93}, {"rpm", 73}, {"currentGear", 44},
            {"fuelLiters", 43}, {"rangeKm", 35}, {"coolantC", 43},
            {"transmissionTemperatureC", 35}, {"outsideTemperatureC", 31},
            {"engineFlywheelTorque", 33}, {"instantFuelConsumption", 39},
            {"consumptionLitersPer100Km", 39}, {"voltage", 31},
            {"odometerKm", 28}, {"dayKm", 26}, {"tripKm", 26},
            {"steeringAngleDeg", 33}, {"DashboardClock.time", 35},
            {"DashboardClock.date", 25}
        };
        for (Object[] expected : expectedSizes) {
            assertEquals("Requested font size for " + expected[0], (int) expected[1],
                    readout((String) expected[0]).getInt("fontSizePx"));
        }
        for (String source : TELEMETRY) {
            JSONObject field = readout(source);
            if (source.startsWith("tyre") || source.startsWith("wheel")) {
                assertEquals(source, 29, field.getInt("fontSizePx"));
            }
            JSONObject typography = field.getJSONObject("typography");
            if (typography.has("caption")) {
                assertEquals(source + " caption size", source.equals("dayKm") || source.equals("tripKm") ? 12 : 14,
                        typography.getJSONObject("caption").getInt("fontSizePx"));
            }
        }

        ApexSportClusterView view = view(false, false);
        view.setClusterState(new Fixture().build());
        Bitmap frame = render(view);
        // These are isolated caption regions in the native foreground, with no
        // backdrop/chrome to inflate their visible ink measurements.
        assertMinimumInkHeight(frame, "bar", 1560, 526, 1684, 549, 11);
        // Nominal font size includes font metrics; the glyph ink is shorter.
        assertMinimumInkHeight(frame, "wheel km/h", 1704, 526, 1825, 549, 11);
        assertMinimumInkHeight(frame, "КОЛЕСО", 1395, 526, 1512, 550, 10);
        assertMinimumInkHeight(frame, "РУЛЬ", 1715, 487, 1786, 509, 10);
        assertMinimumInkHeight(frame, "ATF", 1385, 300, 1523, 318, 11);
        assertMinimumInkHeight(frame, "speed km/h", 174, 357, 282, 382, 12);
        write(frame, "native-typography-critical.png");
        frame.recycle();
    }

    @Test
    public void dayAndTripKeepVisibleGapsAtZeroAndMaximumDistance() throws Exception {
        for (float distance : new float[]{0f, 9999.9f}) {
            Fixture fixture = scenario("long-distances");
            fixture.day = distance;
            fixture.trip = distance;
            ApexSportClusterView view = view(false, false);
            view.setClusterState(fixture.build());
            Bitmap frame = render(view);
            int[][] numberInk = new int[2][];
            for (int row = 0; row < 2; row++) {
                int top = row == 0 ? 649 : 681;
                int bottom = row == 0 ? 680 : 718;
                String name = row == 0 ? "DAY" : "TRIP";
                int[] label = inkBounds(frame, 390, top, 442, bottom);
                int[] number = inkBounds(frame, 442, top, 538, bottom);
                int[] unit = inkBounds(frame, 538, top, 560, bottom);
                assertTrue(name + " label must be visible", label[4] > 20);
                assertTrue(name + " value must be visible", number[4] > 35);
                assertTrue(name + " km unit must be visible", unit[4] > 10);
                boolean brightUnitInk = false;
                for (int y = top; y < bottom; y++) for (int x = 538; x < 560; x++) {
                    int pixel = frame.getPixel(x, y);
                    if (Color.alpha(pixel) >= 128 && Color.red(pixel) >= 230
                            && Color.green(pixel) >= 230 && Color.blue(pixel) >= 230) {
                        brightUnitInk = true;
                    }
                }
                assertTrue(name + " km uses bright ink at the light-map edge", brightUnitInk);
                assertTrue(name + " label/value ink gap for " + distance,
                        number[0] - label[2] - 1 >= 8);
                assertTrue(name + " value/unit ink gap for " + distance,
                        unit[0] - number[2] - 1 >= 4);
                assertTrue(name + " label belongs beside the number, not on top of it",
                        Math.abs(label[3] - number[3]) <= 4);
                assertTrue(name + " km unit belongs beside the number",
                        Math.abs(unit[3] - number[3]) <= 4);
                // Rajdhani at 26 px produces 17 px of digit ink on native Canvas.
                assertTrue(name + " number must remain readable", number[3] - number[1] + 1 >= 17);
                numberInk[row] = number;
            }
            assertTrue("DAY and TRIP rows retain vertical breathing room",
                    numberInk[1][1] - numberInk[0][3] - 1 >= 8);
            write(frame, distance == 0 ? "native-distance-zero-critical.png" : "native-distance-max-critical.png");
            frame.recycle();
        }
    }

    @Test
    public void rpmNeedleAnimatesWhileDigitsFollowSharedHysteresisAndCadence() throws Exception {
        ApexSportClusterView view = view(false, false);
        Fixture fixture = new Fixture();
        fixture.rpm = 0;
        view.setClusterState(fixture.build());
        render(view).recycle();
        RpmReadoutStabilizer oracle = new RpmReadoutStabilizer();
        assertEquals(oracle.update(0, SystemClock.elapsedRealtime()), view.rpmDigitValue());
        int previousDigits = 0;
        long lastDigitChange = SystemClock.elapsedRealtime();
        boolean sawIntermediate = false, sawHeldDigitsWithMovingNeedle = false;
        int exported = 0;
        for (int target : new int[]{800, 8000, 1200, 0}) {
            fixture.rpm = target;
            ClusterState raw = fixture.build();
            view.setClusterState(raw);
            float previousNeedle = view.displayedRpmValue();
            int count = target == 0 ? 100 : 38;
            for (int frame = 0; frame < count; frame++) {
                ShadowSystemClock.advanceBy(Duration.ofMillis(20));
                Bitmap bitmap = render(view);
                float animated = view.displayedRpmValue();
                int digits = view.rpmDigitValue();
                long now = SystemClock.elapsedRealtime();
                assertEquals("Digits must use the shared stabilizer", oracle.update(animated, now), digits);
                assertEquals(0, digits % 10);
                if (digits != previousDigits) {
                    assertTrue("Nonzero digits may not change more often than160ms", animated == 0 || now - lastDigitChange >= 160);
                    previousDigits = digits;
                    lastDigitChange = now;
                } else if (Math.abs(animated - previousNeedle) > .5f) {
                    sawHeldDigitsWithMovingNeedle = true;
                }
                if (animated > 0 && Math.abs(animated - target) > 1) sawIntermediate = true;
                if (target > previousNeedle) assertTrue(animated >= previousNeedle && animated <= target);
                if (target < previousNeedle) assertTrue(animated <= previousNeedle && animated >= target);
                if (frame == 0 || frame == 8 || frame == count - 1) {
                    write(bitmap, "rpm-" + target + "-frame-" + frame + ".png");
                    exported++;
                }
                assertEquals("Display filtering must not mutate raw telemetry", target, raw.rpm);
                previousNeedle = animated;
                bitmap.recycle();
            }
        }
        assertTrue(sawIntermediate);
        assertTrue(sawHeldDigitsWithMovingNeedle);
        assertEquals(0, view.displayedRpmValue(), .001f);
        assertEquals(0, view.rpmDigitValue());
        assertEquals(12, exported);
    }

    @Test
    public void calibratedSourceAndArtworkRemainSeparateFromReviewMasksAndMaps() throws Exception {
        File asset = projectFile("app/src/main/assets/dashboard/skins/apexsport/map_black_gradient.png");
        assertEquals("c21978f23e6bcbcdbb255c8ee3b0599ee4494310741eeb84deb1298ff0957260", sha256(asset));
        assertEquals(sha256(projectFile(TEMPLATE + "04_yandex_map_black_gradient_rgba.png")), sha256(asset));
        assertEquals("654f12772eecdf9cd5f581387acb964d5f06a15d13b7e36fb1f2b96a498f47a2",
                sha256(projectFile(TEMPLATE + "03_system_icons_forbidden_mask.png")));
        String chrome = new String(Files.readAllBytes(projectFile(DESIGN + "chrome.svg").toPath()), StandardCharsets.UTF_8);
        assertFalse("Static artwork has no baked text", chrome.contains("<text"));
        File[] assets = asset.getParentFile().listFiles();
        assertNotNull(assets);
        for (File file : assets) {
            assertFalse("No technical validators in APK assets: " + file.getName(), file.getName().matches("0[23567]_.*"));
            assertFalse("No mockup maps in APK assets", file.getName().startsWith("map-dark") || file.getName().startsWith("map-light"));
        }
    }

    private static ApexSportClusterView view(boolean backdrop, boolean chrome) {
        ApexSportClusterView view = new ApexSportClusterView(RuntimeEnvironment.getApplication(),
                SpeedDisplayMode.REAL, backdrop, chrome, () -> SAMPLE_WALL_TIME);
        layout(view);
        return view;
    }

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, W, H);
    }

    private static Bitmap render(View view) {
        Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        view.draw(new Canvas(bitmap));
        assertEquals(W, bitmap.getWidth());
        assertEquals(H, bitmap.getHeight());
        return bitmap;
    }

    private static int[] pixels(Bitmap bitmap) {
        int[] pixels = new int[W * H];
        bitmap.getPixels(pixels, 0, W, 0, 0, W, H);
        return pixels;
    }

    private static int alpha(int pixel) { return pixel >>> 24; }

    private static void assertMinimumInkHeight(Bitmap bitmap, String name,
            int left, int top, int right, int bottom, int minimumHeight) {
        int[] ink = inkBounds(bitmap, left, top, right, bottom);
        assertTrue(name + " must have visible native glyphs", ink[4] > 20);
        assertTrue(name + " native ink height must be at least " + minimumHeight,
                ink[3] - ink[1] + 1 >= minimumHeight);
    }

    /** Inclusive ink bounds and pixel count; even faint antialiasing consumes clearance. */
    private static int[] inkBounds(Bitmap bitmap, int left, int top, int right, int bottom) {
        int[] result = {right, bottom, left - 1, top - 1, 0};
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
            if (Color.alpha(bitmap.getPixel(x, y)) == 0) continue;
            result[0] = Math.min(result[0], x);
            result[1] = Math.min(result[1], y);
            result[2] = Math.max(result[2], x);
            result[3] = Math.max(result[3], y);
            result[4]++;
        }
        return result;
    }

    private static boolean lowerLeft(int x, int y) {
        return (x >= 387 && x < 498 && y >= 373 && y < 442) || (x >= 230 && x < 548 && y >= 494 && y < 642);
    }
    private static boolean gearException(int x, int y) { return x >= 982 && x < 1018 && y >= 28 && y < 66; }

    private static void assertPixel(String name, int expected, int actual, int x, int y) {
        if (expected != actual) fail(name + " at(" + x + "," + y + "): expected alpha" + expected + " but was" + actual);
    }

    private static void assertBlank(Bitmap bitmap, int left, int top, int right, int bottom) {
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) assertPixel("Factory-owned region", 0, Color.alpha(bitmap.getPixel(x, y)), x, y);
    }

    private static File projectFile(String relative) {
        File file = new File(relative);
        return file.isFile() ? file : new File("../" + relative);
    }

    private static Bitmap decode(String relative) {
        Bitmap bitmap = BitmapFactory.decodeFile(projectFile(relative).getAbsolutePath());
        assertNotNull(relative, bitmap);
        assertEquals(W, bitmap.getWidth());
        assertEquals(H, bitmap.getHeight());
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        return bitmap;
    }

    private static JSONObject passport() throws Exception {
        return new JSONObject(new String(Files.readAllBytes(projectFile(DESIGN + "layout.json").toPath()), StandardCharsets.UTF_8));
    }

    private static JSONObject readout(String source) throws Exception {
        JSONArray readouts = passport().getJSONArray("readouts");
        for (int i = 0; i < readouts.length(); i++) if (source.equals(readouts.getJSONObject(i).getString("source"))) return readouts.getJSONObject(i);
        throw new AssertionError("Missing passport field " + source);
    }

    private static void write(Bitmap bitmap, String name) throws Exception {
        File file = new File("build/reports/apexsport", name);
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        try (FileOutputStream stream = new FileOutputStream(file)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)); }
    }

    private static String sha256(File file) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
        StringBuilder result = new StringBuilder();
        for (byte value : hash) result.append(String.format(java.util.Locale.US, "%02x", value & 255));
        return result.toString();
    }

    private static Fixture scenario(String name) {
        Fixture f = new Fixture();
        if ("zero".equals(name)) {
            f.speed = 0; f.rpm = 0; f.gear = 0; f.selector = "P"; f.fuel = 0; f.range = 0;
            f.odo = 0; f.day = 0; f.trip = 0; f.instant = 0; f.torque = 0; f.steering = 0;
            for (int i = 0; i < 4; i++) f.wheels[i] = 0;
        } else if ("maximum".equals(name)) {
            f.speed = 350; f.rpm = 8000; f.gear = 8; f.fuel = 80; f.coolant = 130;
            f.atf = 140; f.outside = 60; f.torque = 2000; f.steering = 1080;
            for (int i = 0; i < 4; i++) f.wheels[i] = 350;
        } else if ("long-distances".equals(name)) {
            f.odo = 9999999; f.day = 9999.9f; f.trip = 9999.9f; f.range = 9999;
        } else if ("negative-temperature".equals(name)) {
            f.outside = -50; f.coolant = -40; f.atf = -40; f.steering = -1080; f.torque = -2000;
        } else if ("missing".equals(name)) {
            f.speed = -1; f.rpm = -1; f.gear = -1; f.selector = null; f.coolant = Integer.MIN_VALUE;
            f.atf = Float.NaN; f.fuel = Float.NaN; f.range = -1; f.odo = Double.NaN;
            f.day = Float.POSITIVE_INFINITY; f.trip = -1; f.outside = Float.NEGATIVE_INFINITY;
            f.instant = Float.NaN; f.average = -1; f.voltage = Float.POSITIVE_INFINITY;
            f.steering = Float.NaN; f.torque = Float.NaN;
            f.pressures[0] = Float.NaN; f.pressures[1] = Float.POSITIVE_INFINITY; f.pressures[2] = -1; f.pressures[3] = Float.NEGATIVE_INFINITY;
            f.wheels[0] = Float.NaN; f.wheels[1] = Float.POSITIVE_INFINITY; f.wheels[2] = -1; f.wheels[3] = Float.NEGATIVE_INFINITY;
        } else if ("stale-atf".equals(name)) {
            f.atfTimestamp = SystemClock.elapsedRealtime() - 15001;
        } else if ("future-atf".equals(name)) {
            f.atfTimestamp = SystemClock.elapsedRealtime() + 1;
        }
        return f;
    }

    private static final class Fixture {
        int speed = 86, rpm = 2640, gear = 6, coolant = 88, range = 540;
        String selector = "D";
        float atf = 76, fuel = 54, day = 42.8f, trip = 186.4f;
        double odo = 124680;
        float[] pressures = {2.35f, 2.36f, 2.32f, 2.34f};
        float[] wheels = {86, 86.2f, 85.9f, 86.1f};
        float instant = 8.4f, average = 9.7f, voltage = 14.2f, outside = 12.5f, steering = 12, torque = 248;
        long atfTimestamp = SystemClock.elapsedRealtime();

        ClusterState build() {
            long now = SystemClock.elapsedRealtime();
            return new ClusterState(speed, rpm, gear, selector, coolant, atf, fuel, range,
                    odo, day, trip, pressures[0], pressures[1], pressures[2], pressures[3],
                    instant, average, 37.5f, voltage, outside, steering,
                    wheels[0], wheels[1], wheels[2], wheels[3], torque,
                    now, now, now, now, atfTimestamp, "ECO");
        }

        void set(String source, Number value) {
            switch (source) {
                case "odometerKm": odo = value.doubleValue(); break;
                case "dayKm": day = value.floatValue(); break;
                case "tripKm": trip = value.floatValue(); break;
                case "rangeKm": range = value.intValue(); break;
                case "outsideTemperatureC": outside = value.floatValue(); break;
                case "transmissionTemperatureC": atf = value.floatValue(); break;
                case "engineFlywheelTorque": torque = value.floatValue(); break;
                case "instantFuelConsumption": instant = value.floatValue(); break;
                case "consumptionLitersPer100Km": average = value.floatValue(); break;
                case "voltage": voltage = value.floatValue(); break;
                case "tyreFrontLeftBar": pressures[0] = value.floatValue(); break;
                case "tyreFrontRightBar": pressures[1] = value.floatValue(); break;
                case "tyreRearLeftBar": pressures[2] = value.floatValue(); break;
                case "tyreRearRightBar": pressures[3] = value.floatValue(); break;
                case "wheelFrontLeftKph": wheels[0] = value.floatValue(); break;
                case "wheelFrontRightKph": wheels[1] = value.floatValue(); break;
                case "wheelRearLeftKph": wheels[2] = value.floatValue(); break;
                case "wheelRearRightKph": wheels[3] = value.floatValue(); break;
                default: throw new AssertionError("Unsupported fixture field " + source);
            }
        }
    }
}
