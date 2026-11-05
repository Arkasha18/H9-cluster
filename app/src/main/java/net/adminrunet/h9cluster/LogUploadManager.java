package net.adminrunet.h9cluster;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

/** Uploads a bounded one-day diagnostic bundle only after an explicit tap. */
final class LogUploadManager {
    interface Listener {
        void onUploadStarted(String machineId);

        void onUploadFinished(Result result);
    }

    static final String ENDPOINT = "https://logs.admin.ru.net/api/v1/uploads";
    static final int MAX_COMPRESSED_BYTES = 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int[] EXPORT_SIZES = {
            DiagnosticLog.EXPORT_LIMIT_BYTES,
            1024 * 1024,
            512 * 1024,
            256 * 1024
    };

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "h9-log-upload");
        thread.setDaemon(true);
        return thread;
    });
    private boolean uploadInProgress;
    private boolean destroyed;

    LogUploadManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void uploadLastDay() {
        if (destroyed || uploadInProgress) {
            return;
        }
        uploadInProgress = true;
        final MachineIdentity.Identity identity = MachineIdentity.current(context);
        DiagnosticLog.info(
                "support_upload",
                "requested",
                "machine_id", identity.code,
                "identity_source", identity.source);
        listener.onUploadStarted(identity.code);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Result result = upload(identity);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        uploadInProgress = false;
                        if (!destroyed) {
                            listener.onUploadFinished(result);
                        }
                    }
                });
            }
        });
    }

    void destroy() {
        destroyed = true;
        executor.shutdownNow();
    }

    private Result upload(MachineIdentity.Identity identity) {
        String uploadId = UUID.randomUUID().toString();
        HttpURLConnection connection = null;
        try {
            Bundle bundle = createBundle();
            URL url = new URL(ENDPOINT);
            if (!"https".equals(url.getProtocol())
                    || !"logs.admin.ru.net".equals(url.getHost())) {
                throw new IOException("Unexpected diagnostics endpoint");
            }
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setFixedLengthStreamingMode(bundle.compressed.length);
            connection.setRequestProperty("Content-Type", "application/x-ndjson");
            connection.setRequestProperty("Content-Encoding", "gzip");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "H9-Cluster/" + BuildConfig.VERSION_NAME);
            connection.setRequestProperty("X-H9-Machine-ID", identity.code);
            connection.setRequestProperty("X-H9-ID-Source", identity.source);
            connection.setRequestProperty("X-H9-App-Version", BuildConfig.VERSION_NAME);
            connection.setRequestProperty("X-H9-Upload-ID", uploadId);
            connection.setRequestProperty("X-H9-Window-Start", String.valueOf(bundle.export.startMs));
            connection.setRequestProperty("X-H9-Window-End", String.valueOf(bundle.export.endMs));
            connection.setRequestProperty("X-H9-Event-Count", String.valueOf(bundle.export.eventCount));
            connection.setRequestProperty("X-H9-Android-SDK", String.valueOf(Build.VERSION.SDK_INT));
            connection.setRequestProperty(
                    "X-H9-Device-Model",
                    headerSafe((Build.MANUFACTURER + " " + Build.MODEL).trim()));

            try (OutputStream output = connection.getOutputStream()) {
                output.write(bundle.compressed);
                output.flush();
            }
            int status = connection.getResponseCode();
            consumeResponse(status >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream());
            if (status == HttpURLConnection.HTTP_OK
                    || status == HttpURLConnection.HTTP_CREATED) {
                DiagnosticLog.info(
                        "support_upload",
                        "completed",
                        "machine_id", identity.code,
                        "upload_id", uploadId,
                        "events", String.valueOf(bundle.export.eventCount),
                        "raw_bytes", String.valueOf(bundle.export.jsonLines.length),
                        "compressed_bytes", String.valueOf(bundle.compressed.length),
                        "http_status", String.valueOf(status));
                return Result.success(identity.code, uploadId, bundle.export.eventCount);
            }
            DiagnosticLog.warn(
                    "support_upload",
                    "server_rejected",
                    "machine_id", identity.code,
                    "http_status", String.valueOf(status));
            if (status == 413) {
                return Result.failure(identity.code, "Журнал оказался слишком большим");
            }
            if (status == 429) {
                return Result.failure(identity.code, "Дневной лимит отправок исчерпан");
            }
            return Result.failure(identity.code, "Сервер вернул ошибку " + status);
        } catch (Throwable error) {
            DiagnosticLog.warn(
                    "support_upload",
                    "failed",
                    "machine_id", identity.code,
                    "error", error.getClass().getSimpleName());
            return Result.failure(
                    identity.code,
                    "Не удалось отправить: " + friendlyError(error));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static Bundle createBundle() throws IOException {
        DiagnosticLog.Export latest = null;
        byte[] compressed = null;
        for (int exportSize : EXPORT_SIZES) {
            latest = DiagnosticLog.exportLastDay(exportSize);
            compressed = gzip(latest.jsonLines);
            if (compressed.length <= MAX_COMPRESSED_BYTES) {
                return new Bundle(latest, compressed);
            }
        }
        throw new IOException("Compressed diagnostics exceed the upload limit");
    }

    static byte[] gzip(byte[] value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(value);
        }
        return bytes.toByteArray();
    }

    private static void consumeResponse(InputStream input) throws IOException {
        if (input == null) {
            return;
        }
        try (InputStream stream = input) {
            byte[] buffer = new byte[1024];
            int remaining = 8192;
            while (remaining > 0) {
                int read = stream.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) {
                    return;
                }
                remaining -= read;
            }
        }
    }

    private static String friendlyError(Throwable error) {
        String name = error.getClass().getSimpleName();
        if (name.contains("UnknownHost")) {
            return "домен сервера пока недоступен";
        }
        if (name.contains("Timeout")) {
            return "сервер не ответил вовремя";
        }
        if (name.contains("SSL")) {
            return "не удалось проверить сертификат сервера";
        }
        return "нет связи с сервером";
    }

    private static String headerSafe(String value) {
        return value == null
                ? ""
                : value.replace("\r", " ").replace("\n", " ").trim();
    }

    static final class Result {
        final boolean success;
        final String machineId;
        final String uploadId;
        final int eventCount;
        final String message;

        private Result(
                boolean success,
                String machineId,
                String uploadId,
                int eventCount,
                String message) {
            this.success = success;
            this.machineId = machineId;
            this.uploadId = uploadId;
            this.eventCount = eventCount;
            this.message = message;
        }

        static Result success(String machineId, String uploadId, int eventCount) {
            return new Result(true, machineId, uploadId, eventCount, "");
        }

        static Result failure(String machineId, String message) {
            return new Result(false, machineId, "", 0, message);
        }
    }

    private static final class Bundle {
        final DiagnosticLog.Export export;
        final byte[] compressed;

        Bundle(DiagnosticLog.Export export, byte[] compressed) {
            this.export = export;
            this.compressed = compressed;
        }
    }
}
