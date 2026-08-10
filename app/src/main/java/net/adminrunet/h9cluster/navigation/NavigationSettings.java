package net.adminrunet.h9cluster.navigation;

import net.adminrunet.h9cluster.skins.SkinSettings;

/** Primitive-only navigation choice stored independently for each skin. */
public final class NavigationSettings {
    public static final String KEY_MODE = "navigation_mode";
    public static final String MODE_NONE = "";
    public static final String MODE_FACTORY_YANDEX = "factory_yandex";

    private NavigationSettings() {
    }

    public static String selectedMode(SkinSettings settings) {
        if (settings == null) {
            return MODE_NONE;
        }
        return normalizeMode(settings.getString(KEY_MODE, MODE_NONE));
    }

    public static String normalizeMode(String mode) {
        return MODE_FACTORY_YANDEX.equals(mode)
                ? MODE_FACTORY_YANDEX
                : MODE_NONE;
    }

    public static boolean showsFactoryYandexMap(SkinSettings settings) {
        return MODE_FACTORY_YANDEX.equals(selectedMode(settings));
    }

    public static SkinSettings navigationOnly(String mode) {
        return SkinSettings.builder()
                .putString(KEY_MODE, normalizeMode(mode))
                .build();
    }
}
