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
        void onTransmissionTemperature(float temperatureC, long receivedAtMs);
    }

    private static final String TAG = "TransmissionTemp";
    private static final String TBOX_HOST = "172.16.2.97";
    private static final String TBOX_USER = "root";
    private static final int TBOX_SSH_PORT = 22;
    private static final String SNAPSHOT_PATH = "/dev/shm/can_data_collect";
    private static final String READ_SNAPSHOT_COMMAND =
            "dd if=" + SNAPSHOT_PATH + " bs="
                    + TboxTransmissionTemperatureDecoder.SNAPSHOT_LENGTH
                    + " count=1 2>/dev/null";

    private static final int CONNECT_TIMEOUT_MS = 2500;
    private static final int CHANNEL_TIMEOUT_MS = 2000;
    private static final long POLL_INTERVAL_MS = 5000L;
    private static final long FAILURE_LOG_INTERVAL_MS = 30000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Listener listener;

    private volatile boolean started;
    private volatile Session session;
    private ScheduledExecutorService executor;
    private long lastFailureLogAtMs;
    private boolean missingSecretLogged;

    TransmissionTemperatureReader(Listener listener) {
        this.listener = listener;
    }

    synchronized void start() {
        if (started) {
            return;
        }
        started = true;

        if (!hasConfiguredSecret()) {
            if (!missingSecretLogged) {
                missingSecretLogged = true;
                Log.w(TAG, "H9_TBOX_PASSWORD is not set; temperature source disabled");
            }
            return;
        }

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
        try {
            byte[] snapshot = readSnapshot();
            final float temperatureC;
            try {
                temperatureC =
                        TboxTransmissionTemperatureDecoder.decodeCelsius(snapshot);
            } finally {
                Arrays.fill(snapshot, (byte) 0);
            }
            if (Float.isNaN(temperatureC)) {
                throw new IOException("Invalid can_data_collect snapshot");
            }

            lastFailureLogAtMs = 0L;
            final long receivedAtMs = SystemClock.elapsedRealtime();
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (started) {
                        listener.onTransmissionTemperature(
                                temperatureC,
                                receivedAtMs);
                    }
                }
            });
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (Throwable error) {
            disconnectSession();
            logFailureThrottled(error);
        }
    }

    private byte[] readSnapshot() throws Exception {
        Session currentSession = getOrConnectSession();
        ChannelExec channel = null;
        InputStream input = null;
        byte[] snapshot = new byte[
                TboxTransmissionTemperatureDecoder.SNAPSHOT_LENGTH];
        boolean complete = false;
        try {
            channel = (ChannelExec) currentSession.openChannel("exec");
            channel.setCommand(READ_SNAPSHOT_COMMAND);
            channel.setInputStream(null);
            input = channel.getInputStream();
            channel.connect(CONNECT_TIMEOUT_MS);

            int offset = 0;
            long deadline = SystemClock.elapsedRealtime() + CHANNEL_TIMEOUT_MS;
            while (true) {
                while (input.available() > 0 && offset < snapshot.length) {
                    int read = input.read(
                            snapshot,
                            offset,
                            snapshot.length - offset);
                    if (read < 0) {
                        break;
                    }
                    offset += read;
                    if (offset == snapshot.length) {
                        complete = true;
                        return snapshot;
                    }
                }

                if (channel.isClosed()) {
                    break;
                }
                if (SystemClock.elapsedRealtime() >= deadline) {
                    throw new IOException("Timed out reading TBOX snapshot");
                }
                Thread.sleep(10L);
            }
            throw new IOException(
                    "Incomplete TBOX snapshot: " + offset
                            + "/" + snapshot.length + " bytes");
        } finally {
            if (!complete) {
                Arrays.fill(snapshot, (byte) 0);
            }
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
            return newSession;
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
        }
    }

    private void logFailureThrottled(Throwable error) {
        long now = SystemClock.elapsedRealtime();
        if (lastFailureLogAtMs == 0L
                || now - lastFailureLogAtMs >= FAILURE_LOG_INTERVAL_MS) {
            lastFailureLogAtMs = now;
            Log.w(TAG, "TBOX temperature read failed; will retry", error);
        }
    }
}
