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
