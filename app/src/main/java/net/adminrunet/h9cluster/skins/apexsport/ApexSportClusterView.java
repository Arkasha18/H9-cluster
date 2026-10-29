package net.adminrunet.h9cluster.skins.apexsport;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import net.adminrunet.h9cluster.ClusterRenderer;
import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.FuelTankCapacity;
import net.adminrunet.h9cluster.GearSelector;
import net.adminrunet.h9cluster.RpmReadoutStabilizer;
import net.adminrunet.h9cluster.TransmissionTemperatureAlert;
import net.adminrunet.h9cluster.skins.DashboardClock;
import net.adminrunet.h9cluster.skins.SpeedDisplayMode;
import net.adminrunet.h9cluster.skins.WifiIndicator;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/** Live APEX SPORT instruments at the anchors in tools/assets/apex_sport/layout.json. */
public final class ApexSportClusterView extends View implements ClusterRenderer {
    private boolean gearNumberVisible = true;
    private static final String ASSETS = "dashboard/skins/apexsport/";
    private static final String DASH = "—";
    private static final int WHITE = 0xFFF1F4F5;
    private static final int MUTED = 0xFF9BA8B3;
    private static final int RED = 0xFFFF493D;
    private static final int BLUE = 0xFF46B6E6;
    private static final int WARNING = 0xFFFFD54F;
    private static final long ATF_STALE_MS = 15_000L;
    private static final String[] POSITIONS = {"FL", "FR", "RL", "RR"};
    private static final String[] PRESSURE_FIELDS = {
            "tyreFrontLeftBar", "tyreFrontRightBar", "tyreRearLeftBar", "tyreRearRightBar"};
    private static final String[] WHEEL_FIELDS = {
            "wheelFrontLeftKph", "wheelFrontRightKph", "wheelRearLeftKph", "wheelRearRightKph"};

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wifiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pointer = new Path();
    private final Path textPath = new Path();
    private final RectF textBounds = new RectF();
    private final RectF arcBounds = new RectF();
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Typeface numeric;
    private final Typeface caption;
    private final Bitmap staticBase;
    private final SpeedDisplayMode speedDisplayMode;
    private final LongSupplier wallClock;
    private final DashboardClock clock = new DashboardClock();
    private final WifiIndicator wifi;
    private final TransmissionTemperatureAlert atfAlert = new TransmissionTemperatureAlert();
    private RpmReadoutStabilizer rpmReadout = new RpmReadoutStabilizer();
    private ClusterState state;
    private float indicatedSpeed = Float.NaN;
    private float displayedRpm = Float.NaN;
    private int rpmDigits = -1;
    private long lastRpmFrameMs;
    private boolean rpmInitialized;

    public ApexSportClusterView(Context context) {
        this(context, SpeedDisplayMode.REAL);
    }

    public ApexSportClusterView(Context context, SpeedDisplayMode speedDisplayMode) {
        this(context, speedDisplayMode, true, true);
    }

    // Layer switches let native tests inspect actual foreground alpha independently.
    // They do not introduce masks, alternate geometry, or a Demo-only backdrop.
    ApexSportClusterView(Context context, SpeedDisplayMode speedDisplayMode,
            boolean includeBackdrop, boolean includeChrome) {
        this(context, speedDisplayMode, includeBackdrop, includeChrome, System::currentTimeMillis);
    }

    ApexSportClusterView(Context context, SpeedDisplayMode speedDisplayMode,
            boolean includeBackdrop, boolean includeChrome, LongSupplier wallClock) {
        super(context);
        this.speedDisplayMode = speedDisplayMode == null ? SpeedDisplayMode.REAL : speedDisplayMode;
        this.wallClock = wallClock;
        numeric = Typeface.createFromAsset(context.getAssets(), "fonts/Rajdhani-Medium.ttf");
        caption = Typeface.createFromAsset(context.getAssets(), ASSETS + "NotoSans.ttf");
        wifi = new WifiIndicator(context);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        staticBase = buildStaticBase(context, includeBackdrop, includeChrome);
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
        updateRpm(SystemClock.elapsedRealtime());
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        float scale = Math.min(getWidth() / 1920.0f, getHeight() / 720.0f);
        int saved = canvas.save();
        canvas.translate((getWidth() - 1920 * scale) / 2, (getHeight() - 720 * scale) / 2);
        canvas.scale(scale, scale);
        if (staticBase != null) canvas.drawBitmap(staticBase, 0, 0, bitmapPaint);
        long now = SystemClock.elapsedRealtime();
        boolean moving = updateRpm(now);
        drawHeader(canvas, now);
        drawMainDial(canvas, false);
        drawMainDial(canvas, true);
        drawSmallDial(canvas, true);
        drawSmallDial(canvas, false);
        drawReadouts(canvas, now);
        canvas.restoreToCount(saved);
        // Clock, Wi-Fi and ATF expiry must refresh even without new bus packets.
        if (moving) postInvalidateOnAnimation();
        else postInvalidateDelayed(1000L);
    }

