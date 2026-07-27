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
            } else {
                editor.putString(entry.getKey(), (String) value);
            }
        }
        editor.apply();
    }

    static Snapshot fromValues(Map<String, ?> values) {
        Object storedSkin = values == null ? null : values.get(KEY_SELECTED_SKIN);
        String skin = normalizeSkin(storedSkin instanceof String ? (String) storedSkin : null);
        return new Snapshot(skin, BlockVisibility.from(values));
    }

    static Map<String, Object> toValues(
            String skin,
            BlockVisibility visibility) {
        BlockVisibility safeVisibility =
                visibility == null ? BlockVisibility.allVisible() : visibility;
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(KEY_SELECTED_SKIN, normalizeSkin(skin));
        values.putAll(safeVisibility.asMap());
        return Collections.unmodifiableMap(values);
    }

    private static String normalizeSkin(String skin) {
        return SkinPreferences.SKIN_HORIZON.equals(skin)
                ? SkinPreferences.SKIN_HORIZON
                : SkinPreferences.SKIN_CLASSIC;
    }
}
