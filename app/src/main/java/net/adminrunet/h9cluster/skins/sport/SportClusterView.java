package net.adminrunet.h9cluster.skins.sport;

import net.adminrunet.h9cluster.ClusterRenderer;
import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.GearSelector;
import net.adminrunet.h9cluster.PredictiveMotionFilter;
import net.adminrunet.h9cluster.RpmDisplaySmoother;
import net.adminrunet.h9cluster.TransmissionTemperatureAlert;
import net.adminrunet.h9cluster.skins.FuelConsumptionFormatter;
import net.adminrunet.h9cluster.skins.WifiIndicator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Independent renderer for the user-supplied asymmetric red Sport skin. */
public final class SportClusterView extends View implements ClusterRenderer {
    private static final float LOGICAL_WIDTH = 1920.0f;
    private static final float LOGICAL_HEIGHT = 720.0f;
    private static final float TANK_CAPACITY_LITERS = 80.0f;
    private static final float MAIN_SPEED_CENTER_X = 317.0f;
    private static final float SPORT_TYRE_CAR_X = 1538.0f;
    private static final float SPORT_TYRE_CAR_Y = 285.0f;
    private static final float REFERENCE_PIXELS_PER_MM = 160.0f / 25.4f;
    private static final float CLOCK_CARD_LEFT = 11.0f;
    private static final float CLOCK_CARD_TOP = 88.0f;
    private static final float CLOCK_CARD_RIGHT = 175.0f;
    private static final float CLOCK_CARD_BOTTOM = 152.0f;
    private static final float CLOCK_CENTER_X = 93.0f;
    private static final float CLOCK_BASELINE = 132.0f;
    private static final float ATF_CARD_LEFT = 1757.0f;
    private static final float ATF_CARD_TOP = 15.0f;
    private static final float ATF_CARD_RIGHT = 1905.0f;
    private static final float ATF_CARD_BOTTOM = 79.0f;
    private static final float ATF_CENTER_X = 1831.0f;
    private static final float ATF_LABEL_BASELINE = 35.0f;
    private static final float ATF_VALUE_BASELINE = 64.0f;
    private static final float ODOMETER_LABEL_X = 276.0f;
    private static final float ODOMETER_VALUE_X = 355.0f;
    private static final float ODOMETER_BASELINE = 298.0f;
    private static final float DAY_ODOMETER_BASELINE = 334.0f;
    private static final float TRIP_ODOMETER_BASELINE = 370.0f;
    private static final float OUTSIDE_TEMPERATURE_SHIFT =
            4.0f * REFERENCE_PIXELS_PER_MM;
    private static final float OUTSIDE_TEMPERATURE_CARD_LEFT =
            1038.0f + OUTSIDE_TEMPERATURE_SHIFT;
    private static final float OUTSIDE_TEMPERATURE_CARD_RIGHT =
            1186.0f + OUTSIDE_TEMPERATURE_SHIFT;
    private static final float OUTSIDE_TEMPERATURE_X =
            1112.0f + OUTSIDE_TEMPERATURE_SHIFT;
    private static final float SPORT_MAIN_NEEDLE_GAP = 16.0f;
    private static final long TRANSMISSION_TEMPERATURE_STALE_AFTER_MS = 15000L;
    private static final int COLOR_ATF_NORMAL = 0xFFF9F9F7;
    private static final int COLOR_ATF_ELEVATED = 0xFFFFD54F;
    private static final int COLOR_ATF_HOT = 0xFFFF8A3D;
    private static final int COLOR_ATF_CRITICAL = 0xFFFF4D4D;
    private static final int COLOR_CARD_BORDER = 0xFF4C535A;

    private final Paint bitmapPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF logicalBounds =
            new RectF(0.0f, 0.0f, LOGICAL_WIDTH, LOGICAL_HEIGHT);
    private final RectF needleDestination = new RectF();
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final TransmissionTemperatureAlert transmissionTemperatureAlert =
            new TransmissionTemperatureAlert();
    private final RpmDisplaySmoother rpmSmoother =
            new RpmDisplaySmoother();

