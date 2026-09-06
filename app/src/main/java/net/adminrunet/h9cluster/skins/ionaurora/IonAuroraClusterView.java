package net.adminrunet.h9cluster.skins.ionaurora;

import net.adminrunet.h9cluster.BuildConfig;
import net.adminrunet.h9cluster.ClusterRenderer;
import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.GearSelector;
import net.adminrunet.h9cluster.PredictiveMotionFilter;
import net.adminrunet.h9cluster.RpmDisplaySmoother;
import net.adminrunet.h9cluster.TransmissionTemperatureAlert;
import net.adminrunet.h9cluster.skins.WifiIndicator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** ION AURORA spacecraft cockpit with two animated cylindrical rolling tapes. */
public final class IonAuroraClusterView extends View implements ClusterRenderer {
    private static final float LOGICAL_WIDTH = 1920.0f;
    private static final float LOGICAL_HEIGHT = 720.0f;
    private static final float TAPE_OFFSET_Y = 0.0f;
    private static final float TANK_CAPACITY_LITERS = 80.0f;
    private static final long ATF_STALE_AFTER_MS = 15_000L;
    private static final int COLOR_WHITE = 0xFFFAFDFF;
    private static final int COLOR_MUTED = 0xFF9EB9CE;
    private static final int COLOR_CYAN = 0xFF27E8FF;
    private static final int COLOR_VIOLET = 0xFFAE38FF;
    private static final int COLOR_MINT = 0xFF54F2C2;
    private static final int COLOR_AMBER = 0xFFFFB14A;
    private static final int COLOR_WARNING = 0xFFFFD54F;
    private static final int COLOR_HOT = 0xFFFF8A3D;
    private static final int COLOR_CRITICAL = 0xFFFF4D4D;

    private final Paint bitmapPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint.FontMetrics textMetrics = new Paint.FontMetrics();
    private final Typeface dataTypeface;
    private final Typeface gaugeTypeface;
    private final Bitmap staticBase;
    private final boolean includeBackdrop;
    private final RollingTapeGauge speedGauge;
    private final RollingTapeGauge rpmGauge;
    private final WifiIndicator wifiIndicator;
    private final RpmDisplaySmoother rpmSmoother = new RpmDisplaySmoother();
    private final TransmissionTemperatureAlert atfAlert =
            new TransmissionTemperatureAlert();
    private final PredictiveMotionFilter steeringMotion = new PredictiveMotionFilter(
            -1080.0f,
            1080.0f,
            1080.0f,
            450L,
            35.0f,
            60.0f,
            12.0f,
            0.1f,
            0.15f);

    private ClusterState targetState = ClusterState.empty();
    private boolean motionInitialized;
    private float displayedSpeed;
    private float displayedRpm;
    private float visualSpeed;
    private float visualRpm;
    private float displayedSteering;
    private float stabilizedRpmTarget;
    private float speedVelocity;
    private float rpmVelocity;
    private long lastFrameAtMs;
    private long launchStartedAtMs;
    private int cachedSpeed = Integer.MIN_VALUE;
    private int cachedRpm = Integer.MIN_VALUE;
    private String cachedSpeedText = "0";
    private String cachedRpmText = "0";

    private String rangeText = "0";
    private String fuelText = "0";
    private String odometerText = "0 km";
    private String dayText = "0.0 km";
    private String tripText = "0.0 km";
    private String pressureFrontLeftText = "—";
    private String pressureFrontRightText = "—";
    private String pressureRearLeftText = "—";
    private String pressureRearRightText = "—";
    private String coolantText = "— °C";
    private String outsideText = "— °C";
    private String steeringText = "0°";
    private String wheelFrontLeftText = "—";
    private String wheelFrontRightText = "—";
    private String wheelRearLeftText = "—";
    private String wheelRearRightText = "—";
    private String torqueText = "—";
    private String instantValueText = "—";
    private String instantUnitText = "л/100 км";
    private String averageValueText = "—";
    private String voltageText = "—";
    private String currentGearText = "";

    public IonAuroraClusterView(Context context) {
        this(context, true);
    }

