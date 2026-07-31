package net.adminrunet.h9cluster.skins.simple;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Draws the Simple skin's static layer straight from {@link SimpleLayout}.
 *
 * <p>Every coordinate is logical (1920x720); the caller supplies the
 * transform. The blur filters need a software canvas, so callers are
 * expected to render this into an offscreen bitmap once rather than on
 * every frame.
 */
final class SimpleBackground {
    private SimpleBackground() {
    }

    static void draw(
            Canvas canvas,
            boolean demoMode,
            SimpleScaleColor scaleColor) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        drawTachBackdrop(canvas, paint, demoMode);
        drawGauge(canvas, paint, false, scaleColor);
        drawGauge(canvas, paint, true, scaleColor);
    }

    /**
     * Opaque annular sector under the tachometer scale, hiding the factory
     * content behind the band while leaving the gauge centre transparent.
     */
    private static void drawTachBackdrop(
            Canvas canvas,
            Paint paint,
            boolean demoMode) {
        float start = SimpleLayout.tachBackdropStartLength();
        float end = SimpleLayout.tachBackdropEndLength();
        Path sector = new Path();
        appendSweep(
                sector,
                SimpleLayout.TACH_BACKDROP_RADIUS,
                start,
                end,
                true);
        appendSweep(
                sector,
                SimpleLayout.TACH_BACKDROP_INNER_RADIUS,
                end,
                start,
                true);
        sector.close();

        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(SimpleLayout.tachBackdropColor(demoMode));
        paint.setMaskFilter(new BlurMaskFilter(
                SimpleLayout.TACH_BACKDROP_EDGE_BLUR_RADIUS,
                BlurMaskFilter.Blur.NORMAL));
        canvas.drawPath(sector, paint);
    }

    /**
     * Walks the gauge outline between two distances along it, sampling
     * it because the insert makes it something Canvas cannot draw as an
     * arc. Starts a contour on an empty path and continues one
     * otherwise, so a sector is two calls and a stroked arc is one.
     */
    private static void appendSweep(
            Path path,
            float radius,
            float startLength,
            float endLength,
            boolean rightGauge) {
        int segments = SimpleLayout.pathSegments(startLength, endLength);
        for (int index = 0; index <= segments; index++) {
            float along = startLength
                    + (endLength - startLength) * index / segments;
            float x = SimpleLayout.pointXAt(along, radius, rightGauge);
            float y = SimpleLayout.pointYAt(along, radius, rightGauge);
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
            boolean rightGauge,
            SimpleScaleColor scaleColor) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.BUTT);

        paint.setColor(scaleColor.glow());
        paint.setStrokeWidth(SimpleLayout.ACCENT_GLOW_WIDTH);
        paint.setMaskFilter(new BlurMaskFilter(
                SimpleLayout.ACCENT_GLOW_BLUR_RADIUS,
                BlurMaskFilter.Blur.NORMAL));
        drawFullArc(
                canvas,
                paint,
                SimpleLayout.ACCENT_ARC_RADIUS,
                rightGauge);
        paint.setMaskFilter(null);

        paint.setColor(SimpleLayout.SCALE_ARC_COLOR);
        paint.setStrokeWidth(SimpleLayout.SCALE_ARC_WIDTH);
        drawFullArc(
                canvas,
                paint,
                SimpleLayout.SCALE_ARC_RADIUS,
                rightGauge);

        drawTicks(canvas, paint, rightGauge);

        paint.setColor(scaleColor.accent);
        paint.setStrokeWidth(SimpleLayout.ACCENT_ARC_WIDTH);
        drawFullArc(
                canvas,
                paint,
                SimpleLayout.ACCENT_ARC_RADIUS,
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
                0.0f,
                SimpleLayout.SCALE_TOTAL_LENGTH,
                rightGauge);
        canvas.drawPath(arc, paint);
    }

    private static void drawTicks(
            Canvas canvas,
            Paint paint,
            boolean rightGauge) {
        int minorPerMajor = SimpleLayout.minorTicksPerMajor(rightGauge);
        int divisions = SimpleLayout.majorTickIntervals(rightGauge)
                * minorPerMajor;
        paint.setColor(SimpleLayout.TICK_COLOR);
        for (int index = 0; index <= divisions; index++) {
            boolean major = index % minorPerMajor == 0;
            float fraction = (float) index / divisions;
            float innerRadius = major
                    ? SimpleLayout.TICK_MAJOR_INNER_RADIUS
                    : SimpleLayout.TICK_MINOR_INNER_RADIUS;
            paint.setStrokeWidth(major
                    ? SimpleLayout.TICK_MAJOR_WIDTH
                    : SimpleLayout.TICK_MINOR_WIDTH);
            canvas.drawLine(
                    SimpleLayout.radialX(
                            fraction,
                            SimpleLayout.TICK_OUTER_RADIUS,
                            rightGauge),
                    SimpleLayout.radialY(
                            fraction,
                            SimpleLayout.TICK_OUTER_RADIUS,
                            rightGauge),
                    SimpleLayout.radialX(
                            fraction,
                            innerRadius,
                            rightGauge),
                    SimpleLayout.radialY(
                            fraction,
                            innerRadius,
                            rightGauge),
                    paint);
        }
    }
}
