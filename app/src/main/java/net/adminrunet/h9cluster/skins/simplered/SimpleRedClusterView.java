package net.adminrunet.h9cluster.skins.simplered;

import net.adminrunet.h9cluster.BuildConfig;
import net.adminrunet.h9cluster.ClusterRenderer;
import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.PredictiveMotionFilter;
import net.adminrunet.h9cluster.TransmissionTemperatureAlert;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/** Simple Red renderer that preserves the factory indicator zones. */
public final class SimpleRedClusterView extends View
        implements ClusterRenderer {
    private static final float LOGICAL_WIDTH = 1920.0f;
    private static final float LOGICAL_HEIGHT = 720.0f;
    private final Paint bitmapPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG
                    | Paint.DITHER_FLAG
                    | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TransmissionTemperatureAlert transmissionTemperatureAlert =
            new TransmissionTemperatureAlert();

    private final Typeface dataTypeface;
    private final Typeface gaugeTypeface;
    private final Typeface scaleTypeface;

    private final PredictiveMotionFilter steeringMotion =
            new PredictiveMotionFilter(
                    -1080.0f,
                    1080.0f,
                    1080.0f,
                    450L,
                    35.0f,
                    60.0f,
                    12.0f,
                    0.1f,
                    0.15f);
    private Bitmap staticBackground;
    private ClusterState targetState = ClusterState.empty();
    private float displayedSpeed = targetState.speedKph;
    private float displayedRpm = targetState.rpm;
    private float displayedSteering = targetState.steeringAngleDeg;
    private long lastFrameAtMs;

    public SimpleRedClusterView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setBackgroundColor(Color.TRANSPARENT);

        dataTypeface = Typeface.createFromAsset(
                context.getAssets(),
                "fonts/Inter-Regular.ttf");
        gaugeTypeface = Typeface.create(
                "sans-serif",
                Typeface.BOLD);
        scaleTypeface = Typeface.createFromAsset(
                context.getAssets(),
                SimpleRedLayout.SCALE_LABEL_FONT_ASSET);

        bitmapPaint.setAlpha(255);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.BUTT);
    }

    @Override
    public void setClusterState(ClusterState state) {
        if (state == null) {
            return;
        }
        targetState = state;
        displayedRpm = state.rpm;
        if (state.steeringUpdatedAtMs > 0L) {
            steeringMotion.onSample(
                    state.steeringAngleDeg,
                    state.steeringUpdatedAtMs);
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        staticBackground = w > 0 && h > 0
                ? renderStaticLayer(w, h, BuildConfig.DEMO_MODE)
                : null;
    }

    @Override
    protected void onDetachedFromWindow() {
        staticBackground = null;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long frameAtMs = SystemClock.elapsedRealtime();
        updateSmoothedValues(frameAtMs);
        updateTransmissionTemperatureAlert(targetState, frameAtMs);

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        drawStaticLayer(canvas);

        int rootSave = canvas.save();
        applyLogicalTransform(canvas, getWidth(), getHeight());

        drawProgressLayer(canvas);
        drawTextLayer(canvas, frameAtMs);

        canvas.restoreToCount(rootSave);
        if (needsAnotherAnimationFrame(frameAtMs)) {
            postInvalidateOnAnimation();
        } else {
            postInvalidateDelayed(1000L);
        }
    }

    /**
     * Renders the static layer once per size change. The blur filters it
     * uses are only reliable on a software canvas, which is what drawing
     * into an offscreen bitmap gives us.
     */
    private static Bitmap renderStaticLayer(
            int width,
            int height,
            boolean demoMode) {
        Bitmap bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        applyLogicalTransform(canvas, width, height);
        SimpleRedBackground.draw(canvas, demoMode);
        return bitmap;
    }

    private void drawStaticLayer(Canvas canvas) {
        if (staticBackground == null) {
            return;
        }
        canvas.drawBitmap(staticBackground, 0.0f, 0.0f, bitmapPaint);
    }

    private static void applyLogicalTransform(
            Canvas canvas,
            int width,
            int height) {
        float scale = Math.min(
                width / LOGICAL_WIDTH,
                height / LOGICAL_HEIGHT);
        canvas.translate(
                (width - LOGICAL_WIDTH * scale) * 0.5f,
                (height - LOGICAL_HEIGHT * scale) * 0.5f);
        canvas.scale(scale, scale);
    }

    private void drawProgressLayer(Canvas canvas) {
        float speedFraction = clamp(
                SimpleRedLayout.speedFraction(displayedSpeed),
                0.0f,
                1.0f);
        float rpmFraction = clamp(
                SimpleRedLayout.rpmFraction(displayedRpm),
                0.0f,
                1.0f);
        drawScaleProgress(
                canvas,
                speedFraction,
                false);
        drawScaleProgress(
                canvas,
                rpmFraction,
                true);
    }

    private void drawScaleProgress(
            Canvas canvas,
            float fraction,
            boolean rightGauge) {
        float clampedFraction = clamp(fraction, 0.0f, 1.0f);
        if (clampedFraction < 0.002f) {
            return;
        }
        float centerX = rightGauge
                ? SimpleRedLayout.RIGHT_GAUGE_CENTER_X
                : SimpleRedLayout.LEFT_GAUGE_CENTER_X;
        float centerY = SimpleRedLayout.GAUGE_CENTER_Y;
        float radius = SimpleRedLayout.PROGRESS_BAND_RADIUS;
        RectF arcBounds = new RectF(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius);
        float sweep = SimpleRedLayout.progressSweepDegrees(
                clampedFraction);

        Shader glowGradient = progressGradient(
                centerX,
                centerY,
                clampedFraction,
                true);
        progressPaint.setStrokeWidth(
                SimpleRedLayout.PROGRESS_HALO_WIDTH);
        progressPaint.setAlpha(
                SimpleRedLayout.PROGRESS_HALO_ALPHA);
        progressPaint.setShader(glowGradient);
        canvas.drawArc(
                arcBounds,
                SimpleRedLayout.SCALE_START_ANGLE_DEGREES,
                sweep,
                false,
                progressPaint);

        progressPaint.setStrokeWidth(
                SimpleRedLayout.PROGRESS_GLOW_WIDTH);
        progressPaint.setAlpha(255);
        canvas.drawArc(
                arcBounds,
                SimpleRedLayout.SCALE_START_ANGLE_DEGREES,
                sweep,
                false,
                progressPaint);

        progressPaint.setStrokeWidth(
                SimpleRedLayout.PROGRESS_CORE_WIDTH);
        progressPaint.setShader(progressGradient(
                centerX,
                centerY,
                clampedFraction,
                false));
        canvas.drawArc(
                arcBounds,
                SimpleRedLayout.SCALE_START_ANGLE_DEGREES,
                sweep,
                false,
                progressPaint);
        progressPaint.setAlpha(255);
        progressPaint.setShader(null);
    }

    private Shader progressGradient(
            float centerX,
            float centerY,
            float fraction,
            boolean glow) {
        float end = 0.5f + fraction * 0.5f;
        float warmStart = Math.max(
                0.5f,
                end - Math.min(0.08f, fraction * 0.25f));
        int redStart = glow
                ? 0x00FF2020
                : SimpleRedLayout.PROGRESS_START_COLOR;
        int red = glow ? 0x44FF2020 : 0xAAFF2020;
        int leading = glow
                ? 0xCCFFD54F
                : SimpleRedLayout.PROGRESS_LEADING_COLOR;
        return new SweepGradient(
                centerX,
                centerY,
                new int[] {
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                        redStart,
                        red,
                        leading
                },
                new float[] {
                        0.0f,
                        0.499f,
                        0.5f,
                        warmStart,
                        end
                });
    }

    private void drawTextLayer(
            Canvas canvas,
            long frameAtMs) {
        ClusterState state = targetState;

        drawScaleLabels(canvas);
        drawCurrentGear(canvas, state);
        drawTyrePressures(canvas, state);
        drawBottomValues(
                canvas,
                state,
                frameAtMs);
    }

    private void drawScaleLabels(Canvas canvas) {
        configureText(
                scaleTypeface,
                27.0f,
                Paint.Align.CENTER,
                0xFFF4F5F5,
                true);
        textPaint.setTextSkewX(SimpleRedLayout.SCALE_LABEL_SKEW_X);
        int speedSteps = SimpleRedLayout.majorTickIntervals(false);
        for (int index = 0; index <= speedSteps; index++) {
            int speed = index * SimpleRedLayout.SPEED_LABEL_STEP_KPH;
            drawScaleText(
                    canvas,
                    Integer.toString(speed),
                    SimpleRedLayout.speedFraction(speed),
                    false,
                    SimpleRedLayout.MAIN_SCALE_LABEL_OFFSET);
        }
        int rpmSteps = SimpleRedLayout.majorTickIntervals(true);
        for (int index = 0; index <= rpmSteps; index++) {
            drawScaleText(
                    canvas,
                    Integer.toString(index),
                    (float) index / rpmSteps,
                    true,
                    SimpleRedLayout.MAIN_SCALE_LABEL_OFFSET);
        }

        if (SimpleRedLayout.DRAW_SCALE_UNITS) {
            configureText(
                    dataTypeface,
                    15.0f,
                    Paint.Align.CENTER,
                    0xFFCBD0D3,
                    true);
            drawScaleText(canvas, "km/h", 0.90f, false, 98.0f);
            drawScaleText(canvas, "×1000 rpm", 0.90f, true, 98.0f);
        }
    }

    private void drawScaleText(
            Canvas canvas,
            String value,
            float fraction,
            boolean rightGauge,
            float inwardOffset) {
        float x = SimpleRedLayout.scaleX(fraction, rightGauge);
        float y = SimpleRedLayout.scaleY(fraction);
        float tangentX = SimpleRedLayout.scaleTangentX(fraction);
        float tangentY = SimpleRedLayout.scaleTangentY(fraction);
        float length = (float) Math.hypot(tangentX, tangentY);
        if (length < 0.001f) {
            return;
        }
        x += -tangentY / length * inwardOffset;
        y += tangentX / length * inwardOffset;
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = y - (metrics.ascent + metrics.descent) * 0.5f;
        canvas.drawText(value, x, baseline, textPaint);
    }

    private void drawSteeringIcon(Canvas canvas) {
        int save = canvas.save();
        canvas.rotate(
                SimpleRedLayout.steeringRotation(displayedSteering),
                SimpleRedLayout.STEERING_ICON_X,
                SimpleRedLayout.STEERING_ICON_Y);
        drawSteeringWheel(
                canvas,
                SimpleRedLayout.STEERING_ICON_X,
                SimpleRedLayout.STEERING_ICON_Y,
                SimpleRedLayout.STEERING_ICON_RADIUS,
                SimpleRedLayout.STEERING_COLOR);
        canvas.restoreToCount(save);
    }

    private void drawCurrentGear(Canvas canvas, ClusterState state) {
        configureText(
                gaugeTypeface,
                44.0f,
                Paint.Align.CENTER,
                0xFFF7F7F5,
                true);
        canvas.drawText(
                SimpleRedLayout.formatGear(state.currentGear),
                SimpleRedLayout.GEAR_NUMBER_X,
                SimpleRedLayout.GEAR_NUMBER_BASELINE,
                textPaint);
    }

    private void drawTyrePressures(Canvas canvas, ClusterState state) {
        configureText(
                gaugeTypeface,
                SimpleRedLayout.TYRE_TEXT_SIZE,
                Paint.Align.CENTER,
                0xFFF4F4F2,
                false);
        drawTyrePressure(
                canvas,
                state.tyreFrontLeftBar,
                SimpleRedLayout.TYRE_LEFT_X,
                SimpleRedLayout.TYRE_TOP_Y);
        drawTyrePressure(
                canvas,
                state.tyreFrontRightBar,
                SimpleRedLayout.TYRE_RIGHT_X,
                SimpleRedLayout.TYRE_TOP_Y);
        drawTyrePressure(
                canvas,
                state.tyreRearLeftBar,
                SimpleRedLayout.TYRE_LEFT_X,
                SimpleRedLayout.TYRE_BOTTOM_Y);
        drawTyrePressure(
                canvas,
                state.tyreRearRightBar,
                SimpleRedLayout.TYRE_RIGHT_X,
                SimpleRedLayout.TYRE_BOTTOM_Y);
        drawSteeringIcon(canvas);
    }

    private void drawTyrePressure(
            Canvas canvas,
            float pressure,
            float x,
            float y) {
        textPaint.setColor(SimpleRedLayout.pressureColor(pressure));
        canvas.drawText(
                SimpleRedLayout.formatPressure(pressure),
                x,
                y,
                textPaint);
    }

    private void drawBottomValues(
            Canvas canvas,
            ClusterState state,
            long frameAtMs) {
        configureText(
                gaugeTypeface,
                22.0f,
                Paint.Align.LEFT,
                SimpleRedLayout.consumptionColor(
                        state.consumptionLitersPer100Km),
                true);
        canvas.drawText(
                SimpleRedLayout.formatConsumption(
                        state.consumptionLitersPer100Km),
                SimpleRedLayout.CONSUMPTION_X,
                SimpleRedLayout.CONSUMPTION_BASELINE,
                textPaint);

        configureText(
                gaugeTypeface,
                22.0f,
                Paint.Align.RIGHT,
                SimpleRedLayout.fuelColor(state.fuelLiters),
                true);
        canvas.drawText(
                SimpleRedLayout.formatFuel(state.fuelLiters),
                SimpleRedLayout.FUEL_LITERS_X,
                SimpleRedLayout.FUEL_LITERS_BASELINE,
                textPaint);

        configureText(
                gaugeTypeface,
                22.0f,
                Paint.Align.RIGHT,
                SimpleRedLayout.temperatureColor(state.coolantC),
                true);
        canvas.drawText(
                SimpleRedLayout.formatCoolant(state.coolantC),
                SimpleRedLayout.COOLANT_X,
                SimpleRedLayout.COOLANT_BASELINE,
                textPaint);

        int transmissionColor = SimpleRedLayout.temperatureColor(
                state.transmissionTemperatureC);
        String transmissionTemperature =
                SimpleRedLayout.formatTransmissionTemperature(
                        state.transmissionTemperatureC,
                        state.transmissionTemperatureUpdatedAtMs,
                        frameAtMs);
        configureText(
                gaugeTypeface,
                22.0f,
                Paint.Align.LEFT,
                transmissionColor,
                true);
        canvas.drawText(
                transmissionTemperature,
                SimpleRedLayout.TRANSMISSION_X,
                SimpleRedLayout.TRANSMISSION_BASELINE,
                textPaint);
        if (!transmissionTemperature.isEmpty()) {
            configureText(
                    dataTypeface,
                    9.0f,
                    Paint.Align.RIGHT,
                    transmissionColor,
                    true);
            canvas.drawText(
                    "АКПП",
                    SimpleRedLayout.TRANSMISSION_LABEL_X,
                    SimpleRedLayout.TRANSMISSION_LABEL_Y,
                    textPaint);
        }

        configureText(
                gaugeTypeface,
                24.0f,
                Paint.Align.RIGHT,
                SimpleRedLayout.voltageColor(state.voltage),
                true);
        canvas.drawText(
                SimpleRedLayout.formatVoltage(state.voltage),
                SimpleRedLayout.VOLTAGE_X,
                SimpleRedLayout.VOLTAGE_BASELINE,
                textPaint);
    }

    private void drawSteeringWheel(
            Canvas canvas,
            float centerX,
            float centerY,
            float radius,
            int color) {
        linePaint.setColor(color);
        linePaint.setStrokeWidth(SimpleRedLayout.STEERING_RIM_WIDTH);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(centerX, centerY, radius, linePaint);
        float barY = centerY
                + SimpleRedLayout.STEERING_T_BAR_Y_OFFSET;
        linePaint.setStrokeWidth(SimpleRedLayout.STEERING_SPOKE_WIDTH);
        canvas.drawLine(
                centerX - SimpleRedLayout.STEERING_T_BAR_HALF_WIDTH,
                barY,
                centerX + SimpleRedLayout.STEERING_T_BAR_HALF_WIDTH,
                barY,
                linePaint);
        canvas.drawLine(
                centerX,
                barY,
                centerX,
                barY + SimpleRedLayout.STEERING_T_STEM_LENGTH,
                linePaint);
        linePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(
                centerX,
                barY,
                SimpleRedLayout.STEERING_HUB_RADIUS,
                linePaint);
        linePaint.setColor(Color.BLACK);
        canvas.drawCircle(
                centerX,
                barY,
                SimpleRedLayout.STEERING_HUB_HOLE_RADIUS,
                linePaint);
        linePaint.setStyle(Paint.Style.STROKE);
    }

    private void updateSmoothedValues(long now) {
        if (lastFrameAtMs == 0L) {
            lastFrameAtMs = now;
            displayedSpeed = targetState.speedKph;
        }
        float deltaMs = Math.min(
                100.0f,
                Math.max(0.0f, now - lastFrameAtMs));
        lastFrameAtMs = now;
        float blend =
                1.0f - (float) Math.exp(-deltaMs / 115.0f);

        displayedSpeed +=
                (targetState.speedKph - displayedSpeed) * blend;
        displayedSteering = steeringMotion.update(now);
    }

    private boolean needsAnotherAnimationFrame(long nowMs) {
        return Math.abs(targetState.speedKph - displayedSpeed) > 0.05f
                || steeringMotion.needsAnimationFrame(nowMs);
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
        textPaint.setFakeBoldText(bold);
        textPaint.setTextSkewX(SimpleRedLayout.TEXT_SKEW_X);
        textPaint.setTextScaleX(1.0f);
        textPaint.setStyle(Paint.Style.FILL);
    }

    private TransmissionTemperatureAlert.Level
            updateTransmissionTemperatureAlert(
                    ClusterState state,
                    long nowMs) {
        boolean hasFreshValue =
                state.hasTransmissionTemperature()
                        && state.transmissionTemperatureUpdatedAtMs > 0L
                        && nowMs
                        - state.transmissionTemperatureUpdatedAtMs
                        <= SimpleRedLayout
                        .TRANSMISSION_TEMPERATURE_STALE_AFTER_MS;
        return transmissionTemperatureAlert.update(
                state.transmissionTemperatureC,
                hasFreshValue);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