    IonAuroraClusterView(Context context, boolean includeBackdrop) {
        super(context);
        this.includeBackdrop = includeBackdrop;
        setLayerType(
                BuildConfig.DEMO_MODE
                        ? View.LAYER_TYPE_NONE
                        : View.LAYER_TYPE_HARDWARE,
                null);
        setBackgroundColor(Color.TRANSPARENT);

        dataTypeface = Typeface.createFromAsset(
                context.getAssets(),
                "fonts/Inter-Regular.ttf");
        gaugeTypeface = Typeface.createFromAsset(
                context.getAssets(),
                "fonts/Rajdhani-Medium.ttf");
        staticBase = buildStaticBase(context);
        wifiIndicator = new WifiIndicator(context);

        speedGauge = new RollingTapeGauge(
                652.0f,
                115.0f,
                598.0f,
                0.0f,
                220.0f,
                3.6f,
                2.0f,
                10.0f,
                20.0f,
                0,
                gaugeTypeface);
        rpmGauge = new RollingTapeGauge(
                1268.0f,
                115.0f,
                598.0f,
                0.0f,
                8000.0f,
                0.10f,
                100.0f,
                500.0f,
                1000.0f,
                0,
                gaugeTypeface);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        shapePaint.setStyle(Paint.Style.FILL);
        bitmapPaint.setAlpha(255);

    }

    @Override
    public void setClusterState(ClusterState state) {
        if (state == null) {
            return;
        }
        targetState = state;
        if (state.steeringUpdatedAtMs > 0L) {
            steeringMotion.onSample(state.steeringAngleDeg, state.steeringUpdatedAtMs);
        }
        updateTelemetryCache(state);
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long frameAtMs = SystemClock.elapsedRealtime();
        updateMotion(frameAtMs);
        if (launchStartedAtMs == 0L) {
            launchStartedAtMs = frameAtMs;
        }
        long launchAgeMs = Math.max(0L, frameAtMs - launchStartedAtMs);
        float tapeReveal = smoothStep((launchAgeMs - 260.0f) / 560.0f);
        float indexReveal = smoothStep((launchAgeMs - 180.0f) / 480.0f);
        float valuesReveal = smoothStep((launchAgeMs - 520.0f) / 500.0f);

        if (BuildConfig.DEMO_MODE && includeBackdrop) {
            canvas.drawColor(Color.BLACK);
        } else {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        }
        float scale = Math.min(
                getWidth() / LOGICAL_WIDTH,
                getHeight() / LOGICAL_HEIGHT);
        float offsetX = (getWidth() - LOGICAL_WIDTH * scale) * 0.5f;
        float offsetY = (getHeight() - LOGICAL_HEIGHT * scale) * 0.5f;

        int rootSave = canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        canvas.drawBitmap(staticBase, 0.0f, 0.0f, bitmapPaint);
        int tapeSave = canvas.save();
        canvas.translate(0, TAPE_OFFSET_Y);
        // Ambient movement is slow and limited to the side channels; the tape still
        // follows measured vehicle values, never a time-driven fictitious reading.
        // 200 s = exactly 21 energy cycles: continuous wrap and stable float precision.
        float energyPhase = targetState.rpm > 0 ? (frameAtMs % 200_000L) * 0.001f : 0.0f;
        speedGauge.draw(canvas, visualSpeed, speedVelocity, tapeReveal, energyPhase);
        rpmGauge.draw(canvas, visualRpm, rpmVelocity, tapeReveal, energyPhase);

        float breath = 0.04f * (float) Math.sin(frameAtMs * Math.PI / 1000.0);
        float motionBoost = clamp(
                Math.abs(speedVelocity) / 180.0f
                        + Math.abs(rpmVelocity) / 12_000.0f,
                0.0f,
                0.20f);
        float glow = 0.92f + breath + motionBoost;
        speedGauge.drawFixedIndex(canvas, glow, indexReveal);
        rpmGauge.drawFixedIndex(canvas, glow, indexReveal);
        canvas.restoreToCount(tapeSave);
        drawMainValues(canvas, valuesReveal);
        drawTelemetry(canvas, frameAtMs, valuesReveal);
        drawCurrentGear(canvas, valuesReveal);
        wifiIndicator.draw(canvas, shapePaint, 1708, 42, frameAtMs);
        shapePaint.setStyle(Paint.Style.FILL);

        canvas.restoreToCount(rootSave);

        if (needsAnimationFrame(frameAtMs, launchAgeMs)) {
            postInvalidateOnAnimation();
        } else if (BuildConfig.DEMO_MODE || targetState.rpm > 0) {
            postInvalidateDelayed(33L);
        } else {
            postInvalidateDelayed(1000L);
        }
    }

