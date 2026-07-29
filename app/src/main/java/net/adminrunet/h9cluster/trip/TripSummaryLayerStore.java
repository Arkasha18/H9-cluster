package net.adminrunet.h9cluster.trip;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the engine-off renderer latch across Activity and process recreation. */
public final class TripSummaryLayerStore {
    private static final String PREFERENCES = "trip_summary_layers";
    private static final String KEY_RENDERER_SUPPRESSED =
            "renderer_suppressed";

    private final SharedPreferences preferences;

    public TripSummaryLayerStore(Context context) {
        preferences = context
                .getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public boolean isRendererSuppressed() {
        return preferences.getBoolean(KEY_RENDERER_SUPPRESSED, false);
    }

    public void setRendererSuppressed(boolean suppressed) {
        preferences.edit()
                .putBoolean(KEY_RENDERER_SUPPRESSED, suppressed)
                .apply();
    }
}
