package net.adminrunet.h9cluster.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import net.adminrunet.h9cluster.skins.SkinSettings;

import org.junit.Test;

public final class NavigationSettingsTest {
    @Test
    public void validComponentRoundTripsThroughSkinSettings() {
        String component = "ru.yandex.yandexnavi/.MainActivity";

        SkinSettings settings = NavigationSettings.navigationOnly(component);

        assertEquals(component, NavigationSettings.selectedComponent(settings));
    }

    @Test
    public void malformedComponentFallsBackToNoApplication() {
        assertEquals("", NavigationSettings.normalizeComponent("no-separator"));
        assertEquals("", NavigationSettings.normalizeComponent(" / "));
        assertEquals("", NavigationSettings.normalizeComponent(null));
    }

    @Test
    public void navigationOnlyNormalizationDropsForeignKeys() {
        NavigationOnlySettingsProvider provider =
                new NavigationOnlySettingsProvider();
        SkinSettings normalized = provider.normalize(
                SkinSettings.builder()
                        .putString(
                                NavigationSettings.KEY_COMPONENT,
                                "com.example/.MapsActivity")
                        .putBoolean("foreign_option", true)
                        .build());

        assertEquals(1, normalized.asMap().size());
        assertFalse(normalized.contains("foreign_option"));
    }
}
