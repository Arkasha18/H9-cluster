package net.adminrunet.h9cluster;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class FactoryNotificationRootViewTest {
    @Test
    public void cachedOrDismissedWarningKeepsTheSkinAboveTheFactoryTachometer() {
        FactoryNotificationRootView root = new FactoryNotificationRootView(RuntimeEnvironment.getApplication());
        View skin = new View(RuntimeEnvironment.getApplication());
        skin.setBackgroundColor(Color.BLUE);
        root.addView(skin, new FrameLayout.LayoutParams(-1, -1));
        root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1920, 720);
        FactoryNotificationMonitor monitor = new FactoryNotificationMonitor();
        monitor.seedWarningSnapshot("[287,2,1]");
        root.setFactoryNotificationVisible(monitor.isVisibleAt(1000));
        assertEquals(Color.BLUE, centre(root));
        monitor.updateWarning("[287,2,2]", 1100);
        root.setFactoryNotificationVisible(monitor.updateWarning("[287,2,1]", 1200));
        assertEquals(Color.TRANSPARENT, centre(root));
        root.setFactoryNotificationVisible(monitor.updateWarning("[287,2,2]", 1300));
        assertEquals(Color.BLUE, centre(root));
    }

    private static int centre(View root) {
        Bitmap frame = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(frame));
        int pixel = frame.getPixel(1600, 400);
        assertEquals(Color.BLUE, frame.getPixel(1300, 400));
        frame.recycle();
        return pixel;
    }
}
