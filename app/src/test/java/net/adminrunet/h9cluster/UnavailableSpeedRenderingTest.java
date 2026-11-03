package net.adminrunet.h9cluster;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.time.Duration;
import net.adminrunet.h9cluster.skins.ionaurora.IonAuroraClusterView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;

/** Both speed readouts must agree when the data source is unavailable. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class UnavailableSpeedRenderingTest {
    @Test public void unavailableSpeedMustNotAppearAsZeroInSmallDrum() throws Exception {
        IonAuroraClusterView view = new IonAuroraClusterView(RuntimeEnvironment.getApplication());
        view.setClusterState(VehicleSpeedTripIntegrationTest.state(
                800, -1, Math.max(1L, SystemClock.elapsedRealtime())));
        view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1920, 720);
        Bitmap bitmap = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        try {
            view.draw(new Canvas(bitmap));
            ShadowSystemClock.advanceBy(Duration.ofMillis(1_200));
            view.draw(new Canvas(bitmap));
            File preview = new File("build/reports/speed-unavailable/ion-aurora.png");
            assertTrue(preview.getParentFile().isDirectory() || preview.getParentFile().mkdirs());
            try (FileOutputStream stream = new FileOutputStream(preview)) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
            }
            assertEquals("—", field(view, "cachedSpeedText"));
            assertEquals("The small drum must agree with the main speed readout", "—",
                    field(field(view, "speedGauge"), "cachedFocusValue"));
            view.setClusterState(VehicleSpeedTripIntegrationTest.state(
                    800, 0, Math.max(1L, SystemClock.elapsedRealtime())));
            ShadowSystemClock.advanceBy(Duration.ofMillis(16));
            view.draw(new Canvas(bitmap));
            assertEquals("0", field(view, "cachedSpeedText"));
            assertEquals("0", field(field(view, "speedGauge"), "cachedFocusValue"));
        } finally {
            bitmap.recycle();
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field value = target.getClass().getDeclaredField(name);
        value.setAccessible(true);
        return value.get(target);
    }
}
