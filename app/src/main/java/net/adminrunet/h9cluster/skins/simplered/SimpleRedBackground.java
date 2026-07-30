package net.adminrunet.h9cluster.skins.simplered;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Draws the Simple Red static layer straight from {@link SimpleRedLayout}.
 *
 * <p>Every coordinate is logical (1920x720); the caller supplies the
 * transform. The blur filters need a software canvas, so callers are
 * expected to render this into an offscreen bitmap once rather than on
 * every frame.
 */
final class SimpleRedBackground {
    private SimpleRedBackground() {
    }

    static void draw(Canvas canvas, boolean demoMode) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        drawTachBackdrop(canvas, paint, demoMode);
        drawGauge(canvas, paint, false);
        drawGauge(canvas, paint, true);
    }

    /**
     * Opaque annular sector under the tachometer scale, hiding the factory
     * content behind the band while leaving the gauge centre transparent.
     */
    private static void drawTachBackdrop(
            Canvas canvas,
            Paint paint,
            boolean demoMode) {
        float start = SimpleRedLayout.tachBackdropStartDegrees();
        float sweep = SimpleRedLayout.tachBackdropSweepDegrees();
        Path sector = new Path();
        sector.arcTo(
                gaugeBounds(SimpleRedLayout.TACH_BACKDROP_RADIUS),
                start,
                sweep,
                true);
        sector.arcTo(
                gaugeBounds(SimpleRedLayout.TACH_BACKDROP_INNER_RADIUS),
                start + sweep,
                -sweep,
                false);
        sector.close();

        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(SimpleRedLayout.tachBackdropColor(demoMode));
        paint.setMaskFilter(new BlurMaskFilter(
                SimpleRedLayout.TACH_BACKDROP_EDGE_BLUR_RADIUS,
                BlurMaskFilter.Blur.NORMAL));
        canvas.drawPath(sector, paint);
    }

    private static RectF gaugeBounds(float radius) {
        float centerX = SimpleRedLayout.gaugeCenterX(true);
        float centerY = SimpleRedLayout.GAUGE_CENTER_Y;
        return new RectF(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius);
    }

    private static void drawGauge(
            Canvas canvas,
            Paint paint,
            boolean rightGauge) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.BUTT);

        paint.setColor(SimpleRedLayout.REDLINE_GLOW_COLOR);
        paint.setStrokeWidth(SimpleRedLayout.REDLINE_GLOW_WIDTH);
        paint.setMaskFilter(new BlurMaskFilter(
                SimpleRedLayout.REDLINE_GLOW_BLUR_RADIUS,
                BlurMaskFilter.Blur.NORMAL));
        drawFullArc(
                canvas,
                paint,
                SimpleRedLayout.REDLINE_ARC_RADIUS,
                rightGauge);
        paint.setMaskFilter(null);

        paint.setColor(SimpleRedLayout.SCALE_ARC_COLOR);
        paint.setStrokeWidth(SimpleRedLayout.SCALE_ARC_WIDTH);
        drawFullArc(
                canvas,
                paint,
                SimpleRedLayout.SCALE_ARC_RADIUS,
                rightGauge);

        drawTicks(canvas, paint, rightGauge);

        paint.setColor(SimpleRedLayout.REDLINE_ARC_COLOR);
        paint.setStrokeWidth(SimpleRedLayout.REDLINE_ARC_WIDTH);
        drawFullArc(
                canvas,
                paint,
                SimpleRedLayout.REDLINE_ARC_RADIUS,
                rightGauge);
    }

    private static void drawFullArc(
            Canvas canvas,
            Paint paint,
            float radius,
            boolean rightGauge) {
        float centerX = SimpleRedLayout.gaugeCenterX(rightGauge);
        float centerY = SimpleRedLayout.GAUGE_CENTER_Y;
        canvas.drawArc(
                new RectF(
                        centerX - radius,
                        centerY - radius,
                        centerX + radius,
                        centerY + radius),
                SimpleRedLayout.SCALE_START_ANGLE_DEGREES,
                SimpleRedLayout.SCALE_SWEEP_DEGREES,
                false,
                paint);
    }

    private static void drawTicks(
            Canvas canvas,
            Paint paint,
            boolean rightGauge) {
        int minorPerMajor = SimpleRedLayout.minorTicksPerMajor(rightGauge);
        int divisions = SimpleRedLayout.majorTickIntervals(rightGauge)
                * minorPerMajor;
        paint.setColor(SimpleRedLayout.TICK_COLOR);
        for (int index = 0; index <= divisions; index++) {
            boolean major = index % minorPerMajor == 0;
            float fraction = (float) index / divisions;
            float innerRadius = major
                    ? SimpleRedLayout.TICK_MAJOR_INNER_RADIUS
                    : SimpleRedLayout.TICK_MINOR_INNER_RADIUS;
            paint.setStrokeWidth(major
                    ? SimpleRedLayout.TICK_MAJOR_WIDTH
                    : SimpleRedLayout.TICK_MINOR_WIDTH);
            canvas.drawLine(
                    SimpleRedLayout.radialX(
                            fraction,
                            SimpleRedLayout.TICK_OUTER_RADIUS,
                            rightGauge),
                    SimpleRedLayout.radialY(
                            fraction,
                            SimpleRedLayout.TICK_OUTER_RADIUS),
                    SimpleRedLayout.radialX(
                            fraction,
                            innerRadius,
                            rightGauge),
                    SimpleRedLayout.radialY(fraction, innerRadius),
                    paint);
        }
    }
}
