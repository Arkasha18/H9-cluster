package net.adminrunet.h9cluster;

import net.adminrunet.h9cluster.skins.SkinRegistry;
import net.adminrunet.h9cluster.skins.SkinSettings;
import net.adminrunet.h9cluster.skins.SpeedDisplayMode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Keeps independent unsaved settings drafts while the user switches skins. */
final class SkinSettingsSession {
    interface Loader {
        SkinSettings load(String skinId);
    }

    interface Normalizer {
        SkinSettings normalize(String skinId, SkinSettings settings);
    }

    static final class Snapshot {
        final String skinId;
        final SkinSettings settings;
        final SpeedDisplayMode speedDisplayMode;
        final int fuelTankCapacityLiters;

        Snapshot(
                String skinId,
                SkinSettings settings,
                SpeedDisplayMode speedDisplayMode) {
            this(skinId, settings, speedDisplayMode, FuelTankCapacity.DEFAULT_LITERS);
        }

        Snapshot(
                String skinId,
                SkinSettings settings,
                SpeedDisplayMode speedDisplayMode,
                int fuelTankCapacityLiters) {
            this.skinId = SkinRegistry.normalize(skinId);
            this.settings = settings == null ? SkinSettings.empty() : settings;
            this.speedDisplayMode = speedDisplayMode == null
                    ? SpeedDisplayMode.REAL
                    : speedDisplayMode;
            this.fuelTankCapacityLiters = FuelTankCapacity.normalize(fuelTankCapacityLiters);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Snapshot)) {
                return false;
            }
            Snapshot that = (Snapshot) other;
            return skinId.equals(that.skinId)
                    && settings.equals(that.settings)
                    && speedDisplayMode == that.speedDisplayMode
                    && fuelTankCapacityLiters == that.fuelTankCapacityLiters;
        }

        @Override
        public int hashCode() {
            return Objects.hash(skinId, settings, speedDisplayMode, fuelTankCapacityLiters);
        }
    }

    private final Loader loader;
    private final Normalizer normalizer;
    private final Map<String, SkinSettings> drafts = new LinkedHashMap<>();
    private String selectedSkinId;
    private SpeedDisplayMode speedDisplayMode = SpeedDisplayMode.REAL;
    private int fuelTankCapacityLiters = FuelTankCapacity.DEFAULT_LITERS;

    SkinSettingsSession(String selectedSkinId, Loader loader) {
        this(
                selectedSkinId,
                loader,
                new Normalizer() {
                    @Override
                    public SkinSettings normalize(
                            String skinId,
                            SkinSettings settings) {
                        return SkinRegistry.normalizeSettings(
                                skinId,
                                settings);
                    }
                });
    }

    SkinSettingsSession(
            String selectedSkinId,
            Loader loader,
            Normalizer normalizer) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        selectSkin(selectedSkinId);
    }

    void selectSkin(String skinId) {
        selectedSkinId = SkinRegistry.normalize(skinId);
        if (!drafts.containsKey(selectedSkinId)) {
            SkinSettings loaded = loader.load(selectedSkinId);
            drafts.put(
                    selectedSkinId,
                    normalize(selectedSkinId, loaded));
        }
    }

    void updateSettings(SkinSettings settings) {
        drafts.put(
                selectedSkinId,
                normalize(selectedSkinId, settings));
    }

    void selectSpeedDisplayMode(SpeedDisplayMode mode) {
        speedDisplayMode = mode == null ? SpeedDisplayMode.REAL : mode;
    }

    void selectFuelTankCapacity(int capacity) {
        fuelTankCapacityLiters = FuelTankCapacity.normalize(capacity);
    }

    Snapshot snapshot() {
        return new Snapshot(
                selectedSkinId,
                drafts.get(selectedSkinId),
                speedDisplayMode,
                fuelTankCapacityLiters);
    }

    Map<String, SkinSettings> drafts() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(drafts));
    }

    private SkinSettings normalize(String skinId, SkinSettings settings) {
        SkinSettings normalized = normalizer.normalize(skinId, settings);
        return normalized == null ? SkinSettings.empty() : normalized;
    }
}