    private void drawHeader(Canvas canvas, long now) {
        clock.update(wallClock.getAsLong());
        text(canvas, clock.getTimeText(), 1148, 56, 35, 132, WHITE, numeric);
        text(canvas, clock.getDateText(), 788, 54, 25, 165, WHITE, numeric);
        wifi.draw(canvas, wifiPaint, 881, 42, now);
        if (gearNumberVisible) {
            text(canvas, values.get("currentGear"), 1000, 63, 44, 36, WHITE, numeric);
        }
        text(canvas, "APEX", 174, 56, 32, 160, 0xFFD9E1E6, caption);
        text(canvas, "S P O R T", 174, 77, 12, 160, RED, caption);
    }

    private void drawMainDial(Canvas canvas, boolean tachometer) {
        float cx = tachometer ? 1692 : 228;
        float maximum = tachometer ? 8000 : 240;
        int step = tachometer ? 250 : 10;
        int majorStep = tachometer ? 1000 : 40;
        arc(canvas, cx, 330, 121, 135, 270, 0xFF57616B, 1);
        if (tachometer) arc(canvas, cx, 330, 120, 337.5f, 67.5f, 0xFFD93D36, 3);
        for (int value = 0; value <= maximum; value += step) {
            float angle = scaleAngle(value, 0, maximum, 135, 270);
            boolean major = value % majorStep == 0;
            int color = tachometer && value >= 6000 ? RED : major ? 0xFFE1E8EB : 0xFF7B8894;
            radialLine(canvas, cx, 330, major ? 109 : 115, 121, angle, color, major ? 2 : 1);
            if (major) {
                float[] at = scalePoint(cx, 330, 97, angle);
                text(canvas, Integer.toString(tachometer ? value / 1000 : value),
                        at[0], at[1] + 7, 22, 48,
                        tachometer && value >= 6000 ? RED : WHITE, numeric);
            }
        }
        float displayed = tachometer ? displayedRpm : indicatedSpeed;
        if (Float.isFinite(displayed)) {
            float angle = scaleAngle(displayed, 0, maximum, 135, 270);
            arc(canvas, cx, 330, 119, angle - 3.5f, 7, RED, 4);
            float[] tip = scalePoint(cx, 330, 115, angle);
            float[] inner = scalePoint(cx, 330, 89, angle);
            float[] left = scalePoint(cx, 330, 114, angle + 2);
            float[] right = scalePoint(cx, 330, 114, angle - 2);
            pointer.reset();
            pointer.moveTo(tip[0], tip[1]);
            pointer.lineTo(left[0], left[1]);
            pointer.lineTo(inner[0], inner[1]);
            pointer.lineTo(right[0], right[1]);
            pointer.close();
            shapePaint.setStyle(Paint.Style.FILL);
            shapePaint.setColor(RED);
            canvas.drawPath(pointer, shapePaint);
        }
        label(canvas, tachometer ? "×1000" : "APEX", cx, 427, 13, 100);
        label(canvas, tachometer ? "ОБОРОТЫ" : "СКОРОСТЬ", cx, 285, 14, 120);
        label(canvas, tachometer ? "об/мин" : "км/ч", cx, 377, 16, 100);
        if (tachometer) value(canvas, "rpm", cx, 346, 73, 214, WHITE);
        else value(canvas, "speedKph", cx, 349, 93, 208, WHITE);
    }

