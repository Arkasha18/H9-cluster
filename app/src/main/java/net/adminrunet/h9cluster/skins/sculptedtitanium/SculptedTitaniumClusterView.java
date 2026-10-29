package net.adminrunet.h9cluster.skins.sculptedtitanium;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import net.adminrunet.h9cluster.BuildConfig;
import net.adminrunet.h9cluster.ClusterRenderer;
import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.GearSelector;
import net.adminrunet.h9cluster.RpmReadoutStabilizer;
import net.adminrunet.h9cluster.TransmissionTemperatureAlert;
import net.adminrunet.h9cluster.skins.DashboardClock;
import net.adminrunet.h9cluster.skins.SpeedDisplayMode;
import net.adminrunet.h9cluster.skins.WifiIndicator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Approved titanium artwork with independently rendered, live 1920x720 readouts. */
public final class SculptedTitaniumClusterView extends View implements ClusterRenderer {
    private boolean gearNumberVisible = true;
    private static final int WHITE = 0xFFF0EEE7;
    private static final int MUTED = 0xFFA4B0B5;
    private static final int AMBER = 0xFFFFB153;
    private static final int WARNING = 0xFFFFD54F;
    private static final int HOT = 0xFFFF8A3D;
    private static final int CRITICAL = 0xFFFF4D4D;
    private static final long ATF_STALE_MS = 15_000L;
    private static final String ASSETS = "dashboard/skins/sculptedtitanium/";
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wifiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Path pointer = new Path();
    private final Bitmap staticBase;
    private final boolean includeBackdrop;
    private final SpeedDisplayMode speedDisplayMode;
    private final RpmReadoutStabilizer rpmReadout = new RpmReadoutStabilizer();
    private final DashboardClock clock = new DashboardClock();
    private final WifiIndicator wifi;
    private final TransmissionTemperatureAlert atfAlert = new TransmissionTemperatureAlert();
    private ClusterState state = ClusterState.empty();
    private String speed, rpm, gear, outside, instant, average, instantUnit;
    private String range, fuel, odometer, day, trip, coolant, voltage, steering, torque;
    private final String[] pressures = new String[4];
    private final String[] wheels = new String[4];
    private float indicatedSpeed;
    private float displayedRpm;
    private int rpmDigits = -1;
    private long lastRpmFrameMs;
    private boolean rpmInitialized;

    public SculptedTitaniumClusterView(Context context) {
        this(context, SpeedDisplayMode.REAL, true);
    }

    public SculptedTitaniumClusterView(Context context, SpeedDisplayMode speedDisplayMode) {
        this(context, speedDisplayMode, true);
    }

    // Tests render the full artwork without the calibrated background to check
    // actual alpha under reserved zones. No technical mask is loaded by this view.
    SculptedTitaniumClusterView(Context context, boolean includeBackdrop) {
        this(context, SpeedDisplayMode.REAL, includeBackdrop);
    }

    private SculptedTitaniumClusterView(Context context, SpeedDisplayMode speedDisplayMode,
            boolean includeBackdrop) {
        super(context);
        this.includeBackdrop = includeBackdrop;
        this.speedDisplayMode = speedDisplayMode == null ? SpeedDisplayMode.REAL : speedDisplayMode;
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(BuildConfig.DEMO_MODE ? LAYER_TYPE_NONE : LAYER_TYPE_HARDWARE, null);
        wifi = new WifiIndicator(context);
        staticBase = buildStaticBase(context);
        updateReadouts();
    }

    @Override
    public void setGearNumberVisible(boolean visible) {
        gearNumberVisible = visible;
        invalidate();
    }

    @Override
    public void setClusterState(ClusterState snapshot) {
        if (snapshot == null) return;
        state = snapshot;
        updateReadouts();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (BuildConfig.DEMO_MODE && includeBackdrop) canvas.drawColor(Color.BLACK);
        else canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        float scale = Math.min(getWidth() / 1920.0f, getHeight() / 720.0f);
        int save = canvas.save();
        canvas.translate((getWidth() - 1920 * scale) / 2, (getHeight() - 720 * scale) / 2);
        canvas.scale(scale, scale);
        canvas.drawBitmap(staticBase, 0, 0, bitmapPaint);
        long now = SystemClock.elapsedRealtime();
        boolean rpmMoving = updateRpm(now);
        drawHeader(canvas, now);
        drawGauges(canvas);
        drawTelemetry(canvas, now);
        canvas.restoreToCount(save);
        // Vehicle updates request their own frames. This tick serves the clock,
        // Wi-Fi state and ATF freshness even when the vehicle bus is quiet.
        if (rpmMoving) postInvalidateOnAnimation();
        else postInvalidateDelayed(1000L);
    }

