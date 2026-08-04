package net.adminrunet.h9cluster.trip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
    public interface OnDismissListener {
        void onDismiss();
    }

    private static final int COLOR_PANEL = 0xFF171C20;
    private static final int COLOR_PANEL_EDGE = 0xFF333B40;
    private static final int COLOR_TEXT = 0xFFF5F7F8;
    private static final int COLOR_MUTED = 0xFF929DA3;
    private static final int COLOR_DIVIDER = 0xFF3A4348;
    private static final long ANIMATION_DURATION_MS = 180L;
    private static final float START_OFFSET_PX = 24.0f;
    private static final float TITLE_SIZE = 34.0f;
    private static final float LABEL_SIZE = 16.0f;
    private static final float VALUE_SIZE = 34.0f;
    private static final float UNIT_SIZE = 15.0f;
    private static final float METRIC_GAP = 11.0f;
    private static final float VALUE_UNIT_GAP = 8.0f;
    private static final float DURATION_GROUP_GAP = 12.0f;

    private final TripSummary summary;
    private final Paint paint =
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Typeface valueTypeface =
            Typeface.create("sans-serif", Typeface.BOLD);
    private final Typeface labelTypeface =
            Typeface.create("sans-serif", Typeface.NORMAL);
    private final TripSummaryDismissController dismissController;

    private ValueAnimator animator;
    private OnDismissListener onDismissListener;
    private float animationProgress;
    private boolean animationStarted;

    public TripSummaryView(Context context, TripSummary summary) {
        super(context);
        this.summary = summary;
        setBackgroundColor(Color.TRANSPARENT);
        dismissController = new TripSummaryDismissController(
                new TripSummaryDismissController.Host() {
                    @Override
                    public void schedule(Runnable task, long delayMs) {
                        postDelayed(task, delayMs);
                    }

                    @Override
                    public void cancel(Runnable task) {
                        removeCallbacks(task);
                    }

                    @Override
                    public void animateOut(Runnable completion) {
                        startAnimation(
                                animationProgress,
                                0.0f,
                                completion);
                    }

                    @Override
                    public void remove() {
                        if (onDismissListener != null) {
                            onDismissListener.onDismiss();
                        }
                    }
                });
    }

    public void setOnDismissListener(OnDismissListener listener) {
        onDismissListener = listener;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!animationStarted) {
            animationStarted = true;
            startAnimation(0.0f, 1.0f, null);
        }
        dismissController.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        dismissController.detach();
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
        int checkpoint = canvas.saveLayerAlpha(
                0.0f,
                0.0f,
                getWidth(),
                getHeight(),
                Math.round(255.0f * animationProgress));
        canvas.translate(
                0.0f,
                START_OFFSET_PX * plan.scaleY * remaining);
        drawPanel(canvas, plan.panel, plan.scaleY);
        drawSummary(canvas, plan);
        canvas.restoreToCount(checkpoint);
    }

    private void startAnimation(
            float from,
            float to,
            final Runnable completion) {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(ANIMATION_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                animationProgress = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
        if (completion != null) {
            animator.addListener(new AnimatorListenerAdapter() {
                private boolean cancelled;

                @Override
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!cancelled) {
                        completion.run();
                    }
                }
            });
        }
        animator.start();
    }

    private void drawPanel(
            Canvas canvas,
            TripSummaryDrawPlan.Box box,
            float scaleY) {
        float radius = 14.0f * scaleY;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_PANEL);
        canvas.drawRoundRect(
                box.left,
                box.top,
                box.right,
                box.bottom,
                radius,
                radius,
                paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.0f, scaleY));
        paint.setColor(COLOR_PANEL_EDGE);
        canvas.drawRoundRect(
                box.left,
                box.top,
                box.right,
                box.bottom,
                radius,
                radius,
                paint);
    }

    private void drawSummary(
            final Canvas canvas,
            final TripSummaryDrawPlan plan) {
        configureText(
                TITLE_SIZE * plan.scaleY,
                COLOR_TEXT,
                valueTypeface,
                Paint.Align.CENTER);
        canvas.drawText(
                "ПОЕЗДКА ЗАВЕРШЕНА",
                plan.title.centerX(),
                centeredBaseline(plan.title),
                paint);

        drawDivider(
                canvas,
                plan.panel.left,
                plan.panel.right,
                plan.metricRows[0].top,
                plan);
        for (int index = 1; index < plan.metricRows.length; index++) {
            drawDivider(
                    canvas,
                    plan.innerDividerLeft,
                    plan.innerDividerRight,
                    plan.metricRows[index].top,
                    plan);
        }

        drawMetric(
                canvas,
                plan.metricRows[0],
                "РАССТОЯНИЕ",
                new ValueDrawer() {
                    @Override
                    public void draw(
                            Canvas target,
                            float centerX,
                            float baseline,
                            TripSummaryDrawPlan drawPlan) {
                        drawValueWithUnit(
                                target,
                                TripSummaryFormatter.distance(summary),
                                "км",
                                summary.distanceValid,
                                centerX,
                                baseline,
                                drawPlan);
                    }
                },
                plan);
        drawMetric(
                canvas,
                plan.metricRows[1],
                "ВРЕМЯ",
                new ValueDrawer() {
                    @Override
                    public void draw(
                            Canvas target,
                            float centerX,
                            float baseline,
                            TripSummaryDrawPlan drawPlan) {
                        drawDuration(
                                target,
                                TripSummaryFormatter.durationParts(summary),
                                centerX,
                                baseline,
                                drawPlan);
                    }
                },
                plan);
        drawMetric(
                canvas,
                plan.metricRows[2],
                "СРЕДНИЙ РАСХОД",
                new ValueDrawer() {
                    @Override
                    public void draw(
                            Canvas target,
                            float centerX,
                            float baseline,
                            TripSummaryDrawPlan drawPlan) {
                        drawValueWithUnit(
                                target,
                                TripSummaryFormatter.consumption(summary),
                                "л/100 км",
                                summary.consumptionValid,
                                centerX,
                                baseline,
                                drawPlan);
                    }
                },
                plan);
        drawMetric(
                canvas,
                plan.metricRows[3],
                "ИЗРАСХОДОВАНО",
                new ValueDrawer() {
                    @Override
                    public void draw(
                            Canvas target,
                            float centerX,
                            float baseline,
                            TripSummaryDrawPlan drawPlan) {
                        drawValueWithUnit(
                                target,
                                TripSummaryFormatter.fuelUsed(summary),
                                "л",
                                summary.fuelUsedValid,
                                centerX,
                                baseline,
                                drawPlan);
                    }
                },
                plan);
    }

    private void drawMetric(
            Canvas canvas,
            TripSummaryDrawPlan.Box row,
            String label,
            ValueDrawer valueDrawer,
            TripSummaryDrawPlan plan) {
        float gap = METRIC_GAP * plan.scaleY;

        configureText(
                LABEL_SIZE * plan.scaleY,
                COLOR_MUTED,
                labelTypeface,
                Paint.Align.CENTER);
        Paint.FontMetrics labelMetrics = paint.getFontMetrics();
        float labelHeight = labelMetrics.descent - labelMetrics.ascent;

        configureText(
                VALUE_SIZE * plan.scaleY,
                COLOR_TEXT,
                valueTypeface,
                Paint.Align.CENTER);
        Paint.FontMetrics valueMetrics = paint.getFontMetrics();
        float valueHeight = valueMetrics.descent - valueMetrics.ascent;

        float top =
                (row.top + row.bottom - labelHeight - gap - valueHeight)
                        * 0.5f;
        float labelBaseline = top - labelMetrics.ascent;
        float valueBaseline =
                top + labelHeight + gap - valueMetrics.ascent;

        configureText(
                LABEL_SIZE * plan.scaleY,
                COLOR_MUTED,
                labelTypeface,
                Paint.Align.CENTER);
        canvas.drawText(label, row.centerX(), labelBaseline, paint);
        valueDrawer.draw(canvas, row.centerX(), valueBaseline, plan);
    }

    private void drawValueWithUnit(
            Canvas canvas,
            String value,
            String unit,
            boolean valid,
            float centerX,
            float baseline,
            TripSummaryDrawPlan plan) {
        if (!valid) {
            drawValue(canvas, value, centerX, baseline, plan, Paint.Align.CENTER);
            return;
        }

        float valueWidth =
                measureText(value, VALUE_SIZE, valueTypeface, plan);
        float unitWidth =
                measureText(unit, UNIT_SIZE, labelTypeface, plan);
        float gap = VALUE_UNIT_GAP * plan.scaleX;
        float cursor = centerX - (valueWidth + gap + unitWidth) * 0.5f;

        drawValue(canvas, value, cursor, baseline, plan, Paint.Align.LEFT);
        cursor += valueWidth + gap;
        drawUnit(canvas, unit, cursor, baseline, plan);
    }

    private void drawDuration(
            Canvas canvas,
            TripSummaryFormatter.DurationParts duration,
            float centerX,
            float baseline,
            TripSummaryDrawPlan plan) {
        if (!duration.valid) {
            drawValue(
                    canvas,
                    "—",
                    centerX,
                    baseline,
                    plan,
                    Paint.Align.CENTER);
            return;
        }

        String hours = Long.toString(duration.hours);
        String minutes = Long.toString(duration.minutes);
        float valueUnitGap = VALUE_UNIT_GAP * plan.scaleX;
        float groupGap = DURATION_GROUP_GAP * plan.scaleX;
        float hoursWidth = duration.hours > 0L
                ? measureText(hours, VALUE_SIZE, valueTypeface, plan)
                : 0.0f;
        float hourUnitWidth = duration.hours > 0L
                ? measureText("ч", UNIT_SIZE, labelTypeface, plan)
                : 0.0f;
        float minutesWidth =
                measureText(minutes, VALUE_SIZE, valueTypeface, plan);
        float minuteUnitWidth =
                measureText("мин", UNIT_SIZE, labelTypeface, plan);
        float totalWidth =
                minutesWidth + valueUnitGap + minuteUnitWidth;
        if (duration.hours > 0L) {
            totalWidth += hoursWidth
                    + valueUnitGap
                    + hourUnitWidth
                    + groupGap;
        }

        float cursor = centerX - totalWidth * 0.5f;
        if (duration.hours > 0L) {
            drawValue(
                    canvas,
                    hours,
                    cursor,
                    baseline,
                    plan,
                    Paint.Align.LEFT);
            cursor += hoursWidth + valueUnitGap;
            drawUnit(canvas, "ч", cursor, baseline, plan);
            cursor += hourUnitWidth + groupGap;
        }
        drawValue(
                canvas,
                minutes,
                cursor,
                baseline,
                plan,
                Paint.Align.LEFT);
        cursor += minutesWidth + valueUnitGap;
        drawUnit(canvas, "мин", cursor, baseline, plan);
    }

    private float centeredBaseline(TripSummaryDrawPlan.Box box) {
        Paint.FontMetrics metrics = paint.getFontMetrics();
        return (box.top + box.bottom - metrics.ascent - metrics.descent)
                * 0.5f;
    }

    private float measureText(
            String text,
            float referenceSize,
            Typeface typeface,
            TripSummaryDrawPlan plan) {
        configureText(
                referenceSize * plan.scaleY,
                COLOR_TEXT,
                typeface,
                Paint.Align.LEFT);
        return paint.measureText(text);
    }

    private void drawValue(
            Canvas canvas,
            String text,
            float x,
            float baseline,
            TripSummaryDrawPlan plan,
            Paint.Align align) {
        configureText(
                VALUE_SIZE * plan.scaleY,
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
                UNIT_SIZE * plan.scaleY,
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

    private interface ValueDrawer {
        void draw(
                Canvas canvas,
                float centerX,
                float baseline,
                TripSummaryDrawPlan plan);
    }
}
