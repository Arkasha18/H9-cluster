package net.adminrunet.h9cluster.skins.ionaurora;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Locale;

/** A luminous cylindrical tape with value-driven turbine ribs and a fixed readout. */
final class RollingTapeGauge {
    static final float INDEX_Y = 365.0f;
    static final float PROJECTION_RADIUS = 190.0f;

    private static final int COLOR_WHITE = 0xFFFAFDFF;
    private static final int COLOR_CYAN = 0xFF42E9FF;
    private static final float FACE_HALF_WIDTH = 50.0f;
    private static final float READOUT_HALF_WIDTH = 49.0f;
    private static final float READOUT_HALF_HEIGHT = 24.0f;
    private static final float RIB_SPACING_PIXELS = 22.0f;

    private final float centerX;
    private final float top;
    private final float bottom;
    private final float minimum;
    private final float maximum;
    private final float pixelsPerUnit;
    private final int focusDecimals;
    private final int quantizer;
    private final float[] marks;
    private final boolean[] majorMarks;
    private final boolean[] halfMarks;
    private final String[] labels;
    private final Path facePath = new Path();
    private final Path leftTip = new Path();
    private final Path rightTip = new Path();
    private final Path movingRib = new Path();
    private final Path energyTrail = new Path();
    private final RectF readout;
    private final Bitmap bodyBitmap;
    private final float bodyLeft;
    private final float bodyTop;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG);
    private final Paint.FontMetrics textMetrics = new Paint.FontMetrics();
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indexPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capsulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint motionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint focusPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

    private int cachedQuantizedValue = Integer.MIN_VALUE;
    private String cachedFocusValue = "0";

    RollingTapeGauge(
            float centerX,
            float top,
            float bottom,
            float minimum,
            float maximum,
            float pixelsPerUnit,
            float minorStep,
            float halfStep,
            float majorStep,
            int focusDecimals,
            Typeface gaugeTypeface) {
        this.centerX = centerX;
        this.top = top;
        this.bottom = bottom;
        this.minimum = minimum;
        this.maximum = maximum;
        this.pixelsPerUnit = pixelsPerUnit;
        this.focusDecimals = Math.max(0, Math.min(2, focusDecimals));
        quantizer = this.focusDecimals == 0 ? 1
                : this.focusDecimals == 1 ? 10 : 100;

        int majorEvery = Math.max(1, Math.round(majorStep / minorStep));
        int halfEvery = Math.max(1, Math.round(halfStep / minorStep));
        int count = Math.round((maximum - minimum) / minorStep) + 1;
        marks = new float[count];
        majorMarks = new boolean[count];
        halfMarks = new boolean[count];
        labels = new String[count];
        for (int index = 0; index < count; index++) {
            float mark = minimum + index * minorStep;
            boolean major = index % majorEvery == 0;
            marks[index] = mark;
            majorMarks[index] = major;
            halfMarks[index] = !major && index % halfEvery == 0;
            labels[index] = major ? formatLabel(mark, true) : null;
        }

        traceCylinder(facePath, 39.0f, FACE_HALF_WIDTH);
        readout = new RectF(centerX - READOUT_HALF_WIDTH,
                INDEX_Y - READOUT_HALF_HEIGHT,
                centerX + READOUT_HALF_WIDTH,
                INDEX_Y + READOUT_HALF_HEIGHT);
        leftTip.moveTo(centerX - 75.0f, INDEX_Y - 7.0f);
        leftTip.lineTo(centerX - 54.0f, INDEX_Y);
        leftTip.lineTo(centerX - 75.0f, INDEX_Y + 7.0f);
        leftTip.close();
        rightTip.moveTo(centerX + 75.0f, INDEX_Y - 7.0f);
        rightTip.lineTo(centerX + 54.0f, INDEX_Y);
        rightTip.lineTo(centerX + 75.0f, INDEX_Y + 7.0f);
        rightTip.close();

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        indexPaint.setStrokeCap(Paint.Cap.ROUND);
        motionPaint.setStyle(Paint.Style.STROKE);
        motionPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTypeface(gaugeTypeface);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(COLOR_WHITE);
        focusPaint.setTypeface(gaugeTypeface);
        focusPaint.setTextAlign(Paint.Align.CENTER);
        focusPaint.setFakeBoldText(true);
        focusPaint.setTextSize(maximum > 999.0f ? 36.0f : 40.0f);

        // All lighting and glass geometry are rasterized once, not every animation frame.
        bodyLeft = centerX - 84.0f;
        bodyTop = top - 14.0f;
        bodyBitmap = Bitmap.createBitmap(168, Math.round(bottom - top + 28.0f),
                Bitmap.Config.ARGB_8888);
        Canvas bodyCanvas = new Canvas(bodyBitmap);
        bodyCanvas.translate(-bodyLeft, -bodyTop);
        drawStaticBody(bodyCanvas);
    }

    void draw(Canvas canvas, float currentValue, float velocityPerSecond, float reveal) {
        draw(canvas, currentValue, velocityPerSecond, reveal, 0.0f);
    }

    void draw(Canvas canvas, float currentValue, float velocityPerSecond, float reveal,
            float phaseSeconds) {
        float safeValue = sanitizeValue(currentValue, minimum, maximum);
        float safeReveal = clamp(reveal, 0.0f, 1.0f);
        float safeVelocity = Float.isFinite(velocityPerSecond) ? velocityPerSecond : 0.0f;
        float safePhase = Float.isFinite(phaseSeconds) ? phaseSeconds : 0.0f;
        updateFocusValue(safeValue);
        if (safeReveal <= 0.0f) {
            return;
        }
        bitmapPaint.setAlpha(Math.round(255.0f * safeReveal));
        canvas.drawBitmap(bodyBitmap, bodyLeft, bodyTop, bitmapPaint);
        drawMovingStructure(canvas, safeValue, safeVelocity, safeReveal, safePhase);

        int save = canvas.save();
        canvas.clipPath(facePath);
        float motionPixelsPerSecond = Math.abs(safeVelocity * pixelsPerUnit);
        for (int index = 0; index < marks.length; index++) {
            float y = projectMarkY(marks[index], safeValue, pixelsPerUnit);
            if (y < top || y > bottom) {
                continue;
            }
            float distance = Math.abs(y - INDEX_Y);
            float depth = clamp(distance / PROJECTION_RADIUS, 0.0f, 1.0f);
            float focus = 1.0f - depth * depth;
            float alphaFactor = (float) Math.pow(focus, 2.3f);
            if (alphaFactor < 0.012f) {
                continue;
            }
            float compression = 0.58f + 0.42f * focus;
            boolean major = majorMarks[index];
            boolean half = halfMarks[index];
            float tickLength = (major ? 13.0f : half ? 8.0f : 4.0f) * compression;
            int alpha = Math.round(255.0f * alphaFactor * safeReveal);
            tickPaint.setStrokeWidth((major ? 2.2f : half ? 1.6f : 1.1f)
                    * (0.75f + 0.25f * focus));
            tickPaint.setColor(major ? COLOR_WHITE : COLOR_CYAN);
            float leftEdge = centerX - (39.0f + 10.0f * focus);
            float rightEdge = centerX + (39.0f + 10.0f * focus);
            float blurOffset = distance <= 50.0f ? 0.0f
                    : clamp((motionPixelsPerSecond - 80.0f) / 160.0f, 0.0f, 2.0f);
            if (blurOffset > 0.2f) {
                tickPaint.setAlpha(Math.max(1, Math.round(alpha * 0.12f)));
                drawTickPair(canvas, leftEdge, rightEdge, tickLength, y - blurOffset);
                drawTickPair(canvas, leftEdge, rightEdge, tickLength, y + blurOffset);
            }
            tickPaint.setAlpha(alpha);
            drawTickPair(canvas, leftEdge, rightEdge, tickLength, y);

            // The fixed readout obscures the central tape labels, as on a physical drum.
            if (major && distance >= READOUT_HALF_HEIGHT + 13.0f) {
                textPaint.setTextSize(maximum > 999.0f ? 31.0f : 36.0f);
                textPaint.setAlpha(Math.round(alpha * 0.96f));
                int labelSave = canvas.save();
                canvas.scale(0.88f + 0.12f * focus, compression, centerX, y);
                drawCenteredText(canvas, labels[index], centerX, y, textPaint);
                canvas.restoreToCount(labelSave);
            }
        }
        canvas.restoreToCount(save);
    }

    void drawFixedIndex(Canvas canvas, float glowMultiplier, float reveal) {
        float safeReveal = clamp(reveal, 0.0f, 1.0f);
        float safeGlow = clamp(glowMultiplier, 0.0f, 1.4f) * safeReveal;

        // The glowing line terminates outside the numeric capsule, including its bloom.
        indexPaint.setStyle(Paint.Style.STROKE);
        indexPaint.setColor(COLOR_CYAN);
        indexPaint.setAlpha(Math.min(255, Math.round(35.0f * safeGlow)));
        indexPaint.setStrokeWidth(9.0f);
        drawSplitIndex(canvas);
        indexPaint.setColor(0xFF7184FF);
        indexPaint.setAlpha(Math.min(255, Math.round(135.0f * safeGlow)));
        indexPaint.setStrokeWidth(4.0f);
        drawSplitIndex(canvas);
        indexPaint.setColor(COLOR_WHITE);
        indexPaint.setAlpha(Math.round(245.0f * safeReveal));
        indexPaint.setStrokeWidth(1.5f);
        drawSplitIndex(canvas);

        indexPaint.setStyle(Paint.Style.FILL);
        indexPaint.setColor(COLOR_CYAN);
        indexPaint.setAlpha(Math.round(245.0f * safeReveal));
        canvas.drawPath(leftTip, indexPaint);
        canvas.drawPath(rightTip, indexPaint);

        capsulePaint.setStyle(Paint.Style.FILL);
        capsulePaint.setColor(0xFF020C1C);
        capsulePaint.setAlpha(Math.round(252.0f * safeReveal));
        canvas.drawRoundRect(readout, 15.0f, 15.0f, capsulePaint);
        capsulePaint.setStyle(Paint.Style.STROKE);
        capsulePaint.setStrokeWidth(1.4f);
        capsulePaint.setColor(COLOR_CYAN);
        capsulePaint.setAlpha(Math.round(140.0f * safeReveal));
        canvas.drawRoundRect(readout, 15.0f, 15.0f, capsulePaint);

        // Render the glyphs last so no indicator can ever strike through their strokes.
        focusPaint.setStyle(Paint.Style.STROKE);
        focusPaint.setStrokeWidth(4.0f);
        focusPaint.setColor(COLOR_CYAN);
        focusPaint.setAlpha(Math.round(24.0f * safeReveal));
        drawCenteredText(canvas, cachedFocusValue, centerX, INDEX_Y, focusPaint);
        focusPaint.setStyle(Paint.Style.FILL);
        focusPaint.setColor(COLOR_WHITE);
        focusPaint.setAlpha(Math.round(255.0f * safeReveal));
        drawCenteredText(canvas, cachedFocusValue, centerX, INDEX_Y, focusPaint);
    }

    float positionFor(float mark, float currentValue) {
        return projectMarkY(mark, sanitizeValue(currentValue, minimum, maximum), pixelsPerUnit);
    }

    static float projectMarkY(float mark, float currentValue, float pixelsPerUnit) {
        return INDEX_Y - PROJECTION_RADIUS * (float) Math.tanh(
                (mark - currentValue) * pixelsPerUnit / PROJECTION_RADIUS);
    }

    static float sanitizeValue(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) {
            return minimum;
        }
        return clamp(value, minimum, maximum);
    }

    static String formatLabel(float value, boolean integerValue) {
        return Integer.toString(Math.round(value));
    }

    static String formatFocusValue(int quantized, int decimals) {
        if (decimals <= 0) {
            return Integer.toString(quantized);
        }
        if (decimals == 1) {
            return quantized % 10 == 0 ? Integer.toString(quantized / 10)
                    : String.format(Locale.US, "%.1f", quantized / 10.0f);
        }
        if (quantized % 100 == 0) {
            return Integer.toString(quantized / 100);
        }
        if (quantized % 10 == 0) {
            return String.format(Locale.US, "%.1f", quantized / 100.0f);
        }
        return String.format(Locale.US, "%.2f", quantized / 100.0f);
    }

    private void updateFocusValue(float value) {
        int quantized = Math.round(value * quantizer);
        if (quantized != cachedQuantizedValue) {
            cachedQuantizedValue = quantized;
            cachedFocusValue = formatFocusValue(quantized, focusDecimals);
        }
    }

    private void drawSplitIndex(Canvas canvas) {
        canvas.drawLine(centerX - 75.0f, INDEX_Y, centerX - 55.0f, INDEX_Y, indexPaint);
        canvas.drawLine(centerX + 55.0f, INDEX_Y, centerX + 75.0f, INDEX_Y, indexPaint);
    }

    static float projectRibY(int rib, float currentValue, float pixelsPerUnit) {
        return projectMarkY(rib * RIB_SPACING_PIXELS / pixelsPerUnit,
                currentValue, pixelsPerUnit);
    }

    private void drawMovingStructure(Canvas canvas, float value, float velocity,
            float reveal, float phaseSeconds) {
        float intensity = clamp(Math.abs(velocity * pixelsPerUnit) / 260.0f, 0.0f, 1.0f);
        int middleRib = Math.round(value * pixelsPerUnit / RIB_SPACING_PIXELS);
        // The turbine ribs are part of the same cylinder as the numbers: no free-running
        // rotation can suggest a speed or RPM change while the measured value is steady.
        for (int rib = middleRib - 30; rib <= middleRib + 30; rib++) {
            float y = projectRibY(rib, value, pixelsPerUnit);
            float depth = Math.abs(y - INDEX_Y) / PROJECTION_RADIUS;
            float focus = Math.max(0.0f, 1.0f - depth * depth);
            float opacity = focus * focus * reveal;
            if (opacity < 0.025f || y < top + 12.0f || y > bottom - 12.0f) {
                continue;
            }
            for (int side = -1; side <= 1; side += 2) {
                float innerX = centerX + side * (40.0f + 10.0f * focus);
                float outerX = centerX + side * (57.0f + 13.0f * focus);
                float rise = 11.0f * focus;
                movingRib.rewind();
                movingRib.moveTo(innerX, y);
                movingRib.cubicTo(innerX + side * 10.0f, y - rise * 0.12f,
                        outerX, y - rise * 0.48f, outerX, y - rise);
                motionPaint.setColor(0xFF4466E9);
                motionPaint.setStrokeWidth(6.2f * (0.65f + 0.35f * focus));
                motionPaint.setAlpha(Math.round((60.0f + 24.0f * intensity) * opacity));
                canvas.drawPath(movingRib, motionPaint);
                motionPaint.setColor((rib & 3) == 0 ? 0xFFAA7BFF : COLOR_CYAN);
                motionPaint.setStrokeWidth((rib & 3) == 0 ? 2.0f : 1.2f);
                motionPaint.setAlpha(Math.round((178.0f + 45.0f * intensity) * opacity));
                canvas.drawPath(movingRib, motionPaint);
                // The illuminated tips read as separate rotating vanes in the sidewall.
                motionPaint.setColor((rib & 3) == 0 ? 0xFFE0CCFF : 0xFFA9F7FF);
                motionPaint.setStrokeWidth(1.6f);
                motionPaint.setAlpha(Math.round(210.0f * opacity));
                canvas.drawLine(outerX, y - rise, outerX, y - rise + 3.5f * focus,
                        motionPaint);
            }
        }

        // Slow light flowing inside the fixed energy rails adds life at idle without
        // moving the scale. Trails stay outside the glyph column and fade at both ends.
        for (int packet = 0; packet < 3; packet++) {
            float cycle = phaseSeconds * 0.105f + packet / 3.0f;
            float progress = cycle - (float) Math.floor(cycle);
            float y = top + 24.0f + progress * (bottom - top - 48.0f);
            float fade = (float) Math.sin(Math.PI * progress);
            fade *= fade * reveal;
            for (int side = -1; side <= 1; side += 2) {
                energyTrail.rewind();
                for (int segment = 0; segment <= 6; segment++) {
                    float trailY = y - 13.0f + segment * 4.0f;
                    float depth = clamp(Math.abs(trailY - INDEX_Y)
                            / (bottom - top) * 2.0f, 0.0f, 1.0f);
                    float x = centerX + side * (65.0f - 9.0f * depth * depth);
                    if (segment == 0) {
                        energyTrail.moveTo(x, trailY);
                    } else {
                        energyTrail.lineTo(x, trailY);
                    }
                }
                motionPaint.setColor(COLOR_CYAN);
                motionPaint.setStrokeWidth(9.0f);
                motionPaint.setAlpha(Math.round(43.0f * fade));
                canvas.drawPath(energyTrail, motionPaint);
                motionPaint.setColor(0xFFB9FAFF);
                motionPaint.setStrokeWidth(2.3f);
                motionPaint.setAlpha(Math.round(225.0f * fade));
                canvas.drawPath(energyTrail, motionPaint);
            }
        }
    }

    private void traceCylinder(Path path, float endHalfWidth, float focusHalfWidth) {
        path.moveTo(centerX - endHalfWidth, top);
        path.cubicTo(centerX - focusHalfWidth, top + 74.0f,
                centerX - focusHalfWidth, INDEX_Y - 70.0f,
                centerX - focusHalfWidth, INDEX_Y);
        path.cubicTo(centerX - focusHalfWidth, INDEX_Y + 70.0f,
                centerX - focusHalfWidth, bottom - 74.0f,
                centerX - endHalfWidth, bottom);
        path.quadTo(centerX, bottom + 7.0f, centerX + endHalfWidth, bottom);
        path.cubicTo(centerX + focusHalfWidth, bottom - 74.0f,
                centerX + focusHalfWidth, INDEX_Y + 70.0f,
                centerX + focusHalfWidth, INDEX_Y);
        path.cubicTo(centerX + focusHalfWidth, INDEX_Y - 70.0f,
                centerX + focusHalfWidth, top + 74.0f,
                centerX + endHalfWidth, top);
        path.quadTo(centerX, top - 7.0f, centerX - endHalfWidth, top);
        path.close();
    }

    private void drawStaticBody(Canvas canvas) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        Path outer = new Path();
        traceCylinder(outer, 55.0f, 65.0f);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0x123067FF);
        paint.setStrokeWidth(16.0f);
        canvas.drawPath(outer, paint);
        paint.setColor(0x28277EFF);
        paint.setStrokeWidth(8.0f);
        canvas.drawPath(outer, paint);

        paint.setStyle(Paint.Style.FILL);
        // Shader alpha is multiplied by Paint alpha. Reset the translucent halo's
        // alpha here so the cylindrical metal and plasma retain their intended light.
        paint.setAlpha(255);
        paint.setShader(new LinearGradient(centerX - 65.0f, 0.0f,
                centerX + 65.0f, 0.0f,
                new int[] {0xB10E1036, 0xF018427C, 0xF9051730, 0xFC020915,
                        0xF9051730, 0xF018427C, 0xB10E1036},
                new float[] {0.0f, 0.055f, 0.16f, 0.5f, 0.84f, 0.945f, 1.0f},
                Shader.TileMode.CLAMP));
        canvas.drawPath(outer, paint);

        paint.setShader(new LinearGradient(centerX - FACE_HALF_WIDTH, 0.0f,
                centerX + FACE_HALF_WIDTH, 0.0f,
                new int[] {0xFC061327, 0xF90C2A4B, 0xFB081627, 0xF90C2A4B, 0xFC061327},
                new float[] {0.0f, 0.11f, 0.5f, 0.89f, 1.0f}, Shader.TileMode.CLAMP));
        canvas.drawPath(facePath, paint);

        // Symmetric polished rails use a continuous gradient, without angular brackets.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        paint.setShader(new LinearGradient(0.0f, top, 0.0f, bottom,
                new int[] {0x18365BAC, 0xCD6B7BFF, 0xEE42E9FF, 0xFFDCFBFF,
                        0xDC42D7FF, 0xB77B52FF, 0x14234470},
                new float[] {0.0f, 0.16f, 0.35f, 0.52f, 0.69f, 0.84f, 1.0f},
                Shader.TileMode.CLAMP));
        canvas.drawPath(outer, paint);
        paint.setStrokeWidth(1.0f);
        canvas.drawPath(facePath, paint);

        paint.setStyle(Paint.Style.FILL);
        for (int side = -1; side <= 1; side += 2) {
            float railX = centerX + side * 64.0f;
            paint.setShader(new RadialGradient(railX, INDEX_Y, 14.0f,
                    new int[] {0xBB47DFFF, 0x383469FC, Color.TRANSPARENT},
                    new float[] {0.0f, 0.35f, 1.0f}, Shader.TileMode.CLAMP));
            int save = canvas.save();
            canvas.scale(1.0f, 9.6f, railX, INDEX_Y);
            canvas.drawCircle(railX, INDEX_Y, 14.0f, paint);
            canvas.restoreToCount(save);
        }

        // The ends disappear into the background as the tape curls away from the viewer.
        int save = canvas.save();
        canvas.clipPath(outer);
        paint.setShader(new LinearGradient(0.0f, top, 0.0f, bottom,
                new int[] {0xEC010611, 0x28010611, 0x00010611, 0x28010611, 0xEC010611},
                new float[] {0.0f, 0.17f, 0.45f, 0.78f, 1.0f}, Shader.TileMode.CLAMP));
        canvas.drawPath(outer, paint);
        canvas.restoreToCount(save);
    }

    private void drawTickPair(Canvas canvas, float leftEdge, float rightEdge,
            float tickLength, float y) {
        canvas.drawLine(leftEdge, y, leftEdge + tickLength, y, tickPaint);
        canvas.drawLine(rightEdge - tickLength, y, rightEdge, y, tickPaint);
    }

    private void drawCenteredText(Canvas canvas, String value, float centerX,
            float centerY, Paint paint) {
        paint.getFontMetrics(textMetrics);
        float baseline = centerY - (textMetrics.ascent + textMetrics.descent) * 0.5f;
        canvas.drawText(value, centerX, baseline, paint);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
