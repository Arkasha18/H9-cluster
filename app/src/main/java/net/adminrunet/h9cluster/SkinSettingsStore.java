package net.adminrunet.h9cluster;

import net.adminrunet.h9cluster.skins.SkinRegistry;
import net.adminrunet.h9cluster.skins.SkinSettings;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

/** Namespaced persistence for settings owned by individual dashboard skins. */
final class SkinSettingsStore {
    private static final String PREFERENCES_NAME = "skin_settings";

    private SkinSettingsStore() {
    }

    static SkinSettings load(Context context, String skinId) {
        String normalizedId = SkinRegistry.normalize(skinId);
        String prefix = prefix(normalizedId);
        Map<String, Object> values = new LinkedHashMap<>();
        SharedPreferences preferences =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                values.put(
                        entry.getKey().substring(prefix.length()),
                        entry.getValue());
            }
        }
        return SkinRegistry.normalizeSettings(
                normalizedId,
                SkinSettings.fromValues(values));
    }

    static void save(Context context, String skinId, SkinSettings settings) {
        String normalizedId = SkinRegistry.normalize(skinId);
        String prefix = prefix(normalizedId);
        SkinSettings normalizedSettings =
                SkinRegistry.normalizeSettings(normalizedId, settings);
        SharedPreferences preferences =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(prefix)) {
                editor.remove(key);
            }
        }
        for (Map.Entry<String, Object> entry
                : normalizedSettings.asMap().entrySet()) {
            put(editor, prefix + entry.getKey(), entry.getValue());
        }
        editor.apply();
    }

    private static void put(
            SharedPreferences.Editor editor,
            String key,
            Object value) {
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        }
    }

    private static String prefix(String skinId) {
        return skinId + ":";
    }
}
