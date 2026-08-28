package net.adminrunet.h9cluster.skins;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** Cached Wi-Fi transport state and a small programmatic status glyph. */
public final class WifiIndicator {
    private static final long REFRESH_INTERVAL_MS = 1_000L;
    private static final int COLOR_CONNECTED = 0xFFF7F7F5;
    private static final int COLOR_DISCONNECTED = 0xFF596168;

    private final ConnectivityManager connectivityManager;
    private final Path path = new Path();
    private long lastCheckedAtMs = Long.MIN_VALUE;
    private boolean connected;

    public WifiIndicator(Context context) {
        connectivityManager = (ConnectivityManager) context
                .getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void draw(
            Canvas canvas,
            Paint paint,
            float centerX,
            float centerY,
            long nowMs) {
        boolean wifiConnected = isConnected(nowMs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(3.0f);
        paint.setColor(wifiConnected
                ? COLOR_CONNECTED
                : COLOR_DISCONNECTED);

        path.reset();
        path.moveTo(centerX - 14.0f, centerY - 1.0f);
        path.quadTo(
                centerX,
                centerY - 15.0f,
                centerX + 14.0f,
                centerY - 1.0f);
        canvas.drawPath(path, paint);

        path.reset();
        path.moveTo(centerX - 8.0f, centerY + 4.0f);
        path.quadTo(
                centerX,
                centerY - 4.0f,
                centerX + 8.0f,
                centerY + 4.0f);
        canvas.drawPath(path, paint);

        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerX, centerY + 10.0f, 2.8f, paint);
        if (!wifiConnected) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.4f);
            canvas.drawLine(
                    centerX - 11.0f,
                    centerY - 12.0f,
                    centerX + 11.0f,
                    centerY + 11.0f,
                    paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    boolean isConnected(long nowMs) {
        if (lastCheckedAtMs == Long.MIN_VALUE
                || nowMs < lastCheckedAtMs
                || nowMs - lastCheckedAtMs >= REFRESH_INTERVAL_MS) {
            connected = hasWifiTransport(connectivityManager);
            lastCheckedAtMs = nowMs;
        }
        return connected;
    }

    static boolean hasWifiTransport(ConnectivityManager manager) {
        if (manager == null) {
            return false;
        }
        try {
            Network[] networks = manager.getAllNetworks();
            for (Network network : networks) {
                NetworkCapabilities capabilities =
                        manager.getNetworkCapabilities(network);
                if (capabilities != null
                        && capabilities.hasTransport(
                                NetworkCapabilities.TRANSPORT_WIFI)) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }
}
