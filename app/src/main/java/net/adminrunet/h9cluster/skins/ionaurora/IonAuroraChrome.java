package net.adminrunet.h9cluster.skins.ionaurora;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

/** Contoured instrument islands. Mask03 is a QA reference, never a clipping path. */
final class IonAuroraChrome {
    private IonAuroraChrome() { }

    static void draw(Canvas canvas) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        // Measured outside the nonzero-alpha edge, including antialiasing.
        Path left = new Path();
        left.moveTo(230, 199);
        left.lineTo(250, 190);
        left.lineTo(300, 171);
        left.lineTo(350, 152);
        left.lineTo(400, 135);
        left.lineTo(450, 122);
        left.lineTo(500, 108);
        left.lineTo(552, 103);
        left.lineTo(552, 262);
        left.quadTo(552, 278, 536, 278);
        left.lineTo(246, 278);
        left.quadTo(230, 278, 230, 262);
        left.close();
        panel(canvas, paint, left, 100, 278);

        Path right = new Path();
        right.moveTo(1368, 103);
        right.lineTo(1390, 105);
        right.lineTo(1400, 108);
        right.lineTo(1450, 120);
        right.lineTo(1500, 133);
        right.lineTo(1550, 145);
        right.lineTo(1600, 158);
        right.lineTo(1650, 172);
        right.lineTo(1690, 190);
        right.lineTo(1690, 262);
        right.quadTo(1690, 278, 1674, 278);
        right.lineTo(1384, 278);
        right.quadTo(1368, 278, 1368, 262);
        right.close();
        panel(canvas, paint, right, 100, 278);

        // Three distinct system reservations remain outside the left instruments.
        island(canvas, paint, 81, 396, 384, 491);
        island(canvas, paint, 81, 502, 226, 638);
        island(canvas, paint, 82, 647, 386, 708);
        island(canvas, paint, 400, 644, 1396, 708);
        island(canvas, paint, 1436, 404, 1821, 610);
        island(canvas, paint, 1532, 624, 1824, 708);

        rule(canvas, paint, 233, 418, 233, 480);
        rule(canvas, paint, 233, 658, 233, 699);
        rule(canvas, paint, 731, 657, 731, 699);
        rule(canvas, paint, 1063, 657, 1063, 699);
        rule(canvas, paint, 1454, 473, 1802, 473);
        rule(canvas, paint, 1454, 541, 1802, 541);
        rule(canvas, paint, 501, 365, 568, 365);
        rule(canvas, paint, 1352, 365, 1430, 365);
    }

    private static void island(Canvas canvas, Paint paint,
            float left, float top, float right, float bottom) {
        Path path = new Path();
        path.moveTo(left + 12, top);
        path.lineTo(right - 18, top);
        path.lineTo(right, top + 18);
        path.lineTo(right, bottom - 12);
        path.quadTo(right, bottom, right - 12, bottom);
        path.lineTo(left + 18, bottom);
        path.lineTo(left, bottom - 18);
        path.lineTo(left, top + 12);
        path.quadTo(left, top, left + 12, top);
        path.close();
        panel(canvas, paint, path, top, bottom);
    }

    private static void panel(Canvas canvas, Paint paint, Path path, float top, float bottom) {
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setShader(new LinearGradient(0, top, 0, bottom,
                new int[]{0xD3112940, 0xC3071524, 0xA8030B16},
                new float[]{0, .45f, 1}, Shader.TileMode.CLAMP));
        canvas.drawPath(path, paint);
        // Broad light stays INSIDE the panel; no hidden glow in the exterior gap.
        int save = canvas.save();
        canvas.clipPath(path);
        paint.setStyle(Paint.Style.STROKE);
        paint.setShader(null);
        paint.setColor(0x2832D9EF);
        paint.setStrokeWidth(8);
        canvas.drawPath(path, paint);
        canvas.restoreToCount(save);
        paint.setAlpha(255);
        paint.setShader(new LinearGradient(0, top, 0, bottom,
                new int[]{0xDE4CE9F6, 0x8D23617A, 0xA37664C8}, null,
                Shader.TileMode.CLAMP));
        paint.setStrokeWidth(1.2f);
        canvas.drawPath(path, paint);
        paint.setShader(null);
    }

    private static void rule(Canvas canvas, Paint paint,
            float x1, float y1, float x2, float y2) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setAlpha(255);
        paint.setShader(new LinearGradient(x1, y1, x2, y2,
                new int[]{0x003B6C8A, 0xB642A7BC, 0x003B6C8A}, null,
                Shader.TileMode.CLAMP));
        canvas.drawLine(x1, y1, x2, y2, paint);
        paint.setShader(null);
    }
}