    private boolean updateRpm(long nowMs) {
        float target = Math.max(0, Math.min(8000, state.rpm));
        if (!rpmInitialized || nowMs < lastRpmFrameMs) {
            rpmInitialized = true;
            displayedRpm = target;
        } else {
            float elapsed = Math.min(100L, nowMs - lastRpmFrameMs);
            float timeConstant = target < displayedRpm - 350 ? 100.0f : 140.0f;
            displayedRpm += (target - displayedRpm) * (1 - (float) Math.exp(-elapsed / timeConstant));
            if (Math.abs(target - displayedRpm) < 1) displayedRpm = target;
        }
        lastRpmFrameMs = nowMs;
        int value = rpmReadout.update(displayedRpm, nowMs);
        if (value != rpmDigits) {
            rpmDigits = value;
            rpm = Integer.toString(value);
        }
        return Math.abs(target - displayedRpm) >= 1 || rpmReadout.needsUpdate(displayedRpm);
    }

    private void drawHeader(Canvas canvas, long now) {
        clock.update(System.currentTimeMillis());
        text(canvas, clock.getTimeText(), 84, 58, 40, 116, Paint.Align.LEFT, WHITE, true);
        text(canvas, clock.getDateText(), 210, 56, 20, 124, Paint.Align.LEFT, MUTED, false);
        wifi.draw(canvas, wifiPaint, 872, 45, now);
        label(canvas, "СНАРУЖИ", 1825, 28, 16, 158);
        text(canvas, outside, 1825, 64, 31, 162, Paint.Align.CENTER, WHITE, true);
        // The car owns the selector/manual caption. Demo adds that caption in
        // its own upper system layer, never in the production skin.
        if (gearNumberVisible && !gear.isEmpty()) {
            text(canvas, gear, 1000, 63, 44, 32, Paint.Align.CENTER, WHITE, true);
        }
    }

    private void drawGauges(Canvas canvas) {
        gauge(canvas, 130, 404, indicatedSpeed / 220.0f, false);
        gauge(canvas, 1384, 404, displayedRpm / 8000.0f, true);
        text(canvas, speed, 334, 349, 70, 250, Paint.Align.CENTER, WHITE, true);
        text(canvas, rpm, 1586, 349, 70, 280, Paint.Align.CENTER, WHITE, true);
        label(canvas, "км/ч", 334, 363, 13, 70);
        label(canvas, "об/мин", 1586, 363, 13, 90);
        label(canvas, "СКОРОСТЬ", 178, 325, 12, 85);
        label(canvas, "ОБОРОТЫ", 1752, 325, 12, 80);
    }

    private void gauge(Canvas canvas, float x, float width, float proportion, boolean tachometer) {
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(0xFF38434A);
        canvas.drawRoundRect(x, 286, x + width, 292, 1, 1, shapePaint);
        if (tachometer) {
            shapePaint.setColor(0xFF8B3C30);
            canvas.drawRect(x + width * .8125f, 286, x + width, 292, shapePaint);
        }
        float end = x + width * clamp(proportion);
        shapePaint.setColor(AMBER);
        if (end > x) canvas.drawRoundRect(x, 286, end, 292, 1, 1, shapePaint);
        shapePaint.setColor(MUTED);
        shapePaint.setStrokeWidth(1);
        int divisions = tachometer ? 8 : 11;
        for (int i = 0; i <= divisions; i++) {
            float tick = x + width * i / divisions;
            canvas.drawLine(tick, 282, tick, 286, shapePaint);
            if (tachometer || i % 2 == 0 || i == divisions) {
                label(canvas, Integer.toString(tachometer ? i : i * 20), tick, 277, 11, 28);
            }
        }
        shapePaint.setColor(WHITE);
        pointer.reset();
        pointer.moveTo(end - 4, 296);
        pointer.lineTo(end, 291);
        pointer.lineTo(end + 4, 296);
        pointer.close();
        canvas.drawPath(pointer, shapePaint);
    }

