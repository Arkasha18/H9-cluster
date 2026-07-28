package net.adminrunet.h9cluster;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads and atomically stores the selected skin with one visibility snapshot. */
public final class ClusterPreferences {
    private static final String PREFERENCES_NAME = "cluster_settings";
    private static final String KEY_SELECTED_SKIN = "selected_skin";
    private static final String KEY_CLASSIC_CUSTOM_VISIBILITY =
            "classic_custom_visibility_mask";

    public static final class Snapshot {
        public final String skin;
        public final BlockVisibility visibility;

        Snapshot(String skin, BlockVisibility visibility) {
            this.skin = skin;
            this.visibility = visibility;
        }
    }

    private ClusterPreferences() {
    }

    public static Snapshot load(Context context) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        return fromValues(preferences.getAll());
    }

    public static void save(
            Context context,
            String skin,
            BlockVisibility visibility) {
        SharedPreferences.Editor editor = context
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit();
        for (Map.Entry<String, Object> entry : toValues(skin, visibility).entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof Long) {
                editor.putLong(entry.getKey(), (Long) value);
            } else {
                editor.putString(entry.getKey(), (String) value);
            }
        }
        editor.apply();
    }

    static Snapshot fromValues(Map<String, ?> values) {
        Object storedSkin = values == null ? null : values.get(KEY_SELECTED_SKIN);
        String skin = normalizeSkin(storedSkin instanceof String ? (String) storedSkin : null);
        Object storedVisibility =
                values == null ? null : values.get(KEY_CLASSIC_CUSTOM_VISIBILITY);
        return new Snapshot(
                skin,
                BlockVisibility.fromStoredValue(storedVisibility));
    }

    static Map<String, Object> toValues(
            String skin,
            BlockVisibility visibility) {
        BlockVisibility safeVisibility =
                visibility == null ? BlockVisibility.allVisible() : visibility;
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(KEY_SELECTED_SKIN, normalizeSkin(skin));
        values.put(KEY_CLASSIC_CUSTOM_VISIBILITY, safeVisibility.toMask());
        return Collections.unmodifiableMap(values);
    }

    private static String normalizeSkin(String skin) {
        return SkinPreferences.normalize(skin);
    }
}
