package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;

import net.adminrunet.h9cluster.skins.SkinRegistry;
import net.adminrunet.h9cluster.skins.SpeedDisplayMode;

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
    public void tankDefaultsToEightyAndPersistsIndependentlyOfSkin() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(SkinPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();

        assertEquals(80, SkinPreferences.getFuelTankCapacityLiters(context));
        SkinPreferences.setFuelTankCapacityLiters(context, 60);
        for (SkinRegistry.Definition skin : SkinRegistry.getDefinitions()) {
            SkinPreferences.setSelectedSkin(context, skin.id);
            assertEquals(60, SkinPreferences.getFuelTankCapacityLiters(context));
        }
        SkinPreferences.setFuelTankCapacityLiters(context, 80);
        assertEquals(80, SkinPreferences.getFuelTankCapacityLiters(context));
        SkinPreferences.setFuelTankCapacityLiters(context, 0);
        assertEquals(80, SkinPreferences.getFuelTankCapacityLiters(context));
        context.getSharedPreferences(SkinPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().putInt("fuel_tank_capacity_liters", 100).commit();
        assertEquals(80, SkinPreferences.getFuelTankCapacityLiters(context));
    }

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

    @Test
    public void freshInstallShowsTheRealSpeedUntilConfigured() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(
                        SkinPreferences.PREFERENCES_NAME,
                        Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        assertEquals(
                SpeedDisplayMode.REAL,
                SkinPreferences.getSpeedDisplayMode(context));
    }

    @Test
    public void speedDisplayModePersistsAcrossReads() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(
                        SkinPreferences.PREFERENCES_NAME,
                        Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        SkinPreferences.setSpeedDisplayMode(
                context,
                SpeedDisplayMode.FACTORY_PLUS_5_KPH);
        assertEquals(
                SpeedDisplayMode.FACTORY_PLUS_5_KPH,
                SkinPreferences.getSpeedDisplayMode(context));

        SkinPreferences.setSpeedDisplayMode(context, null);
        assertEquals(
                SpeedDisplayMode.REAL,
                SkinPreferences.getSpeedDisplayMode(context));
    }
}
