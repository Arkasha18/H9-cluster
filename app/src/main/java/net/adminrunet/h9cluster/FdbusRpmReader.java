package net.adminrunet.h9cluster;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;

import ipc.fdbus.Fdbus;
import ipc.fdbus.FdbusClient;
import ipc.fdbus.FdbusClientListener;
import ipc.fdbus.FdbusMessage;
import ipc.fdbus.SubscribeItem;

/**
 * Read-only high-rate engine-speed source backed by the MCU FDBus service.
 *
 * The service broadcasts protobuf-wrapped raw CAN frames as event 0x0100.
 * Haval H9 engine speed is a big-endian 16-bit value in bytes 5-6 of CAN
 * frame 0x111, scaled at eight counts per RPM.
 */
final class FdbusRpmReader implements FdbusClientListener {
    interface Listener {
        void onFdbusRpm(int rpm, long receivedAtMs);
    }

    private static final String TAG = "FdbusRpmReader";
    private static final String SERVICE_URL = "svc://fdbus_mcu_ipc";
    private static final int MCU_CAN_EVENT = 0x0100;
    private static final int ENGINE_SPEED_CAN_ID = 0x111;
    private static final int MAX_RPM = 8000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object pendingLock = new Object();
    private final Listener listener;

    private Fdbus runtime;
    private volatile FdbusClient client;
    private volatile boolean started;
    private boolean deliveryScheduled;
    private int pendingRpm;
    private long pendingReceivedAtMs;

    private final Runnable deliverPendingRpm = new Runnable() {
        @Override
        public void run() {
            int rpm;
            long receivedAtMs;
            synchronized (pendingLock) {
                deliveryScheduled = false;
                rpm = pendingRpm;
                receivedAtMs = pendingReceivedAtMs;
            }
            if (started) {
                listener.onFdbusRpm(rpm, receivedAtMs);
            }
        }
    };

    FdbusRpmReader(Listener listener) {
        this.listener = listener;
    }

    void start() {
        if (started) {
            return;
        }
        started = true;
        try {
            runtime = new Fdbus();
            client = new FdbusClient("h9_cluster_rpm", this);
            boolean accepted = client.connect(SERVICE_URL);
            Log.i(TAG, "Connect accepted=" + accepted);
            if (!accepted) {
                stop();
            }
        } catch (Throwable error) {
            Log.e(TAG, "FDBus RPM source unavailable; Binder fallback remains active", error);
            stop();
        }
    }

    void stop() {
        started = false;
        mainHandler.removeCallbacks(deliverPendingRpm);
        synchronized (pendingLock) {
            deliveryScheduled = false;
        }

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
    }

    @Override
    public void onOnline(int sessionId) {
        FdbusClient currentClient = client;
        if (!started || currentClient == null) {
            return;
        }
        ArrayList<SubscribeItem> subscriptions = new ArrayList<>(1);
        subscriptions.add(SubscribeItem.newEvent(MCU_CAN_EVENT));
        boolean accepted = currentClient.subscribe(subscriptions);
        Log.i(TAG, "Online sid=" + sessionId + " subscribeAccepted=" + accepted);
    }

    @Override
    public void onOffline(int sessionId) {
        Log.w(TAG, "Offline sid=" + sessionId + "; Binder fallback will take over");
    }

    @Override
    public void onBroadcast(FdbusMessage message) {
        if (!started || message.code() != MCU_CAN_EVENT) {
            return;
        }
        McuCanFrame frame = decodeMcuCanFrame(message.byteArray());
        if (frame == null
                || frame.id != ENGINE_SPEED_CAN_ID
                || frame.data.length < 8) {
            return;
        }

        int raw = ((frame.data[5] & 0xff) << 8) | (frame.data[6] & 0xff);
        if (raw == 0xffff) {
            return;
        }
        int rpm = Math.round(raw / 8.0f);
        if (rpm < 0 || rpm > MAX_RPM) {
            return;
        }
        scheduleDelivery(rpm, SystemClock.elapsedRealtime());
    }

    @Override
    public void onGetEvent(FdbusMessage message) {
        // This source only consumes broadcasts.
    }

    @Override
    public void onReply(FdbusMessage message) {
        // This source never invokes methods.
    }

    private void scheduleDelivery(int rpm, long receivedAtMs) {
        boolean shouldPost = false;
        synchronized (pendingLock) {
            pendingRpm = rpm;
            pendingReceivedAtMs = receivedAtMs;
            if (!deliveryScheduled) {
                deliveryScheduled = true;
                shouldPost = true;
            }
        }
        if (shouldPost && !mainHandler.post(deliverPendingRpm)) {
            synchronized (pendingLock) {
                deliveryScheduled = false;
            }
        }
    }

    /**
     * Event 0x0100 is a protobuf envelope. Field 1 contains:
     * [channel, flags, DLC, flags, CAN ID (BE24), data].
     */
    private static McuCanFrame decodeMcuCanFrame(byte[] payload) {
        if (payload == null || payload.length < 10 || payload[0] != 0x0A) {
            return null;
        }

        int cursor = 1;
        int rawLength = 0;
        int shift = 0;
        boolean completeLength = false;
        while (cursor < payload.length && shift <= 28) {
            int current = payload[cursor++] & 0xff;
            rawLength |= (current & 0x7f) << shift;
            if ((current & 0x80) == 0) {
                completeLength = true;
                break;
            }
            shift += 7;
        }
        if (!completeLength || rawLength < 7 || cursor + rawLength > payload.length) {
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

    private static final class McuCanFrame {
        final int id;
        final byte[] data;

        McuCanFrame(int id, byte[] data) {
            this.id = id;
            this.data = data;
        }
    }
}
