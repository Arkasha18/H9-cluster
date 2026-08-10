package net.adminrunet.h9cluster.navigation;

import net.adminrunet.h9cluster.skins.SkinSettings;

/** Primitive-only navigation choice stored independently for each skin. */
public final class NavigationSettings {
    public static final String KEY_COMPONENT = "navigation_component";

    private static final int MAX_COMPONENT_LENGTH = 300;

    private NavigationSettings() {
    }

    public static String selectedComponent(SkinSettings settings) {
        if (settings == null) {
            return "";
        }
        return normalizeComponent(settings.getString(KEY_COMPONENT, ""));
    }

    public static String normalizeComponent(String component) {
        if (component == null) {
            return "";
        }
        String normalized = component.trim();
        if (normalized.length() == 0) {
            return "";
        }
        if (normalized.length() > MAX_COMPONENT_LENGTH) {
            return "";
        }
        int separator = normalized.indexOf('/');
        if (separator <= 0 || separator >= normalized.length() - 1) {
            return "";
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isWhitespace(normalized.charAt(index))) {
                return "";
            }
        }
        return normalized;
    }

    public static SkinSettings navigationOnly(String component) {
        return SkinSettings.builder()
                .putString(KEY_COMPONENT, normalizeComponent(component))
                .build();
    }
}