    private void drawSmallDial(Canvas canvas, boolean fuel) {
        float cx = fuel ? 466 : 1454;
        float minimum = fuel ? 0 : 40;
        float maximum = fuel
                ? (state == null ? FuelTankCapacity.DEFAULT_LITERS : state.fuelTankCapacityLiters)
                : 130;
        arc(canvas, cx, 238, 53, 140, 260, 0xFF3F4A54, 4);
        arc(canvas, cx, 238, 53, fuel ? 140 : 358, fuel ? 260 * 10 / maximum : 42, RED, 4);
        for (int i = 0; i <= 8; i++) {
            radialLine(canvas, cx, 238, 46, 50, 140 + i / 8.0f * 260, 0xFFCCD5DB, 1);
        }
        float reading = Float.NaN;
        if (state != null) {
            if (fuel && validNonnegative(state.fuelLiters)) reading = state.fuelLiters;
            if (!fuel && validCoolant(state.coolantC)) reading = state.coolantC;
        }
        if (Float.isFinite(reading)) {
            float angle = scaleAngle(reading, minimum, maximum, 140, 260);
            if (angle > 140) arc(canvas, cx, 238, 53, 140, angle - 140,
                    fuel ? 0xFFEDF2F5 : BLUE, 4);
            radialLine(canvas, cx, 238, 47, 61, angle, RED, 2.2f);
        }
        label(canvas, fuel ? "E" : "C", cx - 50, 285, 13, 20);
        label(canvas, fuel ? "F" : "H", cx + 50, 285, 13, 20);
        label(canvas, fuel ? "ТОПЛИВО" : "ДВИГАТЕЛЬ", cx, 143, 14, 160);
        label(canvas, fuel ? "л" : "°C", cx, 274, 16, 60);
        int color = WHITE;
        if (Float.isFinite(reading) && (fuel ? reading < 10 : reading >= 115)) color = RED;
        value(canvas, fuel ? "fuelLiters" : "coolantC", cx, 250, 43, 100, color);
    }

    private void drawReadouts(Canvas canvas, long now) {
        values.put("transmissionTemperatureC", formatAtf(state, now));
        label(canvas, "ЗАПАС ХОДА", 466, 316, 14, 152);
        value(canvas, "rangeKm", 466, 343, 35, 152, WHITE);
        label(canvas, "км", 466, 365, 16, 70);
        label(canvas, "ATF / °C", 1454, 316, 14, 135);
        TransmissionTemperatureAlert.Level level = atfAlert.update(
                state == null ? Float.NaN : state.transmissionTemperatureC, isAtfFresh(state, now));
        value(canvas, "transmissionTemperatureC", 1454, 343, 35, 135, atfColor(level));
        label(canvas, "СНАРУЖИ / °C", 1454, 381, 14, 156);
        value(canvas, "outsideTemperatureC", 1454, 412, 31, 156, WHITE);
        label(canvas, "МОМЕНТ / Н·м", 1454, 444, 14, 154);
        value(canvas, "engineFlywheelTorque", 1454, 475, 33, 154, WHITE);
        label(canvas, "МГНОВ. РАСХОД", 145, 517, 14, 142);
        value(canvas, "instantFuelConsumption", 145, 555, 39, 142, WHITE);
        label(canvas, values.get("instantUnit"), 145, 576, 16, 142);
        label(canvas, "СРЕДНИЙ", 145, 593, 14, 142);
        value(canvas, "consumptionLitersPer100Km", 145, 625, 39, 142, WHITE);
        label(canvas, "л/100 км", 145, 646, 16, 142);
        label(canvas, "БОРТСЕТЬ / В", 145, 667, 14, 126);
        value(canvas, "voltage", 145, 696, 31, 126,
                state != null && validNonnegative(state.voltage) && state.voltage < 12 ? WARNING : WHITE);
        label(canvas, "ODO / км", 321, 666, 14, 153);
        value(canvas, "odometerKm", 321, 694, 28, 153, WHITE);
        label(canvas, "DAY", 416, 674, 12, 42);
        value(canvas, "dayKm", 490, 675, 26, 88, WHITE);
        text(canvas, "км", 548, 674, 12, 20, WHITE, caption);
        label(canvas, "TRIP", 416, 706, 12, 42);
        value(canvas, "tripKm", 490, 707, 26, 88, WHITE);
        text(canvas, "км", 548, 706, 12, 20, WHITE, caption);
        label(canvas, "РУЛЬ", 1755, 504, 14, 70);
        value(canvas, "steeringAngleDeg", 1640, 506, 33, 150, WHITE);
        drawSteeringDirection(canvas);
        label(canvas, "КОЛЕСО", 1454, 544, 13, 100);
        label(canvas, "bar", 1620, 544, 15, 80);
        label(canvas, "км/ч", 1764, 544, 15, 80);
        for (int i = 0; i < 4; i++) {
            float baseline = 571 + i * 31;
            label(canvas, POSITIONS[i], 1454, baseline, 20, 80);
            value(canvas, PRESSURE_FIELDS[i], 1620, baseline, 29, 110, WHITE);
            value(canvas, WHEEL_FIELDS[i], 1764, baseline, 29, 112, WHITE);
        }
        text(canvas, "H9  /  1920 × 720", 1692, 702, 12, 180, 0xFF55616A, caption);
    }