    private void updateMotion(long nowMs) {
        float rpmTarget = rpmSmoother.update(
                targetState.rpm,
                targetState.speedKph,
                nowMs);
        stabilizedRpmTarget = clamp(rpmTarget, 0.0f, 8000.0f);
        if (!motionInitialized) {
            motionInitialized = true;
            displayedSpeed = clamp(targetState.speedKph, 0.0f, 220.0f);
            displayedRpm = stabilizedRpmTarget;
            displayedSteering = targetState.steeringAngleDeg;
            lastFrameAtMs = nowMs;
            updateVisualValues();
            return;
        }

        float elapsedMs = clamp(nowMs - lastFrameAtMs, 0.0f, 100.0f);
        lastFrameAtMs = nowMs;
        if (elapsedMs <= 0.0f) {
            return;
        }

        float previousSpeed = displayedSpeed;
        float previousRpm = displayedRpm;
        float boundedSpeedTarget = clamp(targetState.speedKph, 0.0f, 220.0f);
        float boundedRpmTarget = stabilizedRpmTarget;
        float speedBlend = 1.0f - (float) Math.exp(-elapsedMs / 140.0f);
        float rpmTimeConstant = boundedRpmTarget < displayedRpm - 350.0f
                ? 70.0f
                : 90.0f;
        float rpmBlend = 1.0f - (float) Math.exp(-elapsedMs / rpmTimeConstant);

        displayedSpeed += (boundedSpeedTarget - displayedSpeed) * speedBlend;
        displayedRpm += (boundedRpmTarget - displayedRpm) * rpmBlend;
        if (Math.abs(boundedSpeedTarget - displayedSpeed) < 0.03f) {
            displayedSpeed = boundedSpeedTarget;
        }
        if (Math.abs(boundedRpmTarget - displayedRpm) < 5.0f) {
            displayedRpm = boundedRpmTarget;
        }

        float seconds = elapsedMs / 1000.0f;
        float instantaneousSpeedVelocity = (displayedSpeed - previousSpeed) / seconds;
        float instantaneousRpmVelocity = (displayedRpm - previousRpm) / seconds;
        speedVelocity += (instantaneousSpeedVelocity - speedVelocity) * 0.32f;
        rpmVelocity += (instantaneousRpmVelocity - rpmVelocity) * 0.38f;
        if (Math.abs(boundedSpeedTarget - displayedSpeed) < 0.03f) {
            speedVelocity *= 0.72f;
        }
        if (Math.abs(boundedRpmTarget - displayedRpm) < 5.0f) {
            rpmVelocity *= 0.68f;
        }

        displayedSteering = steeringMotion.update(nowMs);
        updateVisualValues();
    }

    private boolean needsAnimationFrame(long nowMs, long launchAgeMs) {
        return launchAgeMs < 1100L
                || Math.abs(clamp(targetState.speedKph, 0, 220) - displayedSpeed) > 0.03f
                || Math.abs(clamp(stabilizedRpmTarget, 0, 8000) - displayedRpm) > 5.0f
                || Math.abs(speedVelocity) > 0.05f
                || Math.abs(rpmVelocity) > 2.0f
                || steeringMotion.needsAnimationFrame(nowMs);
    }

