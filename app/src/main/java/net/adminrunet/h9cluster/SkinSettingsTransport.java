package net.adminrunet.h9cluster;

import net.adminrunet.h9cluster.skins.SkinSettings;

import android.os.Bundle;

import java.util.LinkedHashMap;
import java.util.Map;

/** Primitive-only transport for unsaved settings sent to Display ID 2. */
final class SkinSettingsTransport {
    private SkinSettingsTransport() {
    }

    static Bundle toBundle(SkinSettings settings) {
        Bundle bundle = new Bundle();
        if (settings == null) {
            return bundle;
        }
        for (Map.Entry<String, Object> entry : settings.asMap().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                bundle.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof Integer) {
                bundle.putInt(entry.getKey(), (Integer) value);
            } else if (value instanceof Long) {
                bundle.putLong(entry.getKey(), (Long) value);
            } else if (value instanceof Float) {
                bundle.putFloat(entry.getKey(), (Float) value);
            } else if (value instanceof String) {
                bundle.putString(entry.getKey(), (String) value);
            }
        }
        return bundle;
    }

    static SkinSettings fromBundle(Bundle bundle) {
        if (bundle == null || bundle.isEmpty()) {
            return SkinSettings.empty();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : bundle.keySet()) {
            values.put(key, bundle.get(key));
        }
        return SkinSettings.fromValues(values);
    }
}
