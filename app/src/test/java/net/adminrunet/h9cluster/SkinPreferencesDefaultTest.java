package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;

import net.adminrunet.h9cluster.skins.SkinRegistry;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class SkinPreferencesDefaultTest {
    @Test
    public void freshDemoInstallStartsWithIonAurora() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(
                        SkinPreferences.PREFERENCES_NAME,
                        Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        assertEquals(
                BuildConfig.DEMO_MODE
                        ? SkinRegistry.ION_AURORA
                        : SkinRegistry.CLASSIC,
                SkinPreferences.getSelectedSkin(context));
    }
}
