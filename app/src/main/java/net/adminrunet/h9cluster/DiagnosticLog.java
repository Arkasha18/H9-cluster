package net.adminrunet.h9cluster;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small persistent structured log used for user-requested support uploads.
 *
 * The logger owns two one-megabyte files. Export additionally discards records
 * older than 24 hours, so a support upload cannot silently become a long-term
 * vehicle history. Callers must provide metadata rather than raw bus payloads.
 */
public final class DiagnosticLog {
    static final long RETENTION_MS = 24L * 60L * 60L * 1000L;
    static final int FILE_LIMIT_BYTES = 1024 * 1024;
    static final int EXPORT_LIMIT_BYTES = 2 * 1024 * 1024;
    private static final int MAX_LINE_CHARS = 16 * 1024;
    private static final int MAX_VALUE_CHARS = 256;
    private static final String TAG = "H9Diagnostics";
    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("\\\"ts_ms\\\":([0-9]{10,})");
    private static final Pattern VIN_PATTERN =
            Pattern.compile("(?i)(?<![A-Z0-9])[A-HJ-NPR-Z0-9]{17}(?![A-Z0-9])");

    private static volatile DiagnosticLog instance;

    private final Object lock = new Object();
    private final File activeFile;
    private final File previousFile;

    private DiagnosticLog(Context context) {
        File directory = new File(context.getNoBackupFilesDir(), "diagnostics");
        if (!directory.exists() && !directory.mkdirs()) {
            Log.w(TAG, "Cannot create diagnostics directory");
        }
        activeFile = new File(directory, "events.jsonl");
        previousFile = new File(directory, "events.previous.jsonl");
    }

    public static void initialize(Context context) {
        if (instance != null) {
            return;
        }
        synchronized (DiagnosticLog.class) {
            if (instance == null) {
                try {
                    instance = new DiagnosticLog(context.getApplicationContext());
                } catch (RuntimeException error) {
                    Log.w(TAG, "Cannot initialize diagnostics", error);
                }
            }
        }
    }

    public static void info(String component, String event, String... fields) {
        write("INFO", component, event, fields);
    }

    public static void warn(String component, String event, String... fields) {
        write("WARN", component, event, fields);
    }

    public static void error(String component, String event, String... fields) {
        write("ERROR", component, event, fields);
    }

    private static void write(
            String level,
            String component,
            String event,
            String... fields) {
        DiagnosticLog current = instance;
        if (current == null) {
            return;
        }
        current.append(
                formatLine(
                        System.currentTimeMillis(),
                        SystemClock.elapsedRealtime(),
                        level,
                        component,
                        event,
                        fields));
    }