    private void drawSteeringDirection(Canvas canvas) {
        if (state == null || !Float.isFinite(state.steeringAngleDeg)
                || Math.round(state.steeringAngleDeg) == 0) return;
        int saved = canvas.save();
        if (state.steeringAngleDeg < 0) canvas.scale(-1, 1, 1798, 497);
        arc(canvas, 1798, 497, 9, 145, 250, WHITE, 1.5f);
        pointer.reset();
        pointer.moveTo(1808, 497);
        pointer.lineTo(1805, 504);
        pointer.lineTo(1800, 498);
        shapePaint.setColor(WHITE);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(1.5f);
        shapePaint.setStrokeCap(Paint.Cap.BUTT);
        canvas.drawPath(pointer, shapePaint);
        canvas.restoreToCount(saved);
    }

    private void label(Canvas canvas, String value, float x, float baseline, float size, float width) {
        text(canvas, value, x, baseline, size, width, MUTED, caption);
    }

    private void value(Canvas canvas, String source, float x, float baseline,
            float size, float width, int color) {
        text(canvas, values.get(source), x, baseline, size, width, color, numeric);
    }

    private void text(Canvas canvas, String value, float x, float baseline,
            float size, float maxWidth, int color, Typeface typeface) {
        if (value == null || value.isEmpty()) return;
        textPaint.setTypeface(typeface);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(size);
        textPaint.setColor(color);
        textPath.reset();
        textPaint.getTextPath(value, 0, value.length(), 0, 0, textPath);
        textPath.computeBounds(textBounds, true);
        if (textBounds.width() > maxWidth) {
            textPaint.setTextSize(size * maxWidth / textBounds.width());
            textPath.reset();
            textPaint.getTextPath(value, 0, value.length(), 0, 0, textPath);
            textPath.computeBounds(textBounds, true);
        }
        // Centre visible glyph ink, rather than advance widths or the PNG edge.
        canvas.drawText(value, x - textBounds.centerX(), baseline, textPaint);
    }

    private void arc(Canvas canvas, float cx, float cy, float radius,
            float start, float sweep, int color, float width) {
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setColor(color);
        shapePaint.setStrokeWidth(width);
        shapePaint.setStrokeCap(Paint.Cap.ROUND);
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(arcBounds, start, sweep, false, shapePaint);
    }

    private void radialLine(Canvas canvas, float cx, float cy, float r1, float r2,
            float angle, int color, float width) {
        double radians = Math.toRadians(angle);
        float cosine = (float) Math.cos(radians), sine = (float) Math.sin(radians);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeCap(Paint.Cap.BUTT);
        shapePaint.setStrokeWidth(width);
        shapePaint.setColor(color);
        canvas.drawLine(cx + r1 * cosine, cy + r1 * sine,
                cx + r2 * cosine, cy + r2 * sine, shapePaint);
    }