    private void drawTelemetry(Canvas canvas, long now) {
        label(canvas, "ДАВЛЕНИЕ ШИН", 461, 172, 16, 172);
        label(canvas, "bar", 522, 192, 13, 34);
        label(canvas, "СКОРОСТЬ КОЛЁС", 1462, 172, 16, 178);
        label(canvas, "км/ч", 1646, 192, 13, 40);
        String[] positions = {"FL", "FR", "RL", "RR"};
        for (int i = 0; i < 4; i++) {
            float y = i < 2 ? 220 : 252;
            label(canvas, positions[i], i % 2 == 0 ? 255 : 409, y - 3, 14, 24);
            label(canvas, positions[i], i % 2 == 0 ? 1387 : 1543, y - 3, 14, 24);
        }
        pressure(canvas, pressures[0], 324, 220, state.tyreFrontLeftBar);
        pressure(canvas, pressures[1], 480, 220, state.tyreFrontRightBar);
        pressure(canvas, pressures[2], 324, 252, state.tyreRearLeftBar);
        pressure(canvas, pressures[3], 480, 252, state.tyreRearRightBar);
        wheel(canvas, wheels[0], 1458, 220, state.wheelFrontLeftKph);
        wheel(canvas, wheels[1], 1614, 220, state.wheelFrontRightKph);
        wheel(canvas, wheels[2], 1458, 252, state.wheelRearLeftKph);
        wheel(canvas, wheels[3], 1614, 252, state.wheelRearRightKph);
        label(canvas, "МГНОВЕННЫЙ", 165, 422, 14, 128);
        label(canvas, "СРЕДНИЙ", 304, 422, 14, 125);
        text(canvas, instant, 165, 458, 34, 124, Paint.Align.CENTER, WHITE, true);
        text(canvas, average, 304, 458, 34, 120, Paint.Align.CENTER, WHITE, true);
        label(canvas, instantUnit, 165, 481, 15, 124);
        label(canvas, "л/100 км", 304, 481, 15, 120);
        label(canvas, "ЗАПАС ХОДА", 157, 524, 13, 111);
        text(canvas, range, 157, 551, 31, 112, Paint.Align.CENTER, WHITE, true);
        label(canvas, "км", 157, 568, 13, 50);
        label(canvas, "ТОПЛИВО", 157, 590, 13, 111);
        text(canvas, fuel + " л", 157, 624, 31, 110, Paint.Align.CENTER,
                validNonnegative(state.fuelLiters) && state.fuelLiters < 10 ? WARNING : WHITE, true);
        label(canvas, "ОДОМЕТР · км", 190, 674, 13, 174);
        label(canvas, "ДЕНЬ · км", 350, 674, 13, 116);
        label(canvas, "ПОЕЗДКА · км", 482, 674, 13, 116);
        text(canvas, odometer, 190, 701, 27, 172, Paint.Align.CENTER, WHITE, true);
        text(canvas, day, 350, 701, 27, 112, Paint.Align.CENTER, WHITE, true);
        text(canvas, trip, 482, 701, 27, 112, Paint.Align.CENTER, WHITE, true);
        label(canvas, "ОХЛАЖДАЮЩАЯ ЖИДКОСТЬ", 1626, 430, 15, 340);
        label(canvas, "АКПП / ATF", 1626, 500, 15, 340);
        label(canvas, "НАПРЯЖЕНИЕ", 1626, 570, 15, 340);
        text(canvas, coolant, 1626, 463, 33, 296, Paint.Align.CENTER,
                state.coolantC >= 115 ? WARNING : WHITE, true);
        boolean fresh = isAtfFresh(state, now);
        TransmissionTemperatureAlert.Level level = atfAlert.update(state.transmissionTemperatureC, fresh);
        text(canvas, formatAtf(state, now), 1626, 533, 33, 296, Paint.Align.CENTER,
                atfColor(level), true);
        text(canvas, voltage, 1626, 603, 33, 296, Paint.Align.CENTER,
                validNonnegative(state.voltage) && state.voltage < 12 ? WARNING : WHITE, true);
        meter(canvas, 472, state.coolantC / 140.0f);
        meter(canvas, 542, fresh ? state.transmissionTemperatureC / 140 : Float.NaN);
        meter(canvas, 610, validNonnegative(state.voltage) ? (state.voltage - 10) / 5 : Float.NaN);
        label(canvas, "РУЛЬ", 1626, 655, 14, 148);
        label(canvas, "МОМЕНТ · Н·м", 1819, 655, 14, 158);
        steeringWheel(canvas);
        text(canvas, steering, 1647, 695, 30, 116, Paint.Align.CENTER, WHITE, true);
        text(canvas, torque, 1819, 695, 32, 152, Paint.Align.CENTER, WHITE, true);
    }

