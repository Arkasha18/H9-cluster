package net.adminrunet.h9cluster.skins.horizon;

import net.adminrunet.h9cluster.ClusterRenderer;
import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.TransmissionTemperatureAlert;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Original, asset-free renderer for a 1920 x 720 instrument display.
 *
 * The middle of the surface deliberately stays transparent so the factory
 * navigation and transmission layers remain visible underneath.
 */
public final class HorizonClusterView extends View implements ClusterRenderer {
    private static final float LOGICAL_WIDTH = 1920.0f;
    private static final float LOGICAL_HEIGHT = 720.0f;
    private static final float TANK_CAPACITY_LITERS = 80.0f;

    private static final int COLOR_TEXT = 0xFFF4FBFC;
    private static final int COLOR_MUTED = 0xFF82969D;
    private static final int COLOR_TRACK = 0xFF263A40;
    private static final int COLOR_ACCENT = 0xFF31D7C5;
    private static final int COLOR_ACCENT_SOFT = 0xFF1B827C;
    private static final int COLOR_WARNING = 0xFFFF6B5F;
    private static final int COLOR_PANEL = 0xE80A171C;
    private static final int COLOR_CARD = 0xD912242A;
    private static final int COLOR_BORDER = 0xFF28434A;
    private static final int COLOR_ATF_ELEVATED = 0xFFFFD54F;
    private static final int COLOR_ATF_HOT = 0xFFFF8A3D;
    private static final int COLOR_ATF_CRITICAL = 0xFFFF4D4D;

    private static final float STATUS_GEAR_GAP_LEFT = 920.0f;
    private static final float STATUS_GEAR_GAP_RIGHT = 1000.0f;
    private static final long TRANSMISSION_TEMPERATURE_STALE_AFTER_MS = 15000L;

    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path reusablePath = new Path();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final TransmissionTemperatureAlert transmissionTemperatureAlert =
            new TransmissionTemperatureAlert();

    private ClusterState targetState = ClusterState.empty();
    private float displayedSpeed = targetState.speedKph;
    private float displayedRpm = targetState.rpm;
    private float displayedFuel = targetState.fuelLiters;
    private float displayedCoolant = targetState.coolantC;
    private long lastFrameAtMs;

    public HorizonClusterView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    @Override
    public void setClusterState(ClusterState state) {
        if (state == null) {
            return;
        }
        targetState = state;
        displayedRpm = state.rpm;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long frameAtMs = SystemClock.elapsedRealtime();
        updateSmoothedValues(frameAtMs);

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        float scale = Math.min(getWidth() / LOGICAL_WIDTH, getHeight() / LOGICAL_HEIGHT);
        float offsetX = (getWidth() - (LOGICAL_WIDTH * scale)) * 0.5f;
        float offsetY = (getHeight() - (LOGICAL_HEIGHT * scale)) * 0.5f;

        int rootSave = canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        drawSidePanels(canvas);
        drawGauge(canvas, 330.0f, 315.0f, displayedSpeed, 220.0f, false);
        drawGauge(canvas, 1590.0f, 315.0f, displayedRpm, 8000.0f, true);
        drawFuelCard(canvas, targetState);
        drawCoolantCard(canvas, targetState);
        drawStatusBar(canvas, targetState, frameAtMs);
        drawOdometerCard(canvas, targetState);
        drawTyreCard(canvas, targetState);

        canvas.restoreToCount(rootSave);

        if (needsAnotherAnimationFrame(frameAtMs)) {
            postInvalidateOnAnimation();
        } else {
            postInvalidateDelayed(1000L);
        }
    }

