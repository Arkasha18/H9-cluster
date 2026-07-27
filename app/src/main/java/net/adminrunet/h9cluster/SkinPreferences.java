package net.adminrunet.h9cluster;

import android.content.Context;
/** Persists the skin used by manual launches and boot-time display startup. */
public final class SkinPreferences {
    public static final String SKIN_CLASSIC = "classic";
    public static final String SKIN_HORIZON = "horizon";

    private SkinPreferences() {
    }

    public static String getSelectedSkin(Context context) {
        return ClusterPreferences.load(context).skin;
    }

    public static void setSelectedSkin(Context context, String skin) {
        ClusterPreferences.Snapshot current = ClusterPreferences.load(context);
        ClusterPreferences.save(context, skin, current.visibility);
    }
}