    private void updateReadouts() {
        indicatedSpeed = state != null && state.speedKph >= 0
                ? Math.round(speedDisplayMode.indicatedKph(state.speedKph)) : Float.NaN;
        values.put("speedKph", Float.isFinite(indicatedSpeed) ? Integer.toString(Math.round(indicatedSpeed)) : DASH);
        values.put("currentGear", state == null ? "" : currentGearLabel(state.gearSelector, state.currentGear));
        values.put("rpm", rpmDigits < 0 ? DASH : Integer.toString(rpmDigits));
        values.put("fuelLiters", state != null && validNonnegative(state.fuelLiters) ? whole(state.fuelLiters) : DASH);
        values.put("rangeKm", state != null && state.rangeKm >= 0 ? Integer.toString(state.rangeKm) : DASH);
        values.put("coolantC", state != null && validCoolant(state.coolantC) ? Integer.toString(state.coolantC) : DASH);
        values.put("transmissionTemperatureC", formatAtf(state, SystemClock.elapsedRealtime()));
        values.put("outsideTemperatureC", state != null && Float.isFinite(state.outsideTemperatureC)
                ? String.format(Locale.US, "%+.1f", state.outsideTemperatureC) : DASH);
        values.put("engineFlywheelTorque", state != null && Float.isFinite(state.engineFlywheelTorque)
                ? whole(state.engineFlywheelTorque) : DASH);
        values.put("instantFuelConsumption", state != null && state.speedKph >= 0
                ? decimal(state.instantFuelConsumption, 1) : DASH);
        values.put("instantUnit", state == null ? DASH : instantUnit(state.speedKph));
        values.put("consumptionLitersPer100Km", state == null ? DASH : decimal(state.consumptionLitersPer100Km, 1));
        values.put("voltage", state == null ? DASH : decimal(state.voltage, 1));
        values.put("odometerKm", state != null && Double.isFinite(state.odometerKm) && state.odometerKm >= 0
                ? String.format(Locale.US, "%,.0f", state.odometerKm).replace(',', ' ') : DASH);
        values.put("dayKm", state == null ? DASH : decimal(state.dayKm, 1));
        values.put("tripKm", state == null ? DASH : decimal(state.tripKm, 1));
        values.put("steeringAngleDeg", state != null && Float.isFinite(state.steeringAngleDeg)
                ? String.format(Locale.US, "%+d°", Math.round(state.steeringAngleDeg)) : DASH);
        float[] pressure = state == null ? null : new float[] {state.tyreFrontLeftBar,
                state.tyreFrontRightBar, state.tyreRearLeftBar, state.tyreRearRightBar};
        float[] wheel = state == null ? null : new float[] {state.wheelFrontLeftKph,
                state.wheelFrontRightKph, state.wheelRearLeftKph, state.wheelRearRightKph};
        for (int i = 0; i < 4; i++) {
            values.put(PRESSURE_FIELDS[i], pressure == null ? DASH : decimal(pressure[i], 2));
            values.put(WHEEL_FIELDS[i], wheel == null ? DASH : decimal(wheel[i], 1));
        }
    }

    private boolean updateRpm(long nowMs) {
        if (state == null || state.rpm < 0) {
            if (rpmInitialized) rpmReadout = new RpmReadoutStabilizer();
            rpmInitialized = false;
            displayedRpm = Float.NaN;
            rpmDigits = -1;
            values.put("rpm", DASH);
            return false;
        }
        float target = Math.min(8000, state.rpm);
        if (!rpmInitialized || nowMs < lastRpmFrameMs) {
            displayedRpm = target;
            rpmInitialized = true;
        } else {
            long elapsed = Math.min(100L, nowMs - lastRpmFrameMs);
            float timeConstant = target < displayedRpm - 350 ? 100.0f : 140.0f;
            displayedRpm += (target - displayedRpm) * (1 - (float) Math.exp(-elapsed / timeConstant));
            if (Math.abs(target - displayedRpm) < 1) displayedRpm = target;
        }
        lastRpmFrameMs = nowMs;
        int next = rpmReadout.update(displayedRpm, nowMs);
        if (next != rpmDigits) {
            rpmDigits = next;
            values.put("rpm", Integer.toString(next));
        }
        return Math.abs(target - displayedRpm) >= 1 || rpmReadout.needsUpdate(displayedRpm);
    }

