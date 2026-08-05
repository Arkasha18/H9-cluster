package net.adminrunet.h9cluster.skins.simple;

import net.adminrunet.h9cluster.BuildConfig;
import net.adminrunet.h9cluster.ClusterRenderer;
import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.GearSelector;
import net.adminrunet.h9cluster.PredictiveMotionFilter;
import net.adminrunet.h9cluster.TransmissionTemperatureAlert;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/** Simple skin renderer that preserves the factory indicator zones. */
public final class SimpleClusterView extends View
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
    /**
     * The stretched band is no longer an arc Canvas can draw, so it is
     * sampled into a path. Reused and rewound every frame to keep this
     * off the per-frame allocation path, like the bloom shader below.
     */
    private final Path progressPath = new Path();
    private final Matrix progressGradientMatrix = new Matrix();
    private final TransmissionTemperatureAlert transmissionTemperatureAlert =
            new TransmissionTemperatureAlert();
    /**
     * Chosen in the skin settings. Held per instance because the shaders and
     * the cached background are built from it, so a new colour arrives as a
     * new view rather than as a repaint.
     */
    private final SimpleScaleColor scaleColor;
    private final Shader tipBloomShader;

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

    public SimpleClusterView(
            Context context,
            SimpleScaleColor scaleColor) {
        super(context);
        this.scaleColor = scaleColor == null
                ? SimpleScaleColor.defaultColor()
                : scaleColor;
        tipBloomShader = createTipBloomShader(this.scaleColor);
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
                SimpleLayout.SCALE_LABEL_FONT_ASSET);

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
                ? renderStaticLayer(w, h, BuildConfig.DEMO_MODE, scaleColor)
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
            boolean demoMode,
            SimpleScaleColor scaleColor) {
        Bitmap bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        applyLogicalTransform(canvas, width, height);
        SimpleBackground.draw(canvas, demoMode, scaleColor);
        return bitmap;
    }

    private void drawStaticLayer(Canvas canvas) {
        if (staticBackground == null) {
            // Detaching releases the bitmap, and a re-attach at an unchanged
            // size never calls onSizeChanged, so rebuilding has to happen
            // here as well. Otherwise the arcs and ticks stay gone for the
            // rest of the view's life while the live layers keep drawing.
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            staticBackground = renderStaticLayer(
                    width,
                    height,
                    BuildConfig.DEMO_MODE,
                    scaleColor);
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
                SimpleLayout.indicatedSpeedFraction(displayedSpeed),
                0.0f,
                1.0f);
        float rpmFraction = clamp(
                SimpleLayout.rpmFraction(displayedRpm),
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
                ? SimpleLayout.RIGHT_GAUGE_CENTER_X
                : SimpleLayout.LEFT_GAUGE_CENTER_X;
        float centerY = SimpleLayout.GAUGE_CENTER_Y;
        buildProgressPath(clampedFraction, rightGauge);

        progressPaint.setShader(progressGradient(
                centerX,
                centerY,
                clampedFraction,
                rightGauge,
                true));
        for (int layer = 0;
                layer < SimpleLayout.PROGRESS_SOFT_LAYER_COUNT;
                layer++) {
            progressPaint.setAlpha(
                    SimpleLayout.progressSoftLayerAlpha(layer));
            progressPaint.setStrokeWidth(
                    SimpleLayout.progressSoftLayerWidth(layer));
            canvas.drawPath(progressPath, progressPaint);
        }

        progressPaint.setStrokeWidth(
                SimpleLayout.PROGRESS_CORE_WIDTH);
        progressPaint.setAlpha(255);
        progressPaint.setShader(progressGradient(
                centerX,
                centerY,
                clampedFraction,
                rightGauge,
                false));
        canvas.drawPath(progressPath, progressPaint);

        progressPaint.setShader(null);
        progressPaint.setAlpha(255);
        drawProgressTipBloom(canvas, clampedFraction, rightGauge);
    }

    /** Samples the band from the start of the scale up to the fraction. */
    private void buildProgressPath(float fraction, boolean rightGauge) {
        progressPath.rewind();
        float end = SimpleLayout.scaleLength(fraction);
        int segments = SimpleLayout.pathSegments(0.0f, end);
        for (int index = 0; index <= segments; index++) {
            float along = end * index / segments;
            float x = SimpleLayout.pointXAt(
                    along,
                    SimpleLayout.PROGRESS_BAND_RADIUS,
                    rightGauge);
            float y = SimpleLayout.pointYAt(
                    along,
                    SimpleLayout.PROGRESS_BAND_RADIUS,
                    rightGauge);
            if (index == 0) {
                progressPath.moveTo(x, y);
            } else {
                progressPath.lineTo(x, y);
            }
        }
    }

    /**
     * Soft halo around the leading edge, so it reads as a glow. The shader
     * is built once at the origin and moved by translating the canvas,
     * keeping this off the per-frame allocation path.
     */
    private void drawProgressTipBloom(
            Canvas canvas,
            float fraction,
            boolean rightGauge) {
        float tipX = SimpleLayout.radialX(
                fraction,
                SimpleLayout.PROGRESS_BAND_RADIUS,
                rightGauge);
        float tipY = SimpleLayout.radialY(
                fraction,
                SimpleLayout.PROGRESS_BAND_RADIUS,
                rightGauge);
        int save = canvas.save();
        canvas.translate(tipX, tipY);
        progressPaint.setStyle(Paint.Style.FILL);
        progressPaint.setShader(tipBloomShader);
        canvas.drawCircle(
                0.0f,
                0.0f,
                SimpleLayout.PROGRESS_TIP_BLOOM_RADIUS,
                progressPaint);
        progressPaint.setShader(null);
        progressPaint.setStyle(Paint.Style.STROKE);
        canvas.restoreToCount(save);
    }

    private static int withAlpha(int color, int alpha) {
        return alpha << 24 | color & 0x00FFFFFF;
    }

    private static Shader createTipBloomShader(SimpleScaleColor color) {
        int center = (SimpleLayout.PROGRESS_TIP_BLOOM_ALPHA << 24)
                | (color.bloom() & 0x00FFFFFF);
        return new RadialGradient(
                0.0f,
                0.0f,
                SimpleLayout.PROGRESS_TIP_BLOOM_RADIUS,
                new int[] {center, center & 0x00FFFFFF},
                new float[] {0.0f, 1.0f},
                Shader.TileMode.CLAMP);
    }

    /**
     * Sweep gradient keyed to the band of the gauge being drawn rather
     * than to a fixed angle. The tachometer is the speedometer mirrored,
     * so its band occupies entirely different angles; a shared set of
     * stops left the first stretch of it outside the coloured range and
     * therefore invisible.
     *
     * <p>The shader is built at the origin and turned onto the band by a
     * reused matrix, which keeps the rotation off the allocation path.</p>
     */
    private Shader progressGradient(
            float centerX,
            float centerY,
            float fraction,
            boolean rightGauge,
            boolean glow) {
        float leadIn = SimpleLayout.PROGRESS_GRADIENT_LEAD_IN_DEGREES;
        float bandStart = SimpleLayout.bandAngleDegrees(0.0f, rightGauge);
        float travelled = SimpleLayout.bandAngleDegrees(
                fraction,
                rightGauge) - bandStart;
        float end = (travelled + leadIn) / 360.0f;
        float warmStart = clamp(
                end - Math.min(0.08f, fraction * 0.25f),
                0.001f,
                Math.max(0.002f, end - 0.001f));
        // The halo pass runs the same colours at a fraction of their
        // opacity, so it reads as a glow around the band rather than as a
        // second band.
        int redStart = glow
                ? scaleColor.accent & 0x00FFFFFF
                : scaleColor.bandStart();
        int red = glow
                ? withAlpha(scaleColor.accent, 0x44)
                : scaleColor.bandBody();
        int leading = glow
                ? withAlpha(scaleColor.leading, 0xCC)
                : scaleColor.leading;
        SweepGradient gradient = new SweepGradient(
                centerX,
                centerY,
                new int[] {redStart, red, leading},
                new float[] {0.0f, warmStart, end});
        progressGradientMatrix.setRotate(
                bandStart - leadIn,
                centerX,
                centerY);
        gradient.setLocalMatrix(progressGradientMatrix);
        return gradient;
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
                SimpleLayout.SCALE_LABEL_TEXT_SIZE,
                Paint.Align.CENTER,
                SimpleLayout.SCALE_LABEL_COLOR,
                true);
        textPaint.setTextSkewX(SimpleLayout.SCALE_LABEL_SKEW_X);
        int speedSteps = SimpleLayout.majorTickIntervals(false);
        for (int index = 0; index <= speedSteps; index++) {
            int speed = index * SimpleLayout.SPEED_LABEL_STEP_KPH;
            drawScaleText(
                    canvas,
                    Integer.toString(speed),
                    SimpleLayout.speedFraction(speed),
                    false,
                    SimpleLayout.MAIN_SCALE_LABEL_OFFSET);
        }
        int rpmSteps = SimpleLayout.majorTickIntervals(true);
        for (int index = 0; index <= rpmSteps; index++) {
            drawScaleText(
                    canvas,
                    Integer.toString(index),
                    (float) index / rpmSteps,
                    true,
                    SimpleLayout.MAIN_SCALE_LABEL_OFFSET);
        }

        if (SimpleLayout.DRAW_SCALE_UNITS) {
            configureText(
                    dataTypeface,
                    SimpleLayout.SCALE_UNIT_TEXT_SIZE,
                    Paint.Align.CENTER,
                    SimpleLayout.SCALE_UNIT_COLOR,
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
        float x = SimpleLayout.scaleX(fraction, rightGauge);
        float y = SimpleLayout.scaleY(fraction, rightGauge);
        float tangentX = SimpleLayout.scaleTangentX(fraction, rightGauge);
        float tangentY = SimpleLayout.scaleTangentY(fraction, rightGauge);
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
                SimpleLayout.steeringRotation(displayedSteering),
                SimpleLayout.STEERING_ICON_X,
                SimpleLayout.STEERING_ICON_Y);
        drawSteeringWheel(
                canvas,
                SimpleLayout.STEERING_ICON_X,
                SimpleLayout.STEERING_ICON_Y,
                SimpleLayout.STEERING_ICON_RADIUS,
                SimpleLayout.STEERING_COLOR);
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
                currentGearLabel(state.gearSelector, state.currentGear),
                SimpleLayout.GEAR_NUMBER_X,
                SimpleLayout.GEAR_NUMBER_BASELINE,
                textPaint);
    }

    static String currentGearLabel(String selector, int gear) {
        return GearSelector.DRIVE.equals(selector)
                ? GearSelector.ratio(gear)
                : "";
    }

    private void drawTyrePressures(Canvas canvas, ClusterState state) {
        configureText(
                gaugeTypeface,
                SimpleLayout.TYRE_TEXT_SIZE,
                Paint.Align.CENTER,
                0xFFF4F4F2,
                false);
        drawTyrePressure(
                canvas,
                state.tyreFrontLeftBar,
                SimpleLayout.TYRE_LEFT_X,
                SimpleLayout.TYRE_TOP_Y);
        drawTyrePressure(
                canvas,
                state.tyreFrontRightBar,
                SimpleLayout.TYRE_RIGHT_X,
                SimpleLayout.TYRE_TOP_Y);
        drawTyrePressure(
                canvas,
                state.tyreRearLeftBar,
                SimpleLayout.TYRE_LEFT_X,
                SimpleLayout.TYRE_BOTTOM_Y);
        drawTyrePressure(
                canvas,
                state.tyreRearRightBar,
                SimpleLayout.TYRE_RIGHT_X,
                SimpleLayout.TYRE_BOTTOM_Y);
        drawSteeringIcon(canvas);
    }

    private void drawTyrePressure(
            Canvas canvas,
            float pressure,
            float x,
            float y) {
        textPaint.setColor(SimpleLayout.pressureColor(pressure));
        canvas.drawText(
                SimpleLayout.formatPressure(pressure),
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
                SimpleLayout.consumptionColor(
                        state.consumptionLitersPer100Km),
                true);
        canvas.drawText(
                SimpleLayout.formatConsumption(
                        state.consumptionLitersPer100Km),
                SimpleLayout.CONSUMPTION_X,
                SimpleLayout.CONSUMPTION_BASELINE,
                textPaint);

        configureText(
                gaugeTypeface,
                22.0f,
                Paint.Align.RIGHT,
                SimpleLayout.fuelColor(state.fuelLiters),
                true);
        canvas.drawText(
                SimpleLayout.formatFuel(state.fuelLiters),
                SimpleLayout.FUEL_LITERS_X,
                SimpleLayout.FUEL_LITERS_BASELINE,
                textPaint);

        configureText(
                gaugeTypeface,
                22.0f,
                Paint.Align.RIGHT,
                SimpleLayout.temperatureColor(state.coolantC),
                true);
        canvas.drawText(
                SimpleLayout.formatCoolant(state.coolantC),
                SimpleLayout.COOLANT_X,
                SimpleLayout.COOLANT_BASELINE,
                textPaint);

        int transmissionColor = SimpleLayout.temperatureColor(
                state.transmissionTemperatureC);
        String transmissionTemperature =
                SimpleLayout.formatTransmissionTemperature(
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
                SimpleLayout.TRANSMISSION_X,
                SimpleLayout.TRANSMISSION_BASELINE,
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
                    SimpleLayout.TRANSMISSION_LABEL_X,
                    SimpleLayout.TRANSMISSION_LABEL_Y,
                    textPaint);
        }

        configureText(
                gaugeTypeface,
                24.0f,
                Paint.Align.RIGHT,
                SimpleLayout.voltageColor(state.voltage),
                true);
        canvas.drawText(
                SimpleLayout.formatVoltage(state.voltage),
                SimpleLayout.VOLTAGE_X,
                SimpleLayout.VOLTAGE_BASELINE,
                textPaint);
    }

    private void drawSteeringWheel(
            Canvas canvas,
            float centerX,
            float centerY,
            float radius,
            int color) {
        linePaint.setColor(color);
        linePaint.setStrokeWidth(SimpleLayout.STEERING_RIM_WIDTH);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(centerX, centerY, radius, linePaint);
        float barY = centerY
                + SimpleLayout.STEERING_T_BAR_Y_OFFSET;
        linePaint.setStrokeWidth(SimpleLayout.STEERING_SPOKE_WIDTH);
        canvas.drawLine(
                centerX - SimpleLayout.STEERING_T_BAR_HALF_WIDTH,
                barY,
                centerX + SimpleLayout.STEERING_T_BAR_HALF_WIDTH,
                barY,
                linePaint);
        canvas.drawLine(
                centerX,
                barY,
                centerX,
                barY + SimpleLayout.STEERING_T_STEM_LENGTH,
                linePaint);
        linePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(
                centerX,
                barY,
                SimpleLayout.STEERING_HUB_RADIUS,
                linePaint);
        linePaint.setColor(Color.BLACK);
        canvas.drawCircle(
                centerX,
                barY,
                SimpleLayout.STEERING_HUB_HOLE_RADIUS,
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
        textPaint.setTextSkewX(SimpleLayout.TEXT_SKEW_X);
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
                        <= SimpleLayout
                        .TRANSMISSION_TEMPERATURE_STALE_AFTER_MS;
        return transmissionTemperatureAlert.update(
                state.transmissionTemperatureC,
                hasFreshValue);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
