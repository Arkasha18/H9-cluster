package net.adminrunet.h9cluster;

import net.adminrunet.h9cluster.skins.SkinRegistry;
import net.adminrunet.h9cluster.skins.SpeedDisplayMode;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the skin used by manual launches and boot-time display startup. */
public final class SkinPreferences {
    static final String PREFERENCES_NAME = "cluster_settings";
    private static final String KEY_SELECTED_SKIN = "selected_skin";
    private static final String KEY_SPEED_DISPLAY_MODE = "speed_display_mode";
    private static final String KEY_FUEL_TANK_CAPACITY = "fuel_tank_capacity_liters";
    private static final String KEY_GEAR_NUMBER_VISIBLE = "gear_number_visible";

    private SkinPreferences() {
    }

    public static String getSelectedSkin(Context context) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        String defaultSkin = BuildConfig.DEMO_MODE
                ? SkinRegistry.ION_AURORA
                : SkinRegistry.getDefaultId();
        String selected = preferences.getString(
                KEY_SELECTED_SKIN,
                defaultSkin);
        return SkinRegistry.normalize(selected);
    }

    public static void setSelectedSkin(Context context, String skin) {
        String safeSkin = SkinRegistry.normalize(skin);
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SELECTED_SKIN, safeSkin)
                .apply();
    }

    /** Tank size is shared by every skin; existing installations keep 80 liters. */
    public static int getFuelTankCapacityLiters(Context context) {
        return FuelTankCapacity.normalize(context.getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE).getInt(
                KEY_FUEL_TANK_CAPACITY, FuelTankCapacity.DEFAULT_LITERS));
    }

    public static void setFuelTankCapacityLiters(Context context, int capacity) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_FUEL_TANK_CAPACITY, FuelTankCapacity.normalize(capacity))
                .apply();
    }

    /** Keep the existing companion numeral until the user hides it for their vehicle. */
    public static boolean isGearNumberVisible(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_GEAR_NUMBER_VISIBLE, true);
    }

    public static void setGearNumberVisible(Context context, boolean visible) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_GEAR_NUMBER_VISIBLE, visible)
                .apply();
    }

    /** The speedometer reading mode shared by every skin. */
    public static SpeedDisplayMode getSpeedDisplayMode(Context context) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        return SpeedDisplayMode.fromKey(
                preferences.getString(KEY_SPEED_DISPLAY_MODE, null));
    }

    public static void setSpeedDisplayMode(
            Context context,
            SpeedDisplayMode mode) {
        SpeedDisplayMode safeMode =
                mode == null ? SpeedDisplayMode.REAL : mode;
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SPEED_DISPLAY_MODE, safeMode.key())
                .apply();
    }
}
