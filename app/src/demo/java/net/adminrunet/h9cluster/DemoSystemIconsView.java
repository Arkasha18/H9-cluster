package net.adminrunet.h9cluster;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;

/**
 * Visual lamp-test simulation, not vehicle warnings. Photo-derived glyphs are
 * positioned inside the current template's system regions, independently of the skin.
 * This class and all of its bitmap assets exist in the Demo source set only.
 */
final class DemoSystemIconsView extends View implements ClusterRenderer {
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap lamps;

    DemoSystemIconsView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        lamps = createLamps(context);
    }

    @Override
    public void setClusterState(ClusterState state) {
        // Static lamp test only. Selector letters and drive mode are never imitated.
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth() / 1920.0f, getHeight() / 720.0f);
        int save = canvas.save();
        canvas.translate((getWidth() - 1920 * scale) / 2,
                (getHeight() - 720 * scale) / 2);
        canvas.scale(scale, scale);
        canvas.drawBitmap(lamps, 0, 0, bitmapPaint);
        canvas.restoreToCount(save);
    }

    private Bitmap createLamps(Context context) {
        Bitmap result = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
        result.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(result);
        Bitmap nativeIcons = load(context, "native.png");
        // Source rectangles isolate glyphs, not the retained RGB photo beneath zero alpha.
        icon(canvas, nativeIcons, 107, 314, 58, 34, 46, 344, 44, 32); // engine
        icon(canvas, nativeIcons, 123, 272, 50, 41, 48, 288, 36, 30); // ESC
        icon(canvas, nativeIcons, 156, 230, 45, 47, 92, 236, 32, 34); // ESC off
        icon(canvas, nativeIcons, 199, 205, 56, 44, 148, 193, 36, 36); // collision assist
        icon(canvas, nativeIcons, 244, 187, 55, 44, 206, 164, 36, 36); // airbag
        icon(canvas, nativeIcons, 293, 174, 60, 41, 270, 142, 40, 28); // ABS
        icon(canvas, nativeIcons, 396, 146, 50, 34, 338, 116, 42, 32); // amber brake
        icon(canvas, nativeIcons, 446, 134, 47, 39, 404, 96, 38, 30); // TPMS
        icon(canvas, nativeIcons, 493, 125, 45, 39, 466, 78, 36, 30); // lighting
        icon(canvas, nativeIcons, 539, 121, 50, 26, 526, 68, 40, 24); // position lamps
        icon(canvas, nativeIcons, 634, 102, 64, 51, 594, 64, 46, 36); // left turn
        icon(canvas, nativeIcons, 107, 392, 60, 36, 46, 425, 44, 30); // steering
        icon(canvas, nativeIcons, 108, 468, 58, 38, 46, 510, 44, 30); // brake
        icon(canvas, nativeIcons, 1219, 101, 43, 44, 1250, 54, 32, 34); // belt
        icon(canvas, nativeIcons, 1369, 116, 48, 35, 1450, 84, 40, 30); // low beam
        icon(canvas, nativeIcons, 1460, 133, 57, 38, 1515, 100, 44, 30); // red park
        icon(canvas, nativeIcons, 1559, 161, 52, 31, 1580, 118, 42, 26); // battery
        icon(canvas, nativeIcons, 1645, 183, 47, 43, 1640, 134, 32, 30); // blind spot
        icon(canvas, nativeIcons, 1702, 204, 49, 40, 1700, 156, 34, 30); // doors
        icon(canvas, nativeIcons, 1748, 241, 53, 23, 1750, 180, 44, 24); // oil
        nativeIcons.recycle();
        individual(canvas, context, "13_right_turn_signal.png", 36, 37, 61, 50,
                1310, 64, 44, 36);
        individual(canvas, context, "22_high_beam_blue.png", 22, 33, 51, 30,
                1380, 72, 40, 30);
        individual(canvas, context, "21_door_status_red.png", 38, 55, 47, 40,
                316, 524, 40, 40);
        individual(canvas, context, "24_lane_assist_green.png", 34, 52, 48, 39,
                444, 407, 44, 46);
        return result;
    }

    private void individual(Canvas canvas, Context context, String filename,
            int sx, int sy, int sw, int sh, float x, float y, float w, float h) {
        Bitmap bitmap = load(context, filename);
        icon(canvas, bitmap, sx, sy, sw, sh, x, y, w, h);
        bitmap.recycle();
    }

    private void icon(Canvas canvas, Bitmap bitmap, int sx, int sy, int sw, int sh,
            float x, float y, float maxWidth, float maxHeight) {
        float scale = Math.min(maxWidth / sw, maxHeight / sh);
        float halfWidth = sw * scale / 2;
        float halfHeight = sh * scale / 2;
        canvas.drawBitmap(bitmap, new Rect(sx, sy, sx + sw, sy + sh),
                new RectF(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight),
                bitmapPaint);
    }

    private static Bitmap load(Context context, String filename) {
        try (InputStream input = context.getAssets().open("demo_system_icons/" + filename)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) throw new IOException("Cannot decode " + filename);
            bitmap.setDensity(Bitmap.DENSITY_NONE);
            return bitmap;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load demo system icon " + filename, error);
        }
    }
}
