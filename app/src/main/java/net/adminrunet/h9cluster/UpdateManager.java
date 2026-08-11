package net.adminrunet.h9cluster;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.Markwon;

/** Checks GitHub releases and installs a verified update APK. */
public final class UpdateManager {
    private static final String TAG = "H9ClusterUpdate";
    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/Arkasha18/H9-cluster/releases/latest";
    private static final String RELEASE_ASSET_PREFIX = "H9_Cluster_v";
    private static final String RELEASE_ASSET_SUFFIX = "_adminrunet_release.apk";
    private static final String UPDATE_DIRECTORY = "updates";
    private static final String UPDATE_FILE = "h9cluster_update.apk";
    private static final long MAX_APK_BYTES = 250L * 1024L * 1024L;
    private static final int PACKAGE_FLAGS =
            PackageManager.GET_SIGNING_CERTIFICATES
                    | PackageManager.GET_SIGNATURES;
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^v?(\\d+(?:\\.\\d+)*)(?:[-+].*)?$",
            Pattern.CASE_INSENSITIVE);

    private final Activity activity;
    private final Context appContext;
    private final String currentVersion;
    private final boolean updatesEnabled;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean updateCheckInProgress = new AtomicBoolean();

    private volatile boolean destroyed;
    private boolean waitingForInstallPermission;
    private AlertDialog updateDialog;
    private AlertDialog progressDialog;
    private AlertDialog permissionDialog;
    private ProgressBar progressBar;
    private TextView progressPercent;
    private File pendingApkFile;

    public UpdateManager(
            Activity activity,
            String currentVersion,
            boolean updatesEnabled) {
        this.activity = activity;
        this.appContext = activity.getApplicationContext();
        this.currentVersion = currentVersion;
        this.updatesEnabled = updatesEnabled;
    }

    public void checkForUpdates() {
        checkForUpdates(false);
    }

    public void checkForUpdatesManually() {
        checkForUpdates(true);
    }

    private void checkForUpdates(boolean userInitiated) {
        if (!updatesEnabled || destroyed) {
            return;
        }
        if (!isNetworkAvailable()) {
            showToast("Нет подключения к интернету");
            return;
        }
        if (!updateCheckInProgress.compareAndSet(false, true)) {
            if (userInitiated) {
                showToast("Проверка обновления уже выполняется");
            }
            return;
        }
        if (userInitiated) {
            showToast("Проверяем наличие обновления");
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = openConnection(new URL(GITHUB_API_URL), 10_000);
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "Release check failed with HTTP " + responseCode);
                    showToast("Не удалось проверить обновление");
                    return;
                }

                JSONObject release = new JSONObject(readResponse(connection));
                String latestVersion = release.getString("tag_name");
                if (!isNewerVersion(currentVersion, latestVersion)) {
                    if (userInitiated) {
                        showToast("Установлена актуальная версия");
                    }
                    return;
                }

                String downloadUrl = findReleaseApkUrl(
                        release.getJSONArray("assets"),
                        latestVersion);
                if (downloadUrl == null) {
                    Log.w(TAG, "Expected release APK was not found");
                    showToast("APK обновления не найден");
                    return;
                }

                String releaseNotes = release.optString(
                        "body",
                        "Нет описания изменений.");
                postToUi(() -> showUpdateDialog(
                        latestVersion,
                        releaseNotes,
                        downloadUrl));
            } catch (Exception error) {
                Log.e(TAG, "Update check failed", error);
                showToast("Ошибка проверки обновления");
            } finally {
                updateCheckInProgress.set(false);
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * Continues installation after the user returns from the system
     * "Install unknown apps" screen.
     */
    public void onResume() {
        if (!updatesEnabled || destroyed) {
            return;
        }

        File pendingFile = pendingApkFile;
        PermissionResumeAction action = permissionResumeAction(
                waitingForInstallPermission,
                appContext.getPackageManager().canRequestPackageInstalls(),
                pendingFile != null && pendingFile.isFile());
        if (action == PermissionResumeAction.NONE) {
            return;
        }

        waitingForInstallPermission = false;
        if (action == PermissionResumeAction.DENIED) {
            showToast("Разрешение на установку не выдано");
            deletePendingApk();
            return;
        }

        validatePendingApkAndInstall(pendingFile);
    }

    /** Releases Activity-bound work and UI. */
    public void destroy() {
        destroyed = true;
        waitingForInstallPermission = false;
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        dismissDialog(updateDialog);
        dismissDialog(progressDialog);
        dismissDialog(permissionDialog);
        updateDialog = null;
        progressDialog = null;
        permissionDialog = null;
        progressBar = null;
        progressPercent = null;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager manager = (ConnectivityManager)
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        NetworkCapabilities capabilities =
                manager.getNetworkCapabilities(manager.getActiveNetwork());
        return capabilities != null
                && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private void showUpdateDialog(
            String newVersion,
            String releaseNotes,
            String downloadUrl) {
        if (!isUiUsable()) {
            return;
        }

        TextView textView = new TextView(activity);
        float density = activity.getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density + 0.5f);
        textView.setPadding(padding, padding, padding, padding);
        textView.setTextSize(16);
        Markwon.create(activity).setMarkdown(
                textView,
                "Хотите установить новую версию?\n\nЧто нового:\n"
                        + releaseNotes);

        FrameLayout container = new FrameLayout(activity);
        container.addView(textView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        updateDialog = new AlertDialog.Builder(activity)
                .setTitle("Доступно обновление: " + newVersion)
                .setView(container)
                .setPositiveButton("Обновить", (dialog, which) -> {
                    if (!isNetworkAvailable()) {
                        showToast("Интернет отключился");
                        return;
                    }
                    showProgressDialog();
                    downloadAndValidateApk(downloadUrl);
                })
                .setNegativeButton("Позже", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .create();
        updateDialog.show();
    }

    private void showProgressDialog() {
        if (!isUiUsable()) {
            return;
        }
        View view = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_progress, null);
        progressBar = view.findViewById(R.id.progressBar);
        progressPercent = view.findViewById(R.id.tvProgressPercent);
        progressDialog = new AlertDialog.Builder(activity)
                .setTitle("Скачивание обновления...")
                .setView(view)
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void downloadAndValidateApk(String downloadUrl) {
        executor.execute(() -> {
            File partialFile = null;
            HttpURLConnection connection = null;
            try {
                if (!isTrustedDownloadUrl(downloadUrl)) {
                    throw new IOException("Untrusted APK URL");
                }

                File updateDirectory =
                        new File(appContext.getCacheDir(), UPDATE_DIRECTORY);
                if (!updateDirectory.isDirectory()
                        && !updateDirectory.mkdirs()) {
                    throw new IOException("Cannot create update directory");
                }

                File targetFile = new File(updateDirectory, UPDATE_FILE);
                partialFile = new File(updateDirectory, UPDATE_FILE + ".part");
                deleteFile(partialFile);
                deleteFile(targetFile);

                connection = openConnection(new URL(downloadUrl), 15_000);
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("APK download returned HTTP "
                            + responseCode);
                }

                long contentLength = connection.getContentLengthLong();
                if (contentLength > MAX_APK_BYTES) {
                    throw new IOException("APK exceeds maximum size");
                }

                try (InputStream input = connection.getInputStream();
                     FileOutputStream output =
                             new FileOutputStream(partialFile)) {
                    byte[] buffer = new byte[16 * 1024];
                    long total = 0;
                    int lastProgress = -1;
                    int bytesRead;
                    while ((bytesRead = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new IOException("Download interrupted");
                        }
                        total += bytesRead;
                        if (total > MAX_APK_BYTES) {
                            throw new IOException("APK exceeds maximum size");
                        }
                        output.write(buffer, 0, bytesRead);
                        if (contentLength > 0) {
                            int progress = (int) Math.min(
                                    100,
                                    total * 100 / contentLength);
                            if (progress != lastProgress) {
                                lastProgress = progress;
                                postProgress(progress);
                            }
                        }
                    }
                    output.getFD().sync();
                }

                if (!partialFile.renameTo(targetFile)) {
                    throw new IOException("Cannot finalize downloaded APK");
                }

                ApkValidationResult validationResult =
                        validateDownloadedApk(targetFile);
                if (validationResult != ApkValidationResult.OK) {
                    deleteFile(targetFile);
                    postToUi(() -> {
                        dismissProgressDialog();
                        showToast(validationResult.userMessage);
                    });
                    return;
                }

                File verifiedFile = targetFile;
                postToUi(() -> {
                    dismissProgressDialog();
                    pendingApkFile = verifiedFile;
                    requestPermissionOrInstall();
                });
            } catch (Exception error) {
                Log.e(TAG, "APK download failed", error);
                deleteFile(partialFile);
                postToUi(() -> {
                    dismissProgressDialog();
                    showToast("Ошибка при скачивании файла");
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void validatePendingApkAndInstall(File apkFile) {
        if (apkFile == null) {
            showToast("Файл обновления не найден");
            return;
        }
        executor.execute(() -> {
            ApkValidationResult result = validateDownloadedApk(apkFile);
            postToUi(() -> {
                if (result == ApkValidationResult.OK) {
                    installApk(apkFile);
                } else {
                    deletePendingApk();
                    showToast(result.userMessage);
                }
            });
        });
    }

    private void requestPermissionOrInstall() {
        if (!isUiUsable() || pendingApkFile == null) {
            return;
        }
        if (appContext.getPackageManager().canRequestPackageInstalls()) {
            installApk(pendingApkFile);
            return;
        }

        permissionDialog = new AlertDialog.Builder(activity)
                .setTitle("Требуется разрешение")
                .setMessage(
                        "Для обновления приложения разрешите установку "
                                + "из этого источника в настройках системы.")
                .setPositiveButton("В настройки", (dialog, which) -> {
                    waitingForInstallPermission = true;
                    Intent intent = new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                    try {
                        activity.startActivity(intent);
                    } catch (ActivityNotFoundException error) {
                        waitingForInstallPermission = false;
                        Log.e(TAG, "Install permission settings unavailable", error);
                        showToast("Настройки разрешения недоступны");
                    }
                })
                .setNegativeButton("Отмена", (dialog, which) -> {
                    waitingForInstallPermission = false;
                    deletePendingApk();
                    dialog.dismiss();
                })
                .setCancelable(false)
                .create();
        permissionDialog.show();
    }

    private void installApk(File apkFile) {
        if (!isUiUsable() || apkFile == null || !apkFile.isFile()) {
            return;
        }
        Uri apkUri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileprovider",
                apkFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(
                apkUri,
                "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(intent);
            pendingApkFile = null;
        } catch (ActivityNotFoundException error) {
            Log.e(TAG, "Package installer unavailable", error);
            showToast("Установщик APK недоступен");
        }
    }

    private ApkValidationResult validateDownloadedApk(File apkFile) {
        if (apkFile == null || !apkFile.isFile()) {
            return ApkValidationResult.UNREADABLE;
        }
        PackageManager packageManager = appContext.getPackageManager();
        try {
            PackageInfo installed = packageManager.getPackageInfo(
                    appContext.getPackageName(),
                    PACKAGE_FLAGS);
            PackageInfo candidate = packageManager.getPackageArchiveInfo(
                    apkFile.getAbsolutePath(),
                    PACKAGE_FLAGS);
            return validateApkIdentity(
                    packageIdentity(installed),
                    packageIdentity(candidate));
        } catch (PackageManager.NameNotFoundException
                 | NoSuchAlgorithmException error) {
            Log.e(TAG, "Cannot validate APK identity", error);
            return ApkValidationResult.UNREADABLE;
        }
    }

    private static PackageIdentity packageIdentity(PackageInfo packageInfo)
            throws NoSuchAlgorithmException {
        if (packageInfo == null) {
            return null;
        }
        Signature[] signatures = null;
        if (packageInfo.signingInfo != null) {
            signatures = packageInfo.signingInfo.getApkContentsSigners();
        }
        if ((signatures == null || signatures.length == 0)
                && packageInfo.signatures != null) {
            signatures = packageInfo.signatures;
        }
        if (signatures == null || signatures.length == 0) {
            return null;
        }

        Set<String> signerDigests = new HashSet<>();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Signature signature : signatures) {
            signerDigests.add(toHex(digest.digest(signature.toByteArray())));
            digest.reset();
        }
        return new PackageIdentity(
                packageInfo.packageName,
                packageInfo.getLongVersionCode(),
                signerDigests);
    }

    static ApkValidationResult validateApkIdentity(
            PackageIdentity installed,
            PackageIdentity candidate) {
        if (installed == null || candidate == null) {
            return ApkValidationResult.UNREADABLE;
        }
        if (!installed.packageName.equals(candidate.packageName)) {
            return ApkValidationResult.WRONG_PACKAGE;
        }
        if (candidate.versionCode <= installed.versionCode) {
            return ApkValidationResult.NOT_NEWER;
        }
        if (!installed.signerDigests.equals(candidate.signerDigests)) {
            return ApkValidationResult.SIGNATURE_MISMATCH;
        }
        return ApkValidationResult.OK;
    }

    static boolean isNewerVersion(
            String currentVersion,
            String latestVersion) {
        long[] currentParts = parseVersion(currentVersion);
        long[] latestParts = parseVersion(latestVersion);
        if (currentParts == null || latestParts == null) {
            return false;
        }
        int count = Math.max(currentParts.length, latestParts.length);
        for (int index = 0; index < count; index++) {
            long current = index < currentParts.length
                    ? currentParts[index]
                    : 0;
            long latest = index < latestParts.length
                    ? latestParts[index]
                    : 0;
            if (latest > current) {
                return true;
            }
            if (latest < current) {
                return false;
            }
        }
        return false;
    }

    static String findReleaseApkUrl(
            JSONArray assets,
            String latestVersion) {
        String version = normalizedNumericVersion(latestVersion);
        if (version == null) {
            return null;
        }
        String expectedName = RELEASE_ASSET_PREFIX
                + version
                + RELEASE_ASSET_SUFFIX;
        for (int index = 0; index < assets.length(); index++) {
            JSONObject asset = assets.optJSONObject(index);
            if (asset == null
                    || !expectedName.equals(asset.optString("name"))) {
                continue;
            }
            String url = asset.optString("browser_download_url", null);
            return isTrustedDownloadUrl(url) ? url : null;
        }
        return null;
    }

    static boolean isTrustedDownloadUrl(String downloadUrl) {
        if (downloadUrl == null) {
            return false;
        }
        try {
            URL url = new URL(downloadUrl);
            return "https".equalsIgnoreCase(url.getProtocol())
                    && "github.com".equalsIgnoreCase(url.getHost());
        } catch (Exception ignored) {
            return false;
        }
    }

    static PermissionResumeAction permissionResumeAction(
            boolean waiting,
            boolean permissionGranted,
            boolean pendingFileExists) {
        if (!waiting) {
            return PermissionResumeAction.NONE;
        }
        if (permissionGranted && pendingFileExists) {
            return PermissionResumeAction.INSTALL;
        }
        return PermissionResumeAction.DENIED;
    }

    private static long[] parseVersion(String version) {
        String normalized = normalizedNumericVersion(version);
        if (normalized == null) {
            return null;
        }
        String[] components = normalized.split("\\.");
        long[] result = new long[components.length];
        try {
            for (int index = 0; index < components.length; index++) {
                result[index] = Long.parseLong(components[index]);
            }
            return result;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizedNumericVersion(String version) {
        if (version == null) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(version.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static HttpURLConnection openConnection(
            URL url,
            int timeoutMs) throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty(
                "User-Agent",
                "H9-Cluster-Android-Updater");
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setInstanceFollowRedirects(true);
        return connection;
    }

    private static String readResponse(HttpURLConnection connection)
            throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        connection.getInputStream(),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private void postProgress(int progress) {
        postToUi(() -> {
            if (progressBar != null && progressPercent != null) {
                progressBar.setProgress(progress);
                progressPercent.setText("Загрузка: " + progress + "%");
            }
        });
    }

    private void showToast(String message) {
        mainHandler.post(() -> {
            if (!destroyed) {
                Toast.makeText(
                        appContext,
                        message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void postToUi(Runnable action) {
        mainHandler.post(() -> {
            if (isUiUsable()) {
                action.run();
            }
        });
    }

    private boolean isUiUsable() {
        return !destroyed
                && !activity.isFinishing()
                && !activity.isDestroyed();
    }

    private void dismissProgressDialog() {
        dismissDialog(progressDialog);
        progressDialog = null;
        progressBar = null;
        progressPercent = null;
    }

    private static void dismissDialog(AlertDialog dialog) {
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        try {
            dialog.dismiss();
        } catch (IllegalArgumentException error) {
            Log.w(TAG, "Dialog window is already detached", error);
        }
    }

    private void deletePendingApk() {
        deleteFile(pendingApkFile);
        pendingApkFile = null;
    }

    private static void deleteFile(File file) {
        if (file != null && file.exists() && !file.delete()) {
            Log.w(TAG, "Cannot delete " + file);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }

    static final class PackageIdentity {
        final String packageName;
        final long versionCode;
        final Set<String> signerDigests;

        PackageIdentity(
                String packageName,
                long versionCode,
                Set<String> signerDigests) {
            this.packageName = packageName;
            this.versionCode = versionCode;
            this.signerDigests = signerDigests == null
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(
                            new HashSet<>(signerDigests));
        }

        PackageIdentity(
                String packageName,
                long versionCode,
                String... signerDigests) {
            this(
                    packageName,
                    versionCode,
                    new HashSet<>(Arrays.asList(signerDigests)));
        }
    }

    enum ApkValidationResult {
        OK(""),
        UNREADABLE("Не удалось проверить APK обновления"),
        WRONG_PACKAGE("APK предназначен для другого приложения"),
        NOT_NEWER("Версия APK не новее установленной"),
        SIGNATURE_MISMATCH("Подпись APK не совпадает с установленным приложением");

        final String userMessage;

        ApkValidationResult(String userMessage) {
            this.userMessage = userMessage;
        }
    }

    enum PermissionResumeAction {
        NONE,
        INSTALL,
        DENIED
    }
}
