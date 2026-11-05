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
 * Read-only high-rate vehicle-data source backed by the MCU FDBus service.
 *
 * The service broadcasts protobuf-wrapped raw CAN frames as event 0x0100.
 * Haval H9 engine speed is a big-endian 16-bit value in bytes 5-6 of CAN
 * frame 0x111, scaled at eight counts per RPM. ECM2 frame 0x271 carries the
 * cumulative fuel counter used to derive live fuel flow. ABS3 frame 0x265
 * supplies an independent, validity-checked vehicle speed.
 */
final class FdbusRpmReader implements FdbusClientListener {
    interface Listener {
        void onFdbusRpm(int rpm, long receivedAtMs);

        void onFdbusFuelConsumptionCounter(int counter, long receivedAtMs);

        void onFdbusVehicleSpeed(float speedKph, long receivedAtMs);
    }

    private static final String TAG = "FdbusRpmReader";
    private static final String SERVICE_URL = "svc://fdbus_mcu_ipc";
    private static final int MCU_CAN_EVENT = 0x0100;
    private static final int ENGINE_SPEED_CAN_ID = 0x111;
    private static final int FUEL_CONSUMPTION_CAN_ID = 0x271;
    private static final int MAX_RPM = 8000;
    private static final long DIAGNOSTIC_INTERVAL_MS = 60_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object pendingLock = new Object();
    private final Listener listener;

    private Fdbus runtime;
    private volatile FdbusClient client;
    private volatile boolean started;
    private volatile boolean online;
    private volatile long lastSpeedStreamFrameAtMs;
    private volatile long speedSessionStartedAtMs;
    private volatile long deliveredSpeedAtMs;
    private boolean deliveryScheduled;
    private int pendingRpm;
    private long pendingReceivedAtMs;
    private boolean fuelDeliveryScheduled;
    private int pendingFuelCounter;
    private long pendingFuelReceivedAtMs;
    private boolean speedDeliveryScheduled;
    private float pendingSpeedKph;
    private long pendingSpeedReceivedAtMs;
    private long broadcastCount;
    private long decodeFailureCount;
    private long speedFrameCount;
    private long rpmFrameCount;
    private long fuelFrameCount;
    private long speedGapCount;
    private long lastDiagnosticAtMs;
    private long lastSpeedGapLogAtMs;

    private final Runnable deliverPendingSpeed = new Runnable() {
        @Override
        public void run() {
            float speed;
            long receivedAtMs;
            synchronized (pendingLock) {
                speedDeliveryScheduled = false;
                speed = pendingSpeedKph;
                receivedAtMs = pendingSpeedReceivedAtMs;
            }
            if (started) {
                if (!online || receivedAtMs < speedSessionStartedAtMs) {
                    speed = Float.NaN;
                    receivedAtMs = 0;
                }
                deliveredSpeedAtMs = Float.isFinite(speed) ? receivedAtMs : 0;
                listener.onFdbusVehicleSpeed(speed, receivedAtMs);
            }
        }
    };

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

