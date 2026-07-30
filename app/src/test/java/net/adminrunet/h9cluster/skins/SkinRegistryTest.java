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
    public void builtInSkinsDoNotExposeUnrelatedSettings() {
        SkinSettings foreign = SkinSettings.builder()
                .putBoolean("foreign.option", true)
                .build();
        for (SkinRegistry.Definition definition
                : SkinRegistry.getDefinitions()) {
            SkinSettings normalized = definition.normalizeSettings(foreign);
            assertFalse(
                    "no skin may carry another's key: " + definition.id,
                    normalized.contains("foreign.option"));
            if (definition.hasSettings()) {
                // A configurable skin answers with its own complete
                // configuration rather than with nothing.
                assertFalse(
                        definition.id + " must offer its defaults",
                        definition.getDefaultSettings().isEmpty());
                assertFalse(
                        definition.id + " must fill in what it needs",
                        normalized.isEmpty());
            } else {
                assertTrue(definition.getDefaultSettings().isEmpty());
                assertTrue(normalized.isEmpty());
            }
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
