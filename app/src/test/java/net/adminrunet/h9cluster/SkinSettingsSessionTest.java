package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.adminrunet.h9cluster.skins.SkinRegistry;
import net.adminrunet.h9cluster.skins.SkinSettings;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SkinSettingsSessionTest {
    @Test
    public void draftsRemainIndependentWhileSwitchingSkins() {
        final Map<String, Integer> loadCounts = new LinkedHashMap<>();
        SkinSettingsSession session = new SkinSettingsSession(
                SkinRegistry.CLASSIC,
                new SkinSettingsSession.Loader() {
                    @Override
                    public SkinSettings load(String skinId) {
                        Integer previous = loadCounts.get(skinId);
                        loadCounts.put(skinId, previous == null ? 1 : previous + 1);
                        return SkinSettings.builder()
                                .putBoolean("block.visible", true)
                                .build();
                    }
                },
                passThroughNormalizer());

        session.updateSettings(SkinSettings.builder()
                .putBoolean("block.visible", false)
                .build());
        session.selectSkin(SkinRegistry.SPORT);
        session.updateSettings(SkinSettings.builder()
                .putString("accent", "red")
                .build());
        session.selectSkin(SkinRegistry.CLASSIC);

        assertFalse(session.snapshot().settings.getBoolean(
                "block.visible",
                true));
        assertEquals(Integer.valueOf(1), loadCounts.get(SkinRegistry.CLASSIC));
        assertEquals(Integer.valueOf(1), loadCounts.get(SkinRegistry.SPORT));
        assertEquals(2, session.drafts().size());

        session.selectSkin(SkinRegistry.SPORT);
        assertEquals(
                "red",
                session.snapshot().settings.getString("accent", ""));
    }

    @Test
    public void unknownSkinFallsBackToRegistryDefault() {
        SkinSettingsSession session = new SkinSettingsSession(
                "missing",
                emptyLoader(),
                passThroughNormalizer());

        assertEquals(SkinRegistry.getDefaultId(), session.snapshot().skinId);
    }

    @Test
    public void nullNormalizerResultBecomesEmptySettings() {
        SkinSettingsSession session = new SkinSettingsSession(
                SkinRegistry.CLASSIC,
                emptyLoader(),
                new SkinSettingsSession.Normalizer() {
                    @Override
                    public SkinSettings normalize(
                            String skinId,
                            SkinSettings settings) {
                        return null;
                    }
                });

        assertTrue(session.snapshot().settings.isEmpty());
    }

    private static SkinSettingsSession.Loader emptyLoader() {
        return new SkinSettingsSession.Loader() {
            @Override
            public SkinSettings load(String skinId) {
                return SkinSettings.empty();
            }
        };
    }

    private static SkinSettingsSession.Normalizer passThroughNormalizer() {
        return new SkinSettingsSession.Normalizer() {
            @Override
            public SkinSettings normalize(
                    String skinId,
                    SkinSettings settings) {
                return settings;
            }
        };
    }
}