    private final Runnable deliverPendingFuelCounter = new Runnable() {
        @Override
        public void run() {
            int counter;
            long receivedAtMs;
            synchronized (pendingLock) {
                fuelDeliveryScheduled = false;
                counter = pendingFuelCounter;
                receivedAtMs = pendingFuelReceivedAtMs;
            }
            if (started) {
                listener.onFdbusFuelConsumptionCounter(counter, receivedAtMs);
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
        broadcastCount = decodeFailureCount = speedFrameCount = rpmFrameCount = 0L;
        fuelFrameCount = speedGapCount = 0L;
        lastDiagnosticAtMs = lastSpeedGapLogAtMs = 0L;
        DiagnosticLog.info("fdbus", "source_start", "service", SERVICE_URL);
        try {
            runtime = new Fdbus();
            client = new FdbusClient("h9_cluster_vehicle", this);
            boolean accepted = client.connect(SERVICE_URL);
            Log.i(TAG, "Connect accepted=" + accepted);
            DiagnosticLog.info(
                    "fdbus",
                    "connect_requested",
                    "accepted", String.valueOf(accepted));
            if (!accepted) {
                stop();
            }
        } catch (Throwable error) {
            Log.e(TAG, "FDBus vehicle source unavailable; Binder fallback remains active", error);
            DiagnosticLog.error(
                    "fdbus",
                    "source_unavailable",
                    "error", error.getClass().getSimpleName());
            stop();
        }
    }

    void stop() {
        if (started) {
            DiagnosticLog.info(
                    "fdbus",
                    "source_stop",
                    "online", String.valueOf(online),
                    "broadcasts", String.valueOf(broadcastCount),
                    "decode_failures", String.valueOf(decodeFailureCount),
                    "speed_frames", String.valueOf(speedFrameCount),
                    "rpm_frames", String.valueOf(rpmFrameCount),
                    "fuel_frames", String.valueOf(fuelFrameCount));
        }
        started = false;
        online = false;
        lastSpeedStreamFrameAtMs = 0;
        deliveredSpeedAtMs = 0;
        mainHandler.removeCallbacks(deliverPendingRpm);
        mainHandler.removeCallbacks(deliverPendingFuelCounter);
        mainHandler.removeCallbacks(deliverPendingSpeed);
        synchronized (pendingLock) {
            deliveryScheduled = false;
            fuelDeliveryScheduled = false;
            speedDeliveryScheduled = false;
        }

        FdbusClient oldClient = client;
        client = null;
        if (oldClient != null) {
            try {
                oldClient.disconnect();
            } catch (Throwable error) {
                Log.w(TAG, "Disconnect failed", error);
                DiagnosticLog.warn(
                        "fdbus",
                        "disconnect_failed",
                        "error", error.getClass().getSimpleName());
            }
            try {
                oldClient.destroy();
            } catch (Throwable error) {
                Log.w(TAG, "Destroy failed", error);
                DiagnosticLog.warn(
                        "fdbus",
                        "destroy_failed",
                        "error", error.getClass().getSimpleName());
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
        lastSpeedStreamFrameAtMs = 0;
        speedSessionStartedAtMs = SystemClock.elapsedRealtime();
        deliveredSpeedAtMs = 0;
        online = accepted;
        scheduleSpeedDelivery(Float.NaN, 0);
        Log.i(TAG, "Online sid=" + sessionId + " subscribeAccepted=" + accepted);
        DiagnosticLog.info(
                "fdbus",
                "online",
                "session_id", String.valueOf(sessionId),
                "subscribe_accepted", String.valueOf(accepted));
    }

    @Override
    public void onOffline(int sessionId) {
        online = false;
        lastSpeedStreamFrameAtMs = 0;
        deliveredSpeedAtMs = 0;
        scheduleSpeedDelivery(Float.NaN, 0);
        Log.w(TAG, "Offline sid=" + sessionId + "; Binder fallback will take over");
        DiagnosticLog.warn(
                "fdbus",
                "offline",
                "session_id", String.valueOf(sessionId));
    }

    @Override
    public void onBroadcast(FdbusMessage message) {
        if (!started || message.code() != MCU_CAN_EVENT) {
            return;
        }
        broadcastCount++;
        McuCanFrame frame = McuCanFrame.decode(message.byteArray());
        if (frame == null) {
            decodeFailureCount++;
            maybeLogHealth(SystemClock.elapsedRealtime());
            return;
        }

        long receivedAtMs = SystemClock.elapsedRealtime();
        maybeLogHealth(receivedAtMs);
        if (online && frame.channel == McuVehicleSpeed.CHANNEL) {
            if (lastSpeedStreamFrameAtMs > 0
                    && receivedAtMs - lastSpeedStreamFrameAtMs
                            > VehicleSpeedSelector.MCU_STREAM_STALE_MS) {
                speedGapCount++;
                if (lastSpeedGapLogAtMs == 0L
                        || receivedAtMs - lastSpeedGapLogAtMs
                                >= DIAGNOSTIC_INTERVAL_MS) {
                    DiagnosticLog.warn(
                            "fdbus",
                            "speed_stream_gap",
                            "gap_ms", String.valueOf(
                                    receivedAtMs - lastSpeedStreamFrameAtMs),
                            "gap_count", String.valueOf(speedGapCount));
                    lastSpeedGapLogAtMs = receivedAtMs;
                }
                speedSessionStartedAtMs = receivedAtMs;
                deliveredSpeedAtMs = 0;
                scheduleSpeedDelivery(Float.NaN, 0);
            }
            lastSpeedStreamFrameAtMs = receivedAtMs;
            if (frame.id == McuVehicleSpeed.CAN_ID) {
                speedFrameCount++;
                scheduleSpeedDelivery(McuVehicleSpeed.decode(frame.data), receivedAtMs);
            }
        }
        if (frame.id == FUEL_CONSUMPTION_CAN_ID) {
            fuelFrameCount++;
            int counter = decodeFuelConsumptionCounter(frame.data);
            if (counter >= 0) {
                scheduleFuelDelivery(counter, receivedAtMs);
            }
            return;
        }
        if (frame.id != ENGINE_SPEED_CAN_ID || frame.data.length < 8) {
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
        rpmFrameCount++;
        scheduleDelivery(rpm, receivedAtMs);
    }

    @Override
    public void onGetEvent(FdbusMessage message) {
        // This source only consumes broadcasts.
    }

    @Override
    public void onReply(FdbusMessage message) {
        // This source never invokes methods.
    }

    long speedStreamFrameAtMs() {
        return started && online && deliveredSpeedAtMs > 0
                && deliveredSpeedAtMs >= speedSessionStartedAtMs
                ? lastSpeedStreamFrameAtMs : 0;
    }

    private void scheduleSpeedDelivery(float speedKph, long receivedAtMs) {
        synchronized (pendingLock) {
            pendingSpeedKph = speedKph;
            pendingSpeedReceivedAtMs = receivedAtMs;
            if (speedDeliveryScheduled) return;
            speedDeliveryScheduled = true;
        }
        if (!mainHandler.post(deliverPendingSpeed)) {
            synchronized (pendingLock) { speedDeliveryScheduled = false; }
        }
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

    private void scheduleFuelDelivery(int counter, long receivedAtMs) {
        boolean shouldPost = false;
        synchronized (pendingLock) {
            pendingFuelCounter = counter;
            pendingFuelReceivedAtMs = receivedAtMs;
            if (!fuelDeliveryScheduled) {
                fuelDeliveryScheduled = true;
                shouldPost = true;
            }
        }
        if (shouldPost && !mainHandler.post(deliverPendingFuelCounter)) {
            synchronized (pendingLock) {
                fuelDeliveryScheduled = false;
            }
        }
    }

    /** ECM2 byte 0 is checksum; bytes 1-2 are the unsigned fuel counter. */
    static int decodeFuelConsumptionCounter(byte[] canData) {
        if (canData == null || canData.length < 3) {
            return -1;
        }
        return ((canData[1] & 0xff) << 8) | (canData[2] & 0xff);
    }

    private void maybeLogHealth(long nowMs) {
        if (lastDiagnosticAtMs > 0L
                && nowMs >= lastDiagnosticAtMs
                && nowMs - lastDiagnosticAtMs < DIAGNOSTIC_INTERVAL_MS) {
            return;
        }
        DiagnosticLog.info(
                "fdbus",
                "health",
                "online", String.valueOf(online),
                "broadcasts", String.valueOf(broadcastCount),
                "decode_failures", String.valueOf(decodeFailureCount),
                "speed_frames", String.valueOf(speedFrameCount),
                "rpm_frames", String.valueOf(rpmFrameCount),
                "fuel_frames", String.valueOf(fuelFrameCount),
                "speed_gap_count", String.valueOf(speedGapCount),
                "speed_stream_age_ms", lastSpeedStreamFrameAtMs > 0L
                        && nowMs >= lastSpeedStreamFrameAtMs
                        ? String.valueOf(nowMs - lastSpeedStreamFrameAtMs)
                        : "unavailable");
        lastDiagnosticAtMs = nowMs;
    }

}