    private void meter(Canvas canvas, float y, float value) {
        shapePaint.setStrokeWidth(2);
        shapePaint.setStrokeCap(Paint.Cap.ROUND);
        shapePaint.setColor(0xFF344149);
        canvas.drawLine(1500, y, 1752, y, shapePaint);
        if (Float.isFinite(value)) {
            shapePaint.setColor(AMBER);
            canvas.drawLine(1500, y, 1500 + 252 * clamp(value), y, shapePaint);
        }
        shapePaint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void steeringWheel(Canvas canvas) {
        int save = canvas.save();
        if (Float.isFinite(state.steeringAngleDeg)) canvas.rotate(state.steeringAngleDeg, 1569, 684);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setColor(0xFFB6C0C4);
        shapePaint.setStrokeWidth(1.8f);
        canvas.drawCircle(1569, 684, 14, shapePaint);
        canvas.drawCircle(1569, 684, 3, shapePaint);
        canvas.drawLine(1556, 681, 1566, 684, shapePaint);
        canvas.drawLine(1582, 681, 1572, 684, shapePaint);
        canvas.drawLine(1569, 687, 1569, 697, shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);
        canvas.restoreToCount(save);
    }

    private void pressure(Canvas canvas, String value, float x, float y, float pressure) {
        text(canvas, value, x, y, 31, 98, Paint.Align.CENTER,
                validNonnegative(pressure) && pressure < 2 ? WARNING : WHITE, true);
    }

    private void wheel(Canvas canvas, String value, float x, float y, float speed) {
        text(canvas, value, x, y, 31, 102, Paint.Align.CENTER, wheelColor(state, speed), true);
    }

    private void text(Canvas canvas, String value, float x, float baseline, float size,
            float maxWidth, Paint.Align align, int color, boolean heavy) {
        textPaint.setTypeface(heavy ? bold : regular);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(align);
        textPaint.setColor(color);
        textPaint.setTextSize(size);
        float measured = textPaint.measureText(value);
        if (measured > maxWidth) textPaint.setTextSize(size * maxWidth / measured);
        canvas.drawText(value, x, baseline, textPaint);
    }

    private void label(Canvas canvas, String value, float x, float y, float size, float width) {
        text(canvas, value, x, y, size, width, Paint.Align.CENTER, MUTED, false);
    }

    private void updateReadouts() {
        indicatedSpeed = Math.round(speedDisplayMode.indicatedKph(Math.max(0, state.speedKph)));
        speed = Integer.toString(Math.round(indicatedSpeed));
        gear = currentGearLabel(state.gearSelector, state.currentGear);
        outside = Float.isFinite(state.outsideTemperatureC)
                ? String.format(Locale.US, "%+.1f °C", state.outsideTemperatureC) : "— °C";
        pressures[0] = decimal(state.tyreFrontLeftBar, 2);
        pressures[1] = decimal(state.tyreFrontRightBar, 2);
        pressures[2] = decimal(state.tyreRearLeftBar, 2);
        pressures[3] = decimal(state.tyreRearRightBar, 2);
        wheels[0] = decimal(state.wheelFrontLeftKph, 1);
        wheels[1] = decimal(state.wheelFrontRightKph, 1);
        wheels[2] = decimal(state.wheelRearLeftKph, 1);
        wheels[3] = decimal(state.wheelRearRightKph, 1);
        instant = decimal(state.instantFuelConsumption, 1);
        average = decimal(state.consumptionLitersPer100Km, 1);
        instantUnit = instantUnit(state.speedKph);
        range = state.rangeKm >= 0 ? Integer.toString(state.rangeKm) : "—";
        fuel = validNonnegative(state.fuelLiters) ? Integer.toString(Math.round(state.fuelLiters)) : "—";
        odometer = Double.isFinite(state.odometerKm) && state.odometerKm >= 0
                ? String.format(Locale.US, "%,.0f", state.odometerKm).replace(',', ' ') : "—";
        day = decimal(state.dayKm, 1);
        trip = decimal(state.tripKm, 1);
        coolant = state.coolantC + " °C";
        voltage = decimal(state.voltage, 1) + " V";
        steering = Float.isFinite(state.steeringAngleDeg)
                ? String.format(Locale.US, "%+d°", Math.round(state.steeringAngleDeg)) : "—";
        torque = Float.isFinite(state.engineFlywheelTorque)
                ? String.format(Locale.US, "%.0f", state.engineFlywheelTorque) : "—";
    }

    static String currentGearLabel(String selector, int ratio) {
        return GearSelector.DRIVE.equals(selector) ? GearSelector.ratio(ratio) : "";
    }

    static String instantUnit(int speed) { return speed <= 1 ? "л/ч" : "л/100 км"; }

    static String decimal(float value, int places) {
        return validNonnegative(value) ? String.format(Locale.US, "%." + places + "f", value) : "—";
    }

    static boolean isAtfFresh(ClusterState state, long now) {
        long timestamp = state.transmissionTemperatureUpdatedAtMs;
        return Float.isFinite(state.transmissionTemperatureC) && timestamp > 0
                && now >= timestamp && now - timestamp <= ATF_STALE_MS;
    }

    static String formatAtf(ClusterState state, long now) {
        return isAtfFresh(state, now) ? Math.round(state.transmissionTemperatureC) + " °C" : "— °C";
    }

    private static boolean validNonnegative(float value) { return Float.isFinite(value) && value >= 0; }
    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }

    private static int atfColor(TransmissionTemperatureAlert.Level level) {
        switch (level) {
            case ELEVATED: return WARNING;
            case HOT: return HOT;
            case CRITICAL: return CRITICAL;
            default: return WHITE;
        }
    }

    static int wheelColor(ClusterState state, float speed) {
        float fl = state.wheelFrontLeftKph, fr = state.wheelFrontRightKph;
        float rl = state.wheelRearLeftKph, rr = state.wheelRearRightKph;
        if (!validNonnegative(speed) || !validNonnegative(fl) || !validNonnegative(fr)
                || !validNonnegative(rl) || !validNonnegative(rr)) return WHITE;
        float min = Math.min(Math.min(fl, fr), Math.min(rl, rr));
        float max = Math.max(Math.max(fl, fr), Math.max(rl, rr));
        if (max < 4) return WHITE;
        float median = (fl + fr + rl + rr - min - max) / 2;
        return Math.abs(speed - median) >= Math.max(4, median * .18f) ? WARNING : WHITE;
    }

    private Bitmap buildStaticBase(Context context) {
        Bitmap chrome = loadBitmap(context, "chrome.png");
        if (!includeBackdrop) return chrome;
        Bitmap base = loadBitmap(context, "map_black_gradient.png");
        // The original calibration asset remains untouched. Ease only the top
        // opaque strip into its existing side alpha, ending before the map's
        // clear y=80 aperture. Lower-left technical fields keep their exact base.
        Bitmap softenedBase = base.copy(Bitmap.Config.ARGB_8888, true);
        for (int x = 0; x < 1920; x++) {
            int sideAlpha = Color.alpha(base.getPixel(x, 80));
            for (int y = 40; y < 80; y++) {
                float t = (y - 40) / 40.0f;
                float fade = 1 - t * t * (3 - 2 * t);
                int alpha = Math.round(sideAlpha + (255 - sideAlpha) * fade);
                softenedBase.setPixel(x, y, Color.argb(alpha, 0, 0, 0));
            }
        }
        Bitmap composed = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        composed.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(composed);
        canvas.drawBitmap(softenedBase, 0, 0, bitmapPaint);
        canvas.drawBitmap(chrome, 0, 0, bitmapPaint);
        base.recycle();
        softenedBase.recycle();
        chrome.recycle();
        return composed;
    }

    private static Bitmap loadBitmap(Context context, String filename) {
        try (InputStream stream = context.getAssets().open(ASSETS + filename)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap == null || bitmap.getWidth() != 1920 || bitmap.getHeight() != 720) {
                throw new IllegalStateException("Invalid titanium asset: " + filename);
            }
            bitmap.setDensity(Bitmap.DENSITY_NONE);
            return bitmap;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load titanium asset: " + filename, error);
        }
    }
}
