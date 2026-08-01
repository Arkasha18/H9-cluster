package net.adminrunet.h9cluster;

import net.adminrunet.h9cluster.skins.SkinRegistry;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the skin used by manual launches and boot-time display startup. */
public final class SkinPreferences {
    private static final String PREFERENCES_NAME = "cluster_settings";
    private static final String KEY_SELECTED_SKIN = "selected_skin";
    private static final String KEY_SWAP_PRIMARY_GAUGES = "swap_primary_gauges";

    private SkinPreferences() {
    }

    public static String getSelectedSkin(Context context) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        String selected = preferences.getString(
                KEY_SELECTED_SKIN,
                SkinRegistry.getDefaultId());
        return SkinRegistry.normalize(selected);
    }

    public static void setSelectedSkin(Context context, String skin) {
        String safeSkin = SkinRegistry.normalize(skin);
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SELECTED_SKIN, safeSkin)
                .apply();
    }

    public static boolean getSwapPrimaryGauges(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SWAP_PRIMARY_GAUGES, false);
    }

    public static void setSwapPrimaryGauges(Context context, boolean swap) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SWAP_PRIMARY_GAUGES, swap)
                .apply();
    }
}
