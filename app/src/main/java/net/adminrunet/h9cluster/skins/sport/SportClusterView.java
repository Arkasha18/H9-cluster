package net.adminrunet.h9cluster.skins.sport;

import net.adminrunet.h9cluster.ClusterRenderer;
import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.PredictiveMotionFilter;
import net.adminrunet.h9cluster.TransmissionTemperatureAlert;

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

/** Independent renderer for the asymmetric red Sport skin. */
public final class SportClusterView extends View implements ClusterRenderer {
    private static final float LOGICAL_WIDTH = 1920.0f;
    private static final float LOGICAL_HEIGHT = 720.0f;
    private static final float MAX_SPEED_KPH = 220.0f;
    private static final float MAX_RPM = 8000.0f;
    private static final float TANK_CAPACITY_LITERS = 80.0f;
    // 32 logical pixels are approximately 5 mm on the 1920x720 cluster panel.
    private static final float SPORT_GAUGE_OFFSET_Y = 32.0f;
    private static final float SPORT_HALF_WIDTH = LOGICAL_WIDTH * 0.5f;
    private static final RectF SPORT_TYRE_CAR_BOUNDS =
            new RectF(1378.0f, 500.0f, 1418.0f, 590.0f);
    // Normalized positions and red-track coordinates for the asymmetric main scales.
    // The shape starts on the short lower inner arm, wraps around the outer edge,
    // and finishes on the long upper inner arm. The RPM scale mirrors these points.
    private static final float SPORT_MAIN_NEEDLE_GAP = 16.0f;
    private static final float[] SPORT_SCALE_FRACTIONS = {
            0.0f,
            20.0f / 220.0f,
            40.0f / 220.0f,
            60.0f / 220.0f,
            80.0f / 220.0f,
            100.0f / 220.0f,
            120.0f / 220.0f,
            140.0f / 220.0f,
            180.0f / 220.0f,
            1.0f
    };
    private static final float[] SPORT_SCALE_X = {
            558.0f, 320.0f, 225.0f, 165.0f, 115.0f,
            95.0f, 120.0f, 205.0f, 345.0f, 525.0f
    };
    private static final float[] SPORT_SCALE_Y = {
            575.0f, 575.0f, 550.0f, 505.0f, 440.0f,
            365.0f, 285.0f, 220.0f, 150.0f, 148.0f
    };
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

    private final Bitmap staticBackground;
    private final Bitmap staticOverlay;
    private final Bitmap mainNeedle;
    private final Bitmap smallNeedle;
    private final Typeface dataTypeface;
    private final Typeface gaugeTypeface;

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

        staticBackground = loadBitmap(context, "dashboard/skins/sport/background.png");
        staticOverlay = loadBitmap(context, "dashboard/skins/sport/overlay.png");
        mainNeedle = loadBitmap(context, "dashboard/skins/sport/needle_main.png");
        smallNeedle = loadBitmap(context, "dashboard/skins/sport/needle_small.png");
        dataTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/Inter-Regular.ttf");
        gaugeTypeface = Typeface.createFromAsset(
                context.getAssets(), "fonts/Rajdhani-Medium.ttf");

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
        displayedRpm = state.rpm;
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
        drawShiftedSportBitmap(canvas, staticBackground, true, false);
        drawShiftedSportBitmap(canvas, staticOverlay, false, true);

        // The factory turn arrows own 578..696 and 1196..1320. No application
        // card, border or text is drawn in those protected zones.
        drawTopCard(canvas, 12.0f, 276.0f);
        drawTopCard(canvas, 286.0f, 394.0f);
        drawTopCard(canvas, 404.0f, 568.0f);
        drawTopCard(canvas, 706.0f, 882.0f);
        drawTopCard(canvas, 1038.0f, 1186.0f);
        drawTopCard(
                canvas,
                1330.0f,
                1478.0f,
                transmissionTemperatureColor(
                        transmissionTemperatureLevel,
                        COLOR_CARD_BORDER));