    private final Bitmap calibratedEdgeBackground;
    private final Bitmap staticBackground;
    private final Bitmap staticScaleOverlay;
    private final Bitmap tyreCar;
    private final Bitmap mainNeedle;
    private final Bitmap smallNeedle;
    private final Typeface dataTypeface;
    private final Typeface gaugeTypeface;
    private final WifiIndicator wifiIndicator;

    private ClusterState targetState = ClusterState.empty();
    private final PredictiveMotionFilter steeringMotion = new PredictiveMotionFilter(
            -1080.0f, 1080.0f, 1080.0f, 450L,
            35.0f, 60.0f, 12.0f, 0.1f, 0.15f);
    private float displayedSpeed = targetState.speedKph;
    private float displayedRpm = targetState.rpm;
    private float displayedFuel = targetState.fuelLiters;
    private float displayedCoolant = targetState.coolantC;
    private float displayedSteering = targetState.steeringAngleDeg;
    private long lastFrameAtMs;
    private long cachedClockSecond = -1L;
    private String cachedClockText = "00:00";

    public SportClusterView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setBackgroundColor(Color.TRANSPARENT);

        calibratedEdgeBackground = loadBitmap(
                context,
                "dashboard/skins/common/calibrated_edge_background.png");
        staticBackground = loadBitmap(context, "dashboard/skins/sport/background.png");
        staticScaleOverlay = loadBitmap(
                context,
                "dashboard/skins/sport/scale_overlay.png");
        tyreCar = loadBitmap(context, "dashboard/skins/sport/car.png");
        mainNeedle = loadBitmap(context, "dashboard/skins/sport/needle_main.png");
        smallNeedle = loadBitmap(context, "dashboard/skins/sport/needle_small.png");
        dataTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/Inter-Regular.ttf");
        gaugeTypeface = Typeface.createFromAsset(
                context.getAssets(), "fonts/Rajdhani-Medium.ttf");
        wifiIndicator = new WifiIndicator(context);

        bitmapPaint.setAlpha(255);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
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
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long frameAtMs = SystemClock.elapsedRealtime();
        updateSmoothedValues(frameAtMs);
        updateClock();
        TransmissionTemperatureAlert.Level transmissionTemperatureLevel =
                updateTransmissionTemperatureAlert(targetState, frameAtMs);

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        float scale = Math.min(getWidth() / LOGICAL_WIDTH, getHeight() / LOGICAL_HEIGHT);
        float offsetX = (getWidth() - LOGICAL_WIDTH * scale) * 0.5f;
        float offsetY = (getHeight() - LOGICAL_HEIGHT * scale) * 0.5f;

        int rootSave = canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        drawStaticLayer(canvas, transmissionTemperatureLevel);
        drawNeedleLayer(canvas);
        drawTextLayer(canvas, frameAtMs, transmissionTemperatureLevel);