    private void drawSidePanels(Canvas canvas) {
        reusablePath.reset();
        reusablePath.moveTo(0.0f, 0.0f);
        reusablePath.lineTo(594.0f, 0.0f);
        reusablePath.cubicTo(655.0f, 78.0f, 690.0f, 184.0f, 690.0f, 310.0f);
        reusablePath.cubicTo(690.0f, 498.0f, 651.0f, 630.0f, 606.0f, 720.0f);
        reusablePath.lineTo(0.0f, 720.0f);
        reusablePath.close();

        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setShader(new LinearGradient(
                0.0f,
                0.0f,
                690.0f,
                0.0f,
                0xFF050D10,
                COLOR_PANEL,
                Shader.TileMode.CLAMP));
        canvas.drawPath(reusablePath, shapePaint);
        shapePaint.setShader(null);

        reusablePath.reset();
        reusablePath.moveTo(LOGICAL_WIDTH, 0.0f);
        reusablePath.lineTo(1326.0f, 0.0f);
        reusablePath.cubicTo(1265.0f, 78.0f, 1230.0f, 184.0f, 1230.0f, 310.0f);
        reusablePath.cubicTo(1230.0f, 498.0f, 1269.0f, 630.0f, 1314.0f, 720.0f);
        reusablePath.lineTo(LOGICAL_WIDTH, 720.0f);
        reusablePath.close();

        shapePaint.setShader(new LinearGradient(
                1230.0f,
                0.0f,
                LOGICAL_WIDTH,
                0.0f,
                COLOR_PANEL,
                0xFF050D10,
                Shader.TileMode.CLAMP));
        canvas.drawPath(reusablePath, shapePaint);
        shapePaint.setShader(null);

        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(2.0f);
        shapePaint.setColor(0x5A31D7C5);
        reusablePath.reset();
        reusablePath.moveTo(594.0f, 0.0f);
        reusablePath.cubicTo(655.0f, 78.0f, 690.0f, 184.0f, 690.0f, 310.0f);
        reusablePath.cubicTo(690.0f, 498.0f, 651.0f, 630.0f, 606.0f, 720.0f);
        canvas.drawPath(reusablePath, shapePaint);
        reusablePath.reset();
        reusablePath.moveTo(1326.0f, 0.0f);
        reusablePath.cubicTo(1265.0f, 78.0f, 1230.0f, 184.0f, 1230.0f, 310.0f);
        reusablePath.cubicTo(1230.0f, 498.0f, 1269.0f, 630.0f, 1314.0f, 720.0f);
        canvas.drawPath(reusablePath, shapePaint);

        shapePaint.setStrokeWidth(1.0f);
        shapePaint.setColor(0x182ECABD);
        for (int y = 90; y < 690; y += 54) {
            canvas.drawLine(26.0f, y, 610.0f, y, shapePaint);
            canvas.drawLine(1310.0f, y, 1894.0f, y, shapePaint);
        }
    }

