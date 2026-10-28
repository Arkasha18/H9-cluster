package net.adminrunet.h9cluster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.widget.FrameLayout;

/** Factory-layer simulation and mockup label, entirely outside the production skin. */
final class ApexSportDemoSystemView extends FrameLayout implements ClusterRenderer {
    private static final String MOCKUP_LABEL =
            "МАКЕТ • ВСЕ ЗНАЧЕНИЯ ДЕМОНСТРАЦИОННЫЕ";
    private final TitaniumDemoSystemView factoryLayer;
    private final Paint labelPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Rect labelBounds = new Rect();

    ApexSportDemoSystemView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // This existing Demo-only component contains just factory lamps and
        // captions, not Titanium artwork. Reuse the same calibrated lamp test.
        factoryLayer = new TitaniumDemoSystemView(context);
        addView(factoryLayer, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        labelPaint.setTypeface(Typeface.createFromAsset(context.getAssets(),
                "dashboard/skins/apexsport/NotoSans.ttf"));
        labelPaint.setColor(0xFF9BA8B3);
        labelPaint.setTextSize(12);
        labelPaint.setTextAlign(Paint.Align.LEFT);
        labelPaint.getTextBounds(MOCKUP_LABEL, 0, MOCKUP_LABEL.length(), labelBounds);
    }

    @Override
    public void setClusterState(ClusterState snapshot) {
        if (snapshot == null) return;
        factoryLayer.setClusterState(snapshot);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        float scale = Math.min(getWidth() / 1920.0f, getHeight() / 720.0f);
        int save = canvas.save();
        canvas.translate((getWidth() - 1920 * scale) / 2,
                (getHeight() - 720 * scale) / 2);
        canvas.scale(scale, scale);
        float origin = 1677 - (labelBounds.left + labelBounds.right) / 2.0f;
        canvas.drawText(MOCKUP_LABEL, origin, 42, labelPaint);
        canvas.restoreToCount(save);
    }
}
