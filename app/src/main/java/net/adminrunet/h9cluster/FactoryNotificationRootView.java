package net.adminrunet.h9cluster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.widget.FrameLayout;

/** Window root that can cut a transparent hole for the factory QNX alert. */
final class FactoryNotificationRootView extends FrameLayout {
    private static final float LOGICAL_WIDTH = 1920.0f;
    private static final float LOGICAL_HEIGHT = 720.0f;

    // Measured from the factory door-warning card attached to issue #37.
    private static final float ALERT_LEFT = 1320.0f;
    private static final float ALERT_TOP = 90.0f;
    private static final float ALERT_RIGHT = 1800.0f;
    private static final float ALERT_BOTTOM = 560.0f;

    private final Paint clearPaint = new Paint();
    private boolean factoryNotificationVisible;

    FactoryNotificationRootView(Context context) {
        super(context);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    void setFactoryNotificationVisible(boolean visible) {
        if (factoryNotificationVisible == visible) {
            return;
        }
        factoryNotificationVisible = visible;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!factoryNotificationVisible || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        float scaleX = getWidth() / LOGICAL_WIDTH;
        float scaleY = getHeight() / LOGICAL_HEIGHT;
        canvas.drawRect(
                ALERT_LEFT * scaleX,
                ALERT_TOP * scaleY,
                ALERT_RIGHT * scaleX,
                ALERT_BOTTOM * scaleY,
                clearPaint);
    }
}
