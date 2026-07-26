package net.adminrunet.h9cluster;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the skin used by manual launches and boot-time display startup. */
public final class SkinPreferences {
    public static final String SKIN_CLASSIC = "classic";
    public static final String SKIN_HORIZON = "horizon";

    private static final String PREFERENCES_NAME = "cluster_settings";
    private static final String KEY_SELECTED_SKIN = "selected_skin";

    private SkinPreferences() {
    }

    public static String getSelectedSkin(Context context) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        String selected = preferences.getString(KEY_SELECTED_SKIN, SKIN_CLASSIC);
        if (SKIN_HORIZON.equals(selected)) {
            return SKIN_HORIZON;
        }
        return SKIN_CLASSIC;
    }

    public static void setSelectedSkin(Context context, String skin) {
        String safeSkin = SKIN_HORIZON.equals(skin) ? SKIN_HORIZON : SKIN_CLASSIC;
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SELECTED_SKIN, safeSkin)
                .apply();
    }
}
