package net.adminrunet.h9cluster;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import ipc.fdbus.Fdbus;
import ipc.fdbus.FdbusClient;
import ipc.fdbus.FdbusClientListener;
import ipc.fdbus.FdbusMessage;
import ipc.fdbus.SubscribeItem;

/**
 * Explicitly started, read-only FDBus probe.
 *
 * It only performs connect and subscribe operations and never invokes, sends,
 * publishes or broadcasts a vehicle message.
 */
public final class FdbusProbeService extends Service
        implements FdbusClientListener {
    private static final String TAG = "H9FdbusProbe";
    private static final String NOTIFICATION_CHANNEL = "h9_fdbus_probe";
    private static final int NOTIFICATION_ID = 0x0FDB;
    private static final String DEFAULT_SERVICE = "fdbus_mcu_ipc";
    private static final int DEFAULT_GROUP = 0;
    private static final int MCU_CAN_EVENT = 0x0100;
    private static final int MAX_HEX_BYTES = 256;
    private static final long MIN_CHANGED_LOG_INTERVAL_MS = 35L;

    private final ConcurrentHashMap<Integer, EventStats> eventStats =
            new ConcurrentHashMap<>();

    private Fdbus runtime;
    private FdbusClient client;
    private String serviceName = DEFAULT_SERVICE;
    private int group = DEFAULT_GROUP;
    private String topic;
    private Integer exactEvent;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        enterForeground();
        stopProbe();
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        serviceName = sanitizeServiceName(intent.getStringExtra("service"));
        group = clampGroup(intent.getIntExtra("group", DEFAULT_GROUP));
        topic = emptyToNull(intent.getStringExtra("topic"));
        exactEvent = intent.hasExtra("event")
                ? Integer.valueOf(intent.getIntExtra("event", 0))
                : null;

        try {
            runtime = new Fdbus();
            client = new FdbusClient("h9_cluster_probe", this);
            String url = "svc://" + serviceName;
            boolean accepted = client.connect(url);
            Log.i(
                    TAG,
                    "CONNECT service="
                            + serviceName
                            + " url="
                            + url
                            + " accepted="
                            + accepted
                            + " group="
                            + group
                            + " event="
                            + formatOptionalEvent(exactEvent)
                            + " topic="
                            + String.valueOf(topic));
            if (!accepted) {
                stopProbe();
                stopSelf(startId);
            }
        } catch (Throwable error) {
            Log.e(TAG, "START_FAILED service=" + serviceName, error);
            stopProbe();
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onOnline(int sessionId) {
        FdbusClient currentClient = client;
        if (currentClient == null) {
            return;
        }
        ArrayList<SubscribeItem> subscriptions = new ArrayList<>(1);
        if (exactEvent != null) {
            subscriptions.add(
                    SubscribeItem.newEvent(exactEvent.intValue(), topic));
        } else {
            subscriptions.add(SubscribeItem.newGroup(group, topic));
        }
        boolean accepted = currentClient.subscribe(subscriptions);
        Log.i(
                TAG,
                "ONLINE service="
                        + serviceName
                        + " sid="
                        + sessionId
                        + " subscribeAccepted="
                        + accepted
                        + " code="
                        + formatCode(subscriptions.get(0).code()));
    }

    @Override
    public void onOffline(int sessionId) {
        Log.i(TAG, "OFFLINE service=" + serviceName + " sid=" + sessionId);
    }

    @Override
    public void onBroadcast(FdbusMessage message) {
        long now = SystemClock.elapsedRealtime();
        int code = message.code();
        byte[] payload = message.byteArray();
        if (code == MCU_CAN_EVENT) {
            McuCanFrame frame = decodeMcuCanFrame(payload);
            if (frame != null) {
                Log.i(
                        TAG,
                        "CAN t="
                                + now
                                + " id="
                                + String.format(Locale.US, "0x%03X", frame.id)
                                + " dlc="
                                + frame.data.length
                                + " data="
                                + toHex(frame.data, frame.data.length));
                return;
            }
        }
        String hex = toHex(payload, MAX_HEX_BYTES);

        EventStats stats = eventStats.get(Integer.valueOf(code));
        if (stats == null) {
            EventStats newStats = new EventStats();
            EventStats existing =
                    eventStats.putIfAbsent(Integer.valueOf(code), newStats);
            stats = existing == null ? newStats : existing;
        }

        boolean shouldLog;
        long count;
        synchronized (stats) {
            stats.count++;
            count = stats.count;
            boolean changed = !hex.equals(stats.lastHex);
            shouldLog = count <= 3
                    || (changed
                            && now - stats.lastLoggedAtMs
                                    >= MIN_CHANGED_LOG_INTERVAL_MS)
                    || now - stats.lastLoggedAtMs >= 1000L;
            if (shouldLog) {
                stats.lastHex = hex;
                stats.lastLoggedAtMs = now;
            }
        }

        if (shouldLog) {
            Log.i(
                    TAG,
                    "EVENT t="
                            + now
                            + " service="
                            + serviceName
                            + " code="
                            + formatCode(code)
                            + " count="
                            + count
                            + " topic="
                            + String.valueOf(message.topic())
                            + " len="
                            + (payload == null ? 0 : payload.length)
                            + " hex="
                            + hex);
        }
    }

    @Override
    public void onGetEvent(FdbusMessage message) {
        Log.i(
                TAG,
                "GET_EVENT code="
                        + formatCode(message.code())
                        + " status="
                        + message.returnValue());
    }

    @Override
    public void onReply(FdbusMessage message) {
        Log.i(
                TAG,
                "REPLY code="
                        + formatCode(message.code())
                        + " status="
                        + message.returnValue());
    }

    @Override
    public void onDestroy() {
        stopProbe();
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void enterForeground() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL,
                    "H9 FDBus diagnostics",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Read-only FDBus event probe");
            manager.createNotificationChannel(channel);
        }

        Notification.Builder builder = Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, NOTIFICATION_CHANNEL)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_dashboard)
                .setContentTitle("H9 FDBus probe")
                .setContentText("Read-only diagnostics active")
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void stopProbe() {
        FdbusClient oldClient = client;
        client = null;
        if (oldClient != null) {
            try {
                oldClient.disconnect();
            } catch (Throwable error) {
                Log.w(TAG, "Disconnect failed", error);
            }
            try {
                oldClient.destroy();
            } catch (Throwable error) {
                Log.w(TAG, "Destroy failed", error);
            }
        }
        runtime = null;
        eventStats.clear();
    }

    private static String sanitizeServiceName(String value) {
        String normalized = emptyToNull(value);
        if (normalized == null
                || !normalized.matches("[A-Za-z0-9_.-]+")) {
            return DEFAULT_SERVICE;
        }
        return normalized;
    }

    private static int clampGroup(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() == 0 ? null : normalized;
    }

    private static String formatOptionalEvent(Integer event) {
        return event == null ? "group" : formatCode(event.intValue());
    }

    private static String formatCode(int code) {
        return String.format(Locale.US, "0x%08X(%d)", code, code);
    }

    /**
     * The MCU IPC event is a tiny protobuf envelope:
     * field 1 (wire type 2) contains [channel, flags, dlc, flags, CAN id (BE24),
     * data], while field 2 repeats the raw frame length.
     */
    private static McuCanFrame decodeMcuCanFrame(byte[] payload) {
        if (payload == null || payload.length < 10 || payload[0] != 0x0A) {
            return null;
        }

        int cursor = 1;
        int rawLength = 0;
        int shift = 0;
        while (cursor < payload.length && shift <= 28) {
            int current = payload[cursor++] & 0xff;
            rawLength |= (current & 0x7f) << shift;
            if ((current & 0x80) == 0) {
                break;
            }
            shift += 7;
        }
        if (rawLength < 7 || cursor + rawLength > payload.length) {
            return null;
        }

        int dlc = payload[cursor + 2] & 0xff;
        if (dlc > rawLength - 7) {
            return null;
        }
        int id = ((payload[cursor + 4] & 0xff) << 16)
                | ((payload[cursor + 5] & 0xff) << 8)
                | (payload[cursor + 6] & 0xff);
        byte[] data = new byte[dlc];
        System.arraycopy(payload, cursor + 7, data, 0, dlc);
        return new McuCanFrame(id, data);
    }

    private static String toHex(byte[] payload, int maximumBytes) {
        if (payload == null || payload.length == 0) {
            return "";
        }
        int count = Math.min(payload.length, maximumBytes);
        StringBuilder result = new StringBuilder(count * 2 + 3);
        for (int index = 0; index < count; index++) {
            result.append(
                    String.format(
                            Locale.US, "%02X", payload[index] & 0xff));
        }
        if (payload.length > count) {
            result.append("...");
        }
        return result.toString();
    }

    private static final class EventStats {
        long count;
        long lastLoggedAtMs;
        String lastHex = "";
    }

    private static final class McuCanFrame {
        final int id;
        final byte[] data;

        McuCanFrame(int id, byte[] data) {
            this.id = id;
            this.data = data;
        }
    }
}
