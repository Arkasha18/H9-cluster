package net.adminrunet.h9cluster;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Read-only transmission-temperature source.
 *
 * It reads the can_data_collect POSIX shared-memory snapshot already maintained
 * by the stock TBOX. It never changes firewall rules and never sends CAN or UDS
 * requests.
 */
final class TransmissionTemperatureReader {
    interface Listener {
        void onTboxSnapshot(
                TboxCanSnapshotDecoder.Result snapshot,
                long receivedAtMs);
    }

    private static final String TAG = "TransmissionTemp";
    private static final String TBOX_HOST = "172.16.2.97";
    private static final String TBOX_USER = "root";
    private static final int TBOX_SSH_PORT = 22;
    private static final String SNAPSHOT_PATH = "/dev/shm/can_data_collect";
    private static final int SNAPSHOT_CAPTURE_LIMIT_BYTES = 4096;
    private static final int SNAPSHOT_CAPTURE_BUFFER_BYTES =
            SNAPSHOT_CAPTURE_LIMIT_BYTES + 1;
    private static final String READ_SNAPSHOT_COMMAND =
            "dd if=" + SNAPSHOT_PATH + " bs="
                    + SNAPSHOT_CAPTURE_BUFFER_BYTES
                    + " count=1 2>/dev/null";

    private static final int CONNECT_TIMEOUT_MS = 2500;
    private static final int CHANNEL_TIMEOUT_MS = 2000;
    private static final long POLL_INTERVAL_MS = 5000L;
    private static final long FAILURE_LOG_INTERVAL_MS = 60_000L;
    private static final long SUCCESS_LOG_INTERVAL_MS = 5L * 60L * 1000L;
    private static final long CONNECT_LOG_INTERVAL_MS = 5L * 60L * 1000L;
    private static final long BACKOFF_LOG_INTERVAL_MS = 5L * 60L * 1000L;
    private static final int FAILURES_BEFORE_BACKOFF = 3;
    private static final long FAILURE_BACKOFF_MS = 60_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Listener listener;

    private volatile boolean started;
    private volatile Session session;
    private ScheduledExecutorService executor;
    private long lastFailureLogAtMs;
    private long lastSuccessLogAtMs;
    private long pollCount;
    private long successCount;
    private long failureCount;
    private long connectAttemptCount;
    private long lastConnectLogAtMs;
    private long lastConnectedLogAtMs;
    private long lastDisconnectedLogAtMs;
    private long lastRecoveryLogAtMs;
    private long lastBackoffLogAtMs;
    private long backoffUntilMs;
    private long skippedPollCount;
    private long backoffCount;
    private long lastGeneration = -1L;
    private int consecutiveFailures;
    private int lastLoggedEngineState = TboxCanSnapshotDecoder.ENGINE_UNKNOWN;
    private int lastLoggedParkState = -1;
    private String lastTemperatureStatus;
    private boolean previousPollSucceeded;
    private boolean missingSecretLogged;

    TransmissionTemperatureReader(Listener listener) {
        this.listener = listener;
    }

    synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        pollCount = successCount = failureCount = 0L;
        lastFailureLogAtMs = lastSuccessLogAtMs = 0L;
        connectAttemptCount = 0L;
        lastConnectLogAtMs = lastConnectedLogAtMs = 0L;
        lastDisconnectedLogAtMs = lastRecoveryLogAtMs = 0L;
        lastBackoffLogAtMs = backoffUntilMs = 0L;
        skippedPollCount = backoffCount = 0L;
        lastGeneration = -1L;
        consecutiveFailures = 0;
        lastLoggedEngineState = TboxCanSnapshotDecoder.ENGINE_UNKNOWN;
        lastLoggedParkState = -1;
        lastTemperatureStatus = null;
        previousPollSucceeded = false;

        if (!hasConfiguredSecret()) {
            if (!missingSecretLogged) {
                missingSecretLogged = true;
                Log.w(TAG, "H9_TBOX_PASSWORD is not set; temperature source disabled");
                DiagnosticLog.warn(
                        "tbox_temperature",
                        "source_disabled",
                        "reason", "missing_build_secret");
            }
            return;
        }

