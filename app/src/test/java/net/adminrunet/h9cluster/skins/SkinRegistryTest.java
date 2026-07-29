package net.adminrunet.h9cluster.skins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SkinRegistryTest {
    @Test
    public void builtInSkinsRemainRegisteredInStableOrder() {
        SkinRegistry.Definition[] definitions =
                SkinRegistry.getDefinitions();

        assertEquals(4, definitions.length);
        assertEquals(SkinRegistry.CLASSIC, definitions[0].id);
        assertEquals(SkinRegistry.SPORT, definitions[1].id);
        assertEquals(SkinRegistry.SIMPLE_RED, definitions[2].id);
        assertEquals(SkinRegistry.HORIZON, definitions[3].id);
    }

    @Test
    public void classicAndSportDoNotExposeSettings() {
        SkinRegistry.Definition[] definitions =
                SkinRegistry.getDefinitions();
        for (int index = 0; index < 2; index++) {
            SkinRegistry.Definition definition = definitions[index];
            assertFalse(definition.hasSettings());
            assertTrue(definition.getDefaultSettings().isEmpty());
            assertTrue(definition.normalizeSettings(
                    SkinSettings.builder()
                            .putBoolean("foreign.option", true)
                            .build())
                    .isEmpty());
        }
    }

    @Test
    public void horizonExposesItsOwnSettings() {
        SkinRegistry.Definition definition =
                SkinRegistry.getDefinition(SkinRegistry.HORIZON);

        assertTrue(definition.hasSettings());
        assertFalse(definition.getDefaultSettings().getBoolean(
                "swap_primary_gauges",
                true));
    }

    @Test
    public void unknownSkinStillNormalizesToClassic() {
        assertEquals(
                SkinRegistry.CLASSIC,
                SkinRegistry.getDefinition("unknown").id);
    }
}
