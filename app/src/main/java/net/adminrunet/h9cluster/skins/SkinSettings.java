package net.adminrunet.h9cluster.skins;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, transport-neutral settings owned by one dashboard skin.
 *
 * <p>The shared application persists and previews these primitive values, but
 * only the owning skin assigns meaning to their keys.</p>
 */
public final class SkinSettings {
    private static final SkinSettings EMPTY =
            new SkinSettings(Collections.<String, Object>emptyMap());

    private final Map<String, Object> values;

    private SkinSettings(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static SkinSettings empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SkinSettings fromValues(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return empty();
        }
        Builder builder = builder();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            if (isValidKey(entry.getKey()) && isSupportedValue(entry.getValue())) {
                builder.putValue(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    public boolean getBoolean(String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    public int getInt(String key, int fallback) {
        Object value = values.get(key);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    public long getLong(String key, long fallback) {
        Object value = values.get(key);
        return value instanceof Long ? (Long) value : fallback;
    }

    public float getFloat(String key, float fallback) {
        Object value = values.get(key);
        return value instanceof Float ? (Float) value : fallback;
    }

    public String getString(String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    public Map<String, Object> asMap() {
        return values;
    }

    public Builder buildUpon() {
        return new Builder(values);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SkinSettings)) {
            return false;
        }
        SkinSettings that = (SkinSettings) other;
        return values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    private static boolean isValidKey(String key) {
        if (key == null || key.length() == 0 || key.length() > 80) {
            return false;
        }
        for (int index = 0; index < key.length(); index++) {
            char value = key.charAt(index);
            boolean valid = value >= 'a' && value <= 'z'
                    || value >= '0' && value <= '9'
                    || value == '_'
                    || value == '.'
                    || value == '-';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSupportedValue(Object value) {
        return value instanceof Boolean
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof String;
    }

    public static final class Builder {
        private final Map<String, Object> values;

        private Builder() {
            values = new LinkedHashMap<>();
        }

        private Builder(Map<String, Object> source) {
            values = new LinkedHashMap<>(source);
        }

        public Builder putBoolean(String key, boolean value) {
            return putValue(key, Boolean.valueOf(value));
        }

        public Builder putInt(String key, int value) {
            return putValue(key, Integer.valueOf(value));
        }

        public Builder putLong(String key, long value) {
            return putValue(key, Long.valueOf(value));
        }

        public Builder putFloat(String key, float value) {
            return putValue(key, Float.valueOf(value));
        }

        public Builder putString(String key, String value) {
            return putValue(key, Objects.requireNonNull(value, "value"));
        }

        public Builder remove(String key) {
            values.remove(requireValidKey(key));
            return this;
        }

        public SkinSettings build() {
            if (values.isEmpty()) {
                return empty();
            }
            return new SkinSettings(new LinkedHashMap<>(values));
        }

        private Builder putValue(String key, Object value) {
            values.put(requireValidKey(key), value);
            return this;
        }

        private static String requireValidKey(String key) {
            if (!isValidKey(key)) {
                throw new IllegalArgumentException("Invalid skin setting key: " + key);
            }
            return key;
        }
    }
}
