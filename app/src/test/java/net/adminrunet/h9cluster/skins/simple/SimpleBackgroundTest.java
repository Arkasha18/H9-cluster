package net.adminrunet.h9cluster.skins.simple;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class SimpleBackgroundTest {
    @Test
    public void noScalesChoiceLeavesTheStaticLayerEmpty() {
        Bitmap frame = draw(SimpleScaleColor.NONE);
        assertEquals(0, opaquePixelCount(frame));
    }

    @Test
    public void colorChoiceStillDrawsTheStaticScales() {
        Bitmap frame = draw(SimpleScaleColor.RED);
        assertTrue(opaquePixelCount(frame) > 0);
    }

    private static Bitmap draw(SimpleScaleColor color) {
        Bitmap frame = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        SimpleBackground.draw(new Canvas(frame), false, color);
        return frame;
    }

    private static int opaquePixelCount(Bitmap frame) {
        int count = 0;
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                if (Color.alpha(frame.getPixel(x, y)) != 0) {
                    count++;
                }
            }
        }
        return count;
    }
}