    private void updateVisualValues() {
        // The exact same whole reading drives the tape and the digital display.
        visualSpeed = Math.round(displayedSpeed);
        visualRpm = Math.round(displayedRpm / 10.0f) * 10;
        int speed = Math.round(visualSpeed);
        if (speed != cachedSpeed) {
            cachedSpeed = speed;
            cachedSpeedText = Integer.toString(speed);
        }
        int rpm = Math.round(visualRpm);
        if (rpm != cachedRpm) {
            cachedRpm = rpm;
            cachedRpmText = Integer.toString(rpm);
        }
    }

    private void drawMainValues(Canvas canvas, float reveal) {
        drawGlowingValue(canvas, cachedSpeedText, 320, 329, 130, 290, COLOR_CYAN, reveal);
        drawGlowingValue(canvas, cachedRpmText, 1600, 329, 120, 340, COLOR_VIOLET, reveal);
    }

    private void drawCurrentGear(Canvas canvas, float reveal) {
        if (currentGearText.isEmpty()) return;
        // Same numeric position as Simple: the vehicle owns the selector letter.
        // The sole user-authorized mask03 exception is this small 1..8 readout.
        configureText(gaugeTypeface, 44, Paint.Align.CENTER, COLOR_WHITE, true);
        textPaint.setAlpha(Math.round(255 * reveal));
        canvas.drawText(currentGearText, 1000, 63, textPaint);
    }

    static String currentGearLabel(String selector, int gear) {
        return GearSelector.DRIVE.equals(selector) ? GearSelector.ratio(gear) : "";
    }

    private void drawGlowingValue(
            Canvas canvas,
            String value,
            float centerX,
            float centerY,
            float textSize,
            float maximumWidth,
            int glowColor,
            float reveal) {
        configureText(
                gaugeTypeface,
                textSize,
                Paint.Align.CENTER,
                glowColor,
                true);
        fitTextSize(value, maximumWidth, textSize, textSize * 0.78f);
        float fittedSize = textPaint.getTextSize();

        textPaint.setStyle(Paint.Style.STROKE);
        textPaint.setStrokeWidth(10.0f);
        textPaint.setAlpha(Math.round(18.0f * reveal));
        drawCenteredText(canvas, value, centerX, centerY, textPaint);

        textPaint.setStrokeWidth(3.0f);
        textPaint.setAlpha(Math.round(42.0f * reveal));
        drawCenteredText(canvas, value, centerX, centerY, textPaint);

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(fittedSize);
        textPaint.setColor(COLOR_WHITE);
        textPaint.setAlpha(Math.round(255.0f * reveal));
        drawCenteredText(canvas, value, centerX, centerY, textPaint);
    }

    private void drawTelemetry(Canvas canvas, long nowMs, float reveal) {
        ClusterState state = targetState;
        drawPressureValue(canvas, pressureFrontLeftText, 320, 228, state.tyreFrontLeftBar, reveal);
        drawPressureValue(canvas, pressureFrontRightText, 475, 228, state.tyreFrontRightBar, reveal);
        drawPressureValue(canvas, pressureRearLeftText, 320, 260, state.tyreRearLeftBar, reveal);
        drawPressureValue(canvas, pressureRearRightText, 475, 260, state.tyreRearRightBar, reveal);
        field(canvas, wheelFrontLeftText, 1445, 228, 34, 108,
                wheelSpeedColor(state, state.wheelFrontLeftKph), reveal);
        field(canvas, wheelFrontRightText, 1600, 228, 34, 108,
                wheelSpeedColor(state, state.wheelFrontRightKph), reveal);
        field(canvas, wheelRearLeftText, 1445, 260, 34, 108,
                wheelSpeedColor(state, state.wheelRearLeftKph), reveal);
        field(canvas, wheelRearRightText, 1600, 260, 34, 108,
                wheelSpeedColor(state, state.wheelRearRightKph), reveal);

        // The two consumption values form a dedicated, plainly named fuel instrument.
        field(canvas, instantValueText, 158, 448, 46, 132, COLOR_CYAN, reveal);
        field(canvas, averageValueText, 310, 448, 46, 132, COLOR_WHITE, reveal);
        label(canvas, instantUnitText, 158, 482, Paint.Align.CENTER, COLOR_MUTED, 18, reveal);
        field(canvas, rangeText, 143, 540, 44, 92, COLOR_WHITE, reveal);
        field(canvas, fuelText, 141, 600, 44, 92,
                state.fuelLiters < 10 ? COLOR_WARNING : COLOR_WHITE, reveal);
        drawFuelRail(canvas, state.fuelLiters, reveal);

        // Only actual temperatures belong in the thermal instrument.
        field(canvas, coolantText, 1705, 440, 43, 205, COLOR_WHITE, reveal);
        field(canvas, outsideText, 1705, 506, 43, 205, COLOR_WHITE, reveal);
        field(canvas, formatAtf(state, nowMs), 1705, 574, 43, 205,
                atfColor(updateAtfAlert(state, nowMs)), reveal);
        field(canvas, steeringText, 1740, 665, 44, 145, COLOR_WHITE, reveal);
        drawSteeringRail(canvas, displayedSteering, reveal);

        field(canvas, torqueText, 158, 683, 40, 130, COLOR_WHITE, reveal);
        field(canvas, voltageText, 310, 683, 40, 120,
                state.voltage < 12 ? COLOR_WARNING : COLOR_MINT, reveal);
        field(canvas, odometerText, 565, 682, 38, 304, COLOR_WHITE, reveal);
        field(canvas, dayText, 897, 682, 38, 304, COLOR_WHITE, reveal);
        field(canvas, tripText, 1229, 682, 38, 304, COLOR_WHITE, reveal);
    }