    private void append(String line) {
        byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_LINE_CHARS + 1) {
            Log.w(TAG, "Diagnostic event is too large; dropping it");
            return;
        }
        synchronized (lock) {
            try {
                appendBounded(activeFile, previousFile, bytes);
            } catch (IOException | RuntimeException error) {
                Log.w(TAG, "Cannot persist diagnostics", error);
            }
        }
    }

    static void appendBounded(File activeFile, File previousFile, byte[] bytes)
            throws IOException {
        if (bytes.length > FILE_LIMIT_BYTES) {
            throw new IOException("Diagnostic record exceeds file limit");
        }
        if (activeFile.length() + bytes.length > FILE_LIMIT_BYTES) {
            rotate(activeFile, previousFile);
        }
        try (FileOutputStream output = new FileOutputStream(activeFile, true)) {
            output.write(bytes);
            output.flush();
        }
    }

    private static void rotate(File activeFile, File previousFile) throws IOException {
        if (previousFile.exists() && !previousFile.delete()) {
            throw new IOException("Cannot replace previous diagnostic log");
        }
        if (activeFile.exists() && !activeFile.renameTo(previousFile)) {
            throw new IOException("Cannot rotate diagnostic log");
        }
    }

    public static Export exportLastDay() throws IOException {
        return exportLastDay(EXPORT_LIMIT_BYTES);
    }

    static Export exportLastDay(int maximumBytes) throws IOException {
        DiagnosticLog current = instance;
        if (current == null) {
            throw new IOException("Diagnostic log is not initialized");
        }
        return current.export(
                System.currentTimeMillis() - RETENTION_MS,
                Math.max(1024, Math.min(maximumBytes, EXPORT_LIMIT_BYTES)));
    }

    private Export export(long cutoffMs, int maximumBytes) throws IOException {
        synchronized (lock) {
            List<Record> records = new ArrayList<>();
            readRecords(previousFile, cutoffMs, records);
            readRecords(activeFile, cutoffMs, records);

            int totalBytes = 0;
            for (Record record : records) {
                totalBytes += record.bytes.length;
            }
            int first = 0;
            while (first < records.size() && totalBytes > maximumBytes) {
                totalBytes -= records.get(first).bytes.length;
                first++;
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream(totalBytes);
            long startMs = 0L;
            long endMs = 0L;
            int count = 0;
            for (int index = first; index < records.size(); index++) {
                Record record = records.get(index);
                output.write(record.bytes);
                if (startMs == 0L || record.timestampMs < startMs) {
                    startMs = record.timestampMs;
                }
                endMs = Math.max(endMs, record.timestampMs);
                count++;
            }
            return new Export(output.toByteArray(), startMs, endMs, count);
        }
    }

    private static void readRecords(
            File file,
            long cutoffMs,
            List<Record> records) throws IOException {
        if (!file.isFile()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(file),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0 || line.length() > MAX_LINE_CHARS) {
                    continue;
                }
                long timestampMs = timestampFromLine(line);
                if (timestampMs < cutoffMs) {
                    continue;
                }
                records.add(new Record(
                        timestampMs,
                        (line + "\n").getBytes(StandardCharsets.UTF_8)));
            }
        }
    }

    static long timestampFromLine(String line) {
        Matcher matcher = TIMESTAMP_PATTERN.matcher(line);
        if (!matcher.find()) {
            return -1L;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    static String formatLine(
            long timestampMs,
            long uptimeMs,
            String level,
            String component,
            String event,
            String... fields) {
        StringBuilder result = new StringBuilder(256);
        result.append('{')
                .append("\"ts_ms\":").append(timestampMs)
                .append(",\"uptime_ms\":").append(uptimeMs)
                .append(",\"level\":\"").append(json(level)).append('"')
                .append(",\"component\":\"").append(json(component)).append('"')
                .append(",\"event\":\"").append(json(event)).append('"');
        int fieldCount = fields == null ? 0 : fields.length - fields.length % 2;
        for (int index = 0; index < fieldCount; index += 2) {
            String key = safeKey(fields[index]);
            if (key.length() == 0) {
                continue;
            }
            result.append(",\"").append(key).append("\":\"")
                    .append(json(scrub(fields[index + 1])))
                    .append('"');
        }
        return result.append('}').toString();
    }

    private static String safeKey(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder key = new StringBuilder(Math.min(value.length(), 40));
        for (int index = 0; index < value.length() && key.length() < 40; index++) {
            char character = Character.toLowerCase(value.charAt(index));
            if ((character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '_') {
                key.append(character);
            }
        }
        return key.toString();
    }

    private static String scrub(String value) {
        if (value == null) {
            return "";
        }
        String limited = value.length() <= MAX_VALUE_CHARS
                ? value
                : value.substring(0, MAX_VALUE_CHARS);
        return VIN_PATTERN.matcher(limited).replaceAll("[redacted-vin]");
    }

    private static String json(String value) {
        String source = value == null ? "" : value;
        StringBuilder escaped = new StringBuilder(source.length() + 16);
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            switch (character) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (character >= 0x20) {
                        escaped.append(character);
                    }
                    break;
            }
        }
        return escaped.toString();
    }

    public static final class Export {
        public final byte[] jsonLines;
        public final long startMs;
        public final long endMs;
        public final int eventCount;

        Export(byte[] jsonLines, long startMs, long endMs, int eventCount) {
            this.jsonLines = jsonLines;
            this.startMs = startMs;
            this.endMs = endMs;
            this.eventCount = eventCount;
        }
    }

    private static final class Record {
        final long timestampMs;
        final byte[] bytes;

        Record(long timestampMs, byte[] bytes) {
            this.timestampMs = timestampMs;
            this.bytes = bytes;
        }
    }
}
