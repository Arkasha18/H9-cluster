package net.adminrunet.h9cluster.skins.simplered;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

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
        float start = SimpleRedLayout.tachBackdropStartAngle();
        float end = SimpleRedLayout.tachBackdropEndAngle();
        Path sector = new Path();
        appendSweep(
                sector,
                SimpleRedLayout.TACH_BACKDROP_RADIUS,
                start,
                end,
                true);
        appendSweep(
                sector,
                SimpleRedLayout.TACH_BACKDROP_INNER_RADIUS,
                end,
                start,
                true);
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

    /**
     * Walks the gauge outline between two angles, sampling it because the
     * stretch makes it something Canvas cannot draw as an arc. Starts a
     * contour on an empty path and continues one otherwise, so a sector
     * is two calls and a stroked arc is one.
     */
    private static void appendSweep(
            Path path,
            float radius,
            float startAngle,
            float endAngle,
            boolean rightGauge) {
        int segments = SimpleRedLayout.pathSegments(startAngle, endAngle);
        for (int index = 0; index <= segments; index++) {
            float angle = startAngle
                    + (endAngle - startAngle) * index / segments;
            float x = SimpleRedLayout.pointXAt(angle, radius, rightGauge);
            float y = SimpleRedLayout.pointYAt(angle, radius);
            if (index == 0 && path.isEmpty()) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
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
        Path arc = new Path();
        appendSweep(
                arc,
                radius,
                SimpleRedLayout.SCALE_START_ANGLE_RADIANS,
                SimpleRedLayout.SCALE_END_ANGLE_RADIANS,
                rightGauge);
        canvas.drawPath(arc, paint);
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
