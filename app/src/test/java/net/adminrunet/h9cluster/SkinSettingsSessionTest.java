package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.adminrunet.h9cluster.skins.SkinRegistry;
import net.adminrunet.h9cluster.skins.SkinSettings;
import net.adminrunet.h9cluster.skins.SpeedDisplayMode;

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
    public void horizonDraftSurvivesSwitchToSkinWithoutSettings() {
        SkinSettingsSession session = new SkinSettingsSession(
                SkinRegistry.HORIZON,
                emptyLoader(),
                new SkinSettingsSession.Normalizer() {
                    @Override
                    public SkinSettings normalize(
                            String skinId,
                            SkinSettings settings) {
                        return SkinRegistry.normalizeSettings(skinId, settings);
                    }
                });

        session.updateSettings(SkinSettings.builder()
                .putBoolean("swap_primary_gauges", true)
                .build());
        session.selectSkin(SkinRegistry.CLASSIC);
        assertTrue(session.snapshot().settings.isEmpty());

        session.selectSkin(SkinRegistry.HORIZON);
        assertTrue(session.snapshot().settings.getBoolean(
                "swap_primary_gauges",
                false));
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

    @Test
    public void speedDisplayModeTravelsWithTheSnapshot() {
        SkinSettingsSession session = new SkinSettingsSession(
                SkinRegistry.CLASSIC,
                emptyLoader(),
                passThroughNormalizer());

        assertEquals(
                SpeedDisplayMode.REAL,
                session.snapshot().speedDisplayMode);

        session.selectSpeedDisplayMode(
                SpeedDisplayMode.FACTORY_PLUS_4_5_PERCENT);
        SkinSettingsSession.Snapshot corrected = session.snapshot();
        assertEquals(
                SpeedDisplayMode.FACTORY_PLUS_4_5_PERCENT,
                corrected.speedDisplayMode);

        // The speed mode is part of the snapshot identity, so a change
        // forces the cluster preview to rebuild its renderer.
        session.selectSpeedDisplayMode(SpeedDisplayMode.REAL);
        assertFalse(corrected.equals(session.snapshot()));

        session.selectSpeedDisplayMode(null);
        assertEquals(
                SpeedDisplayMode.REAL,
                session.snapshot().speedDisplayMode);
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