        DiagnosticLog.info(
                "tbox_temperature",
                "source_start",
                "host", TBOX_HOST,
                "poll_interval_ms", String.valueOf(POLL_INTERVAL_MS));

        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "h9-tbox-temperature");
                thread.setDaemon(true);
                return thread;
            }
        });
        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                pollOnce();
            }
        }, 0L, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    synchronized void stop() {
        if (started) {
            DiagnosticLog.info(
                    "tbox_temperature",
                    "source_stop",
                    "polls", String.valueOf(pollCount),
                    "successes", String.valueOf(successCount),
                    "failures", String.valueOf(failureCount),
                    "skipped_polls", String.valueOf(skippedPollCount),
                    "backoffs", String.valueOf(backoffCount),
                    "session_connected", String.valueOf(
                            session != null && session.isConnected()));
        }
        started = false;
        mainHandler.removeCallbacksAndMessages(null);

        ScheduledExecutorService oldExecutor = executor;
        executor = null;
        if (oldExecutor != null) {
            oldExecutor.shutdownNow();
        }
        disconnectSession();
    }

    private void pollOnce() {
        if (!started) {
            return;
        }
        long pollStartedAtMs = SystemClock.elapsedRealtime();
        if (pollStartedAtMs < backoffUntilMs) {
            skippedPollCount++;
            return;
        }
        pollCount++;
        try {
            byte[] snapshot = readSnapshot();
            final TboxCanSnapshotDecoder.Result decoded;
            try {
                decoded = TboxCanSnapshotDecoder.decode(snapshot);
            } finally {
                Arrays.fill(snapshot, (byte) 0);
            }
            if (!decoded.validSnapshot) {
                handleDecodeFailure(decoded);
                return;
            }

            final long receivedAtMs = SystemClock.elapsedRealtime();
            successCount++;
            consecutiveFailures = 0;
            backoffUntilMs = 0L;
            boolean recovered = !previousPollSucceeded;
            previousPollSucceeded = true;
            boolean logRecovery = recovered
                    && (lastRecoveryLogAtMs == 0L
                            || receivedAtMs - lastRecoveryLogAtMs
                                    >= FAILURE_LOG_INTERVAL_MS);
            if (logRecovery
                    || lastSuccessLogAtMs == 0L
                    || receivedAtMs - lastSuccessLogAtMs >= SUCCESS_LOG_INTERVAL_MS) {
                DiagnosticLog.info(
                        "tbox_temperature",
                        logRecovery ? "poll_recovered" : "poll_ok",
                        "temperature_available", String.valueOf(
                                decoded.hasTemperature()),
                        "temperature_c", decoded.hasTemperature()
                                ? String.valueOf(decoded.temperatureC)
                                : "unavailable",
                        "temperature_status", decoded.temperatureStatus,
                        "temperature_raw", String.valueOf(decoded.rawTemperature),
                        "temperature_records_raw", decoded.temperatureRecordsRaw,
                        "generation", decoded.generationSignature,
                        "engine_state", decoded.hasEngineState()
                                ? String.valueOf(decoded.engineState)
                                : "unavailable",
                        "park", decoded.parkValid
                                ? String.valueOf(decoded.inPark)
                                : "unavailable",
                        "epb", decoded.epbValid
                                ? String.valueOf(decoded.epbEngaged)
                                : "unavailable",
                        "polls", String.valueOf(pollCount),
                        "successes", String.valueOf(successCount),
                        "failures", String.valueOf(failureCount),
                        "session_connected", String.valueOf(
                                session != null && session.isConnected()));
                lastSuccessLogAtMs = receivedAtMs;
                if (logRecovery) {
                    lastRecoveryLogAtMs = receivedAtMs;
                }
            }
            logTemperatureStatusChange(decoded);
            logVehicleStateChange(decoded);
            if (decoded.generation != lastGeneration) {
                lastGeneration = decoded.generation;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (started) {
                            listener.onTboxSnapshot(decoded, receivedAtMs);
                        }
                    }
                });
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (Throwable error) {
            failureCount++;
            consecutiveFailures++;
            previousPollSucceeded = false;
            disconnectSession();
            logFailureThrottled(error);
            activateBackoffIfNeeded("transport");
        }
    }

    private void handleDecodeFailure(TboxCanSnapshotDecoder.Result decoded) {
        failureCount++;
        consecutiveFailures++;
        previousPollSucceeded = false;
        long now = SystemClock.elapsedRealtime();
        if (lastFailureLogAtMs == 0L
                || now - lastFailureLogAtMs >= FAILURE_LOG_INTERVAL_MS) {
            lastFailureLogAtMs = now;
            DiagnosticLog.warn(
                    "tbox_temperature",
                    "snapshot_rejected",
                    "reason", decoded.failureReason,
                    "observed_bytes", String.valueOf(decoded.observedLength),
                    "declared_bytes", String.valueOf(decoded.declaredLength),
                    "header", decoded.headerSignature,
                    "generation", decoded.generationSignature,
                    "capture_capped", String.valueOf(
                            decoded.observedLength > SNAPSHOT_CAPTURE_LIMIT_BYTES),
                    "polls", String.valueOf(pollCount),
                    "successes", String.valueOf(successCount),
                    "failures", String.valueOf(failureCount));
        }
        // A decoder mismatch cannot be repaired by rebuilding the SSH session.
        // Keep the authenticated read-only session and retry at a bounded rate.
        activateBackoffIfNeeded("snapshot_format");
    }

    private void logTemperatureStatusChange(TboxCanSnapshotDecoder.Result decoded) {
        if (lastTemperatureStatus == null) {
            lastTemperatureStatus = decoded.temperatureStatus;
            return;
        }
        if (lastTemperatureStatus.equals(decoded.temperatureStatus)) {
            return;
        }
        String previousStatus = lastTemperatureStatus;
        lastTemperatureStatus = decoded.temperatureStatus;
        DiagnosticLog.info(
                "tbox_temperature",
                "status_changed",
                "previous_status", previousStatus,
                "temperature_status", decoded.temperatureStatus,
                "temperature_available", String.valueOf(decoded.hasTemperature()),
                "temperature_c", decoded.hasTemperature()
                        ? String.valueOf(decoded.temperatureC)
                        : "unavailable",
                "temperature_raw", String.valueOf(decoded.rawTemperature),
                "temperature_records_raw", decoded.temperatureRecordsRaw,
                "generation", decoded.generationSignature,
                "engine_state", decoded.hasEngineState()
                        ? String.valueOf(decoded.engineState)
                        : "unavailable",
                "park", decoded.parkValid
                        ? String.valueOf(decoded.inPark)
                        : "unavailable");
    }

    private void activateBackoffIfNeeded(String reason) {
        if (consecutiveFailures < FAILURES_BEFORE_BACKOFF) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        backoffUntilMs = now + FAILURE_BACKOFF_MS;
        backoffCount++;
        consecutiveFailures = 0;
        if (lastBackoffLogAtMs == 0L
                || now - lastBackoffLogAtMs >= BACKOFF_LOG_INTERVAL_MS) {
            lastBackoffLogAtMs = now;
            DiagnosticLog.info(
                    "tbox_temperature",
                    "retry_backoff",
                    "reason", reason,
                    "backoff_ms", String.valueOf(FAILURE_BACKOFF_MS),
                    "backoffs", String.valueOf(backoffCount),
                    "polls", String.valueOf(pollCount),
                    "failures", String.valueOf(failureCount));
        }
    }

    private void logVehicleStateChange(TboxCanSnapshotDecoder.Result decoded) {
        int parkState = decoded.parkValid ? (decoded.inPark ? 1 : 0) : -1;
        if (decoded.engineState == lastLoggedEngineState
                && parkState == lastLoggedParkState) {
            return;
        }
        lastLoggedEngineState = decoded.engineState;
        lastLoggedParkState = parkState;
        DiagnosticLog.info(
                "tbox_vehicle",
                "state_changed",
                "engine_state", decoded.hasEngineState()
                        ? String.valueOf(decoded.engineState)
                        : "unavailable",
                "engine_raw", String.valueOf(decoded.rawEngineState),
                "park", decoded.parkValid
                        ? String.valueOf(decoded.inPark)
                        : "unavailable",
                "park_raw", String.valueOf(decoded.rawParkState),
                "epb", decoded.epbValid
                        ? String.valueOf(decoded.epbEngaged)
                        : "unavailable",
                "epb_raw", String.valueOf(decoded.rawEpbState));
    }

    private byte[] readSnapshot() throws Exception {
        Session currentSession = getOrConnectSession();
        ChannelExec channel = null;
        InputStream input = null;
        byte[] capture = new byte[SNAPSHOT_CAPTURE_BUFFER_BYTES];
        try {
            channel = (ChannelExec) currentSession.openChannel("exec");
            channel.setCommand(READ_SNAPSHOT_COMMAND);
            channel.setInputStream(null);
            input = channel.getInputStream();
            channel.connect(CONNECT_TIMEOUT_MS);

            int offset = 0;
            long deadline = SystemClock.elapsedRealtime() + CHANNEL_TIMEOUT_MS;
            while (true) {
                while (input.available() > 0 && offset < capture.length) {
                    int read = input.read(
                            capture,
                            offset,
                            capture.length - offset);
                    if (read < 0) {
                        break;
                    }
                    offset += read;
                    if (offset == capture.length) {
                        return Arrays.copyOf(capture, offset);
                    }
                }

                if (channel.isClosed()) {
                    if (offset == 0) {
                        throw new IOException("Empty TBOX snapshot");
                    }
                    return Arrays.copyOf(capture, offset);
                }
                if (SystemClock.elapsedRealtime() >= deadline) {
                    throw new IOException("Timed out reading TBOX snapshot");
                }
                Thread.sleep(10L);
            }
        } finally {
            Arrays.fill(capture, (byte) 0);
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                    // The channel is disconnected below.
                }
            }
            if (channel != null) {
                channel.disconnect();
            }
        }
    }

    private Session getOrConnectSession() throws Exception {
        Session currentSession = session;
        if (currentSession != null && currentSession.isConnected()) {
            return currentSession;
        }

        connectAttemptCount++;
        long connectNow = SystemClock.elapsedRealtime();
        boolean logConnectAttempt = lastConnectLogAtMs == 0L
                || connectNow - lastConnectLogAtMs >= CONNECT_LOG_INTERVAL_MS;
        if (logConnectAttempt) {
            lastConnectLogAtMs = connectNow;
            DiagnosticLog.info(
                    "tbox_temperature",
                    "connect_attempt",
                    "host", TBOX_HOST,
                    "port", String.valueOf(TBOX_SSH_PORT),
                    "attempts", String.valueOf(connectAttemptCount));
        }
        byte[] password = decodeConfiguredSecret();
        Session newSession = null;
        boolean connected = false;
        try {
            JSch jsch = new JSch();
            newSession = jsch.getSession(
                    TBOX_USER,
                    TBOX_HOST,
                    TBOX_SSH_PORT);
            newSession.setPassword(password);
            newSession.setConfig("PreferredAuthentications", "password");
            newSession.setConfig("StrictHostKeyChecking", "no");
            newSession.setServerAliveInterval(10000);
            newSession.connect(CONNECT_TIMEOUT_MS);
            synchronized (this) {
                if (!started) {
                    throw new InterruptedException(
                            "Temperature reader was stopped while connecting");
                }
                session = newSession;
                connected = true;
            }
            Log.i(TAG, "Connected to read-only TBOX temperature source");
            long connectedAt = SystemClock.elapsedRealtime();
            if (lastConnectedLogAtMs == 0L
                    || connectedAt - lastConnectedLogAtMs
                            >= FAILURE_LOG_INTERVAL_MS) {
                DiagnosticLog.info(
                        "tbox_temperature",
                        "connected",
                        "host", TBOX_HOST,
                        "attempts", String.valueOf(connectAttemptCount));
                lastConnectedLogAtMs = connectedAt;
            }
            return newSession;
        } catch (Exception error) {
            if (logConnectAttempt) {
                DiagnosticLog.warn(
                        "tbox_temperature",
                        "connect_failed",
                        "error", error.getClass().getSimpleName(),
                        "detail", safeDetail(error),
                        "attempts", String.valueOf(connectAttemptCount));
            }
            throw error;
        } finally {
            Arrays.fill(password, (byte) 0);
            if (!connected && newSession != null) {
                newSession.disconnect();
            }
        }
    }

    private static boolean hasConfiguredSecret() {
        return BuildConfig.TBOX_SECRET_MASK.length() > 0
                && BuildConfig.TBOX_SECRET_DATA.length() > 0;
    }

    private static byte[] decodeConfiguredSecret() throws IOException {
        byte[] mask = Base64.decode(BuildConfig.TBOX_SECRET_MASK, Base64.NO_WRAP);
        byte[] data = Base64.decode(BuildConfig.TBOX_SECRET_DATA, Base64.NO_WRAP);
        if (mask.length == 0 || mask.length != data.length) {
            Arrays.fill(mask, (byte) 0);
            Arrays.fill(data, (byte) 0);
            throw new IOException("Invalid TBOX secret");
        }
        for (int index = 0; index < data.length; index++) {
            data[index] = (byte) (data[index] ^ mask[index]);
        }
        Arrays.fill(mask, (byte) 0);
        return data;
    }

    private void disconnectSession() {
        Session oldSession = session;
        session = null;
        if (oldSession != null) {
            oldSession.disconnect();
            long now = SystemClock.elapsedRealtime();
            if (lastDisconnectedLogAtMs == 0L
                    || now - lastDisconnectedLogAtMs >= FAILURE_LOG_INTERVAL_MS) {
                DiagnosticLog.info(
                        "tbox_temperature",
                        "session_disconnected",
                        "attempts", String.valueOf(connectAttemptCount));
                lastDisconnectedLogAtMs = now;
            }
        }
    }

    private void logFailureThrottled(Throwable error) {
        long now = SystemClock.elapsedRealtime();
        if (lastFailureLogAtMs == 0L
                || now - lastFailureLogAtMs >= FAILURE_LOG_INTERVAL_MS) {
            lastFailureLogAtMs = now;
            Log.w(TAG, "TBOX temperature read failed; will retry", error);
            DiagnosticLog.warn(
                    "tbox_temperature",
                    "poll_failed",
                    "error", error.getClass().getSimpleName(),
                    "detail", safeDetail(error),
                    "polls", String.valueOf(pollCount),
                    "successes", String.valueOf(successCount),
                    "failures", String.valueOf(failureCount));
        }
    }

    private static String safeDetail(Throwable error) {
        String detail = error == null ? "" : error.getMessage();
        return detail == null ? "" : detail;
    }
}
