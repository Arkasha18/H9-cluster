package net.adminrunet.h9cluster.skins.horizon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.adminrunet.h9cluster.skins.SkinSettings;

import org.junit.Test;

public final class HorizonSettingsProviderTest {
    private final HorizonSettingsProvider provider =
            new HorizonSettingsProvider();

    @Test
    public void defaultsKeepOriginalGaugeLayout() {
        SkinSettings defaults = provider.getDefaultSettings();

        assertFalse(HorizonSettingsProvider.shouldSwapPrimaryGauges(defaults));
    }

    @Test
    public void normalizationKeepsKnownValuesAndDropsForeignKeys() {
        SkinSettings normalized = provider.normalize(
                SkinSettings.builder()
                        .putBoolean(
                                HorizonSettingsProvider.SWAP_PRIMARY_GAUGES,
                                true)
                        .putString("foreign_option", "ignored")
                        .build());

        assertTrue(HorizonSettingsProvider.shouldSwapPrimaryGauges(normalized));
        assertFalse(normalized.contains("foreign_option"));
        assertEquals(1, normalized.asMap().size());
    }

    @Test
    public void normalizationFillsMissingAndInvalidValuesWithDefaults() {
        SkinSettings normalized = provider.normalize(
                SkinSettings.builder()
                        .putString(
                                HorizonSettingsProvider.SWAP_PRIMARY_GAUGES,
                                "invalid")
                        .build());

        assertFalse(HorizonSettingsProvider.shouldSwapPrimaryGauges(normalized));
    }
}
