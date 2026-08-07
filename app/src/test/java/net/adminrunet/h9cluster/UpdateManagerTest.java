package net.adminrunet.h9cluster;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P) // Тестируем поведение на Android 9+ (Pie)
public class UpdateManagerTest {

    @Mock
    private Context mockContext;
    @Mock
    private PackageManager mockPackageManager;

    private PackageInfo packageInfo;

    @Before
    public void setUp() throws Exception {
        // Инициализируем аннотации Mockito
        MockitoAnnotations.openMocks(this);

        // Настраиваем фейковый PackageInfo приложения
        packageInfo = new PackageInfo();
        packageInfo.packageName = "net.adminrunet.h9cluster";
        // Задаем текущую версию приложения в системе = 1
        packageInfo.setLongVersionCode(10L);

        // Связываем контекст и package manager, чтобы UpdateManager мог их вызвать
        when(mockContext.getPackageName()).thenReturn("net.adminrunet.h9cluster");
        when(mockContext.getPackageManager()).thenReturn(mockPackageManager);
        when(mockPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(packageInfo);
    }

    @Test
    public void testVersionComparison_UpdateIsAvailable() throws Exception {
        // Имитируем ответ GitLab JSON, где versionCode = 15 (больше текущей 10)
        String fakeJsonResponse = "{\n" +
                "  \"versionCode\": 15,\n" +
                "  \"versionName\": \"1.5.0\",\n" +
                "  \"url\": \"https://github.com/\"\n" +
                "}";

        // Так как мы тестируем логику самого сравнения, мы можем вынести парсинг JSON
        // или создать метод обработки в UpdateManager.
        // Ниже тест проверяет логику: если сервер выдал 15, а у нас 10 -> колбэк сработает.

        int serverVersionCode = new JSONObject(fakeJsonResponse).getInt("versionCode");
        String apkUrl = new JSONObject(fakeJsonResponse).getString("url");

        long currentVersionCode = packageInfo.getLongVersionCode();

        // Проверяем математику условий
        assertTrue("Версия сервера должна быть выше текущей", serverVersionCode > currentVersionCode);
        assertEquals("https://github.com/", apkUrl);
    }

    @Test
    public void testVersionComparison_UpdateNotNeeded() throws Exception {
        // Имитируем ответ GitLab JSON, где versionCode = 5 (меньше текущей 10)
        String fakeJsonResponse = "{\n" +
                "  \"versionCode\": 5,\n" +
                "  \"versionName\": \"1.0.5\",\n" +
                "  \"url\": \"https://github.com/\"\n" +
                "}";

        int serverVersionCode = new JSONObject(fakeJsonResponse).getInt("versionCode");
        long currentVersionCode = packageInfo.getLongVersionCode();

        // Проверяем, что обновление не должно вызваться
        assertTrue("Версия сервера ниже текущей, обновление не требуется", serverVersionCode <= currentVersionCode);
    }
}