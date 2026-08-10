package net.adminrunet.h9cluster.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.adminrunet.h9cluster.skins.SkinSettings;

import org.junit.Test;

public final class NavigationSettingsTest {
    @Test
    public void factoryYandexModeRoundTripsThroughSkinSettings() {
        String mode = NavigationSettings.MODE_FACTORY_YANDEX;

        SkinSettings settings = NavigationSettings.navigationOnly(mode);

        assertEquals(mode, NavigationSettings.selectedMode(settings));
        assertTrue(NavigationSettings.showsFactoryYandexMap(settings));
    }

    @Test
    public void unknownAndLegacyValuesFallBackToNoMap() {
        assertEquals(
                NavigationSettings.MODE_NONE,
                NavigationSettings.normalizeMode("ru.yandex.yandexmaps/.MainActivity"));
        assertEquals(
                NavigationSettings.MODE_NONE,
                NavigationSettings.normalizeMode(null));
    }

    @Test
    public void navigationOnlyNormalizationDropsForeignKeys() {
        NavigationOnlySettingsProvider provider =
                new NavigationOnlySettingsProvider();
        SkinSettings normalized = provider.normalize(
                SkinSettings.builder()
                        .putString(
                                NavigationSettings.KEY_MODE,
                                NavigationSettings.MODE_FACTORY_YANDEX)
                        .putBoolean("foreign_option", true)
                        .build());

        assertEquals(1, normalized.asMap().size());
        assertFalse(normalized.contains("foreign_option"));
        assertTrue(NavigationSettings.showsFactoryYandexMap(normalized));
    }
}
