package net.adminrunet.h9cluster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.widget.FrameLayout;

/** Window root that can cut a transparent hole for the factory QNX alert. */
final class FactoryNotificationRootView extends FrameLayout {
    private static final float LOGICAL_WIDTH = 1920.0f;
    private static final float LOGICAL_HEIGHT = 720.0f;

    // Calibrated on the vehicle with H9 Frame Calibrator 2.5.
    private static final float[][] ALERT_POINTS = {
            {1414.49f, 217.56f},
            {1790.55f, 217.56f},
            {1795.28f, 593.07f},
            {1414.49f, 588.35f}
    };

    private final Paint clearPaint = new Paint();
    private final Path clearPath = new Path();
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
        clearPath.rewind();
        for (int index = 0; index < ALERT_POINTS.length; index++) {
            float x = ALERT_POINTS[index][0] * scaleX;
            float y = ALERT_POINTS[index][1] * scaleY;
            if (index == 0) {
                clearPath.moveTo(x, y);
            } else {
                clearPath.lineTo(x, y);
            }
        }
        clearPath.close();
        canvas.drawPath(clearPath, clearPaint);
    }
}