    private void drawFuelRail(Canvas canvas, float liters, float reveal) {
        float proportion = Float.isFinite(liters)
                ? clamp(liters / TANK_CAPACITY_LITERS, 0, 1) : 0;
        for (int i = 0; i < 16; i++) {
            shapePaint.setColor(i < Math.round(proportion * 16)
                    ? (liters < 10 ? COLOR_WARNING : COLOR_CYAN) : 0xFF21394F);
            shapePaint.setAlpha(Math.round(235 * reveal));
            float x = 93 + i * 7.5f;
            canvas.drawRoundRect(x, 626, x + 5.5f, 632, 1.5f, 1.5f, shapePaint);
        }
    }

    private void drawPressureValue(Canvas canvas, String value, float x, float y,
            float pressure, float reveal) {
        field(canvas, value, x, y, 34, 105,
                Float.isFinite(pressure) && pressure < 2.0f
                        ? COLOR_WARNING : COLOR_WHITE, reveal);
    }

    private void field(Canvas canvas, String value, float x, float y,
            float size, float width, int color, float reveal) {
        configureText(gaugeTypeface, size, Paint.Align.CENTER, color, true);
        fitTextSize(value, width, size, size * 0.82f);
        textPaint.setAlpha(Math.round(255 * reveal));
        drawCenteredText(canvas, value, x, y, textPaint);
    }

    private void label(Canvas canvas, String value, float x, float baseline,
            Paint.Align align, int color, float size, float reveal) {
        configureText(dataTypeface, size, align, color, false);
        textPaint.setAlpha(Math.round(255 * reveal));
        canvas.drawText(value, x, baseline, textPaint);
    }

    private void drawSteeringRail(Canvas canvas, float angle, float reveal) {
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(COLOR_CYAN);
        linePaint.setAlpha(Math.round(220 * reveal));
        linePaint.setStrokeWidth(2.2f);
        // An unmistakable steering-wheel pictogram, separate from all °C readings.
        canvas.drawCircle(1570, 666, 19, linePaint);
        canvas.drawCircle(1570, 666, 4, linePaint);
        canvas.drawLine(1552, 663, 1566, 666, linePaint);
        canvas.drawLine(1574, 666, 1588, 663, linePaint);
        canvas.drawLine(1570, 670, 1570, 684, linePaint);
        linePaint.setColor(0xFF456778);
        linePaint.setAlpha(Math.round(180 * reveal));
        linePaint.setStrokeWidth(2);
        canvas.drawLine(1630, 694, 1804, 694, linePaint);
        float safeAngle = Float.isFinite(angle) ? angle : 0;
        float x = 1717 + 87 * clamp(safeAngle / 1080, -1, 1);
        linePaint.setColor(COLOR_CYAN);
        linePaint.setAlpha(Math.round(255 * reveal));
        linePaint.setStrokeWidth(3);
        canvas.drawLine(x, 689, x, 700, linePaint);
    }