    private void drawGauge(
            Canvas canvas,
            float centerX,
            float centerY,
            float value,
            float maximum,
            boolean tachometer) {
        float radius = 218.0f;
        RectF arcBounds = new RectF(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius);

        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setShader(new RadialGradient(
                centerX,
                centerY,
                radius,
                new int[] {0xD90B1A1F, 0xB80A1519, 0x000A1519},
                new float[] {0.0f, 0.76f, 1.0f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(centerX, centerY, radius, shapePaint);
        shapePaint.setShader(null);

        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeCap(Paint.Cap.ROUND);
        shapePaint.setStrokeWidth(18.0f);
        shapePaint.setColor(COLOR_TRACK);
        canvas.drawArc(arcBounds, 135.0f, 270.0f, false, shapePaint);

        float fraction = clamp(value / maximum, 0.0f, 1.0f);
        shapePaint.setStrokeWidth(20.0f);
        shapePaint.setColor(tachometer && value >= 6500.0f ? COLOR_WARNING : COLOR_ACCENT);
        canvas.drawArc(arcBounds, 135.0f, fraction * 270.0f, false, shapePaint);

        if (tachometer) {
            shapePaint.setStrokeWidth(6.0f);
            shapePaint.setColor(COLOR_WARNING);
            canvas.drawArc(arcBounds, 337.5f, 67.5f, false, shapePaint);
        }

        drawGaugeTicks(canvas, centerX, centerY, radius, tachometer);

        configureText(122.0f, Paint.Align.CENTER, COLOR_TEXT, true);
        String valueText = tachometer
                ? String.format(Locale.US, "%.1f", value / 1000.0f)
                : Integer.toString(Math.round(value));
        canvas.drawText(valueText, centerX, centerY + 34.0f, textPaint);

        configureText(24.0f, Paint.Align.CENTER, COLOR_MUTED, false);
        canvas.drawText(tachometer ? "x1000 rpm" : "km/h", centerX, centerY + 78.0f, textPaint);

        configureText(14.0f, Paint.Align.CENTER, COLOR_ACCENT, true);
        canvas.drawText(tachometer ? "ENGINE" : "SPEED", centerX, centerY - 92.0f, textPaint);
    }

    private void drawGaugeTicks(
            Canvas canvas,
            float centerX,
            float centerY,
            float radius,
            boolean tachometer) {
        int divisions = tachometer ? 8 : 11;
        for (int index = 0; index <= divisions; index++) {
            float fraction = index / (float) divisions;
            double angle = Math.toRadians(135.0f + (270.0f * fraction));
            float outerX = centerX + ((float) Math.cos(angle) * (radius - 28.0f));
            float outerY = centerY + ((float) Math.sin(angle) * (radius - 28.0f));
            float innerX = centerX + ((float) Math.cos(angle) * (radius - 46.0f));
            float innerY = centerY + ((float) Math.sin(angle) * (radius - 46.0f));

            shapePaint.setStyle(Paint.Style.STROKE);
            shapePaint.setStrokeCap(Paint.Cap.ROUND);
            shapePaint.setStrokeWidth(3.0f);
            shapePaint.setColor(index == divisions && tachometer ? COLOR_WARNING : 0xFF9DB1B6);
            canvas.drawLine(innerX, innerY, outerX, outerY, shapePaint);

            float labelX = centerX + ((float) Math.cos(angle) * (radius - 72.0f));
            float labelY = centerY + ((float) Math.sin(angle) * (radius - 72.0f)) + 7.0f;
            configureText(20.0f, Paint.Align.CENTER, COLOR_MUTED, true);
            canvas.drawText(
                    tachometer ? Integer.toString(index) : Integer.toString(index * 20),
                    labelX,
                    labelY,
                    textPaint);
        }
    }

    private void drawFuelCard(Canvas canvas, ClusterState state) {
        RectF card = new RectF(590.0f, 112.0f, 706.0f, 438.0f);
        drawCard(canvas, card, 28.0f);

        configureText(14.0f, Paint.Align.CENTER, COLOR_MUTED, true);
        canvas.drawText("FUEL", card.centerX(), 145.0f, textPaint);

        float fraction = clamp(displayedFuel / TANK_CAPACITY_LITERS, 0.0f, 1.0f);
        int accent = fraction < 0.15f ? COLOR_WARNING : COLOR_ACCENT;
        RectF rail = new RectF(615.0f, 174.0f, 631.0f, 348.0f);
        drawRail(canvas, rail, fraction, accent);

        configureText(42.0f, Paint.Align.CENTER, COLOR_TEXT, true);
        canvas.drawText(Integer.toString(Math.round(state.fuelLiters)), 666.0f, 236.0f, textPaint);
        configureText(17.0f, Paint.Align.CENTER, COLOR_MUTED, false);
        canvas.drawText("L", 666.0f, 261.0f, textPaint);

        configureText(31.0f, Paint.Align.CENTER, COLOR_TEXT, true);
        canvas.drawText(Integer.toString(state.rangeKm), card.centerX(), 387.0f, textPaint);
        configureText(14.0f, Paint.Align.CENTER, COLOR_MUTED, false);
        canvas.drawText("RANGE  km", card.centerX(), 414.0f, textPaint);
    }

    private void drawCoolantCard(Canvas canvas, ClusterState state) {
        RectF card = new RectF(1214.0f, 112.0f, 1330.0f, 438.0f);
        drawCard(canvas, card, 28.0f);

        configureText(14.0f, Paint.Align.CENTER, COLOR_MUTED, true);
        canvas.drawText("COOLANT", card.centerX(), 145.0f, textPaint);

        float fraction = clamp((displayedCoolant - 40.0f) / 90.0f, 0.0f, 1.0f);
        int accent = displayedCoolant >= 110.0f ? COLOR_WARNING : COLOR_ACCENT;
        RectF rail = new RectF(1290.0f, 174.0f, 1306.0f, 348.0f);
        drawRail(canvas, rail, fraction, accent);

        configureText(42.0f, Paint.Align.CENTER, COLOR_TEXT, true);
        canvas.drawText(Integer.toString(state.coolantC), 1252.0f, 236.0f, textPaint);
        configureText(17.0f, Paint.Align.CENTER, COLOR_MUTED, false);
        canvas.drawText("\u00b0C", 1252.0f, 261.0f, textPaint);

        configureText(14.0f, Paint.Align.CENTER, COLOR_MUTED, false);
        canvas.drawText("40", 1270.0f, 349.0f, textPaint);
        canvas.drawText("130", 1270.0f, 181.0f, textPaint);
    }

    private void drawRail(Canvas canvas, RectF bounds, float fraction, int accent) {
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(COLOR_TRACK);
        canvas.drawRoundRect(bounds, bounds.width() * 0.5f, bounds.width() * 0.5f, shapePaint);

        if (fraction <= 0.0f) {
            return;
        }
        float height = Math.max(bounds.width(), bounds.height() * fraction);
        RectF fill = new RectF(bounds.left, bounds.bottom - height, bounds.right, bounds.bottom);
        shapePaint.setColor(accent);
        canvas.drawRoundRect(fill, bounds.width() * 0.5f, bounds.width() * 0.5f, shapePaint);
    }

    private void drawStatusBar(Canvas canvas, ClusterState state, long frameAtMs) {
        drawWheelSpeedCard(canvas, state);
        drawTelemetryCard(canvas, new RectF(286.0f, 14.0f, 394.0f, 74.0f),
                "TRQ", formatTorque(state.engineFlywheelTorque));
        drawCurrentGearCard(canvas, state);

        RectF clock = new RectF(404.0f, 14.0f, 568.0f, 74.0f);
        RectF right = new RectF(1038.0f, 14.0f, 1186.0f, 74.0f);
        RectF transmissionTemperature =
                new RectF(1330.0f, 14.0f, 1478.0f, 74.0f);
        TransmissionTemperatureAlert.Level transmissionTemperatureLevel =
                updateTransmissionTemperatureAlert(state, frameAtMs);
        drawCard(canvas, clock, 22.0f);
        drawCard(canvas, right, 22.0f);
        drawCard(
                canvas,
                transmissionTemperature,
                22.0f,
                transmissionTemperatureColor(
                        transmissionTemperatureLevel,
                        COLOR_BORDER));

        configureText(32.0f, Paint.Align.CENTER, COLOR_TEXT, true);
        canvas.drawText(timeFormat.format(new Date()), clock.centerX(), 55.0f, textPaint);
        canvas.drawText(
                formatOutsideTemperature(state.outsideTemperatureC),
                right.centerX(),
                55.0f,
                textPaint);
        configureText(10.0f, Paint.Align.CENTER, COLOR_MUTED, true);
        canvas.drawText(
                "ATF",
                transmissionTemperature.centerX(),
                32.0f,
                textPaint);
        configureText(
                24.0f,
                Paint.Align.CENTER,
                transmissionTemperatureColor(
                        transmissionTemperatureLevel,
                        COLOR_TEXT),
                true);
        canvas.drawText(
                formatTransmissionTemperature(state, frameAtMs),
                transmissionTemperature.centerX(),
                60.0f,
                textPaint);

        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(2.0f);
        shapePaint.setColor(0x5531D7C5);
        canvas.drawLine(STATUS_GEAR_GAP_LEFT, 72.0f, STATUS_GEAR_GAP_RIGHT, 72.0f, shapePaint);
    }

    private void drawCurrentGearCard(Canvas canvas, ClusterState state) {
        RectF bounds = new RectF(916.0f, 78.0f, 1004.0f, 140.0f);
        drawCard(canvas, bounds, 18.0f);

        configureText(10.0f, Paint.Align.CENTER, COLOR_MUTED, true);
        canvas.drawText("GEAR", bounds.centerX(), 96.0f, textPaint);
        configureText(29.0f, Paint.Align.CENTER, COLOR_TEXT, true);
        canvas.drawText(
                state.currentGear > 0 ? Integer.toString(state.currentGear) : "",
                bounds.centerX(),
                130.0f,
                textPaint);
    }

    private void drawOdometerCard(Canvas canvas, ClusterState state) {
        RectF card = new RectF(74.0f, 548.0f, 586.0f, 688.0f);
        drawCard(canvas, card, 26.0f);

        configureText(13.0f, Paint.Align.LEFT, COLOR_MUTED, true);
        canvas.drawText("ODOMETER", 104.0f, 581.0f, textPaint);
        canvas.drawText("TODAY", 104.0f, 633.0f, textPaint);
        canvas.drawText("TRIP", 354.0f, 633.0f, textPaint);

        configureText(27.0f, Paint.Align.RIGHT, COLOR_TEXT, true);
        canvas.drawText(Math.round(state.odometerKm) + " km", 554.0f, 585.0f, textPaint);
        canvas.drawText(String.format(Locale.US, "%.1f km", state.dayKm), 310.0f, 665.0f, textPaint);
        canvas.drawText(formatTrip(state.tripKm) + " km", 554.0f, 665.0f, textPaint);

        configureText(13.0f, Paint.Align.LEFT, COLOR_MUTED, true);
        canvas.drawText("AVG", 104.0f, 613.0f, textPaint);
        configureText(21.0f, Paint.Align.LEFT, COLOR_ACCENT, true);
        canvas.drawText(
                String.format(Locale.US, "%.1f L/100", state.consumptionLitersPer100Km),
                150.0f,
                614.0f,
                textPaint);
    }

    private void drawTyreCard(Canvas canvas, ClusterState state) {
        RectF card = new RectF(1334.0f, 548.0f, 1846.0f, 688.0f);
        drawCard(canvas, card, 26.0f);

        configureText(13.0f, Paint.Align.LEFT, COLOR_MUTED, true);
        canvas.drawText("TYRE PRESSURE  bar", 1364.0f, 579.0f, textPaint);
        configureText(13.0f, Paint.Align.RIGHT, COLOR_MUTED, true);
        canvas.drawText("BATTERY", 1816.0f, 579.0f, textPaint);
        configureText(22.0f, Paint.Align.RIGHT, voltageColor(state.voltage), true);
        canvas.drawText(String.format(Locale.US, "%.1f V", state.voltage), 1816.0f, 610.0f, textPaint);

        drawVehicleOutline(canvas, 1590.0f, 630.0f);
        drawPressure(canvas, state.tyreFrontLeftBar, 1452.0f, 622.0f);
        drawPressure(canvas, state.tyreFrontRightBar, 1728.0f, 622.0f);
        drawPressure(canvas, state.tyreRearLeftBar, 1452.0f, 665.0f);
        drawPressure(canvas, state.tyreRearRightBar, 1728.0f, 665.0f);
    }

    private void drawVehicleOutline(Canvas canvas, float centerX, float centerY) {
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(2.0f);
        shapePaint.setColor(0xFF6F858B);
        RectF body = new RectF(centerX - 24.0f, centerY - 45.0f, centerX + 24.0f, centerY + 45.0f);
        canvas.drawRoundRect(body, 15.0f, 15.0f, shapePaint);
        RectF cabin = new RectF(centerX - 16.0f, centerY - 22.0f, centerX + 16.0f, centerY + 22.0f);
        canvas.drawRoundRect(cabin, 8.0f, 8.0f, shapePaint);

        shapePaint.setStrokeWidth(5.0f);
        canvas.drawLine(centerX - 30.0f, centerY - 26.0f, centerX - 30.0f, centerY - 9.0f, shapePaint);
        canvas.drawLine(centerX + 30.0f, centerY - 26.0f, centerX + 30.0f, centerY - 9.0f, shapePaint);
        canvas.drawLine(centerX - 30.0f, centerY + 9.0f, centerX - 30.0f, centerY + 26.0f, shapePaint);
        canvas.drawLine(centerX + 30.0f, centerY + 9.0f, centerX + 30.0f, centerY + 26.0f, shapePaint);
    }

    private void drawPressure(Canvas canvas, float pressure, float x, float y) {
        configureText(23.0f, Paint.Align.CENTER, pressureColor(pressure), true);
        canvas.drawText(formatPressure(pressure), x, y, textPaint);
    }

    private void drawCard(Canvas canvas, RectF bounds, float radius) {
        drawCard(canvas, bounds, radius, COLOR_BORDER);
    }

    private void drawCard(Canvas canvas, RectF bounds, float radius, int borderColor) {
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(COLOR_CARD);
        canvas.drawRoundRect(bounds, radius, radius, shapePaint);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(1.5f);
        shapePaint.setColor(borderColor);
        canvas.drawRoundRect(bounds, radius, radius, shapePaint);
    }

    private void drawTelemetryCard(
            Canvas canvas, RectF bounds, String label, String value) {
        drawCard(canvas, bounds, 20.0f);
        configureText(10.0f, Paint.Align.CENTER, COLOR_MUTED, true);
        canvas.drawText(label, bounds.centerX(), 32.0f, textPaint);
        configureText(20.0f, Paint.Align.CENTER, COLOR_TEXT, true);
        canvas.drawText(value, bounds.centerX(), 60.0f, textPaint);
    }

    private void drawWheelSpeedCard(Canvas canvas, ClusterState state) {
        RectF bounds = new RectF(12.0f, 14.0f, 276.0f, 74.0f);
        drawCard(canvas, bounds, 20.0f);

        configureText(9.0f, Paint.Align.CENTER, COLOR_MUTED, true);
        canvas.drawText("WHEEL SPEED  km/h", 144.0f, 26.0f, textPaint);

        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(1.0f);
        shapePaint.setColor(COLOR_BORDER);
        canvas.drawLine(144.0f, 30.0f, 144.0f, 69.0f, shapePaint);
        canvas.drawLine(22.0f, 53.0f, 266.0f, 53.0f, shapePaint);

        drawWheelValue(canvas, state, "FL", state.wheelFrontLeftKph,
                30.0f, 88.0f, 46.0f);
        drawWheelValue(canvas, state, "FR", state.wheelFrontRightKph,
                162.0f, 220.0f, 46.0f);
        drawWheelValue(canvas, state, "RL", state.wheelRearLeftKph,
                30.0f, 88.0f, 69.0f);
        drawWheelValue(canvas, state, "RR", state.wheelRearRightKph,
                162.0f, 220.0f, 69.0f);
    }

    private void drawWheelValue(
            Canvas canvas,
            ClusterState state,
            String label,
            float speedKph,
            float labelX,
            float valueX,
            float baseline) {
        configureText(9.0f, Paint.Align.LEFT, COLOR_MUTED, true);
        canvas.drawText(label, labelX, baseline, textPaint);
        configureText(17.0f, Paint.Align.CENTER, wheelSpeedColor(state, speedKph), true);
        canvas.drawText(formatWheelSpeed(speedKph), valueX, baseline, textPaint);
    }

    private void updateSmoothedValues(long now) {
        if (lastFrameAtMs == 0L) {
            lastFrameAtMs = now;
            displayedSpeed = targetState.speedKph;
            displayedFuel = targetState.fuelLiters;
            displayedCoolant = targetState.coolantC;
        }

        float elapsedMs = Math.min(100.0f, now - lastFrameAtMs);
        lastFrameAtMs = now;
        float amount = 1.0f - (float) Math.exp(-elapsedMs / 140.0f);
        displayedSpeed += (targetState.speedKph - displayedSpeed) * amount;
        displayedFuel += (targetState.fuelLiters - displayedFuel) * amount;
        displayedCoolant += (targetState.coolantC - displayedCoolant) * amount;
    }

    private boolean needsAnotherAnimationFrame(long nowMs) {
        return Math.abs(targetState.speedKph - displayedSpeed) > 0.05f
                || Math.abs(targetState.fuelLiters - displayedFuel) > 0.01f
                || Math.abs(targetState.coolantC - displayedCoolant) > 0.01f;
    }

    private void configureText(float size, Paint.Align align, int color, boolean mediumWeight) {
        textPaint.setTypeface(android.graphics.Typeface.create(
                mediumWeight ? "sans-serif-medium" : "sans-serif",
                android.graphics.Typeface.NORMAL));
        textPaint.setTextSize(size);
        textPaint.setTextAlign(align);
        textPaint.setColor(color);
    }

    private static int pressureColor(float pressure) {
        return pressure > 0.0f && (pressure < 1.8f || pressure > 3.2f)
                ? COLOR_WARNING
                : COLOR_TEXT;
    }

    private static int voltageColor(float voltage) {
        return voltage > 0.0f && voltage < 11.8f ? COLOR_WARNING : COLOR_ACCENT;
    }

    private static String formatPressure(float pressure) {
        return pressure <= 0.0f ? "\u2014" : String.format(Locale.US, "%.1f", pressure);
    }

    private static String formatTrip(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05f) {
            return Integer.toString(Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private static String formatOutsideTemperature(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05f) {
            return Math.round(value) + " \u00b0C";
        }
        return String.format(Locale.US, "%.1f \u00b0C", value);
    }

    private static String formatTransmissionTemperature(ClusterState state, long nowMs) {
        if (!state.hasTransmissionTemperature()
                || state.transmissionTemperatureUpdatedAtMs <= 0L
                || nowMs - state.transmissionTemperatureUpdatedAtMs
                        > TRANSMISSION_TEMPERATURE_STALE_AFTER_MS) {
            return "\u2014 \u00b0C";
        }
        return Math.round(state.transmissionTemperatureC) + " \u00b0C";
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
            return COLOR_TEXT;
        }

        float minimum = Math.min(Math.min(frontLeft, frontRight), Math.min(rearLeft, rearRight));
        float maximum = Math.max(Math.max(frontLeft, frontRight), Math.max(rearLeft, rearRight));
        if (maximum < 4.0f) {
            return COLOR_TEXT;
        }
        float median =
                (frontLeft + frontRight + rearLeft + rearRight - minimum - maximum) * 0.5f;
        float threshold = Math.max(4.0f, median * 0.18f);
        return Math.abs(wheelSpeed - median) >= threshold
                ? 0xFFFFC247
                : COLOR_TEXT;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