        configureText(dataTypeface, 31.0f, Paint.Align.CENTER, 0xFFFFFFFF, true, 0.0f);
        canvas.drawText(cachedClockText, 486.0f, 55.0f, textPaint);
    }

    private void drawShiftedSportBitmap(
            Canvas canvas,
            Bitmap bitmap,
            boolean removeScaleArtifacts,
            boolean preserveTyreCar) {
        drawShiftedSportHalf(
                canvas,
                bitmap,
                0.0f,
                SPORT_HALF_WIDTH,
                removeScaleArtifacts,
                false);
        drawShiftedSportHalf(
                canvas,
                bitmap,
                SPORT_HALF_WIDTH,
                LOGICAL_WIDTH,
                removeScaleArtifacts,
                preserveTyreCar);

        if (preserveTyreCar) {
            int carSave = canvas.save();
            canvas.clipRect(SPORT_TYRE_CAR_BOUNDS);
            canvas.drawBitmap(bitmap, (Rect) null, logicalBounds, bitmapPaint);
            canvas.restoreToCount(carSave);
        }
    }

    private void drawShiftedSportHalf(
            Canvas canvas,
            Bitmap bitmap,
            float left,
            float right,
            boolean removeScaleArtifacts,
            boolean removeShiftedTyreCar) {
        int save = canvas.save();
        canvas.clipRect(left, 0.0f, right, LOGICAL_HEIGHT);

        if (removeScaleArtifacts) {
            // The source artwork contains decorative horizontal remnants which
            // become especially visible over navigation. Exclude only those
            // source regions while retaining the shaped Sport scale.
            if (left < SPORT_HALF_WIDTH) {
                canvas.clipOutRect(
                        0.0f,
                        84.0f + SPORT_GAUGE_OFFSET_Y,
                        568.0f,
                        109.0f + SPORT_GAUGE_OFFSET_Y);
                canvas.clipOutRect(
                        202.0f,
                        594.0f + SPORT_GAUGE_OFFSET_Y,
                        444.0f,
                        617.0f + SPORT_GAUGE_OFFSET_Y);
            } else {
                canvas.clipOutRect(
                        1352.0f,
                        84.0f + SPORT_GAUGE_OFFSET_Y,
                        LOGICAL_WIDTH,
                        109.0f + SPORT_GAUGE_OFFSET_Y);
                canvas.clipOutRect(
                        1476.0f,
                        594.0f + SPORT_GAUGE_OFFSET_Y,
                        1728.0f,
                        617.0f + SPORT_GAUGE_OFFSET_Y);
            }
        }

        if (removeShiftedTyreCar) {
            canvas.clipOutRect(
                    SPORT_TYRE_CAR_BOUNDS.left,
                    SPORT_TYRE_CAR_BOUNDS.top + SPORT_GAUGE_OFFSET_Y,
                    SPORT_TYRE_CAR_BOUNDS.right,
                    SPORT_TYRE_CAR_BOUNDS.bottom + SPORT_GAUGE_OFFSET_Y);
        }

        canvas.translate(0.0f, SPORT_GAUGE_OFFSET_Y);
        canvas.drawBitmap(bitmap, (Rect) null, logicalBounds, bitmapPaint);
        canvas.restoreToCount(save);
    }

    private void drawNeedleLayer(Canvas canvas) {
        float speedFraction = clamp(displayedSpeed / MAX_SPEED_KPH, 0.0f, 1.0f);
        float rpmFraction = clamp(displayedRpm / MAX_RPM, 0.0f, 1.0f);
        float fuelFraction = clamp(displayedFuel / TANK_CAPACITY_LITERS, 0.0f, 1.0f);
        float coolantFraction = clamp((displayedCoolant - 40.0f) / 90.0f, 0.0f, 1.0f);

        drawSportScaleNeedle(
                canvas,
                mainNeedle,
                speedFraction,
                false,
                SPORT_MAIN_NEEDLE_GAP,
                56.0f,
                12.0f);
        drawSportScaleNeedle(
                canvas,
                mainNeedle,
                rpmFraction,
                true,
                SPORT_MAIN_NEEDLE_GAP,
                56.0f,
                12.0f);

        drawScaleNeedle(
                canvas,
                smallNeedle,
                620.0f,
                324.0f + SPORT_GAUGE_OFFSET_Y,
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
                324.0f + SPORT_GAUGE_OFFSET_Y,
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
            float fraction,
            boolean mirrored,
            float scaleGap,
            float length,
            float thickness) {
        float clampedFraction = clamp(fraction, 0.0f, 1.0f);
        int lastPoint = SPORT_SCALE_FRACTIONS.length - 1;
        int segment = lastPoint - 1;
        for (int index = 0; index < lastPoint; index++) {
            if (clampedFraction <= SPORT_SCALE_FRACTIONS[index + 1]) {
                segment = index;
                break;
            }
        }

        float fractionStart = SPORT_SCALE_FRACTIONS[segment];
        float fractionEnd = SPORT_SCALE_FRACTIONS[segment + 1];
        float fractionSpan = fractionEnd - fractionStart;
        float t = fractionSpan > 0.0f
                ? (clampedFraction - fractionStart) / fractionSpan
                : 0.0f;

        float startSlopeX = sportScaleSlope(SPORT_SCALE_X, segment);
        float startSlopeY = sportScaleSlope(SPORT_SCALE_Y, segment);
        float endSlopeX = sportScaleSlope(SPORT_SCALE_X, segment + 1);
        float endSlopeY = sportScaleSlope(SPORT_SCALE_Y, segment + 1);

        float x = hermitePosition(
                SPORT_SCALE_X[segment],
                SPORT_SCALE_X[segment + 1],
                startSlopeX,
                endSlopeX,
                fractionSpan,
                t);
        float y = hermitePosition(
                SPORT_SCALE_Y[segment],
                SPORT_SCALE_Y[segment + 1],
                startSlopeY,
                endSlopeY,
                fractionSpan,
                t);
        float tangentX = hermiteTangent(
                SPORT_SCALE_X[segment],
                SPORT_SCALE_X[segment + 1],
                startSlopeX,
                endSlopeX,
                fractionSpan,
                t);
        float tangentY = hermiteTangent(
                SPORT_SCALE_Y[segment],
                SPORT_SCALE_Y[segment + 1],
                startSlopeY,
                endSlopeY,
                fractionSpan,
                t);

        if (mirrored) {
            x = LOGICAL_WIDTH - x;
            tangentX = -tangentX;
        }
        y += SPORT_GAUGE_OFFSET_Y;

        float inwardX = mirrored ? tangentY : -tangentY;
        float inwardY = mirrored ? -tangentX : tangentX;
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

    private static float sportScaleSlope(float[] coordinates, int index) {
        int lastPoint = SPORT_SCALE_FRACTIONS.length - 1;
        if (index <= 0) {
            return (coordinates[1] - coordinates[0])
                    / (SPORT_SCALE_FRACTIONS[1] - SPORT_SCALE_FRACTIONS[0]);
        }
        if (index >= lastPoint) {
            return (coordinates[lastPoint] - coordinates[lastPoint - 1])
                    / (SPORT_SCALE_FRACTIONS[lastPoint]
                    - SPORT_SCALE_FRACTIONS[lastPoint - 1]);
        }
        return (coordinates[index + 1] - coordinates[index - 1])
                / (SPORT_SCALE_FRACTIONS[index + 1]
                - SPORT_SCALE_FRACTIONS[index - 1]);
    }

    private static float hermitePosition(
            float start,
            float end,
            float startSlope,
            float endSlope,
            float fractionSpan,
            float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return (2.0f * t3 - 3.0f * t2 + 1.0f) * start
                + (t3 - 2.0f * t2 + t) * fractionSpan * startSlope
                + (-2.0f * t3 + 3.0f * t2) * end
                + (t3 - t2) * fractionSpan * endSlope;
    }

    private static float hermiteTangent(
            float start,
            float end,
            float startSlope,
            float endSlope,
            float fractionSpan,
            float t) {
        float t2 = t * t;
        return (6.0f * t2 - 6.0f * t) * start
                + (3.0f * t2 - 4.0f * t + 1.0f) * fractionSpan * startSlope
                + (-6.0f * t2 + 6.0f * t) * end
                + (3.0f * t2 - 2.0f * t) * fractionSpan * endSlope;
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
        drawCurrentGearCard(canvas, state);

        // Main values occupy fixed inner safe zones. Their size is reduced only when
        // the measured value would exceed the zone; the position itself never jumps.
        configureText(gaugeTypeface, 112.0f, Paint.Align.CENTER,
                0xFFF7F7F5, true, -0.16f);
        drawFittedText(
                canvas,
                Integer.toString(Math.round(displayedSpeed)),
                349.0f,
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
        canvas.drawText("km/h", 349.0f, 496.0f, textPaint);

        // Keep the fuel values inside the grey insert and clear of the full needle
        // sweep. Range and fuel fraction are nudged about 1 mm farther right.
        configureText(gaugeTypeface, 29.0f, Paint.Align.CENTER, 0xFFE7E8E8, true, -0.08f);
        drawFittedText(
                canvas,
                state.rangeKm + " km",
                602.0f,
                279.0f + SPORT_GAUGE_OFFSET_Y,
                72.0f,
                29.0f,
                22.0f);
        drawFittedText(
                canvas,
                Math.round(displayedFuel) + " L",
                596.0f,
                317.0f + SPORT_GAUGE_OFFSET_Y,
                72.0f,
                29.0f,
                22.0f);
        configureText(gaugeTypeface, 27.0f, Paint.Align.CENTER, 0xFFCFD2D4, true, -0.08f);
        drawFittedText(
                canvas,
                String.format(Locale.US, "%.1f", displayedFuel / TANK_CAPACITY_LITERS),
                564.0f,
                389.0f + SPORT_GAUGE_OFFSET_Y,
                34.0f,
                27.0f,
                20.0f);

        // Shift the coolant value about 5 mm left at 160 dpi. Its narrower fitted
        // box still clears the complete 40-to-130 needle sweep.
        configureText(gaugeTypeface, 31.0f, Paint.Align.CENTER, 0xFFF4F4F2, true, -0.08f);
        drawFittedText(
                canvas,
                Integer.toString(Math.round(displayedCoolant)),
                1300.0f,
                289.0f + SPORT_GAUGE_OFFSET_Y,
                40.0f,
                31.0f,
                23.0f);

        // Center the odometer rows in the free black field above speed.
        configureText(gaugeTypeface, 21.0f, Paint.Align.CENTER,
                0xFFF3F3F1, false, -0.04f);
        drawFittedText(
                canvas,
                String.format(Locale.US, "ODO  %.0f km", state.odometerKm),
                400.0f,
                260.0f,
                150.0f,
                21.0f,
                18.0f);
        drawFittedText(
                canvas,
                String.format(Locale.US, "Day  %.1f km", state.dayKm),
                400.0f,
                290.0f,
                150.0f,
                21.0f,
                18.0f);
        drawFittedText(
                canvas,
                String.format(Locale.US, "Trip  %.1f km", state.tripKm),
                400.0f,
                320.0f,
                150.0f,
                21.0f,
                18.0f);

        // Keep both rows aligned to the front and rear of the fixed car graphic.
        configureText(gaugeTypeface, 24.0f, Paint.Align.CENTER, 0xFFF4F4F2, false, -0.06f);
        drawFittedText(
                canvas,
                formatPressure(state.tyreFrontLeftBar),
                1335.0f,
                529.0f,
                74.0f,
                24.0f,
                19.0f);
        drawFittedText(
                canvas,
                formatPressure(state.tyreFrontRightBar),
                1459.0f,
                529.0f,
                74.0f,
                24.0f,
                19.0f);
        drawFittedText(
                canvas,
                formatPressure(state.tyreRearLeftBar),
                1335.0f,
                559.0f,
                74.0f,
                24.0f,
                19.0f);
        drawFittedText(
                canvas,
                formatPressure(state.tyreRearRightBar),
                1459.0f,
                559.0f,
                74.0f,
                24.0f,
                19.0f);

        // Live outside temperature and steering angle remain at the top.
        drawSteeringWheel(canvas, 762.0f, 44.0f, 13.0f, 0xFFD9DEE2);
        configureText(dataTypeface, 23.0f, Paint.Align.LEFT, 0xFFF7F7F5, false, 0.0f);
        canvas.drawText(formatSteering(displayedSteering), 784.0f, 53.0f, textPaint);
        configureText(dataTypeface, 27.0f, Paint.Align.CENTER, 0xFFF9F9F7, true, 0.0f);
        canvas.drawText(formatOutside(state.outsideTemperatureC), 1112.0f, 55.0f, textPaint);
        configureText(dataTypeface, 11.0f, Paint.Align.CENTER, 0xFFA7AFB5, true, 0.0f);
        canvas.drawText("ATF", 1404.0f, 32.0f, textPaint);
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
                1404.0f,
                61.0f,
                textPaint);

        // Bottom live metrics.
        configureText(gaugeTypeface, 32.0f, Paint.Align.CENTER, 0xFFF5F5F3, true, -0.08f);
        drawFittedText(
                canvas,
                String.format(Locale.US, "%.1fL", state.consumptionLitersPer100Km),
                91.0f,
                678.0f,
                112.0f,
                32.0f,
                24.0f);
        configureText(dataTypeface, 16.0f, Paint.Align.LEFT, 0xFFD6D9DB, true, 0.0f);
        canvas.drawText("/100 km", 46.0f, 712.0f, textPaint);

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
        displayedFuel += (targetState.fuelLiters - displayedFuel) * blend;
        displayedCoolant += (targetState.coolantC - displayedCoolant) * blend;
        displayedSteering = steeringMotion.update(now);
    }

    private boolean needsAnotherAnimationFrame(long nowMs) {
        return Math.abs(targetState.speedKph - displayedSpeed) > 0.05f
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
        textPaint.setStyle(Paint.Style.FILL);
    }

    private void drawTopCard(Canvas canvas, float left, float right) {
        drawTopCard(canvas, left, right, COLOR_CARD_BORDER);
    }

    private void drawTopCard(Canvas canvas, float left, float right, int borderColor) {
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(0xFF080B0E);
        canvas.drawRoundRect(left, 12.0f, right, 76.0f, 19.0f, 19.0f, shapePaint);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(2.0f);
        shapePaint.setColor(borderColor);
        canvas.drawRoundRect(left, 12.0f, right, 76.0f, 19.0f, 19.0f, shapePaint);
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
        configureText(gaugeTypeface, 21.0f, Paint.Align.CENTER, 0xFFF7F7F5, true, -0.04f);
        drawFittedText(canvas, formatTorque(state.engineFlywheelTorque),
                340.0f, 57.0f, 94.0f, 21.0f, 16.0f);
    }

    private void drawCurrentGearCard(Canvas canvas, ClusterState state) {
        RectF bounds = new RectF(916.0f, 78.0f, 1004.0f, 140.0f);

        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(0xFF080B0E);
        canvas.drawRoundRect(bounds, 18.0f, 18.0f, shapePaint);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(2.0f);
        shapePaint.setColor(0xFF4C535A);
        canvas.drawRoundRect(bounds, 18.0f, 18.0f, shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);

        configureText(dataTypeface, 10.0f, Paint.Align.CENTER, 0xFFA7AFB5, true, 0.0f);
        canvas.drawText("GEAR", bounds.centerX(), 96.0f, textPaint);
        configureText(gaugeTypeface, 29.0f, Paint.Align.CENTER, 0xFFF7F7F5, true, -0.04f);
        canvas.drawText(
                state.currentGear > 0 ? Integer.toString(state.currentGear) : "",
                bounds.centerX(),
                130.0f,
                textPaint);
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
                18.0f,
                Paint.Align.CENTER,
                wheelSpeedColor(state, speedKph),
                true,
                -0.04f);
        drawFittedText(
                canvas,
                formatWheelSpeed(speedKph),
                valueX,
                valueCenterY,
                92.0f,
                18.0f,
                15.0f);
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