    private void drawStaticLabels(Canvas canvas) {
        label(canvas, "ДАВЛЕНИЕ ШИН", 527, 174, Paint.Align.RIGHT, COLOR_CYAN, 21, 1);
        label(canvas, "bar", 527, 201, Paint.Align.RIGHT, COLOR_MUTED, 18, 1);
        label(canvas, "FL", 249, 235, Paint.Align.LEFT, COLOR_MUTED, 18, 1);
        label(canvas, "FR", 404, 235, Paint.Align.LEFT, COLOR_MUTED, 18, 1);
        label(canvas, "RL", 249, 267, Paint.Align.LEFT, COLOR_MUTED, 18, 1);
        label(canvas, "RR", 404, 267, Paint.Align.LEFT, COLOR_MUTED, 18, 1);
        label(canvas, "СКОРОСТЬ КОЛЁС", 1388, 174, Paint.Align.LEFT, COLOR_CYAN, 21, 1);
        label(canvas, "км/ч", 1388, 201, Paint.Align.LEFT, COLOR_MUTED, 18, 1);
        label(canvas, "FL", 1382, 235, Paint.Align.LEFT, COLOR_MUTED, 18, 1);
        label(canvas, "FR", 1537, 235, Paint.Align.LEFT, COLOR_MUTED, 18, 1);
        label(canvas, "RL", 1382, 267, Paint.Align.LEFT, COLOR_MUTED, 18, 1);
        label(canvas, "RR", 1537, 267, Paint.Align.LEFT, COLOR_MUTED, 18, 1);
        label(canvas, "км/ч", 320, 392, Paint.Align.CENTER, COLOR_MUTED, 27, 1);
        label(canvas, "об/мин", 1600, 392, Paint.Align.CENTER, COLOR_MUTED, 27, 1);

        label(canvas, "МГНОВЕННЫЙ", 158, 418, Paint.Align.CENTER, COLOR_CYAN, 17, 1);
        label(canvas, "СРЕДНИЙ", 310, 418, Paint.Align.CENTER, COLOR_CYAN, 18, 1);
        label(canvas, "л/100 км", 310, 482, Paint.Align.CENTER, COLOR_MUTED, 18, 1);
        label(canvas, "ЗАПАС ХОДА", 153, 519, Paint.Align.CENTER, COLOR_CYAN, 15, 1);
        label(canvas, "км", 200, 548, Paint.Align.CENTER, COLOR_MUTED, 17, 1);
        label(canvas, "ТОПЛИВО", 153, 578, Paint.Align.CENTER, COLOR_CYAN, 16, 1);
        label(canvas, "л", 192, 609, Paint.Align.CENTER, COLOR_MUTED, 20, 1);

        label(canvas, "ОЖ", 1457, 447, Paint.Align.LEFT, COLOR_CYAN, 23, 1);
        label(canvas, "СНАРУЖИ", 1457, 513, Paint.Align.LEFT, COLOR_CYAN, 19, 1);
        label(canvas, "ATF", 1457, 581, Paint.Align.LEFT, COLOR_CYAN, 23, 1);
        label(canvas, "РУЛЬ", 1610, 657, Paint.Align.LEFT, COLOR_CYAN, 22, 1);
        label(canvas, "TRQ", 158, 665, Paint.Align.CENTER, COLOR_CYAN, 17, 1);
        label(canvas, "VOLT · V", 310, 665, Paint.Align.CENTER, COLOR_CYAN, 17, 1);
        label(canvas, "ODO", 565, 663, Paint.Align.CENTER, COLOR_CYAN, 18, 1);
        label(canvas, "Day", 897, 663, Paint.Align.CENTER, COLOR_CYAN, 18, 1);
        label(canvas, "Trip", 1229, 663, Paint.Align.CENTER, COLOR_CYAN, 18, 1);
    }

