package net.adminrunet.h9cluster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.widget.FrameLayout;

/** Demo-only simulation of the factory layer ABOVE the titanium artwork. */
final class TitaniumDemoSystemView extends FrameLayout implements ClusterRenderer {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private ClusterState state = ClusterState.empty();

    TitaniumDemoSystemView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(new DemoSystemIconsView(context), new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        text.setTypeface(bold);
        text.setColor(0xFFF0EEE7);
        text.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void setGearNumberVisible(boolean visible) {
        // This layer imitates the factory panel, not the skin's added numeral.
    }

    @Override
    public void setClusterState(ClusterState snapshot) {
        if (snapshot == null) return;
        state = snapshot;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        float scale = Math.min(getWidth() / 1920.0f, getHeight() / 720.0f);
        int save = canvas.save();
        canvas.translate((getWidth() - 1920 * scale) / 2, (getHeight() - 720 * scale) / 2);
        canvas.scale(scale, scale);
        String selector = state.gearSelector;
        boolean drive = GearSelector.DRIVE.equals(selector);
        boolean manual = GearSelector.MANUAL.equals(selector);
        if (drive || manual || GearSelector.PARK.equals(selector)
                || GearSelector.REVERSE.equals(selector) || GearSelector.NEUTRAL.equals(selector)) {
            String caption = manual ? GearSelector.label(selector, state.currentGear) : selector;
            float center = drive && !GearSelector.ratio(state.currentGear).isEmpty() ? 970 : 984;
            text.setTextSize(44);
            canvas.drawText(caption, center, 63, text);
        }
        text.setTextSize(20);
        float measured = text.measureText(state.driveMode);
        if (measured > 90) text.setTextSize(20 * 90 / measured);
        canvas.drawText(state.driveMode, 1477, 691, text);
        canvas.restoreToCount(save);
    }
}
