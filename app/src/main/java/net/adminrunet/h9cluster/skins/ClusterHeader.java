package net.adminrunet.h9cluster.skins;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/** Compact clock and system-adjacent Wi-Fi header in 1920x720 coordinates. */
public final class ClusterHeader {
    public enum Style {
        ION_AURORA,
        FACTORY
    }

    // Mask03's complete alpha bounds are 921..1047, 18..78 inclusive.
    // The matched outlines leave one clear pixel even at the antialiased edge.
    // The factory reservation is never filled, clipped or used as a runtime mask.
    private final float cornerRadius;
    private final RectF gearOutline;
    private final RectF clockBounds;
    private final RectF dateBounds = new RectF(81.5f, 15.5f, 300.5f, 78.5f);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint wifiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics metrics = new Paint.FontMetrics();
    private final Shader clockFill;
    private final Shader dateFill;
    private final WifiIndicator wifiIndicator;
    private final DashboardClock clock = new DashboardClock();
    private final boolean showDate;

    public ClusterHeader(Context context, Style style, boolean showDate) {
        this.showDate = showDate;
        boolean factoryCards = style == Style.FACTORY;
        float left = factoryCards ? 886.0f : 885.5f;
        float right = factoryCards ? 1058.0f : 1052.5f;
        float top = factoryCards ? 14.0f : 14.5f;
        float height = factoryCards ? 70.0f : 68.0f;
        float clockTop = factoryCards ? 87.0f : 84.5f;
        gearOutline = new RectF(left, top, right, top + height);
        clockBounds = new RectF(left, clockTop, right, clockTop + height);
        cornerRadius = factoryCards ? 19.0f : 8.0f;
        clockFill = factoryCards ? null : panelGradient(clockBounds);
        dateFill = factoryCards ? null : panelGradient(dateBounds);
        wifiIndicator = new WifiIndicator(context);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(factoryCards ? 2.0f : 1.0f);
        framePaint.setColor(factoryCards ? 0xFF4C535A : 0xFF36DCEB);
        fillPaint.setColor(0xFF080B0E);
        textPaint.setTypeface(Typeface.createFromAsset(
                context.getAssets(), "fonts/Rajdhani-Medium.ttf"));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFFAFDFF);
    }

    public void draw(Canvas canvas, long elapsedMs, long wallTimeMillis) {
        clock.update(wallTimeMillis);
        // Only an outline: the system's selector/manual caption remains untouched.
        canvas.drawRoundRect(gearOutline, cornerRadius, cornerRadius, framePaint);
        // A compact glyph leaves a visibly separate inner gutter without moving
        // neighboring cards or entering the factory selector reservation.
        int wifiSave = canvas.save();
        canvas.translate(907.0f, 48.0f);
        canvas.scale(0.75f, 0.75f);
        wifiIndicator.draw(canvas, wifiPaint, 0.0f, 0.0f, elapsedMs);
        canvas.restoreToCount(wifiSave);
        drawPanel(canvas, clockBounds, clockFill, clock.getTimeText(), 44.0f);
        if (showDate) {
            drawPanel(canvas, dateBounds, dateFill, clock.getDateText(), 38.0f);
        }
    }

    private void drawPanel(Canvas canvas, RectF bounds, Shader shader,
            String value, float textSize) {
        fillPaint.setShader(shader);
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, fillPaint);
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, framePaint);
        textPaint.setTextSize(textSize);
        textPaint.getFontMetrics(metrics);
        float baseline = bounds.centerY() - (metrics.ascent + metrics.descent) * 0.5f;
        canvas.drawText(value, bounds.centerX(), baseline, textPaint);
    }

    private static Shader panelGradient(RectF bounds) {
        return new LinearGradient(0, bounds.top, 0, bounds.bottom,
                0xC6111E29, 0x9905070C, Shader.TileMode.CLAMP);
    }
}