    private void updateTelemetryCache(ClusterState state) {
        currentGearText = currentGearLabel(state.gearSelector, state.currentGear);
        rangeText = Integer.toString(Math.max(0, state.rangeKm));
        fuelText = Float.isFinite(state.fuelLiters)
                ? Integer.toString(Math.round(state.fuelLiters))
                : "—";
        odometerText = formatOdometer(state.odometerKm);
        dayText = formatDistance(state.dayKm);
        tripText = formatDistance(state.tripKm);
        pressureFrontLeftText = formatPressure(state.tyreFrontLeftBar);
        pressureFrontRightText = formatPressure(state.tyreFrontRightBar);
        pressureRearLeftText = formatPressure(state.tyreRearLeftBar);
        pressureRearRightText = formatPressure(state.tyreRearRightBar);
        coolantText = state.coolantC + " °C";
        outsideText = formatOutside(state.outsideTemperatureC);
        steeringText = formatSteering(state.steeringAngleDeg);
        wheelFrontLeftText = formatWheelSpeed(state.wheelFrontLeftKph);
        wheelFrontRightText = formatWheelSpeed(state.wheelFrontRightKph);
        wheelRearLeftText = formatWheelSpeed(state.wheelRearLeftKph);
        wheelRearRightText = formatWheelSpeed(state.wheelRearRightKph);
        torqueText = formatTorque(state.engineFlywheelTorque);
        instantValueText = formatOneDecimal(state.instantFuelConsumption);
        instantUnitText = state.speedKph <= 1 ? "л/ч" : "л/100 км";
        averageValueText = formatOneDecimal(state.consumptionLitersPer100Km);
        voltageText = formatOneDecimal(state.voltage);
    }

    private TransmissionTemperatureAlert.Level updateAtfAlert(
            ClusterState state,
            long nowMs) {
        boolean fresh = state.hasTransmissionTemperature()
                && state.transmissionTemperatureUpdatedAtMs > 0L
                && nowMs - state.transmissionTemperatureUpdatedAtMs <= ATF_STALE_AFTER_MS;
        return atfAlert.update(state.transmissionTemperatureC, fresh);
    }

    private static int atfColor(TransmissionTemperatureAlert.Level level) {
        switch (level) {
            case ELEVATED:
                return COLOR_WARNING;
            case HOT:
                return COLOR_HOT;
            case CRITICAL:
                return COLOR_CRITICAL;
            case NORMAL:
            default:
                return COLOR_WHITE;
        }
    }

    private static String formatAtf(ClusterState state, long nowMs) {
        if (!state.hasTransmissionTemperature()
                || state.transmissionTemperatureUpdatedAtMs <= 0L
                || nowMs - state.transmissionTemperatureUpdatedAtMs > ATF_STALE_AFTER_MS) {
            return "— °C";
        }
        return Math.round(state.transmissionTemperatureC) + " °C";
    }

    private void configureText(
            Typeface typeface,
            float size,
            Paint.Align align,
            int color,
            boolean bold) {
        textPaint.setTypeface(typeface);
        textPaint.setTextSize(size);
        textPaint.setTextAlign(align);
        textPaint.setColor(color);
        textPaint.setAlpha(255);
        textPaint.setFakeBoldText(bold);
        textPaint.setTextSkewX(0.0f);
        textPaint.setTextScaleX(1.0f);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setStrokeWidth(1.0f);
    }

    private void fitTextSize(
            String value,
            float maximumWidth,
            float desiredSize,
            float minimumSize) {
        textPaint.setTextSize(desiredSize);
        float width = textPaint.measureText(value);
        if (width > maximumWidth && width > 0.0f) {
            textPaint.setTextSize(Math.max(minimumSize, desiredSize * maximumWidth / width));
        }
    }

    private void drawCenteredText(
            Canvas canvas,
            String value,
            float centerX,
            float centerY,
            Paint paint) {
        paint.getFontMetrics(textMetrics);
        float baseline = centerY - (textMetrics.ascent + textMetrics.descent) * 0.5f;
        canvas.drawText(value, centerX, baseline, paint);
    }