    String displayedValue(String source) {
        if ("transmissionTemperatureC".equals(source)) return formatAtf(state, SystemClock.elapsedRealtime());
        if ("DashboardClock.time".equals(source) || "DashboardClock.date".equals(source)) {
            clock.update(wallClock.getAsLong());
            return "DashboardClock.time".equals(source) ? clock.getTimeText() : clock.getDateText();
        }
        return values.get(source);
    }

    float indicatedSpeedKph() { return indicatedSpeed; }
    float displayedRpmValue() { return displayedRpm; }
    int rpmDigitValue() { return rpmDigits; }

    static String currentGearLabel(String selector, int ratio) {
        return GearSelector.DRIVE.equals(selector) ? GearSelector.ratio(ratio) : "";
    }

    static String instantUnit(int measuredSpeed) {
        return measuredSpeed < 0 ? DASH : measuredSpeed <= 1 ? "л/ч" : "л/100 км";
    }

    static boolean isAtfFresh(ClusterState snapshot, long nowMs) {
        if (snapshot == null) return false;
        long timestamp = snapshot.transmissionTemperatureUpdatedAtMs;
        return Float.isFinite(snapshot.transmissionTemperatureC) && timestamp > 0
                && timestamp <= nowMs && nowMs - timestamp <= ATF_STALE_MS;
    }

    static float scaleAngle(float value, float min, float max, float start, float sweep) {
        if (!Float.isFinite(value) || !Float.isFinite(min) || !Float.isFinite(max)
                || !Float.isFinite(start) || !Float.isFinite(sweep) || max <= min) return Float.NaN;
        return start + Math.max(0, Math.min(1, (value - min) / (max - min))) * sweep;
    }

    static float[] scalePoint(float cx, float cy, float radius, float angle) {
        double radians = Math.toRadians(angle);
        return new float[] {cx + radius * (float) Math.cos(radians), cy + radius * (float) Math.sin(radians)};
    }

    static float[] scalePoint(float cx, float cy, float radius, float value,
            float min, float max, float start, float sweep) {
        return scalePoint(cx, cy, radius, scaleAngle(value, min, max, start, sweep));
    }

    private static String formatAtf(ClusterState snapshot, long nowMs) {
        return isAtfFresh(snapshot, nowMs) ? whole(snapshot.transmissionTemperatureC) : DASH;
    }

    private static String whole(float value) { return String.format(Locale.US, "%.0f", value); }

    private static String decimal(float value, int places) {
        return validNonnegative(value) ? String.format(Locale.US, "%." + places + "f", value) : DASH;
    }

    private static boolean validNonnegative(float value) { return Float.isFinite(value) && value >= 0; }
    private static boolean validCoolant(int value) { return value >= -40 && value <= 150; }

    private static int atfColor(TransmissionTemperatureAlert.Level level) {
        switch (level) {
            case ELEVATED: return WARNING;
            case HOT: return 0xFFFF8A3D;
            case CRITICAL: return RED;
            default: return WHITE;
        }
    }

    private Bitmap buildStaticBase(Context context, boolean includeBackdrop, boolean includeChrome) {
        if (!includeBackdrop) return includeChrome ? loadBitmap(context, "chrome.png") : null;
        Bitmap original = loadBitmap(context, "map_black_gradient.png");
        // The calibration also hides factory readouts below our clock and trips.
        // Preserve every source pixel; softening either edge exposes that text.
        Bitmap base = original.copy(Bitmap.Config.ARGB_8888, true);
        base.setDensity(Bitmap.DENSITY_NONE);
        original.recycle();
        if (includeChrome) {
            Bitmap chrome = loadBitmap(context, "chrome.png");
            new Canvas(base).drawBitmap(chrome, 0, 0, bitmapPaint);
            chrome.recycle();
        }
        return base;
    }

    private static Bitmap loadBitmap(Context context, String filename) {
        try (InputStream stream = context.getAssets().open(ASSETS + filename)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap == null || bitmap.getWidth() != 1920 || bitmap.getHeight() != 720) {
                throw new IllegalStateException("Invalid APEX SPORT asset: " + filename);
            }
            bitmap.setDensity(Bitmap.DENSITY_NONE);
            return bitmap;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load APEX SPORT asset: " + filename, error);
        }
    }
}
