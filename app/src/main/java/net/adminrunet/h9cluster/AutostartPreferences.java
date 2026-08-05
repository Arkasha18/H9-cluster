package net.adminrunet.h9cluster;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers that the user left the application on purpose. Boot startup stays
 * quiet until the application is opened manually again.
 */
public final class AutostartPreferences {
    private static final String KEY_AUTOSTART_SUSPENDED = "autostart_suspended";

    private AutostartPreferences() {
    }

    public static boolean isAutostartSuspended(Context context) {
        return preferences(context).getBoolean(KEY_AUTOSTART_SUSPENDED, false);
    }

    public static void setAutostartSuspended(Context context, boolean suspended) {
        preferences(context)
                .edit()
                .putBoolean(KEY_AUTOSTART_SUSPENDED, suspended)
                .apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(
                SkinPreferences.PREFERENCES_NAME,
                Context.MODE_PRIVATE);
    }
}
