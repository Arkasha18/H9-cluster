package net.adminrunet.h9cluster;

import net.adminrunet.h9cluster.skins.SkinRegistry;
import net.adminrunet.h9cluster.skins.SkinSettings;

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

        Snapshot(String skinId, SkinSettings settings) {
            this.skinId = SkinRegistry.normalize(skinId);
            this.settings = settings == null ? SkinSettings.empty() : settings;
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
                    && settings.equals(that.settings);
        }

        @Override
        public int hashCode() {
            return Objects.hash(skinId, settings);
        }
    }

    private final Loader loader;
    private final Normalizer normalizer;
    private final Map<String, SkinSettings> drafts = new LinkedHashMap<>();
    private String selectedSkinId;

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

    Snapshot snapshot() {
        return new Snapshot(selectedSkinId, drafts.get(selectedSkinId));
    }

    Map<String, SkinSettings> drafts() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(drafts));
    }

    private SkinSettings normalize(String skinId, SkinSettings settings) {
        SkinSettings normalized = normalizer.normalize(skinId, settings);
        return normalized == null ? SkinSettings.empty() : normalized;
    }
}
