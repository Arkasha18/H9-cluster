package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class SupportProjectQrTest {
    @Test
    public void matrixHasRequiredQuietZoneAndExpectedPaymentUrl() {
        assertEquals(
                "https://qr.nspk.ru/BS2A000TQ6AEVP538UPOR733TS5CFV8D",
                SupportProjectQr.PAYMENT_URL);
        assertEquals(37, SupportProjectQr.MODULE_COUNT);

        for (int offset = 0; offset < 4; offset++) {
            for (int index = 0; index < SupportProjectQr.MODULE_COUNT; index++) {
                assertFalse(SupportProjectQr.isDarkModule(offset, index));
                assertFalse(SupportProjectQr.isDarkModule(
                        SupportProjectQr.MODULE_COUNT - 1 - offset,
                        index));
                assertFalse(SupportProjectQr.isDarkModule(index, offset));
                assertFalse(SupportProjectQr.isDarkModule(
                        index,
                        SupportProjectQr.MODULE_COUNT - 1 - offset));
            }
        }
        assertTrue(SupportProjectQr.isDarkModule(4, 4));
    }

    @Test
    public void settingsScreenRendersQrForExternalDecodeCheck() throws Exception {
        ActivityController<SettingsActivity> controller =
                Robolectric.buildActivity(SettingsActivity.class).setup();
        try {
            SettingsView view = settingsView(controller.get());
            view.measure(
                    View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, 960, 540);
            Bitmap frame = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888);
            try {
                view.draw(new Canvas(frame));
                File directory = new File("build/reports/support-project-qr");
                assertTrue(directory.isDirectory() || directory.mkdirs());
                try (FileOutputStream output = new FileOutputStream(
                        new File(directory, "settings-support-project-qr.png"))) {
                    assertTrue(frame.compress(Bitmap.CompressFormat.PNG, 100, output));
                }
            } finally {
                frame.recycle();
            }
        } finally {
            controller.pause().stop().destroy();
        }
    }

    private static SettingsView settingsView(Activity activity) throws Exception {
        Field field = SettingsActivity.class.getDeclaredField("settingsView");
        field.setAccessible(true);
        return (SettingsView) field.get(activity);
    }
}
