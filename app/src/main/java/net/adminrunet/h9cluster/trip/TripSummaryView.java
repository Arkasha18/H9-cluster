package net.adminrunet.h9cluster.trip;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Transparent cross-skin overlay for the final trip summary. */
public final class TripSummaryView extends View {
    private static final int COLOR_PANEL = 0xFF171C20;
    private static final int COLOR_PANEL_EDGE = 0xFF333B40;
    private static final int COLOR_TEXT = 0xFFF5F7F8;
    private static final int COLOR_MUTED = 0xFF929DA3;
    private static final int COLOR_DIVIDER = 0xFF3A4348;
    private static final long ANIMATION_DURATION_MS = 180L;
    private static final float START_OFFSET_PX = 24.0f;

    private final TripSummary summary;
    private final Paint paint =
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Typeface valueTypeface =
            Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private final Typeface labelTypeface =
            Typeface.create("sans-serif", Typeface.NORMAL);

    private ValueAnimator animator;
    private float animationProgress;
    private boolean animationStarted;

    public TripSummaryView(Context context, TripSummary summary) {
        super(context);
        this.summary = summary;
        setBackgroundColor(Color.TRANSPARENT);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!animationStarted) {
            animationStarted = true;
            animator = ValueAnimator.ofFloat(0.0f, 1.0f);
            animator.setDuration(ANIMATION_DURATION_MS);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    animationProgress = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            animator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        TripSummaryDrawPlan plan =
                TripSummaryDrawPlan.forSize(getWidth(), getHeight());
        float remaining = 1.0f - animationProgress;
        float leftOffset = -START_OFFSET_PX * plan.scaleX * remaining;
        float rightOffset = START_OFFSET_PX * plan.scaleX * remaining;

        drawPanel(canvas, plan.leftPanel, leftOffset, plan.scaleY);
        drawPanel(canvas, plan.rightPanel, rightOffset, plan.scaleY);
        drawLeftMetrics(canvas, plan, leftOffset);
        drawRightMetrics(canvas, plan, rightOffset);
    }

    private void drawPanel(
            Canvas canvas,
            TripSummaryDrawPlan.Box box,
            float offsetX,
            float scaleY) {
        float radius = 14.0f * scaleY;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_PANEL);
        canvas.drawRoundRect(
                box.left + offsetX,
                box.top,
                box.right + offsetX,
                box.bottom,
                radius,
                radius,
                paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.0f, scaleY));
        paint.setColor(COLOR_PANEL_EDGE);
        canvas.drawRoundRect(
                box.left + offsetX,
                box.top,
                box.right + offsetX,
                box.bottom,
                radius,
                radius,
                paint);
    }

    private void drawLeftMetrics(
            Canvas canvas,
            TripSummaryDrawPlan plan,
            float offsetX) {
        float right = plan.leftCritical.right + offsetX;
        drawLabel(canvas, "РАССТОЯНИЕ", right, 456.0f * plan.scaleY, plan, Paint.Align.RIGHT);
        String distance = TripSummaryFormatter.distance(summary);
        if (summary.distanceValid) {
            drawValue(canvas, distance, right - 58.0f * plan.scaleX, 502.0f * plan.scaleY, 42.0f, plan, Paint.Align.RIGHT);
            drawUnit(canvas, "км", right - 46.0f * plan.scaleX, 502.0f * plan.scaleY, plan);
        } else {
            drawValue(canvas, distance, right, 502.0f * plan.scaleY, 42.0f, plan, Paint.Align.RIGHT);
        }
        drawDivider(
                canvas,
                plan.leftCritical.left + offsetX,
                right,
                522.0f * plan.scaleY,
                plan);
        drawLabel(canvas, "ВРЕМЯ", right, 553.0f * plan.scaleY, plan, Paint.Align.RIGHT);
        drawValue(
                canvas,
                TripSummaryFormatter.duration(summary),
                right,
                603.0f * plan.scaleY,
                30.0f,
                plan,
                Paint.Align.RIGHT);
    }

    private void drawRightMetrics(
            Canvas canvas,
            TripSummaryDrawPlan plan,
            float offsetX) {
        float left = plan.rightCritical.left + offsetX;
        float right = plan.rightCritical.right + offsetX;
        drawLabel(
                canvas,
                "ПОЕЗДКА ЗАВЕРШЕНА",
                left,
                462.0f * plan.scaleY,
                plan,
                Paint.Align.LEFT);
        drawDivider(
                canvas,
                left,
                right,
                484.0f * plan.scaleY,
                plan);
        drawLabel(
                canvas,
                "СРЕДНИЙ РАСХОД",
                left,
                526.0f * plan.scaleY,
                plan,
                Paint.Align.LEFT);
        String consumption = TripSummaryFormatter.consumption(summary);
        if (summary.consumptionValid) {
            drawValue(
                    canvas,
                    consumption,
                    left + 166.0f * plan.scaleX,
                    582.0f * plan.scaleY,
                    42.0f,
                    plan,
                    Paint.Align.RIGHT);
            drawUnit(
                    canvas,
                    "л/100 км",
                    left + 178.0f * plan.scaleX,
                    582.0f * plan.scaleY,
                    plan);
        } else {
            drawValue(
                    canvas,
                    consumption,
                    left,
                    582.0f * plan.scaleY,
                    42.0f,
                    plan,
                    Paint.Align.LEFT);
        }
    }

    private void drawLabel(
            Canvas canvas,
            String text,
            float x,
            float baseline,
            TripSummaryDrawPlan plan,
            Paint.Align align) {
        configureText(16.0f * plan.scaleY, COLOR_MUTED, labelTypeface, align);
        canvas.drawText(text, x, baseline, paint);
    }

    private void drawValue(
            Canvas canvas,
            String text,
            float x,
            float baseline,
            float referenceSize,
            TripSummaryDrawPlan plan,
            Paint.Align align) {
        configureText(
                referenceSize * plan.scaleY,
                COLOR_TEXT,
                valueTypeface,
                align);
        canvas.drawText(text, x, baseline, paint);
    }

    private void drawUnit(
            Canvas canvas,
            String text,
            float x,
            float baseline,
            TripSummaryDrawPlan plan) {
        configureText(
                15.0f * plan.scaleY,
                COLOR_MUTED,
                labelTypeface,
                Paint.Align.LEFT);
        canvas.drawText(text, x, baseline, paint);
    }

    private void drawDivider(
            Canvas canvas,
            float left,
            float right,
            float y,
            TripSummaryDrawPlan plan) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.0f, plan.scaleY));
        paint.setColor(COLOR_DIVIDER);
        canvas.drawLine(left, y, right, y, paint);
    }

    private void configureText(
            float size,
            int color,
            Typeface typeface,
            Paint.Align align) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setTypeface(typeface);
        paint.setTextAlign(align);
    }
}
