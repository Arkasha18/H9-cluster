package net.adminrunet.h9cluster;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.noties.markwon.Markwon;

public class UpdateManager {

    private static final String GITHUB_API_URL = "https://api.github.com/repos/aevdokimov1976-alex/H9-cluster/releases/latest";
    private final Context context;
    private final String currentVersion;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AlertDialog progressDialog;
    private ProgressBar progressBar;
    private TextView tvProgressPercent;
    private File downloadedApkFile;

    public UpdateManager(Context context, String currentVersion) {
        this.context = context;
        this.currentVersion = currentVersion;
    }

    // Проверка наличия интернета
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));    }

    // Проверка обновления
    public void checkForUpdates() {
        // Защита: Если интернета нет, прерываем операцию
        if (!isNetworkAvailable()) {
            showToast("Нет подключения к интернету");
            return;
        }

        executor.execute(() -> {
            try {
                URL url = new URL(GITHUB_API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Android-App-Updater");
                // ВАЖНО: Добавляем авторизацию для приватного репозитория
                //connection.setRequestProperty("Authorization", "Bearer Токен_github_pat_...");
                connection.setConnectTimeout(10000); // Таймаут подключения 10 сек
                connection.setReadTimeout(10000);

                if (connection.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject jsonObject = new JSONObject(response.toString());
                    String latestVersion = jsonObject.getString("tag_name");
                    String releaseNotes = jsonObject.optString("body", "Нет описания изменений.");

                    if (!currentVersion.equals(latestVersion)) {
                        JSONArray assets = jsonObject.getJSONArray("assets");
                        String apkDownloadUrl = null;

                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.getString("name");
                            if (name.endsWith(".apk")) {
                                apkDownloadUrl = asset.getString("browser_download_url");
                                break;
                            }
                        }

                        if (apkDownloadUrl != null) {
                            String finalApkUrl = apkDownloadUrl;
                            mainHandler.post(() -> showUpdateDialog(latestVersion, releaseNotes, finalApkUrl));
                        }
                    }
                }
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                showToast("Ошибка проверки обновления");
            }
        });
    }

    // Диалоговое окно доступного обновления
    private void showUpdateDialog(String newVersion, String releaseNotes, String downloadUrl) {

        TextView textView = new TextView(context);

        int paddingInDp = 20;
        final float scale = context.getResources().getDisplayMetrics().density;
        int paddingInPx = (int) (paddingInDp * scale + 0.5f);
        textView.setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx);
        textView.setTextSize(16);

        final Markwon markwon = Markwon.create(context);
        markwon.setMarkdown(textView,"Хотите установить новую версию?\n\nЧто нового:\n" + releaseNotes);

        FrameLayout container = new FrameLayout(context);
        container.addView(textView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        new AlertDialog.Builder(context)
                .setTitle("Доступно обновление: " + newVersion)
                .setView(container)
                .setPositiveButton("Обновить", (dialog, which) -> {
                    if (!isNetworkAvailable()) {
                        showToast("Интернет отключился");
                        return;
                    }
                    showProgressDialog();
                    downloadAndInstallApk(downloadUrl);
                })
                .setNegativeButton("Позже", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    // Прогресс загрузки файла
    private void showProgressDialog() {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_progress, null);
        progressBar = view.findViewById(R.id.progressBar);
        tvProgressPercent = view.findViewById(R.id.tvProgressPercent);

        progressDialog = new AlertDialog.Builder(context)
                .setTitle("Скачивание обновления...")
                .setView(view)
                .setCancelable(false)
                .create();

        progressDialog.show();
    }

    // Загрузка обновления
    private void downloadAndInstallApk(String downloadUrl) {
        executor.execute(() -> {
            try {
                URL url = new URL(downloadUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.connect();

                int fileLength = connection.getContentLength();
                downloadedApkFile = new File(context.getExternalCacheDir(), "h9cluster_update.apk");

                InputStream input = connection.getInputStream();
                FileOutputStream output = new FileOutputStream(downloadedApkFile);

                byte[] buffer = new byte[4096];
                long total = 0;
                int bytesRead;

                while ((bytesRead = input.read(buffer)) != -1) {
                    total += bytesRead;
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        mainHandler.post(() -> {
                            if (progressBar != null && tvProgressPercent != null) {
                                progressBar.setProgress(progress);
                                tvProgressPercent.setText("Загрузка: " + progress + "%");
                            }
                        });
                    }
                    output.write(buffer, 0, bytesRead);
                }

                output.close();
                input.close();
                connection.disconnect();

                mainHandler.post(() -> {
                    dismissProgressDialog();
                    // Переходим к проверке разрешений и установке
                    checkPermissionAndInstall();
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    dismissProgressDialog();
                    showToast("Ошибка при скачивании файла");
                });
            }
        });
    }

    // Проверка разрешения на установку (Android 8.0+)
    private void checkPermissionAndInstall() {
        if (!context.getPackageManager().canRequestPackageInstalls()) {
            // Разрешения нет -> показываем диалог, объясняющий зачем нужен переход в настройки
            new AlertDialog.Builder(context)
                    .setTitle("Требуется разрешение")
                    .setMessage("Для обновления приложения необходимо разрешить установку из этого источника в настройках системы.")
                    .setPositiveButton("В настройки", (dialog, which) -> {
                        // Открываем страницу настроек "Установка неизвестных приложений" для нашей программы
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                        intent.setData(Uri.parse("package:" + context.getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    })
                    .setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss())
                    .setCancelable(false)
                    .show();
        } else {
            // Разрешение уже есть -> устанавливаем
            installApk(downloadedApkFile);
        }
    }

    // Установка обновления
    private void installApk(File apkFile) {
        if (!apkFile.exists()) return;

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apkFile);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(intent);
    }

    // Вспомогательный метод для безопасного вывода сообщений из любого потока
    private void showToast(final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    // Вспомогательный метод для безопасного закрытия диалога загрузки
    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            try {
                progressDialog.dismiss();
            } catch (IllegalArgumentException e) {
                // Перестраховка на случай, если Activity была уничтожена в процессе
                e.printStackTrace();
            }
        }
    }
}