        canvas.restoreToCount(rootSave);
        if (needsAnotherAnimationFrame(frameAtMs)) {
            postInvalidateOnAnimation();
        } else {
            postInvalidateDelayed(1000L);
        }
    }

    private void drawStaticLayer(
            Canvas canvas,
            TransmissionTemperatureAlert.Level transmissionTemperatureLevel) {
        canvas.drawBitmap(
                calibratedEdgeBackground,
                (Rect) null,
                logicalBounds,
                bitmapPaint);
        canvas.drawBitmap(
                staticBackground,
                (Rect) null,
                logicalBounds,
                bitmapPaint);
        canvas.drawBitmap(
                staticScaleOverlay,
                (Rect) null,
                logicalBounds,
                bitmapPaint);
        canvas.drawBitmap(
                tyreCar,
                SPORT_TYRE_CAR_X,
                SPORT_TYRE_CAR_Y,
                bitmapPaint);

        // The factory turn arrows own 578..696 and 1196..1320. No application
        // card, border or text is drawn in those protected zones.
        drawTopCard(canvas, 12.0f, 276.0f);
        drawTopCard(canvas, 286.0f, 394.0f);
        drawTopCard(
                canvas,
                CLOCK_CARD_LEFT,
                CLOCK_CARD_TOP,
                CLOCK_CARD_RIGHT,
                CLOCK_CARD_BOTTOM,
                COLOR_CARD_BORDER);
        drawTopCard(canvas, 706.0f, 882.0f);
        drawTopCard(
                canvas,
                OUTSIDE_TEMPERATURE_CARD_LEFT,
                OUTSIDE_TEMPERATURE_CARD_RIGHT);
        drawTopCard(
                canvas,
                ATF_CARD_LEFT,
                ATF_CARD_TOP,
                ATF_CARD_RIGHT,
                ATF_CARD_BOTTOM,
                transmissionTemperatureColor(
                        transmissionTemperatureLevel,
                        COLOR_CARD_BORDER));

        configureText(dataTypeface, 31.0f, Paint.Align.CENTER, 0xFFFFFFFF, true, 0.0f);
        canvas.drawText(
                cachedClockText,
                CLOCK_CENTER_X,
                CLOCK_BASELINE,
                textPaint);
    }

    private void drawNeedleLayer(Canvas canvas) {
        float fuelFraction = clamp(displayedFuel / TANK_CAPACITY_LITERS, 0.0f, 1.0f);
        float coolantFraction = clamp((displayedCoolant - 40.0f) / 90.0f, 0.0f, 1.0f);

        drawSportScaleNeedle(
                canvas,
                mainNeedle,
                SportDialCalibration.speedNeedle(displayedSpeed),
                false,
                SPORT_MAIN_NEEDLE_GAP,
                56.0f,
                12.0f);
        drawSportScaleNeedle(
                canvas,
                mainNeedle,
                SportDialCalibration.rpm(displayedRpm),
                true,
                SPORT_MAIN_NEEDLE_GAP,
                56.0f,
                12.0f);

        drawScaleNeedle(
                canvas,
                smallNeedle,
                620.0f,
                378.0f,
                90.0f,
                120.0f,
                120.0f - fuelFraction * 196.0f,
                22.0f,
                42.0f,
                10.0f);
        drawScaleNeedle(
                canvas,
                smallNeedle,
                1300.0f,
                378.0f,
                90.0f,
                120.0f,
                64.0f + coolantFraction * 173.0f,
                22.0f,
                42.0f,
                10.0f);
    }

    private void drawSportScaleNeedle(
            Canvas canvas,
            Bitmap needle,
            SportDialCalibration.Sample sample,
            boolean rpmScale,
            float scaleGap,
            float length,
            float thickness) {
        float x = sample.x;
        float y = sample.y;
        float inwardX = rpmScale ? sample.tangentY : -sample.tangentY;
        float inwardY = rpmScale ? -sample.tangentX : sample.tangentX;
        float inwardLength = (float) Math.hypot(inwardX, inwardY);
        if (inwardLength < 0.001f) {
            return;
        }
        inwardX /= inwardLength;
        inwardY /= inwardLength;
        x += inwardX * scaleGap;
        y += inwardY * scaleGap;

        float inwardDirection = (float) Math.toDegrees(Math.atan2(inwardY, inwardX));
        int save = canvas.save();
        canvas.translate(x, y);
        canvas.rotate(inwardDirection - 180.0f);
        needleDestination.set(-length, -thickness * 0.5f, 0.0f, thickness * 0.5f);
        canvas.drawBitmap(needle, (Rect) null, needleDestination, bitmapPaint);
        canvas.restoreToCount(save);
    }

    private void drawScaleNeedle(
            Canvas canvas,
            Bitmap needle,
            float centerX,
            float centerY,
            float radiusX,
            float radiusY,
            float pathAngleDeg,
            float scaleGap,
            float length,
            float thickness) {
        double pathRadians = Math.toRadians(pathAngleDeg);
        float cosine = (float) Math.cos(pathRadians);
        float sine = (float) Math.sin(pathRadians);
        float ellipseRadius = radiusX * radiusY
                / (float) Math.sqrt(
                        radiusY * radiusY * cosine * cosine
                                + radiusX * radiusX * sine * sine);
        float needleRadius = Math.max(length + 8.0f, ellipseRadius - scaleGap);
        float baseX = centerX + cosine * needleRadius;
        float baseY = centerY + sine * needleRadius;
        float inwardDirection = (float) Math.toDegrees(
                Math.atan2(centerY - baseY, centerX - baseX));

        int save = canvas.save();
        canvas.translate(baseX, baseY);
        // The supplied bitmap points left and its rounded base is at the right edge.
        canvas.rotate(inwardDirection - 180.0f);
        needleDestination.set(-length, -thickness * 0.5f, 0.0f, thickness * 0.5f);
        canvas.drawBitmap(needle, (Rect) null, needleDestination, bitmapPaint);
        canvas.restoreToCount(save);
    }

    private void drawTextLayer(
            Canvas canvas,
            long frameAtMs,
            TransmissionTemperatureAlert.Level transmissionTemperatureLevel) {
        ClusterState state = targetState;

        drawLiveTelemetryCards(canvas, state);
        drawCurrentGear(canvas, state);
        wifiIndicator.draw(canvas, shapePaint, 1708.0f, 42.0f, frameAtMs);

        // Main values occupy fixed inner safe zones. Their size is reduced only when
        // the measured value would exceed the zone; the position itself never jumps.
        configureText(gaugeTypeface, 112.0f, Paint.Align.CENTER,
                0xFFF7F7F5, true, -0.16f);
        drawFittedText(
                canvas,
                Integer.toString(Math.round(displayedSpeed)),
                MAIN_SPEED_CENTER_X,
                435.0f,
                168.0f,
                112.0f,
                92.0f);
        configureText(gaugeTypeface, 112.0f, Paint.Align.CENTER,
                0xFFF7F7F5, true, -0.10f);
        drawFittedText(
                canvas,
                String.format(Locale.US, "%.1f", displayedRpm / 1000.0f),
                1581.0f,
                435.0f,
                178.0f,
                112.0f,
                92.0f);

        configureText(dataTypeface, 20.0f, Paint.Align.CENTER, 0xFFC8CDD1, false, 0.0f);
        canvas.drawText("km/h", MAIN_SPEED_CENTER_X, 496.0f, textPaint);

        // Keep the fuel values inside the user-supplied grey insert and clear
        // of the complete needle sweep.
        configureText(gaugeTypeface, 29.0f, Paint.Align.CENTER, 0xFFE7E8E8, true, -0.08f);
        drawFittedText(
                canvas,
                state.rangeKm + " km",
                602.0f,
                347.0f,
                78.0f,
                29.0f,
                22.0f);
        drawFittedText(
                canvas,
                Math.round(displayedFuel) + " L",
                596.0f,
                385.0f,
                72.0f,
                29.0f,
                22.0f);
        configureText(gaugeTypeface, 27.0f, Paint.Align.CENTER, 0xFFCFD2D4, true, -0.08f);
        drawFittedText(
                canvas,
                String.format(Locale.US, "%.1f", displayedFuel / TANK_CAPACITY_LITERS),
                564.0f,
                458.0f,
                40.0f,
                27.0f,
                20.0f);

        // The coolant value stays in the free area inside the right insert.
        configureText(gaugeTypeface, 31.0f, Paint.Align.CENTER, 0xFFF4F4F2, true, -0.08f);
        drawFittedText(
                canvas,
                Integer.toString(Math.round(displayedCoolant)),
                1300.0f,
                352.0f,
                46.0f,
                31.0f,
                23.0f);

        // Match the relocated Classic odometer block above the transparent windows.
        configureText(dataTypeface, 22.0f, Paint.Align.LEFT,
                0xFFF0F0EE, false, 0.0f);
        canvas.drawText("ODO:", ODOMETER_LABEL_X, ODOMETER_BASELINE, textPaint);
        canvas.drawText("Day:", ODOMETER_LABEL_X, DAY_ODOMETER_BASELINE, textPaint);
        canvas.drawText("Trip:", ODOMETER_LABEL_X, TRIP_ODOMETER_BASELINE, textPaint);
        configureText(gaugeTypeface, 29.0f, Paint.Align.LEFT,
                0xFFF8F8F7, false, -0.05f);
        canvas.drawText(String.format(Locale.US, "%.0f  km", state.odometerKm),
                ODOMETER_VALUE_X, ODOMETER_BASELINE, textPaint);
        canvas.drawText(String.format(Locale.US, "%.1f  km", state.dayKm),
                ODOMETER_VALUE_X, DAY_ODOMETER_BASELINE, textPaint);
        canvas.drawText(String.format(Locale.US, "%.1f  km", state.tripKm),
                ODOMETER_VALUE_X, TRIP_ODOMETER_BASELINE, textPaint);

        // Keep all four pressures around the car position from example.png.
        configureText(gaugeTypeface, 31.0f, Paint.Align.CENTER, 0xFFF4F4F2, false, -0.06f);
        drawFittedText(
                canvas,
                formatPressure(state.tyreFrontLeftBar),
                1490.0f,
                293.0f,
                74.0f,
                31.0f,
                25.0f);
        drawFittedText(
                canvas,
                formatPressure(state.tyreFrontRightBar),
                1617.0f,
                293.0f,
                74.0f,
                31.0f,
                25.0f);
        drawFittedText(
                canvas,
                formatPressure(state.tyreRearLeftBar),
                1490.0f,
                346.0f,
                74.0f,
                31.0f,
                25.0f);
        drawFittedText(
                canvas,
                formatPressure(state.tyreRearRightBar),
                1617.0f,
                346.0f,
                74.0f,
                31.0f,
                25.0f);

        // Live outside temperature and steering angle remain at the top.
        drawSteeringWheel(canvas, 762.0f, 44.0f, 13.0f, 0xFFD9DEE2);
        configureText(dataTypeface, 28.0f, Paint.Align.LEFT, 0xFFF7F7F5, false, 0.0f);
        canvas.drawText(formatSteering(displayedSteering), 784.0f, 53.0f, textPaint);
        configureText(dataTypeface, 27.0f, Paint.Align.CENTER, 0xFFF9F9F7, true, 0.0f);
        canvas.drawText(
                formatOutside(state.outsideTemperatureC),
                OUTSIDE_TEMPERATURE_X,
                55.0f,
                textPaint);
        configureText(dataTypeface, 11.0f, Paint.Align.CENTER, 0xFFA7AFB5, true, 0.0f);
        canvas.drawText("ATF", ATF_CENTER_X, ATF_LABEL_BASELINE, textPaint);
        configureText(
                gaugeTypeface,
                26.0f,
                Paint.Align.CENTER,
                transmissionTemperatureColor(
                        transmissionTemperatureLevel,
                        COLOR_ATF_NORMAL),
                true,
                -0.04f);
        canvas.drawText(
                formatTransmissionTemperature(state, frameAtMs),
                ATF_CENTER_X,
                ATF_VALUE_BASELINE,
                textPaint);

        // Instant consumption is above the existing average-consumption row.
        configureText(dataTypeface, 12.0f, Paint.Align.LEFT, 0xFFA7AFB5, true, 0.0f);
        canvas.drawText("INST", 18.0f, 653.0f, textPaint);
        configureText(gaugeTypeface, 25.0f, Paint.Align.CENTER, 0xFFF5F5F3, true, -0.06f);
        drawFittedText(
                canvas,
                FuelConsumptionFormatter.instant(state),
                108.0f,
                647.0f,
                126.0f,
                25.0f,
                19.0f);
        configureText(dataTypeface, 12.0f, Paint.Align.LEFT, 0xFFA7AFB5, true, 0.0f);
        canvas.drawText("AVG", 18.0f, 683.0f, textPaint);
        configureText(gaugeTypeface, 27.0f, Paint.Align.CENTER, 0xFFF5F5F3, true, -0.06f);
        drawFittedText(
                canvas,
                FuelConsumptionFormatter.average(
                        state.consumptionLitersPer100Km),
                108.0f,
                677.0f,
                126.0f,
                27.0f,
                20.0f);

        configureText(gaugeTypeface, 32.0f, Paint.Align.CENTER, 0xFFF5F5F3, true, -0.08f);
        drawFittedText(
                canvas,
                String.format(Locale.US, "%.1fV", state.voltage),
                1830.0f,
                678.0f,
                112.0f,
                32.0f,
                24.0f);
    }

    private void drawFittedText(
            Canvas canvas,
            String value,
            float centerX,
            float centerY,
            float maximumWidth,
            float desiredSize,
            float minimumSize) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(desiredSize);
        float measuredWidth = textPaint.measureText(value);
        if (measuredWidth > maximumWidth && measuredWidth > 0.0f) {
            float fittedSize = desiredSize * maximumWidth / measuredWidth;
            textPaint.setTextSize(Math.max(minimumSize, fittedSize));
        }
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = centerY - (metrics.ascent + metrics.descent) * 0.5f;
        canvas.drawText(value, centerX, baseline, textPaint);
    }

    private void drawSteeringWheel(
            Canvas canvas, float centerX, float centerY, float radius, int color) {
        linePaint.setColor(color);
        linePaint.setStrokeWidth(2.2f);
        canvas.drawCircle(centerX, centerY, radius, linePaint);
        canvas.drawCircle(centerX, centerY, 3.0f, linePaint);
        canvas.drawLine(centerX, centerY - 3.0f, centerX, centerY - radius, linePaint);
        canvas.drawLine(centerX - 2.5f, centerY + 2.0f,
                centerX - radius * 0.78f, centerY + radius * 0.55f, linePaint);
        canvas.drawLine(centerX + 2.5f, centerY + 2.0f,
                centerX + radius * 0.78f, centerY + radius * 0.55f, linePaint);
    }

    private void updateSmoothedValues(long now) {
        if (lastFrameAtMs == 0L) {
            lastFrameAtMs = now;
            displayedSpeed = targetState.speedKph;
            displayedFuel = targetState.fuelLiters;
            displayedCoolant = targetState.coolantC;
        }
        float deltaMs = Math.min(100.0f, Math.max(0.0f, now - lastFrameAtMs));
        lastFrameAtMs = now;
        float blend = 1.0f - (float) Math.exp(-deltaMs / 115.0f);

        displayedSpeed += (targetState.speedKph - displayedSpeed) * blend;
        displayedRpm = rpmSmoother.update(
                targetState.rpm,
                targetState.speedKph,
                now);
        displayedFuel += (targetState.fuelLiters - displayedFuel) * blend;
        displayedCoolant += (targetState.coolantC - displayedCoolant) * blend;
        displayedSteering = steeringMotion.update(now);
    }

    private boolean needsAnotherAnimationFrame(long nowMs) {
        return Math.abs(targetState.speedKph - displayedSpeed) > 0.05f
                || rpmSmoother.needsAnimationFrame(
                        targetState.rpm,
                        targetState.speedKph)
                || Math.abs(targetState.fuelLiters - displayedFuel) > 0.01f
                || Math.abs(targetState.coolantC - displayedCoolant) > 0.01f
                || steeringMotion.needsAnimationFrame(nowMs);
    }

    private void updateClock() {
        long wallTime = System.currentTimeMillis();
        long second = wallTime / 1000L;
        if (second != cachedClockSecond) {
            cachedClockSecond = second;
            cachedClockText = timeFormat.format(new Date(wallTime));
        }
    }

    private void configureText(
            Typeface typeface,
            float size,
            Paint.Align align,
            int color,
            boolean bold,
            float skewX) {
        textPaint.setTypeface(typeface);
        textPaint.setTextSize(size);
        textPaint.setTextAlign(align);
        textPaint.setColor(color);
        textPaint.setFakeBoldText(bold);
        textPaint.setTextSkewX(skewX);
        textPaint.setTextScaleX(1.0f);
        textPaint.setStyle(Paint.Style.FILL);
    }

    private void drawTopCard(Canvas canvas, float left, float right) {
        drawTopCard(canvas, left, 12.0f, right, 76.0f, COLOR_CARD_BORDER);
    }

    private void drawTopCard(
            Canvas canvas,
            float left,
            float top,
            float right,
            float bottom,
            int borderColor) {
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(0xFF080B0E);
        canvas.drawRoundRect(left, top, right, bottom, 19.0f, 19.0f, shapePaint);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(2.0f);
        shapePaint.setColor(borderColor);
        canvas.drawRoundRect(left, top, right, bottom, 19.0f, 19.0f, shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);
    }

    private void drawLiveTelemetryCards(Canvas canvas, ClusterState state) {
        configureText(dataTypeface, 10.0f, Paint.Align.CENTER, 0xFFA7AFB5, true, 0.0f);
        canvas.drawText("WHEEL SPEED  km/h", 144.0f, 25.0f, textPaint);

        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(1.0f);
        shapePaint.setColor(0xFF30363B);
        canvas.drawLine(144.0f, 30.0f, 144.0f, 70.0f, shapePaint);
        canvas.drawLine(22.0f, 54.0f, 266.0f, 54.0f, shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);

        drawWheelValue(canvas, state, "FL", state.wheelFrontLeftKph,
                30.0f, 88.0f, 47.0f, 43.0f);
        drawWheelValue(canvas, state, "FR", state.wheelFrontRightKph,
                162.0f, 220.0f, 47.0f, 43.0f);
        drawWheelValue(canvas, state, "RL", state.wheelRearLeftKph,
                30.0f, 88.0f, 70.0f, 66.0f);
        drawWheelValue(canvas, state, "RR", state.wheelRearRightKph,
                162.0f, 220.0f, 70.0f, 66.0f);

        configureText(dataTypeface, 11.0f, Paint.Align.CENTER, 0xFFA7AFB5, true, 0.0f);
        canvas.drawText("TRQ", 340.0f, 32.0f, textPaint);
        configureText(gaugeTypeface, 26.0f, Paint.Align.CENTER, 0xFFF7F7F5, true, -0.04f);
        drawFittedText(canvas, formatTorque(state.engineFlywheelTorque),
                340.0f, 57.0f, 94.0f, 26.0f, 19.0f);
    }

    private void drawCurrentGear(Canvas canvas, ClusterState state) {
        // Match ION AURORA: the factory owns selector letters and manual-mode text.
        // No lower GEAR card, frame, or duplicate D/M caption is drawn by the skin.
        if (!GearSelector.DRIVE.equals(state.gearSelector)) {
            return;
        }
        String ratio = GearSelector.ratio(state.currentGear);
        if (ratio.isEmpty()) {
            return;
        }
        configureText(gaugeTypeface, 44.0f, Paint.Align.CENTER, 0xFFFAFDFF, true, 0.0f);
        canvas.drawText(ratio, 1000.0f, 63.0f, textPaint);
    }

    private void drawWheelValue(
            Canvas canvas,
            ClusterState state,
            String label,
            float speedKph,
            float labelX,
            float valueX,
            float labelBaseline,
            float valueCenterY) {
        configureText(dataTypeface, 10.0f, Paint.Align.LEFT, 0xFFA7AFB5, true, 0.0f);
        canvas.drawText(label, labelX, labelBaseline, textPaint);
        configureText(
                gaugeTypeface,
                23.0f,
                Paint.Align.CENTER,
                wheelSpeedColor(state, speedKph),
                true,
                -0.04f);
        drawFittedText(
                canvas,
                formatWheelSpeed(speedKph),
                valueX,
                valueCenterY - 1.0f,
                92.0f,
                23.0f,
                18.0f);
    }

    private static Bitmap loadBitmap(Context context, String assetPath) {
        InputStream input = null;
        try {
            input = context.getAssets().open(assetPath);
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                throw new IllegalStateException("Cannot decode asset: " + assetPath);
            }
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

    private static String formatPressure(float pressure) {
        return String.format(Locale.US, "%.2f", pressure);
    }

    private static String formatOutside(float temperatureC) {
        return String.format(Locale.US, "%.1f °C", temperatureC);
    }

    private static String formatTransmissionTemperature(ClusterState state, long nowMs) {
        if (!state.hasTransmissionTemperature()
                || state.transmissionTemperatureUpdatedAtMs <= 0L
                || nowMs - state.transmissionTemperatureUpdatedAtMs
                        > TRANSMISSION_TEMPERATURE_STALE_AFTER_MS) {
            return "— °C";
        }
        return Math.round(state.transmissionTemperatureC) + " °C";
    }

    private TransmissionTemperatureAlert.Level updateTransmissionTemperatureAlert(
            ClusterState state,
            long nowMs) {
        boolean hasFreshValue = state.hasTransmissionTemperature()
                && state.transmissionTemperatureUpdatedAtMs > 0L
                && nowMs - state.transmissionTemperatureUpdatedAtMs
                        <= TRANSMISSION_TEMPERATURE_STALE_AFTER_MS;
        return transmissionTemperatureAlert.update(
                state.transmissionTemperatureC,
                hasFreshValue);
    }

    private static int transmissionTemperatureColor(
            TransmissionTemperatureAlert.Level level,
            int normalColor) {
        switch (level) {
            case ELEVATED:
                return COLOR_ATF_ELEVATED;
            case HOT:
                return COLOR_ATF_HOT;
            case CRITICAL:
                return COLOR_ATF_CRITICAL;
            case NORMAL:
            default:
                return normalColor;
        }
    }

    private static String formatSteering(float angleDeg) {
        int angle = Math.round(angleDeg);
        return angle > 0 ? "+" + angle + "°" : angle + "°";
    }

    private static String formatWheelSpeed(float speedKph) {
        return Float.isNaN(speedKph)
                ? "\u2014"
                : String.format(Locale.US, "%.1f", speedKph);
    }

    private static String formatTorque(float torque) {
        if (Float.isNaN(torque)) {
            return "\u2014";
        }
        return Math.abs(torque) >= 100.0f
                ? String.format(Locale.US, "%.0f", torque)
                : String.format(Locale.US, "%.1f", torque);
    }

    private static int wheelSpeedColor(ClusterState state, float wheelSpeed) {
        float frontLeft = state.wheelFrontLeftKph;
        float frontRight = state.wheelFrontRightKph;
        float rearLeft = state.wheelRearLeftKph;
        float rearRight = state.wheelRearRightKph;
        if (Float.isNaN(wheelSpeed)
                || Float.isNaN(frontLeft)
                || Float.isNaN(frontRight)
                || Float.isNaN(rearLeft)
                || Float.isNaN(rearRight)) {
            return 0xFFF7F7F5;
        }

        float minimum = Math.min(Math.min(frontLeft, frontRight), Math.min(rearLeft, rearRight));
        float maximum = Math.max(Math.max(frontLeft, frontRight), Math.max(rearLeft, rearRight));
        if (maximum < 4.0f) {
            return 0xFFF7F7F5;
        }
        float median =
                (frontLeft + frontRight + rearLeft + rearRight - minimum - maximum) * 0.5f;
        float threshold = Math.max(4.0f, median * 0.18f);
        return Math.abs(wheelSpeed - median) >= threshold
                ? 0xFFFFC247
                : 0xFFF7F7F5;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