    private Bitmap buildStaticBase(Context context) {
        Bitmap base = loadBitmap(context, "dashboard/skins/ionaurora/map_black_gradient.png");
        Bitmap cosmicSides = loadBitmap(context, "dashboard/skins/ionaurora/cosmic_sides.png");
        if (base.getWidth() != 1920 || base.getHeight() != 720
                || cosmicSides.getWidth() != 1920 || cosmicSides.getHeight() != 720) {
            throw new IllegalStateException("ION AURORA assets must be 1920x720");
        }
        Bitmap composed = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        composed.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(composed);
        Paint paint = new Paint(
                Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (includeBackdrop) {
            canvas.drawBitmap(base, 0, 0, paint);
            canvas.drawBitmap(cosmicSides, 0, 0, paint);
        }
        IonAuroraChrome.draw(canvas);
        drawStaticLabels(canvas);
        base.recycle();
        cosmicSides.recycle();
        return composed;
    }

    private static Bitmap loadBitmap(Context context, String assetPath) {
        InputStream input = null;
        try {
            input = context.getAssets().open(assetPath);
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                throw new IllegalStateException("Cannot decode asset: " + assetPath);
            }
            bitmap.setDensity(Bitmap.DENSITY_NONE);
            return bitmap;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load asset: " + assetPath, error);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                    // Nothing else to release.
                }
            }
        }
    }

    private static String formatOdometer(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            return "— km";
        }
        return String.format(Locale.US, "%,.0f km", value).replace(',', ' ');
    }

    private static String formatDistance(float value) {
        return Float.isFinite(value) && value >= 0.0f
                ? String.format(Locale.US, "%.1f km", value)
                : "— km";
    }

    private static String formatPressure(float value) {
        return Float.isFinite(value) && value >= 0.0f
                ? String.format(Locale.US, "%.2f", value)
                : "—";
    }

    private static String formatOutside(float value) {
        if (!Float.isFinite(value)) {
            return "— °C";
        }
        return String.format(Locale.US, value > 0.0f ? "+%.1f °C" : "%.1f °C", value);
    }

    private static String formatSteering(float value) {
        if (!Float.isFinite(value)) {
            return "—";
        }
        int rounded = Math.round(value);
        return rounded > 0 ? "+" + rounded + "°" : rounded + "°";
    }

    private static String formatWheelSpeed(float value) {
        return Float.isFinite(value) && value >= 0.0f
                ? String.format(Locale.US, "%.1f", value)
                : "—";
    }

    private static String formatTorque(float value) {
        if (!Float.isFinite(value)) {
            return "—";
        }
        return Math.abs(value) >= 100.0f
                ? String.format(Locale.US, "%.0f", value)
                : String.format(Locale.US, "%.1f", value);
    }

    static String formatOneDecimal(float value) {
        return Float.isFinite(value) && value >= 0.0f
                ? String.format(Locale.US, "%.1f", value)
                : "—";
    }

    static int wheelSpeedColor(ClusterState state, float wheelSpeed) {
        float fl = state.wheelFrontLeftKph;
        float fr = state.wheelFrontRightKph;
        float rl = state.wheelRearLeftKph;
        float rr = state.wheelRearRightKph;
        if (!Float.isFinite(wheelSpeed) || !Float.isFinite(fl)
                || !Float.isFinite(fr) || !Float.isFinite(rl) || !Float.isFinite(rr)) {
            return COLOR_WHITE;
        }
        float min = Math.min(Math.min(fl, fr), Math.min(rl, rr));
        float max = Math.max(Math.max(fl, fr), Math.max(rl, rr));
        if (max < 4) return COLOR_WHITE;
        float median = (fl + fr + rl + rr - min - max) * .5f;
        return Math.abs(wheelSpeed - median) >= Math.max(4, median * .18f)
                ? COLOR_WARNING : COLOR_WHITE;
    }

    private static float smoothStep(float progress) {
        float clamped = clamp(progress, 0.0f, 1.0f);
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
